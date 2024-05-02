package org.plumelib.merging;

import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.ConflictElement;

/** The interface to a merger, which can be used in a git merge driver or merge tool. */
abstract class Merger {

  /** If true, print diagnostics for debugging. */
  protected final boolean verbose;

  /**
   * Creates a Merger.
   *
   * @param verbose if true, print diagnostics for debugging.
   */
  public Merger(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Possibly side-effects its arguments to resolve some conflicts.
   *
   * <p>The way an implementation performs the side effect is typically by calling {@link
   * MergeState#setConflictedFile}.
   *
   * @param mergeState the merge to be improved; is side-effected
   */
  void merge(MergeState mergeState) {
    ConflictedFile cf = mergeState.conflictedFile();
    if (verbose) {
      System.out.printf("JavaImportsMerger: conflicted file = %s%n", cf);
    }

    @SuppressWarnings("nullness:assignment") // cf.parseError() == null => cf.hunks() != null
    @NonNull List<ConflictElement> hunks = cf.hunks();
    if (verbose) {
      System.out.printf(
          "%s: conflicted file (size %s)=%s%n",
          this.getClass().getSimpleName(), (hunks == null ? "null" : ("" + hunks.size())), cf);
    }
    if (hunks == null) {
      String message =
          this.getClass().getSimpleName() + ": parse error in merged file: " + cf.parseError();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    ConflictedFile newCf = resolveConflicts(cf, mergeState);
    if (newCf != null) {
      mergeState.setConflictedFile(newCf);
    }
  }

  /**
   * Given a conflicted file, returns a new one with some conflicts resolved. Returns null if no
   * changes were made.
   *
   * @param cf a non-erroneous conflicted file
   * @param mergeState the merge state; not needed by most mergers
   * @return the new file contents, or null if no changes were made
   */
  abstract @Nullable ConflictedFile resolveConflicts(ConflictedFile cf, MergeState mergeState);
}
