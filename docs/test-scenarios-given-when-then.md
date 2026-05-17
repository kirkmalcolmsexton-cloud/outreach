# Instrumentation test scenarios (Given–When–Then)

This document catalogs **behavior** for each `androidTest` scenario in **Given / When / Then** form. For **how to run** tests, Gradle flags, **test tags**, runner arguments, and known flakiness constraints, see [`ui-testing.md`](ui-testing.md).

**Maintenance:** When you add or rename an `@Test`, update the matching block here so the catalog stays accurate.

## iOS XCUITest mapping

Run instructions, launch arguments, and identifier parity with Android are documented in [`ios-ui-testing.md`](ios-ui-testing.md). When you add an iOS UI test, extend this table.

| Android scenario (below) | iOS XCTest |
|----------------------------|------------|
| Launch smoke (`AppShellTest#launchesMainActivity`) | `AppShellUITests.testLaunch_reachesMainShell` |
| Bottom tab navigation (`AppShellAutomationTest#appShell_navigatesAcrossBottomTabs`) | `AppShellUITests.testTabNavigation_homeVisitsSettings` |
| Forced signed-out shell / login surface | `LoginGateUITests.testSignedOut_showsLoginGate` (mock `signed_out` + `LOGIN_GATE`) |

---

## App shell / MainActivity

### Launch smoke

**Maps to:** `org.outreach.app.AppShellTest#launchesMainActivity`

- **Given:** No special intent; default `MainActivity` launch.
- **When:** The test launches `MainActivity` via `ActivityScenario`.
- **Then:** The activity is not finishing immediately after launch.

### Bottom tab navigation

**Maps to:** `org.outreach.app.AppShellAutomationTest#appShell_navigatesAcrossBottomTabs`

- **Given:** `MainActivity` with instrumentation extras: skip startup delay, **home view mode list** (avoids loading Google Map in tests). Location permissions granted. Compose tree waited until ready.
- **When:** The user taps Visits, then Settings, then Home in the bottom bar (`NAV_*`).
- **Then:** Each screen’s content root is displayed (`CONTENT_VISITS`, `CONTENT_SETTINGS`, `CONTENT_HOME`) in order.

### Profile menu when signed out (mock auth)

**Maps to:** `org.outreach.app.AppShellAutomationTest#profileMenu_signedOut_showsLoginAction`

- **Given:** Same baseline as bottom-tab navigation (list mode, skip delay, permissions, compose ready). Default signed-out shell for this class’s intent (no forced sign-in extras).
- **When:** The user opens the profile menu (`PROFILE_BUTTON`).
- **Then:** The **Login** action is visible (`PROFILE_MENU_LOGIN`).

### Forced login gate

**Maps to:** `org.outreach.app.AppShellForcedLoginGateIntentTest#forceLoginGateExtra_showsLoginGateImmediately`

- **Given:** `MainActivity` intent with **force login gate** and skip startup delay; location permissions; compose ready.
- **When:** The shell renders after startup.
- **Then:** The login gate composable is shown immediately (`LOGIN_GATE`).

### Forced signed-in shell (mock), list home

**Maps to:** `org.outreach.app.AppShellForcedSignedInIntentTest#forcedSignedInAndListMode_showsSignedInMenuAndListContent`

- **Given:** Intent forces **signed_in** mock auth, list home mode, skip delay; permissions; compose ready.
- **When:** The user opens the profile menu.
- **Then:** **Logout** and **Switch account** appear (`PROFILE_MENU_LOGOUT`, `PROFILE_MENU_SWITCH`); list mode and list map UI tags are visible (`MODE_LIST`, `MAP_LIST`).

### Profile email line (mock vs real auth)

**Maps to:** `org.outreach.app.AppShellMockSignedInConfigurableEmailTest#mockSignedIn_showsConfiguredEmailInProfileMenu`

- **Given:** Intent forces **signed_in**, **mock** auth resolution, list mode, skip delay; optional runner/email via `outreach.ui_test.mock_user_email` / `-PoutreachMockUserEmail`. Permissions; compose ready.
- **When:** The user opens the profile menu.
- **Then:**
  - **Mock (`auth_resolution` not `real`):** The profile shows the configured mock email, or the default `ui-test@outreach.dev` if none set.
  - **Real (`auth_resolution=real`):** The profile shows the signed-in **Firebase** user’s email (or display name). If `-PoutreachMockUserEmail` is set, the Firebase email must match. Requires an actual signed-in user on the device; otherwise the assertion fails with a clear message.

---

## Map

### List mode: select household, start nav, simulated movement

**Maps to:** `org.outreach.feature.map.MapNavigationFlowTest#list_selectHousehold_startNavigation_simulatedMovementUpdates`

- **Given:** `ComposeHostActivity` with `MapScreen` in **list** view; one `HouseholdRecord` with id `nav_test_household`. `NavigationTestSupport` provides a **preview route** and **simulated GPS points** for that household; location permissions granted. Overrides cleared after the test.
- **When:** The user taps the household row (`MAP_LIST_ROW_PREFIX` + id), taps the navigation FAB (`MAP_NAV_FAB`), and the suite waits for the **End navigation** content description (list mode does not show the map-only “Navigation active” label).
- **Then:** The stop/end-navigation control is displayed; simulated navigation produces **at least two** location updates (`NavigationTestSupport.navigationLocationUpdateCount() >= 2`).

---

## Visits

### Save visit with preset brief comment

**Maps to:** `org.outreach.feature.visits.VisitLogScreenTest#saveVisit_withSelectedPreset_invokesCallback`

- **Given:** `VisitLogScreen` hosted in `ComposeHostActivity` with a household loaded from **`visit_household_fixture.json`**, brief presets **Receptive** / **Not home**, and a counting `onSaveVisit` callback.
- **When:** The user opens the brief selector (`VISITS_BRIEF`), chooses **Receptive**, and taps **Save** (`VISITS_SAVE`).
- **Then:** The callback runs exactly once with the fixture household id, brief **Receptive**, notes from the fixture, and a non-blank ISO visit date.

---

## Settings

### Mock spreadsheet pick and sync

**Maps to:** `org.outreach.feature.settings.SettingsScreenMockSpreadsheetSelectionTest#selectingMockedSpreadsheet_syncUsesSampleData`

- **Given:** `SettingsScreen` with `FakeSheetsApi.withSampleDriveSpreadsheet()`, in-memory config/state for picked spreadsheet and sync hooks.
- **When:** The user picks the spreadsheet from Drive (`SETTINGS_PICK_SPREADSHEET`), waits for the tab **60618** to appear, selects it, and taps sync (`SETTINGS_SYNC`).
- **Then:** Sync receives config with spreadsheet id **`sample-drive-sheet-001`**, selected tabs **`60618`**, and **two** rows from the fake API.

### Version line from BuildConfig

**Maps to:** `org.outreach.feature.settings.SettingsScreenVersionUiTest#settingsScreen_showsAppVersionFromBuildConfig`

- **Given:** `SettingsScreen()` with default content in `ComposeHostActivity`; compose idle and semantic tree ready.
- **When:** The test scrolls to the app version node.
- **Then:** The version text equals `Version {VERSION_NAME} ({VERSION_CODE})` plus **`(debug)`** when `BuildConfig.DEBUG` is true (`SETTINGS_APP_VERSION`).

---

## API / wiring (instrumentation)

### GoogleSheetsApi HTTP: list tabs

**Maps to:** `org.outreach.core.data.GoogleSheetsApiHttpMockTest#listTabs_usesInjectedSheetsBaseUrl_andParsesResponse`

- **Given:** A `MockWebServer` returning a Sheets API JSON body with sheet titles **60618** and **60657**; `GoogleSheetsApi` pointed at that base URL with a token provider that returns **`test-token`** and records requested OAuth scopes.
- **When:** `listTabs("sheet-123")` runs.
- **Then:** Returned tab names match **60618**, **60657**; the token provider was asked for a scope including **spreadsheets**; the captured HTTP path is **`/v4/spreadsheets/sheet-123?fields=sheets.properties.title`**.

### GoogleSheetsApi HTTP: resolve spreadsheet id from Drive

**Maps to:** `org.outreach.core.data.GoogleSheetsApiHttpMockTest#resolveSpreadsheetIdFromDriveMetadata_usesDriveScope_andDriveEndpoint`

- **Given:** `MockWebServer` returning Drive file metadata with id **sheet-abc** for **Volunteer List**; API using that base URL; token provider returns **`drive-token`** and records scopes.
- **When:** `resolveSpreadsheetIdFromDriveMetadata("Volunteer List", null)` runs.
- **Then:** Resolved id is **sheet-abc**; token scope includes **drive.metadata.readonly**; request path starts with **`/drive/v3/files`**.

### GoogleSheetsApi HTTP: missing token

**Maps to:** `org.outreach.core.data.GoogleSheetsApiHttpMockTest#listTabs_throwsWhenAccessTokenMissing`

- **Given:** `GoogleSheetsApi` with a token provider that returns **null**.
- **When:** `listTabs("sheet-123")` runs.
- **Then:** An **`IOException`** is thrown.

### Service locator uses repository with fake Sheets API

**Maps to:** `org.outreach.testing.GoogleApiMockWiringTest#installOverrides_usesFakeSheetsApiForRepositoryCalls`

- **Given:** An `OutreachRepository` built with a **`FakeSheetsApi`** returning tabs **60618**/**60657** for spreadsheet **`sheet-test`**; overrides installed via `OutreachUiTestEnvironment.installOverrides` (real app `Context` + DB/config). Overrides cleared after the test class methods.
- **When:** Code asks the service locator’s repository for `availableTabs("sheet-test")`.
- **Then:** Tabs are **60618**, **60657**.

### Sample Drive spreadsheet fixture

**Maps to:** `org.outreach.testing.GoogleApiMockWiringTest#sampleDriveSpreadsheet_canBeSelected_andProvidesRows`

- **Given:** `FakeSheetsApi.withSampleDriveSpreadsheet()`.
- **When:** Resolve id by display name **Sample Outreach Households**, then `listTabs`, then `fetchRows` for tab **60618**.
- **Then:** Resolved id is **`sample-drive-sheet-001`**; tabs list is **`60618`**; **`fetchRows`** returns **two** rows; first row name **Sample Person One** with a non-null street address.
