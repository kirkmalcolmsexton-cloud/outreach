# Google OAuth checklist (Outreach Android)

Use this when debugging **403 access_denied**, verification blocks, or Firebase sign-in after Google returns.

## 1. Test users (app in Testing)

- In the Google Cloud project that owns your **Web client ID** (same ID as `google_web_client_id` in `strings.xml`):
  - **Google Auth Platform → Audience → Test users**, or **APIs & Services → OAuth consent screen → Test users**.
- Add every Google account that should be able to sign in while the app is not **In production**.

## 2. Consent scopes

- In the same project, open OAuth **Data access** / **Scopes**.
- The app requests **`drive.file`** and **Sheets** in code, not full **`https://www.googleapis.com/auth/drive`**. Remove full Drive from the consent screen unless you explicitly need it—full Drive increases verification requirements and can confuse debugging.

## 3. Android OAuth client and SHA-1

- **APIs & Services → Credentials**: ensure an **Android** OAuth client exists for `org.outreach.app`.
- Add your **debug** (and **release**) **SHA-1** fingerprints to that client.
- If sign-in fails with **DEVELOPER_ERROR (10)** in-app or Logcat, SHA-1 mismatch is a common cause.

## 4. Firebase `google-services.json`

- Download **`google-services.json`** from [Firebase Console](https://console.firebase.google.com/) → Project settings → Your apps → Android app `org.outreach.app`.
- Replace [`android/app/google-services.json`](../android/app/google-services.json) with that file. It must belong to the **same** Firebase/GCP project as your Google Sign-In Web client and Firebase Auth configuration.
- Alternatively, hydrate that path (and **`MAPS_API_KEY_DEBUG` / `MAPS_API_KEY_RELEASE`** in **`local.properties`**, from the team’s consolidated JSON) using **`android/scripts/setup-secrets.sh`** — see [**README → Firebase config**](../README.md#5-firebase-config-required-for-google-sign-in--firebase).
- Do not commit a real `google-services.json`; this repo commits only [`android/app/google-services.json.example`](../android/app/google-services.json.example) as a template shape.

## 5. What is safe to commit

- Safe to commit:
  - Placeholder/example configs (for example `google-services.json.example`).
  - OAuth/Firebase identifiers intended for client distribution (for example web client IDs and app IDs).
- Do not commit:
  - Real environment-specific config files (`android/app/google-services.json`).
  - Downloaded OAuth client secrets (`client_secret*.json`), service-account keys, keystores, `.env*`, or any private key material.
- If a real credential is committed, rotate/revoke it in Google Cloud/Firebase and remove it from history if required by policy.

## 6. In-app and Logcat

- On failure, the app surfaces **Firebase** error codes/messages when credential sign-in fails.
- Filter Logcat by tag **`OutreachAuth`** (debug builds) for Google and Firebase auth details.
