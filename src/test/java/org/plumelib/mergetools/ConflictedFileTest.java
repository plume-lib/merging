package org.plumelib.mergetools;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.plumelib.mergetools.ConflictedFile.CommonLines;
import org.plumelib.mergetools.ConflictedFile.ConflictElement;
import org.plumelib.mergetools.ConflictedFile.MergeConflict;
import org.plumelib.util.FilesPlume;

public class ConflictedFileTest {

  private String fileContents(String file) {
    try {
      ClassLoader cl = this.getClass().getClassLoader();
      if (cl == null) {
        throw new Error("no class leader for " + this.getClass());
      }
      try (InputStream is = cl.getResourceAsStream(file)) {
        if (is == null) {
          throw new Error("Can't find resource " + file);
        }
        return FilesPlume.streamString(is);
      }
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private ConflictedFile parseConflictedFile(String filename) {
    String fileContents = fileContents(filename);
    return ConflictedFile.parseFileContents(fileContents);
  }

  private void testNonConflictedFile(String filename) {
    String fileContents = fileContents(filename);
    ConflictedFile cf = ConflictedFile.parseFileContents(fileContents);
    Assertions.assertNull(cf.error());
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents = cf.contents();
    Assertions.assertEquals(1, contents.size());
    ConflictElement elt = contents.get(0);
    Assertions.assertEquals(fileContents, ((CommonLines) elt).joinedLines());
  }

  @Test
  void testNonConflictedFiles() {
    testNonConflictedFile("non-conflicted-file.txt");
    testNonConflictedFile("non-conflicted-file-without-terminator.txt");
  }

  @Test
  void testConflictedFiles() {
    ConflictedFile cf1 = parseConflictedFile("conflicted-file-1.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents1 = cf1.contents();
    Assertions.assertEquals(5, contents1.size());
    Assertions.assertEquals("pre-text\n", ((CommonLines) contents1.get(0)).joinedLines());
    Assertions.assertEquals(null, ((MergeConflict) contents1.get(1)).base());
    Assertions.assertEquals("mid-text\n", ((CommonLines) contents1.get(2)).joinedLines());
    Assertions.assertEquals(
        "2puts 'hello world'\n", ((MergeConflict) contents1.get(3)).baseJoined());
    Assertions.assertEquals("post-text\n", ((CommonLines) contents1.get(4)).joinedLines());

    ConflictedFile cf2 = parseConflictedFile("conflicted-file-1-no-common-text.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents2 = cf2.contents();
    Assertions.assertEquals(2, contents2.size());
    MergeConflict conflict1 = (MergeConflict) contents2.get(0);
    Assertions.assertEquals("1puts 'hola world'\n", conflict1.leftJoined());
    Assertions.assertEquals("1puts 'hello mundo'\n", conflict1.rightJoined());
    Assertions.assertEquals(null, conflict1.base());
    MergeConflict conflict2 = (MergeConflict) contents2.get(1);
    Assertions.assertEquals("2puts 'hola world'\n", conflict2.leftJoined());
    Assertions.assertEquals("2puts 'hello mundo'\n", conflict2.rightJoined());
    Assertions.assertEquals("2puts 'hello world'\n", conflict2.baseJoined());

    ConflictedFile cf3 = parseConflictedFile("conflicted-file-1-more-common-text.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents3 = cf3.contents();
    Assertions.assertEquals(5, contents3.size());
    Assertions.assertEquals(
        "pre-text-a\npre-text-b\npre-text-c\n", ((CommonLines) contents3.get(0)).joinedLines());
    Assertions.assertEquals(null, ((MergeConflict) contents3.get(1)).base());
    Assertions.assertEquals(
        "mid-text-a\nmid-text-b\nmid-text-c\n", ((CommonLines) contents3.get(2)).joinedLines());
    Assertions.assertEquals(
        "2puts 'hello world'\n", ((MergeConflict) contents3.get(3)).baseJoined());
    Assertions.assertEquals(
        "post-text-a\npost-text-b\npost-text-c\n", ((CommonLines) contents3.get(4)).joinedLines());

    ConflictedFile cf4 = parseConflictedFile("conflicted-file-no-left.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents4 = cf4.contents();
    Assertions.assertEquals(2, contents4.size());
    Assertions.assertEquals(null, ((MergeConflict) contents4.get(0)).base());
    Assertions.assertEquals(
        "2puts 'hello world'\n", ((MergeConflict) contents4.get(1)).baseJoined());

    ConflictedFile cf5 = parseConflictedFile("conflicted-file-no-right.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents5 = cf5.contents();
    Assertions.assertEquals(5, contents5.size());
    Assertions.assertEquals("pre-text\n", ((CommonLines) contents5.get(0)).joinedLines());
    Assertions.assertEquals(null, ((MergeConflict) contents5.get(1)).base());
    Assertions.assertEquals("mid-text\n", ((CommonLines) contents5.get(2)).joinedLines());
    Assertions.assertEquals(
        "2puts 'hello world'\n", ((MergeConflict) contents5.get(3)).baseJoined());
    Assertions.assertEquals("post-text\n", ((CommonLines) contents5.get(4)).joinedLines());

    ConflictedFile cf6 = parseConflictedFile("conflicted-file-2.txt");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents6 = cf6.contents();
    Assertions.assertEquals(2, contents6.size());
    Assertions.assertTrue(contents6.get(0) instanceof MergeConflict);
    Assertions.assertTrue(contents6.get(1) instanceof CommonLines);

    ConflictedFile cf7 =
        ConflictedFile.parseFileContents(
            "<<<<<<< ImportsTest2Output.java\n"
                + "import java.io.File;\n"
                + "||||||| ImportsTest2Base.java\n"
                + "=======\n"
                + "import java.nio.file.Path;\n"
                + ">>>>>>> ImportsTest2B.java\n"
                + "\n"
                + "public class ImportsTest {\n"
                + "}\n");
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> contents7 = cf7.contents();
    Assertions.assertEquals(2, contents7.size());
    Assertions.assertTrue(contents7.get(0) instanceof MergeConflict);
    Assertions.assertTrue(contents7.get(1) instanceof CommonLines);
  }
}
