# Build process

How to produce Outreach Android artifacts locally: debug builds for development, configuring Firebase and Maps secrets, and where release signing fits relative to CI.

## Debug builds (daily development)

Working directory: **`android/`**.

```bash
./gradlew assembleDebug
```

Windows PowerShell uses **`.\gradlew.bat`** instead of **`./gradlew`**.

**`local.properties`** should contain **`sdk.dir=…`** once Android Studio has synced the project (points Gradle at your SDK).

## Secrets (Firebase / Maps)

The app needs **`google-services.json`** under **`android/app/`** and Map keys in **`local.properties`** (**`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`**, or legacy **`MAPS_API_KEY`**). Recommended path:

1. Obtain team **`secrets/outreach-secrets.json.age`** and passphrase (out of band).
2. Install **`age`** and **`jq`**.
3. From **`android/`**, run **`./scripts/setup-secrets.sh`** with **`OUTREACH_SECRETS_PASSPHRASE`** set.

Full narrative, **`encrypt-secrets.sh`**, manual Firebase download, and **`~/etc/outreach.env`** one-shot builds live in **[`docs/developer-onboarding.md`](developer-onboarding.md)**. Repository secret names for Actions are in **[`docs/github-actions-secrets.md`](github-actions-secrets.md)**.

### iOS (GoogleService-Info.plist)

From the repo root, after the same **`secrets/outreach-secrets.json`** (or **`.age`**) contains **`google_service_info_plist_base64`** or **`google_service_info_plist`**, run **`bash ios/scripts/setup-secrets.sh`**. It writes **gitignored** **`ios/Outreach/Outreach/GoogleService-Info.plist`**. Command-line builds: **`bash ios/scripts/build.sh`** (see **`ios/scripts/build.sh -h`**). Details: **[`docs/ios-onboarding.md`](ios-onboarding.md)**.

## CI parity

Android CI on GitHub runs **`android/scripts/build.sh` `ci` …** — same as locally from **`outreach/android`**: **`./scripts/build.sh` `ci` `verify`**, and for instrumentation **`ci` `build-instrumented-apks`** + **`ci` `connected-mock`** (see **[`ci-cd.md`](ci-cd.md)**). The **`build.sh`** `ci` subcommands replace **`app/google-services.json`** with the example and match CI **`MAPS_API_KEY`**. **Release** builds: **`./scripts/build.sh` `release-bundle`** (or **`build-release-bundle.sh`**).

See **[`docs/testing-process.md`](testing-process.md)** for when to add physical-device runs. Commands and **`adb`** details: **[`docs/testing-guide.md`](testing-guide.md)**.

## Release builds (AAB) and signing

Release artifacts use **`bundleRelease`**. Locally, signing can use **`android/keystore.properties`** (gitignored); CI resolves **`ANDROID_UPLOAD_*`** Gradle env vars from **`secrets/outreach-secrets.json`** (**`android_upload_signing`**) plus **`secrets/upload-keystore.jks.age`**, or from legacy GitHub **`ANDROID_UPLOAD_*`** secrets (**[`github-actions-secrets.md`](github-actions-secrets.md)**).

Authoritative steps for keystore custody, **`bundleRelease`**, and Play uploads: **[`docs/release-process.md`](release-process.md)**. Signing wiring is implemented in **`android/app/build.gradle.kts`**.

## Related links

- **[`ci-cd.md` → CLI quick reference](ci-cd.md#cli-quick-reference)** — Gradle shortcuts and CI parity.
- **[`docs/testing-process.md`](testing-process.md)** — validation before merge or release.
- **[`docs/testing-guide.md`](testing-guide.md)** — Gradle, devices, instrumentation flags.
