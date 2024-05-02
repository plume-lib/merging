package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.checkerframework.checker.regex.qual.Regex;
import org.junit.jupiter.api.Test;
import org.plumelib.util.StringsPlume;

public class JavaAnnotationsMergerTest {

  void assertIsJavaAnnotations(String s) {
    assertTrue(JavaAnnotationsMerger.isJavaAnnotations(s), s);
  }

  void assertMatches(Pattern p, String s) {
    assertTrue(p.matcher(s).matches());
  }

  void assertMatches(@Regex String regex, String s) {
    assertMatches(Pattern.compile(regex), s);
  }

  void assertNotMatches(Pattern p, String s) {
    assertFalse(p.matcher(s).matches());
  }

  String doubleQuoted(String s) {
    return "\"" + s + "\"";
  }

  void assertNotMatches(@Regex String regex, String s) {
    assertNotMatches(Pattern.compile(regex), s);
  }

  void assertAnnotationArrayContents(String s) {
    assertMatches(JavaAnnotationsMerger.annotationArrayContentsRegex, s);
  }

  void assertAnnotationValue(String s) {
    assertMatches(JavaAnnotationsMerger.annotationValueRegex, s);
  }

  void assertAnnotation(String s) {
    assertMatches(JavaAnnotationsMerger.annotationRegex, s);
  }

  void assertAnnotationArguments(String s) {
    assertMatches(JavaAnnotationsMerger.annotationArgumentsRegex, s);
  }

  void assertAnnotationArgument(String s) {
    assertMatches(JavaAnnotationsMerger.annotationArgumentRegex, s);
  }

  void assertExtends(String s) {
    assertMatches(JavaAnnotationsMerger.extendsPattern, s);
  }

  void assertThis(String s) {
    assertMatches(JavaAnnotationsMerger.thisPattern, s);
  }

  void assertAnnotations(String s) {
    assertMatches(JavaAnnotationsMerger.annotationsPattern, s);
  }

  String multilineAnnotation1 =
      StringsPlume.joinLines(
          "@SuppressWarnings({",
          "      \"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\",",
          "  })");

  String multilineAnnotation2 =
      StringsPlume.joinLines(
          "@SuppressWarnings({",
          "      \"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\"",
          "  })");

  String multilineAnnotationValue1 =
      StringsPlume.joinLines(
          "{",
          "      \"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\",",
          "  }");
  String multilineAnnotationValue2 =
      StringsPlume.joinLines(
          "{",
          "      \"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\"",
          "  }");

  String multilineArrayContents1 =
      StringsPlume.joinLines(
          "\"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\",");

  String multilineArrayContents2 =
      StringsPlume.joinLines(
          "\"value:argument\",",
          "      \"lowerbound:array.access.unsafe.low\",",
          "      \"upperbound:array.access.unsafe.high\"");

  @Test
  // This name makes it show up first in JUnit reports
  void testAaSubpatterns() {

    assertAnnotationArrayContents("");
    assertAnnotationArrayContents("64");
    assertAnnotationArrayContents("2,4, 8, 16");
    assertAnnotationArrayContents("2,\n4,\n 8, \n16");
    assertAnnotationArrayContents(multilineArrayContents1);
    assertAnnotationArrayContents(multilineArrayContents2);

    assertAnnotationValue("{}");
    assertAnnotationValue(multilineAnnotationValue1);
    assertAnnotationValue(multilineAnnotationValue2);

    assertAnnotationArgument("64");
    assertAnnotationArgument("foo= 64");
    assertAnnotationArgument("{}");

    assertAnnotationArguments("");
    assertAnnotationArguments("{}");
    assertAnnotationArguments("64");
    assertAnnotationArguments("from=2");
    assertAnnotationArguments("from=2, to=36");

    assertMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("easily"));
    assertMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("eas.ily"));
    assertMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("eas\\Xily"));
    assertMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("eas\\\\ily"));
    assertMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("eas\\.ily"));
    assertNotMatches(JavaAnnotationsMerger.stringRegex, doubleQuoted("eas\\.ily\\"));

    assertMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "easily");
    assertMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a.b");
    assertMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a.b.c");
    assertMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a1b2c");
    assertMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a1b2c.d3e");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a1b2c.3e");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a1b2c^d3e");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a..b");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, ".a.b");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "a.b.");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "1234");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "..");
    assertNotMatches(JavaAnnotationsMerger.javaDottedIdentifiersRegex, "");
  }

  @Test
  void testAnnotations() {

    assertAnnotation("@ArrayLen");

    assertAnnotation("@ArrayLen()");
    assertAnnotation("@ArrayLen(64)");
    assertAnnotation("@IntRange(from=2)");
    assertAnnotation("@IntRange(from=2, to=36)");

    assertAnnotations("@ArrayLen()");
    assertAnnotations("@ArrayLen(64)");
    assertAnnotations("@AssertMethod(VerifyException.class)");
    assertAnnotations("@IndexOrHigh(\"#1\")");
    assertAnnotations("@IndexOrLow(\"this\")");
    assertAnnotations("@IntRange(from=2)");
    assertAnnotations("@IntRange(from=2, to=36)");
    assertAnnotations("@KeyFor(\"this\")");
    assertAnnotations("@LTEqLengthOf(\"replacements\")");

    assertAnnotations("@AnnotatedFor({\"nullness\"})");
    assertAnnotations("@Format({ConversionCategory.INT})");

    assertAnnotations("@IntRange(from = 65) @LTLengthOf(value = \"#1\", offset = \"#2 - 1\")");
    assertAnnotations("@NonNegative @LTLengthOf(value = \"#1\", offset = \"#2 - 1\")");
    assertAnnotations("@NonNegative @LTLengthOf(value = \"#1\", offset = \"#3 - 1\")");
    assertAnnotations("@NonNegative @LessThan(\"1\")");
    assertAnnotations("@GTENegativeOne @LTEqLengthOf(\"#1\")");

    assertAnnotations(multilineAnnotation1);
    assertAnnotations(multilineAnnotation2);
  }

  @Test
  void testExtends() {
    assertExtends("extends @NonNull Object");
  }

  @Test
  void testThis() {
    assertThis("@PolyValue @Unsigned UnsignedLong this");
    assertThis("@PolyValue UnsignedLong this");
    assertThis("@PolyValue UnsignedLong this,");
    assertThis("@PolyValue UnsignedLong this  ,");
    assertThis("@UnknownSignedness Invokable<T, R> this");
    assertThis("EvictingQueue<@PolyNull @PolySigned E> this");
  }

  @Test
  void testIsJavaAnnotations() {
    assertIsJavaAnnotations("@Foo");
    assertIsJavaAnnotations("  @Foo ");
    assertIsJavaAnnotations(" @Foo @Bar ");
    assertIsJavaAnnotations(" @Foo @Bar(x=y, z=\"hello\") ");
    assertIsJavaAnnotations(" @Foo({\"a\", 22}) ");

    assertIsJavaAnnotations(" extends @NonNull Object");

    assertIsJavaAnnotations("@UnknownSignedness Invokable<T, R> this");

    assertIsJavaAnnotations("final");
    assertIsJavaAnnotations("  final ");
    assertIsJavaAnnotations("@Foo final @Bar");
    assertIsJavaAnnotations("final @Bar static");
    assertIsJavaAnnotations("final @Bar static @Baz");

    assertIsJavaAnnotations(
        "@SuppressWarnings(\"nullness:argument\")"
            + " // Suppressed due to annotations on remove in Java.Map");
    assertIsJavaAnnotations(
        "@SuppressWarnings(\"nullness:argument\")"
            + " // Suppressed due to annotations on remove in Java.Map"
            + "\n"
            + "@CheckForNull");

    assertIsJavaAnnotations(multilineAnnotation1);
    assertIsJavaAnnotations(multilineAnnotation2);

    assertIsJavaAnnotations("  ");
    assertIsJavaAnnotations("");
  }
}
