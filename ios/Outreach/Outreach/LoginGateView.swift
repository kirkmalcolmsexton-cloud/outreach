import SwiftUI
struct LoginGateView: View {
    var onSignedIn: () -> Void
    @State private var errorMessage: String?
    @State private var isSigningIn = false

    var body: some View {
        VStack(spacing: 24) {
            Text("Outreach")
                .font(.largeTitle.bold())
            Text("Sign in with Google to access your spreadsheet, map, and visit log.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
            if let e = errorMessage {
                Text(e)
                    .font(.callout)
                    .foregroundStyle(.red)
            }
            Button {
                Task { await signInTapped() }
            } label: {
                if isSigningIn { ProgressView() } else { Text("Sign in with Google") }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isSigningIn)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .accessibilityIdentifier(UiTestTags.loginGate)
    }

    @MainActor
    private func signInTapped() async {
        errorMessage = nil
        isSigningIn = true
        defer { isSigningIn = false }
        do {
            guard let root = UIApplication.outreachKeyWindow?.rootViewController?.outreachTopPresented else {
                errorMessage = "No window — cannot present sign-in."
                return
            }
            if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") == nil {
                errorMessage = "Add GoogleService-Info.plist from Firebase to enable sign-in (see docs/ios-onboarding.md)."
                return
            }
            try await GoogleSignInCoordinator.signInWithGoogle(presenting: root)
            if GoogleSignInCoordinator.isFullySignedInForSheets {
                onSignedIn()
            } else {
                errorMessage = "Additional permissions may be required. Try again."
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
