package org.plumelib.merging;

import org.plumelib.options.Option;

// TODO: Rewrite this without using reflection, to ease native compilation.
/** The command-line options for a Java merge driver or merge tool. */
public class JavaCommandLineOptions {

  /** Create a CommandLineArgs. */
  public JavaCommandLineOptions() {}

  /** If true, merge annotations. */
  @Option("Merge annotations")
  public boolean annotations = true;

  /** If true, only merge annotations. */
  @Option("Only merge annotations")
  public boolean only_annotations = false;

  /** If true, merge imports. */
  @Option("Merge imports")
  public boolean imports = true;

  /** If true, only merge imports. */
  @Option("Only merge imports")
  public boolean only_imports = false;

  /** If true, print diagnostics for debugging. */
  @Option("Print diagnostics")
  public boolean verbose = false;

  /**
   * Checks the command-line arguments for consistency, and sets them as needed. May call {@code
   * System.exit()}.
   *
   * @param args the command-line arguments
   */
  public void check(String[] args) {

    if (only_annotations && only_imports) {
      exitErroneously(
          "Do not supply more than one  --only-* flag.  Arguments: " + String.join(" ", args));
    }

    if ((only_annotations || only_imports) && !(annotations && imports)) {
      exitErroneously(
          "Do not supply --only-* and also set any other flag to false.  Arguments: "
              + String.join(" ", args));
    }

    if (only_annotations) {
      assert annotations == true;
      imports = false;
    } else if (only_imports) {
      annotations = false;
      assert imports == true;
    }
  }

  /**
   * Print an error message and exit.
   *
   * @param message the error message
   */
  private void exitErroneously(String message) {
    System.out.println(message);
    System.err.println(message);
    System.exit(129);
  }
}
