# BT Notifier 1.1.0 verification

## Checked revisions and environment

Base: `3de9b2a90b59cc14ab750e7ecbc2b4ab92892611`.

Application candidate: `e4a3c603a8b0316209e838ccbe8cfa857f39084f`.

Application tree: `f6ea6437c58d534f275ad021ce52fa6a8857cfa1`.

The candidate builds version 1.1.0, code 3, targeting and compiling against API 37 with minimum API 23. This feature pass adds no dependencies or permissions. Verification used Windows, Temurin Java 17.0.18, Gradle 9.3.1, and the approved Android build stack. Heavy local commands used one worker and a 1 GiB Gradle heap.

## Local checks

1. `testDebugUnitTest`: 51 tests, zero failures, errors, or skips.
2. `lintDebug`: zero errors and 106 warnings.
3. `assembleDebug` and `assembleDebugAndroidTest`: successful.
4. Android instrumentation: 13 tests, zero failures or assumptions skipped, 81.602 seconds. The receipt is `.local-build/runtime-20261023/production-candidate-instrumentation.log`.
5. XML parsing, diff whitespace, version/SDK manifest inspection, and the filtered Pages deployment contract passed. Only `docs/privacy-policy.html` matches the deployment inputs; the workflow itself is unchanged.

The combined build receipt is `.local-build/release-nearby-reset-build.log`. The exact input identity and aggregated results are saved in `.local-build/production-core-verification.json`.

## Signed artifact

The signed release build completed successfully with the existing upload key. The upload file is `.local-build/bt-notifier-1.1.0-api37.aab`.

SHA-256: `0e075a5a9664fd355f7f121646accb723ad8cecb27c6b1038e1af9aec900eba9`.

`jarsigner -verify` returned zero and reported `jar verified`. It also reported the self-signed upload certificate, absent timestamp, ZIP attributes, and the build tool's manifest ordering difference between random-access and streaming JAR readers. The raw output is retained in `.local-build/production-signature-summary.log`. Play's own bundle validation remains a separate release gate. The bundle's optional version-control metadata reports `NO_VALID_GIT_FOUND` in this linked checkout; the clean Git candidate, application tree, build receipt, and artifact hash provide provenance outside that metadata.

## Functional observations

The exclusively reserved emulator ran API 36 in a read-only session, using a private Android Debug Bridge server on port 5041 and serial `emulator-5560`. Physical devices and other projects' processes were untouched. The adapter-shutdown regression temporarily disabled this emulator's Bluetooth and restored it in cleanup.

An in-place update from code 2 and a populated version-3 database to code 3 and database version 4 retained exact watched rows, aliases, hidden choices, added timestamps, last-seen timestamps, and disabled alert choices. The migration adds the observation watermark without replacing existing data. The retained-data comparison is `.local-build/runtime-20261023/in-place-upgrade-proof.json`.

Actual application tests cover all five tabs, Watch List to Finder navigation, an active Finder search stopping with its activity, a real Back-button tap, health diagnostics, explicit resume, service survival after leaving the activity, stop/restart, cancellation without delayed completion or notification revival, adapter shutdown, and fresh Nearby display results without clearing another consumer's observations.

History tests cover additive migration, monotonic sightings independent of alert delivery, alias/hidden retention, bounded storage, CSV escaping, a real Fragment and ContentResolver export through a simulated chosen destination, picker cancellation, cancelled clearing, and confirmed clearing of only owned fixtures. Alias tests exercise both actual view models with stale dialogs and newer stored observations. Routine unconfigured observations create no permanent metadata rows.

Recorded failing regressions precede fixes for permanent metadata retention, Settings restoring an obsolete disabled preference, adapter shutdown, stale alias dialogs, and stale Nearby rows. Their receipts remain under `.local-build/runtime-20261023/`.

## Independent review

A fresh independent review of the application candidate and base above completed full code/documentation, architecture, and functional review with no actionable findings. Earlier independent reviews identified issues that were corrected and covered by regressions before this candidate. The final review independently inspected the completed 13-test Android receipt and the local build evidence.

## Deliberate platform verification exceptions

These exceptions apply to application candidate `e4a3c603a8b0316209e838ccbe8cfa857f39084f` against base `3de9b2a90b59cc14ab750e7ecbc2b4ab92892611`.

1. API 37 execution, real Bluetooth advertising reception and screen-off radio filtering, prolonged operation, physical Wi-Fi eligibility, and real reboot recovery were not run. The available authorized emulator is API 36, and the physical devices remain outside this task's testing scope. Alternative evidence consists of the local policy tests, actual emulator adapter-off handling, service lifecycle tests, API 37 compilation, and code review. Vendor radio behavior, Android 17 runtime changes, battery restrictions, and extended recovery remain risks rather than passing results.
2. The actual Android document picker and third-party document providers were not driven. Tests deliver successful and cancelled picker results to the real Fragment, then verify the real ContentResolver output and data preservation. Provider-specific grants, UI behavior, and interoperability remain unverified; export failures are reported to the user.
3. GitHub CI and hosted review were omitted under the user's explicit local-verification authorization. Local builds/tests and fresh independent review supply the relevant evidence. The changed privacy HTML still requires the existing protected Pages deployment after merge.

Wi-Fi-gated startup deliberately requires visible resume when Android's location-service rules prevent a background start. The app retains the enabled preference and a resume reason and provides an activity-opening notification. This release does not add background-location permission or promise uninterrupted monitoring under every Android restriction.

An evidence-only delivery commit may follow the application candidate. Reuse of application checks requires an unchanged application tree and build inputs, a recorded final candidate/base assessment, and a scoped evidence consistency review. Such reuse does not close the platform gaps above.
