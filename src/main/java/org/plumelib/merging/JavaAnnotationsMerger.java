package org.plumelib.merging;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.javacparse.JavacParse;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.CollectionsPlume.Replacement;
import org.plumelib.util.StringsPlume;

/**
 * This is a merger for Java files. It handles conflicts where the edits differ only in adding
 * annotations or modifiers. It merges such conflicts, accepting the annotations as additions.
 */
public class JavaAnnotationsMerger extends Merger {

  /**
   * Creates a JavaAnnotationsMerger.
   *
   * @param verbose if true, output diagnostic information
   */
  public JavaAnnotationsMerger(boolean verbose) {
    super(verbose);
  }

  @Override
  public void merge(MergeState mergeState) {
    if (!mergeState.hasConflict()) {
      return;
    }

    super.merge(mergeState);
  }

  @Override
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf, MergeState mergeState) {

    List<Replacement<String>> replacements = new ArrayList<>();

    diff_match_patch dmp = new diff_match_patch();
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;

    for (MergeConflict mc : cf.mergeConflicts()) {
      String leftLines = StringsPlume.join("", mc.left());
      String rightLines = StringsPlume.join("", mc.right());
      if (verbose) {
        System.err.printf("calling diff_main([[[%s]]], [[[%s]]])%n", leftLines, rightLines);
      }
      List<Diff> diffs = dmp.diff_main(leftLines, rightLines);
      if (verbose) {
        System.err.printf("called diff_main => %s%n%n", diffs);
      }
      String merged = mergedWithAnnotations(diffs);
      if (merged != null) {
        replacements.add(
            Replacement.of(mc.start(), mc.end() - 1, Collections.singletonList(merged)));
      }
    }

    if (replacements.isEmpty()) {
      return null;
    }

    if (verbose) {
      System.out.printf("before replacement: replacements = %s%n", replacements);
    }
    List<String> newLines = CollectionsPlume.replace(cf.lines(), replacements);
    ConflictedFile result = new ConflictedFile(newLines);
    return result;
  }

  /**
   * If all the differences are annotations or modifiers, then return a string that contains them
   * all. Otherwise, return null.
   *
   * @param diffs the differences
   * @return the merged differences or null
   */
  private static @Nullable String mergedWithAnnotations(List<Diff> diffs) {
    StringBuilder result = new StringBuilder();
    for (Diff diff : diffs) {
      switch (diff.operation) {
          // DELETE means it was inserted in the right edit.
        case INSERT:
        case DELETE:
          if (isJavaAnnotations(diff.text)) {
            result.append(diff.text);
          } else {
            return null;
          }
          break;
        case EQUAL:
          result.append(diff.text);
          break;
        default:
          throw new Error("unexpected operation " + diff.operation);
      }
    }
    return result.toString();
  }

  /**
   * Returns true if the given text is one or more Java annotations or modifiers. Acutally permits
   * ancillary text too; for example "@Anno ClassName this" and "extends @Anno Object"
   *
   * @param text a string
   * @return true if the given text is one or more Java annotations or modifiers
   */
  // "protected" to permit tests to access it.
  protected static boolean isJavaAnnotations(String text) {
    text = commentPattern.matcher(text).replaceAll(" ");
    text = text.trim();
    if (text.isEmpty()) {
      return true;
    }

    // At one time, the parser calls were very expensive, and text matching via regexes was cheaper.
    // I have not re-measured recently.

    // Set to false only to double-check that parsing and regexes give the same result (by running
    // the unit tests).
    final boolean useRegex = true;

    String declText = null;
    // The test for " this" must precede the test for "@", because both can be true.
    if (text.endsWith(" this") || text.endsWith(" this,")) {
      if (useRegex && thisPattern.matcher(text).matches()) {
        return true;
      } else {
        declText = text.substring(0, text.length() - 5) + " " + "varname";
      }
    } else if (text.startsWith("extends ")) {
      if (useRegex && extendsPattern.matcher(text).matches()) {
        return true;
      } else {
        declText = text.substring(8) + " varname";
      }
    } else if (annotationStartPattern.matcher(text).find()) {
      if (useRegex && annotationsPattern.matcher(text).matches()) {
        return true;
      } else {
        declText = text + " String varname";
      }
    } else {
      return false;
    }
    String classText = "class MyClass {" + declText + ";" + "}";

    // Use this diagnostic to determine which strings are still getting parsed.
    // Perhaps write regular expressions for them to improve performance.
    // System.out.printf("isJavaAnnotations: %s%n", text);

    JCCompilationUnit mergedCU = JavacParse.parseJavaCode(classText);
    return mergedCU != null;
  }

  ///////////////////////////////////////////////////////////////////////////
  /// Regular expressions
  ///

  /**
   * Groups the regex in a non-capturing group
   *
   * @param regex a regex
   * @return the regex, in non-capturing group
   */
  @SuppressWarnings("regex") // string concatenation
  protected static @Regex String group(String regex) {
    return "(?:" + regex + ")";
  }

  /**
   * Returns a regex that matches any of the given regexes.
   *
   * @param regexes the disjuncts
   * @return a regex that matches any of the given regexes
   */
  @SuppressWarnings("regex") // string concatenation
  protected static @Regex String or(String... regexes) {
    if (regexes.length < 2) {
      throw new Error("not enough arguments to or(): " + Arrays.toString(regexes));
    }
    List<String> groupedElts = CollectionsPlume.mapList(JavaAnnotationsMerger::group, regexes);
    return group(String.join("|", groupedElts));
  }

  /**
   * Matches one or more of the given regex, separated by the given separator regex.
   *
   * @param regex the regex to possibly repeat
   * @param separator the regex that separates the occurrences
   * @return a regex that matches one or more of the given regex, separated by the given separator
   */
  @SuppressWarnings("regex") // string concatenation
  protected static @Regex String oneOrMore(String regex, String separator) {
    return group(regex) + group(group(separator) + group(regex)) + "*";
  }

  /**
   * Matches zero or more of the given regex, separated by the given separator regex.
   *
   * @param regex the regex to possibly repeat
   * @param separator the regex that separates the occurrences
   * @return a regex that matches zero or more of the given regex, separated by the given separator
   */
  @SuppressWarnings("regex:return") // string concatenation
  protected static @Regex String zeroOrMore(String regex, String separator) {
    return group(oneOrMore(regex, separator)) + "?";
  }

  /** Matches a Java identifier. */
  protected static final @Regex String javaIdentifierRegex =
      "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

  /**
   * Matches dotted identifiers, which might be an enum or CLASSNAME.class, both of which are
   * permitted as annotation arguments.
   */
  protected static final @Regex String javaDottedIdentifiersRegex =
      oneOrMore(javaIdentifierRegex, "\\s*\\.\\s*");

  /** Matches an integral or floating-point value. */
  protected static final @Regex String numberRegex =
      "[-+]?"
          + or(
              // Has a whole-number part.
              // The possessive qualifier "++" improves efficiency.
              "[0-9]++(\\.[0-9]*)?",
              // Has no whole-number part.
              "\\.[0-9]+");

  /** Matches a string. */
  protected static final @Regex String stringRegex = "\"(?:[^\\\\\"]|\\\\.)*\"";

  /** A single annotation value. */
  protected static final String annotationValueSingleRegex =
      or(stringRegex, numberRegex, javaDottedIdentifiersRegex);

  /**
   * Zero or more non-array annotation values, separated by commas and possibly followed by a comma.
   */
  protected static final @Regex String annotationArrayContentsRegex =
      // Java permits a trailing comma in an array initializer.  Is it permitted if there are no
      // elements?
      or("", oneOrMore(annotationValueSingleRegex, "\\s*,\\s*") + "\\s*(?:,\\s*)?");

  /** An annotation value (that is possibly an array). This does not match nested arrays. */
  protected static final @Regex String annotationValueRegex =
      or(annotationValueSingleRegex, "\\{" + "\\s*+" + annotationArrayContentsRegex + "\\}");

  /** Matches an annotation argument. */
  protected static final @Regex String annotationArgumentRegex =
      // Optional field name
      ("(?:" + javaIdentifierRegex + "\\s*=\\s*" + ")?")
          // The value
          + annotationValueRegex;

  /**
   * Matches zero or more annotation arguments. This is what goes inside parentheses in an
   * annotation.
   */
  protected static final @Regex String annotationArgumentsRegex =
      zeroOrMore(annotationArgumentRegex, "\\s*,\\s*");

  /** Matches one Java annotation. */
  protected static final @Regex String annotationOnlyRegex =
      // at-sign
      "@"
          // Java identifier
          + javaIdentifierRegex
          // optional arguments
          + ("(?:\\s*\\(\\s*" + annotationArgumentsRegex + "\\s*\\))?");

  /** Matches one Java annotation OR modifier. */
  @SuppressWarnings("regex:assignment") // string concatenation
  protected static final @Regex String annotationRegex =
      // creates fewer gratuitous groups than `or(...)`
      String.join(
          "|",
          group(annotationOnlyRegex),
          "abstract",
          "final",
          "private",
          "protected",
          "public",
          "static",
          "synchronized",
          "transient",
          "volatile");

  /** Matches one or more Java annotations separated by spaces. */
  protected static final @Regex String annotationsRegex = oneOrMore(annotationRegex, "\\s+");

  /** Matches one or more Java annotations separated by spaces. */
  protected static final Pattern annotationsPattern = Pattern.compile(annotationsRegex);

  /** Matches zero or more Java annotations, each followed by space. */
  protected static final String annotationsSpacesRegex = "(?:(?:" + annotationRegex + ")\\s+)*";

  /** Matches a type, possibly followed by type parameters. */
  protected static final String parameterizedTypeRegex =
      javaDottedIdentifiersRegex
          + ("(?:<\\s*"
              + oneOrMore(annotationsSpacesRegex + javaIdentifierRegex, "\\s*,\\s*")
              + "\\s*>)?");

  /** Matches a "this" formal parameter. */
  protected static final @Regex String thisRegex =
      annotationsSpacesRegex + parameterizedTypeRegex + "\\s+" + "this" + "(?:\\s*,)?";

  /** Matches a "this" formal parameter. */
  protected static Pattern thisPattern = Pattern.compile(thisRegex);

  /** Matches an "extends Object" clause. */
  protected static final @Regex String extendsRegex =
      "extends\\s+" + annotationsSpacesRegex + "Object";

  /** Matches an extends clause. */
  protected static Pattern extendsPattern = Pattern.compile(extendsRegex);

  // TODO: Should this handle multiline?
  /** Matches an end-of-line comment. */
  protected static final Pattern commentPattern = Pattern.compile("//.*(\\z|\\R)");

  /** Matches the start of a Java annotation OR modifier. */
  protected static final @Regex String annotationStartRegex =
      "^"
          + group(
              String.join(
                  "|",
                  "@" + javaIdentifierRegex,
                  "abstract",
                  "final",
                  "private",
                  "protected",
                  "public",
                  "static",
                  "synchronized",
                  "transient",
                  "volatile"))
          + "\\b";

  /** Matches the start of a Java annotation OR modifier. */
  protected static final Pattern annotationStartPattern = Pattern.compile(annotationStartRegex);
}
