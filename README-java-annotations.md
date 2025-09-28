# Java annotations

This merger resolves conflicts in favor of retaining a
Java annotation, when the only textual difference is in annotations.

We are not aware of any real-world examples where this merger makes a mistake.

## Example

Suppose that a git merge yielded a conflict in one of your `.java` files:

```diff-fenced
<<<<<<< OURS
  private long foo(byte[] bytes, @NonNegative int length) {
||||||| BASE
  public long foo(byte[] bytes, int length) {
=======
  private long foo(byte[] bytes, int length) {
>>>>>>> THEIRS
```

The Java annotation merger would resolve the conflict as:

```output
  private long foo(byte[] bytes, @NonNegative int length) {
```

Note that modifiers such as `public` and `private` are merged just as
annotations are.
