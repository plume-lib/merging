public class Backup {

<<<<<<< OURS
  @Deprecated public int count = 0;
||||||| BASE
  public int count = 0;
=======
  public final int count = 0;
>>>>>>> THEIRS

  void method() {}

  void other() {}

<<<<<<< OURS
  public String name = "left";
||||||| BASE
  public String name = "base";
=======
  public String name = "right";
>>>>>>> THEIRS
}
