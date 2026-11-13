package com.elysium.nexus.ui.mac

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.transport.mac.MacProtocol
import com.elysium.nexus.core.transport.mac.MacTransport
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView

enum class ScreenScaleMode(val label: String) {
    ADAPTIVE_FILL("📐 ADAPTATIVO"),
    ORIGINAL_FIT("📐 ORIGINAL FIT"),
    CROP_FILL("📐 RECORTAR CROP")
}

/**
 * The Mac/PC control surface — the **headline
 * feature** of the Elysium Nexus app.
 *
 * This screen turns the Android phone into a
 * real Mac/PC trackpad + keyboard. The user
 * sees a live "host" panel that mirrors every
 * gesture they make, so the experience is
 * genuinely functional, not a mock.
 *
 * ## Layout (responsive, no overlap)
 *
 * ```
 * ┌─────────────────────────────────────┐
 * │  [ATRÁS]    Status    [?]           │   <- top bar (44dp)
 * ├─────────────────────────────────────┤
 * │  Hero: Host name · CONECTADO        │   <- compact (56dp)
 * ├─────────────────────────────────────┤
 * │  [1d Mouse] [Tap] [2d Scroll] [3d]   │   <- gesture hints (32dp)
 * ├─────────────────────────────────────┤
 * │  ┌─────── TRACKPAD ─────────────┐    │
 * │  │                            │    │
 * │  │   • Cursor                 │    │   <- main area
 * │  │   (multi-touch surface)    │    │      (≥60% screen)
 * │  │                            │    │
 * │  └────────────────────────────┘    │
 * ├─────────────────────────────────────┤
 * │  ⌘  ⌥  ⌃  ⇧  Esc  Tab  ⏎  ⌫         │   <- modifiers+actions
 * └─────────────────────────────────────┘
 *                              [⌨ FAB]
 * ```
 *
 * The layout uses `Column` with `weight(1f)`
 * on the trackpad, so the trackpad always
 * fills the available space regardless of
 * the device's screen size. The modifier
 * bar and action bar sit at the bottom in a
 * single row of equal-width chips, with no
 * overlap.
 *
 * ## Multi-touch gestures (real)
 *
 * The trackpad uses `awaitEachGesture` to
 * process every pointer event. The gestures
 * are classified by **pointer count**:
 *
 *  - **1 pointer**: mouse move + tap.
 *  - **2 pointers**: scroll (drag) +
 *    right-click (tap) + pinch zoom.
 *  - **3 pointers swipe up**: Mission Control
 *    (sends ⌃↑).
 *  - **3 pointers swipe down**: App Exposé
 *    (sends ⌃↓).
 *  - **3 pointers swipe left/right**: switch
 *    Spaces (sends ⌃←/⌃→).
 *
 * The cursor is **continuous** — it moves
 * with each drag event, not in steps. The
 * movement is normalized to the host's
 * coordinate system (0..1).
 *
 * ## The "host" panel (functional mock)
 *
 * The hero card shows a mini "Mac desktop"
 * with a cursor (a cyan dot) that moves in
 * real time based on the trackpad input. When
 * the user taps, a click ripple appears at
 * the tap location. This makes the experience
 * **genuinely functional** — the user sees
 * that their gestures are being recognized
 * and processed.
 *
 * ## Keyboard
 *
 * The keyboard button (FAB) opens a panel
 * with a `BasicTextField`. Tapping the field
 * brings up the **Android system soft
 * keyboard** (the user's normal Android
 * keyboard, not a custom one). Each character
 * typed is sent as a key event to the host.
 *
 * This is the right choice for a real-world
 * product: the user already knows how to
 * type on their phone, has their dictionaries,
 * their emoji, their swipe-to-type, etc. We
 * don't reinvent the keyboard — we just
 * forward the events to the host.
 */
@Composable
fun MacControlSurfaceScreen(
    host: DiscoveredHost,
    onBack: () -> Unit,
    transport: MacTransport? = null,
    posture: com.elysium.nexus.core.posture.Posture = com.elysium.nexus.core.posture.Posture.UNKNOWN,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    // The set of currently-held modifiers.
    // The user can hold multiple at once
    // (e.g. ⌘ + Shift for ⌘⇧P).
    var activeModifiers by remember { mutableStateOf<Set<MacModifier>>(emptySet()) }
    // The cursor position, normalized 0..1
    // over the host's "screen" area. The
    // cursor starts at the center.
    var cursorX by remember { mutableStateOf(0.5f) }
    var cursorY by remember { mutableStateOf(0.5f) }
    // The last event displayed in the status
    // pill. Used for debugging + visual
    // feedback.
    var lastEvent by remember { mutableStateOf<MacInputEvent?>(null) }
    // The list of click ripples currently
    // animating on the host panel. Each
    // ripple is a (x, y, age) tuple; the
    // ripple fades out over 600ms.
    val ripples = remember { mutableStateListOf<Ripple>() }
    val coroutineScope = rememberCoroutineScope()
    // Auto-remove old ripples. The list is
    // pruned every 100ms.
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            ripples.removeAll { now - it.createdAt > 600 }
        }
    }
    // The text input. When the keyboard
    // panel is visible, this TextField is
    // focused and the Android system IME
    var textInput by remember { mutableStateOf("") }
    // Screen mirror state — defaults to true so live screen IS the trackpad on launch
    var screenMirrorEnabled by remember { mutableStateOf(true) }
    // Screen Scale Mode: default to ADAPTIVE_FILL (stretches video to fill container vertical space without black bars)
    var screenScaleMode by remember { mutableStateOf(ScreenScaleMode.ADAPTIVE_FILL) }
    // Input Mode state: default to false (Trackpad Mouse - Relative Delta)
    var isDirectTouchMode by remember { mutableStateOf(false) }
    // Fullscreen View state: when true, hides AppleMagicKeyboard to maximize live screen size
    var isFullScreenView by remember { mutableStateOf(false) }
    // Drag state: true while long-press drag is active on trackpad
    var isDragging by remember { mutableStateOf(false) }
    // Selection lock state: when true, Left Mouse stays pressed (DOWN) for dragging or multi-selection
    var isSelectLocked by remember { mutableStateOf(false) }
    // System keyboard (Android IME) overlay
    var showSystemKeyboard by remember { mutableStateOf(false) }
    var systemTextInput by remember { mutableStateOf("") }
    // Haptic feedback reference
    val view = LocalView.current
    val context = LocalContext.current
    // Interactive Zoom and Pan state for 1080p screen preview
    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var latestScreenBitmap by remember {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var renderedFrameCount by remember(transport) { mutableStateOf(0) }
    var failedFrameCount by remember(transport) { mutableStateOf(0) }
    var screenShareStatus by remember { mutableStateOf<MacProtocol.ScreenShareStatus?>(null) }
    LaunchedEffect(transport) {
        screenShareStatus = null
        transport?.screenStatus?.collect { status ->
            screenShareStatus = status
            android.util.Log.i("MacControlSurface", "Screen share status=${status.name}")
        }
    }
    // Collect screen frames from the transport with RGB_565 fast decoding.
    LaunchedEffect(transport, screenMirrorEnabled) {
        if (transport != null && screenMirrorEnabled) {
            latestScreenBitmap = null
            screenShareStatus = null
            android.util.Log.i("MacControlSurface", "Requesting Mac screen capture")
            transport.sendScreenRequest(true)
            val decodeOptions = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            transport.screenFrames.collect { jpegBytes ->
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (bmp != null) {
                    latestScreenBitmap = bmp
                    renderedFrameCount += 1
                    if (renderedFrameCount == 1 || renderedFrameCount % 120 == 0) {
                        android.util.Log.i(
                            "MacControlSurface",
                            "SCREEN_RENDERED count=$renderedFrameCount resolution=${bmp.width}x${bmp.height} jpegBytes=${jpegBytes.size}"
                        )
                    }
                } else {
                    failedFrameCount += 1
                    if (failedFrameCount == 1 || failedFrameCount % 30 == 0) {
                        android.util.Log.w("MacControlSurface", "SCREEN_RENDER_FAILED count=$failedFrameCount reason=bitmap_decode_null jpegBytes=${jpegBytes.size}")
                    }
                }
            }
        } else if (transport != null && !screenMirrorEnabled) {
            transport.sendScreenRequest(false)
            latestScreenBitmap = null
        }
    }

    // Interactive system notification showing live session on Android
    LaunchedEffect(transport, screenMirrorEnabled) {
        val state = transport?.state?.value
        if (state is com.elysium.nexus.core.transport.mac.MacConnectionState.Ready) {
            com.elysium.nexus.core.transport.mac.MacControlNotificationManager.showConnectedNotification(
                context = context,
                hostName = host.name,
                hostIp = host.host,
                isScreenMirroring = screenMirrorEnabled
            )
        }
    }

    // Stop screen capture and dismiss notification on disposal.
    DisposableEffect(Unit) {
        onDispose {
            transport?.sendScreenRequest(false)
            com.elysium.nexus.core.transport.mac.MacControlNotificationManager.dismissNotification(context)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // === LAYOUT ===
        Column(modifier = Modifier.fillMaxSize()) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                lastEvent?.let { event ->
                    NeonStatusPill(
                        label = event.shortLabel,
                        color = when (event.type) {
                            EventType.MOUSE_MOVE -> ElysiumColors.NeonCyan
                            EventType.CLICK_LEFT, EventType.CLICK_RIGHT -> ElysiumColors.NeonGreen
                            EventType.SCROLL -> ElysiumColors.NeonPurple
                            EventType.ZOOM -> ElysiumColors.NeonOrange
                            EventType.MISSION_CONTROL, EventType.APP_EXPOSE -> ElysiumColors.NeonMagenta
                            EventType.KEY -> ElysiumColors.NeonGreen
                            EventType.MODIFIER -> ElysiumColors.NeonCyan
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElysiumColors.NeonCyan.copy(alpha = 0.55f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                runCatching {
                                    context.startActivity(
                                        Intent().setClassName(
                                            context.packageName,
                                            "com.jsm.core.MainActivity"
                                        )
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "ScreenMirror no está disponible en esta instalación de Elysium.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Cast,
                            contentDescription = "Compartir pantalla del Android al Mac",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    NeonChip(
                        label = if (isFullScreenView) "⌨ VER TECLADO" else "🖥 PANTALLA FULL",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            isFullScreenView = !isFullScreenView
                        },
                        accent = if (isFullScreenView) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                            .clickable { showHelp = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = "Ayuda",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // === EXPANDED LIVE VIDEO SCREEN + TRACKPAD UNIFIED CONTAINER (weight 1f) ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElysiumColors.SurfaceHigh,
                                ElysiumColors.Surface
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (screenMirrorEnabled) ElysiumColors.NeonGreen.copy(alpha = 0.8f)
                                else ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Trackpad(
                    onMouseMove = { dx, dy, nx, ny ->
                        // Direct-touch gestures report absolute coordinates over the live Mac image.
                        cursorX = nx.coerceIn(0f, 1f)
                        cursorY = ny.coerceIn(0f, 1f)
                        lastEvent = MacInputEvent(
                            type = EventType.MOUSE_MOVE,
                            shortLabel = "Trackpad (${dx.roundToInt()}, ${dy.roundToInt()})"
                        )
                        transport?.sendMouseAbsMove(cursorX, cursorY)
                        if (zoomScale > 1.05f) {
                            panOffset = Offset(0.5f - cursorX, 0.5f - cursorY)
                        }
                    },
                    onMouseMoveRelative = { dx, dy ->
                        val factor = 0.006f
                        cursorX = (cursorX + dx * factor).coerceIn(0f, 1f)
                        cursorY = (cursorY + dy * factor).coerceIn(0f, 1f)
                        lastEvent = MacInputEvent(
                            type = EventType.MOUSE_MOVE,
                            shortLabel = "Trackpad (${dx.roundToInt()}, ${dy.roundToInt()})"
                        )
                        transport?.sendMouseMove(dx, dy)
                        if (zoomScale > 1.05f) {
                            panOffset = Offset(0.5f - cursorX, 0.5f - cursorY)
                        }
                    },
                    onLeftClick = { x, y ->
                        if (isDirectTouchMode) {
                            cursorX = x.coerceIn(0f, 1f)
                            cursorY = y.coerceIn(0f, 1f)
                            transport?.sendMouseAbsMove(cursorX, cursorY)
                        }
                        ripples.add(Ripple(cursorX, cursorY, System.currentTimeMillis()))
                        lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "Click Izquierdo")
                        transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                        coroutineScope.launch {
                            delay(30)
                            if (!isSelectLocked) {
                                transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                            }
                        }
                    },
                    onRightClick = { x, y ->
                        if (isDirectTouchMode) {
                            cursorX = x.coerceIn(0f, 1f)
                            cursorY = y.coerceIn(0f, 1f)
                            transport?.sendMouseAbsMove(cursorX, cursorY)
                        }
                        ripples.add(Ripple(cursorX, cursorY, System.currentTimeMillis()))
                        lastEvent = MacInputEvent(type = EventType.CLICK_RIGHT, shortLabel = "Click Derecho")
                        transport?.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.DOWN)
                        coroutineScope.launch {
                            delay(30)
                            transport?.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.UP)
                        }
                    },
                    onScroll = { dx, dy ->
                        lastEvent = MacInputEvent(type = EventType.SCROLL, shortLabel = "Scroll (${dx.roundToInt()}, ${dy.roundToInt()})")
                        transport?.sendScroll(dx, dy)
                    },
                    onZoom = { factor ->
                        zoomScale = (zoomScale * factor).coerceIn(1.0f, 4.0f)
                        lastEvent = MacInputEvent(type = EventType.ZOOM, shortLabel = "Zoom ${String.format("%.1f", zoomScale)}x")
                    },
                    onMissionControl = {
                        lastEvent = MacInputEvent(type = EventType.MISSION_CONTROL, shortLabel = "Mission Control")
                        transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x52, MacProtocol.Modifiers.CONTROL)
                        transport?.sendKey(MacProtocol.KeyAction.UP, 0x52, MacProtocol.Modifiers.CONTROL)
                    },
                    onAppExpose = {
                        lastEvent = MacInputEvent(type = EventType.APP_EXPOSE, shortLabel = "App Exposé")
                        transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x51, MacProtocol.Modifiers.CONTROL)
                        transport?.sendKey(MacProtocol.KeyAction.UP, 0x51, MacProtocol.Modifiers.CONTROL)
                    },
                    onNextSpace = {
                        lastEvent = MacInputEvent(type = EventType.MISSION_CONTROL, shortLabel = "Cambiar App ➔")
                        transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x4F, MacProtocol.Modifiers.CONTROL)
                        transport?.sendKey(MacProtocol.KeyAction.UP, 0x4F, MacProtocol.Modifiers.CONTROL)
                    },
                    onPrevSpace = {
                        lastEvent = MacInputEvent(type = EventType.MISSION_CONTROL, shortLabel = "◄ Cambiar App")
                        transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x50, MacProtocol.Modifiers.CONTROL)
                        transport?.sendKey(MacProtocol.KeyAction.UP, 0x50, MacProtocol.Modifiers.CONTROL)
                    },
                    onDragStart = {
                        isDragging = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "⟹ ARRASTRANDO (DRAG)")
                        transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                    },
                    onDragEnd = {
                        isDragging = false
                        if (!isSelectLocked) {
                            transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                        }
                        lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "⟹ FIN ARRASTRE")
                    },
                    cursorX = cursorX,
                    cursorY = cursorY,
                    ripples = ripples,
                    backgroundBitmap = if (screenMirrorEnabled) latestScreenBitmap else null,
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    isDirectTouchMode = isDirectTouchMode,
                    screenScaleMode = screenScaleMode,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay top row controls for 1080p HD Screen Mirror, Mode Switch & Zoom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (screenMirrorEnabled && latestScreenBitmap != null) {
                                        ElysiumColors.NeonGreen.copy(alpha = 0.9f)
                                    } else {
                                        ElysiumColors.NeonOrange.copy(alpha = 0.9f)
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    !screenMirrorEnabled -> "● TRACKPAD"
                                    latestScreenBitmap != null -> "● MAC EN VIVO"
                                    screenShareStatus == MacProtocol.ScreenShareStatus.SCREEN_RECORDING_PERMISSION_REQUIRED -> "● PERMISO DEL MAC"
                                    screenShareStatus == MacProtocol.ScreenShareStatus.CAPTURE_FAILED -> "● ERROR DE CAPTURA"
                                    screenShareStatus == MacProtocol.ScreenShareStatus.ALREADY_STREAMING -> "● MAC EN USO"
                                    else -> "● ESPERANDO MAC"
                                },
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.Black
                            )
                        }

                        // Screen Scale Mode Toggle Button: ADAPTATIVO (LLENAR) / ORIGINAL FIT / CROP
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElysiumColors.NeonPurple.copy(alpha = 0.35f))
                                .border(1.dp, ElysiumColors.NeonPurple, RoundedCornerShape(4.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    screenScaleMode = when (screenScaleMode) {
                                        ScreenScaleMode.ADAPTIVE_FILL -> ScreenScaleMode.ORIGINAL_FIT
                                        ScreenScaleMode.ORIGINAL_FIT -> ScreenScaleMode.CROP_FILL
                                        ScreenScaleMode.CROP_FILL -> ScreenScaleMode.ADAPTIVE_FILL
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = screenScaleMode.label,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElysiumColors.NeonPurple
                            )
                        }

                        // Mode Switcher Badge: Direct Touch vs Trackpad Mouse
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDirectTouchMode) ElysiumColors.NeonGreen.copy(alpha = 0.3f) else ElysiumColors.NeonPurple.copy(alpha = 0.3f))
                                .border(1.dp, if (isDirectTouchMode) ElysiumColors.NeonGreen else ElysiumColors.NeonPurple, RoundedCornerShape(4.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isDirectTouchMode = !isDirectTouchMode
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isDirectTouchMode) "👆 TÁCTIL DIRECTO" else "🖱 MODO MOUSE",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isDirectTouchMode) ElysiumColors.NeonGreen else ElysiumColors.NeonPurple
                            )
                        }

                        // Zoom Level Selector Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElysiumColors.NeonCyan.copy(alpha = 0.3f))
                                .border(1.dp, ElysiumColors.NeonCyan, RoundedCornerShape(4.dp))
                                .clickable {
                                    zoomScale = if (zoomScale < 1.4f) 1.5f else if (zoomScale < 2.2f) 2.5f else if (zoomScale < 3.2f) 3.5f else 1.0f
                                    if (zoomScale == 1.0f) panOffset = Offset.Zero
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔍 ${String.format("%.1f", zoomScale)}x",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElysiumColors.NeonCyan
                            )
                        }
                        // Full Screen Mode Toggle Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isFullScreenView) ElysiumColors.NeonGreen.copy(alpha = 0.35f) else ElysiumColors.NeonOrange.copy(alpha = 0.3f))
                                .border(1.dp, if (isFullScreenView) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange, RoundedCornerShape(4.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isFullScreenView = !isFullScreenView
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isFullScreenView) "🖥 PANTALLA MAXIMIZADA" else "🖥 PANTALLA COMPLETA",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isFullScreenView) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElysiumColors.NeonPurple.copy(alpha = 0.3f))
                            .border(1.dp, ElysiumColors.NeonPurple, RoundedCornerShape(6.dp))
                            .clickable { screenMirrorEnabled = !screenMirrorEnabled }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (screenMirrorEnabled) "🖱 MODO TRACKPAD" else "📺 PANTALLA EN VIVO",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = ElysiumColors.NeonPurple
                        )
                    }
                }
            }

            // === PHYSICAL DEDICATED MOUSE BUTTONS (BOTÓN IZQUIERDO / BOTÓN DERECHO) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // BOTÓN IZQUIERDO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    ElysiumColors.NeonCyan.copy(alpha = 0.25f),
                                    ElysiumColors.SurfaceHigh
                                )
                            )
                        )
                        .border(1.5.dp, ElysiumColors.NeonCyan, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                ripples.add(Ripple(cursorX, cursorY, System.currentTimeMillis()))
                                lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "BOTÓN IZQUIERDO")
                                transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentChange = event.changes.firstOrNull { it.id == down.id }
                                    if (currentChange == null || !currentChange.pressed) {
                                        break
                                    }
                                }

                                transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mouse, contentDescription = null, tint = ElysiumColors.NeonCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "BOTÓN IZQUIERDO",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                            color = ElysiumColors.NeonCyan
                        )
                    }
                }

                // BOTÓN SELECCIONAR / ARRASTRAR (LOCK / UNLOCK)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                if (isSelectLocked) listOf(
                                    ElysiumColors.NeonOrange.copy(alpha = 0.45f),
                                    ElysiumColors.SurfaceHigh
                                ) else listOf(
                                    ElysiumColors.NeonGreen.copy(alpha = 0.2f),
                                    ElysiumColors.SurfaceHigh
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelectLocked) ElysiumColors.NeonOrange else ElysiumColors.NeonGreen,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            isSelectLocked = !isSelectLocked
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            if (isSelectLocked) {
                                lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "🔒 MODO ARRASTRE ON")
                                transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                            } else {
                                lastEvent = MacInputEvent(type = EventType.CLICK_LEFT, shortLabel = "🔓 ARRASTRE OFF")
                                transport?.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelectLocked) Icons.Filled.RadioButtonChecked else Icons.Filled.OpenWith,
                            contentDescription = null,
                            tint = if (isSelectLocked) ElysiumColors.NeonOrange else ElysiumColors.NeonGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isSelectLocked) "ARRAS. ON" else "SELECCIONAR",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                            color = if (isSelectLocked) ElysiumColors.NeonOrange else ElysiumColors.NeonGreen
                        )
                    }
                }

                // BOTÓN DERECHO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    ElysiumColors.NeonMagenta.copy(alpha = 0.25f),
                                    ElysiumColors.SurfaceHigh
                                )
                            )
                        )
                        .border(1.5.dp, ElysiumColors.NeonMagenta, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                ripples.add(Ripple(cursorX, cursorY, System.currentTimeMillis()))
                                lastEvent = MacInputEvent(type = EventType.CLICK_RIGHT, shortLabel = "BOTÓN DERECHO")
                                transport?.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.DOWN)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentChange = event.changes.firstOrNull { it.id == down.id }
                                    if (currentChange == null || !currentChange.pressed) {
                                        break
                                    }
                                }

                                transport?.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.UP)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mouse, contentDescription = null, tint = ElysiumColors.NeonMagenta, modifier = Modifier.size(16.dp))
                        Text(
                            text = "BOTÓN DERECHO",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                            color = ElysiumColors.NeonMagenta
                        )
                    }
                }
            }

            // === PROPRIETARY APPLE MAGIC KEYBOARD (BOTTOM AREA - Hidden in Full Screen Mode) ===
            if (!isFullScreenView) {
                AppleMagicKeyboard(
                    transport = transport,
                    activeModifiers = activeModifiers,
                    onModifierToggle = { mod ->
                        activeModifiers = if (mod in activeModifiers) activeModifiers - mod else activeModifiers + mod
                        lastEvent = MacInputEvent(
                            type = EventType.MODIFIER,
                            shortLabel = "${mod.symbol} ${if (mod in activeModifiers) "on" else "off"}"
                        )
                    },
                    onKeyTrigger = { label, hidUsage, isMedia ->
                        lastEvent = MacInputEvent(type = EventType.KEY, shortLabel = label)
                        if (!isMedia && hidUsage != 0) {
                            val mods = modifiersToBits(activeModifiers)
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, hidUsage, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, hidUsage, mods)
                        }
                    },
                    onToggleSystemKeyboard = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showSystemKeyboard = !showSystemKeyboard
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // === SYSTEM KEYBOARD PANEL OVERLAY ===
        AnimatedVisibility(
            visible = showSystemKeyboard,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp, start = 8.dp, end = 8.dp)
        ) {
            AndroidKeyboardPanel(
                text = systemTextInput,
                onTextChange = { newText ->
                    if (newText.length > systemTextInput.length) {
                        val newChars = newText.substring(systemTextInput.length)
                        for (c in newChars) {
                            val (usage, needsShift) = asciiToHidUsage(c)
                            val mods = modifiersToBits(activeModifiers) or
                                (if (needsShift) MacProtocol.Modifiers.SHIFT else 0)
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, usage, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, usage, mods)
                        }
                    }
                    systemTextInput = newText
                },
                onBackspace = {
                    transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2A, 0)
                    transport?.sendKey(MacProtocol.KeyAction.UP, 0x2A, 0)
                    if (systemTextInput.isNotEmpty()) {
                        systemTextInput = systemTextInput.dropLast(1)
                    }
                },
                onSpace = {
                    transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2C, 0)
                    transport?.sendKey(MacProtocol.KeyAction.UP, 0x2C, 0)
                    systemTextInput += " "
                },
                onModifier = { mod ->
                    activeModifiers = if (mod in activeModifiers) activeModifiers - mod else activeModifiers + mod
                },
                activeModifiers = activeModifiers,
                onClose = { showSystemKeyboard = false },
                autoFocus = showSystemKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Control de ${host.name}",
            whatIsThis = "Esta pantalla convierte tu teléfono en un trackpad y " +
                "teclado profesional para tu ${host.name}.",
            howToUse = listOf(
                "1 dedo arrastra: mueve el cursor (verás el punto cyan en la pantalla de arriba).",
                "1 dedo tap: click izquierdo (verás una onda de click).",
                "2 dedos arrastra: scroll vertical y horizontal.",
                "2 dedos tap: click derecho.",
                "Pellizca con 2 dedos: zoom.",
                "3 dedos desliza arriba/abajo: Mission Control / App Exposé.",
                "Toca los chips ⌘ ⌥ ⌃ ⇧ para mantenerlos presionados.",
                "Toca el botón morado ⌨ para abrir el teclado Android."
            ),
            tip = "Cuando escribes en el teclado Android, cada carácter se " +
                "envía al ${host.name} en tiempo real. ¡Prueba con Cmd+Q " +
                "para cerrar una app!",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * The mini "host" panel — a visual representation
 * of the connected Mac/PC's screen. Shows the
 * cursor position, click ripples, and a status
 * bar. The panel is purely visual feedback —
 * the user sees their gestures being processed.
 */
@Composable
private fun HostPanel(
    cursorX: Float,
    cursorY: Float,
    hostName: String,
    isConnected: Boolean,
    ripples: List<Ripple>,
    activeModifiers: Set<MacModifier>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElysiumColors.SurfaceHigh,
                        ElysiumColors.Surface
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                shape = shape
            )
    ) {
        // The "Mac desktop" — a subtle
        // gradient with a grid of dots (the
        // macOS wallpaper hint).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElysiumColors.NeonCyan.copy(alpha = 0.08f),
                            ElysiumColors.NeonPurple.copy(alpha = 0.05f)
                        )
                    )
                )
        )
        // The cursor.
        val cursorXOffset by animateFloatAsState(
            targetValue = cursorX,
            animationSpec = spring(stiffness = 600f),
            label = "cursor_x"
        )
        val cursorYOffset by animateFloatAsState(
            targetValue = cursorY,
            animationSpec = spring(stiffness = 600f),
            label = "cursor_y"
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = maxWidth
            val h = maxHeight
            // The cursor (a glowing cyan dot).
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * cursorXOffset
                val cy = size.height * cursorYOffset
                // The cursor halo.
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                    radius = 14.dp.toPx(),
                    center = Offset(cx, cy)
                )
                // The cursor itself.
                drawCircle(
                    color = ElysiumColors.NeonCyan,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
                // The click ripples. Each
                // ripple expands from its
                // tap location and fades.
                val now = System.currentTimeMillis()
                ripples.forEach { ripple ->
                    val age = (now - ripple.createdAt) / 600f
                    if (age < 1f) {
                        val r = (10 + age * 50).dp.toPx()
                        val alpha = (1f - age) * 0.8f
                        drawCircle(
                            color = ElysiumColors.NeonCyan.copy(alpha = alpha),
                            radius = r,
                            center = Offset(size.width * ripple.x, size.height * ripple.y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
        // The status bar at the top of the
        // host panel. Shows the host name +
        // active modifiers.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ElysiumColors.NeonGreen
                            else ElysiumColors.NeonMagenta
                        )
                )
                Text(
                    text = hostName,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ElysiumColors.OnSurface
                )
            }
            if (activeModifiers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    activeModifiers.forEach { mod ->
                        Text(
                            text = mod.symbol,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = when (mod) {
                                MacModifier.CMD -> ElysiumColors.NeonCyan
                                MacModifier.OPT -> ElysiumColors.NeonOrange
                                MacModifier.CTRL -> ElysiumColors.NeonPurple
                                MacModifier.SHIFT -> ElysiumColors.NeonGreen
                                MacModifier.ESC -> ElysiumColors.NeonMagenta
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The trackpad. The user multi-touch surface
 * that captures all gestures. Uses
 * `awaitEachGesture` to process every
 * pointer event with full multi-touch
 * awareness.
 *
 * The gesture classification is based on
 * **pointer count** at the time of the
 * event:
 *
 *  - 1 pointer → mouse move / left click
 *  - 2 pointers → scroll / right click /
 *    pinch zoom
 *  - 3 pointers → swipe gesture (up/down =
 *    Mission Control / App Exposé,
 *    left/right = switch Spaces)
 */
@Composable
private fun Trackpad(
    onMouseMove: (dx: Float, dy: Float, nx: Float, ny: Float) -> Unit,
    onMouseMoveRelative: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onLeftClick: (x: Float, y: Float) -> Unit,
    onRightClick: (x: Float, y: Float) -> Unit,
    onScroll: (dx: Float, dy: Float) -> Unit,
    onZoom: (factor: Float) -> Unit,
    onMissionControl: () -> Unit,
    onAppExpose: () -> Unit,
    onNextSpace: () -> Unit = {},
    onPrevSpace: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    cursorX: Float = 0.5f,
    cursorY: Float = 0.5f,
    ripples: List<Ripple> = emptyList(),
    backgroundBitmap: android.graphics.Bitmap? = null,
    zoomScale: Float = 1f,
    panOffset: Offset = Offset.Zero,
    isDirectTouchMode: Boolean = true,
    screenScaleMode: ScreenScaleMode = ScreenScaleMode.ADAPTIVE_FILL,
    modifier: Modifier = Modifier
) {
    // --- rememberUpdatedState: keeps gesture lambda reading latest values
    // WITHOUT restarting the pointer handler on every video frame ---
    val currentDirectTouch by rememberUpdatedState(isDirectTouchMode)
    val currentScaleMode by rememberUpdatedState(screenScaleMode)
    val currentBitmap by rememberUpdatedState(backgroundBitmap)

    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (backgroundBitmap != null) Color.Black
                else ElysiumColors.Surface
            )
            .border(
                width = 2.dp,
                color = if (backgroundBitmap != null) ElysiumColors.NeonGreen.copy(alpha = 0.8f)
                        else ElysiumColors.NeonCyan.copy(alpha = 0.6f),
                shape = shape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    var isTap = true
                    var peakPointerCount = currentEvent.changes.size
                    var totalDrag = Offset.Zero
                    var initialPinchDistance: Float? = null
                    var isLongPressDrag = false
                    android.util.Log.d("MacTrackpad", "GESTURE_BEGIN pointers=${currentEvent.changes.size} directTouch=$currentDirectTouch")

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val currentChanges = event.changes
                        if (currentChanges.size > peakPointerCount) {
                            peakPointerCount = currentChanges.size
                        }

                        val allUp = currentChanges.none { it.pressed }
                        if (allUp) {
                            // End active drag if any
                            if (isLongPressDrag) {
                                onDragEnd()
                                isLongPressDrag = false
                                break
                            }
                            val duration = System.currentTimeMillis() - startTime
                            android.util.Log.d(
                                "MacTrackpad",
                                "GESTURE_END durationMs=$duration peakPointers=$peakPointerCount moved=${!isTap} dragX=${totalDrag.x.toInt()} dragY=${totalDrag.y.toInt()}"
                            )
                            if (isTap && duration < 300 && totalDrag.getDistance() < 20f) {
                                val tapPos = firstDown.position
                                val (nx, ny) = calculateNormalizedTouch(
                                    posX = tapPos.x,
                                    posY = tapPos.y,
                                    containerW = size.width.toFloat(),
                                    containerH = size.height.toFloat(),
                                    bitmapW = currentBitmap?.width,
                                    bitmapH = currentBitmap?.height,
                                    scaleMode = currentScaleMode
                                )
                                val isBottomRight = (nx > 0.70f && ny > 0.70f)
                                if (peakPointerCount == 2 || isBottomRight) {
                                    onRightClick(nx, ny)
                                } else {
                                    onLeftClick(nx, ny)
                                }
                            } else if (peakPointerCount >= 3 && totalDrag.getDistance() > 40f) {
                                val adx = abs(totalDrag.x)
                                val ady = abs(totalDrag.y)
                                if (ady > adx) {
                                    if (totalDrag.y < 0) {
                                        onMissionControl()
                                    } else {
                                        onAppExpose()
                                    }
                                } else {
                                    if (totalDrag.x < 0) {
                                        onNextSpace()
                                    } else {
                                        onPrevSpace()
                                    }
                                }
                            }
                            break
                        }

                        var frameDrag = Offset.Zero
                        val pressedPointers = currentChanges.filter { it.pressed }
                        for (change in pressedPointers) {
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                frameDrag += delta
                                change.consume()
                            }
                        }

                        if (frameDrag.getDistance() > 0.5f) {
                            isTap = false
                        }
                        totalDrag += frameDrag

                        // Trigger Long-Press Drag if held >= 280ms with minimal initial movement
                        val elapsed = System.currentTimeMillis() - startTime
                        if (pressedPointers.size == 1 && !isLongPressDrag && elapsed >= 280 && totalDrag.getDistance() < 22f) {
                            isLongPressDrag = true
                            isTap = false
                            onDragStart()
                        }

                        when (pressedPointers.size) {
                            1 -> {
                                if (pressedPointers.isNotEmpty()) {
                                    if (currentDirectTouch) {
                                        val pos = pressedPointers[0].position
                                        val (nx, ny) = calculateNormalizedTouch(
                                            posX = pos.x,
                                            posY = pos.y,
                                            containerW = size.width.toFloat(),
                                            containerH = size.height.toFloat(),
                                            bitmapW = currentBitmap?.width,
                                            bitmapH = currentBitmap?.height,
                                            scaleMode = currentScaleMode
                                        )
                                        onMouseMove(frameDrag.x, frameDrag.y, nx, ny)
                                    } else {
                                        if (frameDrag != Offset.Zero) {
                                            onMouseMoveRelative(frameDrag.x, frameDrag.y)
                                        }
                                    }
                                }
                            }
                            2 -> {
                                val p1 = pressedPointers[0].position
                                val p2 = pressedPointers[1].position
                                val currentDist = (p2 - p1).getDistance()
                                if (initialPinchDistance == null) {
                                    initialPinchDistance = currentDist
                                } else if (initialPinchDistance!! > 15f && currentDist > 15f) {
                                    val factor = currentDist / initialPinchDistance!!
                                    if (abs(factor - 1f) > 0.04f) {
                                        onZoom(factor)
                                        initialPinchDistance = currentDist
                                    } else if (frameDrag != Offset.Zero) {
                                        val scrollAccel = 2.2f
                                        onScroll(frameDrag.x * scrollAccel, frameDrag.y * scrollAccel)
                                    }
                                } else if (frameDrag != Offset.Zero) {
                                    val scrollAccel = 2.2f
                                    onScroll(frameDrag.x * scrollAccel, frameDrag.y * scrollAccel)
                                }
                            }
                            3 -> {
                                if (frameDrag != Offset.Zero) {
                                    isTap = false
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (backgroundBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = backgroundBitmap.asImageBitmap(),
                contentDescription = "Mac Screen Live Stream 1080p HD",
                contentScale = when (screenScaleMode) {
                    ScreenScaleMode.ADAPTIVE_FILL -> androidx.compose.ui.layout.ContentScale.FillBounds
                    ScreenScaleMode.ORIGINAL_FIT -> androidx.compose.ui.layout.ContentScale.Fit
                    ScreenScaleMode.CROP_FILL -> androidx.compose.ui.layout.ContentScale.Crop
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        val maxTx = size.width * (zoomScale - 1f) / 2f
                        val maxTy = size.height * (zoomScale - 1f) / 2f
                        translationX = (panOffset.x * size.width * zoomScale).coerceIn(-maxTx, maxTx)
                        translationY = (panOffset.y * size.height * zoomScale).coerceIn(-maxTy, maxTy)
                    }
            )
        } else {
            TrackpadDots()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Mouse,
                        contentDescription = null,
                        tint = ElysiumColors.NeonCyan.copy(alpha = 0.3f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "TRACKPAD",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        ),
                        color = ElysiumColors.NeonCyan.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "1d · 2d · pinch · 3d swipe",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        ),
                        color = ElysiumColors.OnSurfaceMuted.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Real-Time Click Ripples & Trackpad-Only Cursor Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * cursorX
            val cy = size.height * cursorY

            // Only draw Android local cursor if live video stream is NOT active
            // (when video stream is active, real Mac cursor is already rendered directly by Mac agent)
            if (backgroundBitmap == null) {
                drawMacOsCursorPointer(cx, cy)
            }

            // Click Ripples
            val now = System.currentTimeMillis()
            ripples.forEach { ripple ->
                val age = (now - ripple.createdAt) / 600f
                if (age < 1f) {
                    val r = (10 + age * 50).dp.toPx()
                    val alpha = (1f - age) * 0.8f
                    drawCircle(
                        color = ElysiumColors.NeonGreen.copy(alpha = alpha),
                        radius = r,
                        center = Offset(size.width * ripple.x, size.height * ripple.y),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMacOsCursorPointer(
    cx: Float,
    cy: Float
) {
    // Outer cyan glow aura
    drawCircle(
        color = ElysiumColors.NeonCyan.copy(alpha = 0.45f),
        radius = 16.dp.toPx(),
        center = Offset(cx, cy)
    )

    // macOS Classic Mouse Cursor Arrow Path
    val scale = 1.4f
    val cursorPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, cy)
        lineTo(cx + 0f * scale, cy + 22.dp.toPx() * scale)
        lineTo(cx + 5.5.dp.toPx() * scale, cy + 16.5.dp.toPx() * scale)
        lineTo(cx + 10.dp.toPx() * scale, cy + 26.dp.toPx() * scale)
        lineTo(cx + 14.dp.toPx() * scale, cy + 24.dp.toPx() * scale)
        lineTo(cx + 9.5.dp.toPx() * scale, cy + 14.5.dp.toPx() * scale)
        lineTo(cx + 16.dp.toPx() * scale, cy + 14.5.dp.toPx() * scale)
        close()
    }

    // 1. Black outer shadow/stroke for maximum contrast
    drawPath(
        path = cursorPath,
        color = Color.Black,
        style = Stroke(width = 3.dp.toPx())
    )
    // 2. High-contrast White arrow body
    drawPath(
        path = cursorPath,
        color = Color.White
    )
    // 3. Neon Cyan inner contour highlight
    drawPath(
        path = cursorPath,
        color = ElysiumColors.NeonCyan,
        style = Stroke(width = 1.2.dp.toPx())
    )
    // 4. Precision tip hot-point dot
    drawCircle(
        color = ElysiumColors.NeonGreen,
        radius = 3.5.dp.toPx(),
        center = Offset(cx, cy)
    )
}

@Composable
private fun TrackpadDots() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / 30
        val stepY = size.height / 20
        for (i in 0..30) {
            for (j in 0..20) {
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.04f),
                    radius = 0.8.dp.toPx(),
                    center = Offset(i * stepX, j * stepY)
                )
            }
        }
    }
}

/**
 * The media bar (Phase ULT.7). A row of
 * equal-width chips for the system media
 * keys: volume down / up / mute + previous /
 * play-pause / next. The chips send the
 * `MEDIA` frame to the Mac agent, which
 * dispatches the corresponding `NSEvent` to
 * macOS.
 */
@Composable
private fun MediaBar(
    transport: MacTransport?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MediaButton("🔉", "Vol-", ElysiumColors.NeonOrange, { transport?.sendMedia(1) }, Modifier.weight(1f))
        MediaButton("🔇", "Mute", ElysiumColors.NeonOrange, { transport?.sendMedia(7) }, Modifier.weight(1f))
        MediaButton("🔊", "Vol+", ElysiumColors.NeonOrange, { transport?.sendMedia(0) }, Modifier.weight(1f))
        MediaButton("⏮", "Prev", ElysiumColors.NeonPurple, { transport?.sendMedia(17) }, Modifier.weight(1f))
        MediaButton("⏯", "Play", ElysiumColors.NeonGreen, { transport?.sendMedia(16) }, Modifier.weight(1f))
        MediaButton("⏭", "Next", ElysiumColors.NeonPurple, { transport?.sendMedia(18) }, Modifier.weight(1f))
    }
}

@Composable
private fun RowScope.MediaButton(
    label: String,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "media_btn_scale"
    )
    Box(
        modifier = modifier
            .height(40.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.18f), ElysiumColors.Surface)
                )
            )
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

/**
 * The combined modifier + action bar at the
 * bottom. The bar has 8 equal-width cells:
 * 4 modifier chips, then 4 action chips
 * (Tab, Esc, arrow keys, Return, Backspace).
 *
 * The bar is `wrapContentHeight` so it
 * always fits at the bottom without
 * overlapping the trackpad.
 */
@Composable
private fun ModifierActionBar(
    activeModifiers: Set<MacModifier>,
    onModifierToggle: (MacModifier) -> Unit,
    onClearModifiers: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = listOf<@Composable RowScope.() -> Unit>(
        // Row 1: 5 modifier chips
        { ModifierChip(MacModifier.CMD, MacModifier.CMD in activeModifiers, { onModifierToggle(MacModifier.CMD) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.OPT, MacModifier.OPT in activeModifiers, { onModifierToggle(MacModifier.OPT) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.CTRL, MacModifier.CTRL in activeModifiers, { onModifierToggle(MacModifier.CTRL) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.SHIFT, MacModifier.SHIFT in activeModifiers, { onModifierToggle(MacModifier.SHIFT) }, Modifier.weight(1f)) },
        { ActionChip("Tab", "Tab", ElysiumColors.NeonCyan, { onAction("Tab") }, Modifier.weight(1f)) },
        { ActionChip("Esc", "Esc", ElysiumColors.NeonMagenta, { onAction("Esc") }, Modifier.weight(1f)) },
        { ActionChip("↑", "Up", ElysiumColors.NeonCyan, { onAction("↑") }, Modifier.weight(1f)) },
        { ActionChip("↓", "Down", ElysiumColors.NeonCyan, { onAction("↓") }, Modifier.weight(1f)) },
        { ActionChip("←", "Left", ElysiumColors.NeonCyan, { onAction("←") }, Modifier.weight(1f)) },
        { ActionChip("→", "Right", ElysiumColors.NeonCyan, { onAction("→") }, Modifier.weight(1f)) },
        { ActionChip("⌫", "Bksp", ElysiumColors.NeonOrange, { onAction("⌫") }, Modifier.weight(1f)) },
        { ActionChip("⏎", "Enter", ElysiumColors.NeonGreen, { onAction("⏎") }, Modifier.weight(1f)) }
    )
    // The bar uses Column with 2 rows: 5
    // modifiers on top, 7 actions on
    // bottom. The cells auto-wrap based on
    // width.
    val itemsPerRow = 6
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        cells.chunked(itemsPerRow).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCells.forEach { cell ->
                    cell()
                }
            }
        }
    }
}

@Composable
private fun ModifierChip(
    modifier: MacModifier,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier2: Modifier = Modifier
) {
    val color = when (modifier) {
        MacModifier.CMD -> ElysiumColors.NeonCyan
        MacModifier.OPT -> ElysiumColors.NeonOrange
        MacModifier.CTRL -> ElysiumColors.NeonPurple
        MacModifier.SHIFT -> ElysiumColors.NeonGreen
        MacModifier.ESC -> ElysiumColors.NeonMagenta
    }
    val scale by animateFloatAsState(
        targetValue = if (isActive) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "mod_scale"
    )
    Box(
        modifier = modifier2
            .height(44.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) {
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.4f),
                            color.copy(alpha = 0.15f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            ElysiumColors.SurfaceHigh,
                            ElysiumColors.Surface
                        )
                    )
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) color else ElysiumColors.Outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = modifier.symbol,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (isActive) color else ElysiumColors.OnSurface
        )
    }
}

@Composable
private fun RowScope.ActionChip(
    symbol: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "action_chip_scale"
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.18f),
                        ElysiumColors.Surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

/**
 * The Android system keyboard panel. When the
 * user taps the FAB, this panel appears at the
 * bottom. It contains:
 *
 *  - A `BasicTextField` that, when focused,
 *    shows the **Android system soft keyboard**
 *    (the user's normal Android keyboard, not
 *    a custom one).
 *  - A row of modifier chips (Cmd, Option,
 *    Ctrl, Shift) for sending keyboard
 *    shortcuts.
 *  - Space + Backspace buttons.
 *  - A close button.
 *
 * Each character the user types is captured
 * by the `onTextChange` callback and sent to
 * the host as a key event. The IME brings its
 * own dictionaries, emoji, swipe-to-type, etc.
 */
@Composable
private fun AndroidKeyboardPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onModifier: (MacModifier) -> Unit,
    activeModifiers: Set<MacModifier>,
    onClose: () -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier
) {
    val imeController = LocalSoftwareKeyboardController.current
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonPurple,
        cornerRadius = 16.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: title + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = ElysiumColors.NeonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Teclado Android",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ElysiumColors.NeonPurple
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.Surface)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = ElysiumColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // The native TextField. Tapping
            // it brings up the Android
            // system IME.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElysiumColors.Surface)
                    .border(
                        width = 1.dp,
                        color = ElysiumColors.NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Toca aquí para escribir...",
                        style = TextStyle(fontSize = 14.sp),
                        color = ElysiumColors.OnSurfaceMuted
                    )
                } else {
                    Text(
                        text = text,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = ElysiumColors.OnSurface
                        )
                    )
                }
                // The actual TextField (transparent, on top).
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Transparent
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
            // The modifier + action row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModifierChip(
                    MacModifier.CMD,
                    MacModifier.CMD in activeModifiers,
                    { onModifier(MacModifier.CMD) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.OPT,
                    MacModifier.OPT in activeModifiers,
                    { onModifier(MacModifier.OPT) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.CTRL,
                    MacModifier.CTRL in activeModifiers,
                    { onModifier(MacModifier.CTRL) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.SHIFT,
                    MacModifier.SHIFT in activeModifiers,
                    { onModifier(MacModifier.SHIFT) },
                    Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionChip(
                    "Espacio",
                    "Space",
                    ElysiumColors.NeonCyan,
                    onSpace,
                    Modifier.weight(2f)
                )
                ActionChip(
                    "⌫ Borrar",
                    "Bksp",
                    ElysiumColors.NeonOrange,
                    onBackspace,
                    Modifier.weight(1f)
                )
                ActionChip(
                    "⏎",
                    "Enter",
                    ElysiumColors.NeonGreen,
                    { onTextChange("$text\n") },
                    Modifier.weight(1f)
                )
            }
        }
    }
    // Auto-focus the TextField when the
    // panel appears, bringing up the IME.
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(200)
            imeController?.show()
        }
    }
}

/**
 * A macOS modifier key.
 */
enum class MacModifier(val symbol: String) {
    CMD("⌘"),
    OPT("⌥"),
    CTRL("⌃"),
    SHIFT("⇧"),
    ESC("Esc")
}

/**
 * A typed input event from the trackpad /
 * keyboard. Used for the status pill +
 * diagnostic.
 */
data class MacInputEvent(
    val type: EventType,
    val shortLabel: String
)

enum class EventType {
    MOUSE_MOVE,
    CLICK_LEFT,
    CLICK_RIGHT,
    SCROLL,
    ZOOM,
    MISSION_CONTROL,
    APP_EXPOSE,
    KEY,
    MODIFIER
}

/**
 * A click ripple on the host panel.
 */
data class Ripple(
    val x: Float,
    val y: Float,
    val createdAt: Long
)

/**
 * A small "gesture hint" chip.
 */
@Composable
private fun HintChip(
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = sublabel,
                style = TextStyle(fontSize = 8.sp),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
    }
}

/**
 * The scale modifier for animation (alias
 * to avoid the import collision with
 * `androidx.compose.ui.draw.scale`).
 */
@Composable
internal fun Modifier.androidScale(scale: Float): Modifier {
    val s = if (scale == 0f) 0.001f else scale
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * Convert the UI-level [Set]<[MacModifier]> to
 * the wire-level [MacProtocol.Modifiers] bitmask.
 * `ESC` is not a modifier (it's a key), so it's
 * ignored here.
 */
internal fun modifiersToBits(mods: Set<MacModifier>): Int {
    var bits = 0
    if (MacModifier.CMD in mods) bits = bits or MacProtocol.Modifiers.COMMAND
    if (MacModifier.OPT in mods) bits = bits or MacProtocol.Modifiers.OPTION
    if (MacModifier.CTRL in mods) bits = bits or MacProtocol.Modifiers.CONTROL
    if (MacModifier.SHIFT in mods) bits = bits or MacProtocol.Modifiers.SHIFT
    return bits
}

/**
 * Map an ASCII character to its USB HID usage
 * code. Returns the HID usage and a boolean
 * indicating whether Shift must be held to type
 * the character.
 *
 * Covers the printable ASCII range. Non-ASCII
 * characters fall back to a no-op (the user
 * sees the typed character in the text input,
 * but no event is sent to the host — emoji /
 * accented chars require a richer IM model
 * that Phase ULT.5 will add).
 */
internal fun asciiToHidUsage(c: Char): Pair<Int, Boolean> {
    if (c in 'a'..'z') {
        return Pair(0x04 + (c - 'a'), false)
    }
    if (c in 'A'..'Z') {
        return Pair(0x04 + (c - 'A'), true)
    }
    if (c in '1'..'9') {
        return Pair(0x1E + (c - '1'), false)
    }
    return when (c) {
        '0' -> Pair(0x27, false)
        ' ' -> Pair(0x2C, false)
        '\n' -> Pair(0x28, false)
        '\t' -> Pair(0x2B, false)
        '-' -> Pair(0x2D, false)
        '=' -> Pair(0x2E, false)
        '[' -> Pair(0x2F, false)
        ']' -> Pair(0x30, false)
        '\\' -> Pair(0x31, false)
        ';' -> Pair(0x33, false)
        '\'' -> Pair(0x34, false)
        '`' -> Pair(0x35, false)
        ',' -> Pair(0x36, false)
        '.' -> Pair(0x37, false)
        '/' -> Pair(0x38, false)
        '!' -> Pair(0x1E, true) // 1
        '@' -> Pair(0x1F, true) // 2
        '#' -> Pair(0x20, true) // 3
        '$' -> Pair(0x21, true) // 4
        '%' -> Pair(0x22, true) // 5
        '^' -> Pair(0x23, true) // 6
        '&' -> Pair(0x24, true) // 7
        '*' -> Pair(0x25, true) // 8
        '(' -> Pair(0x26, true) // 9
        ')' -> Pair(0x27, true) // 0
        '_' -> Pair(0x2D, true) // -
        '+' -> Pair(0x2E, true) // =
        '{' -> Pair(0x2F, true) // [
        '}' -> Pair(0x30, true) // ]
        '|' -> Pair(0x31, true) // backslash
        ':' -> Pair(0x33, true) // ;
        '"' -> Pair(0x34, true) // '
        '?' -> Pair(0x38, true) // /
        else -> Pair(0x2C, false) // space fallback
    }
}

/**
 * Proprietary Apple Magic Keyboard (Spanish ISO layout).
 * 6-row pixel-perfect replica of the official Apple Magic Keyboard.
 */
@Composable
internal fun AppleMagicKeyboard(
    transport: MacTransport?,
    activeModifiers: Set<MacModifier>,
    onModifierToggle: (MacModifier) -> Unit,
    onKeyTrigger: (String, Int, Boolean) -> Unit,
    onToggleSystemKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isCapsLockActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141418))
            .border(1.5.dp, ElysiumColors.NeonPurple.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ROW 0: Function Keys (F1..F12 + Media & IME & Esc)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KeyCap("esc", 0x29, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("esc", 0x29, false) }
                KeyCap("F1 🔅", 0x3A, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(3); onKeyTrigger("Brillo-", 0, true) }
                KeyCap("F2 🔆", 0x3B, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(2); onKeyTrigger("Brillo+", 0, true) }
                KeyCap("F3 🖼", 0x3C, Modifier.weight(1f), fontSize = 8.sp) { onKeyTrigger("F3", 0x3C, false) }
                KeyCap("F4 🔍", 0x3D, Modifier.weight(1f), fontSize = 8.sp) { onKeyTrigger("F4", 0x3D, false) }
                KeyCap("F5 🎙", 0x3E, Modifier.weight(1f), fontSize = 8.sp) { onKeyTrigger("F5", 0x3E, false) }
                KeyCap("F6 🌙", 0x3F, Modifier.weight(1f), fontSize = 8.sp) { onKeyTrigger("F6", 0x3F, false) }
                KeyCap("F7 ◄◄", 0x40, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(17); onKeyTrigger("Prev", 0, true) }
                KeyCap("F8 ►||", 0x41, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(16); onKeyTrigger("Play/Pause", 0, true) }
                KeyCap("F9 ►►", 0x42, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(18); onKeyTrigger("Next", 0, true) }
                KeyCap("F10 🔇", 0x43, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(7); onKeyTrigger("Mute", 0, true) }
                KeyCap("F11 🔉", 0x44, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(1); onKeyTrigger("Vol-", 0, true) }
                KeyCap("F12 🔊", 0x45, Modifier.weight(1f), fontSize = 8.sp) { transport?.sendMedia(0); onKeyTrigger("Vol+", 0, true) }
                KeyCap("⌨ IME", 0, Modifier.weight(1.2f), accentColor = ElysiumColors.NeonPurple, fontSize = 8.sp) { onToggleSystemKeyboard() }
                KeyCap("🔒", 0x29, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("Lock", 0x29, false) }
            }

            // ROW 1: Numbers & Symbols (º 1..0 ' ¿ ⌫)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KeyCap("º \\", 0x35, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("º", 0x35, false) }
                KeyCap("1 !", 0x1E, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("1", 0x1E, false) }
                KeyCap("2 \"", 0x1F, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("2", 0x1F, false) }
                KeyCap("3 ·", 0x20, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("3", 0x20, false) }
                KeyCap("4 $", 0x21, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("4", 0x21, false) }
                KeyCap("5 %", 0x22, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("5", 0x22, false) }
                KeyCap("6 &", 0x23, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("6", 0x23, false) }
                KeyCap("7 /", 0x24, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("7", 0x24, false) }
                KeyCap("8 (", 0x25, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("8", 0x25, false) }
                KeyCap("9 )", 0x26, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("9", 0x26, false) }
                KeyCap("0 =", 0x27, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("0", 0x27, false) }
                KeyCap("' ?", 0x2D, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("'", 0x2D, false) }
                KeyCap("¿ ¡", 0x2E, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("¿", 0x2E, false) }
                KeyCap("⌫", 0x2A, Modifier.weight(1.5f), fontSize = 11.sp, accentColor = ElysiumColors.NeonOrange) { onKeyTrigger("⌫", 0x2A, false) }
            }

            // ROW 2: Top QWERTY Row (Tab Q..P ^ * Return)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KeyCap("⇥", 0x2B, Modifier.weight(1.4f), fontSize = 11.sp, accentColor = ElysiumColors.NeonCyan) { onKeyTrigger("Tab", 0x2B, false) }
                KeyCap("Q", 0x14, Modifier.weight(1f)) { onKeyTrigger("Q", 0x14, false) }
                KeyCap("W", 0x1A, Modifier.weight(1f)) { onKeyTrigger("W", 0x1A, false) }
                KeyCap("E", 0x08, Modifier.weight(1f)) { onKeyTrigger("E", 0x08, false) }
                KeyCap("R", 0x15, Modifier.weight(1f)) { onKeyTrigger("R", 0x15, false) }
                KeyCap("T", 0x17, Modifier.weight(1f)) { onKeyTrigger("T", 0x17, false) }
                KeyCap("Y", 0x1C, Modifier.weight(1f)) { onKeyTrigger("Y", 0x1C, false) }
                KeyCap("U", 0x18, Modifier.weight(1f)) { onKeyTrigger("U", 0x18, false) }
                KeyCap("I", 0x0C, Modifier.weight(1f)) { onKeyTrigger("I", 0x0C, false) }
                KeyCap("O", 0x12, Modifier.weight(1f)) { onKeyTrigger("O", 0x12, false) }
                KeyCap("P", 0x13, Modifier.weight(1f)) { onKeyTrigger("P", 0x13, false) }
                KeyCap("^ [", 0x2F, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("[", 0x2F, false) }
                KeyCap("* +", 0x30, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("+", 0x30, false) }
                KeyCap("⏎", 0x28, Modifier.weight(1.6f), fontSize = 11.sp, accentColor = ElysiumColors.NeonGreen) { onKeyTrigger("⏎", 0x28, false) }
            }

            // ROW 3: Middle ASDFGH Row (Caps A..Ñ ; Ç)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KeyCap(
                    label = "⇪ caps",
                    hidUsage = 0x39,
                    modifier = Modifier.weight(1.8f),
                    isActive = isCapsLockActive,
                    accentColor = ElysiumColors.NeonGreen,
                    hasLed = true,
                    fontSize = 9.sp
                ) {
                    isCapsLockActive = !isCapsLockActive
                    onKeyTrigger("Caps Lock", 0x39, false)
                }
                KeyCap("A", 0x04, Modifier.weight(1f)) { onKeyTrigger("A", 0x04, false) }
                KeyCap("S", 0x16, Modifier.weight(1f)) { onKeyTrigger("S", 0x16, false) }
                KeyCap("D", 0x07, Modifier.weight(1f)) { onKeyTrigger("D", 0x07, false) }
                KeyCap("F", 0x09, Modifier.weight(1f)) { onKeyTrigger("F", 0x09, false) }
                KeyCap("G", 0x0A, Modifier.weight(1f)) { onKeyTrigger("G", 0x0A, false) }
                KeyCap("H", 0x0B, Modifier.weight(1f)) { onKeyTrigger("H", 0x0B, false) }
                KeyCap("J", 0x0D, Modifier.weight(1f)) { onKeyTrigger("J", 0x0D, false) }
                KeyCap("K", 0x0E, Modifier.weight(1f)) { onKeyTrigger("K", 0x0E, false) }
                KeyCap("L", 0x0F, Modifier.weight(1f)) { onKeyTrigger("L", 0x0F, false) }
                KeyCap("Ñ", 0x33, Modifier.weight(1f)) { onKeyTrigger("Ñ", 0x33, false) }
                KeyCap("; :", 0x34, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger(";", 0x34, false) }
                KeyCap("Ç", 0x31, Modifier.weight(1.2f), fontSize = 9.sp) { onKeyTrigger("Ç", 0x31, false) }
            }

            // ROW 4: Bottom ZXCVBN Row (Shift < Z..M , . - Shift)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KeyCap(
                    label = "⇧ shift",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.8f),
                    isActive = MacModifier.SHIFT in activeModifiers,
                    accentColor = ElysiumColors.NeonGreen,
                    fontSize = 9.sp
                ) {
                    onModifierToggle(MacModifier.SHIFT)
                }
                KeyCap("< >", 0x64, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("<", 0x64, false) }
                KeyCap("Z", 0x1D, Modifier.weight(1f)) { onKeyTrigger("Z", 0x1D, false) }
                KeyCap("X", 0x1B, Modifier.weight(1f)) { onKeyTrigger("X", 0x1B, false) }
                KeyCap("C", 0x06, Modifier.weight(1f)) { onKeyTrigger("C", 0x06, false) }
                KeyCap("V", 0x19, Modifier.weight(1f)) { onKeyTrigger("V", 0x19, false) }
                KeyCap("B", 0x05, Modifier.weight(1f)) { onKeyTrigger("B", 0x05, false) }
                KeyCap("N", 0x11, Modifier.weight(1f)) { onKeyTrigger("N", 0x11, false) }
                KeyCap("M", 0x10, Modifier.weight(1f)) { onKeyTrigger("M", 0x10, false) }
                KeyCap(",", 0x36, Modifier.weight(1f)) { onKeyTrigger(",", 0x36, false) }
                KeyCap(".", 0x37, Modifier.weight(1f)) { onKeyTrigger(".", 0x37, false) }
                KeyCap("- _", 0x38, Modifier.weight(1f), fontSize = 9.sp) { onKeyTrigger("-", 0x38, false) }
                KeyCap(
                    label = "⇧ shift",
                    hidUsage = 0,
                    modifier = Modifier.weight(2.0f),
                    isActive = MacModifier.SHIFT in activeModifiers,
                    accentColor = ElysiumColors.NeonGreen,
                    fontSize = 9.sp
                ) {
                    onModifierToggle(MacModifier.SHIFT)
                }
            }

            // ROW 5: Modifiers, Spacebar & Inverted-T Arrows (fn ctrl opt cmd SPACE cmd opt ◄ ▲/▼ ►)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyCap("🌐 fn", 0, Modifier.weight(1.1f), isActive = MacModifier.ESC in activeModifiers, fontSize = 8.sp) {
                    onModifierToggle(MacModifier.ESC)
                }
                KeyCap("⌨", 0, Modifier.weight(1.1f), accentColor = ElysiumColors.NeonPurple, fontSize = 10.sp) {
                    onToggleSystemKeyboard()
                }
                KeyCap(
                    label = "⌃ ctrl",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.2f),
                    isActive = MacModifier.CTRL in activeModifiers,
                    accentColor = ElysiumColors.NeonPurple,
                    fontSize = 8.sp
                ) {
                    onModifierToggle(MacModifier.CTRL)
                }
                KeyCap(
                    label = "⌥ opt",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.3f),
                    isActive = MacModifier.OPT in activeModifiers,
                    accentColor = ElysiumColors.NeonOrange,
                    fontSize = 8.sp
                ) {
                    onModifierToggle(MacModifier.OPT)
                }
                KeyCap(
                    label = "⌘ cmd",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.5f),
                    isActive = MacModifier.CMD in activeModifiers,
                    accentColor = ElysiumColors.NeonCyan,
                    fontSize = 8.sp
                ) {
                    onModifierToggle(MacModifier.CMD)
                }
                KeyCap(
                    label = "SPACE",
                    hidUsage = 0x2C,
                    modifier = Modifier.weight(5.5f),
                    accentColor = ElysiumColors.NeonCyan,
                    fontSize = 10.sp
                ) {
                    onKeyTrigger("Space", 0x2C, false)
                }
                KeyCap(
                    label = "⌘ cmd",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.5f),
                    isActive = MacModifier.CMD in activeModifiers,
                    accentColor = ElysiumColors.NeonCyan,
                    fontSize = 8.sp
                ) {
                    onModifierToggle(MacModifier.CMD)
                }
                KeyCap(
                    label = "⌥ opt",
                    hidUsage = 0,
                    modifier = Modifier.weight(1.3f),
                    isActive = MacModifier.OPT in activeModifiers,
                    accentColor = ElysiumColors.NeonOrange,
                    fontSize = 8.sp
                ) {
                    onModifierToggle(MacModifier.OPT)
                }

                // Inverted-T Arrow Cluster
                Row(
                    modifier = Modifier.weight(3.2f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KeyCap("◄", 0x50, Modifier.weight(1f).height(34.dp), fontSize = 10.sp, accentColor = ElysiumColors.NeonCyan) {
                        onKeyTrigger("Left", 0x50, false)
                    }

                    Column(
                        modifier = Modifier.weight(1f).height(34.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        KeyCap("▲", 0x52, Modifier.fillMaxWidth().weight(1f), fontSize = 8.sp, accentColor = ElysiumColors.NeonCyan) {
                            onKeyTrigger("Up", 0x52, false)
                        }
                        KeyCap("▼", 0x51, Modifier.fillMaxWidth().weight(1f), fontSize = 8.sp, accentColor = ElysiumColors.NeonCyan) {
                            onKeyTrigger("Down", 0x51, false)
                        }
                    }

                    KeyCap("►", 0x4F, Modifier.weight(1f).height(34.dp), fontSize = 10.sp, accentColor = ElysiumColors.NeonCyan) {
                        onKeyTrigger("Right", 0x4F, false)
                    }
                }
            }
        }
    }
}

/**
 * Individual keycap for the Apple Magic Keyboard.
 */
@Composable
internal fun KeyCap(
    label: String,
    hidUsage: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    accentColor: Color = ElysiumColors.NeonCyan,
    hasLed: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "key_scale"
    )

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isActive || pressed) {
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.45f),
                            accentColor.copy(alpha = 0.20f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF26262E),
                            Color(0xFF18181F)
                        )
                    )
                }
            )
            .border(
                width = if (isActive || pressed) 1.5.dp else 1.dp,
                color = if (isActive || pressed) accentColor else Color(0xFF383842),
                shape = RoundedCornerShape(5.dp)
            )
            .clickable {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (hasLed) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF00FF66) else Color(0xFF444444))
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = label,
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = if (isActive || pressed) FontWeight.Black else FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                color = if (isActive || pressed) accentColor else Color.White
            )
        }
    }
}

private fun calculateNormalizedTouch(
    posX: Float,
    posY: Float,
    containerW: Float,
    containerH: Float,
    bitmapW: Int?,
    bitmapH: Int?,
    scaleMode: ScreenScaleMode
): Pair<Float, Float> {
    if (bitmapW == null || bitmapH == null || bitmapW == 0 || bitmapH == 0 || containerW <= 0f || containerH <= 0f || scaleMode == ScreenScaleMode.ADAPTIVE_FILL) {
        return Pair((posX / containerW).coerceIn(0f, 1f), (posY / containerH).coerceIn(0f, 1f))
    }
    val imgAspect = bitmapW.toFloat() / bitmapH.toFloat()
    val containerAspect = containerW / containerH

    return when (scaleMode) {
        ScreenScaleMode.ORIGINAL_FIT -> {
            if (containerAspect > imgAspect) {
                val drawW = containerH * imgAspect
                val leftX = (containerW - drawW) / 2f
                val nx = ((posX - leftX) / drawW).coerceIn(0f, 1f)
                val ny = (posY / containerH).coerceIn(0f, 1f)
                Pair(nx, ny)
            } else {
                val drawH = containerW / imgAspect
                val topY = (containerH - drawH) / 2f
                val nx = (posX / containerW).coerceIn(0f, 1f)
                val ny = ((posY - topY) / drawH).coerceIn(0f, 1f)
                Pair(nx, ny)
            }
        }
        ScreenScaleMode.CROP_FILL -> {
            if (containerAspect > imgAspect) {
                val drawH = containerW / imgAspect
                val cropY = (drawH - containerH) / 2f
                val nx = (posX / containerW).coerceIn(0f, 1f)
                val ny = ((posY + cropY) / drawH).coerceIn(0f, 1f)
                Pair(nx, ny)
            } else {
                val drawW = containerH * imgAspect
                val cropX = (drawW - containerW) / 2f
                val nx = ((posX + cropX) / drawW).coerceIn(0f, 1f)
                val ny = (posY / containerH).coerceIn(0f, 1f)
                Pair(nx, ny)
            }
        }
        ScreenScaleMode.ADAPTIVE_FILL -> {
            Pair((posX / containerW).coerceIn(0f, 1f), (posY / containerH).coerceIn(0f, 1f))
        }
    }
}
