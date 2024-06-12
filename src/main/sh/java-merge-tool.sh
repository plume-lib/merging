#!/bin/sh

# This is a git merge tool for Java files. A git merge tool takes as input four
# filenames, for the base, local, remote, and merged versions of the file.
# The merge tool overwrites the merged file with a better merge result.
# Command-line flags such as `--verbose` can be passed before the filenames.

if [ "$1" = "--verbose" ] ; then
  VERBOSE=1
fi

if [ -n "$VERBOSE" ] ; then
  echo "$0:" "$@"
fi

SCRIPTDIR="$(cd "$(dirname "$0")" && pwd -P)"

ROOTDIR="${SCRIPTDIR}/../../.."
JARFILE="${ROOTDIR}/build/libs/merging-all.jar"
if [ ! -f "$JARFILE" ] ; then
    (cd "$ROOTDIR" && ./gradlew shadowJar)
fi

# Can add to the below if desired.
# TIMEFORMAT="%3R seconds" \
# time \

if [ -n "${JAVA_HOME+x}" ] && [ -n "${JAVA17_HOME+x}" ] &&  [ "$JAVA_HOME" != "$JAVA17_HOME" ] ; then
  # JAVA_HOME is set and JAVA17_HOME is set and they differ.
  JAVA_HOME="$JAVA17_HOME" \
  "$JAVA17_HOME"/bin/java \
    --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    -cp "${SCRIPTDIR}/../../../build/libs/merging-all.jar" \
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
    -cp "${SCRIPTDIR}/../../../build/libs/merging-all.jar" \
    org.plumelib.merging.Main tool \
    "$@"
  result=$?
fi

if [ -n "$VERBOSE" ] ; then
  echo "Result $result for java-merge-tool.sh:" "$@"
fi

exit $result
