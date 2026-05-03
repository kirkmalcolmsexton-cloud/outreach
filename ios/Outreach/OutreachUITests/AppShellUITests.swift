import XCTest

/// Uses `UiTestTags` (same string constants as Android `TestTags`) for stable selectors; strings are compiled into
/// this target via shared [`UiTestTags.swift`](../Outreach/UiTestTags.swift) in the UI test target.
private func ui(_ app: XCUIApplication, _ id: String) -> XCUIElement {
    app.descendants(matching: .any).matching(identifier: id).firstMatch
}

private enum MainShellTab: Int {
    case home = 0
    case visits = 1
    case settings = 2
}

/// SwiftUI `TabView` often does not forward `.accessibilityIdentifier` on `Label` to the tab bar button; prefer titles, fall back to tab order (Home, Visits, Settings).
private func tapMainTab(_ app: XCUIApplication, _ tab: MainShellTab, file: StaticString = #filePath, line: UInt = #line) {
    let bar = app.tabBars.firstMatch
    XCTAssertTrue(bar.waitForExistence(timeout: 15), "Expected tab bar.", file: file, line: line)
    let titles = ["Home", "Visits", "Settings"]
    let title = titles[tab.rawValue]
    let byTitle = bar.buttons[title]
    if byTitle.waitForExistence(timeout: 4) {
        byTitle.tap()
        return
    }
    let buttons = bar.buttons
    XCTAssertGreaterThanOrEqual(buttons.count, tab.rawValue + 1, "Tab bar missing index \(tab.rawValue)", file: file, line: line)
    buttons.element(boundBy: tab.rawValue).tap()
}

/// Multiline SwiftUI `TextField` may surface as a `TextView` or `TextField` for XCTest; accept either for a tagged control.
private func assertTaggedControlExists(
    _ app: XCUIApplication,
    _ id: String,
    timeout: TimeInterval = 10,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    if ui(app, id).waitForExistence(timeout: timeout) { return }
    if app.textViews.matching(identifier: id).firstMatch.waitForExistence(timeout: 3) { return }
    if app.textFields.matching(identifier: id).firstMatch.waitForExistence(timeout: 3) { return }
    if id == UiTestTags.visitsNotes,
       app.textViews.containing(NSPredicate(format: "label CONTAINS[c] %@", "Tap to type")).firstMatch
       .waitForExistence(timeout: 4) { return }
    if id == UiTestTags.visitsNotes,
       app.textFields.containing(NSPredicate(format: "placeholderValue CONTAINS[c] %@", "Tap to type")).firstMatch
       .waitForExistence(timeout: 4) { return }
    XCTFail("Missing tagged control \(id)", file: file, line: line)
}

/// SwiftUI `ScrollView` / `Form` often omit off-screen nodes from the accessibility snapshot until scrolled.
/// Scroll until notes + save are both present in the snapshot (fixed fling counts can scroll the save button off-screen).
private func scrollVisitLogUntilNotesAndSaveVisible(_ app: XCUIApplication) {
    let scroll = app.scrollViews.firstMatch
    guard scroll.waitForExistence(timeout: 6) else { return }
    for _ in 0..<12 {
        let notesReady =
            ui(app, UiTestTags.visitsNotes).exists
            || app.textViews.matching(identifier: UiTestTags.visitsNotes).firstMatch.exists
            || app.textFields.matching(identifier: UiTestTags.visitsNotes).firstMatch.exists
        let saveReady =
            ui(app, UiTestTags.visitsSave).exists
            || app.buttons["Save offline + queue sync"].exists
        if notesReady && saveReady { return }
        scroll.swipeUp(velocity: .fast)
    }
}

final class AppShellUITests: XCTestCase {

    private func configureSignedInListShell(_ app: XCUIApplication) {
        app.launchArguments = [
            "-outreach.ui_test",
            "outreach.ui_test.force_auth_state=signed_in",
            "outreach.ui_test.skip_startup_delay=true",
            "outreach.ui_test.home_view_mode=list",
        ]
    }

    private func configureSignedInMapShell(_ app: XCUIApplication) {
        app.launchArguments = [
            "-outreach.ui_test",
            "outreach.ui_test.force_auth_state=signed_in",
            "outreach.ui_test.skip_startup_delay=true",
            "outreach.ui_test.home_view_mode=map",
        ]
    }

    func testLaunch_reachesMainShell() {
        let app = XCUIApplication()
        configureSignedInListShell(app)
        app.launch()
        XCTAssertEqual(app.state, .runningForeground)
        XCTAssertTrue(
            ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20),
            "Expected main shell (app_root) after launch."
        )
    }

    /// Mirrors `AppShellAutomationTest.appShell_navigatesAcrossBottomTabs` on Android (tag-based tab + content).
    func testTabNavigation_shellAndHomeMapTags() {
        let app = XCUIApplication()
        configureSignedInListShell(app)
        app.launch()

        XCTAssertTrue(ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20))

        tapMainTab(app, .visits)
        XCTAssertTrue(ui(app, UiTestTags.contentVisits).waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Visits"].waitForExistence(timeout: 5))
        XCTAssertTrue(ui(app, UiTestTags.visitsRoot).waitForExistence(timeout: 10))

        tapMainTab(app, .settings)
        XCTAssertTrue(ui(app, UiTestTags.contentSettings).waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Load"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Save & sync from sheet now"].waitForExistence(timeout: 10))

        tapMainTab(app, .home)
        XCTAssertTrue(app.navigationBars["Outreach"].waitForExistence(timeout: 10))
        XCTAssertTrue(ui(app, UiTestTags.profileButton).waitForExistence(timeout: 5))
        XCTAssertTrue(ui(app, UiTestTags.mapRoot).waitForExistence(timeout: 10))
        let searchField = app.textFields["Search name or address"]
        XCTAssertTrue(
            searchField.waitForExistence(timeout: 10),
            "List home should expose the search field (screenshot parity)."
        )
        XCTAssertTrue(
            app.buttons["List"].waitForExistence(timeout: 5) && app.buttons["Map"].waitForExistence(timeout: 2),
            "Map/List mode picker should be visible."
        )
        // List may be empty (no UIKit table in hierarchy); map_root still anchors the home column in list mode.
        _ = ui(app, UiTestTags.modeList)
        let homeColumn = ui(app, UiTestTags.mapRoot)
        XCTAssertTrue(homeColumn.waitForExistence(timeout: 3))
        XCTAssertLessThanOrEqual(
            searchField.frame.maxY,
            homeColumn.frame.maxY
        )
        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.waitForExistence(timeout: 5))
        XCTAssertGreaterThan(
            tabBar.frame.minY,
            ui(app, UiTestTags.mapRoot).frame.maxY - 1,
            "Tab bar should be below the home tab’s map root."
        )
    }

    /// Tag-based selectors for the visit log form (parity with Android `visitsTab_showsFormCopyAndVerticalOrder`).
    func testVisitsTab_taggedVisitLoggingControls() {
        let app = XCUIApplication()
        configureSignedInListShell(app)
        app.launch()
        XCTAssertTrue(ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20))
        tapMainTab(app, .visits)
        XCTAssertTrue(ui(app, UiTestTags.contentVisits).waitForExistence(timeout: 10))
        XCTAssertTrue(ui(app, UiTestTags.visitsRoot).waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Visit update"].waitForExistence(timeout: 8))
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "Select someone from the Home"))
                .firstMatch.waitForExistence(timeout: 8),
            "Expected visit-log onboarding copy when no household is selected."
        )
        XCTAssertTrue(
            ui(app, UiTestTags.visitsBrief).waitForExistence(timeout: 8)
                || app.staticTexts["Choose brief comment"].waitForExistence(timeout: 4),
            "Brief menu should expose visits_brief or default label text."
        )
        scrollVisitLogUntilNotesAndSaveVisible(app)
        assertTaggedControlExists(app, UiTestTags.visitsNotes)
        XCTAssertTrue(
            ui(app, UiTestTags.visitsSave).waitForExistence(timeout: 10)
                || app.buttons["Save offline + queue sync"].waitForExistence(timeout: 6)
        )
    }

    func testSettingsTab_taggedSpreadsheetControls() {
        let app = XCUIApplication()
        configureSignedInListShell(app)
        app.launch()
        XCTAssertTrue(ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20))
        tapMainTab(app, .settings)
        XCTAssertTrue(ui(app, UiTestTags.contentSettings).waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 8))
        XCTAssertTrue(ui(app, UiTestTags.settingsSpreadsheetLinkField).waitForExistence(timeout: 12))
        XCTAssertTrue(ui(app, UiTestTags.settingsLoadSpreadsheet).waitForExistence(timeout: 10))
        XCTAssertTrue(ui(app, UiTestTags.settingsPickSpreadsheet).waitForExistence(timeout: 10))
        XCTAssertTrue(ui(app, UiTestTags.settingsValidate).waitForExistence(timeout: 10))
        XCTAssertTrue(ui(app, UiTestTags.settingsSync).waitForExistence(timeout: 10))
        XCTAssertTrue(
            ui(app, UiTestTags.settingsAppVersion).waitForExistence(timeout: 10),
            "Expected settings_app_version (injected at top of Settings when running UI tests)."
        )
    }

    /// Map mode: exercise `mode_map`, FABs, and search. Skips if the map shell is slow to appear (MapKit on simulator).
    func testHomeMapMode_taggedMapChrome() throws {
        let app = XCUIApplication()
        configureSignedInMapShell(app)
        app.launch()
        XCTAssertTrue(ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20))
        if ui(app, UiTestTags.modeList).waitForExistence(timeout: 8) {
            let seg = app.segmentedControls.firstMatch
            if seg.waitForExistence(timeout: 3), seg.buttons["Map"].waitForExistence(timeout: 2) {
                seg.buttons["Map"].tap()
            } else if app.buttons["Map"].waitForExistence(timeout: 4) {
                app.buttons["Map"].tap()
            }
        }
        guard ui(app, UiTestTags.mapRoot).waitForExistence(timeout: 30) else {
            throw XCTSkip("Map home (map_root) not ready; skip on slow MapKit / simulator environments.")
        }
        let mapLayerTagged = ui(app, UiTestTags.modeMap).waitForExistence(timeout: 20)
        let listLayerTagged = ui(app, UiTestTags.modeList).waitForExistence(timeout: 5)
        XCTAssertTrue(
            mapLayerTagged || !listLayerTagged,
            "Home should be in map mode (mode_map or no mode_list)."
        )
        XCTAssertTrue(
            ui(app, UiTestTags.mapSearch).waitForExistence(timeout: 20)
                || app.textFields["Search name or address"].waitForExistence(timeout: 6)
        )
        XCTAssertTrue(
            ui(app, UiTestTags.mapNavFab).waitForExistence(timeout: 20)
                || app.buttons["Start in-app driving route"].waitForExistence(timeout: 10)
        )
        XCTAssertTrue(
            ui(app, UiTestTags.mapAddPerson).waitForExistence(timeout: 20)
                || app.buttons["Add household"].waitForExistence(timeout: 8)
        )
    }

    func testProfileMenu_signedIn_showsSignOutAndSwitch() {
        let app = XCUIApplication()
        configureSignedInListShell(app)
        app.launch()
        XCTAssertTrue(ui(app, UiTestTags.appRoot).waitForExistence(timeout: 20))
        ui(app, UiTestTags.profileButton).tap()
        XCTAssertTrue(ui(app, UiTestTags.profileMenuLogout).waitForExistence(timeout: 5))
        XCTAssertTrue(ui(app, UiTestTags.profileMenuSwitch).waitForExistence(timeout: 5))
    }
}

final class LoginGateUITests: XCTestCase {

    func testSignedOut_showsLoginGate() {
        let app = XCUIApplication()
        app.launchArguments = [
            "-outreach.ui_test",
            "outreach.ui_test.force_auth_state=signed_out",
            "outreach.ui_test.skip_startup_delay=true",
        ]
        app.launch()
        XCTAssertTrue(
            ui(app, UiTestTags.loginGate).waitForExistence(timeout: 15)
        )
    }
}
