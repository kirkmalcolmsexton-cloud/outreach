#!/usr/bin/env bash
# Canonical implementation: ../../scripts/encrypt-secrets.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../../scripts/encrypt-secrets.sh" "$@"
