import SwiftUI
import FirebaseAuth

struct SettingsTabView: View {
    @EnvironmentObject private var appConfig: AppConfigStore
    @Environment(\.modelContext) private var modelContext
    @State private var sheetId: String = ""
    @State private var titleHint: String = ""
    @State private var availableTabs: [String] = []
    @State private var selected: Set<String> = []
    @State private var status: String = ""
    @State private var isLoading = false
    @State private var presenceTab: String = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("Spreadsheet") {
                    TextField("Spreadsheet file ID (from URL)", text: $sheetId)
                    Button("Load tab names from Sheets") { Task { await loadTabs() } }
                    if isLoading { ProgressView() }
                    if !availableTabs.isEmpty {
                        ForEach(availableTabs, id: \.self) { t in
                            Toggle(t, isOn: Binding(
                                get: { selected.contains(t) },
                                set: { on in
                                    if on { selected.insert(t) } else { selected.remove(t) }
                                }
                            ))
                        }
                    }
                    if !titleHint.isEmpty { Text(titleHint).font(.caption) }
                }
                Section("Sync") {
                    Button("Save & sync from sheet now") { Task { await saveAndSync() } }
                    if !status.isEmpty { Text(status).font(.callout) }
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
            }
            .navigationTitle("Settings")
            .onAppear {
                let c = appConfig.config
                sheetId = c.spreadsheetId
                selected = c.selectedTabs
                titleHint = c.spreadsheetTitle.map { "Document: \($0)" } ?? ""
            }
        }
    }

    @MainActor
    private func loadTabs() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        isLoading = true
        status = ""
        defer { isLoading = false }
        let id = sheetId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, let repo = ServiceLocator.repository else {
            status = "Enter a spreadsheet id first."
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
    private func saveAndSync() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        isLoading = true
        status = ""
        defer { isLoading = false }
        let id = sheetId.trimmingCharacters(in: .whitespacesAndNewlines)
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
                spreadsheetTitle: title,
                selectedTabs: selected,
                mapBriefCommentMode: appConfig.config.mapBriefCommentMode,
                mapBriefCommentFilter: appConfig.config.mapBriefCommentFilter,
                mapDateStartIso: appConfig.config.mapDateStartIso,
                mapDateEndIso: appConfig.config.mapDateEndIso,
                mapQuickRange: appConfig.config.mapQuickRange,
                mapOldestRecordsLimit: appConfig.config.mapOldestRecordsLimit
            )
            await repo.setConfig(c)
            try await repo.syncFromSheet()
            status = "Synced."
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
