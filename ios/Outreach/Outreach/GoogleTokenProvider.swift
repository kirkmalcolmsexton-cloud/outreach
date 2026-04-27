import Foundation
import UIKit
import GoogleSignIn
import FirebaseAuth
import FirebaseCore

/// OAuth scopes aligned with Android `outreachGoogleSheetsScopes` in `MainActivity.kt`.
let outreachGoogleSheetsScopeStrings: [String] = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive.file",
    "https://www.googleapis.com/auth/drive.metadata.readonly"
]

final class GoogleTokenProvider: GoogleAccessTokenProvider, @unchecked Sendable {
    func getAccessToken(_ scopes: [String]) async -> String? {
        guard let user = GIDSignIn.sharedInstance.currentUser else { return nil }
        do {
            let missing = scopes.filter { scope in
                !(user.grantedScopes?.contains(scope) ?? false)
            }
            if !missing.isEmpty {
                guard let root = UIApplication.outreachKeyWindow?.rootViewController?.outreachTopPresented else { return nil }
                _ = try await user.addScopes(missing, presenting: root)
            }
            try await user.refreshTokensIfNeeded()
            return user.accessToken.tokenString
        } catch {
            return nil
        }
    }
}

enum GoogleSignInCoordinator {
    static func signInWithGoogle(presenting root: UIViewController) async throws {
        guard FirebaseApp.app() != nil else {
            throw NSError(
                domain: "OutreachAuth",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "Add GoogleService-Info.plist to the app target and rebuild."]
            )
        }
        let result = try await GIDSignIn.sharedInstance.signIn(
            withPresenting: root,
            hint: nil,
            additionalScopes: outreachGoogleSheetsScopeStrings
        )
        let user = result.user
        guard let idToken = user.idToken?.tokenString else {
            throw NSError(domain: "OutreachAuth", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing ID token"])
        }
        let accessToken = user.accessToken.tokenString
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
        try await Auth.auth().signIn(with: credential)
    }

    static func signOut() {
        GIDSignIn.sharedInstance.signOut()
        if FirebaseApp.app() != nil {
            try? Auth.auth().signOut()
        }
    }

    static var isFullySignedInForSheets: Bool {
        #if DEBUG
        switch UiAutomationConfig.mockAuthState {
        case .signedIn: return true
        case .signedOut: return false
        case .none: break
        }
        #endif
        guard FirebaseApp.app() != nil,
              Auth.auth().currentUser != nil,
              let u = GIDSignIn.sharedInstance.currentUser
        else { return false }
        return outreachGoogleSheetsScopeStrings.allSatisfy { u.grantedScopes?.contains($0) ?? false }
    }
}

extension UIApplication {
    static var outreachKeyWindow: UIWindow? {
        shared
            .connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }
}

extension UIViewController {
    /// Top-most controller for modal presentation (SwiftUI hosting hierarchy safe).
    var outreachTopPresented: UIViewController {
        if let presented = presentedViewController {
            return presented.outreachTopPresented
        }
        if let nav = self as? UINavigationController, let visible = nav.visibleViewController {
            return visible.outreachTopPresented
        }
        if let tab = self as? UITabBarController, let selected = tab.selectedViewController {
            return selected.outreachTopPresented
        }
        return self
    }
}
