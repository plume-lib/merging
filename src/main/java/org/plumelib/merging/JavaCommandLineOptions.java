package org.plumelib.merging;

import org.plumelib.options.Option;

// TODO: Rewrite this without using reflection, to ease native compilation.
/** The command-line options for a Java merge driver or merge tool. */
public class JavaCommandLineOptions {

  /** Create a CommandLineArgs. */
  public JavaCommandLineOptions() {}

  /** If true, merge adjacent. */
  @Option("Merge adjacent lines")
  public boolean adjacent = true;

  /** Default value for --adjacent. */
  public boolean adjacentDefault = true;

  /** If true, only merge adjacent lines. */
  @Option("Only merge adjacent")
  public boolean only_adjacent = false;

  /** If true, merge annotations. */
  @Option("Merge annotations")
  public boolean annotations = true;

  /** Default value for --annotations. */
  public boolean annotationsDefault = true;

  /** If true, only merge annotations. */
  @Option("Only merge annotations")
  public boolean only_annotations = false;

  /** If true, merge imports. */
  @Option("Merge imports")
  public boolean imports = true;

  /** Default value for --imports. */
  public boolean importsDefault = true;

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

    if ((only_adjacent ? 1 : 0) + (only_annotations ? 1 : 0) + (only_imports ? 1 : 0) > 1) {
      exitErroneously(
          "Do not supply more than one  --only-* flag.  Arguments: " + String.join(" ", args));
    }

    // A user could supply a redundant flag, as in "--only-annotations --imports", and won't be
    // warned about "--imports" because it doesn't change the value from the default.
    if ((only_adjacent || only_annotations || only_imports)
        && ((adjacent && (adjacent != adjacentDefault))
            || (annotations && (annotations != annotationsDefault))
            || (imports && (imports != importsDefault)))) {
      exitErroneously(
          "Do not supply --only-* and also other feature flag.  Arguments: "
              + String.join(" ", args));
    }

    if (only_adjacent) {
      adjacent = true;
      annotations = false;
      imports = false;
    } else if (only_annotations) {
      adjacent = false;
      annotations = true;
      imports = false;
    } else if (only_imports) {
      adjacent = false;
      annotations = false;
      imports = true;
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
