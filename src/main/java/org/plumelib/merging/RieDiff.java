package org.plumelib.merging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/**
 * A RieDiff is one of the operations "equal", "replace", or "insert".
 *
 * <p>I am hopeful this representation will be easier to work with.
 *
 * <p>The edit operations of many diff tools are "insert", "delete", and "equal".
 */
@SuppressWarnings({"index:argument", "lowerbound:argument"})
public abstract class RieDiff {

  /**
   * Returns the text that the operation processes.
   *
   * @return the text that the operation processes
   */
  public abstract String preText();

  /**
   * Returns the text that the operat produces.
   *
   * @return the text that the operation produces
   */
  public abstract String postText();

  /**
   * Returns the size of the text that the operation processes.
   *
   * @return the size of the text that the operation processes
   */
  public int preLength() {
    return preText().length();
  }

  /**
   * Returns the size of the text that the operat produces.
   *
   * @return the size of the text that the operation produces
   */
  public int postLength() {
    return postText().length();
  }

  /**
   * Returns true if this RieDiff supports splitting.
   *
   * @return true if this RieDiff supports splitting
   */
  public boolean canSplit() {
    return false;
  }

  /**
   * Returns a RieDiff that is the prefix of this one, with the given preLength.
   *
   * <p>For any RieDiff {@code r}, the effect of {@code r} is the same as the combined effect of
   * {@code r.beforeSplit(n)} and {@code r.afterSplit(n)}.
   *
   * @param len the length of the returned RieDiff
   * @return a RieDiff that does the first {@code len} edits of this one
   */
  public RieDiff beforeSplit(int len) {
    throw new Error("Don't split " + this);
  }

  /**
   * Returns a RieDiff that is the suffix of this one, after the given preLength.
   *
   * <p>For any RieDiff {@code r}, the effect of {@code r} is the same as the combined effect of
   * {@code r.beforeSplit(n)} and {@code r.afterSplit(n)}.
   *
   * @param len where the returned RieDiff starts (within this one)
   * @return a RieDiff that does all but the first {@code len} edits of this one
   */
  public RieDiff afterSplit(int len) {
    throw new Error("Don't split " + this);
  }

  /** A replacement operation. */
  public static class Replace extends RieDiff {
    /** The text being replaced. */
    String before;

    /** The replacement text. */
    String after;

    /**
     * Creates a Replace operation.
     *
     * @param before the text being replaced
     * @param after the replacement text
     */
    private Replace(String before, String after) {
      this.before = before;
      this.after = after;
    }

    /**
     * Creates a Replace operation. May return an Equal operation instead.
     *
     * @param before the text being replaced
     * @param after the replacement text
     * @return an operation for the replacement
     */
    public static RieDiff of(String before, String after) {
      if (before.equals(after)) {
        if (before.equals("")) {
          return NoOp.it;
        } else {
          return new Equal(before);
        }
      } else {
        return new Replace(before, after);
      }
    }

    @Override
    public String preText() {
      return before;
    }

    @Override
    public String postText() {
      return after;
    }

    @Override
    public String toString(@GuardSatisfied Replace this) {
      return "Replace{"
          + StringsPlume.escapeNonASCII(before)
          + " -> "
          + StringsPlume.escapeNonASCII(after)
          + "}";
    }
  }

  // TODO: Represent this via a replacement operation?
  /** An insertion operation. */
  public static class Insert extends RieDiff {
    /** The text being inserted. */
    String text;

    /**
     * Creates an insertion operation
     *
     * @param text the text being inserted
     */
    public Insert(String text) {
      this.text = text;
    }

    @Override
    public String preText() {
      return "";
    }

    @Override
    public String postText() {
      return text;
    }

    @Override
    public String toString(@GuardSatisfied Insert this) {
      return "Insert{" + StringsPlume.escapeNonASCII(text) + "}";
    }
  }

  /** An equality operation. */
  public static class Equal extends RieDiff {
    /** The text that is unchanged. */
    String text;

    /**
     * Creates an equality operation.
     *
     * @param text the text that is unchanged
     */
    public Equal(String text) {
      this.text = text;
    }

    @Override
    public String preText() {
      return text;
    }

    @Override
    public String postText() {
      return text;
    }

    @Override
    public RieDiff beforeSplit(int len) {
      assert 0 < len;
      assert len < text.length();
      return new Equal(text.substring(0, len));
    }

    @Override
    public RieDiff afterSplit(int len) {
      assert 0 < len;
      assert len < text.length();
      return new Equal(text.substring(len));
    }

    @Override
    public String toString(@GuardSatisfied Equal this) {
      return "Equal{" + StringsPlume.escapeNonASCII(text) + "}";
    }
  }

  /** A no-op operation, which transforms "" into "". */
  public static class NoOp extends RieDiff {

    /** The no-op operation. */
    public static final NoOp it = new NoOp();

    /** Creates a no-op operation. */
    private NoOp() {}

    @Override
    public String preText() {
      return "";
    }

    @Override
    public String postText() {
      return "";
    }

    @Override
    public String toString(@GuardSatisfied NoOp this) {
      return "NoOp{}";
    }
  }

  /**
   * Converts a list of diff_match_patch.Diff to a list of RieDiff.
   *
   * @param diffs a list of diff_match_patch.Diff
   * @return an equivalent list of RieDiff
   */
  static List<RieDiff> diffsToRieDiffs(List<Diff> diffs) {
    List<RieDiff> result = new ArrayList<>();

    // prev is always a deletion
    Diff prev = null;
    for (Diff diff : diffs) {
      switch (diff.operation) {
        case DELETE -> {
          if (prev != null) {
            prev = new Diff(Operation.DELETE, prev.text + diff.text);
          } else {
            prev = diff;
          }
        }
        case INSERT -> {
          if (prev != null) {
            result.add(Replace.of(prev.text, diff.text));
            prev = null;
          } else {
            result.add(new Insert(diff.text));
          }
        }
        case EQUAL -> {
          if (prev != null) {
            result.add(Replace.of(prev.text, ""));
            prev = null;
          }
          result.add(new Equal(diff.text));
        }
      }
    }

    if (prev != null) {
      result.add(Replace.of(prev.text, ""));
      prev = null;
    }

    return result;
  }

  // TODO: Make the resulting lists have equal length?
  /**
   * Breaks {@link Equal} RieDiffs so that, for every RieDiff in either output list, there is
   * RieDiff in the other output list that starts in the same character location (in the original
   * text). If this is not possible, return null.
   *
   * @param edits1 edits to a text
   * @param edits2 different edits to the same text
   * @return new lists with aligned diffs
   */
  static @Nullable IPair<List<RieDiff>, List<RieDiff>> align(
      List<RieDiff> edits1, List<RieDiff> edits2) {
    Iterator<RieDiff> itor1 = edits1.iterator();
    Iterator<RieDiff> itor2 = edits2.iterator();
    RieDiff edit1 = itor1.hasNext() ? itor1.next() : null;
    RieDiff edit2 = itor2.hasNext() ? itor2.next() : null;

    // Invariant: The sum of preLengths of elements of `result1` and `result2` are equal.
    List<RieDiff> result1 = new ArrayList<>();
    List<RieDiff> result2 = new ArrayList<>();

    while (edit1 != null || edit2 != null) {
      if (edit1 == null) {
        assert edit2 != null : "@AssumeAssertion(nullness): at most one editN is null";
        assert edit2.preLength() == 0;
        result1.add(NoOp.it);
        result2.add(edit2);
        edit2 = itor2.hasNext() ? itor2.next() : null;
        continue;
      } else if (edit2 == null) {
        assert edit1 != null : "@AssumeAssertion(nullness): at most one editN is null";
        assert edit1.preLength() == 0;
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(NoOp.it);
        continue;
      }
      int preLen1 = edit1.preLength();
      int preLen2 = edit2.preLength();

      if (preLen1 == preLen2) {
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(edit2);
        edit2 = itor2.hasNext() ? itor2.next() : null;
      } else if (preLen1 == 0) {
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(NoOp.it);
      } else if (preLen2 == 0) {
        result1.add(NoOp.it);
        result2.add(edit2);
        edit2 = itor2.hasNext() ? itor2.next() : null;
      } else if (preLen1 < preLen2) {
        if (!edit2.canSplit()) {
          return null;
        }
        int len = edit1.preLength();
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(edit2.afterSplit(len));
        edit2 = edit2.afterSplit(len);
      } else if (preLen1 > preLen2) {
        if (!edit1.canSplit()) {
          return null;
        }
        int len = edit2.preLength();
        result1.add(edit1.beforeSplit(len));
        edit1 = edit1.afterSplit(len);
        result2.add(edit2);
        edit2 = itor2.hasNext() ? itor2.next() : null;
      } else {
        String message =
            String.format(
                "preLen1=%d preLen2=%d edit1=%s edit2=%s", preLen1, preLen2, edit1, edit2);
        throw new Error(message);
      }
    }

    return IPair.of(result1, result2);
  }
}
