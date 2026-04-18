#!/usr/bin/env bash
# Start the Outreach default AVD; create it with avdmanager if missing.
# Requires ANDROID_HOME or ANDROID_SDK_ROOT (same as Android Studio / CLI tools).
#
# Override name: OUTREACH_AVD_NAME=my_avd ./scripts/start-outreach-emulator.sh
# Matching adb serial when the emulator is running: ./scripts/resolve-outreach-emulator-serial.sh
# Extra emulator flags: ./scripts/start-outreach-emulator.sh -no-snapshot-load

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
if [[ -f "${SCRIPT_DIR}/android-sdk.env" ]]; then
  source "${SCRIPT_DIR}/android-sdk.env"
fi

ANDROID_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "${ANDROID_ROOT}" ]]; then
  echo "start-outreach-emulator: set ANDROID_HOME or ANDROID_SDK_ROOT" >&2
  exit 1
fi

# sdkmanager / avdmanager resolve the SDK via ANDROID_SDK_ROOT or ANDROID_HOME (not --sdk_root on create avd).
export ANDROID_SDK_ROOT="${ANDROID_ROOT}"
export ANDROID_HOME="${ANDROID_ROOT}"

EMULATOR_BIN="${ANDROID_ROOT}/emulator/emulator"
resolve_cmdline_bin() {
  local name="$1"
  local path
  for path in \
    "${ANDROID_ROOT}/cmdline-tools/latest/bin/${name}" \
    "${ANDROID_ROOT}/cmdline-tools/bin/${name}" \
    "${ANDROID_ROOT}/tools/bin/${name}"; do
    if [[ -x "${path}" ]]; then
      echo "${path}"
      return 0
    fi
  done
  echo ""
  return 1
}

SDKMANAGER="$(resolve_cmdline_bin sdkmanager || true)"
AVDMANAGER="$(resolve_cmdline_bin avdmanager || true)"

if [[ ! -x "${EMULATOR_BIN}" ]]; then
  echo "start-outreach-emulator: missing emulator at ${EMULATOR_BIN}" >&2
  exit 1
fi
if [[ -z "${SDKMANAGER}" || -z "${AVDMANAGER}" ]]; then
  echo "start-outreach-emulator: install Android SDK Command-line Tools (sdkmanager/avdmanager)." >&2
  exit 1
fi

# Values aligned with scripts/outreach-emulator.snapshot.ini (Google Play / API 36 / x86_64 / pixel_9).
AVD_NAME="${OUTREACH_AVD_NAME:-Galaxy_S938U_API36_x86_64}"
SYSIMG_PKG="system-images;android-36;google_apis_playstore;x86_64"
DEVICE_ID="pixel_9"

AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
INI_PATH="${AVD_HOME}/${AVD_NAME}.ini"

avd_exists() {
  [[ -f "${INI_PATH}" ]] && [[ -d "${AVD_HOME}/${AVD_NAME}.avd" ]]
}

ensure_system_image() {
  echo "start-outreach-emulator: ensuring system image ${SYSIMG_PKG} ..."
  # Accept licenses once if needed (harmless if already done).
  yes | "${SDKMANAGER}" --licenses >/dev/null 2>&1 || true
  "${SDKMANAGER}" "${SYSIMG_PKG}"
}

create_avd() {
  echo "start-outreach-emulator: creating AVD ${AVD_NAME} (${SYSIMG_PKG}, device ${DEVICE_ID}) ..."
  ensure_system_image
  # Non-interactive: decline custom hardware profile.
  echo no | "${AVDMANAGER}" create avd \
    -n "${AVD_NAME}" \
    -k "${SYSIMG_PKG}" \
    -d "${DEVICE_ID}" \
    --force
}

if ! avd_exists; then
  create_avd
fi

exec "${EMULATOR_BIN}" -avd "${AVD_NAME}" "$@"
