# Developer onboarding

Audience: contributors setting up a machine to build and test Outreach locally.

After machine setup, follow **[Testing process](testing-process.md)** for how we validate changes, **[Testing guide](testing-guide.md)** for commands and devices, **[Build process](build-process.md)** for artifacts and secrets wiring, and **[`ci-cd.md` → CLI quick reference](ci-cd.md#cli-quick-reference)** for Gradle shortcuts and **`gh`** aligned with Actions.

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

**Recommended — consolidated secrets file**

The app expects a real **`google-services.json`** under **`android/app/`** and Map keys **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** in **`android/local.properties`** (Gradle `debug` / `release` build types; see **`android/app/build.gradle.kts`**). A single legacy **`MAPS_API_KEY`** still works for both variants when the dual properties are unset (e.g. CI). Teams can distribute **one passphrase-encrypted JSON** instead of copying files by hand.

1. Install **`age`** and **`jq`** (needed by the helper scripts below):
   - **macOS:** `brew install age jq`
   - **Ubuntu / Debian:** `sudo apt install age jq`
   - **Windows:** use **Git Bash** or **WSL** to run the **`bash`** scripts (same installs via your package manager inside WSL).

2. Obtain **`outreach-secrets.json.age`** from your team (and the passphrase). Do **not** commit the passphrase.

3. Put the file at **`secrets/outreach-secrets.json.age`** next to **`secrets/outreach-secrets.example.json`**, **or** pass its path explicitly.

4. From **`outreach/android`**, decrypt and write the gitignored outputs:

```bash
export OUTREACH_SECRETS_PASSPHRASE='your-shared-passphrase'
./scripts/setup-secrets.sh
```

You can point at a specific file with **`./scripts/setup-secrets.sh /path/to/outreach-secrets.json.age`** or **`OUTREACH_SECRETS_FILE`**.

This writes **`app/google-services.json`** and **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** into **`local.properties`** without removing **`sdk.dir`**.

**Maintainers — create or refresh the encrypted file**

Assemble plaintext JSON with the same shape as **`secrets/outreach-secrets.example.json`** (schema: **`docs/outreach-secrets.schema.json`**). Include the full Firebase **`google_services`** object from your downloaded **`google-services.json`**, a dev/feature Maps key as **`development_api_key`**, and a release Maps key as **`release_api_key`**.

```bash
# From repo root — copy and edit secrets/outreach-secrets.json (gitignored)
./android/scripts/encrypt-secrets.sh \
  -i secrets/outreach-secrets.json \
  -o secrets/outreach-secrets.json.age
```

Set **`OUTREACH_SECRETS_PASSPHRASE`** for non-interactive encryption (requires **`expect`**, included on macOS; Linux: **`sudo apt install expect`**).

**Extract plaintext `outreach-secrets.json` from `outreach-secrets.json.age`**

Use **`age`** with the same passphrase you use for encryption (install **`age`** as in step 1). From the **`outreach`** repo root:

```bash
age -d -o secrets/outreach-secrets.json secrets/outreach-secrets.json.age
```

You will be prompted for the passphrase; **`secrets/outreach-secrets.json`** is **gitignored** so it is not committed after editing. To print JSON to the terminal only (no output file):

```bash
age -d secrets/outreach-secrets.json.age
```

Headless decrypt (scripted/CI) uses **`OUTREACH_SECRETS_PASSPHRASE`** and **`expect`** the same way as **`android/scripts/setup-secrets.sh`** and **`encrypt-secrets.sh`**; for day-to-day editing, prefer an interactive terminal and a local **`secrets/outreach-secrets.json`** you delete or re-encrypt when done.

**Fallback — manual Firebase download**

If you do not use the consolidated file:

1. Copy **`android/app/google-services.json.example`** to **`android/app/google-services.json`**.
2. Replace with your real **`google-services.json`** from the Firebase console.
3. After **`setup-secrets`**, use **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** in **`local.properties`**, or a single legacy **`MAPS_API_KEY`** in **`~/.gradle/gradle.properties`** (see **`android/gradle.properties`**).  
   - Keep these files **local-only**; do not commit them.
   - **Routing / arrival times** on the map use the **Directions API** with the same key: in Google Cloud, enable **Directions API** for the project, keep **billing** on for Maps Platform, and under the key’s **API restrictions** allow **Directions API** (not only Maps SDK for Android).

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

### 6b. Terminal build with `~/etc/outreach.env` (team `.age` file)

Use this when your team shares **`secrets/outreach-secrets.json.age`** and a passphrase — one script decrypts to **`secrets/outreach-secrets.json`**, runs **`android/scripts/setup-secrets.sh`** (writes **`app/google-services.json`** and Map keys in **`local.properties`**), then **`assembleDebug`**.

1. **Ask your admin** for the shared passphrase **`OUTREACH_SECRETS_PASSPHRASE`** (same value used to encrypt the **`.age`** file). Treat it like a password: do not paste it into tickets, chat logs, or the repo.

2. **Create** **`~/etc/outreach.env`** on your machine (**macOS / Linux / Git Bash / WSL** — not PowerShell). Example:

   ```bash
   mkdir -p ~/etc
   nano ~/etc/outreach.env   # or vim, VS Code, etc.
   ```

   Minimum content (use your real passphrase from the admin):

   ```bash
   # Outreach — local only. chmod 600 this file.
   export OUTREACH_SECRETS_PASSPHRASE='paste-passphrase-from-admin-here'
   ```

   Lock down permissions:

   ```bash
   chmod 600 ~/etc/outreach.env
   ```

   **Windows:** Use **Git Bash** so **`~/etc`** resolves under your profile the same way as other **`scripts/*.sh`** steps in this README.

3. Install **`age`**, **`jq`**, and **`expect`** if you have not already ([**Firebase config → step 1**](#5-firebase-config-required-for-google-sign-in--firebase); **`expect`** is standard on macOS; **Ubuntu:** **`sudo apt install expect`**).

4. Ensure **`secrets/outreach-secrets.json.age`** is present in your clone (from the team).

5. From the **`outreach`** repo root (**parent** of **`android/`**), run:

   ```bash
   chmod +x ./scripts/build-with-secrets-from-env.sh   # once
   ./scripts/build-with-secrets-from-env.sh
   ```

   The script **errors** if **`~/etc/outreach.env`** is missing, if **`OUTREACH_SECRETS_PASSPHRASE`** is empty after sourcing it, or if decrypt fails (wrong passphrase or missing **`.age`**).

This is equivalent to manually running **`age -d`**, **`setup-secrets.sh`**, and **`./gradlew assembleDebug`**, but wired for automation.

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

**Success:** Gradle finishes with tests on the emulator. **Failure:** **[`testing-guide.md` → Troubleshooting](testing-guide.md#troubleshooting-tests-and-devices)** and **`docs/ui-testing.md`**.

### Notes for beginners

- **Android Studio’s “Android” project view** (Project tool window dropdown) shows **`app`**, **`manifests`**, **`java`**, **`res`**. Opening only the repo root in **VS Code / Cursor** shows flat files — use Studio for the Android layout.
- **`local.properties`** with **`sdk.dir=...`** is usually created when you open **`android/`** in Studio.
- Team **`.age`** secrets: put the passphrase only in **`~/etc/outreach.env`** (never in the repo). Use **[**§6b**](#6b-terminal-build-with-etcoutreach-env-team-age-file)** to decrypt, materialize Firebase/Maps files, and **build** in one command.

