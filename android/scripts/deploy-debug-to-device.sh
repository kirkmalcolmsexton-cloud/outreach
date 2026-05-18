#!/usr/bin/env bash
# Build the debug APK, install it on a physical Android device, and launch the app
# (similar to "Run" in Android Studio or ios/scripts/deploy-device.sh).
#
# Usage (from repo root or android/):
#   bash android/scripts/deploy-debug-to-device.sh
#   bash android/scripts/deploy-debug-to-device.sh --serial <device-serial>
#   bash android/scripts/deploy-debug-to-device.sh --pick
#   OUTREACH_DEPLOY_SKIP_BUILD=1 bash android/scripts/deploy-debug-to-device.sh
#
# Prerequisites: USB debugging authorized; device shows as "device" in adb devices.
# Unlock the phone so the launcher activity can start.
#
# Environment:
#   ANDROID_SERIAL              Target device when set (unless --serial overrides)
#   OUTREACH_DEPLOY_SKIP_BUILD=1  Install existing app-debug.apk only (no Gradle build)
#   OUTREACH_APPLICATION_ID     Package name (default: org.outreach.app)
#   OUTREACH_LAUNCH_ACTIVITY    Activity class suffix (default: .MainActivity)
#
# Options:
#   --serial SERIAL   Target device serial (physical only)
#   --pick            Pick first physical device via get-device-serial.sh --pick
#   --apk PATH        APK to install when skipping build (default: app/build/outputs/apk/debug/app-debug.apk)
#   --no-launch       Install only; do not start the app
#   --reverse         Run adb reverse tcp:7747 for debug NDJSON ingest after install
#   -h, --help        Show help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ANDROID_DIR}"

PICK=0
NO_LAUNCH=0
DO_REVERSE=0
SKIP_BUILD=0
TARGET_SERIAL="${ANDROID_SERIAL:-}"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_ID="${OUTREACH_APPLICATION_ID:-org.outreach.app}"
LAUNCH_ACTIVITY="${OUTREACH_LAUNCH_ACTIVITY:-.MainActivity}"

if [[ "${OUTREACH_DEPLOY_SKIP_BUILD:-}" == "1" ]]; then
  SKIP_BUILD=1
fi

usage() {
  cat <<'EOF'
Build the debug APK, install on a physical Android device, and launch Outreach.

  bash android/scripts/deploy-debug-to-device.sh
  bash android/scripts/deploy-debug-to-device.sh --serial <device-serial>
  bash android/scripts/deploy-debug-to-device.sh --pick
  OUTREACH_DEPLOY_SKIP_BUILD=1 bash android/scripts/deploy-debug-to-device.sh

Prerequisites: USB debugging enabled and authorized; device state "device" in adb.

Options:
  --serial SERIAL   Target device serial (physical only)
  --pick            Pick first physical device (get-device-serial.sh --pick)
  --apk PATH        APK when skipping build (default: app/build/outputs/apk/debug/app-debug.apk)
  --no-launch       Install only
  --reverse         adb reverse tcp:7747 → host (debug ingest)
  -h, --help        Show help

Environment:
  ANDROID_SERIAL, OUTREACH_DEPLOY_SKIP_BUILD=1, OUTREACH_APPLICATION_ID, OUTREACH_LAUNCH_ACTIVITY
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "deploy-debug-to-device: --serial requires a value" >&2; exit 2; }
      TARGET_SERIAL="$2"
      shift 2
      ;;
    --pick)
      PICK=1
      shift
      ;;
    --apk)
      [[ $# -ge 2 ]] || { echo "deploy-debug-to-device: --apk requires a path" >&2; exit 2; }
      APK_PATH="$2"
      shift 2
      ;;
    --no-launch)
      NO_LAUNCH=1
      shift
      ;;
    --reverse)
      DO_REVERSE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "deploy-debug-to-device: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"
export PATH="${ANDROID_HOME}/platform-tools:${PATH}"

ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
[[ -x "${ADB_BIN}" ]] || { echo "deploy-debug-to-device: adb missing at ${ADB_BIN}" >&2; exit 1; }

resolve_serial() {
  if [[ -n "${TARGET_SERIAL}" ]]; then
    echo "${TARGET_SERIAL}"
    return
  fi
  if [[ "${PICK}" -eq 1 ]]; then
    "${SCRIPT_DIR}/get-device-serial.sh" --pick
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
    echo "" && return
  fi
  echo "MULTIPLE"
}

TARGET_SERIAL="$(resolve_serial)"
if [[ -z "${TARGET_SERIAL}" ]]; then
  echo "deploy-debug-to-device: no physical device detected in adb 'device' state." >&2
  echo "Connect a device, authorize USB debugging, or pass --serial / --pick." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" == "MULTIPLE" ]]; then
  echo "deploy-debug-to-device: multiple physical devices detected." >&2
  echo "Set ANDROID_SERIAL, pass --serial, or use --pick." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" =~ ^emulator- ]]; then
  echo "deploy-debug-to-device: this script targets physical devices only." >&2
  echo "For emulators, use Android Studio Run or ./gradlew installDebug with ANDROID_SERIAL set." >&2
  exit 1
fi

STATE="$("${ADB_BIN}" devices | awk -v s="${TARGET_SERIAL}" '$1==s {print $2}')"
if [[ "${STATE}" != "device" ]]; then
  echo "deploy-debug-to-device: serial ${TARGET_SERIAL} is not in device state (got: ${STATE:-offline})." >&2
  exit 1
fi

export ANDROID_SERIAL="${TARGET_SERIAL}"
echo "deploy-debug-to-device: target ${TARGET_SERIAL}"

chmod +x ./gradlew

if [[ "${SKIP_BUILD}" -eq 1 ]]; then
  if [[ ! -f "${APK_PATH}" ]]; then
    echo "deploy-debug-to-device: missing APK: ${APK_PATH}" >&2
    echo "Build first: bash android/scripts/build.sh debug" >&2
    echo "Or omit OUTREACH_DEPLOY_SKIP_BUILD to build and install via Gradle." >&2
    exit 1
  fi
  echo "deploy-debug-to-device: installing ${APK_PATH} (skip build) …"
  "${ADB_BIN}" -s "${TARGET_SERIAL}" install -r "${APK_PATH}"
else
  echo "deploy-debug-to-device: building and installing (installDebug) …"
  ./gradlew installDebug
  APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
fi

if [[ "${DO_REVERSE}" -eq 1 ]]; then
  echo "deploy-debug-to-device: adb reverse tcp:7747 (debug ingest) …"
  ANDROID_SERIAL="${TARGET_SERIAL}" bash "${SCRIPT_DIR}/adb-reverse-debug-ingest.sh"
fi

if [[ "${NO_LAUNCH}" -eq 0 ]]; then
  LAUNCH_CLASS="${LAUNCH_ACTIVITY}"
  if [[ "${LAUNCH_CLASS}" == .* ]]; then
    LAUNCH_CLASS="${PACKAGE_ID}${LAUNCH_CLASS}"
  fi
  COMPONENT="${PACKAGE_ID}/${LAUNCH_CLASS}"
  echo "deploy-debug-to-device: launching ${COMPONENT} …"
  if ! "${ADB_BIN}" -s "${TARGET_SERIAL}" shell am start -n "${COMPONENT}" >/dev/null 2>&1; then
    echo "deploy-debug-to-device: launch failed (is the device unlocked?). Open Outreach from the launcher or re-run." >&2
    exit 1
  fi
fi

echo "deploy-debug-to-device: OK (${TARGET_SERIAL})"
