package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class StaticImports {

  void test() {
    assertEquals(1, 1);
  }

  void other() {
    assertFalse(false);
  }
}
