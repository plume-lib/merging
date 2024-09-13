package org.plumelib.merging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.ConflictElement;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;
import org.plumelib.merging.fileformat.RDiff;
import org.plumelib.merging.fileformat.RDiff.Equal;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.CollectionsPlume.Replacement;
import org.plumelib.util.IPair;

/** This is a merger that resolves conflicts where the edits are on different but adjacent lines. */
public class AdjacentLinesMerger extends Merger {

  /**
   * Creates an AdjacentLinesMerger.
   *
   * @param verbose if true, output diagnostic information
   */
  public AdjacentLinesMerger(boolean verbose) {
    super(verbose);
  }

  @Override
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf, MergeState mergeState) {

    List<ConflictElement> hunks = cf.hunks();
    if (hunks == null) {
      Main.exitErroneously("Unparseable file " + cf.path);
      throw new Error("unreachable");
    }

    List<Replacement<String>> replacements = new ArrayList<>();

    for (ConflictElement ce : hunks) {
      if (!(ce instanceof MergeConflict)) {
        continue;
      }
      MergeConflict mc = (MergeConflict) ce;
      List<String> merged = mergedWithAdjacent(mc);
      if (merged == null) {
        merged = mergedLinewise(mc);
      }
      if (merged != null) {
        replacements.add(Replacement.of(mc.start(), mc.end() - 1, merged));
      }
    }

    if (replacements.isEmpty()) {
      return null;
    }

    if (verbose) {
      System.out.printf("AdjacentLinesMerger: replacements = %s%n", replacements);
      // System.out.printf("AdjacentLinesMerger: before replacement = %s%n", cf.lines());
    }
    List<String> newLines = CollectionsPlume.replace(cf.lines(), replacements);
    if (verbose) {
      // System.out.printf("AdjacentLinesMerger: after replacement = %s%n", newLines);
    }
    ConflictedFile result = new ConflictedFile(newLines, cf.path);
    return result;
  }

  /**
   * If all the edits are on different lines, then return a string that contains them all.
   * Otherwise, return null.
   *
   * @param mc the merge conflict, which includes the base, left, and right texts
   * @return the merged differences or null
   */
  private @Nullable List<String> mergedWithAdjacent(MergeConflict mc) {
    String baseJoined = mc.baseJoined();
    if (baseJoined == null) {
      Main.exitErroneously("need a 3-way diff");
      throw new Error("unreachable");
    }

    List<Diff> leftDmpDiffs = DmpLibrary.diffByLines(baseJoined, mc.leftJoined());
    List<Diff> rightDmpDiffs = DmpLibrary.diffByLines(baseJoined, mc.rightJoined());
    if (verbose) {
      System.out.printf("left diffs: %s%n", leftDmpDiffs);
      System.out.printf("right diffs: %s%n", rightDmpDiffs);
    }
    List<RDiff> leftUnaligned = RDiff.diffsToRDiffs(leftDmpDiffs);
    List<RDiff> rightUnaligned = RDiff.diffsToRDiffs(rightDmpDiffs);
    IPair<List<RDiff>, List<RDiff>> pair = RDiff.align(leftUnaligned, rightUnaligned);
    if (pair == null) {
      return null;
    }
    List<RDiff> leftDiffs = pair.first;
    List<RDiff> rightDiffs = pair.second;
    if (verbose) {
      System.out.printf("left diffs: %s%n", leftDiffs);
      System.out.printf("right diffs: %s%n", rightDiffs);
    }
    assert leftDiffs.size() == rightDiffs.size();

    List<String> result = new ArrayList<>();
    for (Iterator<RDiff> i1 = leftDiffs.iterator(), i2 = rightDiffs.iterator();
        i1.hasNext() && i2.hasNext(); ) {
      RDiff d1 = i1.next();
      RDiff d2 = i2.next();
      assert d1.preText().equals(d2.preText());
      if (d1 instanceof Equal || d1.isNoOp()) {
        result.add(d2.postText());
      } else if (d2 instanceof Equal || d2.isNoOp()) {
        result.add(d1.postText());
      } else if (d1.postText().equals(d2.postText())) {
        // Can this happen?
        result.add(d1.postText());
      } else {
        return null;
      }
    }
    if (verbose) {
      System.out.printf("mergedWithAdjacent(%s) => %s%n", mc, result);
    }
    return result;
  }

  /**
   * If all three texts have the same length, and for every line, at least two of {base, left,
   * right} are the same, then return a linewise merge. Otherwise, return null.
   *
   * @param mc the merge conflict, which includes the base, left, and right texts
   * @return the merged differences or null
   */
  private @Nullable List<String> mergedLinewise(MergeConflict mc) {
    if (mc.base() == null) {
      Main.exitErroneously("need a 3-way diff");
      throw new Error("unreachable");
    }

    if (mc.base().size() != mc.left().size() || mc.base().size() != mc.right().size()) {
      return null;
    }
    List<String> result = new ArrayList<>();
    for (Iterator<String> iBase = mc.base().iterator(),
            iLeft = mc.left().iterator(),
            iRight = mc.right().iterator();
        iBase.hasNext() && iLeft.hasNext() && iRight.hasNext(); ) {
      String base = iBase.next();
      String left = iLeft.next();
      String right = iRight.next();
      if (left.equals(right)) {
        result.add(left);
      } else if (base.equals(left)) {
        result.add(right);
      } else if (base.equals(right)) {
        result.add(left);
      } else {
        return null;
      }
    }
    if (verbose) {
      System.out.printf("mergedLinewise(%s) => %s%n", mc, result);
    }
    return result;
  }
}
