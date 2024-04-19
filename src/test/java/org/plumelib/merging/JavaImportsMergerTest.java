package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.plumelib.merging.ConflictedFile.CommonLines;
import org.plumelib.merging.ConflictedFile.ConflictElement;
import org.plumelib.merging.ConflictedFile.MergeConflict;

public class JavaImportsMergerTest {

  @Test
  void testIsCommentLine() {
    assertTrue(JavaImportsMerger.isCommentLine("// x"));
    assertTrue(JavaImportsMerger.isCommentLine("  // x"));
    assertTrue(JavaImportsMerger.isCommentLine("  // x\n"));
    assertTrue(JavaImportsMerger.isCommentLine("  // x\r"));
    assertTrue(JavaImportsMerger.isCommentLine("  // x\r\n"));
    assertTrue(JavaImportsMerger.isCommentLine("/* x */"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */\n"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */\r"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */\r\n"));
    assertTrue(JavaImportsMerger.isCommentLine("/* x */  "));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */  "));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */  \n"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */  \r"));
    assertTrue(JavaImportsMerger.isCommentLine("  /* x */  \r\n"));
    assertTrue(JavaImportsMerger.isCommentLine("/* // x */  "));
    assertTrue(JavaImportsMerger.isCommentLine("//*"));

    assertFalse(JavaImportsMerger.isCommentLine("/*/  "));
    assertFalse(JavaImportsMerger.isCommentLine("  /*/  "));
    assertFalse(JavaImportsMerger.isCommentLine("  /* x *  "));
    assertFalse(JavaImportsMerger.isCommentLine("  /* x /  "));
  }

  void assertMergeImportConflictCommentwise(
      List<String> left, List<String> right, List<String> goal) {
    ConflictElement ce = MergeConflict.of(left, right, null, 0, 0);
    CommonLines cl;
    if (ce instanceof MergeConflict mc) {
      cl = JavaImportsMerger.mergeImportConflictCommentwise(mc);
    } else {
      cl = (CommonLines) ce;
    }
    assertEquals(goal, cl.textLines());
  }

  @SuppressWarnings("UnusedVariable")
  @Test
  void testMergeCommentwise() {

    List<String> x = List.of("import x;");
    List<String> xa = List.of("import x;", "// comment A");
    List<String> ax = List.of("// comment A", "import x;");
    List<String> y = List.of("import y;");
    List<String> ya = List.of("import y;", "// comment A");
    List<String> ay = List.of("// comment A", "import y;");
    List<String> z = List.of("import z;");
    List<String> za = List.of("import z;", "// comment A");
    List<String> az = List.of("// comment A", "import z;");

    List<String> xy = List.of("import x;", "import y;");
    List<String> xay = List.of("import x;", "// comment A", "import y;");
    List<String> yx = List.of("import y;", "import x;");
    List<String> xz = List.of("import x;", "import z;");
    List<String> xaz = List.of("import x;", "// comment A", "import z;");
    List<String> zx = List.of("import z;", "import x;");
    List<String> yz = List.of("import y;", "import z;");
    List<String> yaz = List.of("import y;", "// comment A", "import z;");
    List<String> zy = List.of("import z;", "import y;");

    List<String> xyz = List.of("import x;", "import y;", "import z;");
    List<String> xayz = List.of("import x;", "// comment A", "import y;", "import z;");
    List<String> xyaz = List.of("import x;", "import y;", "// comment A", "import z;");
    List<String> xybz = List.of("import x;", "import y;", "// comment B", "import z;");
    List<String> xaybz =
        List.of("import x;", "// comment A", "import y;", "// comment B", "import z;");
    List<String> xayaz =
        List.of("import x;", "// comment A", "import y;", "// comment A", "import z;");

    assertMergeImportConflictCommentwise(xy, xyz, xyz);
    assertMergeImportConflictCommentwise(xyz, xy, xyz);
    assertMergeImportConflictCommentwise(x, yz, xyz);
    assertMergeImportConflictCommentwise(x, zy, xyz);
    assertMergeImportConflictCommentwise(yx, yz, xyz);

    assertMergeImportConflictCommentwise(xa, xa, xa);
    assertMergeImportConflictCommentwise(ax, ax, ax);
    assertMergeImportConflictCommentwise(xay, ay, xay);

    assertMergeImportConflictCommentwise(xa, xa, xa);

    assertMergeImportConflictCommentwise(xyz, xyz, xyz);
    assertMergeImportConflictCommentwise(xayz, xayz, xayz);
    assertMergeImportConflictCommentwise(xybz, xybz, xybz);
    assertMergeImportConflictCommentwise(xaybz, xaybz, xaybz);
    assertMergeImportConflictCommentwise(xayaz, xayaz, xayaz);

    assertMergeImportConflictCommentwise(xayz, xa, xayz);
    assertMergeImportConflictCommentwise(xayz, ay, xayz);
    assertMergeImportConflictCommentwise(xyaz, ya, xyaz);
    assertMergeImportConflictCommentwise(xyaz, xa, xyaz);
    assertMergeImportConflictCommentwise(xaz, yaz, xyaz);
  }

  void assertJavaImportsMerger(String fileBaseName) {
    assertJavaImportsMerger(
        fileBaseName + "A.java",
        fileBaseName + "B.java",
        fileBaseName + "Base.java",
        fileBaseName + "Output.java",
        fileBaseName + "Goal.java");
  }

  void assertJavaImportsMerger(
      String fileA, String fileB, String fileBase, String fileOutput, String fileGoal) {
    Path pathA = Paths.get("src", "test", "resources", fileA);
    // Path pathB = Paths.get("src", "test", "resources", fileB);
    // Path pathBase = Paths.get("src", "test", "resources", fileBase);
    Path pathOutput = Paths.get("src", "test", "resources", fileOutput);
    Path pathGoal = Paths.get("src", "test", "resources", fileGoal);
    try {
      Files.copy(pathA, pathOutput, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new Error("Problem copying " + pathA + " to " + pathOutput, e);
    }
    MergeState ms = new MergeState(fileBase, fileA, fileB, fileOutput, true);
    JavaImportsMerger jimd = new JavaImportsMerger();
    jimd.merge(ms);
    try {
      assertEquals(
          -1L,
          Files.mismatch(pathGoal, pathOutput),
          "Mismatch between " + pathGoal + " and " + pathOutput);
    } catch (IOException e) {
      throw new Error("Problem comparing " + pathGoal + " to " + pathOutput, e);
    }
  }

  /// This test gets skipped, and causes other tests not to run.  So, for now, run the tests via a
  /// Makefile. :-(
  // @Test
  // void testJavaImportsMerger() {
  //   assertJavaImportsMerger("ImportsTest1");
  //   assertJavaImportsMerger("ImportsTest2");
  // }
}
