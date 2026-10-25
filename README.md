# QR → NFC

Scan a QR code, tap an NFC tag to write it (and optionally lock it), then tap again to verify. You don't press anything between steps. The design is in [docs/PLAN.md](docs/PLAN.md).

## Build

You need JDK 17+ (Android Studio's bundled JBR works) and the Android SDK (platform 36).

```sh
# first time only: create the Gradle wrapper (or open the project in Android Studio)
gradle wrapper --gradle-version 9.3.1

./gradlew testDebugUnitTest     # unit tests
./gradlew assembleRelease       # signed APK → app/build/outputs/apk/release/app-release.apk
```

Release signing reads `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword). This file and `release.jks` are git-ignored, so **back them up**. Updates to an installed app must be signed with the same key.
