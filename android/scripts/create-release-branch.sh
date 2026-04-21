#!/usr/bin/env bash
# Cut release/X.Y.Z or hotfix/X.Y.Z from develop or main, bump version keys in the tracked
# Gradle props file (tracked android/gradle.properties), commit, push.
#
# Requires a clean working tree by default; --ignore-unstaged allows unstaged edits and
# untracked files (still aborts if there are staged changes). Omit X.Y.Z and/or --version-code to derive them from the
# base branch's gradle.properties (semver bump + versionCode+1). Always confirm versionCode
# against Google Play before shipping.
#
# Usage:
#   bash android/scripts/create-release-branch.sh [X.Y.Z] [--version-code N] [--bump patch|minor|major]
#       [--hotfix] [--base BRANCH] [--remote origin] [--no-push] [--dry-run] [--ignore-unstaged]
#
# Precedence: --base BRANCH overrides the default base (develop vs main) chosen by --hotfix.

set -euo pipefail

VERSION_SEMVER=""
VERSION_CODE=""
BASE_BRANCH_OVERRIDE=""
REMOTE="origin"
NO_PUSH=0
DRY_RUN=0
HOTFIX=0
BUMP="patch"
IGNORE_UNSTAGED=0

err() {
  echo "create-release-branch: $*" >&2
}

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [X.Y.Z] [options]

Create release/X.Y.Z or hotfix/X.Y.Z from the appropriate base branch, set
outreach.versionName and outreach.versionCode in the resolved Gradle properties file
(see below), commit, and push to the remote.

Version (optional):
  X.Y.Z              Semantic version (must match branch suffix). If omitted, read
                     outreach.versionName from the base branch on ${REMOTE} (tracked
                     tracked android/gradle.properties), then bump:
                     patch (default), minor, or major (--bump).
  --version-code N   outreach.versionCode. If omitted, set to outreach.versionCode from
                     the base branch plus 1 (verify against Play Console before upload).
  --bump patch|minor|major   Used only when X.Y.Z is omitted (default: patch).

Options:
  --hotfix           Create hotfix/X.Y.Z from main (default: release/X.Y.Z from develop).
  --base BRANCH      Checkout this branch instead of develop or main (--base overrides
                     the default implied by --hotfix).
  --remote NAME      Remote name (default: origin).
  --no-push          Commit locally but do not git push.
  --dry-run          Print resolved versions and exit 0 without modifying the repository.
  --ignore-unstaged  Allow unstaged changes (tracked files) and untracked files; still aborts
                     if there are staged changes.
  -h, --help         Show this help.

Examples:
  $(basename "$0") --dry-run
  $(basename "$0") --bump minor --no-push
  $(basename "$0") 1.2.0 --version-code 42
  $(basename "$0") 1.2.1 --hotfix
EOF
}

die() {
  err "$@"
  exit 1
}

parse_outreach_version_name() {
  printf '%s\n' "$1" | grep -E '^[[:space:]]*outreach\.versionName[[:space:]]*=' | head -1 \
    | sed -E 's/^[[:space:]]*outreach\.versionName[[:space:]]*=[[:space:]]*"?([^"#[:space:]]+).*/\1/' \
    | tr -d '\r'
}

parse_outreach_version_code() {
  printf '%s\n' "$1" | grep -E '^[[:space:]]*outreach\.versionCode[[:space:]]*=' | head -1 \
    | sed -E 's/^[[:space:]]*outreach\.versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/' \
    | tr -d '\r'
}

bump_semver() {
  local ver="$1" kind="$2"
  local a b c
  if [[ ! "$ver" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    return 1
  fi
  a="${BASH_REMATCH[1]}"
  b="${BASH_REMATCH[2]}"
  c="${BASH_REMATCH[3]}"
  case "$kind" in
    patch) c=$((10#$c + 1)) ;;
    minor) b=$((10#$b + 1)); c=0 ;;
    major) a=$((10#$a + 1)); b=0; c=0 ;;
    *) return 1 ;;
  esac
  echo "${a}.${b}.${c}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version-code)
      [[ $# -ge 2 ]] || die "--version-code requires a value."
      VERSION_CODE="$2"
      shift 2
      ;;
    --bump)
      [[ $# -ge 2 ]] || die "--bump requires patch, minor, or major."
      BUMP="$2"
      shift 2
      ;;
    --base)
      [[ $# -ge 2 ]] || die "--base requires a branch name."
      BASE_BRANCH_OVERRIDE="$2"
      shift 2
      ;;
    --remote)
      [[ $# -ge 2 ]] || die "--remote requires a name."
      REMOTE="$2"
      shift 2
      ;;
    --hotfix)
      HOTFIX=1
      shift
      ;;
    --no-push)
      NO_PUSH=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --ignore-unstaged)
      IGNORE_UNSTAGED=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      die "Unknown option: $1 (try --help)"
      ;;
    *)
      if [[ -n "$VERSION_SEMVER" ]]; then
        die "Unexpected extra argument: $1"
      fi
      VERSION_SEMVER="$1"
      shift
      ;;
  esac
done

case "$BUMP" in
  patch|minor|major) ;;
  *) die "--bump must be patch, minor, or major (got: $BUMP)." ;;
esac

if [[ -n "$VERSION_SEMVER" ]] && [[ ! "$VERSION_SEMVER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  die "Version must be semver X.Y.Z (got: $VERSION_SEMVER)."
fi

if [[ -n "$VERSION_CODE" ]] && [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  die "version-code must be a positive integer (got: $VERSION_CODE)."
fi

if [[ -n "$BASE_BRANCH_OVERRIDE" ]]; then
  BASE_BRANCH="$BASE_BRANCH_OVERRIDE"
elif [[ "$HOTFIX" -eq 1 ]]; then
  BASE_BRANCH="main"
else
  BASE_BRANCH="develop"
fi

if [[ "$HOTFIX" -eq 1 ]]; then
  PREFIX="hotfix"
else
  PREFIX="release"
fi

if ! GIT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"; then
  die "Not inside a git repository."
fi

cd "$GIT_ROOT"

if [[ ! -d "${GIT_ROOT}/android" ]]; then
  die "Missing android/ directory — run from the Outreach repo root (git root: ${GIT_ROOT})."
fi

if [[ "$IGNORE_UNSTAGED" -eq 1 ]]; then
  if ! git diff --cached --quiet 2>/dev/null; then
    die "Staged changes present; commit or stash them (--ignore-unstaged does not allow staged changes)."
  fi
else
  if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
    die "Working tree is not clean. Commit or stash changes before cutting a release branch."
  fi
fi

ref_exists_remote() {
  git ls-remote "$REMOTE" "refs/heads/$1" 2>/dev/null | grep -q .
}

branch_exists_local() {
  git show-ref --verify --quiet "refs/heads/$1"
}

git fetch "$REMOTE"

if ! ref_exists_remote "$BASE_BRANCH"; then
  die "Base branch '${BASE_BRANCH}' not found on ${REMOTE}."
fi

PROP_FILE="android/gradle.properties"
if ! git cat-file -e "${REMOTE}/${BASE_BRANCH}:${PROP_FILE}" 2>/dev/null; then
  die "Tracked ${PROP_FILE} missing on ${REMOTE}/${BASE_BRANCH}."
fi
PROP_PATH="${GIT_ROOT}/${PROP_FILE}"

need_props=0
if [[ -z "$VERSION_SEMVER" ]] || [[ -z "$VERSION_CODE" ]]; then
  need_props=1
fi

props_remote=""
if [[ "$need_props" -eq 1 ]]; then
  props_remote="$(git show "${REMOTE}/${BASE_BRANCH}:${PROP_FILE}" 2>/dev/null)" \
    || die "Cannot read ${PROP_FILE} from ${REMOTE}/${BASE_BRANCH}."
fi

if [[ -z "$VERSION_SEMVER" ]]; then
  vn="$(parse_outreach_version_name "$props_remote")"
  [[ -n "$vn" ]] || die "Could not parse outreach.versionName from ${REMOTE}/${BASE_BRANCH}:${PROP_FILE}."
  VERSION_SEMVER="$(bump_semver "$vn" "$BUMP")" || die "Could not bump semver from '${vn}'."
fi

if [[ -z "$VERSION_CODE" ]]; then
  vc="$(parse_outreach_version_code "$props_remote")"
  [[ -n "$vc" ]] || die "Could not parse outreach.versionCode from ${REMOTE}/${BASE_BRANCH}:${PROP_FILE}."
  VERSION_CODE=$((10#$vc + 1))
fi

if [[ ! "$VERSION_SEMVER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  die "Version must be semver X.Y.Z (got: $VERSION_SEMVER)."
fi

if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  die "versionCode resolved to invalid value: $VERSION_CODE"
fi

NEW_BRANCH="${PREFIX}/${VERSION_SEMVER}"

if branch_exists_local "$NEW_BRANCH"; then
  die "Branch already exists locally: $NEW_BRANCH"
fi

if ref_exists_remote "$NEW_BRANCH"; then
  die "Branch already exists on ${REMOTE}: $NEW_BRANCH"
fi

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Dry run — no repository changes."
  echo "  Base branch: $BASE_BRANCH (remote: $REMOTE)"
  echo "  New branch:  $NEW_BRANCH"
  echo "  Version file: ${PROP_FILE}"
  echo "  Set outreach.versionName=${VERSION_SEMVER}, outreach.versionCode=${VERSION_CODE}"
  if [[ "$NO_PUSH" -eq 1 ]]; then
    echo "  Push: skipped (--no-push)"
  else
    echo "  Push: git push -u ${REMOTE} $NEW_BRANCH"
  fi
  exit 0
fi

git checkout "$BASE_BRANCH"
git pull --ff-only "$REMOTE" "$BASE_BRANCH"

git checkout -b "$NEW_BRANCH"

sed -i.bak \
  -e "s/^outreach\\.versionName=.*/outreach.versionName=${VERSION_SEMVER}/" \
  -e "s/^outreach\\.versionCode=.*/outreach.versionCode=${VERSION_CODE}/" \
  "$PROP_PATH"
rm -f "${PROP_PATH}.bak"

if ! grep -qE "^outreach\\.versionName=${VERSION_SEMVER}" "$PROP_PATH" \
  || ! grep -qE "^outreach\\.versionCode=${VERSION_CODE}" "$PROP_PATH"; then
  err "sed did not produce expected outreach.versionName / outreach.versionCode in ${PROP_FILE}."
  err "Confirm the file contains lines starting with outreach.versionName= and outreach.versionCode=."
  git checkout -- "$PROP_FILE" 2>/dev/null || true
  git checkout "$BASE_BRANCH" 2>/dev/null || true
  git branch -D "$NEW_BRANCH" 2>/dev/null || true
  die "Reverted local branch ${NEW_BRANCH}."
fi

grep -E '^outreach\.version(Name|Code)=' "$PROP_PATH" || true

git add "$PROP_FILE"

if git diff --staged --quiet; then
  die "No staged changes (unexpected)."
fi

git commit -m "Release ${VERSION_SEMVER} (versionCode ${VERSION_CODE})"

if [[ "$NO_PUSH" -eq 1 ]]; then
  echo "Created ${NEW_BRANCH} with version bump; push skipped (--no-push)."
else
  git push -u "$REMOTE" HEAD
fi

echo "create-release-branch: OK — branch ${NEW_BRANCH}"
