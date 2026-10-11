package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.core.agent.ui.agentAction
import com.elysium369.meet.core.agent.ui.AgentUiControlId

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.home.HomeExperience
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.ui.navigation.safeNavigate
import com.elysium369.meet.ui.screens.home.components.HomeExperiencePreviewBanner
import com.elysium369.meet.ui.screens.home.components.HomeExperienceSelectionDialog
import com.elysium369.meet.ui.screens.home.components.HomeExperienceSwitcherHeaderButton
import com.elysium369.meet.ui.home.activity.HomeActivityStripPolicy
import com.elysium369.meet.ui.home.activity.HomeActivityStripWidget
import com.elysium369.meet.ui.home.activity.VehicleAlert
import com.elysium369.meet.ui.theme.MeetColors
import java.util.Calendar

@Composable
fun HomeAdaptiveScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onSelectExperience: (HomeExperience) -> Unit = {},
    onPreviewExperience: (HomeExperience) -> Unit = {},
    isPreview: Boolean = false,
    onCommitPreview: () -> Unit = {},
    onCancelPreview: () -> Unit = {}
) {
    val context = LocalContext.current
    val compactHeader = LocalConfiguration.current.screenWidthDp < 600
    val prefs = remember { context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE) }
    val userProfile = remember { prefs.getString("user_profile", "owner").orEmpty() }

    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val readiness by viewModel.readinessMonitors.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val protocol by viewModel.detectedProtocol.collectAsState()
    val adapterVer by viewModel.adapterVersion.collectAsState()
    val isClone by viewModel.isCloneAdapter.collectAsState()
    val platformOwnerAccess by viewModel.platformOwnerAccess.collectAsState()
    val activeRide by viewModel.activeRideRequest.collectAsState()
    val maintenanceAlerts by viewModel.maintenanceAlerts.collectAsState()
    val activeJourneys by viewModel.activeJourneys.collectAsState()
    val activePttChannels by viewModel.activePttChannels.collectAsState()
    val pendingMessageCount by viewModel.pendingMessageCount.collectAsState()

    val totalDtcs = activeDtcs.size
    val readyCount = readiness?.monitors?.count { it.complete } ?: 0
    val monitorCount = readiness?.monitors?.size ?: 0

    val prioritizedActions = remember(activeVehicle, obdState, activeDtcs, healthScore, readyCount, monitorCount) {
        HomeActionEngine.derivePrioritizedActions(
            hasVehicle = activeVehicle != null,
            vehicleId = activeVehicle?.id,
            obdState = obdState,
            activeDtcs = activeDtcs,
            healthScore = healthScore,
            monitorsReady = readyCount,
            monitorsTotal = monitorCount
        )
    }

    val modulesBySection = remember(userProfile, platformOwnerAccess) {
        HomeModuleRegistry.getModulesByCategory(
            userRole = userProfile,
            isPlatformOwner = platformOwnerAccess == com.elysium369.meet.ride.domain.PlatformOwnerAccess.GRANTED
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    val scrollState = rememberScrollState()
    var showThemeCustomizer by remember { mutableStateOf(false) }
    var showExperienceDialog by remember { mutableStateOf(false) }

    if (showExperienceDialog) {
        HomeExperienceSelectionDialog(
            currentExperience = HomeExperience.ADAPTIVE,
            onDismiss = { showExperienceDialog = false },
            onSelectExperience = onSelectExperience,
            onPreviewExperience = onPreviewExperience
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        com.elysium369.meet.ui.components.ElysiumArtworkBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState, overscrollEffect = null),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Banner if in preview mode
            if (isPreview) {
                HomeExperiencePreviewBanner(
                    previewExperience = HomeExperience.ADAPTIVE,
                    onCommit = onCommitPreview,
                    onCancel = onCancelPreview
                )
            }

            ElysiumVanguardHeader()

            // ── Hero Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.titleSmall,
                        color = MeetColors.textSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = if (compactHeader) 18.sp else 24.sp)) {
                                append("COMMAND")
                            }
                            append(if (compactHeader) "\n" else " ")
                            withStyle(SpanStyle(color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = if (compactHeader) 14.sp else 18.sp)) {
                                append("CENTER")
                            }
                        },
                        softWrap = true,
                        maxLines = 2,
                    )
                    // Role badge
                    val roleLabel = when (userProfile) {
                        "owner" -> "Dueño de carro"
                        "mechanic" -> "Mecánico"
                        "workshop" -> "Taller"
                        "fleet" -> "Flota"
                        "ride_passenger" -> "Pasajero"
                        "ride_driver" -> "Conductor"
                        else -> "Usuario"
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        border = ButtonDefaults.outlinedButtonBorder,
                    ) {
                        Text(
                            text = roleLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeExperienceSwitcherHeaderButton(
                        currentExperience = HomeExperience.ADAPTIVE,
                        onClick = { showExperienceDialog = true }
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cardBackground)
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), CircleShape)
                            .clickable { showThemeCustomizer = true }
                            .then(Modifier.pulseOnHover()),
                        contentAlignment = Alignment.Center
                    ) {
                        MeetSectionIcon(
                            key = "theme",
                            contentDescription = "Personalizar tema",
                            tint = MeetColors.neonGreen,
                            size = 18.dp,
                            fallbackGlyph = "🎨"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cardBackground)
                            .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), CircleShape)
                            .clickable { navController.safeNavigate(MeetDestinations.SETTINGS) }
                            .then(Modifier.pulseOnHover()),
                        contentAlignment = Alignment.Center
                    ) {
                        MeetSectionIcon(
                            key = "settings",
                            contentDescription = "Ajustes",
                            tint = MeetColors.electricBlue,
                            size = 18.dp,
                            fallbackGlyph = "⚙️"
                        )
                    }
                }
            }

            val vehicleAlerts = remember(activeDtcs, maintenanceAlerts) {
                val dtcAlerts = activeDtcs.take(3).map { code ->
                    VehicleAlert(
                        title = "DTC Activo",
                        message = code,
                        severity = "WARNING",
                        isCritical = true,
                    )
                }
                val maintenanceAlertItems = maintenanceAlerts.take(2).map { alert ->
                    val remainingKm = alert.nextDueKm - alert.lastDoneKm
                    VehicleAlert(
                        title = "${alert.type} — ${alert.intervalKm} km",
                        message = if (remainingKm > 0) "Vence en ${remainingKm} km" else (alert.notes ?: "Requiere atención"),
                        severity = if (remainingKm <= 0) "CRITICAL" else "WARNING",
                        isCritical = remainingKm <= 0,
                    )
                }
                dtcAlerts + maintenanceAlertItems
            }

            val activityStrip = remember(activeRide, vehicleAlerts, activeJourneys, activePttChannels, pendingMessageCount) {
                HomeActivityStripPolicy.buildFromState(
                    activeRides = listOfNotNull(
                        activeRide?.let { ride ->
                            HomeActivityStripPolicy.activeRide(
                                rideId = ride.requestId,
                                vehicleName = ride.assignedDriverVehicle,
                                stateName = ride.serverState ?: ride.status,
                            )
                        },
                    ),
                    fuelAlerts = emptyList(),
                    activeJourneys = activeJourneys,
                    activePttChannels = activePttChannels,
                    pendingMessages = pendingMessageCount,
                    activeListings = 0,
                    vehicleAlerts = vehicleAlerts
                )
            }

            if (activityStrip.hasActiveOperations) {
                HomeActivityStripWidget(
                    strip = activityStrip,
                    onDismissRide = {
                        val stuckRide = activeRide
                        if (stuckRide != null &&
                            stuckRide.status == "PENDING_PUBLICATION" &&
                            stuckRide.serverVersion <= 0L
                        ) {
                            viewModel.localCancelStuckRide(stuckRide.requestId)
                        } else {
                            viewModel.selectActiveRide(null)
                        }
                    },
                    onItemClick = { item ->
                        item.actionRoute?.let { navController.safeNavigate(it) }
                    }
                )
            }

            // ── Elysium Mesh Master Dock (Governs Wi-Fi, Bluetooth & Dual Sync) ──
            com.elysium369.meet.ui.components.ElysiumMeshMasterDock(navController = navController)

            if (userProfile == "ride_passenger") {
                // ── Passenger Mobility Hero Card (Ride-Centric Home Screen) ──
                EliteCard(
                    glowColor = MeetColors.neonGreen,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.5f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(16.dp),
                    onClick = { navController.safeNavigate(MeetDestinations.RIDE_PASSENGER_REQUEST) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🚕", fontSize = 20.sp)
                                Column {
                                    Text(
                                        "¿A dónde te llevamos hoy?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Transporte inteligente con tarifa verificada",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Elysium RIDES",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MeetColors.neonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeetColors.cardBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.safeNavigate(MeetDestinations.RIDE_PASSENGER_REQUEST) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🔍", fontSize = 14.sp)
                                Text(
                                    "Ingresa tu punto de destino...",
                                    color = MeetColors.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Button(
                            onClick = { navController.safeNavigate(MeetDestinations.RIDE_PASSENGER_REQUEST) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                        ) {
                            Text("PEDIR VIAJE AHORA", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (userProfile == "ride_driver") {
                // ── Driver Cockpit Hero Card (Ride-Centric Home Screen) ──
                EliteCard(
                    glowColor = MeetColors.electricBlue,
                    borderColor = MeetColors.electricBlue.copy(alpha = 0.5f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(16.dp),
                    onClick = { navController.safeNavigate(MeetDestinations.RIDE_DRIVER_MODE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🚘", fontSize = 20.sp)
                                Column {
                                    Text(
                                        "Cockpit del Conductor",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Turno y despacho inteligente Elysium",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MeetColors.electricBlue.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "DRIVER OS",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MeetColors.electricBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = { navController.safeNavigate(MeetDestinations.RIDE_DRIVER_MODE) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue, contentColor = Color.White)
                        ) {
                            Text("ABRIR COCKPIT DEL CONDUCTOR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Vehicle & Connection Hero Card ──
            EliteCard(
                glowColor = if (obdState == ObdState.CONNECTED) MeetColors.success else MeetColors.electricBlue,
                borderColor = if (obdState == ObdState.CONNECTED) MeetColors.success.copy(alpha = 0.4f) else MeetColors.electricBlue.copy(alpha = 0.3f),
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                onClick = { navController.safeNavigate(MeetDestinations.GARAGE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🚗", fontSize = 18.sp)
                            Column {
                                Text(
                                    activeVehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Sin Vehículo Activo",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                val maskedVin = activeVehicle?.vin?.let {
                                    if (it.length >= 4) "*".repeat(it.length - 4) + it.takeLast(4) else it
                                } ?: "N/A"
                                Text(
                                    "VIN: $maskedVin",
                                    color = MeetColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // OBD Connection Pill
                        val isConnected = obdState == ObdState.CONNECTED
                        val statusColor = when (obdState) {
                            ObdState.CONNECTED -> MeetColors.success
                            ObdState.CONNECTING -> MeetColors.warning
                            ObdState.ERROR -> MeetColors.error
                            else -> MeetColors.textMuted
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = statusColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(statusColor, CircleShape)
                                        .then(if (isConnected) Modifier.pulseOnHover() else Modifier)
                                )
                                Text(
                                    if (isConnected) "OBD ONLINE" else if (obdState == ObdState.CONNECTING) "ENLAZANDO..." else "OBD OFFLINE",
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Key telemetry metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    val isObdOnline = obdState == ObdState.CONNECTED
                    MiniCommandBadge(
                        title = "Salud",
                        value = if (isObdOnline && healthScore > 0) "$healthScore%" else if (!isObdOnline) "—" else "$healthScore%",
                        color = if (!isObdOnline) MeetColors.textMuted else if (healthScore >= 80) MeetColors.neonGreen else if (healthScore >= 50) MeetColors.warning else MeetColors.error,
                        modifier = Modifier.weight(1f)
                    )
                        MiniCommandBadge(
                            title = "Fallas (DTC)",
                            value = "$totalDtcs",
                            color = if (totalDtcs > 0) MeetColors.neonGreen else MeetColors.neonGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MiniCommandBadge(
                            title = "Monitores",
                            value = if (monitorCount > 0) "$readyCount/$monitorCount" else "—",
                            color = if (readyCount == monitorCount && monitorCount > 0) MeetColors.neonGreen else MeetColors.warning,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Connection Action Bar ──
                    when (obdState) {
                        ObdState.CONNECTED -> {
                            EliteButton(
                                text = "DESCONECTAR ESCÁNER",
                                onClick = { viewModel.disconnect() },
                                color = MeetColors.error,
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            )
                        }
                        ObdState.CONNECTING, ObdState.NEGOTIATING -> {
                            EliteButton(
                                text = "CANCELAR CONEXIÓN",
                                onClick = { viewModel.cancelConnection() },
                                color = MeetColors.warning,
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            )
                        }
                        else -> {
                            EliteButton(
                                text = "CONECTAR ESCÁNER OBD",
                                onClick = { navController.safeNavigate("connect") },
                                color = MeetColors.neonGreen,
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            )
                        }
                    }
                }
            }

            // ── "⚡ AHORA" — Contextual Prioritized Actions ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚡ ACCIONES PRIORITARIAS (AHORA)",
                        color = MeetColors.neonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "${prioritizedActions.size} PENDIENTE(S)",
                        color = MeetColors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ── Elysium LinkSphere Social Hero ──
                EliteCard(
                    glowColor = MeetColors.neonGreen,
                    borderColor = MeetColors.cyberCyan.copy(alpha = 0.5f),
                    backgroundColor = Color(0xFF071424),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { navController.safeNavigate(MeetDestinations.LINKSPHERE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("🪐", fontSize = 28.sp)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "ELYSIUM LINKSPHERE",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.5.sp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = MeetColors.neonGreen.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, MeetColors.neonGreen)
                                    ) {
                                        Text(
                                            "SOCIAL",
                                            color = MeetColors.neonGreen,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    "Red social universal de enlaces: YouTube, TikTok, Spotify, X...",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text("→", color = MeetColors.neonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ── Featured Education Quick Action Hero ──
                EliteCard(
                    glowColor = Color(0xFFD4AF37),
                    borderColor = Color(0xFFD4AF37).copy(alpha = 0.5f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(16.dp),
                    onClick = { navController.safeNavigate(MeetDestinations.ELYSIUM_LEARNING_OS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("🎓", fontSize = 28.sp)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "ELYSIUM EDUCACIÓN OS",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.5.sp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD4AF37).copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            "PISA 6",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFD4AF37),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Tutor Socrático IA · Estándar OCDE PISA · 100% Offline SHA-256",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        Button(
                            onClick = { navController.safeNavigate(MeetDestinations.ELYSIUM_LEARNING_OS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD4AF37),
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text("ENTRAR", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                prioritizedActions.forEach { action ->
                    AdaptiveActionCard(
                        action = action,
                        onActionClick = { navController.safeNavigate(action.destination) }
                    )
                }
            }

            // ── Modular Sections Grid ──
            modulesBySection.forEach { (section, modules) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(section.glyph, fontSize = 14.sp)
                        Text(
                            section.title,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // 2 columns grid
                    val chunked = modules.chunked(2)
                    chunked.forEach { rowModules ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowModules.forEach { module ->
                                AdaptiveModuleTile(
                                    module = module,
                                    onClick = { navController.safeNavigate(module.destination) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowModules.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        if (showThemeCustomizer) {
            com.elysium369.meet.ui.components.SystemThemeCustomizerDialog(
                onDismiss = { showThemeCustomizer = false }
            )
        }
    }
}

@Composable
private fun AdaptiveActionCard(
    action: HomeAction,
    onActionClick: () -> Unit
) {
    val borderColor = when (action.priority) {
        HomeActionPriority.CRITICAL -> MeetColors.error.copy(alpha = 0.6f)
        HomeActionPriority.HIGH -> MeetColors.warning.copy(alpha = 0.5f)
        HomeActionPriority.NORMAL -> MeetColors.cyberCyan.copy(alpha = 0.4f)
        HomeActionPriority.LOW -> MeetColors.neonGreen.copy(alpha = 0.3f)
    }

    val glowColor = when (action.priority) {
        HomeActionPriority.CRITICAL -> MeetColors.error
        HomeActionPriority.HIGH -> MeetColors.warning
        HomeActionPriority.NORMAL -> MeetColors.cyberCyan
        HomeActionPriority.LOW -> MeetColors.neonGreen
    }

    EliteCard(
        glowColor = glowColor,
        borderColor = borderColor,
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(action.glyph, fontSize = 18.sp)
                    Column {
                        Text(
                            action.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            action.subtitle,
                            color = MeetColors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (action.priority) {
                            HomeActionPriority.CRITICAL -> MeetColors.error
                            HomeActionPriority.HIGH -> MeetColors.warning
                            else -> MeetColors.neonGreen
                        }
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        action.buttonLabel,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveModuleTile(
    module: HomeModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileGlow = if (module.isHighlight) MeetColors.neonGreen else Color.Transparent
    val tileBorder = if (module.isHighlight) MeetColors.neonGreen.copy(alpha = 0.35f) else MeetColors.borderSubtle

    EliteCard(
        glowColor = tileGlow,
        borderColor = tileBorder,
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
        modifier = modifier.height(68.dp).agentAction(
            AgentUiControlId("home.command.${module.destination}"), module.title,
            route = "home", onActivate = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MeetSectionIcon(
                key = module.glyph,
                contentDescription = module.title,
                tint = if (module.isHighlight) MeetColors.neonGreen else MeetColors.cyberCyan,
                size = 20.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        module.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    module.badgeText?.let { badge ->
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.neonGreen)
                        ) {
                            Text(
                                badge,
                                color = MeetColors.neonGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    module.subtitle,
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MiniCommandBadge(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(
                title,
                color = MeetColors.textMuted,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}
