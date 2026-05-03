import SwiftUI
import SwiftData
import FirebaseAuth
import UniformTypeIdentifiers

struct SettingsTabView: View {
    @EnvironmentObject private var appConfig: AppConfigStore
    @Environment(\.modelContext) private var modelContext
    @Query(sort: [SortDescriptor(\HouseholdEntry.id)])
    private var householdEntries: [HouseholdEntry]

    @State private var sheetId: String = ""
    @State private var titleHint: String = ""
    @State private var availableTabs: [String] = []
    @State private var selected: Set<String> = []
    @State private var status: String = ""
    @State private var isLoading = false
    @State private var presenceTab: String = ""

    @State private var briefCommentMode: String = "include_all"
    @State private var selectedBriefComments: Set<String> = []
    @State private var briefOptions: [String] = []
    @State private var startDate: Date = Date()
    @State private var endDate: Date = Date()
    @State private var selectedQuickRange: String = "All"
    @State private var oldestRecordsLimitInput: String = ""

    @State private var showFileImporter = false

    private let zipTabRegex = try! NSRegularExpression(pattern: "^\\d{5}(-\\d{4})?$", options: [])

    private var appVersionLabel: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    private var earliestVisitationDate: Date {
        let cal = Calendar.current
        let dates = householdEntries.compactMap { e -> Date? in
            guard let lv = e.lastVisited, !lv.isEmpty else { return nil }
            return parseIsoDateOrNull(lv)
        }
        let floor = cal.date(from: DateComponents(year: 1970, month: 1, day: 1))!
        return dates.map { cal.startOfDay(for: $0) }.min() ?? floor
    }

    private var zipTabs: [String] {
        availableTabs.filter { isZipTabName($0) }
    }

    private var nonZipTabs: [String] {
        availableTabs.filter { !isZipTabName($0) }
    }

    var body: some View {
        formBody
    }

    @ViewBuilder
    private var formBody: some View {
        Form {
            Section {
                HStack(spacing: 20) {
                    Button {
                        showFileImporter = true
                    } label: {
                        Image(systemName: "folder")
                    }
                    .accessibilityIdentifier(UiTestTags.settingsPickSpreadsheet)
                    Button {
                        Task { await validateSchema() }
                    } label: {
                        Image(systemName: "checkmark.circle")
                    }
                    .accessibilityIdentifier(UiTestTags.settingsValidate)
                    Button {
                        Task { await saveAndSync() }
                    } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                    }
                    .accessibilityIdentifier(UiTestTags.settingsSync)
                    Spacer()
                }
                HStack(alignment: .center, spacing: 12) {
                    TextField("Spreadsheet link or ID", text: $sheetId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier(UiTestTags.settingsSpreadsheetLinkField)
                    Button("Load") {
                        Task { await loadTabs() }
                    }
                    .accessibilityIdentifier(UiTestTags.settingsLoadSpreadsheet)
                }
                Text("On iPhone this picker uses Apple’s Files view of Google Drive, not the full drive.google.com or Android picker—shared items often won’t appear here. Paste a Sheets link or spreadsheet ID above (same as opening the sheet in Safari), or move the file to My Drive in the Drive app first.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if isLoading { ProgressView() }
                if !titleHint.isEmpty { Text(titleHint).font(.caption) }
            } header: {
                Text("Spreadsheet")
                    .accessibilityAddTraits(.isHeader)
            }

            if !availableTabs.isEmpty {
                Section {
                    Text("Choose tabs for map and sync. ZIP-style tab names are grouped below.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ForEach(nonZipTabs, id: \.self) { t in
                        Toggle(t, isOn: tabBinding(t))
                    }
                } header: {
                    Text("Tabs")
                }
                if !zipTabs.isEmpty {
                    Section {
                        ForEach(zipTabs, id: \.self) { z in
                            Toggle(z, isOn: tabBinding(z))
                        }
                    } header: {
                        Text("ZIP codes")
                    }
                    .accessibilityIdentifier(UiTestTags.settingsZipSection)
                }
            }

            Section {
                Picker("Brief comments on map", selection: $briefCommentMode) {
                    Text("Include all").tag("include_all")
                    Text("Pick some…").tag("pick_some")
                }
                .onChange(of: briefCommentMode) { _, _ in persistMapFiltersOnly() }

                if briefCommentMode == "pick_some" {
                    if briefOptions.isEmpty {
                        Text("Load tab names and sync to fetch brief options, or type filters from your sheet’s values.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    ForEach(briefOptions, id: \.self) { b in
                        Toggle(formatBriefComment(b), isOn: briefFilterBinding(b))
                    }
                }

                Picker("Quick date range", selection: $selectedQuickRange) {
                    ForEach(["Today", "Yesterday", "All", "Custom"], id: \.self) { r in
                        Text(r).tag(r)
                    }
                }
                .onChange(of: selectedQuickRange) { _, new in
                    applyQuickRange(new)
                    persistMapFiltersOnly()
                }

                if selectedQuickRange == "Custom" {
                    DatePicker("From", selection: $startDate, displayedComponents: .date)
                        .onChange(of: startDate) { _, _ in persistMapFiltersOnly() }
                    DatePicker("Through", selection: $endDate, displayedComponents: .date)
                        .onChange(of: endDate) { _, _ in persistMapFiltersOnly() }
                }

                TextField("Oldest records limit (blank = all)", text: $oldestRecordsLimitInput)
                    .keyboardType(.numberPad)
                    .onChange(of: oldestRecordsLimitInput) { _, _ in persistMapFiltersOnly() }
            } header: {
                Text("Map filters")
            }

            Section {
                Button("Save & sync from sheet now") { Task { await saveAndSync() } }
                if !status.isEmpty { Text(status).font(.callout) }
            } header: {
                Text("Sync")
            }

            Section("Collaboration (Firestore)") {
                TextField("Current tab (for presence)", text: $presenceTab)
                Button("Publish presence to team") { Task { await presence() } }
            }

            Section {
                if let email = Auth.auth().currentUser?.email {
                    Text("Signed in: \(email)")
                }
                Button("Sign out", role: .destructive) {
                    GoogleSignInCoordinator.signOut()
                    NotificationCenter.default.post(name: .outreachSignedOut, object: nil)
                }
            }

            Section {
                Text("Version \(appVersionLabel)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier(UiTestTags.settingsAppVersion)
            }
        }
        .accessibilityIdentifier(UiTestTags.settingsRoot)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.item, .data],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else { return }
                defer { url.stopAccessingSecurityScopedResource() }
                let path = url.absoluteString
                let resolved: String? =
                    extractSpreadsheetIdFromText(path)
                    ?? extractSpreadsheetIdFromText(url.lastPathComponent)
                if let id = resolved {
                    sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: id)
                    Task { await loadTabs() }
                }
            case .failure:
                break
            }
        }
        .onAppear {
            syncStateFromStore()
            Task { await reloadTabsAfterRestore() }
        }
    }

    /// Refetch tab names from Sheets after relaunch so ZIP / tab toggles match stored selection (`availableTabs` is not persisted).
    @MainActor
    private func reloadTabsAfterRestore() async {
        let id = appConfig.config.spreadsheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty else { return }
        guard availableTabs.isEmpty else { return }
        await loadTabs()
    }

    private func isZipTabName(_ name: String) -> Bool {
        zipTabRegex.firstMatch(
            in: name,
            options: [],
            range: NSRange(location: 0, length: (name as NSString).length)
        ) != nil
    }

    private func tabBinding(_ t: String) -> Binding<Bool> {
        Binding(
            get: { selected.contains(t) },
            set: { on in
                if on { selected.insert(t) } else { selected.remove(t) }
                persistMapFiltersOnly()
            }
        )
    }

    private func briefFilterBinding(_ b: String) -> Binding<Bool> {
        Binding(
            get: { selectedBriefComments.contains(b) },
            set: { on in
                if on { selectedBriefComments.insert(b) } else { selectedBriefComments.remove(b) }
                persistMapFiltersOnly()
            }
        )
    }

    private func syncStateFromStore() {
        let c = appConfig.config
        let sid = c.spreadsheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let link = c.spreadsheetDisplayLink?.trimmingCharacters(in: .whitespacesAndNewlines), !link.isEmpty {
            sheetId = link
        } else if !sid.isEmpty {
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: sid)
        } else {
            sheetId = ""
        }
        selected = c.selectedTabs
        titleHint = c.spreadsheetTitle.map { "Document: \($0)" } ?? ""
        briefCommentMode = c.mapBriefCommentMode
        selectedBriefComments = c.mapBriefCommentFilter
        selectedQuickRange = c.mapQuickRange
        oldestRecordsLimitInput = c.mapOldestRecordsLimit.map(String.init) ?? ""

        let cal = Calendar.current
        let floor = earliestVisitationDate
        if let s = c.mapDateStartIso, let d = parseIsoDateOrNull(s) {
            startDate = max(cal.startOfDay(for: d), cal.startOfDay(for: floor))
        } else {
            startDate = cal.startOfDay(for: floor)
        }
        if let e = c.mapDateEndIso, let d = parseIsoDateOrNull(e) {
            endDate = cal.startOfDay(for: d)
        } else {
            endDate = cal.startOfDay(for: Date())
        }
        applyQuickRange(selectedQuickRange)
        Task { await loadBriefOptionsIfPossible() }
    }

    private func applyQuickRange(_ range: String) {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        let floor = earliestVisitationDate
        switch range {
        case "Today":
            let s = max(today, cal.startOfDay(for: floor))
            startDate = s
            endDate = s
        case "Yesterday":
            let y = cal.date(byAdding: .day, value: -1, to: today) ?? today
            let s = max(y, cal.startOfDay(for: floor))
            startDate = s
            endDate = s
        default:
            break
        }
    }

    private func isoDateString(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = .withFullDate
        f.timeZone = TimeZone(identifier: "UTC")
        return f.string(from: d)
    }

    private func persistMapFiltersOnly() {
        var c = appConfig.config
        c.mapBriefCommentMode = briefCommentMode
        c.mapBriefCommentFilter = selectedBriefComments
        c.mapDateStartIso = isoDateString(startDate)
        c.mapDateEndIso = isoDateString(endDate)
        c.mapQuickRange = selectedQuickRange
        c.mapOldestRecordsLimit = parseOldestLimit(oldestRecordsLimitInput)
        Task { @MainActor in
            ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
            guard let repo = ServiceLocator.repository else { return }
            await repo.setConfig(c)
        }
    }

    private func parseOldestLimit(_ s: String) -> Int? {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let n = Int(t), n > 0 else { return nil }
        return n
    }

    @MainActor
    private func loadBriefOptionsIfPossible() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let repo = ServiceLocator.repository else { return }
        let presets = (try? await repo.loadBriefCommentPresets()) ?? []
        if !presets.isEmpty {
            briefOptions = presets
        } else {
            briefOptions = Array(Set(householdEntries.map(\.briefComment))).sorted()
        }
    }

    @MainActor
    private func loadTabs() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        isLoading = true
        status = ""
        defer { isLoading = false }
        var id = sheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let extracted = extractSpreadsheetIdFromText(id) {
            id = extracted
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: extracted)
        } else if !id.isEmpty {
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: id)
        }
        guard !id.isEmpty, let repo = ServiceLocator.repository else {
            status = "Enter a spreadsheet id or paste a Google Sheets URL."
            return
        }
        do {
            if let t = try await repo.fetchSpreadsheetTitle(spreadsheetId: id) {
                titleHint = "Document: \(t)"
            }
            let tabs = try await repo.availableTabs(spreadsheetId: id)
            availableTabs = tabs
        } catch {
            status = "Load tabs: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func validateSchema() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        var id = sheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let extracted = extractSpreadsheetIdFromText(id) {
            id = extracted
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: extracted)
        } else if !id.isEmpty {
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: id)
        }
        guard !id.isEmpty, let repo = ServiceLocator.repository else {
            status = "Enter a spreadsheet id first."
            return
        }
        guard !selected.isEmpty else {
            status = "Select at least one tab."
            return
        }
        do {
            let ok = try await repo.isSheetSchemaValid(spreadsheetId: id, selectedTabs: selected)
            status = ok ? "Schema valid for selected tabs." : "Missing required headers on at least one tab."
        } catch {
            status = "Validate: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func saveAndSync() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        isLoading = true
        status = ""
        defer { isLoading = false }
        var id = sheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let extracted = extractSpreadsheetIdFromText(id) {
            id = extracted
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: extracted)
        } else if !id.isEmpty {
            sheetId = canonicalGoogleSheetsEditURL(forSpreadsheetId: id)
        }
        guard !id.isEmpty, let repo = ServiceLocator.repository else {
            status = "Spreadsheet id required."
            return
        }
        guard !selected.isEmpty else {
            status = "Select at least one tab."
            return
        }
        do {
            guard try await repo.isSheetSchemaValid(spreadsheetId: id, selectedTabs: selected) else {
                status = "One or more tabs are missing required column headers."
                return
            }
            let title = try? await repo.fetchSpreadsheetTitle(spreadsheetId: id)
            let c = AppConfig(
                spreadsheetId: id,
                spreadsheetDisplayLink: canonicalGoogleSheetsEditURL(forSpreadsheetId: id),
                spreadsheetTitle: title,
                selectedTabs: selected,
                mapBriefCommentMode: briefCommentMode,
                mapBriefCommentFilter: selectedBriefComments,
                mapDateStartIso: isoDateString(startDate),
                mapDateEndIso: isoDateString(endDate),
                mapQuickRange: selectedQuickRange,
                mapOldestRecordsLimit: parseOldestLimit(oldestRecordsLimitInput)
            )
            await repo.setConfig(c)
            try await repo.syncFromSheet()
            status = "Synced."
            await loadBriefOptionsIfPossible()
        } catch {
            status = "Sync: \(error.localizedDescription)"
        }
    }

    @MainActor
    private func presence() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let uid = Auth.auth().currentUser?.uid, let c = ServiceLocator.collaboration else {
            status = "Firebase or collaboration not available."
            return
        }
        do {
            try await c.publishPresence(userId: uid, tabName: presenceTab)
            try await c.publishActivity(
                CollaborationEvent(
                    userId: uid,
                    householdId: nil,
                    tabName: presenceTab.isEmpty ? nil : presenceTab,
                    type: "SETTINGS_PRESENCE",
                    epochMillis: Int64(Date().timeIntervalSince1970 * 1000)
                )
            )
            status = "Published presence / activity."
        } catch {
            status = "Collab: \(error.localizedDescription)"
        }
    }
}
