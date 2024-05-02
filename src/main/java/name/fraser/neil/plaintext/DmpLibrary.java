package name.fraser.neil.plaintext;

import java.util.LinkedList;
import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.LinesToCharsResult;
import name.fraser.neil.plaintext.diff_match_patch.Patch;

/** This class contains static methods for use with diff_match_patch. */
public class DmpLibrary {

  /** Do not instantiate. */
  private DmpLibrary() {
    throw new Error("do not instantiate");
  }

  /** A diff_match_patch for which context is 0; that is, Patch_Margin is 0. */
  protected static diff_match_patch dmp;

  static {
    dmp = new diff_match_patch();
    // This is essential when comparing via diffLineHash.
    dmp.Diff_EditCost = 0;
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;
  }

  /**
   * Returns the diff of two multi-line strings, linewise.
   *
   * @param text1 a string
   * @param text2 a string
   * @return the differences
   */
  @SuppressWarnings("NonApiType") // diff_match_patch specifies LinkedList
  public static LinkedList<Diff> diffByLines(String text1, String text2) {
    LinesToCharsResult a = dmp.diff_linesToChars(text1, text2);
    text1 = a.chars1;
    text2 = a.chars2;
    List<String> linearray = a.lineArray;

    LinkedList<Diff> diffs = dmp.diff_main(text1, text2, false);

    // Convert the diff back to original text.
    dmp.diff_charsToLines(diffs, linearray);
    // Do not call `dmp.diff_cleanupSemantic(diffs)` because it adjusts boundaries.
    // All boundaries are currently at line ends, which is where we want them.

    return diffs;
  }

  /**
   * Converts each line to a hash; converts each hash to a Unicode character; then diffs the
   * resulting strings. This is useful for understanding the diff relationship between two lines,
   * without knowing the content of each line.
   *
   * @param text1 a string
   * @param text2 a string
   * @return the differences (as one character per line)
   */
  @SuppressWarnings("NonApiType") // diff_match_patch specifies LinkedList
  public static LinkedList<Diff> diffLineHash(String text1, String text2) {
    LinesToCharsResult a = dmp.diff_linesToChars(text1, text2);
    String chars1 = a.chars1;
    String chars2 = a.chars2;

    LinkedList<Diff> diffs = dmp.diff_main(chars1, chars2, false);

    // Don't do `dmp.diff_cleanupSemantic(diffs);` because it changes "edit,equal,edit" into one
    // larger edit, which I do not want.

    return diffs;
  }

  /**
   * Format a patch for debugging output.
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
