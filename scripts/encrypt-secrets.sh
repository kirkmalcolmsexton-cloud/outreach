#!/usr/bin/env bash
# Encrypt consolidated Outreach secrets JSON (see secrets/outreach-secrets.example.json).
#
# Prerequisites: age (https://github.com/FiloSottile/age), jq.
# Non-interactive: set OUTREACH_SECRETS_PASSPHRASE and install expect (macOS has it;
# Ubuntu: sudo apt install expect).
#
# Passphrase:
#   - Sources ~/etc/outreach.env when present for OUTREACH_SECRETS_PASSPHRASE.
#   - OUTREACH_SECRETS_PASSPHRASE already set in the environment overrides the file.
#
# Defaults (under repo root): secrets/outreach-secrets.json → secrets/outreach-secrets.json.age
#
# Examples (repo root):
#   bash scripts/encrypt-secrets.sh
#   bash scripts/encrypt-secrets.sh --checkin    # same + git add reminder (operator check-in)
#
set -euo pipefail

_cli_passphrase="${OUTREACH_SECRETS_PASSPHRASE:-}"
if [[ -f "${HOME}/etc/outreach.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "${HOME}/etc/outreach.env"
  set +a
fi
if [[ -n "${_cli_passphrase}" ]]; then
  OUTREACH_SECRETS_PASSPHRASE="${_cli_passphrase}"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTREACH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

CHECKIN_HINT=0
FILTERED=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --checkin | -c)
      CHECKIN_HINT=1
      shift
      ;;
    -h | --help)
      cat >&2 <<EOF
Usage: $(basename "$0") [--checkin | -c] [-i INPUT_JSON] [-o OUTPUT_AGE]

  Encrypt plaintext consolidated secrets for sharing or storage.

  Default input:  ${OUTREACH_ROOT}/secrets/outreach-secrets.json
  Default output: ${OUTREACH_ROOT}/secrets/outreach-secrets.json.age

  --checkin, -c   After success, print which file to git add (operator flow).

  Passphrase:
    - Set OUTREACH_SECRETS_PASSPHRASE for non-interactive encryption (uses expect).
    - Optional: define it in ~/etc/outreach.env (overridden by env already set before this runs).
    - Otherwise run in a terminal; age prompts twice (TTY required).

EOF
      exit 0
      ;;
    *)
      FILTERED+=("$1")
      shift
      ;;
  esac
done
set -- "${FILTERED[@]}"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--checkin | -c] [-i INPUT_JSON] [-o OUTPUT_AGE]

  Run with -h or --help for full options.

EOF
}

INPUT_JSON=""
OUTPUT_AGE=""
while getopts "hi:o:" opt; do
  case "$opt" in
    h) usage; exit 0 ;;
    i) INPUT_JSON="$OPTARG" ;;
    o) OUTPUT_AGE="$OPTARG" ;;
    *) usage; exit 1 ;;
  esac
done

[[ -z "${INPUT_JSON}" ]] && INPUT_JSON="${OUTREACH_ROOT}/secrets/outreach-secrets.json"
[[ -z "${OUTPUT_AGE}" ]] && OUTPUT_AGE="${OUTREACH_ROOT}/secrets/outreach-secrets.json.age"

command -v age >/dev/null || {
  echo "encrypt-secrets: install age — https://github.com/FiloSottile/age" >&2
  exit 1
}
command -v jq >/dev/null || {
  echo "encrypt-secrets: install jq" >&2
  exit 1
}

[[ -f "${INPUT_JSON}" ]] || {
  echo "encrypt-secrets: input not found: ${INPUT_JSON}" >&2
  exit 1
}

jq empty "${INPUT_JSON}" || {
  echo "encrypt-secrets: invalid JSON: ${INPUT_JSON}" >&2
  exit 1
}
jq -e '.development_api_key | type == "string" and length > 0' "${INPUT_JSON}" >/dev/null 2>&1 || {
  echo "encrypt-secrets: .development_api_key must be a non-empty string" >&2
  exit 1
}
jq -e '.release_api_key | type == "string" and length > 0' "${INPUT_JSON}" >/dev/null 2>&1 || {
  echo "encrypt-secrets: .release_api_key must be a non-empty string" >&2
  exit 1
}
jq -e '.google_services | type == "object"' "${INPUT_JSON}" >/dev/null 2>&1 || {
  echo "encrypt-secrets: .google_services must be an object" >&2
  exit 1
}

encrypt_interactive() {
  age -p -o "${OUTPUT_AGE}" "${INPUT_JSON}"
}

encrypt_with_env_passphrase() {
  command -v expect >/dev/null || {
    echo "encrypt-secrets: OUTREACH_SECRETS_PASSPHRASE is set but 'expect' was not found." >&2
    echo "  Install expect (Ubuntu: sudo apt install expect; brew: brew install expect)." >&2
    exit 1
  }
  export INPUT_JSON
  export OUTPUT_AGE
  expect <<'EXPECTEOF'
log_user 0
set passphrase $env(OUTREACH_SECRETS_PASSPHRASE)
if {$passphrase eq ""} {
  puts stderr "encrypt-secrets: OUTREACH_SECRETS_PASSPHRASE is empty"
  exit 1
}
set timeout -1
spawn bash -c {exec age -p -o "$1" "$2"} _ $env(OUTPUT_AGE) $env(INPUT_JSON)
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
    echo "encrypt-secrets: for non-interactive use, set OUTREACH_SECRETS_PASSPHRASE (and install expect)." >&2
    echo "encrypt-secrets: or run this script in a terminal for interactive passphrase entry." >&2
    exit 1
  fi
  encrypt_interactive
fi

echo "encrypt-secrets: wrote ${OUTPUT_AGE}"

if [[ "${CHECKIN_HINT}" -eq 1 ]]; then
  echo ""
  echo "Check in ciphertext only:"
  echo "  git add secrets/outreach-secrets.json.age"
  echo "Do not commit: secrets/outreach-secrets.json (plaintext)."
fi
