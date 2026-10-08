# Validation results

Validated October 8, 2026 on Windows with Java 21, Gradle 8.11.1, Android SDK 35, and Build Tools 35.0.0.

## Passed

```text
gradlew.bat :core:test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
BUILD SUCCESSFUL
```

- Seven JVM tests passed; zero failures, errors, or skipped tests.
- Debug app APK built: `app/build/outputs/apk/debug/app-debug.apk`.
- Instrumented test APK built: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- Android lint passed with no errors. Remaining warnings recommend newer dependency versions; the pinned versions intentionally retain the API 35 toolchain.
- Import scan of `core/src` found no `android.*` or `androidx.*` imports.
- Instrumented test sources compile, including tab/menu selection, single-badge positioning, add-relative submission, search/connect selection, pan/fit reset, profile saving, and logout/resume.

## Compatibility correction

The originally planned Compose BOM 2026.08.00 resolved libraries requiring compile SDK 37 and Android Gradle Plugin 9.1. The build's AAR metadata check rejected that combination with API 35 / AGP 8.9.2. BOM 2025.04.01 was substituted; all other planned toolchain versions remain unchanged. The corrected combination compiled and passed lint.

## Device checks still required

`adb devices` reported no connected devices or emulators. Instrumented Compose tests were compiled but not executed. Screenshots, interactive visual acceptance, phone/font-size comparisons, and process-restart persistence checks on Android remain unverified. Persistence serialization/reload, failed writes, and logout/resume were verified by JVM tests.

Connect a device or start an emulator, then run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

For manual acceptance, add relatives using all four actions, connect a discovered person to a selected member, edit profile/privacy/notification preferences, force-stop and relaunch, and confirm those edits persist. Check graph pan/zoom/fit and popover bounds on compact and larger screens with increased font size. Log out and resume without losing the tree.

The local SDK and `local.properties` are ignored by Git; other machines need their own SDK configuration.
