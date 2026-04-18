# GitHub Actions — secrets and CI credentials

Current workflows avoid repository **Secrets** beyond `GITHUB_TOKEN` (automatic). Nothing in `.github/workflows/` reads `secrets.*` except `secret-scan.yml`, which passes `GITHUB_TOKEN` to **gitleaks** (required by that action).

| Workflow | Purpose | Repo secrets |
|----------|---------|----------------|
| [`android.yml`](../.github/workflows/android.yml) | **`verify`** (`./gradlew check`), **`instrumented`** (emulator + mock auth) | None |
| [`android-release-readiness.yml`](../.github/workflows/android-release-readiness.yml) | **`release-readiness`** — version vs branch, `lintRelease`, `testReleaseUnitTest` | None |
| [`dependency-review.yml`](../.github/workflows/dependency-review.yml) | **Dependency Review** | None |
| [`secret-scan.yml`](../.github/workflows/secret-scan.yml) | **gitleaks** | `GITHUB_TOKEN` only |

**Firebase / Maps in CI:** Jobs copy **`android/app/google-services.json.example`** to **`google-services.json`** and set **`MAPS_API_KEY`** to a non-secret placeholder in the workflow environment. That is enough for compile, unit tests, lint, and mock instrumentation — not for real OAuth against production Firebase.

**Fork PRs:** Workflows use **`permissions: contents: read`** where applicable. Do not add signing or Play upload jobs that consume repo secrets on **`pull_request`** from forks without an explicit trust model (for example **`pull_request_target`** with extreme care, or “label to approve” patterns).

**Dependency graph:** [**Dependency Review**](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review) needs the dependency graph enabled for the repository (and compatible manifest lockfiles where applicable).

When you add optional CD (**`bundleRelease`**, Play upload), document each new **`secrets.*`** name, which workflow job consumes it, and remove those checks from branch rulesets before deleting the job.
