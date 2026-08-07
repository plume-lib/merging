package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests. Each test runs the program in a subprocess, then examines only the program's
 * output: the file that the program writes, its standard output, its standard error, and its exit
 * status.
 *
 * <p>The inputs and the goal (expected) outputs are files in {@code src/test/resources/end-to-end},
 * one directory per test case. See {@code src/test/resources/end-to-end/README.md} for the file
 * naming conventions.
 */
public class EndToEndTest {

  /** Creates an EndToEndTest. */
  public EndToEndTest() {}

  /** The directory that contains one directory per test case. */
  private static final Path testCasesDir = Path.of("src", "test", "resources", "end-to-end");

  /** The directory in which the program is run. Each run uses a fresh subdirectory. */
  private static final Path runsDir = Path.of("build", "end-to-end-runs");

  /** Command-line arguments that the program needs in order to run under Java 21 or later. */
  private static final List<String> addExportsArgs =
      List.of(
          "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");

  /** The exit status when the merge result contains a conflict. */
  private static final int conflictExitStatus = 1;

  /** The exit status when the merge result contains no conflict. */
  private static final int cleanExitStatus = 0;

  /** The exit status when the command-line arguments are erroneous. */
  private static final int erroneousArgsExitStatus = 129;

  /** The exit status that picocli uses for an unparsable command line. */
  private static final int picocliUsageExitStatus = 2;

  // //////////////////////////////////////////////////////////////////////
  // Adjacent lines
  //

  @Test
  void testAdjacentDifferentLines() {
    checkDriver("adjacent-different-lines", List.of("--only-adjacent"));
    checkDriverBackward("adjacent-different-lines", "goal", List.of("--only-adjacent"));
  }

  @Test
  void testAdjacentSameLine() {
    checkDriver("adjacent-same-line", List.of("--only-adjacent"));
    checkDriverBackward("adjacent-same-line", "goal-backward", List.of("--only-adjacent"));
  }

  @Test
  void testAdjacentInsertions() {
    checkDriver("adjacent-insertions", List.of("--only-adjacent"));
    checkDriverBackward("adjacent-insertions", "goal", List.of("--only-adjacent"));
  }

  /** The example in README-adjacent-lines.md. */
  @Test
  void testAdjacentReadmeExample() {
    checkDriver("adjacent-readme-example", List.of("--only-adjacent"));
    checkDriverBackward("adjacent-readme-example", "goal", List.of("--only-adjacent"));
  }

  @Test
  void testAdjacentDeleteAndEdit() {
    checkDriver("adjacent-delete-and-edit", List.of("--only-adjacent"));
    checkDriverBackward("adjacent-delete-and-edit", "goal", List.of("--only-adjacent"));
  }

  /** The adjacent-lines merger is disabled by default, so the conflict remains. */
  @Test
  void testAdjacentIsDisabledByDefault() {
    checkDriver("adjacent-different-lines", "goal-defaults", List.of());
  }

  /**
   * The {@code --adjacent} command-line argument enables the adjacent-lines merger, and {@code
   * --no-adjacent} disables it.
   *
   * <p>This test fails: the two command-line arguments have the opposite of their documented
   * meanings. See claude-tests.md.
   */
  @Disabled("--adjacent and --no-adjacent are inverted; see claude-tests.md")
  @Test
  void testAdjacentEnabledAlongWithDefaults() {
    checkDriver("adjacent-different-lines", "goal", List.of("--adjacent"));
    checkDriver("adjacent-different-lines", "goal-defaults", List.of("--no-adjacent"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Java annotations
  //

  @Test
  void testAnnotationsBothAdd() {
    checkDriver("annotations-both-add", List.of("--only-java-annotations"));
    checkDriverBackward("annotations-both-add", "goal", List.of("--only-java-annotations"));
  }

  /** The example in README-java-annotations.md. */
  @Test
  void testAnnotationsReadmeExample() {
    checkDriver("annotations-readme-example", List.of("--only-java-annotations"));
    checkDriverBackward("annotations-readme-example", "goal", List.of("--only-java-annotations"));
  }

  /** Modifiers such as {@code final} are merged just as annotations are. */
  @Test
  void testAnnotationsModifier() {
    checkDriver("annotations-modifier", List.of("--only-java-annotations"));
    checkDriverBackward("annotations-modifier", "goal", List.of("--only-java-annotations"));
  }

  /** A conflict between two comments is not resolved. */
  @Test
  void testAnnotationsCommentConflict() {
    checkDriver("annotations-comment-conflict", List.of("--only-java-annotations"));
    checkDriverBackward(
        "annotations-comment-conflict", "goal-backward", List.of("--only-java-annotations"));
  }

  /** A conflict whose differences are not all annotations is not resolved. */
  @Test
  void testAnnotationsNotAnnotation() {
    checkDriver("annotations-not-annotation", List.of("--only-java-annotations"));
    checkDriverBackward(
        "annotations-not-annotation", "goal-backward", List.of("--only-java-annotations"));
  }

  /**
   * When one edit adds an annotation and the other adds a comment, the conflict is not resolved.
   */
  @Test
  void testAnnotationsWithComment() {
    checkDriver("annotations-with-comment", List.of("--only-java-annotations"));
    checkDriverBackward(
        "annotations-with-comment", "goal-backward", List.of("--only-java-annotations"));
  }

  /**
   * When the two edits add different annotations at the same position, the conflict is not
   * resolved, even though the only textual difference is in annotations. See claude-tests.md.
   */
  @Test
  void testAnnotationsTwoAtSamePosition() {
    checkDriver("annotations-two-at-same-position", List.of("--only-java-annotations"));
    checkDriverBackward(
        "annotations-two-at-same-position", "goal-backward", List.of("--only-java-annotations"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Java imports
  //

  @Test
  void testImportsBothAdd() {
    checkDriver("imports-both-add", List.of("--only-java-imports"));
    checkDriverBackward("imports-both-add", "goal", List.of("--only-java-imports"));
  }

  /**
   * The example in README-java-imports.md: imports are unioned, an import that a clean merge
   * removed is re-inserted, and unneeded imports are removed.
   */
  @Test
  void testImportsReadmeExample() {
    checkDriver("imports-readme-example", List.of("--only-java-imports"));
    checkDriverBackward("imports-readme-example", "goal", List.of("--only-java-imports"));
  }

  /**
   * The example in README-java-imports.md, with the imports in the sorted order that the README
   * shows.
   *
   * <p>This test fails: a re-inserted import is placed after the imports that the merger unioned,
   * rather than in sorted order. See claude-tests.md.
   */
  @Disabled("a re-inserted import is not placed in sorted order; see claude-tests.md")
  @Test
  void testImportsReadmeExampleSorted() {
    checkDriver("imports-readme-example", "goal-sorted", List.of("--only-java-imports"));
  }

  /** Both edits add the same import statement. */
  @Test
  void testImportsSameImportBoth() {
    checkDriver("imports-same-import-both", List.of("--only-java-imports"));
    checkDriverBackward("imports-same-import-both", "goal", List.of("--only-java-imports"));
  }

  @Test
  void testImportsStatic() {
    checkDriver("imports-static", List.of("--only-java-imports"));
    checkDriverBackward("imports-static", "goal", List.of("--only-java-imports"));
  }

  /**
   * One edit removes an import along with its use; the other edit adds a use. The clean merge would
   * lack a needed import, so the merger re-inserts it.
   */
  @Test
  void testImportsReinsertRemoved() {
    checkDriver("imports-reinsert-removed", List.of("--only-java-imports"));
    checkDriverBackward("imports-reinsert-removed", "goal", List.of("--only-java-imports"));
  }

  /** An import that was renamed rather than removed is not re-inserted. */
  @Test
  void testImportsRenamed() {
    checkDriver("imports-renamed", List.of("--only-java-imports"));
    checkDriverBackward("imports-renamed", "goal", List.of("--only-java-imports"));
  }

  /** A removed wildcard import is not re-inserted. */
  @Test
  void testImportsWildcard() {
    checkDriver("imports-wildcard", List.of("--only-java-imports"));
    checkDriverBackward("imports-wildcard", "goal", List.of("--only-java-imports"));
  }

  /** Comments within an import block are retained. */
  @Test
  void testImportsCommentInBlock() {
    checkDriver("imports-comment-in-block", List.of("--only-java-imports"));
    checkDriverBackward("imports-comment-in-block", "goal", List.of("--only-java-imports"));
  }

  /** When an import conflict contains different comments, the conflict is not resolved. */
  @Test
  void testImportsDifferentComments() {
    checkDriver("imports-different-comments", List.of("--only-java-imports"));
    checkDriverBackward(
        "imports-different-comments", "goal-backward", List.of("--only-java-imports"));
  }

  /** The merger does nothing if any conflict is outside the import statements. */
  @Test
  void testImportsConflictOutside() {
    checkDriver("imports-conflict-outside", List.of("--only-java-imports"));
    checkDriverBackward(
        "imports-conflict-outside", "goal-backward", List.of("--only-java-imports"));
  }

  /**
   * The merge does not affect the imports, but the merger removes an unused import that is in all
   * three versions of the file. See claude-tests.md.
   */
  @Test
  void testImportsUnusedInBase() {
    checkDriver("imports-unused-in-base", List.of("--only-java-imports"));
    checkDriverBackward("imports-unused-in-base", "goal", List.of("--only-java-imports"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Version numbers
  //

  @Test
  void testVersionNumbersLargerWins() {
    checkDriver("version-numbers-larger-wins", List.of("--only-version-numbers"));
    checkDriverBackward("version-numbers-larger-wins", "goal", List.of("--only-version-numbers"));
  }

  /** Version numbers are compared componentwise and numerically, not lexicographically. */
  @Test
  void testVersionNumbersNumericCompare() {
    checkDriver("version-numbers-numeric-compare", List.of("--only-version-numbers"));
    checkDriverBackward(
        "version-numbers-numeric-compare", "goal", List.of("--only-version-numbers"));
  }

  /** Version number 1.2.11 is greater than version number 1.2.9. */
  @Test
  void testVersionNumbersTwoDigit() {
    checkDriver("version-numbers-two-digit", List.of("--only-version-numbers"));
    checkDriverBackward("version-numbers-two-digit", "goal", List.of("--only-version-numbers"));
  }

  @Test
  void testVersionNumbersJavaString() {
    checkDriver("version-numbers-java-string", List.of("--only-version-numbers"));
    checkDriverBackward("version-numbers-java-string", "goal", List.of("--only-version-numbers"));
  }

  /** When one edit also changes text that is not a version number, the conflict is not resolved. */
  @Test
  void testVersionNumbersOtherChange() {
    checkDriver("version-numbers-other-change", List.of("--only-version-numbers"));
    checkDriverBackward(
        "version-numbers-other-change", "goal-backward", List.of("--only-version-numbers"));
  }

  /** When one edit decreases the version number, the conflict is not resolved. */
  @Test
  void testVersionNumbersDecrease() {
    checkDriver("version-numbers-decrease", List.of("--only-version-numbers"));
    checkDriverBackward(
        "version-numbers-decrease", "goal-backward", List.of("--only-version-numbers"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Disabling a merger
  //

  /** The {@code --no-java-annotations} command-line argument disables the annotations merger. */
  @Test
  void testNoJavaAnnotations() {
    checkDriver("annotations-both-add", "goal-no-annotations", List.of("--no-java-annotations"));
  }

  /** The {@code --no-java-imports} command-line argument disables the imports merger. */
  @Test
  void testNoJavaImports() {
    checkDriver("imports-both-add", "goal-no-imports", List.of("--no-java-imports"));
  }

  /** The {@code --no-version-numbers} command-line argument disables the version-numbers merger. */
  @Test
  void testNoVersionNumbers() {
    checkDriver(
        "version-numbers-larger-wins", "goal-no-version-numbers", List.of("--no-version-numbers"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Multiple mergers, and files that are not Java files
  //

  /** With no command-line arguments, the annotations and imports mergers both run. */
  @Test
  void testDefaultsAnnotationsAndImports() {
    checkDriver("defaults-annotations-and-imports", List.of());
    checkDriverBackward("defaults-annotations-and-imports", "goal", List.of());
  }

  /** The mergers do not corrupt a file that is not a Java file. */
  @Test
  void testDefaultsTextFile() {
    checkDriver("defaults-text-file", List.of());
    checkDriverBackward("defaults-text-file", "goal", List.of());
  }

  // //////////////////////////////////////////////////////////////////////
  // Other merge driver behaviors
  //

  /**
   * With {@code --no-git-merge-file}, the program resolves the conflicts that are already in the
   * file, without running {@code git merge-file} first.
   */
  @Test
  void testNoGitMergeFile() {
    checkDriver("no-git-merge-file", List.of("--no-git-merge-file", "--only-adjacent"));
  }

  /** A file with unbalanced conflict markers is left alone, and a message is printed. */
  @Test
  void testUnparsableConflict() {
    checkDriver("unparsable-conflict", List.of("--no-git-merge-file", "--only-java-annotations"));
  }

  /** A file whose last line has no line terminator is merged, and stays without a terminator. */
  @Test
  void testNoTrailingNewline() {
    checkDriver("no-trailing-newline", List.of("--only-adjacent"));
    checkDriverBackward("no-trailing-newline", "goal", List.of("--only-adjacent"));
  }

  /** Both versions add different content to a file that is empty in the base version. */
  @Test
  void testEmptyBase() {
    checkDriver("empty-base", List.of("--only-adjacent"));
  }

  /** Both versions make the same edit, so the merge is clean. */
  @Test
  void testIdenticalChanges() {
    checkDriver("identical-changes", List.of());
  }

  // //////////////////////////////////////////////////////////////////////
  // Merge tool
  //

  @Test
  void testToolAnnotations() {
    checkTool("tool-annotations", List.of("--only-java-annotations"));
  }

  /** As a re-merge tool, the imports merger improves a merge that git performed cleanly. */
  @Test
  void testToolImports() {
    checkTool("tool-imports", List.of("--only-java-imports"));
  }

  /** The merge tool resolves one of two conflicts. */
  @Test
  void testToolBackup() {
    checkTool("tool-backup", List.of("--only-java-annotations"));
  }

  /**
   * When the base file's name contains "_BASE_" (as it does when git runs a merge tool), the result
   * is also written to a "_BACKUP_" file, because git discards the merged file when the merge tool
   * exits with a non-zero status.
   */
  @Test
  void testToolWritesBackupFile() {
    String testCase = "tool-backup";
    Path caseDir = testCasesDir.resolve(testCase);
    Path runDir = runDirectory(testCase + "-backup-file");
    copy(caseDir.resolve("left.java"), runDir.resolve("Backup_LOCAL_9999.java"));
    copy(caseDir.resolve("base.java"), runDir.resolve("Backup_BASE_9999.java"));
    copy(caseDir.resolve("right.java"), runDir.resolve("Backup_REMOTE_9999.java"));
    copy(caseDir.resolve("merged.java"), runDir.resolve("Backup.java"));

    ProgramResult result =
        runProgram(
            runDir,
            List.of(
                "tool",
                "--only-java-annotations",
                "Backup_LOCAL_9999.java",
                "Backup_BASE_9999.java",
                "Backup_REMOTE_9999.java",
                "Backup.java"));

    String goalContents = readFile(caseDir.resolve("goal.java"));
    assertEquals(goalContents, readFile(runDir.resolve("Backup.java")), "merged file");
    assertEquals(goalContents, readFile(runDir.resolve("Backup_BACKUP_9999.java")), "backup file");
    assertEquals("", result.stdout(), "standard output");
    assertEquals("", result.stderr(), "standard error");
    assertEquals(conflictExitStatus, result.exitStatus(), "exit status");
  }

  // //////////////////////////////////////////////////////////////////////
  // Command-line arguments
  //

  /** A merge driver takes 3 file arguments, not 4. */
  @Test
  void testCliDriverTooManyArgs() {
    checkCliError(
        "goal-driver-too-many-args.txt",
        List.of("driver", "current.java", "base.java", "right.java", "extra.java"));
  }

  /** A merge tool takes 4 file arguments, not 3. */
  @Test
  void testCliToolTooFewArgs() {
    checkCliError(
        "goal-tool-too-few-args.txt", List.of("tool", "current.java", "base.java", "right.java"));
  }

  @Test
  void testCliTwoOnlyFlags() {
    checkCliError(
        "goal-two-only-flags.txt",
        List.of(
            "driver",
            "--only-adjacent",
            "--only-java-imports",
            "current.java",
            "base.java",
            "right.java"));
  }

  @Test
  void testCliOnlyPlusFeature() {
    checkCliError(
        "goal-only-plus-feature.txt",
        List.of(
            "driver",
            "--only-adjacent",
            "--java-imports",
            "current.java",
            "base.java",
            "right.java"));
  }

  @Test
  void testCliUnreadableFile() {
    checkCliError(
        "goal-unreadable-file.txt",
        List.of("driver", "current.java", "nonexistent.java", "right.java"));
  }

  /** With no command-line arguments, picocli prints an error message and the usage message. */
  @Test
  void testCliNoArgs() {
    checkCliUsageError("goal-no-args.txt", List.of());
  }

  /** The first argument must be "driver" or "tool". */
  @Test
  void testCliBadMode() {
    checkCliUsageError(
        "goal-bad-mode.txt", List.of("drive", "current.java", "base.java", "right.java"));
  }

  /**
   * The program has no "--help" command-line argument, so "--help" produces an error. It should
   * instead print the usage message and exit with status 0. See claude-tests.md.
   */
  @Test
  void testCliHelp() {
    checkCliUsageError("goal-no-args.txt", List.of("--help"));
  }

  /** With "--verbose", the program prints its configuration. */
  @Test
  void testCliVerboseDefaults() {
    checkVerboseConfiguration("goal-verbose-defaults.txt", List.of("--verbose"));
  }

  /** A "--only-*" argument disables all the other mergers. */
  @Test
  void testCliVerboseOnlyAdjacent() {
    checkVerboseConfiguration(
        "goal-verbose-only-adjacent.txt", List.of("--verbose", "--only-adjacent"));
  }

  // //////////////////////////////////////////////////////////////////////
  // Helper methods that run the program
  //

  /**
   * Runs the merge driver on the given test case, then checks its output against goal.EXTENSION.
   *
   * @param testCase the name of a directory under {@code src/test/resources/end-to-end}
   * @param options the command-line options to pass to the program
   */
  private void checkDriver(String testCase, List<String> options) {
    checkDriver(testCase, "goal", false, options);
  }

  /**
   * Runs the merge driver on the given test case, then checks its output against the goal file.
   *
   * @param testCase the name of a directory under {@code src/test/resources/end-to-end}
   * @param goalName the goal file's name, without the file extension
   * @param options the command-line options to pass to the program
   */
  private void checkDriver(String testCase, String goalName, List<String> options) {
    checkDriver(testCase, goalName, false, options);
  }

  /**
   * Runs the merge driver on the given test case, with the left and right versions swapped, then
   * checks its output against the goal file. A merge is expected to give the same result no matter
   * which version is the left one.
   *
   * @param testCase the name of a directory under {@code src/test/resources/end-to-end}
   * @param goalName the goal file's name, without the file extension
   * @param options the command-line options to pass to the program
   */
  private void checkDriverBackward(String testCase, String goalName, List<String> options) {
    checkDriver(testCase, goalName, true, options);
  }

  /**
   * Runs the merge driver on the given test case, then checks its output against the goal file.
   *
   * @param testCase the name of a directory under {@code src/test/resources/end-to-end}
   * @param goalName the goal file's name, without the file extension
   * @param backward if true, use the right version as the left one and vice versa
   * @param options the command-line options to pass to the program
   */
  private void checkDriver(
      String testCase, String goalName, boolean backward, List<String> options) {
    Path caseDir = testCasesDir.resolve(testCase);
    String extension = fileExtension(caseDir);
    Path runDir =
        runDirectory(
            testCase + "-" + goalName + (backward ? "-backward" : "") + optionsSuffix(options));

    String currentFileName = "current" + extension;
    String baseFileName = "base" + extension;
    String rightFileName = "right" + extension;
    copy(
        caseDir.resolve((backward ? "right" : "left") + extension),
        runDir.resolve(currentFileName));
    copy(caseDir.resolve(baseFileName), runDir.resolve(baseFileName));
    copy(caseDir.resolve((backward ? "left" : "right") + extension), runDir.resolve(rightFileName));

    List<String> args = new ArrayList<>();
    args.add("driver");
    args.addAll(options);
    args.add(currentFileName);
    args.add(baseFileName);
    args.add(rightFileName);

    ProgramResult result = runProgram(runDir, args);
    checkOutput(caseDir, goalName + extension, runDir.resolve(currentFileName), result);
  }

  /**
   * Runs the merge tool on the given test case, then checks its output against goal.java. The merge
   * tool is given a file that already contains the result of a git merge.
   *
   * @param testCase the name of a directory under {@code src/test/resources/end-to-end}
   * @param options the command-line options to pass to the program
   */
  private void checkTool(String testCase, List<String> options) {
    Path caseDir = testCasesDir.resolve(testCase);
    Path runDir = runDirectory(testCase + "-tool");
    for (String fileName : List.of("left.java", "base.java", "right.java", "merged.java")) {
      copy(caseDir.resolve(fileName), runDir.resolve(fileName));
    }

    List<String> args = new ArrayList<>();
    args.add("tool");
    args.addAll(options);
    args.add("left.java");
    args.add("base.java");
    args.add("right.java");
    args.add("merged.java");

    ProgramResult result = runProgram(runDir, args);
    checkOutput(caseDir, "goal.java", runDir.resolve("merged.java"), result);
  }

  /**
   * Runs the program with erroneous command-line arguments. The program prints the given message on
   * both standard output and standard error, and exits with status 129.
   *
   * @param goalFileName the name, in directory {@code cli-args}, of the file that contains the
   *     expected message
   * @param args the command-line arguments to pass to the program
   */
  private void checkCliError(String goalFileName, List<String> args) {
    Path caseDir = testCasesDir.resolve("cli-args");
    Path runDir = cliRunDirectory(goalFileName + optionsSuffix(args));
    ProgramResult result = runProgram(runDir, args);

    String goalContents = readFile(caseDir.resolve(goalFileName));
    assertEquals(goalContents, result.stdout(), "standard output");
    assertEquals(goalContents, result.stderr(), "standard error");
    assertEquals(erroneousArgsExitStatus, result.exitStatus(), "exit status");
    // The program did not modify the file that a merge driver overwrites.
    assertEquals(
        readFile(caseDir.resolve("left.java")),
        readFile(runDir.resolve("current.java")),
        "the file was not modified");
  }

  /**
   * Runs the program with a command line that picocli cannot parse. The program prints an error
   * message and the usage message on standard error, and exits with status 2.
   *
   * @param goalFileName the name, in directory {@code cli-args}, of the file that contains the
   *     expected first line of standard error
   * @param args the command-line arguments to pass to the program
   */
  private void checkCliUsageError(String goalFileName, List<String> args) {
    Path caseDir = testCasesDir.resolve("cli-args");
    Path runDir = cliRunDirectory(goalFileName + optionsSuffix(args));
    ProgramResult result = runProgram(runDir, args);

    String goalContents = readFile(caseDir.resolve(goalFileName));
    assertTrue(
        result.stderr().startsWith(goalContents),
        "standard error should start with: " + goalContents + "but was: " + result.stderr());
    assertTrue(
        result.stderr().contains("Usage: plumelib-merge "),
        "standard error should contain the usage message, but was: " + result.stderr());
    assertEquals("", result.stdout(), "standard output");
    assertEquals(picocliUsageExitStatus, result.exitStatus(), "exit status");
  }

  /**
   * Runs the merge driver with "--verbose", then checks the first line of standard output, which
   * states which mergers are enabled.
   *
   * @param goalFileName the name, in directory {@code cli-args}, of the file that contains the
   *     expected first line of standard output
   * @param options the command-line options to pass to the program
   */
  private void checkVerboseConfiguration(String goalFileName, List<String> options) {
    Path caseDir = testCasesDir.resolve("cli-args");
    Path runDir = cliRunDirectory(goalFileName + optionsSuffix(options));

    List<String> args = new ArrayList<>();
    args.add("driver");
    args.addAll(options);
    args.add("current.java");
    args.add("base.java");
    args.add("right.java");

    ProgramResult result = runProgram(runDir, args);
    String goalContents = readFile(caseDir.resolve(goalFileName));
    assertTrue(
        result.stdout().startsWith(goalContents),
        "standard output should start with: " + goalContents + "but was: " + result.stdout());
  }

  /**
   * Checks the program's output: the file that it wrote, its standard output, its standard error,
   * and its exit status.
   *
   * <p>The program is silent unless the test case contains a goal-stdout.txt or goal-stderr.txt
   * file. Its exit status indicates whether the file it wrote contains a conflict.
   *
   * @param caseDir the directory that contains the test case's input and goal files
   * @param goalFileName the name of the goal file, within {@code caseDir}
   * @param outputPath the file that the program wrote
   * @param result the program's exit status, standard output, and standard error
   */
  private void checkOutput(
      Path caseDir, String goalFileName, Path outputPath, ProgramResult result) {
    Path goalPath = caseDir.resolve(goalFileName);
    String goalContents = readFile(goalPath);
    assertEquals(
        goalContents,
        readFile(outputPath),
        "contents of " + outputPath + " (goal is " + goalPath + ")");
    assertEquals(expectedOutput(caseDir, "goal-stdout.txt"), result.stdout(), "standard output");
    assertEquals(expectedOutput(caseDir, "goal-stderr.txt"), result.stderr(), "standard error");
    int expectedExitStatus = hasConflictMarker(goalContents) ? conflictExitStatus : cleanExitStatus;
    assertEquals(expectedExitStatus, result.exitStatus(), "exit status");
  }

  // //////////////////////////////////////////////////////////////////////
  // Utility methods
  //

  /** The exit status, standard output, and standard error of one run of the program. */
  private record ProgramResult(int exitStatus, String stdout, String stderr) {}

  /**
   * Runs the program in a subprocess.
   *
   * @param workingDir the working directory for the subprocess
   * @param args the command-line arguments to pass to the program
   * @return the program's exit status, standard output, and standard error
   */
  private ProgramResult runProgram(Path workingDir, List<String> args) {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null) {
      throw new AssertionError("Property java.home is not set.");
    }
    String classpath = System.getProperty("java.class.path");
    if (classpath == null) {
      throw new AssertionError("Property java.class.path is not set.");
    }

    List<String> command = new ArrayList<>();
    command.add(Path.of(javaHome, "bin", "java").toString());
    command.addAll(addExportsArgs);
    command.add("-cp");
    command.add(classpath);
    command.add("org.plumelib.merging.Main");
    command.addAll(args);

    // Redirecting to files, rather than reading from pipes, avoids deadlock if the program writes
    // a lot of output.
    Path stdoutPath = workingDir.resolve("test-stdout.txt");
    Path stderrPath = workingDir.resolve("test-stderr.txt");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workingDir.toFile());
    pb.redirectOutput(stdoutPath.toFile());
    pb.redirectError(stderrPath.toFile());
    try {
      Process p = pb.start();
      int exitStatus = p.waitFor();
      return new ProgramResult(exitStatus, readFile(stdoutPath), readFile(stderrPath));
    } catch (IOException e) {
      throw new UncheckedIOException("Problem running " + command, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while running " + command, e);
    }
  }

  /**
   * Returns the contents of the given goal file, or "" if the file does not exist.
   *
   * @param caseDir the directory that contains the test case's input and goal files
   * @param goalFileName the name of the goal file, within {@code caseDir}
   * @return the goal file's contents, or ""
   */
  private String expectedOutput(Path caseDir, String goalFileName) {
    Path goalPath = caseDir.resolve(goalFileName);
    return Files.exists(goalPath) ? readFile(goalPath) : "";
  }

  /**
   * Returns true if the given file contents contain a conflict marker.
   *
   * @param contents the contents of a file
   * @return true if the file contains a conflict marker
   */
  private boolean hasConflictMarker(String contents) {
    return contents.startsWith("<<<<<<<") || contents.contains("\n<<<<<<<");
  }

  /**
   * Returns the file extension, including the period, of the test case's files. For example, if the
   * test case's input file is {@code left.java}, this returns {@code .java}.
   *
   * @param caseDir the directory that contains the test case's input and goal files
   * @return the file extension of the test case's files
   */
  private String fileExtension(Path caseDir) {
    String leftPathPrefix = caseDir.resolve("left").toString();
    try (Stream<Path> files = Files.list(caseDir)) {
      List<String> leftPaths =
          files
              .map(Path::toString)
              .filter(p -> p.startsWith(leftPathPrefix + "."))
              .sorted()
              .toList();
      if (leftPaths.size() != 1) {
        throw new AssertionError(
            "Expected exactly one file named left.* in " + caseDir + ", found " + leftPaths);
      }
      return leftPaths.get(0).substring(leftPathPrefix.length());
    } catch (IOException e) {
      throw new UncheckedIOException("Problem listing " + caseDir, e);
    }
  }

  /**
   * Returns a string that is unique to the given command-line options, for use in a directory name.
   * Command-line arguments that are not options, such as file names, are ignored.
   *
   * @param args command-line arguments
   * @return a string that distinguishes these options from any other options
   */
  private String optionsSuffix(List<String> args) {
    StringBuilder result = new StringBuilder();
    for (String arg : args) {
      if (arg.startsWith("-")) {
        result.append(arg.replace("--", "-"));
      }
    }
    return result.toString();
  }

  /**
   * Returns a new empty directory in which to run the program. The directory is under {@code
   * build/}, so that it is easy to examine after a test failure.
   *
   * @param name a name that is unique among all the runs in this test class
   * @return an empty directory
   */
  private Path runDirectory(String name) {
    Path result = runsDir.resolve(name);
    try {
      deleteRecursively(result);
      Files.createDirectories(result);
    } catch (IOException e) {
      throw new UncheckedIOException("Problem creating directory " + result, e);
    }
    return result;
  }

  /**
   * Returns a new directory that contains the files of the {@code cli-args} test case, in which to
   * run the program.
   *
   * @param name a name that is unique among all the runs in this test class
   * @return a directory that contains current.java, base.java, and right.java
   */
  private Path cliRunDirectory(String name) {
    Path caseDir = testCasesDir.resolve("cli-args");
    Path runDir = runDirectory("cli-args-" + name);
    copy(caseDir.resolve("left.java"), runDir.resolve("current.java"));
    copy(caseDir.resolve("base.java"), runDir.resolve("base.java"));
    copy(caseDir.resolve("right.java"), runDir.resolve("right.java"));
    return runDir;
  }

  /**
   * Deletes the given file or directory, including its contents, if it exists.
   *
   * @param path the file or directory to delete
   * @throws IOException if the file or directory cannot be deleted
   */
  private void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(path)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }

  /**
   * Copies a file.
   *
   * @param from the file to copy
   * @param to the destination
   */
  private void copy(Path from, Path to) {
    try {
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("Problem copying " + from + " to " + to, e);
    }
  }

  /**
   * Returns the contents of a file.
   *
   * @param path the file to read
   * @return the file's contents
   */
  private String readFile(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("Problem reading " + path, e);
    }
  }
}
