#!/usr/bin/env bash
# Map device localhost:7747 → host localhost:7747 for debug NDJSON ingest (physical USB).
# See README — Physical device testing / CLI scripts.

set -euo pipefail

if [[ -n "${ANDROID_HOME:-}" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
fi

"${ADB[@]}" reverse tcp:7747 tcp:7747
