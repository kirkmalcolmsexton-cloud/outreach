# Release process

Operator-facing guide for GitFlow, GitHub Actions release bundles, Play uploads, and repository secrets. CI workflow details also appear in **[`github-actions-secrets.md`](github-actions-secrets.md)**.

## First-time release

Use this checklist the first time you expect CI to produce a **signed** **`.aab`** and you are wiring **GitHub Actions** secrets. Secret names and meanings are in **[§ GitHub repository secrets](#github-repository-secrets)**.

### Prerequisites (before any step)

- **Repository:** Permission to add **Actions** repository secrets on this GitHub repo (maintainer/admin as required by org policy).
- **Branch:** Signing and **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** must be **merged** into the branch you will run Actions on (typically **`develop`** → **`release/X.Y.Z`**).
- **Files in repo:** Committed **`secrets/outreach-secrets.json.age`** and teammate-shared passphrase for decryption (same as local **`encrypt-secrets.sh`** / **`setup-secrets.sh`**).
- **Tools:** **JDK** (`keytool`), **`age`**, **`jq`**, **`expect`** (repo-mode keystore encrypt/decrypt paths), **GitHub CLI** **`gh`** (**legacy** Path B only — **`gh auth login`**), **Git** clone of the repo.

---

### Step 1 — Confirm signing is in the codebase

**Goal:** The runner can sign **`bundleRelease`**; otherwise secrets alone are not enough.

**Do:**

1. Open [`android/app/build.gradle.kts`](../android/app/build.gradle.kts) and confirm **`signingConfigs`** / **`ANDROID_UPLOAD_*`** wiring exists for **`release`**.
2. Open **[`.github/workflows/android-release-build.yml`](../.github/workflows/android-release-build.yml)** and confirm the job runs **`android/scripts/build.sh release-bundle`** (same as **`build-release-bundle.sh`** locally) with repository **`OUTREACH_SECRETS_PASSPHRASE`** and, for **legacy** signing, the four **`ANDROID_UPLOAD_*`** secrets.

**Verify:** Merged on **`main`** / **`develop`** / your release branch per your team’s process.

---

### Step 2 — Add `OUTREACH_SECRETS_PASSPHRASE` (one secret)

**Goal:** CI can run **`android/scripts/build.sh release-bundle`** (same **`setup-secrets.sh`** as local) and write **`google-services.json`** + **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** in **`local.properties`** before **`bundleRelease`**.

**Do:**

1. GitHub → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. **Name:** **`OUTREACH_SECRETS_PASSPHRASE`** (exact spelling).
3. **Value:** The passphrase your team uses to encrypt/decrypt **`outreach-secrets.json.age`**.

**Verify:** Run **Android release bundle** once; if this is missing or wrong, the job fails in the step **Require OUTREACH_SECRETS_PASSPHRASE** or during **`setup-secrets.sh`** / **`age`** decrypt — not during Gradle signing yet.

---

### Step 3 — Upload keystore and CI signing (**repo mode** or **legacy**)

**Goal:** CI can produce **`${RUNNER_TEMP}/outreach-upload.jks`** and Gradle **`ANDROID_UPLOAD_*`** env vars. Pick **one** approach:

- **Repo mode (recommended):** Commit **`secrets/upload-keystore.jks.age`** (same passphrase as **`outreach-secrets.json.age`**) and add **`android_upload_signing`** to plaintext consolidated JSON before regenerating **`outreach-secrets.json.age`**. No GitHub **`ANDROID_UPLOAD_*`** secrets needed.
- **Legacy:** Store the keystore as **`ANDROID_UPLOAD_KEYSTORE_BASE64`** plus three password/alias secrets — **[§ Upload signing](#upload-signing)**.

The workflow prefers **repo mode** when **`secrets/upload-keystore.jks.age`** exists **and** decrypted JSON contains a complete **`android_upload_signing`** object; otherwise it requires all four legacy secrets.

#### Path A — Repo mode script

**Do:**

1. **`cd`** to the repo root (same folder as **`android/`**).
2. Install **JDK**, **`age`**, **`expect`**, **`jq`** (encrypt path / instructions).
3. Run: **`OUTREACH_SIGNING_REPO_MODE=1`** **`bash android/scripts/create-upload-keystore-and-gh-secrets.sh`** (set **`OUTREACH_SECRETS_PASSPHRASE`** for non-interactive **`age -p`**, or use a TTY).  
   **Or** if you already have a plaintext upload **`.jks`**: **`OUTREACH_SECRETS_PASSPHRASE=... bash android/scripts/encrypt-upload-keystore-age.sh`** (no path arguments — reads **`~/.config/outreach/upload-keystore.jks`** by default, writes **`secrets/upload-keystore.jks.age`**; set **`OUTREACH_UPLOAD_KEYSTORE_PATH`** to use another **`.jks`**). Repo root **`scripts/encrypt-upload-keystore-age.sh`** forwards to the same implementation.
4. Merge the printed **`android_upload_signing`** object into **`secrets/outreach-secrets.json`**, run **`bash android/scripts/encrypt-secrets.sh`**, and commit **`secrets/outreach-secrets.json.age`** + **`secrets/upload-keystore.jks.age`**. Never commit plaintext **`*.jks`** or **`outreach-secrets.json`** (both gitignored where applicable).
5. Back up the local **`.jks`** copy from the script output path (default **`~/.config/outreach/upload-keystore.jks`**) off-disk.

**Verify:** **`keytool -list`** on your local **`.jks`** works. After push, release workflow logs **Upload signing: repo mode**.

#### Path B — Legacy: `gh` script

**Do:**

1. **`cd`** to the repo root; **`gh auth status`** with permission to set secrets (**`repo`** scope).
2. **`bash android/scripts/create-upload-keystore-and-gh-secrets.sh`** (do **not** set **`OUTREACH_SIGNING_REPO_MODE`**).
3. Back up the **`.jks`** file securely (not only GitHub).

**Verify:** **Settings → Secrets → Actions** lists all four **`ANDROID_UPLOAD_*`** names in **[§ Upload signing](#upload-signing)**.

#### Path C — Legacy: manual (Studio or `keytool`)

**Do:**

1. Create a keystore (**RSA**, **`PKCS12`** / **`.jks`**), note **alias** and passwords.
2. Base64 (single line): e.g. **`base64 -i upload.jks | tr -d '\n'`**.
3. Create the four repository secrets exactly as in **[§ Upload signing](#upload-signing)**.

**Verify:** Same as Path B — secrets present; **`keytool -list`** works locally.

---

### Step 4 — Cut release branch and bump version

**Goal:** **`bundleRelease`** builds the **version** you intend to ship; **`versionCode`** must increase for every new Play upload.

**Do:**

1. **`git checkout -b release/X.Y.Z`** (or **`hotfix/X.Y.Z`** from **`main`**).
2. Edit **`android/gradle.properties`**: set **`outreach.versionName=X.Y.Z`** and **`outreach.versionCode`** to an integer **greater** than the last build uploaded to Play.
3. Commit and **push** the branch to **GitHub**.

**Or** run **`bash android/scripts/create-release-branch.sh`** from the repo root (see **`--help`**); **`scripts/create-release-branch.sh`** forwards to the same script. You may omit **`X.Y.Z`** and **`--version-code`**: the script reads **`outreach.versionName`** / **`outreach.versionCode`** from the **base branch** on **`origin`**, applies a **patch** semver bump by default (**`--bump minor|major`** to change), and sets **`versionCode`** to **remote + 1** unless you pass **`--version-code`**. Always confirm **`versionCode`** against the last Play upload. Optional: **`--hotfix`**, **`--base BRANCH`**, **`--no-push`**, **`--dry-run`**. Requires a clean working tree; aborts if the base branch is missing, the branch already exists, or **`git pull --ff-only`** cannot fast-forward.

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
| 1 | **`OUTREACH_SECRETS_PASSPHRASE`** set → **`outreach-secrets.json.age`** decrypt succeeds |
| 2 | **Repo mode:** **`upload-keystore.jks.age`** + **`android_upload_signing`** → keystore decrypt; **Legacy:** **`ANDROID_UPLOAD_*`** → base64 decode |
| 3 | **`bundleRelease`** succeeds → artifact uploads |

Steps **2** (passphrase) and **3** (signing material) can be prepared in either order **before** first green run; **both** passphrase and signing configuration must exist before Step **5** succeeds end-to-end.

---

### If CI already failed once

**Symptom:** Red **Android release bundle** run.

**Do:**

1. Read the **first failed step** (and its log; **`Bundle release`** runs **`android/scripts/build.sh release-bundle`**, so failures can still map to **`scripts/decrypt-age-passphrase.sh`**, signing mode, keystore, **`setup-secrets`**, or **Gradle**).
2. Fix or add the missing **repository secrets** or committed **`.age`** files / JSON fields (Steps **2**–**3**).
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
| **`OUTREACH_SECRETS_PASSPHRASE`** | Passphrase for **`secrets/outreach-secrets.json.age`** (and repo-mode **`upload-keystore.jks.age`**) for **`setup-secrets.sh`** / **`build.sh release-bundle`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** |

### Upload signing

**Repo mode:** Files **`secrets/upload-keystore.jks.age`** and **`android_upload_signing`** in consolidated JSON (**[`docs/outreach-secrets.schema.json`](outreach-secrets.schema.json)**). Same passphrase as **`outreach-secrets.json.age`** — see **Step 3 Path A**.

**Legacy (GitHub Actions secrets only — skip if using repo mode):**

| Secret | Purpose | Used by |
|--------|---------|---------|
| **`ANDROID_UPLOAD_KEYSTORE_BASE64`** | Upload keystore file (binary), **base64-encoded** (single line) | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** |
| **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** | Keystore password | Same |
| **`ANDROID_UPLOAD_KEY_ALIAS`** | Key alias inside the keystore | Same |
| **`ANDROID_UPLOAD_KEY_PASSWORD`** | Key password | Same |

Populate legacy **`ANDROID_UPLOAD_*`** with **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** (without **`OUTREACH_SIGNING_REPO_MODE`**) or manually. **`GITHUB_TOKEN`** is automatic and is not a repository secret.

Fork and branch-build signing caveats — see **[`github-actions-secrets.md` → Fork and branch-build caveat](github-actions-secrets.md#fork-and-branch-build-caveat)**.

---

## GitFlow overview

This repo follows **classic GitFlow**: **`main`** matches what ships on **Google Play**; **`develop`** integrates feature work. **CI** runs on **branch push**; **PR rulesets** require those checks (and **Dependency Review**) on the head commit before merge. **Shipping** to Play is a human cut (branch, **`bundleRelease`**, Console) unless you add CD.

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
5. QA using **`docs/release-checklist.md`** and **[`google-oauth-checklist.md`](google-oauth-checklist.md)**. Build a signed bundle locally the same way CI does: **`OUTREACH_SECRETS_PASSPHRASE=… bash android/scripts/build.sh release-bundle`** (see **[`docs/build-process.md`](build-process.md)**), or download the artifact from CI.
6. Upload the **AAB** to Play **Internal** or **Closed testing** first. Ensure OAuth / Maps / Firebase allow the correct **SHA-1** signatures (**upload** vs **Play app signing** — see **`google-oauth-checklist.md`**).
7. When ready, merge **`release/X.Y.Z` → `main`** via PR.
8. On **`main`**, tag: **`git tag -a vX.Y.Z -m "Outreach X.Y.Z"`**.
9. **`git checkout develop && git merge main`** so **`develop`** includes release fixes.
10. Delete **`release/X.Y.Z`** after merges complete.

### Hotfix (production emergency)

1. **`git checkout main && git pull`**
2. **`git checkout -b hotfix/X.Y.Z`** — bump **`outreach.versionCode`** and **`outreach.versionName`** in **`android/gradle.properties`**, fix, **`OUTREACH_SECRETS_PASSPHRASE=… bash android/scripts/build.sh release-bundle`**, upload to Play.
3. Merge **`hotfix/X.Y.Z` → `main`**, tag **`vX.Y.Z`**, then **`git checkout develop && git merge main`** (or cherry-pick) so **`develop`** stays in sync.

### Secrets and signing (orthogonal to branches)

**`google-services.json`** and Map keys **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** come from **`android/scripts/setup-secrets.sh`** or manual setup (see README Firebase section). Document internally who holds the **upload keystore** backup and how **Play App Signing** is configured.

---

## Automation

**[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** runs on **`workflow_dispatch`** and on **push** to **`release/**`** or **`hotfix/**`**. The step **Bundle release** invokes **`"${GITHUB_WORKSPACE}/android/scripts/build.sh" release-bundle`** (same as local **`build-release-bundle.sh`**): **`setup-secrets`**, **`scripts/decrypt-age-passphrase.sh`** for repo-mode keystore, **`./gradlew bundleRelease`**, then uploads the **`.aab`** as the **`release-bundle`** artifact.

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
