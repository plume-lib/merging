class C {
<<<<<<< OURS
  @SuppressWarnings({"unchecked", "rawtypes"}) void m(String s) {}
||||||| BASE
  void m(String s) {}
=======
  void m(@Named("x") String s) {}
>>>>>>> THEIRS
}
