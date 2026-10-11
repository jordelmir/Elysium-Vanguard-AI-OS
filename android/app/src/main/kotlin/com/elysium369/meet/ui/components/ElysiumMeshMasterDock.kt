package com.elysium369.meet.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.mesh.ElysiumMeshGovernance
import com.elysium369.meet.core.mesh.MeshTransportLevel
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   M E S H   M A S T E R   D O C K   ( 3 D   N E O N )
 * ══════════════════════════════════════════════════════════════════════
 *
 * Consola de mando táctica 3D situada en la parte superior del Home,
 * exactamente sobre "Acciones Rápidas".
 *
 * Gobierna todo el proyecto MEET / Elysium:
 *  - Switch 1: WI-FI MESH (Alta velocidad: Llamadas VoIP, Audio HD, Imágenes)
 *  - Switch 2: BLUETOOTH MESH (Supervivencia: Textos, Telemetría, SOS Off-Grid)
 *  - Switch 3: DUAL SYNC HÍBRIDO (Mantiene ambos activos con Prioridad Wi-Fi -> BLE)
 */
@Composable
fun ElysiumMeshMasterDock(
    modifier: Modifier = Modifier
) {
    val isWifiOn by ElysiumMeshGovernance.isWifiMeshEnabled.collectAsState()
    val isBleOn by ElysiumMeshGovernance.isBleMeshEnabled.collectAsState()
    val isDualOn by ElysiumMeshGovernance.isDualSyncEnabled.collectAsState()
    val isWifiHwOn by ElysiumMeshGovernance.isWifiHardwareOn.collectAsState()
    val isBleHwOn by ElysiumMeshGovernance.isBleHardwareOn.collectAsState()
    val localIp by ElysiumMeshGovernance.localIpAddress.collectAsState()
    val discoveredPeers by ElysiumMeshGovernance.discoveredPhysicalPeers.collectAsState()
    val activeLinkedPeer by ElysiumMeshGovernance.activeLinkedPeer.collectAsState()
    val activeRoute by ElysiumMeshGovernance.activeRoute.collectAsState()
    val signalQuality by ElysiumMeshGovernance.meshSignalQuality.collectAsState()
    val peersCount by ElysiumMeshGovernance.connectedPeersCount.collectAsState()
    val statusDetail by ElysiumMeshGovernance.statusDetail.collectAsState()

    // ── Animación de pulso neón 3D continuo ──
    val infiniteTransition = rememberInfiniteTransition(label = "MeshPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "RadarSweep"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0xFF00F0FF).copy(alpha = 0.25f),
                spotColor = Color(0xFF39FF66).copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF090F1C),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00F0FF).copy(alpha = if (isWifiOn && isWifiHwOn) pulseAlpha else 0.2f),
                    Color(0xFF39FF66).copy(alpha = if (isDualOn && isWifiHwOn && isBleHwOn) pulseAlpha else 0.2f),
                    Color(0xFF7928CA).copy(alpha = if (isBleOn && isBleHwOn) pulseAlpha else 0.2f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Cabecera del Dock con Radar & Telemetría ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF00F0FF).copy(alpha = 0.35f),
                                        Color(0xFF060B14)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isDualOn && isWifiHwOn && isBleHwOn) "⚡" else if (isWifiOn && isWifiHwOn) "📶" else if (isBleOn && isBleHwOn) "📡" else "⭕",
                            fontSize = 18.sp
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RED MESH ELYSIUM",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            val liveTagText = when {
                                !isWifiHwOn && !isBleHwOn -> "HW APAGADO"
                                activeLinkedPeer != null -> "ENLAZADO"
                                (isWifiOn && isWifiHwOn) || (isBleOn && isBleHwOn) -> "EN VIVO"
                                else -> "INACTIVO"
                            }
                            val liveTagColor = when {
                                !isWifiHwOn && !isBleHwOn -> Color(0xFFFF4466)
                                activeLinkedPeer != null -> Color(0xFF00F0FF)
                                (isWifiOn && isWifiHwOn) || (isBleOn && isBleHwOn) -> Color(0xFF39FF66)
                                else -> MeetColors.textSecondary
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(liveTagColor.copy(alpha = 0.18f))
                                    .border(1.dp, liveTagColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = liveTagText,
                                    color = liveTagColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = if (localIp != null) "IP LOCAL: $localIp · HW RADAR ON" else "GOBERNADOR SUPREMO DE RADIOS P2P",
                            color = Color(0xFF00D9FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Píldora de pares en alcance
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E1A30),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (discoveredPeers.isNotEmpty()) Color(0xFF39FF66) else if (isWifiHwOn || isBleHwOn) Color(0xFFFFAA00) else Color(0xFFFF3366))
                        )
                        Text(
                            text = "${discoveredPeers.size} Nodos P2P",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Barra Jerárquica de Enrutamiento (1° Internet -> 2° Wi-Fi -> 3° Bluetooth) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF060B15),
                border = BorderStroke(1.dp, Color(0xFF162540))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PRIORIDAD:",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )

                    HierarchyBadge(
                        order = "1°",
                        label = "Internet Nube",
                        isActive = activeRoute == MeshTransportLevel.INTERNET,
                        accentColor = Color(0xFF00D9FF)
                    )

                    Text("▶", color = Color(0xFF304565), fontSize = 10.sp)

                    HierarchyBadge(
                        order = "2°",
                        label = if (!isWifiHwOn) "Wi-Fi OFF" else "Wi-Fi Mesh",
                        isActive = activeRoute == MeshTransportLevel.WIFI_DIRECT_MESH,
                        accentColor = if (isWifiHwOn) Color(0xFF39FF66) else Color(0xFFFF4444)
                    )

                    Text("▶", color = Color(0xFF304565), fontSize = 10.sp)

                    HierarchyBadge(
                        order = "3°",
                        label = if (!isBleHwOn) "BLE OFF" else "BLE Mesh",
                        isActive = activeRoute == MeshTransportLevel.BLUETOOTH_BLE_MESH,
                        accentColor = if (isBleHwOn) Color(0xFF9D00FF) else Color(0xFFFF4444)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── LOS 3 INTERRUPTORES NEÓN 3D LLAMATIVOS CON DETECCIÓN REAL DE HARDWARE ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. SWITCH WI-FI MESH
                Neon3DSwitchButton(
                    modifier = Modifier.weight(1f),
                    title = "WIFI MESH",
                    subtitle = if (!isWifiHwOn) "HW APAGADO ⚠️" else if (localIp != null) "IP: ${localIp?.takeLast(11)}" else "Llamadas · HD",
                    icon = "⚡",
                    isOn = isWifiOn && isWifiHwOn,
                    activeColor = if (isWifiHwOn) Color(0xFF00F0FF) else Color(0xFFFF3366),
                    onToggle = {
                        if (isWifiHwOn) {
                            ElysiumMeshGovernance.setWifiMesh(!isWifiOn)
                        } else {
                            ElysiumMeshGovernance.setWifiMesh(true)
                        }
                    }
                )

                // 2. SWITCH BLUETOOTH MESH
                Neon3DSwitchButton(
                    modifier = Modifier.weight(1f),
                    title = "BLE MESH",
                    subtitle = if (!isBleHwOn) "HW APAGADO ⚠️" else "Resiliente · SOS",
                    icon = "📡",
                    isOn = isBleOn && isBleHwOn,
                    activeColor = if (isBleHwOn) Color(0xFF7928CA) else Color(0xFFFF3366),
                    onToggle = {
                        if (isBleHwOn) {
                            ElysiumMeshGovernance.setBleMesh(!isBleOn)
                        } else {
                            ElysiumMeshGovernance.setBleMesh(true)
                        }
                    }
                )

                // 3. SWITCH DUAL SYNC SINÉRGICO (AMBOS ACTIVOS CON PRIORIDAD)
                Neon3DSwitchButton(
                    modifier = Modifier.weight(1.15f),
                    title = "DUAL SYNC",
                    subtitle = if (!isWifiHwOn || !isBleHwOn) "HW PARCIAL" else if (discoveredPeers.isNotEmpty()) "${discoveredPeers.size} P2P Directo" else "Prioridad Wi-Fi",
                    icon = "🚀",
                    isOn = isDualOn && isWifiHwOn && isBleHwOn,
                    activeColor = Color(0xFF39FF66),
                    onToggle = { ElysiumMeshGovernance.setDualSync(!isDualOn) }
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Pie informativo en vivo ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusDetail,
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Calidad:", color = MeetColors.textMuted, fontSize = 9.sp)
                    Text(
                        text = "$signalQuality%",
                        color = if (signalQuality > 70) Color(0xFF39FF66) else Color(0xFFFFAA00),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Botón / Interruptor interactivo Neón con relieve 3D y brillo fosforescente
 */
@Composable
private fun Neon3DSwitchButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: String,
    isOn: Boolean,
    activeColor: Color,
    onToggle: () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isOn) activeColor.copy(alpha = 0.16f) else Color(0xFF0A101C),
        label = "BtnBg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isOn) activeColor else Color(0xFF1A2A42),
        label = "BtnBorder"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        color = animatedBg,
        border = BorderStroke(if (isOn) 1.5.dp else 1.dp, animatedBorder),
        shadowElevation = if (isOn) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(icon, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isOn) activeColor else Color.Gray)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = title,
                color = if (isOn) Color.White else MeetColors.textSecondary,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                color = if (isOn) activeColor else MeetColors.textMuted,
                fontSize = 9.sp,
                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            // Switch visual estilo interruptor cyber
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) activeColor.copy(alpha = 0.35f) else Color(0xFF141E2E))
                    .border(1.dp, if (isOn) activeColor else Color(0xFF22344E), RoundedCornerShape(9.dp))
                    .padding(2.dp),
                contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isOn) activeColor else Color(0xFF556885))
                )
            }
        }
    }
}

@Composable
private fun HierarchyBadge(
    order: String,
    label: String,
    isActive: Boolean,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) accentColor.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (isActive) accentColor else Color(0xFF1F3350)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = order,
                color = if (isActive) accentColor else MeetColors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = if (isActive) Color.White else MeetColors.textSecondary,
                fontSize = 9.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
