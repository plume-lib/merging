// It seems that JGit's MergeResult is produced only by its own tools; that is, one cannot create a
// MergeResult by parsing a conflicted file.

package org.plumelib.merging;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.FilesPlume;
import org.plumelib.util.StringsPlume;

/**
 * Represents a file that may contain conflict markers.
 *
 * <p>It is either erroneous, or it is a sequence of ConflictElement objects.
 *
 * <p>There are two general forms of conflicted sections of a file:
 *
 * <p>diff style:
 *
 * <pre>{@code
 * <<<<<<< HEAD
 * puts 'hola world'
 * =======
 * puts 'hello mundo'
 * >>>>>>> mundo
 * }</pre>
 *
 * <p>diff3 style:
 *
 * <pre>{@code
 * <<<<<<< ours
 * puts 'hola world'
 * ||||||| base
 * puts 'hello world'
 * =======
 * puts 'hello mundo'
 * >>>>>>> theirs
 * }</pre>
 */
@SuppressWarnings("lock") // todo
public class ConflictedFile {

  // /** If true, output diagnostic information for debugging. */
  // private static final boolean verbose = false;

  // At least one of fileContents, lines, and hunks is always non-null.
  /** The file contents, as a single string. Includes conflict markers. */
  private @MonotonicNonNull String fileContents;

  /** The lines of the file, including conflict markers. */
  private @MonotonicNonNull List<String> lines;

  /**
   * The contents of the conflicted file. These aren't really hunks. They are interspersed conflict
   * hunks and common lines.
   */
  private @MonotonicNonNull List<ConflictElement> hunks;

  /** The error message indicating why the file could not be parsed, or null. */
  private @MonotonicNonNull String parseError;

  /** True if the {@link #hasConflict} variable has been initialized. */
  private boolean hasConflictInitialized = false;

  /** True if the file contains a conflict. */
  private boolean hasConflict = false;

  /** True if the file had trivial conflicts that were resolved. */
  private boolean hasTrivalConflict = false;

  /**
   * Create a new ConflictedFile.
   *
   * @param lines the lines in the conflicted file, including conflict markers
   * @param hunks the contents of the conflicted file
   */
  public ConflictedFile(List<String> lines, List<ConflictElement> hunks) {
    this.fileContents = null;
    this.lines = lines;
    this.hunks = hunks;
    this.parseError = null;
  }

  /**
   * Create a new erroneous ConflictedFile.
   *
   * @param lines the lines in the conflicted file, including conflict markers
   * @param parseError the error message
   */
  public ConflictedFile(List<String> lines, String parseError) {
    this.fileContents = null;
    this.lines = lines;
    this.hunks = null;
    this.parseError = parseError;
    this.hasConflictInitialized = true;
    this.hasConflict = true;
  }

  /**
   * Create a new ConflictedFile.
   *
   * @param lines the lines in the conflicted file, including conflict markers
   * @param hasConflict true if the file contains a conflict
   */
  public ConflictedFile(List<String> lines, boolean hasConflict) {
    this.fileContents = null;
    this.lines = lines;
    this.hunks = null;
    this.parseError = null;
    this.hasConflictInitialized = true;
    this.hasConflict = hasConflict;
  }

  /**
   * Parse a conflicted file.
   *
   * @param path the path of the conflicted file
   */
  public ConflictedFile(Path path) {
    this(FilesPlume.readString(path));
  }

  /**
   * Parse a conflicted file.
   *
   * @param path the path of the conflicted file
   * @param hasConflict true if the file contains a conflict
   */
  public ConflictedFile(Path path, boolean hasConflict) {
    this(FilesPlume.readString(path), hasConflict);
  }

  /**
   * Create a new ConflictedFile.
   *
   * @param fileContents the conflicted file, as a single string
   */
  public ConflictedFile(String fileContents) {
    this.fileContents = fileContents;
    this.lines = null;
    this.hunks = null;
    this.parseError = null;
  }

  /**
   * Create a new ConflictedFile.
   *
   * @param fileContents the conflicted file, as a single string
   * @param hasConflict true if the file contains a conflict
   */
  public ConflictedFile(String fileContents, boolean hasConflict) {
    this.fileContents = fileContents;
    this.lines = null;
    this.hunks = null;
    this.parseError = null;
    this.hasConflictInitialized = true;
    this.hasConflict = hasConflict;
  }

  /**
   * Create a new ConflictedFile.
   *
   * @param lines the lines of the conflicted file
   */
  public ConflictedFile(List<String> lines) {
    this.lines = lines;
    this.hunks = null;
    this.parseError = null;
  }

  ///////////////////////////////////////////////////////////////////////////
  /// End of constructors
  ///

  /**
   * Returns the contents of the conflicted file, or null if the file format is erroneous.
   *
   * @return the contents of the conflicted file
   */
  public @Nullable List<ConflictElement> hunks() {
    if (hunks == null && parseError == null) {
      parse();
    }
    return hunks;
  }

  /**
   * Returns the merge conflicts in this.
   *
   * @return the merge conflicts in this
   */
  public List<MergeConflict> mergeConflicts() {
    hunks();
    if (hunks == null) {
      throw new Error("parsing failed: " + parseError());
    }
    List<MergeConflict> result = new ArrayList<>((hunks.size() + 1) / 2);
    for (ConflictElement ce : hunks) {
      if (ce instanceof MergeConflict) {
        result.add((MergeConflict) ce);
      }
    }
    return result;
  }

  /**
   * Returns the format error of the conflicted file, or null if the file format is not erroneous.
   *
   * @return the format error of the conflicted file, or null
   */
  public @Nullable String parseError() {
    if (parseError == null && hunks == null) {
      parse();
    }
    return parseError;
  }

  /**
   * Returns true if the conflict file has improper format.
   *
   * @return true if the conflict file has improper format
   */
  public boolean isParseError() {
    return parseError() != null;
  }

  /** Matches the start of a conflict, in a multiline string. */
  private Pattern conflictStartMultilinePattern = Pattern.compile("^<<<<<<", Pattern.MULTILINE);

  /**
   * Returns true if the file contains any conflicts.
   *
   * @return true if the file contains any conflicts
   */
  public boolean hasConflict() {
    if (!hasConflictInitialized) {
      if (hunks != null) {
        hasConflict = CollectionsPlume.anyMatch(hunks, ce -> ce instanceof MergeConflict);
      } else {
        if (fileContents != null) {
          hasConflict = conflictStartMultilinePattern.matcher(fileContents).find();
        } else if (lines != null) {
          hasConflict = CollectionsPlume.anyMatch(lines, l -> l.startsWith("<<<<<<"));
        } else {
          throw new Error("Bad state");
        }
      }
      hasConflictInitialized = true;
    }
    return hasConflict;
  }

  /**
   * Returns true if this file contained a "trivial" conflict, where two of base, left, and right
   * were the same.
   *
   * @return true if this file contained a "trivial" conflict
   */
  public boolean hasTrivalConflict() {
    return hasTrivalConflict;
  }

  /**
   * Returns the contents of the conflicted file, including conflict markers.
   *
   * @return the contents of the conflicted file
   * @see #lines()
   */
  @EnsuresNonNull("fileContents")
  public String fileContents() {
    if (fileContents == null) {
      if (lines != null) {
        fileContents = String.join("", lines());
      } else if (hunks != null) {
        StringBuilder sb = new StringBuilder();
        for (ConflictElement ce : hunks) {
          for (String line : ce.toLines()) {
            sb.append(line);
          }
        }
        fileContents = sb.toString();
      } else {
        throw new Error("too many null values");
      }
    }
    return fileContents;
  }

  /**
   * Returns the lines of the conflicted file, including conflict markers. Clients should not mutate
   * the return value.
   *
   * @return the lines of the conflicted file
   * @see #fileContents()
   */
  @EnsuresNonNull("lines")
  public List<String> lines() {
    if (lines == null) {
      if (fileContents != null) {
        lines = StringsPlume.splitLinesRetainSeparators(fileContents);
      } else if (hunks != null) {
        lines = new ArrayList<String>();
        for (ConflictElement ce : hunks) {
          lines.addAll(ce.toLines());
        }
      } else {
        throw new Error("too many null values");
      }
    }
    return lines;
  }

  @Override
  public String toString() {
    if (parseError != null) {
      return "ParseError{" + parseError + "}";
    } else if (hunks != null) {
      return "ConflictedFile{" + hunks.toString() + "}";
    } else if (fileContents != null) {
      return "UnparsedConflictedFile{" + fileContents + "}";
    } else if (lines != null) {
      return "UnparsedConflictedFile{" + String.join("", lines) + "}";
    } else {
      throw new Error();
    }
  }

  /** One element of a conflicted file: either {@link MergeConflict} or {@link CommonLines}. */
  public static interface ConflictElement {
    /**
     * Returns the lines in the confict-file representation of this.
     *
     * @return the lines in the confict-file representation of this
     */
    public List<String> toLines();
  }

  /** A single merge conflict (part of a conflicted file). */
  // This cannot be a record because I don't want the default constructor to be public.
  public static class MergeConflict implements ConflictElement {
    /** The base text; empty string means empty, null means unknown. */
    @MonotonicNonNull List<String> base;

    /** The left text. */
    List<String> left;

    /** The right text. */
    List<String> right;

    /** The first line in the conflict --- that is, the line with {@code <<<<<<}. */
    int start;

    /** The line after the conflict --- that is, the line after the one with {@code >>>>>>}. */
    int end;

    /**
     * Creates a MergeConflict. Clients should use {@link #of} instead.
     *
     * @param left the left text
     * @param right the right text
     * @param base the base text
     * @param start the first line in the conflict --- that is, the line with {@code <<<<<<}
     * @param end the line after the conflict --- that is, the line after the one with {@code
     *     >>>>>>}
     */
    private MergeConflict(
        @Nullable List<String> base, List<String> left, List<String> right, int start, int end) {
      this.base = base;
      this.left = left;
      this.right = right;
      this.start = start;
      this.end = end;
    }

    // `git merge-file` sometimes produces trivial conflicts.  I don't know why, but here is an
    // example:
    //
    // base:
    // import java.util.function.BiFunction;
    // import org.checkerframework.checker.nullness.qual.Nullable;
    //
    // left:
    // import java.util.function.BiFunction;
    // import javax.annotation.CheckForNull;
    // import org.checkerframework.checker.nullness.qual.KeyFor;
    // import org.checkerframework.checker.nullness.qual.Nullable;
    // import org.checkerframework.checker.signedness.qual.UnknownSignedness;
    //
    // right:
    // import java.util.function.BiFunction;
    // import javax.annotation.CheckForNull;
    // import org.checkerframework.checker.nullness.qual.Nullable;
    //
    // merged:
    // import java.util.function.BiFunction;
    // import javax.annotation.CheckForNull;
    // <<<<<<<
    // import org.checkerframework.checker.nullness.qual.KeyFor;
    // =======
    // >>>>>>>
    // import org.checkerframework.checker.nullness.qual.Nullable;
    // import org.checkerframework.checker.signedness.qual.UnknownSignedness;

    /**
     * Creates a MergeConflict. Creates a CommonLines if the merge conflict would be trivial.
     *
     * @param base the base text
     * @param left the left text
     * @param right the right text
     * @param start the first line in the conflict --- that is, the line with {@code <<<<<<}
     * @param end the line after the conflict --- that is, the line after the one with {@code
     *     >>>>>>}
     * @return a new MergeConflict or CommonLines
     */
    public static ConflictElement of(
        @Nullable List<String> base, List<String> left, List<String> right, int start, int end) {
      if (left.equals(right) || left.equals(base)) {
        return new CommonLines(right);
      } else if (right.equals(base)) {
        return new CommonLines(left);
      } else {
        return new MergeConflict(base, left, right, start, end);
      }
    }

    /**
     * Returns the base text.
     *
     * @return the base text
     */
    @Pure
    public @Nullable List<String> base() {
      return base;
    }

    /**
     * Returns the left text.
     *
     * @return the left text
     */
    @Pure
    public List<String> left() {
      return left;
    }

    /**
     * Returns the right text.
     *
     * @return the right text
     */
    @Pure
    public List<String> right() {
      return right;
    }

    /**
     * Returns the first line in the conflict --- that is, the line with {@code <<<<<<}.
     *
     * @return the first line in the conflict
     */
    @Pure
    public int start() {
      return start;
    }

    /**
     * Returns the line after the conflict --- that is, the line after the one with {@code >>>>>>}.
     *
     * @return the line after the conflict
     */
    @Pure
    public int end() {
      return end;
    }

    /**
     * Returns the base text as a single string.
     *
     * @return the base text as a single string
     */
    protected @Nullable String baseJoined() {
      if (base == null) {
        return null;
      } else {
        return String.join("", base);
      }
    }

    /**
     * Returns the left text as a single string.
     *
     * @return the left text as a single string
     */
    protected String leftJoined() {
      return String.join("", left);
    }

    /**
     * Returns the right text as a single string.
     *
     * @return the right text as a single string
     */
    protected String rightJoined() {
      return String.join("", right);
    }

    @Override
    public List<String> toLines() {
      List<String> result =
          new ArrayList<>(left.size() + right.size() + (base == null ? 0 : (base.size() + 1)) + 3);
      // TODO: Use the file separator from the file.
      result.add("<<<<<<< OURS" + System.lineSeparator());
      result.addAll(left);
      if (base != null) {
        result.add("||||||| BASE" + System.lineSeparator());
        result.addAll(base);
      }
      result.add("=======" + System.lineSeparator());
      result.addAll(right);
      result.add(">>>>>>> THEIRS" + System.lineSeparator());
      return result;
    }

    @Override
    public String toString() {
      return "MergeConflict"
          + ("{base=" + base + "}")
          + ("{left=" + left + "}")
          + ("{right=" + right + "}");
    }
  }

  /**
   * A non-conflicted part of a conflicted file.
   *
   * @param textLines the text
   */
  public static record CommonLines(
      /** The text. */
      List<String> textLines) implements ConflictElement {
    /**
     * Creates a CommonLines.
     *
     * @param textLines the lines. It is permitted for this to be an empty array.
     */
    public CommonLines {}

    @Override
    public String toString(@GuardSatisfied CommonLines this) {
      return textLines.toString();
    }

    @Override
    public List<String> toLines() {
      return textLines;
    }

    /**
     * Returns the lines, joined into a single string. Is expensive if there are many lines.
     *
     * @return the lines as a single string
     */
    public String joinedLines() {
      return String.join("", textLines);
    }

    /**
     * Returns a copy of this CommonLines, without lines that match {@code p}. May return the
     * receiver.
     *
     * @param p a pattern
     * @return a copy of this without lines that match {@code p}, or this
     */
    public CommonLines removeMatchingLines(Pattern p) {
      List<String> result = new ArrayList<>();
      for (String line : textLines) {
        if (!p.matcher(line).matches()) {
          result.add(line);
        }
      }
      int size = result.size();
      if (size == textLines.size()) {
        return this;
      } else {
        return new CommonLines(result);
      }
    }

    /**
     * Converts a list of CommonLines to a list of Strings.
     *
     * @param cls a list of CommonLines
     * @return all the lines in the input
     */
    public static List<String> toLines(List<CommonLines> cls) {
      List<String> result = new ArrayList<>();
      for (CommonLines cl : cls) {
        for (String s : cl.textLines()) {
          result.add(s);
        }
      }
      return result;
    }

    /**
     * Converts a list of CommonLines to a single string.
     *
     * @param cls a list of CommonLines
     * @return the concatenation of all the lines in the input
     */
    public static String toString(List<CommonLines> cls) {
      StringBuilder result = new StringBuilder();
      for (CommonLines cl : cls) {
        for (String s : cl.textLines()) {
          result.append(s);
        }
      }
      return result.toString();
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  /// Parsing
  ///

  /** Parse a conflicted file, filling in the {@link #hunks} or {@link #parseError} field. */
  @SuppressWarnings({
    "index" // todo
  })
  private void parse() {
    try {
      int numLines = lines().size();

      List<ConflictElement> result = new ArrayList<>();

      int i = 0;
      int lastConflictEnder = -1;
      while (i < numLines) {
        String line = lines.get(i);
        if (!line.startsWith("<<<<<<")) {
          i++;
          continue;
        }

        int conflictStart = i;

        // We found <<<<<<, the left conflict start marker.
        if (i > lastConflictEnder + 1) {
          List<String> commonText = lines.subList(lastConflictEnder + 1, i);
          if (commonText.size() != 0) {
            result.add(new CommonLines(commonText));
          }
        }
        int leftConflictMarker = i;
        i++;
        // Determine the left text, and the base text if it exists.
        List<String> left = null;
        boolean foundBaseSeparator = false;
        while (i < numLines) {
          line = lines.get(i);
          foundBaseSeparator = line.startsWith("||||||");
          if (foundBaseSeparator || line.startsWith("======")) {
            left = lines.subList(leftConflictMarker + 1, i);
            break;
          } else {
            i++;
          }
        }
        if (i == numLines) {
          parseError =
              "No ====== or |||||| line found after <<<<<< on line " + (leftConflictMarker + 1);
          return;
        }
        assert left != null : "@AssumeAssertion(nullness): due to test of i==numLines";
        List<String> base = null;
        int baseConflictMarker = -1;
        if (foundBaseSeparator) {
          // We found ||||||, still need to find ======.
          baseConflictMarker = i;
          i++;
          while (i < numLines) {
            line = lines.get(i);
            if (line.startsWith("======")) {
              base = lines.subList(baseConflictMarker + 1, i);
              break;
            }
            i++;
          }
          if (i == numLines) {
            String msg1 = "No ====== line found after <<<<<< on line " + (leftConflictMarker + 1);
            String msg2;
            if (foundBaseSeparator) {
              msg2 = " and |||||| on line " + (baseConflictMarker + 1);
            } else {
              msg2 = "";
            }
            parseError = msg1 + msg2;
            return;
          }
        }
        // We have read the left conflict text, and the base conflict text if any.
        @SuppressWarnings("interning:not.interned")
        boolean sameLine = (line == lines.get(i));
        assert sameLine;
        assert line.startsWith("======")
            : "line " + (i + 1) + " doesn't start with \"======\": " + line;
        int rightConflictMarker = i;
        i++;
        List<String> right = null;
        while (i < numLines) {
          line = lines.get(i);
          if (line.startsWith(">>>>>>")) {
            right = lines.subList(rightConflictMarker + 1, i);
            break;
          }
          i++;
        }
        if (right == null) {
          parseError =
              "No >>>>>> line found after <<<<<< on line "
                  + (leftConflictMarker + 1)
                  + " and ====== on line "
                  + (rightConflictMarker + 1);
          return;
        }
        assert right != null : "@AssumeAssertion(nullness): if left is non-null, so is right";
        ConflictElement ce = MergeConflict.of(base, left, right, conflictStart, i + 1);
        if (ce instanceof CommonLines) {
          hasTrivalConflict = true;
        }
        result.add(ce);
        lastConflictEnder = i;
        i++;
      } // while (i < numLines)
      assert i == numLines;
      List<String> lastCommon = lines.subList(lastConflictEnder + 1, i);
      if (!lastCommon.isEmpty()) {
        result.add(new CommonLines(lastCommon));
      }
      hunks = result;
      if (hasTrivalConflict) {
        resetLinesAndFileContents();
      }
    } catch (Throwable e) {
      System.out.println(this);
      throw e;
    }
  }

  /**
   * Sets {@link #lines} and {@link fileContents} to null. This is a separate method so that a
   * {@code @SuppressWarnings} annotation can be written on it.
   */
  @SuppressWarnings("nullness:assignment") // resets the data structure
  private void resetLinesAndFileContents() {
    lines = null;
    fileContents = null;
  }
}
