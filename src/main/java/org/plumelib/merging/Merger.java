package org.plumelib.merging;

/** The interface to a merger, which can be used in a git merge driver or merge tool. */
public interface Merger {

  /**
   * Side-effects its arguments to perhaps resolve some conflicts.
   *
   * @param ms the merge to be improved; is side-effected
   */
  void merge(MergeState ms);
}
