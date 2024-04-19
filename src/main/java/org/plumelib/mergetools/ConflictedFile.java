// It seems that JGit's MergeResult is produced only by its own tools; that is, one cannot create a
// MergeResult by parsing a conflicted file.

package org.plumelib.mergetools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.util.FilesPlume;
import org.plumelib.util.StringsPlume;

/**
 * Represents a file that contains conflict markers.
 *
 * <p>It is a sequence of ConflictElement objects.
 *
 * <p>There are two general forms of conflicted sections of a file:
 *
 * <pre>{@code
 * <<<<<<< HEAD
 * puts 'hola world'
 * =======
 * puts 'hello mundo'
 * >>>>>>> mundo
 * }</pre>
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
  // private static final boolean verbose = true;

  /** The contents of the conflicted file. */
  private final @Nullable List<ConflictElement> contents;

  /** The line separator of the conflicted file. */
  private final @Nullable String lineSeparator;

  /** The error message, or null. */
  private final @Nullable String error;

  /**
   * Create a new ConflictedFile.
   *
   * @param contents the contents of the conflicted file
   * @param lineSeparator the line separator
   */
  private ConflictedFile(List<ConflictElement> contents, String lineSeparator) {
    this.contents = contents;
    this.lineSeparator = lineSeparator;
    this.error = null;
  }

  /**
   * Create a new erroneous ConflictedFile.
   *
   * @param error the error message
   */
  private ConflictedFile(String error) {
    this.contents = null;
    this.lineSeparator = null;
    this.error = error;
  }

  /**
   * Returns the contents of the conflicted file, or null if the file format is erroneous.
   *
   * @return the contents of the conflicted file
   */
  public @Nullable List<ConflictElement> contents() {
    return contents;
  }

  /**
   * Returns the line separator of the conflicted file. Throws an exception if the file format is
   * erroneous.
   *
   * @return the line separator of the conflicted file
   */
  public String lineSeparator() {
    if (lineSeparator == null) {
      throw new Error();
    }
    return lineSeparator;
  }

  /**
   * Returns the format error of the conflicted file, or null if the file format is not erroneous.
   *
   * @return the format error of the conflicted file, or null
   */
  public @Nullable String error() {
    return error;
  }

  /**
   * Parse a conflicted file.
   *
   * @param filename the name of the conflicted file
   * @return the parsed file
   */
  public static ConflictedFile parseFile(String filename) {
    try {
      return parseFileContents(FilesPlume.fileContents(new File(filename)));
    } catch (Throwable e) {
      throw new Error(e);
    }
  }

  /**
   * Parse a conflicted file.
   *
   * @param fileContents the contents of the conflicted file
   * @return the parsed file
   */
  @SuppressWarnings({
    "index", // todo
    "nullness:assignment" // result of Arrays.copyOfRange contains no nulls
  })
  public static ConflictedFile parseFileContents(String fileContents) {
    @Regex String lineSeparator = StringsPlume.firstLineSeparator(fileContents);
    // Each line ends with a line separator (except possibly the last).
    String[] lines = fileContents.split("(?<=" + lineSeparator + ")");
    int numLines = lines.length;

    List<ConflictElement> result = new ArrayList<>();

    int i = 0;
    int lastConflictEnder = -1;
    while (i < numLines) {
      String line = lines[i];
      if (!line.startsWith("<<<<<<")) {
        i++;
        continue;
      }

      // We found <<<<<<, the left conflict start marker.
      if (i > lastConflictEnder + 1) {
        String[] commonText = Arrays.copyOfRange(lines, lastConflictEnder + 1, i);
        if (commonText.length != 0) {
          result.add(new CommonLines(commonText));
        }
      }
      int leftConflictMarker = i;
      i++;
      // Determine the left text, and the base text if it exists.
      String[] left = null;
      boolean foundBaseSeparator = false;
      while (i < numLines) {
        line = lines[i];
        foundBaseSeparator = line.startsWith("||||||");
        if (foundBaseSeparator || line.startsWith("======")) {
          left = Arrays.copyOfRange(lines, leftConflictMarker + 1, i);
          break;
        } else {
          i++;
        }
      }
      if (i == numLines) {
        return new ConflictedFile(
            "No ====== or |||||| line found after <<<<<< on line " + (leftConflictMarker + 1));
      }
      assert left != null : "@AssumeAssertion(nullness): due to test of i==numLines";
      String[] base = null;
      int baseConflictMarker = -1;
      if (foundBaseSeparator) {
        // We found ||||||, still need to find ======.
        baseConflictMarker = i;
        i++;
        while (i < numLines) {
          line = lines[i];
          if (line.startsWith("======")) {
            base = Arrays.copyOfRange(lines, baseConflictMarker + 1, i);
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
          return new ConflictedFile(msg1 + msg2);
        }
      }
      // We have read the left conflict text, and the base conflict text if any.
      @SuppressWarnings("interning:not.interned")
      boolean sameLine = (line == lines[i]);
      assert sameLine;
      assert line.startsWith("======")
          : "line " + (i + 1) + " doesn't start with \"======\": " + line;
      int rightConflictMarker = i;
      i++;
      String[] right = null;
      while (i < numLines) {
        line = lines[i];
        if (line.startsWith(">>>>>>")) {
          right = Arrays.copyOfRange(lines, rightConflictMarker + 1, i);
          break;
        }
        i++;
      }
      if (right == null) {
        return new ConflictedFile(
            "No >>>>>> line found after <<<<<< on line "
                + (leftConflictMarker + 1)
                + " and ====== on line "
                + (rightConflictMarker + 1));
      }
      assert right != null : "@AssumeAssertion(nullness): if left is non-null, so is right";
      result.add(new MergeConflict(left, right, base));
      lastConflictEnder = i;
      i++;
    } // while (i < numLines)
    assert i == numLines;
    String[] lastCommon = Arrays.copyOfRange(lines, lastConflictEnder + 1, i);
    if (lastCommon.length != 0) {
      result.add(new CommonLines(lastCommon));
    }
    return new ConflictedFile(result, lineSeparator);
  }

  // TODO

  /**
   * One element of a conflicted file: either a merge conflict, or lines that are common to both
   * files.
   */
  public interface ConflictElement {}

  /**
   * A single merge conflict (part of a conflicted file).
   *
   * @param left the left text
   * @param right the right text
   * @param base the base text; empty string means empty, null means unknown
   */
  // TODO: I end up splitting the components into lines fairly often.  Maybe it would be better to
  // store them as lines.
  public record MergeConflict(
      /** The left text. */
      String[] left,
      /** The right text. */
      String[] right,
      /** The base text. */
      String @Nullable [] base)
      implements ConflictElement {
    /**
     * Returns the left text as a single string. Is expensive if there are many lines.
     *
     * @return the left text as a single string
     */
    public String leftJoined() {
      return String.join("", left);
    }

    /**
     * Returns the right text as a single string. Is expensive if there are many lines.
     *
     * @return the right text as a single string
     */
    public String rightJoined() {
      return String.join("", right);
    }

    /**
     * Returns the base text as a single string. Is expensive if there are many lines.
     *
     * @return the base text as a single string
     */
    public @Nullable String baseJoined() {
      if (base == null) {
        return null;
      } else {
        return String.join("", base);
      }
    }

    @Override
    public String toString() {
      return "MergeConflict"
          + ("{left=" + Arrays.toString(left) + "}")
          + ("{right=" + Arrays.toString(right) + "}")
          + ("{base" + (base == null ? "null" : Arrays.toString(base)) + "}");
    }
  }

  /**
   * A non-conflicted part of a conflicted file.
   *
   * @param textLines the text
   */
  public record CommonLines(
      /** The text. */
      String[] textLines) implements ConflictElement {
    /**
     * Creates a CommonLines.
     *
     * @param textLines the lines
     */
    public CommonLines {
      if (textLines.length == 0) {
        throw new Error("CommonLines(<empty array>)");
      }
    }

    @Override
    public String toString(@GuardSatisfied CommonLines this) {
      return Arrays.toString(textLines);
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
      if (size == textLines.length) {
        return this;
      } else {
        return new CommonLines(result.toArray(new String[0]));
      }
    }
  }

  // TODO: return a LinkedList to facilitate insertion?
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

  @Override
  public String toString() {
    if (error != null) {
      return "ConflictedFileError{" + error + "}";
    } else if (contents != null) {
      return contents.toString();
    } else {
      throw new Error();
    }
  }
}
