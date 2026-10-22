# Google Play release process

## Build prerequisites

Use Java 17 or newer and install Android SDK Platform 37.0. The checked-in Gradle wrapper selects Gradle 9.3.1. Android Gradle plugin 9.1.1 supplies the Kotlin compiler; Kotlin Symbol Processing 2.3.12 generates the Room 2.8.4 database code. Keep the Room compiler and runtime versions aligned.

The app compiles against and targets Android 17 (API 37). It supports Android 6.0 (API 23) and later. Confirm that the version code exceeds every bundle already uploaded to Play Console before building an update.

Run verification locally. Use one worker on a shared machine:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease --max-workers=1 --no-daemon
```

Without the upload-signing environment variable, `bundleRelease` produces an unsigned bundle. Use the signing script below for a bundle that can be uploaded to Play. Successful compilation does not verify screen-off scanning, reboot recovery, device battery restrictions, or Play acceptance; test those separately before release.

## Build a signed bundle

Run the release script from PowerShell:

```powershell
.\scripts\build-play-release.ps1
```

The first run creates two ignored files in `.local-signing`:

1. `bt-notifier-upload.jks` is the Google Play upload keystore.
2. `bt-notifier-upload-password.dpapi` stores the keystore password encrypted for the current Windows user through the Windows Data Protection API.

The signed bundle is written to `build/app/outputs/bundle/release/app-release.aab`.

Back up both upload-key files to a secure location. The encrypted password file can only be decrypted by the Windows account that created it. Losing the upload key does not expose the Play signing key, but it does require an upload-key reset through Play Console before another release can be uploaded.

## Verification

The script runs unit tests and Android lint before it builds the bundle. Verify the bundle signature separately when needed:

```powershell
jarsigner -verify -verbose -certs .\build\app\outputs\bundle\release\app-release.aab
```

## Store copy and privacy policy

The source listing copy is in `docs/play-store-listing.md`. GitHub Pages serves the support page and privacy policy from `docs/index.html` and `docs/privacy-policy.html`.

## Foreground service declaration

The Play Console declaration uses the demonstration video at `docs/assets/foreground-service-demo.mp4`. The recording shows the user enabling background Bluetooth scans, the persistent service notification, the optional Wi-Fi scan limit, and its location permission prompt.
