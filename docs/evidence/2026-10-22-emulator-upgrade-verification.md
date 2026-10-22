# Emulator upgrade verification, October 22, 2026

## Build and isolation

Tested implementation: `cfdcaaa8fcc883f234d9d7f5a1aed93fd33b4937`, with the local verification record committed at `9b56083e1e187ca066b86f3426323611a2045ff1`. No application source or build configuration changed during these runtime checks.

The user authorized waiting for the shared emulator to be free and then using it. At the scheduled check, the shared registry's emulator claim was empty and no emulator process was running. This chat acquired the emulator reservation through the existing device-owner module before launch.

The test used the existing Android 16 / API 36 image in a task-owned session launched with `-read-only -no-snapshot-load -no-snapshot-save -memory 3072 -no-window -port 5560`. Its serial was `emulator-5560`. A separate ADB server on port 5041 isolated the commands from the existing servers on ports 5037 and 5038. Every device command targeted this emulator; no package, shell, or UI command targeted either physical device. No existing Android Virtual Device configuration was edited.

## In-place update and data retention

The read-only emulator copy initially had no BT Notifier installation. The existing primary-checkout debug APK was installed as the old version:

1. Package `com.btnotifier`, version 1.0.0 / code 1, target API 35.
2. APK SHA-256: `feae5925e1f29db6905056c3eab74593eb01b7a2184ff9f727b70c8e7ac9b3d2`.
3. This was an existing artifact; its source provenance was not regenerated during this check.

A synthetic version-3 database was created only in the temporary emulator copy before first launch. It contained one watched device, a custom alias, retained names and timestamps, disabled alerts, and one hidden device. The original app opened this database and displayed the watched alias and stored last-seen time.

The normal Bluetooth and notification permission dialogs were allowed. In Settings, the service was disabled and the scan interval was set to 31 minutes. Only BT Notifier was stopped to obtain a consistent snapshot of its database, write-ahead log, and DataStore settings.

The new APK was installed with:

```powershell
adb -P 5041 -s emulator-5560 install -r build/app/outputs/apk/debug/app-debug.apk
```

The update succeeded without uninstalling or clearing app data. Android reported version 1.0.1 / code 2, minimum API 23, and target API 37. The existing Bluetooth and notification grants remained granted.

After launching the new version and opening Settings and Watch List:

1. The service remained disabled and the interval remained 31 minutes.
2. The watched alias and last-seen display remained intact. The hidden-device sheet displayed the hidden alias.
3. Consistent before/after snapshots contained identical watched rows, metadata rows, database version 3, and schema identity `fe088f0f5d4eac8f85e13360e645494a`.
4. The settings file was byte-identical across the update. Its SHA-256 was `4710d588c5320658cf149fc8aa05ad9e2ab53d3111d888227fe9710291359e49` before and after.

This verifies an existing version-3 database across the library and target-SDK update. It does not exercise the older schema-1-to-2 or schema-2-to-3 migration paths.

## Service, navigation, and permissions

1. The background-service switch started `BluetoothScanService`. Android reported `isForeground=true`, notification ID 1001, the `bt_scan_service` channel, and service type `0x10` (`connectedDevice`).
2. After returning to the launcher and putting this emulator's screen to sleep normally, the service and application process remained alive at a check more than 30 seconds later. Android reported `mWakefulness=Asleep` and retained the foreground notification. No forced Doze or device reboot was used.
3. Turning the service off removed its active service record. Turning it on again created a new foreground service record and notification.
4. The optional Wi-Fi gate presented the normal precise-location permission dialog. Granting access while using the app also granted the required nearby Wi-Fi permission. The network selector offered the emulator's `AndroidWifi` network.
5. Selecting that network and restarting the service while the app was visible added the `location` service type (`0x18`, combined with `connectedDevice`). After leaving the app, the service remained in the foreground. The application log recorded `Wi-Fi gate allowed scan` followed by `Bluetooth scan started`.
6. Nearby, Watch List, Settings, and the hidden-device sheet opened normally. Manual scanning changed the button to `Stop Scan`; pressing it changed the button back to `Scan Now`.
7. The captured upgraded-application process log contained no fatal exception, `SQLiteException`, or `SecurityException`. This observation covers the exercised paths and time window only.

The final database snapshot retained the same fixture rows and schema identity after these controls were exercised. Its settings differed from the upgrade comparison because the later Wi-Fi and service tests intentionally changed settings in the temporary emulator copy.

## Evidence and cleanup

Screenshots, UI hierarchy captures, service records, application-process logs, database snapshots, and a SHA-256 inventory are in `.local-build/runtime-20261022/`. They remain local. The new APK hash matches the prior verification record: `38fe98afe96d95b3198b140aad9154b79cd1d512c6884bd8f8c17d399e09d58e`.

BT Notifier was stopped after the checks. Only the task-owned emulator session was shut down, then its reservation was released and verified empty. The private ADB server created for this check was stopped after its process identity and creation time were verified. Existing ADB servers on ports 5037 and 5038 remained running. The read-only session's test data was discarded with that session; the original emulator disk was preserved.

## Limits and publication

These are debug-build checks on API 36. They do not establish Android 17 runtime behavior, real Bluetooth discovery or range, long-duration scanning, automatic process-death recovery, reboot recovery, manufacturer battery restrictions, or Play acceptance. The scan log proves that the emulator entered the scan path; it does not prove discovery of a physical Bluetooth device.

No hosted checks, automated GitHub review, store submission, push, merge, or deployment occurred. The remote `main` revision remained `8688c91ae9242a38990d34a8714aee25ecf0a95d`. GitHub Pages remained configured as a legacy deployment from `main:/docs`; publication needs a separate resolution of that unrelated hosted-build trigger.
