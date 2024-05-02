package org.plumelib.merging;

/** The interface to a merger, which can be used in a git merge driver or merge tool. */
public interface Merger {

  /**
   * Possibly side-effects its arguments to resolve some conflicts.
   *
   * <p>The way an implementation performs the side effect is typically by calling {@link
   * MergeState#setConflictedFile}.
   *
   * @param ms the merge to be improved; is side-effected
   */
  void merge(MergeState ms);
}
