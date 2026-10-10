# Heritage · Family Tree MVP

A Kotlin/Jetpack Compose family tree with four tabs: **My Tree**, **Sharing**, **Settings**, and **Logs**. Share a URL/QR, preview an incoming tree, explicitly match relatives, choose overwrite/live-sync preferences, and review linked-tree updates before accepting them.

## Build and run

Install Java 21 and Android SDK API 35 with Build Tools 35.0.0. Open this folder in Android Studio, or set `sdk.dir` in an untracked `local.properties` file. A project-local SDK installed during validation lives in `.android-sdk/` and is ignored by Git.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The last command requires a running emulator or connected Android device. Install `app/build/outputs/apk/debug/app-debug.apk` to try the app. The Gradle wrapper downloads Gradle 8.11.1; dependency downloads require internet access. On macOS/Linux, run `bash gradlew` with the equivalent tasks.

Online sharing uses Firebase Anonymous Authentication and Cloud Firestore. Put your Android `google-services.json` in `app/` and follow [Firebase setup](firebase/README.md) to enable authentication and deploy the rules/HTTPS link-opening page. The configuration file is ignored by Git. Without configuration, local tree editing and Logs still work; sharing shows a setup message.

Compatibility correction: the requested Compose BOM 2026.08.00 resolves Compose 1.12 libraries requiring API 37 and AGP 9.1. This project uses BOM 2025.04.01 to retain the requested AGP 8.9.2 / Kotlin 2.1.20 / API 35 baseline.

## Source map

| Location | Responsibility |
| --- | --- |
| `core/.../core/model/Models.kt` | Swift-friendly immutable models and form types |
| `core/.../core/data/` | Storage/sharing contracts, JSON migration, audit history, merge/three-way sync, relationship validation, graph layout |
| `core/.../feature/{tree,sharing,settings,logs}/` | Pure Kotlin ViewModels and immutable `StateFlow` states |
| `app/.../core/theme/` | Exact beige, espresso, warm grey, brown palette and typography |
| `app/.../core/ui/` | Avatar, heading, error, and relative-form renderers |
| `app/.../feature/` | Stateless tree, sharing/import, settings, and Logs renderers |
| `app/.../sharing/` | Firebase adapter and QR generation |
| `app/.../navigation/FamilyTreeApp.kt` | State collection, tab switching, presentation viewport, back behavior, welcome screen |
| `app/.../MainActivity.kt` | Android lifecycle owner and manual dependency composition |
| `app/.../AndroidStorage.kt` | Atomic app-private file persistence on the IO dispatcher |
| `app/src/main/res/drawable/` | Bundled original vector portrait illustrations |
| `core/src/test/`, `app/src/androidTest/` | Core behavior and Compose interaction tests |
| `firebase/` | Firestore rules/tests, Hosting link page, and deployment instructions |

`...` denotes `src/main/kotlin/com/app/familytree`. The `:core` module has no Android or AndroidX dependencies/imports. The Android `AppOwner` owns coroutine scopes and cancels them when cleared; feature ViewModels are ordinary Kotlin classes. Route adapters collect their state; screens emit callbacks. Only coordinate transforms and other presentation work occur in the UI.

## Demo behavior

- First launch seeds 11 Doe relatives across four generations. John is selected; the plus badge exposes Parent, Child, Sibling, and Partner actions.
- Add a relative with a name and optional HTTPS avatar URL. Partner forms support an optional year. A sibling shares all known parents; add a parent first when none is known.
- Canonical relationships are directed parent links and symmetric partner links. Duplicate links, self-links, ancestry cycles, and partnerships that prevent a consistent generation layout are rejected.
- Pan the graph with a drag, pinch to zoom, and press **Fit tree** to reset the viewport. Selection determines the anchor for imported profiles.
- Sharing replaces name search and suggestions. Create a share link, send its URL, show its QR, paste a received link, or scan a QR using Google Play services.
- Import asks whether to keep or replace explicitly matched people's details and whether to follow future changes. Matching is never inferred from a name. When the shared profile is new, choose its relationship to the selected local member. Preview and confirm before any import changes data; cancel preserves the tree.
- Overwrite updates matching names/photos and matching relationship details, preserving local-only relatives. One-time imports keep a source record without observing changes. Live sync follows that source one-way while the app is running; **Check updates** fetches its latest revision.
- **Logs** records successful additions/removals, profile/preferences/session changes, share-link creation/revocation, imports, sync controls, and review decisions. Incoming updates appear as **Changes by [name]** with Accept/Reject. A newer revision supersedes an older pending review. Reject preserves local data; accept validates the merge atomically.
- Synced removals only delete source-imported, unedited people/relationships. Pre-existing matched relatives and local-only work stay. Conflicting local edits prevent acceptance and produce an explanation. Reject the proposal or resolve those edits before retrying. Disconnect preserves imported relatives.
- Select a relative and choose **Remove selected relative** to delete them and their links after confirmation. Your own profile cannot be removed. Deletion is logged and published to followers for review when a sharing link is active.
- Profile changes update John in the tree. Privacy and notification preferences persist; Tree Access lists local family members, with links managed in Sharing.
- Log Out preserves data and pauses online publishing/following until you resume. The local welcome screen is separate from the anonymous Firebase installation identity. There is no public discovery or notification delivery.
- Saved JSON lives in app-private `files/family-tree.json`. Version 1 data migrates to version 2 with a stable tree identity, linked-source metadata, and Logs, preserving relatives and settings. No history is invented for earlier edits. Writes are atomic and serialized; failed saves do not publish data or history. Invalid or future-version files are preserved with a retryable error. Uninstalling/clearing storage removes local data and the anonymous sharing identity; backups are disabled.

External avatars load using Coil; when unavailable, initials remain visible. Seed portraits work offline. The bundled illustrations replace photographic references because no reference design images were supplied.

## Validation

Core tests cover relationships, layout, persistence/migration and failed writes, explicit merge matching, overwrite policy, safe source additions/removals, local conflicts, pending-review deduplication/superseding, cancel/reject/accept, one-time imports, and disconnection. Compose tests cover tabs, forms, Logs, import confirmation/cancellation, pending acceptance, profile saving, and logout/resume. Firestore emulator tests cover read capabilities, owner-only writes, revocation, and denied enumeration.

An Android emulator/device is needed for Compose tests and visual review. A configured/deployed Firebase project and two installations are needed for cross-device acceptance. See [validation results](VALIDATION.md) for completed checks and remaining limits.
