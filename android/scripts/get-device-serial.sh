#!/usr/bin/env bash
set -euo pipefail

# Print ADB serial numbers for devices in the "device" state (authorized, ready).
# Resolves adb from PATH, or ANDROID_SDK_ROOT / ANDROID_HOME / default macOS SDK location.

usage() {
  cat <<'EOF'
Usage: get-device-serial.sh [OPTION]

Without options, prints a short table of connected devices (serial, model, type).

Options:
  --serial       One serial per line (all connected devices/emulators).
  --physical     One serial per line for physical USB devices only (excludes emulator-*).
  --pick         Print a single serial: first physical device if any, otherwise the first
                 connected device. Intended for: export ANDROID_SERIAL="$(...)".
  -h, --help     Show this help.

Requires adb (Android platform-tools). Example:
  ANDROID_SERIAL="$(./scripts/get-device-serial.sh --pick)" ./gradlew connectedDebugAndroidTest
EOF
}

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  if [[ -x "$sdk_dir/platform-tools/adb" ]]; then
    echo "$sdk_dir/platform-tools/adb"
    return 0
  fi
  echo "error: adb not found. Install platform-tools or add them to PATH; set ANDROID_HOME." >&2
  return 1
}

# Outputs lines: SERIAL<TAB>MODEL<TAB>emulator|physical
list_device_rows() {
  local adb_bin="$1"
  local line serial state model rest
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == List* ]] && continue
    read -r serial state rest <<<"$line"
    [[ "$state" != device ]] && continue
    model="unknown"
    if [[ "$rest" =~ model:([^[:space:]]+) ]]; then
      model="${BASH_REMATCH[1]}"
    fi
    if [[ "$serial" =~ ^emulator- ]]; then
      printf '%s\t%s\temulator\n' "$serial" "$model"
    else
      printf '%s\t%s\tphysical\n' "$serial" "$model"
    fi
  done < <("$adb_bin" devices -l)
}

main() {
  local mode="table"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h | --help) usage; exit 0 ;;
      --serial) mode="serial" ;;
      --physical) mode="physical" ;;
      --pick) mode="pick" ;;
      *)
        echo "error: unknown option: $1" >&2
        usage >&2
        exit 2
        ;;
    esac
    shift
  done

  local adb_bin
  adb_bin="$(resolve_adb)"

  local rows
  rows="$(list_device_rows "$adb_bin")"
  if [[ -z "$rows" ]]; then
    echo "error: no connected devices in 'device' state. Run 'adb devices' and authorize USB debugging." >&2
    exit 1
  fi

  case "$mode" in
    serial)
      awk -F '\t' '{print $1}' <<<"$rows"
      ;;
    physical)
      local phys
      phys="$(awk -F '\t' '$3 == "physical" {print $1}' <<<"$rows")"
      if [[ -z "$phys" ]]; then
        echo "error: no physical device found (only emulator(s) connected?)." >&2
        exit 1
      fi
      printf '%s\n' "$phys"
      ;;
    pick)
      local phys
      phys="$(awk -F '\t' '$3 == "physical" {print $1; exit}' <<<"$rows")"
      if [[ -n "$phys" ]]; then
        printf '%s\n' "$phys"
      else
        awk -F '\t' '{print $1; exit}' <<<"$rows"
      fi
      ;;
    table)
      echo "Connected devices (adb):"
      while IFS=$'\t' read -r serial kind model; do
        [[ -z "${serial:-}" ]] && continue
        kind_label="emulator"
        [[ "$kind" == physical ]] && kind_label="physical"
        printf '  %s  model=%s  (%s)\n' "$serial" "$model" "$kind_label"
      done <<<"$(awk -F '\t' '{print $1"\t"$3"\t"$2}' <<<"$rows")"
      ;;
  esac
}

main "$@"
