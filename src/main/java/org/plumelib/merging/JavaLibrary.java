package org.plumelib.merging;

import java.util.List;
import java.util.regex.Pattern;
import org.plumelib.util.CollectionsPlume;

/** This class contains static methods related to Java code. */
public class JavaLibrary {

  /** Do not instantiate. */
  private JavaLibrary() {
    throw new Error("do not instantiate");
  }

  /** A pattern that matches a string consisting only of whitespace. */
  private static Pattern whitespacePattern = Pattern.compile("\\s*\\R*");

  /**
   * Returns true if the given string is a blank line.
   *
   * @param line a string
   * @return true if the given string is a blank line
   */
  public static boolean isBlankLine(String line) {
    return whitespacePattern.matcher(line).matches();
  }

  /**
   * A pattern that matches a comment line. Because of use of {@code matches()}, no regex anchoring
   * is needed.
   */
  private static Pattern commentLinePattern = Pattern.compile("\\s*(//.*|/\\*.*\\*/\\s*)\\R?");

  /**
   * Returns true if the given line is a comment line.
   *
   * @param line a line of code, which may be terminated by a line separator
   * @return true if the line is a comment line
   */
  public static boolean isCommentLine(String line) {
    return commentLinePattern.matcher(line).matches();
  }

  /**
   * Returns all the comment lines in the input.
   *
   * @param lines code lines, each of which may be terminated by a line separator
   * @return the comment lines in the input
   */
  public static List<String> commentLines(List<String> lines) {
    return CollectionsPlume.filter(lines, JavaLibrary::isCommentLine);
  }

  /**
   * A pattern that matches an import line in Java code. Does not match import lines with a trailing
   * comment.
   */
  private static Pattern importPattern = Pattern.compile("\\s*import\\s.*;\\R?");

  /**
   * Returns true if the given line is an import statement.
   *
   * @param line a line of Java code
   * @return true if the given line is an import statement
   */
  public static boolean isImportStatement(String line) {
    return importPattern.matcher(line).matches();
  }

  /**
   * Given a line of code, return true if can appear in an import block: it is an {@code import},
   * blank line, or comment.
   *
   * @param line a line of code
   * @return true if the line can be in an import block
   */
  public static boolean isImportBlockLine(String line) {
    return line.isEmpty() || isBlankLine(line) || isCommentLine(line) || isImportStatement(line);
  }
}
