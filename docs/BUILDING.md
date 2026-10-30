# Building Shadowhunt Android alpha

## Requirements

- Android Studio with support for Android API 36.
- JDK 17.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Gradle 8.11.1 (compatible with Android Gradle Plugin 8.9.2).
- An Android device with Google Play services for Nearby Connections, Fused Location Provider, and Google Code Scanner.

## Build locally

1. Open the project root in Android Studio and allow Gradle sync.
2. Create `local.properties` with your Android SDK path if Android Studio does not create it automatically.
3. Set a real development relay URL using Gradle property `SHADOWHUNT_SERVER_WS_URL`, for example in your user-level Gradle properties:

   ```properties
   SHADOWHUNT_SERVER_WS_URL=wss://your-development-domain.example/ws
   ```

4. Build the debug variant:

   ```bash
   gradle --no-daemon :app:assembleDebug
   ```

5. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

This source snapshot intentionally has no Gradle wrapper JAR because it could not be downloaded in the authoring environment. The GitHub Actions workflow provisions Gradle 8.11.1 and builds the APK from a pushed repository. It uploads the resulting debug APK as a workflow artifact.

## Build with GitHub Actions

1. Create a private GitHub repository and upload the contents of this project.
2. Push the `main` branch.
3. Open the repository's **Actions** tab.
4. Run **Android debug APK** or wait for the push build.
5. Optionally add a repository Actions secret named `SHADOWHUNT_SERVER_WS_URL` containing your `wss://` relay URL.
6. Download the `shadowhunt-debug-apk` artifact from the successful workflow run.

The generated artifact is a debug APK for controlled testing only. It is not a signed Play Store release and must not be treated as production-ready.
