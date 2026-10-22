# Lessons Learned

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
