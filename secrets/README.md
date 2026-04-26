# Outreach secrets layout

This folder holds **templates**, **committed ciphertext**, and documents **where sensitive values originate**, **where they are consumed**, and **how operators move them safely**.

For full release checklists and GitHub UI steps, see **[`docs/release-process.md`](../docs/release-process.md)** and **[`docs/github-actions-secrets.md`](../docs/github-actions-secrets.md)**.

---

## What lives here

| Path | In git? | Role |
|------|---------|------|
| **`outreach-secrets.example.json`** | Yes | Shape-only template — safe to browse; replace placeholders with real values in a **local** copy (see below). |
| **`outreach-secrets.json`** | **No** (gitignored) | Your **plaintext** consolidated secrets while editing. Never commit. |
| **`outreach-secrets.json.age`** | Yes (operators commit updates) | **Passphrase-encrypted** JSON shared with the team; CI and scripts decrypt with **`OUTREACH_SECRETS_PASSPHRASE`**. |
| **`upload-keystore.jks.age`** | Yes (when using repo-mode signing) | **Passphrase-encrypted** upload keystore — **same passphrase** as **`outreach-secrets.json.age`**. Plaintext **`.jks`** is gitignored (`*.jks`). |

Schema for consolidated JSON (including optional **`android_upload_signing`**): **[`docs/outreach-secrets.schema.json`](../docs/outreach-secrets.schema.json)**.

---

## Where values come from

| Kind | Typical source |
|------|----------------|
| **`development_api_key`** / **`release_api_key`** | Google Cloud / Maps Platform — API keys restricted by package + signing cert per environment. |
| **`google_services`** | Firebase — download **`google-services.json`** (Android) or mirror its structure into the consolidated JSON (see example). |
| **`google_service_info_plist`** / **`google_service_info_plist_base64`** (optional) | iOS—**`ios/scripts/setup-secrets.sh`** writes **`ios/Outreach/Outreach/GoogleService-Info.plist`** (gitignored). Prefer **base64** in JSON: `base64 -i GoogleService-Info.plist \| tr -d '\n'`. Or download the plist by hand; see [`docs/ios-onboarding.md`](../docs/ios-onboarding.md). |
| **`android_upload_signing`** (repo mode) | Values must match your **upload keystore**: alias and passwords from when the **`.jks`** was created (**[`android/scripts/create-upload-keystore-and-gh-secrets.sh`](../android/scripts/create-upload-keystore-and-gh-secrets.sh)** or Android Studio / **`keytool`**). |
| Upload keystore bytes (repo mode) | Generated locally; only the **`.age`** ciphertext is committed. Encrypt with **`android/scripts/encrypt-upload-keystore-age.sh`** (defaults: read **`~/.config/outreach/upload-keystore.jks`**, write **`secrets/upload-keystore.jks.age`**). |
| **`OUTREACH_SECRETS_PASSPHRASE`** (GitHub Actions) | Chosen by the team — one passphrase decrypts **`outreach-secrets.json.age`** and **`upload-keystore.jks.age`** when using the same passphrase for both encryptions. Stored only as a **repository secret**, never in git. |

---

## Where secrets go (consumers)

### Local development

1. Obtain **`outreach-secrets.json.age`** (and passphrase) out of band from your team.
2. Decrypt or place plaintext **`secrets/outreach-secrets.json`** if you maintain it locally (never push plaintext).
3. Run **`bash android/scripts/setup-secrets.sh`** — it writes **`android/app/google-services.json`** and **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** into **`android/local.properties`** (Gradle picks by build type; see **`android/app/build.gradle.kts`**).  
4. (iOS) Run **`bash ios/scripts/setup-secrets.sh`** when your consolidated JSON includes **`google_service_info_plist_base64`** or **`google_service_info_plist`** — it writes **`ios/Outreach/Outreach/GoogleService-Info.plist`**. The same **`.age`** or **`outreach-secrets.json`** file is used (same search order as Android’s script).  
   Terminal automation with **`~/etc/outreach.env`**: **[`docs/developer-onboarding.md`](../docs/developer-onboarding.md)** (§6b).

### CI (release bundle)

**[`android-release-build.yml`](../.github/workflows/android-release-build.yml)** on **`release/**`** / **`hotfix/**`** or **`workflow_dispatch`**:

1. Reads **`OUTREACH_SECRETS_PASSPHRASE`** from GitHub.
2. Runs **`build-release-bundle.sh`**: **`setup-secrets.sh`** (same as local) + export path for signing **`jq`**, then keystore and **`bundleRelease`**.
3. Resolves **repo mode** vs **legacy** signing (see **`docs/github-actions-secrets.md`**): decrypt **`upload-keystore.jks.age`** or use **`ANDROID_UPLOAD_*`** secrets + base64 keystore.
4. Runs Gradle **`bundleRelease`** with **`ANDROID_UPLOAD_*`** environment variables.

Fork PRs do **not** receive repository secrets — release signing jobs are not meant for untrusted forks.

---

## How to get secrets “there” (operators)

### 1. Edit consolidated JSON (plaintext)

1. Copy **`outreach-secrets.example.json`** → **`secrets/outreach-secrets.json`** if you are bootstrapping (or edit your existing plaintext file).
2. Fill real Maps keys, **`google_services`**, optional **iOS plist** keys (**`google_service_info_plist_base64`** or **`google_service_info_plist`**), and optionally **`android_upload_signing`** per schema.

### 2. Encrypt and commit **`outreach-secrets.json.age`**

From repo root:

```bash
OUTREACH_SECRETS_PASSPHRASE='your-shared-passphrase' bash android/scripts/encrypt-secrets.sh
```

Commit **`secrets/outreach-secrets.json.age`**. Do **not** commit **`secrets/outreach-secrets.json`**.

### 3. Add or rotate **`upload-keystore.jks.age`** (repo-mode signing)

After you have a plaintext upload **`.jks`** (default **`~/.config/outreach/upload-keystore.jks`** unless overridden):

```bash
OUTREACH_SECRETS_PASSPHRASE='same-passphrase-as-json.age' bash android/scripts/encrypt-upload-keystore-age.sh
```

Commit **`secrets/upload-keystore.jks.age`**. Ensure **`android_upload_signing`** in plaintext JSON matches that keystore before re-encrypting **`outreach-secrets.json.age`**.

### 4. Configure GitHub

- **`OUTREACH_SECRETS_PASSPHRASE`** — required for release bundle (Settings → Secrets and variables → Actions).
- **Legacy only:** **`ANDROID_UPLOAD_*`** four secrets — omit once **repo mode** is fully adopted and verified; see **`docs/github-actions-secrets.md`**.

---

## Related scripts

| Script | Purpose |
|--------|---------|
| **`android/scripts/setup-secrets.sh`** | Materialize **`google-services.json`** + **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** from plaintext or **`.age`**. |
| **`ios/scripts/setup-secrets.sh`** | Materialize **`ios/Outreach/Outreach/GoogleService-Info.plist`** from the same consolidated JSON (optional iOS fields). |
| **`ios/scripts/build.sh`** | Command-line **xcodebuild** (build, clean, test, archive) for the iOS app. |
| **`android/scripts/encrypt-secrets.sh`** | Produce **`outreach-secrets.json.age`** from plaintext JSON. |
| **`android/scripts/decrypt-age-passphrase.sh`** | Headless **`age -d`** (used by **`setup-secrets.sh`** and CI). |
| **`android/scripts/encrypt-upload-keystore-age.sh`** | Encrypt upload keystore → **`secrets/upload-keystore.jks.age`**. |
| **`android/scripts/create-upload-keystore-and-gh-secrets.sh`** | Create upload keystore; **`OUTREACH_SIGNING_REPO_MODE=1`** writes repo ciphertext instead of **`gh secret set`**. |

---

## Safety rules

- Treat **`OUTREACH_SECRETS_PASSPHRASE`** like a master password — same blast radius as the ciphertext it unlocks.
- Never commit plaintext **`*.jks`**, **`outreach-secrets.json`**, or **`android/keystore.properties`** (see **`.gitignore`**).
- Prefer password managers and encrypted backups for local keystore copies; GitHub only stores automation secrets you explicitly configure.
