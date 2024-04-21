# Java annotations

This merger resolves conflicts in favor of retaining a
Java annotation, when the only textual difference is in annotations.

Suppose that a git merge yielded a conflict in one of your `.java` files:

```
<<<<<<< OURS
  private long foo(byte[] bytes, @NonNegative int length) {
||||||| BASE
  public long foo(byte[] bytes, int length) {
=======
  private long foo(byte[] bytes, int length) {
>>>>>>> THEIRS
```

The Java annotation merger would resolve the conflict as:

```
  private long foo(byte[] bytes, @NonNegative int length) {
```
