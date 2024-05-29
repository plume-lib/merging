#!/bin/bash

# This script is like `git mergetool`, with two differences:
#  * It requires no user interaction (unless the merge tool does).
#  * When passed the `--all` or `-a` command-line argument, this
#    script runs a git mergetool on every file that is different in
#    all of base, left, and right.  It does so even if the file has
#    been cleanly merged and contains no merge conflict markers, and
#    even if the merge completed and was committed.  (That is, the
#    script can be run either when a merge is in progress, or when
#    HEAD is a merge.)

show_help () {
  echo "git-mergetool.sh [-a | --all] [--tool=<tool>] [<file>...]"
}

# Initialize our own variables:
tool=""
all=NO
files=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|-\?)
      show_help
      exit 0
      ;;
    -a|--all)
      all=YES
      shift
      ;;
    --verbose)
      verbose=YES
      shift
      ;;
    -t|--tool)
      tool="$2";
      shift 2
      ;;
    -t=*|--tool=*)
      tool="${1#*=}"
      shift
      ;;
    -*)
      echo "Unknown option $1"
      exit 1
      ;;
    *)
       files+=("$1");
       shift 1
       ;;
  esac
done

if [ -n "$verbose" ] ; then
  # Show commands as they are executed.
  set -x
fi

toplevel=$(git rev-parse --show-toplevel)
merge_head_file="$toplevel/.git/MERGE_HEAD"
if [ -f "$merge_head_file" ] ; then
  # A merge is in progress.
  if [ "$(wc -l <"$merge_head_file")" -ge 2 ] ; then
    echo "git-mergetool.sh: Can't handle octopus merge."
    exit 1
  fi
  LEFT_REV="$(git rev-parse HEAD)"
  RIGHT_REV="$(cat .git/MERGE_HEAD)"
elif git rev-parse HEAD^3 >/dev/null 2>/dev/null ; then
  # An octopus merge (i.e., with more than 2 parents) has just occurred.
  echo "git-mergetool.sh: Can't handle octopus merge."
  exit 1
elif git rev-parse HEAD^2 >/dev/null 2>/dev/null ; then
  # A merge with 2 parents has just occurred.
  LEFT_REV="$(git rev-parse HEAD^1)"
  RIGHT_REV="$(git rev-parse HEAD^2)"
else
  echo "git-mergetool.sh: Not in or at a merge."
  exit 1
fi

BASE_REV="$(git merge-base "$LEFT_REV" "$RIGHT_REV")"

if [ -n "$verbose" ] ; then
  echo "BASE_REV ${BASE_REV} LEFT_REV ${LEFT_REV} RIGHT_REV ${RIGHT_REV}"
fi

if [ ${#files[@]} -eq 0 ] ; then
  # `files` is empty.
  if [ "$all" = "YES" ] ; then
    readarray -t files < \
      <(comm -12 <(comm -12 <(git diff --name-only "${BASE_REV}..${LEFT_REV}" | sort) \
                            <(git diff --name-only "${BASE_REV}..${RIGHT_REV}" | sort)) \
                 <(git diff --name-only "${LEFT_REV}..${RIGHT_REV}" | sort))
  else
    # TODO: Does this handle filenames with spaces? How should core.quotePath be set?
    mapfile -t files < <(git -c core.quotePath=false diff --name-only --diff-filter=U)
  fi
fi

if [ ${#files[@]} -eq 0 ] ; then
  exit 0
fi


# Unfortunately, mergetool_command lacks the quoting that appears in the git configuration file.
# This might lead to trouble with filenames that contain spaces.
# I'm not sure why `git config` reports it without the quotes.
# I might need to read the configuration file directly. :-(
if [ -z "${tool}" ] ; then
  tool="$(git config --get merge.tool)"
  if [ -z "${tool}" ] ; then
    echo "No mergetool specified with --tool or configured with \"git config\""
    exit 1
  fi
fi
mergetool_command="$(git config --get mergetool."$tool".cmd)"

for file in "${files[@]}" ; do
  basefile="$(mktemp -p /tmp "base-XXXXXX-$(basename "$file")")"
  git show "$BASE_REV:$file" > "$basefile"
  leftfile="$(mktemp -p /tmp "left-XXXXXX-$(basename "$file")")"
  git show "$LEFT_REV:$file" > "$leftfile"
  rightfile="$(mktemp -p /tmp "right-XXXXXX-$(basename "$file")")"
  git show "$RIGHT_REV:$file" > "$rightfile"

  command="export BASE='$basefile'; export LOCAL='$leftfile'; export REMOTE='$rightfile'; export MERGED='$file'; $mergetool_command"
  if [ "$verbose" = 1 ] ; then
    echo "$command"
  fi
  eval "$command"

  if [ -z "$verbose" ] ; then
    rm -f "$basefile" "$leftfile" "$rightfile"
  fi
done
