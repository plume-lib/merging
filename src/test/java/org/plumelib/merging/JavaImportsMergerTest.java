package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.plumelib.merging.fileformat.ConflictedFile.CommonLines;
import org.plumelib.merging.fileformat.ConflictedFile.ConflictElement;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;

final class JavaImportsMergerTest {

  /** Creates a new JavaImportsMergerTest. */
  JavaImportsMergerTest() {}

  void assertMergeImportConflictCommentwise(
      List<String> left, List<String> right, List<String> goal) {
    ConflictElement ce = MergeConflict.of(null, left, right, 0, 0);
    CommonLines cl;
    if (ce instanceof MergeConflict mc) {
      cl = JavaImportsMerger.mergeImportsCommentwise(mc);
    } else {
      cl = (CommonLines) ce;
    }
    assertEquals(goal, cl.textLines());
  }

  @Test
  void testMergeCommentwise() {

    List<String> x = List.of("import x;");
    List<String> xa = List.of("import x;", "// comment A");
    List<String> ax = List.of("// comment A", "import x;");
    List<String> ya = List.of("import y;", "// comment A");
    List<String> ay = List.of("// comment A", "import y;");

    List<String> xy = List.of("import x;", "import y;");
    List<String> xay = List.of("import x;", "// comment A", "import y;");
    List<String> yx = List.of("import y;", "import x;");
    List<String> xaz = List.of("import x;", "// comment A", "import z;");
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
}
