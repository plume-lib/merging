import java.io.File;
import java.nio.file.Path;

public class ImportsTest {

  public static void main(String[] args) {}

  File getFile() {
    return null;
  }

  Path getOtherPath() {
    return Path.of("other hello");
  }

  Path getPath() {
    return Path.of("hello");
  }

  File getOtherFile() {
    return null;
  }
}
