#!/usr/bin/env bash
# Build Outreach for the iOS Simulator, install the .app, and launch it (like "Run" in Xcode).
# Apple ships the iOS Simulator, not an "emulator"; this script targets Simulator only.
#
# From repo root:
#   bash ios/scripts/deploy-simulator.sh
#
# Prerequisites: Full Xcode, same as ios/scripts/build.sh. Optional GoogleService-Info.plist for sign-in.
#
# Environment (inherits build.sh / simulator.sh where noted):
#   OUTREACH_XCODE, OUTREACH_DERIVED_DATA, OUTREACH_DESTINATION, OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH,
#   OUTREACH_SKIP_SPM_RESOLVE, OUTREACH_IOS_SIM_NAME (simulator.sh),
#   OUTREACH_BUNDLE_ID          — default org.outreach.ios
#   OUTREACH_APP_NAME           — .app folder name under Debug-iphonesimulator (default: Outreach)
#   OUTREACH_DEPLOY_SKIP_BOOT=1 — do not open Simulator or boot (use an already-booted device)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DD="${OUTREACH_DERIVED_DATA:-${IOS_DIR}/build/DerivedData}"
BUNDLE_ID="${OUTREACH_BUNDLE_ID:-org.outreach.ios}"
APP_NAME="${OUTREACH_APP_NAME:-Outreach}"

if [[ -n "${OUTREACH_XCODE:-}" ]]; then
  export DEVELOPER_DIR="${OUTREACH_XCODE}/Contents/Developer"
fi

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0")

  Builds Debug for iOS Simulator (via ios/scripts/build.sh), installs to the simulator, launches ${BUNDLE_ID}.

  Default flow: ensure the Outreach simulator exists and is booted (ios/scripts/simulator.sh start),
  then build, simctl install, simctl launch.

  OUTREACH_DEPLOY_SKIP_BOOT=1  — skip boot (destination must already be booted)
  OUTREACH_DESTINATION='platform=iOS Simulator,id=…'  — pin device (still runs boot unless skip)

See: ios/scripts/simulator.sh, ios/scripts/build.sh -h
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

command -v xcrun >/dev/null 2>&1 || {
  echo "deploy-simulator: xcrun not found. Install Xcode." >&2
  exit 1
}

udid_from_destination() {
  local d="$1"
  if [[ "${d}" =~ id=([A-Fa-f0-9-]{36}) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  echo "deploy-simulator: could not parse UDID from OUTREACH_DESTINATION: ${d}" >&2
  return 1
}

if [[ "${OUTREACH_DEPLOY_SKIP_BOOT:-}" == "1" ]]; then
  :
else
  if [[ -n "${OUTREACH_DESTINATION:-}" ]]; then
    udid="$(udid_from_destination "${OUTREACH_DESTINATION}")"
    bash "${SCRIPT_DIR}/simulator.sh" boot "${udid}"
  else
    bash "${SCRIPT_DIR}/simulator.sh" start
  fi
fi

DEST="${OUTREACH_DESTINATION:-}"
if [[ -z "${DEST}" ]]; then
  DEST="$(bash "${SCRIPT_DIR}/simulator.sh" destination)"
  export OUTREACH_DESTINATION="${DEST}"
fi

udid="$(udid_from_destination "${DEST}")"

echo "deploy-simulator: building (destination ${DEST}) …" >&2
bash "${SCRIPT_DIR}/build.sh" build

APP="${DD}/Build/Products/Debug-iphonesimulator/${APP_NAME}.app"
if [[ ! -d "${APP}" ]]; then
  echo "deploy-simulator: missing ${APP} after build (check OUTREACH_APP_NAME / scheme product name)" >&2
  exit 1
fi

echo "deploy-simulator: installing ${APP_NAME}.app on ${udid} …" >&2
xcrun simctl install "${udid}" "${APP}"

echo "deploy-simulator: launching ${BUNDLE_ID} …" >&2
xcrun simctl terminate "${udid}" "${BUNDLE_ID}" 2>/dev/null || true
xcrun simctl launch "${udid}" "${BUNDLE_ID}"

echo "deploy-simulator: OK"
