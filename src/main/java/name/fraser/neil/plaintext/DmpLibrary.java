package name.fraser.neil.plaintext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.LinesToCharsResult;
import name.fraser.neil.plaintext.diff_match_patch.Patch;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.util.MPair;
import org.plumelib.util.OrderedPairIterator;

/** This class contains static methods for use with diff_match_patch. */
@SuppressWarnings({"UnusedMethod", "UnusedVariable", "NonApiType"}) // TEMPORARY
public class DmpLibrary {

  /** Do not instantiate. */
  private DmpLibrary() {
    throw new Error("do not instantiate");
  }

  /** A diff_match_patch for which context is 0; that is, Patch_Margin is 0. */
  static diff_match_patch dmp;

  static {
    dmp = new diff_match_patch();
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
  public static LinkedList<Diff> diffByLines(String text1, String text2) {
    LinesToCharsResult a = dmp.diff_linesToChars(text1, text2);
    text1 = a.chars1;
    text2 = a.chars2;
    List<String> linearray = a.lineArray;

    LinkedList<Diff> diffs = dmp.diff_main(text1, text2, false);

    // Convert the diff back to original text.
    dmp.diff_charsToLines(diffs, linearray);
    // Eliminate freak matches (e.g. blank lines)
    dmp.diff_cleanupSemantic(diffs);

    return diffs;
  }

  /**
   * Converts each line to a hash; converts each hash to a Unicode character; then diffs the
   * resulting strings. This is useful for understanding the diff relationship between two lines,
   * without knowing the content of each line.
   *
   * @param text1 a string
   * @param text2 a string
   * @return the differences
   */
  private static LinkedList<Diff> diffLineHash(String text1, String text2) {
    LinesToCharsResult a = dmp.diff_linesToChars(text1, text2);
    text1 = a.chars1;
    text2 = a.chars2;

    LinkedList<Diff> diffs = dmp.diff_main(text1, text2, false);

    // Eliminate freak matches (e.g. blank lines)
    dmp.diff_cleanupSemantic(diffs);

    return diffs;
  }

  /**
   * Returns a list of the line numbers of text1 that were modified in text2.
   *
   * @param text1 a multi-line string
   * @param text2 a multi-line string
   * @return the zero-based line numbers of text1 that were changed
   */
  public static List<Integer> affectedLines(String text1, String text2) {
    LinkedList<Diff> diffs = DmpLibrary.diffLineHash(text1, text2);

    // Don't use Patch objects because they can have a leading and trailing EQUAL Diff within.
    int lineNumber = 0;
    List<Integer> result = new ArrayList<>();
    for (Diff d : diffs) {
      switch (d.operation) {
        case EQUAL -> lineNumber += d.text.length();
        case DELETE -> {
          int len = d.text.length();
          for (int i = 0; i < len; i++) {
            result.add(lineNumber);
            lineNumber++;
          }
        }
        case INSERT -> {}
      }
    }
    return result;
  }

  /**
   * Returns true if the left and right edits overlap -- that is, both of them change the same line.
   *
   * @param base the base text
   * @param left the left text
   * @param right the right text
   * @return true if the left and right edits overlap
   */
  public static boolean affectedLinesOverlap(String base, String left, String right) {
    List<Integer> affectedLinesLeft = affectedLines(base, left);
    List<Integer> affectedLinesRight = affectedLines(base, right);
    Iterator<MPair<@Nullable Integer, @Nullable Integer>> pairItor =
        new OrderedPairIterator<Integer>(
            affectedLinesLeft.iterator(), affectedLinesRight.iterator());
    while (pairItor.hasNext()) {
      MPair<@Nullable Integer, @Nullable Integer> pair = pairItor.next();
      if (pair.first != null && pair.second != null) {
        return true;
      }
    }
    return false;
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
