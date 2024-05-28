# merge-tools

This project contains git merge drivers and git merge tools.  (See
[below](#git-merge-terminology) for definitions of "merge driver" and
"merge tool".)

Currently some of the mergers only work on Java files, and some are more general.

Currently they are relatively slow:  about 1/3 second per Java file that was
modified in both versions to be merged.  Most merges involve few Java files that
were modified in both versions, but if there are many, the merge will be slow.


## Features

* [Adjacent lines](README-adjacent-lines.md): This resolves conflicts when
the two edits affect different lines that are adjacent.  By default, git
considers edits to different, adjacent lines to be a conflict.

* [Java annotations](README-java-annotations.md):  This resolves conflicts in
favor of retaining a Java annotation, when the only textual difference is in
annotations.

* [Java imports](README-java-imports.md):  This handles conflicts in `import`
statements, keeping all the necessary imports.  It also prevents a merge from
removing a needed `import` statement, even if the merge would be clean.  It does
nothing if the file's conflicts contain anything other than import statements.

* [Version numbers](README-version-numbers.md):  This resolves conflicts in
favor of the larger version number.

You can enable and disable each feature individually, or enable just one feature.
These command-line arguments are supported by the merge driver
`java-merge-driver.sh` and the merge tool `java-merge-tool.sh`.
 * `--adjacent`, `--no-adjacent`, `--only-adjacent` [default: disabled]
 * `--annotations`, `--no-annotations`, `--only-annotations` [default: enabled]
 * `--imports`, `--no-imports`, `--only-imports` [default: enabled]
 * `--version-numbers`, `--no-version-numbers`, `--only-version-numbers` [default: enabled]

Unfortunately, git does not permit the user to specify command-line
arguments to be passed to a merge driver or merge tool.  See below for how
to define different merge drivers and merge tools that pass different
command-line arguments.


## How to use

You can use the mergers in this repository in three ways: as merge drivers,
as merge tools, or as re-merge tools.


### Common setup

0. You must have Java 17 or later installed.

1. Clone this repository.

2. In the top level of this repository, run: `./gradlew shadowJar`

3. Put directory `.../merge-tools/src/main/sh/` on your PATH,
adjusting "..." according to where you cloned this repository.
After changing a dotfile to set PATH, you may need to log out
and log back in again to have the change take effect.
(Or, use the absolute pathname in uses of `*.sh` files below.)


### How to use as a merge driver

After performing the following steps, git will automatically use this merge
driver for every merge of Java files.  You can also define your own merge
drivers that pass different sets of arguments, beyond the `merge-java` and
`merge-adjacent` merge drivers defined below.

1. Run these commands:
```
git config --global merge.conflictstyle diff3
git config --global merge.merge-java.name "Merge Java files"
git config --global merge.merge-java.driver 'java-merge-driver.sh %A %O %B'
git config --global merge.merge-adjacent.name "Merge changes on adjacent lines"
git config --global merge.merge-adjacent.driver 'java-merge-driver.sh --only-adjacent %A %O %B'
```

To take effect only for one repository, replace `--global` by `--local` and run
the commands within the repository.

2. In a gitattributes file, add:

```
*.java merge=merge-java
```

or

```
* merge=merge-adjacent
```

To enable this for a single repository, add this to the repository's `.gitattributes` file.
(Or to its `.git/info/attributes` file, in which case it won't be committed with the project.)

To enable this for all repositories, add this to your your user-level
gitattributes file.  The user-level gitattributes file is by default
`$XDG_CONFIG_HOME/git/attributes`.  You can change the user-level file to be
`~/.gitattributes` by running the following command, once ever per computer:
`git config --global core.attributesfile '~/.gitattributes'`


### How to use as a merge tool

First, edit your `~/.gitconfig` file as shown below.

Run one of the following commands after a git merge that leaves conflicts:

```
yes | git mergetool --tool=merge-java
```
or
```
yes | git mergetool --tool=merge-adjacent
```

The reason for `yes |` is that `git mergetool` stops and asks after each file
that wasn't perfectly merged.  This question is not helpful, the `-y` and
`--no-prompt` command-line arguments do not suppress it, and it's tedious to
keep typing "y".


#### Setup for use as a merge tool

There is just one step for setup.

1. Run the following commands:

```
git config --global merge.conflictstyle diff3
git config --global mergetool.prompt false
git config --global merge.tool merge-java
git config --global mergetool.merge-java.cmd 'java-merge-tool.sh ${BASE} ${LOCAL} ${REMOTE} ${MERGED}'
git config --global mergetool.merge-java.trustExitCode true
git config --global merge.tool merge-adjacent
git config --global mergetool.merge-adjacent.cmd 'java-merge-tool.sh --only-adjacent ${BASE} ${LOCAL} ${REMOTE} ${MERGED}'
git config --global mergetool.merge-adjacent.trustExitCode true
```

To take effect only for one repository, replace `--global` by `--local` and run
the commands within the repository.


### How to use as a re-merge tool

Edit your `~/.gitconfig` file as for a merge tool.

To perform a merge, run:

```
git merge [ARGS]
git-mergetool-on-all.sh
```

You can create a shell alias or a git alias to simplify invoking the
re-merge tool.


## Git merge terminology

A **merge driver** is automatically called during `git merge` whenever no
two of {base,parent1,parent2} are the same.  It writes a merged file, which
may or may not contain conflict markers.  The merge drivers in this
repository first call `git merge-file`, then resolve some conflicts left by
`git merge-file`.

A **merge tool** is called manually by the programmer (via `git mergetool`)
after a merge that left conflict markers.  After running `git merge` (and
perhaps manually resolving some of the conflicts), you might run a merge
tool to resolve further conflicts.  For each file that contains conflict
markers, the merge tool runs and observes the base, parent1, parent2, and
the conflicted merge (which the merge tool can overwrite with a new merge
result).  If the merge driver produced a clean merge for a given file, then
the merge tool is not run on the file.

A **re-merge tool** is a merge tool that is run on every file, even ones
for which the merge driver produced a clean merge.  The command
`git-mergetool-on-all.sh` runs a re-merge tool.

A re-merge tool is only necessary for mergers that may re-introduce lines
that were removed in a clean merge.  The [Java
imports](README-java-imports.md) merger is the only example currently.  For
most mergers (other than the Java imports merger), using them as a merge
tool is adequate.

You may wish to use a mergers in this repository as a merge tool or a
re-merge tool, rather than as a merge driver.  The reason is that `git
merge-file` sometimes produces merge conflicts where `git merge` does not
(even with rerere and other `git merge` functionality disabled!).
Therefore, the merge drivers in this repository (which first call `git
merge-file`, then improve the results) may produce suboptimal results.  A
merge tool or re-merge tool lets you use `git merge`, then still use a
merger to improve the results.


## License

This project is distributed under the [MIT license](LICENSE).  One file
uses a different license:
[diff_match_patch.java](src/main/java/name/fraser/neil/plaintext/diff_match_patch.java)
uses the [Apache License, Version
2.0](http://www.apache.org/licenses/LICENSE-2.0), which is compatible with
the MIT license.
