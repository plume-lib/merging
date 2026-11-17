public class NotAnnotation {

<<<<<<< OURS
  public String process(Object arg) {
||||||| BASE
  public String process(String arg) {
=======
  public String process(@Nullable String arg) {
>>>>>>> THEIRS
    return arg;
  }
}
