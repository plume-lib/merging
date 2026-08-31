package org.plumelib.merging;

import java.util.LinkedList;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.LinesToCharsResult;
import name.fraser.neil.plaintext.diff_match_patch.Patch;

/** This class contains static methods for use with diff_match_patch. */
public final class DmpLibrary {

  /** A diff_match_patch for which context is 0; that is, Patch_Margin is 0. */
  private static final diff_match_patch DMP;

  static {
    DMP = new diff_match_patch();
    // This is essential when comparing via lines.
    DMP.Diff_EditCost = 0;
    DMP.Match_Threshold = 0.0f;
    DMP.Patch_DeleteThreshold = 0.0f;
  }

  /** Do not instantiate. */
  private DmpLibrary() {
    throw new UnsupportedOperationException("do not instantiate");
  }

  /**
   * Returns the diff of two multi-line strings, linewise.
   *
   * @param text1 a string
   * @param text2 a string
   * @return the differences
   */
  @SuppressWarnings({"NonApiType", "PMD.LooseCoupling"}) // diff_match_patch specifies LinkedList
  public static LinkedList<Diff> diffByLines(String text1, String text2) {
    // Convert each line to a single character.
    LinesToCharsResult a = DMP.diff_linesToChars(text1, text2);

    // Do a character-wise diff.
    LinkedList<Diff> diffs = DMP.diff_main(a.chars1, a.chars2, false);

    // Convert the character-wise diff back to lines.
    DMP.diff_charsToLines(diffs, a.lineArray);
    // Do not call `DMP.diff_cleanupSemantic(diffs)` because it adjusts boundaries.
    // All boundaries are currently at line ends, which is where we want them.

    return diffs;
  }

  /**
   * Format a diff_match_patch patch for debugging output.
   *
   * @param p a patch
   * @return the patch, formatted
   */
  public static String patchToString(Patch p) {
    return String.format(
        "Patch(diffs=%s, start1=%s, start2=%s, length1=%s, length2=%s)",
        p.diffs, p.start1, p.start2, p.length1, p.length2);
  }
}
