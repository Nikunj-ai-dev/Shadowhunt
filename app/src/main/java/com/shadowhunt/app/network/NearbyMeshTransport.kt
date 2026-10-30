package com.shadowhunt.app.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Local peer transport. Nearby Connections creates direct peer links only. The application-level
 * MeshMessageRouter forwards packets between those links to create multi-hop delivery.
 */
class NearbyMeshTransport(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPeerConnected(endpointId: String, endpointName: String)
        fun onPeerDisconnected(endpointId: String)
        fun onPacket(endpointId: String, packet: String)
        fun onStatus(message: String)
    }

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val connected = ConcurrentHashMap.newKeySet<String>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val endpointNames = ConcurrentHashMap<String, String>()
    private val endpointPlayerIds = ConcurrentHashMap<String, String>()
    private var roomCode: String = ""
    private var endpointName: String = ""
    private val serviceId = "com.shadowhunt.app.mesh"
    private val strategy = Strategy.P2P_CLUSTER

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val packet = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return
            listener.onPacket(endpointId, packet)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val remoteName = info.endpointName.orEmpty()
            if (!belongsToRoom(remoteName)) {
                client.rejectConnection(endpointId)
                return
            }
            endpointNames[endpointId] = remoteName
            endpointPlayerIds[endpointId] = playerIdFromEndpoint(remoteName).orEmpty()
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected.add(endpointId)
                connecting.remove(endpointId)
                // Pass the full advertised identity so MainActivity can recover player ID + callsign.
                val remoteIdentity = endpointNames[endpointId].orEmpty()
                listener.onPeerConnected(endpointId, remoteIdentity)
                listener.onStatus("LOCAL MESH · ${connected.size} PEER(S)")
            } else {
                connecting.remove(endpointId)
                listener.onStatus("Peer connection failed: ${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            connecting.remove(endpointId)
            endpointNames.remove(endpointId)
            val playerId = endpointPlayerIds.remove(endpointId) ?: endpointId
            listener.onPeerDisconnected(playerId)
            listener.onStatus(if (connected.isEmpty()) "SEARCHING FOR PEERS" else "LOCAL MESH · ${connected.size} PEER(S)")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!belongsToRoom(info.endpointName)) return
            endpointNames[endpointId] = info.endpointName
            endpointPlayerIds[endpointId] = playerIdFromEndpoint(info.endpointName).orEmpty()
            if (connected.contains(endpointId) || !connecting.add(endpointId)) return
            client.requestConnection(endpointName, endpointId, lifecycleCallback)
                .addOnFailureListener {
                    connecting.remove(endpointId)
                    listener.onStatus("Connection request failed")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // Existing established connections are not dropped just because discovery loses sight.
        }
    }

    fun start(roomCode: String, playerId: String, displayName: String) {
        this.roomCode = roomCode
        this.endpointName = "SHADOWHUNT|$roomCode|$playerId|${displayName.take(20)}"
        listener.onStatus("STARTING LOCAL DISCOVERY")
        val advertising = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discovery = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(endpointName, serviceId, lifecycleCallback, advertising)
            .addOnSuccessListener { listener.onStatus("ADVERTISING · SEARCHING FOR PEERS") }
            .addOnFailureListener { listener.onStatus("Advertising unavailable: ${it.localizedMessage}") }
        client.startDiscovery(serviceId, discoveryCallback, discovery)
            .addOnFailureListener { listener.onStatus("Discovery unavailable: ${it.localizedMessage}") }
    }

    fun broadcast(packet: String, exceptEndpointId: String? = null) {
        val bytes = packet.toByteArray(Charsets.UTF_8)
        val recipients = connected.filter { it != exceptEndpointId }
        if (recipients.isEmpty()) return
        client.sendPayload(recipients.toList(), Payload.fromBytes(bytes))
            .addOnFailureListener { listener.onStatus("Local delivery failed") }
    }

    fun connectedPeerCount(): Int = connected.size

    fun stop() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        connected.clear()
        connecting.clear()
        endpointNames.clear()
        endpointPlayerIds.clear()
        listener.onStatus("LOCAL RADIO STOPPED")
    }

    private fun belongsToRoom(remoteName: String): Boolean {
        val fields = remoteName.split('|')
        return fields.size >= 4 && fields[0] == "SHADOWHUNT" && fields[1] == roomCode
    }

    private fun playerIdFromEndpoint(remoteName: String): String? = remoteName.split('|').getOrNull(2)
}
