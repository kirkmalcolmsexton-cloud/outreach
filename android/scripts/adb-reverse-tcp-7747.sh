#!/usr/bin/env bash
# Map device localhost:7747 → host localhost:7747 (USB debugging).
# Same as: adb reverse tcp:7747 tcp:7747
# Optional: ANDROID_SERIAL selects the device; ANDROID_HOME adds platform-tools to PATH.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
exec "${REPO_ROOT}/scripts/adb-reverse-debug-ingest.sh"
