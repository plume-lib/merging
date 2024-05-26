package org.plumelib.merging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;
import org.plumelib.merging.fileformat.RDiff;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.CollectionsPlume.Replacement;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/**
 * This is a merger that handles conflicts where the edits differ only in version numbers. A version
 * number has the form "N.N", "N.N.N", etc, where "N" consists of digits. It merges such conflicts
 * in favor of the largest version number.
 */
public class VersionNumbersMerger extends Merger {

  /**
   * Creates a VersionNumbersMerger.
   *
   * @param verbose if true, output diagnostic information
   */
  public VersionNumbersMerger(boolean verbose) {
    super(verbose);
  }

  /** An instance of diff_match_patch. */
  private static diff_match_patch dmp = new diff_match_patch();

  static {
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;
  }

  @Override
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf, MergeState mergeState) {

    List<Replacement<String>> replacements = new ArrayList<>();

    for (MergeConflict mc : cf.mergeConflicts()) {
      String merged = mergedWithVersionNumbers(mc);
      if (merged != null) {
        replacements.add(
            Replacement.of(mc.start(), mc.end() - 1, Collections.singletonList(merged)));
      }
    }

    if (replacements.isEmpty()) {
      return null;
    }

    if (verbose) {
      System.out.printf("before replacement: replacements = %s%n", replacements);
    }
    List<String> newLines = CollectionsPlume.replace(cf.lines(), replacements);
    ConflictedFile result = new ConflictedFile(newLines, cf.path);
    return result;
  }

  /**
   * If all the differences are version numbers, then return a string that contains them all.
   * Otherwise, return null.
   *
   * @param mc the merge conflict
   * @return the merged differences or null
   */
  private @Nullable String mergedWithVersionNumbers(MergeConflict mc) {
    List<String> baseLines = mc.base();
    if (baseLines == null) {
      throw new Error("Use 3-way diff for VersionNumbersMerger: " + mc);
    }
    String baseText = StringsPlume.join("", baseLines);
    String leftText = StringsPlume.join("", mc.left());
    String rightText = StringsPlume.join("", mc.right());
    List<Diff> leftDiffs = dmp.diff_main(baseText, leftText);
    if (verbose) {
      System.err.printf("calling diff_main([[[%s]]], [[[%s]]])%n", baseText, leftText);
    }
    List<Diff> rightDiffs = dmp.diff_main(baseText, rightText);
    if (verbose) {
      System.err.printf("calling diff_main([[[%s]]], [[[%s]]])%n", baseText, rightText);
    }
    List<RDiff> leftRDiffs = rdiffsForVersionNumbers(leftDiffs);
    List<RDiff> rightRDiffs = rdiffsForVersionNumbers(rightDiffs);
    System.out.printf("leftRDiffs = %s%n", leftRDiffs);
    System.out.printf("rightRDiffs = %s%n", rightRDiffs);
    IPair<List<RDiff>, List<RDiff>> aligned = RDiff.align(leftRDiffs, rightRDiffs);
    System.out.printf("aligned = %s%n", aligned);
    if (aligned == null) {
      return null;
    }
    List<RDiff> leftAligned = aligned.first;
    List<RDiff> rightAligned = aligned.second;
    System.out.printf("left diffs: %s%n", leftAligned);
    System.out.printf("right diffs: %s%n", rightAligned);

    StringBuilder result = new StringBuilder();
    for (Iterator<RDiff> i1 = leftAligned.iterator(), i2 = rightAligned.iterator();
        i1.hasNext() && i2.hasNext(); ) {
      RDiff d1 = i1.next();
      RDiff d2 = i2.next();

      if (d1 instanceof RDiff.Equal || d1.isNoOp()) {
        result.append(d2.postText());
      } else if (d2 instanceof RDiff.Equal || d2.isNoOp()) {
        result.append(d1.postText());
      } else {
        String pre = d1.preText();
        assert d1.preText().equals(d2.preText());
        String post1 = d1.postText();
        String post2 = d2.postText();

        if (StringsPlume.isVersionNumber(pre)
            && StringsPlume.isVersionNumber(post1)
            && StringsPlume.isVersionNumber(post2)
            && StringsPlume.isVersionNumberLE(pre, post1)
            && StringsPlume.isVersionNumberLE(pre, post2)) {
          if (StringsPlume.isVersionNumberLE(post1, post2)) {
            result.append(post2);
          } else {
            result.append(post1);
          }
        } else {
          return null;
        }
      }
    }
    return result.toString();
  }

  /**
   * Merges or splits operations, to make version number changes atomic.
   *
   * @param diffs the differences
   * @return the rewritten diffs
   */
  private static List<RDiff> rdiffsForVersionNumbers(List<Diff> diffs) {
    List<RDiff> rdiffs = RDiff.diffsToRDiffs(diffs);
    List<RDiff> result = new ArrayList<>(rdiffs.size());
    RDiff nextRDiff = null;
    for (RDiff rdiff : rdiffs) {
      if (nextRDiff == null) {
        nextRDiff = rdiff;
      } else {
        List<RDiff> versionNumberMerged = versionNumberMerge(nextRDiff, rdiff);
        System.out.printf(
            "versionNumberMerge(%s, %s) => %s%n", nextRDiff, rdiff, versionNumberMerged);
        if (versionNumberMerged != null) {
          int size = versionNumberMerged.size() - 1;
          for (int i = 0; i < size; i++) {
            result.add(versionNumberMerged.get(i));
          }
          nextRDiff = versionNumberMerged.get(size);
        } else {
          result.add(nextRDiff);
          nextRDiff = rdiff;
        }
      }
    }
    if (nextRDiff != null) {
      result.add(nextRDiff);
    }
    return result;
  }

  /** Matches part of a version number at the beginning of a string. */
  private static final Pattern versionNumberPrefixPattern =
      Pattern.compile("^([.0-9]+).*$", Pattern.DOTALL);

  /** Matches part of a version number at the end of a string. */
  private static final @Regex(1) Pattern versionNumberSuffixPattern =
      Pattern.compile("^.*?([.0-9]+)$", Pattern.DOTALL);

  /**
   * If the given RDiffs may abut within a version number, split into up to 3 RDiffs, such that the
   * potential version number is in its own RDiff.
   *
   * @param r1 a RDiff
   * @param r2 a RDiff
   * @return the merge of {@code r1} and {@code r2}, or null
   */
  private static List<RDiff> versionNumberMerge(RDiff r1, RDiff r2) {

    IPair<RDiff, RDiff> pair1 = r1.suffixSplit(versionNumberSuffixPattern);
    RDiff r1NonVersionNumber = pair1.first;
    RDiff r1VersionNumber = pair1.second;
    IPair<RDiff, RDiff> pair2 = r2.prefixSplit(versionNumberPrefixPattern);
    RDiff r2VersionNumber = pair2.first;
    RDiff r2NonVersionNumber = pair2.second;
    System.out.printf("versionNumberMerge(%s, %s)%n", r1, r2);
    System.out.printf(
        "  intermediate: %s %s %s %s%n",
        r1NonVersionNumber, r1VersionNumber, r2VersionNumber, r2NonVersionNumber);

    List<RDiff> result = new ArrayList<>(3);
    if (!r1NonVersionNumber.isNoOp()) {
      result.add(r1NonVersionNumber);
    }
    if (!r1VersionNumber.isNoOp()) {
      if (r2VersionNumber.isNoOp()) {
        result.add(r1VersionNumber);
      } else {
        result.add(r1VersionNumber.merge(r2VersionNumber));
      }
    } else {
      if (!r2VersionNumber.isNoOp()) {
        result.add(r2VersionNumber);
      }
    }
    if (!r2NonVersionNumber.isNoOp()) {
      result.add(r2NonVersionNumber);
    }
    return result;
  }
}
