# GitHub Actions — secrets and CI credentials

## Repository secrets

| Secret | Consumers |
|--------|-----------|
| **`OUTREACH_SECRETS_PASSPHRASE`** | **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** — passphrase for **`secrets/outreach-secrets.json.age`** in the repo (same passphrase used locally with **`encrypt-secrets.sh`** / **`setup-secrets.sh`**). If unset or wrong, the release bundle job fails before Gradle (explicit guard, then **`age`** decrypt). |

Workflows that do not appear in the table use **`GITHUB_TOKEN`** only (automatic) or no secrets.

**Legacy reference:** [`secret-scan.yml`](../.github/workflows/secret-scan.yml) passes **`GITHUB_TOKEN`** to **gitleaks** (required by that action).

## Workflow matrix

| Workflow | Purpose | Repo secrets beyond `GITHUB_TOKEN` |
|----------|---------|-------------------------------------|
| [`android.yml`](../.github/workflows/android.yml) | **`verify`**, **`instrumented`** (placeholder Firebase / Maps) | None |
| [`android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **`release-readiness`** — version check, `lintRelease`, `testReleaseUnitTest` (placeholders) | None |
| [`android-release-build.yml`](../.github/workflows/android-release-build.yml) | Decrypt **`.age`**, merge **`release_api_key`** into **`MAPS_API_KEY`**, **`bundleRelease`**, upload **`.aab`** | **`OUTREACH_SECRETS_PASSPHRASE`** |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** | `GITHUB_TOKEN` only |

**Firebase / Maps in most CI jobs:** **`android.yml`** and **`android-release-readiness.yml`** copy **`android/app/google-services.json.example`** and use a non-secret **`MAPS_API_KEY`** placeholder — enough for compile, tests, and mock flows, not production OAuth.

**Release bundle job:** Reads the committed **`secrets/outreach-secrets.json.age`**, decrypts with **`OUTREACH_SECRETS_PASSPHRASE`**, runs **`android/scripts/setup-secrets.sh`** with **`OUTREACH_MAPS_KEY_FIELD=release_api_key`**, then **`bundleRelease`**. Consolidated JSON must include both **`development_api_key`** and **`release_api_key`** (see **`docs/outreach-secrets.schema.json`**).

## Fork and pull-request caveat

- **`pull_request` workflows from forks** do not receive repository secrets; those jobs would see an empty passphrase and would fail the guard step if they depended on **`OUTREACH_SECRETS_PASSPHRASE`**.
- **[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** uses **`workflow_dispatch`** and **`push`** to **`release/**`** / **`hotfix/**`** only — not **`pull_request`** — so it does not run on fork PRs to the upstream repo.
- Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model (for example **`pull_request_target`** with extreme care, or “label to approve” patterns).

## Adding more secrets later

When you add optional CD (**Play upload**, release signing), document each **`secrets.*`** name, which workflow job consumes it, and remove those checks from branch rulesets before deleting the job.

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).
