# Lessons Learned

## 2026-06-07: Wi-Fi gate background service needs location foreground type

1. What happened: Wi-Fi-gated scans could stay blocked with `Waiting for selected Wi-Fi` even while the phone was connected to the selected SSID.
2. Root cause: Android treats Wi-Fi SSID and scan-result access as location-protected. The scan service had the `connectedDevice` foreground-service type, but not `location`, so background Wi-Fi API calls were rejected even though Location and Nearby Wi-Fi runtime permissions were granted.
3. Prevention rule: Any background service path that reads current SSID or Wi-Fi scan results must declare and activate the `location` foreground-service type when the Wi-Fi gate is enabled.
4. Verification: Check read-only `adb logcat` for `WifiService` permission violations, run `testDebugUnitTest`, `assembleDebug`, and `lintDebug`, then validate the installed build against a real selected SSID.
