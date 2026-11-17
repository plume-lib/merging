class C {
<<<<<<< OURS
  @SuppressWarnings({"unchecked", "rawtypes"}) void m(String s) {}
||||||| BASE
  void m(String s) {}
=======
  void m(@Size(min = 1, max = 10) String s) {}
>>>>>>> THEIRS
}
