#!/usr/bin/env bash
# Build the Outreach iOS app with xcodebuild (same project as in Xcode: ios/Outreach/Outreach.xcodeproj).
#
# Typical use (from any directory):
#   ./ios/scripts/build.sh
#   ./ios/scripts/build.sh test
#   ./ios/scripts/build.sh clean
#
# Prerequisites: Full Xcode (not only CommandLineTools) — xcode-select -s /Applications/Xcode.app/...
# Optional: set OUTREACH_XCODE to your Xcode .app if non-default.
#
# If xcodebuild aborts with IDESimulatorFoundation / DVTDownloads errors, run once:
#   xcodebuild -runFirstLaunch
#   (or open Xcode.app and let it finish installing components.)
#
# Environment:
#   OUTREACH_XCODE  — e.g. /Applications/Xcode.app
#   OUTREACH_DERIVED_DATA — override DerivedData path (default: build/DerivedData under ios/)
#   OUTREACH_DESTINATION — full override for xcodebuild -destination (skips auto sim pick).
#   OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH=1 — run xcodebuild -runFirstLaunch before build (one-time; fixes DVT/IDESimulator issues)
#   OUTREACH_SKIP_SPM_RESOLVE=1 — skip -resolvePackageDependencies (not recommended; CLI needs this for Firebase/GoogleSignIn SPM)

set -euo pipefail

# First available iPhone simulator UDID (empty if none). Needs an installed iOS Simulator runtime.
outreach_first_iphone_simulator_udid() {
  local line
  while IFS= read -r line; do
    if [[ "$line" =~ iPhone.*\(([A-Fa-f0-9-]{36})\) ]]; then
      echo "${BASH_REMATCH[1]}"
      return 0
    fi
  done < <(xcrun simctl list devices available 2>/dev/null)
  return 1
}

# Resolves -destination for Simulator builds when OUTREACH_DESTINATION is unset.
# Prefers platform=iOS Simulator,id=<udid> because generic/platform=iOS Simulator fails when no runtime
# matches (xcodebuild then only shows the device placeholder / "iOS … is not installed").
outreach_resolve_simulator_destination() {
  if [[ -n "${OUTREACH_DESTINATION:-}" ]]; then
    printf '%s\n' "${OUTREACH_DESTINATION}"
    return 0
  fi
  local udid
  udid="$(outreach_first_iphone_simulator_udid || true)"
  if [[ -n "${udid}" ]]; then
    echo "platform=iOS Simulator,id=${udid}"
    return 0
  fi
  echo "generic/platform=iOS Simulator"
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJ="${IOS_DIR}/Outreach/Outreach.xcodeproj"
SCHEME="Outreach"
DD="${OUTREACH_DERIVED_DATA:-${IOS_DIR}/build/DerivedData}"
ACTION="${1:-build}"

if [[ -n "${OUTREACH_XCODE:-}" ]]; then
  export DEVELOPER_DIR="${OUTREACH_XCODE}/Contents/Developer"
fi

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [build|clean|test|archive]

  build   — xcodebuild build (default), Debug. Picks the first available iPhone Simulator (simctl) when
            OUTREACH_DESTINATION is unset — avoids generic/platform=iOS Simulator when no runtime resolves.
            Override: OUTREACH_DESTINATION=… (e.g. generic/platform=iOS after installing the iOS platform).
  clean   — xcodebuild clean (same destination resolution as build)
  test    — xcodebuild test (same Simulator resolution as build)
  archive — xcodebuild archive (generic iOS device; requires iOS device platform in Xcode)

  Project: ${PROJ}
  Scheme:  ${SCHEME}

  Simulator helper: ios/scripts/simulator.sh (create, start, destination for OUTREACH_DESTINATION).
  If no simulators appear: Xcode → Settings → Platforms → install the **iOS … Simulator** runtime for your Xcode.
  If you see: DVTPlugInLoading / DVTDownloads / "Abort trap: 6": xcodebuild -runFirstLaunch (or OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH=1).

  Swift packages (Firebase, Google Sign-In) are resolved via  xcodebuild -resolvePackageDependencies  before build
  (same as File → Packages → Resolve in Xcode). Needs network on first run.
EOF
}

if [[ "${ACTION}" == "-h" || "${ACTION}" == "--help" ]]; then
  usage
  exit 0
fi

command -v xcodebuild >/dev/null 2>&1 || {
  echo "build-ios: xcodebuild not found. Install full Xcode and run: xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
}
[[ -d "${PROJ}" ]] || {
  echo "build-ios: missing ${PROJ}" >&2
  exit 1
}

cd "${IOS_DIR}/Outreach"
mkdir -p "${DD}"

if [[ "${OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH:-}" == "1" ]]; then
  xcodebuild -runFirstLaunch || true
fi

XCODE_VERSION="$(xcodebuild -version 2>/dev/null | head -1 || true)"
if [[ -z "${XCODE_VERSION}" ]]; then
  echo "build-ios: xcodebuild is not the full Xcode stack (e.g. only CommandLineTools). Select Xcode: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
fi

_dest_source="override"
DEST=""
case "${ACTION}" in
  build|clean|test)
    DEST="$(outreach_resolve_simulator_destination)"
    if [[ -n "${OUTREACH_DESTINATION:-}" ]]; then
      _dest_source="env"
    elif [[ "${DEST}" == platform=iOS\ Simulator,id=* ]]; then
      _dest_source="simctl_udid"
    else
      _dest_source="generic_sim"
    fi
    ;;
  archive)
    DEST="${OUTREACH_DESTINATION:-generic/platform=iOS}"
    if [[ -n "${OUTREACH_DESTINATION:-}" ]]; then
      _dest_source="env"
    else
      _dest_source="archive_device"
    fi
    ;;
  *)
    ;;
esac

if [[ "${_dest_source}" == "generic_sim" ]] && [[ "${ACTION}" == "build" || "${ACTION}" == "clean" || "${ACTION}" == "test" ]]; then
  echo "build-ios: warning: no iPhone appeared in 'xcrun simctl list devices available'. Install an iOS Simulator runtime (Xcode → Settings → Platforms), then re-run." >&2
fi

# Swift Package Manager: CLI builds do not fetch packages until -resolvePackageDependencies runs (unlike opening Xcode).
if [[ "${ACTION}" == "build" || "${ACTION}" == "clean" || "${ACTION}" == "test" || "${ACTION}" == "archive" ]]; then
  if [[ "${OUTREACH_SKIP_SPM_RESOLVE:-}" != "1" ]]; then
    echo "build-ios: resolving Swift package dependencies…" >&2
    if ! xcodebuild \
      -project "Outreach.xcodeproj" \
      -scheme "${SCHEME}" \
      -resolvePackageDependencies \
      -derivedDataPath "${DD}"
    then
      echo "build-ios: Swift package resolution failed (network, GitHub access, or package graph). Try again online or open Xcode → File → Packages → Resolve Package Versions." >&2
      exit 1
    fi
  fi
fi

xcodebuild_failed() {
  echo "" >&2
  echo "build-ios: xcodebuild failed. Remedies:" >&2
  echo "  • DVT / IDESimulatorFoundation / DVTDownloads / 'Abort trap: 6': run  xcodebuild -runFirstLaunch" >&2
  echo "    (or OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH=1 once), then re-run." >&2
  echo "  • 'Unable to find a destination' for Simulator: install an **iOS Simulator** runtime:" >&2
  echo "    Xcode → Settings → Platforms → iOS … Simulator (or Components in older Xcode)." >&2
  echo "    Check: xcrun simctl list runtimes   (empty list ⇒ install a runtime)" >&2
  echo "  • 'iOS … is not installed' for **Any iOS Device**: install that **iOS** *device* platform (same UI)." >&2
  echo "  • Override destination: OUTREACH_DESTINATION='platform=iOS Simulator,id=<udid>'  (xcrun simctl list devices)" >&2
  echo "  • 'Missing package product' (FirebaseCore, etc.): ensure network; script runs -resolvePackageDependencies first." >&2
  echo "    Or: open Xcode → File → Packages → Resolve. OUTREACH_SKIP_SPM_RESOLVE=1 skips resolve (usually wrong)." >&2
}

case "${ACTION}" in
  build)
    if ! xcodebuild \
      -project "Outreach.xcodeproj" \
      -scheme "${SCHEME}" \
      -configuration Debug \
      -destination "${DEST}" \
      -derivedDataPath "${DD}" \
      -quiet \
      build
    then
      xcodebuild_failed
      exit 1
    fi
    echo "build-ios: OK (${DD})"
    ;;
  clean)
    if ! xcodebuild \
      -project "Outreach.xcodeproj" \
      -scheme "${SCHEME}" \
      -configuration Debug \
      -destination "${DEST}" \
      -derivedDataPath "${DD}" \
      clean
    then
      xcodebuild_failed
      exit 1
    fi
    echo "build-ios: clean OK"
    ;;
  test)
    if ! xcodebuild \
      -project "Outreach.xcodeproj" \
      -scheme "${SCHEME}" \
      -configuration Debug \
      -destination "${DEST}" \
      -derivedDataPath "${DD}" \
      -quiet \
      test
    then
      xcodebuild_failed
      exit 1
    fi
    echo "build-ios: tests finished"
    ;;
  archive)
    OUT_ARCHIVE="${OUTREACH_ARCHIVE_DIR:-${IOS_DIR}/build}"
    mkdir -p "${OUT_ARCHIVE}"
    if ! xcodebuild \
      -project "Outreach.xcodeproj" \
      -scheme "${SCHEME}" \
      -configuration Release \
      -destination "${DEST}" \
      -derivedDataPath "${DD}" \
      -archivePath "${OUT_ARCHIVE}/Outreach.xcarchive" \
      archive
    then
      xcodebuild_failed
      exit 1
    fi
    echo "build-ios: archived to ${OUT_ARCHIVE}/Outreach.xcarchive (signing must be configured in Xcode)"
    ;;
  *)
    echo "build-ios: unknown action: ${ACTION}" >&2
    usage
    exit 1
    ;;
esac
