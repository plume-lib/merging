#!/bin/sh

# This is a git merge tool for Java files. A git merge tool takes as input four
# filenames, for the base, local, remote, and merged versions of the file; the
# merge driver overwrites the merged file with a better merge result.

# echo "java-merge-tool.sh:" "$@"

SCRIPTDIR="$(cd "$(dirname "$0")" && pwd -P)"

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
