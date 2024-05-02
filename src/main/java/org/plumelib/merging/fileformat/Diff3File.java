package org.plumelib.merging.fileformat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.util.FilesPlume;
import org.plumelib.util.IPair;

// TODO: Parsing could use LineNumberReader instead of reading the entire file at once.  That would
// be just slightly more efficient, probably, and I wouldn't have to return pairs that include line
// numbers.

/**
 * Represents a file that was output by diff3. It is a sequence of Diff3Hunk objects.
 *
 * <p>For documentation of the file format, see
 * https://www.gnu.org/software/diffutils/manual/diffutils.html#Comparing-Three-Files.
 */
public class Diff3File {

  /** If true, output diagnostic information for debugging. */
  private static final boolean verbose = false;

  /** The contents of the diff3 file. */
  private final List<Diff3Hunk> contents;

  /**
   * Create a new Diff3File.
   *
   * @param contents the contents of the diff3 file
   */
  private Diff3File(List<Diff3Hunk> contents) {
    this.contents = contents;
  }

  /**
   * Returns the contents of the diff3 file. Clients should not side-effect this.
   *
   * @return the contents of the diff3 file
   */
  public List<Diff3Hunk> contents() {
    return contents;
  }

  /**
   * Parse a diff3 file.
   *
   * @param filename the name of the diff3 file
   * @return the parsed file
   */
  public static Diff3File parseFile(String filename) {
    try {
      return parseFileContents(FilesPlume.readString(Path.of(filename)), filename);
    } catch (Throwable e) {
      throw new Error("Problem parsing " + filename, e);
    }
  }

  /**
   * Parse a diff3 file.
   *
   * @param fileContents the contents of the diff3 file
   * @param filename the file name, for diagnostics
   * @return the parsed file
   * @throws Diff3ParseException if the input is malformed
   */
  public static Diff3File parseFileContents(String fileContents, String filename)
      throws Diff3ParseException {
    List<String> lines = fileContents.lines().collect(Collectors.toList());
    int numLines = lines.size();

    List<Diff3Hunk> result = new ArrayList<>();

    int i = 0;
    while (i < numLines) {
      // This is the first line of a hunk
      String line = lines.get(i);
      if (!line.startsWith("====")) {
        throw new Error("Expected \"====\" at line " + (i + 1) + ", found: " + line);
      }
      i = Diff3Hunk.parse(lines, i, result);
    }

    return new Diff3File(result);
  }

  @Override
  public String toString(@GuardSatisfied Diff3File this) {
    return contents.toString();
  }

  /**
   * A single hunk from diff3 output.
   *
   * @param kind the kind of hunk
   * @param section1 the first text section
   * @param section2 the second text section
   * @param section3 the third text section
   */
  public static record Diff3Hunk(
      Diff3HunkKind kind,
      Diff3HunkSection section1,
      Diff3HunkSection section2,
      Diff3HunkSection section3) {

    /**
     * Creates a Diff3Hunk.
     *
     * @param kind the kind of hunk
     * @param section1 the first text section
     * @param section2 the second text section
     * @param section3 the third text section
     */
    public Diff3Hunk {
      checkRep(kind, section1, section2, section3);
    }

    /**
     * Checks the representation of an object made up of the given fields; throws an exception if
     * the object would be malformed.
     *
     * @param kind the kind of hunk
     * @param section1 the first text section
     * @param section2 the second text section
     * @param section3 the third text section
     */
    private static void checkRep(
        Diff3HunkKind kind,
        Diff3HunkSection section1,
        Diff3HunkSection section2,
        Diff3HunkSection section3) {
      assert section1 != null : "@AssumeAssertion(nullness)";
      assert section2 != null : "@AssumeAssertion(nullness)";
      assert section3 != null : "@AssumeAssertion(nullness)";
      assert section1.command().inputFile() == 1;
      assert section2.command().inputFile() == 2;
      assert section3.command().inputFile() == 3;
      assert ((kind == Diff3HunkKind.ONE_DIFFERS) && section2.lines().equals(section3.lines()))
          || ((kind == Diff3HunkKind.TWO_DIFFERS) && section1.lines().equals(section3.lines()))
          || ((kind == Diff3HunkKind.THREE_DIFFERS) && section1.lines().equals(section2.lines()))
          || kind == Diff3HunkKind.THREE_WAY;
    }

    /**
     * Reads a Diff3Hunk from the given lines, starting at index {@code start}. Adds the hunk to
     * {@code sink}.
     *
     * @param lines the lines to read from
     * @param start the first line to read
     * @param sink where to put the newly-read Diff3Hunk
     * @return the first line following the hunk
     * @throws Diff3ParseException if the input is malformed
     */
    public static int parse(List<String> lines, int start, List<Diff3Hunk> sink)
        throws Diff3ParseException {
      if (verbose) {
        System.out.printf("Starting to parse hunk starting at line " + start + ".%n");
        System.out.flush();
      }

      Diff3HunkKind kind;
      try {
        String header = lines.get(start);
        kind = Diff3HunkKind.fromHunkHeader(header);
      } catch (Diff3ParseException e) {
        throw new Diff3ParseException("At line " + (start + 1) + ": " + e.getMessage());
      }
      return ThreeSections.parse(lines, start + 1, kind, sink);
    }

    /**
     * A triple of three hunk sections. Used for intermediate results.
     *
     * @param section1 the first section
     * @param section2 the second section
     * @param section3 the third section
     */
    public static record ThreeSections(
        Diff3HunkSection section1, Diff3HunkSection section2, Diff3HunkSection section3) {

      /**
       * Parses three sections.
       *
       * @param lines lines of text from which to parse
       * @param startLine where to start parsing within the text
       * @param kind the kind of diff3 hunk whose sections are being parsed
       * @param sink where to store the parsed Diff3Hunk
       * @return three sections
       * @throws Diff3ParseException if the input is malformed
       */
      private static int parse(
          List<String> lines, int startLine, Diff3HunkKind kind, List<Diff3Hunk> sink)
          throws Diff3ParseException {
        if (verbose) {
          System.out.printf("Starting to parse 3 sections at line %s.%n", startLine + 1);
          System.out.flush();
        }

        int i = startLine;
        IPair<Integer, Diff3HunkSection> sectionPairA = Diff3HunkSection.parse(lines, i);
        Diff3HunkSection sectionA = sectionPairA.second;
        i = sectionPairA.first;
        IPair<Integer, Diff3HunkSection> sectionPairB = Diff3HunkSection.parse(lines, i);
        Diff3HunkSection sectionB = sectionPairB.second;
        i = sectionPairB.first;
        IPair<Integer, Diff3HunkSection> sectionPairC = Diff3HunkSection.parse(lines, i);
        Diff3HunkSection sectionC = sectionPairC.second;
        i = sectionPairC.first;

        ThreeSections unsorted = new ThreeSections(sectionA, sectionB, sectionC);
        ThreeSections sorted = unsorted.sort();
        ThreeSections filled = sorted.fillIn(kind);
        if (verbose) {
          System.out.println(
              "Finished parsing 3 sections, ending before line " + i + ": " + filled);
          System.out.flush();
        }
        sink.add(new Diff3Hunk(kind, filled.section1(), filled.section2(), filled.section3()));
        return i;
      }

      /**
       * Return a ThreeSections with the same contents as the receiver, but with the sections in
       * order.
       *
       * @return a ThreeSections with the sections in order
       */
      private ThreeSections sort() {

        Diff3HunkSection newSection1 = null;
        Diff3HunkSection newSection2 = null;
        Diff3HunkSection newSection3 = null;

        Diff3HunkSection thisSection;
        thisSection = section1();
        switch (thisSection.command().inputFile()) {
          case 1:
            newSection1 = thisSection;
            break;
          case 2:
            newSection2 = thisSection;
            break;
          case 3:
            newSection3 = thisSection;
            break;
          default:
            throw new Error();
        }
        thisSection = section2();
        switch (thisSection.command().inputFile()) {
          case 1:
            newSection1 = thisSection;
            break;
          case 2:
            newSection2 = thisSection;
            break;
          case 3:
            newSection3 = thisSection;
            break;
          default:
            throw new Error();
        }
        thisSection = section3();
        switch (thisSection.command().inputFile()) {
          case 1:
            newSection1 = thisSection;
            break;
          case 2:
            newSection2 = thisSection;
            break;
          case 3:
            newSection3 = thisSection;
            break;
          default:
            throw new Error();
        }

        if (newSection1 == null) {
          throw new Error();
        }
        if (newSection2 == null) {
          throw new Error();
        }
        if (newSection3 == null) {
          throw new Error();
        }

        @SuppressWarnings("interning:not.interned") // assignments preserve identity
        boolean same =
            newSection1 == section1 && newSection2 == section2 && newSection3 == section3;
        if (same) {
          return this;
        } else {
          return new ThreeSections(newSection1, newSection2, newSection3);
        }
      }

      /**
       * Return a new ThreeSections, with each section's lines filled in.
       *
       * @param kind the kind of diff3 hunk that the three sections belong in
       * @return a ThreeSections with each sections line's filled in
       */
      private ThreeSections fillIn(Diff3HunkKind kind) {

        if (kind == Diff3HunkKind.THREE_WAY) {
          return this;
        }

        List<String> lines1 = section1.lines();
        List<String> lines2 = section2.lines();
        List<String> lines3 = section3.lines();

        switch (kind) {
          case ONE_DIFFERS:
            assert lines2.isEmpty() || lines3.isEmpty();
            if (lines2.isEmpty() && !lines3.isEmpty()) {
              return new ThreeSections(
                  section1, new Diff3HunkSection(section2.command(), lines3), section3);
            } else if (lines3.isEmpty() && !lines2.isEmpty()) {
              return new ThreeSections(
                  section1, section2, new Diff3HunkSection(section3.command(), lines2));
            } else {
              return this;
            }
          case TWO_DIFFERS:
            assert lines1.isEmpty() || lines3.isEmpty();
            if (lines1.isEmpty() && !lines3.isEmpty()) {
              return new ThreeSections(
                  new Diff3HunkSection(section1.command(), lines3), section2, section3);
            } else if (lines3.isEmpty() && !lines1.isEmpty()) {
              return new ThreeSections(
                  section1, section2, new Diff3HunkSection(section3.command(), lines1));
            } else {
              return this;
            }
          case THREE_DIFFERS:
            assert lines1.isEmpty() || lines2.isEmpty();
            if (lines1.isEmpty() && !lines2.isEmpty()) {
              return new ThreeSections(
                  new Diff3HunkSection(section1.command(), lines2), section2, section3);
            } else if (lines2.isEmpty() && !lines1.isEmpty()) {
              return new ThreeSections(
                  section1, new Diff3HunkSection(section2.command(), lines1), section3);
            } else {
              return this;
            }
          default:
            throw new Error();
        }
      }
    }

    /**
     * Returns the difference in length caused by the hunk. The receiver must be a hunk that was
     * merged cleanly by {@code git merge}.
     *
     * @return how much the hunk changed the length of the code
     */
    public int lineChangeSize() {
      int lengthDifference = section1().lines().size() - section3().lines().size();
      switch (kind()) {
        case ONE_DIFFERS:
          return lengthDifference;
        case TWO_DIFFERS:
          return 0;
        case THREE_DIFFERS:
          return -lengthDifference;
        case THREE_WAY:
          return lengthDifference;
        default:
          throw new Error();
      }
    }
  }

  /** The kind of a diff3 hunk. */
  public static enum Diff3HunkKind {
    /** Section 1 text differs, sections 2 and 3 have the same text. */
    ONE_DIFFERS,
    /** Section 2 text differs, sections 1 and 3 have the same text. */
    TWO_DIFFERS,
    /** Section 2 text differs, sections 2 and 3 have the same text. */
    THREE_DIFFERS,
    /** All three sections differ. */
    THREE_WAY;

    /**
     * Parses a hunk kind from a hunk header.
     *
     * @param header the header to parse
     * @throws Diff3ParseException if the input is malformed
     * @return a hunk kind, parsed from the input
     */
    public static Diff3HunkKind fromHunkHeader(String header) throws Diff3ParseException {
      header = header.stripTrailing();
      switch (header) {
        case "====":
          return THREE_WAY;
        case "====1":
          return ONE_DIFFERS;
        case "====2":
          return TWO_DIFFERS;
        case "====3":
          return THREE_DIFFERS;
        default:
          throw new Diff3ParseException("Bad diff3 hunk header: " + header);
      }
    }
  }

  /**
   * One of the 3 sections of a diff3 hunk.
   *
   * @param command the command
   * @param lines the text
   */
  public static record Diff3HunkSection(Diff3Command command, List<String> lines) {

    /**
     * Parses a Diff3HunkSection.
     *
     * @param lines the lines to read from
     * @param startLine the first line to read
     * @return the parsed Diff3HunkSection
     * @throws Diff3ParseException if the input is malformed
     */
    public static IPair<Integer, Diff3HunkSection> parse(List<String> lines, int startLine)
        throws Diff3ParseException {
      String commandLine = lines.get(startLine);

      if (verbose) {
        System.out.printf(
            "Starting to parse section at line %d, commandLine: %s%n", startLine + 1, commandLine);
        System.out.flush();
      }

      Diff3Command command;
      try {
        command = Diff3Command.parse(commandLine);
      } catch (Diff3ParseException e) {
        throw new Diff3ParseException("At line " + (startLine + 1) + ": " + e.getMessage());
      }
      List<String> sectionLines = new ArrayList<>();
      int numLines = lines.size();
      int i = startLine + 1;
      while (i < numLines) {
        String line = lines.get(i);
        if (!line.startsWith("  ")) {
          break;
        }
        sectionLines.add(line.substring(2));
        i++;
      }
      // i is the first line after the hunk section.
      return IPair.of(i, new Diff3HunkSection(command, sectionLines));
    }
  }

  /** The kind of a diff3 command: append or change. */
  public static enum Diff3CommandKind {
    /** Append (insert) text. */
    APPEND,
    /** Change text. */
    CHANGE;
  }

  /**
   * This is the command (append or change), without the associated text lines.
   *
   * @param inputFile 1, 2, or 3
   * @param kind append or change
   * @param startLine the first line at which to edit
   * @param endLine the last line at which to edit
   */
  public static record Diff3Command(
      int inputFile, Diff3CommandKind kind, int startLine, int endLine) {

    /**
     * Creates a Diff3Command record.
     *
     * @param inputFile 1, 2, or 3
     * @param kind append or change
     * @param startLine the first line at which to edit
     * @param endLine the last line at which to edit
     */
    public Diff3Command {
      assert inputFile >= 1 && inputFile <= 3;
      assert startLine >= 0;
      assert endLine >= startLine;
    }

    /**
     * Parses a Diff3Command from a command line.
     *
     * @param line the line representing the command
     * @return the parsed command
     * @throws Diff3ParseException if the input is malformed
     */
    public static Diff3Command parse(String line) throws Diff3ParseException {
      int inputFile;
      if (line.startsWith("1:")) {
        inputFile = 1;
      } else if (line.startsWith("2:")) {
        inputFile = 2;
      } else if (line.startsWith("3:")) {
        inputFile = 3;
      } else {
        throw new Diff3ParseException(
            "Malformed command line, should start with \"1:\" or \"2:\" or \"3:\": " + line);
      }
      int lengthMinusOne = line.length() - 1;

      Diff3CommandKind kind =
          switch (line.charAt(lengthMinusOne)) {
            case 'a' -> Diff3CommandKind.APPEND;
            case 'c' -> Diff3CommandKind.CHANGE;
            default ->
                throw new Diff3ParseException(
                    "Malformed command line, should end with \"a\" or \"c\": " + line);
          };

      int startLine;
      int endLine;
      int commaPos = line.indexOf(',');
      if (commaPos == -1) {
        startLine = Integer.parseInt(line, 2, line.length() - 1, 10);
        endLine = startLine;
        if (startLine < 0) {
          throw new Diff3ParseException(
              "Malformed command line, line number must be non-negative: " + line);
        }

      } else {
        if (kind == Diff3CommandKind.APPEND) {
          throw new Diff3ParseException(
              "Malformed command line, append command may not specify two line numbers: " + line);
        }
        startLine = Integer.parseInt(line, 2, commaPos, 10);
        endLine = Integer.parseInt(line, commaPos + 1, line.length() - 1, 10);
        if (startLine < 0) {
          throw new Diff3ParseException(
              "Malformed command line, start line number must be non-negative: " + line);
        }
        if (endLine < startLine) {
          throw new Diff3ParseException(
              "Malformed command line, end line must be no smaller than start line: " + line);
        }
      }

      return new Diff3Command(inputFile, kind, startLine, endLine);
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  /**
   * Runs diff3 on the given files and returns the result.
   *
   * @param leftFileName the left file name
   * @param baseFileName the base file name
   * @param rightFileName the right file name
   * @return the diff3 of the files
   */
  public static Diff3File from3files(
      String leftFileName, String baseFileName, String rightFileName) {

    ProcessBuilder pbDiff3 = new ProcessBuilder("diff3", leftFileName, baseFileName, rightFileName);
    if (verbose) {
      System.out.printf("About to call: %s%n", pbDiff3.command());
    }
    String diff3Output;
    try {
      Process pDiff3 = pbDiff3.start();
      diff3Output = new String(pDiff3.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (verbose) {
        System.out.println("diff3Output: " + diff3Output);
      }
      // It is essential to call waitFor *after* reading the output from getInputStream().
      int diff3ExitCode = pDiff3.waitFor();
      if (diff3ExitCode != 0 && diff3ExitCode != 1) {
        // `diff3` erred, so abort the merge
        String message = "diff3 erred (status " + diff3ExitCode + "): " + diff3Output;
        System.out.println(message);
        System.err.println(message);
        System.exit(129);
        throw new Error("unreachable");
      }
    } catch (IOException | InterruptedException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable");
    }

    Diff3File diff3file;
    try {
      diff3file = Diff3File.parseFileContents(diff3Output, leftFileName);
    } catch (Diff3ParseException e) {
      String message = e.getMessage();
      System.out.println(message);
      System.err.println(message);
      System.exit(129);
      throw new Error("unreachable");
    }
    return diff3file;
  }

  ///////////////////////////////////////////////////////////////////////////

  /** An error when parsing the output of diff3. This is a checked exception. */
  public static class Diff3ParseException extends Exception {

    /** Unique identifier for serialization. If you add or remove fields, change this number. */
    static final long serialVersionUID = 20240331;

    /**
     * Creates a Diff3ParseException3
     *
     * @param message the descriptive message
     */
    public Diff3ParseException(String message) {
      super(message);
    }

    /**
     * Creates a Diff3ParseException3
     *
     * @param message the descriptive message
     * @param cause the underlying exception
     */
    public Diff3ParseException(String message, @Nullable Throwable cause) {
      super(message, cause);
    }
  }
}
