package com.jsm.core

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import com.jsm.core.SafeLog as Log
import com.jsm.core.foldable.FoldStateListener
import com.jsm.core.sensory.SensoryFeedbackManager
import com.jsm.core.signaling.NsdBroadcaster
import com.jsm.core.signaling.SignalingServer
import com.jsm.core.webrtc.CaptureForegroundService
import com.jsm.core.webrtc.RTCClient
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.DataChannel
import org.webrtc.RtpReceiver

class MainActivity : ComponentActivity() {

    private val pairingInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    private data class PendingPairing(
        val address: String,
        val decision: kotlinx.coroutines.CompletableDeferred<Boolean>,
    )
    private var pendingPairing by mutableStateOf<PendingPairing?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        com.google.android.play.core.splitcompat.SplitCompat.installActivity(this)
    }

    companion object {
        private const val TAG = "ElysiumMainActivity"
        const val ACTION_STOP_SCREEN_SHARE = "com.jsm.core.action.STOP_SCREEN_SHARE"
    }

    private lateinit var nsdBroadcaster: NsdBroadcaster
    private lateinit var sensoryFeedback: SensoryFeedbackManager
    private lateinit var foldStateListener: FoldStateListener
    private var rtcClient: RTCClient? = null
    private var signalingServer: SignalingServer? = null
    private var foldMonitorJob: Job? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestScreenCaptureConsent()
        } else {
            isBroadcastingState.value = false
            stopPipeline()
            connectionState.value = "NOTIFICATION_PERMISSION_REQUIRED"
        }
    }

    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    private val mediaProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                try {
                    // Start only after the user accepts Android's capture prompt.
                    CaptureForegroundService.isForegroundActive = false
                    startForegroundService(Intent(this@MainActivity, CaptureForegroundService::class.java))
                    var waited = 0
                    while (!CaptureForegroundService.isForegroundActive && waited < 3000) {
                        kotlinx.coroutines.delay(100)
                        waited += 100
                    }
                    check(CaptureForegroundService.isForegroundActive) {
                        "Android no pudo iniciar el servicio de captura en primer plano. Vuelve a intentar."
                    }
                    bootFullPipeline(result.data!!)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to initialize WebRTC pipeline", t)
                    isBroadcastingState.value = false
                    stopPipeline()
                    connectionState.value = "ERROR: ${t.message}"
                }
            }
        } else {
            isBroadcastingState.value = false
            stopPipeline()
            connectionState.value = "PERMISSION_DENIED"
        }
    }

    // ─── Observable State ───
    private var isBroadcastingState = mutableStateOf(false)
    private var connectionState = mutableStateOf("IDLE")
    private var peerState = mutableStateOf("—")
    private var lastFoldPosture = mutableStateOf("UNKNOWN")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nsdBroadcaster = NsdBroadcaster(this)
        sensoryFeedback = SensoryFeedbackManager(this)
        foldStateListener = FoldStateListener(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF080810))) {
                pendingPairing?.let { pairing ->
                    AlertDialog(
                        onDismissRequest = {
                            pairing.decision.complete(false)
                            pendingPairing = null
                        },
                        title = { Text("Autorizar ScreenMirror") },
                        text = { Text("¿Permitir que ${pairing.address} vea tu pantalla? Solo acepta si reconoces ese dispositivo.") },
                        confirmButton = { TextButton(onClick = {
                            pairing.decision.complete(true)
                            pendingPairing = null
                        }) { Text("Permitir") } },
                        dismissButton = { TextButton(onClick = {
                            pairing.decision.complete(false)
                            pendingPairing = null
                        }) { Text("Rechazar") } },
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { finish() }) { Text("← Volver a Elysium") }
                    Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.background) {
                        if (android.os.Build.VERSION.SDK_INT >= 31) ElysiumControlPanel()
                        else Text("ScreenMirror requiere Android 12 o superior.")
                    }
                }
            }
        }

        if (intent?.action == ACTION_STOP_SCREEN_SHARE) {
            isBroadcastingState.value = false
            stopPipeline()
        }
    }

    private fun startMediaProjectionRequest() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                connectionState.value = "NOTIFICATION_PERMISSION_REQUIRED"
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestScreenCaptureConsent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenMirror", e)
            connectionState.value = "ERROR: ${e.message}"
        }
    }

    private fun requestScreenCaptureConsent() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun bootFullPipeline(projectionData: Intent) {
        connectionState.value = "INITIALIZING"

        val pcObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }

        rtcClient = RTCClient(this, pcObserver)

        // ICE → Signaling bridge
        rtcClient?.onLocalIceCandidate = { candidate ->
            signalingServer?.sendIceCandidate(
                candidate.sdp,
                candidate.sdpMid,
                candidate.sdpMLineIndex
            )
        }

        // Connection state tracking
        rtcClient?.onConnectionStateChanged = { state ->
            runOnUiThread {
                peerState.value = state.name
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        connectionState.value = "STREAMING"
                        sensoryFeedback.triggerPairingSuccess()
                        startFoldStateMonitoring()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        connectionState.value = "DISCONNECTED"
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        connectionState.value = "FAILED"
                    }
                    else -> {}
                }
            }
        }

        rtcClient?.startStreaming(projectionData)
        connectionState.value = "WAITING_FOR_MAC"

        // Signaling server con ICE completo + reconnection
        signalingServer = SignalingServer(
            port = 9999,
            onConnectionRequested = { address ->
                if (!pairingInProgress.compareAndSet(false, true)) false
                else try {
                    val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    runOnUiThread { pendingPairing = PendingPairing(address, decision) }
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(30_000L) { decision.await() } ?: false
                    }
                } finally {
                    pairingInProgress.set(false)
                    runOnUiThread { pendingPairing = null }
                }
            },
            onOfferRequested = { sendOffer ->
                connectionState.value = "NEGOTIATING"
                rtcClient?.createOffer { sdp ->
                    sendOffer(sdp.description)
                }
            },
            onAnswerReceived = { sdpAnswer ->
                rtcClient?.setRemoteDescription(
                    SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
                )
            },
            onRemoteIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
                rtcClient?.addRemoteIceCandidate(candidate, sdpMid, sdpMLineIndex)
            },
            onMacDisconnected = {
                runOnUiThread {
                    connectionState.value = "MAC_DISCONNECTED"
                }
            }
        )
        signalingServer?.start()
    }

    /**
     * Inicia el monitoreo de fold state y lo envía por DataChannel al Mac.
     */
    private fun startFoldStateMonitoring() {
        // lifecycleScope se cancela automáticamente en onDestroy
        foldMonitorJob = lifecycleScope.launch {
            foldStateListener.monitorDevicePosture().collect { posture ->
                val postureStr = posture.name
                lastFoldPosture.value = postureStr
                rtcClient?.sendDataChannelMessage("POSTURE:$postureStr")
                sensoryFeedback.triggerDataChannelReceived()
            }
        }

        // Enviar orientación/dimensiones actuales
        sendCurrentOrientation()
    }

    private fun sendCurrentOrientation() {
        val config = resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dm = resources.displayMetrics
        val msg = "ORIENTATION:${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}:${dm.widthPixels}:${dm.heightPixels}"
        rtcClient?.sendDataChannelMessage(msg)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "⚡ Config changed: orientation=${newConfig.orientation}, " +
                "screenW=${newConfig.screenWidthDp}, screenH=${newConfig.screenHeightDp}")

        // Enviar nuevas dimensiones al Mac inmediatamente
        sendCurrentOrientation()

        // Adaptar la resolución de captura al nuevo layout de pantalla
        // Enviar resolución nativa para máxima nitidez en LAN
        val dm = resources.displayMetrics
        val newWidth = dm.widthPixels
        val newHeight = dm.heightPixels

        rtcClient?.adaptCaptureResolution(
            width = newWidth,
            height = newHeight,
            fps = 60
        )

        Log.d(TAG, "📐 Capture adaptado: ${newWidth}x${newHeight}")
    }

    private fun stopPipeline() {
        foldMonitorJob?.cancel()
        foldMonitorJob = null
        nsdBroadcaster.stopBroadcasting()
        signalingServer?.stop()
        signalingServer = null
        rtcClient?.dispose()
        rtcClient = null
        stopService(Intent(this, CaptureForegroundService::class.java))
        connectionState.value = "IDLE"
        peerState.value = "—"
    }

    // ══════════════════════════════════════════════════════════════
    //  COMPOSE UI — Cyberpunk Premium Control Panel
    // ══════════════════════════════════════════════════════════════

    @Composable
    fun ElysiumControlPanel() {
        var isBroadcasting by isBroadcastingState
        val connState by connectionState
        val peer by peerState
        val foldPosture by lastFoldPosture

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "pulseAlpha"
        )
        val radarScale by infiniteTransition.animateFloat(
            initialValue = 0.6f, targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "radarScale"
        )
        val glowRotation by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "glowRotation"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // ─── Fondo Deep Space ───
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF060610),
                                Color(0xFF0A0B1A),
                                Color(0xFF080814),
                                Color(0xFF060610)
                            )
                        )
                    )
            )

            // ─── Grid Industrial de Fondo ───
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val gridSpacing = 36.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.025f)
                for (x in 0..((size.width / gridSpacing).toInt())) {
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x * gridSpacing, 0f),
                        end = androidx.compose.ui.geometry.Offset(x * gridSpacing, size.height), strokeWidth = 0.5f)
                }
                for (y in 0..((size.height / gridSpacing).toInt())) {
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y * gridSpacing),
                        end = androidx.compose.ui.geometry.Offset(size.width, y * gridSpacing), strokeWidth = 0.5f)
                }
            }

            // ─── Glow ambiental superior (Cyan) ───
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-100).dp)
                    .blur(70.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // ─── Glow ambiental inferior (Purple) ───
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 60.dp, y = 80.dp)
                    .blur(60.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE040FB).copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // ─── Header ───
                Text(
                    text = "ELYSIUM",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 10.sp
                )
                Text(
                    text = "VANGUARD LINK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.25f),
                    letterSpacing = 8.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ─── Status Chips ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip("NSD", if (isBroadcasting) "LIVE" else "OFF",
                        if (isBroadcasting) Color(0xFF00E5FF) else Color(0xFF555566),
                        isBroadcasting,
                        Modifier.weight(1f))
                    StatusChip("P2P", when {
                        peer.contains("CONNECTED") || peer.contains("COMPLETED") -> "OK"
                        peer.contains("CHECKING") -> "..."
                        peer.contains("FAILED") -> "ERR"
                        else -> "—"
                    },
                        when {
                            peer.contains("CONNECTED") || peer.contains("COMPLETED") -> Color(0xFF00E676)
                            peer.contains("CHECKING") -> Color(0xFFFFAB00)
                            peer.contains("FAILED") -> Color(0xFFFF1744)
                            else -> Color(0xFF555566)
                        },
                        peer.contains("CONNECTED") || peer.contains("COMPLETED"),
                        Modifier.weight(1f))
                    StatusChip("FOLD", when(foldPosture) {
                        "FOLDED" -> "FOLD"
                        "HALF_OPENED" -> "HALF"
                        "UNFOLDED" -> "OPEN"
                        else -> "—"
                    },
                        Color(0xFFE040FB),
                        foldPosture != "UNKNOWN",
                        Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Connection State Banner ───
                AnimatedVisibility(visible = connState != "IDLE") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(connectionColor(connState).copy(alpha = 0.06f))
                            .border(1.dp, connectionColor(connState).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Indicador pulsante
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        connectionColor(connState).copy(alpha = pulseAlpha),
                                        CircleShape
                                    )
                            )
                            Column {
                                Text(
                                    text = if (connState == "NOTIFICATION_PERMISSION_REQUIRED") {
                                        "NOTIFICACIONES REQUERIDAS"
                                    } else {
                                        connState.replace("_", " ")
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = connectionColor(connState)
                                )
                                Text(
                                    text = when(connState) {
                                        "STREAMING" -> "1080p @ 60fps • WebRTC P2P • LAN"
                                        "WAITING_FOR_MAC" -> "Servidor TCP activo en puerto 9999"
                                        "NEGOTIATING" -> "Intercambiando SDP + ICE candidates..."
                                        "MAC_DISCONNECTED" -> "Esperando reconexión automática..."
                                        "NOTIFICATION_PERMISSION_REQUIRED" -> "Permite las notificaciones para iniciar la captura de pantalla."
                                        else -> "Inicializando subsistemas..."
                                    },
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }

                // ─── Telemetry Panel (visible when P2P connected) ───
                AnimatedVisibility(visible = peer.contains("CONNECTED") || peer.contains("COMPLETED")) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE040FB).copy(alpha = 0.04f))
                                .border(0.5.dp, Color(0xFFE040FB).copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TelemetryItem("POSTURE", when(foldPosture) {
                                    "FOLDED" -> "📱 FOLD"
                                    "HALF_OPENED" -> "📐 HALF"
                                    "UNFOLDED" -> "📺 OPEN"
                                    else -> "❓"
                                }, Color(0xFFE040FB))
                                TelemetryItem("STREAM", "1080p", Color(0xFF00E5FF))
                                TelemetryItem("CODEC", "H264/HW", Color(0xFF00E676))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ─── Main Action Button ───
                Box(contentAlignment = Alignment.Center) {
                    // Radar rings (cuando espera Mac)
                    if (isBroadcasting && connState != "STREAMING") {
                        Box(
                            modifier = Modifier
                                .size((130 * radarScale).dp)
                                .border(
                                    1.dp,
                                    Color(0xFF00E5FF).copy(alpha = (1f - (radarScale - 0.6f) / 0.8f).coerceIn(0f, 1f)),
                                    CircleShape
                                )
                        )
                        // Segunda onda offset
                        Box(
                            modifier = Modifier
                                .size((130 * ((radarScale + 0.3f) % 1.4f + 0.6f)).dp)
                                .border(
                                    0.5.dp,
                                    Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    CircleShape
                                )
                        )
                    }

                    // Glow ring detrás del botón
                    if (isBroadcasting) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .blur(12.dp)
                                .background(
                                    Color(0xFF00E5FF).copy(alpha = 0.15f * pulseAlpha),
                                    CircleShape
                                )
                        )
                    }

                    Button(
                        onClick = {
                            try {
                                if (isBroadcasting) {
                                    isBroadcasting = false
                                    stopPipeline()
                                } else {
                                    isBroadcasting = true
                                    nsdBroadcaster.startBroadcasting(9999)
                                    startMediaProjectionRequest()
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in broadcast toggle", t)
                                connectionState.value = "ERROR: ${t.message}"
                                isBroadcasting = false
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBroadcasting)
                                Color(0xFF00E5FF).copy(alpha = 0.12f)
                            else
                                Color.White.copy(alpha = 0.05f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isBroadcasting)
                                Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFFE040FB),
                                        Color(0xFF00E5FF)
                                    )
                                )
                            else
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                )
                        ),
                        modifier = Modifier.size(104.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isBroadcasting) "■" else "▶",
                                fontSize = if (isBroadcasting) 22.sp else 26.sp,
                                color = if (isBroadcasting) Color(0xFF00E5FF) else Color.White
                            )
                            Text(
                                text = if (isBroadcasting) "STOP" else "LINK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = if (isBroadcasting) Color(0xFF00E5FF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                                letterSpacing = 3.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ─── Footer ───
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Divider(
                        modifier = Modifier
                            .width(60.dp)
                            .padding(bottom = 12.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = 0.5.dp
                    )
                    Text(
                        text = "ELYSIUM VANGUARD v1.0",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.12f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "P2P SCREEN MIRROR STUDIO",
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.06f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 3.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun StatusChip(label: String, value: String, color: Color, isActive: Boolean, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) color.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.025f)
                )
                .border(
                    0.5.dp,
                    if (isActive) color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color.copy(alpha = 0.45f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }

    @Composable
    private fun TelemetryItem(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }

    private fun connectionColor(state: String): Color = when (state) {
        "STREAMING" -> Color(0xFF00E676)
        "NEGOTIATING", "WAITING_FOR_MAC" -> Color(0xFF00E5FF)
        "INITIALIZING" -> Color(0xFFFFAB00)
        "NOTIFICATION_PERMISSION_REQUIRED" -> Color(0xFFFFAB00)
        "FAILED", "PERMISSION_DENIED" -> Color(0xFFFF1744)
        "MAC_DISCONNECTED" -> Color(0xFFFF6D00)
        "DISCONNECTED" -> Color(0xFFFF6D00)
        else -> Color.Gray
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPipeline()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_STOP_SCREEN_SHARE) {
            isBroadcastingState.value = false
            stopPipeline()
        }
    }
}
