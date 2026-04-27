# Testing guide

Operational reference for running and debugging Outreach tests locally and in CI: Gradle tasks, **`adb`** / **`ANDROID_SERIAL`**, instrumentation flags, scripts, emulator defaults, physical devices, and troubleshooting.

**Workflow** (when to run which layer, release QA): **[`docs/testing-process.md`](testing-process.md)**.

**UI suite internals** (Compose tags, **`TestRuntime`**, intent extras, test scaffolding): **[`docs/ui-testing.md`](ui-testing.md)**.

**Scenario catalog** (Given–When–Then): **[`docs/test-scenarios-given-when-then.md`](test-scenarios-given-when-then.md)**.

Assume shell commands from **`outreach/android`** unless noted (`cd android`).

---

## Test layers and CI parity

| Layer | Where | Typical command |
|-------|--------|-----------------|
| JVM unit tests | Host | `./gradlew testDebugUnitTest` (included in `./gradlew check`) |
| Lint / static checks | Host | `./gradlew lint` (part of `check`) |
| Instrumentation (UI) | Emulator or device | `./gradlew connectedDebugAndroidTest` |

GitHub Actions jobs **`verify`** / **`instrumented`** are defined in **[`.github/workflows/android.yml`](../.github/workflows/android.yml)** — see **[`docs/ci-cd.md`](ci-cd.md)** for workflow names, triggers, **`gh workflow run`**, and branch rules.

| Job | What it runs |
|-----|----------------|
| **`verify`** | **`./scripts/build.sh` `ci` `verify`** (placeholder **`google-services`**, **`./gradlew check`**) |
| **`instrumented`** | **`build.sh` `ci` `build-instrumented-apks`**, then API **34** emulator, then **`build.sh` `ci` `connected-mock`** (same `build.sh` as release/debug) |

Locally, run the **same** commands as the workflow: **`./scripts/build.sh` `ci` `verify`**, then (with an emulator) **`./scripts/build.sh` `ci` `build-instrumented-apks`** and **`./scripts/build.sh` `ci` `connected-mock`** — see **[`ci-cd.md`](ci-cd.md)**.

---

## Gradle (connected tests)

| Task | Purpose |
|------|---------|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew assembleDebugAndroidTest` | Instrumentation test APK |
| `./gradlew connectedDebugAndroidTest` | Install both and run **`src/androidTest`** on connected device(s) |
| `./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest` | Build everything then run connected tests |

Single test class:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.TestClass
```

**No device:** If Gradle reports no connected devices, start an emulator or connect a phone; **`adb devices`** must list **`device`** (not **`unauthorized`**).

---

## Default Outreach emulator

| Item | Value |
|------|--------|
| **AVD name** | **`Galaxy_S938U_API36_x86_64`** (override with **`OUTREACH_AVD_NAME`** when starting the emulator) |
| **Resolve adb serial** | **`export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"`** |

Typical scripted flow (bash — from **`android/`**):

```bash
./scripts/start-outreach-emulator.sh
./scripts/wait-for-adb-online.sh
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

Emulator-only helper (starts an AVD if needed, then **`build connectedDebugAndroidTest`** with **`mock`**): **`./scripts/run-emulator-tests.sh`**.

### Start emulator without Studio

**`./scripts/start-outreach-emulator.sh`** uses **`scripts/outreach-emulator.snapshot.ini`** (Google Play / API **36** / **pixel_9**) and **`avdmanager create`** only if the AVD folder is missing. Requires SDK **cmdline-tools**. **`scripts/android-sdk.env`** sets **`ANDROID_HOME`** (defaults **`~/Library/Android/sdk`** on macOS).

---

## Physical device testing

Complete **[`docs/developer-onboarding.md` — First-time setup (emulator)](developer-onboarding.md#first-time-setup-emulator)** before relying on a phone unless you already have Android Studio, Firebase/Maps secrets wired, and a successful emulator **`connectedDebugAndroidTest`**.

**When to use USB hardware instead of an emulator**, **`mock` vs `real`** auth for sign-in scenarios, and how that fits release QA — **[`docs/testing-process.md`](testing-process.md)** (**Physical device** / **Auth** sections).

Everything below is **operational**: **`adb`**, Gradle, **`ANDROID_SERIAL`**, debug ingest (**`:7747`**), instrumentation flags, and **`./scripts/run-physical-ui-tests.sh`** (see [Interactive physical device runner](#interactive-physical-device-runner)).

### One-time phone setup

1. Enable **Developer options** and **USB debugging**.
2. Connect USB (or wireless debugging). Accept **Allow USB debugging?**
3. Prefer **Developer options → Stay awake** while charging.

### Run tests on the phone only

```bash
adb devices -l
export ANDROID_SERIAL=<your-phone-serial>
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

Pick one serial automatically (prefers USB hardware over emulators):

```bash
export ANDROID_SERIAL="$(./scripts/get-device-serial.sh --pick)"
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

### Multiple devices

**`connectedDebugAndroidTest`** targets **every** online device unless you set **`ANDROID_SERIAL`**. Always set it when more than one device appears in **`adb devices`**.

Align **`ANDROID_HOME`** with **`sdk.dir`** in **`local.properties`** so **`adb`** and Gradle share one SDK — see [SDK alignment](#sdk-alignment).

---

## Debug ingest (port 7747)

On a **physical device**, **`127.0.0.1:7747`** is the phone, not your PC.

| Situation | What to do |
|-----------|------------|
| **Android Emulator** | App uses **`10.0.2.2:7747`** to reach the host. **Do not** use **`adb reverse`** for this. |
| **Physical device (USB)** | Host listens on **`127.0.0.1:7747`**; from **repo root** run **`./android/scripts/adb-reverse-debug-ingest.sh`** (or **`./scripts/adb-reverse-debug-ingest.sh`**, forwarder) so device localhost **7747** forwards to the machine. |
| **Stale adb / reconnect** | Run the script again after **`adb kill-server`**, unplugging, or if forwarding stops. |

With multiple devices:

```bash
export ANDROID_SERIAL="$(./scripts/get-device-serial.sh --pick)"
./android/scripts/adb-reverse-debug-ingest.sh
```

(Run from **`outreach/`** repo root; canonical script is **`android/scripts/adb-reverse-debug-ingest.sh`**; **`scripts/adb-reverse-debug-ingest.sh`** forwards.)

---

## Auth modes and instrumentation CLI flags

Gradle maps **`-P`** properties in **`android/app/build.gradle.kts`** (prefer these over long **`android.testInstrumentationRunnerArguments.outreach.ui_test…`** keys):

| Gradle property | Maps to |
|-----------------|---------|
| **`-PoutreachMockUserEmail=`** | **`outreach.ui_test.mock_user_email`** |
| **`-PoutreachAuthResolution=mock`** or **`=real`** | **`outreach.ui_test.auth_resolution`** |

- **`mock`** — forced UI for automation; no Google login (typical for CI).
- **`real`** — **Firebase** on device; sign in manually before tests that expect a logged-in user.

Examples:

```bash
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellMockSignedInConfigurableEmailTest \
  -PoutreachAuthResolution=mock \
  -PoutreachMockUserEmail='your-account@gmail.com'
```

```bash
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellMockSignedInConfigurableEmailTest \
  -PoutreachAuthResolution=real \
  -PoutreachMockUserEmail='your-account@gmail.com'
```

With **`real`**, tests that enforce email may **fail** if Firebase user does not match — they are not skipped.

Full intent extras and **`mock` vs `real`** tables: **[`docs/ui-testing.md`](ui-testing.md)**.

### Automating sign-in

| Approach | Notes |
|----------|--------|
| **`mock`** | No OAuth — typical for CI. |
| **`real`** | **`FirebaseAuth`** on device — sign in manually or reuse session. |
| Custom token / advanced flows | See **`docs/ui-testing.md`**. |

---

## SDK alignment

Use the same SDK for **`adb`** and Gradle:

```bash
export ANDROID_HOME="$(grep '^sdk.dir=' local.properties | cut -d= -f2)"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

---

## Scripts used for testing

Most commands assume **`cd outreach/android`**. **`android/scripts/build-with-secrets-from-env.sh`** runs from **repo root** (**`scripts/build-with-secrets-from-env.sh`** forwards).

| Script | Purpose |
|--------|---------|
| **`./scripts/start-outreach-emulator.sh`** | Create default AVD if missing; start emulator |
| **`./scripts/wait-for-adb-online.sh`** | Wait until **`adb`** is **`device`** and boot complete |
| **`./scripts/resolve-outreach-emulator-serial.sh`** | Print emulator serial for **`ANDROID_SERIAL`** |
| **`./scripts/run-emulator-tests.sh`** | Boot emulator if needed; **`build`** + **`connectedDebugAndroidTest`** (**`mock`**) |
| **`./scripts/get-device-serial.sh`** | Table **`adb devices`**; **`--pick`**, **`--physical`** — **`--help`** |
| **`./scripts/run-physical-ui-tests.sh`** | Interactive physical device: serial, **`mock`/`real`**, optional **`adb reverse :7747`**, then connected tests |
| **`./scripts/adb-reverse-debug-ingest.sh`** | **`adb reverse tcp:7747 tcp:7747`** (**`android/scripts/`**; from repo root **`android/scripts/adb-reverse-debug-ingest.sh`** or **`scripts/`** forwarder) |
| **`./scripts/adb_restart.sh`** | **`adb kill-server`** / **`start-server`** |

Secrets for Firebase/Maps before tests: **`./scripts/setup-secrets.sh`** — see **[`docs/developer-onboarding.md`](developer-onboarding.md#5-firebase-config-required-for-google-sign-in--firebase)**.

### Interactive physical device runner

**[`android/scripts/run-physical-ui-tests.sh`](../android/scripts/run-physical-ui-tests.sh)** targets **physical** hardware only (rejects **`emulator-*`** when **`ANDROID_SERIAL`** is set explicitly). It resolves **`ANDROID_HOME`** from **`local.properties`**, prompts when needed, and runs **`assembleDebug`**, **`assembleDebugAndroidTest`**, **`connectedDebugAndroidTest`**.

```bash
cd android
./scripts/run-physical-ui-tests.sh
```

```bash
./scripts/run-physical-ui-tests.sh --non-interactive --pick --auth mock
```

```bash
./scripts/run-physical-ui-tests.sh --auth real --no-reverse
```

Extra Gradle args after **`--`**:

```bash
./scripts/run-physical-ui-tests.sh -- \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellAutomationTest
```

See **`./scripts/run-physical-ui-tests.sh --help`**.

### Cursor skill runner

**`.cursor/skills/android-build-tests/scripts/run-build-tests.sh`** — compile androidTest, run **`connectedDebugAndroidTest`** or fall back to JVM unit tests. Modes: **`emulator`** (default), **`physical`**.

---

## OAuth consent and manual test account

While the OAuth app is not in production, add accounts used for sign-in testing under **Test users** for your Web client ID (`google_web_client_id`): [Google Auth platform → Audience](https://console.cloud.google.com/auth/audience) or [OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent).

### Shared test account (manual / login flows)

Use for repeatable manual QA and **`real`**-auth instrumentation when your team aligns on a single account:

- Name: **`Test Sexton`**
- Email: **`skbobalima@gmail.com`**
- Recovery phone: **`(512) 818-2688`**

Treat like any shared credential; rotate passwords and revoke access if these details leak.

---

## Troubleshooting (tests and devices)

| Symptom | What to do |
|---------|------------|
| **No connected devices** | **`adb devices`** → **`device`**. Start AVD or plug in phone (USB debugging authorized). |
| **OFFLINE / Finished 0 tests** | Emulator booting or stale **`adb`** — **`wait-for-adb-online.sh`** or **`adb_restart.sh`**. |
| **Debug ingest not reaching host (phone)** | Host must listen on **localhost:7747**; run **`adb-reverse-debug-ingest.sh`**. Emulators: **`10.0.2.2`**, no reverse. |
| Map / Firebase errors | **`setup-secrets.sh`** and **`outreach-secrets.json.age`** — **[developer onboarding](developer-onboarding.md#5-firebase-config-required-for-google-sign-in--firebase)**. |
| **`Can't find service: package`** | **`PackageManager`** not ready — cold boot, **`wait-for-adb-online.sh`**, **`sys.boot_completed`**. Multiple devices: **`ANDROID_SERIAL`**. |
| **`device '<serial>' not found`** | Align **`ANDROID_HOME`** with **`sdk.dir`**, **`adb_restart.sh`**. |
| Flaky **`adb`** | Use **`$ANDROID_HOME/platform-tools/adb`** consistently. |

---

## Known UI-test constraints

From **`docs/ui-testing.md`**: Maps, Firebase, and network flows can be flaky end-to-end; prefer deterministic data and overrides. Physical devices are often slower to the first frame — use **`waitForSemanticTree`**; **`ComposeHostActivity`** is in the debug manifest; keep the device unlocked and **`ANDROID_SERIAL`** set when multiple devices are attached.
