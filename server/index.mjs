/**
 * SHADOWHUNT DEVELOPMENT RELAY ONLY.
 * This is a packet fan-out relay for integration experiments, not a secure production game server.
 * It does not authenticate player identities, authorize roles, persist sessions, validate capture
 * evidence, or encrypt location data. Do not expose this directly to the public internet.
 * Production deployment requires TLS termination, authenticated short-lived tickets, role-aware
 * authorization, rate limiting, durable state, audit logs, and server-authoritative game logic.
 */
import { WebSocketServer } from 'ws';

const port = Number(process.env.PORT || 8787);
const wss = new WebSocketServer({ port, maxPayload: 64 * 1024 });
const rooms = new Map();
const seen = new Map();
const WINDOW_MS = 10 * 60 * 1000;

function removeClient(client) {
  if (!client.roomCode) return;
  const peers = rooms.get(client.roomCode);
  if (!peers) return;
  peers.delete(client);
  if (peers.size === 0) rooms.delete(client.roomCode);
}

function safeSend(client, data) {
  if (client.readyState === 1) client.send(data);
}

wss.on('connection', (socket) => {
  socket.isAlive = true;
  socket.on('pong', () => { socket.isAlive = true; });

  socket.on('message', (buffer) => {
    let message;
    try { message = JSON.parse(buffer.toString('utf8')); }
    catch { socket.close(1003, 'invalid json'); return; }

    if (message.type === 'HELLO') {
      const roomCode = String(message.roomCode || '').toUpperCase();
      const playerId = String(message.playerId || '');
      if (!/^[A-Z0-9]{6}$/.test(roomCode) || !playerId || playerId.length > 80) {
        socket.close(1008, 'invalid room identity');
        return;
      }
      socket.roomCode = roomCode;
      socket.playerId = playerId;
      if (!rooms.has(roomCode)) rooms.set(roomCode, new Set());
      rooms.get(roomCode).add(socket);
      safeSend(socket, JSON.stringify({ type: 'RELAY_READY', roomCode }));
      return;
    }

    if (!socket.roomCode || message.roomCode !== socket.roomCode) {
      socket.close(1008, 'not joined to this room');
      return;
    }

    const id = String(message.id || '');
    if (!id || id.length > 100) return;
    const now = Date.now();
    const recipients = rooms.get(socket.roomCode) || new Set();
    const ack = JSON.stringify({
      id: `ack-${id}`,
      roomCode: socket.roomCode,
      originPlayerId: 'SERVER',
      type: 'RELAY_ACK',
      createdAt: now,
      ttl: 8,
      payload: { ackedId: id }
    });
    if (seen.has(id)) {
      for (const peer of recipients) safeSend(peer, ack);
      return;
    }
    seen.set(id, now);
    if (seen.size > 20_000) {
      for (const [key, timestamp] of seen) {
        if (now - timestamp > WINDOW_MS) seen.delete(key);
        if (seen.size <= 10_000) break;
      }
    }

    const raw = JSON.stringify(message);
    for (const peer of recipients) safeSend(peer, raw);
    for (const peer of recipients) safeSend(peer, ack);
  });

  socket.on('close', () => removeClient(socket));
  socket.on('error', () => removeClient(socket));
});

const heartbeat = setInterval(() => {
  for (const socket of wss.clients) {
    if (socket.isAlive === false) { socket.terminate(); continue; }
    socket.isAlive = false;
    socket.ping();
  }
}, 25_000);

wss.on('close', () => clearInterval(heartbeat));
console.log(`Shadowhunt DEV relay listening on :${port}`);
