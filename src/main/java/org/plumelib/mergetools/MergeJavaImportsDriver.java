package org.plumelib.mergetools;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.plumelib.util.CollectionsPlume.mapList;

import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.RemoveUnusedImports;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.TerminatesExecution;
import org.plumelib.mergetools.ConflictedFile.CommonLines;
import org.plumelib.mergetools.ConflictedFile.ConflictElement;
import org.plumelib.mergetools.ConflictedFile.MergeConflict;
import org.plumelib.mergetools.Diff3File.Diff3Hunk;
import org.plumelib.mergetools.Diff3File.Diff3HunkSection;
import org.plumelib.mergetools.Diff3File.Diff3ParseException;
import org.plumelib.mergetools.javacparse.JavacParse;
import org.plumelib.util.CollectionsPlume;

/**
 * This is a git merge driver for Java files. A git merge driver takes as input three filenames, for
 * the current, base, and other versions of the file; the merge driver overwrites the current file
 * with the merge result.
 *
 * <p>This program first does {@code git merge-file}, then it tries to re-insert any {@code import}
 * statements that were removed but are needed for compilation to succeed.
 */
@SuppressWarnings({"UnusedMethod", "UnusedVariable", "lock"}) // todo
public class MergeJavaImportsDriver {

  /** If true, print diagnostics for debugging. */
  private static final boolean verbose = false;

  /** Do not instantiate. */
  private MergeJavaImportsDriver() {
    throw new Error("Do not instantiate");
  }

  /**
   * A git merge driver to merge a Java file.
   *
   * <p>Exit status greater than 128 means to abort the merge.
   *
   * @param args the command-line arguments of the merge driver, 3 filenames: current, base, other
   */
  public static void main(String[] args) {
    if (verbose) {
      System.out.printf("main arguments: %s%n", Arrays.toString(args));
    }
    if (args.length != 3) {
      String message =
          String.format(
              "MergeJavaImportsDriver: expected 3 arguments current, base, other; got %d: %s",
              args.length, Arrays.toString(args));
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }
    String currentFileName = args[0];
    String baseFileName = args[1];
    String otherFileName = args[2];

    Path currentPath = Path.of(currentFileName);
    Path basePath = Path.of(baseFileName);
    Path otherPath = Path.of(otherFileName);

    mainHelper(currentPath, basePath, otherPath);
  }

  /**
   * Does the work of MergeJavaImportsDriver, except argument parsing.
   *
   * @param currentPath the current file; is overwritten by this method
   * @param basePath the base file
   * @param otherPath the other file
   */
  protected static void mainHelper(Path currentPath, Path basePath, Path otherPath) {

    String currentCode;
    String baseCode;
    String otherCode;
    try {
      currentCode = new String(Files.readAllBytes(currentPath), UTF_8);
      baseCode = new String(Files.readAllBytes(basePath), UTF_8);
      otherCode = new String(Files.readAllBytes(otherPath), UTF_8);
    } catch (IOException e) {
      String message = "MergeJavaImportsDriver: trouble reading file: " + e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    // `git merge-file` overwrites the current file, so make a copy of its contents first.
    Path currentPathCopy;
    try {
      currentPathCopy = File.createTempFile("currentFileCopy-", ".java").toPath();
      Files.copy(currentPath, currentPathCopy, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      currentPathCopy = null;
      throw new Error("Problem copying " + currentPath + " to " + currentPathCopy, e);
    }

    ProcessBuilder pb =
        new ProcessBuilder(
            "git", "merge-file", currentPath.toString(), basePath.toString(), otherPath.toString());
    if (verbose) {
      System.out.printf("About to call: %s%n", pb.command());
    }
    int gitMergeFileExitCode;
    try {
      Process p = pb.start();
      gitMergeFileExitCode = p.waitFor();
    } catch (IOException | InterruptedException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    if (verbose) {
      System.out.printf("gitMergeFileExitCode=%s%n", gitMergeFileExitCode);
    }

    String gitMergedCode;
    try {
      gitMergedCode = new String(Files.readAllBytes(currentPath), UTF_8);
      if (verbose) {
        System.out.printf("gitMergedCode=%s%n", gitMergedCode);
      }
    } catch (IOException e) {
      String message = "MergeJavaImportsDriver: trouble reading merged file: " + e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    ConflictedFile cf = ConflictedFile.parseFileContents(gitMergedCode);
    List<ConflictElement> ces = cf.contents();
    if (verbose) {
      System.out.printf(
          "conflicted file (size %s)=%s%n", (ces == null ? "null" : ("" + ces.size())), cf);
    }
    if (ces == null) {
      String message = "MergeJavaImportsDriver: trouble reading merged file: " + cf.error();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
    }
    List<MergeConflict> mcs = new ArrayList<>();
    for (ConflictElement ce : ces) {
      if (ce instanceof MergeConflict) {
        mcs.add((MergeConflict) ce);
      }
    }

    if (gitMergeFileExitCode == 0) {
      // There are no merge conflicts, but we should check the imports anyway.
      assert mcs.isEmpty();
    } else if (0 < gitMergeFileExitCode && gitMergeFileExitCode <= 127) {
      // There are merge conflicts.
      // Proceed only if all the merge conflicts are within the imports.
      if (mcs.stream().anyMatch(MergeJavaImportsDriver::isOutsideImports)) {
        System.exit(gitMergeFileExitCode);
      }
    } else {
      // `git merge-file` erred, so abort the merge
      System.exit(gitMergeFileExitCode);
    }

    // There are no merge conflicts except possibly within the imports.

    // TODO: If this is too restrictive, expand it.
    // If an import merge conflict has different comments within it, give up.
    if (mcs.stream().anyMatch(MergeJavaImportsDriver::hasDifferingComments)) {
      System.exit(gitMergeFileExitCode);
    }

    // First, do merges where git showed a conflict.
    List<CommonLines> cls = new ArrayList<>();
    for (ConflictElement ce : ces) {
      CommonLines cl;
      if (ce instanceof CommonLines) {
        cl = (CommonLines) ce;
      } else if (ce instanceof MergeConflict) {
        cl = mergeImportsCommentwise((MergeConflict) ce);
        if (verbose) {
          System.out.printf("merged commentwise = %s%n", cl);
        }
      } else {
        throw new Error();
      }
      cls.add(cl);
    }

    // If git produced a clean merge that removed an import from one of the two sides, reintroduce
    // that import.
    // Run diff3 to obtain all the differences.
    ProcessBuilder pbDiff3 =
        new ProcessBuilder(
            "diff3", currentPathCopy.toString(), basePath.toString(), otherPath.toString());
    if (verbose) {
      System.out.printf("About to call: %s%n", pb.command());
    }
    String diff3Output;
    try {
      Process pDiff3 = pbDiff3.start();
      int diff3ExitCode = pDiff3.waitFor();
      if (diff3ExitCode == 2) {
        // `diff3` erred, so abort the merge
        System.out.printf("diff3 erred");
        System.exit(129);
        throw new Error("unreachable"); // to tell javac that execution does not continue
      }
      diff3Output = new String(pDiff3.getInputStream().readAllBytes(), UTF_8);
      if (verbose) {
        System.out.println("diff3Output: " + diff3Output);
      }
    } catch (IOException | InterruptedException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    Diff3File diff3file;
    try {
      diff3file = Diff3File.parseFileContents(diff3Output, currentPathCopy.toString());
    } catch (Diff3ParseException e) {
      System.out.println(e.getMessage());
      System.exit(129);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    int startLineOffset = 0;
    List<String> mergedCodeLines = ConflictedFile.toLines(cls);
    // TODO: I should probably track how the insertion of import statements has changed where other
    // import statemets should be inserted.
    for (Diff3Hunk h : diff3file.contents()) {
      List<String> lines = h.section2().lines();
      List<String> importStatementsThatMightBeRemoved =
          CollectionsPlume.filter(lines, MergeJavaImportsDriver::isImportStatement);
      if (importStatementsThatMightBeRemoved.isEmpty()) {
        // Merging this hunk did not remove any import statements.
        startLineOffset += h.lineChangeSize();
        continue;
      }
      String lineSeparator = cf.lineSeparator();
      for (int i = 0; i < importStatementsThatMightBeRemoved.size(); i++) {
        importStatementsThatMightBeRemoved.set(
            i, importStatementsThatMightBeRemoved.get(i) + lineSeparator);
      }

      Diff3HunkSection edit;
      switch (h.kind()) {
        case ONE_DIFFERS:
          edit = h.section1();
          break;
        case THREE_DIFFERS:
          edit = h.section3();
          break;
        case TWO_DIFFERS:
          // no change to startLineOffset;
          continue;
        case THREE_WAY:
          // A 3-way conflict must be from an import statement, but each such conflict has already
          // been solved by being unioned, above.
          startLineOffset += h.lineChangeSize();
          continue;
        default:
          throw new Error("Unhandled kind: " + h.kind());
      }

      // TODO: Insertion may be expensive because `lines` is an ArrayList.  Maybe change to a
      // LinkedList just for the insertion operations?
      int startLine;
      switch (edit.command().kind()) {
        case APPEND:
          startLine = edit.command().startLine();
          break;
        case CHANGE:
          // Find the first line that is an import, and insert immediately after it.
          int i;
          List<String> editLines = edit.lines();
          int editLinesSize = editLines.size();
          for (i = 0; i < editLinesSize; i++) {
            if (isImportStatement(editLines.get(i))) {
              break;
            }
          }
          startLine = edit.command().startLine() + i;
          break;
        default:
          throw new Error();
      }
      startLine += startLineOffset;
      if (verbose) {
        System.out.printf("Before inserting at %d: %s%n", startLine, mergedCodeLines);
      }
      mergedCodeLines.addAll(startLine, importStatementsThatMightBeRemoved);
      if (verbose) {
        System.out.printf("After inserting: %s%n", mergedCodeLines);
      }
      startLineOffset += h.lineChangeSize();
    }

    String mergedCode = String.join("", mergedCodeLines);
    if (verbose) {
      System.out.println("mergedCode=" + mergedCode);
    }

    JCCompilationUnit mergedCU = JavacParse.parseJavaCode(mergedCode);
    if (mergedCU == null) {
      // Our merge is nonsyntactic, so don't write it out.
      if (verbose) {
        System.out.printf("mergedCU is null%n");
      }
      System.exit(gitMergeFileExitCode);
    }

    List<? extends ImportTree> mergedImports = mergedCU.getImports();
    if (verbose) {
      System.out.printf("mergedImports=%s%n", mergedImports);
    }

    // TODO: handle static imports

    String gjfCode;
    try {
      gjfCode = RemoveUnusedImports.removeUnusedImports(mergedCode);
    } catch (FormatterException e) {
      if (verbose) {
        System.out.printf("gjf threw FormatterException: %s%n", e.getMessage());
      }
      gjfCode = mergedCode;
    }
    if (gjfCode.equals(mergedCode)) {
      // gjf made no changes.
      if (verbose) {
        System.out.printf("gjf removed no imports%n");
      }
      writeAndExit(gjfCode, currentPath, 0);
    }

    JCCompilationUnit gjfCU = JavacParse.parseJavaCode(gjfCode);
    if (gjfCU == null) {
      throw new Error();
    }

    List<Import> gjfImports = mapList(Import::new, gjfCU.getImports());

    List<Import> removedImports = new ArrayList<>();
    for (ImportTree it : mergedImports) {
      // TODO: different predicate?
      Import i = new Import(it);
      if (!gjfImports.contains(i)) {
        removedImports.add(i);
      }
    }

    if (verbose) {
      System.out.printf("removedImports=%s%n", removedImports);
    }

    if (!removedImports.isEmpty()) {
      StringJoiner removedImportRegex = new StringJoiner("|", "\\s*import\\s+(", ");\\R?");
      for (Import i : removedImports) {
        removedImportRegex.add(i.regexAfterImport());
      }
      @SuppressWarnings("regex:argument") // regex constructed via string concatenation
      Pattern removedImportPattern = Pattern.compile(removedImportRegex.toString());

      assert ces.size() == cls.size();
      int size = ces.size();
      for (int i = 0; i < size; i++) {
        if (ces.get(i) instanceof MergeConflict) {
          cls.set(i, cls.get(i).removeMatchingLines(removedImportPattern));
        }
      }
    }

    String prunedCode = ConflictedFile.toString(cls);
    writeAndExit(prunedCode, currentPath, 0);
  }

  /**
   * Returns the difference in length caused by the hunk.
   *
   * @param h a hunk that was merged cleanly by {@code git merge}
   * @return how much the hunk changed the length of the code
   */
  private static int lineChangeSize(Diff3Hunk h) {
    int lengthDifference = h.section1().lines().size() - h.section3().lines().size();
    switch (h.kind()) {
      case ONE_DIFFERS:
        return -lengthDifference;
      case THREE_DIFFERS:
        return lengthDifference;
      default:
        throw new Error();
    }
  }

  /**
   * Represents an import statement.
   *
   * <p>One reason for this class is that ImportTree uses reference equality. Another reason is that
   * it is expensive to repeatedly call {@code toString} on the Tree resulting from {@code
   * ImportTree.identifier()}.
   *
   * @param isStatic true if the import is a static import
   * @param identifier the identifier being imported
   */
  @SuppressWarnings("lock") // todo
  static record Import(boolean isStatic, String identifier) {
    /**
     * Constructs an Import from an ImportTree.
     *
     * @param it an ImportTree
     */
    public Import(ImportTree it) {
      this(it.isStatic(), it.getQualifiedIdentifier().toString());
    }

    @Override
    public boolean equals(Import this, @Nullable Object o) {
      if (!(o instanceof Import)) {
        return false;
      }
      Import other = (Import) o;
      return isStatic == other.isStatic() && identifier.equals(other.identifier());
    }

    @Override
    public int hashCode(Import this) {
      return Objects.hash(isStatic, identifier);
    }

    @Override
    public String toString() {
      if (isStatic) {
        return "import static " + identifier;
      } else {
        return "import " + identifier;
      }
    }

    /**
     * Return a regex that matches the text after "import " for this.
     *
     * @return a regex that matches the text after "import "
     */
    public String regexAfterImport() {
      String identifierRegex = Pattern.quote(identifier);
      if (isStatic) {
        return "static\\s+" + identifierRegex;
      } else {
        return identifierRegex;
      }
    }
  }

  /**
   * Writes the contents to the file and exits with the given status code.
   *
   * @param contents the file contents
   * @param path the path for the output file
   * @param status the status code
   */
  @TerminatesExecution
  static void writeAndExit(String contents, Path path, int status) {
    // TODO: perhaps abstract this out.
    try (PrintWriter out = new PrintWriter(path.toString(), UTF_8)) {
      out.print(contents);
    } catch (IOException e) {
      throw new Error(e);
    }
    System.exit(status);
  }

  /**
   * Returns true if the given merge conflict is not an import block.
   *
   * @param mc a merge conflict
   * @return true if the argument has with non-<code>import</code> lines
   */
  static boolean isOutsideImports(MergeConflict mc) {
    String[] base = mc.base();
    return !(isImportBlock(mc.left())
        && isImportBlock(mc.right())
        && (base == null || isImportBlock(base)));
  }

  /**
   * Given some lines of code, return true if they can all be part of an import block: every line
   * satisfies {@link #isImportBlockLine}.
   *
   * @param lines some lines of code
   * @return true if the argument is an import block
   */
  static boolean isImportBlock(String[] lines) {
    return Arrays.stream(lines).allMatch(MergeJavaImportsDriver::isImportBlockLine);
  }

  /**
   * Returns true if the given conflict element is a merge conflict that has different comments in
   * different variants.
   *
   * @param ce an element of a conflicted file
   * @return true if the argument is a merge conflit with differing comments
   */
  static boolean hasDifferingComments(ConflictElement ce) {

    if (!(ce instanceof MergeConflict)) {
      return false;
    }
    MergeConflict mc = (MergeConflict) ce;

    return !sameCommentLines(mc);
  }

  /**
   * Returns true if the left and right contain the same comment lines.
   *
   * @param mc a merge conflict
   * @return true if the left and right contain the same comment lines
   */
  static boolean sameCommentLines(MergeConflict mc) {
    List<String> leftComments = commentLines(mc.left());
    List<String> rightComments = commentLines(mc.right());
    return leftComments.equals(rightComments);
  }

  /**
   * Given a merge conflict that is an import block, merge it, retaining comments but not caring for
   * whitespace.
   *
   * @param mc a merge conflict that is an import block. The left and right variants contain the
   *     same comment lines in the same order.
   * @return the result of merging the conflict
   */
  // "protected" so test code can call it.
  protected static CommonLines mergeImportsCommentwise(MergeConflict mc) {
    List<String> leftLines = Arrays.asList(mc.left());
    List<String> rightLines = Arrays.asList(mc.right());

    List<String> leftComments = commentLines(mc.left());
    List<String> rightComments = commentLines(mc.right());
    assert leftComments.equals(rightComments);

    List<String> result = new ArrayList<>();

    int leftIndex = 0; // the index after the most recently found comment
    int rightIndex = 0; // the index after the most recently found comment
    for (String comment : leftComments) {
      int leftCommentIndex = indexOf(leftLines, comment, leftIndex);
      int rightCommentIndex = indexOf(rightLines, comment, rightIndex);
      if (leftCommentIndex == -1 || rightCommentIndex == -1) {
        throw new Error();
      }
      SortedSet<String> contents = new TreeSet<>();
      contents.addAll(leftLines.subList(leftIndex, leftCommentIndex));
      contents.addAll(rightLines.subList(rightIndex, rightCommentIndex));
      result.addAll(contents);
      result.add(comment);
      leftIndex = leftCommentIndex + 1;
      rightIndex = rightCommentIndex + 1;
    }
    SortedSet<String> contents = new TreeSet<>();
    contents.addAll(leftLines.subList(leftIndex, leftLines.size()));
    contents.addAll(rightLines.subList(rightIndex, rightLines.size()));
    result.addAll(contents);

    return new CommonLines(result.toArray(new String[0]));
  }

  /** A pattern that matches a string consisting only of whitespace. */
  private static Pattern whitespacePattern = Pattern.compile("\s*");

  // TODO: Should this forbid leading whitespace, to avoid false positive matches?
  /**
   * A pattern that matches an import line in Java code. Does not match import lines with a trailing
   * comment.
   */
  private static Pattern importPattern = Pattern.compile("\s*import .*;\\R?");

  /**
   * Returns true if the given line is an import statement.
   *
   * @param line a line of Java code
   * @return true if the given line is an import statement
   */
  static boolean isImportStatement(String line) {
    return importPattern.matcher(line).matches();
  }

  /**
   * Given a line of code, return true if can appear in an import block: it is an <code>import
   * </code>, blank line, or comment.
   *
   * @param line a line of code
   * @return true if the line can be in an import block
   */
  static boolean isImportBlockLine(String line) {
    return line.isEmpty()
        || whitespacePattern.matcher(line).matches()
        || isCommentLine(line)
        || isImportStatement(line);
  }

  /**
   * Returns all the comment lines in the input.
   *
   * @param lines code lines, each of which may be terminated by a line separator
   * @return the comment lines in the input
   */
  static List<String> commentLines(String[] lines) {
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      if (isCommentLine(line)) {
        result.add(line);
      }
    }
    return result;
  }

  /**
   * A pattern that matches a comment line. Because of use of {@code matches()}, no regex anchoring
   * is needed.
   */
  private static Pattern commentLinePattern = Pattern.compile("\\s*(//.*|/\\*.*\\*/\\s*)\\R?");

  /**
   * Returns true if the given line is a comment line.
   *
   * @param line a line of code, which may be terminated by a line separator
   * @return true if the line is a comment line
   */
  // "protected" to permit test code to call it.
  protected static boolean isCommentLine(String line) {
    return commentLinePattern.matcher(line).matches();
  }

  /**
   * Returns the imports of the given Java code.
   *
   * @param javaCode the contents of a Java file: a compilation unit
   * @return the imports of the given Java code
   */
  @SuppressWarnings({"UnusedMethod", "UnusedVariable"}) // TODO: remove
  private List<? extends ImportTree> getImports(String javaCode) {
    // TODO
    throw new Error("to implement");
  }

  // I don't really need the base file information, but include it for completeness.
  /**
   * Information about the 4 files involved in a 3-way merge.
   *
   * @param baseFilename the name of the base file
   * @param baseJavaCode the contents of the base file
   * @param leftFilename the name of the left file
   * @param leftJavaCode the contents of the left file
   * @param rightFilename the name of the right file
   * @param rightJavaCode the contents of the right file
   * @param mergedFilename the name of the merged file
   * @param mergedJavaCode the contents of the merged file
   */
  private record MergeFiles(
      /** The name of the base file. */
      String baseFilename,
      /** The contents of the base file. */
      String baseJavaCode,

      /** The name of the left file. */
      String leftFilename,
      /** The contents of the left file. */
      String leftJavaCode,

      /** The name of the right file. */
      String rightFilename,
      /** The contents of the right file. */
      String rightJavaCode,

      /** The name of the merged file. */
      String mergedFilename,
      /** The contents of the merged file. */
      String mergedJavaCode) {}

  /**
   * Parse command-line arguments.
   *
   * @param args the command-line arguments
   * @return the files specified by the command-line arguments; null if the files are not Java files
   */
  private static @Nullable MergeFiles parseArgs(String[] args) {

    String baseFilename;
    String baseJavaCode;
    String leftFilename;
    String leftJavaCode;
    String rightFilename;
    String rightJavaCode;
    String mergedFilename;
    String mergedJavaCode;

    if (args.length == 4) {
      baseFilename = args[0];
      leftFilename = args[1];
      rightFilename = args[2];
      mergedFilename = args[3];
    } else if (args.length == 0) {
      baseFilename = System.getenv("BASE");
      leftFilename = System.getenv("LOCAL");
      rightFilename = System.getenv("REMOTE");
      mergedFilename = System.getenv("MERGED");
      if (baseFilename == null
          || leftFilename == null
          || rightFilename == null
          || mergedFilename == null) {
        String message =
            String.format(
                "MergeJavaImportsDriver: no arguments given, but null environment variable."
                    + "  $BASE=%s  $LOCAL=%s  $REMOTE=%s  $MERGED=%s",
                baseFilename, leftFilename, rightFilename, mergedFilename);
        System.out.println(message);
        System.err.println(message);
        System.exit(2);
      }
    } else {
      String message =
          String.format(
              "MergeJavaImportsDriver: expected 0 or 4 arguments, got %d: %s",
              args.length, Arrays.toString(args));
      System.out.println(message);
      System.err.println(message);
      System.exit(2);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    // Perhaps do this check in a non-Java process (that is, before calling this , for efficiency.
    // TODO: Can I improve this check?  What are the filenames provided to the mergetool?  Can I use
    // `endsWith()`?
    if (!(baseFilename.contains(".java")
        && leftFilename.contains(".java")
        && rightFilename.contains(".java")
        && mergedFilename.contains(".java"))) {
      return null;
    }

    try {
      baseJavaCode = new String(Files.readAllBytes(Path.of(baseFilename)), UTF_8);
      leftJavaCode = new String(Files.readAllBytes(Path.of(leftFilename)), UTF_8);
      rightJavaCode = new String(Files.readAllBytes(Path.of(rightFilename)), UTF_8);
      mergedJavaCode = new String(Files.readAllBytes(Path.of(mergedFilename)), UTF_8);
    } catch (IOException e) {
      String message = "MergeJavaImportsDriver: trouble reading file: " + e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(2);
      throw new Error("unreachable"); // to tell javac that execution does not continue
    }

    return new MergeFiles(
        baseFilename,
        baseJavaCode,
        leftFilename,
        leftJavaCode,
        rightFilename,
        rightJavaCode,
        mergedFilename,
        mergedJavaCode);
  }

  ///////////////////////////////////////////////////////////////////////////

  // TODO: use this from CollectionsPlume once plume-util is released.
  /**
   * Returns the first index of the given value in the list, starting at the given index. Uses
   * {@code Object.equals} for comparison.
   *
   * @param list a list
   * @param start the starting index
   * @param value the value to search for
   * @return the index of the value in the list, at or after the given index
   */
  public static int indexOf(List<?> list, Object value, int start) {
    int idx = list.subList(start, list.size()).indexOf(value);
    return idx == -1 ? -1 : idx + start;
  }
}
