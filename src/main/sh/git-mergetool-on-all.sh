#!/bin/bash
# This script uses bash, not sh, because of process substitution.

# Run this script when a merge is in progress, or when HEAD is a merge.
# This script runs `git mergetool` on every file that is different in all of base, left, and right
# -- even if the file has been cleanly merged and contains no merge conflict markers.

toplevel=$(git rev-parse --show-toplevel)
merge_head_file="$toplevel/.git/MERGE_HEAD"
if [ -f "$merge_head_file" ] ; then
    if [ "$(wc -l <"$merge_head_file")" -ge 2 ] ; then
        echo "git-mergetool-on-all.sh: Can't handle octopus merge."
        exit 1
    fi
    LEFT=$(git rev-parse HEAD)
    RIGHT=$(cat .git/MERGE_HEAD)
elif git rev-parse HEAD^3 >/dev/null 2>/dev/null ; then
    echo "git-mergetool-on-all.sh: Can't handle octopus merge."
    exit 1
elif git rev-parse HEAD^2 >/dev/null 2>/dev/null ; then
    # I'm concerned that `git mergetool` isn't applicable here and I might need to do more work.
    LEFT=$(git rev-parse HEAD^1)
    RIGHT=$(git rev-parse HEAD^2)
else
    echo "git-mergetool-on-all.sh: Not in or at a merge."
    exit 1
fi

BASE="$(git merge-base "$LEFT" "$RIGHT")"

# Run the mergetool on all files that are different in all 3 versions.
comm -12 <(comm -12 <(git diff --name-only "${BASE}..${LEFT}" | sort) <(git diff --name-only "${BASE}..${RIGHT}" | sort)) <(git diff --name-only "${LEFT}..${RIGHT}" | sort) | xargs git mergetool


