// Git merges cleanly. Version A removes an import, but version B introduces a use of the import.

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
