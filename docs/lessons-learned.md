# Lessons Learned

## 2026-10-23: Observe adapter shutdown independently of scan callbacks

1. What happened: Turning Bluetooth off during a scan left a stale active-transport flag, and monitoring recorded a successful completion.
2. Root cause: Android can remove Bluetooth Low Energy scan registrations without calling the scan-failure callback. Registration flags alone did not establish current adapter availability.
3. Prevention rule: Observe adapter state changes independently of discovery callbacks. Invalidate generations and interrupt existing leases on shutdown. Check live permissions and adapter availability while atomically finishing a lease. Restoring Bluetooth must not erase an old lease's interruption.
4. Verification: On an exclusively owned disposable emulator, disable Bluetooth during an active cycle and verify blocked state, cleared transport flags, and unchanged completion statistics. Restore Bluetooth before cleanup and verify a fresh start separately.

## 2026-10-23: Resolve alias edits against current stored observations

1. What happened: Saving an alias dialog opened before a newer detection could restore the dialog's older actual device name.
2. Root cause: Alias edits trusted an untimestamped dialog snapshot when device metadata lacked a timestamp, even though the watched row contained a newer observation.
3. Prevention rule: Resolve actual names and their timestamps from current database rows inside the alias transaction. Carry the newest known timestamp into metadata. Use captured dialog text only when no authoritative stored observation exists.
4. Verification: Exercise both Nearby and Watch List view models with a stale dialog, a newer watched observation without metadata, and newer metadata than the watched row. Save and clear the alias, then verify names, timestamps, and hidden choices remain intact.

## 2026-10-23: Restore settings views before reading authoritative preferences

1. What happened: Returning to Settings after resuming monitoring restored an old unchecked switch and persisted that value, stopping the service again.
2. Root cause: A fast preferences read completed in `onViewCreated` before Android restored saved widget state. The restored switch then invoked its already-active change listener.
3. Prevention rule: Suppress persistence listeners during view restoration. Load authoritative preferences after `onViewStateRestored` so saved widget state cannot overwrite current settings.
4. Verification: Visit Settings with monitoring disabled, resume monitoring elsewhere, then return. Verify that both the persisted preference and switch remain enabled until the user turns monitoring off.

## 2026-10-23: Synchronize test input with Android task transitions

1. What happened: Finder's Back assertion repeatedly failed after an activity stop/resume transition, although the real button listener remained attached.
2. Root cause: Espresso 3.5.1 injected coordinates before the API 36 task transition finished. Android's InputDispatcher logged `No new touched window` for the attempted tap. The application never received the input.
3. Prevention rule: Use the public instrumentation input method for a real tap after task transitions when the installed test library lacks Android's window/input synchronization. Keep actual button and navigation assertions. Do not replace the click with a direct handler call or change production navigation to compensate for dropped test input.
4. Verification: Compare input and transition timestamps, then run the real feature flow with platform-synchronized input. Leave Finder scanning before stopping its activity and verify that its lease is released and the screen returns idle.

## 2026-10-23: Keep routine observations inside bounded storage

1. What happened: Review found that each detected address created permanent device metadata, bypassing history retention and Clear History.
2. Root cause: Routine observations were copied into the same table as explicit aliases, hidden choices, and legacy retained names.
3. Prevention rule: Store observations for unconfigured devices only in the bounded history table. Update existing metadata and watched timestamps without creating permanent rows for every detected address. Preserve configured and legacy metadata when clearing history.
4. Verification: Ingest thousands of unique addresses and verify that no metadata rows are created. Clear history and verify that watch-list timestamps, aliases, hidden choices, and legacy metadata remain intact.

## 2026-10-23: Scan consumers need independent lifetimes and observations

1. What happened: Background monitoring, manual scans, and delayed completion callbacks could stop replacement scans or publish state after a scan had ended. Alert settings also controlled whether sightings updated last-seen timestamps.
2. Root cause: Scan ownership and timers were shared implicitly, and observation persistence was coupled to alert eligibility.
3. Prevention rule: Give each scan consumer a unique lease and a fresh result map. Reject stale callbacks by generation. Cancel the complete monitoring cycle when configuration changes or the service stops. Persist observations before applying alert settings, cooldowns, and Wi-Fi eligibility. Activate location-dependent monitoring from a visible activity when Android requires it.
4. Verification: Test replacement leases, stale releases, cancellation, and alert-independent timestamps. Verify the Room database migration and alias retention on Android. Exercise foreground/background navigation, stop/start, and the resume action on an exclusively reserved emulator. Use Bluetooth hardware to verify actual screen-off reception.

## 2026-10-22: Legacy Pages publishing starts unrelated hosted builds

1. What happened: Preparing an Android-only update exposed a legacy Pages publishing configuration tied to `main:/docs`. A main-branch push could start a hosted Pages build even when the support site had no changes.
2. Root cause: Legacy branch publishing had no path filter separating site inputs from application code and operational documentation.
3. Prevention rule: Use GitHub Actions publishing with paths limited to the actual support-site HTML, assets, and site-specific configuration. Keep deployment permissions and the protected Pages environment. Change the remote publishing mode before merging an application-only update so the legacy trigger cannot run first.
4. Verification: Parse the workflow locally, test both included and excluded paths, confirm that the proposed main-branch diff matches no deployment input, and review the configuration independently. After publication, verify the remote mode, live support and privacy-policy URLs, and the absence of an unrelated hosted run for the checked revision.

## 2026-10-22: Target SDK upgrades require a supported build stack

1. What happened: The app targeted API 35 and Play Console required API 36 or higher. The build also suppressed an unsupported compile-SDK warning from Android Gradle plugin 8.2.2.
2. Root cause: The SDK target and build-plugin support had drifted apart. Suppressing the warning concealed the unsupported tool combination.
3. Prevention rule: Check Google's API compatibility table before raising compile and target SDK levels. Upgrade the complete Gradle wrapper and compiler tooling together, then remove obsolete suppressions. With Android Gradle plugin 9, use its built-in Kotlin compiler and a compatible Kotlin Symbol Processing plugin. Check annotation-processor compatibility too: Room 2.6.1 fails with `unexpected jvm signature V` under the newer engine, while Room 2.7 and later support KSP2. Keep Room compiler and runtime versions aligned, with separate approval for that dependency upgrade.
4. Verification: Run local unit tests, lint, APK assembly, and bundle assembly. Inspect the packaged manifest for the intended target and minimum SDK levels. Verify screen-off scans and reboot recovery separately on an available device before store submission.

## 2026-06-07: Wi-Fi gate background service needs location foreground type

1. What happened: Wi-Fi-gated scans could stay blocked with `Waiting for selected Wi-Fi` even while the phone was connected to the selected SSID.
2. Root cause: Android treats Wi-Fi SSID and scan-result access as location-protected. The scan service had the `connectedDevice` foreground-service type, but not `location`, so background Wi-Fi API calls were rejected even though Location and Nearby Wi-Fi runtime permissions were granted.
3. Prevention rule: Any background service path that reads current SSID or Wi-Fi scan results must declare and activate the `location` foreground-service type when the Wi-Fi gate is enabled.
4. Verification: Check read-only `adb logcat` for `WifiService` permission violations, run `testDebugUnitTest`, `assembleDebug`, and `lintDebug`, then validate the installed build against a real selected SSID.

## 2026-08-29: Trim DPAPI ciphertext before decryption

1. What happened: The first Play release build created an upload keystore and encrypted password, then failed while reading the encrypted password.
2. Root cause: PowerShell `Set-Content` added a line ending to the Windows Data Protection API ciphertext. Passing the raw file contents to `ConvertTo-SecureString` treated that line ending as part of the ciphertext.
3. Prevention rule: Trim surrounding whitespace from serialized DPAPI ciphertext before calling `ConvertTo-SecureString`.
4. Verification: Run `scripts/build-play-release.ps1`, verify that the signed bundle is produced, and confirm its signature with `jarsigner -verify`.
