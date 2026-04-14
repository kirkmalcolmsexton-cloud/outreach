#!/usr/bin/env bash
# Build debug APK. Prerequisites:
# - JDK 17 (JAVA_HOME)
# - Android SDK with platform + build-tools (ANDROID_HOME or ANDROID_SDK_ROOT)

set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then echo "Error: Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path." >&2
  exit 1
fi

./gradlew assembleDebug

APK="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
echo "APK: $APK"
