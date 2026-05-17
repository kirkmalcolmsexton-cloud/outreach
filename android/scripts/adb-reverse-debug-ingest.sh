#!/usr/bin/env bash
# Map device localhost:7747 → host localhost:7747 for debug NDJSON ingest (physical USB).
# Canonical path; ../../scripts/adb-reverse-debug-ingest.sh forwards here for backwards compatibility.
# See docs/testing-guide.md — Physical device testing / Debug ingest (port 7747).

set -euo pipefail

if [[ -n "${ANDROID_HOME:-}" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
fi

"${ADB[@]}" reverse tcp:7747 tcp:7747
