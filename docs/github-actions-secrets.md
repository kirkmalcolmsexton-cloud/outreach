# GitHub Actions — secrets and CI credentials

**Workflows, jobs, and `gh` CLI:** **[`ci-cd.md`](ci-cd.md)**.

**Operator checklist** of repository secret names and release steps: **[`release-process.md`](release-process.md#github-repository-secrets)**.

You can populate the **`ANDROID_UPLOAD_*`** secrets by running **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** locally (see **[`release-process.md` → First-time release](release-process.md#first-time-release)**).

## Repository secrets

| Secret | Consumers |
|--------|-----------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — passphrase for **`secrets/outreach-secrets.json.age`** in the repo (same passphrase used locally with **`encrypt-secrets.sh`** / **`setup-secrets.sh`**). If unset or wrong, the release bundle job fails before Gradle (explicit guard, then **`age`** decrypt). |
| **`ANDROID_UPLOAD_KEYSTORE_BASE64`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — base64-encoded upload **`.jks`** / **`.keystore`** file for **`bundleRelease`**. Decoded to **`${RUNNER_TEMP}/outreach-upload.jks`** before Gradle; empty → guard failure. |
| **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — keystore password (passed as **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** env to Gradle). |
| **`ANDROID_UPLOAD_KEY_ALIAS`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — signing key alias (Gradle env **`ANDROID_UPLOAD_KEY_ALIAS`**). |
| **`ANDROID_UPLOAD_KEY_PASSWORD`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — key password (Gradle env **`ANDROID_UPLOAD_KEY_PASSWORD`**). |

Workflows that do not appear in the table use **`GITHUB_TOKEN`** only (automatic) or no secrets.

**Legacy reference:** [`secret-scan.yml`](../.github/workflows/secret-scan.yml) passes **`GITHUB_TOKEN`** to **gitleaks** (required by that action).

## Workflow matrix

| Workflow | Purpose | Repo secrets beyond `GITHUB_TOKEN` |
|----------|---------|-------------------------------------|
| [`android.yml`](../.github/workflows/android.yml) | **`verify`**, **`instrumented`** (placeholder Firebase / Maps) | None |
| [`android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **`release-readiness`** — version check, `lintRelease`, `testReleaseUnitTest` (placeholders) | None |
| [`android-release-build.yml`](../.github/workflows/android-release-build.yml) | Decrypt **`.age`**, merge **`release_api_key`** into **`MAPS_API_KEY`**, decode keystore, signed **`bundleRelease`**, upload **`.aab`** | **`OUTREACH_SECRETS_PASSPHRASE`**, **`ANDROID_UPLOAD_KEYSTORE_BASE64`**, **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`**, **`ANDROID_UPLOAD_KEY_ALIAS`**, **`ANDROID_UPLOAD_KEY_PASSWORD`** |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** | `GITHUB_TOKEN` only |

**Firebase / Maps in most CI jobs:** **`android.yml`** and **`android-release-readiness.yml`** copy **`android/app/google-services.json.example`** and use a non-secret **`MAPS_API_KEY`** placeholder — enough for compile, tests, and mock flows, not production OAuth.

**Release bundle job:** Reads the committed **`secrets/outreach-secrets.json.age`**, decrypts with **`OUTREACH_SECRETS_PASSPHRASE`**, runs **`android/scripts/setup-secrets.sh`** with **`OUTREACH_MAPS_KEY_FIELD=release_api_key`**, decodes **`ANDROID_UPLOAD_KEYSTORE_BASE64`** to a temp **`.jks`**, exports **`ANDROID_UPLOAD_*`** env for Gradle, then **`bundleRelease`**. Consolidated JSON must include both **`development_api_key`** and **`release_api_key`** (see **`docs/outreach-secrets.schema.json`**).

## Fork and pull-request caveat

- **`pull_request` workflows from forks** do not receive repository secrets; those jobs would see an empty passphrase and would fail the guard step if they depended on **`OUTREACH_SECRETS_PASSPHRASE`** or **`ANDROID_UPLOAD_*`**.
- **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** uses **`workflow_dispatch`** and **`push`** to **`release/**`** / **`hotfix/**`** only — not **`pull_request`** — so it does not run on fork PRs to the upstream repo.
- Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model (for example **`pull_request_target`** with extreme care, or “label to approve” patterns).

## Adding more secrets later

When you add optional CD (**Play upload** API, etc.), document each **`secrets.*`** name, which workflow job consumes it, and remove those checks from branch rulesets before deleting the job.

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).
