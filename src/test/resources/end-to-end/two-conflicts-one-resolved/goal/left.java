class C {
  @Deprecated void m(@Nullable String s) {}

  void n() {
<<<<<<< OURS
    x = 2;
||||||| BASE
    x = 1;
=======
    x = 3;
>>>>>>> THEIRS
  }
}
