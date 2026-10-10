# Validation results

Validated October 10, 2026 on Windows with Java 21, Gradle 8.11.1, compile SDK 35, Build Tools 35.0.0, and the Medium Phone API 37 Android emulator.

## Passed

```text
gradlew.bat :core:test :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest
BUILD SUCCESSFUL
```

- 14 JVM tests passed: relationships, ancestry, sibling behavior, deterministic layout, persistence, v1-to-v2 migration, atomic history/save failures, explicit matching/overwrite, safe remote additions/removals, local conflicts, fresh-preview enforcement, cancellation, review deduplication/superseding/rejection/acceptance, one-time import, and disconnection.
- Seven Compose tests passed on the Android 17/API 37 emulator: tabs and single badge/menu placement, graph pan/fit, add-relative submission, Sharing/Logs, profile/logout/resume, import preview/confirmation and accepting remote updates, and import cancellation.
- Debug app APK built at `app/build/outputs/apk/debug/app-debug.apk` using the supplied local Firebase configuration.
- Lint completed with zero errors and 23 warnings: newer dependency/toolchain suggestions, HTTPS app-link verification setup, and a URI Kotlin-extension suggestion.
- `core/src` contains no Android or AndroidX imports.
- Four Firestore rules tests passed using `npm.cmd test` in `firebase/`, against the local `demo-heritage` emulator: owner-only creation/updates/revocation, authenticated capability reads, denied recipient writes/unauthenticated reads/enumeration, and invalid/oversized document rejection. No production data was written.

The menu regression check also ensures the badge's touch area does not cover the selected avatar center. The Android test dependencies use Espresso 3.7.0 to support Android 17 input APIs.

## Compatibility baseline

Compose BOM 2026.08.00 requires compile SDK 37 and AGP 9.1. BOM 2025.04.01 retains API 35 / AGP 8.9.2 / Kotlin 2.1.20. Firebase BOM 34.0.0 retains this compile SDK baseline.

## Remaining device/service checks

Compose sharing tests inject a remote repository. Production Firebase deployment and end-to-end cross-device sharing have not been performed. Enable Anonymous Authentication, create Firestore, and deploy the provided rules/Hosting page using [Firebase setup](firebase/README.md); then run its two-device acceptance procedure. Hardware QR scanning, Android share-sheet delivery, release HTTPS domain verification, compact/large phone and increased-font screenshots, and manual process-restart persistence remain unverified. JVM serialization/reload covers tree, linked-source metadata, and history persistence.

The Firebase configuration, local SDK, `local.properties`, downloaded tools, and generated build/test artifacts are ignored by Git. Other checkouts need their own Firebase configuration and SDK path.
