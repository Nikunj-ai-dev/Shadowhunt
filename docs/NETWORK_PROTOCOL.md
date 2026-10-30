# Shadowhunt alpha mesh protocol

## Packet envelope

```json
{
  "id": "UUID",
  "roomCode": "A1B2C3",
  "originPlayerId": "room-player-id",
  "type": "LOCATION_PING",
  "createdAt": 1793347380000,
  "ttl": 8,
  "payload": {}
}
```

## Transport

- Nearby Connections uses `P2P_CLUSTER` to establish direct M-to-N links between devices that are currently in radio range.
- Each device advertises and discovers the same app service and room code. The local endpoint name is only a discovery filter; it is not identity proof.
- Payloads are byte arrays containing JSON in this alpha implementation.
- An internet-enabled device opens a WebSocket to the configured relay. It can be Hunter, Shadow, or Host.

## Forwarding

1. Each packet has a unique ID and hop limit.
2. Each node keeps a bounded LRU cache of seen IDs.
3. On first receive, a node forwards the packet to all connected peers except the incoming peer, decrementing TTL.
4. If the node has an internet WebSocket, it sends the packet to the relay.
5. The relay fans the packet out to all WebSocket clients in that room and sends a relay acknowledgement.
6. Acknowledgements are flooded locally and remove a packet from each node's in-memory pending queue.
7. If no route currently exists, a node queues the packet and attempts delivery again when a new peer connects.

This is controlled flooding, chosen for a small initial player limit. It is not shortest-path routing, does not estimate the best gateway path, and is not a general-purpose MANET implementation. It can produce duplicate traffic; deduplication and TTL limit but do not eliminate all scaling risks.

## Message types currently handled by the app

- `PLAYER_HELLO`, `JOIN_REQUEST`
- `ROLE_ASSIGN`, `PLAYER_READY`
- `GAME_START`, `GAME_PAUSE`, `GAME_RESUME`, `GAME_END`
- `LOCATION_PING`, `BOUNDARY_ALERT`, `PLAYER_ELIMINATED`
- `CAPTURE_REQUEST`, `CAPTURE_CONFIRMED`
- `RELAY_ACK` (transport acknowledgement)

## Critical security note

Alpha payloads are plaintext and the relay is not an authority. A real deployment must encrypt location payloads for Hunters/Host, authenticate all event authors, and validate state transitions on the server. Never trust `originPlayerId`, QR contents, timestamps, coordinates, or role claims just because they arrived over Nearby Connections.
