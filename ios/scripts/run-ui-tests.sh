#!/usr/bin/env bash
# Run Outreach XCUITests on the iOS Simulator (default) or a connected iPhone.
#
# Usage (from repo root):
#   bash ios/scripts/run-ui-tests.sh
#   bash ios/scripts/run-ui-tests.sh --device
#
# Simulator: when OUTREACH_DESTINATION is unset, boots the default Outreach sim (unless
# OUTREACH_DEPLOY_SKIP_BOOT=1) and sets OUTREACH_DESTINATION from resolve-outreach-ios-simulator-destination.sh.
#
# Device: set OUTREACH_DESTINATION to platform=iOS,id=<udid> (same id as Xcode / xcodebuild -showdestinations),
# or set OUTREACH_DEVICE_UDID and this script will map it. Signing: OUTREACH_DEVELOPMENT_TEAM (see deploy-device.sh).
#
# Environment:
#   OUTREACH_DESTINATION, OUTREACH_DEVICE_UDID, OUTREACH_DEVELOPMENT_TEAM, OUTREACH_DEPLOY_SKIP_BOOT,
#   OUTREACH_SKIP_IOS_SETUP_SECRETS (default 1 here), OUTREACH_SKIP_SPM_RESOLVE — see ios/scripts/build.sh -h

set -euo pipefail

if [[ -f "${HOME}/etc/outreach.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "${HOME}/etc/outreach.env"
  set +a
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${IOS_DIR}/.." && pwd)"

MODE="simulator"
if [[ "${1:-}" == "--device" ]]; then
  MODE="device"
fi

cd "${REPO_ROOT}"

if [[ -z "${OUTREACH_DESTINATION:-}" ]]; then
  if [[ "${MODE}" == "device" ]]; then
    if [[ -n "${OUTREACH_DEVICE_UDID:-}" ]]; then
      export OUTREACH_DESTINATION="platform=iOS,id=${OUTREACH_DEVICE_UDID}"
    else
      echo "run-ui-tests: for --device, set OUTREACH_DESTINATION or OUTREACH_DEVICE_UDID." >&2
      echo "  Example: export OUTREACH_DEVICE_UDID=00008140-001A118E1234567C" >&2
      echo "  Or: export OUTREACH_DESTINATION='platform=iOS,id=<udid from xcodebuild -showdestinations>'" >&2
      exit 1
    fi
  else
    if [[ "${OUTREACH_DEPLOY_SKIP_BOOT:-}" != "1" ]]; then
      bash "${IOS_DIR}/scripts/simulator.sh" start >/dev/null || true
    fi
    export OUTREACH_DESTINATION="$(bash "${IOS_DIR}/scripts/resolve-outreach-ios-simulator-destination.sh")"
  fi
fi

export OUTREACH_SKIP_IOS_SETUP_SECRETS="${OUTREACH_SKIP_IOS_SETUP_SECRETS:-1}"

echo "run-ui-tests: OUTREACH_DESTINATION=${OUTREACH_DESTINATION}" >&2
exec bash "${IOS_DIR}/scripts/build.sh" test
