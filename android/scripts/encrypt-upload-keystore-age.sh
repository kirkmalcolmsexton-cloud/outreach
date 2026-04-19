#!/usr/bin/env bash
# Encrypt the upload keystore to secrets/upload-keystore.jks.age (symmetric age -p).
# Use the same passphrase as secrets/outreach-secrets.json.age (OUTREACH_SECRETS_PASSPHRASE).
#
# No path arguments — fixed layout:
#   Plaintext (input):  OUTREACH_UPLOAD_KEYSTORE_PATH or ~/.config/outreach/upload-keystore.jks
#   Ciphertext (out): <repo>/secrets/upload-keystore.jks.age
#
# Prerequisites: age; for headless encrypt, expect.
#
# Usage (from repo root):
#   OUTREACH_SECRETS_PASSPHRASE=... ./android/scripts/encrypt-upload-keystore-age.sh
# TTY: omit env; age prompts twice.

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

if [[ $# -ne 0 ]]; then
  echo "encrypt-upload-keystore-age: this script takes no path arguments (use -h). Set OUTREACH_UPLOAD_KEYSTORE_PATH to override the input .jks location." >&2
  echo "" >&2
  usage
  exit 1
fi

INPUT_FILE="${OUTREACH_UPLOAD_KEYSTORE_PATH:-${HOME}/.config/outreach/upload-keystore.jks}"
OUTPUT_AGE="${OUTREACH_ROOT}/secrets/upload-keystore.jks.age"

[[ -f "${INPUT_FILE}" ]] || {
  echo "encrypt-upload-keystore-age: plaintext keystore not found: ${INPUT_FILE}" >&2
  echo "  Generate one with create-upload-keystore-and-gh-secrets.sh or set OUTREACH_UPLOAD_KEYSTORE_PATH." >&2
  exit 1
}

command -v age >/dev/null || {
  echo "encrypt-upload-keystore-age: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}

mkdir -p "$(dirname "${OUTPUT_AGE}")"

encrypt_interactive() {
  age -p -o "${OUTPUT_AGE}" "${INPUT_FILE}"
}

encrypt_with_env_passphrase() {
  command -v expect >/dev/null || {
    echo "encrypt-upload-keystore-age: OUTREACH_SECRETS_PASSPHRASE is set but expect was not found." >&2
    echo "  Install expect (Ubuntu: sudo apt install expect; brew: brew install expect)." >&2
    exit 1
  }
  export OUTREACH_AGE_PLAIN_IN="${INPUT_FILE}"
  export OUTREACH_AGE_CIPHER_OUT="${OUTPUT_AGE}"
  expect <<'EXPECTEOF'
log_user 0
set passphrase $env(OUTREACH_SECRETS_PASSPHRASE)
if {$passphrase eq ""} {
  puts stderr "encrypt-upload-keystore-age: OUTREACH_SECRETS_PASSPHRASE is empty"
  exit 1
}
set timeout -1
spawn bash -c {exec age -p -o "$1" "$2"} _ $env(OUTREACH_AGE_CIPHER_OUT) $env(OUTREACH_AGE_PLAIN_IN)
expect {
  -re {Enter passphrase} {
    send "$passphrase\r"
    exp_continue
  }
  -re {Confirm passphrase} {
    send "$passphrase\r"
    exp_continue
  }
  eof
}
EXPECTEOF
}

if [[ -n "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
  encrypt_with_env_passphrase
else
  if [[ ! -t 0 ]]; then
    echo "encrypt-upload-keystore-age: for non-interactive use set OUTREACH_SECRETS_PASSPHRASE (and install expect)." >&2
    exit 1
  fi
  encrypt_interactive
fi

echo "encrypt-upload-keystore-age: wrote ${OUTPUT_AGE}"
