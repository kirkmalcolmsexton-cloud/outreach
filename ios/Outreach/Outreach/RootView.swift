import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var appConfig: AppConfigStore
    @Query(sort: [SortDescriptor(\HouseholdEntry.id)])
    private var householdEntries: [HouseholdEntry]
    @State private var showLoginGate: Bool
    @State private var selectedTab = 0
    @State private var syncError: String?
    @State private var isSyncing = false

    init() {
        _showLoginGate = State(initialValue: !GoogleSignInCoordinator.isFullySignedInForSheets)
    }

    var body: some View {
        Group {
            if showLoginGate {
                LoginGateView {
                    showLoginGate = false
                }
            } else {
                mainContent
            }
        }
        .onAppear {
            ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
            runInitialSync()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                runInitialSync()
                scheduleBackgroundSync()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .outreachSignedOut)) { _ in
            showLoginGate = true
        }
    }

    @ViewBuilder
    private var mainContent: some View {
        let records: [HouseholdRecord] = householdEntries.map { $0.toRecord() }
        let config = appConfig.config
        TabView(selection: $selectedTab) {
            MapTabView(
                households: records,
                config: config,
                selectedHouseholdId: .constant(nil)
            )
            .tabItem { Label("Map", systemImage: "map") }
            .tag(0)
            VisitsTabView(
                records: records,
                config: config
            )
            .tabItem { Label("Visits", systemImage: "list.bullet") }
            .tag(1)
            SettingsTabView()
            .tabItem { Label("Settings", systemImage: "gearshape") }
            .tag(2)
        }
        if let s = syncError {
            Text(s)
                .font(.footnote)
                .foregroundStyle(.red)
                .padding(8)
        }
        if isSyncing { ProgressView().padding(8) }
    }

    private func runInitialSync() {
        Task { @MainActor in
            guard let repo = ServiceLocator.repository else { return }
            isSyncing = true
            syncError = nil
            defer { isSyncing = false }
            do {
                try await repo.flushPendingSync()
                try await repo.syncFromSheet()
            } catch {
                syncError = "Sync failed: \(error.localizedDescription)"
            }
        }
    }
}
