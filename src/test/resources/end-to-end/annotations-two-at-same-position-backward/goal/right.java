public class TypeParameter {

<<<<<<< OURS
  public <T extends @UnknownSignedness Object> T[] toArray(T[] array) {
||||||| BASE
  public <T extends Object> T[] toArray(T[] array) {
=======
  public <T extends @Nullable Object> T[] toArray(T[] array) {
>>>>>>> THEIRS
    return array;
  }
}
