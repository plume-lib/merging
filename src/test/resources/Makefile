test: test-adjacent test-annotations test-imports test-version-numbers

test-adjacent: AdjacentTest1.diff AdjacentTest2.diff AdjacentTest3.diff AdjacentTest4.diff

test-annotations: AnnotationsTest1.diff

test-imports: ImportsTest1.diff ImportsTest2.diff ImportsTest3.diff ImportsTest4.diff ImportsTest5.diff ImportsTest6.diff ImportsTest7.diff ImportsTest8.diff ImportsTest9.diff

test-version-numbers: VersionNumbersTest1.diff

Adjacent%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

# The AnnotationsTest2Merged.java file (for example) is the result of: git merge-file -p AnnotationsTest2A.java AnnotationsTest2Base.java AnnotationsTest2B.java > AnnotationsTest2Merged.java
Annotations%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)Merged.java $(basename $@)Output.java
	../../main/sh/java-merge-tool.sh --only-java-annotations \
	  $(basename $@)Base.java $(basename $@)A.java $(basename $@)B.java $(basename $@)Output.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)Merged.java $(basename $@)Output.java
	../../main/sh/java-merge-tool.sh --only-java-annotations \
	  $(basename $@)Base.java $(basename $@)B.java $(basename $@)A.java $(basename $@)Output.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

Imports%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-java-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-java-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

VersionNumbers%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-version-numbers \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-version-numbers \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

