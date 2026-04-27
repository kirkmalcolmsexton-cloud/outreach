#!/usr/bin/env bash
# Canonical: ../android/scripts/encrypt-upload-keystore-age.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../android/scripts/encrypt-upload-keystore-age.sh" "$@"
