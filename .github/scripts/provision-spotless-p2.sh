#!/bin/bash

# Downloads the Groovy-Eclipse formatter that Spotless's `greclipse` step uses.
#
# Spotless downloads Groovy-Eclipse from the P2 repository at
# download.eclipse.org, which is intermittently unavailable; a build that is
# unlucky fails with "Failed to provision P2 dependencies".  This script retries
# that download, so that a transient outage delays the build rather than failing
# it.  Spotless stores the download in ~/.m2/repository/dev/equo/p2-data and
# reads from that cache in preference to the network, so the rest of the build,
# and any later build that restores that cache, does not contact eclipse.org.
#
# This script fails only if the download failed.  Any other failure of
# `spotlessCheck`, such as a formatting violation, is left for the main build to
# report.

set -euo pipefail

attempts=5
log=$(mktemp)

for attempt in $(seq $attempts); do
  if ./gradlew spotlessCheck 2>&1 | tee "$log"; then
    exit 0
  fi
  if ! grep -q 'Failed to provision P2 dependencies' "$log"; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ]; then
    echo "Download from download.eclipse.org failed; retrying in $((attempt * 30)) seconds."
    sleep $((attempt * 30))
  fi
done

echo "Could not download from download.eclipse.org in $attempts attempts."
exit 1
