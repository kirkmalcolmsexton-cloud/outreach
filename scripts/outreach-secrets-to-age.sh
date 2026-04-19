#!/usr/bin/env bash
# Generate secrets/outreach-secrets.json.age from plaintext secrets/outreach-secrets.json.
#
# This is a thin wrapper around android/scripts/encrypt-secrets.sh (age encryption + jq
# validation of maps_api_key and google_services). Prefer running this from the repo root:
#
#   ./scripts/outreach-secrets-to-age.sh
#
# Non-interactive (CI / scripted):
#   export OUTREACH_SECRETS_PASSPHRASE='…'
#   ./scripts/outreach-secrets-to-age.sh
#
# Custom paths (same flags as encrypt-secrets.sh):
#   ./scripts/outreach-secrets-to-age.sh -i path/in.json -o path/out.age
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

exec "${OUTREACH_ROOT}/android/scripts/encrypt-secrets.sh" "$@"
