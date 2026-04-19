#!/usr/bin/env bash
# Materialize gitignored android/app/google-services.json and MAPS_API_KEY in android/local.properties
# from one consolidated JSON file (plaintext or age-encrypted). See secrets/outreach-secrets.example.json.
#
# Prerequisites: age, jq; for OUTREACH_SECRETS_PASSPHRASE decryption, expect.
#
# Usage:
#   ./scripts/setup-secrets.sh [PATH]
#   Default input: see resolve_input below (real plaintext overrides committed .age when present).
#   Override with OUTREACH_SECRETS_FILE=/path/to/file

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [PATH_TO_JSON_OR_AGE]

  Writes:
    ${ANDROID_DIR}/app/google-services.json
    ${ANDROID_DIR}/local.properties (merges MAPS_API_KEY; preserves sdk.dir and other keys)

  Default secret file search (when no PATH and OUTREACH_SECRETS_FILE unset):
    1) ${OUTREACH_ROOT}/secrets/outreach-secrets.json if present and not the repo template placeholder
    2) ${OUTREACH_ROOT}/secrets/outreach-secrets.json.age if it exists
    3) ${OUTREACH_ROOT}/secrets/outreach-secrets.json (template / last resort)

  Override with OUTREACH_SECRETS_FILE=/path/to/file or pass PATH as first argument.

  Passphrase for .age files:
    Set OUTREACH_SECRETS_PASSPHRASE, or run in a terminal and enter when age prompts.

  Which Maps key merges into MAPS_API_KEY / local.properties:
    OUTREACH_MAPS_KEY_FIELD=development_api_key (default) or release_api_key

EOF
}

if [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

command -v jq >/dev/null || {
  echo "setup-secrets: install jq" >&2
  exit 1
}
command -v age >/dev/null || {
  echo "setup-secrets: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}

# Exit 0 if FILE matches secrets/outreach-secrets.example.json template (still not safe to ship, but avoids
# silently preferring plaintext over .age when the dev only copied the example).
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
  if [[ -f "${json_file}" ]] && ! secrets_file_is_example_placeholder "${json_file}"; then
    echo "${json_file}"
    return
  fi
  if [[ -f "${age_file}" ]]; then
    echo "${age_file}"
    return
  fi
  if [[ -f "${json_file}" ]]; then
    echo "${json_file}"
    return
  fi
  echo ""
}

INPUT="$(resolve_input "${1:-}")"
if [[ -z "${INPUT}" ]]; then
  echo "setup-secrets: no secrets file found. Place outreach-secrets.json.age or outreach-secrets.json under ${OUTREACH_ROOT}/secrets/ or pass a path." >&2
  exit 1
fi

[[ -f "${INPUT}" ]] || {
  echo "setup-secrets: not a file: ${INPUT}" >&2
  exit 1
}

TMPJSON=""
cleanup() {
  if [[ -n "${TMPJSON}" ]] && [[ -f "${TMPJSON}" ]]; then
    rm -f "${TMPJSON}"
  fi
}
trap cleanup EXIT

decrypt_age_to_tmp() {
  local age_file="$1"
  TMPJSON="$(mktemp -t outreach-secrets.XXXXXX.json)"
  bash "${SCRIPT_DIR}/decrypt-age-passphrase.sh" "${age_file}" "${TMPJSON}"
}

JSON_PATH=""
if [[ "${INPUT}" == *.age ]]; then
  decrypt_age_to_tmp "${INPUT}"
  JSON_PATH="${TMPJSON}"
else
  JSON_PATH="${INPUT}"
fi

jq empty "${JSON_PATH}" || {
  echo "setup-secrets: invalid JSON: ${INPUT}" >&2
  exit 1
}
jq -e '.development_api_key | type == "string" and length > 0' "${JSON_PATH}" >/dev/null 2>&1 || {
  echo "setup-secrets: .development_api_key must be a non-empty string" >&2
  exit 1
}
jq -e '.release_api_key | type == "string" and length > 0' "${JSON_PATH}" >/dev/null 2>&1 || {
  echo "setup-secrets: .release_api_key must be a non-empty string" >&2
  exit 1
}
jq -e '.google_services | type == "object"' "${JSON_PATH}" >/dev/null 2>&1 || {
  echo "setup-secrets: .google_services must be an object" >&2
  exit 1
}

if jq -e 'has("android_upload_signing")' "${JSON_PATH}" >/dev/null 2>&1; then
  jq -e '
    .android_upload_signing
    | type == "object"
    and (.key_alias | type == "string" and length > 0)
    and (.keystore_password | type == "string" and length > 0)
    and (.key_password | type == "string" and length > 0)
  ' "${JSON_PATH}" >/dev/null 2>&1 || {
    echo "setup-secrets: when set, .android_upload_signing must include non-empty key_alias, keystore_password, key_password" >&2
    exit 1
  }
fi

MAPS_FIELD="${OUTREACH_MAPS_KEY_FIELD:-development_api_key}"
if [[ "${MAPS_FIELD}" != "development_api_key" && "${MAPS_FIELD}" != "release_api_key" ]]; then
  echo "setup-secrets: OUTREACH_MAPS_KEY_FIELD must be development_api_key or release_api_key" >&2
  exit 1
fi

GOOGLE_SERVICES_OUT="${ANDROID_DIR}/app/google-services.json"
LOCAL_PROPS="${ANDROID_DIR}/local.properties"

jq '.google_services' "${JSON_PATH}" > "${GOOGLE_SERVICES_OUT}"
MAPS_KEY="$(jq --arg f "${MAPS_FIELD}" -r '.[$f]' "${JSON_PATH}")"

TMP_PROPS="$(mktemp)"
if [[ -f "${LOCAL_PROPS}" ]]; then
  grep -v '^[[:space:]]*MAPS_API_KEY=' "${LOCAL_PROPS}" > "${TMP_PROPS}" || true
else
  touch "${TMP_PROPS}"
fi
{
  cat "${TMP_PROPS}"
  printf 'MAPS_API_KEY=%s\n' "${MAPS_KEY}"
} > "${LOCAL_PROPS}"
rm -f "${TMP_PROPS}"

echo "setup-secrets: wrote ${GOOGLE_SERVICES_OUT}"
echo "setup-secrets: merged MAPS_API_KEY (${MAPS_FIELD}) into ${LOCAL_PROPS}"
