# CI/CD (GitHub Actions)

How Outreach runs automation on GitHub: workflows, job names you see in **Checks**, repository secrets, branch rules, and **command-line** ways to inspect runs and trigger workflows.

**Secrets matrix and fork behavior:** **[`docs/github-actions-secrets.md`](github-actions-secrets.md)**.

**Release signing, keystore custody, Play uploads:** **[`docs/release-process.md`](release-process.md)**.

**Rulesets and Code Owners (Phase 1):** **[`docs/github-phase1-setup.md`](github-phase1-setup.md)**.

**Local commands** use the same **`android/scripts/build.sh`** as GitHub Actions (not a “mirror”): **[`docs/testing-guide.md`](testing-guide.md)**.

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

### Same script as CI (**Android CI**)

From **`cd outreach/android`**, the workflow runs **`scripts/build.sh`**. Run the same subcommands locally:

| CI job / step | Local (same as Actions) |
|-----------------|-------------------------|
| **`verify`** | **`./scripts/build.sh` `ci` `verify`** |
| **`instrumented`** (build) | **`./scripts/build.sh` `ci` `build-instrumented-apks`** |
| **`instrumented`** (after emulator) | **`./scripts/build.sh` `ci` `connected-mock`** |

(Thin **`android-ci.sh`** still forwards to **`build.sh` `ci`**.)

The script copies **`app/google-services.json.example`** to **`app/google-services.json`** and uses the same default **`MAPS_API_KEY`** as the workflow when unset. For API **34** / **Nexus 6** parity, use the **`instrumented`** job’s emulator settings or a matching AVD.

Release paths (**`lintRelease`**, **`testReleaseUnitTest`**, **`bundleRelease`**) need release signing env or placeholders as in workflows — see **`build-process.md`** and **`release-process.md`**.

### Cursor skill runner

**`.cursor/skills/android-build-tests/scripts/run-build-tests.sh`** — builds Android tests and runs **`connectedDebugAndroidTest`**, or JVM unit tests if no device. Modes: **`emulator`** (default), **`physical`**. Further detail: **[`testing-guide.md`](testing-guide.md)**.

---

## Workflows and jobs

Status check names in the GitHub UI are usually **`Workflow display name / job id`** (for example **`Android CI / verify`** — from workflow **`name:`** + job key).

| Workflow file | Workflow `name` (UI) | Job id | What it does |
|----------------|------------------------|--------|----------------|
| [`.github/workflows/android.yml`](../.github/workflows/android.yml) | **Android CI** | **`verify`** | **`android/scripts/build.sh` `ci` `verify`** (Firebase example + **`check`**). |
| Same | Same | **`instrumented`** | **`build.sh` `ci` `build-instrumented-apks`**, then API **34** / **x86_64** emulator, then **`build.sh` `ci` `connected-mock`**. |
| [`.github/workflows/android-release.yml`](../.github/workflows/android-release.yml) | **Android release** | **`release-readiness`** | **PR** or **push** to **`release/**`** / **`hotfix/**`**: version check, **`lintRelease`**, **`testReleaseUnitTest`**. |
| Same | Same | **`bundle-release`** | **push** / **`workflow_dispatch`** only: **`build.sh` `release-bundle`**, upload **`.aab`** artifact. See **[`github-actions-secrets.md`](github-actions-secrets.md)**. |
| Same | Same | **`deploy-play-internal`** | After parallel jobs succeed: **environment approval** → Play **Internal** track. |
| [`.github/workflows/android-version-bump.yml`](../.github/workflows/android-version-bump.yml) | **Android version bump** | **`bump`** | Manual only on **`release/**`** / **`hotfix/**`**: bumps **`outreach.version*`** in **`gradle.properties`** (never **`main`**). |
| [`.github/workflows/dependency-review.yml`](../.github/workflows/dependency-review.yml) | Dependency Review | **`dependency-review`** | Supply-chain review on **`pull_request`** only (GitHub has no push equivalent). |
| [`.github/workflows/secret-scan.yml`](../.github/workflows/secret-scan.yml) | **Secret Scan** | **`gitleaks`** | Secret scanning on **push** to any branch. |

**Triggers and paths:** **`android.yml`** and **`secret-scan.yml`** run on **`push`** / **`pull_request`** (path-filtered). **`android-release.yml`** runs **`release-readiness`** on **PR** and **push** to **`release/**`** / **`hotfix/**`**; **`bundle-release`** and **`deploy-play-internal`** run on **push** and **`workflow_dispatch`** only. See **`github-actions-secrets.md`**. **`dependency-review.yml`** runs on **`pull_request`** for all branches.

**Concurrency:** Workflows use **`concurrency`** keyed by **`github.ref`** so newer runs cancel superseded ones on the same branch.

---

## Branch builds vs PR merge gates

CI **builds** run on **`push`** to the source branch. **PRs** still require those checks to pass on the **head commit** before merge — GitHub attaches check results to the commit SHA, not the event type.

**Contributor workflow:**

1. **Push** (or re-push) your branch and wait for Actions to finish on that commit.
2. Open or update the PR — the **Checks** tab should show the same status names as the branch push run.
3. After rebasing onto **`develop`**, **`main`**, or a release branch, **push again** so CI re-runs on the new head SHA.

Opening a PR alone does **not** start **Android CI** or **Secret Scan** on the PR event — those attach to **push** on the head branch. **PRs into `release/**`** also run **`Android release / release-readiness`**. **Dependency Review** runs on every **`pull_request`**.

**Rulesets (GitHub → Settings → Rules → Rulesets):** On **`develop`**, **`main`**, **`release/**`**, and **`hotfix/**`**, enable:

- **Require status checks to pass before merging** — use the **exact** check names from the table below.
- **Require branches to be up to date before merging** — forces a fresh push (and branch build) after the target branch moves.

---

## Merge quality gates (branch rulesets)

Configure **GitHub Rulesets → Required status checks** using the **exact** strings from the PR **Checks** tab (often **`Workflow name / job id`**). Checks must have completed on the PR’s **head commit** (from a branch **`push`**, except **Dependency Review**).

| Typical PR target | Often required checks |
|-------------------|------------------------|
| **`develop`** | **`Android CI / verify`**, **`Android CI / instrumented`** *(optional by policy)*, **`Dependency Review / dependency-review`**, **`Secret Scan / gitleaks`** *(names may vary slightly)* |
| **`release/**`** or **`hotfix/**`** | Above + **`Android release / release-readiness`** on PR head commit *(exact name from Checks)* |
| **`main`** | Mirror team policy — often aligned with **`release/**`**. |

**Disable a gate safely:** remove it from Rulesets **before** deleting or renaming the workflow job — otherwise merges can wait forever on a missing check.

### Validate rulesets (dry run)

After enabling required checks:

1. Push a commit to a feature branch that touches **`android/**`** (or use **`gh workflow run "Android CI" --ref my-branch`**).
2. Confirm **Actions** shows **push** runs for **Android CI** and **Secret Scan** (not duplicate PR runs for those workflows).
3. Open a PR into **`develop`** — **Checks** should list the branch-build results on the head SHA plus **Dependency Review** from the PR event.
4. Rebase onto latest **`develop`**, push, and confirm new checks run before merge is allowed.

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

Workflows that define **`workflow_dispatch`** (for example **Android CI**, **Android release**) can be started from the UI (**Actions → workflow → Run workflow**) or:

```bash
gh workflow run "Android CI"
```

```bash
gh workflow run "Android release"
```

Branch:

```bash
gh workflow run "Android CI" --ref my-branch
```

**Android release** **`bundle-release`** requires **`OUTREACH_SECRETS_PASSPHRASE`**, plus either repo-mode signing or legacy **`ANDROID_UPLOAD_*`** secrets. **`deploy-play-internal`** requires **`PLAY_STORE_SERVICE_ACCOUNT_JSON`** and environment **`play-internal-release`** with reviewers ([**`github-actions-secrets.md`](github-actions-secrets.md)**).

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

## Continuous deployment (Play Internal)

**[`android-release.yml`](../.github/workflows/android-release.yml)** uploads to Play **Internal testing** after **`release-readiness`** and **`bundle-release`** succeed. **`deploy-play-internal`** uses environment **`play-internal-release`** for required reviewer approval before upload. Configure secrets per **[`github-actions-secrets.md`](github-actions-secrets.md)**.
