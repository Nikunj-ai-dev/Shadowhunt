# Build status

**Artifact:** Shadowhunt Android application source snapshot 0.1.0-alpha  
**APK:** Not built in the current authoring environment  
**Production status:** Not production-ready; see `docs/PRODUCTION_GAPS.md`.

## What was checked here

- AndroidManifest.xml and styles.xml parse as valid XML.
- server/package.json parses as valid JSON.
- `node --check server/index.mjs` passes.
- The pure Kotlin game model source compiles with the local Kotlin compiler.
- A small model smoke test for room-code length and geographic distance passes.

## What could not be checked here

- Android Gradle sync or `:app:assembleDebug`.
- Compose, Android framework, Google Play services, or manifest-merger compilation.
- APK installation, launch, permission flow, or UI behavior on a device/emulator.
- Nearby Connections peer discovery, Android-to-Android radio behavior, or multi-hop delivery.
- Foreground location service behavior across OEMs and Android versions.
- QR scanning on devices with different Google Play services versions.
- Internet gateway behavior against a deployed TLS relay.

The execution environment has no Android SDK, `sdkmanager`, `adb`, or Gradle executable, and cannot resolve the external hosts required to fetch the Android build toolchain. No APK has been fabricated or mislabeled as built.

## How to obtain a debug APK

Use the included `.github/workflows/android-debug.yml` workflow after pushing this folder to a GitHub repository, or build locally with Android Studio / Gradle 8.11.1 and Android SDK Platform 36. See `docs/BUILDING.md`.

A debug APK from that workflow is for controlled alpha testing only. Complete the release gates in `docs/PRODUCTION_GAPS.md` before any public deployment.
