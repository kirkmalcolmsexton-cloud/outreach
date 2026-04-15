# Outreach Android UI Testing

This app uses Android instrumentation tests with Compose test APIs.

## Run tests locally

From `outreach/android`:

- `./gradlew connectedDebugAndroidTest`

Optional single-class run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.testing.SomeTestClass`

Make sure an emulator/device is running before executing the command.

## Test tag conventions

Stable selectors are defined in:

- `android/app/src/main/java/org/outreach/ui/testtags/TestTags.kt`

Use these constants instead of matching visible text wherever possible.

Current high-value tag groups:

- App shell and navigation: `APP_ROOT`, `NAV_HOME`, `NAV_VISITS`, `NAV_SETTINGS`
- Home view mode: `MODE_MAP`, `MODE_LIST`
- Screen roots: `CONTENT_HOME`, `CONTENT_VISITS`, `CONTENT_SETTINGS`
- Feature roots/actions: map, visits, and settings tags

## Test runtime behavior

Instrumentation runs use a guarded startup path:

- `TestRuntime.skipStartupSideEffects` disables startup-only side effects such as periodic sync scheduling and initial eager sync.
- Production behavior is unchanged when not running instrumentation.

Runtime helper:

- `android/app/src/main/java/org/outreach/app/testing/TestRuntime.kt`

## Dependency overrides for tests

The service locator supports test overrides:

- `OutreachServiceLocator.installTestOverrides(...)`
- `OutreachServiceLocator.clearTestOverrides()`

Test-side helper:

- `android/app/src/androidTest/java/org/outreach/testing/OutreachUiTestEnvironment.kt`

Base test scaffold:

- `android/app/src/androidTest/java/org/outreach/testing/BaseOutreachComposeTest.kt`

Typical usage in a future test:

1. Install test overrides in setup (if needed).
2. Use Compose rule to interact with tagged nodes.
3. Let base cleanup clear overrides after each test.

## Known constraints

- Google Maps rendering, Firebase auth, and network-backed flows can be flaky in instrumentation if exercised end-to-end.
- Prefer deterministic test data + overrides for UI checks.
- Keep one smoke path network-free before adding deeper integration scenarios.
