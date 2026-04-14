# Outreach Android Architecture

## Project layout (matches single-app Gradle projects like `android-dice`)

- **`android/`** — Gradle root (`settings.gradle.kts`, `build.gradle.kts`, wrapper, `build.sh`).
- **`android/app/`** — single Android application module; all Kotlin sources live under `app/src/main/java/` in packages:
  - `org.outreach.app` — `Application`, `MainActivity`, entry wiring.
  - `org.outreach.core.model` — data model + parsers.
  - `org.outreach.core.data` — Room, DataStore, Sheets API, repositories, `WorkManager` worker.
  - `org.outreach.feature.*` — Compose screens (auth, map, settings, visits, collab).

Unit tests: `app/src/test/java/`. Instrumentation tests: `app/src/androidTest/java/`.

## Primary data flow

1. User signs in (Google SSO provider).
2. User selects sheet + tab filters in settings.
3. Repository syncs selected tabs via Sheets API into Room.
4. Home map renders cached records and can launch navigation intents.
5. Visit updates save locally first, then queue for sync.
6. WorkManager flushes pending updates to Google Sheets when network is available.

## MVP decisions

- Offline-first write path with queueing and eventual consistency.
- Google Sheets remains source of truth for household data.
- Firestore is a collaboration overlay for presence/assignment/activity only.
