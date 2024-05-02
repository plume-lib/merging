package org.plumelib.merging;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.util.FilesPlume;

/** Data about a merge. */
@SuppressWarnings("nullness") // TEMPORARY
public class MergeState {

  /** The base file name. */
  public final String baseFileName;

  /** The left file name. Also known as "current" or "ours". */
  public final String leftFileName;

  /** The right file name. Also known as "theirs". */
  public final String rightFileName;

  /** The merged file name. */
  public final @Nullable String mergedFileName;

  /** The base file path. */
  public final Path basePath;

  /** The left file path; is overwritten by this method. */
  public final Path leftPath;

  /** The right file path. */
  public final Path rightPath;

  /** The merged file path. */
  public final Path mergedPath;

  /** The base file contents. */
  private @MonotonicNonNull List<String> baseFileLines;

  /** The left file contents. */
  private @MonotonicNonNull List<String> leftFileLines;

  /** The right file contents. */
  private @MonotonicNonNull List<String> rightFileLines;

  /** The merged file. */
  private @MonotonicNonNull ConflictedFile conflictedFile;

  /** True if the merged file contains a conflict when this MergeState was constructed. */
  private boolean hasConflictInitially;

  /**
   * True if the conflictedFile is dirty: it has changed and its contents need to be written back to
   * the file. If this is true, then conflictedFile is non-null.
   */
  private boolean conflictedFileChanged;

  /**
   * Creates a MergeState.
   *
   * @param baseFileName the base file name
   * @param leftFileName the left, or left, file name; is overwritten by a merge driver
   * @param rightFileName the other file name
   * @param mergedFileName the merged file name; is overwritten by a merge tool; is null for a merge
   *     driver
   * @param hasConflictInitially true if the merged file contains a conflict
   */
  public MergeState(
      String baseFileName,
      String leftFileName,
      String rightFileName,
      String mergedFileName,
      boolean hasConflictInitially) {

    this.baseFileName = baseFileName;
    this.leftFileName = leftFileName;
    this.rightFileName = rightFileName;
    this.mergedFileName = mergedFileName;
    basePath = Path.of(baseFileName);
    leftPath = Path.of(leftFileName);
    rightPath = Path.of(rightFileName);
    mergedPath = Path.of(mergedFileName);
    this.hasConflictInitially = hasConflictInitially;
    if (!Files.isReadable(basePath)) {
      exitErroneously("file is not readable: " + baseFileName);
    }
    if (!Files.isReadable(leftPath)) {
      exitErroneously("file is not readable: " + leftFileName);
    }
    if (!Files.isReadable(rightPath)) {
      exitErroneously("file is not readable: " + rightFileName);
    }
    if (!Files.isReadable(mergedPath)) {
      exitErroneously("file is not readable: " + mergedFileName);
    }
    if (!Files.isWritable(mergedPath)) {
      exitErroneously("file is not writable: " + mergedFileName);
    }
  }

  /**
   * Returns the lines in the base file.
   *
   * @return the lines in the base file
   */
  public List<String> baseFileLines() {
    if (baseFileLines == null) {
      baseFileLines = FilesPlume.readLinesRetainingSeparators(basePath);
    }
    return baseFileLines;
  }

  /**
   * Returns the lines in the left file.
   *
   * @return the lines in the left file
   */
  public List<String> leftFileLines() {
    if (leftFileLines == null) {
      leftFileLines = FilesPlume.readLinesRetainingSeparators(leftPath);
    }
    return leftFileLines;
  }

  /**
   * Returns the lines in the right file.
   *
   * @return the lines in the right file
   */
  public List<String> rightFileLines() {
    if (rightFileLines == null) {
      rightFileLines = FilesPlume.readLinesRetainingSeparators(rightPath);
    }
    return rightFileLines;
  }

  /**
   * Returns true if the merged file has a conflict.
   *
   * @return true if the merged file has a conflict
   */
  public boolean hasConflict() {
    if (conflictedFile == null) {
      return hasConflictInitially;
    } else {
      return conflictedFile.hasConflict();
    }
  }

  /**
   * Returns the merged file, parsed into a ConflictedFile.
   *
   * @return the merged file, parsed into a ConflictedFile
   */
  public ConflictedFile conflictedFile() {
    if (conflictedFile == null) {
      // TODO: make it possible to pass in both fileContents and lines, to save work in
      // ConflictedFile.  Anyway, MergeState should not be doing any splitting work here.
      conflictedFile = new ConflictedFile(mergedPath, hasConflictInitially);
    }
    return conflictedFile;
  }

  /**
   * Sets the merged file.
   *
   * @param cf the new conflicted file
   */
  public void setConflictedFile(ConflictedFile cf) {
    conflictedFile = cf;
    conflictedFileChanged = true;
  }

  /**
   * Writes the conflicted file back to the file system, if needed.
   *
   * @param verbose if true, print diagnostic information
   */
  public void writeBack(boolean verbose) {
    if (conflictedFile != null && (conflictedFileChanged || conflictedFile.hasTrivalConflict())) {
      if (verbose) {
        System.out.printf("Writing back to %s .%n", mergedPath);
      }
      writeBack(mergedPath);
      // By default, if a mergetool returns a non-zero status, git discards any edits done by the
      // mergetool, reverting to the state before the mergetool was run from a backup file. To work
      // around this, such a tool can write partial results to a *_BACKUP_* file (named analogously
      // to *_LOCAL_*, *_BASE_*, etc.).
      if (baseFileName.contains("_BASE_")) {
        writeBack(Path.of(baseFileName.replace("_BASE_", "_BACKUP_")));
      }
      conflictedFileChanged = false;
    }
  }

  /**
   * Writes the conflicted file back to the given path, unconditionally.
   *
   * @param path the path to which to write the conflicted file
   */
  private void writeBack(Path path) {
    // TODO: It may be more efficient not to make one big string, but that inefficiency is
    // probably so small that it does not matter.
    FilesPlume.writeString(path, conflictedFile.fileContents());
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
