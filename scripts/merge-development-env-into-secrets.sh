#!/usr/bin/env bash
# Merge KEY=value pairs from ~/etc/development.env into secrets/outreach-secrets.json under the
# top-level key "development_env" (string map). Uses the single consolidated secrets file —
# encrypt with scripts/encrypt-secrets.sh (or encrypt-for-checkin.sh) → secrets/outreach-secrets.json.age only.
#
# Reads simple shell-style env files: empty lines and # comments skipped, optional "export " prefix,
# values may be single- or double-quoted (basic escape support) or unquoted.
#
# Prerequisites: secrets/outreach-secrets.json must exist (copy from outreach-secrets.example.json first).
#
# Usage (from repo root):
#   bash scripts/merge-development-env-into-secrets.sh
#   bash scripts/merge-development-env-into-secrets.sh --output /path/to/outreach-secrets.json
#   bash scripts/merge-development-env-into-secrets.sh /path/to/custom.env

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_ENV="${DEVELOPMENT_ENV_FILE:-${HOME}/etc/development.env}"
DEFAULT_OUT="${REPO_ROOT}/secrets/outreach-secrets.json"

ENV_FILE="${DEFAULT_ENV}"
OUT_FILE="${DEFAULT_OUT}"

usage() {
  sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      ;;
    -o | --output)
      [[ $# -ge 2 ]] || {
        echo "$(basename "$0"): --output requires a path" >&2
        exit 2
      }
      OUT_FILE="$2"
      shift 2
      ;;
    --)
      shift
      POSITIONAL+=("$@")
      break
      ;;
    -*)
      echo "$(basename "$0"): unknown option: $1" >&2
      exit 2
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done

if [[ ${#POSITIONAL[@]} -gt 1 ]]; then
  echo "$(basename "$0"): at most one env file path is allowed" >&2
  exit 2
fi
if [[ ${#POSITIONAL[@]} -eq 1 ]]; then
  ENV_FILE="${POSITIONAL[0]}"
fi

export OUTREACH_ENV_FILE="${ENV_FILE}"
export OUTREACH_OUT_FILE="${OUT_FILE}"

python3 <<'PY'
import json
import os
import re
import sys
from pathlib import Path

env_path = Path(os.environ["OUTREACH_ENV_FILE"]).expanduser()
out_path = Path(os.environ["OUTREACH_OUT_FILE"]).expanduser()


def parse_env(text):
    out = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            continue
        key, _, rest = line.partition("=")
        key = key.strip()
        if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", key):
            continue
        val = rest.strip()
        if val and val[0] not in "\"'":
            if " #" in val:
                val = val.split(" #", 1)[0].rstrip()
        if len(val) >= 2 and val[0] == val[-1] and val[0] in "\"'":
            inner = val[1:-1]
            if val[0] == '"':
                inner = (
                    inner.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace('\\"', '"')
                    .replace("\\\\", "\\")
                )
            out[key] = inner
        else:
            out[key] = val
    return out


if not env_path.is_file():
    print(f"merge-development-env-into-secrets: missing env file: {env_path}", file=sys.stderr)
    sys.exit(1)

if not out_path.is_file():
    print(
        f"merge-development-env-into-secrets: missing {out_path}",
        file=sys.stderr,
    )
    print(
        "  Copy secrets/outreach-secrets.example.json to secrets/outreach-secrets.json and fill required keys.",
        file=sys.stderr,
    )
    sys.exit(1)

try:
    merged = json.loads(out_path.read_text(encoding="utf-8"))
except json.JSONDecodeError as e:
    print(f"merge-development-env-into-secrets: invalid JSON in {out_path}: {e}", file=sys.stderr)
    sys.exit(1)

if not isinstance(merged, dict):
    print(f"merge-development-env-into-secrets: root must be a JSON object: {out_path}", file=sys.stderr)
    sys.exit(1)

de = merged.get("development_env")
if de is None:
    de = {}
elif not isinstance(de, dict):
    print(f"merge-development-env-into-secrets: development_env must be an object in {out_path}", file=sys.stderr)
    sys.exit(1)

updates = parse_env(env_path.read_text(encoding="utf-8"))
for k, v in updates.items():
    de[str(k)] = str(v)

merged["development_env"] = de

out_path.parent.mkdir(parents=True, exist_ok=True)
out_path.write_text(json.dumps(merged, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(
    f"Updated {out_path} — development_env now has {len(de)} keys ({len(updates)} from {env_path})."
)
PY
