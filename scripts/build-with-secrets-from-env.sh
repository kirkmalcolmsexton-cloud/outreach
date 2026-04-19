#!/usr/bin/env bash
# Source ~/etc/outreach.env (required), decrypt secrets/outreach-secrets.json.age to plaintext JSON,
# run android/scripts/setup-secrets.sh on that JSON, then Gradle assembleDebug from android/.
#
# Prerequisites: age, jq, expect (same as setup-secrets / encrypt-secrets headless paths).
# Env file must define OUTREACH_SECRETS_PASSPHRASE (from your team admin).
#
# Usage: from repo root — ./scripts/build-with-secrets-from-env.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTREACH_ENV="${HOME}/etc/outreach.env"
AGE_FILE="${OUTREACH_ROOT}/secrets/outreach-secrets.json.age"
PLAIN_FILE="${OUTREACH_ROOT}/secrets/outreach-secrets.json"

if [[ ! -f "${OUTREACH_ENV}" ]]; then
  echo "build-with-secrets-from-env: missing env file: ${OUTREACH_ENV}" >&2
  echo "  Create it (see README): mkdir -p ~/etc && nano ${OUTREACH_ENV}" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
source "${OUTREACH_ENV}"
set +a

if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
  echo "build-with-secrets-from-env: OUTREACH_SECRETS_PASSPHRASE is unset or empty after sourcing ${OUTREACH_ENV}" >&2
  exit 1
fi

command -v age >/dev/null || {
  echo "build-with-secrets-from-env: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}
command -v jq >/dev/null || {
  echo "build-with-secrets-from-env: install jq" >&2
  exit 1
}
command -v expect >/dev/null || {
  echo "build-with-secrets-from-env: install expect (macOS: preinstalled; Ubuntu: sudo apt install expect)" >&2
  exit 1
}

[[ -f "${AGE_FILE}" ]] || {
  echo "build-with-secrets-from-env: encrypted secrets not found: ${AGE_FILE}" >&2
  exit 1
}

export OUTREACH_AGE_INPUT="${AGE_FILE}"
export OUTREACH_JSON_OUT="${PLAIN_FILE}"

expect <<'EXPECTEOF'
log_user 0
set passphrase $env(OUTREACH_SECRETS_PASSPHRASE)
if {$passphrase eq ""} {
  puts stderr "build-with-secrets-from-env: OUTREACH_SECRETS_PASSPHRASE is empty"
  exit 1
}
set timeout -1
spawn bash -c {exec age -d -o "$1" "$2"} _ $env(OUTREACH_JSON_OUT) $env(OUTREACH_AGE_INPUT)
expect {
  -re {Enter passphrase} {
    send "$passphrase\r"
    exp_continue
  }
  eof
}
EXPECTEOF

[[ -f "${PLAIN_FILE}" ]] || {
  echo "build-with-secrets-from-env: decrypt produced no file at ${PLAIN_FILE}" >&2
  exit 1
}
jq empty "${PLAIN_FILE}" 2>/dev/null || {
  echo "build-with-secrets-from-env: decrypted file is not valid JSON: ${PLAIN_FILE}" >&2
  exit 1
}

bash "${OUTREACH_ROOT}/android/scripts/setup-secrets.sh" "${PLAIN_FILE}"

cd "${OUTREACH_ROOT}/android"
chmod +x ./gradlew
exec ./gradlew assembleDebug --no-daemon
