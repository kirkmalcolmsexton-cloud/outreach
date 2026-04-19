# CI/CD (GitHub Actions)

How Outreach runs automation on GitHub: workflows, job names you see in **Checks**, repository secrets, branch rules, and **command-line** ways to inspect runs and trigger workflows.

**Secrets matrix and fork behavior:** **[`docs/github-actions-secrets.md`](github-actions-secrets.md)**.

**Release signing, keystore custody, Play uploads:** **[`docs/release-process.md`](release-process.md)**.

**Rulesets and Code Owners (Phase 1):** **[`docs/github-phase1-setup.md`](github-phase1-setup.md)**.

**Local commands that approximate CI Android jobs** (Gradle, emulator, **`connectedDebugAndroidTest`**): **[`docs/testing-guide.md`](testing-guide.md)**.

---

## CLI quick reference

Assume **`cd outreach/android`** unless noted.

### Where to read more

| Topic | Document |
|-------|----------|
| **Testing** — Gradle connected tasks, emulator defaults, scripts, instrumentation **`-P`** flags, devices, troubleshooting | **[`testing-guide.md`](testing-guide.md)** |
| **Build / secrets** — **`assembleDebug`**, **`setup-secrets.sh`**, **`~/etc/outreach.env`** | **[`build-process.md`](build-process.md)**, **[`developer-onboarding.md`](developer-onboarding.md)** |

### Gradle shortcuts

| Task | Purpose |
|------|---------|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew connectedDebugAndroidTest` | Run **`src/androidTest`** on connected device(s) |

Single test class: **`-Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.ClassName`**.

Default Outreach emulator AVD: **`Galaxy_S938U_API36_x86_64`**. Once the emulator is online: **`export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"`**.

### Parity with CI jobs (**Android CI**)

| CI job | Approximate local command |
|--------|---------------------------|
| **`verify`** | `./gradlew check` (CI also substitutes **`google-services.json`** from example and a placeholder **`MAPS_API_KEY`**) |
| **`instrumented`** | Start an emulator (API **34** if you want strict parity), then `./gradlew connectedDebugAndroidTest -PoutreachAuthResolution=mock` |

Release paths (**`lintRelease`**, **`testReleaseUnitTest`**, **`bundleRelease`**) need release signing env or placeholders as in workflows — see **`build-process.md`** and **`release-process.md`**.

### Cursor skill runner

**`.cursor/skills/android-build-tests/scripts/run-build-tests.sh`** — builds Android tests and runs **`connectedDebugAndroidTest`**, or JVM unit tests if no device. Modes: **`emulator`** (default), **`physical`**. Further detail: **[`testing-guide.md`](testing-guide.md)**.

---

## Workflows and jobs

Status check names in the GitHub UI are usually **`Workflow display name / job id`** (for example **`Android CI / verify`** — from workflow **`name:`** + job key).

| Workflow file | Workflow `name` (UI) | Job id | What it does |
|----------------|------------------------|--------|----------------|
| [`.github/workflows/android.yml`](../.github/workflows/android.yml) | **Android CI** | **`verify`** | Gradle wrapper validation, **`google-services.json`** from example, **`./gradlew check`**. |
| Same | Same | **`instrumented`** | API **34** emulator, **`connectedDebugAndroidTest`** with **`-PoutreachAuthResolution=mock`** after **`verify`**. |
| [`.github/workflows/android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **Android release readiness** | **`release-readiness`** | When PR **base** is **`release/**`** or **`hotfix/**`**: version check script, **`lintRelease`**, **`testReleaseUnitTest`**. |
| [`.github/workflows/android-release-build.yml`](../.github/workflows/android-release-build.yml) | **Android release bundle** | **`bundle-release`** | **`workflow_dispatch`** or **push** to **`release/**`** / **`hotfix/**`**: decrypt **`outreach-secrets.json.age`**, materialize upload keystore (**repo** **`.age`** file or legacy GitHub secrets), **`bundleRelease`**, upload **`.aab`**. See **[`github-actions-secrets.md`](github-actions-secrets.md)**. |
| [`.github/workflows/android-version-bump.yml`](../.github/workflows/android-version-bump.yml) | **Android version bump** | **`bump`** | Manual only on **`release/**`** / **`hotfix/**`**: bumps **`outreach.version*`** in **`gradle.properties`** (never **`main`**). |
| [`.github/workflows/dependency-review.yml`](../.github/workflows/dependency-review.yml) | Dependency Review | **`dependency-review`** | Supply-chain review (requires dependency graph where applicable). |
| [`.github/workflows/secret-scan.yml`](../.github/workflows/secret-scan.yml) | **Secret Scan** | **`gitleaks`** | Secret scanning. |

**Triggers and paths:** **`android.yml`** runs on **`push`** / **`pull_request`** when **`android/**`** or that workflow file changes — doc-only PRs may skip Android CI. **`android-release-build.yml`** does **not** run on arbitrary PRs from forks with secrets; see **`github-actions-secrets.md`**.

**Concurrency:** Workflows use **`concurrency`** so newer runs cancel superseded ones on the same branch/ref where configured.

---

## Merge quality gates (branch rulesets)

Configure **GitHub Rulesets → Required status checks** using the **exact** strings from the PR **Checks** tab (often **`Workflow name / job id`**).

| Typical PR target | Often required checks |
|-------------------|------------------------|
| **`develop`** | **`Android CI / verify`**, **`Android CI / instrumented`** *(optional by policy)*, **`Dependency Review / dependency-review`**, **`Secret Scan / gitleaks`** *(names may vary slightly)* |
| **`release/**`** or **`hotfix/**`** | Above + **`Android release readiness / release-readiness`** *(exact name from Checks)* |
| **`main`** | Mirror team policy — often aligned with **`release/**`**. |

**Disable a gate safely:** remove it from Rulesets **before** deleting or renaming the workflow job — otherwise merges can wait forever on a missing check.

---

## GitHub CLI (`gh`)

Install: [GitHub CLI](https://cli.github.com/). Authenticate with **`gh auth login`**. Repo context: **`cd`** to a clone or pass **`-R owner/repo`**.

### Inspect workflows and runs

```bash
gh workflow list
```

```bash
gh run list --limit 15
```

```bash
gh run watch
```

Follow logs for the latest run on the current branch:

```bash
gh run view --web
```

### Re-run failed jobs

```bash
gh run rerun <run-id> --failed
```

Use **`gh run list`** to find **`<run-id>`**, or open the run in the browser from **`gh run view --web`**.

### Dispatch a workflow manually

Workflows that define **`workflow_dispatch`** (for example **Android CI**, **Android release bundle**) can be started from the UI (**Actions → workflow → Run workflow**) or:

```bash
gh workflow run "Android CI"
```

```bash
gh workflow run "Android release bundle"
```

Branch:

```bash
gh workflow run "Android CI" --ref my-branch
```

**Release bundle** requires **`OUTREACH_SECRETS_PASSPHRASE`**, plus either repo-mode signing (**`secrets/upload-keystore.jks.age`** + **`android_upload_signing`** in plaintext JSON behind **`outreach-secrets.json.age`**) or all legacy **`ANDROID_UPLOAD_*`** secrets ([**`github-actions-secrets.md`](github-actions-secrets.md)**).

### PR checks from the terminal

```bash
gh pr checks
```

### Repository secrets (visibility)

Listing secret **names** (not values):

```bash
gh secret list
```

Setting secrets is usually done in the GitHub UI or via **`gh secret set NAME --body …`** — document new names in **`github-actions-secrets.md`** and **`release-process.md`**.

---

## Continuous deployment (optional)

If you add a workflow that uploads to Play Internal, chain **`needs:`** to **`verify`** / **`release-readiness`** (by job id) before any signing/upload job. Store API credentials as repository secrets and document them like **`ANDROID_UPLOAD_*`**.
