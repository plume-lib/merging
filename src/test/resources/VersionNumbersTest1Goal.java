Here are some version numbers:

identical:
1.2.3 z

b:
5.6.7 z

c:
2.0.0 z

d:
2.2.4 z

e:
143.456.789 z

f:
123.456.789 z

g:
123.456.789 z

conflict:
<<<<<<< OURS
1.4.3 z
||||||| BASE
1.2.3 z
=======
1.2.4 z
>>>>>>> THEIRS

conflict2:
<<<<<<< OURS
2.2.3 z
||||||| BASE
1.2.3 z
=======
1.2.4 z
>>>>>>> THEIRS

Not a version number:
<<<<<<< OURS
2 z
||||||| BASE
1 z
=======
3 z
>>>>>>> THEIRS

Also not a version number:
<<<<<<< OURS
2. z
||||||| BASE
1. z
=======
3. z
>>>>>>> THEIRS

end of file.
