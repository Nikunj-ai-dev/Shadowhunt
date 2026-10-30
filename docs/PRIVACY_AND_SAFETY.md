# Privacy, consent, and physical safety

## Alpha-build warning

This alpha is a development prototype. Its mesh packets and development WebSocket relay are not protected by production end-to-end encryption or production authentication. Do not use it for a public match, with strangers, or with sensitive real-world location data. Do not expose the development relay directly to the public internet.

## Location transparency

A player should understand before entering a match that:

- The app reads device location while the active match session is running.
- Shadows' scheduled and boundary-triggered location snapshots are intended for the Hunter team and Host.
- The phone may forward game packets for other nearby participants, regardless of the owner's team.
- A player can leave the session and stop sharing, subject to the match's forfeit rules.
- The game uses an Android foreground location service with an ongoing notification during an active session.

The current alpha does not implement the end-to-end team encryption required to make the intended recipient restriction technically enforceable. A production client must encrypt team-only payloads before relaying them.

## Physical safety rules

- Play only in a Host-approved public area where the group is legally allowed to be.
- Never enter roads, railway property, construction sites, private property, restricted facilities, or unsafe buildings.
- Do not run while looking at the phone or scanning a QR code.
- A capture requires a safe, consensual physical approach. No grabbing, blocking, threatening, or physical contact is permitted.
- Anyone can stop the game immediately for safety reasons.
- The Host must provide a safe meeting point and emergency contact plan before starting.
- Do not use this game to follow, locate, or track anyone who has not knowingly joined the match.

## Data handling requirement for production

Before public release, publish a privacy policy and explain the collection, use, relay, retention, and deletion of precise location and device identifiers. Minimize collection, limit access to the correct team, retain only what is needed, and provide a clear leave/stop control. Complete applicable Android and Google Play location declarations and review requirements before submission.
