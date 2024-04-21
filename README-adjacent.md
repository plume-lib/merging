# Adjacent

This merger resolves conflicts that result from edits on different but adjacent
lines.  By default, git reports such edits as conflicts that must be manually
resolved.

For example, suppose that git merge yields a conflict like the following:

```
a
<<<<<<< OURS
bleft1
bleft2
bleft3
c
||||||| BASE
b
c
=======
b
>>>>>>> THEIRS
d
```

The adjacent merger would resolve the conflict as:

```
a
bleft1
bleft2
bleft3
d
```
