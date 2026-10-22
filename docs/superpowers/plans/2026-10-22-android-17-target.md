# Android 17 target migration

**Goal:** Build BT Notifier for Android 17 (API 37), above the API 36 requirement shown in Play Console.

**Scope:** Upgrade the supported build tools and release version. Preserve Android 6.0 (API 23) support and existing application behavior. The background reliability findings and feature recommendations are review deliverables; their implementation is outside this migration.

**Architecture:** Use Android Gradle plugin 9.1.1 with its built-in Kotlin compiler, Gradle 9.3.1, and Kotlin Symbol Processing 2.3.12. Upgrade the Room compiler and runtime together to the separately approved version 2.8.4, which supports the newer code generator. Retain Java 17 bytecode compatibility and the existing database schema.

## Constraints

1. Work in the isolated checkout created from commit `8688c91`.
2. Run checks locally with one worker and a bounded heap. Leave other processes and emulators alone.
3. Do not submit a store release or trigger hosted verification or Copilot review.
4. Inspect deployment triggers before publishing; the legacy Pages configuration deploys from `main`.

## Implementation

1. Install the API 37.0 SDK platform alongside existing SDK platforms.
2. Set compile and target SDK levels to 37, version code to 2, and version name to 1.0.1 in `app/build.gradle`.
3. Upgrade the Android build plugin and Kotlin Symbol Processing plugin in `build.gradle`. Remove the separate Kotlin Android plugin and obsolete `kotlinOptions` block; built-in Kotlin follows the existing Java 17 target.
4. Upgrade the complete Gradle wrapper, pin its distribution checksum, and remove the unsupported-SDK warning suppression.
5. Use explicit property assignments in the Groovy build configuration and pin wrapper line endings for Unix and Windows.
6. Upgrade Room to 2.8.4. Compare generated schema identity and table definitions against the baseline build.
7. Document build prerequisites and preserve the distinction between local verification and Play approval.
8. Apply the separately approved filtered support-site workflow. Preserve the protected Pages environment, switch remote Pages publishing to Actions before merging, and verify that application and Markdown-only changes do not start a deployment.

## Verification

1. Run unit tests, Android lint, debug APK assembly, and release bundle assembly with one worker. Compile Kotlin in the Gradle process to bound concurrent memory use.
2. Inspect the packaged manifest for API 37, minimum API 23, version code 2, and the existing foreground-service declarations.
3. Inspect generated Room database code and the release archive for migration and native-library compatibility concerns.
4. Complete an independent review of the migration and resolve material findings.
5. Record the checked revision, exact commands, results, and runtime limitations in a dated evidence record. Device testing requires a separately available Android device; do not use another project's emulator.

## Sources

1. [Android build-tool compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
2. [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
3. [Kotlin Symbol Processing 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12)
4. [Room releases and KSP2 support](https://developer.android.com/jetpack/androidx/releases/room)
5. [Custom GitHub Pages workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
