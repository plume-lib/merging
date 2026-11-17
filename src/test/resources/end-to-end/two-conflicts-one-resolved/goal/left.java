class C {
<<<<<<< OURS
  void m(@Nullable String s) {}
||||||| BASE
  void m(String s) {}
=======
  void m(@NonNull String s) {}
>>>>>>> THEIRS

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
