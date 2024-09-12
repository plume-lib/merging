package org.plumelib.merging;

import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.RemoveUnusedImports;
import java.nio.file.Path;
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
import org.plumelib.util.FilesPlume;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/**
 * This class resolves conflicts in {@code import} statements and re-inserts any {@code import}
 * statements that were removed by a merge but are needed for compilation to succeed.
 */
public class JavaImportsMerger extends Merger {

  /**
   * Creates a JavaImportsMerger.
   *
   * @param verbose if true, output diagnostic information
   */
  public JavaImportsMerger(boolean verbose) {
    super(verbose, true);
  }

  @Override
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf, MergeState mergeState) {

    List<MergeConflict> mcs = cf.mergeConflicts();

    // Proceed only if all the merge conflicts (if any) are within the imports.
    if (CollectionsPlume.anyMatch(mcs, JavaImportsMerger::isOutsideImports)) {
      return null;
    }

    // There are no merge conflicts except possibly within the imports.

    // If an import merge conflict has different comments within it, give up.
    if (!CollectionsPlume.allMatch(mcs, MergeConflict::sameCommentLines)) {
      return null;
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
    if (verbose) {
      System.out.printf("mergeState=%s%n", mergeState);
    }
    forbiddenImports.addAll(renamedImports(baseContents, leftContents));
    forbiddenImports.addAll(renamedImports(baseContents, rightContents));

    // Wherever git produced a conflict, replace it by a CommonLines.
    List<CommonLines> cls = new ArrayList<>();
    assert cf.hunks() != null : "@AssumeAssertion(nullness): precondition of resolveConflicts";
    for (ConflictElement ce : cf.hunks()) {
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
    Diff3File diff3file;
    try {
      diff3file =
          Diff3File.from3paths(mergeState.leftPath, mergeState.basePath, mergeState.rightPath);
    } catch (Diff3ParseException e) {
      Main.exitErroneously(e.getMessage());
      throw new Error("unreachable");
    }

    // Iterate through the diffs, adding lines to the file.
    List<String> mergedFileContentsLines;
    try {
      mergedFileContentsLines = insertRemovedImports(CommonLines.toLines(cls), diff3file);
    } catch (Throwable t) {
      System.out.printf(
          "Problem with conflicted file (hasTrivalConflict=%s):%n", cf.hasTrivalConflict());
      System.out.println("On disk:");
      System.out.println(FilesPlume.readString(cf.path));
      System.out.println("In data structure:");
      System.out.println(cf.fileContents());
      System.out.println(cf);
      throw t;
    }

    if (verbose) {
      System.out.println("forbiddenImports=" + forbiddenImports);
    }
    if (!forbiddenImports.isEmpty()) {
      if (verbose) {
        System.out.printf("baseContents = %s%nend of baseContents.%n", baseContents);
        System.out.printf("leftContents = %s%nend of leftContents.%n", leftContents);
        System.out.printf("rightContents = %s%nend of rightContents.%n", rightContents);
      }
      mergedFileContentsLines =
          CollectionsPlume.filter(
              mergedFileContentsLines,
              (String line) -> {
                final String imported = getImportedType(line);
                return imported == null || !forbiddenImports.contains(imported);
              });
      if (verbose) {
        System.out.println("without forbidden imports = " + mergedFileContentsLines);
      }
    }

    String mergedFileContents = String.join("", mergedFileContentsLines);
    if (verbose) {
      System.out.println("input to gjf = " + mergedFileContents);
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

    return new ConflictedFile(
        gjfFileContents, false, Path.of("google-java-format on merged version of " + cf.path));
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
   * satisfies {@link JavaLibrary#isImportBlockLine}.
   *
   * @param lines some lines of code
   * @return true if the argument is an import block
   */
  static boolean isImportBlock(List<String> lines) {
    return CollectionsPlume.allMatch(lines, JavaLibrary::isImportBlockLine);
  }

  /**
   * For each diff in imports, insert the differing imports into the file.
   *
   * <p>This results in a file that contains (nearly) every import from both parents. A subsequent
   * pass will remove the unnecessary ones.
   *
   * @param fileLines the lines of the file
   * @param diff3file the diffs
   * @return the lines of the file, after inserting more import statements
   */
  List<String> insertRemovedImports(List<String> fileLines, Diff3File diff3file) {

    // Find the first and last import lines in the file.
    // These are 1-based, so the first line in the file is line 1; therefore, these cannot be used
    // for indexing into a list of lines.
    int firstImportLineInFile = -1;
    int lastImportLineInFile = -1;
    Set<String> importLinesInFile = new HashSet<>();
    for (int i = 0; i < fileLines.size(); i++) {
      String line = fileLines.get(i);
      if (JavaLibrary.isImportStatement(line)) {
        importLinesInFile.add(line);
        if (firstImportLineInFile == -1) {
          firstImportLineInFile = i + 1;
        }
        lastImportLineInFile = i + 1;
      }
    }

    if (verbose) {
      System.out.printf("insertRemovedImports: diff3file=%s%n", diff3file);
    }

    // TODO: Carefully document the value of this variable.
    int startLineOffset = 0;
    for (Diff3Hunk h : diff3file.contents()) {
      if (verbose) {
        System.out.printf("h=%s%n", h);
      }
      List<String> hunkSection2Lines = h.section2().lines();
      List<String> importStatementsThatAreRemoved =
          CollectionsPlume.filter(hunkSection2Lines, JavaLibrary::isImportStatement);
      // Do not reinsert deleted wildcard imports.
      importStatementsThatAreRemoved.removeIf(s -> s.endsWith("*;"));
      for (int i = 0; i < importStatementsThatAreRemoved.size(); i++) {
        importStatementsThatAreRemoved.set(
            i, importStatementsThatAreRemoved.get(i) + System.lineSeparator());
      }
      importStatementsThatAreRemoved.removeAll(importLinesInFile);

      if (importStatementsThatAreRemoved.isEmpty()) {
        // Merging this hunk did not remove any import statements.
        if (verbose) {
          System.out.printf(
              "no import statements removed, old Startlineoffset=%d,"
                  + " h.lineChangeSize()=%d, new startlineoffset=%d%n",
              startLineOffset, h.lineChangeSize(), startLineOffset + h.lineChangeSize());
        }
        startLineOffset += h.lineChangeSize();
        continue;
      }
      if (verbose) {
        System.out.printf("importStatementsThatAreRemoved=%s%n", importStatementsThatAreRemoved);
      }

      // The 3 sections are (in order) left, base, right.
      // We use `edit` only to determine where to insert the import statements (and how to adjust
      // the startLineOffset).
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
      if (verbose) {
        System.out.printf("edit=%s%n", edit);
      }

      int startLine;
      switch (edit.command().kind()) {
        case APPEND:
          startLine = edit.command().startLine();
          break;
        case CHANGE:
          // Find the first line that is an import, and insert immediately after it.
          List<String> editLines = edit.lines();
          int importLine = JavaLibrary.firstImportStatement(editLines);
          if (importLine != -1) {
            startLine = edit.command().startLine() + importLine;
          } else {
            int packageLine = JavaLibrary.firstPackageStatement(editLines);
            if (packageLine != -1) {
              startLine = edit.command().startLine() + packageLine + 1;
            } else {
              int nonCommentLine = JavaLibrary.firstOutsideCommentLine(editLines);
              startLine = edit.command().startLine() + nonCommentLine;
            }
          }
          break;
        default:
          throw new Error("Unhandled kind: " + edit.command().kind());
      }
      if (verbose) {
        System.out.printf(
            "from case %s: old startLine = %s, startLineOffset = %s, new startLine = %s%n",
            edit.command().kind(), startLine, startLineOffset, startLine + startLineOffset);
      }
      startLine += startLineOffset;
      if (startLine < 0 || startLine >= fileLines.size()) {
        System.out.printf("problem in insertRemovedImports.%n");
        System.out.printf("diff3file = %s%n", diff3file);
        System.out.printf("startLine = %s%n", startLine);
        System.out.printf("startLineOffset = %s%n", startLineOffset);
        System.out.printf("hunk = %s%n", h);
        System.out.printf("edit = %s%n", edit);
        System.out.printf("fileLines = %s%n", fileLines);
      }
      if (verbose) {
        System.out.printf(
            "Before inserting import lines at line %d (startLineOffset=%d): %s%n",
            startLine, startLineOffset, fileLines);
      }
      // TODO: I probably need to adjust lastImportLineInFile based on previous insertions.
      if (firstImportLineInFile != -1 && lastImportLineInFile != -1) {
        startLine = Math.max(startLine, firstImportLineInFile);
        startLine = Math.min(startLine, lastImportLineInFile + 1);
        if (verbose) {
          System.out.printf("Adjusted startLine: %d%n", startLine);
        }
      }
      fileLines.addAll(startLine - 1, importStatementsThatAreRemoved);
      // TODO: Also adjust startLineOffset to account for the inserted lines?
      startLineOffset += h.lineChangeSize();
      if (verbose) {
        System.out.printf(
            "After inserting import lines at line %d (index %d)"
                + " (lineChangeSize=%d, startLineOffset=%d): %s%n",
            startLine, startLine - 1, h.lineChangeSize(), startLineOffset, fileLines);
      }
    }

    if (verbose) {
      System.out.printf("insertRemovedImports returning: %s%n", fileLines);
    }

    return fileLines;
  }

  /**
   * Given a merge conflict that is an import block, merge it, retaining comments but not caring
   * about whitespace.
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

    List<String> leftComments = JavaLibrary.commentLines(mc.left());
    assert leftComments.equals(JavaLibrary.commentLines(mc.right()));

    List<String> result = new ArrayList<>();

    int leftIndex = 0; // the index after the most recently found comment
    int rightIndex = 0; // the index after the most recently found comment
    for (String comment : leftComments) {
      int leftCommentIndex = CollectionsPlume.indexOf(leftLines, comment, leftIndex);
      int rightCommentIndex = CollectionsPlume.indexOf(rightLines, comment, rightIndex);
      if (leftCommentIndex == -1 || rightCommentIndex == -1) {
        Main.exitErroneously("didn't find comment: " + comment);
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

    // `leftLines` and `rightLines` are *portions* of a merge conflict, so they could be equal.
    int leftLen = leftLines.size();
    int rightLen = rightLines.size();
    if (leftLen == rightLen) {
      if (leftLines.equals(rightLines)) {
        return leftLines;
      }
    } else if (leftLen > rightLen) {
      if (rightLen == 0
          || CollectionsPlume.isSubsequenceMaybeNonContiguous(leftLines, rightLines)) {
        return leftLines;
      }
    } else if (rightLen > leftLen) {
      if (leftLen == 0 || CollectionsPlume.isSubsequenceMaybeNonContiguous(rightLines, leftLines)) {
        return rightLines;
      }
    }

    assert !leftLines.isEmpty() : "leftLines=" + leftLines + ", rightLines=" + rightLines;
    assert !rightLines.isEmpty() : "leftLines=" + leftLines + ", rightLines=" + rightLines;
    // If non-null, the empty first line.
    String firstLineEmpty =
        JavaLibrary.isBlankLine(leftLines.get(0))
            ? leftLines.get(0)
            : (JavaLibrary.isBlankLine(rightLines.get(0)) ? rightLines.get(0) : null);
    String lastLineEmpty =
        JavaLibrary.isBlankLine(leftLines.get(leftLines.size() - 1))
            ? leftLines.get(leftLines.size() - 1)
            : (JavaLibrary.isBlankLine(rightLines.get(rightLines.size() - 1))
                ? rightLines.get(rightLines.size() - 1)
                : null);
    SortedSet<String> imports = new TreeSet<>();
    imports.addAll(leftLines);
    imports.addAll(rightLines);
    List<String> result = new ArrayList<>(imports.size() + 2);
    if (firstLineEmpty != null) {
      result.add(firstLineEmpty);
    }
    result.addAll(CollectionsPlume.filter(imports, Predicate.not(JavaLibrary::isBlankLine)));
    if (lastLineEmpty != null) {
      result.add(lastLineEmpty);
    }
    return result;
  }

  // //////////////////////////////////////////////////////////////////////

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
    HashSet<String> intersection = new HashSet<>(deleted);
    intersection.retainAll(inserted);
    deleted.removeAll(intersection);
    inserted.removeAll(intersection);
    return IPair.of(deleted, inserted);
  }

  /**
   * Returns a list of deleted imports that were also inserted with a different prefix. For example,
   * if "import a.b.c.Foo" was deleted and "import d.e.Foo" was added, then the result contains
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
    // System.out.printf("deleted imports =  %s%n", deleted);
    // System.out.printf("inserted imports = %s%n", inserted);
    Set<String> insertedIdentifiers =
        new HashSet<>(CollectionsPlume.mapList(JavaImportsMerger::lastIdentifier, inserted));
    List<String> result = new ArrayList<>();
    for (String del : deleted) {
      String deletedIdentifier = lastIdentifier(del);
      if (insertedIdentifiers.contains(deletedIdentifier)) {
        result.add(del);
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
   * If the given line is an import statement, then return what is being imported, as a
   * fully-qualified dotted identifier.
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
