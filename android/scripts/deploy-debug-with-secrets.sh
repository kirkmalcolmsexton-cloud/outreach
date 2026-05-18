#!/usr/bin/env bash
# Materialize Firebase/Maps from team secrets (outreach-secrets.json.age), then build,
# install, and launch debug on a physical device — Option B in one command.
#
# Prerequisites: age, jq, expect; USB device authorized; ~/etc/outreach.env with
# OUTREACH_SECRETS_PASSPHRASE (see docs/developer-onboarding.md §6b).
#
# Usage (from repo root or android/):
#   bash android/scripts/deploy-debug-with-secrets.sh
#   bash android/scripts/deploy-debug-with-secrets.sh --pick
#   bash android/scripts/deploy-debug-with-secrets.sh --pick --reverse
#
# All options after --skip-setup-secrets are passed to deploy-debug-to-device.sh
# (--serial, --pick, --no-launch, --reverse, etc.).
#
# Environment:
#   OUTREACH_SECRETS_PASSPHRASE   Decrypts secrets/outreach-secrets.json.age (or set in ~/etc/outreach.env)
#   OUTREACH_SECRETS_FILE         Optional path to .age or plaintext JSON for setup-secrets.sh
#   OUTREACH_SKIP_SETUP_SECRETS=1 Skip setup-secrets (deploy only)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTREACH_ENV="${HOME}/etc/outreach.env"
AGE_FILE="${REPO_ROOT}/secrets/outreach-secrets.json.age"

SKIP_SETUP=0
DEPLOY_ARGS=()

usage() {
  cat <<'EOF'
Decrypt team secrets, write google-services.json + Maps keys, then deploy debug to a physical device.

  bash android/scripts/deploy-debug-with-secrets.sh
  bash android/scripts/deploy-debug-with-secrets.sh --pick
  bash android/scripts/deploy-debug-with-secrets.sh --pick --reverse

Prerequisites:
  secrets/outreach-secrets.json.age, age + jq + expect, OUTREACH_SECRETS_PASSPHRASE
  (typically in ~/etc/outreach.env — see docs/developer-onboarding.md §6b).

Options (this script only):
  --skip-setup-secrets   Skip setup-secrets.sh (use existing app/google-services.json)

Remaining options are passed to deploy-debug-to-device.sh (--serial, --pick, --no-launch, --reverse, -h).

Environment:
  OUTREACH_SECRETS_PASSPHRASE, OUTREACH_SECRETS_FILE, OUTREACH_SKIP_SETUP_SECRETS=1
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-setup-secrets)
      SKIP_SETUP=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      DEPLOY_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ "${OUTREACH_SKIP_SETUP_SECRETS:-}" == "1" ]]; then
  SKIP_SETUP=1
fi

source_outreach_env() {
  if [[ -n "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
    return 0
  fi
  if [[ ! -f "${OUTREACH_ENV}" ]]; then
    return 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "${OUTREACH_ENV}"
  set +a
}

run_setup_secrets() {
  if [[ "${SKIP_SETUP}" -eq 1 ]]; then
    echo "deploy-debug-with-secrets: skipping setup-secrets (OUTREACH_SKIP_SETUP_SECRETS / --skip-setup-secrets)"
    return 0
  fi

  local secrets_input="${OUTREACH_SECRETS_FILE:-}"
  if [[ -z "${secrets_input}" && -f "${AGE_FILE}" ]]; then
    secrets_input="${AGE_FILE}"
  fi

  if [[ -n "${secrets_input}" && "${secrets_input}" == *.age ]]; then
    if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
      if source_outreach_env; then
        :
      else
        echo "deploy-debug-with-secrets: need OUTREACH_SECRETS_PASSPHRASE to decrypt ${AGE_FILE}" >&2
        echo "  Set it in the environment or in ${OUTREACH_ENV} (see docs/developer-onboarding.md §6b)." >&2
        exit 1
      fi
    fi
    if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
      echo "deploy-debug-with-secrets: OUTREACH_SECRETS_PASSPHRASE is empty after sourcing ${OUTREACH_ENV}" >&2
      exit 1
    fi
    command -v age >/dev/null || {
      echo "deploy-debug-with-secrets: install age — https://github.com/FiloSottile/age" >&2
      exit 1
    }
    command -v jq >/dev/null || {
      echo "deploy-debug-with-secrets: install jq" >&2
      exit 1
    }
    command -v expect >/dev/null || {
      echo "deploy-debug-with-secrets: install expect (macOS: preinstalled; Ubuntu: sudo apt install expect)" >&2
      exit 1
    }
  elif [[ -z "${secrets_input}" && ! -f "${REPO_ROOT}/secrets/outreach-secrets.json" ]]; then
    echo "deploy-debug-with-secrets: no secrets file found." >&2
    echo "  Place ${AGE_FILE} or set OUTREACH_SECRETS_FILE." >&2
    exit 1
  fi

  echo "deploy-debug-with-secrets: materializing google-services.json and Maps keys …"
  if [[ -n "${secrets_input}" ]]; then
    bash "${SCRIPT_DIR}/setup-secrets.sh" "${secrets_input}"
  else
    bash "${SCRIPT_DIR}/setup-secrets.sh"
  fi

  if ! jq -e '(.client[0].oauth_client // []) | length > 0' "${ANDROID_DIR}/app/google-services.json" >/dev/null 2>&1; then
    echo "deploy-debug-with-secrets: warning: google-services.json has no oauth_client entries — Google Sign-In will fail." >&2
    echo "  Refresh google_services in secrets and re-run setup-secrets / merge-google-services-into-secrets.sh." >&2
  fi
}

run_setup_secrets
exec bash "${SCRIPT_DIR}/deploy-debug-to-device.sh" "${DEPLOY_ARGS[@]}"
