# COM-Loop — WIDEX COM-DEX Android Controller

COM-Loop is an independent Android app for the WIDEX COM-DEX Bluetooth accessory. It can show the accessory's battery and connection status, adjust volume, toggle surrounding sounds (Room Off), and remember previously connected units. It also offers horizontal and vertical home-screen widgets and controls for a connected COM-DEX Remote Mic.

COM-Loop is independently developed and has no affiliation with or endorsement from WIDEX. WIDEX COM-DEX identifies the compatible hardware.

## Install

1. Download the signed APK from [GitHub Releases](https://github.com/Moorlack/com-loop/releases/latest) and open it on Android 7 or later.
2. Pair the WIDEX COM-DEX accessory in Android Bluetooth settings.
3. Open COM-Loop and choose your COM-DEX.

Android cannot update a local debug installation in place with an APK signed by the release key. The official WIDEX app uses a different package and can remain installed.

## Build from source

Open the folder containing `pubspec.yaml` in Android Studio with the Flutter and Dart plugins. Use a Flutter SDK with Dart 3.12.2 or compatible, JDK 17 or newer, and Android SDK 36.

```sh
flutter pub get
flutter analyze
flutter test
python scripts/verify_protocol.py
flutter build apk --debug
```

Run Kotlin tests from `android` with `./gradlew :app:testDebugUnitTest` (Windows: `.\gradlew.bat :app:testDebugUnitTest`). Run a Flutter build first to generate the local Gradle configuration.

For a signed release, create a local `android/key.properties` with `storeFile` (relative to `android`), `storePassword`, `keyAlias`, and `keyPassword`, then run `flutter build apk --release`. Keep signing files outside Git.

## Compatibility and documentation

Android handles Bluetooth audio streaming. COM-Loop sends control commands over GATT. The accessory reports its own battery and does not expose the hearing aids' absolute volume. Starting playback can reset the ambient volume even when COM-Loop's control session is disconnected.

- [Hardware checklist](docs/DEVICE_CHECKLIST.md)
- [Feature comparison](docs/PARITY.md)
- [Protocol notes](docs/PROTOCOL.md)
- [Public references](docs/PUBLIC_RESEARCH.md)
- [Validation notes](docs/VALIDATION.md)

If you'd like to support development, you can [leave a tip on Ko-fi](https://ko-fi.com/moorlack).
