# End-to-end test cases

This directory holds the data for the end-to-end tests of `plumelib-merge`, the
program that acts as a git merge driver or git merge tool.  The test data is
independent of any implementation language:  a test harness written in any
language can run these test cases against any implementation of the program.
`src/test/java/org/plumelib/merging/EndToEndTest.java` is the harness for the
Java implementation.

Each subdirectory of this directory is one test case; the name of the
subdirectory is the name of the test case.  A test case runs the program once
and compares everything that the program produced to goal files.  To add a test
case, add a subdirectory; no harness code needs to change.

## The program's command line

The program's command-line arguments are, in order:

* the mode:  `driver` or `tool`;
* zero or more options, such as `--only-adjacent` or `--no-java-imports`;
* for mode `driver`, three file names:  the current (left, ours) file, the base
  file, and the other (right, theirs) file.  The program overwrites the current
  file with the merge result.  Unless option `--no-git-merge-file` is given,
  the program first runs `git merge-file --diff3` on the three files;
* for mode `tool`, four file names:  the local (left, ours) file, the base file,
  the remote (right, theirs) file, and the merged file, which contains the
  result of an earlier merge, possibly with conflict markers.  The program
  overwrites the merged file with an improved merge result.

The program's exit status is 0 if the result has no conflict, 1 if the result
has a conflict, 129 if the arguments are inconsistent or name an unreadable
file, and 2 if the command line cannot be parsed at all.

## Files in a test case directory

| File | Meaning |
| --- | --- |
| `description.txt` | Required.  What the test case checks.  The harness ignores this file.  A description that starts with "BUG:" describes a way in which the program behaves differently than it should; the goal files record what the program currently does. |
| `args.txt` | Required.  The program's command-line arguments, one per line, starting with the mode.  Empty lines are ignored.  An empty file means no arguments. |
| `input/` | Files that the harness copies into the working directory before running the program.  The file names in `args.txt` refer to these files, relative to the working directory. |
| `goal/` | Files that should be in the working directory after the program runs, in addition to the files of `input/`, or in place of the same-named files of `input/`.  Usually it holds the file that the program overwrote. |
| `goal-stdout.txt` | What the program should write to standard output. |
| `goal-stdout-prefix.txt` | What the program's standard output should start with. |
| `goal-stderr.txt` | What the program should write to standard error. |
| `goal-stderr-prefix.txt` | What the program's standard error should start with. |
| `goal-status.txt` | The program's exit status, as a decimal integer.  If this file is absent, the exit status should be 0. |

A test case has at most one of `goal-stdout.txt` and `goal-stdout-prefix.txt`,
and at most one of `goal-stderr.txt` and `goal-stderr-prefix.txt`.  If it has
neither, the program should write nothing to that stream.  A `*-prefix.txt`
file is used where the full output would be brittle, as for a usage message or
for diagnostic output that contains the names of temporary files.  The final
line separator of a `*-prefix.txt` file is not part of the prefix, so a prefix
can end in the middle of a line.

The harness rejects a test case directory that lacks a required file or that
contains any file not listed above.

## How to run a test case

1. Create a fresh, empty working directory.  It must not be inside a git
   repository.
2. Copy the contents of `input/`, if it exists, into the working directory.
3. Run the program in the working directory, passing the arguments in
   `args.txt`.  Standard input is an empty file.  The program's standard
   output and standard error go to files outside the working directory.  The
   environment is the harness's environment, with these changes, which isolate
   the `git` program (which the program runs) from the user's configuration:
   * remove every variable whose name starts with `GIT_`;
   * set `GIT_CONFIG_NOSYSTEM` to `1`;
   * set `GIT_CONFIG_GLOBAL` to the name of an empty file outside the working
     directory;
   * set `GIT_CEILING_DIRECTORIES` to the parent of the working directory.
4. Compare the results to the goal files.  The test case passes if all of
   these hold:
   * The exit status equals the contents of `goal-status.txt` (or 0).
   * Standard output and standard error are as described above.  Before
     comparison, each CR LF in the output and in the goal file is replaced by
     LF, because the program ends each line that it prints with the platform's
     line separator.
   * The working directory contains exactly the files of `input/` and
     `goal/`, no more and no fewer.  Each file whose name is in `goal/` is
     identical to that goal file; each other file is identical to the file of
     the same name in `input/`.  These comparisons are of bytes:  there is no
     normalization of line separators or anything else.

The program writes temporary files elsewhere, such as in the system's temporary
directory; the harness ignores those.

The goal files assume a platform whose line separator is LF:  where the program
generates a line, such as a conflict marker that it writes itself, it ends the
line with the platform's line separator.

## How the test data was made

In mode `tool`, the merged file in `input/` is usually the output of `git
merge-file` on the left, base, and right files, as in `git merge-file -p
--diff3 -L OURS -L BASE -L THEIRS left.java base.java right.java >
merged.java`.  A test case of a merge-style conflict (with no base section)
omits `--diff3`.

Many test cases come in pairs:  test case `NAME-backward` is the same as test
case `NAME`, but with the current and other versions swapped.  Most mergers give
the same result in both directions, except that conflict markers list the
current version first.

## Line separators and whitespace in the test data

These files are test data, so their exact bytes matter:  some of them
intentionally lack a final line separator, have trailing whitespace, or use
CR LF line separators.  `prek.toml` excludes this directory from the hooks
that would "fix" such things, and `.gitattributes` turns off line-ending
conversion for it.
