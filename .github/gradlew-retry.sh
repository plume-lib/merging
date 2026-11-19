#!/bin/bash

# Usage: .github/gradlew-retry.sh [GRADLE-ARGUMENT...]
#
# Run ./gradlew with the given arguments.  If that fails because Gradle could not resolve a
# dependency, wait, then run ./gradlew once more with --refresh-dependencies.  Exit with the status
# of the last run of ./gradlew.  Other failures, such as a compilation error or a test failure, are
# not retried.
#
# A repository such as Maven Central sometimes transiently fails to serve an artifact that it
# holds, for example by returning 404.  Gradle caches the result that a module is missing and does
# not query the repositories for that module again until the cache entry expires, so a retry
# without --refresh-dependencies would fail the same way.

set -o pipefail

log=$(mktemp)
./gradlew "$@" 2>&1 | tee "$log"
status=$?
if [ "$status" -eq 0 ] || ! grep -qE 'Could not resolve all |Plugin \[id: .*\] was not found' "$log"; then
  exit "$status"
fi

echo "gradlew-retry.sh: Gradle could not resolve a dependency; retrying in 60 seconds."
sleep 60
exec ./gradlew --refresh-dependencies "$@"
