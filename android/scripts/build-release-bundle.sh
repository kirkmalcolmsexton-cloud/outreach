#!/usr/bin/env bash
# Build signed release App Bundle (.aab) for Play upload. No arguments.
# Run from repo: bash android/scripts/build-release-bundle.sh
# Or from android/: ./scripts/build-release-bundle.sh
# Prerequisites:
# - JDK 17 (JAVA_HOME)
# - Android SDK: export ANDROID_HOME, or sdk.dir in android/local.properties, or a default SDK under $HOME
# - Release signing: android/keystore.properties (see keystore.properties.example), or
#   ANDROID_UPLOAD_KEYSTORE_PATH + ANDROID_UPLOAD_* env vars (same as CI).
# - If google-services.json / MAPS_API_KEY are missing, run ./scripts/setup-secrets.sh from android/ first.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."
# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"

./gradlew bundleRelease --no-daemon

AAB="$(pwd)/app/build/outputs/bundle/release/app-release.aab"
echo "AAB: $AAB"
