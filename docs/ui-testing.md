# Outreach Android UI Testing

This app uses Android instrumentation tests with Compose test APIs.

## Run tests locally

From `outreach/android`:

- `./gradlew connectedDebugAndroidTest`

**No device:** If you see **`DeviceException: No connected devices!`**, start an emulator or connect a phone and confirm `adb devices` lists it as **`device`**. Authorized USB debugging is required on physical hardware.

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
- Profile and auth menu: `PROFILE_BUTTON`, `PROFILE_MENU_LOGIN`, `PROFILE_MENU_SWITCH`, `PROFILE_MENU_LOGOUT`
- Sync error/status surface: `SYNC_STATUS_BANNER`
- Feature roots/actions: map (`MAP_ROOT`, `MAP_SEARCH`, `MAP_LIST`, `MAP_NAV_FAB`, `MAP_ADD_PERSON`), visits, and settings tags

## Test runtime behavior

Instrumentation runs use a guarded startup path:

- `TestRuntime.skipStartupSideEffects` disables startup-only side effects such as periodic sync scheduling and initial eager sync.
- Production behavior is unchanged when not running instrumentation.

Runtime helper:

- `android/app/src/main/java/org/outreach/app/testing/TestRuntime.kt`

UI automation intent configuration:

- `android/app/src/main/java/org/outreach/app/testing/UiAutomationConfig.kt`

Supported extras for instrumentation-only scenarios:

- `outreach.ui_test.skip_startup_delay` (Boolean)
- `outreach.ui_test.force_login_gate` (Boolean)
- `outreach.ui_test.force_auth_state` (`signed_in` / `signed_out`) — only applies when auth resolution is **mock** (default)
- `outreach.ui_test.home_view_mode` (`map` / `list`)
- `outreach.ui_test.auth_resolution` (`mock` / `real`) — **`mock`** (default): UI follows `force_auth_state` without OAuth; **`real`**: ignores `force_auth_state` and uses Firebase like production (sign in on the device/emulator before tests that expect a logged-in UI)
- `outreach.ui_test.mock_user_email` (String) — profile line when **`mock`** + **`signed_in`**; defaults to `ui-test@outreach.dev` if omitted or blank

Pass these on the **`MainActivity` intent** from `ActivityScenarioRule` / `ActivityScenario` (see `AppShellAutomationTest`).

**Instrumentation runner arguments** (merged over intent extras when present): keys match the extras above (e.g. **`outreach.ui_test.mock_user_email`**).

**Preferred (CLI):** `android/app/build.gradle.kts` wires project properties into the runner Bundle—use **`-PoutreachMockUserEmail=`** and optionally **`-PoutreachAuthResolution=`** (`mock` / `real`) instead of long **`-Pandroid.testInstrumentationRunnerArguments.outreach.ui_test…`** keys (those nested Gradle properties are unreliable and conflict with configuration caching):

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellMockSignedInConfigurableEmailTest \
  -PoutreachMockUserEmail='your-account@gmail.com'
```

Quote values that contain `@` if your shell requires it.

**Auth mode from Gradle:** Use **`-PoutreachAuthResolution=mock`** or **`=real`** only. No email address triggers **`real`** by itself—opt in explicitly. For **`real`**, sign in on the device/emulator before tests that expect a logged-in shell.

**Mock vs real summary**

| `auth_resolution` | `force_auth_state` | Result |
|-------------------|-------------------|--------|
| omitted / `mock` | `signed_in` | Signed-in UI with **`mock_user_email`** (or default `ui-test@outreach.dev`) |
| omitted / `mock` | `signed_out` | Signed-out UI |
| `real` | any | **`force_auth_state` ignored** — UI reflects **`FirebaseAuth.getInstance().currentUser`** |

## Dependency overrides for tests

The service locator supports test overrides:

- `OutreachServiceLocator.installTestOverrides(...)`
- `OutreachServiceLocator.clearTestOverrides()`

Test-side helper:

- `android/app/src/androidTest/java/org/outreach/testing/OutreachUiTestEnvironment.kt`

Map navigation hooks (instrumentation-only; gated by `TestRuntime.isInstrumentation` in UI code — release is always false; debug uses `ActivityThread`’s `mInstrumentation` class: anything other than `android.app.Instrumentation` counts as a connected test):

- `android/app/src/main/java/org/outreach/app/testing/NavigationTestSupport.kt` — optional fake driving route per household id and simulated GPS points while in-app navigation is active. Cleared by `NavigationTestSupport.reset()` and whenever `OutreachUiTestEnvironment.clearOverrides()` runs.

Base test scaffold:

- `android/app/src/androidTest/java/org/outreach/testing/BaseOutreachComposeTest.kt`

Typical usage in a future test:

1. Install test overrides in setup (if needed).
2. Use Compose rule to interact with tagged nodes.
3. Let base cleanup clear overrides after each test.

When you need startup/auth/view-mode control per test scenario, launch `MainActivity`
with an `ActivityScenarioRule` intent carrying the extras above.

## Current comprehensive suite entry points

- `android/app/src/androidTest/java/org/outreach/app/AppShellAutomationTest.kt`
- `android/app/src/androidTest/java/org/outreach/feature/settings/SettingsScreenMockSpreadsheetSelectionTest.kt`
- `android/app/src/androidTest/java/org/outreach/feature/visits/VisitLogScreenTest.kt`
- `android/app/src/androidTest/java/org/outreach/feature/map/MapNavigationFlowTest.kt` (deterministic map navigation: `MapScreen` in isolation with injected household + [NavigationTestSupport](android/app/src/main/java/org/outreach/app/testing/NavigationTestSupport.kt); no Directions API or live GPS; list mode waits on the Stop FAB `contentDescription` because the “Navigation active” label is map-only)

## Known constraints

- Google Maps rendering, Firebase auth, and network-backed flows can be flaky in instrumentation if exercised end-to-end.
- Prefer deterministic test data + overrides for UI checks.
- Keep one smoke path network-free before adding deeper integration scenarios.
- **Physical devices** are often slower to the first Composable frame than emulators. Tests use `org.outreach.testing.waitForSemanticTree`; screen composables run in **`ComposeHostActivity`** declared in the **debug** manifest (`src/debug/AndroidManifest.xml`) so the activity ships in the **app** APK (`org.outreach.app`), not the androidTest APK — required for `createAndroidComposeRule`. The activity uses **`FLAG_KEEP_SCREEN_ON`**. Still enable **Developer options → Stay awake** when testing over USB, keep the device **unlocked**, and set **`ANDROID_SERIAL`** if multiple devices are connected so Gradle does not run the suite on every device at once.
