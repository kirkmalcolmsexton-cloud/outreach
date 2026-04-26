import Foundation
import SwiftData

@Model
final class HouseholdEntry {
    @Attribute(.unique) var id: String
    var name: String
    var streetAddress: String
    var neighborhood: String
    var briefComment: String
    var lastVisited: String?
    var notes: String?
    var sheetName: String
    var rowNumber: Int
    var latitude: Double?
    var longitude: Double?
    var assignedTo: String?

    init(
        id: String,
        name: String,
        streetAddress: String,
        neighborhood: String,
        briefComment: String,
        lastVisited: String?,
        notes: String?,
        sheetName: String,
        rowNumber: Int,
        latitude: Double?,
        longitude: Double?,
        assignedTo: String?
    ) {
        self.id = id
        self.name = name
        self.streetAddress = streetAddress
        self.neighborhood = neighborhood
        self.briefComment = briefComment
        self.lastVisited = lastVisited
        self.notes = notes
        self.sheetName = sheetName
        self.rowNumber = rowNumber
        self.latitude = latitude
        self.longitude = longitude
        self.assignedTo = assignedTo
    }
}

@Model
final class PendingSyncEntry {
    @Attribute(.unique) var id: UUID
    var created: Date
    var householdId: String
    var briefComment: String
    var notes: String?
    var lastVisitedIsoDate: String
    var sheetName: String?
    var rowNumber: Int?

    init(
        id: UUID = UUID(),
        created: Date = Date(),
        householdId: String,
        briefComment: String,
        notes: String?,
        lastVisitedIsoDate: String,
        sheetName: String?,
        rowNumber: Int?
    ) {
        self.id = id
        self.created = created
        self.householdId = householdId
        self.briefComment = briefComment
        self.notes = notes
        self.lastVisitedIsoDate = lastVisitedIsoDate
        self.sheetName = sheetName
        self.rowNumber = rowNumber
    }
}

@Model
final class PendingAppendEntry {
    @Attribute(.unique) var householdId: String
    var name: String
    var streetAddress: String
    var neighborhood: String
    var sheetName: String

    init(
        householdId: String,
        name: String,
        streetAddress: String,
        neighborhood: String,
        sheetName: String
    ) {
        self.householdId = householdId
        self.name = name
        self.streetAddress = streetAddress
        self.neighborhood = neighborhood
        self.sheetName = sheetName
    }
}

extension HouseholdEntry {
    func toRecord() -> HouseholdRecord {
        HouseholdRecord(
            id: id,
            name: name,
            streetAddress: streetAddress,
            neighborhood: neighborhood,
            briefComment: briefComment,
            lastVisited: lastVisited,
            notes: notes,
            source: SourceMetadata(sheetName: sheetName, rowNumber: rowNumber),
            raw: RawHouseholdRow(),
            latitude: latitude,
            longitude: longitude,
            assignedTo: assignedTo
        )
    }
}
