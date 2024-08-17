// Git merges cleanly. Version A removes an import, but version B introduces a use of the import.

import java.io.File;

public class ImportsTest {

  public static void main(String[] args) {}

  File getFile() {
    return null;
  }

<<<<<<< OURS
  Path getPath() {
    return Path.of("hello");
  }

||||||| BASE
=======
  Path getOtherPath() {
    return null;
  }

>>>>>>> THEIRS
  File getOtherFile() {
    return null;
  }

}
