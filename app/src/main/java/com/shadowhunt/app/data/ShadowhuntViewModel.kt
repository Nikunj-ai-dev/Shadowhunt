package com.shadowhunt.app.data

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ShadowhuntViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()
    private var timerJob: Job? = null
    private var boundaryOutsideSinceElapsed: Long? = null
    private var boundaryAlertSent = false
    private var sendMessage: ((String) -> Unit)? = null

    fun attachBroadcaster(sender: (String) -> Unit) { sendMessage = sender }

    fun beginCreate() { _state.value = _state.value.copy(phase = GamePhase.CONFIGURING, error = "") }

    fun returnHome() {
        timerJob?.cancel()
        boundaryOutsideSinceElapsed = null
        boundaryAlertSent = false
        val name = _state.value.displayName
        _state.value = GameUiState(displayName = name)
        sendMessage = null
    }

    fun setDisplayName(value: String) {
        _state.value = _state.value.copy(displayName = value.take(24))
    }

    fun updateSettings(settings: GameSettings) {
        _state.value = _state.value.copy(settings = settings)
    }

    fun createRoom(name: String) {
        val s = _state.value
        val playerId = newPlayerId()
        val host = GamePlayer(playerId, s.displayName.ifBlank { "Host" }, PlayerRole.HOST, PlayerStatus.READY, isHost = true)
        _state.value = s.copy(
            phase = GamePhase.LOBBY,
            roomName = name.ifBlank { "Shadowhunt Room" }.take(32),
            roomCode = makeRoomCode(),
            localPlayerId = playerId,
            localRole = PlayerRole.HOST,
            isHost = true,
            players = listOf(host),
            message = "Room created. Waiting for players.",
            error = ""
        )
    }

    fun joinRoom(code: String) {
        val cleaned = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
        if (cleaned.length != 6) {
            _state.value = _state.value.copy(error = "Enter a valid 6-character room code.")
            return
        }
        val playerId = newPlayerId()
        val player = GamePlayer(playerId, _state.value.displayName.ifBlank { "Player" })
        _state.value = _state.value.copy(
            phase = GamePhase.LOBBY,
            roomCode = cleaned,
            localPlayerId = playerId,
            localRole = PlayerRole.UNASSIGNED,
            isHost = false,
            players = listOf(player),
            message = "Searching for room $cleaned…",
            error = ""
        )
        emit("JOIN_REQUEST", JSONObject().put("playerId", playerId).put("name", player.name))
    }

    fun onPeerConnected(peerId: String, peerName: String) {
        val s = _state.value
        if (s.players.any { it.id == peerId }) return
        val remote = GamePlayer(peerId, peerName.ifBlank { "Nearby player" }, connected = true)
        _state.value = s.copy(players = s.players + remote, message = "$peerName connected")
        emit("PLAYER_HELLO", JSONObject().put("playerId", s.localPlayerId).put("name", s.displayName).put("role", s.localRole.name).put("qrToken", s.players.firstOrNull { it.id == s.localPlayerId }?.qrToken ?: ""))
    }

    private fun sendRoomConfig() {
        val s = _state.value
        val cfg = JSONObject().put("hunterSlots", s.settings.hunterSlots).put("shadowSlots", s.settings.shadowSlots)
            .put("durationMinutes", s.settings.durationMinutes).put("pingIntervalSeconds", s.settings.pingIntervalSeconds)
            .put("zoneRadiusMeters", s.settings.zoneRadiusMeters).put("boundaryWarningMeters", s.settings.boundaryWarningMeters)
            .put("eliminationBufferMeters", s.settings.eliminationBufferMeters).put("boundaryGraceSeconds", s.settings.boundaryGraceSeconds)
        emit("ROOM_CONFIG", JSONObject().put("roomName", s.roomName).put("settings", cfg))
    }

    fun onPeerDisconnected(peerId: String) {
        _state.value = _state.value.copy(players = _state.value.players.map {
            if (it.id == peerId) it.copy(connected = false, status = if (it.status == PlayerStatus.ACTIVE) PlayerStatus.DISCONNECTED else it.status) else it
        }, message = "A nearby connection was lost")
    }

    fun onNetworkMessage(raw: String) {
        val packet = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = packet.optString("type")
        val payload = packet.optJSONObject("payload") ?: JSONObject()
        when (type) {
            "PLAYER_HELLO", "JOIN_REQUEST" -> {
                val id = payload.optString("playerId")
                val name = payload.optString("name", "Player")
                if (id.isNotBlank() && id != _state.value.localPlayerId) {
                    val token = payload.optString("qrToken").ifBlank { null }
                    val remoteRole = runCatching { PlayerRole.valueOf(payload.optString("role", "UNASSIGNED")) }.getOrDefault(PlayerRole.UNASSIGNED)
                    if (_state.value.players.none { it.id == id }) {
                        val p = GamePlayer(id, name, role = remoteRole, isHost = remoteRole == PlayerRole.HOST, qrToken = token ?: UUID.randomUUID().toString())
                        _state.value = _state.value.copy(players = _state.value.players + p, message = "$name joined the lobby")
                    } else {
                        updatePlayer(id) { it.copy(name = name, role = remoteRole, isHost = remoteRole == PlayerRole.HOST, qrToken = token ?: it.qrToken) }
                    }
                    if (_state.value.isHost && type == "PLAYER_HELLO") sendRoomConfig()
                }
            }
            "ROOM_CONFIG" -> {
                val config = payload.optJSONObject("settings") ?: JSONObject()
                _state.value = _state.value.copy(
                    roomName = payload.optString("roomName", _state.value.roomName),
                    settings = GameSettings(
                        hunterSlots = config.optInt("hunterSlots", _state.value.settings.hunterSlots),
                        shadowSlots = config.optInt("shadowSlots", _state.value.settings.shadowSlots),
                        durationMinutes = config.optInt("durationMinutes", _state.value.settings.durationMinutes),
                        pingIntervalSeconds = config.optInt("pingIntervalSeconds", 180),
                        zoneRadiusMeters = config.optInt("zoneRadiusMeters", 500),
                        boundaryWarningMeters = config.optInt("boundaryWarningMeters", 25),
                        eliminationBufferMeters = config.optInt("eliminationBufferMeters", 25),
                        boundaryGraceSeconds = config.optInt("boundaryGraceSeconds", 20)
                    )
                )
            }
            "ROLE_ASSIGN" -> {
                val id = payload.optString("playerId")
                val role = runCatching { PlayerRole.valueOf(payload.optString("role")) }.getOrDefault(PlayerRole.UNASSIGNED)
                updatePlayer(id) { it.copy(role = role) }
                if (id == _state.value.localPlayerId) _state.value = _state.value.copy(localRole = role, message = "You are ${role.name.lowercase()}")
            }
            "PLAYER_READY" -> {
                val id = payload.optString("playerId")
                updatePlayer(id) { it.copy(status = if (payload.optBoolean("ready", true)) PlayerStatus.READY else PlayerStatus.WAITING) }
            }
            "GAME_START" -> {
                val center = payload.optJSONObject("zoneCenter")
                if (center != null) _state.value = _state.value.copy(zoneCenter = GeoPoint(center.optDouble("lat"), center.optDouble("lon"), center.optDouble("accuracy").toFloat(), center.optLong("capturedAt")))
                val config = payload.optJSONObject("settings")
                if (config != null) _state.value = _state.value.copy(settings = GameSettings(
                    hunterSlots = config.optInt("hunterSlots", _state.value.settings.hunterSlots),
                    shadowSlots = config.optInt("shadowSlots", _state.value.settings.shadowSlots),
                    durationMinutes = config.optInt("durationMinutes", _state.value.settings.durationMinutes),
                    pingIntervalSeconds = config.optInt("pingIntervalSeconds", 180),
                    zoneRadiusMeters = config.optInt("zoneRadiusMeters", 500),
                    boundaryWarningMeters = config.optInt("boundaryWarningMeters", 25),
                    eliminationBufferMeters = config.optInt("eliminationBufferMeters", 25),
                    boundaryGraceSeconds = config.optInt("boundaryGraceSeconds", 20)
                ))
                startLocalTimer(payload.optInt("durationSeconds", _state.value.settings.durationMinutes * 60), broadcast = false, startedAtEpochMs = payload.optLong("startedAt", System.currentTimeMillis()))
            }
            "LOCATION_PING" -> {
                if (_state.value.localRole != PlayerRole.HUNTER && !_state.value.isHost) return
                val id = payload.optString("playerId")
                val location = payload.optJSONObject("location") ?: return
                val point = GeoPoint(location.optDouble("lat"), location.optDouble("lon"), location.optDouble("accuracy").toFloat(), location.optLong("capturedAt"))
                updatePlayer(id) { it.copy(lastLocation = point, lastPingAt = point.capturedAtEpochMs) }
            }
            "CAPTURE_REQUEST" -> {
                // The Host is the local MVP authority when no cloud server is available.
                // Validate both sides of the requested capture; role checks on the scanning UI alone
                // are not an authorization boundary.
                if (_state.value.isHost) {
                    val hunterId = payload.optString("hunterId")
                    val shadowId = payload.optString("shadowId")
                    val hunter = _state.value.players.firstOrNull { it.id == hunterId }
                    val shadow = _state.value.players.firstOrNull { it.id == shadowId }
                    if (hunter?.role == PlayerRole.HUNTER && shadow?.role == PlayerRole.SHADOW &&
                        shadow.status != PlayerStatus.CAPTURED && shadow.status != PlayerStatus.ELIMINATED) {
                        confirmCapture(shadowId, broadcast = true)
                    } else {
                        _state.value = _state.value.copy(lastEvent = "Rejected invalid capture request")
                    }
                }
            }
            "CAPTURE_CONFIRMED" -> confirmCapture(payload.optString("shadowId"), broadcast = false)
            "PLAYER_ELIMINATED" -> {
                val id = payload.optString("playerId")
                updatePlayer(id) { it.copy(status = PlayerStatus.ELIMINATED) }
                if (_state.value.activeShadows.isEmpty() && _state.value.isHost) finishGame("HUNTERS", broadcast = true)
            }
            "GAME_PAUSE" -> { timerJob?.cancel(); _state.value = _state.value.copy(phase = GamePhase.PAUSED) }
            "GAME_RESUME" -> startLocalTimer(payload.optInt("durationSeconds", _state.value.secondsRemaining), broadcast = false)
            "BOUNDARY_ALERT" -> {
                if (_state.value.localRole == PlayerRole.HUNTER || _state.value.isHost) {
                    val id = payload.optString("playerId")
                    val location = GeoPoint(payload.optDouble("lat"), payload.optDouble("lon"), payload.optDouble("accuracy").toFloat(), payload.optLong("capturedAt"))
                    updatePlayer(id) { it.copy(lastLocation = location, lastPingAt = location.capturedAtEpochMs) }
                    _state.value = _state.value.copy(lastEvent = "ZONE ALERT · ${_state.value.players.firstOrNull { it.id == id }?.name ?: "Player"}")
                }
            }
            "GAME_END" -> finishGame(payload.optString("winner", "Game ended"), broadcast = false)
        }
    }

    fun assignRole(playerId: String, role: PlayerRole) {
        if (!_state.value.isHost || role == PlayerRole.HOST || role == PlayerRole.UNASSIGNED) return
        val s = _state.value
        val current = s.players.firstOrNull { it.id == playerId } ?: return
        val assigned = s.players.count { it.role == role && it.id != playerId }
        val max = if (role == PlayerRole.HUNTER) s.settings.hunterSlots else s.settings.shadowSlots
        if (assigned >= max) {
            _state.value = s.copy(error = "All ${role.name.lowercase()} slots are filled.")
            return
        }
        updatePlayer(playerId) { current.copy(role = role, status = PlayerStatus.WAITING) }
        emit("ROLE_ASSIGN", JSONObject().put("playerId", playerId).put("role", role.name))
        _state.value = _state.value.copy(error = "", message = "${current.name} assigned as ${role.name.lowercase()}")
    }

    fun setReady(ready: Boolean) {
        val s = _state.value
        if (s.isHost) return
        val player = s.players.firstOrNull { it.id == s.localPlayerId } ?: return
        if (player.role == PlayerRole.UNASSIGNED) {
            _state.value = s.copy(error = "Wait for the Host to assign your role.")
            return
        }
        updatePlayer(player.id) { it.copy(status = if (ready) PlayerStatus.READY else PlayerStatus.WAITING) }
        emit("PLAYER_READY", JSONObject().put("playerId", player.id).put("ready", ready))
        _state.value = _state.value.copy(message = if (ready) "You're ready." else "Ready status removed.", error = "")
    }

    fun canHostStart(): Boolean {
        val s = _state.value
        val participants = s.players.filter { !it.isHost }
        return s.isHost && s.zoneCenter != null && participants.isNotEmpty() &&
            participants.count { it.role == PlayerRole.HUNTER } == s.settings.hunterSlots &&
            participants.count { it.role == PlayerRole.SHADOW } == s.settings.shadowSlots &&
            participants.all { it.status == PlayerStatus.READY && it.connected }
    }

    fun startGame() {
        if (!canHostStart()) {
            _state.value = _state.value.copy(error = "Fill every Hunter and Shadow slot. Everyone must be connected and READY.")
            return
        }
        val duration = _state.value.settings.durationMinutes * 60
        val s = _state.value
        val center = s.zoneCenter
        if (center == null) {
            _state.value = s.copy(error = "Set the game-zone centre using a fresh GPS fix before starting.")
            return
        }
        val settingsJson = JSONObject().put("hunterSlots", s.settings.hunterSlots).put("shadowSlots", s.settings.shadowSlots)
            .put("durationMinutes", s.settings.durationMinutes).put("pingIntervalSeconds", s.settings.pingIntervalSeconds)
            .put("zoneRadiusMeters", s.settings.zoneRadiusMeters).put("boundaryWarningMeters", s.settings.boundaryWarningMeters)
            .put("eliminationBufferMeters", s.settings.eliminationBufferMeters).put("boundaryGraceSeconds", s.settings.boundaryGraceSeconds)
        val startAt = System.currentTimeMillis()
        emit("GAME_START", JSONObject().put("durationSeconds", duration).put("startedAt", startAt)
            .put("settings", settingsJson).put("zoneCenter", JSONObject().put("lat", center.latitude).put("lon", center.longitude).put("accuracy", center.accuracyMeters).put("capturedAt", center.capturedAtEpochMs)))
        startLocalTimer(duration, broadcast = false, startedAtEpochMs = startAt)
    }

    private fun startLocalTimer(durationSeconds: Int, broadcast: Boolean, startedAtEpochMs: Long = System.currentTimeMillis()) {
        timerJob?.cancel()
        val endAt = startedAtEpochMs + durationSeconds.coerceAtLeast(0) * 1000L
        _state.value = _state.value.copy(phase = GamePhase.ACTIVE, secondsRemaining = durationSeconds, lastPingCountdown = _state.value.settings.pingIntervalSeconds, error = "", message = "HUNT STARTED", lastEvent = "The hunt is live")
        timerJob = viewModelScope.launch {
            var lastElapsed = -1
            while (_state.value.phase == GamePhase.ACTIVE) {
                val remaining = ((endAt - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
                val elapsed = (durationSeconds - remaining).coerceAtLeast(0)
                val interval = _state.value.settings.pingIntervalSeconds
                val pingCountdown = if (interval > 0) interval - (elapsed % interval) else 0
                _state.value = _state.value.copy(secondsRemaining = remaining, lastPingCountdown = pingCountdown)
                if (interval > 0 && elapsed > 0 && elapsed % interval == 0 && elapsed != lastElapsed && _state.value.localRole == PlayerRole.SHADOW) {
                    _state.value.currentLocation?.let { sendLocationPing(it) }
                    _state.value = _state.value.copy(lastEvent = "Scheduled location ping sent")
                }
                lastElapsed = elapsed
                if (remaining == 0) {
                    val winner = if (_state.value.activeShadows.isNotEmpty()) "SHADOWS" else "HUNTERS"
                    finishGame(winner, broadcast = _state.value.isHost)
                    break
                }
                delay(1000)
            }
        }
    }

    fun setLocation(point: GeoPoint) {
        val s = _state.value
        _state.value = s.copy(currentLocation = point)
        if (s.phase != GamePhase.ACTIVE || s.localRole != PlayerRole.SHADOW || s.zoneCenter == null) return
        val d = distanceMeters(s.zoneCenter, point)
        val outsideBy = d - s.settings.zoneRadiusMeters
        val warning = d >= s.settings.zoneRadiusMeters - s.settings.boundaryWarningMeters
        _state.value = _state.value.copy(boundaryWarning = warning, boundaryDistanceMeters = (s.settings.zoneRadiusMeters - d).toFloat())

        // Treat a point as definitely outside only when it lies beyond the boundary by more
        // than its reported horizontal accuracy (with a small 5 m minimum margin).
        val confirmedOutside = outsideBy > maxOf(point.accuracyMeters, 5f) && point.accuracyMeters <= 30f
        if (confirmedOutside && !boundaryAlertSent) {
            boundaryAlertSent = true
            sendLocationPing(point)
            emit("BOUNDARY_ALERT", JSONObject().put("playerId", s.localPlayerId).put("lat", point.latitude).put("lon", point.longitude).put("accuracy", point.accuracyMeters).put("capturedAt", point.capturedAtEpochMs))
        }

        // The elimination buffer and grace period are separate from the immediate boundary alert.
        if (outsideBy > s.settings.eliminationBufferMeters && point.accuracyMeters <= 30f) {
            if (boundaryOutsideSinceElapsed == null) boundaryOutsideSinceElapsed = SystemClock.elapsedRealtime()
            if (SystemClock.elapsedRealtime() - (boundaryOutsideSinceElapsed ?: 0L) >= s.settings.boundaryGraceSeconds * 1000L) {
                eliminateLocalShadow("Boundary violation")
                boundaryOutsideSinceElapsed = null
                boundaryAlertSent = false
            }
        } else {
            boundaryOutsideSinceElapsed = null
        }
        if (!confirmedOutside) boundaryAlertSent = false
    }

    fun setZoneCenter(point: GeoPoint) {
        _state.value = _state.value.copy(zoneCenter = point, message = "Zone center set from current GPS location")
    }

    fun sendLocationPing(point: GeoPoint) {
        val s = _state.value
        val payload = JSONObject().put("playerId", s.localPlayerId)
            .put("location", JSONObject().put("lat", point.latitude).put("lon", point.longitude).put("accuracy", point.accuracyMeters).put("capturedAt", point.capturedAtEpochMs))
        emit("LOCATION_PING", payload)
        updatePlayer(s.localPlayerId) { it.copy(lastLocation = point, lastPingAt = point.capturedAtEpochMs) }
    }

    fun captureScannedCode(raw: String) {
        val s = _state.value
        if (s.localRole != PlayerRole.HUNTER || s.phase != GamePhase.ACTIVE) {
            _state.value = s.copy(error = "Only active Hunters can scan a Shadow QR code.")
            return
        }
        val parts = raw.split("|")
        if (parts.size < 4 || parts[0] != "SHADOWHUNT" || parts[1] != s.roomCode) {
            _state.value = s.copy(error = "This QR code does not belong to the current room.")
            return
        }
        val shadowId = parts[2]
        val token = parts[3]
        val target = s.players.firstOrNull { it.id == shadowId }
        if (target == null || target.role != PlayerRole.SHADOW || target.status == PlayerStatus.CAPTURED || target.status == PlayerStatus.ELIMINATED || target.qrToken != token) {
            _state.value = s.copy(error = "Invalid, expired, or already-used Shadow QR code.")
            return
        }
        emit("CAPTURE_REQUEST", JSONObject().put("shadowId", shadowId).put("hunterId", s.localPlayerId).put("scannedAt", System.currentTimeMillis()))
        _state.value = s.copy(message = "Capture request sent for ${target.name}. Awaiting authority confirmation.", error = "")
        // A production server must verify and commit the capture. In local test mode the Host can confirm it.
        if (s.isHost) confirmCapture(shadowId, broadcast = true)
    }

    fun confirmCapture(shadowId: String, broadcast: Boolean) {
        val target = _state.value.players.firstOrNull { it.id == shadowId } ?: return
        if (target.status == PlayerStatus.CAPTURED || target.status == PlayerStatus.ELIMINATED) return
        updatePlayer(shadowId) { it.copy(status = PlayerStatus.CAPTURED) }
        if (broadcast) emit("CAPTURE_CONFIRMED", JSONObject().put("shadowId", shadowId).put("hunterId", _state.value.localPlayerId).put("at", System.currentTimeMillis()))
        _state.value = _state.value.copy(lastEvent = "${target.name} captured · ${_state.value.activeShadows.size} Shadows remain", message = "SHADOW CAPTURED")
        if (_state.value.activeShadows.isEmpty() && _state.value.isHost) finishGame("HUNTERS", broadcast = true)
    }

    private fun eliminateLocalShadow(reason: String) {
        val id = _state.value.localPlayerId
        updatePlayer(id) { it.copy(status = PlayerStatus.ELIMINATED) }
        emit("PLAYER_ELIMINATED", JSONObject().put("playerId", id).put("reason", reason).put("at", System.currentTimeMillis()))
        _state.value = _state.value.copy(lastEvent = "YOU WERE ELIMINATED · $reason", message = "ELIMINATED")
        if (_state.value.activeShadows.isEmpty() && _state.value.isHost) finishGame("HUNTERS", broadcast = true)
    }

    fun pauseGame() {
        if (!_state.value.isHost || _state.value.phase != GamePhase.ACTIVE) return
        timerJob?.cancel()
        _state.value = _state.value.copy(phase = GamePhase.PAUSED, message = "Game paused by Host")
        emit("GAME_PAUSE", JSONObject().put("at", System.currentTimeMillis()))
    }

    fun resumeGame() {
        if (!_state.value.isHost || _state.value.phase != GamePhase.PAUSED) return
        emit("GAME_RESUME", JSONObject().put("durationSeconds", _state.value.secondsRemaining).put("at", System.currentTimeMillis()))
        startLocalTimer(_state.value.secondsRemaining, false)
    }

    fun finishGame(winner: String, broadcast: Boolean) {
        timerJob?.cancel()
        _state.value = _state.value.copy(phase = GamePhase.FINISHED, winner = winner, message = "$winner WIN", lastEvent = "MATCH COMPLETE")
        if (broadcast) emit("GAME_END", JSONObject().put("winner", winner).put("at", System.currentTimeMillis()))
    }

    fun updateNetworkLabel(label: String) { _state.value = _state.value.copy(networkLabel = label) }
    fun dismissError() { _state.value = _state.value.copy(error = "") }

    private fun updatePlayer(id: String, transform: (GamePlayer) -> GamePlayer) {
        _state.value = _state.value.copy(players = _state.value.players.map { if (it.id == id) transform(it) else it })
    }

    private fun emit(type: String, payload: JSONObject) {
        val s = _state.value
        val envelope = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("roomCode", s.roomCode)
            .put("originPlayerId", s.localPlayerId)
            .put("type", type)
            .put("createdAt", System.currentTimeMillis())
            .put("ttl", 8)
            .put("payload", payload)
        sendMessage?.invoke(envelope.toString())
    }
}
