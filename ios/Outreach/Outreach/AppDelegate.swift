import UIKit
import FirebaseCore
import GoogleSignIn
import BackgroundTasks

// Align with Firebase docs: use Firebase's OAuth client ID; optional WEB_CLIENT_ID → serverClientID (Firebase Auth id token).
private func outreachConfigureGoogleSignInFromServicePlist(path: String) {
    guard let plist = NSDictionary(contentsOfFile: path) as? [String: Any] else { return }
    let fromPlist = plist["CLIENT_ID"] as? String
    let firebaseClientId = FirebaseApp.app()?.options.clientID
    let clientId = [firebaseClientId, fromPlist].compactMap { $0 }.first { !$0.isEmpty }
    guard let clientId else { return }
    let webId = (plist["WEB_CLIENT_ID"] as? String).flatMap { $0.isEmpty ? nil : $0 }
    if let webId {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId, serverClientID: webId)
    } else {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
    }
}

enum BGTaskId {
    static let refresh = "com.outreach.app.refresh"
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"), FileManager.default.fileExists(atPath: path) {
            FirebaseApp.configure()
            outreachConfigureGoogleSignInFromServicePlist(path: path)
        } else {
            // Developers must add GoogleService-Info.plist from Firebase; auth stays disabled until then.
        }
        registerBackgroundSync()
        return true
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }

    private func registerBackgroundSync() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: BGTaskId.refresh, using: nil) { task in
            scheduleBackgroundSync()
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            refresh.expirationHandler = { }
            Task {
                let ok = (try? await BackgroundSyncRunner.runSync()) != nil
                await MainActor.run {
                    refresh.setTaskCompleted(success: ok)
                }
            }
        }
    }
}

func scheduleBackgroundSync() {
    let request = BGAppRefreshTaskRequest(identifier: BGTaskId.refresh)
    request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
    try? BGTaskScheduler.shared.submit(request)
}

extension Notification.Name {
    static let outreachDataDidChange = Notification.Name("outreachDataDidChange")
    static let outreachSignedOut = Notification.Name("outreachSignedOut")
}
