#!/bin/bash

# Usage: .github/gradlew-retry.sh [GRADLE-ARGUMENT...]
#
# Run ./gradlew with the given arguments.  If that fails because Gradle could not resolve a
# dependency that a retry might resolve, wait, then run ./gradlew again, up to 2 more times.  Exit
# with the status of the last run of ./gradlew.  Other failures, such as a compilation error or a
# test failure, are not retried.
#
# A repository such as Maven Central sometimes transiently fails to serve an artifact that it
# holds, for example by returning 404 for a minute or more.  A dependency that does not exist, such
# as a mistyped version, also yields 404; to avoid pointless retries in that case, this script
# retries a 404 only if the artifact exists, according to the URL where Gradle looked for it or a
# Google-hosted mirror of Maven Central.  This script consults the mirror only to make that
# decision; Gradle never downloads from it.

set -o pipefail

# The prefix of each Maven Central URL that Gradle reports, and the corresponding prefix on the
# mirror.
central=https://repo.maven.apache.org/maven2/
mirror=https://maven-central.storage-download.googleapis.com/maven2/

log=$(mktemp)

# Return 0 (true) if the given URL serves a file.
url_exists() {
  curl --silent --fail --head --location --max-time 20 --output /dev/null "$1"
}

# Return 0 (true) if the run of Gradle that wrote $log failed in a way that a retry might fix.
retry_might_help() {
  if ! grep -qE 'Could not resolve all |Plugin \[id: .*\] was not found' "$log"; then
    return 1
  fi
  # Gradle lists the URLs at which it did not find a module, one per line, as in
  # "       - https://repo.maven.apache.org/maven2/org/example/x/1.0/x-1.0.pom".
  local urls
  urls=$(sed -n 's/^ *- \(https:\/\/[^ ]*\)$/\1/p' "$log" | sort -u)
  # A failure other than a 404, such as a timeout or a 5xx status, does not list URLs.
  if [ -z "$urls" ] || grep -qE 'Could not (GET|HEAD) ' "$log"; then
    return 0
  fi
  local url
  for url in $urls; do
    if url_exists "$url"; then
      echo "gradlew-retry.sh: $url exists."
      return 0
    fi
    case "$url" in
      "$central"*)
        if url_exists "$mirror${url#"$central"}"; then
          echo "gradlew-retry.sh: $url exists on a mirror of Maven Central."
          return 0
        fi
        ;;
    esac
  done
  echo "gradlew-retry.sh: Not retrying, because no missing artifact exists at a URL that Gradle"
  echo "  reported or on a mirror of Maven Central."
  return 1
}

# The number of seconds to wait before each retry.
for delay in 60 180; do
  ./gradlew "$@" 2>&1 | tee "$log"
  status=$?
  if [ "$status" -eq 0 ] || ! retry_might_help; then
    exit "$status"
  fi
  echo "gradlew-retry.sh: Gradle could not resolve a dependency; retrying in $delay seconds."
  sleep "$delay"
done
exec ./gradlew "$@"
