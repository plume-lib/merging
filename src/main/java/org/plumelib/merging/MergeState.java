package org.plumelib.merging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.util.FilesPlume;

/** Data about a merge. */
public class MergeState {

  /** The left file. Also known as "current" or "ours". May be overwritten. */
  public final Path leftPath;

  /** The base file path. */
  public final Path basePath;

  /** The right file. Also known as "other" or "theirs". */
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
   * @param leftPath the left (aka current or ours) file; is overwritten by a merge driver
   * @param basePath the base file
   * @param rightPath the right (aka other or theirs) file
   * @param mergedPath the merged file; is overwritten by a merge tool; is null for a merge driver
   * @param hasConflictInitially true if the merged file contains a conflict
   */
  public MergeState(
      Path leftPath, Path basePath, Path rightPath, Path mergedPath, boolean hasConflictInitially) {

    this.leftPath = leftPath;
    this.basePath = basePath;
    this.rightPath = rightPath;
    this.mergedPath = mergedPath;
    this.hasConflictInitially = hasConflictInitially;
    if (!Files.isReadable(leftPath)) {
      Main.exitErroneously("file is not readable: " + leftPath);
    }
    if (!Files.isReadable(basePath)) {
      Main.exitErroneously("file is not readable: " + basePath);
    }
    if (!Files.isReadable(rightPath)) {
      Main.exitErroneously("file is not readable: " + rightPath);
    }
    if (!Files.isReadable(mergedPath)) {
      Main.exitErroneously("file is not readable: " + mergedPath);
    }
    if (!Files.isWritable(mergedPath)) {
      Main.exitErroneously("file is not writable: " + mergedPath);
    }
  }

  @Override
  @SuppressWarnings({
    "allcheckers:purity.not.sideeffectfree.call", // side effect to local state
    "lock:method.guarantee.violated"
  })
  public String toString(@GuardSatisfied MergeState this) {
    StringJoiner sj = new StringJoiner(System.lineSeparator());
    sj.add("MergeState{");
    sj.add("  basePath=" + basePath);
    sj.add("  leftPath=" + leftPath);
    sj.add("  rightPath=" + rightPath);
    sj.add("  mergedPath=" + mergedPath);
    sj.add("  conflictedFile=" + conflictedFile());
    sj.add("}");
    return sj.toString();
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
  @SuppressWarnings({
    "allcheckers:purity.not.deterministic.not.sideeffectfree.assign.field", // assign to cache
    "allcheckers:purity.not.deterministic.object.creation" // create object to put in cache
  })
  @Pure
  public ConflictedFile conflictedFile(@GuardSatisfied MergeState this) {
    if (conflictedFile == null) {
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
      String baseFileName = basePath.toString();
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
  @RequiresNonNull("conflictedFile")
  private void writeBack(Path path) {
    FilesPlume.writeString(path, conflictedFile().fileContents());
  }
}
