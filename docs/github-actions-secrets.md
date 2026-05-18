# GitHub Actions — secrets and CI credentials

**Workflows, jobs, and `gh` CLI:** **[`ci-cd.md`](ci-cd.md)**.

**Operator checklist** of repository secret names and release steps: **[`release-process.md`](release-process.md#github-repository-secrets)**.

Upload signing can be configured in **repo mode** (committed **`secrets/upload-keystore.jks.age`** + **`android_upload_signing`** inside plaintext JSON encrypted as **`secrets/outreach-secrets.json.age`**) or **legacy mode** (**`ANDROID_UPLOAD_*`** GitHub secrets). Use **[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** (**`OUTREACH_SIGNING_REPO_MODE=1`** for repo mode — see **[`release-process.md` → First-time release](release-process.md#first-time-release)**).

## Repository secrets

### Always required for release bundle

| Secret | Consumers |
|--------|-----------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | **[`android-release.yml`](../.github/workflows/android-release.yml)** job **`bundle-release`** — passphrase for **`secrets/outreach-secrets.json.age`** and (**repo mode**) **`secrets/upload-keystore.jks.age`** (same passphrase as local **`encrypt-secrets.sh`** / **`encrypt-upload-keystore-age.sh`** / **`setup-secrets.sh`**). If unset or wrong, the bundle job fails before Gradle. |

### Play Internal deploy (optional until CD enabled)

| Secret | Consumers |
|--------|-----------|
| **`PLAY_STORE_SERVICE_ACCOUNT_JSON`** | **`deploy-play-internal`** in **[`android-release.yml`](../.github/workflows/android-release.yml)** — full JSON body of a Google Play API service account key. May live as a **repository** secret or on the **`play-internal-release`** [environment](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment) (recommended). |

**One-time Play Console setup:** Play Console → **Setup → API access** → link GCP project → create service account → grant **Release to testing tracks** (or Admin) for the app → download JSON key → `gh secret set PLAY_STORE_SERVICE_ACCOUNT_JSON < key.json`.

### Legacy upload signing (optional if using repo mode)

These four secrets are **ignored** when the workflow resolves **repo mode**: **`secrets/upload-keystore.jks.age`** exists **and** decrypted consolidated JSON contains a complete **`android_upload_signing`** object (see **[`docs/outreach-secrets.schema.json`](outreach-secrets.schema.json)**).

| Secret | Consumers |
|--------|-----------|
| **`ANDROID_UPLOAD_KEYSTORE_BASE64`** | **`bundle-release`** — base64-encoded upload **`.jks`** (**legacy**); decoded to **`${RUNNER_TEMP}/outreach-upload.jks`**. |
| **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`** | Same — Gradle **`ANDROID_UPLOAD_KEYSTORE_PASSWORD`**. |
| **`ANDROID_UPLOAD_KEY_ALIAS`** | Same — Gradle **`ANDROID_UPLOAD_KEY_ALIAS`**. |
| **`ANDROID_UPLOAD_KEY_PASSWORD`** | Same — Gradle **`ANDROID_UPLOAD_KEY_PASSWORD`**. |

Workflows that do not appear in the table use **`GITHUB_TOKEN`** only (automatic) or no secrets.

**Legacy reference:** [`secret-scan.yml`](../.github/workflows/secret-scan.yml) passes **`GITHUB_TOKEN`** to **gitleaks** (required by that action).

## GitHub Environment (approval gate)

| Environment | Purpose |
|-------------|---------|
| **`play-internal-release`** | **`deploy-play-internal`** waits for a **required reviewer** before uploading to Play **Internal testing**. Create under **Settings → Environments** with at least one required reviewer; optionally restrict deployment branches to **`release/**`** and **`hotfix/**`**. |

When **`release-readiness`** and **`bundle-release`** finish on a **push**, the workflow pauses at **Waiting for review** until someone approves the deployment in the Actions UI.

## Workflow matrix

| Workflow | Purpose | Repo secrets beyond `GITHUB_TOKEN` |
|----------|---------|-------------------------------------|
| [`android.yml`](../.github/workflows/android.yml) | **`verify`**, **`instrumented`** on **push** / **PR** (placeholder Firebase / Maps) | None |
| [`android-release.yml`](../.github/workflows/android-release.yml) | **`release-readiness`** on **PR** or **push** to **`release/**`** / **`hotfix/**`**; **`bundle-release`** + **`deploy-play-internal`** on **push** / **`workflow_dispatch`** only | **`OUTREACH_SECRETS_PASSPHRASE`** (+ legacy **`ANDROID_UPLOAD_*`** or repo-mode files) for **`bundle-release`**; **`PLAY_STORE_SERVICE_ACCOUNT_JSON`** for **`deploy-play-internal`** |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** on **`pull_request`** only | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** on **push** | `GITHUB_TOKEN` only |

**Firebase / Maps in most CI jobs:** **`android.yml`** and **`release-readiness`** copy **`android/app/google-services.json.example`** and use a non-secret **`MAPS_API_KEY`** placeholder — enough for compile, tests, and mock flows, not production OAuth.

**Release bundle job:** Runs **`android/scripts/build.sh release-bundle`** (**`build-release-bundle.sh`** is a one-line alias), which calls **`setup-secrets.sh`** the same way as local developers (default resolution, dual Maps keys), sets **`OUTREACH_EXPORT_PLAINTEXT_JSON`** so the consolidated JSON is available for signing checks, then resolves **repo** vs **legacy** signing (**repo**: **`scripts/decrypt-age-passphrase.sh`** decrypts **`upload-keystore.jks.age`** into **`${RUNNER_TEMP}/outreach-upload.jks`**), exports Gradle **`ANDROID_UPLOAD_*`** env vars, then **`bundleRelease`**. Consolidated JSON must include **`development_api_key`** and **`release_api_key`**; **repo mode** also needs **`android_upload_signing`** when **`upload-keystore.jks.age`** is present.

## Fork and branch-build caveat

- **Fork contributors:** upstream Actions do **not** run on pushes that exist only on a fork unless the fork has Actions enabled and runs workflows there. Prefer branches in the upstream repo, or ensure the fork runs the same **`push`** workflows before opening a PR.
- **`pull_request` from forks** does not receive repository secrets; **`bundle-release`** and **`deploy-play-internal`** do not run on **`pull_request`** (only **`release-readiness`**).
- **[`dependency-review.yml`](../.github/workflows/dependency-review.yml)** is the only workflow still on **`pull_request`** for all branches; it uses **`GITHUB_TOKEN`** only.
- Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model.

## Adding more secrets later

When you extend CD (e.g. Closed or Production tracks), document each **`secrets.*`** name, which workflow job consumes it, and update branch rulesets before deleting jobs.

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).
