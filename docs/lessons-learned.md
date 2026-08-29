# Lessons Learned

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
