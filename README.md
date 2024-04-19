# Plume-lib merging

This project contains git merge drivers and merge tools.

Currently, the only tool is `Merge Java imports".  It does a better
job merging `import` statements than other tools do.  It first uses git's
normal merging.  If there are conflicts outside import blocks, it does nothing.

If the only conflicts are in import statements, or there are no conflicts,
then:
 * It merges any conflicts by unioning the imports.  It is careful not to
   lose comments that appear within the conflict.
 * It adds back in any `import` statements that were removed by a clean
   merge.
 * It removes all unneeded imports.  A needed import is one that is used
   somewhere in the Java file.

Here is an example:

```
BASE:          EDIT 1:         EDIT 2:

import A.a;    import A.a;    import A.a;
               import B.b;    import C.c;     // git considers this a conflict
import D.d;    import D.d;    import D.d;
import E.e;                   import E.e;     // edit 1 removed, but edit 2 still needs
import F.f;    import F.f;    import F.f;
import G.g;                   import G.g;     // edit 1 removed, and edit 2 does not need
import H.h;    import H.h;    import H.h;
import I.i;                                   // neither edit needs "I"
import J.j;    import J.j;    import J.j;
```

`merge-imports` merges the above edits in the way a programmer would want:
retain "B", "C", and "E", but do not retain "G" or "I".

```
MERGED:

import A.a;
import B.b;
import C.c;
import D.d;
import E.e;
import F.f;
import H.h;
import J.j;
```


To use this whenever you do a git merge of Java files:

0. You must have Java 17 or later installed.

1. Clone this repository.

2. In the top level of this repository, run: `./gradlew assemble`

3. Put directory `.../merging/src/main/sh/` on your PATH,
adjusting "..." according to where you cloned this repository.
(Or, use the absolute pathname to the `merge-imports-driver.sh` file below.)

4. In your `~/.gitconfig` file, add:

```
[merge "merge-java-imports"]
        name = Merge Java imports
        driver = merge-java-imports-driver.sh
```

5. In your `~\.gitattributes` file, add:

```
*.java merge=merge-java-imports
```
