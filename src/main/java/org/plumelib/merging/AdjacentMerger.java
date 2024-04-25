package org.plumelib.merging;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import name.fraser.neil.plaintext.DmpLibrary;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.javacparse.JavacParse;
import org.plumelib.merging.ConflictedFile.ConflictElement;
import org.plumelib.merging.ConflictedFile.MergeConflict;
import org.plumelib.util.CollectionsPlume;
import org.plumelib.util.CollectionsPlume.Replacement;
import org.plumelib.util.StringsPlume;

/** This is a merger that resolves conflicts where the edits are on different but adjacent lines. */
public class AdjacentMerger implements Merger {

  /** If true, produce debugging output. */
  private static boolean verbose = false;

  /** Creates a JavaAdjacentMerger. */
  AdjacentMerger() {}

  @Override
  public void merge(MergeState mergeState) {
    if (!mergeState.hasConflict()) {
      return;
    }

    ConflictedFile cf = mergeState.conflictedFile();
    if (cf.parseError() != null) {
      String message = "AdjacentMerger: trouble reading merged file: " + cf.parseError();
      System.out.println(message);
      System.err.println(message);
      return;
    }

    @SuppressWarnings("nullness:assignment") // cf.parseError() == null => cf.contents() != null
    @NonNull List<ConflictElement> ces = cf.hunks();
    if (verbose) {
      System.out.printf(
          "conflicted file (size %s)=%s%n", (ces == null ? "null" : ("" + ces.size())), cf);
    }

    ConflictedFile newCf = resolveConflicts(cf);
    if (newCf != null) {
      mergeState.setConflictedFile(newCf);
    }
  }

  /**
   * Given a conflicted file, returns a new one, possibly with some conflicts resolved. Returns null
   * if no changes were made.
   *
   * @param cf the conflicted file, which should not be erroneous
   * @return the new file contents, or null if no changes were made
   */
  @Nullable ConflictedFile resolveConflicts(ConflictedFile cf) {

    // Todo: localize the test for conflicts.
    List<ConflictElement> ces = cf.hunks();
    if (ces == null) {
      throw new Error("Erroneous ConflictedFile");
    }

    List<Replacement<String>> replacements = new ArrayList<>();

    diff_match_patch dmp = new diff_match_patch();
    dmp.Match_Threshold = 0.0f;
    dmp.Patch_DeleteThreshold = 0.0f;

    for (ConflictElement ce : ces) {
      if (!(ce instanceof MergeConflict)) {
        continue;
      }
      MergeConflict mc = (MergeConflict) ce;
      String leftContent = StringsPlume.join("", mc.left());
      String rightContent = StringsPlume.join("", mc.right());
      if (verbose) {
        System.err.printf("calling diff_main([[[%s]]], [[[%s]]])%n", leftContent, rightContent);
      }

      String baseJoined = mc.baseJoined();
      if (baseJoined == null) {
        throw new Error("AdjacentMerger needs a 3-way diff");
      }
      if (DmpLibrary.affectedLinesOverlap(baseJoined, mc.leftJoined(), mc.rightJoined())) {
        System.out.println("Affected lines overlap: " + mc);
        return null;
      }

      List<String> merged = mergedWithAdjacent(mc);
      if (merged != null) {
        replacements.add(Replacement.of(mc.start(), mc.end() - 1, merged));
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
   * If all the edits are on different lines, then return a string that contains them all.
   * Otherwise, return null.
   *
   * @param mc the merge conflict, which includes the base, left, and right texts
   * @return the merged differences or null
   */
  private static @Nullable List<String> mergedWithAdjacent(MergeConflict mc) {
    List<String> baseLines = mc.base();
    String baseJoined = mc.baseJoined();
    if (baseLines == null || baseJoined == null) {
      throw new Error("AdjacentMerger needs a 3-way diff");
    }
    Iterator<Diff> itorLeft = DmpLibrary.diffLineHash(baseJoined, mc.leftJoined()).iterator();
    Iterator<Diff> itorRight = DmpLibrary.diffLineHash(baseJoined, mc.rightJoined()).iterator();
    Diff leftDiff = itorLeft.hasNext() ? itorLeft.next() : null;
    Diff rightDiff = itorRight.hasNext() ? itorRight.next() : null;
    // The line number in the base, left, or right text.
    int baseLineNumber = 0;
    int leftLineNumber = 0;
    int rightLineNumber = 0;
    // The line number in the base text
    int leftBaseLineNumber = 0;
    int rightBaseLineNumber = 0;

    List<String> result = new ArrayList<>();
    int numBaseLines = baseLines.size();
    while (baseLineNumber < numBaseLines) {
      if (verbose) {
        System.out.printf(
            "baseLineNumber=%s leftLineNumber=%s rightLineNumber=%s leftBaseLineNumber=%s"
                + " rightBaseLineNumber=%s%n",
            baseLineNumber,
            leftLineNumber,
            rightLineNumber,
            leftBaseLineNumber,
            rightBaseLineNumber);
      }
      boolean leftMatches = baseLineNumber == leftBaseLineNumber;
      boolean rightMatches = baseLineNumber == rightBaseLineNumber;
      if (!leftMatches && !rightMatches) {
        result.add(baseLines.get(baseLineNumber));
        baseLineNumber++;
      } else if (leftMatches && rightMatches) {
        boolean leftIsEqual = leftDiff != null && leftDiff.operation == Operation.EQUAL;
        boolean rightIsEqual = rightDiff != null && rightDiff.operation == Operation.EQUAL;
        if (!leftIsEqual && !rightIsEqual) {
          // The edits overlap.  This isn't the only way they can overlap, but it's the only one we
          // double-check in this algorithm.
          throw new Error("Overlapping edits.");
        }
        if (leftIsEqual) {
          assert leftDiff != null : "@AssumeAssertion(nullness): leftIsEqual => leftDiff!=null";
          leftLineNumber += leftDiff.text.length();
          leftBaseLineNumber += leftDiff.text.length();
          leftDiff = itorLeft.hasNext() ? itorLeft.next() : null;
          if (leftDiff == null) {
            leftLineNumber = -1;
            leftBaseLineNumber = -1;
          }
        }
        if (rightIsEqual) {
          assert rightDiff != null : "@AssumeAssertion(nullness): rightIsEqual => rightDiff!=null";
          rightLineNumber += rightDiff.text.length();
          rightBaseLineNumber += rightDiff.text.length();
          rightDiff = itorRight.hasNext() ? itorRight.next() : null;
          if (rightDiff == null) {
            rightLineNumber = -1;
            rightBaseLineNumber = -1;
          }
        }
      } else if (leftMatches) {
        assert leftDiff != null : "@AssumeAssertion(nullness): leftMatches => leftDiff!=null";
        int leftLength = leftDiff.text.length();
        switch (leftDiff.operation) {
          case EQUAL -> {
            leftLineNumber += leftLength;
            leftBaseLineNumber += leftLength;
          }
          case INSERT -> {
            for (int i = 0; i < leftLength; i++) {
              result.add(mc.left().get(leftLineNumber));
              leftLineNumber++;
            }
          }
          case DELETE -> {
            leftBaseLineNumber += leftLength;
          }
        }
        leftDiff = itorLeft.hasNext() ? itorLeft.next() : null;
        if (leftDiff == null) {
          leftLineNumber = -1;
          leftBaseLineNumber = -1;
        }
      } else if (rightMatches) {
        assert rightDiff != null : "@AssumeAssertion(nullness): rightMatches => rightDiff!=null";
        int rightLength = rightDiff.text.length();
        switch (rightDiff.operation) {
          case EQUAL -> {
            rightLineNumber += rightLength;
            rightBaseLineNumber += rightLength;
          }
          case INSERT -> {
            for (int i = 0; i < rightLength; i++) {
              result.add(mc.right().get(rightLineNumber));
              rightLineNumber++;
            }
          }
          case DELETE -> {
            rightBaseLineNumber += rightLength;
          }
        }
        rightDiff = itorRight.hasNext() ? itorRight.next() : null;
        if (rightDiff == null) {
          rightLineNumber = -1;
          rightBaseLineNumber = -1;
        }
      } else {
        throw new Error("this can't happen");
      }
    }

    return result;
  }

  /**
   * Groups the regex.
   *
   * @param regex a regex
   * @return the regex, grouped
   */
  protected static String group(String regex) {
    return "(?:" + regex + ")";
  }

  /**
   * Returns a regex that matches any of the given regexes.
   *
   * @param regexes the disjuncts
   * @return a regex that matches any of the given regexes
   */
  protected static String or(String... regexes) {
    if (regexes.length < 2) {
      throw new Error("not enough arguments to or(): " + Arrays.toString(regexes));
    }
    List<String> groupedElts = CollectionsPlume.mapList(AdjacentMerger::group, regexes);
    return group(String.join("|", groupedElts));
  }

  /**
   * Matches one or more of the given regex, separated by the given separator (also a regex).
   *
   * @param regex the regex to possibly repeat
   * @param separator the regex that separates the occurrences
   * @return a regex that matches one or more of the given regex, separated by the given separator
   */
  protected static String oneOrMoreRegex(String regex, String separator) {
    return group(regex) + group(group(separator) + group(regex)) + "*";
  }

  /**
   * Matches zero or more of the given regex, separated by the given separator (also a regex).
   *
   * @param regex the regex to possibly repeat
   * @param separator the regex that separates the occurrences
   * @return a regex that matches zero or more of the given regex, separated by the given separator
   */
  protected static String zeroOrMoreRegex(String regex, String separator) {
    return group(oneOrMoreRegex(regex, separator)) + "?";
  }

  /** Matches a Java identifier. */
  protected static final String javaIdentifierRegex =
      "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

  /**
   * Matches dotted identifiers, which might be an enum or CLASSNAME.class, both of which are
   * permitted as annotation arguments.
   */
  protected static final String javaDottedIdentifiersRegex =
      oneOrMoreRegex(javaIdentifierRegex, "\\.");

  /** Matches an integral or floating-point value. */
  protected static final String numberRegex =
      "[-+]?"
          + group(
              String.join(
                  "|",
                  // has a whole-number part
                  // The possessive qualifier "++" is important for efficiency.
                  group("[0-9]++(\\.[0-9]*)?"),
                  // has no whole-number part
                  "\\.[0-9]+"));

  /** Matches a string. */
  protected static final String stringRegex = "\"(?:[^\\\"]|\\.)*\"";

  /** A single annotation value. */
  protected static final String annotationValueSingleRegex =
      or(stringRegex, numberRegex, javaDottedIdentifiersRegex);

  /**
   * Zero or more non-array annotation values, separated by commas and possibly followed by a comma.
   */
  @SuppressWarnings("regex:assignment") // string concatenation
  protected static final @Regex String annotationArrayContentsRegex =
      // Java permits a trailing comma in an array initializer.  Is it permitted if there are no
      // elements?
      or("", oneOrMoreRegex(annotationValueSingleRegex, "\\s*,\\s*") + "\\s*(?:,\\s*)?");

  /** An annotation value (that is possibly an array). This does not match nested arrays. */
  @SuppressWarnings("regex:assignment") // string concatenation
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
  @SuppressWarnings("regex:assignment") // string concatenation
  protected static final @Regex String annotationArgumentsRegex =
      zeroOrMoreRegex(annotationArgumentRegex, "\\s*,\\s*");

  /** Matches one Java annotation. */
  protected static final @Regex String annotationOnlyRegex =
      // at-sign
      "@"
          // Java identifier
          + javaIdentifierRegex
          // optional arguments
          + ("(?:\\s*\\(" + annotationArgumentsRegex + "\\))?");

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

  /** Matches one or more Java annotations. */
  @SuppressWarnings("regex:assignment") // string concatenation to create regex
  protected static final @Regex String annotationsRegex = oneOrMoreRegex(annotationRegex, "\\s+");

  /** Matches one or more Java annotations. */
  protected static final Pattern annotationsPattern = Pattern.compile(annotationsRegex);

  /** Matches zero or more Java annotations, each followed by space. */
  protected static final String annotationsSpacesRegex = "(?:(?:" + annotationRegex + ")\\s+)*";

  /** Matches a type, possibly followed by type parameters. */
  protected static final String parameterizedTypeRegex =
      javaDottedIdentifiersRegex
          + ("(?:<"
              + oneOrMoreRegex(annotationsSpacesRegex + javaIdentifierRegex, "\\s*,\\s*")
              + ">)?");

  /** Matches a "this" formal parameter. */
  @SuppressWarnings("regex:assignment") // string concatenation to create regex
  protected static final @Regex String thisRegex =
      annotationsSpacesRegex + parameterizedTypeRegex + "\\s+" + "this";

  /** Matches a "this" formal parameter. */
  protected static Pattern thisPattern = Pattern.compile(thisRegex);

  /** Matches an extends clause. */
  @SuppressWarnings("regex:assignment") // string concatenation to create regex
  protected static final @Regex String extendsRegex =
      "extends\\s+" + annotationsSpacesRegex + parameterizedTypeRegex;

  /** Matches an extends clause. */
  protected static Pattern extendsPattern = Pattern.compile(extendsRegex);

  // TODO: Should this handle multiline?
  /** Matches an end-of-line comment. */
  protected static final Pattern commentPattern = Pattern.compile("//.*(\\z|[\r\n]+)");

  /** Matches the start of a Java annotation OR modifier. */
  @SuppressWarnings("regex:assignment") // string concatenation
  protected static final @Regex String annotationStartRegex =
      "^"
          + String.join(
              "|",
              "@\\p{javaJavaIdentifierStart}",
              "abstract",
              "final",
              "private",
              "protected",
              "public",
              "static",
              "synchronized",
              "transient",
              "volatile")
          + "\\b";

  /** Matches the start of a Java annotation OR modifier. */
  protected static final Pattern annotationStartPattern = Pattern.compile(annotationStartRegex);

  /**
   * Returns true if the given text is one or more Java annotations.
   *
   * @param text a string
   * @return true if the given text is one or more Java annotations
   */
  // "protected" to permit tests to access it.
  protected static boolean isJavaAdjacent(String text) {
    text = commentPattern.matcher(text).replaceAll(" ");
    text = text.trim();
    if (text.isEmpty()) {
      return true;
    }

    // At one time, the parser calls were very expensive, and text matching was cheaper.
    // I have not re-measured recently.

    // Set to false only to double-check that parsing and regexes give the same result (by running
    // the unit tests).
    final boolean useRegex = true;

    String declText = null;
    // The test for " this" must precede the test for "@", because both can be true.
    if (text.endsWith(" this")) {
      if (useRegex && thisPattern.matcher(text).matches()) {
        return true;
      } else {
        declText = text.substring(0, text.length() - 4) + "varname";
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
    // System.out.printf("isJavaAdjacent: %s%n", text);

    JCCompilationUnit mergedCU = JavacParse.parseJavaCode(classText);
    return mergedCU != null;
  }
}
