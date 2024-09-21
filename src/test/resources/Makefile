test: test-adjacent test-annotations test-imports test-version-numbers

test-adjacent: AdjacentTest1.diff AdjacentTest2.diff AdjacentTest3.diff AdjacentTest4.diff AdjacentTest5.diff AdjacentTest6.diff 

test-annotations: AnnotationsTest1.diff

test-imports: ImportsTest1.diff ImportsTest2.diff ImportsTest3.diff ImportsTest4.diff ImportsTest5.diff \
              ImportsTest6.diff ImportsTest7.diff ImportsTest8.diff ImportsTest9.diff ImportsTest10.diff \
              ImportsTest11.diff ImportsTest12.diff ImportsTest13.diff

test-version-numbers: VersionNumbersTest1.diff

test-defaults: DefaultsTest14.diff

compile:
	@(cd ../../.. && ./gradlew -q assemble)
ifneq ("","$(wildcard ../../../build/native/nativeCompile/plumelib-merge)")
	@(cd ../../.. && ./gradlew -q nativeCompile)
endif

Adjacent%.diff: compile
	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

# The AnnotationsTest2Merged.java file (for example) is the result of: git merge-file -p AnnotationsTest2A.java AnnotationsTest2Base.java AnnotationsTest2B.java > AnnotationsTest2Merged.java
Annotations%.diff: compile
	cp -f $(basename $@)Merged.java $(basename $@)Output.java
	../../main/sh/merge-tool.sh --only-java-annotations \
	  $(basename $@)Base.java $(basename $@)A.java $(basename $@)B.java $(basename $@)Output.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)Merged.java $(basename $@)Output.java
	../../main/sh/merge-tool.sh --only-java-annotations \
	  $(basename $@)Base.java $(basename $@)B.java $(basename $@)A.java $(basename $@)Output.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

Imports%.diff: compile
	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-java-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-java-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

VersionNumbers%.diff: compile

	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-version-numbers \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --only-version-numbers \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi

# This runs the default mergers
Defaults%.diff: compile
	cp -f $(basename $@)A.java $(basename $@).git-merge
	git merge-file --diff3 \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java || true

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh --verbose \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java || true
	if [ -f $(basename $@)Goal-forward.java ] ; then \
	  diff -u $(basename $@)Goal-forward.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff ; \
	fi

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/merge-driver.sh \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java || true
	if [ -f $(basename $@)Goal-backward.java ] ; then \
	  diff -u $(basename $@)Goal-backward.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	else \
	  diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff ; \
	fi


jcardreader:
	test-jcardreader.sh
