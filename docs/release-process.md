# Release process

Operator-facing guide for GitFlow, GitHub Actions release bundles, Play uploads, and repository secrets. CI workflow details also appear in **[`github-actions-secrets.md`](github-actions-secrets.md)**.

## First-time release

Complete these before expecting a green **Android release bundle** workflow. The full secret-name checklist is in **[§ GitHub repository secrets](#github-repository-secrets)**.

### Recommended order (first green CI bundle)

1. **Merge signing support** — [`android/app/build.gradle.kts`](../android/app/build.gradle.kts) reads **`ANDROID_UPLOAD_*`** env vars (CI) or **`android/keystore.properties`** (local). **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** must exist on the branch Actions runs.

2. **Set `OUTREACH_SECRETS_PASSPHRASE`** — In the GitHub repo: **Settings → Secrets and variables → Actions**. Required so **[`android/scripts/setup-secrets.sh`](../android/scripts/setup-secrets.sh)** can decrypt **`secrets/outreach-secrets.json.age`** and materialize release Maps / Firebase files before **`bundleRelease`**. Without it, the job fails at the passphrase guard step.

3. **Create upload keystore + `ANDROID_UPLOAD_*` secrets** — Preferred: from a clone of this repo, run **`android/scripts/create-upload-keystore-and-gh-secrets.sh`** (requires **`gh`** logged in with permission to set secrets). It generates a keystore (default path under **`~/.config/outreach/`**) and sets **`ANDROID_UPLOAD_KEYSTORE_BASE64`**, **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`**, **`ANDROID_UPLOAD_KEY_ALIAS`**, **`ANDROID_UPLOAD_KEY_PASSWORD`**. **Back up** the keystore file and passwords outside GitHub.

4. **Manual fallback** — Use Android Studio (**Build → Generate Signed Bundle**) or **`keytool`**, then base64 the keystore file and paste each value into repository secrets under the names in **[§ GitHub repository secrets](#github-repository-secrets)**.

5. **Cut / update a release branch** — e.g. **`release/X.Y.Z`** or **`hotfix/X.Y.Z`**, and bump **`android/gradle.properties`** **`outreach.versionCode`** / **`outreach.versionName`** (see below).

6. **Run the Android release bundle workflow** — **Actions → Android release bundle → Run workflow**, or **push** to **`release/**`** or **`hotfix/**`** (see triggers in **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)**).

7. **Confirm a green run** — Download the **`release-bundle`** artifact (**`.aab`**).

8. **Google Play Console** — Create the app listing if needed; use **Play App Signing**; upload the **`.aab`** to **Internal testing** or **Closed testing** first.

9. **OAuth / Firebase / Maps** — Register **SHA-1** / **SHA-256** for the **upload** certificate and for the **Play app signing** certificate as needed. Follow **[`google-oauth-checklist.md`](google-oauth-checklist.md)** (Play shows fingerprints after upload).

10. **QA** — Test on internal/closed tracks; then merge **release → main** and promote in Play when ready.

**Ordering note:** The workflow checks **`OUTREACH_SECRETS_PASSPHRASE`** before Gradle. **`ANDROID_UPLOAD_*`** must all be present before **`bundleRelease`** can succeed. You may set the passphrase secret before or after running the keystore script; both must exist before a successful bundle.

### If you triggered CI before secrets were ready

The workflow **fails at the first missing prerequisite** — often **`OUTREACH_SECRETS_PASSPHRASE`**, or **`bundleRelease`** if the passphrase is set but **`ANDROID_UPLOAD_*`** are missing. Add or fix secrets (run **`create-upload-keystore-and-gh-secrets.sh`** or set values manually), then **Re-run jobs** in Actions or push again / dispatch again. **Do not** bump **`outreach.versionCode`** unless Play already consumed that version.

### What the helper script does not do

It does **not** create the Play listing, enroll **Play App Signing**, or upload an **`.aab`** — step 8 remains manual until optional CD is added.

---

## GitHub repository secrets

Each row maps to **Settings → Secrets and variables → Actions → New repository secret**.

### Decrypt consolidated secrets

| Secret | Purpose | Used by |
|--------|---------|---------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | Passphrase for **`secrets/outreach-secrets.json.age`** (release Maps key + paths into **`setup-secrets.sh`**) | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** |

### Upload signing

| Secret | Purpose | Used by |
|--------|---------|---------|
| **`ANDROID_UPLOAD_KEYSTORE_BASE64`** | Upload keystore file (binary), **base64-encoded** (single line) | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** |
| **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** | Keystore password | Same |
| **`ANDROID_UPLOAD_KEY_ALIAS`** | Key alias inside the keystore | Same |
| **`ANDROID_UPLOAD_KEY_PASSWORD`** | Key password | Same |

Populate **`ANDROID_UPLOAD_*`** with **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** or manually. **`GITHUB_TOKEN`** is automatic and is not a repository secret.

Secrets are **not** passed to **`pull_request`** workflows from forks — see **[`github-actions-secrets.md`](github-actions-secrets.md#fork-and-pull-request-caveat)**.

---

## GitFlow overview

This repo follows **classic GitFlow**: **`main`** matches what ships on **Google Play**; **`develop`** integrates feature work. **CI** runs gates on PRs; **shipping** to Play is a human cut (branch, **`bundleRelease`**, Console) unless you add CD.

### Branches

| Branch / pattern | Role |
|------------------|------|
| **`main`** | Production-ready. Tag each Play release **`vX.Y.Z`** on the merge commit that matches what you uploaded. |
| **`develop`** | Integration target for features; day-to-day PRs merge here first, not directly to **`main`** (except via release/hotfix flows). |
| **`feature/<name>`** or ticket-prefixed | Branch from **`develop`**; PR back to **`develop`**. |
| **`release/X.Y.Z`** | Cut from **`develop`** when preparing a release. Freeze new features — fixes and polish only. Bump app version here. |
| **`hotfix/X.Y.Z`** | Branch from **`main`** for urgent production fixes; merge to **`main`**, tag, then merge **`main` → `develop`**. |

**Version numbers** live in **`android/gradle.properties`** (**`outreach.versionCode`**, **`outreach.versionName`**); the app module wires them into the Android plugin. **`versionCode`** must **increase** for every Play upload; **`versionName`** is user-visible semver (**`X.Y.Z`**). Bump on **`release/`** or **`hotfix/`** before building the store artifact.

### Feature work (routine)

1. **`git checkout develop && git pull`**
2. **`git checkout -b feature/<name>`**
3. Implement, push, open PR **into `develop`**
4. After review, merge; delete the feature branch

### Play Store release (happy path)

1. Confirm **`develop`** builds and tests (**`./gradlew assembleDebug`**, instrumentation tests per README CLI quick reference) and fix blockers.
2. **`git checkout develop && git pull`** then **`git checkout -b release/X.Y.Z`** (same **`X.Y.Z`** as **`versionName`** — no **`v`** in the branch name).
3. Edit **`android/gradle.properties`**: set **`outreach.versionName`** to **`X.Y.Z`** and bump **`outreach.versionCode`** by at least **1** vs the last Play upload.
4. Stabilize on **`release/X.Y.Z`** with bugfixes only.
5. QA using **`docs/release-checklist.md`** and **[`google-oauth-checklist.md`](google-oauth-checklist.md)**. Build a signed bundle locally (**`./gradlew bundleRelease`** from **`android/`** with **`keystore.properties`**) or rely on CI (**§ First-time release**).
6. Upload the **AAB** to Play **Internal** or **Closed testing** first. Ensure OAuth / Maps / Firebase allow the correct **SHA-1** signatures (**upload** vs **Play app signing** — see **`google-oauth-checklist.md`**).
7. When ready, merge **`release/X.Y.Z` → `main`** via PR.
8. On **`main`**, tag: **`git tag -a vX.Y.Z -m "Outreach X.Y.Z"`**.
9. **`git checkout develop && git merge main`** so **`develop`** includes release fixes.
10. Delete **`release/X.Y.Z`** after merges complete.

### Hotfix (production emergency)

1. **`git checkout main && git pull`**
2. **`git checkout -b hotfix/X.Y.Z`** — bump **`outreach.versionCode`** and **`outreach.versionName`** in **`android/gradle.properties`**, fix, **`./gradlew bundleRelease`**, upload to Play.
3. Merge **`hotfix/X.Y.Z` → `main`**, tag **`vX.Y.Z`**, then **`git checkout develop && git merge main`** (or cherry-pick) so **`develop`** stays in sync.

### Secrets and signing (orthogonal to branches)

**`google-services.json`** and **`MAPS_API_KEY`** come from **`android/scripts/setup-secrets.sh`** or manual setup (see README Firebase section). Document internally who holds the **upload keystore** backup and how **Play App Signing** is configured.

---

## Automation

**[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** runs on **`workflow_dispatch`** and on **push** to **`release/**`** or **`hotfix/**`**. It decrypts secrets, decodes the upload keystore, runs **`./gradlew bundleRelease`**, and uploads the **`.aab`** as the **`release-bundle`** artifact.

For the full workflow matrix and fork caveats, see **[`github-actions-secrets.md`](github-actions-secrets.md)**.

---

## Related documentation

- **[`github-actions-secrets.md`](github-actions-secrets.md)** — workflow matrix, fork caveat
- **[`google-oauth-checklist.md`](google-oauth-checklist.md)** — OAuth / Maps / Firebase SHA-1
- **[`release-checklist.md`](release-checklist.md)** — QA before ship
- **[`../README.md`](../README.md)** — CI/CD table and development setup
