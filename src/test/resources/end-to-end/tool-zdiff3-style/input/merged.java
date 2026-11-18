class C {
<<<<<<< OURS
  @Deprecated void m(String s) {}
||||||| BASE
  void m(String s) {}
=======
  void m(@Nullable String s) {}
>>>>>>> THEIRS
  int x;
}
