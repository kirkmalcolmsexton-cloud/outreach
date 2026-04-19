#!/usr/bin/env bash
# Encrypt a file with symmetric age (-p) for the same passphrase model as
# secrets/outreach-secrets.json.age (OUTREACH_SECRETS_PASSPHRASE in CI and headless use).
#
# Prerequisites: age; for headless, expect.
#
# Usage:
#   OUTREACH_SECRETS_PASSPHRASE=... encrypt-age-passphrase.sh INPUT_FILE OUTPUT.age
# TTY: omit env; age prompts twice.

set -euo pipefail

usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//' >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 2 ]]; then
  echo "encrypt-age-passphrase: need two arguments (or use -h/--help)." >&2
  echo "" >&2
  usage
  exit 1
fi

INPUT_FILE="$1"
OUTPUT_AGE="$2"

[[ -f "${INPUT_FILE}" ]] || {
  echo "encrypt-age-passphrase: not a file: ${INPUT_FILE}" >&2
  exit 1
}

command -v age >/dev/null || {
  echo "encrypt-age-passphrase: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}

mkdir -p "$(dirname "${OUTPUT_AGE}")"

encrypt_interactive() {
  age -p -o "${OUTPUT_AGE}" "${INPUT_FILE}"
}

encrypt_with_env_passphrase() {
  command -v expect >/dev/null || {
    echo "encrypt-age-passphrase: OUTREACH_SECRETS_PASSPHRASE is set but expect was not found." >&2
    echo "  Install expect (Ubuntu: sudo apt install expect; brew: brew install expect)." >&2
    exit 1
  }
  export OUTREACH_AGE_PLAIN_IN="${INPUT_FILE}"
  export OUTREACH_AGE_CIPHER_OUT="${OUTPUT_AGE}"
  expect <<'EXPECTEOF'
log_user 0
set passphrase $env(OUTREACH_SECRETS_PASSPHRASE)
if {$passphrase eq ""} {
  puts stderr "encrypt-age-passphrase: OUTREACH_SECRETS_PASSPHRASE is empty"
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
    echo "encrypt-age-passphrase: for non-interactive use set OUTREACH_SECRETS_PASSPHRASE (and install expect)." >&2
    exit 1
  fi
  encrypt_interactive
fi

echo "encrypt-age-passphrase: wrote ${OUTPUT_AGE}"
