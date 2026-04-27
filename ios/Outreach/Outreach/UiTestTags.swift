import Foundation

/// String identifiers aligned with Android `org.outreach.ui.testtags.TestTags` for UI automation parity.
enum UiTestTags {
    static let appRoot = "app_root"
    static let loginGate = "login_gate"

    static let profileButton = "profile_button"
    static let profileMenuLogin = "profile_menu_login"
    static let profileMenuSwitch = "profile_menu_switch"
    static let profileMenuLogout = "profile_menu_logout"

    static let navHome = "nav_home"
    static let navVisits = "nav_visits"
    static let navSettings = "nav_settings"

    static let contentHome = "content_home"
    static let contentVisits = "content_visits"
    static let contentSettings = "content_settings"

    static let mapRoot = "map_root"
    static let mapSearch = "map_search"
    static let modeMap = "mode_map"
    static let modeList = "mode_list"
    static let mapNavFab = "map_nav_fab"
    static let mapAddPerson = "map_add_person"

    static let visitsRoot = "visits_root"
    static let visitsBrief = "visits_brief"
    static let visitsNotes = "visits_notes"
    static let visitsSave = "visits_save"

    static let settingsRoot = "settings_root"
    static let settingsPickSpreadsheet = "settings_pick_spreadsheet"
    static let settingsValidate = "settings_validate"
    static let settingsSync = "settings_sync"
    static let settingsZipSection = "settings_zip_section"
    static let settingsAppVersion = "settings_app_version"

    static let syncStatusBanner = "sync_status_banner"
}
