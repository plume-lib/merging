package name.fraser.neil.plaintext;

public class DmpLibraryTest {

  String test1base =
      """
    a
    b
    c
    d
    """;
  String test1a =
      """
    a
    bleft1
    bleft2
    bleft3
    c
    d
    """;
  String test1b =
      """
    a
    b
    d
    """;
  String test1goal =
      """
    a
    bleft1
    bleft2
    bleft3
    d
    """;

  String test2base =
      """
    a
    b
    c
    """;
  String test2a =
      """
    a
    bleft1
    bleft2
    bleft3
    c
    """;
  String test2b =
      """
    aright
    b
    cright
    """;
  String test2goal =
      """
    aright
    bleft1
    bleft2
    bleft3
    cright
    """;

  String test3base =
      """
    a
    b
    c
    d
    """;
  String test3a =
      """
    a
    d
    """;
  String test3b =
      """
    a
    b
    newline
    c
    d
    """;
  String test3goal =
      """
    GOAL IS A CONFLICT
    a
    b
    c
    d
    """;
}
