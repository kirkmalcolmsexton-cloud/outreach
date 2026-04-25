#!/usr/bin/env bash
# Back-compat name; implementation is `build.sh release-bundle` (one script for all build paths).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${HERE}/build.sh" release-bundle
