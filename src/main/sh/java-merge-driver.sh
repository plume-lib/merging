#!/bin/sh

# This is a git merge driver for Java files. A git merge driver takes as input
# three filenames, for the current, base, and other versions of the file; the
# merge driver overwrites the current file with the merge result.
#
# This program first does `git merge-file`, then it tries to re-insert any
# `import` statements that were removed but are needed for compilation to
# succeed.

# echo "java-merge-driver.sh:" "$@"

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
