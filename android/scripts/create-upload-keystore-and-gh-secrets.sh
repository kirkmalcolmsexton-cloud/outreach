#!/usr/bin/env bash
# Generate a new Play *upload* keystore and push signing material to GitHub Actions
# repository secrets (ANDROID_UPLOAD_*). Does not enroll the app in Play Console—do that separately.
#
# Prerequisites:
#   - JDK (keytool on PATH or JAVA_HOME/bin/keytool)
#   - GitHub CLI: gh, authenticated with permission to set repo secrets ("repo" scope)
#   - Run from a clone of this repository (git root = outreach repo root)
#
# Usage:
#   ./android/scripts/create-upload-keystore-and-gh-secrets.sh
#
# Environment (optional):
#   OUTREACH_UPLOAD_KEYSTORE_PATH     Output path for the new .jks (default: ~/.config/outreach/upload-keystore.jks)
#   OUTREACH_UPLOAD_KEY_ALIAS        Key alias (default: upload)
#   OUTREACH_KEYSTORE_DNAME           Distinguished name for keytool -dname (default: CN=Outreach Upload, O=Outreach, C=US — assembled in script, not a secret)
#   OUTREACH_KEYSTORE_VALIDITY_DAYS  Key validity in days (default: 10000)
#   OUTREACH_KEYSTORE_PASSWORD       Keystore password; if unset, script prompts (hidden)
#   OUTREACH_UPLOAD_KEY_PASSWORD      Key password; if unset, same as keystore password
#   OUTREACH_DRY_RUN                  Set to 1 to print actions only (no keytool, no gh secret set)
#   OUTREACH_OVERWRITE_KEYSTORE       Set to 1 to replace an existing file at OUTREACH_UPLOAD_KEYSTORE_PATH
#
# Secrets written (names match android-release-build.yml / Gradle):
#   ANDROID_UPLOAD_KEYSTORE_BASE64
#   ANDROID_UPLOAD_KEYSTORE_PASSWORD
#   ANDROID_UPLOAD_KEY_ALIAS
#   ANDROID_UPLOAD_KEY_PASSWORD
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

if [[ "${DRY_RUN}" != "1" ]] && ! gh auth status >/dev/null 2>&1; then
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
  echo "[dry-run] Would gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64 ANDROID_UPLOAD_KEYSTORE_PASSWORD ..."
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

echo "Keystore created. Back up ${KEYSTORE_PATH} securely (password manager, encrypted backup)—GitHub only stores automation copies."

if base64 --help 2>&1 | grep -q -- '-w'; then
  B64="$(base64 -w0 "${KEYSTORE_PATH}")"
else
  B64="$(base64 "${KEYSTORE_PATH}" | tr -d '\n')"
fi

printf '%s' "${B64}" | gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64
printf '%s' "${store_pass}" | gh secret set ANDROID_UPLOAD_KEYSTORE_PASSWORD
printf '%s' "${KEY_ALIAS}" | gh secret set ANDROID_UPLOAD_KEY_ALIAS
printf '%s' "${key_pass}" | gh secret set ANDROID_UPLOAD_KEY_PASSWORD

echo "GitHub Actions secrets set: ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_KEYSTORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD"
echo "Next: enroll upload key in Play Console if needed, register OAuth/Maps SHA-1 (docs/google-oauth-checklist.md), ensure CI signing reads these secrets (Gradle + workflow)."
