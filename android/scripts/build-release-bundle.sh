#!/usr/bin/env bash
# Build signed release App Bundle (.aab) for Play upload using the same flow as
# .github/workflows/android-release-build.yml.
# Run from repo: bash android/scripts/build-release-bundle.sh
# Or from android/: ./scripts/build-release-bundle.sh
# Prerequisites:
# - JDK 17 (JAVA_HOME)
# - Android SDK: export ANDROID_HOME, or sdk.dir in android/local.properties, or a default SDK under $HOME
# - age, jq, expect (same as CI decrypt/setup flow)
# - OUTREACH_SECRETS_PASSPHRASE set (used to decrypt secrets/outreach-secrets.json.age and repo keystore .age)
# - Signing configured as either:
#   - Repo mode: secrets/upload-keystore.jks.age + android_upload_signing in secrets JSON
#   - Legacy mode: ANDROID_UPLOAD_KEYSTORE_BASE64 + ANDROID_UPLOAD_KEYSTORE_PASSWORD +
#                  ANDROID_UPLOAD_KEY_ALIAS + ANDROID_UPLOAD_KEY_PASSWORD

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="${SCRIPT_DIR}/.."
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ANDROID_DIR}"
# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"

command -v jq >/dev/null || {
  echo "build-release-bundle: install jq" >&2
  exit 1
}
command -v age >/dev/null || {
  echo "build-release-bundle: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}

RUNNER_TEMP="${RUNNER_TEMP:-$(mktemp -d -t outreach-release.XXXXXX)}"
OUTREACH_JSON="${RUNNER_TEMP}/outreach-secrets.json"
UPLOAD_JKS="${RUNNER_TEMP}/outreach-upload.jks"

cleanup() {
  if [[ -n "${_TMP_DIR_CREATED:-}" && "${_TMP_DIR_CREATED}" == "1" && -d "${RUNNER_TEMP}" ]]; then
    rm -rf "${RUNNER_TEMP}"
  fi
}
trap cleanup EXIT
if [[ "${RUNNER_TEMP}" == /tmp/outreach-release.* || "${RUNNER_TEMP}" == /private/tmp/outreach-release.* ]]; then
  _TMP_DIR_CREATED="1"
else
  _TMP_DIR_CREATED="${_TMP_DIR_CREATED:-0}"
fi

if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" && -f "${HOME}/etc/outreach.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "${HOME}/etc/outreach.env"
  set +a
fi

if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
  echo "build-release-bundle: OUTREACH_SECRETS_PASSPHRASE is required (same as CI)." >&2
  echo "Set it directly or add it to ~/etc/outreach.env." >&2
  exit 1
fi

bash "${SCRIPT_DIR}/decrypt-age-passphrase.sh" "${REPO_ROOT}/secrets/outreach-secrets.json.age" "${OUTREACH_JSON}"
jq empty "${OUTREACH_JSON}" >/dev/null

KS_AGE="${REPO_ROOT}/secrets/upload-keystore.jks.age"
repo_ok=false
if [[ -f "${KS_AGE}" ]] && jq -e '
  .android_upload_signing
  | type == "object"
  and (.key_alias | type == "string" and length > 0)
  and (.keystore_password | type == "string" and length > 0)
  and (.key_password | type == "string" and length > 0)
' "${OUTREACH_JSON}" >/dev/null 2>&1; then
  repo_ok=true
fi

legacy_ok=false
if [[ -n "${ANDROID_UPLOAD_KEYSTORE_BASE64:-}" && -n "${ANDROID_UPLOAD_KEYSTORE_PASSWORD:-}" && -n "${ANDROID_UPLOAD_KEY_ALIAS:-}" && -n "${ANDROID_UPLOAD_KEY_PASSWORD:-}" ]]; then
  legacy_ok=true
fi

if [[ "${repo_ok}" == true ]]; then
  SIGNING_MODE="repo"
elif [[ "${legacy_ok}" == true ]]; then
  SIGNING_MODE="legacy"
else
  echo "build-release-bundle: Release signing not configured (same rules as CI)." >&2
  echo "- Repo mode requires: secrets/upload-keystore.jks.age and android_upload_signing in secrets JSON." >&2
  echo "- Legacy mode requires all 4 env vars: ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_KEYSTORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD." >&2
  echo "To initialize repo mode quickly: OUTREACH_SIGNING_REPO_MODE=1 bash android/scripts/create-upload-keystore-and-gh-secrets.sh" >&2
  exit 1
fi

OUTREACH_MAPS_KEY_FIELD=release_api_key bash "${SCRIPT_DIR}/setup-secrets.sh" "${OUTREACH_JSON}"

if [[ "${SIGNING_MODE}" == "repo" ]]; then
  bash "${SCRIPT_DIR}/decrypt-age-passphrase.sh" "${KS_AGE}" "${UPLOAD_JKS}"
  if ! [[ -s "${UPLOAD_JKS}" ]]; then
    echo "build-release-bundle: decrypted keystore missing/empty at ${UPLOAD_JKS}" >&2
    exit 1
  fi
  export ANDROID_UPLOAD_KEYSTORE_PASSWORD
  ANDROID_UPLOAD_KEYSTORE_PASSWORD="$(jq -r '.android_upload_signing.keystore_password' "${OUTREACH_JSON}")"
  export ANDROID_UPLOAD_KEY_ALIAS
  ANDROID_UPLOAD_KEY_ALIAS="$(jq -r '.android_upload_signing.key_alias' "${OUTREACH_JSON}")"
  export ANDROID_UPLOAD_KEY_PASSWORD
  ANDROID_UPLOAD_KEY_PASSWORD="$(jq -r '.android_upload_signing.key_password' "${OUTREACH_JSON}")"
else
  if base64 --help 2>&1 | grep -q '\-d'; then
    printf '%s' "${ANDROID_UPLOAD_KEYSTORE_BASE64}" | base64 -d > "${UPLOAD_JKS}"
  else
    printf '%s' "${ANDROID_UPLOAD_KEYSTORE_BASE64}" | base64 -D > "${UPLOAD_JKS}"
  fi
  if ! [[ -s "${UPLOAD_JKS}" ]]; then
    echo "build-release-bundle: decoded keystore missing/empty at ${UPLOAD_JKS}" >&2
    exit 1
  fi
fi
export ANDROID_UPLOAD_KEYSTORE_PATH="${UPLOAD_JKS}"

./gradlew bundleRelease --no-daemon

AAB="$(pwd)/app/build/outputs/bundle/release/app-release.aab"
echo "AAB: $AAB"
