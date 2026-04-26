import SwiftUI
import SwiftData
import FirebaseCore

@main
struct OutreachApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appConfig = AppConfigStore()

    var sharedModelContainer: ModelContainer = {
        do {
            return try ModelContainer(
                for: HouseholdEntry.self, PendingSyncEntry.self, PendingAppendEntry.self
            )
        } catch {
            fatalError("ModelContainer: \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            RootView()
                .modelContainer(sharedModelContainer)
                .environmentObject(appConfig)
        }
    }
}
