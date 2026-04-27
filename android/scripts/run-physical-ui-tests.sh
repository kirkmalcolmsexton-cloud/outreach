#!/usr/bin/env bash
# Run connected Android instrumentation tests on a physical device (USB or authorized wireless).
# Interactive prompts when the device is missing, unauthorized, or ambiguous; optional adb reverse
# for debug ingest on port 7747. See docs/testing-guide.md.
#
# Usage (from repo anywhere; script cds to android/):
#   ./scripts/run-physical-ui-tests.sh
#   ./scripts/run-physical-ui-tests.sh --non-interactive --pick --auth mock
#
# Environment:
#   ANDROID_HOME / ANDROID_SDK_ROOT  SDK root (defaults to sdk.dir in android/local.properties)
#   ANDROID_SERIAL                   If set, uses this device (must not be an emulator serial)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ANDROID_DIR}"

NON_INTERACTIVE=0
PICK=0
AUTH_RESOLUTION="mock"
AUTH_FROM_CLI=0
NO_REVERSE=0
EXTRA_GRADLE=()

usage() {
  cat <<'EOF'
Run connected Android instrumentation tests on a physical device (USB or authorized wireless).
Interactive prompts when the device is missing, unauthorized, or ambiguous; optional adb reverse
for debug ingest on port 7747. See docs/testing-guide.md.

Usage (from android/ or any cwd):
  ./scripts/run-physical-ui-tests.sh
  ./scripts/run-physical-ui-tests.sh --non-interactive --pick --auth mock

Environment:
  ANDROID_HOME / ANDROID_SDK_ROOT  SDK root (defaults to sdk.dir in android/local.properties)
  ANDROID_SERIAL                   If set, uses this device (must not be an emulator serial)

Options:
  --non-interactive   Fail fast if no usable physical device or multiple devices (unless ANDROID_SERIAL set)
  --pick              Pick ANDROID_SERIAL via get-device-serial.sh --pick (physical preferred)
  --auth mock|real    Instrumentation auth resolution (default: mock)
  --no-reverse        Do not prompt for adb reverse tcp:7747 (debug ingest)
  -h, --help          Show this help

Arguments after "--" are passed through to Gradle (e.g. -Pandroid.testInstrumentationRunnerArguments.class=...).

Examples:
  ./scripts/run-physical-ui-tests.sh
  ./scripts/run-physical-ui-tests.sh --auth real
  ./scripts/run-physical-ui-tests.sh --non-interactive --pick --
  ./scripts/run-physical-ui-tests.sh -- \
    -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellAutomationTest
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --non-interactive)
      NON_INTERACTIVE=1
      shift
      ;;
    --pick)
      PICK=1
      shift
      ;;
    --auth)
      if [[ $# -lt 2 ]]; then
        echo "error: --auth requires mock or real" >&2
        exit 2
      fi
      AUTH_RESOLUTION="$2"
      AUTH_FROM_CLI=1
      shift 2
      ;;
    --no-reverse)
      NO_REVERSE=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_GRADLE=("$@")
      break
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "$AUTH_RESOLUTION" in
  mock | real) ;;
  *)
    echo "error: --auth must be mock or real (got: ${AUTH_RESOLUTION})" >&2
    exit 2
    ;;
esac

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
  echo "run-physical-ui-tests: Set ANDROID_HOME or add sdk.dir=... to android/local.properties" >&2
  exit 1
fi

export PATH="${ANDROID_HOME}/platform-tools:${PATH}"

ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
if [[ ! -x "$ADB_BIN" ]]; then
  echo "run-physical-ui-tests: Missing adb at ${ADB_BIN}" >&2
  exit 1
fi

physical_device_count() {
  "$ADB_BIN" devices 2>/dev/null | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { n++ } END { print n+0 }'
}

device_state_for_serial() {
  local serial="$1"
  "$ADB_BIN" devices 2>/dev/null | awk -v s="$serial" '$1==s { print $2 }'
}

is_emulator_serial() {
  [[ "$1" =~ ^emulator- ]]
}

ensure_google_services_placeholder() {
  if [[ ! -f "${ANDROID_DIR}/app/google-services.json" ]]; then
    if [[ -f "${ANDROID_DIR}/app/google-services.json.example" ]]; then
      echo "run-physical-ui-tests: Copying app/google-services.json.example → app/google-services.json"
      cp "${ANDROID_DIR}/app/google-services.json.example" "${ANDROID_DIR}/app/google-services.json"
    else
      echo "run-physical-ui-tests: Missing app/google-services.json (and no .example). Run ./scripts/setup-secrets.sh or add Firebase config." >&2
      exit 1
    fi
  fi
}

wait_for_physical_device_interactive() {
  local attempt=0
  while [[ "$(physical_device_count)" -eq 0 ]]; do
    if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
      echo "error: no physical device in 'device' state. Connect USB, enable debugging, authorize." >&2
      exit 1
    fi
    attempt=$((attempt + 1))
    if [[ "$attempt" -gt 60 ]]; then
      echo "error: timed out waiting for a physical device." >&2
      exit 1
    fi
    echo ""
    echo "No authorized physical device detected (emulators do not count for this script)."
    echo "  • Enable Developer options and USB debugging"
    echo "  • Accept the USB debugging prompt on the phone"
    echo "  • Prefer \"Stay awake\" while charging for long test runs"
    read -r -p "Press Enter when the device shows as \"device\" in adb (Ctrl+C to abort)... " _
    "$ADB_BIN" start-server >/dev/null 2>&1 || true
  done
}

resolve_android_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    local st
    st="$(device_state_for_serial "$ANDROID_SERIAL")"
    if [[ "$st" != "device" ]]; then
      echo "error: ANDROID_SERIAL=${ANDROID_SERIAL} is not in \"device\" state (got: ${st:-offline})." >&2
      exit 1
    fi
    if is_emulator_serial "$ANDROID_SERIAL"; then
      echo "error: This script targets physical hardware. ANDROID_SERIAL=${ANDROID_SERIAL} looks like an emulator." >&2
      echo "  Use ./scripts/run-emulator-tests.sh for emulators, or unset ANDROID_SERIAL." >&2
      exit 1
    fi
    return
  fi

  wait_for_physical_device_interactive

  local count
  count="$(physical_device_count)"
  if [[ "$count" -eq 0 ]]; then
    echo "error: still no physical device." >&2
    exit 1
  fi

  if [[ "$count" -eq 1 ]]; then
    ANDROID_SERIAL="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }')"
    export ANDROID_SERIAL
    return
  fi

  # Multiple physical devices
  if [[ "$PICK" -eq 1 ]]; then
    ANDROID_SERIAL="$("${ANDROID_DIR}/scripts/get-device-serial.sh" --physical | head -1)"
    if [[ -z "$ANDROID_SERIAL" ]]; then
      ANDROID_SERIAL="$("${ANDROID_DIR}/scripts/get-device-serial.sh" --pick)"
    fi
    export ANDROID_SERIAL
    return
  fi

  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    echo "error: multiple physical devices connected; set ANDROID_SERIAL or pass --pick." >&2
    "${ANDROID_DIR}/scripts/get-device-serial.sh" || true
    exit 1
  fi

  echo ""
  "${ANDROID_DIR}/scripts/get-device-serial.sh"
  echo ""
  local line
  while true; do
    read -r -p "Enter adb serial for the phone to use: " line || true
    line="$(echo "$line" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [[ -z "$line" ]] && continue
    st="$(device_state_for_serial "$line")"
    if [[ "$st" != "device" ]]; then
      echo "  That serial is not ready (state: ${st:-missing}). Try again."
      continue
    fi
    if is_emulator_serial "$line"; then
      echo "  Choose a physical device serial, not an emulator."
      continue
    fi
    ANDROID_SERIAL="$line"
    export ANDROID_SERIAL
    break
  done
}

prompt_auth_if_interactive() {
  if [[ "$NON_INTERACTIVE" -eq 1 ]] || [[ "$AUTH_FROM_CLI" -eq 1 ]]; then
    return
  fi
  if [[ "${AUTH_RESOLUTION}" != "mock" ]]; then
    return
  fi
  echo ""
  read -r -p "Auth resolution [mock/real] (default mock): " ans || true
  ans="$(echo "$ans" | tr '[:upper:]' '[:lower:]' | tr -d '\r')"
  [[ -z "$ans" ]] && return
  case "$ans" in
    mock | real) AUTH_RESOLUTION="$ans" ;;
    *)
      echo "  Unrecognized value; keeping mock."
      ;;
  esac
}

maybe_prompt_reverse() {
  if [[ "$NO_REVERSE" -eq 1 ]] || [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    return
  fi
  echo ""
  read -r -p "Run adb reverse for debug ingest (device localhost:7747 → host :7747)? [y/N] " r || true
  r="$(echo "$r" | tr '[:upper:]' '[:lower:]' | tr -d '\r')"
  if [[ "$r" == "y" || "$r" == "yes" ]]; then
    local rev="${REPO_ROOT}/android/scripts/adb-reverse-debug-ingest.sh"
    if [[ ! -x "$rev" ]]; then
      chmod +x "$rev" 2>/dev/null || true
    fi
    if [[ ! -f "$rev" ]]; then
      echo "warning: missing ${rev}; skipping reverse." >&2
      return
    fi
    (cd "${REPO_ROOT}" && ANDROID_SERIAL="${ANDROID_SERIAL}" bash "$rev")
  fi
}

maybe_pause_for_real_auth() {
  if [[ "$AUTH_RESOLUTION" != "real" ]]; then
    return
  fi
  echo ""
  echo "Auth resolution is **real**: tests use Firebase on the device like production."
  echo "Sign in with Google in the app before or during tests that expect a logged-in user."
  echo "See docs/ui-testing.md (mock vs real)."
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    return
  fi
  read -r -p "Press Enter when ready to run Gradle connected tests... " _
}

ensure_google_services_placeholder
resolve_android_serial

echo ""
echo "run-physical-ui-tests: Using ANDROID_SERIAL=${ANDROID_SERIAL}"
prompt_auth_if_interactive
maybe_prompt_reverse
maybe_pause_for_real_auth

chmod +x "${ANDROID_DIR}/gradlew"

${ANDROID_DIR}/gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest \
  -PoutreachAuthResolution="${AUTH_RESOLUTION}" \
  "${EXTRA_GRADLE[@]}"

echo "run-physical-ui-tests: Done."
