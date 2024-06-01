package org.plumelib.merging;

import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.junit.jupiter.api.Test;
import org.plumelib.merging.fileformat.RDiff;
import org.plumelib.util.IPair;

public class RDiffTest {

  @Test
  void cueball() {

    diff_match_patch dmp = new diff_match_patch();
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;

    String baseLines = "//Cueball 1: Make me a sandwich.\n//Cueball 2: Make it yourself.\n";
    String leftLines = "//Cueball 1: Sudo make me a sandwich.\n//Cueball 2: Make it yourself.\n";
    String rightLines = "//Cueball 1: Make me a sandwich.\n//Cueball 2: Okay.\n";
    List<Diff> diffs1 = dmp.diff_main(baseLines, leftLines);
    List<Diff> diffs2 = dmp.diff_main(baseLines, rightLines);
    List<RDiff> rdiffs1 = RDiff.diffsToRDiffs(diffs1);
    List<RDiff> rdiffs2 = RDiff.diffsToRDiffs(diffs2);
    IPair<List<RDiff>, List<RDiff>> aligned = RDiff.align(rdiffs1, rdiffs2);
    assert aligned != null;

    // List<RDiff> aligned1 = aligned.first;
    // List<RDiff> aligned2 = aligned.second;
    // System.out.printf("aligned1 = %s%naligned2 = %s%n", aligned1, aligned2);

    // aligned1 = [Equal{//Cueball 1: }, Replace{M -> Sudo m}, Equal{ake me a sandwich.\n//Cueball
    // 2: }, Equal{Ma}, Equal{k}, Equal{e it }, Equal{y}, Equal{ourself}, Equal{.\n}]
    // aligned2 = [Equal{//Cueball 1: }, Equal{M}, Equal{ake me a sandwich.\n//Cueball 2: },
    // Replace{Ma -> O}, Equal{k}, Replace{e it  -> a}, Equal{y}, Replace{ourself -> }, Equal{.\n}]

  }
}
