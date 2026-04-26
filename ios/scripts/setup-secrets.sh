#!/usr/bin/env bash
# Materialize gitignored ios/Outreach/Outreach/GoogleService-Info.plist from the same consolidated
# JSON as android/scripts/setup-secrets.sh (plain or .age-encrypted). See secrets/outreach-secrets.example.json.
#
# The plist is optional: if not present in JSON, the script still succeeds and prints a notice (Android-only teams).
#
# iOS data in JSON (first match wins):
#   - google_service_info_plist_base64 — recommended; one line:  base64 -i GoogleService-Info.plist
#   - google_service_info_plist — full XML (escaped newlines in JSON) if you do not use base64
#
# Prerequisites: age, jq; for OUTREACH_SECRETS_PASSPHRASE decryption, expect (same as Android).
# Optional: pass PATH_TO_JSON_OR_AGE; else same search as setup-secrets.sh (Android) under repo secrets/.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ANDROID_SCRIPT_DIR="${OUTREACH_ROOT}/android/scripts"
PLIST_OUT="${IOS_DIR}/Outreach/Outreach/GoogleService-Info.plist"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [PATH_TO_JSON_OR_AGE]

  Writes (when the consolidated JSON includes iOS plist data):
    ${PLIST_OUT}

  Secret file search (when no path and OUTREACH_SECRETS_FILE unset) matches
  android/scripts/setup-secrets.sh:
    1) ${OUTREACH_ROOT}/secrets/outreach-secrets.json.age
    2) ${OUTREACH_ROOT}/secrets/outreach-secrets.json (non-placeholder, else template)
    3) ${OUTREACH_ROOT}/secrets/outreach-secrets.json (last resort)

  Passphrase for .age: set OUTREACH_SECRETS_PASSPHRASE, or run interactively.

  After writing the plist, update URL scheme in ios/.../Info.plist to match
  REVERSED_CLIENT_ID (see docs/google-oauth-checklist.md §7).
EOF
}

if [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

command -v jq >/dev/null || { echo "setup-ios-secrets: install jq" >&2; exit 1; }
command -v age >/dev/null || { echo "setup-ios-secrets: install age — https://github.com/FiloSottile/age" >&2; exit 1; }

# Same placeholder detection as android/scripts/setup-secrets.sh
secrets_file_is_example_placeholder() {
  local f="$1"
  [[ -f "$f" ]] || return 1
  jq -e '
    ((.google_services.project_info.project_id? // "") == "outreach-placeholder")
    or (((.google_services.client[0].api_key // [])[0].current_key? // "") == "AIzaSyPlaceholderReplaceForProd")
  ' "$f" >/dev/null 2>&1
}

resolve_input() {
  if [[ -n "${1:-}" ]]; then
    echo "$1"
    return
  fi
  if [[ -n "${OUTREACH_SECRETS_FILE:-}" ]]; then
    echo "${OUTREACH_SECRETS_FILE}"
    return
  fi
  local age_file="${OUTREACH_ROOT}/secrets/outreach-secrets.json.age"
  local json_file="${OUTREACH_ROOT}/secrets/outreach-secrets.json"
  if [[ -f "${age_file}" ]]; then
    echo "${age_file}"
    return
  fi
  if [[ -f "${json_file}" ]] && ! secrets_file_is_example_placeholder "${json_file}"; then
    echo "${json_file}"
    return
  fi
  if [[ -f "${json_file}" ]]; then
    echo "${json_file}"
    return
  fi
  echo ""
}

decrypt_age_to_tmp() {
  local age_file="$1"
  local tmp
  tmp="$(mktemp -t outreach-secrets.XXXXXX.json)"
  bash "${ANDROID_SCRIPT_DIR}/decrypt-age-passphrase.sh" "${age_file}" "${tmp}"
  echo "${tmp}"
}

INPUT="$(resolve_input "${1:-}")"
if [[ -z "${INPUT}" ]]; then
  echo "setup-ios-secrets: no secrets file found. Place outreach-secrets.json.age or outreach-secrets.json under ${OUTREACH_ROOT}/secrets/ or pass a path." >&2
  exit 1
fi
[[ -f "${INPUT}" ]] || { echo "setup-ios-secrets: not a file: ${INPUT}" >&2; exit 1; }

JSON_PATH=""
TMPJSON=""
cleanup() {
  if [[ -n "${TMPJSON}" && -f "${TMPJSON}" ]]; then
    rm -f "${TMPJSON}"
  fi
}
trap cleanup EXIT

if [[ "${INPUT}" == *.age ]]; then
  TMPJSON="$(decrypt_age_to_tmp "${INPUT}")"
  JSON_PATH="${TMPJSON}"
else
  JSON_PATH="${INPUT}"
fi

jq empty "${JSON_PATH}" || { echo "setup-ios-secrets: invalid JSON: ${INPUT}" >&2; exit 1; }

B64="$(jq -r '.google_service_info_plist_base64 // ""' "${JSON_PATH}" | tr -d ' \n' | tr -d '\r')"
XML_RAW="$(jq -r '.google_service_info_plist // ""' "${JSON_PATH}")"

write_plist_from_bytes() {
  local dest="$1"
  mkdir -p "$(dirname "${dest}")"
  cat > "${dest}"
}

WROTE=0
if [[ -n "${B64}" ]]; then
  if printf '%s' "${B64}" | base64 -d > "${PLIST_OUT}" 2>/dev/null; then
    :
  elif [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/bin/base64 >/dev/null; then
    printf '%s' "${B64}" | /usr/bin/base64 -D > "${PLIST_OUT}"
  else
    echo "setup-ios-secrets: could not base64-decode (install GNU coreutils or use macOS /usr/bin/base64)." >&2
    exit 1
  fi
  WROTE=1
elif [[ -n "${XML_RAW}" && "${XML_RAW}" != "null" ]]; then
  # Trim: jq may return "null" string for empty
  if echo "${XML_RAW}" | grep -q '<plist\|<?xml'; then
    printf '%s' "${XML_RAW}" > "${PLIST_OUT}"
    WROTE=1
  fi
fi

if [[ "${WROTE}" -eq 0 ]]; then
  echo "setup-ios-secrets: no .google_service_info_plist_base64 or .google_service_info_plist in JSON; skipped (optional for iOS)."
  exit 0
fi

if command -v plutil >/dev/null 2>&1; then
  plutil -lint "${PLIST_OUT}" || {
    echo "setup-ios-secrets: plutil reported an issue with ${PLIST_OUT}" >&2
    exit 1
  }
fi

echo "setup-ios-secrets: wrote ${PLIST_OUT}"
echo "setup-ios-secrets: ensure URL scheme in Outreach/Info.plist matches REVERSED_CLIENT_ID in that plist (docs/google-oauth-checklist.md §7)."
