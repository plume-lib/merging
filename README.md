# Plume-lib merging:  merge drivers and merge tools

This project contains git merge drivers and git merge tools.  (See
[below](#git-merge-terminology) for definitions of "merge driver" and
"merge tool".)


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
 * `--java-annotations`, `--no-java-annotations`, `--only-java-annotations` [default: enabled]
 * `--java-imports`, `--no-java-imports`, `--only-java-imports` [default: enabled]
 * `--version-numbers`, `--no-version-numbers`, `--only-version-numbers` [default: enabled]

Unfortunately, git does not permit the user to specify command-line
arguments to be passed to a merge driver or merge tool.  See below for how
to define different merge drivers and merge tools that pass different
command-line arguments.


## How to use

You can use the mergers in this repository in three ways.

 * Using them as [**merge drivers**](#how-to-use-as-a-merge-driver) is most
   convenient, because you don't have to remember to issue any commands.

 * Using them as [**re-merge tools**](#how-to-use-as-a-re-merge-tool) leads
   to the best merge results; see
   [below](#why-to-use-a-re-merge-tool-rather-than-a-merge-driver) for an
   explanation.

 * Using them as [**merge tools**](#how-to-use-as-a-merge-tool) is not
   recommended, because a merge tool requires too much user interaction for
   what should be an automated process.


### Common setup

0. You must have Java 17 or later installed.
   Either the `JAVA_HOME` or `JAVA17_HOME` environment variable
   must be set to it.

1. Clone this repository.

2. In the top level of this repository, run either `./gradlew
nativeCompile` (if you are using GraalVM) or `./gradlew shadowJar` (if you
are using any other JVM).  Using `nativeCompile` is recommended, because it
produces a binary that runs much faster than Java `.class` files do.

3. Put directory `.../merging/src/main/sh/` on your PATH,
adjusting "..." according to where you cloned this repository.
(Or, use the absolute pathname in uses of `*.sh` files below.)
After changing one of your dotfiles to set PATH, you may need to log out
and log back in again to have the change take effect.


### How to use as a merge driver

After performing the following steps, git will automatically use the merge
driver for **every merge**.

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

You can define additional merge drivers that pass different sets of
arguments, beyond the `merge-java` and `merge-adjacent` merge drivers
defined below.

2. In a gitattributes file, add:

```
*.java merge=merge-java
```

or

```
* merge=merge-adjacent
```

To enable the merge driver for a single repository, add the above text to
the repository's `.gitattributes` file.  (Or to its `.git/info/attributes`
file, in which case it won't be committed with the project.)

To enable the merge driver for all repositories, add the above text to your
user-level gitattributes file.  The user-level gitattributes file is by
default `$XDG_CONFIG_HOME/git/attributes`.  You can change the user-level
file to be `~/.gitattributes` by running the following command, once ever
per computer:  `git config --global core.attributesfile '~/.gitattributes'`


### How to use as a re-merge tool

See [below](#setup-for-use-as-a-merge-tool-or-re-merge-tool) for setup.

**To perform a merge**, run:

```
git merge [ARGS]
git-mergetool.sh --all [--tool=merge-java]
```

(You can omit the `--tool=...` command-line argument if you have only set
up one merge tool.)

Or, **after a git merge that leaves conflicts**, run:

```
git-mergetool.sh --all [--tool=merge-java]
```

You can create a shell alias or a git alias that first runs `git merge`,
then runs `git-mergetool.sh --all`.


#### Setup for use as a merge tool or re-merge-tool

There is just one step for setup.

1. Run the following commands to edit your `~/.gitconfig` file.

```
git config --global merge.conflictstyle diff3
git config --global mergetool.prompt false
git config --global merge.tool merge-java
git config --global mergetool.merge-java.cmd 'java-merge-tool.sh ${LOCAL} ${BASE} ${REMOTE} ${MERGED}'
git config --global mergetool.merge-java.trustExitCode true
git config --global merge.tool merge-adjacent
git config --global mergetool.merge-adjacent.cmd 'java-merge-tool.sh --only-adjacent ${LOCAL} ${BASE} ${REMOTE} ${MERGED}'
git config --global mergetool.merge-adjacent.trustExitCode true
```

To take effect only for one repository, replace `--global` by `--local` and run
the commands within the repository.

You may wish to set up just one merge tool (not two as shown above), so
that you do not have to pass the `--tool=` command-line argument to
`git-mergetool.sh` and `git mergetool`.


### How to use as a merge tool

See [above](#setup-for-use-as-a-merge-tool-or-re-merge-tool) for setup.

**After a git merge that leaves conflicts**, run one of the following commands.
(You can omit the `--tool=...` command-line argument if you have only set
up one merge tool.)

```
git mergetool [--tool=merge-java]
```
or
```
git mergetool [--tool=merge-adjacent]
```

A fundamental limitation of `git mergetool` is that it requires user
interaction in two scenarios (even with the `-y` and `--no-prompt`
command-line arguments!):

 * Whenever a file was not perfectly merged, you need to type `y` to
   continue.  You should choose "y" because the merge tool might have made
   some improvements even if it didn't resolve every conflict, and also
   because you wish to run it on the rest of the files in the repository.

 * Whenever there is a merge-delete conflict, you need to choose among
   "Use (m)odified or (d)eleted file, or (a)bort?".

Instead of `git mergetool`, you can run `git-mergetool.sh`, which
eliminates the need for user interaction.


## Git merge terminology

A **merge driver** is _automatically called_ during `git merge` whenever no
two of {base,version1,version2} are the same.  It writes a merged file, which
may or may not contain conflict markers.  The merge drivers in this
repository first call `git merge-file`, then resolve some conflicts left by
`git merge-file`.

A **merge tool** is _called manually_ by the programmer (via `git mergetool`)
after a merge that left conflict markers.  After running `git merge` (and
perhaps manually resolving some of the conflicts), you might run a merge
tool to resolve further conflicts.  For each file that contains conflict
markers, the merge tool runs and observes the base, version1, version2, and
the conflicted merge (which the merge tool can overwrite with a new merge
result).  If the merge driver produced a clean merge for a given file, then
the merge tool is not run on the file.

A **re-merge tool** is _called manually_ by the programmer
(via `git-mergetool.sh`).
A re-merge tool differs from a merge tool in the following ways:

 * It not require user interaction.  (By contrast, a regular git merge tool
   requires you to press a key for every file that gets merged.)

 * With the `--all` command-line argument, it is run on every file that
   differed between the two versions being merged -- even ones for which
   the merge driver produced a clean merge.  This feature is is only
   necessary for mergers that may re-introduce lines that were removed in a
   clean merge.  The [Java imports](README-java-imports.md) merger is the
   only example currently.  Most mergers (other than the Java imports
   merger) do not require the `--all` command-line argument.

A **merger** is either a merge tool or a merge driver.

A **merge strategy** works on internal git data structures, deciding what
text to hand to a merge driver.  (For example, it detects renames.)
However, if two of {version1,version2,base} are the same, then the merge
strategy makes a decision and the merge driver is never called.  This
repository does not include a merge strategy; the ones built into git are
adequate.


## Why to use a (re-)merge tool rather than a merge driver

You may wish to use a merger in this repository as a re-merge tool, rather
than as a merge driver.  The reason is that `git merge-file` sometimes
produces merge conflicts where `git merge` does not (even with rerere and
other `git merge` functionality disabled!).  Therefore, the merge drivers
in this repository (which first call `git merge-file`, then improve the
results) may produce suboptimal results.  A (re-)merge tool lets you use
`git merge`, then still use a merger to improve the results.


## License

This project is distributed under the [MIT license](LICENSE).  One file
uses a different license:
[diff_match_patch.java](src/main/java/name/fraser/neil/plaintext/diff_match_patch.java)
uses the [Apache License, Version
2.0](http://www.apache.org/licenses/LICENSE-2.0), which is compatible with
the MIT license.
