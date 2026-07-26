# Common code for merge-driver.sh and merge-tool.sh.  This file is sourced, not
# executed, so it has no shebang line and is not executable.  A script that
# sources this file must first set SCRIPT_DIR to the directory that contains the
# script.

# shellcheck shell=sh

ROOTDIR="${SCRIPT_DIR}/../../.."
JARFILE="${ROOTDIR}/build/libs/merging-all.jar"

# If PLUMELIB_MERGE_EXECUTABLE is set, it names the native executable to run, and
# setting PLUMELIB_MERGE_EXECUTABLE to the empty string forces use of the fat jar.
# If PLUMELIB_MERGE_EXECUTABLE is unset, the native executable in its default
# location is used, if one has been built there.  Gradle's `runMakefileTests`
# task sets PLUMELIB_MERGE_EXECUTABLE, so that the tests cannot silently run a
# stale executable that a previous build left behind.
if [ -n "${PLUMELIB_MERGE_EXECUTABLE+x}" ]; then
  EXECUTABLE="$PLUMELIB_MERGE_EXECUTABLE"
  if [ -n "$EXECUTABLE" ] && [ ! -x "$EXECUTABLE" ]; then
    echo "$0: PLUMELIB_MERGE_EXECUTABLE is not an executable file: $EXECUTABLE" >&2
    exit 2
  fi
else
  EXECUTABLE="${ROOTDIR}/build/native/nativeCompile/plumelib-merge"
fi

## Gradle is potentially too expensive to run on every invocation of this script.
# if [ -x "$EXECUTABLE" ] ; then
#     (cd "$ROOTDIR" && ./gradlew nativeCompile)
# else
#     (cd "$ROOTDIR" && ./gradlew shadowJar)
# fi

# Runs org.plumelib.merging.Main: the native executable if one is available, and
# the fat jar otherwise.  The first argument is the subcommand ("driver" or
# "tool"); the remaining arguments are passed to the subcommand.  Returns the
# exit status of org.plumelib.merging.Main.
#
# The `--add-exports` arguments below correspond to `javacInternalPackages` in
# build.gradle; keep the two lists in sync.
run_plumelib_merge() {
  subcommand="$1"
  shift

  # Can add to the below if desired.
  # TIMEFORMAT="%3R seconds" \
  # time \

  if [ -x "$EXECUTABLE" ]; then
    if [ -n "${VERBOSE:-}" ]; then
      echo "running executable $EXECUTABLE"
    fi
    "$EXECUTABLE" "$subcommand" "$@"
  else
    # The fat jar's class files require Java 21 or later.
    #
    # PLUMELIB_MERGE_JAVA_HOME, when it is set and nonempty, names the Java
    # installation to use and overrides both variables below.  Gradle's
    # `runMakefileTests` task sets it, so that the tests do not depend on
    # whatever JAVA_HOME or JAVA21_HOME the developer's environment defines.
    #
    # Otherwise, JAVA21_HOME, when it is set and differs from JAVA_HOME, names a
    # Java 21 installation to use.
    if [ -n "${PLUMELIB_MERGE_JAVA_HOME:-}" ]; then
      java_home="$PLUMELIB_MERGE_JAVA_HOME"
    elif [ -n "${JAVA_HOME+x}" ] && [ -n "${JAVA21_HOME+x}" ] && [ "$JAVA_HOME" != "$JAVA21_HOME" ]; then
      java_home="$JAVA21_HOME"
    else
      java_home="${JAVA_HOME:-}"
    fi
    if [ -n "$java_home" ]; then
      java_command="${java_home}/bin/java"
      # Make JAVA_HOME agree with the Java that is about to run.  This script
      # exits immediately afterward, so changing JAVA_HOME affects nothing else.
      JAVA_HOME="$java_home"
      export JAVA_HOME
    else
      # None of the variables above names a Java installation.  Use the `java`
      # on PATH rather than "${JAVA_HOME}/bin/java", which would expand to
      # "/bin/java" and, on a system that has such a file, could silently run a
      # Java version that cannot read the fat jar's class files.
      java_command="$(command -v java || true)"
    fi
    # Diagnose a missing Java here.  Otherwise the failure surfaces only as an
    # unexpected merge result, because callers such as src/test/resources/Makefile
    # ignore this script's exit status.
    if [ -z "$java_command" ] || [ ! -x "$java_command" ]; then
      echo "$0: found no Java executable; set JAVA_HOME or JAVA21_HOME to a Java 21 or later installation" >&2
      exit 2
    fi
    "$java_command" \
      --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
      -cp "$JARFILE" \
      org.plumelib.merging.Main "$subcommand" \
      "$@"
  fi
}
