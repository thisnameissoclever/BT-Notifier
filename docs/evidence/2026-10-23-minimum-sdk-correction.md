# Android 7 minimum for Play automatic protection

The user approved raising the minimum supported Android version to Android 7/API 24 while retaining Play automatic protection after the code 3 bundle was rejected. The replacement is version 1.1.0, code 4, with compile and target API 37. Android 6 installations cannot receive this update.

Base: `9a732ef748fc0f53aada7b762c57cc16f513232b`.

Checked application candidate: `cf63fbb42bdc768c63b2d79a73a1606d63c94ca9`.

## Verification

1. Local unit tests were explicitly rerun with `:app:testDebugUnitTest --rerun`: 51 tests, zero failures, errors, or skips. Receipt: `.local-build/min24-unit-fresh.log`.
2. Lint completed with zero errors and 107 warnings. Debug and Android test APK assembly succeeded. Receipt: `.local-build/min24-build.log`.
3. All 13 Android instrumentation tests passed in 127.408 seconds without assumption skips. Receipt: `.local-build/min24-runtime/instrumentation.log`.
4. An in-place update from code 2/schema 3 to code 4/schema 4 retained exact watched rows, aliases, hidden choices, and existing timestamps. The new observation watermark was seeded correctly. Receipt: `.local-build/min24-runtime/in-place-upgrade-proof.json`.
5. Packaged APK inspection confirms code 4, version 1.1.0, minimum API 24, and target API 37.
6. The signed release build succeeded. `jarsigner -verify` returned zero and reported `jar verified`. A separate JarFile check fully read and cryptographically verified all 956 payload entries with one consistent upload certificate and no duplicate paths. Signature warnings remain recorded in `.local-build/min24-signature.log`.

Bundle: `.local-build/bt-notifier-1.1.0-code4-min24-api37.aab`.

SHA-256: `d6be93a7d3faa9bfc24aaaeba9835cc9b6bf760014b65fdf92d4ce39e5e49574`.

Upload certificate SHA-256: `f3fced42b62ce027c35e19241813294b5f9d267867125b99fd4e8fd1e291d97b`.

## Review and scope

A fresh independent review of the application candidate against the base found no actionable correctness, specification, or documentation issues. A separate architecture stage is inapplicable: the correction changes compatibility/version metadata and documentation, with no runtime source, dependency, permission, or interface changes.

The existing runtime policies and features remain as verified in the [feature release evidence](2026-10-23-feature-release-verification.md). The default store description was prepared with those features and the Android 7 minimum for the same release review. Play acceptance and production submission require separate Console confirmation after merge.

## Platform exceptions

For candidate `cf63fbb42bdc768c63b2d79a73a1606d63c94ca9` against base `9a732ef748fc0f53aada7b762c57cc16f513232b`, the available authorized emulator ran API 36. Execution on API 24 and API 37, real Bluetooth reception and screen-off radio filtering, prolonged operation, actual reboot recovery, physical Wi-Fi eligibility, and real document-provider picker interoperability were not tested. Alternative evidence includes the fresh unit run, packaged metadata inspection, successful local native builds, all 13 emulator tests, the actual in-place database upgrade, and independent review. These results do not prove the untested platform outcomes.

The task-owned read-only emulator and private Android Debug Bridge server were stopped and the reservation released after verification. Other device servers and physical devices were untouched. No dependency or protection setting was changed.
