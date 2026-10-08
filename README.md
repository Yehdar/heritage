# Heritage · Family Tree MVP

A local Android family tree built with Kotlin and Jetpack Compose. Three tabs provide a zoomable family graph, demo discovery with explicit relationship selection, and editable local settings.

## Build and run

Install Java 21 and Android SDK API 35 with Build Tools 35.0.0. Open this folder in Android Studio, or set `sdk.dir` in an untracked `local.properties` file. A project-local SDK installed during validation lives in `.android-sdk/` and is ignored by Git.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The last command requires a running emulator or connected Android device. Install `app/build/outputs/apk/debug/app-debug.apk` to try the app. The Gradle wrapper downloads Gradle 8.11.1; dependency downloads require internet access. On macOS/Linux, run `bash gradlew` with the equivalent tasks.

Compatibility correction: the requested Compose BOM 2026.08.00 resolves Compose 1.12 libraries requiring API 37 and AGP 9.1. This project uses BOM 2025.04.01 to retain the requested AGP 8.9.2 / Kotlin 2.1.20 / API 35 baseline.

## Source map

| Location | Responsibility |
| --- | --- |
| `core/.../core/model/Models.kt` | Swift-friendly immutable models and form types |
| `core/.../core/data/` | Repository/storage contracts, versioned JSON, seed data, relationship validation, deterministic graph layout |
| `core/.../feature/{tree,findothers,settings}/` | Pure Kotlin ViewModels and immutable `StateFlow` states |
| `app/.../core/theme/` | Exact beige, espresso, warm grey, brown palette and typography |
| `app/.../core/ui/` | Avatar, heading, error, and relative-form renderers |
| `app/.../feature/` | Stateless feature screens, canvas graph, discovery and settings |
| `app/.../navigation/FamilyTreeApp.kt` | State collection, tab switching, presentation viewport, back behavior, welcome screen |
| `app/.../MainActivity.kt` | Android lifecycle owner and manual dependency composition |
| `app/.../AndroidStorage.kt` | Atomic app-private file persistence on the IO dispatcher |
| `app/src/main/res/drawable/` | Bundled original vector portrait illustrations |
| `core/src/test/`, `app/src/androidTest/` | Core behavior and Compose interaction tests |

`...` denotes `src/main/kotlin/com/app/familytree`. The `:core` module has no Android or AndroidX dependencies/imports. The Android `AppOwner` owns coroutine scopes and cancels them when cleared; feature ViewModels are ordinary Kotlin classes. Route adapters collect their state; screens emit callbacks. Only coordinate transforms and other presentation work occur in the UI.

## Demo behavior

- First launch seeds 11 Doe relatives across four generations. John is selected; the plus badge exposes Parent, Child, Sibling, and Partner actions.
- Add a relative with a name and optional HTTPS avatar URL. Partner forms support an optional year. A sibling shares all known parents; add a parent first when none is known.
- Canonical relationships are directed parent links and symmetric partner links. Duplicate links, self-links, ancestry cycles, and partnerships that prevent a consistent generation layout are rejected.
- Pan the graph with a drag, pinch to zoom, and press **Fit tree** to reset the viewport. Selection determines the anchor for discovery connections.
- Search Sarah Doe, Michael Doe, Elizabeth Hill, or `DOE-1999`. **Connect** asks for the person's relationship to the selected member; saving adds that person to the tree. Cancel does not change data.
- Profile changes update John in the tree. Privacy and notification toggles persist; Tree Access lists local family members and the demo code.
- Log Out preserves data and opens the local welcome screen. Continue restores the session. There is no remote authentication, invitation/access enforcement, discovery service, or notification delivery.
- Saved JSON lives in app-private `files/family-tree.json`. Writes are atomic and serialized. UI data publishes only after successful writes. Invalid or future-version files are preserved and produce a retryable error. Uninstalling/clearing app storage deletes local data; backups are disabled for this demo.

External avatars load using Coil; when unavailable, initials remain visible. Seed portraits work offline. The bundled illustrations replace photographic references because no reference design images were supplied.

## Validation

Core tests cover relationship creation and validation, graph stability, persistence and failed writes, corrupt/future files, search/code matching, cancellation/duplicate connections, profile edits, and session restoration. Instrumented Compose tests cover navigation, the single add badge and its menu, form submission, connection selection, profile saving, and logout/resume.

An Android emulator/device is needed for interactive screenshot review, process-restart checks, large-font/compact-screen review, and execution of Compose tests. Build and test results from this implementation are recorded in `VALIDATION.md`.
