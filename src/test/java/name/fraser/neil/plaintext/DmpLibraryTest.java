package name.fraser.neil.plaintext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

public class DmpLibraryTest {

  String test1base = """
    a
    b
    c
    d
    """;
  String test1a = """
    a
    bleft1
    bleft2
    bleft3
    c
    d
    """;
  String test1b = """
    a
    b
    d
    """;
  String test1goal = """
    a
    bleft1
    bleft2
    bleft3
    d
    """;

  String test2base = """
    a
    b
    c
    """;
  String test2a = """
    a
    bleft1
    bleft2
    bleft3
    c
    """;
  String test2b = """
    aright
    b
    cright
    """;
  String test2goal = """
    aright
    bleft1
    bleft2
    bleft3
    cright
    """;

  @Test
  void testAffectedLines() {
    // These diffs have 5 elements: "delete,insert,equal,delete,insert".
    assertEquals(5, DmpLibrary.dmp.diff_main("abc", "dbf", true).size());
    assertEquals(5, DmpLibrary.dmp.diff_main("abc", "dbf", false).size());
    assertEquals(5, DmpLibrary.dmp.diff_main("", "", false).size());

    assertEquals(List.of(1), DmpLibrary.affectedLines(test1base, test1a));
    assertEquals(List.of(2), DmpLibrary.affectedLines(test1base, test1b));
    assertEquals(List.of(1), DmpLibrary.affectedLines(test2base, test2a));
    assertEquals(List.of(0, 2), DmpLibrary.affectedLines(test2base, test2b));
  }

  @Test
  void testAffectedLinesOverlap() {
    assertFalse(DmpLibrary.affectedLinesOverlap(test1base, test1a, test1b));
    assertFalse(DmpLibrary.affectedLinesOverlap(test2base, test2a, test2b));
  }
}
