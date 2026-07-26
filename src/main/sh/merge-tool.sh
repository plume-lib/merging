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

# shellcheck source=plumelib-merge-common.sh
. "${SCRIPT_DIR}/plumelib-merge-common.sh"

run_plumelib_merge tool "$@"
result=$?

if [ -n "$VERBOSE" ]; then
  echo "Result $result for merge-tool.sh:" "$@"
fi

exit $result
