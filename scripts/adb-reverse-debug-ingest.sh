#!/usr/bin/env bash
# Canonical: ../android/scripts/adb-reverse-debug-ingest.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../android/scripts/adb-reverse-debug-ingest.sh" "$@"
