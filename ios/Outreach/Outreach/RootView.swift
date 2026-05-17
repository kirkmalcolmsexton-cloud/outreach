import SwiftData
import SwiftUI
import UIKit
import FirebaseAuth

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
    @State private var selectedHouseholdId: String?
    @State private var homeMapMode = MapHomeMode.defaultForLaunch()
    @State private var profileActionMessage: String?

    init() {
        #if DEBUG
        let gate: Bool = {
            switch UiAutomationConfig.mockAuthState {
            case .signedIn: return false
            case .signedOut: return true
            case .none: break
            }
            return !GoogleSignInCoordinator.isFullySignedInForSheets
        }()
        _showLoginGate = State(initialValue: gate)
        #else
        _showLoginGate = State(initialValue: !GoogleSignInCoordinator.isFullySignedInForSheets)
        #endif
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
            if !UiAutomationConfig.skipStartupSideEffects {
                runInitialSync()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, !UiAutomationConfig.skipStartupSideEffects {
                runInitialSync()
                scheduleBackgroundSync()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .outreachSignedOut)) { _ in
            showLoginGate = true
        }
    }

    private var shellTitle: String {
        switch selectedTab {
        case 1: return "Visits"
        case 2: return "Settings"
        default: return "Outreach"
        }
    }

    @ViewBuilder
    private var mainContent: some View {
        let records: [HouseholdRecord] = householdEntries.map { $0.toRecord() }
        let config = appConfig.config
        NavigationStack {
            VStack(spacing: 0) {
                TabView(selection: $selectedTab) {
                    ZStack {
                        MapTabView(
                            households: records,
                            config: config,
                            selectedHouseholdId: $selectedHouseholdId,
                            mapHomeMode: $homeMapMode
                        )
                    }
                    .accessibilityIdentifier(UiTestTags.mapRoot)
                    .tabItem {
                        Label("Home", systemImage: "map")
                            .accessibilityIdentifier(UiTestTags.navHome)
                    }
                    .tag(0)
                    VisitsTabView(
                        records: records,
                        selectedHouseholdId: $selectedHouseholdId
                    )
                    .tabItem {
                        Label("Visits", systemImage: "list.bullet")
                            .accessibilityIdentifier(UiTestTags.navVisits)
                    }
                    .tag(1)
                    ZStack {
                        SettingsTabView()
                    }
                    .accessibilityIdentifier(UiTestTags.contentSettings)
                    .tabItem {
                        Label("Settings", systemImage: "gearshape")
                            .accessibilityIdentifier(UiTestTags.navSettings)
                    }
                    .tag(2)
                }
                if let s = syncError {
                    Text(s)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .padding(8)
                        .accessibilityIdentifier(UiTestTags.syncStatusBanner)
                }
                if isSyncing { ProgressView().padding(8) }
                if let m = profileActionMessage, !m.isEmpty {
                    Text(m)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                }
            }
            .navigationTitle(shellTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if selectedTab == 0 {
                        Picker("Map or list", selection: $homeMapMode) {
                            Text("Map").tag(MapHomeMode.map)
                            Text("List").tag(MapHomeMode.list)
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 200)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        if let email = Auth.auth().currentUser?.email, !email.isEmpty {
                            Text(email).foregroundStyle(.secondary)
                        }
                        Button("Login") {
                            profileActionMessage = nil
                            Task { await profileSignIn() }
                        }
                        .accessibilityIdentifier(UiTestTags.profileMenuLogin)
                        Button("Switch account") {
                            profileActionMessage = nil
                            Task { await profileSwitchAccount() }
                        }
                        .accessibilityIdentifier(UiTestTags.profileMenuSwitch)
                        Button("Log out", role: .destructive) {
                            GoogleSignInCoordinator.signOut()
                            NotificationCenter.default.post(name: .outreachSignedOut, object: nil)
                        }
                        .accessibilityIdentifier(UiTestTags.profileMenuLogout)
                    } label: {
                        Image(systemName: "person.circle")
                    }
                    .accessibilityIdentifier(UiTestTags.profileButton)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        dismissKeyboardForTabSwitch()
                    }
                    .fontWeight(.semibold)
                }
            }
            .accessibilityIdentifier(UiTestTags.appRoot)
            .onChange(of: selectedTab) { _, _ in
                dismissKeyboardForTabSwitch()
            }
        }
    }

    /// Resign first responder so the tab bar works while a `TextField` has focus (keyboard would otherwise eat taps).
    private func dismissKeyboardForTabSwitch() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    @MainActor
    private func profileSignIn() async {
        do {
            guard let root = UIApplication.outreachKeyWindow?.rootViewController?.outreachTopPresented else {
                profileActionMessage = "No window for sign-in."
                return
            }
            try await GoogleSignInCoordinator.signInWithGoogle(presenting: root)
            profileActionMessage = GoogleSignInCoordinator.isFullySignedInForSheets ? "Signed in." : "Check required permissions."
        } catch {
            profileActionMessage = error.localizedDescription
        }
    }

    @MainActor
    private func profileSwitchAccount() async {
        do {
            guard let root = UIApplication.outreachKeyWindow?.rootViewController?.outreachTopPresented else {
                profileActionMessage = "No window for sign-in."
                return
            }
            GoogleSignInCoordinator.signOut()
            try await GoogleSignInCoordinator.signInWithGoogle(presenting: root)
            profileActionMessage = GoogleSignInCoordinator.isFullySignedInForSheets ? "Switched account." : "Check required permissions."
        } catch {
            profileActionMessage = error.localizedDescription
        }
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

    private func scheduleBackgroundSync() {
        // Reserved for BGTask / periodic flush (parity with Android WorkManager hooks).
    }
}
