# Outreach iOS UI testing (XCUITest)

The app target exposes stable **accessibility identifiers** aligned with Android [`TestTags`](../android/app/src/main/java/org/outreach/ui/testtags/TestTags.kt) via [`UiTestTags.swift`](../ios/Outreach/Outreach/UiTestTags.swift). Debug-only launch flags mirror Android’s [`UiAutomationConfig`](../android/app/src/main/java/org/outreach/app/testing/UiAutomationConfig.kt) (see [`UiAutomationConfig.swift`](../ios/Outreach/Outreach/UiAutomationConfig.swift)).

**Android counterpart:** [`ui-testing.md`](ui-testing.md) and [`testing-guide.md`](testing-guide.md).

## Run tests locally

From the repo root (or `outreach/ios`), with **Xcode** installed and an **iOS Simulator** runtime (or a connected iPhone):

```bash
bash ios/scripts/run-ui-tests.sh
```

Simulator mode boots the default Outreach simulator when needed, resolves `OUTREACH_DESTINATION`, and runs `ios/scripts/build.sh test`. Device mode:

```bash
bash ios/scripts/run-ui-tests.sh --device
```

Requires a trusted device with **Developer Mode**, and `OUTREACH_DEVELOPMENT_TEAM` (or `DEVELOPMENT_TEAM`) set for CLI signing, same as [`ios/scripts/deploy-device.sh`](../ios/scripts/deploy-device.sh).

### Manual `xcodebuild`

```bash
export OUTREACH_DESTINATION="$(bash ios/scripts/resolve-outreach-ios-simulator-destination.sh)"
OUTREACH_SKIP_IOS_SETUP_SECRETS=1 bash ios/scripts/build.sh test
```

Physical iPhone:

```bash
export OUTREACH_DESTINATION="platform=iOS,id=<UDID>"
export OUTREACH_DEVELOPMENT_TEAM=XXXXXXXXXX
bash ios/scripts/build.sh test
```

`build.sh` passes **`-parallel-testing-enabled NO`** so UI tests run serially (avoids flaky parallel simulator workers).

## Launch arguments (host app)

All automation flags are ignored unless **`-outreach.ui_test`** is present (Debug builds only; Release always behaves as production).

Pass strings as separate argv entries, same pattern as Android extras:

| Argument | Values | Effect |
|----------|--------|--------|
| `-outreach.ui_test` | (flag) | Enables parsing of the keys below. |
| `outreach.ui_test.force_auth_state=signed_in` | `signed_in` / `signed_out` | Mock shell: skip real Google/Firebase gate when `signed_in`; force login gate when `signed_out`. |
| `outreach.ui_test.skip_startup_delay=true` | `true` / `1` / `yes` | Skips initial sheet sync and background scheduling from `RootView` on appear / resume (parity with Android `skip_startup_delay`). |
| `outreach.ui_test.home_view_mode=list` | `list` / `map` | Home tab starts in list mode to avoid heavy MapKit work in CI (parity with Android list home in tests). |

Environment fallback: each key can also be read with dots replaced by underscores and uppercased (e.g. `OUTREACH_UI_TEST_HOME_VIEW_MODE=list`).

## Test bundle

- Target: **`OutreachUITests`** (UI Testing Bundle), scheme **`Outreach`**. The target also compiles [`UiTestTags.swift`](../ios/Outreach/Outreach/UiTestTags.swift) (shared with the app) so test code uses the same string constants.
- Sources: [`ios/Outreach/OutreachUITests/AppShellUITests.swift`](../ios/Outreach/OutreachUITests/AppShellUITests.swift) (`testTabNavigation_shellAndHomeMapTags` mirrors Android `AppShellAutomationTest.appShell_navigatesAcrossBottomTabs`).

## Tag parity matrix (Android / iOS)

Source of string constants: Android [`TestTags.kt`](../android/app/src/main/java/org/outreach/ui/testtags/TestTags.kt), iOS [`UiTestTags.swift`](../ios/Outreach/Outreach/UiTestTags.swift). **Wired** = tag applied in UI; **Test** = referenced from an instrumented or UI test. Empty means not applicable or not yet wired on that platform.

| Tag | Android wired | Android test | iOS wired | iOS test |
|-----|---------------|--------------|-----------|----------|
| `app_root` | yes | `AppShellAutomationTest` | yes | `AppShellUITests` |
| `startup_screen` | yes | — | — | — |
| `login_gate` | yes | `AppShellForcedLoginGateIntentTest` | yes | `LoginGateUITests` |
| `profile_button`, `profile_menu_*` | yes | `AppShellAutomationTest`, `AppShellForcedSignedInIntentTest` | yes | `AppShellUITests` (button) |
| `sync_status_banner` | yes | — (only when sync error) | yes | — |
| `nav_home` / `nav_visits` / `nav_settings` | yes | `AppShellAutomationTest` | yes | `AppShellUITests` |
| `mode_map` / `mode_list` | yes | `AppShellForcedSignedInIntentTest` (list) | yes | `AppShellUITests` (list) |
| `content_home` / `content_visits` / `content_settings` | yes | `AppShellAutomationTest` | yes — `visits` / `settings` shell; `content_home` wired on `MapTabView` (tab shell uses `map_root` in XCUITest) | `AppShellUITests` |
| `map_root` | yes | `AppShellAutomationTest` | yes | `AppShellUITests` |
| `map_search` | yes | `AppShellAutomationTest` | yes | `AppShellUITests` |
| `map_list` / `map_list_row_*` | yes | `MapNavigationFlowTest` | list rows untagged | — |
| `map_nav_fab` | yes | `MapNavigationFlowTest` | yes | — |
| `map_add_person` | yes | `AppShellAutomationTest` | yes | — |
| `visits_root`, `visits_brief`, `visits_notes`, `visits_save` | yes | `VisitLogScreenTest` (Compose) | yes (`VisitLogView`) | `AppShellUITests` (`visits_root`) |
| `settings_root` and `settings_*` | yes | `AppShellAutomationTest` (root); mock/version tests | yes (pick/validate/sync + ZIP + version) | `AppShellUITests` (root) |
| `settings_zip_section`, `settings_app_version` | yes | `SettingsScreenMock*`, `SettingsScreenVersionUiTest` | yes | — |

XCUITest compiles shared [`UiTestTags.swift`](../ios/Outreach/Outreach/UiTestTags.swift) in the **`OutreachUITests`** target so identifiers cannot drift from the app copy.

## Platform / product notes (residual differences)

- **Top bar title:** The shell uses **“Outreach”** on the Home tab (Android-style app title). The bottom tab label is **“Home”** (Android bottom-nav parity). Tests use `nav_home` and `navigationBars["Outreach"]` on Home.
- **Drive spreadsheet picker:** iOS uses the system **Files** / document browser (`UTType` **item** / **data**) to resolve IDs when possible; Android uses the Google **Drive** intent. Deep Drive parity may still differ by account and provider.
- **Map routing:** iOS **MapKit** / Apple Maps opens for directions and map links; Android uses **Google Maps** intents. Same user intent, different platform maps.

## Settings / Visits strategy (testing)

- **Settings:** Android Compose tests remain the reference for edge cases; iOS wires the same **tag strings** on the expanded `Form` (pick/validate/sync, ZIP section, version footer).
- **Visits:** `VisitLogView` exposes `visits_*` tags; shell tests assert **`visits_root`** after opening the Visits tab.

## Known differences vs Android (historical)

- **OAuth:** Real sign-in flows are not automated here; use `force_auth_state` for shell coverage.
- **MapKit:** List home mode is recommended for stable tests; full map flows are not in the default suite.
- **Identifiers:** XCTest uses **accessibility identifier** for `descendants(matching: .any).matching(identifier:)`; tab and content coverage is in `testTabNavigation_shellAndHomeMapTags` using `UiTestTags` (not tab bar text).
