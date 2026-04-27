#!/usr/bin/env bash
# Canonical: ../android/scripts/build-with-secrets-from-env.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../android/scripts/build-with-secrets-from-env.sh" "$@"
