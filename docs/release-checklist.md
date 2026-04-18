# Android Release Checklist

## Secrets and API Setup

- Add local `google-services.json` for the correct Firebase project (do not commit this file).
- Restrict Android API keys by package name and SHA-1.
- Enable Google Sheets API and Drive API in the cloud project.
- Confirm OAuth consent screen and scopes are minimal.
- If any real credential was exposed in git history or logs, rotate/revoke it before release.

## Functional Validation

- Verify sign in and sign out behavior.
- Verify sheet selection, tab filtering, and sync bootstrap.
- Verify map pin rendering and navigation intent launch.
- Verify visit logging works online and offline.
- Verify pending edits are replayed when network returns.
- Verify presence/activity writes to Firestore.

## Failure State Validation

- Network unavailable during sync.
- Google auth token expired.
- Sheet schema mismatch or missing columns.
- Address geocode failures.
