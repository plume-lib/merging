package org.plumelib.merging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.plumelib.merging.fileformat.ConflictedFile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Acts as a git merge driver or merge tool. */
@SuppressWarnings({
  "nullness:initialization.fields.uninitialized",
  "initializedfields:contracts.postcondition"
}) // picocli initializes w/reflection
@Command(name = "plumelib-merge", description = "Acts as a git merge driver or merge tool.")
public class Main implements Callable<Integer> {

  /** Creates a Main. */
  public Main() {}

  /** Whether to run as a merge driver or a merge tool. */
  @Parameters(index = "0", description = "\"driver\" or \"tool\"")
  MergeMode command;

  /** The left or current file file; is overwritten by a merge driver. */
  @Parameters(index = "1", description = "The left, or current, file")
  Path leftPath;

  /** The base file. */
  @Parameters(index = "2", description = "The base file")
  Path basePath;

  /** The right file. */
  @Parameters(index = "3", description = "The right, or other, file")
  Path rightPath;

  /** Fro a merge tool, the merged file; is overwritten. For a merge driver, null. */
  @Parameters(
      arity = "0..1",
      index = "4",
      description = "The merged file; only supplied for a merge tool, which overwrites it")
  @Nullable Path mergedPath = null;

  /** If true, merge adjacent. */
  @Option(
      names = "--adjacent",
      negatable = true,
      description = "Merge adjacent lines",
      defaultValue = "false",
      fallbackValue = "false")
  public boolean adjacent = false;

  /** If true, only merge adjacent lines. */
  @Option(
      names = "--only-adjacent",
      description = "Only merge adjacent lines",
      defaultValue = "false")
  public boolean only_adjacent = false;

  /** If true, merge Java annotations. */
  @Option(names = "--java-annotations", negatable = true, description = "Merge Java annotations")
  public Optional<Boolean> java_annotations_optional = Optional.empty();

  /** If true, merge Java annotations. */
  boolean java_annotations = true;

  /** If true, only merge Java annotations. */
  @Option(
      names = "--only-java-annotations",
      description = "Only merge Java annotations",
      defaultValue = "false")
  public boolean only_java_annotations = false;

  /** If true, merge Java imports. */
  @Option(names = "--java-imports", negatable = true, description = "Merge Java imports")
  public Optional<Boolean> java_imports_optional = Optional.empty();

  /** If true, merge Java imports. */
  public boolean java_imports = true;

  /** If true, only merge Java imports. */
  @Option(
      names = "--only-java-imports",
      description = "Only merge Java imports",
      defaultValue = "false")
  public boolean only_java_imports = false;

  /** If true, merge version numbers. */
  @Option(names = "--version-numbers", negatable = true, description = "Merge version numbers")
  public Optional<Boolean> version_numbers_optional = Optional.empty();

  /** If true, merge version numbers. */
  public boolean version_numbers;

  /** If true, only merge version numbers. */
  @Option(
      names = "--only-version-numbers",
      description = "Only merge version numbers",
      defaultValue = "false")
  public boolean only_version_numbers = true;

  /** If true, print diagnostics for debugging. */
  @Option(names = "--verbose", description = "Print diagnostics", defaultValue = "false")
  public boolean verbose = false;

  /** If false, don't run `git merge-file`, just work from the conflicts that exist in the file. */
  @Option(
      names = "--git-merge-file",
      negatable = true,
      description = "Run `git merge-file` first (merge driver only)")
  public static Optional<Boolean> git_merge_file_optional = Optional.empty();

  /** If false, don't run `git merge-file`, just work from the conflicts that exist in the file. */
  public static boolean git_merge_file;

  /**
   * Acts as a git merge driver or merge tool.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  /**
   * Throws an exception if the command-line arguments are inconsistent with one another. Also sets
   * fields as needed, for overall consistency.
   */
  public void checkArgs() {

    if (command == MergeMode.driver && mergedPath != null) {
      exitErroneously("Too many arguments for a merge driver:  expected 3, got 4.");
    } else if (command == MergeMode.tool && mergedPath == null) {
      exitErroneously("Not enough arguments for a merge tool:  expected 4, got 3.");
    }

    // "--adjacent" defaults to false, so it was set by picocli.  Likewise for "--only-*".
    java_annotations = java_annotations_optional.orElse(Boolean.TRUE);
    java_imports = java_imports_optional.orElse(Boolean.TRUE);
    version_numbers = version_numbers_optional.orElse(Boolean.TRUE);
    git_merge_file = git_merge_file_optional.orElse(Boolean.TRUE);

    if ((only_adjacent ? 1 : 0)
            + (only_java_annotations ? 1 : 0)
            + (only_java_imports ? 1 : 0)
            + (only_version_numbers ? 1 : 0)
        > 1) {
      exitErroneously("Do not supply more than one --only-* flag.");
    }

    if ((only_adjacent || only_java_annotations || only_java_imports || only_version_numbers)
        && (adjacent
            || (java_annotations_optional.isPresent() && java_annotations)
            || (java_imports_optional.isPresent() && java_imports)
            || (version_numbers_optional.isPresent() && version_numbers))) {
      exitErroneously("Do not supply --only-* and also another feature flag.");
    }

    if (only_adjacent) {
      adjacent = true;
      java_annotations = false;
      java_imports = false;
      version_numbers = false;
    } else if (only_java_annotations) {
      adjacent = false;
      java_annotations = true;
      java_imports = false;
      version_numbers = false;
    } else if (only_java_imports) {
      adjacent = false;
      java_annotations = false;
      java_imports = true;
      version_numbers = false;
    } else if (only_version_numbers) {
      adjacent = false;
      java_annotations = false;
      java_imports = false;
      version_numbers = true;
    }
  }

  @Override
  public Integer call() {
    checkArgs();

    @SuppressWarnings("nullness:argument") // command == tool => mergedPath != null
    MergeState ms =
        switch (command) {
          case driver -> mergeStateForDriver();
          case tool -> new MergeState(leftPath, basePath, rightPath, mergedPath, true);
        };

    // Even if ms.gitMergeFileExitCode is 0, give fixups a chance to run.

    if (java_annotations) {
      if (verbose) {
        System.out.println("calling annotations");
      }
      new JavaAnnotationsMerger(verbose).merge(ms);
    }

    if (version_numbers) {
      if (verbose) {
        System.out.println("calling version numbers");
      }
      new VersionNumbersMerger(verbose).merge(ms);
    }

    // Sub-line merges go above here, whole-line merges go below here.

    if (adjacent) {
      if (verbose) {
        System.out.println("calling adjacent");
      }
      new AdjacentLinesMerger(verbose).merge(ms);
    }

    // Imports must come last, because it does nothing unless every non-import conflict
    // has already been resolved.
    if (java_imports) {
      if (verbose) {
        System.out.println("calling imports");
      }
      new JavaImportsMerger(verbose).merge(ms);
    }

    ms.writeBack(verbose);

    int exitStatus = ms.hasConflict() ? 1 : 0;
    if (verbose) {
      System.out.printf("Exiting with status %d.%n", exitStatus);
    }
    return exitStatus;
  }

  /**
   * Returns the MergeState that should be used for a merge driver.
   *
   * @return the MergeState that should be used for a merge driver
   */
  MergeState mergeStateForDriver() {
    Path leftFileSavedPath = Path.of("not yet initialized");

    // Make a copy of the file that will be overwritten, for passing to external tools.
    try {
      File leftFileSaved = File.createTempFile("leftBeforeOverwriting-", ".bak");
      leftFileSaved.deleteOnExit();
      leftFileSavedPath = leftFileSaved.toPath();
      // REPLACE_EXISTING is needed because createTempFile creates an empty file.
      Files.copy(leftPath, leftFileSavedPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      Main.exitErroneously(
          "Problem copying " + leftPath + " to " + leftFileSavedPath + ": " + e.getMessage());
      throw new Error("unreachable");
    }

    int gitMergeFileExitCode;
    if (git_merge_file) {
      gitMergeFileExitCode = GitLibrary.performGitMergeFile(leftPath, basePath, rightPath);
    } else {
      // There is a difference between baseFile and otherFile; otherwise the merge driver would
      // not have been called.  We don't know whether there is a merge conflict in currentFile,
      // but assume there is.
      gitMergeFileExitCode = 1;
    }
    if (verbose) {
      System.out.printf(
          "status %d for: git merge-file %s %s %s%n",
          gitMergeFileExitCode, leftPath, basePath, rightPath);
    }

    // Look for trivial merge conflicts
    ConflictedFile cf = new ConflictedFile(leftPath);
    cf.hunks();

    MergeState ms =
        new MergeState(leftFileSavedPath, basePath, rightPath, leftPath, gitMergeFileExitCode != 0);
    return ms;
  }

  ///////////////////////////////////////////////////////////////////////////
  /// Utilities
  ///

  /** The merge mode: merge driver or merge tool. */
  public enum MergeMode {
    /** Run as a merge driver. */
    driver,
    /** Run as a merge tool. */
    tool
  }

  /**
   * Print an error message and then exit erroneously. Call this when there is an unexpected and
   * unrecoverable problem, such as an invalid invocation or inability to read or parse files.
   *
   * @param errorMessage the error message
   */
  public static void exitErroneously(String errorMessage) {
    System.out.println(errorMessage);
    System.err.println(errorMessage);
    System.exit(129);
  }
}
