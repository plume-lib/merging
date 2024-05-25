#!/bin/bash
# This script uses bash, not sh, because of process substitution and arrays.

# Run this script when a merge is in progress, or when HEAD is a merge.
# This script runs `git mergetool` on every file that is different in all of base, left, and right
# -- even if the file has been cleanly merged and contains no merge conflict markers.

# Show commands as they are executed.
set -x

toplevel=$(git rev-parse --show-toplevel)
merge_head_file="$toplevel/.git/MERGE_HEAD"
if [ -f "$merge_head_file" ] ; then
    # A merge is in progress.
    if [ "$(wc -l <"$merge_head_file")" -ge 2 ] ; then
        echo "git-mergetool-on-all.sh: Can't handle octopus merge."
        exit 1
    fi
    LEFT_REV="$(git rev-parse HEAD)"
    RIGHT_REV="$(cat .git/MERGE_HEAD)"
elif git rev-parse HEAD^3 >/dev/null 2>/dev/null ; then
    echo "git-mergetool-on-all.sh: Can't handle octopus merge."
    exit 1
elif git rev-parse HEAD^2 >/dev/null 2>/dev/null ; then
    # A merge has just occurred.
    # I'm concerned that `git mergetool` isn't applicable here and I might need to do more work.
    LEFT_REV="$(git rev-parse HEAD^1)"
    RIGHT_REV="$(git rev-parse HEAD^2)"
else
    echo "git-mergetool-on-all.sh: Not in or at a merge."
    exit 1
fi

BASE_REV="$(git merge-base "$LEFT_REV" "$RIGHT_REV")"

# echo "BASE_REV ${BASE_REV} LEFT_REV ${LEFT_REV} RIGHT_REV ${RIGHT_REV}"

# Discover all files that are different in all 3 versions.
readarray -t differing_files < <(comm -12 <(comm -12 <(git diff --name-only "${BASE_REV}..${LEFT_REV}" | sort) <(git diff --name-only "${BASE_REV}..${RIGHT_REV}" | sort)) <(git diff --name-only "${LEFT_REV}..${RIGHT_REV}" | sort))

# Unfortunately, mergetool_command lacks the quoting that appears in the git configuration file.
# This might lead to trouble with filenames that contain spaces.
# I'm not sure why `git config` reports it without the quotes.
mergetool_command="$(git config --get mergetool."$(git config --get merge.tool)".cmd)"

for file in "${differing_files[@]}" ; do
  basefile="$(mktemp -p /tmp "base-XXXXXX-$(basename "$file")")"
  git show "$BASE_REV:$file" > "$basefile"
  leftfile="$(mktemp -p /tmp "left-XXXXXX-$(basename "$file")")"
  git show "$LEFT_REV:$file" > "$leftfile"
  rightfile="$(mktemp -p /tmp "right-XXXXXX-$(basename "$file")")"
  git show "$RIGHT_REV:$file" > "$rightfile"

  command="export BASE='$basefile'; export LOCAL='$leftfile'; export REMOTE='$rightfile'; export MERGED='$file'; $mergetool_command"
  eval "$command"

done
