# Java annotations

This merger resolves conflicts in favor of retaining a
Java annotation, when the only textual difference is in annotations.

Suppose that a git merge yielded a conflict in one of your `.java` files:

```
<<<<<<< HEAD
  private long foo(byte[] bytes, @NonNegative int length) {
||||||| 8ff79e15e
  public long foo(byte[] bytes, int length) {
=======
  private long foo(byte[] bytes, int length) {
>>>>>>> 0a17f4a429323589396c38d8ce75ca058faa6c64
```

Then running `git mergetool --tool=annos` would resolve the result, rewriting your `.java` file to contain:

```
  private long foo(byte[] bytes, @NonNegative int length) {
```
