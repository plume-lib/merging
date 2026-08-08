import java.io.File
import net.ltgt.gradle.errorprone.errorprone
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("java")
  id("application")

  // Creates the fat jar build/libs/...-all.jar as part of "assemble".
  // To create only that jar, run: ./gradlew shadowJar
  alias(libs.plugins.com.gradleup.shadow)

  // Code formatting; defines targets "spotlessApply" and "spotlessCheck"
  // which are run by "check" (which is itself run by "build").
  alias(libs.plugins.com.diffplug.spotless)

  // Error Prone linter
  alias(libs.plugins.net.ltgt.errorprone)

  // PMD linter
  id("pmd")

  // Code coverage
  id("jacoco")

  // Checker Framework pluggable type-checking
  alias(libs.plugins.org.checkerframework)

  // GraalVM native compilation
  alias(libs.plugins.org.graalvm.buildtools.native)
}

repositories {
  // mavenLocal() comes first so that a locally-installed snapshot of a dependency takes
  // precedence.  Restricting this to snapshots prevents a stale locally-installed non-snapshot
  // release from shadowing the one on Maven Central.  One consequence is that a locally-installed
  // *release* build of a dependency is ignored; to build against such a build, either temporarily
  // remove "snapshotsOnly()" or install the build as a snapshot.
  mavenLocal { mavenContent { snapshotsOnly() } }
  mavenCentral()
  maven {
    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    mavenContent { snapshotsOnly() }
  }
}

dependencies {
  implementation(libs.google.java.format)
  implementation(libs.javac.parse)
  implementation(libs.picocli)
  implementation(libs.plume.util)

  annotationProcessor(libs.picocli.codegen)

  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// This project calls javac internals, which the jdk.compiler module does not export.  Every javac
// and JVM invocation therefore needs these packages exported.  Exporting all six packages in an
// invocation that needs only some of them is harmless.
// The shell scripts in src/main/sh contain a copy of this list.
val javacInternalPackages = listOf("api", "code", "file", "parser", "tree", "util")
val javacExportTargets = javacInternalPackages.map {
  "jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED"
}
val addExportsArgs = javacExportTargets.map { "--add-exports=$it" }
val addOpensArgs = javacExportTargets.map { "--add-opens=$it" }

// Packaging

application { mainClass = "org.plumelib.merging.Main" }

// The "application" and "shadow" plugins each add a distribution -- a .tar and
// a .zip holding the jar, its dependencies, and start scripts -- to "assemble",
// which "build" runs.  This project ships jars rather than distributions, so
// writing about 8 MB of archives on every build is wasted work.
// Disabling a task does not skip its dependencies, so "startScripts" and
// "startShadowScripts" still run; "installDist" and "installShadowDist" need them.
listOf("distTar", "distZip", "shadowDistTar", "shadowDistZip").forEach { taskName ->
  tasks.named(taskName) { enabled = false }
}

graalvmNative {
  binaries {
    named("main") {
      imageName = "plumelib-merge"
      mainClass = application.mainClass
      buildArgs.add("-O4")
    }
    named("test") { buildArgs.add("-O0") }
  }
  binaries.configureEach {
    buildArgs.add("--verbose")
    // These arguments are from google-java-format's core/pom.xml.
    buildArgs.add("-H:+UnlockExperimentalVMOptions")
    buildArgs.add("-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler")
    buildArgs.add("-H:IncludeResourceBundles=com.sun.tools.javac.resources.javac")
    buildArgs.add("--no-fallback")
    buildArgs.add("--initialize-at-build-time=com.sun.tools.javac.file.Locations")
    buildArgs.add("-H:+ReportExceptionStackTraces")
    buildArgs.add("-H:-UseContainerSupport")
    addExportsArgs.forEach { buildArgs.add("-J$it") }
    buildArgs.add("-march=compatibility")
  }
}

// Compilation

java {
  toolchain {
    // Always compile using Java 21.  The "test" task below overrides this for
    // test execution, so that tests run under various Java versions.
    languageVersion = JavaLanguageVersion.of(21)
  }
}

tasks.withType<JavaCompile>().configureEach {
  // No `options.release`, because `--release` is incompatible with `--add-exports`.  The class
  // files are therefore Java 21 class files, which an earlier JVM cannot read.
  options.compilerArgs.addAll(addExportsArgs)

  // Gradle compiles in a worker process whenever the toolchain above differs
  // from the JVM that runs Gradle.  That worker does not inherit the
  // `org.gradle.jvmargs` heap setting in gradle.properties, so set it here too.
  options.forkOptions.jvmArgs = (options.forkOptions.jvmArgs ?: emptyList()) + "-Xmx6g"
  options.compilerArgs.add("-Werror")
  // "-processing" avoids javac warning "No processor claimed any of these annotations".
  options.compilerArgs.add("-Xlint:all,-processing,-this-escape")

  // To generate Picocli's native-image configuration, add:
  //   options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

// Testing

// Compilation always uses Java 21, but the tests run under various Java versions.  The
// `java.toolchain` setting above applies to Test tasks as well as to compilation, so without this
// the tests would always run under Java 21.  By default the tests run under the JVM that Gradle
// itself is running under.  The job matrix in .github/workflows/gradle.yml does not rely on that
// default:  it runs Gradle under Java 21 in every job and selects the test JVM by passing
// `-PtestJavaVersion`.
// Override with, for example:
//   ./gradlew test -PtestJavaVersion=25
val testJavaVersionProperty = project.findProperty("testJavaVersion")

if (testJavaVersionProperty != null && testJavaVersionProperty.toString().isEmpty()) {
  throw GradleException(
    "-PtestJavaVersion needs a value, as in `-PtestJavaVersion=25`." +
      "  Omit the property entirely to test under the JVM that Gradle is running under."
  )
}

val testJavaVersion =
  JavaLanguageVersion.of((testJavaVersionProperty ?: JavaVersion.current().majorVersion).toString())

if (testJavaVersion.asInt() < 21) {
  throw GradleException(
    "-PtestJavaVersion must be 21 or later, but is $testJavaVersion." +
      "  The code is compiled under Java 21, whose class files an earlier JVM cannot read."
  )
}

tasks.withType<Test>().configureEach {
  javaLauncher = javaToolchains.launcherFor { languageVersion = testJavaVersion }

  useJUnitPlatform {
    includeEngines("junit-jupiter")
    excludeEngines("junit-vintage")
  }

  jvmArgs(addExportsArgs)

  // Always re-run the tests, so that their output always appears.  Both lines
  // are needed:  "upToDateWhen" alone would still permit the build cache to
  // supply the outputs of a previous run.
  outputs.upToDateWhen { false }
  outputs.cacheIf { false }

  testLogging {
    // "passed" is what makes the forced re-run above worth its cost:  without it, a run in which
    // every test passes prints nothing at all.
    // `showStandardStreams = true` would be a redundant way to say the same
    // thing as including the "standardOut" and "standardError" events here.
    events("passed", "skipped", "failed", "standardOut", "standardError")
    exceptionFormat = TestExceptionFormat.FULL
  }

  // Generate the coverage report after the tests run.
  finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco { toolVersion = libs.versions.jacoco.get() }

tasks.named<JacocoReport>("jacocoTestReport") {
  reports {
    xml.required = false
    csv.required = true // Output is written to build/reports/jacoco/test/jacocoTestReport.csv
    html.required = true // Output is written to build/reports/jacoco/test/html/index.html
  }
}

tasks.register<Exec>("runMakefileTests") {
  group = "verification"
  description = "Run tests defined in Makefile."
  // The Makefile's tests run the shell scripts in src/main/sh, which use either
  // the fat jar or the native executable.
  dependsOn("shadowJar")
  // Testing the native executable is opt-in, via -PtestNative, because building
  // it takes minutes.  Do not instead test whether the native executable
  // already exists: that would make the task graph depend on the results of
  // previous builds, so that `./gradlew check` behaves differently on a clean
  // checkout than on a tree where someone once ran `nativeCompile`.
  val testNative = project.hasProperty("testNative")
  if (testNative) {
    dependsOn("nativeCompile")
  }
  // This task declares no inputs or outputs, so Gradle always runs it and never
  // caches it.  That is intentional: the Makefile's rules do not create the
  // files that name them, so the Makefile has no reliable up-to-date check of
  // its own for Gradle to defer to.
  //
  // Tell the shell scripts which artifact to run, rather than letting them
  // choose by probing for a native executable in its default location.  Without
  // this, an executable left behind by an earlier `nativeCompile` would be
  // tested in place of the fat jar that this task just built -- so `./gradlew
  // check` would silently test stale code and still pass.  Ask "nativeCompile"
  // for its output file rather than hard-coding the path, which would go stale
  // if `imageName` or the plugin's output directory changed.
  val nativeExecutable: Provider<String> =
    if (testNative) {
      tasks
        .named<BuildNativeImageTask>("nativeCompile")
        .flatMap { it.outputFile }
        .map { it.asFile.absolutePath }
    } else {
      providers.provider { "" }
    }
  // Without PLUMELIB_MERGE_EXECUTABLE naming a native executable, the shell
  // scripts run the fat jar under a Java installation.  Name that installation
  // explicitly, so that the tests do not depend on whether the developer's
  // environment happens to define JAVA_HOME or JAVA21_HOME.  Overriding
  // JAVA_HOME would not suffice: an `Exec` task inherits the parent process's
  // environment, and the shell scripts prefer JAVA21_HOME over JAVA_HOME
  // whenever the two differ, so a stray JAVA21_HOME would still win.
  //
  // Under -PtestNative the scripts run the native executable and never consult
  // PLUMELIB_MERGE_JAVA_HOME, so do not name a toolchain then; asking for one
  // would download a JDK in order to ignore it.
  val makefileJavaHome: Provider<String> =
    if (testNative) {
      providers.provider { "" }
    } else {
      javaToolchains
        .launcherFor { languageVersion = testJavaVersion }
        .map { it.metadata.installationPath.asFile.absolutePath }
    }
  // Set the environment in `doFirst`, so that merely configuring this task
  // neither provisions a JDK nor forces "nativeCompile" to be configured.
  doFirst {
    environment("PLUMELIB_MERGE_EXECUTABLE", nativeExecutable.get())
    environment("PLUMELIB_MERGE_JAVA_HOME", makefileJavaHome.get())
  }
  // SKIP_GRADLE tells the Makefile not to build the artifacts itself.  Gradle
  // has already built them, per the dependencies above, and a nested Gradle
  // invocation from within a running Gradle build contends for Gradle's locks.
  commandLine("make", "-C", file("src/test/resources").absolutePath, "SKIP_GRADLE=1")
  // The "test" task runs the JUnit tests, whose failure is more informative than system tests.
  mustRunAfter(tasks.named("test"))
  // Under -PtestNative, "nativeCompile" is a dependency, so it is already
  // ordered first.  This ordering additionally covers a command line that names
  // both tasks, such as `./gradlew nativeCompile check`.
  mustRunAfter(tasks.named("nativeCompile"))
}

tasks.named("check") { dependsOn("runMakefileTests") }

// Code formatting

// Generated files, a checkout of another repository, and test data, none of which should be
// reformatted.
val spotlessExclusions =
  arrayOf("build/**", ".gradle/**", ".plume-scripts/**", "src/test/resources/**")

spotless {
  java {
    googleJavaFormat(libs.versions.google.java.format.get())
    formatAnnotations()
    // An external library, included as source because it is not on Maven Central.
    targetExclude("**/diff_match_patch.java")
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude(*spotlessExclusions)

    // googleStyle() to match the Java code, which google-java-format formats:  2-space indentation
    // and a 100-column line limit.
    ktfmt(libs.versions.ktfmt.get()).googleStyle()

    trimTrailingWhitespace()
    // endWithNewline() // Don't want to end empty files with a newline
  }
}

// Error Prone linter

dependencies { errorprone(libs.error.prone.core) }

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    disable("AnnotateFormatMethod") // Error Prone doesn't know about CF @FormatMethod.
    disable("DoNotCallSuggester") // Suggests use of an Error Prone annotation.
    disable("EffectivelyPrivate") // Loses information about the abstraction.
    disable("ExtendsObject") // Incorrect when using the Checker Framework.
    disable("InlineMeSuggester") // `@InlineMe` requires a dependency on error_prone_annotations.
    disable("ReferenceEquality") // Use Interning Checker instead.
    // An external library, included as source because it is not on Maven Central.
    excludedPaths = ".*/diff_match_patch.java"
  }
}

// PMD linter

pmd {
  toolVersion = libs.versions.pmd.get()
  ruleSets = listOf() // Prevent the default errorprone.xml from being applied.
  ruleSetFiles = files("$rootDir/.pmd-ruleset.xml")
  isConsoleOutput = true
}

// Checker Framework pluggable type-checking

checkerFramework {
  version = libs.versions.checker.framework.get()
  checkers =
    listOf(
      // No need to run CalledMethodsChecker, because ResourceLeakChecker does so.
      // "org.checkerframework.checker.calledmethods.CalledMethodsChecker",
      "org.checkerframework.checker.formatter.FormatterChecker",
      // TODO: "org.checkerframework.checker.index.IndexChecker",
      "org.checkerframework.checker.interning.InterningChecker",
      "org.checkerframework.checker.lock.LockChecker",
      "org.checkerframework.checker.nullness.NullnessChecker",
      "org.checkerframework.checker.regex.RegexChecker",
      "org.checkerframework.checker.resourceleak.ResourceLeakChecker",
      "org.checkerframework.checker.signature.SignatureChecker",
      "org.checkerframework.checker.signedness.SignednessChecker",
      "org.checkerframework.common.initializedfields.InitializedFieldsChecker",
    )
  extraJavacArgs =
    listOf(
      "-Werror",
      // "-Aversion",
      // "-verbose",
      "-AcheckPurityAnnotations",
      "-ArequirePrefixInWarningSuppressions",
      "-AwarnRedundantAnnotations",
      "-AwarnUnneededSuppressions",
      "-AskipDefs=.*\\.diff_match_patch",
    )
}

// Javadoc

// Javadoc generates a CSS import of a font that is not distributed alongside
// the documentation, so remove the import.  Cannot use "ant.replaceregexp",
// because the Ant builder is reachable only through the "Project" object, which
// the configuration cache forbids a task action from using.
//
// This is a method of an object rather than of the build script, because a lambda that called a
// method of the build script would capture the script, which the configuration cache cannot
// serialize.
object JavadocFonts {
  private val dejaVuImport = Regex("""@import url\('(?:resources/)?fonts/dejavu\.css'\);[ \t]*""")

  fun removeDejaVuFontImport(javadocDir: File?) {
    if (javadocDir == null || !javadocDir.isDirectory) {
      return
    }
    javadocDir
      .walkTopDown()
      .filter { it.isFile && (it.name.endsWith(".css") || it.name.endsWith(".html")) }
      .forEach { file ->
        val contents = file.readText()
        val newContents = dejaVuImport.replace(contents, "")
        if (newContents != contents) {
          file.writeText(newContents)
        }
      }
  }
}

tasks.withType<Javadoc>().configureEach {
  val docletOptions = options as StandardJavadocDocletOptions
  docletOptions.isNoTimestamp = true
  docletOptions.quiet()
  docletOptions.addMultilineStringsOption("-add-exports").value = javacExportTargets
  doLast { JavadocFonts.removeDejaVuFontImport((this as Javadoc).destinationDir) }
}

// Turns Javadoc warnings into errors.  This is applied to individual tasks rather than to every
// Javadoc task, because a task that uses a custom doclet rejects the standard doclet's options.
fun strictJavadoc(javadocTask: Javadoc) {
  val docletOptions = javadocTask.options as StandardJavadocDocletOptions
  docletOptions.addBooleanOption("Xdoclint:all", true)
  docletOptions.addBooleanOption("Xwerror", true)
}

tasks.named<Javadoc>("javadoc") { strictJavadoc(this) }

// The `javadoc` task documents only the public API.
// `javadocPrivate` applies the same doclint checks to private members.
val javadocPrivate =
  tasks.register<Javadoc>("javadocPrivate") {
    group = "documentation"
    description = "Generate Javadoc for all members, including private ones."
    source = sourceSets.main.get().allJava
    classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    destinationDir = layout.buildDirectory.dir("docs/javadocPrivate").get().asFile
    (options as StandardJavadocDocletOptions).addBooleanOption("private", true)
    strictJavadoc(this)
  }

tasks.named("check") { dependsOn("javadoc", javadocPrivate) }

val javadocWebUpload =
  tasks.register<Javadoc>("javadocWebUpload") {
    description = "Write API documentation to the website directory."
    source = sourceSets.main.get().allJava
    classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    destinationDir = file("/cse/web/research/plumelib/${project.name}/api")
    strictJavadoc(this)
  }

// Set permissions
val javadocWebChgrp =
  tasks.register<Exec>("javadocWebChgrp") {
    description = "Set the Unix group of the website's API documentation."
    mustRunAfter(javadocWebUpload)
    commandLine("chgrp", "-R", "plse_www", "/cse/web/research/plumelib/${project.name}/api")
    // A file that another user owns cannot be chgrped, which is not worth failing the build over.
    isIgnoreExitValue = true
    val result = executionResult
    doLast {
      val exitValue = result.get().exitValue
      if (exitValue != 0) {
        logger.warn("chgrp of the uploaded API documentation exited with status $exitValue.")
      }
    }
  }

val javadocWebChmod =
  tasks.register<Exec>("javadocWebChmod") {
    description = "Set the Unix permissions of the website's API documentation."
    mustRunAfter(javadocWebUpload)
    commandLine("chmod", "-R", "g+w", "/cse/web/research/plumelib/${project.name}/api")
    // A file that another user owns cannot be chmoded, which is not worth failing the build over.
    isIgnoreExitValue = true
    val result = executionResult
    doLast {
      val exitValue = result.get().exitValue
      if (exitValue != 0) {
        logger.warn("chmod of the uploaded API documentation exited with status $exitValue.")
      }
    }
  }

// The three tasks above are steps of "javadocWeb", so they have no group and thus do not appear in
// the output of `./gradlew tasks`.
tasks.register<DefaultTask>("javadocWeb") {
  group = "documentation"
  description = "Upload API documentation to website."
  dependsOn(javadocWebUpload, javadocWebChgrp, javadocWebChmod)
}

// `resolvable` rather than the `configurations { requireJavadoc }` shorthand, which creates a
// configuration that is both resolvable and consumable and that Gradle reports as legacy.
val requireJavadocConfiguration = configurations.resolvable("requireJavadoc")

dependencies { "requireJavadoc"(libs.require.javadoc) }

val requireJavadoc =
  tasks.register<JavaExec>("requireJavadoc") {
    group = "documentation"
    description = "Ensures that Javadoc documentation exists."
    // RequireJavadoc produces no output of its own, so write a marker file.  Without a declared
    // output, the task could never be up to date and the declared inputs would have no effect.
    // The marker is a local variable rather than a variable of the build script, because a task
    // action that read a variable of the build script would capture the script object, which the
    // configuration cache cannot serialize.
    val requireJavadocMarker = layout.buildDirectory.file("requireJavadoc/requireJavadoc.txt")
    inputs.files(sourceSets.main.get().allJava)
    outputs.file(requireJavadocMarker)
    mainClass = "org.plumelib.javadoc.RequireJavadoc"
    classpath = files(requireJavadocConfiguration)
    args(sourceSets.main.get().allJava.srcDirs.map { it.absolutePath })
    jvmArgs(addExportsArgs)
    jvmArgs(addOpensArgs)
    // Runs only if the tool found no problems, so that a failure does not leave behind a marker
    // file
    // that would mark this task up to date.
    doLast {
      val marker = requireJavadocMarker.get().asFile
      marker.parentFile.mkdirs()
      marker.writeText("")
    }
  }

tasks.named("check") { dependsOn(requireJavadoc) }

// On javadocWebUpload rather than on javadocWeb, so that the check runs *before* the upload.  A
// dependency of javadocWeb would be unordered with respect to javadocWebUpload.
javadocWebUpload.configure { dependsOn(requireJavadoc) }

// Emacs support

/* Make Emacs TAGS table */
tasks.register<Exec>("tags") {
  group = "IDE"
  description = "Run etags to create an Emacs TAGS table"
  val sourceFiles =
    fileTree("src") {
      include("**/*.java")
      include("**/*.sh")
    }
  // `projectPath` in a build script is Gradle's project path (such as ":"), not a file system
  // path, so compute the project directory explicitly.
  val projectDirPath = layout.projectDirectory.asFile.toPath()
  inputs.files(sourceFiles)
  outputs.file(layout.projectDirectory.file("TAGS"))
  executable("etags")
  // Compute the arguments when the task runs, not when it is configured.
  argumentProviders.add(
    CommandLineArgumentProvider {
      sourceFiles.files.sorted().map { projectDirPath.relativize(it.toPath()).toString() }
    }
  )
}

// Debugging support

tasks.register("printCompileClasspaths") {
  group = "help"
  description = "Print the compile-time classpaths"
  // Look up the classpaths when the task is configured, and resolve them (`asPath`) when it runs.
  // Reading `sourceSets` from a task action would capture the `Project` object, which the
  // configuration cache forbids.
  val mainClasspath = sourceSets.main.get().compileClasspath
  val testClasspath = sourceSets.test.get().compileClasspath
  doFirst {
    println("Compile classpath:")
    println(mainClasspath.asPath)
    println("Compile test classpath:")
    println(testClasspath.asPath)
  }
}
