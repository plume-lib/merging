#!/bin/bash

# This script is a git re-merge tool.
# This script is like `git mergetool`, with two differences:
#  * It requires no user interaction (unless the merge tool does).
#  * When passed the `--all` or `-a` command-line argument, this script runs a
#    git mergetool on every file that is different in all of left, base, and
#    right.  It does so even if the file has been cleanly merged and contains no
#    merge conflict markers, and even if the merge completed and was committed.
#    (That is, the script can be run either when a merge is in progress, or when
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
      exit 2
      ;;
    *)
       files+=("$1");
       shift 1
       ;;
  esac
done

if [ -n "$verbose" ] ; then
  echo "entered $0"
  echo "files:" "${files[@]}"
  # Show commands as they are executed.
  set -x
fi

if [ ${#files[@]} -ne 0 ] && [ "$all" = "YES" ] ; then
  echo "$0: Supplied both --all and file names:" "${files[@]}"
  exit 2
fi

toplevel=$(git rev-parse --show-toplevel)
merge_head_file="$toplevel/.git/MERGE_HEAD"
if [ -f "$merge_head_file" ] ; then
  # A merge is in progress.
  if [ "$(wc -l <"$merge_head_file")" -ge 2 ] ; then
    echo "git-mergetool.sh: Can't handle octopus merge."
    exit 2
  fi
  LEFT_REV="$(git rev-parse HEAD)"
  RIGHT_REV="$(cat .git/MERGE_HEAD)"
elif git rev-parse HEAD^3 >/dev/null 2>/dev/null ; then
  # An octopus merge (i.e., with more than 2 parents) has just occurred.
  echo "git-mergetool.sh: Can't handle octopus merge."
  exit 2
elif git rev-parse HEAD^2 >/dev/null 2>/dev/null ; then
  # A merge with 2 parents has just occurred.
  LEFT_REV="$(git rev-parse HEAD^1)"
  RIGHT_REV="$(git rev-parse HEAD^2)"
else
  echo "$0: Not in or at a merge."
  exit 2
fi

BASE_REV="$(git merge-base "$LEFT_REV" "$RIGHT_REV")"

if [ -n "$verbose" ] ; then
  echo "$0: LEFT_REV ${LEFT_REV} BASE_REV ${BASE_REV} RIGHT_REV ${RIGHT_REV}"
fi

if [ ${#files[@]} -eq 0 ] && [ "$all" = "YES" ] ; then
  # The caller provided no files on the command line.
  # We cannot use
  #    mapfile -t files < <(git -c core.quotePath=false diff --name-only --diff-filter=U)
  # because if git merge already made a commit, it will return nothing rather than all changed files.
  readarray -t files < \
    <(comm -12 <(comm -12 <(git diff --name-only "${BASE_REV}..${LEFT_REV}" | sort) \
                          <(git diff --name-only "${BASE_REV}..${RIGHT_REV}" | sort)) \
               <(git diff --name-only "${LEFT_REV}..${RIGHT_REV}" | sort))
  # For debugging the above line.
  # if [ -n "$verbose" ] ; then
  #   echo "base to left:"
  #   git diff --name-only "${BASE_REV}..${LEFT_REV}" | sort
  #   echo "base to right:"
  #   git diff --name-only "${BASE_REV}..${RIGHT_REV}" | sort
  #   echo "left to right:"
  #   git diff --name-only "${LEFT_REV}..${RIGHT_REV}" | sort
  #   echo "end of two-way diffs."
  # fi
fi

if [ ${#files[@]} -eq 0 ] ; then
  if [ -n "$verbose" ] ; then
    echo "$0: no files; exiting"
  fi
  exit 0
fi

if [ -n "$verbose" ] ; then
  echo "$0: files = ${files[*]}"
fi

# Unfortunately, mergetool_command lacks the quoting that appears in the git configuration file.
# This might lead to trouble with filenames that contain spaces.
# I'm not sure why `git config` reports it without the quotes.
# I might need to read the configuration file directly. :-(
if [ -z "${tool}" ] ; then
  tool="$(git config --get merge.tool)"
  if [ -z "${tool}" ] ; then
    echo "No mergetool specified with --tool or configured with \"git config\""
    exit 2
  fi
fi
mergetool_command="$(git config --get mergetool."$tool".cmd)"
mergetool_trustExitCode="$(git config --get mergetool."$tool".trustExitCode)"

function is_bin_in_path {
  builtin type -P "$1" &> /dev/null
}
function beginswith() { case $2 in "$1"*) true;; *) false;; esac; }

mergetool_command_first_word=${mergetool_command%% *}
if beginswith "$mergetool_command_first_word" "/" ; then
  if [ ! -f "$mergetool_command_first_word" ] ; then
    echo "$0: WARNING: file does not exist: $mergetool_command_first_word"
    echo "$0: WARNING: file does not exist: $mergetool_command_first_word" >&2
  fi
elif is_bin_in_path "$mergetool_command_first_word" ; then
  : # OK
else
  echo "$0: WARNING: not in path: $mergetool_command_first_word"
  echo "$0: WARNING: not in path: $mergetool_command_first_word" >&2
fi

## Enable this for debugging.  Watch out, there will be filename collisions if
## this script is being run multiple times in parallel.
# deterministic_filename=YES

# I tried to make this loop parallel by enclosing the body in "( ... ) &" and
# adding "wait" after the loop, but that led to nondeterministic behavior.  One
# problem might be that git operations running in parallel interfere with one
# another, for example by creating lock files.

for file in "${files[@]}" ; do
  if [ -n "$deterministic_filename" ] ; then
    hash="$(echo "${file}" | sha256sum | cut -c1-8)"
  fi

  # `git cat-file -e "$RIGHT_REV:$file"` sometimes doesn't work; I don't know why.  So use `git show`.
  if [ -n "$deterministic_filename" ] ; then
    leftfile="/tmp/left-$hash-$(basename "$file")"
    touch "$leftfile"
  else
    leftfile="$(mktemp -p /tmp "left-XXXXXX" --suffix "-$(basename "$file")")"
  fi
  # shellcheck disable=2106 # the group is the whole loop body
  if ! git show "$LEFT_REV:$file" > "$leftfile" ; then continue ; fi

  if [ -n "$deterministic_filename" ] ; then
    basefile="/tmp/base-$hash-$(basename "$file")"
    touch "$basefile"
  else
    basefile="$(mktemp -p /tmp "base-XXXXXX" --suffix "-$(basename "$file")")"
  fi
  # shellcheck disable=2106 # the group is the whole loop body
  if ! git show "$BASE_REV:$file" > "$basefile" ; then continue ; fi

  if [ -n "$deterministic_filename" ] ; then
    rightfile="/tmp/right-$hash-$(basename "$file")"
    touch "$rightfile"
  else
    rightfile="$(mktemp -p /tmp "right-XXXXXX" --suffix "-$(basename "$file")")"
  fi
  # shellcheck disable=2106 # the group is the whole loop body
  if ! git show "$RIGHT_REV:$file" > "$rightfile" 2> /dev/null ; then continue ; fi

  command="export LOCAL='$leftfile'; export BASE='$basefile'; export REMOTE='$rightfile'; export MERGED='$file'; $mergetool_command"
  if [ -n "$verbose" ] ; then
    echo "$0: command = $command"
  fi
  eval "$command"

  # `git add` the file if the merge was successful.
  command_status=$?
  if [ "$mergetool_trustExitCode" == true ] ; then
    if [ "$command_status" -eq 0 ] ; then
      git add "$file"
    fi
  else
    if git \
         -c core.whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,-indent-with-non-tab,-tab-in-indent,-cr-at-eol \
         diff --check --quiet "$file" ; then
      git add "$file"
    fi
  fi

  if [ -z "$verbose" ] ; then
    rm -f "$leftfile" "$basefile" "$rightfile"
  fi
done
