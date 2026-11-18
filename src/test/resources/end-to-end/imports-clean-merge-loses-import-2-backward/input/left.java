// Git merges cleanly. Version A removes an import, but version B introduces a use of the import.

import java.io.File;

public class ImportsTest {

  // This is the main method.
  public static void main(String[] args) {}

  // Get the file
  File getFile() {
    return null;
  } // getFile

  // Get the other file
  File getOtherFile() {
    return null;
  } // getOtherFile
}
