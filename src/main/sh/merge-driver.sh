#!/bin/sh

# This is a git merge driver for Java files. A git merge driver takes as input
# three filenames, for the current, base, and other versions of the file.
# The merge driver overwrites the current file with the merge result.
# Command-line flags such as `--verbose` can be passed before the filenames.

if [ "$1" = "--verbose" ] ; then
  VERBOSE=1
fi

if [ -n "$VERBOSE" ] ; then
  echo "$0:" "$@"
fi

SCRIPTDIR="$(cd "$(dirname "$0")" && pwd -P)"

ROOTDIR="${SCRIPTDIR}/../../.."
# JARFILE="${ROOTDIR}/build/libs/merging-all.jar"
EXECUTABLE="${ROOTDIR}/build/native/nativeCompile/plumelib-merge"

## Gradle is potentially too expensive to run on every invocation of this script.
# if [ -x "$EXECUTABLE" ] ; then
#     (cd "$ROOTDIR" && ./gradlew nativeCompile)
# else
#     (cd "$ROOTDIR" && ./gradlew shadowJar)
# fi

# Can add to the below if desired.
# TIMEFORMAT="%3R seconds" \
# time \

if [ -x "$EXECUTABLE" ] ; then
  if [ -n "$VERBOSE" ] ; then
    echo "running executable $EXECUTABLE"
  fi
  "$EXECUTABLE" driver "$@"
  result=$?
elif [ -n "${JAVA_HOME+x}" ] && [ -n "${JAVA17_HOME+x}" ] &&  [ "$JAVA_HOME" != "$JAVA17_HOME" ] ; then
  # JAVA_HOME is set, and JAVA17_HOME is set, and they differ.
  JAVA_HOME="$JAVA17_HOME" \
  "$JAVA17_HOME"/bin/java \
    --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    -cp "${SCRIPTDIR}/../../../build/libs/merging-all.jar" \
    org.plumelib.merging.Main driver \
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
    -cp "${SCRIPTDIR}/../../../build/libs/merging-all.jar" \
    org.plumelib.merging.Main driver \
    "$@"
  result=$?
fi

if [ -n "$VERBOSE" ] ; then
  echo "Result $result for merge-driver.sh:" "$@"
fi

exit $result
