# Outreach

**Repository:** [github.com/kirkmalcolmsexton-cloud/outreach](https://github.com/kirkmalcolmsexton-cloud/outreach)

Outreach is an **Android** app for teams that work from a **Google Sheet** of households: sign in with Google, pick the spreadsheet and zip-code tabs in settings, then use a **map or list** home screen to plan routes and log visits. Data **syncs from Sheets into local storage** for offline use; visit changes are **queued and flushed** when the network is back. A **Firestore** layer adds collaboration scaffolding (presence and activity) without replacing the sheet as the source of truth.

Stack highlights: **Kotlin**, **Jetpack Compose**, **Room + DataStore + WorkManager**, Google Sign-In with Sheets/Drive scopes.

The Android app lives in **`android/`** as a **single-module** project (root Gradle + **`app/`** only). See **`docs/android-architecture.md`** for layout and data flow.

---

## README guide — pick your path

| Who you are | Where to start |
|---------------|----------------|
| **New to Android** — viewing this on GitHub and need the shortest path to **build** and run **tests on the emulator** | Prep: [**Before you clone**](#before-you-clone), then [**First-time setup (emulator)**](#first-time-setup-emulator) |
| **Past first setup** — exploring Outreach using a **real phone** over USB | [**Physical device testing**](#physical-device-testing) |
| **Comfortable with Android** — you want **commands, flags, and scripts** without extra narrative | [**CLI quick reference**](#cli-quick-reference) |
| **Cursor / multi-root workspace** — you use the workspace repo next to Outreach | [**Cursor workspace**](#cursor-workspace) |

---

## First-time setup (emulator)

Goal: get the repo onto your machine (**Windows**, **macOS**, or **Linux**), install tooling once, then build and run **instrumentation tests** on the **default Outreach emulator**.

### Before you clone

Complete these steps on your machine **before** **`git clone`**.

#### 1) Check free disk space (~15 GB minimum)

Android Studio, one emulator image, Gradle, and builds need **about 15 GB free** (more is better).

- **Windows — PowerShell:** press **Windows key**, type **`PowerShell`**, press **Enter**, then run:
  ```powershell
  Get-PSDrive C | Select-Object Used,Free
  ```
  Check the **`Free`** column — it should be **several gigabytes above 15 GB** if possible. Use **`Get-PSDrive D`** if you install on **`D:`** instead.

- **macOS:** press **Cmd + Space**, type **Terminal**, **Enter**. Run:
  ```bash
  df -h ~
  ```
  Find the **`Avail`** column on the line for your disk — ideally **15G** or larger.

- **Linux:** open your terminal app and run:
  ```bash
  df -h ~
  ```
  Same — ensure enough **Avail** on the filesystem where your home directory lives.

Free space inside **Settings → System → Storage** (Windows) or **About This Mac → Storage** (Mac) works too — you want **at least ~15 GB** clear before proceeding.

#### 2) Install Git

1. Open **[git-scm.com/downloads](https://git-scm.com/downloads)** in a browser.
2. Click **Windows** / **Mac** / **Linux** and download the installer.
3. Run the installer:
   - **Windows:** leave **Git Bash** enabled when the wizard lists components (you need it for **`scripts/*.sh`** later). Finish with **Install**.
   - **macOS:** drag **Git** to **Applications** if using the `.dmg`, or install via **Homebrew**: `brew install git` if you use Brew.
   - **Linux (Debian/Ubuntu):**
     ```bash
     sudo apt update
     sudo apt install -y git
     ```
4. Open a **new** terminal (or **Git Bash** on Windows) and verify:
   ```bash
   git --version
   ```
   If you see **`git version 2.x`**, Git works. If the command is not found, restart the terminal or reboot once and try again.

#### 3) GitHub sign-in (assumed)

These instructions assume you **already have a GitHub account** and can **open this README** in the repo in your browser (you have access to the project). On a new machine, sign in at **[github.com/login](https://github.com/login)** if prompted. The next sections (**3a** / **3b**) only set up **HTTPS** or **SSH** so **`git clone`** can authenticate — there is no separate onboarding checklist here.

#### 3a) HTTPS: clone with a Personal Access Token (recommended if you skip SSH)

When Git asks for a **password** after **`git clone https://…`**, GitHub expects a **[Personal Access Token](https://github.com/settings/tokens)** (not your GitHub login password).

1. On GitHub: click your avatar → **Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (classic)**.
2. Give it a **Note** (e.g. “laptop”), set an expiry, check the **`repo`** scope → **Generate token**.
3. **Copy the token immediately** (you cannot see it again). Store it in a password manager.
4. Later, when **`git`** asks for **Password**, paste that token.

#### 3b) SSH: optional, avoids pasting passwords after one-time setup

Do this only if you prefer **`git@github.com:…`** clones.

1. Open **Terminal**, **Git Bash**, or Linux shell and run (use your email):
   ```bash
   ssh-keygen -t ed25519 -C "your_email@example.com"
   ```
   Press **Enter** three times to accept defaults and optionally empty passphrase (or set a passphrase for extra safety).

2. Print your **public** key and copy the whole line starting with **`ssh-ed25519`**:

   ```bash
   cat ~/.ssh/id_ed25519.pub
   ```

3. GitHub → **Settings → SSH and GPG keys → New SSH key** → paste → **Add SSH key**.

4. Test:
   ```bash
   ssh -T git@github.com
   ```
   Type **`yes`** if asked **Are you sure you want to continue connecting**. Success looks like **`Hi username! You've successfully authenticated…`**.

Clone URL becomes:
```bash
git clone git@github.com:kirkmalcolmsexton-cloud/outreach.git
```

#### 4) Virtualization for the Android Emulator

The emulator runs a **full virtual Android device**. Your PC must expose hardware virtualization.

- **Windows**
  1. Restart the PC and enter **BIOS/UEFI** (often **F2**, **Del**, **F10**, **Esc** during boot — search “{your laptop model} BIOS key” if unsure).
  2. Find **Intel VT-x**, **Intel Virtualization Technology**, **AMD-V**, or **SVM Mode** → set **Enabled**. **Save and exit**.
  3. After Windows boots: **Settings → Privacy & Security → Windows Security → Device security → Core isolation details** — if **Memory integrity** breaks the emulator, follow [Google’s emulator acceleration guide](https://developer.android.com/studio/run/emulator-acceleration).
  4. **Settings → Apps → Optional features → Related settings → More Windows features** → turn on **Windows Hypervisor Platform** / **Virtual Machine Platform** if your Studio install recommends them → restart.

- **macOS** — Usually nothing to enable; use the latest Android Studio.

- **Linux** — Install **KVM** and add your user to **`kvm`** (Ubuntu example):
  ```bash
  sudo apt install qemu-kvm libvirt-daemon-system bridge-utils
  sudo adduser "$USER" kvm
  ```
  Log out and back in. Details vary by distro — see **[Run with hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration)**.

#### 5) Create a folder for the clone

Avoid long paths with spaces (**`C:\My Stuff\…`** can cause Gradle pain on Windows).

**macOS / Linux:**
```bash
mkdir -p ~/projects
cd ~/projects
```
You will run **`git clone`** here so you get **`~/projects/outreach`**.

**Windows PowerShell:**
```powershell
mkdir C:\dev\projects -Force | Out-Null
cd C:\dev\projects
```
You will clone into **`C:\dev\projects\outreach`**.

Stay in this folder when you reach **[1. Clone this repository](#1-clone-this-repository)** below.

On a **typical home Wi‑Fi or Ethernet setup**, you do **not** need HTTP proxy settings for **Git** or **Android Studio**. Leave proxy fields empty unless something unusual on your network (for example a school or employer) tells you otherwise.

---

When finished, go to **[1. Clone this repository](#1-clone-this-repository)**.

### 1. Clone this repository

You already installed **Git** in **Before you clone**. Open a terminal, **`cd`** to the parent folder you created in **Before you clone** (for example **`~/projects`** or **`C:\dev\projects`**), then run **one** of:

**HTTPS:**

```bash
git clone https://github.com/kirkmalcolmsexton-cloud/outreach.git
cd outreach
```

**SSH** (only if you completed **Before you clone → 3b**):

```bash
git clone git@github.com:kirkmalcolmsexton-cloud/outreach.git
cd outreach
```

### 2. Install Android Studio (all platforms)

1. **Download** the installer: **[developer.android.com/studio#downloads](https://developer.android.com/studio#downloads)** — pick **Windows**, **Mac**, or **Linux** (on Mac, match **Apple Silicon** vs **Intel** if both are listed).

2. **Install**
   - **Windows:** Run **`android-studio-*.exe`** → leave **Installation Type** as **Standard** unless your team says otherwise → **Next** → accept SDK path (default **`%LOCALAPPDATA%\Android\Sdk`** is fine) → **Install** → **Finish** → leave **Start Android Studio** checked → **Finish**.
   - **macOS:** Open **`.dmg`** → drag **Android Studio** into **Applications** → open **Android Studio** from **Applications**. If Gatekeeper blocks it: **System Settings → Privacy & Security → Open Anyway**.
   - **Linux:** Extract **`.tar.gz`**, enter the folder, run **`bin/studio.sh`** (exact steps depend on distro; Google’s page lists alternatives).

3. **First launch wizard:** **Do not import settings** → **Next** → **Standard** setup → accept licenses → wait for downloads. When asked for JDK, choose **JDK 17** if offered, otherwise accept **Embedded JDK** (**17**).

4. **SDK Platforms:** **File → Settings** (Linux/Windows) or **Android Studio → Preferences** (macOS, or press **Cmd + ,**) → **Languages & Frameworks → Android SDK → SDK Platforms** tab → enable **Android 14.0 (“UpsideDownCake”) — API Level 34** → **Apply**.

5. **SDK Tools:** same dialog → **SDK Tools** tab → ensure checked: **Android SDK Build-Tools**, **Android Emulator**, **Android SDK Platform-Tools** → **Apply** → **OK**.

### 3. Match your OS (terminal + scripts)

Use this table so later steps match how **your** machine runs shells and Gradle:

| OS | Terminal / Gradle | Scripts (`./scripts/*.sh`) |
|----|-------------------|----------------------------|
| **macOS** | **Terminal** (or iTerm). From **`android/`**: **`./gradlew …`**. | Same terminal — **bash**/**zsh** run the helper scripts as written. Default SDK: **`~/Library/Android/sdk`** (Studio usually writes **`sdk.dir`** into **`local.properties`**). |
| **Linux** | Your distro terminal. From **`android/`**: **`./gradlew …`**. Install **OpenJDK 17** if Studio does not bundle one (e.g. Debian/Ubuntu: **`sudo apt install openjdk-17-jdk`**). | Same — **bash** runs **`start-outreach-emulator.sh`**, etc. |
| **Windows** | **PowerShell** or **cmd**: from **`android/`** use **`.\gradlew.bat …`** (not **`./gradlew`**). Install **Git for Windows** so you get **Git Bash** — use **Git Bash** for the **`./scripts/*.sh`** block in step **7**. Typical SDK: **`%LOCALAPPDATA%\Android\Sdk`**. |

**Windows without Git Bash / WSL:** Use Android Studio (**Device Manager**) to start an AVD whose name is **`Galaxy_S938U_API36_x86_64`** (or create it once using the same settings as **`scripts/start-outreach-emulator.sh`**). Then in **PowerShell** run **`.\gradlew.bat`** with **`$env:ANDROID_SERIAL`** set to the serial from **`adb devices`** (often **`emulator-5554`**). For the **full scripted** flow (create AVD + wait + resolve serial), install **Git Bash** or **WSL2** and use the **`bash`** commands in step **7**.

### 4. Open the Android project in Studio

**File → Open…** → select the **`android`** folder inside your clone (contains **`settings.gradle.kts`** — **not** opening only the parent **`outreach`** folder as a generic folder if that confuses Studio). Wait for **Gradle sync** to finish.

### 5. Firebase config (required for Google Sign-In / Firebase)

1. Copy **`android/app/google-services.json.example`** to **`android/app/google-services.json`**.
2. Replace with your real **`google-services.json`** from the Firebase console.  
   - Keep this file **local-only**; do not commit it.

### 6. Sanity check: build

In Studio: **Build → Make Project**.

Or from a terminal:

**macOS / Linux / Git Bash / WSL** (from **`outreach/android`**):

```bash
cd android
./gradlew assembleDebug
```

**Windows PowerShell** (from **`outreach/android`**):

```powershell
.\gradlew.bat assembleDebug
```

If this succeeds, JDK + SDK + Gradle are correct.

### 7. Run UI tests on the default Outreach emulator

Use a **bash** shell (**macOS**, **Linux**, **Git Bash**, or **WSL**) for this block. Working directory: **`outreach/android`**.

**Simplest path:** unplug other Android phones/tablets so only the emulator is connected.

```bash
cd android

./scripts/start-outreach-emulator.sh
./scripts/wait-for-adb-online.sh
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

**Windows PowerShell only** (if you started the emulator from Studio and know the serial, e.g. **`emulator-5554`**):

```powershell
cd android
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

Prefer setting **`ANDROID_SERIAL`** via **`resolve-outreach-emulator-serial.sh`** in **Git Bash** so the port is not hardcoded.

**Success:** Gradle finishes with tests on the emulator. **Failure:** [**CLI quick reference → Troubleshooting**](#troubleshooting) and **`docs/ui-testing.md`**.

### Notes for beginners

- **Android Studio’s “Android” project view** (Project tool window dropdown) shows **`app`**, **`manifests`**, **`java`**, **`res`**. Opening only the repo root in **VS Code / Cursor** shows flat files — use Studio for the Android layout.
- **`local.properties`** with **`sdk.dir=...`** is usually created when you open **`android/`** in Studio.

---

## Physical device testing

Use this after you can build and run tests on the emulator.

### One-time phone setup

1. Enable **Developer options** and **USB debugging** on the phone.
2. Connect USB (or use wireless debugging). Accept **“Allow USB debugging?”**
3. Prefer **Developer options → Stay awake** while charging so the screen does not sleep mid-test.

### Run tests on the phone only

From **`android/`**:

```bash
adb devices -l
# Copy the serial for your phone (hardware id, not emulator-*)

export ANDROID_SERIAL=<your-phone-serial>
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

Or pick a single serial automatically (prefers USB hardware over emulators):

```bash
export ANDROID_SERIAL="$(./scripts/get-device-serial.sh --pick)"
./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

### Multiple devices attached (phone + emulator)

**`connectedDebugAndroidTest`** installs and runs on **every** online device unless you pin one with **`ANDROID_SERIAL`**. Always set **`ANDROID_SERIAL`** when more than one device appears in **`adb devices`** — otherwise installs can fail or run twice.

Align **`ANDROID_HOME`** with **`sdk.dir`** in **`local.properties`** so **`adb`** and Gradle use the same SDK ([**SDK alignment**](#sdk-alignment) in the quick reference).

### Auth in tests

- **`mock`** — forced UI for automation; no Google login required ([**CLI flags**](#instrumentation-cli-flags)).
- **`real`** — uses **Firebase** on the device; sign in manually in the app before tests that expect a logged-in user.

Full automation of Google OAuth from tests is **not** practical for most teams — see [**Automating sign-in**](#automating-sign-in).

---

## CLI quick reference

Assume **`cd outreach/android`** unless noted.

### Gradle (connected tests)

| Task | Purpose |
|------|---------|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew assembleDebugAndroidTest` | Instrumentation test APK |
| `./gradlew connectedDebugAndroidTest` | Install both and run **`src/androidTest`** on connected device(s) |
| `./gradlew assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest` | Common “build everything then test” combo |

Single test class:

```text
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.TestClass
```

### Default Outreach emulator

| Item | Value |
|------|--------|
| **AVD name** | **`Galaxy_S938U_API36_x86_64`** (override with **`OUTREACH_AVD_NAME`** when starting the emulator) |
| **Resolve adb serial for that AVD** | **`export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"`** |

### Scripts

| Script | Purpose |
|--------|---------|
| **`./scripts/start-outreach-emulator.sh`** | Create default AVD if missing; start emulator ([**details**](#start-emulator-without-studio)) |
| **`./scripts/wait-for-adb-online.sh`** | Block until **`adb`** → **`device`** and boot complete; **`ADB_WAIT_TIMEOUT`** / **`ADB_WAIT_INTERVAL`** |
| **`./scripts/resolve-outreach-emulator-serial.sh`** | Print **`adb`** serial for the Outreach default AVD (use with **`ANDROID_SERIAL`**) |
| **`./scripts/get-device-serial.sh`** | Table **`adb devices`**; **`--pick`** one serial; **`--physical`** USB only — **`--help`** |
| **`./scripts/adb_restart.sh`** | **`adb kill-server`** / **`start-server`** (stale adb) |

### Instrumentation CLI flags

Set via **`android/app/build.gradle.kts`** from Gradle **`-P`** properties (preferred over long nested **`android.testInstrumentationRunnerArguments.*`** keys):

| Gradle property | Maps to |
|-----------------|--------|
| **`-PoutreachMockUserEmail=`** | **`outreach.ui_test.mock_user_email`** |
| **`-PoutreachAuthResolution=mock`** or **`=real`** | **`outreach.ui_test.auth_resolution`** |

Examples:

```bash
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellMockSignedInConfigurableEmailTest \
  -PoutreachAuthResolution=mock \
  -PoutreachMockUserEmail='your-account@gmail.com'
```

```bash
export ANDROID_SERIAL="$(./scripts/resolve-outreach-emulator-serial.sh)"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.outreach.app.AppShellMockSignedInConfigurableEmailTest \
  -PoutreachAuthResolution=real \
  -PoutreachMockUserEmail='your-account@gmail.com'
```

With **`real`**, **`AppShellMockSignedInConfigurableEmailTest`** **fails** if there is no Firebase user or email mismatch — not skipped. **`local.properties`** is not used for Gmail unless your app reads it.

### SDK alignment

Use the same SDK for **`adb`** and Gradle:

```bash
export ANDROID_HOME="$(grep '^sdk.dir=' local.properties | cut -d= -f2)"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

### Troubleshooting

| Symptom | What to do |
|---------|------------|
| **No connected devices** | **`adb devices`** must show **`device`**. Start an AVD or plug in the phone (USB debugging authorized). |
| **OFFLINE / device offline / Finished 0 tests** | Emulator still booting or adb stale — **`./scripts/wait-for-adb-online.sh`** or **`./scripts/adb_restart.sh`**. |
| **`Can't find service: package`** on install | Emulator **PackageManager** not ready — cold boot AVD, **`wait-for-adb-online.sh`**, **`sys.boot_completed`** = **`1`**. With **phone + emulator**, set **`ANDROID_SERIAL`**. |
| **`device '<serial>' not found`** | Align **`ANDROID_HOME`** with **`sdk.dir`**, **`./scripts/adb_restart.sh`**, verify **`adb -s SERIAL get-state`** → **`device`**. |
| Flaky **`adb`** | Use **`$ANDROID_HOME/platform-tools/adb`** consistently (avoid mixing SDKs). |

### Automating sign-in

| Approach | Notes |
|----------|--------|
| **`mock`** | No OAuth — typical for CI. |
| **`real`** | **`FirebaseAuth`** on device — sign in manually before tests or reuse session. |
| Custom token / emulator / email-password | Advanced; see **`docs/ui-testing.md`**. |

### Start emulator without Studio

**`./scripts/start-outreach-emulator.sh`** uses **`scripts/outreach-emulator.snapshot.ini`** (Google Play / API **36** / **pixel_9**) and **`avdmanager create`** only if the AVD folder is missing. Requires SDK **cmdline-tools**. **`scripts/android-sdk.env`** sets **`ANDROID_HOME`** (defaults **`~/Library/Android/sdk`** on macOS).

### Project runner script (Cursor skill)

**`.cursor/skills/android-build-tests/scripts/run-build-tests.sh`** — builds Android tests and runs connected tests, or JVM unit tests if no device. Modes: **`emulator`** (default), **`physical`** (`physical` / `device` / `real`).

---

## Cursor workspace

If you use **Cursor** with a **multi-root workspace** that includes Outreach alongside editor meta-config, clone the **`cursor-workspace`** repository **next to** **`outreach`** (same parent folder). Layout, opening **`cursor-workspace.code-workspace`**, and adding more repos are documented there — **start with the README in that repo** (path when cloned as a sibling: **`../cursor-workspace/README.md`**).

Outreach development does **not** require that workspace; you can open **`android/`** in Android Studio or any editor on its own.

---

## Documentation

- **`docs/android-architecture.md`**
- **`docs/sync-and-collab.md`**
- **`docs/release-checklist.md`**
- **`docs/google-oauth-checklist.md`** (Google sign-in / Firebase OAuth troubleshooting)
- **`docs/ui-testing.md`** (UI automation suite, test tags, instrumentation extras)

**OAuth test users:** In the Google Cloud project for your Web client ID (`google_web_client_id`), add accounts under **Test users** while the app is not in production — [Google Auth platform → Audience](https://console.cloud.google.com/auth/audience) or [OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent).

### Test account (manual / login flows)

- Name: **`Test Sexton`**
- Email: **`skbobalima@gmail.com`**
- Recovery phone: **`(512) 818-2688`**
