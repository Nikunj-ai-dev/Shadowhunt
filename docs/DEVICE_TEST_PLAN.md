# Shadowhunt physical-device acceptance test plan

Do not use a public match until the P0 security work in `PRODUCTION_GAPS.md` is complete. Run tests in a controlled, permitted outdoor area with participants who understand that this is an alpha build.

## Test equipment

- Minimum: 3 Android phones with Google Play services, GPS, Bluetooth, and Wi-Fi.
- Preferred: 6–10 Android phones from at least three manufacturers and covering Android 10, 12, 13, 14, 15, and 16 where available.
- One phone with mobile data enabled as an internet gateway; test other combinations with no mobile data and Wi-Fi disabled.
- A development WebSocket relay reachable via `wss://` for internet-gateway tests.
- A measured, open, safe test zone with no traffic, restricted property, or other hazards.

## Lobby and game flow

1. Host creates a room and records the six-character code.
2. Two or more players join using the same code while nearby.
3. Confirm every device shows the correct callsign and a connected state.
4. Host assigns the configured number of Hunters and Shadows.
5. Confirm a player cannot ready before role assignment.
6. Confirm Host cannot start until every required slot is filled and every participant is connected and ready.
7. Start a short match; verify all devices enter ACTIVE and timers remain acceptably aligned.
8. Pause and resume from Host; verify every device follows.
9. Capture all Shadows and verify exactly one Hunters-win result is produced.
10. Run a separate match until the timer expires with one Shadow remaining; verify Shadows win.

## GPS and zone

1. Verify the Host can set the zone center from a fresh location fix.
2. Verify each device displays its own location accuracy.
3. Verify the Shadow radar never displays Hunter coordinates.
4. Verify Hunter radar displays only received Shadow snapshots and shows their age.
5. Verify scheduled Shadow location snapshots are emitted every configured interval (default 180 seconds).
6. Walk a Shadow toward the boundary and verify a warning appears before crossing.
7. Cross the boundary in a controlled test; verify an immediate alert is delivered after the point is confidently outside the boundary.
8. Verify elimination only occurs after crossing the configured elimination buffer for the full grace period.
9. Repeat with poor GPS accuracy and verify the player is not eliminated from a single uncertain fix.
10. Disable location permission or GPS mid-match; verify a visible location-unavailable state and no false elimination.

## QR capture

1. Display a Shadow QR code on one phone.
2. Scan it using an active Hunter phone.
3. Verify the Host confirms the capture and all connected players receive one capture event.
4. Scan the same QR again; verify it cannot create a second capture.
5. Attempt to scan as a Shadow; verify the app rejects it.
6. Attempt a malformed code and a code from another room; verify rejection.
7. Test scanning when the Hunter has no internet but has a multi-hop path to a gateway.
8. Test scanning with no server path; UI must show pending/unconfirmed rather than claiming a globally committed capture.

## Local peer and mesh relay

Use this topology test:

- Phone A: no internet.
- Phone B: no internet, acting as relay.
- Phone C: internet gateway.

1. Verify A and B establish a Nearby Connections peer link.
2. Verify B and C establish a peer link.
3. Send a uniquely identified non-sensitive test packet from A; verify C's development relay receives it with A as the original sender.
4. Verify server ACK returns to A through C → B → A.
5. Add Phone D as a second gateway and verify duplicate delivery is deduplicated.
6. Break B–C while another valid route exists; verify delivery continues through the alternate route.
7. Break every gateway route; verify eligible messages queue and are marked stale/pending, not shown as live.
8. Restore a route and verify pending delivery retries without creating duplicate game events.
9. Move devices until the peer graph changes; record connection recovery time and packet delivery rate.
10. Test Bluetooth/Wi-Fi toggle, airplane mode with radios selectively re-enabled, screen lock, app background, battery saver, process kill, and permission revocation.

## Metrics and release criteria

Record for each run:

- Device model, Android version, Google Play services version.
- Topology and radio state.
- Peer connection setup and reconnection time.
- Location fix accuracy and age.
- Message delivery latency and delivery success percentage.
- Duplicate and out-of-order message counts.
- Battery drain per 30-minute match and device temperature.
- Any crashes, ANRs, permission errors, or false eliminations.

Release criteria must be defined by the product owner and met repeatedly across the declared device matrix. No test in this document alone establishes production security or Play policy compliance.
