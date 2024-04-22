package name.fraser.neil.plaintext;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

  @Test
  void testAffectedLines() {
    assertEquals(List.of(1), DmpLibrary.affectedLines(test1base, test1a));
    assertEquals(List.of(2), DmpLibrary.affectedLines(test1base, test1b));
  }
}
