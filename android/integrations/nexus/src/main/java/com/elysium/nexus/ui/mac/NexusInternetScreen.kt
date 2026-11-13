package com.elysium.nexus.ui.mac

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.elysium.nexus.core.transport.mac.MacProtocol
import com.elysium.nexus.core.transport.mac.NexusGlobalPhase
import com.elysium.nexus.core.transport.mac.NexusGlobalRtcSession
import com.elysium.nexus.core.transport.mac.NexusGlobalSessionRegistry
import com.elysium.nexus.core.transport.mac.NexusCloudSignaling
import com.elysium.nexus.core.transport.mac.NexusScreenShareService
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Internet pairing and bidirectional screen/control surface inside Elysium Nexus. */
@Composable
fun NexusInternetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cloud = remember { NexusCloudSignaling(context) }
    val session = remember { NexusGlobalRtcSession(context, cloud) }
    val state by session.state.collectAsState()
    val remoteTrack by session.remoteVideoTrack.collectAsState()
    var pairingCode by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var remoteText by remember { mutableStateOf("") }
    val rendererTrack = remember { mutableStateOf<org.webrtc.VideoTrack?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusMessage = if (granted) "Notificaciones habilitadas. Toca de nuevo Compartir pantalla de Android." else
            "Android no permitió mostrar la notificación; activa notificaciones para compartir la pantalla."
    }
    val projectionPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val grant = result.data
        if (result.resultCode == Activity.RESULT_OK && grant != null) {
            NexusScreenShareService.start(context, result.resultCode, grant)
            statusMessage = "Android está preparando la transmisión con notificación activa."
        } else {
            statusMessage = "No se inició la captura. Android requiere tu autorización explícita."
        }
    }

    DisposableEffect(session) {
        NexusGlobalSessionRegistry.active = session
        onDispose {
            if (NexusGlobalSessionRegistry.active === session) NexusGlobalSessionRegistry.active = null
            NexusScreenShareService.stop(context)
            session.close()
        }
    }

    fun requestAndroidScreenShare() {
        if (state.phase == NexusGlobalPhase.IDLE || state.phase == NexusGlobalPhase.FAILED) {
            statusMessage = "Conecta con el Mac antes de compartir la pantalla de Android."
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionPermission.launch(manager.createScreenCaptureIntent())
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070812)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeonChip(
                label = "Atrás",
                onClick = onBack,
                accent = ElysiumColors.NeonPurple,
                icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("NEXUS GLOBAL", color = ElysiumColors.NeonCyan, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("Android ↔ Mac · Internet", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
            }
            Icon(Icons.Filled.Public, contentDescription = null, tint = ElysiumColors.NeonPurple)
        }

        if (state.phase == NexusGlobalPhase.IDLE || state.phase == NexusGlobalPhase.FAILED) {
            NeonCard(Modifier.fillMaxWidth(), accent = ElysiumColors.NeonPurple) {
                Text("Emparejamiento privado", color = ElysiumColors.NeonPurple, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Pega aquí la invitación temporal que copiaste desde Elysium Nexus para Mac.", color = Color.White.copy(alpha = .76f), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.take(256) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF111525)).padding(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    decorationBox = { inner ->
                        if (pairingCode.isBlank()) Text("elysium-nexus://pair/…", color = Color.White.copy(alpha = .35f), fontSize = 13.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(10.dp))
                ActionButton("Pegar código", Icons.Filled.ContentPaste, enabled = clipboard.getText()?.text?.isNotBlank() == true) {
                    pairingCode = clipboard.getText()?.text.orEmpty().take(256)
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    label = "Conectar por Internet",
                    icon = Icons.Filled.Sync,
                    enabled = pairingCode.isNotBlank(),
                    onClick = { session.connect(pairingCode.trim()) }
                )
            }
        }

        if (state.phase != NexusGlobalPhase.IDLE && state.phase != NexusGlobalPhase.FAILED) {
            NeonCard(Modifier.fillMaxWidth(), accent = if (state.phase == NexusGlobalPhase.CONNECTED) ElysiumColors.NeonGreen else ElysiumColors.NeonCyan) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (state.phase == NexusGlobalPhase.CONNECTED) Icons.Filled.CheckCircle else Icons.Filled.Sync,
                        contentDescription = null,
                        tint = if (state.phase == NexusGlobalPhase.CONNECTED) ElysiumColors.NeonGreen else ElysiumColors.NeonCyan
                    )
                    Column(Modifier.weight(1f)) {
                        Text(if (state.phase == NexusGlobalPhase.CONNECTED) "ENLACE P2P ACTIVO" else "CONECTANDO…", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(state.message, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    }
                    if (state.phase == NexusGlobalPhase.CONNECTED) Icon(Icons.Filled.Lock, contentDescription = "Canal cifrado", tint = ElysiumColors.NeonGreen)
                }
                if (state.phase == NexusGlobalPhase.CONNECTED && !state.relayAvailable) {
                    Spacer(Modifier.height(5.dp))
                    Text("La red permite conexión directa. Algunas redes estrictas requieren TURN, aún pendiente de habilitar en el servicio global.", color = ElysiumColors.NeonOrange, fontSize = 11.sp)
                }
            }
        }

        if (state.phase == NexusGlobalPhase.CONNECTED) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF111525))
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        SurfaceViewRenderer(viewContext).apply {
                            init(session.eglContext, null)
                            setEnableHardwareScaler(true)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            setMirror(false)
                        }
                    },
                    update = { renderer ->
                        if (rendererTrack.value !== remoteTrack) {
                            rendererTrack.value?.removeSink(renderer)
                            rendererTrack.value = remoteTrack
                            remoteTrack?.addSink(renderer)
                        }
                    },
                    onRelease = { renderer ->
                        rendererTrack.value?.removeSink(renderer)
                        rendererTrack.value = null
                        renderer.release()
                    }
                )
                if (remoteTrack == null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Cast, contentDescription = null, tint = ElysiumColors.NeonCyan, modifier = Modifier.size(34.dp))
                        Text("Esperando que el Mac comparta su pantalla", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                    }
                }
                Box(Modifier.fillMaxSize().nexusTrackpad(session))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Clic izq.", Icons.Filled.Mouse, Modifier.weight(1f), true) {
                    sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                    sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                }
                ActionButton("Clic der.", Icons.Filled.Mouse, Modifier.weight(1f), true) {
                    sendMouseButton(session, MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.DOWN)
                    sendMouseButton(session, MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.UP)
                }
                ActionButton("↑", Icons.Filled.VerticalAlignTop, Modifier.weight(.55f), true) { sendScroll(session, 0f, -80f) }
                ActionButton("↓", Icons.Filled.VerticalAlignBottom, Modifier.weight(.55f), true) { sendScroll(session, 0f, 80f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Pedir pantalla Mac", Icons.Filled.Cast, Modifier.weight(1f), true) {
                    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.SCREEN_REQUEST, byteArrayOf(1)))
                    statusMessage = "Solicitud enviada. En el Mac, activa Compartir pantalla de Mac para autorizar la transmisión."
                }
                ActionButton(
                    if (state.androidScreenSharing) "Detener Android" else "Compartir Android",
                    if (state.androidScreenSharing) Icons.Filled.StopCircle else Icons.Filled.ScreenShare,
                    Modifier.weight(1f),
                    true,
                    onClick = {
                        if (state.androidScreenSharing) NexusScreenShareService.stop(context) else requestAndroidScreenShare()
                    }
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = remoteText,
                    onValueChange = { remoteText = it.take(512) },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111525)).padding(12.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (remoteText.isEmpty()) Text("Escribir en el Mac…", color = Color.White.copy(alpha = .38f), fontSize = 13.sp)
                        inner()
                    }
                )
                ActionButton("Enviar", Icons.Filled.Keyboard, enabled = remoteText.isNotEmpty()) {
                    sendTextToMac(session, remoteText)
                    remoteText = ""
                }
            }
        }

        statusMessage?.let { Text(it, color = ElysiumColors.NeonCyan, fontSize = 12.sp) }
        if (state.phase == NexusGlobalPhase.FAILED) {
            NeonCard(Modifier.fillMaxWidth(), accent = ElysiumColors.NeonOrange) {
                Text(state.message, color = ElysiumColors.NeonOrange, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ActionButton("Reintentar", Icons.Filled.Sync, enabled = pairingCode.isNotBlank()) {
                    session.connect(pairingCode.trim())
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    NeonChip(
        label = label,
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else .42f),
        accent = ElysiumColors.NeonCyan,
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
    )
}

private fun sendAbsoluteMove(session: NexusGlobalRtcSession, x: Float, y: Float) {
    val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        .putFloat(x.coerceIn(0f, 1f)).putFloat(y.coerceIn(0f, 1f)).array()
    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.MOUSE_ABS_MOVE, payload))
}

private fun sendMouseButton(
    session: NexusGlobalRtcSession,
    button: MacProtocol.MouseButton,
    state: MacProtocol.ButtonState
) {
    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.MOUSE_BUTTON, byteArrayOf(button.byte, state.byte)))
}

private fun sendScroll(session: NexusGlobalRtcSession, dx: Float, dy: Float) {
    val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putFloat(dx).putFloat(dy).array()
    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.SCROLL, payload))
}

private fun Modifier.nexusTrackpad(session: NexusGlobalRtcSession): Modifier = pointerInput(session) {
    val touchSlop = viewConfiguration.touchSlop
    val viewport = size
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val origin = firstDown.position
        var dragStarted = false
        var usedMultipleFingers = false
        var previousCentroid = origin
        var previousSpan = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                event.changes.forEach { it.consume() }
                break
            }

            if (pressed.size >= 2) {
                val centroid = Offset(
                    pressed.map { it.position.x }.average().toFloat(),
                    pressed.map { it.position.y }.average().toFloat()
                )
                val span = pressed.maxOf { (it.position - centroid).getDistance() }
                if (!usedMultipleFingers) {
                    usedMultipleFingers = true
                    previousCentroid = centroid
                    previousSpan = span
                } else if (previousSpan > 0f) {
                    val zoom = span / previousSpan
                    if (kotlin.math.abs(zoom - 1f) > 0.035f) sendPinch(session, zoom)
                    else {
                        val delta = centroid - previousCentroid
                        if (delta.getDistance() > 1f) sendScroll(session, -delta.x, -delta.y)
                    }
                    previousCentroid = centroid
                    previousSpan = span
                }
            } else if (!usedMultipleFingers) {
                val pointer = pressed.first()
                if (!dragStarted && (pointer.position - origin).getDistance() > touchSlop) {
                    dragStarted = true
                    sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                }
                if (dragStarted) sendAbsoluteMove(session, pointer.position.x / viewport.width, pointer.position.y / viewport.height)
            }

            event.changes.forEach { it.consume() }
        }

        if (dragStarted) {
            sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
        } else if (!usedMultipleFingers && viewport.width > 0f && viewport.height > 0f) {
            sendAbsoluteMove(session, origin.x / viewport.width, origin.y / viewport.height)
            sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
            sendMouseButton(session, MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
        }
    }
}

private fun sendPinch(session: NexusGlobalRtcSession, factor: Float) {
    val payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(factor).array()
    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.PINCH, payload))
}

private fun sendTextToMac(session: NexusGlobalRtcSession, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size !in 1..2_048) return
    session.sendControlFrame(MacProtocol.encodeFrame(MacProtocol.FrameType.TEXT_INPUT, bytes))
}
