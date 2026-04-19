# GitHub Actions — secrets and CI credentials

**Workflows, jobs, and `gh` CLI:** **[`ci-cd.md`](ci-cd.md)**.

**Operator checklist** of repository secret names and release steps: **[`release-process.md`](release-process.md#github-repository-secrets)**.

Upload signing can be configured in **repo mode** (committed **`secrets/upload-keystore.jks.age`** + **`android_upload_signing`** inside plaintext JSON encrypted as **`secrets/outreach-secrets.json.age`**) or **legacy mode** (**`ANDROID_UPLOAD_*`** GitHub secrets). Use **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** (**`OUTREACH_SIGNING_REPO_MODE=1`** for repo mode — see **[`release-process.md` → First-time release](release-process.md#first-time-release)**).

## Repository secrets

### Always required for release bundle

| Secret | Consumers |
|--------|-----------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — passphrase for **`secrets/outreach-secrets.json.age`** and (**repo mode**) **`secrets/upload-keystore.jks.age`** (same passphrase as local **`encrypt-secrets.sh`** / **`encrypt-age-passphrase.sh`** / **`setup-secrets.sh`**). If unset or wrong, the release bundle job fails before Gradle. |

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
| [`android.yml`](../.github/workflows/android.yml) | **`verify`**, **`instrumented`** (placeholder Firebase / Maps) | None |
| [`android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **`release-readiness`** — version check, `lintRelease`, `testReleaseUnitTest` (placeholders) | None |
| [`android-release-build.yml`](../.github/workflows/android-release-build.yml) | Decrypt JSON **`.age`**, materialize keystore (**repo** or **legacy**), signed **`bundleRelease`**, upload **`.aab`** | **`OUTREACH_SECRETS_PASSPHRASE`**; and **either** **`ANDROID_UPLOAD_*`** (legacy) **or** files in repo (**`upload-keystore.jks.age`** + JSON — no extra GH secrets for signing). |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** | `GITHUB_TOKEN` only |

**Firebase / Maps in most CI jobs:** **`android.yml`** and **`android-release-readiness.yml`** copy **`android/app/google-services.json.example`** and use a non-secret **`MAPS_API_KEY`** placeholder — enough for compile, tests, and mock flows, not production OAuth.

**Release bundle job:** Decrypts **`outreach-secrets.json.age`** to **`${RUNNER_TEMP}/outreach-secrets.json`**, runs **`setup-secrets.sh`** on that plaintext path with **`OUTREACH_MAPS_KEY_FIELD=release_api_key`**, resolves **repo** vs **legacy** signing, materializes **`${RUNNER_TEMP}/outreach-upload.jks`**, exports Gradle **`ANDROID_UPLOAD_*`** env vars, then **`bundleRelease`**. Consolidated JSON must include **`development_api_key`** and **`release_api_key`**; **repo mode** also needs **`android_upload_signing`** when **`upload-keystore.jks.age`** is present.

## Fork and pull-request caveat

- **`pull_request` workflows from forks** do not receive repository secrets; those jobs would see an empty passphrase and would fail the guard step if they depended on **`OUTREACH_SECRETS_PASSPHRASE`**.
- **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** uses **`workflow_dispatch`** and **`push`** to **`release/**`** / **`hotfix/**`** only — not **`pull_request`** — so it does not run on fork PRs to the upstream repo.
- Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model (for example **`pull_request_target`** with extreme care, or “label to approve” patterns).

## Adding more secrets later

When you add optional CD (**Play upload** API, etc.), document each **`secrets.*`** name, which workflow job consumes it, and remove those checks from branch rulesets before deleting the job.

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).
