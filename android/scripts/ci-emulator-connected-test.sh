#!/usr/bin/env bash
# Run after ReactiveCircus/android-emulator-runner boots the emulator.
# Kept as a real script because the action runs each YAML `script:` *line* as a separate `sh -c`
# (no persistent cwd); see outreach Android CI instrumentation job.
set -euo pipefail
ANDROID_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ANDROID_ROOT}"
chmod +x ./gradlew ./scripts/wait-for-adb-online.sh
SERIAL="$(adb devices | awk '/^emulator-/ { print $1; exit }')"
export ANDROID_SERIAL="${SERIAL:-emulator-5554}"
export ADB_WAIT_TIMEOUT="${ADB_WAIT_TIMEOUT:-300}"
./scripts/wait-for-adb-online.sh
n=0
until adb -s "${ANDROID_SERIAL}" shell pm path android >/dev/null 2>&1; do
  n=$((n + 1))
  if [[ "${n}" -gt 60 ]]; then
    echo "Timed out waiting for PackageManager (pm path android)."
    exit 1
  fi
  sleep 2
done
./gradlew connectedDebugAndroidTest -PoutreachAuthResolution=mock --no-daemon
