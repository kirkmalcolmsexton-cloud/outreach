# iOS — first-time setup

## Requirements

- **macOS** with **Xcode 15+** (iOS 17 SDK).
- Apple Developer Program membership to run on a physical device; Simulator works for much of the UI, but **Google Sign-In** and **notifications/background** are best verified on a device.

## Open the project

1. In Finder or Terminal, go to `outreach/ios/Outreach/`.
2. Open **`Outreach.xcodeproj`** in Xcode.
3. Select the **Outreach** scheme and a simulator (e.g. iPhone 15) or a connected device.

## Swift package dependencies (first open)

The Xcode project references these remote packages (Xcode will resolve on first build):

- **Firebase iOS SDK** — [https://github.com/firebase/firebase-ios-sdk](https://github.com/firebase/firebase-ios-sdk) — products: `FirebaseAuth`, `FirebaseCore`, `FirebaseFirestore`
- **Google Sign-In** — [https://github.com/google/GoogleSignIn-iOS](https://github.com/google/GoogleSignIn-iOS) — products: `GoogleSignIn`, `GoogleSignInSwift`

If packages fail to resolve: **File → Packages → Reset Package Caches**, then build again (network required).

## GoogleService-Info.plist (required for sign-in)

1. In [Firebase Console](https://console.firebase.google.com/) → your project → **Add app** → **iOS**.
2. Register bundle ID `org.outreach.ios` (or the ID you set in the Xcode target).
3. Download **`GoogleService-Info.plist`** and add it to the **Outreach** target (copy into `ios/Outreach/Outreach/`, ensure **Target Membership** is checked).  
   - Do **not** commit a real file; use **[`ios/GoogleService-Info.plist.example`](../ios/GoogleService-Info.plist.example)** as a template and keep real plist out of git (see `secrets/.gitignore` if you add a local copy rule).
4. In **Info → URL types**, the project includes a placeholder **URL Scheme**; replace it with the **REVERSED_CLIENT_ID** value from your downloaded plist (Xcode: Target → Info → URL Types, or edit `Info.plist` / build settings as documented in [`google-oauth-checklist.md`](google-oauth-checklist.md#7-ios-brief)).

## OAuth in Google Cloud

Create an **iOS** OAuth client (bundle ID, App Store ID if applicable) in the same Google Cloud project as the Android app. Add the iOS app’s **REVERSED_CLIENT_ID** to URL schemes. See [Google OAuth checklist §7](google-oauth-checklist.md#7-ios-brief).

## Build and test

- **Build:** `Cmd+B`.
- **Run:** `Cmd+R`.
- **Secrets (CLI):** From the repo root, after the same consolidated secrets as Android exist, run **`bash ios/scripts/setup-secrets.sh`** to write **`ios/Outreach/Outreach/GoogleService-Info.plist`** when **`google_service_info_plist_base64`** or **`google_service_info_plist`** is set in **`secrets/outreach-secrets.json`** (or decrypts from **`secrets/outreach-secrets.json.age`**). See **[`secrets/README.md`](../secrets/README.md)**.
- **Simulator (CLI):** Apple provides the **iOS Simulator** (not an “emulator”). From the repo root:
  - **`bash ios/scripts/simulator.sh start`** — creates the default **Outreach iPhone 16** simulator if needed (latest installed iOS runtime + iPhone 16/15/14 device type), opens **Simulator.app**, and boots it. Override the name with **`OUTREACH_IOS_SIM_NAME`**.
  - **`export OUTREACH_DESTINATION="$(bash ios/scripts/resolve-outreach-ios-simulator-destination.sh)"`** then **`bash ios/scripts/build.sh build`** — same concrete destination idea as Android’s **`ANDROID_SERIAL`** helper.
  - See **`bash ios/scripts/simulator.sh --help`** for **`list`**, **`shutdown all`**, **`wait`**, etc.
  - **Install + launch on Simulator:** **`bash ios/scripts/deploy-simulator.sh`** — builds Debug, installs **`Outreach.app`**, opens the app (same idea as **Run** in Xcode). Uses **`simulator.sh start`** unless **`OUTREACH_DEPLOY_SKIP_BOOT=1`**.
- **Physical iPhone (CLI):** With the device connected, trusted, **Developer Mode** on, and a signing team that can run the **Outreach** target on that device (same as **Run** in Xcode), use **`bash ios/scripts/deploy-device.sh`**. The script uses **`xcrun devicectl`** (Xcode 15+) to list devices, build with **`OUTREACH_DESTINATION=platform=iOS,id=…`**, install the **`Debug-iphoneos`** app, and launch it. If several devices are attached, pass **`--udid`**, set **`OUTREACH_DEVICE_UDID`**, or **`--pick`**. The repo’s Xcode project has an **empty** development team in git; for command-line device builds, set your Apple team id once: **`export OUTREACH_DEVELOPMENT_TEAM=XXXXXXXXXX`** (same 10‑character id as in **Xcode → target → Signing & Capabilities**), set **`DEVELOPMENT_TEAM`**, or add the same to **`~/etc/outreach.env`** ( **`build.sh`** and **`deploy-device.sh`** source that file when it exists, like **[§6b](developer-onboarding.md#6b-terminal-build-with-etcoutreach-env-team-age-file)** for Android). See **`bash ios/scripts/deploy-device.sh --help`**. **`OUTREACH_DEPLOY_SKIP_BUILD=1`** reinstalls/launches an existing device build without **`xcodebuild`**.
- **Build (CLI):**  
  `bash ios/scripts/build.sh`  
  Subcommands: **`build`** (default), **`clean`**, **`test`**, **`archive`**. Requires full **Xcode** (not Command Line Tools only). See **`ios/scripts/build.sh -h`**.  
  - **`build`** picks the first **available iPhone Simulator** from **`xcrun simctl list devices available`** (concrete `id=…` destination), because **`generic/platform=iOS Simulator`** often fails when no runtime resolves (xcodebuild then only shows the device placeholder / “iOS … is not installed”). Override with **`OUTREACH_DESTINATION`**.  
  - If **`xcodebuild`** fails with **IDESimulatorFoundation** / **DVTDownloads** / **Abort trap: 6**, run **`xcodebuild -runFirstLaunch`** once (or **`OUTREACH_XCODEBUILD_RUN_FIRST_LAUNCH=1 bash ios/scripts/build.sh build`**).  
  - If **`simctl list runtimes`** is empty or **`simctl list devices available`** has no iPhones, install an **iOS Simulator** runtime under **Xcode → Settings → Platforms**. Device builds / **archive** need the **iOS** (device) platform for your SDK version.

## App Store / TestFlight (Milestone D)

- Configure **App Store Connect** for bundle ID, privacy nutrition labels (location, third-party sign-in, Google APIs), and App Review notes for restricted OAuth scopes.
- See [release-process](release-process.md) for org-wide release practices; iOS store steps are not yet integrated into GitHub Actions in this repository.

## Local secrets alignment

Team consolidated secrets: [`secrets/README.md`](../secrets/README.md). Use **`ios/scripts/setup-secrets.sh`** to materialize **`GoogleService-Info.plist`** in parallel with Android’s **`android/scripts/setup-secrets.sh`** (same **`outreach-secrets.json`** / **`.age`** file).
