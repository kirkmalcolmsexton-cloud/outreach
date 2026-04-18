#!/usr/bin/env bash
# Print adb serial for the running Outreach default AVD (same name as start-outreach-emulator.sh).
#
# Usage:
#   ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"
#   ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)" ./gradlew connectedDebugAndroidTest
#
# Override AVD name (must match OUTREACH_AVD_NAME used when creating/starting the emulator):
#   OUTREACH_AVD_NAME=my_avd ./scripts/resolve-outreach-emulator-serial.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
[[ -f "${SCRIPT_DIR}/android-sdk.env" ]] && source "${SCRIPT_DIR}/android-sdk.env"

ANDROID_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "${ANDROID_ROOT}" ]]; then
  echo "resolve-outreach-emulator-serial: set ANDROID_HOME or ANDROID_SDK_ROOT" >&2
  exit 1
fi

ADB="${ANDROID_ROOT}/platform-tools/adb"
if [[ ! -x "${ADB}" ]]; then
  echo "resolve-outreach-emulator-serial: missing adb at ${ADB}" >&2
  exit 1
fi

AVD_NAME="${OUTREACH_AVD_NAME:-Galaxy_S938U_API36_x86_64}"

avd_name_for_serial() {
  local serial="$1"
  local raw
  raw="$("${ADB}" -s "${serial}" emu avd name 2>/dev/null || true)"
  raw="$(printf '%s' "${raw}" | tr -d '\r')"
  # Some adb builds emit a leading "OK" line.
  printf '%s\n' "${raw}" | grep -v '^OK$' | grep -v '^ *$' | head -n1
}

matches=()
while read -r serial state _; do
  [[ "${serial}" =~ ^emulator- ]] || continue
  [[ "${state}" == "device" ]] || continue
  resolved="$(avd_name_for_serial "${serial}")"
  if [[ "${resolved}" == "${AVD_NAME}" ]]; then
    matches+=("${serial}")
  fi
done < <("${ADB}" devices)

if [[ "${#matches[@]}" -eq 1 ]]; then
  printf '%s\n' "${matches[0]}"
  exit 0
fi

if [[ "${#matches[@]}" -gt 1 ]]; then
  echo "resolve-outreach-emulator-serial: multiple emulators report AVD ${AVD_NAME}: ${matches[*]}" >&2
  echo "Tip: stop extra emulators or set ANDROID_SERIAL explicitly." >&2
  exit 1
fi

echo "resolve-outreach-emulator-serial: no online emulator matched AVD \"${AVD_NAME}\"." >&2
echo "Start it from android/: ./scripts/start-outreach-emulator.sh" >&2
echo "Then wait until boot: ./scripts/wait-for-adb-online.sh (omit ANDROID_SERIAL to use the first emulator-* line)." >&2
exit 1
