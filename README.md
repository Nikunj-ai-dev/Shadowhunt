# SHADOWHUNT — Android alpha

A native Android Jetpack Compose implementation scaffold for the Shadowhunt real-world pursuit game. The visual direction is a futuristic tactical console: dark navy surfaces, cyan radar elements, magenta Hunter indicators, amber warnings, compact status telemetry, and role-specific player screens.

> **Release status: ALPHA / NOT PRODUCTION READY.** This source project has not been compiled, installed, or tested on physical Android devices in this environment. It is not safe to use for a real public match yet. See `docs/PRODUCTION_GAPS.md` and `docs/DEVICE_TEST_PLAN.md` before testing with participants.

## What is in this project

- Android-native Jetpack Compose UI for Home, Create Room, Join Room, Lobby, Hunter, Shadow, Host, Paused, and Results screens.
- Host configuration for team sizes, duration, and circular zone radius.
- Nearby Connections transport using `P2P_CLUSTER` so nearby devices can establish multiple direct peer links.
- Application-level mesh packet flooding with message IDs, duplicate suppression, a hop limit, pending message retries, and a basic WebSocket internet gateway.
- Fused Location Provider updates through a foreground location service during an active session.
- Three-minute Shadow location snapshots, immediate boundary-alert pings, boundary warnings, and grace-period elimination logic.
- Per-player QR token display and QR scanning using Google Code Scanner.
- A development WebSocket relay in `server/` for multi-gateway fan-out tests.
- A GitHub Actions workflow (`.github/workflows/android-debug.yml`) that can build a debug APK and upload it as a workflow artifact.

## Build the APK on a developer machine

This execution environment does not contain Android SDK / `sdkmanager` / Gradle, and outbound DNS is unavailable, so it cannot download the Android toolchain or produce a verified APK. The following steps are required on a machine with Android Studio and internet access:

1. Install Android Studio, JDK 17, Android SDK Platform 36, Android SDK Build-Tools 36.0.0, and Gradle 8.11.1.
2. From the project root, generate the missing Gradle wrapper scripts/JAR with `gradle wrapper --gradle-version 8.11.1` (or use system Gradle directly).
3. Open this `shadowhunt/` folder in Android Studio and allow Gradle sync to download the plugin and dependencies.
4. Add a development relay URL in the project's `gradle.properties` or your user-level `~/.gradle/gradle.properties`:

   ```properties
   SHADOWHUNT_SERVER_WS_URL=wss://your-test-domain.example/ws
   ```

5. Build a debug APK using **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or from a terminal with Gradle 8.11.1 installed:

   ```bash
   gradle assembleDebug
   ```

6. The output should be at `app/build/outputs/apk/debug/app-debug.apk`.
7. Install the same build on multiple real Android phones and run the acceptance tests in `docs/DEVICE_TEST_PLAN.md`.

The current release build intentionally has minification disabled. Do not publish it as a Play Store release. The app has no production signing key or privacy-policy URL.

## Run the development WebSocket relay

```bash
cd server
npm install
PORT=8787 npm start
```

Put a TLS reverse proxy in front of it and expose a `wss://` URL. The Android development relay broadcasts packets to every socket in a room and sends relay acknowledgements so disconnected clients can retry queued packets after a route appears.

**The relay is only for development.** It is not an authoritative or secure game backend. It does not authenticate player identity, authorize role changes, filter Hunter-only location data, validate capture requests, persist sessions, or prevent a malicious client from spoofing events.

## Current networking model

- Nearby Connections `P2P_CLUSTER` establishes direct local links. It may use a combination of Bluetooth, BLE, and Wi-Fi transports managed by Google Play services.
- Each app forwards unique packets over its connected peer links. The implementation uses controlled flooding (seen-message cache + hop limit), not a shortest-path routing protocol.
- Any internet-connected player can open a WebSocket to the relay. A packet that reaches any gateway can be fanned out to every gateway in the room and then forwarded over the local peer mesh.
- A message that has no current route is kept in a bounded in-memory pending queue and retried when a peer connects. It is not durably stored across app process death yet.
- GPS is independent of internet. The app uses the device's location provider; coordinates still need a route to reach other players.

## Important limitations

The goal is to give a developer a concrete starting point, not to imply a complete production system. Key gaps include end-to-end encryption and team-only location distribution, signed join tickets, secure Host authority, persistent outbox/state recovery, proper server-side game rules, GPS anti-spoofing, disconnect and split-brain handling, background execution testing, complete accessibility, automated UI tests, and device-matrix validation. These are detailed in `docs/PRODUCTION_GAPS.md`.
