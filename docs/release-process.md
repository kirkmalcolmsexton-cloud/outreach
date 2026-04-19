# Release process

Operator-facing guide for GitFlow, GitHub Actions release bundles, Play uploads, and repository secrets. CI workflow details also appear in **[`github-actions-secrets.md`](github-actions-secrets.md)**.

## First-time release

Use this checklist the first time you expect CI to produce a **signed** **`.aab`** and you are wiring **GitHub Actions** secrets. Secret names and meanings are in **[§ GitHub repository secrets](#github-repository-secrets)**.

### Prerequisites (before any step)

- **Repository:** Permission to add **Actions** repository secrets on this GitHub repo (maintainer/admin as required by org policy).
- **Branch:** Signing and **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** must be **merged** into the branch you will run Actions on (typically **`develop`** → **`release/X.Y.Z`**).
- **Files in repo:** Committed **`secrets/outreach-secrets.json.age`** and teammate-shared passphrase for decryption (same as local **`encrypt-secrets.sh`** / **`setup-secrets.sh`**).
- **Tools (for scripted keystore upload):** **JDK** (`keytool`), **GitHub CLI** **`gh`** (`gh auth login`), **Git** clone of the repo.

---

### Step 1 — Confirm signing is in the codebase

**Goal:** The runner can sign **`bundleRelease`**; otherwise secrets alone are not enough.

**Do:**

1. Open [`android/app/build.gradle.kts`](../android/app/build.gradle.kts) and confirm **`signingConfigs`** / **`ANDROID_UPLOAD_*`** wiring exists for **`release`**.
2. Open **[`.github/workflows/android-release-build.yml`](../.github/workflows/android-release-build.yml)** and confirm jobs decode the keystore and pass env vars into Gradle.

**Verify:** Merged on **`main`** / **`develop`** / your release branch per your team’s process.

---

### Step 2 — Add `OUTREACH_SECRETS_PASSPHRASE` (one secret)

**Goal:** CI can decrypt **`secrets/outreach-secrets.json.age`** and write **`google-services.json`** + **`MAPS_API_KEY`** before **`bundleRelease`**.

**Do:**

1. GitHub → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. **Name:** **`OUTREACH_SECRETS_PASSPHRASE`** (exact spelling).
3. **Value:** The passphrase your team uses to encrypt/decrypt **`outreach-secrets.json.age`**.

**Verify:** Run **Android release bundle** once; if this is missing or wrong, the job fails in the step **Require OUTREACH_SECRETS_PASSPHRASE** or during **`setup-secrets.sh`** / **`age`** decrypt — not during Gradle signing yet.

---

### Step 3 — Create upload keystore and set four `ANDROID_UPLOAD_*` secrets

**Goal:** CI has the **upload keystore** material as repository secrets (**`ANDROID_UPLOAD_KEYSTORE_BASE64`**, **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`**, **`ANDROID_UPLOAD_KEY_ALIAS`**, **`ANDROID_UPLOAD_KEY_PASSWORD`**).

#### Path A — Script (recommended)

**Do:**

1. On your machine: **`cd`** to the repo root (same folder as **`android/`**).
2. Ensure **`gh auth status`** shows access to **this** repository with permission to set secrets (**`repo`** scope where applicable).
3. Run: **`bash android/scripts/create-upload-keystore-and-gh-secrets.sh`**
4. Enter keystore / key passwords when prompted (or set **`OUTREACH_KEYSTORE_PASSWORD`** etc. — see script header).
5. Copy the generated **`.jks`** file from the path the script prints (default under **`~/.config/outreach/`**) to **secure backup** (password manager + encrypted backup — not only GitHub).

**Verify:** **Settings → Secrets → Actions** lists all four **`ANDROID_UPLOAD_*`** names. Locally, **`keytool -list -keystore /path/to/your.jks`** opens with your password.

#### Path B — Manual (Studio or `keytool`)

**Do:**

1. Create a keystore with Android Studio (**Build → Generate Signed App Bundle / APK**) or **`keytool -genkeypair`** (RSA, **`PKCS12`** / **`.jks`**, note **alias** and passwords).
2. Base64 the file (single line, no wrapping), e.g. **`base64 -i upload.jks | tr -d '\n'`** (macOS/Linux adjust if needed).
3. Create four repository secrets with names exactly as in **[§ GitHub repository secrets](#github-repository-secrets)** under **Upload signing**.

**Verify:** Same as Path A — four secrets present; **`keytool -list`** works on your local copy.

---

### Step 4 — Cut release branch and bump version

**Goal:** **`bundleRelease`** builds the **version** you intend to ship; **`versionCode`** must increase for every new Play upload.

**Do:**

1. **`git checkout -b release/X.Y.Z`** (or **`hotfix/X.Y.Z`** from **`main`**).
2. Edit **`android/gradle.properties`**: set **`outreach.versionName=X.Y.Z`** and **`outreach.versionCode`** to an integer **greater** than the last build uploaded to Play.
3. Commit and **push** the branch to **GitHub**.

**Verify:** Branch **`release/X.Y.Z`** (or **`hotfix/...`**) exists on the remote; **`gradle.properties`** reflects the intended **`versionCode`** / **`versionName`**.

---

### Step 5 — Run **Android release bundle** on GitHub Actions

**Goal:** Workflow runs **`bundleRelease`** and uploads an **`.aab`** artifact.

**Do (pick one):**

- **Push:** Push a commit to **`release/**`** or **`hotfix/**`** (same patterns as workflow **`on.push.branches`**), **or**
- **Dispatch:** **Actions** → **Android release bundle** → **Run workflow**, choose the **`release/`** / **`hotfix/`** branch.

**Verify:** Workflow finishes green; artifact **`release-bundle`** contains **`app-release.aab`** (path under **`android/app/build/outputs/bundle/release/`** in the job).

---

### Step 6 — Download the `.aab`

**Goal:** You have the store bundle file for Play Console upload.

**Do:** Open the successful run → **Artifacts** → download **`release-bundle`**.

**Verify:** Local file exists and ends with **`.aab`**.

---

### Step 7 — Google Play Console (first upload path)

**Goal:** App exists in Play (or new version on existing app); **Play App Signing** is understood; binary is on **Internal** or **Closed testing** before production.

**Do:**

1. Open [Google Play Console](https://play.google.com/console) → select **app** (create if first time).
2. Confirm **Play App Signing** enrollment for the app (follow Google’s prompts).
3. **Testing → Internal testing** or **Closed testing** → **Create release** → upload the **`.aab`** from Step 6.
4. Complete any **policy / content** steps Google requires for that track.

**Verify:** Release shows as processing / available to testers per your track configuration.

---

### Step 8 — OAuth / Firebase / Maps (certificate fingerprints)

**Goal:** Sign-in and Maps work on **release-signed** builds (fingerprints differ from debug).

**Do:**

1. In Play Console, note **App signing key certificate** and **Upload key certificate** **SHA-1** / **SHA-256** if shown.
2. Follow **[`google-oauth-checklist.md`](google-oauth-checklist.md)** to register the correct hashes in **Google Cloud / Firebase** (upload cert vs Play-held cert — checklist explains).

**Verify:** Internal/closed build can sign in and use Maps per your QA checklist.

---

### Step 9 — QA and GitFlow wrap-up

**Goal:** Release is validated; **`main`** and tags match what you shipped.

**Do:**

1. Test on **internal/closed** testers using **[`release-checklist.md`](release-checklist.md)** as needed.
2. When satisfied, merge **`release/X.Y.Z` → `main`**, tag **`vX.Y.Z`**, merge **`main` → `develop`**, and promote in Play per **[§ Play Store release (happy path)](#play-store-release-happy-path)** below.

**Verify:** Tag **`vX.Y.Z`** points at the commit that matches the uploaded **`versionCode`**.

---

### Dependency order (secrets)

| Order on the runner | What must already be true |
|---------------------|---------------------------|
| 1 | **`OUTREACH_SECRETS_PASSPHRASE`** set → decrypt succeeds |
| 2 | **`ANDROID_UPLOAD_*`** set → keystore decodes → Gradle signs |
| 3 | **`bundleRelease`** succeeds → artifact uploads |

Steps **2** (passphrase) and **3** (upload keystore) can be completed in either order **before** first green run; **both** must exist before Step **5** succeeds end-to-end.

---

### If CI already failed once

**Symptom:** Red **Android release bundle** run.

**Do:**

1. Read the **first failed step**: passphrase guard vs **`setup-secrets`** vs **`Decode upload keystore`** vs **`bundleRelease`**.
2. Fix or add the missing **repository secrets** (Steps **2**–**3**).
3. **Re-run failed jobs** or re-dispatch the workflow.
4. **Do not** increase **`outreach.versionCode`** unless Play has already accepted that **`versionCode`** for this app.

---

### What `create-upload-keystore-and-gh-secrets.sh` does not do

It does **not** create the Play listing, finish **Play Console** setup, or upload **`.aab`** to Play — Steps **7**–**9** stay manual unless you add separate CD automation later.

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

1. Confirm **`develop`** builds and tests (**`./gradlew assembleDebug`**, instrumentation tests per **[`docs/testing-guide.md`](testing-guide.md)**). For device-specific or **`real`**-auth validation before cut, follow **[`docs/testing-process.md`](testing-process.md)** and **[`docs/testing-guide.md`](testing-guide.md)** (including **[`android/scripts/run-physical-ui-tests.sh`](../android/scripts/run-physical-ui-tests.sh)** when using a USB phone). Fix blockers before branching.
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

For workflows, **`gh`**, and merge gates, see **[`ci-cd.md`](ci-cd.md)**. Secret names and fork caveats: **[`github-actions-secrets.md`](github-actions-secrets.md)**.

---

## Related documentation

- **[`ci-cd.md`](ci-cd.md)** — GitHub Actions workflows, **`gh`** CLI, required checks, local Gradle parity
- **[`testing-process.md`](testing-process.md)** — unit vs UI tests, when to run hardware / **`real`** auth
- **[`testing-guide.md`](testing-guide.md)** — Gradle commands, **`adb`**, instrumentation flags, troubleshooting
- **[`developer-onboarding.md`](developer-onboarding.md)** — first-time machine and emulator setup
- **[`build-process.md`](build-process.md)** — debug/release artifacts and secrets overview
- **[`github-actions-secrets.md`](github-actions-secrets.md)** — workflow matrix, fork caveat
- **[`google-oauth-checklist.md`](google-oauth-checklist.md)** — OAuth / Maps / Firebase SHA-1
- **[`release-checklist.md`](release-checklist.md)** — QA before ship
- **[`../README.md`](../README.md)** — CI/CD table and development setup
