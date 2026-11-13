package com.elysium.nexus.ui.mac

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Mac/PC discovery screen.
 *
 * The user picked "Mac" or "PC" on the Hub. This
 * screen scans the local network for hosts that
 * are running the Elysium agent (mDNS/Bonjour
 * service `_elysium._tcp`). The scan is **animated**
 * — the user sees a radar-style spinner while the
 * scan runs. When hosts are found, they appear as
 * cards the user can tap to start pairing.
 *
 * The "Agregar manualmente" option lets the user
 * enter an IP/hostname for cases where mDNS is
 * blocked (corporate networks, mDNS repeaters, etc.).
 */
@Composable
fun MacDiscoveryScreen(
    onBack: () -> Unit,
    onHostSelected: (DiscoveredHost) -> Unit,
    onManualAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val discovery = remember { com.elysium.nexus.core.transport.mac.MacDiscovery(context) }
    var showHelp by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(true) }
    var hosts by remember { mutableStateOf<List<DiscoveredHost>>(emptyList()) }
    var scanTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(scanTrigger) {
        isScanning = true
        val foundList = mutableListOf<DiscoveredHost>()

        val rememberedHost = try {
            (com.elysium.nexus.core.profile.LastDeviceMemory(context).get() as? com.elysium.nexus.core.profile.LastDevice.Mac)?.host
        } catch (_: Throwable) {
            null
        }

        // Run fast TCP probe and Bonjour concurrently for instantaneous discovery
        coroutineScope {
            // Coroutine 1: Fast TCP probe (checks remembered host first in <350ms, then gateways and local subnet)
            launch {
                try {
                    val host = com.elysium.nexus.core.transport.mac.FastHostScanner
                        .findFirstActiveHost(context, preferredHostIp = rememberedHost, timeoutMs = 2500)
                    if (host != null) {
                        val discovered = DiscoveredHost(
                            id = host.host,
                            name = host.name,
                            type = HostType.MAC_DESKTOP,
                            signalStrength = 4,
                            isOnline = true,
                            host = host.host,
                            port = host.port,
                            publicKeyB64 = host.publicKeyB64
                        )
                        synchronized(foundList) {
                            if (foundList.none { it.host == discovered.host }) {
                                foundList.add(discovered)
                                hosts = foundList.toList()
                                android.util.Log.i("MacDiscoveryScreen", "Fast TCP probe found host=${discovered.host}:${discovered.port}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MacDiscoveryScreen", "Fast TCP probe failed: ${e.message}")
                }
            }

            // Coroutine 2: Bonjour discovery for rich metadata
            launch {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(3_000) {
                        discovery.discover().collect { item ->
                            val hostType = when {
                                item.model.lowercase().contains("macbook") -> HostType.MAC_LAPTOP
                                item.model.lowercase().contains("win") -> HostType.WINDOWS_PC
                                item.model.lowercase().contains("linux") -> HostType.LINUX_PC
                                else -> HostType.MAC_DESKTOP
                            }
                            val discovered = DiscoveredHost(
                                id = item.host,
                                name = item.name,
                                type = hostType,
                                signalStrength = 4,
                                isOnline = true,
                                host = item.host,
                                port = item.port,
                                publicKeyB64 = item.publicKeyB64
                            )
                            synchronized(foundList) {
                                val existingIndex = foundList.indexOfFirst { it.host == discovered.host }
                                if (existingIndex >= 0) {
                                    // Update with richer Bonjour metadata
                                    foundList[existingIndex] = discovered
                                } else {
                                    foundList.add(discovered)
                                }
                                hosts = foundList.toList()
                                android.util.Log.i("MacDiscoveryScreen", "Bonjour host found name=${discovered.name} host=${discovered.host}:${discovered.port}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MacDiscoveryScreen", "Bonjour discovery failed: ${e.message}")
                }
            }
            Unit
        }
        isScanning = false
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
                    onClick = onBack,
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
                title = if (isScanning) "Buscando..." else "Macs y PCs",
                subtitle = if (isScanning) {
                    "Escaneando la red local..."
                } else {
                    "${hosts.size} dispositivos encontrados"
                },
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    if (isScanning) {
                        NeonStatusPill(label = "Escaneando", color = ElysiumColors.NeonOrange)
                    } else {
                        NeonStatusPill(label = "Wi-Fi", color = ElysiumColors.NeonGreen)
                    }
                }
            )
            // === SCANNING ANIMATION ===
            if (isScanning) {
                ScanningAnimation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 16.dp)
                )
            }
            // === HOST LIST ===
            if (!isScanning && hosts.isNotEmpty()) {
                NeonSectionHeader(
                    text = "Dispositivos encontrados",
                    accent = ElysiumColors.NeonCyan,
                    modifier = Modifier.padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    )
                )
                hosts.forEach { host ->
                    HostCard(
                        host = host,
                        onClick = { onHostSelected(host) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = info.sidePadding,
                                vertical = 4.dp
                            )
                    )
                }
            }
            // === NO HOSTS FOUND: STEP-BY-STEP GITHUB GUIDE ===
            if (!isScanning && hosts.isEmpty()) {
                MacAgentDownloadGuide(
                    isInitiallyExpanded = true,
                    onRetryScan = { scanTrigger++ },
                    modifier = Modifier.padding(horizontal = info.sidePadding, vertical = 6.dp)
                )
            }
            // === MANUAL ADD (available if automatic connection fails / no hosts found) ===
            if (!isScanning) {
                NeonSectionHeader(
                    text = "Conexión Manual · Wi-Fi / red local",
                    accent = ElysiumColors.NeonPurple,
                    modifier = Modifier.padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    )
                )
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 4.dp)
                        .clickable { onManualAdd() },
                    accent = ElysiumColors.NeonPurple
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElysiumColors.NeonPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = ElysiumColors.NeonPurple,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Agregar Mac por IP",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElysiumColors.OnSurface
                            )
                            Text(
                                text = "Usar la IP local del Mac y el puerto 7878",
                                style = TextStyle(fontSize = 12.sp),
                                color = ElysiumColors.OnSurfaceMuted
                            )
                        }
                    }
                }

                // If hosts WERE found, also expose the guide in collapsed state
                if (hosts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MacAgentDownloadGuide(
                        isInitiallyExpanded = false,
                        onRetryScan = { scanTrigger++ },
                        modifier = Modifier.padding(horizontal = info.sidePadding, vertical = 6.dp)
                    )
                }
            } // end if (!isScanning)
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Buscar Mac/PC",
            whatIsThis = "Esta pantalla busca Macs y PCs en tu red Wi-Fi " +
                "que tengan el agente Elysium instalado.",
            howToUse = listOf(
                "Espera a que termine el escaneo (unos segundos).",
                "Toca el dispositivo al que quieres conectarte.",
                "Si no aparece, toca 'Agregar manualmente'."
            ),
            tip = "Para que tu Mac aparezca, descarga e instala el agente Elysium Nexus desde GitHub Releases " +
                "siguiendo la guía interactiva paso a paso que se muestra en esta pantalla.",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * A host discovered on the network.
 *
 * Phase ULT.4 added the transport-relevant
 * fields (`host`, `port`, `publicKeyB64`) so the
 * pairing screen can open a real TCP socket
 * to the Mac agent and the connection can be
 * end-to-end encrypted.
 */
data class DiscoveredHost(
    val id: String,
    val name: String,
    val type: HostType,
    val signalStrength: Int,
    val isOnline: Boolean,
    /** IP / hostname the Mac agent is reachable on. Phase ULT.4. */
    val host: String = "127.0.0.1",
    /** TCP port the agent listens on (7878 by default). Phase ULT.4. */
    val port: Int = 7878,
    /** Base64-encoded X25519 public key from the agent's TXT record. Phase ULT.4. */
    val publicKeyB64: String? = null
)

enum class HostType {
    MAC_LAPTOP, MAC_DESKTOP, WINDOWS_PC, LINUX_PC;

    val labelEs: String
        get() = when (this) {
            MAC_LAPTOP -> "MacBook"
            MAC_DESKTOP -> "iMac / Mac Mini"
            WINDOWS_PC -> "PC Windows"
            LINUX_PC -> "PC Linux"
        }
    val icon: ImageVector
        get() = when (this) {
            MAC_LAPTOP, MAC_DESKTOP -> Icons.Filled.Laptop
            WINDOWS_PC, LINUX_PC -> Icons.Filled.Monitor
        }
}

private val MOCK_HOSTS = listOf(
    DiscoveredHost(
        id = "imac-jor",
        name = "iMac de Jor",
        type = HostType.MAC_DESKTOP,
        signalStrength = 4,
        isOnline = true,
        host = "192.168.1.5",
        port = 7878
    ),
    DiscoveredHost(
        id = "macbook-pro",
        name = "MacBook Pro",
        type = HostType.MAC_LAPTOP,
        signalStrength = 3,
        isOnline = true,
        host = "192.168.1.5",
        port = 7878
    ),
    DiscoveredHost(
        id = "windows-pc",
        name = "PC-Oficina",
        type = HostType.WINDOWS_PC,
        signalStrength = 2,
        isOnline = true,
        host = "192.168.1.5",
        port = 7878
    )
)

@Composable
private fun HostCard(
    host: DiscoveredHost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier.clickable(onClick = onClick),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 14.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    host.type.icon,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dispositivo por Wi-Fi",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.OnSurface,
                    maxLines = 1
                )
                Text(
                    text = "${host.type.labelEs} · ${host.host}:${host.port}",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurfaceMuted,
                    maxLines = 1
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .width(if (i < host.signalStrength) 4.dp else 2.dp)
                            .height((6 + i * 2).dp)
                            .background(
                                if (i < host.signalStrength) ElysiumColors.NeonGreen
                                else ElysiumColors.OnSurfaceMuted.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * The radar-style scanning animation.
 */
@Composable
private fun ScanningAnimation(
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "scanning")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_pulse"
    )
    NeonCard(
        modifier = modifier.height(220.dp),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 16.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = minOf(size.width, size.height) * 0.4f
                for (i in 0..2) {
                    val r = maxR * (0.3f + i * 0.35f) * pulse
                    drawCircle(
                        color = ElysiumColors.NeonCyan.copy(alpha = 0.3f - i * 0.08f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                rotate(rotation, Offset(cx, cy)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ElysiumColors.NeonCyan.copy(alpha = 0f),
                                ElysiumColors.NeonCyan.copy(alpha = 0.5f),
                                ElysiumColors.NeonCyan.copy(alpha = 0f)
                            ),
                            center = Offset(cx, cy)
                        ),
                        startAngle = 0f,
                        sweepAngle = 60f,
                        useCenter = true,
                        topLeft = Offset(cx - maxR, cy - maxR),
                        size = Size(maxR * 2, maxR * 2)
                    )
                }
                drawCircle(
                    color = ElysiumColors.NeonCyan,
                    radius = 4.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                Icon(
                    Icons.Filled.SignalWifi4Bar,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Buscando Macs y PCs...",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Text(
                    text = "Red Wi-Fi local",
                    style = TextStyle(fontSize = 11.sp),
                    color = ElysiumColors.OnSurfaceMuted
                )
            }
        }
    }
}
