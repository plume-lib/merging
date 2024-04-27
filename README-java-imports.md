# Java imports

This merger does a better job merging `import` statements than other tools do.
It handles conflicts in `import` statements, keeping all the necessary imports.
It also prevents a merge from removing a needed `import` statement, even if the
merge would be clean.

If the only conflicts are in import statements, or there are no conflicts, then:
 * Resolve conflicts by unioning the imports, being careful not to
   lose comments that appear within the conflict.
 * Reinster all `import` statements that were removed by a clean
   merge.
 * Remove all unneeded imports.  A needed import is one that is used
   somewhere in the Java file.

(If there are conflicts beyound import statements, such as in code or in
comments, then you should first resolve those other conflicts, then run the Java
imports merger as a merge tool.)

## Example

Here is an example:

```
BASE:          EDIT 1:         EDIT 2:        Remarks (not in either edit):

import A.a;    import A.a;    import A.a;
               import B.b;    import C.c;     different additions; git considers this a conflict
import D.d;    import D.d;    import D.d;
import E.e;                   import E.e;     edit 1 removed, but edit 2 still needs
import F.f;    import F.f;    import F.f;
import G.g;                   import G.g;     edit 1 removed, and edit 2 does not need
import H.h;    import H.h;    import H.h;
import I.i;                                   neither edit needs "I"
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
