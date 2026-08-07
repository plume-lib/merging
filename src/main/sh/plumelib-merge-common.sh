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
# exit status of org.plumelib.merging.Main, or exits the calling script with
# status 2 if there is nothing to run.
#
# The `--add-exports` arguments below correspond to `javacInternalPackages` in
# build.gradle; keep the two lists in sync.
#
# This file is sourced, and POSIX sh has no `local`, so every variable that this
# function sets is global.  The `plumelib_` prefix keeps those variables from
# colliding with variables of the script that sources this file.
run_plumelib_merge() {
  plumelib_subcommand="$1"
  shift

  # Can add to the below if desired.
  # TIMEFORMAT="%3R seconds" \
  # time \

  if [ -x "$EXECUTABLE" ]; then
    if [ -n "${VERBOSE:-}" ]; then
      echo "running executable $EXECUTABLE"
    fi
    "$EXECUTABLE" "$plumelib_subcommand" "$@"
  else
    # Diagnose a missing fat jar here.  Otherwise the failure surfaces only as an
    # unexpected merge result, because callers such as src/test/resources/Makefile
    # ignore this script's exit status; `java` would merely report that it cannot
    # load org.plumelib.merging.Main, without saying which file is absent.
    if [ ! -f "$JARFILE" ]; then
      echo "$0: found no fat jar at $JARFILE; run: ./gradlew shadowJar" >&2
      exit 2
    fi

    # The fat jar's class files require Java 21 or later.
    #
    # PLUMELIB_MERGE_JAVA_HOME, when it is set and nonempty, names the Java
    # installation to use and overrides both variables below.  Gradle's
    # `runMakefileTests` task sets it, so that the tests do not depend on
    # whatever JAVA_HOME or JAVA21_HOME the developer's environment defines.
    #
    # Otherwise, JAVA21_HOME, when it is set and nonempty, names a Java 21
    # installation to use.  The comparison to JAVA_HOME only chooses
    # which variable a diagnostic names when the two agree.
    #
    # plumelib_java_home_source records which variable supplied
    # plumelib_java_home, so that a diagnostic can name the variable that the
    # user needs to correct.
    if [ -n "${PLUMELIB_MERGE_JAVA_HOME:-}" ]; then
      plumelib_java_home="$PLUMELIB_MERGE_JAVA_HOME"
      plumelib_java_home_source="PLUMELIB_MERGE_JAVA_HOME"
    elif [ -n "${JAVA21_HOME:-}" ] && [ "${JAVA_HOME:-}" != "$JAVA21_HOME" ]; then
      plumelib_java_home="$JAVA21_HOME"
      plumelib_java_home_source="JAVA21_HOME"
    else
      plumelib_java_home="${JAVA_HOME:-}"
      plumelib_java_home_source="JAVA_HOME"
    fi
    if [ -n "$plumelib_java_home" ]; then
      plumelib_java_command="${plumelib_java_home}/bin/java"
      # Make JAVA_HOME agree with the Java that is about to run.  This script
      # exits immediately afterward, so changing JAVA_HOME affects nothing else.
      JAVA_HOME="$plumelib_java_home"
      export JAVA_HOME
    else
      # None of the variables above names a Java installation.  Use the `java`
      # on PATH rather than "${JAVA_HOME}/bin/java", which would expand to
      # "/bin/java" and, on a system that has such a file, could silently run a
      # Java version that cannot read the fat jar's class files.
      plumelib_java_command="$(command -v java || true)"
    fi
    # Diagnose a missing Java here, for the same reason as the missing fat jar
    # above.  Name the variable that supplied the bad directory, rather than
    # listing every variable that might have; the one at fault is the one the
    # user needs to correct, and it is often PLUMELIB_MERGE_JAVA_HOME, which
    # Gradle rather than the user sets.
    if [ -z "$plumelib_java_command" ] || [ ! -x "$plumelib_java_command" ]; then
      if [ -n "$plumelib_java_home" ]; then
        echo "$0: $plumelib_java_home_source does not name a Java installation: no executable $plumelib_java_command" >&2
      else
        echo "$0: found no Java executable; set JAVA_HOME or JAVA21_HOME to a Java 21 or later installation" >&2
      fi
      exit 2
    fi
    "$plumelib_java_command" \
      --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
      --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
      -cp "$JARFILE" \
      org.plumelib.merging.Main "$plumelib_subcommand" \
      "$@"
  fi
}
