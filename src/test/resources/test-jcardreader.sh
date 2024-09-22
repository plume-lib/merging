#!/bin/sh

set -e

# The directory with the plume-lib merging scripts.
SHDIR="$(cd "$(dirname "$0")"/../../../src/main/sh && pwd -P)"

tmpdir=$(mktemp -d)
cd "$tmpdir"
pwd
git clone -q https://github.com/mangstadt/ez-vcard.git
cd ez-vcard
git checkout ea6026ee62cc184db68d841d50d58474fcdf4862
git merge ab2032ca9769d452d4906f51cf56ca7d983a27c4

git config --local merge.conflictstyle diff3
git config --local mergetool.prompt false
git config --local merge.tool plumelib-merge
# shellcheck disable=2016 # quoted expressions
git config --local mergetool.plumelib-merge.cmd "${SHDIR}"/'merge-tool.sh --verbose ${LOCAL} ${BASE} ${REMOTE} ${MERGED}'
git config --local mergetool.plumelib-merge.trustExitCode true

"$SHDIR"/git-mergetool.sh --verbose --all

echo "$tmpdir"/ez-vcard/src/test/java/ezvcard/io/json/JCardReaderTest.java
