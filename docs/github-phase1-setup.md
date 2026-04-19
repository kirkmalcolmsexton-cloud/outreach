# GitHub Phase 1 setup (governance without CI gates)

Phase 1 turns on **human** merge discipline before **GitHub Actions** workflows add required checks (Phase 2).

## 1. Long-lived branches

Ensure these exist on the remote (create empty commits or push if needed):

- **`main`**
- **`develop`**

Release and hotfix branches are created only when cutting a release or hotfix — no need to create them upfront.

## 2. Branch naming (team convention)

Pick one style and document it for contributors:

- **`feature/<short-description>`**, or  
- **`SCRUM-123-short-description`** (ticket prefix — keys match **[Jira](https://kirkmalcolmsexton.atlassian.net)**).

Semantic version in **`release/X.Y.Z`** and **`hotfix/X.Y.Z`** matches **`android/gradle.properties`** **`outreach.versionName`** when those branches ship.

## 3. Rulesets (GitHub: Settings → Rules → Rulesets)

Create rulesets for:

| Target refs | Settings to enable |
|-------------|-------------------|
| **`main`**, **`develop`**, **`refs/heads/release/**`**, **`refs/heads/hotfix/**`** | Require a pull request before merging |
| | Require approvals (**≥ 1**) |
| | **Require review from Code Owners** (uses [`.github/CODEOWNERS`](../.github/CODEOWNERS)) |
| | Dismiss stale pull request approvals when new commits are pushed (recommended) |

**Do not** enable **Require status checks to pass** until Phase 2 workflows exist and you have run a dry-run PR (see **`README.md`** → CI/CD). Otherwise merges can block waiting for checks that never run.

Optional: a ruleset restricting creation/update of tags matching **`v*`** to administrators.

## 4. Upload keystore & Play custody (fill in privately)

Keep this information **out of git** (password manager or internal doc). Replace the placeholders:

| Item | Owner / location |
|------|------------------|
| Play Console app | |
| Upload keystore file | (path or escrow reference) |
| Keystore passphrase | |
| Play App Signing: app signing key held by | Google / note |
| GitHub Actions secrets (Phase 2 CD) | See **[`github-actions-secrets.md`](github-actions-secrets.md)** — **`android-release-build`** uses **`OUTREACH_SECRETS_PASSPHRASE`**; other workflows typically need only `GITHUB_TOKEN` |

## 5. After Phase 1

When Phase 2 adds **`.github/workflows/`**, open a throwaway PR, note **exact** job names on the **Checks** tab, then add those names under **Require status checks** per target branch (`README.md` → CI/CD tables).
