package com.elysium.nexus.ui.usb

import android.graphics.BitmapFactory
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.transport.mac.DiscoveredHost
import com.elysium.nexus.core.transport.mac.MacConnectionState
import com.elysium.nexus.core.transport.mac.MacProtocol
import com.elysium.nexus.core.transport.mac.MacTransport
import com.elysium.nexus.ui.mac.AppleMagicKeyboard
import com.elysium.nexus.ui.mac.MacModifier
import com.elysium.nexus.ui.mac.asciiToHidUsage
import com.elysium.nexus.ui.mac.modifiersToBits
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "UsbCScreen"

enum class UsbControlMode(val title: String) {
    FULLSCREEN("100% Pantalla"),
    KEYBOARD_DESK("Teclado Mac"),
    TRACKPAD("Trackpad Pro"),
    MEDIA("Media"),
    GAMEPAD("Gamepad")
}

enum class UsbScreenScaleMode(val label: String, val scale: ContentScale) {
    FILL_100("100% PANTALLA", ContentScale.FillBounds),
    CROP_ADAPTIVE("ADAPTATIVO", ContentScale.Crop),
    ORIGINAL_FIT("ORIGINAL", ContentScale.Fit)
}

data class ClickRipple(val normX: Float, val normY: Float, val createdAt: Long)

/**
 * USB-C Direct Wired Control Surface & 100% Fullscreen Monitor — COMPLETE FEATURE SET.
 *
 * Fully integrated features:
 * - 100% Edge-to-Edge Pure Fullscreen Mac Display (0 margins, 0 borders)
 * - Interactive Pinch-to-Zoom (1.0x to 4.0x dynamic screen zoom)
 * - Multi-touch gestures (1-finger move/touch, 2-finger scroll & zoom, 3-finger Mission Control / App Exposé)
 * - Click Ripple animations at tap locations
 * - Direct Touch Mode vs Precision Relative Trackpad Mode toggle
 * - Automatic Instant Zero-PIN Connection (No code pairing prompt)
 * - macOS Apple Magic Keyboard Toolbar (Cmd ⌘, Option ⌥, Control ⌃, Shift ⇧, Esc, Tab, Space, Enter, F-Keys)
 * - Soft-Keyboard Forwarding (Android IME → macOS)
 */
@Composable
fun UsbCConnectionScreen(
    onBack: () -> Unit,
    onFallbackToWifi: () -> Unit = {},
    transport: MacTransport = remember { MacTransport() },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val connectionState by transport.state.collectAsState()
    val isConnected = connectionState is MacConnectionState.Ready ||
        connectionState is MacConnectionState.ReadyEvent
    val isConnecting = connectionState is MacConnectionState.Connecting
    val hasError = connectionState is MacConnectionState.Error

    var activeMode by remember { mutableStateOf(UsbControlMode.KEYBOARD_DESK) }
    var scaleMode by remember { mutableStateOf(UsbScreenScaleMode.FILL_100) }
    var connectionAttempt by remember { mutableIntStateOf(0) }
    var pinText by remember { mutableStateOf("") }
    var eventCount by remember { mutableIntStateOf(0) }
    var showChromeOverlay by remember { mutableStateOf(true) }

    // Screen Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isDirectTouchMode by remember { mutableStateOf(true) }

    // Click ripples
    val ripples = remember { mutableStateListOf<ClickRipple>() }

    // Apple Magic Keyboard state (reuses exact WiFi screen keyboard)
    var showAppleMagicKeyboard by remember { mutableStateOf(false) }
    var showSystemKeyboard by remember { mutableStateOf(false) }
    var systemTextInput by remember { mutableStateOf("") }
    var activeModifiers by remember { mutableStateOf<Set<MacModifier>>(emptySet()) }

    // Prune old ripples
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            ripples.removeAll { now - it.createdAt > 600 }
        }
    }

    // USB-C requires the Mac app to install `adb reverse` after USB debugging is
    // authorized. This probe runs once; localhost is never treated as the Mac by default.
    LaunchedEffect(connectionAttempt) {
        val usbHost = DiscoveredHost("USB-C · puente ADB", "127.0.0.1", 7878, "Mac", "macOS", null)
        withContext(Dispatchers.IO) { transport.startHandshake(usbHost) }
    }



    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // === SURFACE ROUTER ===
        when (activeMode) {
            UsbControlMode.FULLSCREEN -> UsbDisplaySurfaceComplete(
                transport = transport,
                isConnected = isConnected,
                scaleMode = scaleMode,
                zoomScale = zoomScale,
                isDirectTouchMode = isDirectTouchMode,
                ripples = ripples,
                onZoomChange = { factor ->
                    zoomScale = (zoomScale * factor).coerceIn(1.0f, 4.0f)
                },
                onTapScreen = { showChromeOverlay = !showChromeOverlay },
                onAddRipple = { nx, ny -> ripples.add(ClickRipple(nx, ny, System.currentTimeMillis())) },
                onEvent = { eventCount++ }
            )
            UsbControlMode.KEYBOARD_DESK -> UsbKeyboardDeskSurface(
                transport = transport,
                isConnected = isConnected,
                scaleMode = scaleMode,
                zoomScale = zoomScale,
                isDirectTouchMode = isDirectTouchMode,
                ripples = ripples,
                activeModifiers = activeModifiers,
                onModifierToggle = { mod ->
                    activeModifiers = if (mod in activeModifiers) activeModifiers - mod else activeModifiers + mod
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onToggleSystemKeyboard = {
                    showSystemKeyboard = !showSystemKeyboard
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onZoomChange = { factor ->
                    zoomScale = (zoomScale * factor).coerceIn(1.0f, 4.0f)
                },
                onAddRipple = { nx, ny -> ripples.add(ClickRipple(nx, ny, System.currentTimeMillis())) },
                onEvent = { eventCount++ }
            )
            UsbControlMode.TRACKPAD -> UsbTrackpadSurface(
                transport = transport,
                isConnected = isConnected,
                onEvent = { eventCount++ }
            )
            UsbControlMode.MEDIA -> UsbMediaSurface(
                transport = transport,
                isConnected = isConnected,
                onEvent = { eventCount++ }
            )
            UsbControlMode.GAMEPAD -> UsbGamepadSurface(
                transport = transport,
                isConnected = isConnected,
                onEvent = { eventCount++ }
            )
        }

        // === TRANSLUCENT FLOATING CONTROL CHROME ===
        AnimatedVisibility(
            visible = showChromeOverlay || (activeMode != UsbControlMode.FULLSCREEN && activeMode != UsbControlMode.KEYBOARD_DESK),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                transport.disconnect()
                                onBack()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = ElysiumColors.OnSurface
                            )
                        }
                        Text(
                            text = "USB-C 100% MONITOR",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.0.sp
                            ),
                            color = ElysiumColors.NeonYellow
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeMode == UsbControlMode.FULLSCREEN || activeMode == UsbControlMode.KEYBOARD_DESK) {
                            // Direct Touch vs Trackpad Toggle
                            NeonChip(
                                label = if (isDirectTouchMode) "👉 TÁCTIL" else "🖱 CURSOR",
                                onClick = { isDirectTouchMode = !isDirectTouchMode },
                                accent = if (isDirectTouchMode) ElysiumColors.NeonGreen else ElysiumColors.NeonPurple
                            )
                            // Scale Mode Cycle Button
                            NeonChip(
                                label = scaleMode.label,
                                onClick = {
                                    scaleMode = when (scaleMode) {
                                        UsbScreenScaleMode.FILL_100 -> UsbScreenScaleMode.CROP_ADAPTIVE
                                        UsbScreenScaleMode.CROP_ADAPTIVE -> UsbScreenScaleMode.ORIGINAL_FIT
                                        UsbScreenScaleMode.ORIGINAL_FIT -> UsbScreenScaleMode.FILL_100
                                    }
                                },
                                accent = ElysiumColors.NeonCyan
                            )
                            // Zoom reset button if zoomed
                            if (zoomScale > 1.05f) {
                                NeonChip(
                                    label = "🔍 ${"%.1f".format(zoomScale)}x ↺",
                                    onClick = { zoomScale = 1.0f },
                                    accent = ElysiumColors.NeonYellow
                                )
                            }
                        }

                        // ⌨ Apple Magic Keyboard toggle button
                        if (isConnected) {
                            NeonChip(
                                label = if (showAppleMagicKeyboard) "⌨ OCULTAR" else "⌨ TECLADO",
                                onClick = {
                                    showAppleMagicKeyboard = !showAppleMagicKeyboard
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                },
                                accent = if (showAppleMagicKeyboard) ElysiumColors.NeonPurple else ElysiumColors.NeonCyan
                            )
                        }

                        // Latency Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    (if (isConnected) ElysiumColors.NeonGreen else ElysiumColors.NeonYellow)
                                        .copy(alpha = 0.2f)
                                )
                                .border(
                                    1.dp,
                                    if (isConnected) ElysiumColors.NeonGreen else ElysiumColors.NeonYellow,
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) ElysiumColors.NeonGreen else ElysiumColors.NeonYellow)
                                )
                                Text(
                                    text = if (isConnected) "ENLACE REAL"
                                    else if (isConnecting) "conectando..."
                                    else if (hasError) "error"
                                    else "—",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isConnected) ElysiumColors.NeonGreen else ElysiumColors.NeonYellow
                                )
                            }
                        }
                    }
                }

                // Bottom Floating Panel
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mode Selector Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UsbControlMode.values().forEach { mode ->
                            val selected = activeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) ElysiumColors.NeonYellow.copy(alpha = 0.25f) else Color.Transparent)
                                    .border(
                                        if (selected) 1.dp else 0.dp,
                                        if (selected) ElysiumColors.NeonYellow else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { activeMode = mode }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.title,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (selected) ElysiumColors.NeonYellow else ElysiumColors.OnSurfaceVariant
                                )
                            }
                        }
                    }

                    // === APPLE MAGIC KEYBOARD (exact same as WiFi screen) ===
                    AnimatedVisibility(
                        visible = showAppleMagicKeyboard,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        AppleMagicKeyboard(
                            transport = transport,
                            activeModifiers = activeModifiers,
                            onModifierToggle = { mod ->
                                activeModifiers = if (mod in activeModifiers) activeModifiers - mod else activeModifiers + mod
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onKeyTrigger = { label, hidUsage, isMedia ->
                                if (!isMedia && hidUsage != 0) {
                                    val mods = modifiersToBits(activeModifiers)
                                    transport.sendKey(MacProtocol.KeyAction.DOWN, hidUsage, mods)
                                    transport.sendKey(MacProtocol.KeyAction.UP, hidUsage, mods)
                                }
                                eventCount++
                            },
                            onToggleSystemKeyboard = {
                                showSystemKeyboard = !showSystemKeyboard
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // === SYSTEM KEYBOARD (Android IME → macOS) OVERLAY ===
        AnimatedVisibility(
            visible = showSystemKeyboard && isConnected,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp, start = 8.dp, end = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ElysiumColors.Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Android IME → macOS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElysiumColors.NeonPurple
                        )
                        IconButton(
                            onClick = { showSystemKeyboard = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = ElysiumColors.OnSurface)
                        }
                    }

                    BasicTextField(
                        value = systemTextInput,
                        onValueChange = { newText ->
                            if (newText.length > systemTextInput.length) {
                                val newChars = newText.substring(systemTextInput.length)
                                for (c in newChars) {
                                    val (usage, needsShift) = asciiToHidUsage(c)
                                    val mods = modifiersToBits(activeModifiers) or
                                        (if (needsShift) MacProtocol.Modifiers.SHIFT else 0)
                                    transport.sendKey(MacProtocol.KeyAction.DOWN, usage, mods)
                                    transport.sendKey(MacProtocol.KeyAction.UP, usage, mods)
                                }
                            } else if (newText.length < systemTextInput.length) {
                                val deleted = systemTextInput.length - newText.length
                                repeat(deleted) {
                                    val mods = modifiersToBits(activeModifiers)
                                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x2A, mods)
                                    transport.sendKey(MacProtocol.KeyAction.UP, 0x2A, mods)
                                }
                            }
                            systemTextInput = newText
                            eventCount++
                        },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = ElysiumColors.OnSurface
                        ),
                        cursorBrush = SolidColor(ElysiumColors.NeonPurple),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElysiumColors.Background)
                            .border(1.dp, ElysiumColors.NeonPurple, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }

    if (connectionState is MacConnectionState.AwaitingPin) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Confirmar Mac") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escribe el código de 6 dígitos que muestra Elysium Vanguard en el Mac.")
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.filter(Char::isDigit).take(6) },
                        label = { Text("Código de emparejamiento") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = pinText.length == 6, onClick = {
                    val pin = pinText
                    pinText = ""
                    scope.launch { withContext(Dispatchers.IO) { transport.sendPin(pin) } }
                }) { Text("Conectar") }
            },
            dismissButton = { TextButton(onClick = onFallbackToWifi) { Text("Usar Wi‑Fi") } }
        )
    } else if (hasError && !isConnected) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = { connectionAttempt++ }) { Text("Reintentar USB-C") }
            TextButton(onClick = onFallbackToWifi) { Text("Cambiar a Wi‑Fi") }
        }
    }
}

// =================================================================
// 100% PURE FULLSCREEN DISPLAY WITH MULTI-TOUCH GESTURES & ZOOM
// =================================================================

@Composable
fun UsbDisplaySurfaceComplete(
    transport: MacTransport,
    isConnected: Boolean,
    scaleMode: UsbScreenScaleMode,
    zoomScale: Float,
    isDirectTouchMode: Boolean,
    ripples: List<ClickRipple>,
    onZoomChange: (Float) -> Unit,
    onTapScreen: () -> Unit,
    onAddRipple: (Float, Float) -> Unit,
    onEvent: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var frameBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    var captureStatus by remember { mutableStateOf<MacProtocol.ScreenShareStatus?>(null) }

    // Preserve the latest status so the permission/error explanation is visible
    // even when the Mac responds immediately after SCREEN_REQUEST.
    LaunchedEffect(transport) {
        transport.screenStatus.collect { status ->
            captureStatus = status
            Log.i(TAG, "UsbDisplaySurfaceComplete: Mac capture status=${status.name}")
        }
    }

    // Always request screen capture when this composable is active AND connected.
    // Using Unit key ensures it fires on first composition; onDispose stops it.
    DisposableEffect(Unit) {
        if (isConnected) {
            Log.i(TAG, "UsbDisplaySurfaceComplete: START 100% stream (composition)")
            transport.sendScreenRequest(true)
        }
        onDispose {
            Log.i(TAG, "UsbDisplaySurfaceComplete: STOP stream (dispose)")
            transport.sendScreenRequest(false)
        }
    }

    // Re-send screen request whenever connection state transitions to connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            Log.i(TAG, "UsbDisplaySurfaceComplete: isConnected=true, sending SCREEN_REQUEST start")
            transport.sendScreenRequest(true)
        }
    }

    // Collect screen frames whenever connected (conflate ensures ZERO accumulative lag!)
    LaunchedEffect(isConnected) {
        if (isConnected) {
            transport.screenFrames
                .conflate()
                .collect { jpegBytes ->
                    withContext(Dispatchers.Default) {
                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bmp != null) {
                            frameBitmap = bmp.asImageBitmap()
                        }
                    }
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                surfaceSize = coords.size
            }
            .pointerInput(isConnected, isDirectTouchMode) {
                if (!isConnected) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    var isTap = true
                    var peakPointers = currentEvent.changes.size
                    var totalDrag = Offset.Zero
                    var initialPinchDistance: Float? = null

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val changes = event.changes
                        if (changes.size > peakPointers) peakPointers = changes.size
                        val allUp = changes.none { it.pressed }

                        if (allUp) {
                            val duration = System.currentTimeMillis() - startTime
                            if (isTap && duration < 300 && totalDrag.getDistance() < 20f) {
                                onTapScreen()
                                if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                                    val tapPos = firstDown.position
                                    val nx = (tapPos.x / surfaceSize.width.toFloat()).coerceIn(0f, 1f)
                                    val ny = (tapPos.y / surfaceSize.height.toFloat()).coerceIn(0f, 1f)
                                    onAddRipple(nx, ny)
                                    if (peakPointers == 2) {
                                        transport.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.DOWN)
                                        scope.launch {
                                            delay(50)
                                            transport.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.UP)
                                        }
                                    } else {
                                        transport.sendMouseAbsMove(nx, ny)
                                        transport.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                                        scope.launch {
                                            delay(50)
                                            transport.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                                        }
                                    }
                                    onEvent()
                                }
                            } else if (peakPointers >= 3 && totalDrag.getDistance() > 40f) {
                                // 3-Finger gestures
                                val adx = abs(totalDrag.x)
                                val ady = abs(totalDrag.y)
                                if (ady > adx) {
                                    if (totalDrag.y < 0) {
                                        // Mission Control (Ctrl + Up)
                                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x52, MacProtocol.Modifiers.CONTROL)
                                        transport.sendKey(MacProtocol.KeyAction.UP, 0x52, MacProtocol.Modifiers.CONTROL)
                                    } else {
                                        // App Exposé (Ctrl + Down)
                                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x51, MacProtocol.Modifiers.CONTROL)
                                        transport.sendKey(MacProtocol.KeyAction.UP, 0x51, MacProtocol.Modifiers.CONTROL)
                                    }
                                } else {
                                    if (totalDrag.x < 0) {
                                        // Next Space (Ctrl + Right)
                                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x4F, MacProtocol.Modifiers.CONTROL)
                                        transport.sendKey(MacProtocol.KeyAction.UP, 0x4F, MacProtocol.Modifiers.CONTROL)
                                    } else {
                                        // Prev Space (Ctrl + Left)
                                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x50, MacProtocol.Modifiers.CONTROL)
                                        transport.sendKey(MacProtocol.KeyAction.UP, 0x50, MacProtocol.Modifiers.CONTROL)
                                    }
                                }
                                onEvent()
                            }
                            break
                        }

                        var frameDrag = Offset.Zero
                        val pressedPointers = changes.filter { it.pressed }
                        for (c in pressedPointers) {
                            val delta = c.position - c.previousPosition
                            if (delta != Offset.Zero) {
                                frameDrag += delta
                                c.consume()
                            }
                        }
                        if (frameDrag.getDistance() > 0.5f) isTap = false
                        totalDrag += frameDrag

                        when (pressedPointers.size) {
                            1 -> {
                                if (frameDrag != Offset.Zero && surfaceSize.width > 0 && surfaceSize.height > 0) {
                                    if (isDirectTouchMode) {
                                        val pos = pressedPointers[0].position
                                        val nx = (pos.x / surfaceSize.width.toFloat()).coerceIn(0f, 1f)
                                        val ny = (pos.y / surfaceSize.height.toFloat()).coerceIn(0f, 1f)
                                        transport.sendMouseAbsMove(nx, ny)
                                    } else {
                                        transport.sendMouseMove(frameDrag.x * 1.5f, frameDrag.y * 1.5f)
                                    }
                                    onEvent()
                                }
                            }
                            2 -> {
                                val p1 = pressedPointers[0].position
                                val p2 = pressedPointers[1].position
                                val dist = (p2 - p1).getDistance()
                                if (initialPinchDistance == null) {
                                    initialPinchDistance = dist
                                } else if (initialPinchDistance!! > 15f && dist > 15f) {
                                    val factor = dist / initialPinchDistance!!
                                    if (abs(factor - 1f) > 0.03f) {
                                        onZoomChange(factor)
                                        initialPinchDistance = dist
                                    } else if (frameDrag != Offset.Zero) {
                                        transport.sendScroll(frameDrag.x * 2f, frameDrag.y * 2f)
                                        onEvent()
                                    }
                                } else if (frameDrag != Offset.Zero) {
                                    transport.sendScroll(frameDrag.x * 2f, frameDrag.y * 2f)
                                    onEvent()
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = frameBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Pantalla Mac 100%",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                contentScale = scaleMode.scale
            )

            // Click Ripples overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val now = System.currentTimeMillis()
                for (r in ripples) {
                    val age = (now - r.createdAt).toFloat().coerceIn(0f, 600f)
                    val alpha = 1.0f - (age / 600f)
                    val radius = (age / 600f) * 60.dp.toPx()
                    drawCircle(
                        color = Color(0xFF00FFE0).copy(alpha = alpha * 0.8f),
                        radius = radius,
                        center = Offset(r.normX * size.width, r.normY * size.height)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = captureStatus?.userMessage
                        ?: if (isConnected) "Cargando Pantalla Mac 100% a 30 FPS..."
                    else "Conecta por USB para ver la pantalla completa del Mac",
                    style = TextStyle(fontSize = 13.sp, color = ElysiumColors.OnSurfaceVariant),
                )
            }
        }
    }
}

@Composable
fun UsbKeyboardDeskSurface(
    transport: MacTransport,
    isConnected: Boolean,
    scaleMode: UsbScreenScaleMode,
    zoomScale: Float,
    isDirectTouchMode: Boolean,
    ripples: List<ClickRipple>,
    activeModifiers: Set<MacModifier>,
    onModifierToggle: (MacModifier) -> Unit,
    onToggleSystemKeyboard: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onAddRipple: (Float, Float) -> Unit,
    onEvent: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. LIVE MAC SCREEN (TOP AREA)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ElysiumColors.NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            UsbDisplaySurfaceComplete(
                transport = transport,
                isConnected = isConnected,
                scaleMode = scaleMode,
                zoomScale = zoomScale,
                isDirectTouchMode = isDirectTouchMode,
                ripples = ripples,
                onZoomChange = onZoomChange,
                onTapScreen = {},
                onAddRipple = onAddRipple,
                onEvent = onEvent
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. MOUSE BUTTONS (MIDDLE AREA - Left/Right Click)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    if (!isConnected) return@Button
                    transport.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.DOWN)
                    scope.launch {
                        delay(60)
                        transport.sendMouseButton(MacProtocol.MouseButton.LEFT, MacProtocol.ButtonState.UP)
                    }
                    onEvent()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElysiumColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElysiumColors.NeonCyan)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = ElysiumColors.NeonCyan, modifier = Modifier.size(16.dp))
                    Text("BOTÓN IZQUIERDO", color = ElysiumColors.NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    if (!isConnected) return@Button
                    transport.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.DOWN)
                    scope.launch {
                        delay(60)
                        transport.sendMouseButton(MacProtocol.MouseButton.RIGHT, MacProtocol.ButtonState.UP)
                    }
                    onEvent()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElysiumColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElysiumColors.NeonPurple)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = ElysiumColors.NeonPurple, modifier = Modifier.size(16.dp))
                    Text("BOTÓN DERECHO", color = ElysiumColors.NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. FULL APPLE MAGIC KEYBOARD (BOTTOM AREA - 6 Rows)
        AppleMagicKeyboard(
            transport = transport,
            activeModifiers = activeModifiers,
            onModifierToggle = onModifierToggle,
            onKeyTrigger = { label, hidUsage, isMedia ->
                if (!isMedia && hidUsage != 0) {
                    val mods = modifiersToBits(activeModifiers)
                    transport.sendKey(MacProtocol.KeyAction.DOWN, hidUsage, mods)
                    transport.sendKey(MacProtocol.KeyAction.UP, hidUsage, mods)
                }
                onEvent()
            },
            onToggleSystemKeyboard = onToggleSystemKeyboard,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun UsbTrackpadSurface(
    transport: MacTransport,
    isConnected: Boolean,
    onEvent: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    val sensitivity = 1.8f

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isConnected)
                        Brush.verticalGradient(
                            listOf(
                                ElysiumColors.Surface.copy(alpha = 0.8f),
                                ElysiumColors.NeonYellow.copy(alpha = 0.05f)
                            )
                        )
                    else
                        Brush.verticalGradient(
                            listOf(
                                ElysiumColors.Surface.copy(alpha = 0.4f),
                                ElysiumColors.Surface.copy(alpha = 0.4f)
                            )
                        )
                )
                .border(
                    1.dp,
                    if (isConnected) ElysiumColors.NeonYellow.copy(alpha = 0.5f)
                    else ElysiumColors.OnSurfaceVariant.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
                )
                .pointerInput(isConnected) {
                    if (!isConnected) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        accX += dragAmount.x * sensitivity
                        accY += dragAmount.y * sensitivity
                        val dx = accX
                        val dy = accY
                        if (abs(dx) >= 0.5f || abs(dy) >= 0.5f) {
                            transport.sendMouseMove(dx, dy)
                            onEvent()
                            accX = 0f
                            accY = 0f
                        }
                    }
                }
                .pointerInput(isConnected) {
                    if (!isConnected) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(pass = PointerEventPass.Initial)
                        first.consume()
                        var lastY = first.position.y
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.size >= 2) {
                                val avgY = pointers.map { it.position.y }.average().toFloat()
                                val scrollDy = avgY - lastY
                                if (abs(scrollDy) > 2f) {
                                    transport.sendScroll(0f, -scrollDy * 0.5f)
                                    onEvent()
                                    lastY = avgY
                                }
                                pointers.forEach { it.consume() }
                            }
                            if (pointers.isEmpty()) break
                        }
                    }
                }
                .pointerInput(isConnected) {
                    if (!isConnected) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            transport.sendMouseButton(
                                MacProtocol.MouseButton.LEFT,
                                MacProtocol.ButtonState.DOWN
                            )
                            scope.launch {
                                delay(50)
                                transport.sendMouseButton(
                                    MacProtocol.MouseButton.LEFT,
                                    MacProtocol.ButtonState.UP
                                )
                            }
                            onEvent()
                        },
                        onDoubleTap = {
                            repeat(2) {
                                transport.sendMouseButton(
                                    MacProtocol.MouseButton.LEFT,
                                    MacProtocol.ButtonState.DOWN
                                )
                                transport.sendMouseButton(
                                    MacProtocol.MouseButton.LEFT,
                                    MacProtocol.ButtonState.UP
                                )
                            }
                            onEvent()
                        },
                        onLongPress = {
                            transport.sendMouseButton(
                                MacProtocol.MouseButton.RIGHT,
                                MacProtocol.ButtonState.DOWN
                            )
                            scope.launch {
                                delay(100)
                                transport.sendMouseButton(
                                    MacProtocol.MouseButton.RIGHT,
                                    MacProtocol.ButtonState.UP
                                )
                            }
                            onEvent()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = if (isConnected) ElysiumColors.NeonYellow.copy(alpha = 0.6f)
                    else ElysiumColors.OnSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = if (isConnected)
                        "Desliza → Cursor · Toque → Clic · Largo → Derecho\n2 dedos → Scroll"
                    else
                        "Sin conexión · Conecta USB-C + adb reverse",
                    style = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                    color = ElysiumColors.OnSurfaceVariant.copy(alpha = if (isConnected) 0.7f else 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!isConnected) return@Button
                    transport.sendMouseButton(
                        MacProtocol.MouseButton.LEFT,
                        MacProtocol.ButtonState.DOWN
                    )
                    scope.launch {
                        delay(80)
                        transport.sendMouseButton(
                            MacProtocol.MouseButton.LEFT,
                            MacProtocol.ButtonState.UP
                        )
                    }
                    onEvent()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) ElysiumColors.Surface
                    else ElysiumColors.Surface.copy(alpha = 0.5f)
                ),
                enabled = isConnected
            ) {
                Text(
                    "CLIC IZQUIERDO",
                    color = if (isConnected) ElysiumColors.OnSurface
                    else ElysiumColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (!isConnected) return@Button
                    transport.sendMouseButton(
                        MacProtocol.MouseButton.RIGHT,
                        MacProtocol.ButtonState.DOWN
                    )
                    scope.launch {
                        delay(80)
                        transport.sendMouseButton(
                            MacProtocol.MouseButton.RIGHT,
                            MacProtocol.ButtonState.UP
                        )
                    }
                    onEvent()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) ElysiumColors.Surface
                    else ElysiumColors.Surface.copy(alpha = 0.5f)
                ),
                enabled = isConnected
            ) {
                Text(
                    "CLIC DERECHO",
                    color = if (isConnected) ElysiumColors.OnSurface
                    else ElysiumColors.OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UsbMediaSurface(
    transport: MacTransport,
    isConnected: Boolean,
    onEvent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isConnected)
                    Brush.verticalGradient(
                        listOf(
                            ElysiumColors.Surface.copy(alpha = 0.7f),
                            ElysiumColors.NeonGreen.copy(alpha = 0.05f)
                        )
                    )
                else
                    Brush.verticalGradient(
                        listOf(
                            ElysiumColors.Surface.copy(alpha = 0.4f),
                            ElysiumColors.Surface.copy(alpha = 0.4f)
                        )
                    )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(7)
                    onEvent()
                },
                modifier = Modifier.size(56.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.VolumeMute,
                    contentDescription = "Mute",
                    tint = if (isConnected) ElysiumColors.NeonYellow else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(1)
                    onEvent()
                },
                modifier = Modifier.size(56.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.VolumeDown,
                    contentDescription = "Vol -",
                    tint = if (isConnected) ElysiumColors.NeonYellow else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(0)
                    onEvent()
                },
                modifier = Modifier.size(56.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Vol +",
                    tint = if (isConnected) ElysiumColors.NeonYellow else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(17)
                    onEvent()
                },
                modifier = Modifier.size(64.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Prev",
                    tint = if (isConnected) ElysiumColors.OnSurface else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(16)
                    onEvent()
                },
                modifier = Modifier.size(72.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isConnected) ElysiumColors.NeonGreen else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(
                onClick = {
                    if (!isConnected) return@IconButton
                    transport.sendMedia(18)
                    onEvent()
                },
                modifier = Modifier.size(64.dp),
                enabled = isConnected
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = if (isConnected) ElysiumColors.OnSurface else ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun UsbGamepadSurface(
    transport: MacTransport,
    isConnected: Boolean,
    onEvent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isConnected)
                    Brush.verticalGradient(
                        listOf(
                            ElysiumColors.Surface.copy(alpha = 0.7f),
                            ElysiumColors.NeonGreen.copy(alpha = 0.03f)
                        )
                    )
                else
                    Brush.verticalGradient(
                        listOf(
                            ElysiumColors.Surface.copy(alpha = 0.4f),
                            ElysiumColors.Surface.copy(alpha = 0.4f)
                        )
                    )
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("D-PAD", fontSize = 10.sp, color = ElysiumColors.OnSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            GamepadButton(
                label = "▲",
                isConnected = isConnected,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x52, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x52, 0) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GamepadButton(
                    label = "◄",
                    isConnected = isConnected,
                    onPress = {
                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x50, 0)
                        onEvent()
                    },
                    onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x50, 0) }
                )
                GamepadButton(
                    label = "►",
                    isConnected = isConnected,
                    onPress = {
                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x4F, 0)
                        onEvent()
                    },
                    onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x4F, 0) }
                )
            }
            GamepadButton(
                label = "▼",
                isConnected = isConnected,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x51, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x51, 0) }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("ACCIONES", fontSize = 10.sp, color = ElysiumColors.OnSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            GamepadButton(
                label = "Y",
                isConnected = isConnected,
                color = ElysiumColors.NeonYellow,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x1A, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x1A, 0) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GamepadButton(
                    label = "X",
                    isConnected = isConnected,
                    color = Color(0xFF4488FF),
                    onPress = {
                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x04, 0)
                        onEvent()
                    },
                    onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x04, 0) }
                )
                GamepadButton(
                    label = "B",
                    isConnected = isConnected,
                    color = Color(0xFFFF4444),
                    onPress = {
                        transport.sendKey(MacProtocol.KeyAction.DOWN, 0x07, 0)
                        onEvent()
                    },
                    onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x07, 0) }
                )
            }
            GamepadButton(
                label = "A",
                isConnected = isConnected,
                color = ElysiumColors.NeonGreen,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x16, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x16, 0) }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("BUMPERS", fontSize = 10.sp, color = ElysiumColors.OnSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            GamepadButton(
                label = "LB",
                isConnected = isConnected,
                wide = true,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x14, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x14, 0) }
            )
            GamepadButton(
                label = "RB",
                isConnected = isConnected,
                wide = true,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0x08, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0x08, 0) }
            )
            GamepadButton(
                label = "LT",
                isConnected = isConnected,
                wide = true,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0xE1, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0xE1, 0) }
            )
            GamepadButton(
                label = "RT",
                isConnected = isConnected,
                wide = true,
                onPress = {
                    transport.sendKey(MacProtocol.KeyAction.DOWN, 0xE0, 0)
                    onEvent()
                },
                onRelease = { transport.sendKey(MacProtocol.KeyAction.UP, 0xE0, 0) }
            )
        }
    }
}

@Composable
fun GamepadButton(
    label: String,
    isConnected: Boolean,
    color: Color = ElysiumColors.OnSurface,
    wide: Boolean = false,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(50),
        label = "btn"
    )

    Box(
        modifier = Modifier
            .then(if (wide) Modifier.width(60.dp) else Modifier.size(48.dp))
            .height(if (wide) 36.dp else 48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(if (wide) RoundedCornerShape(8.dp) else CircleShape)
            .background(
                if (pressed) color.copy(alpha = 0.4f)
                else ElysiumColors.Surface.copy(alpha = if (isConnected) 0.8f else 0.3f)
            )
            .border(
                1.dp,
                if (pressed) color else color.copy(alpha = 0.3f),
                if (wide) RoundedCornerShape(8.dp) else CircleShape
            )
            .pointerInput(isConnected) {
                if (!isConnected) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    onPress()
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val up = event.changes.firstOrNull { !it.pressed }
                        if (up != null) {
                            pressed = false
                            onRelease()
                            up.consume()
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = if (wide) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConnected) color else ElysiumColors.OnSurfaceVariant
        )
    }
}
