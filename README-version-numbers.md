# Version numbers

This merger resolves conflicts that result from updates to version numbers.
If both edits increase the version number from the base, then the merger
chooses the greater of the version numbers.
A version number is dot-separated numbers, with at least one dot.

We are not aware of any real-world examples where this merger makes a mistake.

## Example

Suppose that git merge yields a conflict like the following:

```diff-fenced
  // Checker Framework pluggable type-checking
<<<<<<< OURS
  id "org.checkerframework" version "0.6.38"
||||||| BASE
  id "org.checkerframework" version "0.6.37"
=======
  id "org.checkerframework" version "0.6.39"
>>>>>>> THEIRS
}
```

The version-numbers merger would resolve the conflict as:

```gradle
  // Checker Framework pluggable type-checking
  id "org.checkerframework" version "0.6.39"
}
```

## Example 2

Given this conflict:

```diff-fenced
  // Checker Framework pluggable type-checking
<<<<<<< OURS
  id "org.checkerframework" version "0.5.99"
||||||| BASE
  id "org.checkerframework" version "0.5.12"
=======
  id "org.checkerframework" version "0.6.02"
>>>>>>> THEIRS
}
```

The version-numbers merger would resolve the conflict as:

```gradle
  // Checker Framework pluggable type-checking
  id "org.checkerframework" version "0.6.02"
}
```
