val minimumJavaVersion = JavaVersion.VERSION_21

if (JavaVersion.current() < minimumJavaVersion) {
  throw GradleException(
    "This build requires Gradle to run under Java $minimumJavaVersion or later," +
      " but Gradle is running under Java ${JavaVersion.current()}" +
      " from ${System.getProperty("java.home")}."
  )
}

// This project's class files are Java 21 class files, because build.gradle.kts cannot use
// `--release` (which is incompatible with `--add-exports`).  An earlier JVM therefore cannot run
// the tests.  This check must run before the `plugins` block resolves the foojay-resolver plugin.
pluginManagement {
  if (JavaVersion.current() < JavaVersion.VERSION_21) {
    throw GradleException("Use Java 21 or later.")
  }
}

plugins {
  // Downloads a JDK if the Java toolchain that build.gradle.kts requests is not installed locally.
  //
  // Literal version number because a settings script cannot use the version catalog
  // "gradle/libs.versions.toml", which is not yet available while settings.gradle.kts is evaluated.
  // Dependency-update tools do not propose new versions of this plugin, so update it by hand,
  // from https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention.
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Project name is read-only in build scripts, and defaults to directory name.
rootProject.name = "merging"
