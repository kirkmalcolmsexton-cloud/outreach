#!/usr/bin/env bash
# Start an Android emulator if needed, run a full Gradle build, then instrumentation tests.
# Intended to be run with no arguments (defaults only). Optional env overrides below.
#
# Prerequisites: Android SDK (sdk.dir in android/local.properties or ANDROID_HOME), one AVD
# (Android Studio → Device Manager) unless a device/emulator is already connected via adb.
#
# Environment (all optional):
#   ANDROID_HOME / ANDROID_SDK_ROOT  SDK root (defaults to sdk.dir in android/local.properties)
#   OUTREACH_EMULATOR_AVD            AVD name to start; default: first AVD from "emulator -list-avds"
#   OUTREACH_EMULATOR_ARGS           Extra args passed to the emulator binary (quoted string)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ANDROID_DIR}"

read_sdk_dir() {
  local f="${ANDROID_DIR}/local.properties"
  if [[ -f "$f" ]]; then
    grep -E '^sdk\.dir=' "$f" | head -1 | sed 's/^sdk\.dir=//' | tr -d '\r'
  fi
}

resolve_android_home() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "${ANDROID_HOME}"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "${ANDROID_SDK_ROOT}"
    return
  fi
  local from_props
  from_props="$(read_sdk_dir)"
  if [[ -n "$from_props" ]]; then
    echo "$from_props"
    return
  fi
  echo ""
}

ANDROID_HOME="$(resolve_android_home)"
export ANDROID_HOME
if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME" ]]; then
  echo "run-emulator-tests: Set ANDROID_HOME or add sdk.dir=... to android/local.properties" >&2
  exit 1
fi

export PATH="${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools:${PATH}"

EMULATOR_BIN="${ANDROID_HOME}/emulator/emulator"
ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "run-emulator-tests: Missing emulator at ${EMULATOR_BIN}" >&2
  exit 1
fi
if [[ ! -x "$ADB_BIN" ]]; then
  echo "run-emulator-tests: Missing adb at ${ADB_BIN}" >&2
  exit 1
fi

if [[ ! -f "${ANDROID_DIR}/app/google-services.json" ]]; then
  if [[ -f "${ANDROID_DIR}/app/google-services.json.example" ]]; then
    echo "run-emulator-tests: Copying app/google-services.json.example → app/google-services.json"
    cp "${ANDROID_DIR}/app/google-services.json.example" "${ANDROID_DIR}/app/google-services.json"
  else
    echo "run-emulator-tests: Missing app/google-services.json (and no .example). Run ./scripts/setup-secrets.sh or add Firebase config." >&2
    exit 1
  fi
fi

device_ready() {
  # Count lines ending in <tab>device (not offline/unauthorized)
  "$ADB_BIN" devices 2>/dev/null | awk '/\tdevice$/ { n++ } END { print n+0 }'
}

wait_for_boot() {
  local i
  for i in $(seq 1 90); do
    local boot
    boot="$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot" == "1" ]]; then
      # Allow system services to settle
      sleep 3
      return 0
    fi
    sleep 2
  done
  echo "run-emulator-tests: Timed out waiting for emulator boot (sys.boot_completed)." >&2
  return 1
}

STARTED_EMULATOR_PID=""

cleanup() {
  if [[ -n "$STARTED_EMULATOR_PID" ]] && kill -0 "$STARTED_EMULATOR_PID" 2>/dev/null; then
    echo "run-emulator-tests: Stopping emulator we started (pid ${STARTED_EMULATOR_PID})"
    "$ADB_BIN" emu kill 2>/dev/null || true
    kill "$STARTED_EMULATOR_PID" 2>/dev/null || true
    wait "$STARTED_EMULATOR_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if [[ "$(device_ready)" -ge 1 ]]; then
  echo "run-emulator-tests: Using existing device/emulator (adb already has a device)."
else
  # First line from -list-avds (bash 3.2–compatible; avoid mapfile)
  FIRST_AVD="$("$EMULATOR_BIN" -list-avds 2>/dev/null | head -1)"
  if [[ -z "${FIRST_AVD}" ]]; then
    echo "run-emulator-tests: No AVD found. Create a virtual device in Android Studio (Device Manager), then re-run." >&2
    exit 1
  fi

  AVD_NAME="${OUTREACH_EMULATOR_AVD:-${FIRST_AVD}}"
  echo "run-emulator-tests: Starting AVD \"${AVD_NAME}\" (set OUTREACH_EMULATOR_AVD to override)..."

  # shellcheck disable=SC2086
  "$EMULATOR_BIN" -avd "$AVD_NAME" -no-audio -no-boot-anim ${OUTREACH_EMULATOR_ARGS:-} &
  STARTED_EMULATOR_PID=$!

  echo "run-emulator-tests: Waiting for adb device..."
  "$ADB_BIN" wait-for-device
  wait_for_boot
fi

echo "run-emulator-tests: Full build (compile, unit tests, lint) + instrumentation (mock auth)..."
chmod +x "${ANDROID_DIR}/gradlew"
${ANDROID_DIR}/gradlew build connectedDebugAndroidTest \
  -PoutreachAuthResolution=mock \
  --no-daemon

echo "run-emulator-tests: Done."
