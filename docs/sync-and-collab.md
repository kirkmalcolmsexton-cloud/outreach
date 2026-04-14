# Sync and Collaboration

## Offline Sync

- Visit edits are applied immediately to Room (`households`) for optimistic UX.
- The same edits are written to `pending_sync`.
- A WorkManager task retries sending pending edits to Sheets.
- Conflict policy is `last_write_wins`; unresolved cases should be surfaced as user warnings in a follow-up iteration.

## Collaboration Overlay

- Firestore collections:
  - `presence`: latest tab/location context per user.
  - `activity`: append-only feed of visit and assignment events.
- Collaboration events never overwrite spreadsheet rows directly.

## Operational Checklist

- Keep scopes limited to Google Sheets/Drive file access.
- Validate sheet column headers before sync.
- Log sync failures with enough context to retry safely.
