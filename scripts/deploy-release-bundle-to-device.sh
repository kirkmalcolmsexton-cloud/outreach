#!/usr/bin/env bash
# Wrapper: run from repo root as ./scripts/deploy-release-bundle-to-device.sh
# Implementation: ../android/scripts/deploy-release-bundle-to-device.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../android/scripts/deploy-release-bundle-to-device.sh" "$@"
