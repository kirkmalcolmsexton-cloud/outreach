#!/usr/bin/env bash
# Clear Outreach app cache on a physical Android device.
# Default behavior: cache-only clear via run-as (debuggable app).
# Optional fallback: --clear-data uses `pm clear` (wipes app data + cache).
#
# Usage:
#   bash android/scripts/clear-outreach-cache-on-device.sh --pick
#   bash android/scripts/clear-outreach-cache-on-device.sh --serial <device-serial>
#   bash android/scripts/clear-outreach-cache-on-device.sh --pick --clear-data
#
# Options:
#   --package NAME   Android package (default: org.outreach.app)
#   --serial SERIAL  Target adb serial
#   --pick           Pick first physical device
#   --clear-data     Fallback to `pm clear` if cache-only clear fails
#   --dry-run        Resolve target + verify package; do not clear anything
#   -h, --help       Show help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ANDROID_DIR}"

PACKAGE_NAME="org.outreach.app"
TARGET_SERIAL="${ANDROID_SERIAL:-}"
PICK=0
ALLOW_CLEAR_DATA=0
DRY_RUN=0

usage() {
  cat <<'EOF'
Clear Outreach app cache on a physical Android device.

Usage:
  bash android/scripts/clear-outreach-cache-on-device.sh --pick
  bash android/scripts/clear-outreach-cache-on-device.sh --serial <device-serial>
  bash android/scripts/clear-outreach-cache-on-device.sh --pick --clear-data

Options:
  --package NAME   Android package (default: org.outreach.app)
  --serial SERIAL  Target adb serial
  --pick           Pick first physical device
  --clear-data     Fallback to `pm clear` if cache-only clear fails
  --dry-run        Resolve target + verify package; do not clear anything
  -h, --help       Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)
      [[ $# -ge 2 ]] || { echo "error: --package requires a value" >&2; exit 2; }
      PACKAGE_NAME="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || { echo "error: --serial requires a value" >&2; exit 2; }
      TARGET_SERIAL="$2"
      shift 2
      ;;
    --pick)
      PICK=1
      shift
      ;;
    --clear-data)
      ALLOW_CLEAR_DATA=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"
export PATH="${ANDROID_HOME}/platform-tools:${PATH}"
ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
[[ -x "${ADB_BIN}" ]] || { echo "clear-outreach-cache-on-device: adb missing at ${ADB_BIN}" >&2; exit 1; }

pick_serial() {
  if [[ -n "${TARGET_SERIAL}" ]]; then
    echo "${TARGET_SERIAL}"
    return
  fi
  if [[ "${PICK}" -eq 1 ]]; then
    "${SCRIPT_DIR}/get-device-serial.sh" --physical | awk 'NR==1 {print; exit}'
    return
  fi
  local rows count
  rows="$("${ADB_BIN}" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')"
  count="$(printf '%s\n' "${rows}" | awk 'NF {n++} END {print n+0}')"
  if [[ "${count}" -eq 1 ]]; then
    printf '%s\n' "${rows}" | awk 'NF {print; exit}'
    return
  fi
  if [[ "${count}" -eq 0 ]]; then
    echo ""
    return
  fi
  echo "MULTIPLE"
}

TARGET_SERIAL="$(pick_serial)"
if [[ -z "${TARGET_SERIAL}" ]]; then
  echo "clear-outreach-cache-on-device: no physical device in adb 'device' state." >&2
  echo "Connect/authorize a device, or pass --serial." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" == "MULTIPLE" ]]; then
  echo "clear-outreach-cache-on-device: multiple physical devices detected." >&2
  echo "Pass --serial or use --pick." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" =~ ^emulator- ]]; then
  echo "clear-outreach-cache-on-device: this script targets physical devices only." >&2
  exit 1
fi

DEVICE_STATE="$("${ADB_BIN}" devices | awk -v s="${TARGET_SERIAL}" '$1==s {print $2}')"
if [[ "${DEVICE_STATE}" != "device" ]]; then
  echo "clear-outreach-cache-on-device: ${TARGET_SERIAL} is not in device state (got: ${DEVICE_STATE:-offline})." >&2
  exit 1
fi

PKG_PATH="$("${ADB_BIN}" -s "${TARGET_SERIAL}" shell pm path "${PACKAGE_NAME}" 2>/dev/null | tr -d '\r')"
if [[ -z "${PKG_PATH}" ]]; then
  echo "clear-outreach-cache-on-device: package not found on device: ${PACKAGE_NAME}" >&2
  exit 1
fi

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "dry-run: device=${TARGET_SERIAL} package=${PACKAGE_NAME} ready"
  exit 0
fi

"${ADB_BIN}" -s "${TARGET_SERIAL}" shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true

if "${ADB_BIN}" -s "${TARGET_SERIAL}" shell run-as "${PACKAGE_NAME}" sh -c 'rm -rf cache/* code_cache/* 2>/dev/null; mkdir -p cache code_cache' >/dev/null 2>&1; then
  echo "clear-outreach-cache-on-device: cache cleared via run-as for ${PACKAGE_NAME} on ${TARGET_SERIAL}"
  exit 0
fi

if [[ "${ALLOW_CLEAR_DATA}" -ne 1 ]]; then
  echo "clear-outreach-cache-on-device: cache-only clear failed (likely non-debuggable release build)." >&2
  echo "Re-run with --clear-data to use 'pm clear' (this wipes app data + cache)." >&2
  exit 1
fi

PM_CLEAR_OUT="$("${ADB_BIN}" -s "${TARGET_SERIAL}" shell pm clear "${PACKAGE_NAME}" 2>/dev/null | tr -d '\r')"
if [[ "${PM_CLEAR_OUT}" == *"Success"* ]]; then
  echo "clear-outreach-cache-on-device: cleared app data+cache for ${PACKAGE_NAME} on ${TARGET_SERIAL}"
  exit 0
fi
echo "clear-outreach-cache-on-device: pm clear failed for ${PACKAGE_NAME} on ${TARGET_SERIAL}" >&2
exit 1
