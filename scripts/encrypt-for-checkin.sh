#!/usr/bin/env bash
# Operator alias: encrypt with ~/etc/outreach.env support + git check-in reminder.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/encrypt-secrets.sh" --checkin "$@"
