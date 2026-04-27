import XCTest

/// Uses `UiTestTags` (same string constants as Android `TestTags`) for stable selectors; strings are compiled into
/// this target via shared [`UiTestTags.swift`](../Outreach/UiTestTags.swift) in the UI test target.
private func ui(_ app: XCUIApplication, _ id: String) -> XCUIElement {
    app.descendants(matching: .any).matching(identifier: id).firstMatch
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

        ui(app, UiTestTags.navVisits).tap()
        XCTAssertTrue(ui(app, UiTestTags.contentVisits).waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Visits"].waitForExistence(timeout: 5))
        XCTAssertTrue(ui(app, UiTestTags.visitsRoot).waitForExistence(timeout: 10))

        ui(app, UiTestTags.navSettings).tap()
        XCTAssertTrue(ui(app, UiTestTags.contentSettings).waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.buttons["Load tab names from Sheets"].waitForExistence(timeout: 5),
            "Spreadsheet section should expose load-tabs (header text may be grouped for VoiceOver)."
        )
        XCTAssertTrue(app.buttons["Save & sync from sheet now"].waitForExistence(timeout: 5))

        ui(app, UiTestTags.navHome).tap()
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
