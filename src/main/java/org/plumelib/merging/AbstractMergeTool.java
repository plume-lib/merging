package org.plumelib.merging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * This is a base class for a git merge tool. A git merge tool takes as input four filenames, for
 * the current, base, other, and merged versions of the file. The merged version contains conflict
 * markers. (Otherwise, the merge tool is not run.) The merge tool overwrites the merged file with a
 * better merge result.
 *
 * <p>An exit status of 0 means the merge was successful and there are no remaining conflicts. An
 * exit status of 1-128 means there are remaining conflicts. An exit status of 129 or greater means
 * to abort the merge.
 */
public class AbstractMergeTool {

  /** The name of the base file */
  public final String baseFileName;

  /** The name of the local, or left, file. */
  public final String leftFileName;

  /** The name of the remote, or right, file. */
  public final String rightFileName;

  /** The name of the merged file, which contains merge conflicts and will be overwritten. */
  public final String mergedFileName;

  /** The path of the base file */
  public final Path basePath;

  /** The path of the local, or left, file. */
  public final Path leftPath;

  /** The path of the remote, or right, file. */
  public final Path rightPath;

  /** The path of the merged file, which contains merge conflicts and will be overwritten. */
  public final Path mergedPath;

  /**
   * Creates an AbstractMergeTool.
   *
   * @param args command-line arguments; must have length 0 or 4. If length 4, the files are base,
   *     left, right, merged. If length 0, environment variables BASE, LOCAL, REMOTE, and MERGED
   *     must be set.
   */
  AbstractMergeTool(String[] args) {

    // These local variables are nullable. If the values are non-null, they are written into fields.
    String baseFileName;
    String leftFileName;
    String rightFileName;
    String mergedFileName;

    if (args.length == 0) {
      baseFileName = System.getenv("BASE");
      leftFileName = System.getenv("LOCAL");
      rightFileName = System.getenv("REMOTE");
      mergedFileName = System.getenv("MERGED");
      if (baseFileName == null
          || leftFileName == null
          || rightFileName == null
          || mergedFileName == null) {
        JavaLibrary.exitErroneously(
            String.format(
                "unset environment variable: BASE=%s, LOCAL=%s, REMOTE=%s, MERGED=%s",
                baseFileName, leftFileName, rightFileName, mergedFileName));
        throw new Error("unreachable");
      }
    } else if (args.length == 4) {
      baseFileName = args[0];
      leftFileName = args[1];
      rightFileName = args[2];
      mergedFileName = args[3];
    } else {
      JavaLibrary.exitErroneously(
          "expected 0 or 4 arguments, got " + args.length + ": " + Arrays.toString(args));
      throw new Error("unreachable");
    }
    this.baseFileName = baseFileName;
    this.leftFileName = leftFileName;
    this.rightFileName = rightFileName;
    this.mergedFileName = mergedFileName;
    basePath = Path.of(baseFileName);
    leftPath = Path.of(leftFileName);
    rightPath = Path.of(rightFileName);
    mergedPath = Path.of(mergedFileName);
    if (!Files.isReadable(basePath)) {
      JavaLibrary.exitErroneously("file is not readable: " + baseFileName);
    }
    if (!Files.isReadable(leftPath)) {
      JavaLibrary.exitErroneously("file is not readable: " + leftFileName);
    }
    if (!Files.isReadable(rightPath)) {
      JavaLibrary.exitErroneously("file is not readable: " + rightFileName);
    }
    if (!Files.isReadable(mergedPath)) {
      JavaLibrary.exitErroneously("file is not readable: " + mergedFileName);
    }
    if (!Files.isWritable(mergedPath)) {
      JavaLibrary.exitErroneously("file is not writeable: " + mergedFileName);
    }
  }
}
