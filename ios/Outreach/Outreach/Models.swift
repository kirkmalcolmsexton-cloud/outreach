import Foundation

enum VisitOutcome: String, CaseIterable, Codable {
    case notHome = "not_home"
    case leftMessage = "left_message"
    case receptive
    case doNotVisit = "do_not_visit"
    case moved
    case dawatSaath = "dawat_saath"
    case other
}

struct SourceMetadata: Equatable, Hashable, Codable {
    var sheetName: String
    var rowNumber: Int
}

struct RawHouseholdRow: Equatable, Hashable, Codable {
    var briefComments: String?
    var lastVisited: String?
    var name: String?
    var streetAddress: String?
    var neighborhood: String?
    var notes: String?
}

struct HouseholdRecord: Identifiable, Equatable, Hashable {
    var id: String
    var name: String
    var streetAddress: String
    var neighborhood: String
    var briefComment: String
    var lastVisited: String?
    var notes: String?
    var source: SourceMetadata
    var raw: RawHouseholdRow
    var latitude: Double?
    var longitude: Double?
    var assignedTo: String?
}

struct SpreadsheetRowInput: Equatable, Codable {
    var briefComments: String?
    var lastVisited: String?
    var name: String?
    var streetAddress: String?
    var neighborhood: String?
    var notes: String?
}

struct AppConfig: Equatable, Codable {
    var spreadsheetId: String = ""
    var spreadsheetTitle: String?
    var selectedTabs: Set<String> = []
    var mapBriefCommentMode: String = "include_all"
    var mapBriefCommentFilter: Set<String> = []
    var mapDateStartIso: String?
    var mapDateEndIso: String?
    var mapQuickRange: String = "All"
    var mapOldestRecordsLimit: Int?
}

struct VisitUpdate: Equatable {
    var householdId: String
    var briefComment: String
    var notes: String?
    var lastVisitedIsoDate: String
    var sheetName: String?
    var rowNumber: Int?
}

struct CollaborationEvent: Equatable {
    var userId: String
    var householdId: String?
    var tabName: String?
    var type: String
    var epochMillis: Int64
}

struct AppendHouseholdResult {
    var rowNumber: Int
}
