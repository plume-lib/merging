# End-to-end test cases

Each directory here is one test case for `EndToEndTest.java`, which runs the
program in a subprocess and examines only the program's output:  the file that
the program writes, its standard output, its standard error, and its exit
status.

## File naming conventions

Within a test case directory, `EXT` is the file extension (such as `.java`,
`.txt`, or `.gradle`) of the files being merged.  All the files being merged
have the same extension.

| File name | Meaning |
| --- | --- |
| `base`*EXT* | the base version of the file |
| `left`*EXT* | the left (also known as "current" or "ours") version |
| `right`*EXT* | the right (also known as "other" or "theirs") version |
| `merged`*EXT* | the conflicted merge that git produced; an input to the merge tool |
| `goal`*EXT* | the file that the program should write |
| `goal-backward`*EXT* | the file that the program should write, when the left and right versions are swapped |
| `goal-`*NAME**EXT* | the file that the program should write, for the run named *NAME* (for example, with different command-line arguments) |
| `goal-stdout.txt` | what the program should print to standard output; if absent, the program should print nothing |
| `goal-stderr.txt` | what the program should print to standard error; if absent, the program should print nothing |

A test that swaps the left and right versions of the file uses goal file
`goal-backward`*EXT* if the test case directory contains one, and `goal`*EXT*
otherwise.  Most mergers produce the same result no matter which version is the
left one; the exception is that git's conflict markers list the versions in the
order they were given.

The program's exit status is not stored in a file:  it is 1 if the goal file
contains a conflict marker, and 0 if it does not.

Directory `cli-args` is different from the others:  its goal files hold the
messages that the program prints when its command-line arguments are erroneous.
