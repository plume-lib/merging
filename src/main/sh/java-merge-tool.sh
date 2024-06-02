#!/bin/sh

# This is a git merge tool for Java files. A git merge tool takes as input four
# filenames, for the base, local, remote, and merged versions of the file; the
# merge driver overwrites the merged file with a better merge result.
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
  org.plumelib.merging.JavaMergeTool \
  "$@"

result=$?

if [ -n "$VERBOSE" ] ; then
  echo "Result $result for java-merge-tool.sh:" "$@"
fi

exit $result
