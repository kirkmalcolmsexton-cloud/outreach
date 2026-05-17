# Development process

Day-to-day workflow for contributing to Outreach: branching, pull requests, and quality gates. **Shipping** releases (GitFlow, versioning, Play Store, secrets) is documented separately in **[`docs/release-process.md`](release-process.md)**.

## Branch model (summary)

- **`develop`** — integration branch for ongoing feature work.
- **`feature/<name>`** — branch from **`develop`**; open PRs **into `develop`**.
- **`release/X.Y.Z`** / **`hotfix/X.Y.Z`** — cut when preparing a Play release or an urgent production fix; details, versioning, and merge order are in **[`docs/release-process.md`](release-process.md)**.

Do not bump **`main`** or Play **`versionCode`** from feature branches unless you are following the release/hotfix flow described there.

## Routine feature work

1. **`git checkout develop && git pull`**
2. **`git checkout -b feature/<short-description-or-ticket>`**
3. Implement, **push**, and wait for branch CI (see **Reviews and CI** below); then open a PR **into `develop`**.
4. Address review feedback; **re-push** after rebase or new commits so checks run on the latest head SHA; merge when checks pass and approval is in place.

## Pull requests

- Describe **what** changed and **why** (user-visible behavior, risk, and test evidence).
- Keep PRs focused; prefer follow-up tickets over drive-by refactors.
- Touch **`android/**`** when you need CI workflows that filter on that path — doc-only pushes may skip Android jobs ([README → CI/CD](../README.md#cicd-github-actions)).

## Reviews and CI

Build workflows run on **branch push**, not on opening a PR. Rulesets still require those checks on the PR **head commit** before merge. Full model, ruleset settings, and validation steps: **[`docs/ci-cd.md` → Branch builds vs PR merge gates](ci-cd.md#branch-builds-vs-pr-merge-gates)**. Workflow names, **`gh`** commands, and local Gradle parity: **[`docs/ci-cd.md`](ci-cd.md)**. Short summary: [README → CI/CD (GitHub Actions)](../README.md#cicd-github-actions).

Typical expectations (from the latest **push** to your branch, plus **Dependency Review** on the PR):

- **`Android CI / verify`** — Gradle **`check`** (compilation, unit tests, lint where applicable).
- **`Android CI / instrumented`** — emulator **`connectedDebugAndroidTest`** with **`mock`** auth (optional as a required gate depending on policy).
- **Dependency Review** — runs on **`pull_request`** only.
- **Secret Scan** — runs on **push**.

Use **[`docs/testing-process.md`](testing-process.md)** for what to run before pushing; **[`docs/testing-guide.md`](testing-guide.md)** for exact commands and device setup. **[`docs/build-process.md`](build-process.md)** covers debug vs release artifacts and secrets wiring.

## Environment setup

New machine or contributor: **[`docs/developer-onboarding.md`](developer-onboarding.md)**.

Daily command reference: **[`ci-cd.md` → CLI quick reference](ci-cd.md#cli-quick-reference)** (Gradle + **`gh`**); **[`testing-guide.md`](testing-guide.md)** for devices and instrumentation.

## Cursor and multi-root workspace

If you use **Cursor** with a **multi-root workspace** that includes Outreach alongside editor meta-config, clone the **`cursor-workspace`** repository **next to** **`outreach`** (same parent folder). Layout, opening **`cursor-workspace.code-workspace`**, and adding more repos are documented there — **start with the README in that repo** (path when cloned as a sibling: **`../cursor-workspace/README.md`**).

Outreach development does **not** require that workspace; you can open **`android/`** in Android Studio or any editor on its own.
