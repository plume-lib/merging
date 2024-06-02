package org.plumelib.merging.fileformat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.util.IPair;
import org.plumelib.util.StringsPlume;

/**
 * A RDiff is one of the operations "equal", "replace", or "insert".
 *
 * <p>By contrast, the edit operations of {@code diff} and of {@link diff_match_patch} are "insert",
 * "delete", and "equal".
 */
@SuppressWarnings({"index:argument", "lowerbound:argument"})
public abstract class RDiff {

  /** Creates a new RDiff. */
  private RDiff() {}

  /**
   * Returns an RDiff that replaces {@code before} by {@code after}. The result might be an Equal,
   * NoOp, or Replace operation.
   *
   * @param before the text to be replaced
   * @param after the replacement text
   * @return an RDiff that replaces {@code before} by {@code after}
   */
  public static RDiff of(String before, String after) {
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

  /**
   * Converts a list of diff_match_patch.Diff to a list of RDiff.
   *
   * @param diffs a list of diff_match_patch.Diff
   * @return an equivalent list of RDiff
   */
  public static List<RDiff> diffsToRDiffs(List<Diff> diffs) {
    List<RDiff> result = new ArrayList<>();

    // `prev` is always a deletion operation or null.
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
            result.add(RDiff.of(prev.text, diff.text));
            prev = null;
          } else {
            result.add(new Insert(diff.text));
          }
        }
        case EQUAL -> {
          if (prev != null) {
            result.add(RDiff.of(prev.text, ""));
            prev = null;
          }
          result.add(new Equal(diff.text));
        }
      }
    }

    if (prev != null) {
      result.add(RDiff.of(prev.text, ""));
      prev = null;
    }

    return result;
  }

  /**
   * Returns the text that the operation processes.
   *
   * @return the text that the operation processes
   */
  public abstract String preText();

  /**
   * Returns the text that the operation produces.
   *
   * @return the text that the operation produces
   */
  public abstract String postText();

  /**
   * Returns true if this is a NoOp.
   *
   * @return true if this is a NoOp
   */
  public boolean isNoOp() {
    return false;
  }

  /**
   * Returns a RDiff that has the effect of this followed by {@code other}.
   *
   * @param other the rdiff to append to this one
   * @return a RDiff that has the effect of this followed by {@code other}
   */
  public RDiff merge(RDiff other) {
    return of(this.preText() + other.preText(), this.postText() + other.postText());
  }

  /**
   * Return a pair of RDiffs that are together equivalent to this one. The first one's before and
   * after texts match the given pattern, or are the empty string.
   *
   * @param p a pattern that matches text that should be in the first part. It must be of the form
   *     "^(PATTERN).*$, where the PATTERN subpattern matches text that should be in the first part.
   * @return a pair of RDiffs that are together equivalent to this one
   */
  @SuppressWarnings("nullness:dereference.of.nullable") // p is @Regex(1) => group(1) is non-null
  public IPair<RDiff, RDiff> prefixSplit(@Regex(1) Pattern p) {
    Matcher m;

    String before = preText();
    String before1;
    String before2;
    m = p.matcher(before);
    if (m.matches()) {
      before1 = m.group(1);
      before2 = before.substring(before1.length());
    } else {
      before1 = "";
      before2 = before;
    }

    String after = postText();
    String after1;
    String after2;
    m = p.matcher(after);
    if (m.matches()) {
      after1 = m.group(1);
      after2 = after.substring(after1.length());
    } else {
      after1 = "";
      after2 = after;
    }

    return IPair.of(RDiff.of(before1, after1), RDiff.of(before2, after2));
  }

  /**
   * Return a pair of RDiffs that are together equivalent to this one. The second one's before and
   * after texts match the given pattern, or are the empty string.
   *
   * @param p a pattern that matches characters that should be in the second part. It must be of the
   *     form "^.*?(PATTERN)$", where the PATTERN subpattern matches text that should be in the
   *     second part.
   * @return a pair of RDiffs that are together equivalent to this one
   */
  @SuppressWarnings("nullness:dereference.of.nullable") // p is @Regex(1) => group(1) is non-null
  public IPair<RDiff, RDiff> suffixSplit(@Regex(1) Pattern p) {
    Matcher m;

    String before = preText();
    String before1;
    String before2;
    m = p.matcher(before);
    if (m.matches()) {
      before2 = m.group(1);
      before1 = before.substring(0, before.length() - before2.length());
      assert before.length() == before1.length() + before2.length();
    } else {
      before1 = before;
      before2 = "";
    }

    String after = postText();
    String after1;
    String after2;
    m = p.matcher(after);
    if (m.matches()) {
      after2 = m.group(1);
      after1 = after.substring(0, after.length() - after2.length());
    } else {
      after1 = after;
      after2 = "";
    }

    return IPair.of(RDiff.of(before1, after1), RDiff.of(before2, after2));
  }

  /**
   * Returns true if this RDiff supports splitting at arbitrary locations.
   *
   * @return true if this RDiff supports splitting at arbitrary locations
   */
  public boolean canSplit() {
    return false;
  }

  /**
   * Returns a RDiff that is the prefix of this one, with the given preLength.
   *
   * <p>For any RDiff {@code r}, the effect of {@code r} is the same as the combined effect of
   * {@code r.beforeSplit(n)} and {@code r.afterSplit(n)}.
   *
   * @param len the length of the returned RDiff
   * @return a RDiff that does the first {@code len} operations of this one
   */
  public RDiff beforeSplit(int len) {
    throw new Error("Don't split " + this);
  }

  /**
   * Returns a RDiff that is the suffix of this one, after the given preLength.
   *
   * <p>For any RDiff {@code r}, the effect of {@code r} is the same as the combined effect of
   * {@code r.beforeSplit(n)} and {@code r.afterSplit(n)}.
   *
   * @param len where the returned RDiff starts (within this one)
   * @return a RDiff that does all but the first {@code len} operations of this one
   */
  public RDiff afterSplit(int len) {
    throw new Error("Don't split " + this);
  }

  /** A replacement operation. */
  public static class Replace extends RDiff {
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

  // TODO: Is this needed, or can it be represented by a "replace" with "" as its pre-text?
  /** An insertion operation. */
  public static class Insert extends RDiff {
    /** The text being inserted. */
    String text;

    /**
     * Creates an insertion operation
     *
     * @param text the text being inserted
     */
    private Insert(String text) {
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
  public static class Equal extends RDiff {
    /** The text that is unchanged. */
    String text;

    /**
     * Creates an equality operation.
     *
     * @param text the text that is unchanged
     */
    private Equal(String text) {
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
    public boolean canSplit() {
      return true;
    }

    @Override
    public RDiff beforeSplit(int len) {
      assert 0 < len;
      assert len < text.length();
      return new Equal(text.substring(0, len));
    }

    @Override
    public RDiff afterSplit(int len) {
      assert 0 < len;
      assert len < text.length();
      return new Equal(text.substring(len));
    }

    @Override
    public String toString(@GuardSatisfied Equal this) {
      return "Equal{" + StringsPlume.escapeNonASCII(text) + "}";
    }
  }

  // TODO: Is NoOp necessary?  Experimentally remove it to find out.
  /** A no-op operation, which transforms "" into "". */
  public static @Interned class NoOp extends RDiff {

    /** The no-op operation. */
    @SuppressWarnings("interning:interned.object.creation") // create the singleton object
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
    public boolean isNoOp() {
      return true;
    }

    @Override
    public String toString(@GuardSatisfied NoOp this) {
      return "NoOp{}";
    }
  }

  /**
   * Breaks {@link Equal} RDiffs into smaller ones so that, for every RDiff in either output list,
   * there is an RDiff in the other output list that starts in the same character location (in the
   * original text). In other words, each result list has the same length, and each corresponding
   * pair of RDiffs have the same pre-length. If this is not possible, return null.
   *
   * @param edits1 edits to a text
   * @param edits2 different edits to the same text
   * @return new lists with aligned diffs
   */
  public static @Nullable IPair<List<RDiff>, List<RDiff>> align(
      List<RDiff> edits1, List<RDiff> edits2) {
    Iterator<RDiff> itor1 = edits1.iterator();
    Iterator<RDiff> itor2 = edits2.iterator();
    RDiff edit1 = itor1.hasNext() ? itor1.next() : null;
    RDiff edit2 = itor2.hasNext() ? itor2.next() : null;

    // Invariant: The sum of preLengths of elements of `result1` and `result2` are equal.
    List<RDiff> result1 = new ArrayList<>();
    List<RDiff> result2 = new ArrayList<>();

    while (edit1 != null || edit2 != null) {
      if (edit1 == null) {
        assert edit2 != null : "@AssumeAssertion(nullness): at most one editN is null";
        assert edit2.preText().isEmpty();
        result1.add(NoOp.it);
        result2.add(edit2);
        edit2 = itor2.hasNext() ? itor2.next() : null;
        continue;
      } else if (edit2 == null) {
        assert edit1 != null : "@AssumeAssertion(nullness): at most one editN is null";
        assert edit1.preText().isEmpty();
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(NoOp.it);
        continue;
      }
      int preLen1 = edit1.preText().length();
      int preLen2 = edit2.preText().length();

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
        result1.add(edit1);
        edit1 = itor1.hasNext() ? itor1.next() : null;
        result2.add(edit2.beforeSplit(preLen1));
        edit2 = edit2.afterSplit(preLen1);
      } else if (preLen1 > preLen2) {
        if (!edit1.canSplit()) {
          return null;
        }
        result1.add(edit1.beforeSplit(preLen2));
        edit1 = edit1.afterSplit(preLen2);
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
