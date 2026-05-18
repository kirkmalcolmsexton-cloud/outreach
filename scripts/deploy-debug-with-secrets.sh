#!/usr/bin/env bash
# Wrapper: run from repo root as ./scripts/deploy-debug-with-secrets.sh
# Implementation: ../android/scripts/deploy-debug-with-secrets.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../android/scripts/deploy-debug-with-secrets.sh" "$@"
