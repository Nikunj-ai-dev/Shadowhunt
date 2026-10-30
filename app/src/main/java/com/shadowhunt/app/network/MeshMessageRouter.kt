package com.shadowhunt.app.network

import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Small-group controlled-flood router. Every peer forwards each unique packet once, with a hop
 * limit. For a 2–12 player game this is easier to reason about than an incomplete general-purpose
 * routing protocol. It is not a replacement for a tested MANET protocol at larger scale.
 */
class MeshMessageRouter(
    private val roomCode: String,
    private val localPlayerId: String,
    private val broadcastToPeers: (String, String?) -> Unit,
    private val sendToServer: (String) -> Boolean,
    private val deliverToApp: (String) -> Unit
) {
    private val seen = object : LinkedHashMap<String, Long>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 4096
    }
    private val pending = LinkedHashMap<String, String>()

    @Synchronized
    fun send(type: String, payload: JSONObject) {
        val packet = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("roomCode", roomCode)
            .put("originPlayerId", localPlayerId)
            .put("type", type)
            .put("createdAt", System.currentTimeMillis())
            .put("ttl", 8)
            .put("payload", payload)
        val raw = packet.toString()
        remember(packet.optString("id"))
        broadcastToPeers(raw, null)
        sendToServer(raw)
        deliverToApp(raw)
    }

    @Synchronized
    fun sendRaw(raw: String) {
        val packet = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (packet.optString("roomCode") != roomCode) return
        val id = packet.optString("id")
        if (id.isNotBlank()) {
            remember(id)
            pending[id] = raw
            while (pending.size > 512) pending.remove(pending.keys.first())
        }
        broadcastToPeers(raw, null)
        sendToServer(raw)
    }

    @Synchronized
    fun retryPending() {
        pending.values.toList().forEach { raw ->
            broadcastToPeers(raw, null)
            sendToServer(raw)
        }
    }

    @Synchronized
    fun onPeerPacket(raw: String, fromEndpointId: String) {
        val packet = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (packet.optString("roomCode") != roomCode) return
        val id = packet.optString("id")
        if (id.isBlank() || hasSeen(id)) return
        remember(id)
        if (packet.optString("type") == "RELAY_ACK") {
            pending.remove(packet.optJSONObject("payload")?.optString("ackedId"))
            broadcastToPeers(raw, fromEndpointId)
            return
        }
        pending[id] = raw
        while (pending.size > 512) pending.remove(pending.keys.first())
        val ttl = packet.optInt("ttl", 0)
        if (ttl > 0) {
            packet.put("ttl", ttl - 1)
            val forwarded = packet.toString()
            broadcastToPeers(forwarded, fromEndpointId)
            sendToServer(forwarded)
        }
        deliverToApp(raw)
    }

    @Synchronized
    fun onServerPacket(raw: String) {
        val packet = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (packet.optString("roomCode") != roomCode) return
        val id = packet.optString("id")
        if (id.isNotBlank() && hasSeen(id)) return
        if (id.isNotBlank()) remember(id)
        if (packet.optString("type") == "RELAY_ACK") {
            pending.remove(packet.optJSONObject("payload")?.optString("ackedId"))
            broadcastToPeers(raw, null)
            return
        }
        broadcastToPeers(raw, null)
        deliverToApp(raw)
    }

    private fun hasSeen(id: String): Boolean = synchronized(seen) { seen.containsKey(id) }
    private fun remember(id: String) { synchronized(seen) { seen[id] = System.currentTimeMillis() } }
}
