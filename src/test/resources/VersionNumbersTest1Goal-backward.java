Here are some version numbers:

identical:
a 1.2.3 z

b:
a 5.6.7 z

c:
a 2.0.0 z

d:
a 2.2.4 z

e:
a 143.456.789 z

f:
a 143.456.79 z

g:
a 143.456.79 z

h:
a 133.466.799 mn 193.496.799 z

i:
a 1.4.3 z

j:
a 2.2.3 z

k:
a 143.456.799 z

a number goes down:
<<<<<<< OURS
a 113.456.789 z
||||||| BASE
a 123.456.789 z
=======
a 123.456.799 z
>>>>>>> THEIRS

Not a version number:
<<<<<<< OURS
a 3 z
||||||| BASE
a 1 z
=======
a 2 z
>>>>>>> THEIRS

Longer not a version number:
<<<<<<< OURS
a 939 z
||||||| BASE
a 99 z
=======
a 929 z
>>>>>>> THEIRS

Also not a version number:
<<<<<<< OURS
a 3. z
||||||| BASE
a 1. z
=======
a 2. z
>>>>>>> THEIRS

Still not a version number:
<<<<<<< OURS
a .3 z
||||||| BASE
a .1 z
=======
a .2 z
>>>>>>> THEIRS

end of file.
