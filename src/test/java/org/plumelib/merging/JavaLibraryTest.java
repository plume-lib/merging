package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JavaLibraryTest {

  @Test
  void testIsCommentLine() {
    assertTrue(JavaLibrary.isCommentLine("// x"));
    assertTrue(JavaLibrary.isCommentLine("  // x"));
    assertTrue(JavaLibrary.isCommentLine("  // x\n"));
    assertTrue(JavaLibrary.isCommentLine("  // x\r"));
    assertTrue(JavaLibrary.isCommentLine("  // x\r\n"));
    assertTrue(JavaLibrary.isCommentLine("/* x */"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */\n"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */\r"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */\r\n"));
    assertTrue(JavaLibrary.isCommentLine("/* x */  "));
    assertTrue(JavaLibrary.isCommentLine("  /* x */  "));
    assertTrue(JavaLibrary.isCommentLine("  /* x */  \n"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */  \r"));
    assertTrue(JavaLibrary.isCommentLine("  /* x */  \r\n"));
    assertTrue(JavaLibrary.isCommentLine("/* // x */  "));
    assertTrue(JavaLibrary.isCommentLine("//*"));

    assertFalse(JavaLibrary.isCommentLine("/*/  "));
    assertFalse(JavaLibrary.isCommentLine("  /*/  "));
    assertFalse(JavaLibrary.isCommentLine("  /* x *  "));
    assertFalse(JavaLibrary.isCommentLine("  /* x /  "));
  }
}
