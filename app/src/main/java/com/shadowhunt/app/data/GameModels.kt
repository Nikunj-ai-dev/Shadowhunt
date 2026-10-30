package com.shadowhunt.app.data

import java.util.UUID

enum class PlayerRole { HOST, HUNTER, SHADOW, UNASSIGNED }
enum class PlayerStatus { WAITING, READY, ACTIVE, CAPTURED, ELIMINATED, DISCONNECTED }
enum class GamePhase { HOME, CONFIGURING, LOBBY, ACTIVE, PAUSED, FINISHED }

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float = 0f, val capturedAtEpochMs: Long = System.currentTimeMillis())

data class GameSettings(
    val hunterSlots: Int = 2,
    val shadowSlots: Int = 3,
    val durationMinutes: Int = 30,
    val pingIntervalSeconds: Int = 180,
    val zoneRadiusMeters: Int = 500,
    val boundaryWarningMeters: Int = 25,
    val eliminationBufferMeters: Int = 25,
    val boundaryGraceSeconds: Int = 20
)

data class GamePlayer(
    val id: String,
    val name: String,
    val role: PlayerRole = PlayerRole.UNASSIGNED,
    val status: PlayerStatus = PlayerStatus.WAITING,
    val isHost: Boolean = false,
    val hasInternet: Boolean = false,
    val connected: Boolean = true,
    val lastLocation: GeoPoint? = null,
    val lastPingAt: Long? = null,
    val qrToken: String = UUID.randomUUID().toString()
)

data class GameUiState(
    val phase: GamePhase = GamePhase.HOME,
    val displayName: String = "Player",
    val roomName: String = "Friday Night Hunt",
    val roomCode: String = "",
    val localPlayerId: String = "",
    val localRole: PlayerRole = PlayerRole.UNASSIGNED,
    val isHost: Boolean = false,
    val settings: GameSettings = GameSettings(),
    val players: List<GamePlayer> = emptyList(),
    val zoneCenter: GeoPoint? = null,
    val currentLocation: GeoPoint? = null,
    val secondsRemaining: Int = 0,
    val lastPingCountdown: Int = 180,
    val networkLabel: String = "NOT CONNECTED",
    val message: String = "",
    val error: String = "",
    val boundaryWarning: Boolean = false,
    val boundaryDistanceMeters: Float? = null,
    val lastEvent: String = "Awaiting game start",
    val winner: String? = null
) {
    val activeShadows: List<GamePlayer>
        get() = players.filter { it.role == PlayerRole.SHADOW && it.status != PlayerStatus.CAPTURED && it.status != PlayerStatus.ELIMINATED }

    val hunters: List<GamePlayer> get() = players.filter { it.role == PlayerRole.HUNTER }
    val capturedCount: Int get() = players.count { it.role == PlayerRole.SHADOW && it.status == PlayerStatus.CAPTURED }
    val eliminatedCount: Int get() = players.count { it.role == PlayerRole.SHADOW && it.status == PlayerStatus.ELIMINATED }
}

fun newPlayerId(): String = UUID.randomUUID().toString()

fun makeRoomCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { alphabet.random() }.joinToString("")
}

fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
}
