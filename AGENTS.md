# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

Android (Kotlin) app — "Long SMS Sender" — single-module Gradle project. No backend, no databases, no Docker. Fully offline (no INTERNET permission).

### Build commands

- Debug build: `./gradlew assembleDebug` (output: `app/build/outputs/apk/debug/app-debug.apk`)
- Release build: `./gradlew assembleRelease` (unsigned unless signing config is added)
- Lint: `./gradlew lint` (pre-existing errors in codebase; exits non-zero)
- Clean: `./gradlew clean`

### Environment

- **JDK 21** is pre-installed and satisfies the JDK 17+ requirement.
- **Android SDK** is installed at `$HOME/android-sdk` with platform 34 and build-tools 34.0.0. The env vars `ANDROID_HOME` and `ANDROID_SDK_ROOT` are set in `~/.bashrc`.
- The Gradle wrapper downloads **Gradle 9.0-milestone-1** automatically on first build.

### Gotchas

- `./gradlew lint` fails with 5 pre-existing errors (e.g. API level 24 calls with minSdk 23). These are **not** regressions — the codebase ships with them.
- No test source files exist (`src/test/`, `src/androidTest/` are empty). The `testInstrumentationRunner` is configured but there are no tests to run.
- This is a mobile app — it cannot be "run" in the cloud agent VM. Build verification (`assembleDebug`) is the primary validation. Running on a device/emulator requires Android Emulator setup (not included in the update script).
- Kotlin compiler warnings about deprecated `SmsManager` APIs are expected and harmless.
