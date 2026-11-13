package com.elysium.nexus.ui.mac

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.transport.mac.MacConnectionState
import com.elysium.nexus.core.transport.mac.MacTransport
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The pairing screen.
 *
 * The flow is the real end-to-end pairing the
 * §46 protocol specifies:
 *
 *  1. **Connecting** — the phone opens a TCP
 *     socket to the Mac, performs the X25519
 *     ECDH, derives the ChaCha20 channel key.
 *     During this state, an animated spinner
 *     shows progress.
 *  2. **Awaiting PIN** — the Mac has generated
 *     a 6-digit PIN and shows it in a window.
 *     The user must type the same PIN on the
 *     phone to confirm physical presence. The
 *     phone has 6 input boxes, auto-advancing
 *     as the user types.
 *  3. **Verifying** — the phone has sent the 6
 *     encrypted PIN_DIGIT frames. The Mac is
 *     comparing. The screen shows a spinner.
 *  4. **Connected** — the Mac accepted the PIN.
 *     The screen shows a green check and
 *     auto-transitions to the control surface.
 *  5. **Error** — the connection or pairing
 *     failed. The screen shows the reason and
 *     a "Reintentar" button.
 *
 * The PIN is **never stored on the device**; it
 * lives only in the [MacTransport]'s in-memory
 * state for the duration of the handshake. After
 * pairing it is discarded.
 */
@Composable
fun MacPairingScreen(
    host: DiscoveredHost,
    onBack: () -> Unit,
    onPaired: () -> Unit,
    transport: MacTransport,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(PairingState.CONNECTING) }
    var errorReason by remember { mutableStateOf<String?>(null) }
    val pinDigits = remember { mutableStateOf(List(6) { "" }) }
    val connectingProgress = remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    // Phase ULT.4 — drive the real handshake.
    LaunchedEffect(Unit) {
        transport.startHandshake(
            host = com.elysium.nexus.core.transport.mac.DiscoveredHost(
                name = host.name,
                host = host.host,
                port = host.port,
                model = host.type.labelEs,
                osVersion = "macOS",
                publicKeyB64 = host.publicKeyB64
            )
        )
    }
    // Observe the transport state and mirror it
    // into the UI state.
    LaunchedEffect(Unit) {
        transport.state.collect { s ->
            when (s) {
                is MacConnectionState.Connecting -> {
                    state = PairingState.CONNECTING
                }
                is MacConnectionState.AwaitingPin -> {
                    state = PairingState.AWAITING_PIN
                }
                is MacConnectionState.Ready -> {
                    state = PairingState.CONNECTED
                    delay(400)
                    onPaired()
                }
                is MacConnectionState.Error -> {
                    state = PairingState.ERROR
                    errorReason = s.reason
                }
                else -> Unit
            }
        }
    }
    // Animated progress for the connecting state.
    LaunchedEffect(state) {
        if (state == PairingState.CONNECTING) {
            while (state == PairingState.CONNECTING) {
                connectingProgress.value = (connectingProgress.value + 0.05f) % 1f
                delay(50)
            }
        }
    }

    var pinText by remember { mutableStateOf("") }

    // When 6 digits are entered, submit automatically.
    LaunchedEffect(pinText) {
        if (state == PairingState.AWAITING_PIN && pinText.length == 6) {
            val pin = pinText
            state = PairingState.VERIFYING
            scope.launch { transport.sendPin(pin) }
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = {
                        transport.disconnect()
                        onBack()
                    },
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // === HERO CARD ===
            NeonHeroCard(
                title = host.name,
                subtitle = when (state) {
                    PairingState.CONNECTING -> "Conectando…"
                    PairingState.AWAITING_PIN -> "Esperando PIN"
                    PairingState.VERIFYING -> "Verificando"
                    PairingState.CONNECTED -> "Conectado"
                    PairingState.ERROR -> "Error"
                },
                accent = when (state) {
                    PairingState.CONNECTING -> ElysiumColors.NeonCyan
                    PairingState.AWAITING_PIN -> ElysiumColors.NeonOrange
                    PairingState.VERIFYING -> ElysiumColors.NeonCyan
                    PairingState.CONNECTED -> ElysiumColors.NeonGreen
                    PairingState.ERROR -> ElysiumColors.NeonMagenta
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = when (state) {
                            PairingState.CONNECTING -> "TCP + X25519"
                            PairingState.AWAITING_PIN -> "Esperando PIN"
                            PairingState.VERIFYING -> "Verificando"
                            PairingState.CONNECTED -> "Conectado"
                            PairingState.ERROR -> "Error"
                        },
                        color = when (state) {
                            PairingState.CONNECTING -> ElysiumColors.NeonCyan
                            PairingState.AWAITING_PIN -> ElysiumColors.NeonOrange
                            PairingState.VERIFYING -> ElysiumColors.NeonCyan
                            PairingState.CONNECTED -> ElysiumColors.NeonGreen
                            PairingState.ERROR -> ElysiumColors.NeonMagenta
                        }
                    )
                }
            )
            // === STATE-SPECIFIC CONTENT ===
            when (state) {
                PairingState.CONNECTING -> ConnectingContent(
                    hostName = host.name,
                    progress = connectingProgress.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.AWAITING_PIN -> PinInputContent(
                    pinText = pinText,
                    onPinTextChange = { pinText = it },
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.VERIFYING -> VerifyingContent(
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.CONNECTED -> ConnectedContent(
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.ERROR -> ErrorContent(
                    reason = errorReason ?: "Error desconocido",
                    onRetry = {
                        errorReason = null
                        state = PairingState.CONNECTING
                        scope.launch {
                            transport.startHandshake(
                                host = com.elysium.nexus.core.transport.mac.DiscoveredHost(
                                    name = host.name,
                                    host = host.host,
                                    port = host.port,
                                    model = host.type.labelEs,
                                    osVersion = "macOS",
                                    publicKeyB64 = host.publicKeyB64
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
            }
            // === SECURITY EXPLANATION ===
            Spacer(modifier = Modifier.height(16.dp))
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonGreen
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Conexión cifrada",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonGreen
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tu teléfono y el host usan X25519 (criptografía de curva elíptica) " +
                        "para autenticarse. El PIN de 6 dígitos es solo para que TÚ confirmes " +
                        "que la Mac que aparece es la correcta. Nadie puede interceptar la " +
                        "conexión aunque esté en la misma Wi-Fi.",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Emparejamiento",
            whatIsThis = "Esta pantalla te pide el PIN de 6 dígitos que tu Mac o PC " +
                "está mostrando en una ventana. Es la confirmación final de que " +
                "estás hablando con el dispositivo correcto.",
            howToUse = listOf(
                "Abre la app Elysium Nexus en tu Mac o PC.",
                "Mira la ventana que aparece con un PIN de 6 dígitos.",
                "Toca las casillas de esta pantalla y escribe el mismo PIN.",
                "La conexión se establece automáticamente cuando los 6 dígitos coincidan."
            ),
            tip = "Si los PINs no coinciden, NO los confirmes. Puede ser un " +
                "atacante en tu misma red Wi-Fi.",
            onDismiss = { showHelp = false }
        )
    }
}

private enum class PairingState { CONNECTING, AWAITING_PIN, VERIFYING, CONNECTED, ERROR }

/**
 * The connecting content. Shows an animated
 * progress bar while the TCP socket opens and
 * the X25519 handshake runs.
 */
@Composable
private fun ConnectingContent(
    hostName: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "connecting")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "conn_rotation"
    )
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(80.dp)
            ) {
                val strokeWidth = 6.dp.toPx()
                val r = (size.minDimension - strokeWidth) / 2f
                drawArc(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = strokeWidth)
                )
                rotate(rotation) {
                    drawArc(
                        color = ElysiumColors.NeonCyan,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            Text(
                text = "Conectando a $hostName...",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.NeonCyan,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Intercambiando claves X25519 y derivando el secreto compartido.",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * The PIN input content. 6 boxes, one per
 * digit. The user types and the boxes auto-
 * advance. When all 6 are filled, the screen
 * automatically submits.
 */
@Composable
private fun PinInputContent(
    pinText: String,
    onPinTextChange: (String) -> Unit,
    hostName: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = ElysiumColors.NeonCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Escribe el PIN de 6 dígitos de tu $hostName",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // SINGLE CONTINUOUS TEXT FIELD (6 visual boxes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Invisible/transparent input field spanning the full width
                BasicTextField(
                    value = pinText,
                    onValueChange = { newValue ->
                        val clean = newValue.filter { it.isDigit() }.take(6)
                        onPinTextChange(clean)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    textStyle = TextStyle(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                )

                // 6 Visual boxes that display the typed digits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until 6) {
                        val char = pinText.getOrNull(i)?.toString() ?: ""
                        val isCurrent = (i == pinText.length)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            if (isCurrent) ElysiumColors.NeonCyan.copy(alpha = 0.35f)
                                            else ElysiumColors.NeonCyan.copy(alpha = 0.15f),
                                            ElysiumColors.Surface
                                        )
                                    )
                                )
                                .border(
                                    width = if (isCurrent) 2.dp else 1.5.dp,
                                    color = if (isCurrent) ElysiumColors.NeonGreen else ElysiumColors.NeonCyan.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (char.isNotEmpty()) char else "•",
                                style = TextStyle(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (char.isNotEmpty()) ElysiumColors.NeonCyan else ElysiumColors.OnSurfaceMuted
                                )
                            )
                        }
                    }
                }
            }

            Text(
                text = "Escribe continuo en el teclado. Se envía automáticamente al completar 6 dígitos.",
                style = TextStyle(fontSize = 11.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun VerifyingContent(
    hostName: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "verifying")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "verif_rotation"
    )
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(80.dp)
            ) {
                val strokeWidth = 6.dp.toPx()
                val r = (size.minDimension - strokeWidth) / 2f
                drawArc(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = strokeWidth)
                )
                rotate(rotation) {
                    drawArc(
                        color = ElysiumColors.NeonCyan,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            Text(
                text = "Verificando con $hostName...",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.NeonCyan,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Estableciendo canal cifrado. Esto toma unos segundos.",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    hostName: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonGreen,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f))
                    .border(
                        width = 3.dp,
                        color = ElysiumColors.NeonGreen,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = "¡Conectado a $hostName!",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.NeonGreen
            )
            Text(
                text = "Abriendo el control remoto...",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun ErrorContent(
    reason: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonMagenta,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Error de emparejamiento",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.NeonMagenta
            )
            Text(
                text = reason,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = ElysiumColors.OnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            NeonChip(
                label = "Reintentar",
                onClick = onRetry,
                accent = ElysiumColors.NeonCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            MacAgentDownloadGuide(
                isInitiallyExpanded = false,
                onRetryScan = onRetry
            )
        }
    }
}
