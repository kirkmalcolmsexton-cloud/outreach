#!/usr/bin/env bash
# Merge android/app/google-services.json into secrets/outreach-secrets.json by replacing the top-level
# "google_services" object. All other keys (Maps keys, signing, iOS, …) stay unchanged.
#
# Prerequisites: jq; plaintext secrets/outreach-secrets.json must already exist (bootstrap from
# outreach-secrets.example.json if needed).
#
# Workflow: download google-services.json from Firebase → save/replace android/app/google-services.json → run:
#   bash android/scripts/merge-google-services-into-secrets.sh
#
# Next steps:
#   bash scripts/encrypt-secrets.sh
#   bash android/scripts/setup-secrets.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GOOGLE_JSON="${REPO_ROOT}/android/app/google-services.json"
OUTREACH_JSON="${REPO_ROOT}/secrets/outreach-secrets.json"

usage() {
  cat >&2 <<'EOF'
Merge android/app/google-services.json into secrets/outreach-secrets.json (.google_services only).

  1. Download google-services.json from Firebase (Android app org.outreach.app).
  2. Copy it to: android/app/google-services.json
  3. Run (from repo root): bash android/scripts/merge-google-services-into-secrets.sh

No arguments. Then: bash scripts/encrypt-secrets.sh && bash android/scripts/setup-secrets.sh
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "$(basename "$0"): no arguments expected; place Firebase config at android/app/google-services.json" >&2
  usage
  exit 2
fi

command -v jq >/dev/null || {
  echo "$(basename "$0"): install jq" >&2
  exit 1
}

[[ -f "${GOOGLE_JSON}" ]] || {
  echo "$(basename "$0"): missing ${GOOGLE_JSON}" >&2
  echo "  Copy your Firebase-downloaded google-services.json to that path, then re-run." >&2
  exit 1
}
[[ -f "${OUTREACH_JSON}" ]] || {
  echo "$(basename "$0"): missing ${OUTREACH_JSON}" >&2
  echo "  Copy secrets/outreach-secrets.example.json and fill Maps keys first, or restore from backup." >&2
  exit 1
}

jq empty "${GOOGLE_JSON}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): invalid JSON: ${GOOGLE_JSON}" >&2
  exit 1
}
jq -e '(.project_info | type == "object") and (.client | type == "array")' "${GOOGLE_JSON}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): ${GOOGLE_JSON} does not look like Firebase google-services.json (need .project_info and .client)." >&2
  exit 1
}

OUT_DIR="$(cd "$(dirname "${OUTREACH_JSON}")" && pwd)"
OUT_BASE="$(basename "${OUTREACH_JSON}")"
TMP="$(mktemp "${OUT_DIR}/.${OUT_BASE}.XXXXXX.tmp")"
cleanup() {
  [[ -n "${TMP:-}" && -f "${TMP}" ]] && rm -f "${TMP}"
}
trap cleanup EXIT

jq --slurpfile gs "${GOOGLE_JSON}" '.google_services = $gs[0]' "${OUTREACH_JSON}" > "${TMP}"

jq empty "${TMP}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): merged JSON is invalid" >&2
  exit 1
}
jq -e '.development_api_key | type == "string" and length > 0' "${TMP}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): merged file must still have non-empty .development_api_key (see encrypt-secrets validation)." >&2
  exit 1
}
jq -e '.release_api_key | type == "string" and length > 0' "${TMP}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): merged file must still have non-empty .release_api_key" >&2
  exit 1
}
jq -e '.google_services | type == "object"' "${TMP}" >/dev/null 2>&1 || {
  echo "$(basename "$0"): merged file must have .google_services object" >&2
  exit 1
}

mv "${TMP}" "${OUTREACH_JSON}"
trap - EXIT

echo "$(basename "$0"): merged ${GOOGLE_JSON} → ${OUTREACH_JSON} (.google_services)"
echo "  Next: OUTREACH_SECRETS_PASSPHRASE=… bash scripts/encrypt-secrets.sh"
echo "  Then: bash android/scripts/setup-secrets.sh"
