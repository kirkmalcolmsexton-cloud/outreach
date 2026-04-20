#!/usr/bin/env bash
# Compare repo app version (gradle.properties preferred) with release/X.Y.Z or hotfix/X.Y.Z base branch.
# Usage: BASE_REF=release/1.2.3 ./android/scripts/ci-release-version-check.sh
set -euo pipefail

ANDROID_DIR="${ANDROID_DIR:-android}"
GRADLE_FILE="$ANDROID_DIR/app/build.gradle.kts"
PROPS_FILE="$ANDROID_DIR/gradle.properties"

ref="${BASE_REF:?Set BASE_REF to the PR base branch name (e.g. release/1.2.3)}"

case "$ref" in
  release/*) ver="${ref#release/}" ;;
  hotfix/*) ver="${ref#hotfix/}" ;;
  *)
    echo "ci-release-version-check: base must be release/X.Y.Z or hotfix/X.Y.Z (got: $ref)" >&2
    exit 1
    ;;
esac

if [[ ! "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ci-release-version-check: could not parse semver from '$ref' (parsed: '$ver')" >&2
  exit 1
fi

file_ver=""
if [[ -f "$PROPS_FILE" ]]; then
  file_ver="$(
    { grep -E '^[[:space:]]*outreach\.versionName[[:space:]]*=' "$PROPS_FILE" 2>/dev/null || true; } \
      | head -1 \
      | sed -E 's/^[[:space:]]*outreach\.versionName[[:space:]]*=[[:space:]]*"?([^"#[:space:]]+).*/\1/' \
      | tr -d '\r'
  )"
fi

# Legacy fallback: older branches may still use a quoted literal in build.gradle.kts (not outreachVersionName).
if [[ -z "$file_ver" && -f "$GRADLE_FILE" ]]; then
  file_ver="$(
    { grep -E '^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"[^"]+"' "$GRADLE_FILE" 2>/dev/null || true; } \
      | head -1 \
      | sed -E 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/' \
      | tr -d '\r'
  )"
fi

if [[ -z "$file_ver" ]]; then
  echo "ci-release-version-check: could not read versionName from $PROPS_FILE or $GRADLE_FILE" >&2
  exit 1
fi

if [[ "$file_ver" != "$ver" ]]; then
  echo "ci-release-version-check: versionName \"$file_ver\" does not match base branch sem ver \"$ver\" (from $ref)." >&2
  exit 1
fi

echo "ci-release-version-check: OK — versionName $file_ver matches $ref"
