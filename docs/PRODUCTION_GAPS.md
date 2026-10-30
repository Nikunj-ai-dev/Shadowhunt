# Production readiness gaps — do not skip

The current code is an alpha implementation. The existence of screens, a peer transport, and a relay does not make it production-ready. Do not use it for a public game with real participant location data until these issues are implemented and verified.

## P0 — security and game integrity

1. **Encrypt location traffic end-to-end.** The current alpha packet payloads are plain JSON. Any peer relay that receives a packet can inspect its content. Hunter-only location snapshots must be encrypted for the Hunter team and Host; ordinary Shadows and relay-only devices must not be able to decrypt them. Use authenticated group key distribution and key rotation. Do not treat a UI role check as access control.
2. **Authenticate every player.** Room codes are discovery hints only. The server must issue short-lived, single-use join tickets and bind a device public key to the room-specific player ID. Local Nearby endpoint names are untrusted and can be spoofed.
3. **Make the server authoritative when a gateway is available.** It must validate Host-only role changes, ready states, start/pause/resume, timer, captures, boundary eliminations, and winner calculation. The sample server merely relays messages.
4. **Sign critical player messages.** Location pings, boundary alerts, and capture requests need device signatures and replay protection. Store nonce/sequence state server-side.
5. **Validate QR captures centrally.** Current QR values are room/player/token strings generated on-device. Tokens are not signed by a server, do not expire, and are not bound to a recent capture challenge. A screenshot can be copied. Production should use short-lived signed challenges or a server-issued token whose use is atomic and single-use.
6. **Protect against GPS spoofing.** GPS accuracy and location history can provide useful evidence but cannot prove a human is physically present. Define anti-cheat limits and an appeal/Host resolution path; do not automatically eliminate on one inaccurate reading.
7. **Enforce authorization at the recipient layer.** The alpha router floods every packet to every local peer. UI filtering of `LOCATION_PING` is not confidentiality. Implement opaque encrypted envelopes with audience keying before real use.

## P1 — reliability and networking

1. Implement a durable message outbox (Room/SQLite) with expiry, priority, retry count, and delivery receipts. Current pending packets live only in memory.
2. Replace uncontrolled flooding with a tested routing strategy or keep the group-size cap low and prove flooding remains stable. The current hop limit is 8 and the dedupe cache is in memory.
3. Persist room/game state and support recovery after Android kills the process.
4. Implement Host failover or a clearly enforced Host-unavailable pause and rejoin procedure. The current UI has no secure Host migration.
5. Use server time / clock-offset estimation for synchronized starts and timers. Current clients use device wall clocks; clock skew can create timer drift.
6. Add per-message size limits, backpressure, rate limits, retransmission policy, congestion handling, and battery safeguards.
7. Test simultaneous peer links and discovery on target device families. Nearby Connections `P2P_CLUSTER` is a direct peer topology capability, not a guarantee that every phone can connect to every other phone.
8. Test network churn: radio off/on, Wi-Fi/Bluetooth changes, gateway death, process suspension, app background/foreground, battery saver, and permissions revoked mid-game.
9. The development relay broadcasts to all WebSocket clients in a room. A production server must authorize recipient groups and never fan out sensitive locations to the wrong team.

## P1 — Android platform behavior

1. Validate Bluetooth, Nearby Devices, Wi-Fi Nearby Devices, and precise location runtime permission flows on Android 8–16 devices.
2. Validate the location foreground service on each supported Android version and OEM. FGS access does not guarantee unthrottled location in all power states.
3. Keep the player visibly aware when a match is tracking location. Provide a persistent session notification and a clear stop action.
4. Provide a privacy policy, in-app disclosure, data retention policy, and Play Console declarations for location usage. Request only the permissions needed for the active match.
5. Google Code Scanner requires a compatible Google Play services module. Add an alternate bundled scanner path for devices without it.
6. Implement graceful behavior when location is unavailable, precise location is denied, Bluetooth is off, Nearby services are missing, the map is offline, or the phone is overheating/low battery.

## P2 — product completion

- Complete visual zone selection with map/offline map tiles, polygon zones, and safe-area validation.
- Add reconnect/resume, lobby removal/kick, role reassignment confirmation, and explicit leave/forfeit rules.
- Add full pause and disconnect semantics for every role.
- Add event history, game results, error telemetry, analytics with privacy controls, and crash reporting.
- Add accessibility, dynamic type, screen-reader labels, localization, and color-blind-safe cues.
- Implement unit tests, instrumentation tests, protocol fuzz tests, and end-to-end real-device tests.
- Add release signing, R8 rules, dependency scanning, SBOM, secrets management, CI/CD, and staged rollout.

## Release gate

A production release should be blocked until every P0 item is closed, P1 reliability items are verified on a documented device matrix, a third-party security review is completed, and the real-device test plan passes repeatedly in outdoor conditions.
