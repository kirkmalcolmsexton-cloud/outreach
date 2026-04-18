#!/usr/bin/env bash
# Block until adb reports device state "device" (not offline) and boot has completed.
# Use before: ./gradlew connectedDebugAndroidTest
#
#   ANDROID_SERIAL=emulator-5554 ./scripts/wait-for-adb-online.sh
#   ADB_WAIT_TIMEOUT=300 ./scripts/wait-for-adb-online.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
[[ -f "${SCRIPT_DIR}/android-sdk.env" ]] && source "${SCRIPT_DIR}/android-sdk.env"

ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "${ADB}" ]]; then
  echo "wait-for-adb-online: missing adb at ${ADB}; set ANDROID_HOME" >&2
  exit 1
fi

SERIAL="${ANDROID_SERIAL:-}"
TIMEOUT="${ADB_WAIT_TIMEOUT:-180}"
INTERVAL="${ADB_WAIT_INTERVAL:-2}"

if [[ -z "${SERIAL}" ]]; then
  # Default: first emulator line from adb devices
  SERIAL="$("${ADB}" devices | awk '/^emulator-/ { print $1; exit }')" || true
fi
if [[ -z "${SERIAL}" ]]; then
  echo "wait-for-adb-online: no emulator serial; start an AVD or set ANDROID_SERIAL" >&2
  exit 1
fi

echo "wait-for-adb-online: waiting for ${SERIAL} (timeout ${TIMEOUT}s) ..."
deadline=$((SECONDS + TIMEOUT))
while (( SECONDS < deadline )); do
  state="$("${ADB}" -s "${SERIAL}" get-state 2>/dev/null || echo unknown)"
  if [[ "${state}" == "device" ]]; then
    boot="$("${ADB}" -s "${SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo 0)"
    if [[ "${boot}" == "1" ]]; then
      echo "wait-for-adb-online: ${SERIAL} is online and boot completed."
      exit 0
    fi
  fi
  sleep "${INTERVAL}"
done

echo "wait-for-adb-online: timed out. Try: adb kill-server && adb start-server" >&2
echo "wait-for-adb-online: current state=$(${ADB} -s "${SERIAL}" get-state 2>/dev/null || echo missing)" >&2
exit 1
