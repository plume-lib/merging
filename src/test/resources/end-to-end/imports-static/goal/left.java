package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticImports {

  void test() {
    assertEquals(1, 1);
    assertTrue(true);
  }

  void other() {
    assertFalse(false);
  }
}
