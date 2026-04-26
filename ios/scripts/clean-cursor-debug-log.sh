#!/usr/bin/env bash
# Local cleanup before a fresh iOS repro or build:
#   1) Cursor NDJSON debug log (default session 1f9f8a)
#   2) ios/build/ — CLI DerivedData (OUTREACH_DERIVED_DATA default) and archives
#
# Default layout: repo next to .cursor/ (e.g. workspaces/initial/outreach → ../.cursor).
#
# Usage:
#   ./ios/scripts/clean-cursor-debug-log.sh           # log + ios/build
#   ./ios/scripts/clean-cursor-debug-log.sh --log-only
#   ./ios/scripts/clean-cursor-debug-log.sh mysession # debug log session id
#
# Overrides:
#   OUTREACH_CURSOR_DEBUG_LOG=/abs/path/debug.log
#   CURSOR_DEBUG_SESSION_ID=abc123
#   OUTREACH_IOS_BUILD_ROOT=/abs/path/to/build   # default: <ios>/build
#   OUTREACH_SKIP_BUILD_CLEAN=1                  # only touch the debug log
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_ONLY=0
SESSION_ID="${CURSOR_DEBUG_SESSION_ID:-1f9f8a}"

for arg in "$@"; do
  case "$arg" in
    --log-only) LOG_ONLY=1 ;;
    --help|-h)
      grep '^#' "$0" | grep -v '^#!/' | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*)
      echo "Unknown option: $arg" >&2
      exit 2
      ;;
    *)
      SESSION_ID="$arg"
      ;;
  esac
done

if [[ -n "${OUTREACH_CURSOR_DEBUG_LOG:-}" ]]; then
  LOG="$OUTREACH_CURSOR_DEBUG_LOG"
else
  REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
  PARENT="$(cd "$REPO_ROOT/.." && pwd)"
  LOG="$PARENT/.cursor/debug-${SESSION_ID}.log"
fi

if [[ -f "$LOG" ]]; then
  rm -f "$LOG"
  echo "Removed $LOG"
elif [[ -e "$LOG" ]]; then
  echo "Refusing to remove non-file: $LOG" >&2
  exit 1
else
  echo "Nothing to remove (missing): $LOG"
fi

if [[ "${LOG_ONLY}" -eq 1 || "${OUTREACH_SKIP_BUILD_CLEAN:-}" == "1" ]]; then
  exit 0
fi

BUILD_ROOT="${OUTREACH_IOS_BUILD_ROOT:-$IOS_DIR/build}"
if [[ -d "$BUILD_ROOT" ]]; then
  rm -rf "$BUILD_ROOT"
  echo "Removed $BUILD_ROOT"
else
  echo "No build folder at $BUILD_ROOT"
fi
