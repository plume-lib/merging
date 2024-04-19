// TODO: improve the name of this class.

package org.plumelib.merging;

import java.io.IOException;

/** This class contains static methods. */
public class Library {

  /** If true, output diagnostics. */
  private static boolean verbose = false;

  /** Do not instantiate. */
  private Library() {
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
