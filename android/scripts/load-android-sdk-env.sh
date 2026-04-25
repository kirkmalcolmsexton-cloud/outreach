#!/usr/bin/env bash
# Resolve ANDROID_HOME / ANDROID_SDK_ROOT for Gradle CLI when not already set.
# Same resolution order as scripts/run-emulator-tests.sh:
#   1) ANDROID_HOME  2) ANDROID_SDK_ROOT  3) sdk.dir in android/local.properties
#   4) ~/Library/Android/sdk (macOS Studio default)  5) ~/Android/Sdk (common Linux)
# Usage (from another script after cd to android/ Gradle root):
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "${SCRIPT_DIR}/load-android-sdk-env.sh"

set -euo pipefail

_SDK_HELPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_ANDROID_GRADLE_ROOT="$(cd "${_SDK_HELPER_DIR}/.." && pwd)"

_read_sdk_dir_from_local_properties() {
  local f="${_ANDROID_GRADLE_ROOT}/local.properties"
  if [[ -f "$f" ]]; then
    grep -E '^sdk\.dir=' "$f" | head -1 | sed 's/^sdk\.dir=//' | tr -d '\r'
  fi
}

_resolve_android_home() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "${ANDROID_HOME}"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    echo "${ANDROID_SDK_ROOT}"
    return
  fi
  local from_props
  from_props="$(_read_sdk_dir_from_local_properties)"
  if [[ -n "$from_props" ]]; then
    echo "$from_props"
    return
  fi
  local d
  for d in "${HOME}/Library/Android/sdk" "${HOME}/Android/Sdk"; do
    if [[ -d "$d" ]]; then
      echo "$d"
      return
    fi
  done
  echo ""
}

_h="$(_resolve_android_home)"
if [[ -z "$_h" || ! -d "$_h" ]]; then
  echo "Error: Android SDK not found. Set ANDROID_HOME, or add sdk.dir=... to android/local.properties (Android Studio → Settings → SDK location)." >&2
  exit 1
fi
export ANDROID_HOME="$_h"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
