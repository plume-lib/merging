package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.plumelib.merging.fileformat.Diff3File;
import org.plumelib.merging.fileformat.Diff3File.Diff3Hunk;
import org.plumelib.merging.fileformat.Diff3File.Diff3HunkKind;
import org.plumelib.merging.fileformat.Diff3File.Diff3ParseException;
import org.plumelib.util.FilesPlume;

public class Diff3FileTest {

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
      throw new UncheckedIOException(e);
    }
  }

  private Diff3File parseDiff3File(String filename) throws Diff3ParseException {
    String fileContents = fileContents(filename);
    return Diff3File.parseFileContents(fileContents, filename);
  }

  @Test
  void testParseFileContents() throws Diff3ParseException {
    Diff3File d = parseDiff3File("lao-tzu-tao.diff3");
    assertEquals(4, d.contents().size());
    Diff3Hunk h1 = d.contents().get(0);
    assertEquals(h1.kind(), Diff3HunkKind.TWO_DIFFERS);
    Diff3Hunk h2 = d.contents().get(1);
    assertEquals(h2.kind(), Diff3HunkKind.ONE_DIFFERS);
    Diff3Hunk h3 = d.contents().get(2);
    assertEquals(h3.kind(), Diff3HunkKind.THREE_DIFFERS);
    Diff3Hunk h4 = d.contents().get(3);
    assertEquals(h4.kind(), Diff3HunkKind.THREE_WAY);
  }
}
