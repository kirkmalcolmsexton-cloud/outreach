#!/usr/bin/env bash
# Build Outreach for a physical iPhone, install the .app, and launch it (like "Run" in Xcode).
# Uses xcodebuild (ios/scripts/build.sh) and xcrun devicectl (Xcode 15+).
#
# From repo root:
#   bash ios/scripts/deploy-device.sh
#   OUTREACH_DEVICE_UDID=<udid> bash ios/scripts/deploy-device.sh
#   bash ios/scripts/deploy-device.sh --udid <udid>
#   bash ios/scripts/deploy-device.sh --pick
#
# Prerequisites: Full Xcode; iPhone connected and trusted; Developer Mode on device; signing
# team/provisioning the same as when you Run from Xcode to that device. On first sideload, trust
# the developer on the device: Settings → General → VPN & Device Management → Trust.
#
# Environment (inherits build.sh where noted):
#   OUTREACH_XCODE, OUTREACH_DERIVED_DATA, OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH,
#   OUTREACH_SKIP_SPM_RESOLVE, OUTREACH_BUNDLE_ID,
#   OUTREACH_APP_NAME           — .app under Debug-iphoneos (default: Outreach)
#   OUTREACH_DEVICE_UDID         — target device; overrides when set (unless --udid; see order below)
#   OUTREACH_DESTINATION         — if set to platform=iOS,id=<id>, that id is used when not set by flags
#   OUTREACH_DEPLOY_SKIP_BUILD=1 — skip xcodebuild; install/launch last Debug-iphoneos build in DerivedData
#   OUTREACH_DEVELOPMENT_TEAM  — (or DEVELOPMENT_TEAM) Apple team id for signing; same as in Xcode for Run → device
#   ~/etc/outreach.env           — if present, sourced (set -a) before build; place OUTREACH_DEVELOPMENT_TEAM=… here
#   (build.sh) OUTREACH_XCODE_ALLOW_PROVISIONING_UPDATES — default on; pass 0 to omit -allowProvisioningUpdates (CI)
#
set -euo pipefail

if [[ -f "${HOME}/etc/outreach.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "${HOME}/etc/outreach.env"
  set +a
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DD="${OUTREACH_DERIVED_DATA:-${IOS_DIR}/build/DerivedData}"
BUNDLE_ID="${OUTREACH_BUNDLE_ID:-org.outreach.ios}"
APP_NAME="${OUTREACH_APP_NAME:-Outreach}"
APP="${DD}/Build/Products/Debug-iphoneos/${APP_NAME}.app"

if [[ -n "${OUTREACH_XCODE:-}" ]]; then
  export DEVELOPER_DIR="${OUTREACH_XCODE}/Contents/Developer"
fi

UDID_CMD=""
PICK=0

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [options]

  Builds Debug for a physical iOS device (ios/scripts/build.sh), installs with devicectl,
  launches ${BUNDLE_ID}.

  Prerequisites: Xcode 15+ (xcrun devicectl), USB or network-paired iPhone, trusted, Developer
  Mode, and automatic or manual signing that works in Xcode for this device and scheme.

  First time per developer on this iPhone: after install, iOS may block launch until you trust the
  dev certificate — on the device: Settings → General → VPN & Device Management (or "Device
  Management") → your Developer App certificate → Trust. Then launch the app again or re-run
  this script.

  Picking a device (first match wins):
  1) --udid <id>   (hardware / DVT id from Xcode, e.g. 00008140-… — not always the same as devicectl’s "Identifier" column)
  2) OUTREACH_DEVICE_UDID
  3) OUTREACH_DESTINATION=platform=iOS,id=<id>
  4) If exactly one device in devicectl JSON — hardware id is read from JSON (e.g. *.coredevice.local hostname)
  5) --pick — first device in that list
     If more than one device is connected, set an id or use --pick.

  Options:
  --udid <id>     Use this device (same id Xcode uses for Run → device)
  --pick          Use first device from devicectl list
  -h, --help      This help

  OUTREACH_DEPLOY_SKIP_BUILD=1  — do not run xcodebuild; only install+launch (expects existing ${APP_NAME}.app)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --udid)
      [[ $# -ge 2 ]] || {
        echo "deploy-device: --udid requires a value" >&2
        exit 2
      }
      UDID_CMD="$2"
      shift 2
      ;;
    --pick)
      PICK=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "deploy-device: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

command -v xcrun >/dev/null 2>&1 || {
  echo "deploy-device: xcrun not found. Install full Xcode (not only CommandLineTools)." >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  echo "deploy-device: python3 not found (needed to parse devicectl JSON)." >&2
  exit 1
}

if ! xcrun devicectl help >/dev/null 2>&1; then
  echo "deploy-device: xcrun devicectl not available. Install Xcode 15+ and select it:" >&2
  echo "  sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
fi

udid_from_destination() {
  local d="$1"
  # xcodebuild accepts id= — value is either a 36-char UUID (Core Simulator) or hardware ECID (e.g. 00008140-…)
  if [[ "${d}" =~ id=([A-Fa-f0-9-]+) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

# Prints one xcodebuild/xcode-compatible device id per line (hardware ECID / DVT id), not devicectl's Core Device service UUID.
# See: devicectl "Identifier" vs xcodebuild -destination id= (must match "platform:iOS, id:00008140-…" from -showdestinations).
list_devicectl_xcode_ids() {
  local json="$1"
  python3 - <<'PY' "${json}"
import json, re, sys

RE_ECID = re.compile(r"^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$")

def xcode_id_for_device(d):
    if not isinstance(d, dict):
        return None

    def walk(obj):
        if isinstance(obj, str):
            if obj.endswith(".coredevice.local"):
                head = obj.split(".")[0]
                if RE_ECID.match(head):
                    return head
            if RE_ECID.match(obj):
                return obj
        elif isinstance(obj, dict):
            for v in obj.values():
                w = walk(v)
                if w:
                    return w
        elif isinstance(obj, list):
            for v in obj:
                w = walk(v)
                if w:
                    return w
        return None

    return walk(d)

path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
devs = data.get("result", {}).get("devices", [])
if not isinstance(devs, list):
    sys.exit(0)
out = []
for d in devs:
    if not isinstance(d, dict):
        continue
    xid = xcode_id_for_device(d)
    if xid:
        out.append(xid)
if not out and devs:
    print(
        "deploy-device: devicectl listed",
        len(devs),
        "device(s) but no hardware/ECID id for xcodebuild in JSON. Use the iOS id from Xcode (Devices) or: xcodebuild -showdestinations -project … -scheme … — then set OUTREACH_DEVICE_UDID.",
        file=sys.stderr,
    )
    sys.exit(2)
for x in out:
    print(x)
PY
}

TARGET_UDID=""

if [[ -n "${UDID_CMD}" ]]; then
  TARGET_UDID="${UDID_CMD}"
elif [[ -n "${OUTREACH_DEVICE_UDID:-}" ]]; then
  TARGET_UDID="${OUTREACH_DEVICE_UDID}"
elif [[ -n "${OUTREACH_DESTINATION:-}" ]] && [[ "${OUTREACH_DESTINATION}" != *"iOS Simulator"* ]] &&
  _u="$(udid_from_destination "${OUTREACH_DESTINATION}" 2>/dev/null)" && [[ -n "${_u}" ]]; then
  TARGET_UDID="${_u}"
else
  jf="$(mktemp -t outreach-devicectl.XXXXXX.json)"
  if ! xcrun devicectl list devices --json-output "${jf}"; then
    rm -f "${jf}"
    echo "deploy-device: xcrun devicectl list devices failed. Is a device connected and trusted?" >&2
    exit 1
  fi
  set +e
  ucid_rc=0
  udid_lines="$(list_devicectl_xcode_ids "${jf}")"
  ucid_rc=$?
  set -e
  rm -f "${jf}"
  if [[ "${ucid_rc}" -eq 2 ]]; then
    exit 1
  fi
  if [[ "${ucid_rc}" -ne 0 ]]; then
    echo "deploy-device: could not list xcode device ids (exit ${ucid_rc})" >&2
    exit 1
  fi
  udid_count="$(printf '%s\n' "${udid_lines}" | awk 'NF' | wc -l | tr -d ' ')"
  if [[ -z "${udid_count}" || "${udid_count}" -eq 0 ]]; then
    echo "deploy-device: no devices in devicectl list. Connect an iPhone, trust this Mac, enable Developer Mode." >&2
    echo "If the list is empty but Xcode shows the device, open Xcode and run to the device once to finish pairing." >&2
    echo "Set OUTREACH_DEVICE_UDID or pass --udid (see Xcode → Window → Devices and Simulators for the identifier)." >&2
    exit 1
  fi
  if [[ "${PICK}" -eq 1 ]]; then
    TARGET_UDID="$(printf '%s\n' "${udid_lines}" | awk 'NF {print; exit}')"
  elif [[ "${udid_count}" -eq 1 ]]; then
    TARGET_UDID="$(printf '%s\n' "${udid_lines}" | awk 'NF {print; exit}')"
  else
    echo "deploy-device: multiple devices connected; choose one: pass --udid, set OUTREACH_DEVICE_UDID, or use --pick." >&2
    printf '%s\n' "${udid_lines}" >&2
    exit 1
  fi
fi

if [[ -z "${TARGET_UDID}" ]]; then
  echo "deploy-device: could not resolve device UDID." >&2
  exit 1
fi

export OUTREACH_DESTINATION="platform=iOS,id=${TARGET_UDID}"

if [[ "${OUTREACH_DEPLOY_SKIP_BUILD:-}" == "1" ]]; then
  echo "deploy-device: skipping build (OUTREACH_DEPLOY_SKIP_BUILD=1)" >&2
else
  echo "deploy-device: building (destination ${OUTREACH_DESTINATION}) …" >&2
  bash "${SCRIPT_DIR}/build.sh" build
fi

if [[ ! -d "${APP}" ]]; then
  echo "deploy-device: missing ${APP} — build first or check OUTREACH_APP_NAME / OUTREACH_DERIVED_DATA" >&2
  exit 1
fi

echo "deploy-device: installing ${APP_NAME}.app on ${TARGET_UDID} …" >&2
xcrun devicectl device install app --device "${TARGET_UDID}" "${APP}"

echo "deploy-device: launching ${BUNDLE_ID} …" >&2
xcrun devicectl device process launch --device "${TARGET_UDID}" "${BUNDLE_ID}"

echo "deploy-device: OK"
