#!/usr/bin/env bash
# Single entry for Android builds: debug APK, signed release bundle, and CI (same as
# .github/workflows/android.yml). Other scripts in this directory delegate here.
# Usage:  build.sh <command> [args]
#   build.sh debug
#   build.sh release-bundle
#   build.sh ci verify|build-instrumented-apks|connected-mock
# From repo: bash android/scripts/build.sh …   — from android/: ./scripts/build.sh …
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="${SCRIPT_DIR}/.."
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cmd_debug() {
  cd "${ANDROID_DIR}"
  # shellcheck source=load-android-sdk-env.sh
  source "${SCRIPT_DIR}/load-android-sdk-env.sh"
  chmod +x ./gradlew
  ./gradlew assembleDebug
  echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
}

# Subshell: trap/Temp cleanup matches legacy standalone `build-release-bundle.sh` behavior.
cmd_release_bundle() (
  set -euo pipefail
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  ANDROID_DIR="${SCRIPT_DIR}/.."
  REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
  cd "${ANDROID_DIR}"
  # shellcheck source=load-android-sdk-env.sh
  source "${SCRIPT_DIR}/load-android-sdk-env.sh"

  command -v jq >/dev/null || {
    echo "build: (release-bundle) install jq" >&2
    exit 1
  }
  command -v age >/dev/null || {
    echo "build: (release-bundle) install age — https://github.com/FiloSottile/age" >&2
    exit 1
  }

  RUNNER_TEMP="${RUNNER_TEMP:-$(mktemp -d -t outreach-release.XXXXXX)}"
  OUTREACH_JSON="${RUNNER_TEMP}/outreach-secrets.json"
  UPLOAD_JKS="${RUNNER_TEMP}/outreach-upload.jks"

  cleanup() {
    if [[ -n "${_TMP_DIR_CREATED:-}" && "${_TMP_DIR_CREATED}" == "1" && -d "${RUNNER_TEMP}" ]]; then
      rm -rf "${RUNNER_TEMP}"
    fi
  }
  trap cleanup EXIT
  if [[ "${RUNNER_TEMP}" == /tmp/outreach-release.* || "${RUNNER_TEMP}" == /private/tmp/outreach-release.* ]]; then
    _TMP_DIR_CREATED="1"
  else
    _TMP_DIR_CREATED="${_TMP_DIR_CREATED:-0}"
  fi

  if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" && -f "${HOME}/etc/outreach.env" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "${HOME}/etc/outreach.env"
    set +a
  fi

  if [[ -z "${OUTREACH_SECRETS_PASSPHRASE:-}" ]]; then
    echo "build: (release-bundle) OUTREACH_SECRETS_PASSPHRASE is required (same as CI)." >&2
    echo "Set it directly or add it to ~/etc/outreach.env." >&2
    exit 1
  fi

  export OUTREACH_EXPORT_PLAINTEXT_JSON="${OUTREACH_JSON}"
  bash "${SCRIPT_DIR}/setup-secrets.sh"
  jq empty "${OUTREACH_JSON}" >/dev/null

  KS_AGE="${REPO_ROOT}/secrets/upload-keystore.jks.age"
  repo_ok=false
  if [[ -f "${KS_AGE}" ]] && jq -e '
  .android_upload_signing
  | type == "object"
  and (.key_alias | type == "string" and length > 0)
  and (.keystore_password | type == "string" and length > 0)
  and (.key_password | type == "string" and length > 0)
' "${OUTREACH_JSON}" >/dev/null 2>&1; then
    repo_ok=true
  fi

  legacy_ok=false
  if [[ -n "${ANDROID_UPLOAD_KEYSTORE_BASE64:-}" && -n "${ANDROID_UPLOAD_KEYSTORE_PASSWORD:-}" && -n "${ANDROID_UPLOAD_KEY_ALIAS:-}" && -n "${ANDROID_UPLOAD_KEY_PASSWORD:-}" ]]; then
    legacy_ok=true
  fi

  if [[ "${repo_ok}" == true ]]; then
    SIGNING_MODE="repo"
  elif [[ "${legacy_ok}" == true ]]; then
    SIGNING_MODE="legacy"
  else
    echo "build: (release-bundle) Release signing not configured (same rules as CI)." >&2
    echo "- Repo mode requires: secrets/upload-keystore.jks.age and android_upload_signing in secrets JSON." >&2
    echo "- Legacy mode requires all 4 env vars: ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_KEYSTORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD." >&2
    echo "To initialize repo mode: OUTREACH_SIGNING_REPO_MODE=1 bash android/scripts/create-upload-keystore-and-gh-secrets.sh" >&2
    exit 1
  fi

  if [[ "${SIGNING_MODE}" == "repo" ]]; then
    bash "${REPO_ROOT}/scripts/decrypt-age-passphrase.sh" "${KS_AGE}" "${UPLOAD_JKS}"
    if ! [[ -s "${UPLOAD_JKS}" ]]; then
      echo "build: (release-bundle) decrypted keystore missing/empty at ${UPLOAD_JKS}" >&2
      exit 1
    fi
    export ANDROID_UPLOAD_KEYSTORE_PASSWORD
    ANDROID_UPLOAD_KEYSTORE_PASSWORD="$(jq -r '.android_upload_signing.keystore_password' "${OUTREACH_JSON}")"
    export ANDROID_UPLOAD_KEY_ALIAS
    ANDROID_UPLOAD_KEY_ALIAS="$(jq -r '.android_upload_signing.key_alias' "${OUTREACH_JSON}")"
    export ANDROID_UPLOAD_KEY_PASSWORD
    ANDROID_UPLOAD_KEY_PASSWORD="$(jq -r '.android_upload_signing.key_password' "${OUTREACH_JSON}")"
  else
    if base64 --help 2>&1 | grep -q '\-d'; then
      printf '%s' "${ANDROID_UPLOAD_KEYSTORE_BASE64}" | base64 -d > "${UPLOAD_JKS}"
    else
      printf '%s' "${ANDROID_UPLOAD_KEYSTORE_BASE64}" | base64 -D > "${UPLOAD_JKS}"
    fi
    if ! [[ -s "${UPLOAD_JKS}" ]]; then
      echo "build: (release-bundle) decoded keystore missing/empty at ${UPLOAD_JKS}" >&2
      exit 1
    fi
  fi
  export ANDROID_UPLOAD_KEYSTORE_PATH="${UPLOAD_JKS}"

  ./gradlew bundleRelease --no-daemon
  echo "AAB: $(pwd)/app/build/outputs/bundle/release/app-release.aab"
)

# --- CI (identical to previous android-ci.sh) ---------------------------------
ci_place_firebase() {
  cp -f app/google-services.json.example app/google-services.json
}

ci_ensure() {
  cd "${ANDROID_DIR}"
  export MAPS_API_KEY="${MAPS_API_KEY:-ci-placeholder-maps-not-used-at-runtime}"
  # shellcheck source=load-android-sdk-env.sh
  source "${SCRIPT_DIR}/load-android-sdk-env.sh"
}

cmd_ci_verify() {
  ci_ensure
  ci_place_firebase
  chmod +x ./gradlew
  ./gradlew check --no-daemon
}

cmd_ci_build_instrumented_apks() {
  ci_ensure
  ci_place_firebase
  chmod +x ./gradlew
  ./gradlew assembleDebug assembleDebugAndroidTest -PoutreachAuthResolution=mock --no-daemon
}

cmd_ci_connected_mock() {
  ci_ensure
  ci_place_firebase
  chmod +x ./gradlew ./scripts/wait-for-adb-online.sh
  SERIAL="$(adb devices | awk '/^emulator-/ { print $1; exit }')"
  export ANDROID_SERIAL="${SERIAL:-emulator-5554}"
  export ADB_WAIT_TIMEOUT="${ADB_WAIT_TIMEOUT:-300}"
  ./scripts/wait-for-adb-online.sh
  n=0
  until adb -s "${ANDROID_SERIAL}" shell pm path android >/dev/null 2>&1; do
    n=$((n + 1))
    if [[ "${n}" -gt 60 ]]; then
      echo "build: (ci connected-mock) Timed out waiting for PackageManager (pm path android)." >&2
      exit 1
    fi
    sleep 2
  done
  ./gradlew connectedDebugAndroidTest -PoutreachAuthResolution=mock --no-daemon
}

usage() {
  cat <<'USAGE' >&2
Usage: build.sh <command> [args]

  debug
      Debug APK: assembleDebug (load-android-sdk, same as before).
  release-bundle
      Signed release .aab: setup-secrets, keystore, bundleRelease (same as CI / old build-release-bundle.sh).
  ci verify
  ci build-instrumented-apks
  ci connected-mock
      Same as GitHub Android CI. Replaces app/google-services.json with the example; optional MAPS_API_KEY
      default matches the workflow. ci connected-mock runs after an emulator is booted.

  Aliases: `android-ci.sh` → `build.sh ci`, `build-release-bundle.sh` → `build.sh release-bundle`.
USAGE
  exit 1
}

case "${1:-}" in
  debug) cmd_debug ;;
  release-bundle) cmd_release_bundle ;;
  ci)
    case "${2:-}" in
      verify) cmd_ci_verify ;;
      build-instrumented-apks) cmd_ci_build_instrumented_apks ;;
      connected-mock) cmd_ci_connected_mock ;;
      *) usage ;;
    esac
    ;;
  -h | --help | help) usage ;;
  "")
    usage
    ;;
  *)
    echo "build: unknown command: $1" >&2
    usage
    ;;
esac
