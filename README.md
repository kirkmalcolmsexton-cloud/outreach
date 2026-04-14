# outreach

Android app lives in `android/` as a **single-module** project (same style as `android-dice`: root Gradle + `app/` only).

- Kotlin + Jetpack Compose
- Google sign-in + Sheets/Drive scopes
- Settings for spreadsheet ID and zip-code tabs
- Map/list home with routing intents
- Room + DataStore + WorkManager for offline sync
- Firestore collaboration scaffolding

## Quick start

1. Open the **`android/`** folder in Android Studio (the directory that contains `settings.gradle.kts`).
2. Wait for **Gradle sync** to finish (elephant icon in the toolbar). If prompted, accept the Android Gradle plugin / JDK (use **JDK 17** to match this project).
3. In the **Project** tool window, set the view dropdown to **Android** (not **Project**) so you see `app`, `manifests`, `java`, `res` like a typical app—same idea as `android-dice`.
4. Copy **`android/app/google-services.json.example`** to `android/app/google-services.json`, then replace with your real Firebase download.
   - Keep `android/app/google-services.json` local-only; do not commit it.
5. From `android/`, run `./gradlew build` or `./build.sh` for a debug APK.

**Cursor / VS Code:** Opening `android/` here only shows files on disk; it will **not** show the Android “app / manifests / res” layout. Use **Android Studio** for that UI and for running on emulators/devices.

Optional: `local.properties` with `sdk.dir=...` if the IDE does not create it automatically.

Additional documentation:

- `docs/android-architecture.md`
- `docs/sync-and-collab.md`
- `docs/release-checklist.md`
- `docs/google-oauth-checklist.md` (Google sign-in / Firebase OAuth troubleshooting)
