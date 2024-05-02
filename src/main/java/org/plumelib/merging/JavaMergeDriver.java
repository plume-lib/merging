package org.plumelib.merging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.options.Option;
import org.plumelib.options.Options;

/**
 * This is a git merge driver for Java files. A git merge driver takes as input three filenames, for
 * the current, base, and other versions of the file; the merge driver overwrites the current file
 * with the merge result. The filenames are temporary names that convey no information.
 *
 * <p>An exit status of 0 means the merge was successful and there are no remaining conflicts. An
 * exit status of 1-128 means there are remaining conflicts. An exit status of 129 or greater means
 * to abort the merge.
 *
 * <p>This program first does {@code git merge-file}, then it tries to correct conflicts in
 * annotations and tries to improve merges in {@code import} statements.
 */
public class JavaMergeDriver extends AbstractMergeDriver {

  /** If false, don't run `git merge-file`. */
  @Option("Run `git merge-file`")
  public static boolean git_merge_file = true;

  // TODO: Should this be an instance variable?
  /** Holds command-line options. */
  public static final JavaCommandLineOptions jclo = new JavaCommandLineOptions();

  /**
   * Creates a JavaMergeDriver.
   *
   * @param args the 3 command-line arguments to a merge driver
   */
  private JavaMergeDriver(String[] args) {
    super(args);
  }

  /**
   * A git merge driver to merge a Java file.
   *
   * <p>Exit status greater than 128 means to abort the merge.
   *
   * @param args the command-line arguments of the merge driver, 3 filenames: current, base, other;
   *     note order of arguments
   */
  public static void main(String[] args) {

    String[] orig_args = args;
    Options options =
        new Options(
            "JavaMergeDriver [options] basefile leftfile rightfile mergedfile",
            jclo,
            JavaMergeDriver.class);
    args = options.parse(true, orig_args);
    jclo.check(orig_args);

    if (jclo.verbose) {
      System.out.printf("JavaMergeDriver arguments: %s%n", Arrays.toString(orig_args));
    }

    JavaMergeDriver jmd = new JavaMergeDriver(args);

    jmd.mainHelper();
  }

  // TODO: Can this be moved into a separate file and shared with merge drivers?
  /** Does the work of JavaMergeDriver. */
  public void mainHelper() {

    try {
      String leftFileSavedName = "left file saved: not yet named";

      // Make a copy of the file that will be overwritten, for passing to external tools.
      // TODO: Can I do this more lazily, to avoid the expense if it will not be needed?
      try {
        File leftFileSaved = File.createTempFile("leftBeforeOverwriting-", ".bak");
        leftFileSaved.deleteOnExit();
        leftFileSavedName = leftFileSaved.toString();
        // REPLACE_EXISTING is needed because createTempFile creates an empty file.
        Files.copy(
            Path.of(currentFileName), leftFileSaved.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new Error("Problem copying " + currentFileName + " to " + leftFileSavedName, e);
      }

      int gitMergeFileExitCode;
      if (git_merge_file) {
        gitMergeFileExitCode =
            GitLibrary.performGitMergeFile(baseFileName, currentFileName, otherFileName);
      } else {
        // There is initially a merge conflict, which appears in currentFile.
        gitMergeFileExitCode = 1;
      }
      if (jclo.verbose) {
        System.out.printf(
            "status %d for: git merge-file %s %s %s%n",
            gitMergeFileExitCode, currentFileName, baseFileName, otherFileName);
      }

      // Look for trivial merge conflicts
      ConflictedFile cf = new ConflictedFile(currentPath);
      cf.hunks();

      MergeState ms =
          new MergeState(
              baseFileName,
              leftFileSavedName,
              otherFileName,
              currentFileName,
              gitMergeFileExitCode != 0);

      // TODO: Common (but short) code in both JavaMergeDriver and JavaMergeTool.

      if (jclo.adjacent) {
        if (jclo.verbose) {
          System.out.printf("calling adjacent%n");
        }
        new AdjacentLinesMerger().merge(ms);
      }

      // Even if gitMergeFileExitCode is 0, give fixups a chance to run.
      if (jclo.annotations) {
        if (jclo.verbose) {
          System.out.printf("calling annotations%n");
        }
        new JavaAnnotationsMerger().merge(ms);
      }

      // Imports should come last, because it does nothing unless every non-import conflict
      // has already been resolved.
      if (jclo.imports) {
        if (jclo.verbose) {
          System.out.printf("calling imports%n");
        }
        new JavaImportsMerger(jclo.verbose).merge(ms);
      }

      ms.writeBack(jclo.verbose);

      int exitStatus = ms.hasConflict() ? 1 : 0;
      if (jclo.verbose) {
        System.out.printf("Exiting with status %d.%n", exitStatus);
      }
      System.exit(exitStatus);
    } catch (Throwable t) {
      t.printStackTrace(System.out);
      t.printStackTrace(System.err);
      System.exit(129);
    }
  }
}
