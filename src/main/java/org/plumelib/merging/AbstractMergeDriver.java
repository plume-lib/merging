package org.plumelib.merging;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * This is a base class for a git merge driver. A git merge driver takes as input three filenames,
 * for the current, base, and other versions of the file. The merge driver overwrites the current
 * file with the merge result. The filenames are temporary names that convey no information.
 */
public class AbstractMergeDriver {

  /** The base file name. */
  public final String baseFileName;

  /** The current file name; is overwritten by this method. */
  public final String currentFileName;

  /** The other file name. */
  public final String otherFileName;

  /** The base file path. */
  public final Path basePath;

  /** The current file path; is overwritten by this method. */
  public final Path currentPath;

  /** The other file path. */
  public final Path otherPath;

  /**
   * Creates an AbstractMergeDriver.
   *
   * @param args the command-line arguments; must have length 3.
   */
  public AbstractMergeDriver(String[] args) {
    if (args.length != 3) {
      exitErroneously(
          String.format(
              "%s: expected 3 arguments current, base, other; got %d: %s",
              getClass().getSimpleName(), args.length, Arrays.toString(args)));
      throw new Error("unreachable");
    }
    currentFileName = args[0];
    baseFileName = args[1];
    otherFileName = args[2];
    currentPath = Path.of(currentFileName);
    basePath = Path.of(baseFileName);
    otherPath = Path.of(otherFileName);
    if (!Files.isReadable(currentPath)) {
      exitErroneously("file is not readable: " + currentFileName);
    }
    if (!Files.isWritable(currentPath)) {
      exitErroneously("file is not writable: " + currentFileName);
    }
    if (!Files.isReadable(basePath)) {
      exitErroneously("file is not readable: " + baseFileName);
    }
    if (!Files.isReadable(otherPath)) {
      exitErroneously("file is not readable: " + otherFileName);
    }
  }

  /**
   * Exit erroneously, for example because of an invalid invocation.
   *
   * @param errorMessage the error message
   */
  public static void exitErroneously(String errorMessage) {
    String className = MethodHandles.lookup().lookupClass().getSimpleName();
    System.out.println(className + ": " + errorMessage);
    System.err.println(className + ": " + errorMessage);
    System.exit(129);
  }
}
