package com.shadowhunt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.mlkit.vision.barcode.common.Barcode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.shadowhunt.app.data.GamePhase
import com.shadowhunt.app.data.GamePlayer
import com.shadowhunt.app.data.GameSettings
import com.shadowhunt.app.data.GameUiState
import com.shadowhunt.app.data.GeoPoint
import com.shadowhunt.app.data.PlayerRole
import com.shadowhunt.app.data.PlayerStatus
import com.shadowhunt.app.data.ShadowhuntViewModel
import com.shadowhunt.app.location.GameSessionService
import com.shadowhunt.app.location.LocationBus
import com.shadowhunt.app.network.MeshMessageRouter
import com.shadowhunt.app.network.NearbyMeshTransport
import com.shadowhunt.app.network.ServerGateway
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private val Ink = Color(0xFF070B13)
private val Panel = Color(0xFF101927)
private val PanelRaised = Color(0xFF152236)
private val Cyan = Color(0xFF55F3E2)
private val CyanDim = Color(0xFF1A8F91)
private val Magenta = Color(0xFFFF4D9A)
private val Amber = Color(0xFFFFC76A)
private val Muted = Color(0xFF91A2B8)
private val White = Color(0xFFEAF4FF)

class MainActivity : ComponentActivity() {
    private val vm: ShadowhuntViewModel by viewModels()
    private lateinit var mesh: NearbyMeshTransport
    private lateinit var gateway: ServerGateway
    private var router: MeshMessageRouter? = null
    private var pendingPermissionAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission()
        val radioGranted = Build.VERSION.SDK_INT < 31 || (
            result[Manifest.permission.BLUETOOTH_SCAN] == true &&
                result[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                result[Manifest.permission.BLUETOOTH_ADVERTISE] == true
            ) || hasBluetoothPermission()
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (locationGranted && radioGranted) action?.invoke()
        else vm.updateNetworkLabel("PERMISSION REQUIRED · CHECK SETTINGS")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mesh = NearbyMeshTransport(this, object : NearbyMeshTransport.Listener {
            override fun onPeerConnected(endpointId: String, endpointName: String) {
                val parts = endpointName.split('|')
                val id = parts.getOrNull(2).orEmpty()
                val name = parts.getOrNull(3).orEmpty()
                vm.onPeerConnected(id.ifBlank { endpointId }, name.ifBlank { "Nearby player" })
                router?.retryPending()
            }
            override fun onPeerDisconnected(endpointId: String) {
                vm.onPeerDisconnected(endpointId)
            }
            override fun onPacket(endpointId: String, packet: String) {
                val currentRouter = router
                if (currentRouter != null) currentRouter.onPeerPacket(packet, endpointId) else vm.onNetworkMessage(packet)
            }
            override fun onStatus(message: String) { vm.updateNetworkLabel(message) }
        })
        gateway = ServerGateway(
            onMessage = { raw -> router?.onServerPacket(raw) ?: vm.onNetworkMessage(raw) },
            onStatus = { status -> vm.updateNetworkLabel(status) }
        )
        setContent {
            val state by vm.state.collectAsState()
            val latestLocation by LocationBus.latest.collectAsState()
            LaunchedEffect(latestLocation) { latestLocation?.let(vm::setLocation) }
            ShadowhuntApp(
                state = state,
                onNameChange = vm::setDisplayName,
                onBeginCreate = { prepareCreate() },
                onCreateRoom = { roomName, settings ->
                    vm.updateSettings(settings)
                    vm.createRoom(roomName)
                    val updated = vm.state.value
                    updated.currentLocation?.let(vm::setZoneCenter)
                    startNetworking(updated.roomCode, updated.localPlayerId, updated.displayName)
                },
                onJoin = { code ->
                    vm.joinRoom(code)
                    val updated = vm.state.value
                    startNetworking(updated.roomCode, updated.localPlayerId, updated.displayName)
                },
                onAssignRole = vm::assignRole,
                onReady = vm::setReady,
                onStart = {
                    val location = vm.state.value.currentLocation
                    if (location != null && vm.state.value.zoneCenter == null) vm.setZoneCenter(location)
                    vm.startGame()
                },
                onPause = vm::pauseGame,
                onResume = vm::resumeGame,
                onCapture = { openScanner() },
                onSetZone = { vm.state.value.currentLocation?.let(vm::setZoneCenter) },
                onSettings = vm::updateSettings,
                onLeave = {
                    vm.finishGame("SESSION CLOSED", broadcast = true)
                    GameSessionService.stop(this)
                    mesh.stop()
                    gateway.close()
                    router = null
                    vm.returnHome()
                },
                onDismissError = vm::dismissError
            )
            LaunchedEffect(state.phase) {
                if (state.phase == GamePhase.ACTIVE) GameSessionService.start(this@MainActivity)
                if (state.phase == GamePhase.FINISHED || state.phase == GamePhase.HOME) GameSessionService.stop(this@MainActivity)
            }
        }
    }

    private fun prepareCreate() {
        val action = {
            vm.beginCreate()
            if (hasLocationPermission()) {
                runCatching {
                    LocationServices.getFusedLocationProviderClient(this)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                val point = GeoPoint(loc.latitude, loc.longitude, loc.accuracy, loc.time)
                                LocationBus.publish(point)
                                vm.setLocation(point)
                                vm.setZoneCenter(point)
                            }
                        }
                }
            }
        }
        val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startNetworking(roomCode: String, playerId: String, displayName: String) {
        val action = {
            router = MeshMessageRouter(
                roomCode = roomCode,
                localPlayerId = playerId,
                broadcastToPeers = { raw, except -> mesh.broadcast(raw, except) },
                sendToServer = { raw -> gateway.send(raw) },
                deliverToApp = vm::onNetworkMessage
            )
            vm.attachBroadcaster { raw -> router?.sendRaw(raw) ?: mesh.broadcast(raw) }
            mesh.start(roomCode, playerId, displayName)
            gateway.connect(BuildConfig.SERVER_WS_URL, roomCode, playerId)
        }
        val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 || (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)

    private fun openScanner() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::captureScannedCode) }
            .addOnCanceledListener { }
            .addOnFailureListener { vm.updateNetworkLabel("QR SCANNER UNAVAILABLE · CHECK GOOGLE PLAY SERVICES") }
    }
}

@Composable
private fun ShadowhuntApp(
    state: GameUiState,
    onNameChange: (String) -> Unit,
    onBeginCreate: () -> Unit,
    onCreateRoom: (String, GameSettings) -> Unit,
    onJoin: (String) -> Unit,
    onAssignRole: (String, PlayerRole) -> Unit,
    onReady: (Boolean) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCapture: () -> Unit,
    onSetZone: () -> Unit,
    onSettings: (GameSettings) -> Unit,
    onLeave: () -> Unit,
    onDismissError: () -> Unit
) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Ink, surface = Panel, primary = Cyan, secondary = Magenta, onBackground = White, onSurface = White)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Box(Modifier.fillMaxSize()) {
                BackgroundGrid()
                when (state.phase) {
                    GamePhase.HOME -> HomeScreen(state, onNameChange, onCreate = onBeginCreate, onJoin = onJoin)
                    GamePhase.CONFIGURING -> CreateRoomScreen(state, onCreateRoom, onSettings, onSetZone)
                    GamePhase.LOBBY -> LobbyScreen(state, onAssignRole, onReady, onStart, onLeave)
                    GamePhase.ACTIVE -> when (state.localRole) {
                        PlayerRole.HUNTER -> HunterScreen(state, onCapture, onPause, onLeave)
                        PlayerRole.SHADOW -> ShadowScreen(state, onLeave)
                        else -> HostScreen(state, onPause, onLeave)
                    }
                    GamePhase.PAUSED -> PausedScreen(state, onResume, onLeave)
                    GamePhase.FINISHED -> FinishedScreen(state, onLeave)
                }
                if (state.error.isNotBlank()) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF3B1728)).border(1.dp, Magenta.copy(alpha = .5f), RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.error, color = White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Icon(Icons.Default.Close, null, tint = White, modifier = Modifier.clickable { onDismissError() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundGrid() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0C1524), Ink, Color(0xFF080D16))))
        val step = 34.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(Color(0xFF23445B).copy(alpha = .10f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(Color(0xFF23445B).copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawCircle(Cyan.copy(alpha = .055f), size.minDimension * .56f, Offset(size.width * .9f, size.height * .05f))
        drawCircle(Magenta.copy(alpha = .04f), size.minDimension * .42f, Offset(size.width * .08f, size.height * .86f))
    }
}

@Composable
private fun HomeScreen(state: GameUiState, onNameChange: (String) -> Unit, onCreate: () -> Unit, onJoin: (String) -> Unit) {
    var name by remember { mutableStateOf(state.displayName) }
    var code by remember { mutableStateOf("") }
    var joinOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(54)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SHADOWHUNT", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("REAL-WORLD PURSUIT NETWORK", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            StatusPill("ALPHA", Amber)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("THE HUNT\nSTARTS HERE.", color = White, fontSize = 38.sp, lineHeight = 41.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text("Find them. Outsmart them.\nStay inside the zone.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        }
        RadarHero()
        GlassPanel {
            Eyebrow("PLAYER IDENTIFICATION")
            OutlinedTextField(
                value = name, onValueChange = { name = it; onNameChange(it) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Callsign") }, singleLine = true,
                colors = fieldColors(), shape = RoundedCornerShape(13.dp)
            )
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(9.dp)); Text("CREATE A ROOM", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
        OutlinedButton(onClick = { joinOpen = !joinOpen }, modifier = Modifier.fillMaxWidth().height(56.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .55f)), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) {
            Icon(Icons.Default.Bluetooth, null, tint = Cyan); Spacer(Modifier.width(9.dp)); Text("JOIN A ROOM", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (joinOpen) {
            GlassPanel {
                Eyebrow("ROOM ACCESS CODE")
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, modifier = Modifier.fillMaxWidth(), label = { Text("6-character code") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), colors = fieldColors(), shape = RoundedCornerShape(13.dp))
                Button(onClick = { onJoin(code) }, modifier = Modifier.fillMaxWidth(), enabled = code.length == 6, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Cyan), shape = RoundedCornerShape(12.dp)) { Text("DISCOVER NEARBY ROOM  ›", fontWeight = FontWeight.Bold) }
                Text("Nearby discovery uses local radios. Internet is not required for a nearby connection, but server functions need a route to an internet gateway.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TinyFeature(Icons.Default.Bluetooth, "LOCAL MESH")
            TinyFeature(Icons.Default.GpsFixed, "GPS RADAR")
            TinyFeature(Icons.Default.Security, "QR CAPTURE")
        }
        Text("Play safely · Stay in public areas · Never enter traffic or private property", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CreateRoomScreen(state: GameUiState, onCreate: (String, GameSettings) -> Unit, onSettings: (GameSettings) -> Unit, onSetZone: () -> Unit) {
    var roomName by remember { mutableStateOf(state.roomName) }
    var settings by remember { mutableStateOf(state.settings) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTop("CONFIGURE MATCH", "01 / LOBBY SETUP")
        GlassPanel {
            Eyebrow("ROOM IDENTITY")
            OutlinedTextField(roomName, { roomName = it; onSettings(settings) }, modifier = Modifier.fillMaxWidth(), label = { Text("Room name") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
        }
        GlassPanel {
            Eyebrow("TEAM COMPOSITION")
            SettingRow("HUNTERS", settings.hunterSlots.toString(), Magenta) {
                settings = settings.copy(hunterSlots = (settings.hunterSlots + it).coerceIn(1, 8)); onSettings(settings)
            }
            Divider(color = White.copy(alpha = .08f))
            SettingRow("SHADOWS", settings.shadowSlots.toString(), Cyan) {
                settings = settings.copy(shadowSlots = (settings.shadowSlots + it).coerceIn(1, 12)); onSettings(settings)
            }
            Text("Host is the non-playing game administrator. Required player count: ${settings.hunterSlots + settings.shadowSlots}.", color = Muted, fontSize = 11.sp)
        }
        GlassPanel {
            Eyebrow("MATCH PARAMETERS")
            Text("GAME DURATION · ${settings.durationMinutes} MIN", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Slider(value = settings.durationMinutes.toFloat(), onValueChange = { settings = settings.copy(durationMinutes = it.toInt()); onSettings(settings) }, valueRange = 5f..90f, steps = 16, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
            Text("PING INTERVAL · 03:00", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Shadow location snapshots are scheduled every three minutes. A verified boundary violation triggers an immediate alert ping.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("ZONE RADIUS · ${settings.zoneRadiusMeters} M", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Slider(value = settings.zoneRadiusMeters.toFloat(), onValueChange = { settings = settings.copy(zoneRadiusMeters = it.toInt()) }, valueRange = 100f..2000f, steps = 18, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
            Button(onClick = { onSetZone() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Cyan), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("USE CURRENT GPS AS ZONE CENTRE")
            }
            Text(if (state.currentLocation != null) "GPS FIX AVAILABLE · ${state.currentLocation.accuracyMeters.toInt()} M ACCURACY" else "WAITING FOR GPS FIX · GRANT LOCATION PERMISSION AFTER ROOM CREATION", color = if (state.currentLocation != null) Cyan else Amber, fontSize = 9.sp, letterSpacing = .8.sp)
        }
        Button(onClick = { onCreate(roomName, settings) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink), shape = RoundedCornerShape(14.dp)) { Text("CREATE ROOM  →", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
    }
}

@Composable
private fun LobbyScreen(state: GameUiState, onAssignRole: (String, PlayerRole) -> Unit, onReady: (Boolean) -> Unit, onStart: () -> Unit, onLeave: () -> Unit) {
    val participants = state.players.filter { !it.isHost }
    val hunterCount = participants.count { it.role == PlayerRole.HUNTER }
    val shadowCount = participants.count { it.role == PlayerRole.SHADOW }
    val localPlayer = state.players.firstOrNull { it.id == state.localPlayerId }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop("MISSION LOBBY", "ROOM ${state.roomCode}", onBack = onLeave)
        GlassPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Eyebrow("ACTIVE ROOM"); Text(state.roomName, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.End) { Text("${participants.size}/${state.settings.hunterSlots + state.settings.shadowSlots}", color = Cyan, fontSize = 23.sp, fontWeight = FontWeight.Black); Text("PLAYERS", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp) }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("HUNTERS $hunterCount/${state.settings.hunterSlots}", Magenta)
                StatusPill("SHADOWS $shadowCount/${state.settings.shadowSlots}", Cyan)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OPERATORS", color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 12.sp)
            Spacer(Modifier.weight(1f)); Text(state.networkLabel, color = Cyan, fontSize = 9.sp, letterSpacing = .5.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.players, key = { it.id }) { player ->
                PlayerLobbyCard(player, isHost = state.isHost, isLocal = player.id == state.localPlayerId, onAssignRole = onAssignRole)
            }
            if (state.players.size <= 1) item { EmptyNetworkCard() }
        }
        if (!state.isHost) {
            val ready = localPlayer?.status == PlayerStatus.READY
            Button(onClick = { onReady(!ready) }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = localPlayer?.role != PlayerRole.UNASSIGNED, colors = ButtonDefaults.buttonColors(containerColor = if (ready) PanelRaised else Cyan, contentColor = if (ready) Cyan else Ink), shape = RoundedCornerShape(14.dp)) {
                Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Bolt, null); Spacer(Modifier.width(8.dp)); Text(if (ready) "READY · TAP TO UNREADY" else "I'M READY", fontWeight = FontWeight.Black)
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink), shape = RoundedCornerShape(14.dp)) { Text("START THE HUNT  →", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            Text("Every player must be assigned, connected and ready. The Host does not play.", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlayerLobbyCard(player: GamePlayer, isHost: Boolean, isLocal: Boolean, onAssignRole: (String, PlayerRole) -> Unit) {
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (player.role == PlayerRole.HUNTER) Magenta.copy(alpha = .16f) else Cyan.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Text(if (player.isHost) "H" else player.name.take(1).uppercase(), color = if (player.role == PlayerRole.HUNTER) Magenta else Cyan, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name + if (isLocal) " · YOU" else "", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (player.isHost) "GAME ADMINISTRATOR" else if (player.connected) "CONNECTED · ${player.status.name}" else "DISCONNECTED", color = Muted, fontSize = 9.sp, letterSpacing = .7.sp)
            }
            RolePill(player.role)
        }
        if (isHost && !player.isHost && player.role == PlayerRole.UNASSIGNED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAssignRole(player.id, PlayerRole.HUNTER) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Magenta), border = androidx.compose.foundation.BorderStroke(1.dp, Magenta.copy(alpha = .5f))) { Text("ASSIGN HUNTER", fontSize = 10.sp) }
                OutlinedButton(onClick = { onAssignRole(player.id, PlayerRole.SHADOW) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan), border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .5f))) { Text("ASSIGN SHADOW", fontSize = 10.sp) }
            }
        } else if (isHost && !player.isHost) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAssignRole(player.id, PlayerRole.HUNTER) }) { Text("HUNTER", color = Magenta, fontSize = 10.sp) }
                TextButton(onClick = { onAssignRole(player.id, PlayerRole.SHADOW) }) { Text("SHADOW", color = Cyan, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun EmptyNetworkCard() {
    GlassPanel {
        Icon(Icons.Default.Bluetooth, null, tint = Cyan, modifier = Modifier.size(24.dp))
        Text("DISCOVERING NEARBY PLAYERS", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Keep this screen open. Nearby devices must grant Bluetooth / Nearby Devices permissions and enter the same room code.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun HunterScreen(state: GameUiState, onCapture: () -> Unit, onPause: () -> Unit, onLeave: () -> Unit) {
    val shadows = state.activeShadows.sortedBy { shadow ->
        val mine = state.currentLocation
        val target = shadow.lastLocation
        if (mine != null && target != null) com.shadowhunt.app.data.distanceMeters(mine, target) else Double.MAX_VALUE
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GameTopBar("HUNTER NETWORK", state, onPause, onLeave, Magenta)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("SHADOWS", "${state.activeShadows.size}", Cyan, Modifier.weight(1f))
            StatTile("CAPTURED", "${state.capturedCount}", Magenta, Modifier.weight(1f))
            StatTile("NEXT PING", formatClock(state.lastPingCountdown), Amber, Modifier.weight(1f))
        }
        RadarPanel(state, isHunter = true, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LAST KNOWN TARGETS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f)); Text("SNAPSHOT DATA", color = Muted, fontSize = 8.sp)
        }
        Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shadows.isEmpty()) Text("No active Shadows remaining.", color = Muted, fontSize = 12.sp)
            shadows.forEach { shadow -> TargetRow(shadow, state.currentLocation) }
        }
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Magenta, contentColor = White), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(9.dp)); Text("SCAN SHADOW QR TO CAPTURE", fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        }
        NetworkFooter(state.networkLabel)
    }
}

@Composable
private fun ShadowScreen(state: GameUiState, onLeave: () -> Unit) {
    val me = state.players.firstOrNull { it.id == state.localPlayerId }
    val qrValue = if (me != null) "SHADOWHUNT|${state.roomCode}|${me.id}|${me.qrToken}" else ""
    val qrBitmap = remember(qrValue) { makeQrBitmap(qrValue) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 17.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Eyebrow("SHADOW PROTOCOL"); Text("STAY UNSEEN", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            StatusPill("EVADING", Cyan)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("TIME LEFT", formatClock(state.secondsRemaining), Cyan, Modifier.weight(1f))
            StatTile("SHADOWS LEFT", "${state.activeShadows.size}", Magenta, Modifier.weight(1f))
        }
        RadarPanel(state, isHunter = false, Modifier.weight(1f))
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = if (state.boundaryWarning) Amber else Cyan)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.boundaryWarning) "ZONE BOUNDARY WARNING" else "ZONE STATUS · SAFE", color = if (state.boundaryWarning) Amber else Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(state.boundaryDistanceMeters?.let { "${kotlin.math.abs(it).toInt()} M ${if (it >= 0) "INSIDE" else "OUTSIDE"}" } ?: "Location accuracy pending", color = Muted, fontSize = 10.sp)
                }
            }
            if (state.boundaryWarning) Text("Move back inside the permitted zone. Persistent confirmed violations can eliminate you.", color = Amber, fontSize = 11.sp)
        }
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("PERSONAL CAPTURE TOKEN")
                    Text("Only show this code when a Hunter has physically found you.", color = Muted, fontSize = 10.sp)
                }
                if (qrBitmap != null) androidx.compose.foundation.Image(qrBitmap.asImageBitmap(), contentDescription = "Your Shadow capture QR", modifier = Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp))
            }
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = .2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("LEAVE SESSION") }
        NetworkFooter(state.networkLabel)
    }
}

@Composable
private fun HostScreen(state: GameUiState, onPause: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTopBar("HOST COMMAND", state, onPause, onLeave, Cyan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("HUNTERS", "${state.hunters.size}", Magenta, Modifier.weight(1f))
            StatTile("SHADOWS", "${state.activeShadows.size}", Cyan, Modifier.weight(1f))
            StatTile("TIME LEFT", formatClock(state.secondsRemaining), Amber, Modifier.weight(1f))
        }
        RadarPanel(state, isHunter = true, Modifier.weight(1f))
        GlassPanel {
            Eyebrow("LIVE EVENT FEED")
            Text(state.lastEvent, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(state.networkLabel, color = Cyan, fontSize = 9.sp)
        }
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("END SESSION") }
    }
}

@Composable
private fun PausedScreen(state: GameUiState, onResume: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(68)
        Spacer(Modifier.height(24.dp)); Text("GAME PAUSED", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("${state.roomCode} · ${formatClock(state.secondsRemaining)} remaining", color = Muted)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onResume, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("RESUME GAME", fontWeight = FontWeight.Black) }
        TextButton(onClick = onLeave) { Text("END SESSION", color = Magenta) }
    }
}

@Composable
private fun FinishedScreen(state: GameUiState, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark(74)
        Spacer(Modifier.height(22.dp)); Eyebrow("MISSION REPORT")
        Text("${state.winner ?: "GAME"}\nWIN", color = if (state.winner == "SHADOWS") Cyan else Magenta, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 38.sp)
        Text("${state.capturedCount} captured · ${state.eliminatedCount} eliminated · ${state.activeShadows.size} remaining", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("BACK TO HOME", fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun RadarPanel(state: GameUiState, isHunter: Boolean, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Radar, null, tint = Cyan, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("TACTICAL RADAR", color = White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp) }
            Spacer(Modifier.weight(1f)); Text(if (isHunter) "LAST KNOWN POSITIONS" else "YOUR POSITION", color = Muted, fontSize = 8.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) * .42f
                drawCircle(Cyan.copy(alpha = .22f), radius, center, style = Stroke(1.2.dp.toPx()))
                drawCircle(Cyan.copy(alpha = .13f), radius * .68f, center, style = Stroke(1.dp.toPx()))
                drawCircle(Cyan.copy(alpha = .10f), radius * .34f, center, style = Stroke(1.dp.toPx()))
                drawLine(Cyan.copy(alpha = .18f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
                drawLine(Cyan.copy(alpha = .18f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
                drawCircle(Cyan.copy(alpha = .08f), radius, center)
                val zone = state.zoneCenter ?: state.currentLocation
                val scale = radius / state.settings.zoneRadiusMeters.coerceAtLeast(100)
                fun project(point: GeoPoint): Offset {
                    if (zone == null) return center
                    val northMeters = (point.latitude - zone.latitude) * 111_000.0
                    val eastMeters = (point.longitude - zone.longitude) * 111_000.0 * kotlin.math.cos(Math.toRadians(zone.latitude))
                    return Offset(
                        (center.x + (eastMeters * scale).toFloat()).coerceIn(10f, size.width - 10f),
                        (center.y - (northMeters * scale).toFloat()).coerceIn(10f, size.height - 10f)
                    )
                }
                // The outer ring is the actual geofence, centred on the Host-selected zone centre.
                drawCircle(Amber.copy(alpha = .85f), 3.dp.toPx(), center)
                val me = state.currentLocation
                if (isHunter) {
                    state.activeShadows.forEach { shadow ->
                        val point = shadow.lastLocation ?: return@forEach
                        val marker = project(point)
                        drawCircle(Magenta.copy(alpha = .15f), 13.dp.toPx(), marker)
                        drawCircle(Magenta, 5.dp.toPx(), marker)
                    }
                }
                if (me != null) {
                    val ownMarker = project(me)
                    drawLine(Cyan.copy(alpha = .24f), center, ownMarker, 1.dp.toPx())
                    drawCircle(Cyan.copy(alpha = .18f), 15.dp.toPx(), ownMarker)
                    drawCircle(if (state.boundaryWarning) Amber else Cyan, 5.dp.toPx(), ownMarker)
                    drawLine(Cyan, Offset(ownMarker.x, ownMarker.y - 12.dp.toPx()), Offset(ownMarker.x, ownMarker.y - 26.dp.toPx()), 2.dp.toPx())
                }
                for (i in 0..3) {
                    val angle = i * 90f
                    rotate(angle, center) { drawLine(White.copy(alpha = .35f), Offset(center.x, center.y - radius - 4.dp.toPx()), Offset(center.x, center.y - radius + 5.dp.toPx()), 1.dp.toPx()) }
                }
            }
            Column(Modifier.align(Alignment.TopStart).padding(7.dp)) { Text("N", color = Muted, fontSize = 9.sp); Text("0°", color = Muted.copy(alpha = .5f), fontSize = 7.sp) }
            Column(Modifier.align(Alignment.BottomEnd).padding(7.dp), horizontalAlignment = Alignment.End) { Text("RANGE", color = Muted, fontSize = 7.sp); Text("${state.settings.zoneRadiusMeters} M", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendDot(Cyan, "YOU")
            if (isHunter) LegendDot(Magenta, "SHADOW SNAPSHOT") else LegendDot(Amber, "ZONE ALERTS")
            Text("GPS ${state.currentLocation?.accuracyMeters?.toInt()?.let { "±${it}M" } ?: "SEARCHING"}", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun TargetRow(player: GamePlayer, current: GeoPoint?) {
    val distance = if (current != null && player.lastLocation != null) com.shadowhunt.app.data.distanceMeters(current, player.lastLocation!!) else null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelRaised.copy(alpha = .82f)).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Magenta))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(player.lastPingAt?.let { "PING ${formatAge(it)} AGO" } ?: "NO LOCATION SNAPSHOT", color = Muted, fontSize = 8.sp)
        }
        Text(distance?.let { if (it >= 1000) "%.1f KM".format(it / 1000) else "${it.toInt()} M" } ?: "—", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GameTopBar(title: String, state: GameUiState, onPause: () -> Unit, onLeave: () -> Unit, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Eyebrow(title); Text(state.roomCode, color = accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatClock(state.secondsRemaining), color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("TIME REMAINING", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.width(9.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onPause, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Ⅱ", color = Amber, fontSize = 20.sp) }
            TextButton(onClick = onLeave, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("EXIT", color = Muted, fontSize = 8.sp) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Panel.copy(alpha = .93f)).border(1.dp, color.copy(alpha = .18f), RoundedCornerShape(12.dp)).padding(11.dp)) {
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .8.sp)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingRow(label: String, value: String, color: Color, onDelta: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Text("TEAM SLOTS", color = Muted, fontSize = 8.sp) }
        TextButton(onClick = { onDelta(-1) }) { Text("−", color = White, fontSize = 20.sp) }
        Text(value, color = White, fontWeight = FontWeight.Black, fontSize = 19.sp)
        TextButton(onClick = { onDelta(1) }) { Text("+", color = color, fontSize = 20.sp) }
    }
}

@Composable
private fun ScreenTop(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { Icon(Icons.Default.ArrowBack, null, tint = Cyan, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)) }
        Column { Eyebrow(subtitle); Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp) }
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(size.dp * .29f)).background(Brush.linearGradient(listOf(Color(0xFF164C5A), Color(0xFF10182B)))).border(1.dp, Cyan.copy(alpha = .45f), RoundedCornerShape(size.dp * .29f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding((size * .16f).dp)) {
            val r = minOf(size.width, size.height) * .42f
            drawCircle(Cyan.copy(alpha = .65f), r, style = Stroke(1.5.dp.toPx()))
            drawCircle(Magenta.copy(alpha = .8f), r * .48f, style = Stroke(1.dp.toPx()))
            drawLine(Cyan, Offset(size.width * .5f, size.height * .1f), Offset(size.width * .5f, size.height * .9f), 1.5.dp.toPx())
            drawLine(Cyan, Offset(size.width * .1f, size.height * .5f), Offset(size.width * .9f, size.height * .5f), 1.5.dp.toPx())
            drawCircle(White, 3.dp.toPx())
        }
    }
}

@Composable
private fun RadarHero() {
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = minOf(size.width, size.height) * .43f
            for (i in 1..4) drawCircle(Cyan.copy(alpha = .25f / i), r * i / 4f, c, style = Stroke(1.dp.toPx()))
            drawLine(Cyan.copy(alpha = .22f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(Cyan.copy(alpha = .22f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            drawCircle(Cyan.copy(alpha = .4f), 5.dp.toPx(), c)
            drawCircle(Magenta, 5.dp.toPx(), Offset(c.x + r * .58f, c.y - r * .34f))
            drawCircle(Magenta.copy(alpha = .3f), 14.dp.toPx(), Offset(c.x + r * .58f, c.y - r * .34f), style = Stroke(1.dp.toPx()))
            drawCircle(Amber, 4.dp.toPx(), Offset(c.x - r * .55f, c.y + r * .47f))
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SIGNAL DETECTED", color = Cyan, fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xE8172639), Color(0xE80C1421)))).border(1.dp, Color(0xFF31516B).copy(alpha = .45f), RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Eyebrow(text: String) { Text(text, color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .35f), CircleShape).padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)); Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
    }
}

@Composable
private fun RolePill(role: PlayerRole) {
    val color = when (role) { PlayerRole.HUNTER -> Magenta; PlayerRole.SHADOW -> Cyan; PlayerRole.HOST -> Amber; PlayerRole.UNASSIGNED -> Muted }
    Text(role.name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp, modifier = Modifier.clip(CircleShape).background(color.copy(alpha = .1f)).padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun TinyFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Cyan, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .4.sp) }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(4.dp)); Text(label, color = Muted, fontSize = 8.sp) }
}

@Composable
private fun NetworkFooter(label: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Bluetooth, null, tint = Cyan.copy(alpha = .75f), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)); Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .7.sp)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Color(0xFF42546A),
    focusedLabelColor = Cyan,
    unfocusedLabelColor = Muted,
    cursorColor = Cyan
)

private fun formatClock(seconds: Int): String = "%02d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
private fun formatAge(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 1000).toInt()
    return if (seconds < 60) "${seconds}S" else "${seconds / 60}M"
}

private fun makeQrBitmap(value: String): Bitmap? = runCatching {
    if (value.isBlank()) return@runCatching null
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
    Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 360) for (y in 0 until 360) bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
}.getOrNull()
