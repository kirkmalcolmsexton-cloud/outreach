---
name: android-build-tests
description: Run Android app build and test commands with emulator or physical-device modes and fallbacks for local CLI usage. Use when the user asks to run build tests, debug Gradle test failures, or execute Android command-line verification.
---

# Android Build Tests

## Quick Start

Use this skill when running Android tests from CLI so failures are triaged consistently.

1. Choose a **mode** (see below), then run:
   - `bash .cursor/skills/android-build-tests/scripts/run-build-tests.sh`
   - or pass the mode as the first argument: `.../run-build-tests.sh emulator` or `.../run-build-tests.sh physical`
2. Read the command summary from stdout (`Using device: mode=... ANDROID_SERIAL=...`).
3. If it fails, use the emitted stage (`compile` or `tests`) to focus debugging.

## Modes

| Mode | Env / arg | Behavior |
|------|-----------|------------|
| **Emulated** | `ANDROID_BUILD_TESTS_MODE=emulator` or `./run-build-tests.sh emulator` (default) | Picks first `adb` serial matching `emulator-*`. If none is online, tries to boot the first AVD from `emulator -list-avds`. Sets `ANDROID_SERIAL`, then runs `:app:connectedDebugAndroidTest`. |
| **Real device** | `ANDROID_BUILD_TESTS_MODE=physical` or `./run-build-tests.sh physical` | Picks the first **non-emulator** serial in `adb devices` (USB/hardware). Does **not** start an AVD. Sets `ANDROID_SERIAL`, then runs `:app:connectedDebugAndroidTest`. |

Aliases for physical mode: first argument `physical`, `device`, or `real`.

### Override

If **`ANDROID_SERIAL`** is already set in the environment, the script uses that serial and does not apply mode-based auto-pick (useful when multiple devices of the same class are attached).

## What this runner does

- Validates Android test compilation first (`:app:compileDebugAndroidTestKotlin`)
- Resolves a single target device from the chosen mode (or `ANDROID_SERIAL`), then runs `:app:connectedDebugAndroidTest` on **that device only**
- Falls back to `:app:testDebugUnitTest` when no suitable target exists for the selected mode

## Notes

- Run from the repository root (`outreach`) or any directory; the script resolves paths internally.
- With **physical** mode and no phone/tablet connected (only an emulator), the script falls back to unit tests unless you set `ANDROID_SERIAL` yourself.
