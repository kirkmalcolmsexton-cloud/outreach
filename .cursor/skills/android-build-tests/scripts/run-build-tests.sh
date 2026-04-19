#!/usr/bin/env bash
set -euo pipefail

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

if [[ ! -x "$gradlew" ]]; then
  echo "Failure: gradlew is missing or not executable at $gradlew"
  exit 1
fi

adb_bin=""
if _adb="$(resolve_android_tool adb)"; then
  adb_bin="$_adb"
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
    return 1
  fi

  local avd_name
  avd_name="$("$emulator_bin" -list-avds | awk 'NR==1 {print; exit}')"
  if [[ -z "$avd_name" ]]; then
    return 1
  fi

  nohup "$emulator_bin" -avd "$avd_name" -no-snapshot -no-boot-anim >/tmp/android-build-tests-emulator.log 2>&1 || true
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
  return 0
}

echo "Step 1/2: compiling androidTest sources"
compile_exit=0
if ! "$gradlew" -p "$android_dir" :app:compileDebugAndroidTestKotlin; then
  compile_exit=$?
fi

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
fi

if [[ -n "$target_serial" ]]; then
  export ANDROID_SERIAL="$target_serial"
  echo "Using device: mode=$MODE ANDROID_SERIAL=$ANDROID_SERIAL (selection=$selection)"
  "$gradlew" -p "$android_dir" :app:connectedDebugAndroidTest
else
  echo "No suitable device for mode=$MODE (and no ANDROID_SERIAL); running JVM unit tests only."
  "$gradlew" -p "$android_dir" :app:testDebugUnitTest
fi

echo "Android build/tests command completed successfully."
