package org.plumelib.merging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import org.checkerframework.checker.index.qual.LengthOf;
import org.checkerframework.checker.interning.qual.InternedDistinct;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

// TODO: I suspect a recursive implementation, rather than an iterative implementation that fills in
// the whole table, would be more efficient because it would explore less of the space.

// TODO: I could store, in the table, just the "move" (one of the 7 operations) rather than the
// whole list.  The algorithm would reconstruct the list at the end.  This would save space, but it
// would be harder to debug.  And it might not save all that much space, because most elements are
// IMPOSSIBLE anyway.

/** Uses dynamic programming to merge three texts. */
public class AdjacentDynamicProgramming {

  // The input is three lists A, C (= base), and B.

  // The algorithm tries to consume the same element from the beginning of all three lists, until
  // all the lists are empty.
  // However, it's OK if only two of the three elements are the same.

  // Let the three lengths be aL, bL, cL.  If (aL > bL + cL) || (bL > aL + cL) || (cL > aL + cL),
  // then no solution is possible.
  // We can apply this rule to the lists yet to process, or to the amount of the lists that have
  // been processed so far.

  // When consuming list elements, there are 7 possible operations, each of which produces one
  // element of output:
  //  1. Consume A, C, B; a = c = b; the (partial) output is a (no edits)
  //  2. Consume A, C, B; a = c    ; the (partial) output is b (B changed)
  //  3. Consume A, C, B; a   =   b; the (partial) output is a (both changed in the same way)
  //  4. Consume A, C, B;     c = b; the (partial) output is a (A changed)
  //  5. Consume A, C   ; a = c    ; the (partial) output is empty (B deleted)
  //  6. Consume A,    B; a   =   b; the (partial) output is a (both inserted in the same way)
  //  7. Consume    C, B;     c = b; the (partial) output is empty (A deleted)
  // As a heuristic to produce better merges, if a = c = b, the first case must be chosen and the
  // other 6 cases are forbidden.
  // Also as a heuristic, there is no operation "insert line" (or "delete line") that matches one
  // line in one text to zero lines in both other texts.  Permitting it leads to too many
  // undesirable possibilities, and to ambiguity as well.

  // The data structure is a table with dimensions [ a.size()+1, b.size()+1, c.size()+1 ].
  // Each cell contains a list (or null, indicating that no solution is possible).

  // The table is filled in from the beginning toward the end.
  // Its semantics is:
  //   After consuming up to the given indices (exclusive) in the respective input lists,
  //   the output is as given in the table.
  // The value of table[0, 0, 0] is the empty list.
  // The value of table[a.size(), b.size(), c.size()] is the merged output.

  /** If true, print diagnostic output. */
  private static final boolean debug = false;

  /** The maximum table size that will be attempted. */
  private static int MAX_TABLE_SIZE = 10_000_000;

  /** Indicates that a given table entry is unreachable. */
  @SuppressWarnings("interning:assignment") // unique assignment
  // Cannot use `Collections.singletonList("IMPOSSIBLE")` because that isn't of type ArrayList,
  // which is the only thing that can be inserted into `table`.
  private @InternedDistinct List<String> IMPOSSIBLE = new ArrayList<>(1);

  /** The first parent. */
  List<String> a;

  /** The base. */
  List<String> c;

  /** The second parent. */
  List<String> b;

  /** The length of the first parent. */
  @LengthOf("a") int aLen;

  /** The length of the base. */
  @LengthOf("c") int cLen;

  /** The length of the second parent. */
  @LengthOf("b") int bLen;

  /** The table. */
  List<String> @MonotonicNonNull [][][] table;

  /**
   * Creates a new AdjacentDynamicProgramming.
   *
   * @param a the first parent
   * @param b the base
   * @param c the second parent
   */
  public AdjacentDynamicProgramming(List<String> a, List<String> c, List<String> b) {
    IMPOSSIBLE.add("IMPOSSIBLE");
    this.a = a;
    this.c = c;
    this.b = b;
    aLen = a.size();
    cLen = c.size();
    bLen = b.size();
    if (!possibleLengths(aLen, cLen, bLen)) {
      table = null;
    } else if (aLen * cLen * bLen > MAX_TABLE_SIZE) {
      table = null;
    } else {
      @SuppressWarnings("unchecked")
      ArrayList<String>[][][] tmpTable =
          (ArrayList<String>[][][]) new ArrayList<?>[aLen + 1][cLen + 1][bLen + 1];
      table = tmpTable;
    }
  }

  /**
   * Computes the merge of the three lists, by finding a correspondence between lines.
   *
   * @return the merge of the three lists, or null if none can be found
   */
  public @Nullable List<String> compute() {
    if (table == null) {
      return null;
    }
    fillInTable();
    if (debug) {
      System.out.println(tableToString());
    }
    List<String> result = table[aLen][cLen][bLen];
    if (result == IMPOSSIBLE) {
      return null;
    } else {
      return result;
    }
  }

  /** Fills in the table. */
  @RequiresNonNull("table")
  private void fillInTable() {
    fillInZeroes();
    for (int iA = 1; iA <= aLen; iA++) {
      for (int iC = 1; iC <= cLen; iC++) {
        for (int iB = 1; iB <= bLen; iB++) {
          fillIn(iA, iC, iB);
        }
      }
    }
  }

  /** Fills in the table, for all cells where at least one index is zero. */
  @RequiresNonNull("table")
  private void fillInZeroes() {
    // three zero indices
    table[0][0][0] = new ArrayList<>(0);

    // two zero indices
    for (int iA = 1; iA <= aLen; iA++) {
      if (table[iA][0][0] != null) {
        throw new Error(String.format("Already computed: %d %d %d", iA, 0, 0));
      }
      table[iA][0][0] = IMPOSSIBLE;
    }
    for (int iC = 1; iC <= cLen; iC++) {
      if (table[0][iC][0] != null) {
        throw new Error(String.format("Already computed: %d %d %d", 0, iC, 0));
      }
      table[0][iC][0] = IMPOSSIBLE;
    }
    for (int iB = 1; iB <= bLen; iB++) {
      if (table[0][0][iB] != null) {
        throw new Error(String.format("Already computed: %d %d %d", 0, 0, iB));
      }
      table[0][0][iB] = IMPOSSIBLE;
    }

    // TODO: should the "one zero index" be handled in a uniform way?  Maybe...

    // one zero index
    for (int iC = 1; iC <= cLen; iC++) {
      for (int iB = 1; iB <= bLen; iB++) {
        if (table[0][iC][iB] != null) {
          throw new Error(String.format("Already computed: %d %d %d", 0, iC, iB));
        }
        if (iC != iB) {
          table[0][iC][iB] = IMPOSSIBLE;
          continue;
        }
        List<String> prev = table[0][iC - 1][iB - 1];
        if (prev == IMPOSSIBLE) {
          table[0][iC][iB] = IMPOSSIBLE;
          continue;
        }
        String cElt = c.get(iC - 1);
        String bElt = b.get(iB - 1);
        table[0][iC][iB] = cElt.equals(bElt) ? prev : IMPOSSIBLE;
      }
    }
    for (int iA = 1; iA <= aLen; iA++) {
      for (int iB = 1; iB <= bLen; iB++) {
        if (table[iA][0][iB] != null) {
          throw new Error(String.format("Already computed: %d %d %d", iA, 0, iB));
        }
        if (iA != iB) {
          table[iA][0][iB] = IMPOSSIBLE;
          continue;
        }
        List<String> prev = table[iA - 1][0][iB - 1];
        if (prev == IMPOSSIBLE) {
          table[iA][0][iB] = IMPOSSIBLE;
        }
        String aElt = a.get(iA - 1);
        String bElt = b.get(iB - 1);
        table[iA][0][iB] = aElt.equals(bElt) ? concatenate(prev, aElt) : IMPOSSIBLE;
      }
    }
    for (int iA = 1; iA <= aLen; iA++) {
      for (int iC = 1; iC <= cLen; iC++) {
        if (table[iA][iC][0] != null) {
          throw new Error(String.format("Already computed: %d %d %d", iA, iC, 0));
        }
        if (iA != iC) {
          table[iA][iC][0] = IMPOSSIBLE;
          continue;
        }
        List<String> prev = table[iA - 1][iC - 1][0];
        if (prev == IMPOSSIBLE) {
          table[iA][iC][0] = IMPOSSIBLE;
        }
        String aElt = a.get(iA - 1);
        String cElt = c.get(iC - 1);
        table[iA][iC][0] = aElt.equals(cElt) ? prev : IMPOSSIBLE;
      }
    }
  }

  /**
   * Fill in the given element of the table.
   *
   * <p>Table indices are 1 larger than the corresponding list indices.
   *
   * @param iA the first index in the table
   * @param iC the second index in the table
   * @param iB the third index in the table
   */
  // TODO: should this return a list instead of void?
  @RequiresNonNull("table")
  private void fillIn(int iA, int iC, int iB) {
    if (debug) {
      System.out.printf("fillIn(%d, %d, %d)%n", iA, iC, iB);
    }
    if (table[iA][iC][iB] != null) {
      throw new Error(String.format("Already computed: %d %d %d", iA, iC, iB));
    }

    if (!possibleIndices(iA, iC, iB, aLen, cLen, bLen)) {
      table[iA][iC][iB] = IMPOSSIBLE;
      return;
    }

    String aElt = a.get(iA - 1);
    String cElt = c.get(iC - 1);
    String bElt = b.get(iB - 1);
    boolean acEqual = aElt.equals(cElt);
    boolean cbEqual = cElt.equals(bElt);
    boolean abEqual = aElt.equals(bElt);

    // Go through each of the 7 possibilities.
    List<String> prev = table[iA - 1][iC - 1][iB - 1];
    List<String> prev5 = table[iA - 1][iC - 1][iB];
    List<String> prev6 = table[iA - 1][iC][iB - 1];
    List<String> prev7 = table[iA][iC - 1][iB - 1];
    if (prev == null) {
      throw new Error();
    }
    if (prev5 == null) {
      throw new Error();
    }
    if (prev6 == null) {
      throw new Error();
    }
    if (prev7 == null) {
      throw new Error();
    }

    // Case 1
    if (acEqual && cbEqual && prev != IMPOSSIBLE) {
      if (!abEqual) {
        throw new Error();
      }
      table[iA][iC][iB] = concatenate(prev, aElt);
      return;
    }

    List<String> result2 = acEqual ? concatenate(prev, bElt) : IMPOSSIBLE;
    List<String> result3 = abEqual ? concatenate(prev, aElt) : IMPOSSIBLE;
    List<String> result4 = cbEqual ? concatenate(prev, aElt) : IMPOSSIBLE;

    if (debug) {
      System.out.printf("aElt=%s%n", aElt);
      System.out.printf("cElt=%s%n", cElt);
      System.out.printf("bElt=%s%n", bElt);
      System.out.printf("prev=%s%n", pathToString(prev));
      System.out.printf("result2=%s%n", pathToString(result2));
      System.out.printf("result3=%s%n", pathToString(result3));
      System.out.printf("result4=%s%n", pathToString(result4));
    }

    List<String> result5 = acEqual ? prev5 : IMPOSSIBLE;
    List<String> result6 = abEqual ? concatenate(prev6, aElt) : IMPOSSIBLE;
    List<String> result7 = cbEqual ? prev7 : IMPOSSIBLE;

    try {
      List<String> result = uniquePossible(result2, result3, result4, result5, result6, result7);
      table[iA][iC][iB] = result;
    } catch (Throwable t) {
      throw new Error(
          String.format(
              "problem in fillIn(%d, %d, %d) aElt=%s bElt=%s cElt=%s",
              iA, iC, iB, aElt, cElt, bElt),
          t);
    }
  }

  /**
   * Returns true if lists of the given lengths have a possibility of being aligned by the
   * algorithm.
   *
   * @param len1 a length
   * @param len2 a length
   * @param len3 a length
   * @return true if lists of the given lengths have a possibility of being aligned by the algorithm
   */
  static boolean possibleLengths(int len1, int len2, int len3) {
    if (len1 > len2 + len3) {
      return false;
    }
    if (len2 > len1 + len3) {
      return false;
    }
    if (len3 > len1 + len2) {
      return false;
    }
    return true;
  }

  /**
   * Returns true if the given indices into lists of the given lengths have a possibility of being
   * aligned by the algorithm.
   *
   * @param i1 the first index
   * @param i2 the second index
   * @param i3 the third index
   * @param len1 the first length
   * @param len2 the second length
   * @param len3 the third length
   * @return true if lists of the given lengths have a possibility of being aligned by the algorithm
   */
  static boolean possibleIndices(int i1, int i2, int i3, int len1, int len2, int len3) {
    return possibleLengths(i1, i2, i3) && possibleLengths(len1 - i1, len2 - i2, len3 - i3);
  }

  /**
   * Returns the unique element of the list that is not IMPOSSIBLE. Returns IMPOSSIBLE if all
   * elements of the list are IMPOSSIBLE.
   *
   * @param args elements that might or might not be IMPOSSIBLE
   * @return the unique element of the list that is not IMPOSSIBLE, or IMPOSSIBLE
   */
  @RequiresNonNull("table")
  @SafeVarargs
  final List<String> uniquePossible(List<String>... args) {
    List<String> result = IMPOSSIBLE;
    for (List<String> elt : args) {
      if (result == IMPOSSIBLE) {
        result = elt;
      } else if (elt == IMPOSSIBLE) {
        // nothing to do
      } else if (result.equals(elt)) {
        // nothing to do
      } else {
        System.out.println(tableToString());
        @SuppressWarnings("varargs")
        String msg = "Multiple possibilities in " + Arrays.toString(args);
        throw new Error(msg);
      }
    }
    return result;
  }

  /**
   * Returns a new list that contains the given element at the end.
   *
   * @param lst a list
   * @param elt an element
   * @return a list containing {@code lst} then {@code elt}
   */
  List<String> concatenate(List<String> lst, String elt) {
    if (lst == null) {
      throw new Error();
    }
    if (lst == IMPOSSIBLE) {
      return IMPOSSIBLE;
    }
    List<String> result = new ArrayList<>(lst.size() + 1);
    result.addAll(lst);
    result.add(elt);
    return result;
  }

  /**
   * Formats IMPOSSIBLE as "-".
   *
   * @param lst a list
   * @return its printed representation
   */
  private String pathToString(List<String> lst) {
    if (lst == IMPOSSIBLE) {
      return "-";
    } else {
      return lst.toString();
    }
  }

  /**
   * Returns a string representation of the table.
   *
   * @return a string representation of the table
   */
  @RequiresNonNull("table")
  String tableToString() {
    String lineSep = System.lineSeparator();
    StringBuilder sb = new StringBuilder();
    for (int iA = 0; iA <= aLen; iA++) {
      sb.append("iA=").append(iA).append(':').append(lineSep);
      for (int iC = 0; iC <= cLen; iC++) {
        sb.append("iC=").append(iC).append(": ");
        StringJoiner sjB = new StringJoiner("; ");
        for (int iB = 0; iB <= bLen; iB++) {
          sjB.add("iB=" + iB + ":" + pathToString(table[iA][iC][iB]));
        }
        sb.append(sjB).append(lineSep);
      }
    }
    return sb.toString();
  }
}
