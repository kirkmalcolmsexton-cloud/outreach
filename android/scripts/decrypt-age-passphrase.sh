#!/usr/bin/env bash
# Decrypt passphrase-protected age files (age -p symmetric encryption).
# Same passphrase model as secrets/outreach-secrets.json.age: OUTREACH_SECRETS_PASSPHRASE.
#
# Prerequisites: age; for headless decrypt, expect.
#
# Usage:
#   OUTREACH_SECRETS_PASSPHRASE=... decrypt-age-passphrase.sh INPUT.age OUTPUT_PATH
# TTY: omit env var and age prompts for passphrase.

set -euo pipefail

usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//' >&2
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 2 ]]; then
  echo "decrypt-age-passphrase: need two arguments (or use -h/--help)." >&2
  echo "" >&2
  usage
  exit 1
fi

INPUT_AGE="$1"
OUTPUT_PATH="$2"

[[ -f "${INPUT_AGE}" ]] || {
  echo "decrypt-age-passphrase: not a file: ${INPUT_AGE}" >&2
  exit 1
}

command -v age >/dev/null || {
  echo "decrypt-age-passphrase: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}

mkdir -p "$(dirname "${OUTPUT_PATH}")"

if [[ -n "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
  command -v expect >/dev/null || {
    echo "decrypt-age-passphrase: OUTREACH_SECRETS_PASSPHRASE is set but expect was not found." >&2
    exit 1
  }
  export OUTREACH_AGE_INPUT="${INPUT_AGE}"
  export OUTREACH_DECRYPT_OUT="${OUTPUT_PATH}"
  expect <<'EXPECTEOF'
log_user 0
set passphrase $env(OUTREACH_SECRETS_PASSPHRASE)
if {$passphrase eq ""} {
  puts stderr "decrypt-age-passphrase: OUTREACH_SECRETS_PASSPHRASE is empty"
  exit 1
}
set timeout -1
spawn bash -c {exec age -d -o "$1" "$2"} _ $env(OUTREACH_DECRYPT_OUT) $env(OUTREACH_AGE_INPUT)
expect {
  -re {Enter passphrase} {
    send "$passphrase\r"
    exp_continue
  }
  eof
}
EXPECTEOF
else
  if [[ ! -t 0 ]]; then
    echo "decrypt-age-passphrase: for non-interactive use set OUTREACH_SECRETS_PASSPHRASE (and install expect)." >&2
    exit 1
  fi
  age -d -o "${OUTPUT_PATH}" "${INPUT_AGE}"
fi
