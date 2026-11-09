# Validation

## COM-Loop 0.1.12 (version code 13)

- Flutter analysis reported no issues and all 16 Flutter tests passed.
- The debug APK was built and updated on a Moto G75 using `adb install -r`.
- The release APK was built and its APK Signature Scheme v2 signature verified with Android's `apksigner`. The release certificate subject is `CN=COM-Loop`.
- The release APK has not been tested on hardware. The debug and release builds have different signing certificates.

## Earlier verification

The 0.1.7 widget update passed six Kotlin unit tests and all ten PI command comparisons. Android reported two registered widget providers. Later widget layout and icon revisions were installed as debug updates.

The owner reproduced an ambient-volume reset after starting media while the controller session was disconnected. Automatic compensation is outside the current scope. See the [hardware checklist](DEVICE_CHECKLIST.md) for outstanding device checks.
