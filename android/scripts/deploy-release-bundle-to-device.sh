#!/usr/bin/env bash
# Install a signed release bundle on a physical Android device.
# Expects the .aab produced by build-release-bundle.sh (default path below). Run build-release-bundle.sh first.
# Uses bundletool to generate a device-specific APK set from app-release.aab.
#
# Usage:
#   bash android/scripts/build-release-bundle.sh   # produces app/build/outputs/bundle/release/app-release.aab
#   bash android/scripts/deploy-release-bundle-to-device.sh
#   bash android/scripts/deploy-release-bundle-to-device.sh --serial <device-serial>
#   bash android/scripts/deploy-release-bundle-to-device.sh --bundle /path/to/app-release.aab
#
# Options:
#   --aab PATH              Path to .aab (default: android/app/build/outputs/bundle/release/app-release.aab)
#   --bundle PATH           Alias for --aab
#   --serial SERIAL         Target device serial (physical device only)
#   --pick                  Auto-pick first physical device if serial not provided
#   --bundletool-jar PATH   Use an existing bundletool jar
#   --keep-apks             Keep generated .apks file
#   -h, --help              Show help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ANDROID_DIR}"

KEEP_APKS=0
PICK=0
TARGET_SERIAL="${ANDROID_SERIAL:-}"
BUNDLETOOL_JAR="${BUNDLETOOL_JAR:-}"
AAB_PATH="${ANDROID_DIR}/app/build/outputs/bundle/release/app-release.aab"
DEFAULT_BUNDLETOOL_VERSION="1.17.2"
DEFAULT_BUNDLETOOL_JAR="${HOME}/.cache/outreach/bundletool-all-${DEFAULT_BUNDLETOOL_VERSION}.jar"

usage() {
  cat <<'EOF'
Install a signed release bundle on a physical Android device.

Build the .aab first (same output path as CI):
  bash android/scripts/build-release-bundle.sh

Then deploy (default AAB: android/app/build/outputs/bundle/release/app-release.aab):
  bash android/scripts/deploy-release-bundle-to-device.sh
  bash android/scripts/deploy-release-bundle-to-device.sh --serial <device-serial>
  bash android/scripts/deploy-release-bundle-to-device.sh --bundle /path/to/app-release.aab

Options:
  --aab PATH              Path to .aab (default: android/app/build/outputs/bundle/release/app-release.aab)
  --bundle PATH           Alias for --aab
  --serial SERIAL         Target device serial (physical device only)
  --pick                  Auto-pick first physical device if serial not provided
  --bundletool-jar PATH   Use an existing bundletool jar
  --keep-apks             Keep generated .apks file
  -h, --help              Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aab|--bundle)
      [[ $# -ge 2 ]] || { echo "error: --aab/--bundle requires a path" >&2; exit 2; }
      AAB_PATH="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || { echo "error: --serial requires a value" >&2; exit 2; }
      TARGET_SERIAL="$2"
      shift 2
      ;;
    --pick)
      PICK=1
      shift
      ;;
    --bundletool-jar)
      [[ $# -ge 2 ]] || { echo "error: --bundletool-jar requires a path" >&2; exit 2; }
      BUNDLETOOL_JAR="$2"
      shift 2
      ;;
    --keep-apks)
      KEEP_APKS=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

# shellcheck source=load-android-sdk-env.sh
source "${SCRIPT_DIR}/load-android-sdk-env.sh"
export PATH="${ANDROID_HOME}/platform-tools:${PATH}"

ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
[[ -x "${ADB_BIN}" ]] || { echo "deploy-release-bundle-to-device: adb missing at ${ADB_BIN}" >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "deploy-release-bundle-to-device: java not found on PATH." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "deploy-release-bundle-to-device: curl not found on PATH." >&2; exit 1; }

resolve_serial() {
  if [[ -n "${TARGET_SERIAL}" ]]; then
    echo "${TARGET_SERIAL}"
    return
  fi
  if [[ "${PICK}" -eq 1 ]]; then
    "${SCRIPT_DIR}/get-device-serial.sh" --physical | awk 'NR==1 {print; exit}'
    return
  fi
  local rows count
  rows="$("${ADB_BIN}" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')"
  count="$(printf '%s\n' "${rows}" | awk 'NF {n++} END {print n+0}')"
  if [[ "${count}" -eq 1 ]]; then
    printf '%s\n' "${rows}" | awk 'NF {print; exit}'
    return
  fi
  if [[ "${count}" -eq 0 ]]; then
    echo "" && return
  fi
  echo "MULTIPLE"
}

TARGET_SERIAL="$(resolve_serial)"
if [[ -z "${TARGET_SERIAL}" ]]; then
  echo "deploy-release-bundle-to-device: no physical device detected in adb 'device' state." >&2
  echo "Connect a device and authorize USB debugging, or pass --serial." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" == "MULTIPLE" ]]; then
  echo "deploy-release-bundle-to-device: multiple physical devices detected." >&2
  echo "Set ANDROID_SERIAL, pass --serial, or use --pick." >&2
  exit 1
fi
if [[ "${TARGET_SERIAL}" =~ ^emulator- ]]; then
  echo "deploy-release-bundle-to-device: this script targets physical devices only." >&2
  exit 1
fi

STATE="$("${ADB_BIN}" devices | awk -v s="${TARGET_SERIAL}" '$1==s {print $2}')"
if [[ "${STATE}" != "device" ]]; then
  echo "deploy-release-bundle-to-device: serial ${TARGET_SERIAL} is not in device state (got: ${STATE:-offline})." >&2
  exit 1
fi

if [[ ! -f "${AAB_PATH}" ]]; then
  echo "deploy-release-bundle-to-device: missing AAB: ${AAB_PATH}" >&2
  echo "Run: bash android/scripts/build-release-bundle.sh" >&2
  echo "Or pass: --aab /path/to/app-release.aab" >&2
  exit 1
fi

resolve_bundletool() {
  if command -v bundletool >/dev/null 2>&1; then
    echo "bundletool"
    return
  fi
  local jar="${BUNDLETOOL_JAR:-${DEFAULT_BUNDLETOOL_JAR}}"
  if [[ -f "${jar}" ]]; then
    echo "${jar}"
    return
  fi
  mkdir -p "$(dirname "${jar}")"
  local url="https://github.com/google/bundletool/releases/download/${DEFAULT_BUNDLETOOL_VERSION}/bundletool-all-${DEFAULT_BUNDLETOOL_VERSION}.jar"
  echo "Downloading bundletool ${DEFAULT_BUNDLETOOL_VERSION} ..."
  curl -fsSL "${url}" -o "${jar}"
  echo "${jar}"
}

BUNDLETOOL_RESOLVED="$(resolve_bundletool)"
if [[ "${BUNDLETOOL_RESOLVED}" == "bundletool" ]]; then
  BUNDLETOOL_CMD=(bundletool)
else
  BUNDLETOOL_CMD=(java -jar "${BUNDLETOOL_RESOLVED}")
fi

APKS_PATH="${ANDROID_DIR}/app/build/outputs/bundle/release/app-release-device-${TARGET_SERIAL}.apks"
cleanup() {
  if [[ "${KEEP_APKS}" -ne 1 && -f "${APKS_PATH}" ]]; then
    rm -f "${APKS_PATH}"
  fi
}
trap cleanup EXIT

echo "Target serial: ${TARGET_SERIAL}"
echo "AAB: ${AAB_PATH}"
echo "APKS: ${APKS_PATH}"

"${BUNDLETOOL_CMD[@]}" build-apks \
  --bundle="${AAB_PATH}" \
  --output="${APKS_PATH}" \
  --connected-device \
  --device-id="${TARGET_SERIAL}" \
  --overwrite

"${BUNDLETOOL_CMD[@]}" install-apks \
  --apks="${APKS_PATH}" \
  --device-id="${TARGET_SERIAL}"

echo "deploy-release-bundle-to-device: install completed for ${TARGET_SERIAL}"
