# merge-tools

This project contains git merge drivers and git merge tools.

Currently they only work on Java files.

Currently they are relatively slow:  about 1/3 second per Java file that was
modified in both versions to be merged.  Most merges involve few Java files that
were modified in both versions, but if there are many, the merge will be slow.
For example, merging 300 files (that were modified in both versions) takes about
a minute and a half.


## Features

* [Java imports](README-java-imports.md):  This handles conflicts in `import`
statements, keeping all the necessary imports.  It also prevents a merge from
removing a needed `import` statement, even if the merge would be clean.

* [Java annotations](README-java-annotations.md):  This resolves conflicts in
favor of retaining a Java annotation, when the only textual difference is in
annotations.


## How to use


### Common setup

0. You must have Java 17 or later installed.

1. Clone this repository.

2. In the top level of this repository, run: `./gradlew shadowJar`

3. Put directory `.../merge-tools/src/main/sh/` on your PATH,
adjusting "..." according to where you cloned this repository.
(Or, use the absolute pathname in uses of `*.sh` files below.)


### How to use as a merge driver

After performing the following steps, git will automatically use this merge
driver whenever you do a git merge of Java files.

1. In your `~/.gitconfig` file, add:

```
[merge "merge-java"]
  name = Merge Java files
  driver = java-merge-driver.sh "%A" "%O" "%B"
```

2. In a gitattributes file, add:

```
*.java merge=merge-java
```

To enable this for a single repository, add this to the repository's `.gitattributes` file.

To enable this for all repositories, add this to your your user-level
gitattributes file.  The user-level gitattributes file is by default
`$XDG_CONFIG_HOME/git/attributes`.  You can change the user-level file to be
`~/.gitattributes` by running the following command, once ever per computer:
`git config --global core.attributesfile '~/.gitattributes'`


### How to use as a merge tool

First, do the setup just below.

Run the following after you do a git merge that leaves conflicts:

```
yes | git mergetool --tool=merge-java
```

The reason for `yes |` is that `git mergetool` stops and asks after each file
that wasn't perfectly merged.  This question is not helpful, the `-y`
command-line argument does not suppress it, and it's tedious to keep typing
"y".


#### Setup for use as a merge tool

There is just one step for setup.

1. In your `~/.gitconfig` file, add:

```
[merge]
# Show original in addition to the two conflicting edits.
  conflictstyle = zdiff3

[merge]
  tool = merge-java

[mergetool.merge-java]
        cmd = java-merge-tool.sh
        trustExitCode = true
```


## Git merge terminology

A **merge driver** is automatically called during `git merge` whenever no two of
{base,edit1,edit2} are the same.  It observes the original version of the base
file and the two edited files.  It writes a merged file, which may or may not
contain conflict markers.

A **merge tool** is called manually by the programmer.  For each file that
contains conflict markers, the merge tool runs and observes the base, edit1,
edit2, and the conflicted merge (which the merge tool can overwrite with a new
merge result).  If the merge driver produced a clean merge for a given file,
then the merge tool is not run on the file.

After running `git merge`, you might run a merge tool to resolve further
conflicts (possibly after manually resolving some of the conflicts).


## License

This project is distributed under the [MIT license](LICENSE).  One file
uses a different license:
[diff_match_patch.java](src/main/java/name/fraser/neil/plaintext/diff_match_patch.java)
uses the [Apache License, Version
2.0](http://www.apache.org/licenses/LICENSE-2.0), which is compatible with
the MIT license.
