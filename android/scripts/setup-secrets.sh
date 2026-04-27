#!/usr/bin/env bash
# Materialize gitignored android/app/google-services.json and Maps keys in android/local.properties
# from one consolidated JSON file (plaintext or age-encrypted). See secrets/outreach-secrets.example.json.
#
# Writes MAPS_API_KEY_DEBUG and MAPS_API_KEY_RELEASE; Gradle buildTypes map debug → DEBUG, release → RELEASE
# (see android/app/build.gradle.kts). Legacy single MAPS_API_KEY is not written—use -P or dual keys in local.properties.
#
# Prerequisites: age, jq; for OUTREACH_SECRETS_PASSPHRASE decryption, expect.
#
# Optional: OUTREACH_EXPORT_PLAINTEXT_JSON=/path
#   After resolving plaintext JSON, copy it to this path (for release-bundle signing scripts). Unset in normal dev use.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [PATH_TO_JSON_OR_AGE]

  Writes:
    ${ANDROID_DIR}/app/google-services.json
    ${ANDROID_DIR}/local.properties (merges MAPS_API_KEY_DEBUG, MAPS_API_KEY_RELEASE; preserves sdk.dir and other keys)

  Default secret file search (when no PATH and OUTREACH_SECRETS_FILE unset):
    1) ${OUTREACH_ROOT}/secrets/outreach-secrets.json.age if it exists
    2) ${OUTREACH_ROOT}/secrets/outreach-secrets.json if present and not the repo template placeholder
    3) ${OUTREACH_ROOT}/secrets/outreach-secrets.json (template / last resort)

  Override with OUTREACH_SECRETS_FILE=/path/to/file or pass PATH as first argument.

  Passphrase for .age files:
    Set OUTREACH_SECRETS_PASSPHRASE, or run in a terminal and enter when age prompts.

  Optional: OUTREACH_EXPORT_PLAINTEXT_JSON=/path
    Copy resolved consolidated JSON to this file (e.g. for android/scripts/build-release-bundle.sh).

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
# silently using placeholder when a real .age also exists in some flows).
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
  bash "${OUTREACH_ROOT}/scripts/decrypt-age-passphrase.sh" "${age_file}" "${TMPJSON}"
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

if [[ -n "${OUTREACH_EXPORT_PLAINTEXT_JSON:-}" ]]; then
  mkdir -p "$(dirname "${OUTREACH_EXPORT_PLAINTEXT_JSON}")"
  cp "${JSON_PATH}" "${OUTREACH_EXPORT_PLAINTEXT_JSON}"
fi

MAPS_KEY_DEBUG="$(jq -r '.development_api_key' "${JSON_PATH}")"
MAPS_KEY_RELEASE="$(jq -r '.release_api_key' "${JSON_PATH}")"

GOOGLE_SERVICES_OUT="${ANDROID_DIR}/app/google-services.json"
LOCAL_PROPS="${ANDROID_DIR}/local.properties"

jq '.google_services' "${JSON_PATH}" > "${GOOGLE_SERVICES_OUT}"

strip_maps_lines() {
  grep -v -E '^[[:space:]]*(MAPS_API_KEY|MAPS_API_KEY_DEBUG|MAPS_API_KEY_RELEASE)=' || true
}

TMP_PROPS="$(mktemp)"
if [[ -f "${LOCAL_PROPS}" ]]; then
  strip_maps_lines < "${LOCAL_PROPS}" > "${TMP_PROPS}"
else
  touch "${TMP_PROPS}"
fi
{
  cat "${TMP_PROPS}"
  printf 'MAPS_API_KEY_DEBUG=%s\n' "${MAPS_KEY_DEBUG}"
  printf 'MAPS_API_KEY_RELEASE=%s\n' "${MAPS_KEY_RELEASE}"
} > "${LOCAL_PROPS}"
rm -f "${TMP_PROPS}"

echo "setup-secrets: wrote ${GOOGLE_SERVICES_OUT}"
echo "setup-secrets: merged MAPS_API_KEY_DEBUG and MAPS_API_KEY_RELEASE into ${LOCAL_PROPS} (debug vs release: Gradle buildTypes)."

if [[ -n "${OUTREACH_EXPORT_PLAINTEXT_JSON:-}" ]]; then
  echo "setup-secrets: exported consolidated JSON to ${OUTREACH_EXPORT_PLAINTEXT_JSON}"
fi

echo "setup-secrets: Google Sign-In: register each machine's debug SHA-1 in Firebase (Project settings → Your Android app → Add fingerprint). Run: (cd \"${ANDROID_DIR}\" && ./gradlew :app:signingReport) — see Variant: debug → SHA1."
