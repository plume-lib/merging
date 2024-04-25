package org.plumelib.merging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import name.fraser.neil.plaintext.DmpLibrary;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.merging.ConflictedFile.ConflictElement;
import org.plumelib.merging.ConflictedFile.MergeConflict;
import org.plumelib.merging.RDiff.Equal;
import org.plumelib.merging.RDiff.NoOp;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.CollectionsPlume.Replacement;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/** This is a merger that resolves conflicts where the edits are on different but adjacent lines. */
public class AdjacentMerger implements Merger {

  /** If true, produce debugging output. */
  private static boolean verbose = false;

  /** Creates a JavaAdjacentMerger. */
  AdjacentMerger() {}

  @Override
  public void merge(MergeState mergeState) {
    if (!mergeState.hasConflict()) {
      return;
    }

    ConflictedFile cf = mergeState.conflictedFile();
    if (cf.parseError() != null) {
      String message = "AdjacentMerger: trouble reading merged file: " + cf.parseError();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    @SuppressWarnings("nullness:assignment") // cf.parseError() == null => cf.contents() != null
    @NonNull List<ConflictElement> ces = cf.hunks();
    if (verbose) {
      System.out.printf(
          "AdjacentMerger: conflicted file (size %s)=%s%n",
          (ces == null ? "null" : ("" + ces.size())), cf);
    }

    ConflictedFile newCf = resolveConflicts(cf);
    if (newCf != null) {
      mergeState.setConflictedFile(newCf);
    }
  }

  /**
   * Given a conflicted file, returns a new one, possibly with some conflicts resolved. Returns null
   * if no changes were made.
   *
   * @param cf the conflicted file, which should not be erroneous
   * @return the new file contents, or null if no changes were made
   */
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf) {

    // Todo: localize the test for conflicts.
    List<ConflictElement> ces = cf.hunks();
    if (ces == null) {
      throw new Error("Erroneous ConflictedFile");
    }

    List<Replacement<String>> replacements = new ArrayList<>();

    diff_match_patch dmp = new diff_match_patch();
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;

    for (ConflictElement ce : ces) {
      if (!(ce instanceof MergeConflict)) {
        continue;
      }
      MergeConflict mc = (MergeConflict) ce;
      String leftContent = StringsPlume.join("", mc.left());
      String rightContent = StringsPlume.join("", mc.right());
      if (verbose) {
        System.err.printf("calling diff_main([[[%s]]], [[[%s]]])%n", leftContent, rightContent);
      }

      String baseJoined = mc.baseJoined();
      if (baseJoined == null) {
        throw new Error("AdjacentMerger needs a 3-way diff");
      }

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
      System.out.printf("before replacement: replacements = %s%n", replacements);
    }
    List<String> newLines = CollectionsPlume.replace(cf.lines(), replacements);
    ConflictedFile result = new ConflictedFile(newLines);
    return result;
  }

  /**
   * If all the edits are on different lines, then return a string that contains them all.
   * Otherwise, return null.
   *
   * @param mc the merge conflict, which includes the base, left, and right texts
   * @return the merged differences or null
   */
  private static @Nullable List<String> mergedWithAdjacent(MergeConflict mc) {
    List<String> baseLines = mc.base();
    String baseJoined = mc.baseJoined();
    if (baseLines == null || baseJoined == null) {
      throw new Error("AdjacentMerger needs a 3-way diff");
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
      if (d1 instanceof Equal || d1 instanceof NoOp) {
        result.add(d2.postText());
      } else if (d2 instanceof Equal || d2 instanceof NoOp) {
        result.add(d1.postText());
      } else if (d1.postText().equals(d2.postText())) {
        // Can this happen?
        result.add(d1.postText());
      } else {
        String message = String.format("Bad alignment: d1=%s d2=%s", d1, d2);
        throw new Error(message);
      }
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
  private static @Nullable List<String> mergedLinewise(MergeConflict mc) {
    if (mc.base() == null
        || mc.base().size() != mc.left().size()
        || mc.base().size() != mc.right().size()) {
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
    return result;
  }
}
