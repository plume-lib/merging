test: test-imports test-annotations

test-adjacent: AdjacentTest1.diff

test-annotations: AnnotationsTest1.diff

test-imports: ImportsTest1.diff ImportsTest2.diff ImportsTest3.diff ImportsTest4.diff ImportsTest5.diff


Adjacent%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@).git-merge
	-git merge-file \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-adjacent \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff

# Don't test swapping A and B, because the algorithm is symmetric.
Annotations%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)Merged.java $(basename $@)Output.java
	../../main/sh/java-merge-tool.sh --only-annotations \
	  $(basename $@)Base.java $(basename $@)A.java $(basename $@)B.java $(basename $@)Output.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@).diff

Imports%.diff:
	@(cd ../../.. && ./gradlew -q assemble)

	cp -f $(basename $@)A.java $(basename $@).git-merge
	-git merge-file \
	  $(basename $@).git-merge $(basename $@)Base.java $(basename $@)B.java

	cp -f $(basename $@)A.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)B.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-forward.diff

	cp -f $(basename $@)B.java $(basename $@)Output.java
	../../main/sh/java-merge-driver.sh --only-imports \
	  $(basename $@)Output.java $(basename $@)Base.java $(basename $@)A.java
	diff -u $(basename $@)Goal.java $(basename $@)Output.java > $(basename $@)-backward.diff

