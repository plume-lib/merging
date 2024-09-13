// Both parents introduce a method at the same location.

import java.io.File;
import java.nio.file.Path;

public class ImportsTest {

  public static void main(String[] args) {}

  File getFile() {
    return null;
  }

  Path getOtherPath() {
    return null;
  }

  File getOtherFile() {
    return null;
  }

}
