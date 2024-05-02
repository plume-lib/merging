package org.plumelib.merging;

import java.io.IOException;

// I could instead use (say) JGit, but it seems like overkill to include a whole library just for
// this simple functionality.
/** This class contains static methods related to calling git. */
public class GitLibrary {

  /** If true, output diagnostics. */
  private static boolean verbose = false;

  /** Do not instantiate. */
  private GitLibrary() {
    throw new Error("do not instantiate");
  }

  /**
   * Runs {@code git merge-file} and returns its status code. Note order of arguments.
   *
   * @param baseFileName the base file name
   * @param leftFileName the left file name. This file is overwritten.
   * @param rightFileName the right file name
   * @return the status code of {@code git merge-file}
   */
  public static int performGitMergeFile(
      String baseFileName, String leftFileName, String rightFileName) {

    ProcessBuilder pb =
        new ProcessBuilder(
            "git",
            "merge-file",
            // --zdiff3 is better for human examination, but --diff3 is better for automated
            // analysis because it doesn't move text that is part of the conflict (but is the same
            // in both left and right) out of the conflict markers.  Using --zdiff3 here causes
            // AdjacentLinesMerger.mergedLinewise() to work less well.
            "--diff3",
            "-L",
            "OURS",
            "-L",
            "BASE",
            "-L",
            "THEIRS",
            leftFileName,
            baseFileName,
            rightFileName);
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

    return gitMergeFileExitCode;
  }
}
