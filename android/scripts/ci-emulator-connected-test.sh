#!/usr/bin/env bash
# Back-compat; CI runs `build.sh` directly — see .github/workflows/android.yml
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${HERE}/build.sh" ci connected-mock
