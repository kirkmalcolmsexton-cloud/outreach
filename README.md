# Outreach

**Repository:** [github.com/kirkmalcolmsexton-cloud/outreach](https://github.com/kirkmalcolmsexton-cloud/outreach)

**Jira:** [kirkmalcolmsexton.atlassian.net](https://kirkmalcolmsexton.atlassian.net) (issues and sprints; **`SCRUM-*`** keys in branch names refer to this site.)

Outreach is an **Android** app for teams that work from a **Google Sheet** of households: sign in with Google, pick the spreadsheet and zip-code tabs in settings, then use a **map or list** home screen to plan routes and log visits. Data **syncs from Sheets into local storage** for offline use; visit changes are **queued and flushed** when the network is back. A **Firestore** layer adds collaboration scaffolding (presence and activity) without replacing the sheet as the source of truth.

Stack highlights: **Kotlin**, **Jetpack Compose**, **Room + DataStore + WorkManager**, Google Sign-In with Sheets/Drive scopes.

The Android app lives in **`android/`** as a **single-module** project (root Gradle + **`app/`** only). See **`docs/android-architecture.md`** for layout and data flow.

---

## README guide — pick your path

| Who you are | Where to start |
|---------------|----------------|
| **New to Android** — viewing this on GitHub and need the shortest path to **build** and run **tests on the emulator** | **[`docs/developer-onboarding.md` — First-time setup (emulator)](docs/developer-onboarding.md#first-time-setup-emulator)** — workstation prep through **`connectedDebugAndroidTest`**. Team **`secrets/outreach-secrets.json.age`** + **`~/etc/outreach.env`**: see **§6b** there. |
| **Past first setup** — exploring Outreach using a **real phone** over USB | **[`docs/testing-guide.md` — Physical device testing](docs/testing-guide.md#physical-device-testing)** |
| **Comfortable with Android** — you want **commands, flags, and scripts** without extra narrative | **[CLI quick reference](docs/ci-cd.md#cli-quick-reference)** (**`gh`**, Gradle parity); testing detail — **[`docs/testing-guide.md`](docs/testing-guide.md)** |
| **Cursor / multi-root workspace** — you use the workspace repo next to Outreach | **[`development-process.md` → Cursor and multi-root workspace](docs/development-process.md#cursor-and-multi-root-workspace)** |
| **Cutting a Play release / branching** — maintainers integrating and shipping | [**Release process (GitFlow)**](#release-process-gitflow) |
| **CI/CD & PR checks** — GitHub Actions, merge gates, **`gh`** | **[`docs/ci-cd.md`](docs/ci-cd.md)** (summary: [**CI/CD (GitHub Actions)**](#cicd-github-actions)) |

---

## CI/CD (GitHub Actions)

**[`docs/ci-cd.md`](docs/ci-cd.md)** — workflows, merge gates, **[CLI quick reference](docs/ci-cd.md#cli-quick-reference)** (Gradle shortcuts, parity with **`verify`** / **`instrumented`**, Cursor skill runner), **`gh`** CLI (runs, dispatch, **`pr checks`**), secrets pointers.

Phase **1** (rulesets + **`CODEOWNERS`** without blocking CI yet): **[`docs/github-phase1-setup.md`](docs/github-phase1-setup.md)**.

**Secrets:** Release bundle needs **`OUTREACH_SECRETS_PASSPHRASE`** and **`ANDROID_UPLOAD_*`** — **[`docs/release-process.md`](docs/release-process.md#github-repository-secrets)** and **[`docs/github-actions-secrets.md`](docs/github-actions-secrets.md)**.

---

## Release process (GitFlow)

Branching, versioning, **first-time** keystore and GitHub secrets, Play uploads, and CI signing are documented in **[`docs/release-process.md`](docs/release-process.md)**.

---

## Documentation

**Map:** Contributor setup → **[`developer-onboarding.md`](docs/developer-onboarding.md)**; day-to-day branching/PRs → **[`development-process.md`](docs/development-process.md)**; local builds → **[`build-process.md`](docs/build-process.md)**; shipping/versioning/keystore custody → **[`release-process.md`](docs/release-process.md)**. **GitHub Actions** workflows, required checks, and **`gh`** live in **[`ci-cd.md`](docs/ci-cd.md)**. **Secret *names*** and which job uses each → **[`github-actions-secrets.md`](docs/github-actions-secrets.md)** (release process covers *how* to create and rotate them).

### Issue tracking

- **Jira** — [kirkmalcolmsexton.atlassian.net](https://kirkmalcolmsexton.atlassian.net)

### Testing

Read in order of **policy → commands → code → scenarios**: **[`testing-process.md`](docs/testing-process.md)** (when/what to run), **[`testing-guide.md`](docs/testing-guide.md)** (Gradle, emulator and **[physical USB](docs/testing-guide.md#physical-device-testing)**, scripts, OAuth / manual test account, troubleshooting), **[`ui-testing.md`](docs/ui-testing.md)** (tags, **`TestRuntime`**, test harness), **[`test-scenarios-given-when-then.md`](docs/test-scenarios-given-when-then.md)** (scenario catalog).

### Release and Play Store

- **[`release-process.md`](docs/release-process.md)** — GitFlow, versioning, keystore/GitHub secrets, bundle workflow, Play uploads
- **[`release-checklist.md`](docs/release-checklist.md)** — functional QA before ship

### CI and GitHub

- **[`ci-cd.md`](docs/ci-cd.md)** — workflow/job reference, **`gh`** CLI, merge gates, Gradle parity with Actions
- **[`github-phase1-setup.md`](docs/github-phase1-setup.md)** — rulesets, **`CODEOWNERS`**, Phase 1 enablement

### Repository secrets (reference only)

- **[`github-actions-secrets.md`](docs/github-actions-secrets.md)** — matrix of **`secrets.*`** names vs workflows (no duplication of release/how-to—see **`release-process.md`**)

### Product and engineering reference

- **[`android-architecture.md`](docs/android-architecture.md)** — app layout and data flow
- **[`sync-and-collab.md`](docs/sync-and-collab.md)** — Sheets sync and collaboration
- **[`outreach-secrets.schema.json`](docs/outreach-secrets.schema.json)** — JSON schema for **`secrets/outreach-secrets.example.json`**
- **[`google-oauth-checklist.md`](docs/google-oauth-checklist.md)** — sign-in / OAuth / fingerprint troubleshooting (complements **`testing-guide`** § OAuth for *test accounts*)

### Everyday process (quick links)

- **[`developer-onboarding.md`](docs/developer-onboarding.md)** — first machine + emulator **`connectedDebugAndroidTest`**
- **[`development-process.md`](docs/development-process.md)** — feature branches, PRs, quality gates, **[Cursor / multi-root workspace](docs/development-process.md#cursor-and-multi-root-workspace)**
- **[`build-process.md`](docs/build-process.md)** — debug vs release artifacts, local secrets wiring
