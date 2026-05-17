# iOS — TestFlight and App Store (Milestone D)

## Before TestFlight

1. **App Store Connect** — Create the iOS app with bundle ID `org.outreach.ios` (or the ID you set in the Xcode target).
2. **Signing** — In Xcode, set a **Team** and enable **Automatically manage signing** (or use manual provisioning for enterprise policies).
3. **Firebase / Google** — The same Firebase project as Android can register an additional iOS app; download `GoogleService-Info.plist` and do not commit it.
4. **Privacy (nutrition labels)** — Declare: account sign-in (Google), location when in use (route planning, map), network access, and that data may sync to Google APIs (Sheets, Drive) and Firebase (Auth, Firestore) per your DPA and privacy policy.
5. **App Review notes** — Describe restricted OAuth scopes (Sheets, `drive.file`, Drive metadata) and how test accounts are invited as **Test users** on the OAuth consent screen (see [google-oauth-checklist.md](google-oauth-checklist.md)).
6. **CarPlay** — Not in scope; omit CarPlay entitlements.

## TestFlight

1. Archive in Xcode: **Product → Archive**.
2. Distribute to **TestFlight** from the Organizer.
3. Add internal and external testers; external builds may need **Beta App Review** once.

## CI

GitHub Actions for this repo currently targets the Android app. Adding iOS build/test jobs is optional: use **macos-latest** with **xcodebuild** and a simulator destination, and store signing assets via **App Store Connect API** or matching secrets, following your org’s security policy.
