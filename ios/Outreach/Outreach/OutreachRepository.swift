import Combine
import Foundation
import SwiftData

@MainActor
final class OutreachRepository: ObservableObject {
    private let modelContext: ModelContext
    let configStore: AppConfigStore
    private let sheetsApi: any SheetsApi
    private let geocoder: GeocodingService

    init(
        modelContext: ModelContext,
        configStore: AppConfigStore,
        sheetsApi: any SheetsApi,
        geocoder: GeocodingService
    ) {
        self.modelContext = modelContext
        self.configStore = configStore
        self.sheetsApi = sheetsApi
        self.geocoder = geocoder
    }

    func fetchAllHouseholds() throws -> [HouseholdEntry] {
        try modelContext.fetch(FetchDescriptor<HouseholdEntry>())
    }

    func householdsAsRecords() throws -> [HouseholdRecord] {
        try fetchAllHouseholds().map { $0.toRecord() }
    }

    var config: AppConfig { configStore.config }

    func setConfig(_ c: AppConfig) async {
        configStore.update(c)
    }

    func availableTabs(spreadsheetId: String) async throws -> [String] {
        try await sheetsApi.listTabs(spreadsheetId: spreadsheetId)
    }

    func fetchSpreadsheetTitle(spreadsheetId: String) async throws -> String? {
        try await sheetsApi.getSpreadsheetTitle(spreadsheetId: spreadsheetId)
    }

    func resolveSpreadsheetIdFromDriveMetadata(
        displayName: String,
        lastModifiedMillis: Int64?
    ) async throws -> String? {
        try await sheetsApi.resolveSpreadsheetIdFromDriveMetadata(
            displayName: displayName,
            lastModifiedMillis: lastModifiedMillis
        )
    }

    func loadBriefCommentPresets() async throws -> [String] {
        let cfg = configStore.config
        if cfg.spreadsheetId.isEmpty { return [] }
        return try await sheetsApi.fetchBriefCommentPresets(spreadsheetId: cfg.spreadsheetId)
    }

    func isSheetSchemaValid(spreadsheetId: String, selectedTabs: Set<String>) async throws -> Bool {
        if spreadsheetId.isEmpty || selectedTabs.isEmpty { return false }
        for t in selectedTabs {
            if try await !sheetsApi.validateRequiredHeaders(spreadsheetId: spreadsheetId, tabName: t) { return false }
        }
        return true
    }

    /// Full re-import from Sheets (matches Android `syncFromSheet` replace strategy).
    func syncFromSheet() async throws {
        let cfg = configStore.config
        if cfg.spreadsheetId.isEmpty || cfg.selectedTabs.isEmpty { return }
        let existing = try fetchAllHouseholds()
        let existingById = Dictionary(uniqueKeysWithValues: existing.map { ($0.id, $0) })
        var geocodeCache = [String: (Double, Double)?]()
        var entities: [HouseholdEntry] = []
        for tab in cfg.selectedTabs {
            let rows = try await sheetsApi.fetchRows(spreadsheetId: cfg.spreadsheetId, tabName: tab)
            for (idx, row) in rows.enumerated() {
                let parsed = parseSpreadsheetRow(row, source: SourceMetadata(sheetName: tab, rowNumber: idx + 2))
                let ex = existingById[parsed.id]
                let latLng: (Double, Double)? = {
                    if let e = ex, e.streetAddress == parsed.streetAddress, let la = e.latitude, let lo = e.longitude {
                        return (la, lo)
                    }
                    if let cached = geocodeCache[parsed.streetAddress] { return cached }
                    return nil
                }()
                let resolved: (Double, Double)? = await {
                    if let l = latLng { return l }
                    let g = await geocoder.geocodeAddress(parsed.streetAddress)
                    geocodeCache[parsed.streetAddress] = g
                    return g
                }()
                entities.append(
                    HouseholdEntry(
                        id: parsed.id,
                        name: parsed.name,
                        streetAddress: parsed.streetAddress,
                        neighborhood: parsed.neighborhood,
                        briefComment: parsed.briefComment,
                        lastVisited: parsed.lastVisited,
                        notes: parsed.notes,
                        sheetName: tab,
                        rowNumber: parsed.source.rowNumber,
                        latitude: resolved?.0,
                        longitude: resolved?.1,
                        assignedTo: nil
                    )
                )
            }
        }
        for e in existing { modelContext.delete(e) }
        for h in entities { modelContext.insert(h) }
        try modelContext.save()
        NotificationCenter.default.post(name: .outreachDataDidChange, object: nil)
    }

    func saveVisitUpdate(_ update: VisitUpdate) throws {
        let hid = update.householdId
        let d = FetchDescriptor<HouseholdEntry>(predicate: #Predicate<HouseholdEntry> { $0.id == hid })
        let hous = try modelContext.fetch(d).first
        if let h = hous {
            h.briefComment = update.briefComment
            h.notes = update.notes
            h.lastVisited = update.lastVisitedIsoDate
        }
        modelContext.insert(
            PendingSyncEntry(
                householdId: update.householdId,
                briefComment: update.briefComment,
                notes: update.notes,
                lastVisitedIsoDate: update.lastVisitedIsoDate,
                sheetName: update.sheetName ?? hous?.sheetName,
                rowNumber: update.rowNumber ?? hous?.rowNumber
            )
        )
        try modelContext.save()
        NotificationCenter.default.post(name: .outreachDataDidChange, object: nil)
    }

    func addHousehold(
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ) async throws -> String? {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let s = streetAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        let nh = neighborhood.trimmingCharacters(in: .whitespacesAndNewlines)
        if n.isEmpty || s.isEmpty { return nil }
        let cfg = configStore.config
        if cfg.spreadsheetId.isEmpty { return nil }
        let id = createHouseholdId(name: n, streetAddress: s, neighborhood: nh)
        let latLng = await geocoder.geocodeAddress(s)
        let row = SpreadsheetRowInput(
            briefComments: nil,
            lastVisited: nil,
            name: n,
            streetAddress: s,
            neighborhood: nh,
            notes: nil
        )
        let parsed = parseSpreadsheetRow(row, source: SourceMetadata(sheetName: tabName, rowNumber: 2))
        let appendResult = try? await sheetsApi.appendHouseholdRow(
            spreadsheetId: cfg.spreadsheetId,
            tabName: tabName,
            name: n,
            streetAddress: s,
            neighborhood: nh
        )
        let rowNum = appendResult?.rowNumber ?? -1
        let entity = HouseholdEntry(
            id: id,
            name: parsed.name,
            streetAddress: parsed.streetAddress,
            neighborhood: parsed.neighborhood,
            briefComment: parsed.briefComment,
            lastVisited: parsed.lastVisited,
            notes: parsed.notes,
            sheetName: tabName,
            rowNumber: rowNum >= 1 ? rowNum : -1,
            latitude: latLng?.0,
            longitude: latLng?.1,
            assignedTo: nil
        )
        modelContext.insert(entity)
        if rowNum < 1 {
            modelContext.insert(
                PendingAppendEntry(
                    householdId: id,
                    name: n,
                    streetAddress: s,
                    neighborhood: nh,
                    sheetName: tabName
                )
            )
        }
        try modelContext.save()
        NotificationCenter.default.post(name: .outreachDataDidChange, object: nil)
        return id
    }

    func assignHousehold(householdId: String, assignee: String?) throws {
        let hid = householdId
        let d = FetchDescriptor<HouseholdEntry>(predicate: #Predicate<HouseholdEntry> { $0.id == hid })
        if let h = try modelContext.fetch(d).first {
            h.assignedTo = assignee
            try modelContext.save()
        }
    }

    func fetchPendingAppends() throws -> [PendingAppendEntry] {
        try modelContext.fetch(FetchDescriptor<PendingAppendEntry>())
    }

    func fetchPendingSyncs() throws -> [PendingSyncEntry] {
        try modelContext.fetch(
            FetchDescriptor<PendingSyncEntry>(sortBy: [SortDescriptor(\.created, order: .forward)])
        )
    }

    func flushPendingSync() async throws {
        let cfg = configStore.config
        if cfg.spreadsheetId.isEmpty { return }
        for p in try fetchPendingAppends() {
            if let r = try await sheetsApi.appendHouseholdRow(
                spreadsheetId: cfg.spreadsheetId,
                tabName: p.sheetName,
                name: p.name,
                streetAddress: p.streetAddress,
                neighborhood: p.neighborhood
            ), r.rowNumber >= 1 {
                let hid = p.householdId
                let d = FetchDescriptor<HouseholdEntry>(predicate: #Predicate<HouseholdEntry> { $0.id == hid })
                if let h = try modelContext.fetch(d).first {
                    h.rowNumber = r.rowNumber
                }
                modelContext.delete(p)
            }
        }
        for pend in try fetchPendingSyncs() {
            let hid = pend.householdId
            let hd = FetchDescriptor<HouseholdEntry>(predicate: #Predicate<HouseholdEntry> { $0.id == hid })
            let household = try modelContext.fetch(hd).first
            let rowNumber: Int? = {
                if let h = household, h.rowNumber >= 1 { return h.rowNumber }
                if let r = pend.rowNumber, r >= 1 { return r }
                return nil
            }()
            guard let rowNumber else { continue }
            try await sheetsApi.updateVisit(
                spreadsheetId: cfg.spreadsheetId,
                update: VisitUpdate(
                    householdId: pend.householdId,
                    briefComment: pend.briefComment,
                    notes: pend.notes,
                    lastVisitedIsoDate: pend.lastVisitedIsoDate,
                    sheetName: pend.sheetName ?? household?.sheetName,
                    rowNumber: rowNumber
                )
            )
            modelContext.delete(pend)
        }
        try modelContext.save()
        NotificationCenter.default.post(name: .outreachDataDidChange, object: nil)
    }

    func pickNextTarget(_ items: [HouseholdRecord]) -> HouseholdRecord? {
        items.min { a, b in
            sortEpochForLastVisited(a.lastVisited) < sortEpochForLastVisited(b.lastVisited)
        }
    }
}
