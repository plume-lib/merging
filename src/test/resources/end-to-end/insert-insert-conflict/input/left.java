// Both parents introduce a method at the same location.

import java.io.File;

public class ImportsTest {

  public static void main(String[] args) {}

  File getFile() {
    return null;
  }

  Path getPath() {
    return Path.of("hello");
  }

  File getOtherFile() {
    return null;
  }
}
