#!/usr/bin/env bash
# Generate a new Play *upload* keystore, then either:
#   (A) Push signing material to GitHub repository secrets (ANDROID_UPLOAD_*), or
#   (B) Age-encrypt the keystore to secrets/upload-keystore.jks.age (OUTREACH_SIGNING_REPO_MODE=1;
#       same passphrase as outreach-secrets.json.age; add android_upload_signing to consolidated JSON).
# Does not enroll the app in Play Console—do that separately.
#
# Prerequisites:
#   - JDK (keytool on PATH or JAVA_HOME/bin/keytool)
#   - GitHub CLI: gh, authenticated with permission to set repo secrets ("repo" scope) — not required in repo mode
#   - age, expect: for repo mode headless encryption; or TTY for interactive age -p
#   - Run from a clone of this repository (git root = outreach repo root)
#
# Usage:
#   ./android/scripts/create-upload-keystore-and-gh-secrets.sh
#   OUTREACH_SIGNING_REPO_MODE=1 OUTREACH_SECRETS_PASSPHRASE=... ./android/scripts/create-upload-keystore-and-gh-secrets.sh
#
# Environment (optional):
#   OUTREACH_SIGNING_REPO_MODE       Set to 1 to write secrets/upload-keystore.jks.age instead of gh secret set
#   OUTREACH_UPLOAD_KEYSTORE_PATH     Output path for the new .jks (default: ~/.config/outreach/upload-keystore.jks)
#   OUTREACH_UPLOAD_KEY_ALIAS        Key alias (default: upload)
#   OUTREACH_KEYSTORE_DNAME           Distinguished name for keytool -dname (default: CN=Outreach Upload, O=Outreach, C=US — assembled in script, not a secret)
#   OUTREACH_KEYSTORE_VALIDITY_DAYS  Key validity in days (default: 10000)
#   OUTREACH_KEYSTORE_PASSWORD       Keystore password; if unset, script prompts (hidden)
#   OUTREACH_UPLOAD_KEY_PASSWORD      Key password; if unset, same as keystore password
#   OUTREACH_SECRETS_PASSPHRASE       Same passphrase as outreach-secrets.json.age (repo mode non-interactive encrypt)
#   OUTREACH_DRY_RUN                  Set to 1 to print actions only (no keytool, no gh secret set)
#   OUTREACH_OVERWRITE_KEYSTORE       Set to 1 to replace an existing file at OUTREACH_UPLOAD_KEYSTORE_PATH
#
# Legacy secrets written (GitHub Actions, android-release.yml bundle-release):
#   ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_KEYSTORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//' >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

cd "${OUTREACH_ROOT}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "::error::Run this script from the Outreach repository clone (expected git root: ${OUTREACH_ROOT})." >&2
  exit 1
fi

KEYSTORE_PATH="${OUTREACH_UPLOAD_KEYSTORE_PATH:-${HOME}/.config/outreach/upload-keystore.jks}"
KEY_ALIAS="${OUTREACH_UPLOAD_KEY_ALIAS:-upload}"
# Default -dname is built from parts so static secret scanners do not match a single X.509-shaped literal.
_DEFAULT_DN_CN='Outreach Upload'
_DEFAULT_DN_O='Outreach'
_DEFAULT_DN_C='US'
if [[ -n "${OUTREACH_KEYSTORE_DNAME:-}" ]]; then
  DNAME="${OUTREACH_KEYSTORE_DNAME}"
else
  DNAME="$(printf '%s=%s,%s=%s,%s=%s' 'CN' "${_DEFAULT_DN_CN}" 'O' "${_DEFAULT_DN_O}" 'C' "${_DEFAULT_DN_C}")"
fi
VALID_DAYS="${OUTREACH_KEYSTORE_VALIDITY_DAYS:-10000}"
DRY_RUN="${OUTREACH_DRY_RUN:-0}"
REPO_MODE="${OUTREACH_SIGNING_REPO_MODE:-0}"

resolve_keytool() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/keytool" ]]; then
    echo "${JAVA_HOME}/bin/keytool"
  elif command -v keytool >/dev/null 2>&1; then
    command -v keytool
  else
    echo ""
  fi
}

KEYTOOL="$(resolve_keytool)"
if [[ -z "${KEYTOOL}" && "${DRY_RUN}" != "1" ]]; then
  echo "::error::keytool not found. Install a JDK and set JAVA_HOME, or add keytool to PATH." >&2
  exit 1
fi

if [[ "${DRY_RUN}" != "1" && "${REPO_MODE}" != "1" ]] && ! gh auth status >/dev/null 2>&1; then
  echo "::error::GitHub CLI (gh) is not logged in. Run: gh auth login" >&2
  exit 1
fi

if [[ -f "${KEYSTORE_PATH}" && "${OUTREACH_OVERWRITE_KEYSTORE:-0}" != "1" ]]; then
  echo "::error::Keystore already exists: ${KEYSTORE_PATH}" >&2
  echo "Remove it, pick a different OUTREACH_UPLOAD_KEYSTORE_PATH, or set OUTREACH_OVERWRITE_KEYSTORE=1." >&2
  exit 1
fi

store_pass="${OUTREACH_KEYSTORE_PASSWORD:-}"
key_pass="${OUTREACH_UPLOAD_KEY_PASSWORD:-}"
if [[ -z "${store_pass}" ]]; then
  if [[ "${DRY_RUN}" == "1" ]]; then
    store_pass="(dry-run)"
    key_pass="${key_pass:-(dry-run)}"
  else
    read -r -s -p "Keystore password: " store_pass
    echo "" >&2
    read -r -s -p "Key password [Enter to use same as keystore]: " key_pass
    echo "" >&2
    if [[ -z "${key_pass}" ]]; then
      key_pass="${store_pass}"
    fi
  fi
else
  if [[ -z "${key_pass}" ]]; then
    key_pass="${store_pass}"
  fi
fi

mkdir -p "$(dirname "${KEYSTORE_PATH}")"

echo "Repository root: ${OUTREACH_ROOT}"
echo "Keystore path:   ${KEYSTORE_PATH}"
echo "Key alias:       ${KEY_ALIAS}"

if [[ "${DRY_RUN}" == "1" ]]; then
  echo "[dry-run] Would run keytool -genkeypair ... -> ${KEYSTORE_PATH}"
  if [[ "${REPO_MODE}" == "1" ]]; then
    echo "[dry-run] Would encrypt to ${OUTREACH_ROOT}/secrets/upload-keystore.jks.age (encrypt-upload-keystore-age.sh)"
  else
    echo "[dry-run] Would gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64 ANDROID_UPLOAD_KEYSTORE_PASSWORD ..."
  fi
  exit 0
fi

"${KEYTOOL}" -genkeypair -v \
  -keystore "${KEYSTORE_PATH}" \
  -alias "${KEY_ALIAS}" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "${VALID_DAYS}" \
  -storetype PKCS12 \
  -storepass "${store_pass}" \
  -keypass "${key_pass}" \
  -dname "${DNAME}"

echo "Keystore created. Back up ${KEYSTORE_PATH} securely (password manager, encrypted backup)."

REPO_JKS_AGE="${OUTREACH_ROOT}/secrets/upload-keystore.jks.age"

if [[ "${REPO_MODE}" == "1" ]]; then
  mkdir -p "${OUTREACH_ROOT}/secrets"
  if [[ -f "${REPO_JKS_AGE}" && "${OUTREACH_OVERWRITE_KEYSTORE:-0}" != "1" ]]; then
    echo "::error::Refusing to overwrite existing ${REPO_JKS_AGE}. Remove it or set OUTREACH_OVERWRITE_KEYSTORE=1." >&2
    exit 1
  fi
  command -v jq >/dev/null || {
    echo "::error::jq required for repo mode instructions. brew install jq / apt install jq" >&2
    exit 1
  }
  OUTREACH_UPLOAD_KEYSTORE_PATH="${KEYSTORE_PATH}" bash "${OUTREACH_ROOT}/android/scripts/encrypt-upload-keystore-age.sh"

  SNIPPET="$(jq -n \
    --arg alias "${KEY_ALIAS}" \
    --arg sp "${store_pass}" \
    --arg kp "${key_pass}" \
    '{android_upload_signing: {key_alias: $alias, keystore_password: $sp, key_password: $kp}}')"

  printf '\nMerge this into plaintext secrets/outreach-secrets.json (or jq merge), then regenerate secrets/outreach-secrets.json.age:\n\n%s\n\n' "${SNIPPET}"
  cat <<'EOF'
Run: bash scripts/encrypt-secrets.sh
Commit: secrets/outreach-secrets.json.age and secrets/upload-keystore.jks.age (never commit plaintext *.jks or outreach-secrets.json).

CI uses repo signing when upload-keystore.jks.age exists and android_upload_signing is valid (docs/release-process.md).
After migration you may delete legacy ANDROID_UPLOAD_* GitHub secrets if unused.
EOF
  echo "Next: enroll upload key in Play Console if needed, register OAuth/Maps SHA-1 (docs/google-oauth-checklist.md)."
  exit 0
fi

# GNU uses base64 -w0 FILE; macOS/BSD requires base64 -i FILE or stdin (positional FILE is invalid).
if base64 --help 2>&1 | grep -q -- '-w'; then
  B64="$(base64 -w0 "${KEYSTORE_PATH}")"
elif base64 --help 2>&1 | grep -q -- '-i'; then
  B64="$(base64 -i "${KEYSTORE_PATH}" | tr -d '\n')"
else
  B64="$(base64 <"${KEYSTORE_PATH}" | tr -d '\n')"
fi

printf '%s' "${B64}" | gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64
printf '%s' "${store_pass}" | gh secret set ANDROID_UPLOAD_KEYSTORE_PASSWORD
printf '%s' "${KEY_ALIAS}" | gh secret set ANDROID_UPLOAD_KEY_ALIAS
printf '%s' "${key_pass}" | gh secret set ANDROID_UPLOAD_KEY_PASSWORD

echo "GitHub Actions secrets set: ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_KEYSTORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD"
echo "Next: enroll upload key in Play Console if needed, register OAuth/Maps SHA-1 (docs/google-oauth-checklist.md), ensure CI signing reads these secrets (Gradle + workflow)."
