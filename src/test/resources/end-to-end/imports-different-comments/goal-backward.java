package example;

import java.util.List;
<<<<<<< OURS
// Needed for the set field.
import java.util.Set;
||||||| BASE
=======
// Needed for the map field.
import java.util.Map;
>>>>>>> THEIRS

public class DifferentComments {

  List<String> list;

  Map<String, String> map;

  void method() {}

  void other() {}

  Set<String> set;
}
