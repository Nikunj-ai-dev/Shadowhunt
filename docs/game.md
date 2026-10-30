# SHADOWHUNT — Product, Game Rules & Technical Specification

**Document:** `game.md`  
**Specification version:** 1.0  
**Date:** 30 October 2026  
**Product:** Shadowhunt  
**Initial platform:** Android mobile app  
**Primary mode:** Live, location-based multiplayer pursuit  
**Connectivity model:** Internet-first, offline-capable local peer-to-peer mesh, with Bluetooth/Wi-Fi transport selected automatically where supported

---

## 0. Purpose of this document

This document defines Shadowhunt as an implementable mobile application. It covers the product concept, complete game rules, screens, user flows, game-state transitions, location mechanics, QR capture, internet and local connectivity, multi-hop message forwarding, security, backend responsibilities, reliability, privacy, and acceptance tests.

A developer should use this document as the product and engineering source of truth. Where a value is marked **default**, it can be changed by the Host before a game. Where a value is marked **MVP decision**, implement that behavior for version 1 unless product testing identifies a blocker.

### 0.1 Normative language

- **MUST** means required for a correct and safe implementation.
- **SHOULD** means strongly recommended; deviate only with a documented reason.
- **MAY** means optional or suitable for a later version.
- **MVP** means the first playable release.

### 0.2 Product definition

Shadowhunt is a real-world, team-based pursuit game. A Host creates a room, selects the number of Hunters and Shadows, defines a play zone and game duration, and starts the session after every player is ready.

Hunters try to catch every Shadow. Shadows try to remain uncaught until the game timer ends. Hunters receive scheduled location snapshots of active Shadows every three minutes. Shadows can be captured when a Hunter physically scans the Shadow's personal in-game QR code. A Shadow who leaves the permitted zone receives warnings; a sufficiently clear and sustained zone violation can result in automatic elimination.

The game must work in mixed-connectivity groups:

1. Some players have mobile data or Wi-Fi internet and can communicate with the game service directly.
2. Some players have no internet but can communicate with nearby phones.
3. Any player—Hunter or Shadow—may relay another player's encrypted game messages.
4. A phone with internet can act as an **internet gateway** for nearby devices without internet.
5. If the direct peer is not in range, messages may pass through multiple nearby phones before reaching a gateway or the Host.
6. Phones move, links break, and routes change. The app must discover alternate routes and recover without requiring the players to restart the room.

**Important:** GPS reception, internet access, and peer-to-peer communication are separate capabilities. A phone may determine its own location without internet, but it needs a communication path to share that location. A mesh can only deliver a message while a path exists; otherwise the message must wait until a path returns.

---

# PART I — GAMEPLAY SPECIFICATION

## 1. Roles and players

### 1.1 Host

The Host creates and administers a room. The Host is the session administrator and the authoritative game controller for MVP.

The Host can:
- Create a room and share its join information.
- Set the number of Hunters and Shadows.
- Assign each joined player to a team.
- Configure the game duration and location rules.
- Select and review the permitted play zone.
- See the lobby connection/readiness status.
- Start the game once all required players are ready.
- See all player statuses and resolve permitted administrative issues.
- Pause, resume, or end the game using the rules in this document.
- See the event log and final result.

**MVP decision:** The Host is a non-playing administrator/spectator. The Host is not counted as a Hunter or Shadow. This keeps the Host's control role unambiguous and gives the local game a single authority. A later version may allow the Host to participate, but that requires separate rules for what the Host can see and do.

The Host's phone must remain available during the match. If it disconnects, the game enters a paused/Host-unavailable state after a short grace period. Host migration is out of scope for MVP.

### 1.2 Hunters

Hunters are the catching team.

Hunters can:
- See their own current position.
- See the permitted play-zone boundary.
- See the last known position of each active Shadow after a scheduled or exceptional ping.
- See the timestamp and reported GPS accuracy for each location snapshot.
- See the estimated distance from their own current location to each Shadow's last known position.
- Sort active Shadows by nearest estimated distance.
- Scan a Shadow's QR code to submit a capture request.
- Receive capture, zone-violation, elimination, and game-state notifications.
- Act as local mesh relays and internet gateways, regardless of their Hunter role.

Hunters **cannot** see a Shadow's continuously updated live location between pings in the standard game mode. They see the last reported snapshot. This preserves the value of hiding and movement.

### 1.3 Shadows

Shadows are the escaping team.

Shadows can:
- See their own current position and the permitted play-zone boundary.
- See the game timer and their own status.
- See their personal QR code while active.
- Receive boundary proximity warnings and confirmed boundary-violation warnings.
- Receive a notice if a Hunter submits a capture request that is pending verification, where applicable.
- Receive game result and administrative notifications.
- Act as local mesh relays and internet gateways, regardless of their Shadow role.

Shadows **cannot** see Hunters' positions or Hunters' distances to them.

### 1.4 Player identity

Every player has:
- A stable account/profile identifier, if accounts are enabled.
- A room-specific `playerId`.
- A generated device key pair for the session.
- A display name.
- A role (`HOST`, `HUNTER`, or `SHADOW`).
- A connection state.
- A readiness state.
- A location-permission and location-quality state.
- An active/eliminated/captured state.

A player can only occupy one role in a room. A device may be replaced only through a Host-controlled rejoin/recovery procedure.

### 1.5 Player counts

The Host must select at least one Hunter and at least one Shadow. The app must not start with zero players on either team.

**MVP sizing target:** Test and tune initially for 2–12 playing devices plus the Host. This is an engineering target, not a promise that every handset can maintain the same number of peer connections. A larger maximum should be enabled only after device-matrix testing confirms stable discovery, concurrent peer links, routing, power use, and delivery latency.

---

## 2. Room creation and lobby

### 2.1 Create Room flow

The Host opens the app and selects **Create Room**. The Host sets:

| Setting | MVP default | Notes |
|---|---:|---|
| Room name | `Shadowhunt Room` | Editable text |
| Hunters | 2 | Minimum 1 |
| Shadows | 3 | Minimum 1 |
| Game duration | 30 minutes | Configurable; suggested range 5–120 minutes |
| Scheduled Shadow ping interval | 3 minutes / 180 seconds | Fixed default; Host may choose 1–5 minutes in advanced settings later |
| Zone type | Circle | Polygon can be added after MVP |
| Boundary warning distance | 25 metres | Distance from the zone boundary, not from its centre |
| Elimination buffer | 25 metres outside boundary | Requires reliable location fixes; configurable |
| Boundary grace period | 20 seconds | Configurable; prevents GPS drift from causing immediate elimination |
| QR capture | Required | No manual capture button in normal mode |
| Connectivity | Automatic hybrid | Internet + peer-to-peer local networking |

The app generates:
- A short room code (human-friendly, not a security credential).
- A one-time or short-lived join invitation/token.
- A QR invite that encodes the join invitation (optional but recommended).

A room code by itself MUST NOT be sufficient to impersonate the Host or join an already-running game. Joining must complete an authenticated handshake.

### 2.2 Joining a room

A player selects **Join Room** and joins using one of the available methods:
- Scan the Host's room-invite QR code.
- Enter the room code and invitation PIN/token.
- Discover the nearby Host through the local peer-to-peer discovery screen and request to join.

The app MUST support joining without internet when the Host is reachable locally. If a room was created through the online service, a local invite token must still let a nearby offline player join through the local peer connection.

Joining process:
1. Discover Host or reach the room service.
2. Exchange app/protocol versions and room invitation proof.
3. Establish an encrypted peer session or authenticated server session.
4. Verify the invitation and room status.
5. Create a room-specific player identity and key pair if needed.
6. Add the player to the lobby as `UNASSIGNED`.
7. Host assigns the player to Hunter or Shadow.
8. Player sees assigned role and lobby status.

The Host may reject a join request. A player cannot silently change their role after assignment.

### 2.3 Lobby screen

The lobby MUST show:
- Room name and room code.
- Hunter and Shadow counts, configured versus joined.
- Player list with display name and assigned role.
- Connection mode/status for each player (`Internet`, `Local`, `Relayed`, `Connecting`, `Disconnected`).
- Whether the player has location permission and a usable location fix.
- Whether the player's app is ready.
- Whether the device can relay messages (when relevant).
- Host identity.
- Game settings summary.

The Host can reassign roles before the match starts. The Host cannot change team counts so that they no longer match the lobby assignments without resolving the assignments first.

### 2.4 Ready checks

Each player must explicitly press **Ready**. The app must not mark a player ready automatically.

A player can become ready only if:
- The player has an assigned role.
- Required local-network permissions are granted.
- Location permission is granted at the required precision.
- Device location services are enabled.
- A fresh, usable location fix has been obtained.
- The app has verified the player's connectivity path to the Host (directly or through a relay).
- The player has viewed/accepted the safety notice for the current game.

The Host sees readiness as `READY` or a specific blocking reason. The Host can cancel or remove a player from the lobby. The game cannot start until all included Hunters and Shadows are ready.

### 2.5 Start sequence

1. Host taps **Start Game**.
2. Host app performs a final readiness check.
3. Host distributes a signed `GAME_STARTING` event with a future start timestamp and session epoch.
4. All devices acknowledge receipt.
5. A visible 5-second countdown begins.
6. At the agreed start instant, match state becomes `ACTIVE` and the timer starts.

If a required participant does not acknowledge the start event before the countdown begins, the Host must cancel the countdown and show the disconnected player. The Host can remove that player or wait for reconnection.

---

## 3. Game objective and win conditions

### 3.1 Hunter objective

Catch every active Shadow before the game timer reaches zero.

A Shadow is removed from active play when:
- A valid QR capture is approved by the Host's authoritative game controller; or
- The Shadow is automatically eliminated after a confirmed, sustained, sufficiently large zone violation.

### 3.2 Shadow objective

Remain active and uncaught until the game timer reaches zero.

### 3.3 Win conditions

| Condition | Result |
|---|---|
| Active Shadow count reaches zero before time expires | Hunters win immediately |
| Timer reaches zero and one or more Shadows remain active | Shadows win |
| All Shadows are captured/eliminated at the same authoritative event sequence | Hunters win |
| Host pauses due to a safety or connectivity issue | No winner until resumed or cancelled |
| Host cancels the match | Match cancelled; no winner |

The app must show whether a Shadow was `CAPTURED` or `ELIMINATED`; both remove the Shadow from the active count, but the event log and result screen must distinguish them.

### 3.4 Timer behavior

- The Host is the timer authority.
- Duration is selected before the game starts.
- Timer begins at the agreed `startAt` timestamp.
- Devices display remaining time using a monotonic local clock adjusted by Host time-sync messages.
- A client must not decide that the game has ended solely from its own wall-clock time.
- The Host emits periodic time sync messages, recommended every 10 seconds, and on reconnect.
- When Host time reaches the configured duration, Host commits `GAME_ENDED` with the result.
- A disconnected phone may show an estimated local countdown but must label the game state as stale until it receives an authoritative update.

### 3.5 Pause and resume

The Host can pause for safety, severe connectivity disruption, or an administrative issue.

On pause:
- Timer stops at the Host's committed elapsed time.
- No new captures or automatic eliminations are finalized.
- Location sharing may continue only if required for safety and clearly disclosed; default is to stop game tracking during a manual safety pause.
- All players receive a pause reason.

On resume:
- Host sends a signed `GAME_RESUMED` event and a new authoritative time anchor.
- Players acknowledge the event.
- Timer resumes from the saved elapsed time.

A safety **Emergency Stop** control must be available to every player. It immediately requests a pause from the Host. If the Host is reachable locally, pause is committed immediately. If not, the device shows `Emergency request not yet delivered` and keeps retrying. The app should also show emergency contact/instructions chosen by the organiser; it is not an emergency-services dispatch system.

---

## 4. Location and three-minute ping mechanics

### 4.1 Location concepts

Shadowhunt uses two different location processes:

1. **Local location sampling:** Each phone checks its own position to draw its own marker, calculate distances, and detect zone-boundary conditions.
2. **Hunter location pings:** Active Shadows' latest valid location snapshots are shared with Hunters on the scheduled three-minute cadence, plus defined exceptional events.

The game does not need to publish every GPS fix to every player. This reduces unnecessary traffic, battery use, and location exposure.

### 4.2 Scheduled location ping

Default cadence: every 180 seconds after `GAME_STARTED`.

At each scheduled ping:
1. Each active Shadow obtains the freshest valid location fix available.
2. The phone includes coordinates, accuracy, fix timestamp, and monotonic sequence number.
3. The location message is sent to the Host using direct internet or the local mesh.
4. The Host validates that the Shadow is active and the fix is not stale or out of order.
5. Host creates a signed location-ping event.
6. The Host distributes the ping only to authorized recipients: Hunters and Host.
7. Each Hunter updates the corresponding last-known marker and distance estimate.

If a Shadow has no fresh location fix, the app sends a `LOCATION_UNAVAILABLE` status, not a fabricated coordinate. Hunters see the previous point as stale with its timestamp.

### 4.3 What Hunters see

For each active Shadow, Hunters see:
- Display name or configured alias.
- Last known map/radar position.
- Estimated distance from the Hunter's current location to that last known point.
- Ping timestamp and age.
- Reported horizontal accuracy.
- Status (`Fresh`, `Aging`, `Stale`, `No recent fix`).

The radar MUST NOT animate a Shadow's marker as if it were a live moving position between pings. If the app displays a projected direction or route estimate in a future version, it must be labelled as a prediction, never as a measured position.

### 4.4 Nearest Shadow calculation

Each Hunter's phone uses:
- The Hunter's own freshest current location fix.
- Each Shadow's last known valid ping.

The app calculates approximate straight-line distance using a geodesic distance function (for example, the haversine formula). Display the estimate in metres or kilometres.

The nearest list is local to each Hunter because each Hunter has a different current position. The app should not broadcast Hunters' precise locations to other players merely to calculate this list.

Distance should be labelled approximate. GPS accuracy and movement since the last ping can make the true distance different.

### 4.5 Exceptional immediate pings

The Host must request an immediate Shadow location ping when:
- A Shadow crosses the confirmed zone boundary.
- A Shadow enters the configured outer elimination buffer.
- A Host-authorized safety event requires it.

The app should not send an immediate location update to Hunters for every noisy GPS fluctuation. The boundary algorithm must first confirm a credible violation, as specified below.

### 4.6 Location freshness and stale status

Recommended Hunter UI states:
- `FRESH`: received within one ping interval.
- `AGING`: older than one ping interval but less than two intervals.
- `STALE`: older than two intervals.
- `UNAVAILABLE`: no valid location has been received in the current game.

Use timestamps from the original location fix and delivery time at the Host. Do not replace the original fix time with the relay/server receipt time.

---

## 5. Game zone, warnings, and automatic elimination

### 5.1 Zone selection

MVP supports a circular zone.

The Host selects:
- Centre point (map tap, current GPS position, or coordinate entry).
- Radius in metres.

The map screen must display the circle, centre marker, radius, and a clear confirmation before saving. The Host must be able to review the zone before starting.

**Offline map behavior:** The app must not assume map tiles are available without internet. The Host can select the centre using their current GPS location and a radius even without map tiles. The app must also provide a simple compass/radar/grid representation of the circle. If map tiles are available or downloaded for offline use, they may be displayed. The core game geometry must not depend on a third-party map tile request succeeding.

A later version may support custom polygon zones. Do not implement polygon editing in the first MVP unless it does not delay core multiplayer reliability.

### 5.2 Zone geometry

For a circular zone:
- `centerLat`, `centerLon` define the centre.
- `radiusMeters` defines the permitted circle.
- `distanceFromCenterMeters` is calculated from the player's current fix to the centre.
- `outsideDistanceMeters = max(0, distanceFromCenterMeters - radiusMeters)`.

Use a geodesic calculation, not a flat pixel measurement, for zone enforcement.

### 5.3 Warning behavior

Default warning distance: 25 metres from the boundary.

A player is shown `APPROACHING BOUNDARY` when the player's estimated distance to the boundary is at or below the warning distance, accounting for reported GPS accuracy.

The warning should be visible and audible/vibrating only if the player has enabled those notifications. It should not reveal a Shadow's location to Hunters before a confirmed violation.

### 5.4 Confirmed boundary violation

GPS is noisy. One location fix just outside a circle is not sufficient for elimination.

The app should confirm a boundary crossing only when all of these conditions hold:
- Location fix is sufficiently accurate for the configured rule.
- The conservative position estimate is outside the permitted boundary.
- At least 3 consecutive valid fixes indicate an outside position.
- Those fixes span at least 10 seconds.

A conservative outside test is:

`distanceFromCenterMeters - horizontalAccuracyMeters > radiusMeters`

This avoids treating an uncertainty circle that overlaps the boundary as conclusive evidence of being outside.

On confirmed crossing:
1. The violating player sees `BOUNDARY VIOLATION`.
2. The Host receives a high-priority event.
3. **Every other player is notified that the named Shadow has crossed the zone boundary.** Other Shadows receive the notification/status only; they do not receive the Shadow's coordinates.
4. Hunters and the Host receive an immediate location ping for that Shadow. This is an exception to the normal three-minute ping schedule.
5. The event log records fix timestamps, accuracy, and boundary calculation inputs.
6. The elimination grace period begins.

### 5.5 Automatic elimination rule

Default values:
- Outer elimination buffer: 25 metres beyond the permitted boundary.
- Grace period: 20 seconds after a confirmed crossing.

A Shadow is automatically eliminated when the confirmed boundary-violation grace period expires and the Shadow has not returned inside the zone. The configured outer buffer makes the rule stricter for a player who moves substantially farther outside: if the conservative position is beyond `radiusMeters + eliminationBufferMeters` for 3 valid fixes spanning at least 10 seconds, the remaining grace period is reduced to 5 seconds.

Default behavior therefore is:
- Just outside the boundary: 20-second return grace period.
- At least 25 metres beyond the boundary, confirmed for 10 seconds: 5-second final return grace period.
- If the player returns inside before the applicable grace period expires, the violation clears.
- If the player remains outside when the applicable grace period expires, Host commits `PLAYER_ELIMINATED` and every player is notified.

The Host can configure these values before the match. The UI must show the exact rule to every player before Ready is accepted. The app must not eliminate a player from a single noisy GPS fix or solely because GPS/network service is unavailable.

**No elimination based solely on missing GPS or missing network connectivity.** If GPS quality is insufficient, the state becomes `LOCATION_UNCERTAIN`. If network is unavailable, the violation event is queued and marked pending; the app must not pretend it has been globally confirmed until the Host has accepted it.

### 5.6 Re-entry

If a player returns inside the zone before automatic elimination is committed:
- The violation/grace timer is cancelled.
- Player status returns to `ACTIVE` after enough valid inside fixes.
- Host and Hunters receive a `BOUNDARY_CLEARED` update if a violation had already been announced.

If a player has already been eliminated, re-entry does not restore them. A Host may only reverse an elimination through an explicit, logged administrative correction before the match ends.

### 5.7 Hunter boundary violations

The Host configures the same boundary behavior for Hunters. MVP behavior should be warning + Host notification. Automatic Hunter elimination is optional and disabled by default. This prevents a Hunter being unfairly removed due to GPS drift and keeps the Shadow elimination rule as the primary boundary mechanic.

---

## 6. QR capture mechanic

### 6.1 Shadow QR screen

Every active Shadow has a QR screen containing a session-specific, short-lived signed token. The QR must not contain a permanent public account identifier or raw personal information.

Recommended QR fields:
- Protocol version.
- Room/session identifier.
- Player ID.
- Token time window or issued timestamp.
- Random nonce.
- Signature from that Shadow's session private key.

The app should refresh the QR token periodically (recommended every 20–30 seconds). The Host stores the player's public key and checks the signature and freshness. This makes a screenshot less useful than a permanent static QR, although a rotating QR is not perfect physical-presence proof.

### 6.2 Hunter QR scanner

The Hunter opens **Scan to Catch** and scans the Shadow's QR code.

The scanner must:
- Reject malformed QR data.
- Reject a QR for another room/session.
- Reject a player who is not an active Shadow.
- Reject expired tokens or previously used nonces.
- Ask the Hunter to confirm the identified Shadow before submitting.
- Create a signed `CAPTURE_REQUEST` event.

### 6.3 Capture authority

The Host is the MVP game authority. A scan is not final merely because the Hunter's camera reads a QR code.

Capture flow:
1. Hunter scans QR.
2. Hunter app validates basic format and room/session.
3. Hunter confirms the target name.
4. Hunter app sends `CAPTURE_REQUEST` to Host via direct internet, local mesh, or a gateway.
5. Host verifies signature, role, active status, token freshness, and nonce uniqueness.
6. Host commits `SHADOW_CAPTURED` once, assigning a unique event sequence.
7. Host distributes the event to all players.
8. Hunter sees `CAPTURE CONFIRMED`; Shadow sees `YOU HAVE BEEN CAUGHT`; everyone else sees who caught whom.

### 6.4 Offline capture

If the Hunter is offline but has a mesh route to the Host, capture works normally through the mesh.

If the Hunter has no route to the Host:
- The app records the scan locally as `CAPTURE_PENDING`.
- It shows that the capture has not yet been confirmed.
- It retries when a route returns.
- The Shadow is not globally eliminated until the Host commits the capture.

This is necessary because two isolated devices cannot reliably agree on one global game state without a communication path.

### 6.5 Duplicate and simultaneous scans

The Host must make capture processing idempotent.

If two Hunters scan the same Shadow nearly simultaneously:
- The first valid request committed by the Host wins.
- Later requests receive `TARGET_ALREADY_CAPTURED` and must not change the result.
- The event log preserves all requests for diagnostics.

A capture must never be counted twice.

### 6.6 Capture fairness

The QR mechanic requires the Hunter to be physically close enough to scan the Shadow's displayed code. The app should not use GPS distance alone as proof of capture.

A future version may add an optional short-range confirmation challenge (for example, a locally exchanged one-time challenge) to make remote screenshot sharing harder. This is not required for MVP.

---

## 7. Notifications and event feed

Every player receives the events relevant to their role.

Examples:
- `Game starts in 5 seconds`.
- `Game started`.
- `Shadow 2 location updated` (Hunters and Host only).
- `Shadow 2 is approaching the zone boundary` (Shadow and Host; Hunters only if a confirmed violation creates a ping).
- `Shadow 2 crossed the zone boundary` (everyone receives the event; only Hunters and Host receive the precise location ping).
- `Hunter 1 caught Shadow 3` (everyone).
- `Shadow 3 eliminated for leaving the zone` (everyone).
- `Player disconnected` / `Player reconnected` (Host and relevant players).
- `Game paused` / `Game resumed`.
- `Hunters win` / `Shadows win`.

Notifications must be driven by committed game events, not by arbitrary local UI changes. The app should avoid duplicate banners when the same event is delivered via both a direct internet route and a mesh relay.

---

# PART II — USER INTERFACE AND APP FLOW

## 8. Main screens

### 8.1 Home screen

Actions:
- Create Room.
- Join Room.
- How to Play.
- Settings.

Show a small connectivity summary: Internet available/unavailable, nearby-device capability, Bluetooth/Wi-Fi permissions, and location services.

### 8.2 Create Room screen

Fields:
- Room name.
- Number of Hunters.
- Number of Shadows.
- Game duration.
- Ping interval (180 seconds default).
- Boundary warning distance.
- Elimination buffer and grace time.
- Zone selection.

Show the total required player count before proceeding.

### 8.3 Join Room screen

Methods:
- Scan room QR.
- Enter room code and invite PIN.
- Find nearby room/Host.

Show whether the connection is direct internet or local peer-to-peer. Do not label an unverified nearby device as a trusted Host.

### 8.4 Lobby screen

Player list, team assignments, role labels, readiness, location status, and network status. Host controls role assignment and Start Game. Players have Ready/Not Ready controls.

### 8.5 Zone selection screen

Map/radar with circular zone. Host can set centre and radius, then preview the boundary. If map tiles are unavailable, the screen remains usable through current-location centre selection, coordinate entry, radius control, and a simple grid visualization.

### 8.6 Hunter game screen

Primary elements:
- Remaining timer.
- Map/radar.
- Own location marker.
- Game-zone boundary.
- Last-known Shadow markers.
- Last-ping age and accuracy.
- Nearest Shadow card/list.
- Active Shadows remaining.
- Player list.
- Scan QR button.
- Connectivity quality and stale-data indicator.
- Emergency Stop.

The app must clearly distinguish the Hunter's live local position from a Shadow's last-known position.

### 8.7 Shadow game screen

Primary elements:
- Remaining timer.
- Own live location marker.
- Game-zone boundary.
- Distance to boundary.
- Boundary warning state.
- Personal rotating QR code.
- Active Shadows remaining (optional, based on Host settings).
- Connectivity status.
- Emergency Stop.

No Hunter markers or Hunter distance data are shown.

### 8.8 Host control screen

Primary elements:
- Timer and game status.
- All players and roles.
- Last location fix and network status for each player.
- Zone map/radar.
- Active/captured/eliminated counts.
- Event feed.
- Pause, resume, and end-game controls.
- Gateway/mesh health summary (advanced diagnostics may be hidden from normal users).

### 8.9 Results screen

Show:
- Winning team.
- Final duration.
- Hunters and Shadows.
- Capture/elimination summary.
- Event timeline.
- Network quality summary (optional, for debugging).

Do not expose unnecessary location history in the results screen. Location history should be deleted or retained only according to an explicit retention policy.

---

## 9. Player and game state machine

### 9.1 Player state

A player can be in one of these states:

- `INVITED`
- `JOIN_REQUESTED`
- `LOBBY_UNASSIGNED`
- `LOBBY_ASSIGNED`
- `READY`
- `ACTIVE`
- `LOCATION_UNCERTAIN`
- `CONNECTION_DEGRADED`
- `DISCONNECTED_GRACE`
- `CAPTURE_PENDING`
- `CAPTURED`
- `ELIMINATED`
- `LEFT`

Connectivity and game status are separate dimensions. For example, a player may be `ACTIVE` and `CONNECTION_DEGRADED` at the same time.

### 9.2 Room/game state

- `ROOM_CREATED`
- `LOBBY_OPEN`
- `START_CHECK`
- `COUNTDOWN`
- `ACTIVE`
- `PAUSED`
- `HOST_UNAVAILABLE`
- `COMPLETED_HUNTERS_WIN`
- `COMPLETED_SHADOWS_WIN`
- `CANCELLED`

Only the Host's authoritative game controller can commit transitions to `ACTIVE`, `PAUSED`, `COMPLETED_*`, or `CANCELLED`.

### 9.3 Important transition rules

- A room cannot enter `COUNTDOWN` until every included player is assigned and ready.
- `ACTIVE` starts only at the authoritative start timestamp.
- A Shadow can only transition from `ACTIVE` to `CAPTURED` or `ELIMINATED` once.
- A captured/eliminated player cannot return to `ACTIVE` during normal play.
- `COMPLETED_HUNTERS_WIN` occurs when active Shadow count becomes zero.
- `COMPLETED_SHADOWS_WIN` occurs when timer expires and active Shadow count is greater than zero.
- A paused game cannot process final captures/eliminations until resumed, except the Host may preserve a pending event for resolution after resume.

---

# PART III — CONNECTIVITY AND NETWORK ARCHITECTURE

## 10. Core networking principles

### 10.1 Connectivity is not the same as game role

A Hunter can be a gateway. A Shadow can be a gateway. A Host can have internet or no internet. A device's role in the game does not determine its networking role.

Networking roles are dynamic:
- **Internet client:** Has a working route to the Shadowhunt service.
- **Gateway:** Has a working service connection and at least one local peer connection; forwards messages between the service and the local mesh.
- **Relay node:** Has no internet but forwards messages between connected peers.
- **Leaf node:** Participates in the local mesh but may currently have only one peer connection.
- **Host authority:** Runs the room's authoritative game controller. The Host may also be an internet client, gateway, or relay.

One device may have multiple networking roles at once.

### 10.2 What “automatic Bluetooth/Wi-Fi” means

The app should not ask ordinary players to choose Bluetooth, BLE, or Wi-Fi Direct manually.

The transport layer attempts to establish local peer connections using the platform's supported mechanisms. On Android, the first implementation should evaluate **Google Nearby Connections with the `P2P_CLUSTER` strategy**. Nearby Connections abstracts over Bluetooth, BLE, and Wi-Fi technologies and supports peer-to-peer data exchange without internet. Its Cluster strategy supports an M-to-N cluster topology suited to small multiplayer groups.

Nearby Connections creates direct peer links; it does **not** automatically forward a message from Phone A through Phone B and Phone C to Phone D. That multi-hop behavior is Shadowhunt's application-level responsibility and is specified below.

### 10.3 Direct internet path and local mesh path

Every message is sent using the best currently available path:

1. If sender has a working authenticated connection to the service, send directly over the server connection.
2. If sender has a local route to the Host, send directly through the peer mesh to the Host.
3. If sender has no direct service connection but a mesh route to a gateway exists, relay through the mesh to that gateway; gateway forwards to the service.
4. If no route exists, queue eligible messages locally and retry after route recovery.

The system should prefer a direct, low-latency route when it is healthy, but it must deduplicate messages because the same event may reach the Host through more than one path.

### 10.4 Mixed connectivity example

```text
                         SHADOWHUNT CLOUD RELAY
                           /                 \
                    Internet WS          Internet WS
                        |                     |
                 Phone D (Gateway)      Phone G (Gateway)
                        |                     |
                   Local P2P             Local P2P
                     /   \                 /   \
               Phone C  Phone E       Phone F  Phone H
                  |        |              |        |
               Phone B   Phone A        Phone I   Phone J
```

The diagram is illustrative. Actual peer links depend on radio range, device capability, and the transport API. A phone does not have to connect to every other phone.

### 10.5 Host authority and cloud responsibilities

**MVP decision:** The Host phone is the single authoritative game controller. This avoids two competing authorities when part of the group is offline.

The cloud service provides:
- Room invitation/signalling when internet is available.
- Persistent WebSocket relay sessions for internet-connected devices.
- Message relay between connected clients/gateways and the Host.
- Optional event-log backup and match history.
- Optional player/account services.

The cloud service does not independently decide whether a QR capture or zone elimination is valid during an active match. It forwards authenticated messages and may store a mirror of Host-committed events.

The Host's local game engine validates events and increments the room's authoritative event sequence. If the Host itself has no internet, the room can still play as long as players can reach the Host through local peer-to-peer links.

If the Host cannot be reached by a disconnected network partition, that partition cannot receive authoritative global game decisions until a path returns. The app must show pending/stale state rather than claim that the event has been globally committed.

---

## 11. Android-first transport choice

### 11.1 Recommended MVP stack

- **Language:** Kotlin.
- **UI:** Jetpack Compose.
- **Local peer discovery and connection:** Google Nearby Connections API, using Cluster strategy for the initial multi-peer prototype.
- **Internet connection:** HTTPS for REST/bootstrap and WebSocket for live sessions.
- **Location:** Android location APIs / Fused Location Provider where available, with platform location fallback if necessary.
- **QR scanning:** CameraX plus a maintained barcode/QR decoding library.
- **Local persistence:** Room database (SQLite) for pending messages, local session state, and event outbox.
- **Serialization:** Protocol Buffers or CBOR for production envelopes; JSON may be used during early prototyping.
- **Backend:** TypeScript/Node.js with Fastify and WebSocket support; PostgreSQL for durable rooms and match records; Redis only if multi-instance fan-out or rate coordination becomes necessary.

These are recommended implementation choices, not requirements imposed by the game rules.

### 11.2 Why Nearby Connections

Nearby Connections is a strong first candidate because it can discover, connect, and exchange data with nearby devices without internet and can use Bluetooth, BLE, and Wi-Fi technologies under the hood. It offers `P2P_CLUSTER`, `P2P_STAR`, and `P2P_POINT_TO_POINT` connection strategies. Use Cluster for the initial multi-peer network experiment, because Shadowhunt needs peers to connect to more than one other device.

The API requires Google Play services on Android and relevant permissions. Device support, concurrent connection limits, radio behavior, and background reliability must be tested on real handsets. Do not promise a fixed radio range or fixed number of simultaneous peers.

### 11.3 Wi-Fi Direct and transport abstraction

The app should implement a transport abstraction so that the game protocol does not depend directly on one radio API.

Suggested interface:

```kotlin
interface PeerTransport {
    suspend fun startDiscovery(roomId: String)
    suspend fun stopDiscovery()
    suspend fun startAdvertising(roomId: String)
    suspend fun stopAdvertising()
    suspend fun connect(peer: DiscoveredPeer): Result<PeerConnection>
    suspend fun send(peerId: String, frame: ByteArray): SendResult
    fun observePeerEvents(): Flow<PeerEvent>
    suspend fun disconnect(peerId: String)
}
```

Implement `NearbyConnectionsTransport` first. A separate `WifiDirectTransport` may be added if testing shows a device/API combination needs it. Do not attempt to maintain independent Bluetooth, BLE, and Wi-Fi Direct connections to every peer simultaneously in MVP; this can multiply complexity and drain battery.

The transport manager chooses an available implementation and reports its status to the routing layer. The routing layer sees peer connections and byte frames, not Bluetooth-specific details.

### 11.4 iOS later

Do not promise full iOS support in the first Android release. Nearby Connections has platform support information for Android and iOS, but the exact iOS SDK lifecycle, permissions, background behavior, and Android-to-iOS interoperability must be separately validated before launch. Apple currently marks Multipeer Connectivity deprecated in its documentation, so do not base a new long-term iOS implementation on it without reviewing Apple's current migration guidance.

---

## 12. Multi-hop mesh routing layer

### 12.1 Required behavior

The routing layer must let a message travel over multiple local peer links:

`Phone A → Phone B → Phone C → Internet Gateway D → Cloud/Host`

Every phone may:
- Advertise itself for discovery.
- Discover nearby Shadowhunt peers.
- Maintain one or more direct peer connections, within tested platform limits.
- Forward eligible frames it receives.
- Be both a sender and relay at the same time.

A peer connection is only one hop. Each intermediate phone must explicitly receive, validate, and forward a frame.

### 12.2 Recommended MVP routing algorithm: gateway advertisement + path-vector

For the small, local game network, implement a simple path-vector routing scheme before considering a general-purpose mesh protocol.

A gateway is recognized only after it has successfully authenticated to the Shadowhunt service and received a server acknowledgement. Merely having a cellular/Wi-Fi network icon is not enough.

A gateway periodically advertises:
- Gateway ID.
- Room/session ID.
- Monotonic route sequence.
- Path list (peer IDs already traversed).
- Hop count.
- Route quality estimate.
- Advertisement timestamp/expiry.

Each receiving peer:
1. Verifies that the room/session matches.
2. Rejects an advertisement whose path already contains its own peer ID.
3. Adds the sending peer as the next hop.
4. Increments hop count and appends its own peer ID before forwarding.
5. Stores a candidate route to that gateway.
6. Selects the best healthy route according to route metric.
7. Forwards the advertisement to eligible neighbors, with a bounded hop limit.

**Default route selection order:**
1. Route is healthy and recently confirmed.
2. Fewer hops.
3. Better measured delivery success/latency.
4. Prefer a gateway with a stable service connection.

Do not use physical distance alone to choose routes. A farther peer with a reliable path may be better than a nearby peer whose route ends at another offline device.

### 12.3 Route health and expiry

Suggested initial parameters for testing (tune after field measurements):
- Gateway advertisement interval: 5 seconds.
- Peer-link heartbeat: 5 seconds.
- Mark link degraded after 2 missed heartbeats.
- Mark link unavailable after 3 missed heartbeats or transport disconnect callback.
- Gateway route expiry: 15 seconds without a fresh advertisement.
- Message hop limit: 8 for MVP; make configurable.

These are engineering starting values, not promises about radio timing. Avoid overly frequent scans or keepalives that drain battery.

### 12.4 Alternate routes

Store multiple candidate routes where possible. When the preferred next hop disappears:
1. Mark that neighbor unavailable.
2. Invalidate any route whose next hop is that neighbor.
3. Pick the next healthy route to a gateway or Host.
4. If no route exists, initiate route discovery/advertisement wait.
5. Queue eligible outbound messages.
6. Resume delivery when a route is restored.

Use hysteresis to avoid switching between equally good routes on every small quality fluctuation.

### 12.5 Loop prevention and duplicate suppression

Every message has a globally unique `messageId` and a bounded `hopLimit`.

Each device maintains a short-lived `seenMessageIds` cache. If a `messageId` has already been processed, the relay must not forward it again, except when an explicit retry/acknowledgement protocol requires it.

A relay must:
- Reject frames with invalid size or schema.
- Reject frames for another room/session.
- Reject expired frames.
- Reject frames with exhausted hop limit.
- Reject duplicates.
- Decrement hop limit before forwarding.
- Preserve the original sender identity and signature.
- Never rewrite the signed game payload.

### 12.6 Store-and-forward

If a destination route is temporarily missing, queue messages according to their policy.

Messages are not all equal:

| Message class | Policy | Queue behavior |
|---|---|---|
| Current location | Latest-value-wins | Replace older unsent location for same player/session |
| Scheduled ping | Bounded reliable delivery | Keep the newest scheduled snapshot; do not send a backlog as if live |
| Capture request | Reliable, deduplicated | Keep until Host acknowledgement or expiry |
| Capture confirmation | Reliable event | Retry until acknowledged by connected recipients |
| Zone violation | High priority, reliable | Keep evidence and deliver as soon as a path returns |
| Timer sync | Latest-value-wins | Discard obsolete sync frames |
| Lobby assignment/readiness | Latest-value-wins plus acknowledgement | Keep current state |
| Heartbeat | Best effort | Drop old heartbeats |
| Emergency pause request | Highest priority, reliable retry | Surface `pending delivery` until Host acknowledgement |

A delayed location must always retain its original capture timestamp. The UI must never present it as a current point merely because it was just delivered.

### 12.7 No-route partitions

If a group of phones has no route to the Host or an authenticated internet gateway:
- Those phones may still exchange messages within their local partition.
- They cannot reach the authoritative Host until a route reconnects.
- Pending captures and rule events are not globally confirmed.
- The app must show a clear disconnected/queued status.
- No player is automatically eliminated solely because connectivity disappears.

The Host may pause the match if a player or partition remains unreachable beyond the configured connectivity grace period. The default should be a warning and a Host decision, not automatic elimination.

---

## 13. Message transport and protocol

### 13.1 Unified message envelope

All gameplay data travels through one message envelope format, whether its next hop is a local peer, internet gateway, or cloud service.

Recommended logical fields:

```json
{
  "protocolVersion": 1,
  "messageId": "uuid",
  "roomId": "room-id",
  "sessionEpoch": 1,
  "type": "LOCATION_UPDATE",
  "originPlayerId": "player-id",
  "originDeviceKeyId": "key-id",
  "target": "HOST",
  "createdAtUtc": "2026-10-30T10:00:00Z",
  "expiresAtUtc": "2026-10-30T10:00:30Z",
  "sequenceNumber": 42,
  "hopLimit": 8,
  "payload": {},
  "signature": "base64-signature"
}
```

This is an illustrative JSON representation. Production may use Protocol Buffers or CBOR. The signature must cover a canonical serialization of the immutable origin fields and payload. Hop-by-hop routing metadata should be separated from the origin-signed payload so relays can decrement hop count without invalidating the sender signature.

### 13.2 Message categories

At minimum implement:

**Room/lobby**
- `ROOM_INVITE`
- `JOIN_REQUEST`
- `JOIN_ACCEPTED`
- `JOIN_REJECTED`
- `PLAYER_ASSIGNED`
- `PLAYER_READY_CHANGED`
- `LOBBY_SNAPSHOT`

**Game lifecycle**
- `START_CHECK`
- `GAME_STARTING`
- `GAME_STARTED`
- `GAME_PAUSED`
- `GAME_RESUMED`
- `GAME_ENDED`
- `GAME_CANCELLED`
- `TIME_SYNC`

**Location and zone**
- `LOCATION_UPDATE`
- `LOCATION_PING_COMMITTED`
- `LOCATION_UNAVAILABLE`
- `BOUNDARY_APPROACHING`
- `BOUNDARY_VIOLATION`
- `BOUNDARY_CLEARED`
- `AUTO_ELIMINATION_PENDING`
- `PLAYER_ELIMINATED`

**Capture**
- `CAPTURE_REQUEST`
- `CAPTURE_PENDING`
- `CAPTURE_APPROVED`
- `CAPTURE_REJECTED`
- `SHADOW_CAPTURED`

**Networking**
- `PEER_HELLO`
- `PEER_AUTH_CHALLENGE`
- `PEER_AUTH_RESPONSE`
- `PEER_HEARTBEAT`
- `GATEWAY_ANNOUNCEMENT`
- `ROUTE_WITHDRAWAL`
- `DELIVERY_ACK`
- `ROUTE_ERROR`

**Safety/admin**
- `EMERGENCY_PAUSE_REQUEST`
- `HOST_PAUSE_NOTICE`
- `ADMIN_CORRECTION`

### 13.3 Reliability and acknowledgements

Messages with reliable delivery require an acknowledgement from the final logical recipient, not merely the next-hop relay.

Example:
- A sends capture request to B.
- B forwards to C.
- C forwards to Gateway D.
- D sends to cloud.
- Host receives and validates.
- Host sends `DELIVERY_ACK`/capture result.
- The result travels back through the mesh.

Intermediate relays may send transport-level acknowledgements, but those do not mean the game event was committed.

### 13.4 Message size

Keep routine game messages small. Location events, heartbeats, capture requests, and notifications should generally be a few kilobytes or less. Do not send QR images or map tiles through the mesh. Nearby Connections byte payloads have a documented size limit; verify the selected SDK's current limit and split larger content only if a later feature genuinely requires it.

### 13.5 Ordering

The Host assigns a monotonically increasing `eventSequence` to every committed game event.

Clients apply Host events in sequence order. If a client receives sequence 105 after sequence 103, it should request/retrieve the missing event(s) or a fresh game snapshot before applying dependent state.

Player-originated telemetry may arrive out of order. For each player, accept a location update only if its sequence and original timestamp are newer than the currently stored fix and within the allowed freshness window.

---

## 14. Internet gateway behavior

### 14.1 Becoming a gateway

A device may become a gateway automatically when:
- It has a working internet path.
- It has an authenticated WebSocket session to the Shadowhunt service.
- It is permitted to forward local game traffic.
- It has at least one healthy local peer connection or can accept one.

A device can stop being a gateway when internet is lost. The routing layer then withdraws its gateway advertisement.

### 14.2 Gateway responsibilities

The gateway:
- Maintains a persistent authenticated WebSocket to the service.
- Receives messages from nearby mesh peers.
- Validates envelope framing and room membership but does not change signed game payloads.
- Forwards authorized frames to the cloud relay.
- Receives cloud frames destined for devices reachable through its mesh.
- Routes those frames to the correct next hop.
- Reports delivery status and route health.

The gateway is a transport bridge, not a privileged Hunter or game administrator.

### 14.3 Multiple gateways

A room may have multiple gateways. The routing layer should use the best available gateway and retain alternatives.

The cloud relay must deduplicate by `messageId`, because a sender may deliver the same event over a direct internet path and a mesh gateway path.

If several gateways can reach the same recipient, the service may fan out to more than one gateway. The recipient deduplicates the event.

### 14.4 Cloud relay routing

The cloud service maintains short-lived reachability information:
- Room ID.
- Player ID.
- Active direct WebSocket session, if any.
- Gateway session(s) that recently advertised reachability to that player/mesh.
- Last-seen time and route expiry.

For a message to the Host or a specific player:
1. Deliver directly if the recipient has a direct service connection.
2. Otherwise deliver through one or more recently healthy gateway tunnels that advertised a route to the recipient.
3. If no gateway route exists, queue only messages whose policy permits delayed delivery.
4. Return a route-unavailable status for real-time messages that cannot be delivered.

The cloud service must not claim a message reached the device until the final device or Host acknowledges it.

---

## 15. Security, integrity, and anti-cheat

### 15.1 Threat model

Assume a player may:
- Attempt to join a room without permission.
- Pretend to be another player.
- Replay an old location or capture message.
- Forward a message multiple times.
- Try to view the other team's location data.
- Share a screenshot of a QR code.
- Disable location or network access.
- Run a modified device or mock-location tool.
- Act as an unreliable or malicious relay.

The app cannot guarantee that a consumer phone's GPS reading is truthful. It can reduce casual cheating, detect suspicious patterns, and make limitations visible, but it cannot make GPS spoofing impossible on every device.

### 15.2 Identity and keys

At room join:
- Generate or load a device signing key pair for the session.
- Host registers the player's public key.
- Host issues a signed room membership credential binding `roomId`, `sessionEpoch`, `playerId`, role, public key, and expiry.
- Peer connections authenticate the room membership before relaying gameplay messages.

Use a vetted cryptographic library and established primitives. Do not invent encryption, signature, or key-exchange algorithms.

### 15.3 End-to-end confidentiality

A relay must not automatically gain permission to read the content it forwards.

Use application-layer encryption for sensitive payloads, in addition to transport encryption:
- Shadow location payloads are encrypted for the Host and authorized Hunter audience.
- Hunter location is not distributed to Shadows.
- Private player/device information is not broadcast to the room.
- Relays forward ciphertext and routing headers only.

The Host is trusted as the MVP session authority and may see all player positions for game administration and safety. This must be stated in the privacy notice before a player joins.

### 15.4 Message authenticity and replay protection

- Origin player signs immutable message content.
- Host validates the signature before accepting an event.
- Every message has a unique ID.
- Every player-originated stream has a monotonic sequence number.
- Capture tokens have a short validity window and single-use nonce.
- Expired messages are rejected for live gameplay.
- Relays cannot change `originPlayerId`, role, target, location, or event payload without invalidating the signature.

### 15.5 Server and gateway security

- Use TLS for every cloud connection.
- Authenticate WebSocket sessions.
- Authorize every room message against room membership and role.
- Rate-limit join requests, scans, and message submissions.
- Validate payload size and schema.
- Avoid logging raw coordinates or QR token contents in general application logs.
- Do not trust a gateway's statement about another player's identity; verify the origin signature.

### 15.6 QR security

Use a signed, rotating session token. Host stores public keys and used nonces. A token must be bound to the current room, session epoch, and Shadow ID. Old tokens and cross-room tokens are rejected.

### 15.7 GPS spoofing and fairness limits

Possible anomaly checks:
- Impossible speed between consecutive fixes.
- Sudden large jumps.
- Repeated identical coordinates for implausibly long periods.
- Location accuracy that is persistently poor.
- Mock-location indicator where the OS exposes it.

These checks are warnings for Host review, not proof of cheating. Avoid automatic capture or elimination based only on an anomaly score.

---

# PART IV — LOCATION, BACKGROUND EXECUTION, AND DEVICE BEHAVIOR

## 16. Location implementation

### 16.1 Location sampling

During an active game, request location updates at a battery-conscious cadence suitable for the zone monitor. Initial target: one fix every 3–5 seconds while the app's active-game foreground service is running, adapting to movement and accuracy.

This sampling cadence is for local boundary monitoring and the player's own map. It does not mean Hunters receive live Shadow positions every 3–5 seconds.

### 16.2 Accuracy handling

Every fix must include:
- Latitude and longitude.
- Horizontal accuracy radius in metres.
- Fix timestamp.
- Provider/source if available.
- Speed/bearing if available and relevant.
- Mock-location flag if exposed by OS.

Do not interpret a coordinate without its accuracy. Do not use poor-accuracy fixes to automatically eliminate a player.

### 16.3 Permissions

Request permissions contextually and explain why they are required:
- Precise location for the player's position, zone boundary, and location pings.
- Nearby-device/Bluetooth permissions required by the selected local transport.
- Camera permission for QR scanning.
- Notifications permission where applicable.

If a required permission is denied, explain which gameplay feature cannot work and prevent the player from becoming Ready. Do not repeatedly prompt after denial; provide a Settings route.

### 16.4 Foreground game session

The game should be explicitly started by the user while the app is visible. During an active match, Android should use the appropriate foreground service type for location, display a persistent notification, and comply with the OS version's foreground-service and while-in-use permission requirements.

The app must handle:
- Screen off.
- App switching.
- Battery saver.
- Permission revocation.
- Location services disabled.
- Bluetooth/Wi-Fi toggled off.
- Process death and app restart.

Do not promise unrestricted background operation. OS and device-vendor policies can suspend scanning or networking. The game UI should explain when a player must keep the app active or when the session is degraded.

### 16.5 GPS without internet

The app can use device location services without an internet connection, but location availability and accuracy vary. GPS reception can be poor indoors, between tall buildings, or under obstruction. A device's fused location may use available satellite, Wi-Fi, cellular, and sensor inputs; when those network sources are absent, the fix may take longer or be less accurate.

The app must show `Searching for GPS`, `Low accuracy`, or `Location unavailable` rather than pretending that precise positioning is always available.

---

# PART V — BACKEND AND DATA MODEL

## 17. Backend scope

The backend is needed for internet-connected room discovery, WebSocket relay, optional accounts, and match history. It is not needed for GPS calculation or local peer-to-peer transport.

Recommended services:

1. **API service:** Room invitation, authentication, player profile, match metadata.
2. **WebSocket relay:** Online client and gateway connections; forwards signed encrypted envelopes.
3. **Database:** Durable room metadata, membership, event mirror, match results.
4. **Optional Redis:** Pub/sub or presence coordination if the service runs multiple instances.

The Host remains the authority for game events in MVP. The backend stores Host-committed event records when online and can reconcile them when the Host reconnects.

### 17.1 Suggested backend technology

- TypeScript / Node.js.
- Fastify for HTTP APIs.
- WebSocket plugin or a dedicated WebSocket server.
- PostgreSQL for durable records.
- Redis only when scaling requires cross-instance fan-out or shared presence.

### 17.2 Suggested database entities

**users**
- `id`
- `display_name`
- `created_at`

**rooms**
- `id`
- `room_code_hash`
- `host_player_id`
- `status`
- `created_at`
- `expires_at`

**game_sessions**
- `id`
- `room_id`
- `session_epoch`
- `duration_seconds`
- `ping_interval_seconds`
- `zone_type`
- `zone_center_lat`
- `zone_center_lon`
- `zone_radius_meters`
- `started_at`
- `ended_at`
- `result`

**session_players**
- `id`
- `session_id`
- `player_id`
- `display_name`
- `role`
- `public_key`
- `status`
- `joined_at`
- `left_at`

**game_events**
- `id`
- `session_id`
- `event_sequence`
- `event_type`
- `origin_player_id`
- `payload_ciphertext` or minimal approved event payload
- `created_at`
- `committed_at`

**capture_records**
- `id`
- `session_id`
- `shadow_player_id`
- `hunter_player_id`
- `capture_event_sequence`
- `captured_at`
- `status`

**gateway_presence** (ephemeral/cache table, or Redis)
- `room_id`
- `gateway_player_id`
- `connection_id`
- `last_seen_at`
- `reachable_peer_summary`

Avoid a permanent location-history table in MVP. If diagnostics require location records, retain only the minimum data for a short, disclosed period and restrict access.

### 17.3 Suggested HTTP endpoints

- `POST /v1/rooms` — create a room invitation/metadata record.
- `POST /v1/rooms/{roomId}/join` — request online join.
- `POST /v1/rooms/{roomId}/invite/rotate` — rotate invite credential.
- `GET /v1/rooms/{roomId}` — fetch allowed room metadata.
- `POST /v1/sessions/{sessionId}/snapshot` — upload Host-committed state snapshot.

### 17.4 WebSocket events

- `AUTHENTICATE`
- `ROOM_SUBSCRIBE`
- `RELAY_FRAME`
- `RELAY_ACK`
- `PRESENCE_UPDATE`
- `GATEWAY_REACHABILITY`
- `HOST_SNAPSHOT`
- `ERROR`

The cloud relay must enforce room membership and message-size limits, but it should not need to decrypt player-to-Host or role-restricted game payloads.

---

## 18. Local persistence and reconnection

Each device stores a local session record containing:
- Room and session IDs.
- Player ID and signed membership credential.
- Last known Host event sequence.
- Last known game state snapshot.
- Pending reliable messages.
- Recently seen message IDs.
- Peer and gateway route cache with expiry.
- Last known location fix (short-lived; delete after session retention policy).

On app restart:
1. Verify the session credential has not expired.
2. Reconnect to the Host through direct server or local peer paths.
3. Exchange last committed event sequence.
4. Request missing events or a fresh snapshot.
5. Resend eligible pending messages.
6. Do not replay obsolete location pings as live updates.

Pending capture and emergency requests must be clearly identified as pending until acknowledged by the Host.

---

# PART VI — FAILURE HANDLING AND FAIRNESS

## 19. Connectivity states

Each player has a connection state separate from game state:

- `ONLINE_DIRECT`: Active WebSocket to cloud.
- `LOCAL_DIRECT`: Connected directly to Host over local peer-to-peer transport.
- `LOCAL_RELAYED`: Reaches Host or a gateway through one or more peers.
- `DEGRADED`: Intermittent connection, high latency, or repeated delivery failures.
- `NO_ROUTE`: No path to Host or an internet gateway.
- `RECONNECTING`: Actively searching for a route.

The UI must show this status and, for a relayed player, whether the route is healthy or stale.

## 20. Disconnection policy

### 20.1 Player loses internet but mesh remains

Continue the game. The player's device routes messages through the local mesh. Do not change role or game status.

### 20.2 Player loses one relay connection but another route exists

Route over the alternative path. Do not show a user-facing disconnection unless service quality becomes degraded.

### 20.3 Player has no path to Host

- Set connection state to `NO_ROUTE`.
- Queue reliable messages.
- Mark outgoing locations as unsent.
- Do not automatically eliminate the player.
- Start the configured connectivity grace timer.
- Notify Host when the player becomes unreachable through all known routes.

Recommended MVP policy: after 60 seconds with no path to Host, the Host receives a prominent warning and may pause the game. The Host may choose to pause sooner. Do not automatically award a win or eliminate a player merely because they are disconnected.

### 20.4 Host loses internet but local mesh works

The Host remains authoritative. Local gameplay continues. Cloud mirror and online-only functions are queued until the Host or a gateway regains internet.

### 20.5 Host loses all peer connectivity

Enter `HOST_UNAVAILABLE` after a short detection/grace period. Clients show the game as paused/authority unavailable. MVP does not elect a new Host.

### 20.6 Gateway loses internet

The gateway withdraws its gateway advertisement. Devices search for another gateway. If a route to the Host exists locally, local gameplay continues without cloud.

### 20.7 Server outage

If Host and players can communicate locally, game continues because the Host is authoritative. Cloud-only room join, account, and history functions may be unavailable.

---

# PART VII — PERFORMANCE, SAFETY, AND PRIVACY

## 21. Performance goals for MVP

These are targets to validate, not guaranteed outcomes:

- Lobby local peer discovery: usable within 30 seconds in typical supported conditions.
- Direct local message delivery: target p95 below 2 seconds for small game events.
- Two-to-four-hop message delivery: target p95 below 5 seconds under a healthy mesh; measure on real devices.
- Route recovery after a link failure: target under 10 seconds when an alternate route is available.
- Scheduled location ping: delivered promptly after the scheduled sample, with original fix timestamp visible.
- Capture acknowledgement: target under 3 seconds on a healthy direct route; relayed/offline delivery may take longer.
- UI must remain responsive during discovery, scanning, GPS sampling, and message forwarding.

The game must not label a target as nearby based on stale coordinates without a stale warning.

## 22. Battery and data use

- Use peer discovery at a controlled interval and stop scanning when the room is closed.
- Reuse healthy peer connections instead of constantly reconnecting.
- Batch low-priority telemetry.
- Send location pings only on schedule or defined exceptional events.
- Coalesce unsent location updates so the queue does not grow indefinitely.
- Prefer small binary messages.
- Show a warning if the player's battery is critically low.
- Do not use continuous high-frequency GPS for Hunter-visible tracking.

## 23. Safety requirements

Shadowhunt is a physical outdoor game. Safety is a core product requirement.

- A Host must define a permitted play zone before starting.
- Players must receive and accept the safety rules.
- No player may enter private property, restricted areas, railway tracks, construction sites, rooftops, roads, or other unsafe areas to evade or catch another player.
- No player may run into traffic or physically obstruct, grab, tackle, or threaten another player.
- Capture is confirmed by QR scan; physical contact is not required.
- The app must include an Emergency Stop action for every role.
- The app must make it easy for the Host to pause/cancel the match.
- The game should be played only in a permitted area with organiser approval and appropriate supervision for participants.
- The Host should review the zone for road crossings, water, private property, and other hazards before starting.

The app is not an emergency location service. It must not claim that it can dispatch help or guarantee emergency connectivity.

## 24. Privacy rules

- Explain location sharing before the user joins a room.
- During play, share Shadow location pings only with Hunters and Host.
- Do not share Hunter coordinates with Shadows.
- Relay nodes must not gain access to restricted location content merely by forwarding it.
- Display a visible active-location indicator while the game is tracking.
- Stop location tracking promptly when the game ends or is cancelled.
- Do not retain precise location trails by default.
- Provide a clear retention and deletion policy.
- Do not include coordinates, private keys, QR tokens, or full invitation credentials in analytics/crash logs.

---

# PART VIII — DEVELOPMENT PLAN

## 25. Recommended project structure

```text
shadowhunt/
├── android-app/
│   ├── app/
│   ├── core/
│   │   ├── model/
│   │   ├── protocol/
│   │   ├── crypto/
│   │   ├── persistence/
│   │   └── common/
│   ├── feature-home/
│   ├── feature-room/
│   ├── feature-lobby/
│   ├── feature-zone/
│   ├── feature-hunter/
│   ├── feature-shadow/
│   ├── feature-host/
│   ├── feature-capture/
│   ├── feature-results/
│   ├── location/
│   ├── transport/
│   │   ├── api/
│   │   ├── nearby-connections/
│   │   └── wifi-direct/          # optional later adapter
│   ├── mesh/
│   │   ├── discovery/
│   │   ├── routing/
│   │   ├── forwarding/
│   │   ├── queue/
│   │   └── reliability/
│   └── data/
├── backend/
│   ├── api/
│   ├── websocket-relay/
│   ├── auth/
│   ├── room-service/
│   ├── persistence/
│   └── tests/
├── protocol/
│   ├── messages.proto
│   └── protocol-versioning.md
└── docs/
    ├── game.md
    ├── test-plan.md
    └── privacy-and-safety.md
```

Keep the game engine independent of the UI and transport. It should consume validated events and produce state transitions. This allows unit tests to run without GPS, Bluetooth, or a live server.

## 26. Implementation milestones

### Milestone 0 — Technical feasibility spike

Before building the full app, prove the local transport and multi-hop design on physical devices.

Build a tiny test application with:
- Three Android phones.
- Nearby Connections Cluster strategy.
- Phone A without internet.
- Phone B without internet.
- Phone C with internet.
- A sends a small numbered test message.
- B forwards it to C.
- C sends it to a test WebSocket server.
- Server acknowledgement returns through C → B → A.

Then test:
- Five to eight phones.
- Two gateways.
- Disconnection of the middle relay.
- Alternative route discovery.
- Background/foreground transitions.
- Screen-off behavior.
- Battery use and heat.

**Do not proceed to the full game until multi-hop forwarding has been demonstrated on target devices.**

### Milestone 1 — Offline room and lobby

- Create room on Host device.
- Discover nearby devices.
- Join and authenticate locally.
- Assign roles.
- Ready checks.
- Start countdown.
- Local Host event sequence.

No map or capture needed yet.

### Milestone 2 — Internet gateway and mesh relay

- Direct WebSocket connection for internet devices.
- Peer mesh links.
- Gateway announcements.
- Multi-hop forwarding.
- Message IDs, hop limits, duplicate cache.
- Acknowledgements and queues.
- Route failure/recovery.

### Milestone 3 — Location and zone

- Location permission flow.
- Location sampling.
- Circular zone selection.
- Radar/grid screen.
- Three-minute Shadow pings.
- Last-known position, timestamp, accuracy, distance calculation.
- Boundary warning and confirmed violation logic.

### Milestone 4 — QR capture and game rules

- Rotating signed QR tokens.
- Camera scanner.
- Capture request and Host validation.
- Capture notification to everyone.
- Automatic elimination.
- Win conditions and results screen.

### Milestone 5 — Hardening

- Security review.
- Privacy review.
- Device compatibility tests.
- Battery and thermal tests.
- Background behavior tests.
- Host disconnect and server outage tests.
- User safety and emergency controls.

### Milestone 6 — Cross-platform exploration

Only after Android is stable, evaluate iOS peer connectivity, background limitations, location permissions, QR behavior, and gateway interoperability. Do not promise Android-to-iOS mesh routing until it passes the same test suite.

---

## 27. Acceptance criteria

The MVP is not ready for public use until the following tests pass.

### 27.1 Gameplay

- [ ] Host can create a room with Hunter/Shadow counts.
- [ ] Players can join and be assigned exactly one role.
- [ ] Host cannot start until all included players are Ready.
- [ ] Timer starts at the shared start time and remains consistent after time sync.
- [ ] Hunters see only the last-known Shadow positions, not continuous live movement.
- [ ] Shadow location ping is emitted every 180 seconds while active.
- [ ] Confirmed boundary crossing causes immediate exceptional ping to Hunters.
- [ ] Poor GPS accuracy does not automatically eliminate a player.
- [ ] Valid QR scan produces exactly one confirmed capture.
- [ ] Duplicate/simultaneous scans cannot capture the same Shadow twice.
- [ ] All players receive capture and game-end notifications when connected.
- [ ] Hunters win when no active Shadows remain.
- [ ] Shadows win when timer expires with at least one active Shadow.

### 27.2 Connectivity

- [ ] Online player can connect directly to service.
- [ ] Offline player can join Host over local peer connection.
- [ ] Offline player can send a message through one internet gateway.
- [ ] Offline player can send a message through at least two intermediate relays.
- [ ] Any role can act as a relay or gateway.
- [ ] Gateway is selected only after server reachability is verified.
- [ ] If a relay disconnects, an alternate path is selected when one exists.
- [ ] Duplicate messages do not create duplicate captures or notifications.
- [ ] Messages without a route are queued according to their policy.
- [ ] Stale location is never presented as a fresh location.
- [ ] No player is eliminated solely because network connectivity is lost.
- [ ] Host remains authoritative during a cloud/server outage if local peer connectivity remains.

### 27.3 Security and privacy

- [ ] Unauthorized device cannot join by knowing only the room code.
- [ ] Relays cannot alter a sender-signed message without detection.
- [ ] Old or replayed capture QR tokens are rejected.
- [ ] A Shadow cannot receive Hunter location data.
- [ ] A Hunter cannot receive another Shadow's location if not authorized by the game rules.
- [ ] Logs do not contain private keys or raw QR tokens.
- [ ] Location sharing stops after game end/cancellation.

### 27.4 Safety

- [ ] Safety notice is shown before Ready.
- [ ] Emergency Stop is available to every player.
- [ ] Host can pause or cancel.
- [ ] Zone and elimination values are visible before the game starts.
- [ ] No automatic elimination occurs from a single inaccurate fix.

---

# PART IX — IMPORTANT ENGINEERING REALITIES

## 28. What this design can and cannot guarantee

### It can provide

- Local GPS positioning without internet where the phone has a usable location fix.
- Nearby peer-to-peer communication without internet.
- Multi-hop application-level message forwarding, if peers are connected and the app's routing layer is functioning.
- Gateway-based delivery from local mesh to an internet service.
- Dynamic route replacement when alternate links are available.
- Store-and-forward for supported messages during temporary disconnections.
- A common message protocol for locations, QR capture requests, notifications, timer events, and lobby changes.

### It cannot guarantee

- A mesh route when devices are physically too far apart.
- Immediate server delivery when no path to a gateway/Host exists.
- A fixed Bluetooth/Wi-Fi range in every environment.
- Continuous background operation on every handset or OS version.
- GPS accuracy to a fixed number of metres.
- That GPS cannot be spoofed on a modified device.
- Immediate global confirmation of an event created inside a disconnected network partition.
- That every device can maintain unlimited simultaneous peer connections.

These limitations must be reflected honestly in UI, rules, and marketing.

## 29. Key architectural decisions (MVP)

| Decision | Choice |
|---|---|
| Initial platform | Android native, Kotlin + Jetpack Compose |
| Local transport | Nearby Connections, Cluster strategy, behind an interface |
| Local multi-hop | Custom application-level mesh forwarding |
| Internet transport | TLS WebSocket to Shadowhunt relay service |
| Game authority | Host phone |
| Cloud role | Room/bootstrap services, relay, optional event mirror/history |
| Location cadence to Hunters | Every 180 seconds, plus confirmed boundary violation |
| Hunter location visibility | Own live position + Shadow last-known snapshots |
| Shadow location visibility | Own live position only, plus zone |
| Capture | Rotating signed QR token, Host-authoritative confirmation |
| Zone | Circular in MVP |
| Boundary enforcement | Multiple accurate fixes + grace/buffer; never one noisy fix |
| No-route behavior | Queue eligible messages; show pending; do not silently eliminate |
| Host failure | Pause; no Host migration in MVP |
| iOS | Later validation after Android reliability is proven |

---

# PART X — OFFICIAL TECHNICAL REFERENCES

The following official documentation should be checked again during implementation because Android, Google Play services, and iOS behavior can change.

1. **Google Nearby Connections overview** — peer discovery and data exchange without internet; uses Bluetooth, BLE, and Wi-Fi technologies:  
   https://developers.google.com/nearby/connections/overview
2. **Nearby Connections strategies** — Cluster, Star, and Point-to-Point topology options:  
   https://developers.google.com/nearby/connections/strategies
3. **Nearby Connections Android setup** — SDK setup and required permissions:  
   https://developers.google.com/nearby/connections/android/get-started
4. **Android Wi-Fi Direct overview** — device-to-device Wi-Fi connections without an access point:  
   https://developer.android.com/develop/connectivity/wifi/wifip2p
5. **Android Bluetooth permissions** — runtime scan, advertise, and connect permissions on modern Android:  
   https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
6. **Android foreground-service types** — requirements and runtime prerequisites for location foreground services:  
   https://developer.android.com/develop/background-work/services/fgs/service-types
7. **Android background location policy guidance** — use foreground location where possible and follow current Play policy:  
   https://support.google.com/googleplay/android-developer/answer/9799150
8. **Apple Nearby/Multipeer Connectivity documentation** — evaluate current platform support and deprecation/migration status before iOS implementation:  
   https://developer.apple.com/documentation/multipeerconnectivity

---

# FINAL BUILD INSTRUCTION

Build Shadowhunt in this order: **first prove three-phone multi-hop message delivery, then prove route recovery, then build the game around the validated network layer.** Do not begin with the full UI and assume the mesh will work later.

The core product promise is not simply “GPS hide and seek.” It is:

> **A real-world multiplayer hunt where every phone can participate in the game network—whether it has internet or not—and messages can move through nearby players until they reach the Host or an internet gateway.**

The application must always show the truth about location freshness, connection quality, and event confirmation. Reliability and fair gameplay are more important than pretending that every phone is online all the time.
