package org.plumelib.merging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.RemoveUnusedImports;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.CommonLines;
import org.plumelib.merging.fileformat.ConflictedFile.ConflictElement;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;
import org.plumelib.merging.fileformat.Diff3File;
import org.plumelib.merging.fileformat.Diff3File.Diff3Hunk;
import org.plumelib.merging.fileformat.Diff3File.Diff3HunkSection;
import org.plumelib.merging.fileformat.Diff3File.Diff3ParseException;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/**
 * This class resolves conflicts in {@code import} statements and re-inserts any {@code import}
 * statements that were removed by a merge but are needed for compilation to succeed.
 */
public class JavaImportsMerger implements Merger {

  /** If true, print diagnostics for debugging. */
  private final boolean verbose;

  /** Creates a JavaImportsMerger. */
  public JavaImportsMerger() {
    this(false);
  }

  /**
   * Creates a JavaImportsMerger.
   *
   * @param verbose if true, output diagnostic information
   */
  public JavaImportsMerger(boolean verbose) {
    this.verbose = verbose;
  }

  @Override
  public void merge(MergeState mergeState) {

    ConflictedFile cf = mergeState.conflictedFile();
    if (verbose) {
      System.out.printf("JavaImportsMerger: conflicted file = %s%n", cf);
    }
    // Don't check mergeState.hasConflict() because this merger should run
    // regardless of whether the merge so far is clean.

    List<ConflictElement> hunks = cf.hunks();
    if (hunks == null) {
      String message = "JavaImportsMerger: parse error in merged file: " + cf.parseError();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    List<MergeConflict> mcs = cf.mergeConflicts();

    // Proceed only if all the merge conflicts (if any) are within the imports.
    if (CollectionsPlume.anyMatch(mcs, JavaImportsMerger::isOutsideImports)) {
      return;
    }

    // There are no merge conflicts except possibly within the imports.

    // If an import merge conflict has different comments within it, give up.
    if (CollectionsPlume.anyMatch(mcs, JavaImportsMerger::hasDifferingComments)) {
      return;
    }

    // The imports merger will introduce every `import` statement that was in either of the two
    // parents.  However, if an import was moved -- that is, one parent removed `import a.b.c.Foo`
    // and added `import d.e.Foo` -- then don't re-introduce the removed one.

    // This doesn't use `cf.conflictedFile()` because we are also interested in changes made by
    // clean merges.
    List<String> forbiddenImports = new ArrayList<>();
    String baseContents = String.join("", mergeState.baseFileLines());
    String leftContents = String.join("", mergeState.leftFileLines());
    String rightContents = String.join("", mergeState.rightFileLines());
    forbiddenImports.addAll(renamedImports(baseContents, leftContents));
    forbiddenImports.addAll(renamedImports(baseContents, rightContents));

    // Wherever git produced a conflict, replace it by a CommonLines.
    List<CommonLines> cls = new ArrayList<>();
    for (ConflictElement ce : hunks) {
      CommonLines cl;
      if (ce instanceof CommonLines) {
        cl = (CommonLines) ce;
      } else if (ce instanceof MergeConflict) {
        cl = mergeImportsCommentwise((MergeConflict) ce);
        if (verbose) {
          System.out.printf("merged commentwise = %s%n", cl);
        }
      } else {
        throw new Error("what ConflictElement? " + ce.getClass() + " " + ce);
      }
      cls.add(cl);
    }

    // If git produced a merge that removed an import from one of the two sides, reintroduce
    // that import.
    // Run diff3 to obtain all the differences, even the ones that `git merge-file` merged.
    ProcessBuilder pbDiff3 =
        new ProcessBuilder(
            "diff3", mergeState.leftFileName, mergeState.baseFileName, mergeState.rightFileName);
    if (verbose) {
      System.out.printf("About to call: %s%n", pbDiff3.command());
    }
    String diff3Output;
    try {
      Process pDiff3 = pbDiff3.start();
      diff3Output = new String(pDiff3.getInputStream().readAllBytes(), UTF_8);
      if (verbose) {
        System.out.println("diff3Output: " + diff3Output);
      }
      // It is essential to call waitFor *after* reading the output from getInputStream().
      int diff3ExitCode = pDiff3.waitFor();
      if (diff3ExitCode != 0 && diff3ExitCode != 1) {
        // `diff3` erred, so abort the merge
        String message = "diff3 erred: " + diff3Output;
        System.out.println(message);
        System.err.println(message);
        return;
      }
    } catch (IOException | InterruptedException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    Diff3File diff3file;
    try {
      diff3file = Diff3File.parseFileContents(diff3Output, mergeState.leftFileName);
    } catch (Diff3ParseException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    int startLineOffset = 0;
    List<String> mergedFileContentsLines = CommonLines.toLines(cls);
    // Iterate through the difs, adding lines to `mergedFileContentsLines`.
    for (Diff3Hunk h : diff3file.contents()) {
      List<String> lines = h.section2().lines();
      List<String> importStatementsThatMightBeRemoved =
          CollectionsPlume.filter(lines, JavaImportsMerger::isImportStatement);
      if (importStatementsThatMightBeRemoved.isEmpty()) {
        // Merging this hunk did not remove any import statements.
        startLineOffset += h.lineChangeSize();
        continue;
      }
      for (int i = 0; i < importStatementsThatMightBeRemoved.size(); i++) {
        importStatementsThatMightBeRemoved.set(
            i, importStatementsThatMightBeRemoved.get(i) + System.lineSeparator());
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
        System.out.printf("Before inserting at %d: %s%n", startLine, mergedFileContentsLines);
      }
      mergedFileContentsLines.addAll(startLine, importStatementsThatMightBeRemoved);
      if (verbose) {
        System.out.printf("After inserting: %s%n", mergedFileContentsLines);
      }
      startLineOffset += h.lineChangeSize();
    }

    mergedFileContentsLines =
        CollectionsPlume.filter(
            mergedFileContentsLines,
            (String line) -> {
              final String imported = getImportedType(line);
              return imported == null || !forbiddenImports.contains(imported);
            });

    String mergedFileContents = String.join("", mergedFileContentsLines);
    if (verbose) {
      System.out.println("mergedFileContents=" + mergedFileContents);
    }

    String gjfFileContents;
    try {
      gjfFileContents = RemoveUnusedImports.removeUnusedImports(mergedFileContents);
    } catch (FormatterException e) {
      if (verbose) {
        System.out.printf("gjf threw FormatterException: %s%n", e.getMessage());
      }
      gjfFileContents = mergedFileContents;
    }

    mergeState.setConflictedFile(new ConflictedFile(gjfFileContents, false));
  }

  /**
   * Returns true if the given merge conflict is not an import block.
   *
   * @param mc a merge conflict
   * @return true if the argument has with non-<code>import</code> lines
   */
  static boolean isOutsideImports(MergeConflict mc) {
    List<String> base = mc.base();
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
  static boolean isImportBlock(List<String> lines) {
    return lines.stream().allMatch(JavaImportsMerger::isImportBlockLine);
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
    List<String> leftLines = mc.left();
    List<String> rightLines = mc.right();
    int leftLen = leftLines.size();
    int rightLen = leftLines.size();
    if (leftLen > rightLen
        && CollectionsPlume.isSubsequenceMaybeNonContiguous(leftLines, rightLines)) {
      return new CommonLines(leftLines);
    } else if (rightLen > leftLen
        && CollectionsPlume.isSubsequenceMaybeNonContiguous(rightLines, leftLines)) {
      return new CommonLines(rightLines);
    }

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
      result.addAll(
          mergeImportsAndSpaces(
              leftLines.subList(leftIndex, leftCommentIndex),
              rightLines.subList(rightIndex, rightCommentIndex)));
      result.add(comment);
      leftIndex = leftCommentIndex + 1;
      rightIndex = rightCommentIndex + 1;
    }
    result.addAll(
        mergeImportsAndSpaces(
            leftLines.subList(leftIndex, leftLines.size()),
            rightLines.subList(rightIndex, rightLines.size())));

    return new CommonLines(result);
  }

  /**
   * Merge a sub-part of an import merge conflict. The sub-part consists only of import statements
   * and blank lines.
   *
   * <p>One of the two arguments is returned if it is a supersequence of the other. Otherwise, the
   * import statements are sorted and blank lines are not retained, except at the beginning and end.
   *
   * @param leftLines the lines on the left of the merge conflict
   * @param rightLines the lines on the right of the merge conflict
   * @return the merged lines
   */
  private static List<String> mergeImportsAndSpaces(
      List<String> leftLines, List<String> rightLines) {

    // `leftLines` and `rightLines` are portions of a merge conflict, so they could be equal.
    if (leftLines.equals(rightLines)) {
      return leftLines;
    }
    if (leftLines.isEmpty()) {
      return rightLines;
    }
    if (rightLines.isEmpty()) {
      return leftLines;
    }
    int leftLen = leftLines.size();
    int rightLen = leftLines.size();
    if (leftLen > rightLen
        && CollectionsPlume.isSubsequenceMaybeNonContiguous(leftLines, rightLines)) {
      return leftLines;
    } else if (rightLen > leftLen
        && CollectionsPlume.isSubsequenceMaybeNonContiguous(rightLines, leftLines)) {
      return rightLines;
    }

    // If non-null, the empty first line.
    String firstLineEmpty =
        (!leftLines.isEmpty() && isBlankLine(leftLines.get(0)))
            ? leftLines.get(0)
            : ((!rightLines.isEmpty() && isBlankLine(rightLines.get(0)))
                ? rightLines.get(0)
                : null);
    String lastLineEmpty =
        (!leftLines.isEmpty() && isBlankLine(leftLines.get(leftLines.size() - 1)))
            ? leftLines.get(leftLines.size() - 1)
            : ((!rightLines.isEmpty() && isBlankLine(rightLines.get(rightLines.size() - 1)))
                ? rightLines.get(rightLines.size() - 1)
                : null);
    SortedSet<String> imports = new TreeSet<>();
    imports.addAll(leftLines);
    imports.addAll(rightLines);
    List<String> result = new ArrayList<>(imports.size() + 2);
    if (firstLineEmpty != null) {
      result.add(firstLineEmpty);
    }
    result.addAll(CollectionsPlume.filter(imports, Predicate.not(JavaImportsMerger::isBlankLine)));
    if (lastLineEmpty != null) {
      result.add(lastLineEmpty);
    }
    return result;
  }

  /** A pattern that matches a string consisting only of whitespace. */
  private static Pattern whitespacePattern = Pattern.compile("\s*\\R*");

  /**
   * Returns true if the given string is a blank line.
   *
   * @param line a string
   * @return true if the given string is a blank line
   */
  private static boolean isBlankLine(String line) {
    return whitespacePattern.matcher(line).matches();
  }

  // TODO: Should this forbid leading whitespace, to avoid false positive matches?
  /**
   * A pattern that matches an import line in Java code. Does not match import lines with a trailing
   * comment.
   */
  private static Pattern importPattern = Pattern.compile("\\s*import\\s.*;\\R?");

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
  static List<String> commentLines(List<String> lines) {
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

  /**
   * Returns a pair of (deleted imports, inserted imports).
   *
   * @param javaCode1 the first Java program
   * @param javaCode2 the second Java program
   * @return the deleted and changed imports, each as a list of dotted identifiers
   */
  static IPair<List<String>, List<String>> changedImports(String javaCode1, String javaCode2) {
    // This implementation is hacky in that it works textually instead of parsing the Java code.
    // So, it will not handle bizarrely formatted code.
    LinkedList<Diff> diffs = DmpLibrary.diffByLines(javaCode1, javaCode2);
    List<String> inserted = new ArrayList<>();
    List<String> deleted = new ArrayList<>();
    for (Diff diff : diffs) {
      switch (diff.operation) {
        case INSERT -> {
          for (String insertedLine : StringsPlume.splitLines(diff.text)) {
            String imported = getImportedType(insertedLine);
            if (imported != null) {
              inserted.add(imported);
            }
          }
        }
        case DELETE -> {
          for (String deletedLine : StringsPlume.splitLines(diff.text)) {
            String imported = getImportedType(deletedLine);
            if (imported != null) {
              deleted.add(imported);
            }
          }
        }
        case EQUAL -> {
          // Nothing to do
        }
      }
    }
    return IPair.of(deleted, inserted);
  }

  /**
   * Returns a list of deleted imports that were also inserted with a different prefix. For example,
   * if "import a.b.c.Foo;" was deleted, and "import d.e.Foo" was added, the result contains
   * "a.b.c.Foo". These should not be re-inserted.
   *
   * @param javaCode1 the first Java program
   * @param javaCode2 the second Java program
   * @return the renamed imports, as a list of dotted identifiers (for their old names)
   */
  static List<String> renamedImports(String javaCode1, String javaCode2) {
    IPair<List<String>, List<String>> changedImports = changedImports(javaCode1, javaCode2);
    List<String> deleted = changedImports.first;
    List<String> inserted = changedImports.second;
    if (deleted.isEmpty() || inserted.isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> insertedIdentifiers =
        new HashSet<>(CollectionsPlume.mapList(JavaImportsMerger::lastIdentifier, inserted));
    List<String> result = new ArrayList<>();
    for (String dotted : deleted) {
      String deletedIdentifier = lastIdentifier(dotted);
      if (insertedIdentifiers.contains(deletedIdentifier)) {
        result.add(dotted);
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns the text after the last period in the input.
   *
   * @param dottedIdentifiers dotted identifiers
   * @return the last of the dotted identifiers
   */
  private static String lastIdentifier(String dottedIdentifiers) {
    int dotPos = dottedIdentifiers.lastIndexOf(".");
    if (dotPos == -1) {
      return dottedIdentifiers;
    } else {
      return dottedIdentifiers.substring(dotPos + 1);
    }
  }

  /** Matches an import line in a Java program. */
  private static @Regex(1) Pattern importLine =
      Pattern.compile(
          "^\\s*+import\\s++(?:static\\s++)?+("
              + JavaAnnotationsMerger.javaDottedIdentifiersRegex
              + ")\\s*+;\\s*+\\R?$");

  /** Matches horizontal whitespace. */
  private static Pattern horizontalSpace = Pattern.compile("\\s+");

  /**
   * If the given line is an import statement, then return what is being imported, as a dotted
   * identifier.
   *
   * @param line a line of code
   * @return what is being imported, or null if the line isn't an import statement
   */
  static @Nullable String getImportedType(String line) {
    @Regex(1) Matcher m = importLine.matcher(line);
    if (m.matches()) {
      @SuppressWarnings("nullness:assignment") // this ought to type-check
      @NonNull String withSpaces = m.group(1);
      String withoutSpaces = horizontalSpace.matcher(withSpaces).replaceAll("");
      return withoutSpaces;
    } else {
      return null;
    }
  }
}
