#!/bin/sh

# This is a git merge driver for Java files. A git merge driver takes as input
# three filenames, for the current, base, and other versions of the file; the
# merge driver overwrites the current file with the merge result.
# Command-line flags such as `--verbose` can be passed before the filenames.

if [ "$1" = "--verbose" ] ; then
  VERBOSE=1
fi

if [ -n "$VERBOSE" ] ; then
  echo "$0:" "$@"
fi

SCRIPTDIR="$(cd "$(dirname "$0")" && pwd -P)"

ROOTDIR="${SCRIPTDIR}/../../.."
JARFILE="${ROOTDIR}/build/libs/merge-tools-all.jar"
if [ ! -f "$JARFILE" ] ; then
    (cd "$ROOTDIR" && ./gradlew shadowJar)
fi

# TIMEFORMAT="%3R seconds"
# time \
java \
  --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
  -cp "${SCRIPTDIR}/../../../build/libs/merge-tools-all.jar" \
  org.plumelib.merging.JavaMergeDriver \
  "$@"

result=$?

if [ -n "$VERBOSE" ] ; then
  echo "Result $result for java-merge-driver.sh:" "$@"
fi

exit $result
