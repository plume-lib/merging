package org.plumelib.merging;

import java.util.Arrays;
import org.plumelib.options.Options;

/**
 * This is a git merge tool for Java files. A git merge tool takes as input four filenames, for the
 * current, base, other, and merged versions of the file. The merged version contains conflict
 * markers. (Otherwise, the merge tool is not run.) The merge tool overwrites the merged file with a
 * better merge result.
 *
 * <p>An exit status of 0 means the merge was successful and there are no remaining conflicts. An
 * exit status of 1-128 means there are remaining conflicts. An exit status of 129 or greater means
 * to abort the merge.
 *
 * <p>This program tries to correct conflicts in annotations and tries to improve merges in {@code
 * import} statements.
 */
@SuppressWarnings({"lock"}) // todo
public class JavaMergeTool extends AbstractMergeTool {

  // TODO: Should this be an instance variable?
  /** Holds command-line options. */
  public static final JavaCommandLineOptions jclo = new JavaCommandLineOptions();

  /**
   * Creates a JavaMergeTool.
   *
   * @param args the 0 or 4 command-line arguments to a merge tool
   */
  public JavaMergeTool(String[] args) {
    super(args);
  }

  /**
   * A git merge tool to merge a Java file.
   *
   * <p>Exit status greater than 128 means to abort the merge.
   *
   * @param args the command-line arguments of the merge tool, 0 or 4 filenames: base, current,
   *     other, merged
   */
  public static void main(String[] args) {

    String[] orig_args = args;
    Options options =
        new Options("JavaMergeTool [options] basefile leftfile rightfile mergedfile", jclo);
    options.enableDebugLogging(true);
    args = options.parse(true, orig_args);
    jclo.check(orig_args);

    if (jclo.verbose) {
      System.out.printf("JavaMergeTool arguments: %s%n", Arrays.toString(orig_args));
    }

    JavaMergeTool jmt = new JavaMergeTool(args);

    jmt.mainHelper();
  }

  // TODO: Can this be moved into a separate file and shared with merge tools?
  /** Does the work of JavaMergeTool. */
  public void mainHelper() {

    if (!mergedFileName.endsWith(".java")) {
      System.exit(1);
    }

    MergeState ms = new MergeState(baseFileName, leftFileName, rightFileName, mergedFileName, true);

    // TODO: common (but short) code with JavaMergeDriver and JavaMergeTool.

    // Even if gitMergeFileExitCode is 0, give fixups a chance to run.
    if (jclo.annotations) {
      new JavaAnnotationsMerger().merge(ms);
    }

    if (jclo.imports) {
      new JavaImportsMerger().merge(ms);
    }

    ms.writeBackToBackup();

    System.exit(ms.hasConflict() ? 1 : 0);
  }
}
