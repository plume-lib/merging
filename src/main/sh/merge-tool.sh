#!/bin/sh

# This is a git merge tool for Java files. A git merge tool takes as input four
# filenames, for the local, base, remote, and merged versions of the file.
# The merge tool overwrites the merged file with a better merge result.
# Command-line flags such as `--verbose` can be passed before the filenames.

if [ "$1" = "--verbose" ]; then
  VERBOSE=1
fi

if [ -n "$VERBOSE" ]; then
  echo "$0:" "$@"
fi

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd -P)"

ROOTDIR="${SCRIPT_DIR}/../../.."
# JARFILE="${ROOTDIR}/build/libs/merging-all.jar"

# If PLUMELIB_MERGE_EXECUTABLE is set, it names the native executable to run, and
# setting PLUMELIB_MERGE_EXECUTABLE to the empty string forces use of the fat jar.
# If PLUMELIB_MERGE_EXECUTABLE is unset, this script uses the native executable
# in its default location, if one has been built there.  Gradle's
# `runMakefileTests` task sets PLUMELIB_MERGE_EXECUTABLE, so that the tests
# cannot silently run a stale executable that a previous build left behind.
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

# Can add to the below if desired.
# TIMEFORMAT="%3R seconds" \
# time \

if [ -x "$EXECUTABLE" ]; then
  if [ -n "$VERBOSE" ]; then
    echo "running executable $EXECUTABLE"
  fi
  "$EXECUTABLE" tool "$@"
  result=$?
elif [ -n "${JAVA_HOME+x}" ] && [ -n "${JAVA21_HOME+x}" ] && [ "$JAVA_HOME" != "$JAVA21_HOME" ]; then
  # JAVA_HOME is set, and JAVA21_HOME is set, and they differ.
  JAVA_HOME="$JAVA21_HOME" \
    "$JAVA21_HOME"/bin/java \
    --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    -cp "${SCRIPT_DIR}/../../../build/libs/merging-all.jar" \
    org.plumelib.merging.Main tool \
    "$@"
  result=$?
else
  "$JAVA_HOME"/bin/java \
    --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    -cp "${SCRIPT_DIR}/../../../build/libs/merging-all.jar" \
    org.plumelib.merging.Main tool \
    "$@"
  result=$?
fi

if [ -n "$VERBOSE" ]; then
  echo "Result $result for merge-tool.sh:" "$@"
fi

exit $result
