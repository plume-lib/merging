package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end tests. Each test case runs the program once, in a separate process and in a fresh
 * working directory, and compares everything the program produced -- its exit status, its standard
 * output, its standard error, and the files in the working directory -- to goal files.
 *
 * <p>The test data is in {@code src/test/resources/end-to-end}, one subdirectory per test case. See
 * {@code src/test/resources/end-to-end/README.md} for the layout of a test case. Every
 * subdirectory is a test case, so a new test case can be added without changing this file.
 *
 * <p>By default, the program under test is this implementation, run in a new JVM. If system
 * property {@code e2e.command} is set to a non-blank value, the test cases instead run that
 * command, split at whitespace, followed by the test case's arguments. Gradle sets the property
 * from the Gradle property of the same name, as in {@code ./gradlew test
 * -Pe2e.command=/path/to/plumelib-merge}.
 */
final class EndToEndTest {

  /** Creates an EndToEndTest. */
  EndToEndTest() {}

  /** The classpath resource that contains one subdirectory per test case. */
  private static final String testDataResource = "/end-to-end";

  /** The directory that contains one subdirectory per test case. */
  private static final Path testDataDir = findTestDataDir();

  /** The file names that are permitted in a test case directory. */
  private static final Set<String> permittedCaseFiles =
      Set.of(
          "description.txt",
          "args.txt",
          "input",
          "goal",
          "goal-stdout.txt",
          "goal-stdout-prefix.txt",
          "goal-stderr.txt",
          "goal-stderr-prefix.txt",
          "goal-status.txt");

  /** The file names that are required in a test case directory. */
  private static final List<String> requiredCaseFiles = List.of("description.txt", "args.txt");

  /**
   * Command-line arguments that a JVM needs in order to run this implementation, which calls javac
   * internals.
   */
  private static final List<String> addExportsArgs =
      Arrays.stream(new String[] {"api", "code", "file", "parser", "tree", "util"})
          .map(pkg -> "--add-exports=jdk.compiler/com.sun.tools.javac." + pkg + "=ALL-UNNAMED")
          .toList();

  /** How long to wait for the program to terminate, in seconds. */
  private static final int timeoutSeconds = 120;

  /**
   * Returns the directory that contains the test cases.
   *
   * @return the directory that contains the test cases
   */
  private static Path findTestDataDir() {
    URL url = EndToEndTest.class.getResource(testDataResource);
    if (url == null) {
      throw new Error("Cannot find resource " + testDataResource + " on the classpath.");
    }
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new Error("Cannot convert " + url + " to a path.", e);
    }
  }

  /**
   * Returns the name of each test case: that is, of each subdirectory of the test data directory.
   *
   * @return the name of each test case
   * @throws IOException if the test data directory cannot be read
   */
  static List<String> testCaseNames() throws IOException {
    List<String> result = new ArrayList<>();
    try (Stream<Path> children = Files.list(testDataDir)) {
      for (Path child : children.toList()) {
        if (Files.isDirectory(child)) {
          result.add(child.toFile().getName());
        }
      }
    }
    result.sort(Comparator.naturalOrder());
    assertTrue(!result.isEmpty(), "No test cases in " + testDataDir);
    return result;
  }

  /**
   * Runs one end-to-end test case.
   *
   * @param caseName the name of the test case, which is the name of a subdirectory of the test data
   *     directory
   * @param workingDir the directory in which to run the program
   * @param captureDir a directory that holds the program's standard output and standard error, and
   *     an empty git configuration file; it is separate from {@code workingDir} so that these files
   *     are not mistaken for files that the program wrote
   * @throws Exception if the test case cannot be read or the program cannot be run
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("testCaseNames")
  void endToEnd(String caseName, @TempDir Path workingDir, @TempDir Path captureDir)
      throws Exception {
    Path caseDir = testDataDir.resolve(caseName);
    checkCaseDir(caseDir);

    Path inputDir = caseDir.resolve("input");
    if (Files.isDirectory(inputDir)) {
      copyTree(inputDir, workingDir);
    }

    List<String> args = arguments(caseDir);
    Path stdoutFile = captureDir.resolve("stdout.txt");
    Path stderrFile = captureDir.resolve("stderr.txt");
    Path gitConfigFile = captureDir.resolve("empty-gitconfig");
    Files.writeString(gitConfigFile, "");
    int status = runProgram(workingDir, args, stdoutFile, stderrFile, gitConfigFile);

    String context = "Test case " + caseName + ", arguments " + args + ".";

    assertEquals(goalStatus(caseDir), status, context + "  Wrong exit status.");
    checkOutput(caseDir, "stdout", normalize(readText(stdoutFile)), context);
    checkOutput(caseDir, "stderr", normalize(readText(stderrFile)), context);
    checkWorkingDir(caseDir, workingDir, context);
  }

  /**
   * Fails if the test case directory lacks a required file or contains a file whose name the test
   * harness does not know about; such a file is probably a misspelled goal file, which would
   * otherwise be silently ignored.
   *
   * @param caseDir a test case directory
   * @throws IOException if the directory cannot be read
   */
  private static void checkCaseDir(Path caseDir) throws IOException {
    String caseName = caseDir.toFile().getName();
    for (String required : requiredCaseFiles) {
      assertTrue(
          Files.isRegularFile(caseDir.resolve(required)),
          "Test case " + caseName + " lacks file " + required + ".");
    }
    try (Stream<Path> children = Files.list(caseDir)) {
      for (Path child : children.toList()) {
        String name = child.toFile().getName();
        if (!permittedCaseFiles.contains(name)) {
          fail(
              "Test case "
                  + caseName
                  + " contains unrecognized file "
                  + name
                  + ".  Permitted files are "
                  + new TreeSet<>(permittedCaseFiles)
                  + ".");
        }
      }
    }
    for (String stream : List.of("stdout", "stderr")) {
      assertTrue(
          !(Files.exists(caseDir.resolve("goal-" + stream + ".txt"))
              && Files.exists(caseDir.resolve("goal-" + stream + "-prefix.txt"))),
          "Test case "
              + caseName
              + " contains both goal-"
              + stream
              + ".txt and goal-"
              + stream
              + "-prefix.txt.");
    }
  }

  /**
   * Returns the command-line arguments of the given test case: the non-empty lines of its {@code
   * args.txt} file.
   *
   * @param caseDir a test case directory
   * @return the command-line arguments of the test case
   * @throws IOException if the file cannot be read
   */
  private static List<String> arguments(Path caseDir) throws IOException {
    List<String> result = new ArrayList<>();
    for (String line : Files.readAllLines(caseDir.resolve("args.txt"), StandardCharsets.UTF_8)) {
      if (!line.isEmpty()) {
        result.add(line);
      }
    }
    return result;
  }

  /**
   * Returns the command that runs the program under test, without its arguments: the value of
   * system property {@code e2e.command} split at whitespace, if it is set and not blank, and
   * otherwise a command that runs this implementation in a new JVM.
   *
   * @return the command that runs the program under test, without its arguments
   */
  private static List<String> programCommand() {
    String e2eCommand = System.getProperty("e2e.command");
    if (e2eCommand != null && !e2eCommand.isBlank()) {
      return Arrays.asList(e2eCommand.trim().split("\\s+"));
    }
    List<String> result = new ArrayList<>();
    result.add(javaExecutable());
    result.addAll(addExportsArgs);
    result.add("-cp");
    result.add(classpath());
    result.add(Main.class.getName());
    return result;
  }

  /**
   * Runs the program. Its environment is the environment of this JVM, except that git is isolated
   * from the user's and the system's git configuration and from any enclosing git repository.
   *
   * @param workingDir the working directory for the program
   * @param args the command-line arguments for the program
   * @param stdoutFile where to write the program's standard output
   * @param stderrFile where to write the program's standard error
   * @param gitConfigFile an empty file, which git uses as the user's git configuration file
   * @return the program's exit status
   * @throws IOException if the program cannot be started
   * @throws InterruptedException if this thread is interrupted while the program runs
   */
  private static int runProgram(
      Path workingDir, List<String> args, Path stdoutFile, Path stderrFile, Path gitConfigFile)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(programCommand());
    command.addAll(args);
    ProcessBuilder pb =
        new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile());
    Map<String, String> env = pb.environment();
    env.keySet().removeIf(name -> name.startsWith("GIT_"));
    env.put("GIT_CONFIG_NOSYSTEM", "1");
    env.put("GIT_CONFIG_GLOBAL", gitConfigFile.toAbsolutePath().toString());
    Path parent = workingDir.toAbsolutePath().getParent();
    if (parent != null) {
      env.put("GIT_CEILING_DIRECTORIES", parent.toString());
    }
    Process process = pb.start();
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new Error("The program did not terminate within " + timeoutSeconds + " seconds.");
    }
    return process.exitValue();
  }

  /**
   * Returns the java executable of the JVM that is running the tests.
   *
   * @return the java executable of the JVM that is running the tests
   */
  private static String javaExecutable() {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null) {
      return "java";
    }
    for (String name : List.of("java", "java.exe")) {
      Path java = Path.of(javaHome, "bin", name);
      if (Files.isRegularFile(java)) {
        return java.toString();
      }
    }
    return "java";
  }

  /**
   * Returns the classpath with which to run the program, which is the classpath of the JVM that is
   * running the tests.
   *
   * @return the classpath with which to run the program
   */
  private static String classpath() {
    String result = System.getProperty("java.class.path");
    if (result == null) {
      throw new Error("Property java.class.path is not set.");
    }
    return result;
  }

  /**
   * Returns the expected exit status of the given test case: the contents of its {@code
   * goal-status.txt} file, or 0 if it has none.
   *
   * @param caseDir a test case directory
   * @return the expected exit status of the test case
   * @throws IOException if the goal file cannot be read
   */
  private static int goalStatus(Path caseDir) throws IOException {
    Path goalStatusFile = caseDir.resolve("goal-status.txt");
    if (!Files.isRegularFile(goalStatusFile)) {
      return 0;
    }
    return Integer.parseInt(readText(goalStatusFile).trim());
  }

  /**
   * Fails if what the program wrote to standard output or standard error is not what the test case
   * says it should be. The test case gives either the whole text (in {@code goal-STREAM.txt}) or a
   * prefix of it (in {@code goal-STREAM-prefix.txt}). If it gives neither, the program should
   * write nothing to the stream.
   *
   * @param caseDir a test case directory
   * @param stream "stdout" or "stderr"
   * @param actual what the program wrote to the stream, normalized by {@link #normalize}
   * @param context a description of the test case; used for diagnostics
   * @throws IOException if a goal file cannot be read
   */
  private static void checkOutput(Path caseDir, String stream, String actual, String context)
      throws IOException {
    Path prefixFile = caseDir.resolve("goal-" + stream + "-prefix.txt");
    if (Files.isRegularFile(prefixFile)) {
      String prefix = normalize(readText(prefixFile));
      // The goal file's final line separator is not part of the prefix.
      if (prefix.endsWith("\n")) {
        prefix = prefix.substring(0, prefix.length() - 1);
      }
      assertTrue(
          actual.startsWith(prefix),
          context
              + "  %s should start with:%n%s%nbut was:%n%s".formatted(stream, prefix, actual));
      return;
    }
    Path goalFile = caseDir.resolve("goal-" + stream + ".txt");
    String goal = Files.isRegularFile(goalFile) ? normalize(readText(goalFile)) : "";
    assertEquals(goal, actual, context + "  Wrong " + stream + ".");
  }

  /**
   * Fails if the files in the working directory are not the expected ones. The expected files are
   * those in the test case's {@code input} directory, overlaid by those in its {@code goal}
   * directory. File contents are compared exactly, byte for byte.
   *
   * @param caseDir a test case directory
   * @param workingDir the directory in which the program ran
   * @param context a description of the test case; used for diagnostics
   * @throws IOException if a file cannot be read
   */
  private static void checkWorkingDir(Path caseDir, Path workingDir, String context)
      throws IOException {
    Map<String, Path> expectedFiles = new TreeMap<>();
    for (String dirName : List.of("input", "goal")) {
      Path dir = caseDir.resolve(dirName);
      if (Files.isDirectory(dir)) {
        expectedFiles.putAll(relativeFileNames(dir));
      }
    }
    Map<String, Path> actualFiles = relativeFileNames(workingDir);

    assertEquals(
        String.join(System.lineSeparator(), expectedFiles.keySet()),
        String.join(System.lineSeparator(), actualFiles.keySet()),
        context + "  Wrong files in the working directory.");
    for (Map.Entry<String, Path> expectedFile : expectedFiles.entrySet()) {
      String name = expectedFile.getKey();
      @Nullable Path actualFile = actualFiles.get(name);
      if (actualFile == null) {
        throw new Error("This can't happen: " + name);
      }
      assertEquals(
          showCarriageReturns(readText(expectedFile.getValue())),
          showCarriageReturns(readText(actualFile)),
          context + "  Wrong contents of file " + name + ".");
    }
  }

  /**
   * Returns every file (not directory) under the given directory, as a map from the file's name
   * relative to the given directory (using "/" as the separator) to the file.
   *
   * @param dir a directory
   * @return the files under the given directory
   * @throws IOException if the directory cannot be read
   */
  private static @Modifiable Map<String, Path> relativeFileNames(Path dir) throws IOException {
    Map<String, Path> result = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path path : paths.toList()) {
        if (Files.isRegularFile(path)) {
          result.put(dir.relativize(path).toString().replace(File.separatorChar, '/'), path);
        }
      }
    }
    return result;
  }

  /**
   * Returns the contents of the given file, as UTF-8 text.
   *
   * @param file a file
   * @return the contents of the given file
   * @throws IOException if the file cannot be read
   */
  private static String readText(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Returns the given text with Windows line separators replaced by Unix ones. The program
   * terminates each line of its standard output and standard error with the platform's line
   * separator.
   *
   * @param text some text
   * @return the given text, with Windows line separators replaced by Unix ones
   */
  private static String normalize(String text) {
    return text.replace("\r\n", "\n");
  }

  /**
   * Returns the given text with each carriage return made visible, so that a failure message about
   * line separators is legible.
   *
   * @param text some text
   * @return the given text, with "&lt;CR&gt;" inserted before each carriage return
   */
  private static String showCarriageReturns(String text) {
    return text.replace("\r", "<CR>\r");
  }

  /**
   * Copies a directory tree.
   *
   * @param from a directory
   * @param to the directory to copy it to; it and its parents are created if necessary
   * @throws IOException if a file cannot be copied
   */
  private static void copyTree(Path from, Path to) throws IOException {
    Files.createDirectories(to);
    for (Map.Entry<String, Path> file : relativeFileNames(from).entrySet()) {
      Path target = to.resolve(file.getKey().replace('/', File.separatorChar));
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(file.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
