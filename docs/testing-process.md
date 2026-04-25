# Testing process

How we validate Outreach changes **before merge and release**: which test layers matter, how they relate to CI, when to exercise a physical device or **`real`** Firebase auth, and where to look for scripted QA.

**Operational commands** (Gradle, **`adb`**, **`ANDROID_SERIAL`**, instrumentation **`-P`** flags, scripts, emulator defaults, troubleshooting): **[`docs/testing-guide.md`](testing-guide.md)**.

**Implementation details** of the UI suite (Compose **`TestTags`**, **`TestRuntime`**, overrides, mocks vs Firebase): **[`docs/ui-testing.md`](ui-testing.md)**.

**Scenario inventory** (Given–When–Then): **[`docs/test-scenarios-given-when-then.md`](test-scenarios-given-when-then.md)**.

## Layers vs CI

| Layer | Purpose |
|-------|---------|
| **`./scripts/build.sh` `ci` `verify`** | Same as CI **`verify`**: `google-services` from example, then **`./gradlew check`**. |
| **`./scripts/build.sh` `ci` `build-instrumented-apks`** then **`ci` `connected-mock`** | Same as CI **`instrumented`** (API **34** in Actions; use a matching local AVD for strict parity). |

Prefer those script subcommands (or the equivalent Gradle with the same `google-services` and env) before pushing when your change touches behavior covered by **`androidTest`**. Ad-hoc **`./gradlew check`** is fine for quick checks if you are not depending on CI-identical config.

## Auth: `mock` vs `real`

- **`mock`** (CI default): no Google OAuth; predictable signed-in/out UI for automation.
- **`real`**: production-like **Firebase**; manual sign-in when tests require an authenticated shell.

Use **`mock`** for PR parity and repeatability. Use **`real`** only when you must validate OAuth, tokens, or Firebase-dependent paths that mocks skip. Gradle flags and examples: **[`testing-guide.md` → Auth modes](testing-guide.md#auth-modes-and-instrumentation-cli-flags)**.

## Physical device

Run on hardware when you need **`real`** auth, OEM/**`adb`** quirks, Maps/location realism, or performance signals. Prerequisites, **`ANDROID_SERIAL`**, **`adb reverse`** for debug ingest, and the interactive **`run-physical-ui-tests.sh`** runner are documented in **[`testing-guide.md`](testing-guide.md)** (see **Physical device testing** and **Interactive physical device runner** sections there).

## Before release

Follow **[`docs/release-checklist.md`](release-checklist.md)**. Complement scripted checks with manual flows (sign-in, sheets, maps, offline/retry, Firestore) appropriate to the release. Use **[`testing-guide.md`](testing-guide.md)** to reproduce CI-equivalent runs and optional USB validation.
