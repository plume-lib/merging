public class NotAnnotation {

<<<<<<< OURS
  public String process(@Nullable String arg) {
||||||| BASE
  public String process(String arg) {
=======
  public String process(Object arg) {
>>>>>>> THEIRS
    return arg;
  }
}
