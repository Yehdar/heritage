# Firebase setup for Heritage

The Android app builds without Firebase configuration and keeps local editing/Logs available. Online share links and live-sync reviews require the following setup.

1. In the [Firebase console](https://console.firebase.google.com/), create a project and register Android package `com.app.familytree`.
2. Download `google-services.json` into `app/google-services.json` in the repository root. The file is ignored by Git. Gradle applies Google's configuration plugin only when the file is present.
3. Enable **Authentication → Sign-in method → Anonymous**. Each installation gets an anonymous identity; the local welcome/logout screen is not a Firebase account login. Reinstalling or clearing app data loses ownership of previously shared links. A future account-linking flow is needed for account recovery.
4. Create a **Cloud Firestore** database in production mode.
5. Deploy the included Firestore rules and HTTPS link-opening page. From this directory, using Node 22 LTS:

```powershell
npm.cmd ci
npx.cmd firebase login
npx.cmd firebase deploy --project YOUR_PROJECT_ID --only firestore:rules,hosting
```

For the supplied local configuration, use project ID `heritage-3b105`. Deployment is a separate step; the PR does not automatically publish to your Firebase project.

Share URLs use `https://YOUR_PROJECT_ID.web.app/tree/RANDOM_TOKEN`. Hosting shows an **Open Heritage** link and paste-URL instructions. Installed apps support that HTTPS route and `heritage://tree/RANDOM_TOKEN`; HTTPS auto-verification is not enabled until release signing/domain association is configured. QR codes encode the same HTTPS URL. The scanner uses Google Play services without requesting camera permission; paste a URL when scanning is unavailable.

## Privacy and access

The random 128-bit token is a read capability: anyone holding it and signed in anonymously can retrieve the shared tree. Firestore rules prohibit collection enumeration and writes by recipients. Only the publishing installation can edit or revoke its link. Sharing publishes people/relationship data, excluding device settings, session status, Logs, and internal portrait asset paths. Revocation stops future reads; it cannot erase copies recipients already imported.

The owner publishes a new revision when its tree changes while the app is running. Linked recipients observe revisions while their app process is active; reconnects or **Check updates** retrieve the latest version. Updates are reviewed in Logs, never automatically applied. Intermediate source edits may be combined into the latest revision, and a newer pending review supersedes an older one. This is one-way following, not permission to edit someone else's tree.

## Local rules tests

```powershell
npm.cmd ci
npm.cmd test
```

Tests use the `demo-heritage` Firestore emulator project only. They verify owner creation/revision updates/revocation, capability reads, denied recipient writes, denied unauthenticated reads/collection enumeration, and malformed/oversized documents. No production data is written by this test suite.

## Two-device acceptance

On A, create a share link. On B, paste it or scan A's QR, choose explicit person matches and overwrite/live-sync options, preview, then confirm. Add or remove a relative on A; verify B gets **Changes by [A's name]** in Logs while B's tree stays unchanged. Accept applies validated changes; Reject preserves B's tree. Edit an imported person on B, remove them on A, and verify acceptance reports a local conflict rather than deleting B's work. Pause/resume, disconnect, revoke, and restart both apps to verify persistence and error handling.
