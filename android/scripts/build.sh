#!/usr/bin/env bash
# Build debug APK. No arguments.
# Run from repo: bash android/scripts/build.sh
# Or from android/: ./scripts/build.sh
# Prerequisites:
# - JDK 17 (JAVA_HOME)
# - Android SDK: export ANDROID_HOME, or sdk.dir in android/local.properties, or a default SDK under $HOME

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."
# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"

./gradlew assembleDebug

APK="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
echo "APK: $APK"
