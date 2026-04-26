# Outreach iOS architecture

## Stack (parity plan — locked)

| Area | Choice |
|------|--------|
| **Minimum iOS** | **17.0** — SwiftData for local storage, `async`/`await` throughout |
| **UI** | SwiftUI, three-tab root (`Map` / `Visits` / `Settings`) matching Android’s `OutreachRoot` |
| **Maps** | **MapKit** (no extra Maps API key; Android uses Google Maps) |
| **Shared Kotlin / KMP** | **None** for v1 — native Swift; extract shared code later if both apps need to stay in lockstep |
| **Local persistence** | **SwiftData** for households, pending visit sync, and pending append queue |
| **Config** | `UserDefaults` (same keys/semantics as Android `AppConfigStore` in [`Storage.kt`](../android/app/src/main/java/org/outreach/core/data/Storage.kt)) |
| **Remote** | **Google Sheets API v4** and **Google Drive v3** via `URLSession` (same JSON shapes as `GoogleSheetsApi`) |
| **Auth** | [Google Sign-In for iOS](https://developers.google.com/identity/sign-in/ios) + [Firebase Auth](https://firebase.google.com/docs/auth/ios/google-signin) (identity); OAuth access token for REST calls to Sheets/Drive |
| **Collab** | [Firebase Firestore iOS](https://firebase.google.com/docs/firestore/quickstart#ios) — same collections as Android (`presence`, `activity`) per [`sync-and-collab.md`](sync-and-collab.md) |
| **Background sync** | [BackgroundTasks](https://developer.apple.com/documentation/backgroundtasks) `BGAppRefreshTask` (identifier `com.outreach.app.refresh`) to flush the pending queue and run `syncFromSheet`; also sync on `scenePhase` / foreground |

## Package layout (in-repo)

- **`ios/Outreach/`** — Xcode project and app sources under `Outreach/`.
- Folders (conceptual): `Core` (models, Sheets client, map filters), `Data` (SwiftData, `AppConfigStore`, repository, geocoding, collaboration, background), `App` (entry, `AppDelegate` URL handling, background registration), `Features` (SwiftUI: login, root tabs, map, visits, settings).

## Data flow

Matches [`android-architecture.md`](android-architecture.md):

1. User signs in with Google (Sheets + `drive.file` + metadata scopes).
2. User enters or selects a spreadsheet in Settings and picks ZIP tabs.
3. `OutreachRepository.syncFromSheet` pulls tab rows, parses headers like Android’s `GoogleSheetsApi`, geocodes addresses with `CLGeocoder`.
4. Map/list apply the same filter rules as [`MapFilterUtils.kt`](../android/app/src/main/java/org/outreach/feature/map/MapFilterUtils.kt).
5. Visit updates write to SwiftData first, then enqueue `PendingSync` for the Sheets `values:batchUpdate` path in `flushPendingSync`.

## Differences from Android

- **WorkManager**-style periodic work is not guaranteed on iOS. Product copy should state that full sync is attempted on a schedule when the system allows, and always when the app is active.
- **Android Auto** has no v1 iOS equivalent in this tree; [CarPlay](https://developer.apple.com/documentation/carplay) would be a separate effort.

## Related docs

- [ios-onboarding.md](ios-onboarding.md) — Xcode, dependencies, `GoogleService-Info.plist`, and signing.
- [google-oauth-checklist.md](google-oauth-checklist.md) — Android + **iOS OAuth client** and URL schemes.
