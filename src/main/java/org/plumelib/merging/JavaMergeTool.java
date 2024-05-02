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
 */
public class JavaMergeTool extends AbstractMergeTool {

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

    JavaCommandLineOptions jclo = new JavaCommandLineOptions();
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

    jmt.mainHelper(jclo);
  }

  /**
   * Does the work of JavaMergeTool.
   *
   * @param jclo the command-line options
   */
  public void mainHelper(JavaCommandLineOptions jclo) {

    try {

      // Don't do this until there is a separate MergeTool for non-Java files.
      // if (!adjacent && !mergedFileName.endsWith(".java")) {
      //   System.exit(1);
      // }

      MergeState ms =
          new MergeState(baseFileName, leftFileName, rightFileName, mergedFileName, true);

      // TODO: Common (but short) code in both JavaMergeDriver and JavaMergeTool.

      if (jclo.adjacent) {
        new AdjacentLinesMerger().merge(ms);
      }

      // Even if gitMergeFileExitCode is 0, give fixups a chance to run.
      if (jclo.annotations) {
        new JavaAnnotationsMerger().merge(ms);
      }

      // Imports must come last, because it does nothing unless every non-import conflict
      // has already been resolved.
      if (jclo.imports) {
        new JavaImportsMerger().merge(ms);
      }

      ms.writeBack(jclo.verbose);

      System.exit(ms.hasConflict() ? 1 : 0);
    } catch (Throwable t) {
      t.printStackTrace(System.out);
      t.printStackTrace(System.err);
      System.exit(129);
    }
  }
}
