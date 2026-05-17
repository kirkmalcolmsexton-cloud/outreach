import Foundation

/// Launch arguments / env for UI tests only (`#if DEBUG`). Mirrors Android `UiAutomationConfig` keys where practical.
///
/// XCTest passes launch arguments from `XCUIApplication.launchArguments`. Require **`-outreach.ui_test`** before any mock behavior is honored.
enum UiAutomationConfig {
    enum MockAuthState {
        case none
        case signedIn
        case signedOut
    }

    enum HomeViewMode {
        case none
        case map
        case list
    }

    /// Gate: UI automation keys are ignored unless this flag is present (Debug builds only).
    static var isUiTesting: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-outreach.ui_test")
        #else
        false
        #endif
    }

    /// When active, skip eager sheet sync and background scheduling from `RootView` on appear / resume (Android `skip_startup_delay` / `skipStartupSideEffects`).
    static var skipStartupSideEffects: Bool {
        #if DEBUG
        guard isUiTesting else { return false }
        return truthy(stringArg("outreach.ui_test.skip_startup_delay"))
        #else
        false
        #endif
    }

    static var mockAuthState: MockAuthState {
        #if DEBUG
        guard isUiTesting else { return .none }
        switch stringArg("outreach.ui_test.force_auth_state")?.lowercased() {
        case "signed_in": return .signedIn
        case "signed_out": return .signedOut
        default: return .none
        }
        #else
        .none
        #endif
    }

    static var homeViewMode: HomeViewMode {
        #if DEBUG
        guard isUiTesting else { return .none }
        switch stringArg("outreach.ui_test.home_view_mode")?.lowercased() {
        case "map": return .map
        case "list": return .list
        default: return .none
        }
        #else
        .none
        #endif
    }

    #if DEBUG
    private static func stringArg(_ name: String) -> String? {
        let prefix = "\(name)="
        for arg in ProcessInfo.processInfo.arguments where arg.hasPrefix(prefix) {
            return String(arg.dropFirst(prefix.count))
        }
        let envKey = name.replacingOccurrences(of: ".", with: "_").uppercased()
        if let v = ProcessInfo.processInfo.environment[envKey], !v.isEmpty { return v }
        return nil
    }

    private static func truthy(_ s: String?) -> Bool {
        guard let s = s?.lowercased() else { return false }
        return s == "1" || s == "true" || s == "yes"
    }
    #endif
}
