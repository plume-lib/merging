test: ImportsTest1.diff ImportsTest2.diff ImportsTest3.diff ImportsTest4.diff

%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/merge-java-imports-driver.sh \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/merge-java-imports-driver.sh \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java
