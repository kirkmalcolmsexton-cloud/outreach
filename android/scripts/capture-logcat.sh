#!/usr/bin/env bash
# Dump the device logcat buffer to android/log-captures/ (gitignored).
# Requires one authorized device: adb devices -> "device"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$ANDROID_ROOT/log-captures"

resolve_adb() {
  local base="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  local candidate="$base/platform-tools/adb"
  if [[ -x "$candidate" ]]; then
    echo "$candidate"
    return
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  echo "Error: adb not found. Install platform-tools or set ANDROID_HOME." >&2
  exit 1
}

ADB="$(resolve_adb)"
mkdir -p "$OUT_DIR"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/logcat-${TS}.txt"

{
  echo "=== logcat capture $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="
  echo "=== adb ($("$ADB" version 2>/dev/null | head -1 || true)) ==="
  echo "=== adb devices -l ==="
  "$ADB" devices -l || true
  echo
  echo "=== adb logcat -d -b all (full buffer dump) ==="
  if ! "$ADB" logcat -d -b all 2>/dev/null; then
    echo "(note: -b all failed; falling back to default buffers)"
    "$ADB" logcat -d
  fi
} >"$OUT_FILE"

echo "Wrote: $OUT_FILE"
echo "Bytes: $(wc -c <"$OUT_FILE" | tr -d ' ')"
