# Agent plan: file-based end-to-end tests for plume-lib/merging

**This temporary plan file must be deleted before any pull request is merged.**

Goal: in the git worktree
`/home/mernst/java/plume-lib/merging-fork-mernst-branch-e2e-tests` (branch
`e2e-tests`, forked from upstream's default branch), improve the end-to-end
tests of plume-lib/merging (git merge drivers and merge tools for adjacent
lines, Java imports, Java annotations, and version numbers).  Every test case
must be pure data in files (inputs, command-line arguments, goal files for the
written files, standard output, standard error, and exit status), so that the
same test cases can be run against an implementation in another language.  A
Java JUnit harness runs them against this implementation.  Convert the existing
end-to-end tests (Java-coded `EndToEndTest.java` and the Makefile tests in
`src/test/resources`) into the format and delete the old forms.  Leave genuine
unit tests alone.  Do not change main source code; record bugs as current
behavior with a description that starts with "BUG:".  Commit at each milestone
(including this file); never push, open pull requests, or rewrite history.
Delete this file, and commit the deletion, when every entry is done.  The full
`./gradlew build` must pass, and Markdown must pass `markdownlint-cli2`.

Format (decided): `src/test/resources/end-to-end/CASE/` holds `description.txt`,
`args.txt` (one argument per line; the first is `driver` or `tool`), `input/`
(copied into a fresh working directory), `goal/` (files that differ from
`input/` after the run, or that the run creates), `goal-stdout.txt`,
`goal-stderr.txt` (or `*-prefix.txt` variants), and `goal-status.txt`.
Harness: `EndToEndTest.java`, parameterized over the subdirectories; system
property `e2e.command` (Gradle `-Pe2e.command=...`) runs another program.

## Checklist

- [x] Write the harness (rewrite `EndToEndTest.java`) and the README.
- [x] Convert each old Java-coded test run into a case directory (all pass).
      Still to do:  `cli-verbose-defaults` and `cli-verbose-only-adjacent`.
- [ ] Convert the Makefile tests (`src/test/resources/{Adjacent,Annotations,Imports,VersionNumbers,Defaults,Insertion}Test*`)
      into case directories; delete the Makefile, its README, its resources,
      the `runMakefileTests` Gradle task, and related `.gitignore` lines;
      remove the dead `assertJavaImportsMerger` helpers in `JavaImportsMergerTest.java`.
- [ ] Add new cases: each merger's options, `--verbose`, both modes,
      no conflict, no trailing newline, unresolvable conflict, diff3 vs merge
      conflict style in tool mode, CRLF, `--no-git-merge-file`, error handling.
- [ ] Check `prek.toml` exclusions; check `bin/` (stale Eclipse output?) and
      report on it.
- [ ] `./gradlew spotlessApply build` passes; markdownlint passes.
- [ ] Delete this file; commit.
