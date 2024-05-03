package org.plumelib.merging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.plumelib.merging.fileformat.ConflictedFile;
import org.plumelib.merging.fileformat.ConflictedFile.CommonLines;
import org.plumelib.merging.fileformat.ConflictedFile.ConflictElement;
import org.plumelib.merging.fileformat.ConflictedFile.MergeConflict;
import org.plumelib.util.FilesPlume;

public class ConflictedFileTest {

  private String fileContents(String file) {
    try {
      ClassLoader cl = this.getClass().getClassLoader();
      if (cl == null) {
        throw new Error("no class loader for " + this.getClass());
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
    return new ConflictedFile(fileContents(filename), Path.of(filename));
  }

  private void testNonConflictedFile(String filename) {
    String fileContents = fileContents(filename);
    ConflictedFile cf =
        new ConflictedFile(fileContents, Path.of("testNonConflictedFile JUnit test"));
    Assertions.assertNull(cf.parseError());
    @SuppressWarnings("nullness:assignment")
    @NonNull List<ConflictElement> hunks = cf.hunks();
    Assertions.assertEquals(1, hunks.size());
    ConflictElement elt = hunks.get(0);
    Assertions.assertEquals(fileContents, ((CommonLines) elt).joinedLines());
  }

  @Test
  void testNonConflictedFiles() {
    testNonConflictedFile("non-conflicted-file.txt");
    testNonConflictedFile("non-conflicted-file-without-terminator.txt");
  }

  @Test
  void testConflictedFiles() {
    {
      ConflictedFile cf1 = parseConflictedFile("conflicted-file-1.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks1 = cf1.hunks();
      Assertions.assertEquals(5, hunks1.size());
      Assertions.assertEquals("pre-text\n", ((CommonLines) hunks1.get(0)).joinedLines());
      Assertions.assertEquals(null, ((MergeConflict) hunks1.get(1)).base());
      Assertions.assertEquals("mid-text\n", ((CommonLines) hunks1.get(2)).joinedLines());
      Assertions.assertEquals(
          "2puts 'hello world'\n", ((MergeConflict) hunks1.get(3)).baseJoined());
      Assertions.assertEquals("post-text\n", ((CommonLines) hunks1.get(4)).joinedLines());
    }

    {
      ConflictedFile cf2 = parseConflictedFile("conflicted-file-1-no-common-text.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks2 = cf2.hunks();
      Assertions.assertEquals(2, hunks2.size());
      MergeConflict conflict1 = (MergeConflict) hunks2.get(0);
      Assertions.assertEquals("1puts 'hola world'\n", String.join("", conflict1.left()));
      Assertions.assertEquals("1puts 'hello mundo'\n", String.join("", conflict1.right()));
      Assertions.assertEquals(null, conflict1.base());
      MergeConflict conflict2 = (MergeConflict) hunks2.get(1);
      Assertions.assertEquals("2puts 'hola world'\n", String.join("", conflict2.left()));
      Assertions.assertEquals("2puts 'hello mundo'\n", String.join("", conflict2.right()));
      Assertions.assertEquals("2puts 'hello world'\n", conflict2.baseJoined());
    }

    {
      ConflictedFile cf3 = parseConflictedFile("conflicted-file-1-more-common-text.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks3 = cf3.hunks();
      Assertions.assertEquals(5, hunks3.size());
      Assertions.assertEquals(
          "pre-text-a\npre-text-b\npre-text-c\n", ((CommonLines) hunks3.get(0)).joinedLines());
      Assertions.assertEquals(null, ((MergeConflict) hunks3.get(1)).base());
      Assertions.assertEquals(
          "mid-text-a\nmid-text-b\nmid-text-c\n", ((CommonLines) hunks3.get(2)).joinedLines());
      Assertions.assertEquals(
          "2puts 'hello world'\n", ((MergeConflict) hunks3.get(3)).baseJoined());
      Assertions.assertEquals(
          "post-text-a\npost-text-b\npost-text-c\n", ((CommonLines) hunks3.get(4)).joinedLines());
    }

    {
      ConflictedFile cf4 = parseConflictedFile("conflicted-file-no-left.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks4 = cf4.hunks();
      Assertions.assertEquals(2, hunks4.size());
      Assertions.assertEquals(null, ((MergeConflict) hunks4.get(0)).base());
      Assertions.assertEquals(
          "2puts 'hello world'\n", ((MergeConflict) hunks4.get(1)).baseJoined());
    }

    {
      ConflictedFile cf5 = parseConflictedFile("conflicted-file-no-right.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks5 = cf5.hunks();
      Assertions.assertEquals(5, hunks5.size());
      Assertions.assertEquals("pre-text\n", ((CommonLines) hunks5.get(0)).joinedLines());
      Assertions.assertEquals(null, ((MergeConflict) hunks5.get(1)).base());
      Assertions.assertEquals("mid-text\n", ((CommonLines) hunks5.get(2)).joinedLines());
      Assertions.assertEquals(
          "2puts 'hello world'\n", ((MergeConflict) hunks5.get(3)).baseJoined());
      Assertions.assertEquals("post-text\n", ((CommonLines) hunks5.get(4)).joinedLines());
    }

    {
      ConflictedFile cf6 = parseConflictedFile("conflicted-file-2.txt");
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks6 = cf6.hunks();
      Assertions.assertEquals(2, hunks6.size());
      Assertions.assertTrue(hunks6.get(0) instanceof MergeConflict);
      Assertions.assertTrue(hunks6.get(1) instanceof CommonLines);
    }

    {
      ConflictedFile cf7 =
          new ConflictedFile(
              "<<<<<<< ImportsTest2Output.java\n"
                  + "import java.io.File;\n"
                  + "||||||| ImportsTest2Base.java\n"
                  + "=======\n"
                  + "import java.nio.file.Path;\n"
                  + ">>>>>>> ImportsTest2B.java\n"
                  + "\n"
                  + "public class ImportsTest {\n"
                  + "}\n",
              Path.of("testConflictedFiles JUnit test"));
      @SuppressWarnings("nullness:assignment")
      @NonNull List<ConflictElement> hunks7 = cf7.hunks();
      Assertions.assertEquals(2, hunks7.size());
      Assertions.assertTrue(hunks7.get(0) instanceof MergeConflict);
      Assertions.assertTrue(hunks7.get(1) instanceof CommonLines);
    }
  }
}
