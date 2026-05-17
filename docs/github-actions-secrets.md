# GitHub Actions — secrets and CI credentials

**Workflows, jobs, and `gh` CLI:** **[`ci-cd.md`](ci-cd.md)**.

**Operator checklist** of repository secret names and release steps: **[`release-process.md`](release-process.md#github-repository-secrets)**.

Upload signing can be configured in **repo mode** (committed **`secrets/upload-keystore.jks.age`** + **`android_upload_signing`** inside plaintext JSON encrypted as **`secrets/outreach-secrets.json.age`**) or **legacy mode** (**`ANDROID_UPLOAD_*`** GitHub secrets). Use **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** (**`OUTREACH_SIGNING_REPO_MODE=1`** for repo mode — see **[`release-process.md` → First-time release](release-process.md#first-time-release)**).

## Repository secrets

### Always required for release bundle

| Secret | Consumers |
|--------|-----------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — passphrase for **`secrets/outreach-secrets.json.age`** and (**repo mode**) **`secrets/upload-keystore.jks.age`** (same passphrase as local **`encrypt-secrets.sh`** / **`encrypt-upload-keystore-age.sh`** / **`setup-secrets.sh`**). If unset or wrong, the release bundle job fails before Gradle. |

### Legacy upload signing (optional if using repo mode)

These four secrets are **ignored** when the workflow resolves **repo mode**: **`secrets/upload-keystore.jks.age`** exists **and** decrypted consolidated JSON contains a complete **`android_upload_signing`** object (see **[`docs/outreach-secrets.schema.json`](outreach-secrets.schema.json)**).

| Secret | Consumers |
|--------|-----------|
| **`ANDROID_UPLOAD_KEYSTORE_BASE64`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — base64-encoded upload **`.jks`** (**legacy**); decoded to **`${RUNNER_TEMP}/outreach-upload.jks`**. |
| **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — keystore password → Gradle **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`**. |
| **`ANDROID_UPLOAD_KEY_ALIAS`** | Same — Gradle **`ANDROID_UPLOAD_KEY_ALIAS`**. |
| **`ANDROID_UPLOAD_KEY_PASSWORD`** | Same — Gradle **`ANDROID_UPLOAD_KEY_PASSWORD`**. |

Workflows that do not appear in the table use **`GITHUB_TOKEN`** only (automatic) or no secrets.

**Legacy reference:** [`secret-scan.yml`](../.github/workflows/secret-scan.yml) passes **`GITHUB_TOKEN`** to **gitleaks** (required by that action).

## Workflow matrix

| Workflow | Purpose | Repo secrets beyond `GITHUB_TOKEN` |
|----------|---------|-------------------------------------|
| [`android.yml`](../.github/workflows/android.yml) | **`verify`**, **`instrumented`** on **push** (placeholder Firebase / Maps) | None |
| [`android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **`release-readiness`** on **push** to **`release/**`** / **`hotfix/**`** — version check, `lintRelease`, `testReleaseUnitTest` (placeholders) | None |
| [`android-release-build.yml`](../.github/workflows/android-release-build.yml) | Runs **`android/scripts/build.sh release-bundle`** (thin alias **`android/scripts/build-release-bundle.sh`): same **`setup-secrets.sh`** as local dev (exports plaintext for signing **`jq`**); repo mode decrypts **`upload-keystore.jks.age`** via **`scripts/decrypt-age-passphrase.sh`**; then **`bundleRelease`** + upload **`.aab`** artifact | **`OUTREACH_SECRETS_PASSPHRASE`**; and **either** **`ANDROID_UPLOAD_*`** (legacy) **or** files in repo (**`upload-keystore.jks.age`** + JSON — no extra GH secrets for signing). |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** on **`pull_request`** only | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** on **push** | `GITHUB_TOKEN` only |

**Firebase / Maps in most CI jobs:** **`android.yml`** and **`android-release-readiness.yml`** copy **`android/app/google-services.json.example`** and use a non-secret **`MAPS_API_KEY`** placeholder — enough for compile, tests, and mock flows, not production OAuth.

**Release bundle job:** Runs **`android/scripts/build.sh release-bundle`** (**`build-release-bundle.sh`** is a one-line alias), which calls **`setup-secrets.sh`** the same way as local developers (default resolution, dual Maps keys), sets **`OUTREACH_EXPORT_PLAINTEXT_JSON`** so the consolidated JSON is available for signing checks, then resolves **repo** vs **legacy** signing (**repo**: **`scripts/decrypt-age-passphrase.sh`** decrypts **`upload-keystore.jks.age`** into **`${RUNNER_TEMP}/outreach-upload.jks`**), exports Gradle **`ANDROID_UPLOAD_*`** env vars, then **`bundleRelease`**. Consolidated JSON must include **`development_api_key`** and **`release_api_key`**; **repo mode** also needs **`android_upload_signing`** when **`upload-keystore.jks.age`** is present.

## Fork and branch-build caveat

Most CI runs on **`push`** to the branch (not **`pull_request`**). Merge gates still read check results on the PR head commit from that push.

- **Fork contributors:** upstream Actions do **not** run on pushes that exist only on a fork unless the fork has Actions enabled and runs workflows there. Prefer branches in the upstream repo, or ensure the fork runs the same **`push`** workflows before opening a PR.
- **`pull_request` from forks** does not receive repository secrets; jobs that depend on **`OUTREACH_SECRETS_PASSPHRASE`** would fail if run on fork PRs. **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** uses **`workflow_dispatch`** and **`push`** to **`release/**`** / **`hotfix/**`** only — never **`pull_request`**.
- **[`dependency-review.yml`](../.github/workflows/dependency-review.yml)** is the only workflow still on **`pull_request`**; it uses **`GITHUB_TOKEN`** only.
- Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model (for example **`pull_request_target`** with extreme care, or “label to approve” patterns).

## Adding more secrets later

When you add optional CD (**Play upload** API, etc.), document each **`secrets.*`** name, which workflow job consumes it, and remove those checks from branch rulesets before deleting the job.

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).
