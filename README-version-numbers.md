# Version numbers

This merger resolves conflicts that result from updates to version numbers.

## Example

Suppose that git merge yields a conflict like the following:

```
  // Checker Framework pluggable type-checking
<<<<<<< OURS
  id 'org.checkerframework' version '0.6.38'
||||||| BASE
  id 'org.checkerframework' version '0.6.37'
=======
  id 'org.checkerframework' version '0.6.39'
>>>>>>> THEIRS
}
```

The version-numbers merger would resolve the conflict as:

```
  // Checker Framework pluggable type-checking
  id 'org.checkerframework' version '0.6.39'
}
```
