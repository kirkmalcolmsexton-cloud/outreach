#!/usr/bin/env bash
set -euo pipefail

SESSION_ID="a696b6"
LOG_PATH="/Users/aqeel/development/cursor/workspaces/initial/.cursor/debug-a696b6.log"
RUN_ID="build-tests-$(date +%s)-$$"

log_event() {
  local hypothesis_id="$1"
  local location="$2"
  local message="$3"
  local data_json="$4"
  local timestamp
  timestamp="$(($(date +%s) * 1000))"
  printf '{"sessionId":"%s","runId":"%s","hypothesisId":"%s","location":"%s","message":"%s","data":%s,"timestamp":%s}\n' \
    "$SESSION_ID" "$RUN_ID" "$hypothesis_id" "$location" "$message" "$data_json" "$timestamp" >> "$LOG_PATH"
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
android_dir="$(cd "$script_dir/../../../../android" && pwd)"
gradlew="$android_dir/gradlew"
sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"

resolve_android_tool() {
  local tool_name="$1"
  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return 0
  fi
  if [[ -x "$sdk_dir/platform-tools/$tool_name" ]]; then
    echo "$sdk_dir/platform-tools/$tool_name"
    return 0
  fi
  if [[ -x "$sdk_dir/emulator/$tool_name" ]]; then
    echo "$sdk_dir/emulator/$tool_name"
    return 0
  fi
  return 1
}

# Mode: emulator | physical (real USB device). Env: ANDROID_BUILD_TESTS_MODE. Optional first arg overrides env.
MODE="${ANDROID_BUILD_TESTS_MODE:-emulator}"
if [[ -n "${1:-}" ]]; then
  case "$1" in
    emulator) MODE=emulator ;;
    physical|device|real) MODE=physical ;;
    *)
      echo "Usage: $0 [emulator|physical]"
      echo "  or set ANDROID_BUILD_TESTS_MODE=emulator|physical"
      echo "  Optional: ANDROID_SERIAL=... to force a specific adb serial (overrides mode)."
      exit 2
      ;;
  esac
fi

# #region agent log
log_event "H0" "run-build-tests.sh:init" "runner_started" "{\"androidDir\":\"$android_dir\",\"mode\":\"$MODE\"}"
# #endregion

if [[ ! -x "$gradlew" ]]; then
  # #region agent log
  log_event "H2" "run-build-tests.sh:gradle" "gradlew_not_executable" "{\"path\":\"$gradlew\"}"
  # #endregion
  echo "Failure: gradlew is missing or not executable at $gradlew"
  exit 1
fi

adb_available=0
device_count=0
adb_bin=""
if adb_bin="$(resolve_android_tool adb)"; then
  adb_available=1
  device_count="$("$adb_bin" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
fi

pick_emulator_serial() {
  [[ -n "${adb_bin:-}" ]] || return 1
  "$adb_bin" devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit}'
}

pick_physical_serial() {
  [[ -n "${adb_bin:-}" ]] || return 1
  "$adb_bin" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}'
}

boot_emulator_if_possible() {
  local emulator_bin=""
  if ! emulator_bin="$(resolve_android_tool emulator)"; then
    # #region agent log
    log_event "H4" "run-build-tests.sh:emulator" "emulator_command_missing" "{\"attemptedBoot\":false,\"sdkDir\":\"$sdk_dir\"}"
    # #endregion
    return 1
  fi

  local avd_name
  avd_name="$("$emulator_bin" -list-avds | awk 'NR==1 {print; exit}')"
  if [[ -z "$avd_name" ]]; then
    # #region agent log
    log_event "H5" "run-build-tests.sh:emulator" "no_avd_configured" "{\"attemptedBoot\":false}"
    # #endregion
    return 1
  fi

  # #region agent log
  log_event "H6" "run-build-tests.sh:emulator" "starting_avd_boot" "{\"avd\":\"$avd_name\",\"emulatorBin\":\"$emulator_bin\"}"
  # #endregion
  nohup "$emulator_bin" -avd "$avd_name" -no-snapshot -no-boot-anim >/tmp/android-build-tests-emulator.log 2>&1 || true
  local wait_exit=0
  python3 - "$adb_bin" <<'PY'
import subprocess
import sys
adb_bin = sys.argv[1]
try:
    subprocess.run([adb_bin, "wait-for-device"], check=False, timeout=120)
    sys.exit(0)
except subprocess.TimeoutExpired:
    sys.exit(124)
PY
  wait_exit=$?
  # #region agent log
  log_event "H7" "run-build-tests.sh:emulator" "adb_wait_for_device_finished" "{\"exitCode\":$wait_exit}"
  # #endregion
  local post_boot_count
  post_boot_count="$("$adb_bin" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
  # #region agent log
  log_event "H6" "run-build-tests.sh:emulator" "avd_boot_attempt_finished" "{\"avd\":\"$avd_name\",\"deviceCountAfterBoot\":$post_boot_count}"
  # #endregion
  return 0
}

# #region agent log
log_event "H1" "run-build-tests.sh:devices" "device_probe_complete" "{\"adbAvailable\":$adb_available,\"deviceCount\":$device_count,\"adbBin\":\"$adb_bin\",\"mode\":\"$MODE\"}"
# #endregion

echo "Step 1/2: compiling androidTest sources"
compile_exit=0
if ! "$gradlew" -p "$android_dir" :app:compileDebugAndroidTestKotlin; then
  compile_exit=$?
fi

# #region agent log
log_event "H3" "run-build-tests.sh:compile" "android_test_compile_finished" "{\"exitCode\":$compile_exit}"
# #endregion

if [[ "$compile_exit" -ne 0 ]]; then
  echo "Build/tests failed at compile stage."
  exit "$compile_exit"
fi

echo "Step 2/2: running tests (mode=$MODE)"

target_serial=""
selection="none"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  target_serial="$ANDROID_SERIAL"
  selection="explicit_serial"
  # #region agent log
  log_event "H1" "run-build-tests.sh:tests" "target_from_env_android_serial" "{\"ANDROID_SERIAL\":\"$target_serial\",\"mode\":\"$MODE\"}"
  # #endregion
else
  case "$MODE" in
    emulator)
      target_serial="$(pick_emulator_serial || true)"
      if [[ -z "$target_serial" ]]; then
        boot_emulator_if_possible || true
        target_serial="$(pick_emulator_serial || true)"
      fi
      selection="emulator"
      ;;
    physical)
      target_serial="$(pick_physical_serial || true)"
      selection="physical"
      ;;
    *)
      echo "Invalid mode: $MODE"
      exit 1
      ;;
  esac
  # #region agent log
  log_event "H1" "run-build-tests.sh:tests" "target_resolved" "{\"selection\":\"$selection\",\"targetSerial\":\"${target_serial:-}\",\"mode\":\"$MODE\"}"
  # #endregion
fi

if [[ -n "$target_serial" ]]; then
  export ANDROID_SERIAL="$target_serial"
  # #region agent log
  log_event "H4" "run-build-tests.sh:tests" "running_connected_android_tests" "{\"task\":\":app:connectedDebugAndroidTest\",\"ANDROID_SERIAL\":\"$ANDROID_SERIAL\",\"mode\":\"$MODE\",\"selection\":\"$selection\"}"
  # #endregion
  echo "Using device: mode=$MODE ANDROID_SERIAL=$ANDROID_SERIAL (selection=$selection)"
  "$gradlew" -p "$android_dir" :app:connectedDebugAndroidTest
else
  # #region agent log
  log_event "H1" "run-build-tests.sh:tests" "no_target_fallback_to_unit_tests" "{\"task\":\":app:testDebugUnitTest\",\"mode\":\"$MODE\"}"
  # #endregion
  echo "No suitable device for mode=$MODE (and no ANDROID_SERIAL); running JVM unit tests only."
  "$gradlew" -p "$android_dir" :app:testDebugUnitTest
fi

# #region agent log
log_event "H0" "run-build-tests.sh:done" "runner_completed" "{\"status\":\"success\",\"mode\":\"$MODE\"}"
# #endregion

echo "Android build/tests command completed successfully."
