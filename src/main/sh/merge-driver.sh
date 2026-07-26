#!/bin/sh

# This is a git merge driver for Java files. A git merge driver takes as input
# three filenames, for the current, base, and other versions of the file.
# The merge driver overwrites the current file with the merge result.
# Command-line flags such as `--verbose` can be passed before the filenames.

if [ "$1" = "--verbose" ]; then
  VERBOSE=1
fi

if [ -n "$VERBOSE" ]; then
  echo "$0:" "$@"
fi

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd -P)"

# shellcheck source=plumelib-merge-common.sh
. "${SCRIPT_DIR}/plumelib-merge-common.sh"

run_plumelib_merge driver "$@"
result=$?

if [ -n "$VERBOSE" ]; then
  echo "Result $result for merge-driver.sh:" "$@"
fi

exit $result
