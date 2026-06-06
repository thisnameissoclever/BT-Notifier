# BT-Notifier

## Local debug signing

Android will only update an installed app when the new APK is signed by the same private key as the installed APK. For local builds, do not rely on the default `~/.android/debug.keystore` staying around forever.

This repo uses a durable debug keystore at `.local-signing/bt-notifier-debug.keystore` when that file exists. That key is intentionally kept with the repo so local debug builds use the same signature across machines and clones.

The default debug key settings are:

```properties
btNotifierDebugStoreFile=.local-signing/bt-notifier-debug.keystore
btNotifierDebugStorePassword=android
btNotifierDebugKeyAlias=androiddebugkey
btNotifierDebugKeyPassword=android
```

You can override those values in ignored `local.properties` if needed. Treat this as a local debug key only. Anyone with this key can sign a debug APK with the same package name, so do not reuse it for Play Store or release builds.
