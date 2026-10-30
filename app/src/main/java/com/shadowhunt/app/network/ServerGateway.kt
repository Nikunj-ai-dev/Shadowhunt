package com.shadowhunt.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Optional internet gateway. Configure a real wss:// endpoint in app/build.gradle.kts. */
class ServerGateway(
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var open = false

    fun connect(url: String, roomCode: String, playerId: String) {
        if (url.isBlank() || url.contains("example.invalid")) {
            onStatus("SERVER URL NOT CONFIGURED")
            return
        }
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open = true
                onStatus("INTERNET GATEWAY ONLINE")
                webSocket.send("{\"type\":\"HELLO\",\"roomCode\":\"$roomCode\",\"playerId\":\"$playerId\"}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) = onMessage.invoke(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open = false
                onStatus("SERVER DISCONNECTED")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open = false
                onStatus("SERVER CONNECTION UNAVAILABLE")
            }
        })
    }

    fun send(raw: String): Boolean = if (open) socket?.send(raw) ?: false else false
    fun isOpen(): Boolean = open
    fun close() { open = false; socket?.close(1000, "game session ended"); socket = null }
}
