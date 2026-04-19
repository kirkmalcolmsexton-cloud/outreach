#!/usr/bin/env bash
# Pretty-print JSON using jq (indent; optional sorted keys for stable diffs).
#
# Prerequisites: jq
#
# Usage:
#   ./scripts/pretty-json.sh path/to/file.json           # stdout
#   ./scripts/pretty-json.sh --in-place path/to/file.json
#   curl -s … | ./scripts/pretty-json.sh                 # stdin → stdout
#
set -euo pipefail

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--sort-keys] [--in-place|-i] [FILE]

  Without FILE, reads JSON from stdin.

  --sort-keys     Sort object keys (easier diffs; changes key order).
  --in-place|-i   Overwrite FILE atomically (requires FILE argument).

EOF
}

command -v jq >/dev/null || {
  echo "$(basename "$0"): install jq" >&2
  exit 1
}

SORT_KEYS=0
IN_PLACE=0
POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --sort-keys) SORT_KEYS=1; shift ;;
    -i | --in-place) IN_PLACE=1; shift ;;
    -*)
      echo "$(basename "$0"): unknown option: $1" >&2
      usage
      exit 1
      ;;
    *) POSITIONAL+=("$1"); shift ;;
  esac
done

jq_pretty() {
  if [[ "${SORT_KEYS}" -eq 1 ]]; then
    jq 'walk(if type == "object" then to_entries | sort_by(.key) | from_entries else . end)' "$@"
  else
    jq . "$@"
  fi
}

if [[ "${#POSITIONAL[@]}" -eq 0 ]]; then
  jq_pretty
  exit 0
fi

if [[ "${#POSITIONAL[@]}" -gt 1 ]]; then
  echo "$(basename "$0"): too many arguments (one file at a time)" >&2
  exit 1
fi

FILE="${POSITIONAL[0]}"
[[ -f "${FILE}" ]] || {
  echo "$(basename "$0"): not a file: ${FILE}" >&2
  exit 1
}

if [[ "${IN_PLACE}" -eq 1 ]]; then
  TMP="$(mktemp -t pretty-json.XXXXXX)"
  trap 'rm -f "${TMP}"' EXIT
  jq_pretty "${FILE}" > "${TMP}"
  mv "${TMP}" "${FILE}"
  trap - EXIT
else
  jq_pretty "${FILE}"
fi
