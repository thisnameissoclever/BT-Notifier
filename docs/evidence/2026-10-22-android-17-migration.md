# Android 17 migration verification, October 22, 2026

## Checked revision

Implementation commit: `cfdcaaa8fcc883f234d9d7f5a1aed93fd33b4937`.

Baseline: `8688c91`. Worktree: `D:/VIBES/.worktrees/android-17-target/bluetooth-notifier`. Branch: `twcx/android-17-target`.

The user approved the API 37 build-tool migration and separately approved Room 2.8.4. The application source files under `app/src` are unchanged. Background reliability findings and feature recommendations were delivered in the conversation; their implementation is outside this migration.

## Local checks

Java runtime: Eclipse Adoptium 17.0.18. Android SDK Platform 37.0 revision 2. Build stack: Android Gradle plugin 9.1.1, built-in Kotlin 2.2.10, Gradle 9.3.1, Kotlin Symbol Processing 2.3.12, and Room 2.8.4.

Executed from the worktree in PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Users/myema/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --warning-mode all
```

1. The full build passed in 4 minutes, 3 seconds. A repeat on the committed tree passed in 24 seconds, with 102 tasks up to date and 2 executed.
2. Device-name unit tests: 9 passed. Wi-Fi decision unit tests: 8 passed. No test failures or errors.
3. Debug lint: 0 errors and 93 warnings. Release vital lint passed. These checks do not cover background service lifecycle or radio behavior.
4. Debug APK and release bundle assembly passed. No native libraries are present in the release bundle.
5. `git diff --check` passed.

## Packaged configuration and database

`aapt2 dump badging build/app/outputs/apk/debug/app-debug.apk` reports application ID `com.btnotifier`, compile/target API 37, minimum API 23, version code 2, and version name 1.0.1. The release bundle's generated manifest reports the same target, minimum, and version values.

The old Room 2.6.1 processor failed under KSP2 with `unexpected jvm signature V`. The diagnostic stack begins in `androidx.room.compiler.processing.javac.kotlin.JvmDescriptorUtilsKt.typeNameFromJvmSignature`. The approved Room 2.8.4 compiler completed both debug and release processing.

Generated `CREATE TABLE` statements match the baseline build. Both generated database identity hashes also match: `fe088f0f5d4eac8f85e13360e645494a` and `a21467d9b807043cd205e987536c4d3c`. Database version 3, database filename, and explicit migrations remain unchanged. This is structural evidence; opening and upgrading an installed database still requires runtime testing.

Gradle wrapper JAR SHA-256: `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`. Distribution SHA-256: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`. Both match Gradle's published values. The Unix wrapper uses LF line endings; the Windows wrapper uses CRLF.

## Artifacts and signature

1. Debug APK: `build/app/outputs/apk/debug/app-debug.apk`. SHA-256: `38fe98afe96d95b3198b140aad9154b79cd1d512c6884bd8f8c17d399e09d58e`.
2. Signed release bundle: `.local-build/bt-notifier-1.0.1-api37.aab`. SHA-256: `38ba34cc6852cdd0f5e6853d37d9b5a7e96fef00debcfdf630976d36ca3c31b9`.

The release bundle was signed locally with the existing upload keystore in the primary checkout. The password was decrypted from its existing Windows-protected file into a temporary process environment variable and cleared afterward. No key was created, replaced, or copied. `jarsigner -verify .local-build/bt-notifier-1.0.1-api37.aab` returned success. It reported a self-signed certificate, an untrusted certificate chain, and no timestamp. This establishes signature integrity with the upload key; Play acceptance remains unverified.

## Independent review

A fresh read-only review of `8688c91..cfdcaaa8fcc883f234d9d7f5a1aed93fd33b4937` found no blocking introduced defect. The reviewer checked tool compatibility against official documentation, wrapper checksums and line endings, generated database schema, signing behavior, target-SDK behavior changes, and documentation. Runtime upgrade and data retention, navigation, permission flows, screen-off scanning, Wi-Fi gating, reboot recovery, Play version-code availability, and Play acceptance remain separate proof gaps.

## Runtime and publication limits

The user requested waiting for the shared emulator to become free and checking every 30 minutes. The emulator is currently claimed by another chat and was left untouched. Its configured system image is Android 16 (API 36), so tests on that image cannot establish Android 17 runtime compatibility. An emulator without working Bluetooth hardware also cannot prove device discovery or radio behavior. The two connected physical Android 17 devices were left untouched.

No hosted checks, Copilot reviews, store submissions, or deployments were started. The repository has legacy GitHub Pages deployment from `main:/docs`; a push to `main` must be assessed against the user's prohibition on unrelated hosted deployment. The implementation and this evidence have not been merged or published.
