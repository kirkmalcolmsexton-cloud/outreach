#!/usr/bin/env bash
# Manage the iOS Simulator for Outreach (create, boot, list, shutdown, xcodebuild destination).
# Apple calls this "Simulator", not an emulator; behavior mirrors android/scripts/start-outreach-emulator.sh.
#
# From repo root:
#   bash ios/scripts/simulator.sh create
#   bash ios/scripts/simulator.sh boot
#   bash ios/scripts/simulator.sh destination   # for OUTREACH_DESTINATION
#   bash ios/scripts/build.sh build
#
# Environment:
#   OUTREACH_XCODE           — e.g. /Applications/Xcode.app (sets DEVELOPER_DIR)
#   OUTREACH_IOS_SIM_NAME    — simulator display name (default: Outreach iPhone 16)
#   OUTREACH_IOS_DEVICE_TYPE — optional; full SimDeviceType id (default: newest common iPhone 16→15→14)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SIM_NAME="${OUTREACH_IOS_SIM_NAME:-Outreach iPhone 16}"

if [[ -n "${OUTREACH_XCODE:-}" ]]; then
  export DEVELOPER_DIR="${OUTREACH_XCODE}/Contents/Developer"
fi

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <command>

  list             List available simulators (xcrun simctl list devices available)
  list-runtimes    List installed iOS simulator runtimes
  create           Create "${SIM_NAME}" if it does not exist (needs an iOS runtime in Xcode → Settings → Platforms)
  start [udid]     create (if needed) then boot — closest match to android/scripts/start-outreach-emulator.sh
  boot [udid]      Open Simulator.app and boot this device (default: "${SIM_NAME}")
  shutdown <udid|all>  Shutdown one UDID or every simulator (all)
  destination      Print platform=iOS Simulator,id=<udid> for OUTREACH_DESTINATION (prefers "${SIM_NAME}")
  wait [udid]      Wait until the simulator finishes booting (after boot)

Environment: OUTREACH_XCODE, OUTREACH_IOS_SIM_NAME, OUTREACH_IOS_DEVICE_TYPE

See also: ios/scripts/build.sh (auto-picks an iPhone when OUTREACH_DESTINATION is unset)
EOF
}

require_simctl() {
  command -v xcrun >/dev/null 2>&1 || {
    echo "simulator: xcrun not found. Install Xcode." >&2
    exit 1
  }
  xcrun simctl list devices >/dev/null 2>&1 || {
    echo "simulator: simctl failed. Use full Xcode: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
    exit 1
  }
}

# Parse "    Name (UUID) (State) " from simctl list devices.
# Regex in a variable for Bash 3.2 (macOS); avoid [^)] inside [[ =~ ]] which breaks parsing.
sim_line_name_udid_state() {
  local line="$1"
  local re='^[[:space:]]*(.+)[[:space:]]+\(([A-Fa-f0-9-]{36})\)[[:space:]]+\((.*)\)[[:space:]]*$'
  [[ ${line} =~ ${re} ]] || return 1
  _s_name="${BASH_REMATCH[1]}"
  _s_udid="${BASH_REMATCH[2]}"
  _s_state="${BASH_REMATCH[3]}"
  return 0
}

find_udid_for_name() {
  local want="$1"
  local line
  while IFS= read -r line; do
    sim_line_name_udid_state "${line}" || continue
    if [[ "${_s_name}" == "${want}" ]]; then
      printf '%s\n' "${_s_udid}"
      return 0
    fi
  done < <(xcrun simctl list devices 2>/dev/null)
  return 1
}

latest_ios_runtime_id() {
  local r
  r="$(xcrun simctl list runtimes available 2>/dev/null | grep -Eo 'com\.apple\.CoreSimulator\.SimRuntime\.iOS-[0-9]+-[0-9]+' | sort -uV | tail -n 1)"
  if [[ -z "${r}" ]]; then
    echo "simulator: no iOS Simulator runtime installed. Xcode → Settings → Platforms → iOS … Simulator." >&2
    return 1
  fi
  printf '%s\n' "${r}"
}

default_iphone_device_type() {
  if [[ -n "${OUTREACH_IOS_DEVICE_TYPE:-}" ]]; then
    printf '%s\n' "${OUTREACH_IOS_DEVICE_TYPE}"
    return 0
  fi
  local list dt
  list="$(xcrun simctl list devicetypes 2>/dev/null)"
  for dt in \
    com.apple.CoreSimulator.SimDeviceType.iPhone-16 \
    com.apple.CoreSimulator.SimDeviceType.iPhone-15 \
    com.apple.CoreSimulator.SimDeviceType.iPhone-14; do
    if printf '%s\n' "${list}" | grep -q "${dt}"; then
      printf '%s\n' "${dt}"
      return 0
    fi
  done
  dt="$(printf '%s\n' "${list}" | grep -i 'iPhone' | grep -vi 'iPhone SE' | head -n 1 | sed -n 's/.*(\(com\.apple\.CoreSimulator\.SimDeviceType\.[^)]*\)).*/\1/p')"
  if [[ -z "${dt}" ]]; then
    echo "simulator: could not pick an iPhone device type from simctl list devicetypes" >&2
    return 1
  fi
  printf '%s\n' "${dt}"
}

cmd_create() {
  require_simctl
  local udid runtime dtype
  if udid="$(find_udid_for_name "${SIM_NAME}" || true)" && [[ -n "${udid}" ]]; then
    echo "simulator: already exists: ${SIM_NAME} (${udid})"
    return 0
  fi
  runtime="$(latest_ios_runtime_id)"
  dtype="$(default_iphone_device_type)"
  echo "simulator: creating \"${SIM_NAME}\" (${dtype} / ${runtime}) …"
  udid="$(xcrun simctl create "${SIM_NAME}" "${dtype}" "${runtime}")"
  echo "simulator: created ${udid}"
}

first_available_iphone_udid() {
  local line
  while IFS= read -r line; do
    sim_line_name_udid_state "${line}" || continue
    if [[ "${_s_name}" == iPhone* ]]; then
      printf '%s\n' "${_s_udid}"
      return 0
    fi
  done < <(xcrun simctl list devices available 2>/dev/null)
  return 1
}

resolve_boot_udid() {
  local u="${1:-}"
  if [[ -n "${u}" ]]; then
    printf '%s\n' "${u}"
    return 0
  fi
  local found
  found="$(find_udid_for_name "${SIM_NAME}" || true)"
  if [[ -n "${found}" ]]; then
    printf '%s\n' "${found}"
    return 0
  fi
  echo "simulator: no device named \"${SIM_NAME}\". Run: $(basename "$0") create" >&2
  exit 1
}

cmd_boot() {
  require_simctl
  local udid
  udid="$(resolve_boot_udid "${1:-}")"
  open -a Simulator >/dev/null 2>&1 || true
  echo "simulator: booting ${udid} …"
  xcrun simctl boot "${udid}" 2>/dev/null || true
  xcrun simctl bootstatus "${udid}" -b >/dev/null
  echo "simulator: ready (${udid})"
}

cmd_start() {
  cmd_create
  cmd_boot "${1:-}"
}

cmd_shutdown() {
  require_simctl
  local target="${1:-}"
  if [[ -z "${target}" ]]; then
    echo "simulator: pass a UDID or \"all\": $(basename "$0") shutdown <udid|all>" >&2
    exit 1
  fi
  if [[ "${target}" == "all" ]]; then
    echo "simulator: shutting down all …"
    xcrun simctl shutdown all 2>/dev/null || true
    return 0
  fi
  echo "simulator: shutting down ${target} …"
  xcrun simctl shutdown "${target}"
}

cmd_destination() {
  require_simctl
  local udid
  udid="$(find_udid_for_name "${SIM_NAME}" || true)"
  if [[ -z "${udid}" ]]; then
    udid="$(first_available_iphone_udid || true)"
  fi
  if [[ -z "${udid}" ]]; then
    echo "simulator: no \"${SIM_NAME}\" and no available iPhone simulator. Run: $(basename "$0") create && $(basename "$0") boot" >&2
    exit 1
  fi
  printf 'platform=iOS Simulator,id=%s\n' "${udid}"
}

cmd_wait() {
  require_simctl
  local udid
  udid="$(resolve_boot_udid "${1:-}")"
  xcrun simctl bootstatus "${udid}" -b >/dev/null
  echo "simulator: boot complete (${udid})"
}

main() {
  local cmd="${1:-}"
  [[ -n "${cmd}" ]] || {
    usage
    exit 1
  }
  case "${cmd}" in
    -h | --help | help)
      usage
      exit 0
      ;;
    list)
      require_simctl
      xcrun simctl list devices available
      ;;
    list-runtimes)
      require_simctl
      xcrun simctl list runtimes available
      ;;
    create)
      cmd_create
      ;;
    start)
      cmd_start "${2:-}"
      ;;
    boot)
      cmd_boot "${2:-}"
      ;;
    shutdown)
      cmd_shutdown "${2:-}"
      ;;
    destination)
      cmd_destination
      ;;
    wait)
      cmd_wait "${2:-}"
      ;;
    *)
      echo "simulator: unknown command: ${cmd}" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
