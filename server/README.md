# Shadowhunt development relay

This server is a WebSocket packet fan-out relay for early network experiments only.

## Run locally

```bash
npm install
PORT=8787 npm start
```

Expose it through a TLS-terminating reverse proxy for device testing, then configure the Android build property:

```properties
SHADOWHUNT_SERVER_WS_URL=wss://your-test-host.example/ws
```

The sample server listens at the root WebSocket path. Configure your reverse proxy to pass `/ws` to port 8787.

## Not production ready

This relay is deliberately not a production game backend. It does not issue join tickets, authenticate device identities, bind public keys to players, authorize Host-only actions, filter Hunter-only location messages, validate QR captures, persist game state, or defend against malicious clients. It broadcasts messages for development and protocol testing. Do not use it for a real public match until the production-security checklist in `../docs/PRODUCTION_GAPS.md` is completed.
