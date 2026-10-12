package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import com.elysium369.meet.core.agent.ui.AgentTextFieldRole
import com.elysium369.meet.core.agent.ui.AgentUiControlId
import com.elysium369.meet.core.agent.ui.agentTextInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import coil.compose.AsyncImage
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ui.components.ElysiumToastBar
import com.elysium369.meet.ui.components.ToastType
import com.elysium369.meet.ui.components.ElysiumAnimatedDialog
import com.elysium369.meet.ui.screens.ride.RideIncomingDispatchOverlay
import com.elysium369.meet.ui.screens.ride.RideLostAndFoundDialog
import androidx.compose.material.icons.filled.Inventory2
import com.elysium369.meet.ride.wallet.SinpeReceiptParser
import com.elysium369.meet.ride.wallet.ParsedSinpeReceipt
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.core.money.Money as CoreMoney
import com.elysium369.meet.ride.domain.RidePaymentMethod
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideFareEngine
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideArrivalPolicy
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ride.domain.RideTripPlanPolicy
import com.elysium369.meet.ride.map.RidePlaceSearchProvider
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ride.map.distanceKmFrom
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideSavedPlacesStore
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.RideMapDataSource
import com.elysium369.meet.ride.map.resilientRidePlaceSearchProvider
import com.elysium369.meet.ride.map.resilientRideRoutingProvider
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ride.domain.RidePassengerPreferences
import com.elysium369.meet.ride.domain.PassengerPreferencesSelector
import com.elysium369.meet.ride.domain.RideDriverPresencePolicy
import com.elysium369.meet.ride.domain.RideDispatchExpiryPolicy
import com.elysium369.meet.ride.driver.RideDriverFeedPolicy
import com.elysium369.meet.ride.data.RideProjectionConnectionState
import com.elysium369.meet.ride.traffic.RideRoadIncidentType
import com.elysium369.meet.ride.traffic.RideRoadSide
import com.elysium369.meet.ride.traffic.RideRoadReportAvailabilityPolicy
import com.elysium369.meet.ride.traffic.RideCollaborativeEtaEstimator
import com.elysium369.meet.ride.traffic.RideEtaEvidenceLevel
import com.elysium369.meet.ride.traffic.RideEtaSegment
import com.elysium369.meet.ride.notification.RideNotificationCoordinator
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.RideDispatchGateway
import com.elysium369.meet.ride.data.remote.RideDriverPerformance
import com.elysium369.meet.ride.data.remote.TrustedRideDriver
import com.elysium369.meet.ride.data.remote.RideWalletPolicy
import com.elysium369.meet.ride.data.remote.RideWalletBalance
import com.elysium369.meet.core.fleet.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideServiceScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenDriverRegistration: () -> Unit = {},
    onOpenMessages: (String?) -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToRideCenter: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hudNoticeMessage by remember { mutableStateOf<String?>(null) }
    var hudNoticeType by remember { mutableStateOf(ToastType.INFO) }

    LaunchedEffect(viewModel) {
        viewModel.rideVerificationNotice.collect { message ->
            hudNoticeType = when {
                message.contains("éxito", true) || message.contains("exitosamente", true) ||
                    message.contains("confirmado", true) || message.contains("verificado", true) ||
                    message.contains("guardada", true) -> ToastType.SUCCESS
                message.contains("error", true) || message.contains("falló", true) ||
                    message.contains("incorrecto", true) || message.contains("rechazó", true) -> ToastType.ERROR
                message.contains("aviso", true) || message.contains("atención", true) ||
                    message.contains("requiere", true) -> ToastType.WARNING
                else -> ToastType.INFO
            }
            hudNoticeMessage = message
        }
    }

    // Permissions check
    val permissionsToRequest = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            viewModel.detectCurrentLocation(context)
        } else {
            Toast.makeText(context, "Se requieren permisos de ubicación para la precisión GPS", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(permissionsToRequest)
        viewModel.detectCurrentLocation(context)
        viewModel.refreshOwnTrustDecisions()
        viewModel.startRideProjectionSync()
    }
    val driverMode by viewModel.rideDriverMode.collectAsState()
    val activeRide by viewModel.activeRideRequest.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()
    val projectionConnectionState by viewModel.rideProjectionConnectionState.collectAsState()
    val driverVerification by viewModel.driverVerification.collectAsState()
    val passengerVerification by viewModel.passengerVerification.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val myDriverId = viewModel.currentRideActorId.takeIf { it.isNotBlank() }
        ?: viewModel.currentUserId
    LaunchedEffect(driverMode, driverVerification?.status, myDriverId) {
        if (driverMode && RideVerificationPolicy.grantsAccess(driverVerification?.status)) {
            viewModel.ensureRideDriverPresence()
        }
    }
    val DRIVER_OPERATIONAL_STATES = remember {
        setOf(
            "ASSIGNED",
            "DRIVER_EN_ROUTE",
            "ARRIVED",
            "PASSENGER_ONBOARD",
            "IN_PROGRESS",
        )
    }
    val effectiveActiveRide = remember(activeRide, driverMode, allRides, myDriverId) {
        val selectedRide = activeRide
        when {
            selectedRide != null && selectedRide.serverVersion > 0L &&
                (selectedRide.serverState in DRIVER_OPERATIONAL_STATES || selectedRide.status in DRIVER_OPERATIONAL_STATES) -> selectedRide
            driverMode && myDriverId != null -> {
                allRides.firstOrNull { ride ->
                    ride.assignedDriverId == myDriverId &&
                        ride.serverVersion > 0L &&
                        (ride.serverState in DRIVER_OPERATIONAL_STATES || ride.status in DRIVER_OPERATIONAL_STATES)
                }
            }
            else -> null
        }
    }
    LaunchedEffect(driverMode, effectiveActiveRide?.requestId) {
        if (driverMode && activeRide == null && effectiveActiveRide != null) {
            viewModel.selectActiveRide(effectiveActiveRide)
        }
        val rId = effectiveActiveRide?.requestId
        if (rId != null) {
            viewModel.observeRideCalls(rId, if (driverMode) "DRIVER" else "PASSENGER")
        }
    }
    var showInRideChat by rememberSaveable { mutableStateOf(false) }
    var selectedChatRideId by rememberSaveable { mutableStateOf<String?>(null) }
    val passengerRegistrationMissing = passengerVerification == null
    val driverRegistrationMissing = driverVerification == null
    val voicePreferences = remember(context) {
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    }
    var rideVoiceEnabled by remember {
        mutableStateOf(voicePreferences.getBoolean("voice_feedback_enabled", true))
    }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var profileInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var showRideMenu by remember { mutableStateOf(false) }
    var firstAccessRole by rememberSaveable { mutableStateOf<String?>(null) }
    val presencePreferences = remember(context) {
        context.getSharedPreferences("elysium_ride_driver_presence", Context.MODE_PRIVATE)
    }
    val presencePrincipal by viewModel.activePrincipal.collectAsState()
    val presenceOwnerId = presencePrincipal.takeIf { it.isAuthenticated }?.id
    LaunchedEffect(presenceOwnerId) {
        if (presenceOwnerId != null) {
            // Authentication can change while this screen stays composed.
            // Restart the owner-scoped realtime channel and force an RLS catch-up.
            viewModel.startRideProjectionSync()
            viewModel.refreshRideProjectionNow()
        }
    }
    LaunchedEffect(presenceOwnerId) {
        if (presenceOwnerId == null) return@LaunchedEffect
        while (true) {
            runCatching { RideDispatchGateway.expireStaleRequests() }
                .onSuccess { viewModel.refreshRideProjectionNow() }
            delay(60_000L)
        }
    }
    LaunchedEffect(activeRide?.requestId, activeRide?.serverState, driverMode) {
        if (!driverMode && activeRide?.serverState == "EXPIRED") {
            viewModel.selectActiveRide(null)
        }
    }
    LaunchedEffect(
        presenceOwnerId,
        driverMode,
        activeRide?.requestId,
        activeRide?.serverState,
        rideVoiceEnabled,
    ) {
        if (presenceOwnerId == null || !rideVoiceEnabled) return@LaunchedEffect
        val state = activeRide?.serverState ?: activeRide?.status ?: "DASHBOARD"
        val guideKey = "${if (driverMode) "DRIVER" else "PASSENGER"}:$state:${activeRide?.requestId.orEmpty()}"
        val preferenceKey = "ride_voice_last_context_$presenceOwnerId"
        if (voicePreferences.getString(preferenceKey, null) == guideKey) return@LaunchedEffect
        val guidance = when {
            driverMode && activeRide == null -> "Estás en modo chofer. Revisa las solicitudes, envía una contraoferta o limpia las que no quieras atender. Usa el interruptor para volver a pasajero."
            !driverMode && activeRide == null -> "Estás en modo pasajero. Elige destino, tarifa y forma de pago; luego publica tu solicitud. Puedes silenciar esta guía con el botón del altavoz."
            driverMode && state in setOf("ASSIGNED", "DRIVER_EN_ROUTE") -> "Viaje asignado. Dirígete al punto de recogida y marca tu llegada solamente cuando estés allí."
            driverMode && state in setOf("ARRIVED", "PASSENGER_ONBOARD") -> "Confirma al pasajero con el código de abordaje antes de iniciar el viaje."
            driverMode && state == "IN_PROGRESS" -> "Viaje en curso. Sigue la ruta y completa el viaje únicamente al llegar al destino."
            !driverMode && state in setOf("SEARCHING", "OFFERED") -> "Tu solicitud está publicada. Puedes comparar ofertas, invitar a un chofer que ya conoces o cancelar el viaje."
            !driverMode && state in setOf("ASSIGNED", "DRIVER_EN_ROUTE") -> "Chofer asignado. Revisa su información y espera en el punto de recogida."
            !driverMode && state in setOf("ARRIVED", "PASSENGER_ONBOARD") -> "Tu chofer llegó. Confirma que el vehículo coincide y comparte el código de abordaje solamente con él."
            !driverMode && state == "COMPLETED" -> "Viaje completado. Califica el servicio y, si deseas dejar propina, entrégala en efectivo o por SINPE directamente al chofer."
            else -> null
        }
        guidance?.let {
            voicePreferences.edit { putString(preferenceKey, guideKey) }
            viewModel.voiceFeedbackManager.speak(es = it)
        }
    }
    val presenceKey = RideDriverPresencePolicy.storageKey(presenceOwnerId)
    var showLiveness by rememberSaveable(presenceOwnerId) { mutableStateOf(false) }

    val openPassengerRegistration: () -> Unit = {
        if (driverMode) viewModel.toggleRideDriverMode()
        showProfile = false
        firstAccessRole = "PASSENGER"
    }

    LaunchedEffect(driverMode, driverVerification?.status, presenceKey) {
        if (
            driverMode && presenceKey != null &&
            RideVerificationPolicy.grantsAccess(driverVerification?.status) &&
            RideDriverPresencePolicy.requiresChallenge(
                lastVerifiedAtEpochMs = presencePreferences
                    .getLong(presenceKey, 0L)
                    .takeIf { it > 0L },
                nowEpochMs = System.currentTimeMillis(),
            )
        ) {
            showLiveness = true
        }
    }

    if (showLiveness && presenceKey != null) {
        key(presenceOwnerId) {
            RideLivenessDialog(
                onVerified = { evidenceHash ->
                    val now = System.currentTimeMillis()
                    if (viewModel.activePrincipal.value.id != presenceOwnerId) return@RideLivenessDialog
                    presencePreferences.edit { putLong(presenceKey, now) }
                    viewModel.recordDriverLiveness(evidenceHash, now)
                    showLiveness = false
                },
                onCancel = {
                    showLiveness = false
                    if (driverMode) viewModel.toggleRideDriverMode()
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VIAJES",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showRideMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú de Viajes",
                                tint = MeetColors.cyberCyan,
                            )
                        }
                        DropdownMenu(
                            expanded = showRideMenu,
                            onDismissRequest = { showRideMenu = false },
                            containerColor = Color(0xFF07131E),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (passengerVerification == null) {
                                            "Registrarme como usuario"
                                        } else {
                                            "Cuenta de pasajero"
                                        },
                                        color = Color.White,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = MeetColors.neonGreen,
                                    )
                                },
                                onClick = {
                                    showRideMenu = false
                                    if (passengerVerification == null) {
                                        openPassengerRegistration()
                                    } else {
                                        if (driverMode) viewModel.toggleRideDriverMode()
                                        profileInitialTab = 0
                                        showProfile = true
                                        firstAccessRole = null
                                    }
                                },
                            )
                            listOf(
                                Triple(Icons.Default.Person, "Perfil", 0),
                                Triple(Icons.Default.SupportAgent, "Soporte", 1),
                                Triple(Icons.Default.LocationOn, "Iconos del mapa", 2),
                            ).forEach { (icon, label, destinationTab) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = Color.White) },
                                    leadingIcon = { Icon(icon, null, tint = MeetColors.cyberCyan) },
                                    onClick = {
                                        profileInitialTab = destinationTab
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                            }
                            if (driverMode) {
                                HorizontalDivider(color = MeetColors.borderSubtle)
                                DropdownMenuItem(
                                    text = { Text("Autos y flotillas", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = MeetColors.neonGreen) },
                                    onClick = {
                                        profileInitialTab = 0
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ganancias", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Payments, null, tint = MeetColors.neonGreen) },
                                    onClick = {
                                        profileInitialTab = 0
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                            }
                            HorizontalDivider(color = MeetColors.borderSubtle)
                            DropdownMenuItem(
                                text = { Text("Viajes Programados", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Schedule, null, tint = MeetColors.cyberCyan) },
                                onClick = {
                                    showRideMenu = false
                                    onNavigateToSchedule()
                                },
                            )
                            HorizontalDivider(color = MeetColors.borderSubtle)
                            DropdownMenuItem(
                                text = { Text("Volver a PRO", color = MeetColors.textMuted) },
                                leadingIcon = { Icon(Icons.Default.ArrowBack, null) },
                                onClick = {
                                    showRideMenu = false
                                    onNavigateBack()
                                },
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        if (!driverMode) {
                            IconButton(onClick = { onOpenMessages(activeRide?.requestId) }) {
                                Icon(Icons.Default.Chat, "Mensajes", tint = MeetColors.cyberCyan)
                            }
                        }
                        IconButton(onClick = {
                            rideVoiceEnabled = !rideVoiceEnabled
                            viewModel.voiceFeedbackManager.setEnabled(rideVoiceEnabled)
                            if (rideVoiceEnabled) {
                                viewModel.voiceFeedbackManager.speak(
                                    es = "Guía de voz activada.",
                                    en = "Voice guidance enabled.",
                                )
                            } else {
                                Toast.makeText(context, "Guía de voz silenciada", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                if (rideVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                if (rideVoiceEnabled) "Silenciar guía de voz" else "Activar guía de voz",
                                tint = if (rideVoiceEnabled) MeetColors.neonGreen else MeetColors.textMuted,
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                text = projectionConnectionState.rideProjectionStatusLabel(),
                                color = projectionConnectionState.rideProjectionStatusColor(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (driverMode) "CHOFER" else "PASAJERO",
                                color = if (driverMode) MeetColors.cyberCyan else MeetColors.neonGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val activeCanonicalState = effectiveActiveRide?.serverState ?: effectiveActiveRide?.status
                        val roleLockedByTrip = activeCanonicalState in setOf(
                            "ASSIGNED",
                            "DRIVER_EN_ROUTE",
                            "ARRIVED",
                            "PASSENGER_ONBOARD",
                            "IN_PROGRESS",
                        )
                        Switch(
                            checked = driverMode,
                            enabled = !roleLockedByTrip,
                            onCheckedChange = { checked ->
                                if (!roleLockedByTrip && checked != driverMode) {
                                    viewModel.toggleRideDriverMode()
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (driverMode) {
                                    "Cambiar a modo pasajero"
                                } else {
                                    "Cambiar a modo chofer"
                                }
                                stateDescription = if (driverMode) "Chofer activo" else "Pasajero activo"
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeetColors.cyberCyan,
                                checkedTrackColor = MeetColors.cyberCyan.copy(alpha = 0.5f),
                                uncheckedThumbColor = MeetColors.neonGreen,
                                uncheckedTrackColor = MeetColors.neonGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDark
                )
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val effectivePassengerExists = !passengerRegistrationMissing ||
                RideVerificationPolicy.grantsAccess(driverVerification?.status)
            val effectiveDriverExists = !driverRegistrationMissing

            if (requiresRideRoleRegistration(
                    driverMode = driverMode,
                    passengerRegistrationExists = effectivePassengerExists,
                    driverRegistrationExists = effectiveDriverExists,
                ) && firstAccessRole == null
            ) {
                RideFirstAccessGateway(
                    onPassengerRegistration = openPassengerRegistration,
                    onDriverRegistration = onOpenDriverRegistration,
                )
            } else if (showProfile) {
                RideProfileScreen(
                    viewModel = viewModel,
                    isDriver = driverMode,
                    initialTab = profileInitialTab,
                    onBack = { showProfile = false },
                    onOpenChat = { ride ->
                        selectedChatRideId = ride.requestId
                        viewModel.observeRideChatForRequestId(ride.requestId)
                        showProfile = false
                        showInRideChat = true
                    },
                )
            } else if (driverMode && effectiveActiveRide != null) {
                com.elysium369.meet.ride.driver.ui.DriverActiveTripCockpitRoute(
                    rideId = effectiveActiveRide.requestId,
                    initialProjection = effectiveActiveRide,
                    onOpenMessages = { showInRideChat = true },
                    onTripDismissed = { viewModel.selectActiveRide(null) },
                    currentLatitude = currentGps?.latitude,
                    currentLongitude = currentGps?.longitude,
                    currentAccuracy = currentGps?.accuracy,
                )
            } else if (!driverMode && effectiveActiveRide != null) {
                ActiveRidePanel(
                    viewModel = viewModel,
                    ride = effectiveActiveRide,
                    isDriver = false,
                    onCloseRide = { viewModel.selectActiveRide(null) },
                    onOpenMessages = { showInRideChat = true },
                )
            } else {
                if (driverMode) {
                    DriverDashboard(
                        viewModel = viewModel,
                        onRegisterDriver = onOpenDriverRegistration,
                        onOpenRideCenter = onNavigateToRideCenter,
                        onOpenMessages = { rideId ->
                            selectedChatRideId = rideId
                            viewModel.observeRideChatForRequestId(rideId)
                            showInRideChat = true
                        },
                    )
                } else {
                    PassengerDashboard(
                        viewModel = viewModel,
                        forceRegistration = firstAccessRole == "PASSENGER",
                        onOpenMessages = { rideId ->
                            selectedChatRideId = rideId
                            viewModel.observeRideChatForRequestId(rideId)
                            showInRideChat = true
                        },
                    )
                }
            }

            // ═══ 3D CINEMATIC HUD NOTICE BAR ═══
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                ElysiumToastBar(
                    message = hudNoticeMessage.orEmpty(),
                    type = hudNoticeType,
                    visible = hudNoticeMessage != null,
                    onDismiss = { hudNoticeMessage = null },
                )
            }

            // ═══ ELYSIUM MUTUAL RATING DIALOG ═══
            val pendingRatingRide by viewModel.pendingRatingRide.collectAsState()
            if (pendingRatingRide != null) {
                val rideToRate = pendingRatingRide!!
                val isDriverRating = driverMode || rideToRate.assignedDriverId == myDriverId || rideToRate.assignedDriverId == viewModel.currentUserId
                var ratingStars by remember(rideToRate.requestId) { mutableDoubleStateOf(5.0) }
                var ratingComment by remember(rideToRate.requestId) { mutableStateOf("") }
                var selectedTag by remember(rideToRate.requestId) { mutableStateOf<String?>(null) }
                val haptic = LocalHapticFeedback.current

                val tags = if (isDriverRating) {
                    listOf("Pasajero puntual", "Amable y respetuoso", "Excelente comunicación", "Recomendado 100%")
                } else {
                    listOf("Conducción excelente", "Vehículo impecable", "Puntual", "Trato amable", "Ruta óptima")
                }

                ElysiumAnimatedDialog(
                    visible = true,
                    onDismiss = { viewModel.dismissPendingRating() },
                    title = {
                        Text(
                            text = "¡VIAJE COMPLETADO! 🎉",
                            fontWeight = FontWeight.Black,
                            color = MeetColors.neonGreen,
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (isDriverRating) {
                                    "¿Cómo fue tu experiencia con el pasajero ${rideToRate.passengerName}?"
                                } else {
                                    "¿Cómo fue tu servicio con ${rideToRate.assignedDriverName ?: "el conductor"}?"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )

                            // 5-star interactive rating with glow
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (i in 1..5) {
                                    val active = ratingStars >= i.toDouble()
                                    IconButton(
                                        onClick = {
                                            ratingStars = i.toDouble()
                                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (active) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "$i estrellas",
                                            tint = if (active) Color(0xFFFFD700) else MeetColors.textMuted,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }

                            // Quick rating chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(tags) { tag ->
                                    val isSelected = selectedTag == tag
                                    Surface(
                                        color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.25f) else Color(0xFF142438),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable {
                                            selectedTag = if (isSelected) null else tag
                                            if (!isSelected) {
                                                ratingComment = if (ratingComment.isBlank()) tag else "$ratingComment · $tag"
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = tag,
                                            color = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = ratingComment,
                                onValueChange = { ratingComment = it.take(300) },
                                placeholder = { Text("Deja un comentario opcional...", color = MeetColors.textMuted, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.neonGreen,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.submitRideRating(
                                    requestId = rideToRate.requestId,
                                    isPassengerRating = !isDriverRating,
                                    stars = ratingStars,
                                    comment = ratingComment,
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("CALIFICAR", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissPendingRating() }) {
                            Text("OMITIR", color = MeetColors.textMuted, fontSize = 12.sp)
                        }
                    },
                )
            }

            // ═══ UNIFIED IN-RIDE REALTIME CHAT SHEET ═══
            val chatTargetRide = if (selectedChatRideId != null) {
                allRides.firstOrNull { it.requestId == selectedChatRideId }
            } else {
                effectiveActiveRide ?: activeRide
            }
            if (showInRideChat && chatTargetRide != null) {
                val role = if (driverMode) "DRIVER" else "PASSENGER"
                val myPassengerId = passengerVerification?.passengerId ?: viewModel.currentUserId
                val myId = if (driverMode) (myDriverId ?: "driver_me") else (myPassengerId ?: "passenger_me")
                val myName = if (driverMode) "Chofer" else (passengerVerification?.fullName ?: "Pasajero")
                val chatMessages by viewModel.rideChatMessages.collectAsState()
                val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
                val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()

                com.elysium369.meet.ui.screens.ride.RideInRideChatSheet(
                    rideRequestId = chatTargetRide.requestId,
                    myId = myId,
                    myName = myName,
                    myRole = role,
                    isDriver = driverMode,
                    chatMessages = chatMessages,
                    onDismiss = { showInRideChat = false; selectedChatRideId = null },
                    onSendMessage = { text ->
                        viewModel.sendRideChatMessage(chatTargetRide.requestId, myId, myName, role, text)
                    },
                    onSendPreset = { preset ->
                        viewModel.sendRidePresetMessage(chatTargetRide.requestId, myId, myName, role, preset)
                    },
                    onSendVoiceNote = { _, _ -> },
                    onSendImage = { bytes ->
                        viewModel.sendRideImageBytes(context, chatTargetRide.requestId, myId, myName, role, bytes)
                    },
                    playingAudioPath = isPlayingAudio,
                    onPlayAudio = { path -> viewModel.playAudioMessage(path) },
                    isRecordingAudio = isRecordingAudio,
                    onStartRecording = {
                        viewModel.startAudioRecording(context)
                    },
                    onStopRecording = {
                        viewModel.stopAndSendAudioRecording(chatTargetRide.requestId, myId, myName, role)
                    },
                )
            }
        }
    }

}

@Composable
private fun DriverWalletCard(
    policy: RideWalletPolicy?,
    balance: RideWalletBalance?,
    topups: List<com.elysium369.meet.ride.data.remote.RideWalletTopup>,
    message: String?,
    onRecharge: () -> Unit,
) {
    val commission = policy?.commissionBasisPoints?.toDouble()?.div(100)
    val availableMinor = balance?.availableMinor ?: 0L
    val canRequestLoan = availableMinor <= 400L
    val walletState = remember(availableMinor) {
        runCatching { com.elysium369.meet.core.wallet.SpecialistWalletStore }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF040C18)),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(MeetColors.cyberCyan, Color(0xFF7B2FFF), MeetColors.neonGreen))
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── HEADER ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡", fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text("BILLETERA OPERATIVA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                    Text("Elysium Vanguard AI OS", color = Color(0xFF7B2FFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Surface(
                    color = if (canRequestLoan) MeetColors.neonGreen.copy(alpha = 0.15f) else Color(0xFF1A2A3A),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (canRequestLoan) MeetColors.neonGreen else MeetColors.borderSubtle)
                ) {
                    Text(
                        if (canRequestLoan) "CRÉDITO DISPONIBLE" else "SALDO OK",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = if (canRequestLoan) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // ── SALDO PANEL ──
            Surface(
                color = Color(0xFF071527),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SALDO DISPONIBLE", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(
                            balance?.availableMinor?.let { CoreMoney.ofCrc(it).formatted() } ?: "···",
                            color = if (canRequestLoan) MeetColors.warning else MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("RESERVADO", color = MeetColors.textSecondary, fontSize = 9.sp, letterSpacing = 0.5.sp)
                        Text(
                            balance?.reservedMinor?.let { CoreMoney.ofCrc(it).formatted() } ?: "₡0",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        commission?.let {
                            Text("Comisión: ${"%.1f".format(java.util.Locale.US, it)}%", color = MeetColors.textMuted, fontSize = 9.sp)
                        }
                    }
                }
            }

            // ── BOTÓN DE CRÉDITO (UN SOLO CLICK) ──
            Button(
                onClick = onRecharge,
                enabled = canRequestLoan,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canRequestLoan) MeetColors.neonGreen else Color(0xFF1A2A3A),
                    contentColor = if (canRequestLoan) Color.Black else MeetColors.textMuted,
                    disabledContainerColor = Color(0xFF1A2A3A),
                    disabledContentColor = MeetColors.textMuted,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (canRequestLoan) "⚡ SOLICITAR CRÉDITO ₡3,000" else "✓ SALDO SUFICIENTE (> ₡400)",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                )
            }

            // ── REGLAS COMPACTAS ──
            Text(
                "Crédito fijo: ₡3,000 por solicitud · Disponible cuando saldo ≤ ₡400 · Sin límite de deuda · Liquidación al activar cuenta empresarial",
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                lineHeight = 11.sp,
            )

            // ── HISTORIAL ──
            topups.take(3).forEach { topup ->
                val status = when (topup.status) {
                    "PENDING_REVIEW" -> "⏳ PENDIENTE"
                    "APPROVED" -> "✓ ACREDITADA"
                    "REJECTED" -> "✗ RECHAZADA"
                    else -> topup.status
                }
                Text(
                    "${CoreMoney.ofCrc(topup.amountMinor).formatted()} · $status",
                    color = when (topup.status) {
                        "APPROVED" -> MeetColors.neonGreen
                        "REJECTED" -> MeetColors.error
                        else -> MeetColors.warning
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            message?.let { Text(it, color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}


@Composable
private fun RideFirstAccessGateway(
    onPassengerRegistration: () -> Unit,
    onDriverRegistration: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030712), Color(0xFF071527), Color(0xFF09051A)),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 600.dp || maxWidth < 360.dp
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xD90A1726)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .58f)),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = if (compact) 16.dp else 22.dp,
                        vertical = if (compact) 16.dp else 26.dp,
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 60.dp else 82.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(MeetColors.cyberCyan.copy(alpha = .42f), Color.Transparent),
                            ),
                            CircleShape,
                        )
                        .border(1.dp, MeetColors.neonGreen.copy(alpha = .65f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(if (compact) 31.dp else 42.dp),
                    )
                }
                Text(
                    "ACTIVA TU CUENTA DE VIAJES",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 17.sp else 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Antes de mostrar el mapa necesitamos saber cómo usarás Elysium Vanguard AI OS. El registro protege viajes, pagos y soporte.",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onPassengerRegistration,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = Color(0xFF02100B),
                    ),
                ) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(9.dp))
                    Text("REGISTRARME PARA VIAJAR", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = onDriverRegistration,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MeetColors.cyberCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                ) {
                    Icon(Icons.Default.DirectionsCar, null)
                    Spacer(Modifier.width(9.dp))
                    Text("REGISTRARME COMO CHOFER", fontWeight = FontWeight.Black)
                }
                Text(
                    "Puedes tener ambos perfiles en la misma cuenta. La verificación de usuario y la documentación de chofer se administran por separado.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun RideProjectionConnectionState.rideProjectionStatusLabel(): String =
    when (this) {
        RideProjectionConnectionState.IDLE -> "LOCAL"
        RideProjectionConnectionState.CONNECTING -> "CONECTANDO"
        RideProjectionConnectionState.LIVE -> "EN VIVO"
        RideProjectionConnectionState.RECOVERING -> "RECUPERANDO"
        RideProjectionConnectionState.AUTHENTICATION_REQUIRED -> "SIN SESIÓN"
    }

internal fun requiresRideRoleRegistration(
    driverMode: Boolean,
    passengerRegistrationExists: Boolean,
    driverRegistrationExists: Boolean,
): Boolean = if (driverMode) {
    !driverRegistrationExists
} else {
    !passengerRegistrationExists
}

private fun RideProjectionConnectionState.rideProjectionStatusColor(): Color =
    when (this) {
        RideProjectionConnectionState.LIVE -> MeetColors.neonGreen
        RideProjectionConnectionState.CONNECTING,
        RideProjectionConnectionState.RECOVERING -> MeetColors.warning
        RideProjectionConnectionState.IDLE,
        RideProjectionConnectionState.AUTHENTICATION_REQUIRED -> MeetColors.textMuted
    }

private fun rideGeoPointOrNull(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    capturedAtEpochMs: Long,
): RideGeoPoint? =
    runCatching {
        RideGeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            capturedAtEpochMs = capturedAtEpochMs.coerceAtLeast(0L),
        )
    }.getOrNull()

private fun RideRequestEntity.truthfulPassengerStatus(): String = when {
    syncState == "PENDING" || serverVersion <= 0L -> "Confirmando publicación"
    serverState == "SEARCHING" || status == "OPEN" -> "Buscando chofer"
    else -> status
}

private enum class RidePinTarget { PICKUP, DESTINATION }

@Composable
fun PassengerDashboard(
    viewModel: ObdViewModel,
    forceRegistration: Boolean = false,
    onOpenMessages: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val currentLocale = rememberRideJavaLocale()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()
    val draftPrincipal by viewModel.activePrincipal.collectAsState()
    val draftOwner = remember(draftPrincipal) { viewModel.currentUserId }
    val draftPreferences = remember(context, draftOwner) {
        val owner = draftOwner?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "signed_out"
        context.getSharedPreferences("elysium_ride_draft_$owner", Context.MODE_PRIVATE)
    }

    var destAddress by rememberSaveable(draftOwner) {
        mutableStateOf(draftPreferences.getString("dest_address", "").orEmpty())
    }
    var destLatitude by rememberSaveable(draftOwner) {
        mutableDoubleStateOf(draftPreferences.getString("dest_lat", null)?.toDoubleOrNull() ?: 0.0)
    }
    var destLongitude by rememberSaveable(draftOwner) {
        mutableDoubleStateOf(draftPreferences.getString("dest_lng", null)?.toDoubleOrNull() ?: 0.0)
    }
    var destinationPlaceId by rememberSaveable(draftOwner) {
        mutableStateOf(draftPreferences.getString("dest_place_id", null))
    }
    var destinationSuggestions by remember { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    var destinationSearchLoading by remember { mutableStateOf(false) }
    var destinationSearchFailed by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<RidePinTarget?>(null) }
    var pickupAddress by rememberSaveable(draftOwner) {
        mutableStateOf(draftPreferences.getString("pickup_address", "").orEmpty())
    }
    var pickupPlaceId by rememberSaveable(draftOwner) {
        mutableStateOf(draftPreferences.getString("pickup_place_id", null))
    }
    var pickupSuggestions by remember { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    var pickupSearchLoading by remember { mutableStateOf(false) }
    var pickupSearchFailed by remember { mutableStateOf(false) }
    var pickupPin by remember { mutableStateOf<RideGeoPoint?>(null) }
    var pendingMapPin by remember { mutableStateOf<RideGeoPoint?>(null) }
    var stops by remember { mutableStateOf(emptyList<RideStopSnapshot>()) }
    var paymentMethod by rememberSaveable(draftOwner) {
        mutableStateOf(
            runCatching {
                RidePaymentMethod.valueOf(
                    draftPreferences.getString("payment_method", RidePaymentMethod.UNKNOWN.name)
                        ?: RidePaymentMethod.UNKNOWN.name,
                )
            }.getOrDefault(RidePaymentMethod.UNKNOWN),
        )
    }
    val placeSearchProvider = remember {
        resilientRidePlaceSearchProvider(
            primaryEndpoint = BuildConfig.RIDE_GEOCODER_URL,
            fallbackEndpoint = BuildConfig.RIDE_GEOCODER_FALLBACK_URL,
        )
    }
    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }
    var previewRoadRoute by remember { mutableStateOf<RideRoadRoute?>(null) }
    var routeSearchLoading by remember { mutableStateOf(false) }
    var routeSearchFailed by remember { mutableStateOf(false) }
    val savedPlacesPrincipal by viewModel.activePrincipal.collectAsState()
    val savedPlacesOwner = remember(savedPlacesPrincipal) { viewModel.currentUserId }
    var cancellationTarget by remember(savedPlacesOwner) { mutableStateOf<String?>(null) }
    cancellationTarget?.let { requestId ->
        AuthoritativeRideCancellationDialog(viewModel, requestId, RideActorRole.PASSENGER) { cancellationTarget = null }
    }
    val savedPlacesStore = remember(context) { RideSavedPlacesStore(context) }
    var savedPlaces by remember(savedPlacesOwner) { mutableStateOf(savedPlacesStore.load(savedPlacesOwner)) }

    var offerPrice by rememberSaveable(draftOwner) {
        mutableDoubleStateOf(draftPreferences.getString("offer_price", null)?.toDoubleOrNull() ?: 2_400.0)
    }
    var isUsd by rememberSaveable(draftOwner) { mutableStateOf(draftPreferences.getBoolean("is_usd", false)) }
    var fareMode by rememberSaveable(draftOwner) {
        mutableStateOf(
            runCatching {
                RideFareMode.valueOf(
                    draftPreferences.getString("fare_mode", RideFareMode.OPEN_BID.name)
                        ?: RideFareMode.OPEN_BID.name,
                )
            }.getOrDefault(RideFareMode.OPEN_BID),
        )
    }
    var requestingForSomeoneElse by rememberSaveable(draftOwner) { mutableStateOf(false) }
    var guestName by rememberSaveable(draftOwner) { mutableStateOf("") }
    var guestPhoneE164 by rememberSaveable(draftOwner) { mutableStateOf("") }
    var passengerPreferences by remember(draftOwner) { mutableStateOf(RidePassengerPreferences()) }

    LaunchedEffect(
        destAddress, destLatitude, destLongitude, destinationPlaceId,
        pickupAddress, pickupPlaceId,
        paymentMethod, offerPrice, isUsd, fareMode,
    ) {
        draftPreferences.edit {
            putString("dest_address", destAddress)
            putString("dest_lat", destLatitude.toString())
            putString("dest_lng", destLongitude.toString())
            if (destinationPlaceId == null) remove("dest_place_id")
            else putString("dest_place_id", destinationPlaceId)
            putString("pickup_address", pickupAddress)
            if (pickupPlaceId == null) remove("pickup_place_id")
            else putString("pickup_place_id", pickupPlaceId)
            putString("payment_method", paymentMethod.name)
            putString("offer_price", offerPrice.toString())
            putBoolean("is_usd", isUsd)
            putString("fare_mode", fareMode.name)
        }
    }

    // Passenger verification state
    val passengerVer by viewModel.passengerVerification.collectAsState()
    val activeRide by viewModel.activeRideRequest.collectAsState()

    val myPassengerIds = remember(passengerVer?.passengerId, viewModel.currentUserId, viewModel.currentRideActorId) {
        setOfNotNull(passengerVer?.passengerId, viewModel.currentUserId, viewModel.currentRideActorId)
    }

    val userRides = remember(allRides, myPassengerIds) {
        if (myPassengerIds.isEmpty()) emptyList()
        else allRides.filter { it.passengerId in myPassengerIds }
    }

    val activeRideForPassenger: RideRequestEntity? = remember(allRides, userRides, myPassengerIds, activeRide) {
        val candidate = activeRide?.takeIf {
            it.passengerId in myPassengerIds &&
                it.status in listOf(
                    "PENDING_PUBLICATION", "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED",
                    "PASSENGER_ONBOARD", "IN_PROGRESS",
                )
        }
        if (candidate != null) return@remember candidate

        val active = userRides.filter {
            it.status in listOf(
                "PENDING_PUBLICATION", "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED",
                "PASSENGER_ONBOARD", "IN_PROGRESS",
            )
        }
        if (active.size > 1) {
            android.util.Log.e("ElysiumRides", "CONSISTENCY_VIOLATION: ${active.size} active rides for passenger $myPassengerIds — using most recent")
        }
        active.maxByOrNull { it.createdAt }
    }

    var passengerModeTab by rememberSaveable { mutableIntStateOf(if (activeRideForPassenger != null) 1 else 0) }

    LaunchedEffect(activeRideForPassenger?.requestId, activeRideForPassenger?.status) {
        if (activeRideForPassenger != null && activeRideForPassenger.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")) {
            passengerModeTab = 1
        }
    }
    val latestExpiredRide = remember(userRides, draftPreferences) {
        val waitingRideId = draftPreferences.getString("passenger_actively_waiting_ride_id", null)
        if (waitingRideId.isNullOrBlank()) return@remember null

        val now = System.currentTimeMillis()
        userRides
            .firstOrNull { it.requestId == waitingRideId }
            ?.takeIf { ride ->
                val hasElapsed30Min = (now - ride.createdAt) >= RideDispatchExpiryPolicy.LEASE_MILLIS ||
                    ride.status == "EXPIRED" || ride.serverState == "EXPIRED"
                val noDriverResponse = ride.assignedDriverId.isNullOrBlank()
                val isDismissed = draftPreferences.getBoolean("dismissed_expired_${ride.requestId}", false)
                hasElapsed30Min && noDriverResponse && !isDismissed
            }
    }
    val expiredRepriceMinor = remember(latestExpiredRide) {
        latestExpiredRide?.let { expired ->
            val currentMinor = expired.priceOfferMinor.takeIf { it > 0L }
                ?: if (expired.currency == "CRC") expired.priceOffer.toLong() else (expired.priceOffer * 100.0).toLong()
            RideDispatchExpiryPolicy.recommendedOpenBidMinor(currentMinor, expired.currency)
        }
    }

    LaunchedEffect(latestExpiredRide?.requestId) {
        val expired = latestExpiredRide ?: return@LaunchedEffect
        val announcedKey = "expired_voice_${expired.requestId}"
        if (!draftPreferences.getBoolean(announcedKey, false)) {
            draftPreferences.edit { putBoolean(announcedKey, true) }
            viewModel.voiceFeedbackManager.speak(
                es = "Tu solicitud venció después de treinta minutos. Vuelve a pedir por tiempo y distancia, o usa Pon tu precio. Te recomendamos subir la oferta para aumentar la probabilidad de aceptación.",
                en = "Your request expired after thirty minutes. Request again by time and distance, or raise your offer with Name your price.",
            )
        }
    }

    LaunchedEffect(userRides) {
        if (draftPreferences.getBoolean("passenger_is_waiting_for_drivers", false)) {
            val submittedAt = draftPreferences.getLong("passenger_request_submitted_at", 0L)
            val newest = userRides
                .filter { it.assignedDriverId.isNullOrBlank() && it.status in listOf("PENDING_PUBLICATION", "OPEN", "EXPIRED") }
                .maxByOrNull { it.createdAt }
            if (newest != null && (newest.createdAt >= submittedAt - 30_000L)) {
                draftPreferences.edit {
                    putString("passenger_actively_waiting_ride_id", newest.requestId)
                }
            }
        }
    }

    LaunchedEffect(activeRideForPassenger?.assignedDriverId) {
        if (!activeRideForPassenger?.assignedDriverId.isNullOrBlank()) {
            draftPreferences.edit {
                remove("passenger_actively_waiting_ride_id")
                putBoolean("passenger_is_waiting_for_drivers", false)
            }
        }
    }

    LaunchedEffect(userRides) {
        val unratedCompleted = userRides.firstOrNull { ride ->
            (ride.status == "COMPLETED" || ride.serverState == "COMPLETED") &&
                ride.passengerRating == null &&
                !viewModel.isRatingDismissed(ride.requestId)
        }
        if (unratedCompleted != null) {
            viewModel.promptRideRating(unratedCompleted)
        }
    }

    var dismissedExpiredRideId by rememberSaveable(draftOwner) { mutableStateOf<String?>(null) }
    var showPaxVerification by rememberSaveable(draftOwner) { mutableStateOf(false) }
    var paxName by rememberSaveable(draftOwner) { mutableStateOf("") }
    var paxPhone by rememberSaveable(draftOwner) { mutableStateOf("") }
    var paxProfilePhoto by rememberSaveable(draftOwner) { mutableStateOf("") }
    var paxCedulaFront by rememberSaveable(draftOwner) { mutableStateOf("") }
    var paxSelfieWithCedula by rememberSaveable(draftOwner) { mutableStateOf("") }

    LaunchedEffect(forceRegistration, passengerVer) {
        if (forceRegistration && passengerVer == null) {
            showPaxVerification = true
        }
    }

    LaunchedEffect(destAddress, destinationPlaceId, currentGps) {
        if (destinationPlaceId != null || destAddress.trim().length < 3) {
            destinationSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        destinationSearchLoading = true
        destinationSearchFailed = false
        val searchResult = runCatching {
            placeSearchProvider.search(
                query = destAddress,
                biasLatitude = currentGps?.latitude,
                biasLongitude = currentGps?.longitude,
            )
        }
        destinationSuggestions = searchResult.getOrDefault(emptyList())
            .sortedBy { it.distanceKmFrom(currentGps?.latitude, currentGps?.longitude) ?: Double.MAX_VALUE }
        destinationSearchFailed = searchResult.isFailure
        destinationSearchLoading = false
    }

    LaunchedEffect(pickupAddress, pickupPlaceId, currentGps) {
        if (pickupPlaceId != null || pickupAddress.trim().length < 3) {
            pickupSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        pickupSearchLoading = true
        pickupSearchFailed = false
        val searchResult = runCatching {
            placeSearchProvider.search(
                query = pickupAddress,
                biasLatitude = currentGps?.latitude,
                biasLongitude = currentGps?.longitude,
            )
        }
        pickupSuggestions = searchResult.getOrDefault(emptyList())
            .sortedBy { it.distanceKmFrom(currentGps?.latitude, currentGps?.longitude) ?: Double.MAX_VALUE }
        pickupSearchFailed = searchResult.isFailure
        pickupSearchLoading = false
    }

    LaunchedEffect(
        currentGps?.latitude,
        currentGps?.longitude,
        pickupPin,
        pickupAddress,
        pickupPlaceId,
        destinationPlaceId,
        destLatitude,
        destLongitude,
        stops,
    ) {
        val gps = currentGps
        val resolvedStops = stops.mapNotNull { stop ->
            if (!stop.isResolved) null else rideGeoPointOrNull(
                latitude = requireNotNull(stop.latitude),
                longitude = requireNotNull(stop.longitude),
                accuracyMeters = null,
                capturedAtEpochMs = System.currentTimeMillis(),
            )
        }
        if (
            (gps == null && pickupPin == null) ||
            destinationPlaceId == null ||
            (destLatitude == 0.0 && destLongitude == 0.0) ||
            resolvedStops.size != stops.size
        ) {
            previewRoadRoute = null
            routeSearchLoading = false
            routeSearchFailed = false
            return@LaunchedEffect
        }
        val pickup = pickupPin ?: gps?.let {
            rideGeoPointOrNull(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracy,
                capturedAtEpochMs = it.timestamp.coerceAtLeast(0L),
            )
        }
        val destination = rideGeoPointOrNull(
            latitude = destLatitude,
            longitude = destLongitude,
            accuracyMeters = null,
            capturedAtEpochMs = System.currentTimeMillis(),
        )
        if (pickup == null || destination == null) return@LaunchedEffect
        routeSearchLoading = true
        routeSearchFailed = false
        val result = runCatching {
            routingProvider.route(listOf(pickup) + resolvedStops + destination)
        }
        previewRoadRoute = result.getOrNull()
        routeSearchFailed = result.isFailure
        routeSearchLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = passengerModeTab,
                containerColor = MeetColors.cardBackground,
                contentColor = MeetColors.cyberCyan,
                indicator = { tabPositions ->
                    val indicatorColor = when (passengerModeTab) {
                        1 -> MeetColors.neonGreen
                        else -> MeetColors.cyberCyan
                    }
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[passengerModeTab]),
                        color = indicatorColor
                    )
                }
            ) {
                Tab(
                    selected = passengerModeTab == 0,
                    onClick = { passengerModeTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📍", fontSize = 12.sp)
                            Text(
                                "PEDIR VIAJE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (passengerModeTab == 0) MeetColors.cyberCyan else MeetColors.textSecondary
                            )
                        }
                    }
                )
                Tab(
                    selected = passengerModeTab == 1,
                    onClick = { passengerModeTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activeRideForPassenger != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.neonGreen)
                                )
                            }
                            Text(
                                "VIAJES ACTIVOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (passengerModeTab == 1) MeetColors.neonGreen else if (activeRideForPassenger != null) Color.White else MeetColors.textSecondary
                            )
                            val activeCount = userRides.count {
                                it.status in listOf("PENDING_PUBLICATION", "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")
                            }
                            if (activeCount > 0) {
                                Surface(
                                    color = if (passengerModeTab == 1) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.cardBackground,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        "$activeCount",
                                        color = if (passengerModeTab == 1) MeetColors.neonGreen else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = passengerModeTab == 2,
                    onClick = { passengerModeTab = 2; viewModel.refreshRideProjectionNow() },
                    text = { Text("FINALIZADOS", fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        color = if (passengerModeTab == 2) MeetColors.neonGreen else MeetColors.textSecondary) },
                )
            }

            when (passengerModeTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
        // ── Identity Verification Gate ────────────────────────────────────
        val passengerAccessGranted = RideVerificationPolicy.grantsAccess(passengerVer?.status) ||
            RideVerificationPolicy.grantsAccess(viewModel.driverVerification.value?.status)
        if (!passengerAccessGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.5.dp, MeetColors.warning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🛡️", fontSize = 48.sp)
                        Text(
                            "VERIFICACIÓN DE IDENTIDAD REQUERIDA",
                            color = MeetColors.warning,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Para tu seguridad y la del conductor, necesitamos verificar tu identidad antes de que puedas solicitar viajes.",
                            color = MeetColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        when (passengerVer?.status) {
                            null -> {
                                Button(
                                    onClick = { showPaxVerification = true },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MeetColors.electricBlue,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🔐 VERIFICAR MI IDENTIDAD", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                            "PENDING", RideVerificationPolicy.PILOT_APPROVED -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.warning.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("⏳ Tu verificación está siendo revisada...", color = MeetColors.warning, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Esto puede tardar unas horas.", color = MeetColors.textMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                            "REJECTED" -> {
                                Text(
                                    passengerVer?.rejectionReason ?: "Verificación rechazada. Intenta de nuevo.",
                                    color = Color(0xFFEF5350),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.deletePassengerVerification() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF5350),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🔄 ELIMINAR Y REINTENTAR", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
            // Don't show ride request form if not verified
        } else {
            if (latestExpiredRide != null && latestExpiredRide.requestId != dismissedExpiredRideId && !draftPreferences.getBoolean("dismissed_expired_${latestExpiredRide.requestId}", false) && activeRideForPassenger == null) {
                item(key = "expired-${latestExpiredRide.requestId}") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MeetColors.warning.copy(alpha = 0.12f)),
                        border = BorderStroke(1.5.dp, MeetColors.warning),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⏱️ LA SOLICITUD VENCIÓ", color = MeetColors.warning, fontWeight = FontWeight.Black)
                                IconButton(
                                    onClick = {
                                        dismissedExpiredRideId = latestExpiredRide.requestId
                                        draftPreferences.edit {
                                            putBoolean("dismissed_expired_${latestExpiredRide.requestId}", true)
                                            remove("passenger_actively_waiting_ride_id")
                                            putBoolean("passenger_is_waiting_for_drivers", false)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar aviso",
                                        tint = MeetColors.warning,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                "Después de 30 minutos salió de la lista de choferes. Vuelve a solicitar el viaje por Tiempo + Distancia o aumenta tu oferta en Pon tu precio.",
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                            Button(
                                onClick = {
                                    fareMode = RideFareMode.OPEN_BID
                                    expiredRepriceMinor?.let { recommended ->
                                        offerPrice = if (latestExpiredRide.currency == "CRC") recommended.toDouble() else recommended / 100.0
                                    }
                                    isUsd = latestExpiredRide.currency == "USD"
                                    destAddress = latestExpiredRide.destAddress
                                    destLatitude = latestExpiredRide.destLatitude
                                    destLongitude = latestExpiredRide.destLongitude
                                    destinationPlaceId = null
                                    dismissedExpiredRideId = latestExpiredRide.requestId
                                    draftPreferences.edit {
                                        putBoolean("dismissed_expired_${latestExpiredRide.requestId}", true)
                                        remove("passenger_actively_waiting_ride_id")
                                        putBoolean("passenger_is_waiting_for_drivers", false)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                            ) {
                                Text("PON TU PRECIO · SUBIR OFERTA", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(
                                onClick = {
                                    fareMode = RideFareMode.METERED_TIME_DISTANCE
                                    isUsd = latestExpiredRide.currency == "USD"
                                    destAddress = latestExpiredRide.destAddress
                                    destLatitude = latestExpiredRide.destLatitude
                                    destLongitude = latestExpiredRide.destLongitude
                                    destinationPlaceId = null
                                    dismissedExpiredRideId = latestExpiredRide.requestId
                                    draftPreferences.edit {
                                        putBoolean("dismissed_expired_${latestExpiredRide.requestId}", true)
                                        remove("passenger_actively_waiting_ride_id")
                                        putBoolean("passenger_is_waiting_for_drivers", false)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("VOLVER A PEDIR · TIEMPO + DISTANCIA")
                            }
                            Text(
                                "Confirma de nuevo el destino antes de publicar. No se crea ni cobra otro viaje automáticamente.",
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
            // Banner de viaje activo si lo hay
            if (activeRideForPassenger != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectActiveRide(activeRideForPassenger)
                                passengerModeTab = 1
                            },
                        colors = CardDefaults.cardColors(containerColor = MeetColors.electricBlue.copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, MeetColors.electricBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚨 VIAJE ACTIVO EN CURSO", color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        viewModel.selectActiveRide(activeRideForPassenger)
                                        passengerModeTab = 1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("VER VIAJE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Desde: ${activeRideForPassenger.pickupAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Hasta: ${activeRideForPassenger.destAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Estado: ${activeRideForPassenger.truthfulPassengerStatus()} | Oferta: ${activeRideForPassenger.priceOffer.toInt()} ${activeRideForPassenger.currency}",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // GPS Quirúrgico Card
            item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "📍 Ubicación de Salida (Precisión Quirúrgica)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )

                        // Indicador milimétrico de precisión
                        val accuracy = currentGps?.accuracy ?: 999f
                        val (color, label) = when {
                            accuracy <= 5f -> Pair(MeetColors.neonGreen, "Excelente (≤5m)")
                            accuracy <= 15f -> Pair(MeetColors.warning, "Aceptable (≤15m)")
                            else -> Pair(MeetColors.error, "Impreciso (>15m)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(color.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                color = color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentGps?.addressName ?: "Detectando satélites y geolocalización...",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedPlaces.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(savedPlaces, key = RideSavedPlace::slot) { place ->
                                AssistChip(
                                    onClick = {
                                        pickupAddress = place.address
                                        pickupPlaceId = place.providerId
                                        pickupPin = rideGeoPointOrNull(
                                            place.latitude,
                                            place.longitude,
                                            5f,
                                            System.currentTimeMillis(),
                                        )
                                        pickupSuggestions = emptyList()
                                    },
                                    label = { Text("${place.label} · ${place.address}", maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            if (place.slot == "HOME") Icons.Default.Home else Icons.Default.Star,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = {
                            pickupAddress = it
                            pickupPlaceId = null
                            if (it.isBlank()) {
                                pickupPin = null
                            }
                        },
                        label = { Text("Escribe la dirección de salida / recogida") },
                        placeholder = { Text(currentGps?.addressName ?: "Escribe lugar o dirección de salida") },
                        trailingIcon = {
                            if (pickupAddress.isNotBlank()) {
                                IconButton(onClick = {
                                    pickupAddress = ""
                                    pickupPlaceId = null
                                    pickupPin = null
                                    pickupSuggestions = emptyList()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar y usar GPS",
                                        tint = MeetColors.textMuted,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedLabelColor = MeetColors.cyberCyan,
                        ),
                        singleLine = true,
                    )
                    if (pickupSearchLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MeetColors.cyberCyan,
                        )
                    }
                    pickupSuggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickupAddress = suggestion.displayLabel
                                    pickupPlaceId = suggestion.providerId
                                    pickupPin = rideGeoPointOrNull(
                                        latitude = suggestion.latitude,
                                        longitude = suggestion.longitude,
                                        accuracyMeters = 5f,
                                        capturedAtEpochMs = System.currentTimeMillis(),
                                    )
                                    pickupSuggestions = emptyList()
                                },
                            color = MeetColors.cardBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        suggestion.primaryLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (suggestion.secondaryLabel.isNotBlank()) {
                                        Text(
                                            suggestion.secondaryLabel,
                                            color = MeetColors.textMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (suggestion.source == RideMapDataSource.CACHE) {
                                        Text(
                                            "Caché local reciente",
                                            color = MeetColors.cyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                suggestion.distanceKmFrom(
                                    currentGps?.latitude,
                                    currentGps?.longitude,
                                )?.let { distance ->
                                    Text(
                                        String.format(currentLocale, "%.1f km", distance),
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    if (
                        !pickupSearchLoading &&
                        pickupAddress.trim().length >= 3 &&
                        pickupPlaceId == null &&
                        pickupSuggestions.isEmpty()
                    ) {
                        Text(
                            if (pickupSearchFailed) {
                                "No se pudo consultar el mapa. Revisa internet e inténtalo de nuevo."
                            } else {
                                "Sin coincidencias. Escribe lugar + cantón o fija el punto en el mapa."
                            },
                            color = MeetColors.warning,
                            fontSize = 10.sp,
                        )
                    }
                    if (pickupPlaceId != null || pickupPin != null) {
                        Text(
                            "✓ Punto de salida fijado · ${if (pickupPlaceId != null) "Búsqueda de dirección" else "Ajustado en mapa"}",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.detectCurrentLocation(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Actualizar Localización Satelital", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val defaultPoint = rideGeoPointOrNull(9.9281, -84.0907, 10f, System.currentTimeMillis())
                            val initial = pickupPin ?: currentGps?.let {
                                rideGeoPointOrNull(
                                    it.latitude,
                                    it.longitude,
                                    it.accuracy,
                                    it.timestamp.coerceAtLeast(0L),
                                )
                            } ?: defaultPoint
                            pendingMapPin = initial
                            pinTarget = RidePinTarget.PICKUP
                        },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (pickupPin == null) "AJUSTAR RECOGIDA EN MAPA" else "RECOGIDA FIJADA · CAMBIAR PIN")
                    }
                }
            }
        }

        // Destino Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🏁 Destino del Viaje",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedPlaces.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(savedPlaces, key = RideSavedPlace::slot) { place ->
                                AssistChip(
                                    onClick = {
                                        destAddress = place.address
                                        destLatitude = place.latitude
                                        destLongitude = place.longitude
                                        destinationPlaceId = place.providerId
                                        destinationSuggestions = emptyList()
                                    },
                                    label = { Text("${place.label} · ${place.address}", maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            if (place.slot == "HOME") Icons.Default.Home else Icons.Default.Star,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = destAddress,
                        onValueChange = {
                            destAddress = it
                            destinationPlaceId = null
                            destLatitude = 0.0
                            destLongitude = 0.0
                        },
                        label = { Text("Escribe la dirección de destino") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedLabelColor = MeetColors.cyberCyan
                        ),
                        singleLine = true
                    )
                    if (destinationSearchLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MeetColors.cyberCyan,
                        )
                    }
                    destinationSuggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    destAddress = suggestion.displayLabel
                                    destLatitude = suggestion.latitude
                                    destLongitude = suggestion.longitude
                                    destinationPlaceId = suggestion.providerId
                                    destinationSuggestions = emptyList()
                                },
                            color = MeetColors.cardBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        suggestion.primaryLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                if (suggestion.secondaryLabel.isNotBlank()) {
                                    Text(
                                        suggestion.secondaryLabel,
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (suggestion.source == RideMapDataSource.CACHE) {
                                    Text(
                                        "Caché local reciente",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                }
                                suggestion.distanceKmFrom(
                                    currentGps?.latitude,
                                    currentGps?.longitude,
                                )?.let { distance ->
                                    Text(
                                        String.format(currentLocale, "%.1f km", distance),
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    if (
                        !destinationSearchLoading &&
                        destAddress.trim().length >= 3 &&
                        destinationPlaceId == null &&
                        destinationSuggestions.isEmpty()
                    ) {
                        Text(
                            if (destinationSearchFailed) {
                                "No se pudo consultar el mapa. Revisa internet e inténtalo de nuevo."
                            } else {
                                "Sin coincidencias. Escribe lugar + cantón o fija el punto en el mapa."
                            },
                            color = MeetColors.warning,
                            fontSize = 10.sp,
                        )
                    }
                    if (destinationPlaceId != null) {
                        Text(
                            "✓ Ubicación real seleccionada · © OpenStreetMap contributors",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("HOME", "Casa", "Casa"),
                                Triple("WORK", "Trabajo", "Trabajo"),
                                Triple("FAVORITE", "Favorito", "Favorito"),
                            ).forEach { (slot, label, button) ->
                                TextButton(
                                    onClick = {
                                        savedPlaces = savedPlacesStore.save(
                                            savedPlacesOwner,
                                            RideSavedPlace(
                                                slot = slot,
                                                label = label,
                                                address = destAddress,
                                                latitude = destLatitude,
                                                longitude = destLongitude,
                                                providerId = requireNotNull(destinationPlaceId),
                                            ),
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 5.dp),
                                ) {
                                    Text("Guardar $button", fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val initial = rideGeoPointOrNull(
                                latitude = destLatitude.takeIf { it != 0.0 }
                                    ?: currentGps?.latitude ?: 9.9281,
                                longitude = destLongitude.takeIf { it != 0.0 }
                                    ?: currentGps?.longitude ?: -84.0907,
                                accuracyMeters = null,
                                capturedAtEpochMs = System.currentTimeMillis(),
                            )
                            pendingMapPin = initial
                            pinTarget = RidePinTarget.DESTINATION
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.Place, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("FIJAR DESTINO CON PIN")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Coordenadas manuales de ayuda para precisión milimétrica sin mapa
                        OutlinedTextField(
                            value = if (destLatitude == 0.0) "" else destLatitude.toString(),
                            onValueChange = { destLatitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Latitud") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.cyberCyan,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                        OutlinedTextField(
                            value = if (destLongitude == 0.0) "" else destLongitude.toString(),
                            onValueChange = { destLongitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Longitud") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.cyberCyan,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                    }
                }
            }
        }


        item {
            RideFareModeSelector(
                selected = fareMode,
                onSelected = {
                    fareMode = it
                    if (it == RideFareMode.METERED_TIME_DISTANCE) isUsd = false
                },
            )
        }

        item {
            RideStopsEditor(
                stops = stops,
                provider = placeSearchProvider,
                biasLatitude = currentGps?.latitude,
                biasLongitude = currentGps?.longitude,
                onStopsChanged = { stops = RideTripPlanPolicy.normalize(it) },
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "¿EL VIAJE ES PARA OTRA PERSONA?",
                                color = MeetColors.cyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "Puedes pedirlo para tu mamá, familiar o invitado.",
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                        Switch(
                            checked = requestingForSomeoneElse,
                            onCheckedChange = { requestingForSomeoneElse = it },
                        )
                    }
                    if (requestingForSomeoneElse) {
                        OutlinedTextField(
                            value = guestName,
                            onValueChange = { guestName = it.take(120) },
                            label = { Text("Nombre de quien viaja") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = guestPhoneE164,
                            onValueChange = { guestPhoneE164 = it.filter { char -> char == '+' || char.isDigit() }.take(16) },
                            label = { Text("Teléfono internacional (+ Código + Número)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "La solicitud queda a tu nombre; Elysium protege los datos y vincula a quien realmente viajará.",
                            color = MeetColors.textMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "FORMA DE PAGO",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RidePaymentMethod.CASH, RidePaymentMethod.SINPE_MOVIL).forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = {
                                    Text(
                                        if (method == RidePaymentMethod.CASH) "💵 Efectivo" else "📲 SINPE",
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        "La selección declara cómo se pagará; no confirma por sí sola que el pago fue recibido.",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        if (currentGps != null && destinationPlaceId != null) {
            item {
                val stopPoints = stops.mapNotNull { stop ->
                    if (!stop.isResolved) null else rideGeoPointOrNull(
                        latitude = requireNotNull(stop.latitude),
                        longitude = requireNotNull(stop.longitude),
                        accuracyMeters = null,
                        capturedAtEpochMs = System.currentTimeMillis(),
                    )
                }
                val previewState = RideMapStateFactory.create(
                    pickup = pickupPin ?: rideGeoPointOrNull(
                        currentGps!!.latitude,
                        currentGps!!.longitude,
                        currentGps!!.accuracy,
                        currentGps!!.timestamp.coerceAtLeast(0L),
                    ),
                    stops = stopPoints,
                    destination = rideGeoPointOrNull(
                        destLatitude,
                        destLongitude,
                        null,
                        System.currentTimeMillis(),
                    ),
                    route = previewRoadRoute?.geometry,
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactMap = maxWidth < 360.dp
                    Card(
                        Modifier.fillMaxWidth().height(if (compactMap) 200.dp else 280.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.82f)),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    ) {
                        RideMapPanel(
                            state = previewState,
                            modifier = Modifier.fillMaxSize(),
                            userLocation = currentGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, System.currentTimeMillis()) },
                            onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                        )
                    }
                }
                Text(
                    text = when {
                        routeSearchLoading -> "Calculando ruta vial real…"
                        previewRoadRoute != null -> {
                            val route = requireNotNull(previewRoadRoute)
                            val km = route.distanceMeters / 1_000.0
                            val minutes = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                            "Ruta vial: ${String.format(currentLocale, "%.1f", km)} km · $minutes min · ${route.attribution}"
                        }
                        routeSearchFailed ->
                            "Ruta vial no disponible. No se dibujará una línea falsa ni se inventará un ETA."
                        else -> "Selecciona destino y paradas para calcular la ruta vial."
                    },
                    color = if (routeSearchFailed) MeetColors.warning else MeetColors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        // Subasta de Precio Card
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 360.dp
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (fareMode == RideFareMode.OPEN_BID) {
                                    "PON TU PRECIO · SUBASTA JUSTA"
                                } else {
                                    "TIEMPO + DISTANCIA · TARIFA CLARA"
                                },
                                fontSize = if (compact) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.textPrimary
                            )

                            if (fareMode == RideFareMode.OPEN_BID) Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MeetColors.cardBackground)
                                    .clickable { isUsd = !isUsd }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isUsd) "USD $" else "CRC ₡",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val meteredQuote = previewRoadRoute?.let { route ->
                            runCatching {
                                RideFareEngine.quoteCostaRica(
                                    distanceMeters = route.distanceMeters.toLong(),
                                    durationSeconds = route.durationSeconds.toLong(),
                                )
                            }.getOrNull()
                        }
                        Text(
                            text = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                                meteredQuote?.let {
                                    "${CoreMoney.ofCrc(it.estimatedTotalMinor).formatted()} estimados"
                                } ?: "Calculando estimado…"
                            } else if (isUsd) {
                                "$${String.format(currentLocale, "%.2f", offerPrice / 500.0)} USD"
                            } else {
                                "${CoreMoney.ofCrc(offerPrice.toLong()).formatted()} CRC"
                            },
                            fontSize = if (compact) 24.sp else 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.neonGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                    if (fareMode == RideFareMode.OPEN_BID) Slider(
                        value = offerPrice.toFloat(),
                        onValueChange = {
                            val minor = it.toLong()
                            val activeCurrency = if (isUsd) "USD" else "CRC"
                            offerPrice = RideFareBidPolicy.normalizeMinor(minor, activeCurrency).toDouble()
                        },
                        valueRange = if (isUsd) 100f..6000f else 900f..30000f,
                        steps = 96,
                        colors = SliderDefaults.colors(
                            thumbColor = MeetColors.neonGreen,
                            activeTrackColor = MeetColors.neonGreen,
                            inactiveTrackColor = MeetColors.borderSubtle
                        )
                    )
                    Text(
                        if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                            meteredQuote?.let {
                                "${String.format(currentLocale, "%.1f", it.estimatedDistanceMeters / 1_000.0)} km × ${CoreMoney.ofCrc(300L).formatted()} = ${CoreMoney.ofCrc(it.distanceFareMinor).formatted()}  ·  " +
                                    "${String.format(currentLocale, "%.1f", it.estimatedDurationSeconds / 60.0)} min × ${CoreMoney.ofCrc(60L).formatted()} = ${CoreMoney.ofCrc(it.timeFareMinor).formatted()}\n" +
                                    "El total mostrado es estimado; el definitivo usa distancia y tiempo reales registrados. Puedes añadir paradas durante el viaje."
                            } ?: "Selecciona una ruta real para obtener el desglose."
                        } else if (isUsd) {
                            "Equivalencia referencial; la base se ajusta en saltos de ₡300"
                        } else {
                            "Ajuste exacto en saltos de ₡300"
                        },
                        color = MeetColors.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    PassengerPreferencesSelector(
                        preferences = passengerPreferences,
                        onPreferencesChange = { passengerPreferences = it },
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val verifiedPassenger = passengerVer
                            if (
                                verifiedPassenger == null ||
                                !RideVerificationPolicy.grantsAccess(verifiedPassenger.status)
                            ) {
                                Toast.makeText(
                                    context,
                                    "Completa la verificación de identidad antes de solicitar",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            if (verifiedPassenger.phone.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Falta un teléfono verificado",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            if (paymentMethod !in setOf(
                                    RidePaymentMethod.CASH,
                                    RidePaymentMethod.SINPE_MOVIL,
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Selecciona Efectivo o SINPE antes de solicitar",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            val gps = currentGps
                            if (gps == null) {
                                Toast.makeText(context, "Espere a obtener coordenadas GPS válidas", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (destAddress.isBlank()) {
                                Toast.makeText(context, "Por favor ingrese la dirección de destino", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (
                                requestingForSomeoneElse &&
                                (guestName.trim().isEmpty() ||
                                    !guestPhoneE164.trim().matches(Regex("^\\+[1-9][0-9]{7,14}$")))
                            ) {
                                Toast.makeText(
                                    context,
                                    "Indica el nombre y teléfono internacional de quien viajará",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            if (!RideTripPlanPolicy.canDispatch(
                                    destinationResolved = destinationPlaceId != null &&
                                        destLatitude.isFinite() && destLongitude.isFinite() &&
                                        destLatitude in -90.0..90.0 &&
                                        destLongitude in -180.0..180.0 &&
                                        !(destLatitude == 0.0 && destLongitude == 0.0),
                                    stops = stops,
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Selecciona cada destino y parada desde resultados reales del mapa",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }

                            // Sanitizar dirección: si el geocoder falló, rawAddressName llega
                            // como "Ubicación GPS (lat, lng)" — eso filtra coords exactas al
                            // backend. Si detectamos ese patrón, usamos genérico.
                            // (RISK-3 GPS leak: lat/lon no debe ir en strings user-facing).
                            val selectedPickup = pickupPin
                            val safePickupAddress = when {
                                pickupAddress.isNotBlank() -> pickupAddress.trim()
                                selectedPickup != null -> "Punto de recogida fijado por el pasajero"
                                else -> sanitizeGpsAddress(gps.addressName)
                            }

                            val distance = previewRoadRoute
                                ?.distanceMeters
                                ?.div(1_000.0)
                                ?: 0.0
                            val durationMinutes = previewRoadRoute
                                ?.durationSeconds
                                ?.div(60.0)
                                ?.let { kotlin.math.ceil(it) }
                                ?.toInt()
                                ?: 0
                            if (
                                fareMode == RideFareMode.METERED_TIME_DISTANCE &&
                                previewRoadRoute == null
                            ) {
                                Toast.makeText(
                                    context,
                                    "Espera el cálculo de la ruta para usar tiempo y distancia",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }

                            viewModel.createRideRequest(
                                passengerId = verifiedPassenger.passengerId,
                                passengerName = verifiedPassenger.fullName,
                                passengerPhone = verifiedPassenger.phone,
                                countryCode = gps.countryCode,
                                pickupLat = selectedPickup?.latitude ?: gps.latitude,
                                pickupLng = selectedPickup?.longitude ?: gps.longitude,
                                pickupAddr = safePickupAddress,
                                pickupAcc = selectedPickup?.accuracyMeters ?: gps.accuracy,
                                destLat = destLatitude,
                                destLng = destLongitude,
                                destAddr = destAddress,
                                priceOffer = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                                    meteredQuote?.estimatedTotalMinor?.toDouble() ?: 0.0
                                } else if (isUsd) offerPrice / 500.0 else offerPrice,
                                currency = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                                    "CRC"
                                } else if (isUsd) "USD" else "CRC",
                                estDistance = distance,
                                estDuration = durationMinutes,
                                estimatedDistanceMeters = previewRoadRoute
                                    ?.distanceMeters?.toLong() ?: 0L,
                                estimatedDurationSeconds = previewRoadRoute
                                    ?.durationSeconds?.toLong() ?: 0L,
                                stopsJson = Json.encodeToString(stops),
                                paymentMethod = paymentMethod.name,
                                fareMode = fareMode,
                                guestName = guestName.takeIf { requestingForSomeoneElse },
                                guestPhoneE164 = guestPhoneE164.takeIf { requestingForSomeoneElse },
                                passengerPreferences = passengerPreferences,
                            )
                            draftPreferences.edit {
                                putBoolean("passenger_is_waiting_for_drivers", true)
                                putLong("passenger_request_submitted_at", System.currentTimeMillis())
                            }
                            passengerModeTab = 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (fareMode == RideFareMode.OPEN_BID) {
                                if (requestingForSomeoneElse) {
                                    "🚀 PEDIR VIAJE PARA ${guestName.trim().ifEmpty { "OTRA PERSONA" }.uppercase()}"
                                } else {
                                    "🚀 PUBLICAR MI OFERTA"
                                }
                            } else {
                                "⚡ SOLICITAR CON TARIFA MEDIDA"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.backgroundDark
                        )
                    }
                }
            }
            }
        }
    }
            }
        }
                1 -> {
                    if (activeRideForPassenger != null) {
                        ActiveRidePanel(
                            viewModel = viewModel,
                            ride = activeRideForPassenger,
                            isDriver = false,
                            onCloseRide = { passengerModeTab = 0 },
                            onOpenMessages = { onOpenMessages(activeRideForPassenger.requestId) },
                        )
                    } else if (userRides.any { it.status in listOf("PENDING_PUBLICATION", "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS") }) {
                        val activeRequests = userRides.filter { it.status in listOf("PENDING_PUBLICATION", "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS") }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            item {
                                Text(
                                    "TUS SOLICITUDES ACTIVAS",
                                    color = MeetColors.cyberCyan,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            items(activeRequests) { ride ->
                                PassengerRideItem(
                                    ride = ride,
                                    onSelect = {
                                        viewModel.selectActiveRide(ride)
                                    },
                                    onCancel = { cancellationTarget = ride.requestId }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Surface(
                                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(64.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🚕", fontSize = 32.sp)
                                        }
                                    }
                                    Text(
                                        "SIN VIAJES ACTIVOS",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        letterSpacing = 1.sp,
                                    )
                                    Text(
                                        "Actualmente no tienes ningún viaje activo ni solicitudes en curso. Solicita un viaje desde la pestaña 'PEDIR VIAJE'.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                    Button(
                                        onClick = { passengerModeTab = 0 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MeetColors.cyberCyan,
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                    ) {
                                        Text("PEDIR VIAJE AHORA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> RideHistoryPanel(
                    rides = userRides.filter { it.serverVersion > 0L && (it.serverState in listOf("COMPLETED", "CANCELLED") || it.status in listOf("COMPLETED", "CANCELLED")) },
                    onSendMessage = { ride, message ->
                        viewModel.sendRideChatMessage(ride.requestId, viewModel.currentUserId.orEmpty(), ride.passengerName, "PASSENGER", message)
                    },
                    onOpenChat = { ride -> onOpenMessages(ride.requestId) },
                )
            }
        }
    }

    pinTarget?.let { target ->
        val defaultPoint = rideGeoPointOrNull(9.9281, -84.0907, 10f, System.currentTimeMillis())
        val effectivePickup = pickupPin ?: currentGps?.let {
            rideGeoPointOrNull(
                it.latitude,
                it.longitude,
                it.accuracy,
                it.timestamp.coerceAtLeast(0L),
            )
        } ?: defaultPoint

        val effectiveDestination = if (destLatitude == 0.0 && destLongitude == 0.0) {
            null
        } else {
            rideGeoPointOrNull(
                destLatitude,
                destLongitude,
                null,
                System.currentTimeMillis(),
            )
        }

        val pickerStops = stops.mapNotNull { stop ->
            val latitude = stop.latitude ?: return@mapNotNull null
            val longitude = stop.longitude ?: return@mapNotNull null
            rideGeoPointOrNull(latitude, longitude, null, System.currentTimeMillis())
        }
        var pickerRoadRoute by remember(target) { mutableStateOf<RideRoadRoute?>(null) }
        var pickerRouteLoading by remember(target) { mutableStateOf(false) }
        var pickerRouteFailed by remember(target) { mutableStateOf(false) }
        val movingPoint = pendingMapPin ?: if (target == RidePinTarget.PICKUP) effectivePickup else effectiveDestination
        val pickerPickup = if (target == RidePinTarget.PICKUP) movingPoint else effectivePickup
        val pickerDestination = if (target == RidePinTarget.DESTINATION) movingPoint else effectiveDestination

        LaunchedEffect(
            target,
            pickerPickup?.latitude,
            pickerPickup?.longitude,
            pickerDestination?.latitude,
            pickerDestination?.longitude,
            pickerStops,
        ) {
            val pickup = pickerPickup
            val destination = pickerDestination
            if (pickup == null || destination == null || pickerStops.size != stops.size) {
                pickerRoadRoute = null
                pickerRouteLoading = false
                pickerRouteFailed = false
                return@LaunchedEffect
            }
            pickerRouteLoading = true
            pickerRouteFailed = false
            pickerRoadRoute = null
            delay(350L)
            val result = runCatching {
                routingProvider.route(listOf(pickup) + pickerStops + destination)
            }
            pickerRoadRoute = result.getOrNull()
            pickerRouteFailed = result.isFailure
            pickerRouteLoading = false
        }

        val mapPinState = RideMapStateFactory.create(
            // The moving center pin is a draft. Confirmed R/D markers must not
            // jump or accumulate while the user pans the map.
            pickup = if (target == RidePinTarget.PICKUP) null else effectivePickup,
            destination = if (target == RidePinTarget.DESTINATION) null else effectiveDestination,
            stops = pickerStops,
            route = pickerRoadRoute?.geometry,
            allowStraightLineFallback = false,
        )
        val activeInitialPoint = if (target == RidePinTarget.PICKUP) {
            pendingMapPin ?: effectivePickup
        } else {
            pendingMapPin ?: effectiveDestination ?: effectivePickup
        }
        RidePinPickerDialog(
            targetLabel = if (target == RidePinTarget.PICKUP) {
                "PUNTO EXACTO DE RECOGIDA"
            } else {
                "DESTINO EXACTO"
            },
            state = mapPinState,
            initialPoint = activeInitialPoint,
            routeStatus = when {
                pickerRouteLoading -> "Calculando ruta vial real…"
                pickerRoadRoute != null -> {
                    val route = requireNotNull(pickerRoadRoute)
                    val km = route.distanceMeters / 1_000.0
                    val minutes = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                    "Ruta vial · ${String.format(currentLocale, "%.1f", km)} km · $minutes min"
                }
                pickerRouteFailed -> "Ruta vial no disponible. No se mostrará una línea recta falsa."
                else -> "Fija ambos puntos para calcular la ruta por carretera."
            },
            routeStatusIsWarning = pickerRouteFailed,
            onPinChanged = { pendingMapPin = it },
            onDismiss = {
                pendingMapPin = null
                pinTarget = null
            },
            onConfirm = { point ->
                if (target == RidePinTarget.PICKUP) {
                    pickupPin = point
                    if (pickupAddress.isBlank()) {
                        pickupAddress = "Punto fijado en el mapa"
                    }
                    pickupPlaceId = "elysium-map-pickup-pin"
                    pickupSuggestions = emptyList()
                } else {
                    destLatitude = point.latitude
                    destLongitude = point.longitude
                    destAddress = "Punto seleccionado en el mapa"
                    destinationPlaceId = "elysium-map-pin"
                    destinationSuggestions = emptyList()
                }
                pendingMapPin = null
                pinTarget = null
            },
        )
    }

    if (showPaxVerification) {
        PaxVerificationDialog(
            paxName = paxName,
            onNameChange = { paxName = it },
            paxPhone = paxPhone,
            onPhoneChange = { paxPhone = it },
            paxProfilePhoto = paxProfilePhoto,
            onProfileCapture = { paxProfilePhoto = it },
            paxCedulaFront = paxCedulaFront,
            onCedulaCapture = { paxCedulaFront = it },
            paxSelfieWithCedula = paxSelfieWithCedula,
            onSelfieCapture = { paxSelfieWithCedula = it },
            onDismiss = { showPaxVerification = false },
            onSubmit = {
                viewModel.submitPassengerVerification(
                    fullName = paxName, phone = paxPhone,
                    pathProfilePhoto = paxProfilePhoto,
                    pathCedulaFront = paxCedulaFront,
                    pathSelfieWithCedula = paxSelfieWithCedula
                )
                showPaxVerification = false
            }
        )
    }
}

@Composable
private fun RideFareModeSelector(
    selected: RideFareMode,
    onSelected: (RideFareMode) -> Unit,
) {
    BoxWithConstraints {
        val compact = maxWidth < 360.dp
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xE6081323)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                Modifier.padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "ELIGE CÓMO SE CALCULA TU VIAJE",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 12.sp else 13.sp,
                )
                Text(
                    "La modalidad queda registrada desde la solicitud y siempre será visible para ambas partes.",
                    color = MeetColors.textSecondary,
                    fontSize = if (compact) 10.sp else 11.sp,
                )
                RideFareMode.entries.forEach { mode ->
                    val active = selected == mode
                    val accent = if (mode == RideFareMode.OPEN_BID) {
                        MeetColors.hotMagenta
                    } else {
                        MeetColors.neonGreen
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) },
                        shape = RoundedCornerShape(18.dp),
                        color = accent.copy(alpha = if (active) 0.16f else 0.04f),
                        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = 0.8f)),
                        shadowElevation = if (active) 10.dp else 0.dp,
                    ) {
                        Row(
                            Modifier.padding(if (compact) 10.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                        ) {
                            RadioButton(
                                selected = active,
                                onClick = { onSelected(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = accent),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (mode == RideFareMode.OPEN_BID) {
                                        "PON TU PRECIO"
                                    } else {
                                        "TIEMPO + DISTANCIA"
                                    },
                                    color = if (active) accent else MeetColors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = if (compact) 13.sp else 15.sp,
                                )
                                Text(
                                    if (mode == RideFareMode.OPEN_BID) {
                                        "Tú propones el monto. Paradas solo antes de publicar."
                                    } else {
                                        "₡300/km + ₡60/min. Permite añadir paradas durante el viaje."
                                    },
                                    color = MeetColors.textSecondary,
                                    fontSize = if (compact) 10.sp else 11.sp,
                                    lineHeight = if (compact) 14.sp else 15.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RidePinPickerDialog(
    targetLabel: String,
    state: com.elysium369.meet.ride.map.RideMapState,
    initialPoint: RideGeoPoint?,
    routeStatus: String = "",
    routeStatusIsWarning: Boolean = false,
    onPinChanged: (RideGeoPoint) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (RideGeoPoint) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MeetColors.backgroundDark,
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    color = MeetColors.backgroundDeep,
                    shadowElevation = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar selector", tint = MeetColors.cyberCyan)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                targetLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                            )
                            Text(
                                "Arrastra el mapa bajo el pin. Pellizca o usa + / − para afinar.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                ) {
                    RideMapPanel(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        pinSelectionEnabled = true,
                        pinSelectionLabel = "El pin permanece fijo; mueve el mapa",
                        pinSelectionInitialPoint = initialPoint,
                        onPinSelectionChanged = onPinChanged,
                        onPinSelectionCancelled = onDismiss,
                        onPinSelectionConfirmed = onConfirm,
                    )
                    if (routeStatus.isNotBlank()) {
                        Text(
                            text = routeStatus,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp, start = 56.dp, end = 56.dp)
                                .background(Color(0xDD06121F), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (routeStatusIsWarning) MeetColors.warning else MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideStopsEditor(
    stops: List<RideStopSnapshot>,
    provider: RidePlaceSearchProvider,
    biasLatitude: Double?,
    biasLongitude: Double?,
    onStopsChanged: (List<RideStopSnapshot>) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PARADAS", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text("El conductor las verá antes de aceptar", color = MeetColors.textMuted, fontSize = 9.sp)
                }
                Button(
                    onClick = {
                        if (stops.size < RideTripPlanPolicy.MAX_STOPS) {
                            onStopsChanged(stops + RideStopSnapshot(stops.size + 1, ""))
                        }
                    },
                    enabled = stops.size < RideTripPlanPolicy.MAX_STOPS,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AÑADIR", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            stops.forEachIndexed { index, stop ->
                RideStopField(
                    stop = stop,
                    provider = provider,
                    biasLatitude = biasLatitude,
                    biasLongitude = biasLongitude,
                    onChanged = { updated ->
                        onStopsChanged(stops.toMutableList().also { it[index] = updated })
                    },
                    onRemove = {
                        onStopsChanged(stops.filterIndexed { candidate, _ -> candidate != index })
                    },
                )
            }
        }
    }
}

@Composable
private fun RideStopField(
    stop: RideStopSnapshot,
    provider: RidePlaceSearchProvider,
    biasLatitude: Double?,
    biasLongitude: Double?,
    onChanged: (RideStopSnapshot) -> Unit,
    onRemove: () -> Unit,
) {
    var suggestions by remember(stop.order) { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    LaunchedEffect(stop.label, stop.providerPlaceId, biasLatitude, biasLongitude) {
        if (stop.providerPlaceId != null || stop.label.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = runCatching {
            provider.search(stop.label, biasLatitude, biasLongitude)
        }.getOrDefault(emptyList())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MeetColors.cardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = stop.label,
                onValueChange = {
                    onChanged(
                        stop.copy(
                            label = it,
                            latitude = null,
                            longitude = null,
                            providerPlaceId = null,
                        ),
                    )
                },
                label = { Text("Parada ${stop.order}") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar parada", tint = MeetColors.error)
            }
        }
        suggestions.forEach { suggestion ->
            Text(
                suggestion.displayLabel,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChanged(
                            stop.copy(
                                label = suggestion.displayLabel,
                                latitude = suggestion.latitude,
                                longitude = suggestion.longitude,
                                providerPlaceId = suggestion.providerId,
                            ),
                        )
                        suggestions = emptyList()
                    }
                    .padding(vertical = 8.dp),
            )
        }
        if (stop.isResolved) {
            Text("✓ Parada ubicada en el mapa", color = MeetColors.neonGreen, fontSize = 9.sp)
        }
    }
}

@Composable
fun DriverDashboard(
    viewModel: ObdViewModel,
    onRegisterDriver: () -> Unit = {},
    onOpenRideCenter: () -> Unit = {},
    onOpenMessages: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val openRides by viewModel.openRideRequests.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.canonicalActiveFindingSummaries.collectAsState()
    val historicalDtcs by viewModel.canonicalHistoricalFindingSummaries.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()

    val driverVer by viewModel.driverVerification.collectAsState()
    val driverPresenceHealthy by viewModel.rideDriverPresenceHealthy.collectAsState()
    val myDriverId = viewModel.currentUserId ?: driverVer?.driverId
    val driverHomePrincipal by viewModel.activePrincipal.collectAsState()
    val driverHomeOwner = remember(driverHomePrincipal) { viewModel.currentUserId }
    val driverPrefs = remember(context, driverHomeOwner) {
        context.getSharedPreferences("elysium_ride_driver_ops_${driverHomeOwner ?: "signed_out"}", Context.MODE_PRIVATE)
    }
    val notificationPrincipal by viewModel.activePrincipal.collectAsState()
    val notificationOwner = remember(notificationPrincipal) { viewModel.currentUserId }
    val rideNotifications = remember(context, notificationOwner) { RideNotificationCoordinator(context, notificationOwner) }
    var destinationHomeEnabled by remember(driverHomeOwner) {
        mutableStateOf(driverPrefs.getBoolean("destination_home_enabled", false))
    }
    var homeLatitude by remember(driverHomeOwner) {
        mutableStateOf(driverPrefs.getString("home_latitude", null)?.toDoubleOrNull())
    }
    var homeLongitude by remember(driverHomeOwner) {
        mutableStateOf(driverPrefs.getString("home_longitude", null)?.toDoubleOrNull())
    }
    var walletPolicy by remember(driverHomeOwner) { mutableStateOf<RideWalletPolicy?>(null) }
    var walletBalance by remember(driverHomeOwner) { mutableStateOf<RideWalletBalance?>(null) }
    var walletMessage by remember { mutableStateOf<String?>(null) }
    var walletTopups by remember(driverHomeOwner) { mutableStateOf(emptyList<com.elysium369.meet.ride.data.remote.RideWalletTopup>()) }
    var showTopupDialog by remember { mutableStateOf(false) }
    var topupAmount by rememberSaveable { mutableStateOf(3000) }
    var pendingTopupAmount by remember { mutableStateOf<Long?>(null) }
    val walletScope = rememberCoroutineScope()
    val dispatchScope = rememberCoroutineScope()
    var driverPerformance by remember(driverHomeOwner) { mutableStateOf<RideDriverPerformance?>(null) }
    var dispatchMessage by remember(driverHomeOwner) { mutableStateOf<String?>(null) }
    var hiddenRideIds by remember(driverHomeOwner) {
        mutableStateOf(
            driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty()
                .mapNotNull { it.substringBefore('|').takeIf(String::isNotBlank) }
                .toSet(),
        )
    }
    var trustedInviteRideIds by remember(driverHomeOwner) { mutableStateOf(emptySet<String>()) }
    val proofPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val amount = pendingTopupAmount ?: return@rememberLauncherForActivityResult
        pendingTopupAmount = null
        if (uri == null) return@rememberLauncherForActivityResult
        walletScope.launch {
            runCatching {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= com.elysium369.meet.ride.domain.RideProofFormat.MAX_BYTES) { "Comprobante mayor de 12 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("No se pudo leer el comprobante")
                }
                val extension = com.elysium369.meet.ride.domain.RideProofFormat.extension(bytes)
                    ?: error("Usa JPEG, PNG o PDF de hasta 12 MB")
                val file = File(context.cacheDir, "ride-topup-${System.currentTimeMillis()}.$extension")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.writeBytes(bytes) }
                PlatformTrustCenterGateway.submitWalletTopup(file.absolutePath, amount, null, null)
            }.onSuccess {
                walletMessage = "Comprobante enviado. Estado: pendiente de revisión en Trust Center."
                walletTopups = runCatching { PlatformTrustCenterGateway.loadOwnWalletTopups() }.getOrDefault(walletTopups)
            }
                .onFailure { walletMessage = "No se pudo enviar el comprobante: ${it.message?.take(120)}" }
        }
    }
    LaunchedEffect(driverHomeOwner, driverVer?.status) {
        if (!RideVerificationPolicy.grantsAccess(driverVer?.status)) return@LaunchedEffect
        runCatching { PlatformTrustCenterGateway.walletPolicy() }
            .onSuccess { walletPolicy = it }
            .onFailure { walletPolicy = null; walletMessage = "Política financiera no disponible. Reintenta antes de transferir." }
        runCatching { PlatformTrustCenterGateway.ensureStarterCredit() }
            .onFailure { walletMessage = "No se pudo preparar el saldo inicial: ${it.message?.take(100)}" }
        runCatching { PlatformTrustCenterGateway.walletBalance() }
            .onSuccess { walletBalance = it }
            .onFailure { walletMessage = "No se pudo consultar el saldo persistente: ${it.message?.take(100)}" }
        runCatching { PlatformTrustCenterGateway.loadOwnWalletTopups() }
            .onSuccess { walletTopups = it }
        runCatching { RideDispatchGateway.decisions() }
            .onSuccess { decisions -> hiddenRideIds = hiddenRideIds + decisions.map { it.tripId } }
        runCatching { RideDispatchGateway.performance() }
            .onSuccess { driverPerformance = it }
        runCatching { RideDispatchGateway.trustedInvites() }
            .onSuccess { invites ->
                trustedInviteRideIds = invites.filter { it.state in setOf("PENDING", "SEEN") }.map { it.tripId }.toSet()
            }

        // Owner-scoped offline queue: UI decisions apply immediately and retry
        // with the same idempotency key when connectivity returns.
        val pending = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toSet()
        pending.forEach { encoded ->
            val parts = encoded.split('|')
            if (parts.size == 3) {
                runCatching { RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2]) }
                    .onSuccess {
                        val remaining = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet()
                        remaining.remove(encoded)
                        driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                    }
            }
        }
    }
    LaunchedEffect(driverHomeOwner, driverVer?.status) {
        if (driverHomeOwner == null || !RideVerificationPolicy.grantsAccess(driverVer?.status)) {
            return@LaunchedEffect
        }
        PlatformTrustCenterGateway.ownWalletTopupChanges()
            .retryWhen { _, _ -> delay(5_000L); true }
            .collect {
                walletTopups = runCatching { PlatformTrustCenterGateway.loadOwnWalletTopups() }
                    .getOrDefault(walletTopups)
                walletBalance = runCatching { PlatformTrustCenterGateway.walletBalance() }
                    .getOrDefault(walletBalance)
            }
    }
    LaunchedEffect(driverHomeOwner, driverVer?.status) {
        if (driverHomeOwner == null || !RideVerificationPolicy.grantsAccess(driverVer?.status)) {
            return@LaunchedEffect
        }
        while (true) {
            val pending = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toSet()
            pending.forEach { encoded ->
                val parts = encoded.split('|')
                if (parts.size == 3) {
                    runCatching { RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2]) }
                        .onSuccess {
                            val remaining = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet()
                            remaining.remove(encoded)
                            driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                        }
                }
            }
            runCatching { RideDispatchGateway.trustedInvites() }
                .onSuccess { invites ->
                    trustedInviteRideIds = invites.filter { it.state in setOf("PENDING", "SEEN") }.map { it.tripId }.toSet()
                }
            delay(15_000L)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.rideClaimFeedback.collect { feedback ->
            Toast.makeText(
                context,
                when {
                    feedback.won -> "🎉 ${feedback.message}"
                    feedback.pending -> "⏳ ${feedback.message}"
                    else -> "⚡ ${feedback.message}"
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val activeRide by viewModel.activeRideRequest.collectAsState()
    val offers by viewModel.rideOffers.collectAsState()
    val rideClaimUiState by viewModel.rideClaimUiState.collectAsState()
    var rideToBidOn by remember { mutableStateOf<RideRequestEntity?>(null) }
    val driverIdCandidates = remember(myDriverId, driverVer?.driverId, viewModel.currentUserId, viewModel.currentRideActorId) {
        setOfNotNull(myDriverId, driverVer?.driverId, viewModel.currentUserId, viewModel.currentRideActorId)
    }
    val activeRideForDriver = remember(allRides, driverIdCandidates, activeRide) {
        val candidate = activeRide?.takeIf {
            it.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS") &&
                it.assignedDriverId in driverIdCandidates
        }
        candidate ?: run {
            val active = allRides.filter {
                it.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS") &&
                    it.assignedDriverId in driverIdCandidates
            }
            if (active.size > 1) {
                android.util.Log.e("ElysiumRides", "CONSISTENCY_VIOLATION: ${active.size} active rides for driver $driverIdCandidates — using most recent")
            }
            active.maxByOrNull { it.createdAt }
        }
    }
    val completedDriverRides = remember(allRides, driverIdCandidates) {
        allRides.filter {
            it.serverVersion > 0L &&
                (it.serverState in listOf("COMPLETED", "CANCELLED") || it.status in listOf("COMPLETED", "CANCELLED")) &&
                it.assignedDriverId in driverIdCandidates
        }
    }
    LaunchedEffect(driverHomeOwner, driverVer?.status, completedDriverRides.map { it.requestId to it.serverVersion }) {
        if (driverHomeOwner != null && RideVerificationPolicy.grantsAccess(driverVer?.status)) {
            runCatching { PlatformTrustCenterGateway.walletBalance() }
                .onSuccess { walletBalance = it }
                .onFailure { walletMessage = "No se pudo actualizar el saldo de viajes: ${it.message?.take(100)}" }
        }
    }
    LaunchedEffect(completedDriverRides) {
        if (driverHomeOwner != null) {
            val unratedCompleted = completedDriverRides.firstOrNull { ride ->
                (ride.status == "COMPLETED" || ride.serverState == "COMPLETED") &&
                    ride.driverRating == null &&
                    !viewModel.isRatingDismissed(ride.requestId)
            }
            if (unratedCompleted != null) {
                viewModel.promptRideRating(unratedCompleted)
            }
        }
    }
    var feedClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { feedClockMillis = System.currentTimeMillis(); delay(15_000L) }
    }
    val driverFeed = remember(openRides, driverIdCandidates, hiddenRideIds, activeRideForDriver?.requestId, feedClockMillis) {
        RideDriverFeedPolicy.evaluate(
            rides = openRides,
            actorIds = driverIdCandidates,
            activeRideId = activeRideForDriver?.requestId,
            hiddenRideIds = hiddenRideIds,
            nowEpochMs = feedClockMillis,
        )
    }
    LaunchedEffect(driverFeed) {
        if (BuildConfig.DEBUG) android.util.Log.i("ElysiumRideFeed", "DRIVER_FEED roomOpen=${openRides.size} eligible=${driverFeed.eligibleRides.size} own=${driverFeed.ownPassengerRequests.size} hidden=${driverFeed.hiddenRides.size} expired=${driverFeed.expiredCount}")
    }
    val rankedOpenRides = remember(driverFeed, destinationHomeEnabled, homeLatitude, homeLongitude) {
        val eligibleRides = driverFeed.eligibleRides
        if (!destinationHomeEnabled || homeLatitude == null || homeLongitude == null) {
            eligibleRides
        } else {
            eligibleRides.sortedBy { ride ->
                calculateDistance(
                    ride.destLatitude,
                    ride.destLongitude,
                    requireNotNull(homeLatitude),
                    requireNotNull(homeLongitude),
                )
            }
        }
    }
    LaunchedEffect(driverVer?.status, activeRideForDriver, rankedOpenRides.isEmpty()) {
        if (
            RideVerificationPolicy.grantsAccess(driverVer?.status) &&
            activeRideForDriver == null &&
            rankedOpenRides.isEmpty()
        ) {
            rideNotifications.notifyIdleDriver()
        }
    }

    var driverModeTab by rememberSaveable { mutableStateOf(if (activeRideForDriver != null) 1 else 0) }

    LaunchedEffect(activeRideForDriver?.requestId) {
        if (activeRideForDriver != null) {
            if (activeRide?.requestId != activeRideForDriver.requestId) {
                viewModel.selectActiveRide(activeRideForDriver)
            }
            driverModeTab = 1
        }
    }

    // Track handled incoming dispatch ride IDs so popup only appears ONCE per ride
    val handledDispatchRideIds = remember { mutableStateListOf<String>() }
    // Auto-cleanup: remove handled IDs for rides no longer in the open list
    LaunchedEffect(rankedOpenRides) {
        val activeIds = rankedOpenRides.map { it.requestId }.toSet()
        handledDispatchRideIds.removeAll { id -> id !in activeIds }
    }
    val topIncomingRide = remember(rankedOpenRides, handledDispatchRideIds.toList(), activeRideForDriver) {
        if (activeRideForDriver != null) null
        else rankedOpenRides.firstOrNull { ride ->
            ride.requestId !in handledDispatchRideIds
        }
    }
    var hudNoticeType by remember { mutableStateOf(ToastType.INFO) }
    var hudNoticeMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = driverModeTab,
            containerColor = MeetColors.cardBackground,
            contentColor = MeetColors.cyberCyan,
            indicator = { tabPositions ->
                val indicatorColor = when (driverModeTab) {
                    1 -> MeetColors.neonGreen
                    3 -> Color(0xFFFFD700)
                    else -> MeetColors.cyberCyan
                }
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[driverModeTab]),
                    color = indicatorColor
                )
            }
        ) {
            Tab(
                selected = driverModeTab == 0,
                onClick = { driverModeTab = 0 },
                text = {
                    Text(
                        "MI TURNO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (driverModeTab == 0) MeetColors.cyberCyan else MeetColors.textSecondary
                    )
                }
            )
            Tab(
                selected = driverModeTab == 1,
                onClick = { driverModeTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (activeRideForDriver != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.neonGreen)
                            )
                        }
                        Text(
                            "VIAJE ACTIVO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (driverModeTab == 1) MeetColors.neonGreen else if (activeRideForDriver != null) Color.White else MeetColors.textSecondary
                        )
                    }
                }
            )
            Tab(
                selected = driverModeTab == 2,
                onClick = { driverModeTab = 2; viewModel.refreshRideProjectionNow() },
                text = { Text("FINALIZADOS", fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = if (driverModeTab == 2) MeetColors.neonGreen else MeetColors.textSecondary) },
            )
            Tab(
                selected = driverModeTab == 3,
                onClick = { driverModeTab = 3 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("👑", fontSize = 12.sp)
                        Text(
                            "FLOTA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (driverModeTab == 3) Color(0xFFFFD700) else MeetColors.textSecondary
                        )
                    }
                }
            )
        }

        when (driverModeTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (activeRideForDriver != null) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectActiveRide(activeRideForDriver)
                                        driverModeTab = 1
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2436)),
                                border = BorderStroke(2.dp, MeetColors.cyberCyan),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        color = MeetColors.cyberCyan.copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🚕", fontSize = 22.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "TIENES UN VIAJE ACTIVO",
                                            color = MeetColors.cyberCyan,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            "Recogida: ${activeRideForDriver.pickupAddress}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.selectActiveRide(activeRideForDriver)
                                            driverModeTab = 1
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MeetColors.cyberCyan,
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text("IR AL VIAJE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
        item {
            DriverPerformanceCard(driverPerformance)
        }
        item {
            Text(
                if (driverPresenceHealthy) "Ubicación del chofer confirmada por Supabase"
                else "Esperando ubicación GPS reciente y confirmación de Supabase",
                color = if (driverPresenceHealthy) MeetColors.neonGreen else MeetColors.warning,
                fontSize = 11.sp,
            )
        }
        item {
            DriverWalletCard(
                policy = walletPolicy,
                balance = walletBalance,
                topups = walletTopups,
                message = walletMessage,
                onRecharge = {
                    walletScope.launch {
                        walletMessage = null
                        runCatching {
                            val specialistId = driverHomeOwner ?: error("Sesión no válida")
                            com.elysium369.meet.core.wallet.SpecialistWalletStore.requestOperativeAdvance(
                                context = context,
                                specialistId = specialistId,
                                serviceVertical = "RIDES",
                            )
                        }.onSuccess { topup ->
                            walletMessage = "✓ Crédito de ₡3,000 acreditado. Ref: ${topup.referenceNumber}"
                            runCatching { PlatformTrustCenterGateway.walletBalance() }
                                .onSuccess { walletBalance = it }
                        }.onFailure { e ->
                            val msg = e.message ?: "Error desconocido"
                            walletMessage = when {
                                "BALANCE_ABOVE_THRESHOLD" in msg -> "Tu saldo es mayor a ₡400. El crédito solo está disponible cuando tu saldo sea ≤ ₡400."
                                "AUTH_REQUIRED" in msg -> "Sesión no válida. Reinicia la app."
                                "OPERATIVE_SUSPENDED" in msg -> "Cuenta suspendida. Liquida tu deuda para continuar."
                                else -> "No se pudo procesar: ${msg.take(100)}"
                            }
                        }
                    }
                },
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRideCenter() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                border = BorderStroke(1.5.dp, Color(0xFFFF8C00).copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Color(0xFFFF8C00).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🚗", fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "CENTRO DE VIAJES",
                            color = Color(0xFFFF8C00),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            "${rankedOpenRides.size} solicitud(es) disponible(s)",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFFF8C00),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        if (!RideVerificationPolicy.grantsAccess(driverVer?.status)) {
            item {
                BoxWithConstraints {
                    val compact = maxWidth < 360.dp
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                        border = BorderStroke(1.5.dp, MeetColors.warning.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (compact) 16.dp else 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                        ) {
                            Text("🚨", fontSize = if (compact) 36.sp else 48.sp)
                            Text(
                                "ACCESO RESTRINGIDO A CHOFERES",
                                color = MeetColors.warning,
                                fontSize = if (compact) 14.sp else 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (driverVer?.status == "PENDING") {
                                    "Tu expediente de chofer ha sido enviado con éxito y se encuentra en revisión. Tan pronto sea aprobado por el equipo de confianza, podrás visualizar solicitudes y ofertar inmediatamente."
                                } else {
                                    "Debes completar el registro de chofer y adjuntar la documentación requerida para visualizar solicitudes y ofertar."
                                },
                                color = MeetColors.textSecondary,
                                fontSize = if (compact) 12.sp else 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = if (compact) 16.sp else 18.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "Estado actual: ${driverVer?.status ?: "No registrado"}",
                                color = if (driverVer?.status == "PENDING") MeetColors.warning else MeetColors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compact) 12.sp else 14.sp
                            )
                            if (driverVer == null) {
                                Button(
                                    onClick = onRegisterDriver,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MeetColors.cyberCyan,
                                        contentColor = Color(0xFF02131E),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(Icons.Default.Badge, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("COMPLETAR REGISTRO DE CHOFER", fontWeight = FontWeight.Black)
                                }
                            } else if (driverVer?.status == "PENDING") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.warning.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text("⏳", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Expediente en cola de aprobación",
                                            color = MeetColors.warning,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                    }
                }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF07131E)),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, tint = MeetColors.neonGreen)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DESTINO CASA", color = Color.White, fontWeight = FontWeight.Black)
                                Text(
                                    "Prioriza solicitudes cuyo destino se acerque a tu hogar.",
                                    color = MeetColors.textMuted,
                                    fontSize = 10.sp,
                                )
                            }
                            Switch(
                                checked = destinationHomeEnabled,
                                onCheckedChange = { enabled ->
                                    destinationHomeEnabled = enabled && homeLatitude != null
                                    driverPrefs.edit {
                                        putBoolean("destination_home_enabled", destinationHomeEnabled)
                                    }
                                },
                                enabled = driverHomeOwner != null && homeLatitude != null,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                currentGps?.let { gps ->
                                    homeLatitude = gps.latitude
                                    homeLongitude = gps.longitude
                                    driverPrefs.edit {
                                        putString("home_latitude", gps.latitude.toString())
                                        putString("home_longitude", gps.longitude.toString())
                                    }
                                }
                            },
                            enabled = driverHomeOwner != null && currentGps != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (homeLatitude == null) "FIJAR CASA EN MI GPS" else "ACTUALIZAR UBICACIÓN DE CASA")
                        }
                        if (destinationHomeEnabled) {
                            Text(
                                "Recomendación activa · la asignación sigue siendo autoritativa y de un solo conductor.",
                                color = MeetColors.neonGreen,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }



            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MeetColors.cyberCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tablero de Chofer", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Abajo se listan las solicitudes disponibles. La nube debe estar autenticada para sincronización entre dispositivos.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            if (rankedOpenRides.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MeetColors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (driverFeed.ownPassengerRequests.isNotEmpty())
                                    "Tu solicitud de pasajero sí está publicada. Esta misma cuenta no puede autoasignársela; usa otra cuenta de conductor para probar el despacho."
                                else "Buscando solicitudes de viaje en tu zona...",
                                color = if (driverFeed.ownPassengerRequests.isNotEmpty()) MeetColors.warning else MeetColors.textMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.refreshRideProjectionNow() }) {
                                Text("ACTUALIZAR SOLICITUDES")
                            }
                        }
                    }
                }
            } else {
                items(rankedOpenRides) { request ->
                    val myOfferForRide = offers.firstOrNull {
                        it.requestId == request.requestId &&
                            (it.driverId in driverIdCandidates) &&
                            it.status == "PENDING"
                    }
                    DriverRideItem(
                        ride = request,
                        currentGps = currentGps,
                        isTrustedInvite = request.requestId in trustedInviteRideIds,
                        isOwnRequest = request.passengerId in driverIdCandidates,
                        pendingOfferPrice = myOfferForRide?.counterPrice,
                        isPending = (rideClaimUiState as? ObdViewModel.RideClaimUiState.Pending)?.requestId == request.requestId,
                        onClick = { rideToBidOn = request },
                        onAccept = { viewModel.claimRideFirstCome(request.requestId) },
                        onOffer = { rideToBidOn = request },
                        onDismiss = {
                            hiddenRideIds = hiddenRideIds + request.requestId
                            val pending = "${request.requestId}|DISMISS|${java.util.UUID.randomUUID()}"
                            val queued = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet().apply { add(pending) }
                            driverPrefs.edit { putStringSet("pending_driver_decisions", queued) }
                            dispatchScope.launch {
                                runCatching {
                                    val parts = pending.split('|')
                                    RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2])
                                }.onSuccess {
                                    val remaining = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet().apply { remove(pending) }
                                    driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                                    dispatchMessage = "Solicitud ocultada en tu cuenta."
                                }.onFailure {
                                    dispatchMessage = "Solicitud ocultada aquí; se sincronizará al recuperar internet."
                                }
                            }
                        },
                        onReject = {
                            hiddenRideIds = hiddenRideIds + request.requestId
                            val pending = "${request.requestId}|REJECT|${java.util.UUID.randomUUID()}"
                            val queued = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet().apply { add(pending) }
                            driverPrefs.edit { putStringSet("pending_driver_decisions", queued) }
                            dispatchScope.launch {
                                runCatching {
                                    val parts = pending.split('|')
                                    RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2])
                                }.onSuccess {
                                    val remaining = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toMutableSet().apply { remove(pending) }
                                    driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                                    dispatchMessage = "Oferta rechazada. El pasajero fue avisado y su solicitud sigue activa."
                                }.onFailure {
                                    dispatchMessage = "Rechazo guardado; se sincronizará al recuperar internet."
                                }
                            }
                        },
                    )
                }
            }
            dispatchMessage?.let { message ->
                item {
                    Text(message, color = MeetColors.cyberCyan, fontSize = 11.sp)
                }
            }
        }
    }
            }
            1 -> {
                if (activeRideForDriver != null) {
                    ActiveRidePanel(
                        viewModel = viewModel,
                        ride = activeRideForDriver,
                        isDriver = true,
                        onCloseRide = { viewModel.selectActiveRide(null) },
                        onOpenMessages = { onOpenMessages(activeRideForDriver.requestId) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Surface(
                                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🚕", fontSize = 32.sp)
                                    }
                                }
                                Text(
                                    "SIN VIAJE ACTIVO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    "Actualmente no tienes ningún viaje asignado ni en progreso. Revisa las solicitudes disponibles en la pestaña 'MI TURNO' para ofertar.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Button(
                                    onClick = { driverModeTab = 0 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MeetColors.cyberCyan,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Text("VER SOLICITUDES DISPONIBLES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            2 -> RideHistoryPanel(
                rides = completedDriverRides,
                isDriver = true,
                onOpenChat = { ride -> onOpenMessages(ride.requestId) },
            )
            else -> {
                FleetMogulDashboard(viewModel = viewModel)
            }
        }
    }

        if (topIncomingRide != null && activeRideForDriver == null && driverModeTab != 1) {
            RideIncomingDispatchOverlay(
                ride = topIncomingRide,
                driverLat = currentGps?.latitude,
                driverLng = currentGps?.longitude,
                onAccept = { rideToAccept ->
                    handledDispatchRideIds.add(rideToAccept.requestId)
                    viewModel.claimRideFirstCome(rideToAccept.requestId)
                },
                onCounterOffer = { rideToCounter ->
                    handledDispatchRideIds.add(rideToCounter.requestId)
                    rideToBidOn = rideToCounter
                },
                onDismiss = { rideToDismiss ->
                    handledDispatchRideIds.add(rideToDismiss.requestId)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        ElysiumToastBar(
            message = hudNoticeMessage.orEmpty(),
            type = hudNoticeType,
            visible = hudNoticeMessage != null,
            onDismiss = { hudNoticeMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    rideToBidOn?.let { targetRide ->
        DriverBiddingDialog(
            ride = targetRide,
            viewModel = viewModel,
            onDismiss = { rideToBidOn = null },
            onAccepted = {
                driverModeTab = 1
                rideToBidOn = null
            }
        )
    }

    if (showTopupDialog) {
        var pastedReceiptText by rememberSaveable { mutableStateOf("") }
        var detectedRef by rememberSaveable { mutableStateOf("") }
        val parsedReceipt = remember(pastedReceiptText) {
            SinpeReceiptParser.parse(pastedReceiptText)
        }
        LaunchedEffect(parsedReceipt) {
            if (parsedReceipt != null) {
                topupAmount = parsedReceipt.amountCrc.toInt()
                detectedRef = parsedReceipt.referenceNumber
            }
        }

        AlertDialog(
            onDismissRequest = { showTopupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💰", fontSize = 20.sp)
                    Text("Gestión de Saldo y Préstamos")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sistema de Préstamo Operativo: Solicita un adelanto de ₡3,000 CRC cuando tu saldo sea ≤ ₡400. La deuda se acumula y deberá liquidarse al vincular la cuenta empresarial oficial.",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = Color(0xFF142236),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⚡ Préstamo Operativo: ₡3,000 CRC por solicitud", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MeetColors.neonGreen)
                            Text("📋 Condición: Saldo actual debe ser ≤ ₡400 CRC", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("🏢 Elysium Network Corporation — Fideicomiso Operativo", fontSize = 10.sp, color = MeetColors.cyberCyan)
                        }
                    }

                    OutlinedTextField(
                        value = pastedReceiptText,
                        onValueChange = { pastedReceiptText = it },
                        label = { Text("Pegar texto de confirmación bancaria (BAC, BNCR, BCR)") },
                        placeholder = { Text("Ej: Transferencia SINPE Móvil por ₡10000... Ref: 2026091301") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderSubtle
                        )
                    )

                    if (parsedReceipt != null) {
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MeetColors.neonGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✓", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
                                Text(
                                    "${parsedReceipt.bank.displayName}: Ref ${parsedReceipt.referenceNumber} (₡${parsedReceipt.amountCrc.toInt()})",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = topupAmount.toString(),
                        onValueChange = { value ->
                            value.filter(Char::isDigit).toLongOrNull()?.coerceAtMost(10_000_000L)?.let { topupAmount = it.toInt() }
                        },
                        label = { Text("Monto a acreditar (CRC)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = detectedRef,
                        onValueChange = { detectedRef = it },
                        label = { Text("Número de comprobante o referencia") },
                        singleLine = true,
                        placeholder = { Text("Ej: 20260913987654") }
                    )

                    Text("Se aceptan imágenes o comprobante en texto directo.", color = MeetColors.warning, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTopupDialog = false
                        pendingTopupAmount = topupAmount.toLong()
                        proofPicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                        Toast.makeText(context, "Comprobante enviado para validación autorizada.", Toast.LENGTH_LONG).show()
                    },
                    enabled = topupAmount > 0 && walletPolicy != null && driverHomeOwner != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                ) { Text("ADJUNTAR Y ACREDITAR", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTopupDialog = false }) { Text("CANCELAR") } },
        )
    }
}

@Composable
fun FleetMogulDashboard(
    viewModel: ObdViewModel,
) {
    val fleetUnits by viewModel.fleetMogulUnits.collectAsState()
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var unitToSettle by remember { mutableStateOf<FleetMogulVehicle?>(null) }
    var unitToConfirmLock by remember { mutableStateOf<FleetMogulVehicle?>(null) }

    val totalVehicles = fleetUnits.size
    val activeOnRoute = fleetUnits.count { it.status == FleetUnitStatus.ON_ROUTE }
    val totalGrossRevenue = fleetUnits.sumOf { it.todayGrossRevenueCrc }
    val totalOwnerEarnings = fleetUnits.sumOf { it.todayOwnerEarningsCrc }
    val totalBalanceDue = fleetUnits.sumOf { it.balanceDueFromDriverCrc }
    val totalAlerts = fleetUnits.count { it.hasAlerts }

    val filteredUnits = remember(fleetUnits, selectedFilter) {
        when (selectedFilter) {
            "ON_ROUTE" -> fleetUnits.filter { it.status == FleetUnitStatus.ON_ROUTE }
            "AVAILABLE" -> fleetUnits.filter { it.status == FleetUnitStatus.AVAILABLE }
            "IN_WORKSHOP" -> fleetUnits.filter { it.status == FleetUnitStatus.IN_WORKSHOP }
            "LOCKED" -> fleetUnits.filter { it.isDispatchLocked }
            "ALERTS" -> fleetUnits.filter { it.hasAlerts }
            else -> fleetUnits
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141E33)),
                border = BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("👑", fontSize = 26.sp)
                            Column {
                                Text(
                                    "CENTRO DE CONTROL DE FLOTAS",
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Gestión de Inversionistas & Telemetría en Vivo",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Button(
                            onClick = { showAddVehicleDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ VINCULAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FleetKpiBox(
                            modifier = Modifier.weight(1f),
                            title = "FLOTA TOTAL",
                            value = "$totalVehicles",
                            subtitle = "$activeOnRoute en servicio",
                            accentColor = MeetColors.cyberCyan
                        )
                        FleetKpiBox(
                            modifier = Modifier.weight(1.3f),
                            title = "MI GANANCIA HOY",
                            value = "₡${"%,d".format(totalOwnerEarnings)}",
                            subtitle = "Bruto: ₡${"%,d".format(totalGrossRevenue)}",
                            accentColor = MeetColors.neonGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FleetKpiBox(
                            modifier = Modifier.weight(1f),
                            title = "DEUDA CHOFERES",
                            value = "₡${"%,d".format(totalBalanceDue)}",
                            subtitle = if (totalBalanceDue > 0) "Mora pendiente" else "Flota al día",
                            accentColor = if (totalBalanceDue > 0) MeetColors.warning else MeetColors.electricBlue
                        )
                        FleetKpiBox(
                            modifier = Modifier.weight(1f),
                            title = "ALERTAS SALUD",
                            value = "$totalAlerts",
                            subtitle = if (totalAlerts > 0) "Requieren atención" else "Parámetros óptimos",
                            accentColor = if (totalAlerts > 0) MeetColors.error else MeetColors.neonGreen
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "Todas (${fleetUnits.size})",
                    "ON_ROUTE" to "En Servicio ($activeOnRoute)",
                    "AVAILABLE" to "Disponibles (${fleetUnits.count { it.status == FleetUnitStatus.AVAILABLE }})",
                    "LOCKED" to "Bloqueadas (${fleetUnits.count { it.isDispatchLocked }})",
                    "ALERTS" to "Con Alertas ($totalAlerts)",
                )
                items(filters) { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFD700).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFFFD700),
                            containerColor = MeetColors.cardBackground,
                            labelColor = MeetColors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFFFFD700) else MeetColors.borderSubtle,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }

        if (filteredUnits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚗", fontSize = 36.sp)
                        Text("No hay unidades en esta categoría", color = MeetColors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredUnits) { unit ->
                FleetMogulUnitCard(
                    unit = unit,
                    onToggleLock = { unitToConfirmLock = unit },
                    onSettleDebt = { unitToSettle = unit }
                )
            }
        }
    }

    if (showAddVehicleDialog) {
        AddFleetVehicleDialog(
            onDismiss = { showAddVehicleDialog = false },
            onConfirm = { plate, brand, model, year, driverName, driverPhone, contractType, dailyCanon, splitPercent ->
                viewModel.linkNewFleetVehicle(
                    plate = plate,
                    brand = brand,
                    model = model,
                    year = year,
                    driverName = driverName,
                    driverPhone = driverPhone,
                    contractType = contractType,
                    dailyCanonCrc = dailyCanon,
                    splitPercent = splitPercent
                )
                showAddVehicleDialog = false
            }
        )
    }

    unitToSettle?.let { unit ->
        SettleFleetDebtDialog(
            unit = unit,
            onDismiss = { unitToSettle = null },
            onConfirm = { amount ->
                viewModel.settleDriverDebt(unit.id, amount)
                unitToSettle = null
            }
        )
    }

    unitToConfirmLock?.let { unit ->
        AlertDialog(
            onDismissRequest = { unitToConfirmLock = null },
            title = {
                Text(
                    if (unit.isDispatchLocked) "Habilitar Despacho a Chofer" else "Suspender Despacho por Deuda/Abuso",
                    fontWeight = FontWeight.Bold,
                    color = if (unit.isDispatchLocked) MeetColors.neonGreen else MeetColors.warning
                )
            },
            text = {
                Text(
                    if (unit.isDispatchLocked) {
                        "¿Deseas reactivar el despacho para el chofer ${unit.assignedDriverName} en la unidad ${unit.plate}? Podrá recibir y ofertar viajes nuevamente de inmediato."
                    } else {
                        "¿Confirmas el bloqueo de despacho para ${unit.assignedDriverName} en la unidad ${unit.plate}? La app de chofer no recibirá carreras hasta que sea desbloqueado por la administración."
                    },
                    color = MeetColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleFleetDispatchLock(unit.id)
                        unitToConfirmLock = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (unit.isDispatchLocked) MeetColors.neonGreen else MeetColors.error
                    )
                ) {
                    Text(
                        if (unit.isDispatchLocked) "HABILITAR" else "BLOQUEAR DESPACHO",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { unitToConfirmLock = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

@Composable
fun FleetKpiBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Surface(
        color = MeetColors.cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FleetMogulUnitCard(
    unit: FleetMogulVehicle,
    onToggleLock: () -> Unit,
    onSettleDebt: () -> Unit,
) {
    val statusColor = when (unit.status) {
        FleetUnitStatus.ON_ROUTE -> MeetColors.neonGreen
        FleetUnitStatus.AVAILABLE -> MeetColors.cyberCyan
        FleetUnitStatus.IN_WORKSHOP -> MeetColors.warning
        FleetUnitStatus.DISPATCH_LOCKED -> MeetColors.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            1.dp,
            if (unit.isDispatchLocked) MeetColors.error.copy(alpha = 0.8f)
            else if (unit.hasAlerts) MeetColors.warning.copy(alpha = 0.6f)
            else MeetColors.borderSubtle
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF94A3B8))
                    ) {
                        Text(
                            text = unit.plate,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "${unit.brand} ${unit.model} ${unit.year}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Chofer: ${unit.assignedDriverName} (${unit.driverPhone})",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = unit.status.label,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MeetColors.cardBackground,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modalidad: ${unit.contractType.label}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = when (unit.contractType) {
                            FleetContractType.FIXED_CANON -> "Canon: ₡${"%,d".format(unit.dailyCanonCrc)}/día"
                            FleetContractType.PROFIT_SPLIT -> "Dueño: ${unit.ownerSplitPercent}% / Chofer: ${100 - unit.ownerSplitPercent}%"
                            FleetContractType.HYBRID -> "₡${"%,d".format(unit.dailyCanonCrc)} + ${unit.ownerSplitPercent}%"
                        },
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val healthPct = unit.healthScore / 10
                val healthColor = if (healthPct >= 80) MeetColors.neonGreen else if (healthPct >= 60) MeetColors.warning else MeetColors.error
                Surface(
                    color = Color(0xFF0B132B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SALUD", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("$healthPct%", color = healthColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }

                val tempColor = if (unit.engineTempC < 98f) MeetColors.neonGreen else if (unit.engineTempC < 105f) MeetColors.warning else MeetColors.error
                Surface(
                    color = Color(0xFF0B132B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TEMP OBD", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${unit.engineTempC.toInt()}°C", color = tempColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }

                Surface(
                    color = Color(0xFF0B132B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VEL / RPM", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${unit.currentSpeedKph.toInt()}k | ${unit.rpm.toInt()}", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Surface(
                    color = Color(0xFF0B132B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FALLAS", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (unit.activeDtcs.isEmpty()) "0 DTC" else "${unit.activeDtcs.size} DTC",
                            color = if (unit.activeDtcs.isEmpty()) MeetColors.neonGreen else MeetColors.error,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (unit.isAbuseDetected) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MeetColors.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Text(
                            "Alerta de Telemetría: Patrones de sobreaceleración o RPM excesivo detectados.",
                            color = MeetColors.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Ganancia Dueño Hoy: ₡${"%,d".format(unit.todayOwnerEarningsCrc)}",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        "Bruto chofer: ₡${"%,d".format(unit.todayGrossRevenueCrc)}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                }

                if (unit.balanceDueFromDriverCrc > 0) {
                    Surface(
                        color = MeetColors.warning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "Debe: ₡${"%,d".format(unit.balanceDueFromDriverCrc)}",
                            color = MeetColors.warning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        "Al día",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSettleDebt,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Text("COBRAR / LIQUIDAR", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onToggleLock,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (unit.isDispatchLocked) MeetColors.neonGreen else MeetColors.error
                    )
                ) {
                    Text(
                        if (unit.isDispatchLocked) "HABILITAR" else "BLOQUEAR DESPACHO",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AddFleetVehicleDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        plate: String,
        brand: String,
        model: String,
        year: Int,
        driverName: String,
        driverPhone: String,
        contractType: FleetContractType,
        dailyCanon: Long,
        splitPercent: Int
    ) -> Unit
) {
    var plate by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("2022") }
    var driverName by remember { mutableStateOf("") }
    var driverPhone by remember { mutableStateOf("") }
    var contractType by remember { mutableStateOf(FleetContractType.FIXED_CANON) }
    var dailyCanonText by remember { mutableStateOf("18000") }
    var splitPercentText by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vincular Nueva Unidad a Flota", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Placa del Vehículo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter(Char::isDigit) },
                    label = { Text("Año") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Nombre del Chofer Asignado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = driverPhone,
                    onValueChange = { driverPhone = it },
                    label = { Text("Teléfono del Chofer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Modalidad de Contrato:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FleetContractType.values().forEach { type ->
                        FilterChip(
                            selected = contractType == type,
                            onClick = { contractType = type },
                            label = { Text(type.name, fontSize = 10.sp) }
                        )
                    }
                }

                if (contractType == FleetContractType.FIXED_CANON || contractType == FleetContractType.HYBRID) {
                    OutlinedTextField(
                        value = dailyCanonText,
                        onValueChange = { dailyCanonText = it.filter(Char::isDigit) },
                        label = { Text("Canon Diario (CRC)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (contractType == FleetContractType.PROFIT_SPLIT || contractType == FleetContractType.HYBRID) {
                    OutlinedTextField(
                        value = splitPercentText,
                        onValueChange = { splitPercentText = it.filter(Char::isDigit) },
                        label = { Text("% Comisión para Dueño") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = yearText.toIntOrNull() ?: 2022
                    val dailyCanon = dailyCanonText.toLongOrNull() ?: 0L
                    val split = (splitPercentText.toIntOrNull() ?: 25).coerceIn(1, 99)
                    if (plate.isNotBlank() && driverName.isNotBlank()) {
                        onConfirm(plate, brand, model, year, driverName, driverPhone, contractType, dailyCanon, split)
                    }
                },
                enabled = plate.isNotBlank() && driverName.isNotBlank()
            ) {
                Text("VINCULAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR") }
        }
    )
}

@Composable
fun SettleFleetDebtDialog(
    unit: FleetMogulVehicle,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long) -> Unit
) {
    var amountText by remember { mutableStateOf(unit.balanceDueFromDriverCrc.toString().takeIf { it != "0" } ?: unit.dailyCanonCrc.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liquidar Canon / Saldo de Chofer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vehículo: ${unit.plate} · ${unit.assignedDriverName}", color = MeetColors.textSecondary)
                Text("Saldo en mora actual: ₡${"%,d".format(unit.balanceDueFromDriverCrc)}", fontWeight = FontWeight.Bold)
                Text("Canon diario convenido: ₡${"%,d".format(unit.dailyCanonCrc)}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Monto Recibido en Efectivo / SINPE (CRC)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0) {
                        onConfirm(amount)
                    }
                },
                enabled = (amountText.toLongOrNull() ?: 0L) > 0
            ) {
                Text("REGISTRAR COBRO")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR") }
        }
    )
}

@Composable
fun PassengerRideItem(
    ride: RideRequestEntity,
    onSelect: () -> Unit,
    onCancel: () -> Unit
) {
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "Hace un momento" else "Hace $elapsedMins min"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Viaje a ${ride.destAddress}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = MeetColors.textMuted
                    )
                }

                val statusColor = when (ride.status) {
                    "OPEN" -> MeetColors.warning
                    "ACCEPTED" -> MeetColors.cyberCyan
                    "ARRIVED" -> MeetColors.electricBlue
                    "IN_PROGRESS" -> MeetColors.neonGreen
                    "COMPLETED" -> MeetColors.neonGreen
                    else -> MeetColors.error
                }

                Text(
                    text = ride.status,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Recogida: ${ride.pickupAddress}",
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val itemPreferences = remember(ride.fareBreakdownJson) {
                RidePassengerPreferences.fromJson(ride.fareBreakdownJson)
            }
            if (itemPreferences.hasSpecialPreferences) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemPreferences.toBadges().forEach { badge ->
                        Surface(
                            color = badge.color.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, badge.color.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(badge.icon, fontSize = 11.sp)
                                Text(
                                    badge.label,
                                    color = badge.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Oferta: ${ride.priceOffer.toInt()} ${ride.currency}",
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.neonGreen,
                    fontSize = 14.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ride.status in setOf("OPEN", "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED")) {
                        TextButton(
                            onClick = { onCancel() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Ver detalles 💬",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSelect() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverPerformanceCard(performance: RideDriverPerformance?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07131E)),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("TU DESEMPEÑO REAL", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill(
                    "Aceptación",
                    performance?.acceptanceRatePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Sin datos",
                )
                MetricPill(
                    "Finalizados",
                    performance?.completionRatePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Sin datos",
                )
            }
            Text(
                performance?.let {
                    "${it.offersAccepted}/${it.offersSubmitted} ofertas aceptadas · ${it.tripsCompleted} viajes completados"
                } ?: "Supabase aún no tiene actividad suficiente para calcular porcentajes.",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun DriverRideItem(
    ride: RideRequestEntity,
    currentGps: ObdViewModel.GpsLocationInfo?,
    isTrustedInvite: Boolean,
    isOwnRequest: Boolean = false,
    pendingOfferPrice: Double? = null,
    isPending: Boolean = false,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onOffer: () -> Unit,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
) {
    val orderedStops = remember(ride.stopsJson) {
        runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
            .getOrDefault(emptyList())
            .sortedBy(RideStopSnapshot::order)
    }
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "Hace un momento" else "Hace $elapsedMins min"

    val distanceText = remember(currentGps, ride) {
        if (currentGps != null) {
            val dist = calculateDistance(
                currentGps.latitude, currentGps.longitude,
                ride.pickupLatitude, ride.pickupLongitude
            )
            String.format(java.util.Locale.US, "📍 A %.1f km", dist)
        } else {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (isOwnRequest) MeetColors.neonGreen.copy(alpha = 0.6f) else MeetColors.borderSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with tags and quick dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isTrustedInvite) {
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
                        ) {
                            Text("⭐ CHOFER DE CONFIANZA", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (isOwnRequest) {
                        Surface(
                            color = MeetColors.cyberCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                        ) {
                            Text("🧪 TU SOLICITUD (MODO PRUEBA)", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "Pasajero: ${ride.passengerName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = timeText,
                            color = MeetColors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    distanceText?.let {
                        Text(
                            text = it,
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Ocultar esta solicitud",
                            tint = MeetColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Route points
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🟢", fontSize = 10.sp)
                    Text(
                        text = "Desde: ${ride.pickupAddress}",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                orderedStops.forEachIndexed { index, stop ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text("📍", fontSize = 10.sp)
                        Text(
                            text = "Parada ${index + 1}: ${stop.label}",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏁", fontSize = 10.sp)
                    Text(
                        text = "Hasta: ${ride.destAddress}",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fare and Action row
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pendingOfferPrice != null) {
                    Surface(
                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Column {
                                Text(
                                    "TU CONTRAOFERTA: ${pendingOfferPrice.toInt()} ${ride.currency}",
                                    color = MeetColors.cyberCyan,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    "Enviada al pasajero · Esperando respuesta...",
                                    color = MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }

                Column {
                    Text(
                        if (pendingOfferPrice != null) "TARIFA ORIGINAL DEL PASAJERO" else "PON TU PRECIO · OFERTA",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ride.priceOffer.toInt()} ${ride.currency}",
                        color = if (pendingOfferPrice != null) MeetColors.textSecondary else MeetColors.neonGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "💵 ${ride.paymentMethod} · ${orderedStops.size} parada(s)",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        if (ride.allowsInTripStops) {
                            "Paradas pueden cambiar; el estimado se recalcula"
                        } else {
                            "Paradas cerradas al publicar"
                        },
                        color = if (ride.allowsInTripStops) MeetColors.neonGreen else MeetColors.warning,
                        fontSize = 9.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = !isPending,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.5f),
                            disabledContentColor = Color.Black.copy(alpha = 0.7f),
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "CONFIRMANDO…",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                "ACEPTAR VIAJE (${ride.priceOffer.toInt()} ${ride.currency}) 🚕",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOffer,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(
                                if (pendingOfferPrice != null) "MODIFICAR OFERTA (${pendingOfferPrice.toInt()})" else "CONTRAOFERTA ⚡",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("RECHAZAR", color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverBiddingDialog(
    ride: RideRequestEntity,
    viewModel: ObdViewModel,
    onDismiss: () -> Unit,
    onAccepted: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val driverVer by viewModel.driverVerification.collectAsState()

    var counterPrice by remember(ride.requestId) { mutableDoubleStateOf(ride.priceOffer) }
    var selectedEta by remember { mutableIntStateOf(10) }
    var driverMsg by remember { mutableStateOf("") }

    val distanceText = remember(currentGps) {
        if (currentGps != null) {
            val dist = calculateDistance(
                currentGps!!.latitude, currentGps!!.longitude,
                ride.pickupLatitude, ride.pickupLongitude
            )
            String.format(java.util.Locale.US, "📍 A %.1f km de tu posición", dist)
        } else {
            "📍 Ubicación de recogida disponible"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDark),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PROPONER TARIFA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(distanceText, color = MeetColors.textSecondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                // Origin and Dest info
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🟢", fontSize = 11.sp)
                            Text("Desde: ${ride.pickupAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🏁", fontSize = 11.sp)
                            Text("Hasta: ${ride.destAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Tarifa sugerida por pasajero: ${ride.priceOffer.toInt()} ${ride.currency}",
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Counter price selector
                Text("Tu Tarifa Ofertada:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val step = if (ride.currency == "USD") 1.0 else 500.0
                                if (counterPrice > step) counterPrice -= step
                            },
                            modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                        ) {
                            Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${counterPrice.toInt()} ${ride.currency}",
                            color = MeetColors.neonGreen,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        IconButton(
                            onClick = {
                                val step = if (ride.currency == "USD") 1.0 else 500.0
                                counterPrice += step
                            },
                            modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                        ) {
                            Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick bid chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val offsets = if (ride.currency == "USD") listOf(0.0, 1.0, 2.0, 4.0) else listOf(0.0, 500.0, 1000.0, 2000.0)
                    offsets.forEach { offset ->
                        val total = ride.priceOffer + offset
                        val label = if (offset == 0.0) "Aceptar" else {
                            if (ride.currency == "USD") "+$${offset.toInt()}" else "+${offset.toInt()}"
                        }
                        Button(
                            onClick = { counterPrice = total },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (counterPrice == total) MeetColors.cyberCyan else MeetColors.cardBackground,
                                contentColor = if (counterPrice == total) Color.Black else Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                // ETA selector
                Text("Tiempo de llegada (ETA):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(5, 10, 15, 20).forEach { mins ->
                        Button(
                            onClick = { selectedEta = mins },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedEta == mins) MeetColors.cyberCyan else MeetColors.cardBackground,
                                contentColor = if (selectedEta == mins) Color.Black else Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("${mins} min", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                // Message field
                OutlinedTextField(
                    value = driverMsg,
                    onValueChange = { driverMsg = it },
                    label = { Text("Mensaje al cliente (opcional)") },
                    placeholder = { Text("Ej: Llevo aire acondicionado") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = MeetColors.borderSubtle
                    )
                )

                // Submit button (Direct Accept if price equals passenger offer, or Counter-Offer if altered)
                val isDirectAccept = counterPrice == ride.priceOffer
                Button(
                    onClick = {
                        if (isDirectAccept) {
                            viewModel.claimRideFirstCome(ride.requestId)
                            onDismiss()
                        } else {
                            viewModel.makeRideOffer(
                                requestId = ride.requestId,
                                counterPrice = counterPrice,
                                currency = ride.currency,
                                estArrivalMin = selectedEta,
                                message = driverMsg.takeIf { it.isNotBlank() }
                            )
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isDirectAccept) "ACEPTAR TARIFA (${ride.priceOffer.toInt()} ${ride.currency}) 🚕" else "🚀 ENVIAR CONTRAOFERTA (${counterPrice.toInt()} ${ride.currency})",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveRidePanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    isDriver: Boolean,
    onCloseRide: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offers by viewModel.rideOffers.collectAsState()
    val chatMessages by viewModel.rideChatMessages.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val playingPath by viewModel.isPlayingAudio.collectAsState()
    val presetMessages by viewModel.driverPresetMessages.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()
    val roadIncidents by viewModel.rideRoadIncidents.collectAsState()
    val speedSamplesByTrip by viewModel.rideSpeedSamples.collectAsState()
    val notificationPrincipal by viewModel.activePrincipal.collectAsState()
    val notificationOwner = remember(notificationPrincipal) { viewModel.currentUserId }
    val rideNotifications = remember(context, notificationOwner) { RideNotificationCoordinator(context, notificationOwner) }

    var chatInputText by remember { mutableStateOf("") }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showCancellationDialog by remember { mutableStateOf(false) }
    var showGuardianDialog by remember { mutableStateOf(false) }
    var showRoadReportDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showActiveStopsDialog by remember { mutableStateOf(false) }
    var showLostAndFoundDialog by remember { mutableStateOf(false) }
    val ratingRole = if (isDriver) "DRIVER" else "PASSENGER"
    val isRatingAlreadySettled = remember(ride.requestId, isDriver, ride.passengerRating, ride.driverRating) {
        viewModel.isRatingSettled(ride.requestId, ratingRole) ||
            (if (isDriver) ride.driverRating != null else ride.passengerRating != null)
    }
    var showCompletedSummary by remember(ride.requestId, isRatingAlreadySettled) {
        mutableStateOf(ride.status == "COMPLETED" && !isRatingAlreadySettled)
    }

    // ═══ Haptic feedback on ride state transitions ═══
    val haptic = LocalHapticFeedback.current
    var lastHapticState by remember(ride.requestId) { mutableStateOf(ride.serverState) }
    LaunchedEffect(ride.requestId, ride.serverState) {
        if (ride.serverState == lastHapticState) return@LaunchedEffect
        lastHapticState = ride.serverState
        val feedbackType = when (ride.serverState) {
            "ASSIGNED", "DRIVER_EN_ROUTE" -> HapticFeedbackType.LongPress
            "ARRIVED" -> HapticFeedbackType.LongPress
            "IN_PROGRESS" -> HapticFeedbackType.LongPress
            "COMPLETED" -> HapticFeedbackType.LongPress
            "CANCELLED" -> HapticFeedbackType.TextHandleMove
            else -> null
        }
        feedbackType?.let {
            runCatching { haptic.performHapticFeedback(it) }
        }
    }

    // ═══ Trigger missing notifications on state transitions ═══
    LaunchedEffect(ride.requestId, ride.serverState) {
        when (ride.serverState) {
            "ARRIVED" -> {
                if (!isDriver) {
                    rideNotifications.notifyDriverArrived(ride.requestId, ride.pickupAddress)
                }
            }
            "COMPLETED" -> {
                val fare = "${ride.finalPrice ?: ride.priceOffer} ${ride.currency}"
                rideNotifications.notifyTripCompleted(ride.requestId, fare)
                showCompletedSummary = true
            }
            "CANCELLED" -> {
                val cancelledBy = if (isDriver) "el pasajero" else "el conductor"
                rideNotifications.notifyTripCancelled(ride.requestId, cancelledBy, "")
            }
        }
    }
    var pendingActiveStops by remember(ride.requestId, ride.stopsJson) {
        mutableStateOf(
            runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
                .getOrDefault(emptyList()),
        )
    }
    var pinInput by remember { mutableStateOf("") }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startAudioRecording(context)
        } else {
            Toast.makeText(
                context,
                "El micrófono solo es necesario para enviar mensajes de voz.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            viewModel.sendRideChatImage(
                context = context,
                requestId = ride.requestId,
                senderId = if (isDriver) {
                    viewModel.driverVerification.value?.driverId.orEmpty()
                } else {
                    viewModel.passengerVerification.value?.passengerId.orEmpty()
                },
                senderName = if (isDriver) {
                    viewModel.driverVerification.value?.fullName.orEmpty()
                } else {
                    viewModel.passengerVerification.value?.fullName.orEmpty()
                },
                role = if (isDriver) "DRIVER" else "PASSENGER",
                source = it,
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.ridePinFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val pinVerification by remember(viewModel, ride.requestId) {
        viewModel.latestBoardingPinVerification(ride.requestId)
    }.collectAsState(initial = null)
    LaunchedEffect(isDriver, pinVerification?.updatedAt) {
        val command = pinVerification ?: return@LaunchedEffect
        if (isDriver && command.status in setOf("FAILED", "CONFLICT", "DEAD_LETTER")) {
            Toast.makeText(
                context,
                command.lastErrorMessage ?: "No se pudo verificar el PIN. Intenta de nuevo.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.rideSafetyFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.rideRoadReportFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(isDriver, ride.requestId, ride.status, ride.boardingPin) {
        if (!isDriver && ride.status == "ARRIVED" && ride.boardingPin == null) {
            viewModel.issueRideBoardingPin(ride.requestId)
        }
    }
    val driverVer by viewModel.driverVerification.collectAsState()
    val passengerVer by viewModel.passengerVerification.collectAsState()
    val myDriverId = driverVer?.driverId
    val myPassengerId = passengerVer?.passengerId

    val myId = if (isDriver) myDriverId else myPassengerId
    val myName = if (isDriver) driverVer?.fullName else passengerVer?.fullName
    val myRole = if (isDriver) "DRIVER" else "PASSENGER"

    if (myId == null || myName.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetColors.backgroundDark)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No se puede abrir este viaje sin una identidad verificada.",
                color = MeetColors.warning,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val acceptedOffer = remember(offers, ride.acceptedOfferId) {
        offers.firstOrNull {
            it.offerId == ride.acceptedOfferId || it.status == "ACCEPTED"
        }
    }
    val orderedStops = remember(ride.stopsJson) {
        runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
            .getOrDefault(emptyList())
            .sortedBy(RideStopSnapshot::order)
    }
    val navTargets = remember(
        ride.status,
        ride.pickupLatitude,
        ride.pickupLongitude,
        ride.pickupAddress,
        ride.destLatitude,
        ride.destLongitude,
        ride.destAddress,
        orderedStops,
    ) {
        when (ride.status) {
            "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED" -> {
                Triple(
                    ride.pickupLatitude,
                    ride.pickupLongitude,
                    "Recogida: ${ride.pickupAddress.ifBlank { "Punto del pasajero" }}",
                )
            }
            else -> {
                val pendingStop = orderedStops.firstOrNull { it.latitude != null && it.longitude != null }
                if (pendingStop != null) {
                    Triple(
                        requireNotNull(pendingStop.latitude),
                        requireNotNull(pendingStop.longitude),
                        "Parada ${pendingStop.order}: ${pendingStop.label.ifBlank { "Parada intermedia" }}",
                    )
                } else {
                    Triple(
                        ride.destLatitude,
                        ride.destLongitude,
                        "Destino: ${ride.destAddress.ifBlank { "Destino final" }}",
                    )
                }
            }
        }
    }
    val navTargetLat = navTargets.first
    val navTargetLng = navTargets.second
    val navTargetLabel = navTargets.third
    val navPhaseLabel = when (ride.status) {
        "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED" -> "IR POR EL PASAJERO"
        else -> {
            val pendingStop = orderedStops.firstOrNull { it.latitude != null && it.longitude != null }
            if (pendingStop != null) "PARADA ${pendingStop.order}" else "DESTINO FINAL"
        }
    }
    val pickupPoint = remember(ride) {
        rideGeoPointOrNull(
            latitude = ride.pickupLatitude,
            longitude = ride.pickupLongitude,
            accuracyMeters = ride.pickupAccuracy,
            capturedAtEpochMs = ride.createdAt,
        )
    }
    val destinationPoint = remember(ride) {
        if (ride.destLatitude == 0.0 && ride.destLongitude == 0.0) {
            null
        } else {
            rideGeoPointOrNull(
                latitude = ride.destLatitude,
                longitude = ride.destLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = ride.createdAt,
            )
        }
    }
    val localPoint = currentGps?.let {
        rideGeoPointOrNull(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyMeters = it.accuracy,
            capturedAtEpochMs = it.timestamp.coerceAtLeast(0L),
        )
    }
    val acceptedDriverPoint = acceptedOffer
        ?.takeIf { it.driverLatitude != 0.0 || it.driverLongitude != 0.0 }
        ?.let {
            rideGeoPointOrNull(
                latitude = it.driverLatitude,
                longitude = it.driverLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = it.createdAt,
            )
        }
    val arrivalDecision = remember(localPoint, pickupPoint, ride.status) {
        pickupPoint?.let {
            RideArrivalPolicy.evaluate(
                driver = localPoint,
                pickup = it,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }
    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }
    val activePlaceSearchProvider = remember {
        resilientRidePlaceSearchProvider(
            primaryEndpoint = BuildConfig.RIDE_GEOCODER_URL,
            fallbackEndpoint = BuildConfig.RIDE_GEOCODER_FALLBACK_URL,
        )
    }
    var activeRoadRoute by remember(ride.requestId) {
        mutableStateOf<RideRoadRoute?>(null)
    }
    var activeRouteUnavailable by remember(ride.requestId) {
        mutableStateOf(false)
    }
    LaunchedEffect(ride.requestId, ride.status, pickupPoint, localPoint, orderedStops, destinationPoint) {
        val pickup = if (ride.status == "IN_PROGRESS") localPoint else pickupPoint
        val destination = destinationPoint
        val relevantStops = orderedStops
        val resolvedStops = relevantStops.mapNotNull { stop ->
            if (!stop.isResolved) null else rideGeoPointOrNull(
                latitude = requireNotNull(stop.latitude),
                longitude = requireNotNull(stop.longitude),
                accuracyMeters = null,
                capturedAtEpochMs = ride.createdAt,
            )
        }
        if (
            pickup == null ||
            destination == null ||
            resolvedStops.size != relevantStops.size
        ) {
            activeRoadRoute = null
            activeRouteUnavailable = false
            return@LaunchedEffect
        }
        val result = runCatching {
            routingProvider.route(listOf(pickup) + resolvedStops + destination)
        }
        activeRoadRoute = result.getOrNull()
        activeRouteUnavailable = result.isFailure
    }
    val mapState = remember(
        isDriver,
        pickupPoint,
        orderedStops,
        destinationPoint,
        localPoint,
        acceptedDriverPoint,
        roadIncidents,
        activeRoadRoute,
    ) {
        val baseState = RideMapStateFactory.create(
            passengerGps = if (isDriver) null else localPoint,
            pickup = pickupPoint,
            stops = orderedStops.mapNotNull { stop ->
                if (!stop.isResolved) null else rideGeoPointOrNull(
                    latitude = requireNotNull(stop.latitude),
                    longitude = requireNotNull(stop.longitude),
                    accuracyMeters = null,
                    capturedAtEpochMs = ride.createdAt,
                )
            },
            destination = destinationPoint,
            driverGps = if (isDriver) localPoint else acceptedDriverPoint,
            route = activeRoadRoute?.geometry,
        )
        baseState.copy(
            markers = baseState.markers + roadIncidents
                .filterNot { it.isExpired(System.currentTimeMillis()) }
                .map { incident ->
                    RideMapMarker(
                        id = "incident-${incident.id}",
                        role = RideMarkerRole.ROAD_INCIDENT,
                        point = RideGeoPoint(
                            latitude = incident.latitude,
                            longitude = incident.longitude,
                            accuracyMeters = incident.accuracyMeters,
                            capturedAtEpochMs = incident.createdAtEpochMs,
                        ),
                        label = incident.type.rideRoadLabel(),
                    )
                },
        )
    }
    val collaborativeEta = remember(
        ride.estimatedDistanceKm,
        ride.estimatedDurationMin,
        activeRoadRoute,
        roadIncidents,
        speedSamplesByTrip,
    ) {
        val distanceMeters = activeRoadRoute?.distanceMeters
            ?: ride.estimatedDistanceKm.coerceAtLeast(0.0) * 1_000.0
        val baselineSeconds = activeRoadRoute?.durationSeconds
            ?: ride.estimatedDurationMin.coerceAtLeast(1) * 60.0
        if (distanceMeters <= 0.0) {
            null
        } else {
            RideCollaborativeEtaEstimator.estimate(
                segments = listOf(
                    RideEtaSegment(
                        id = "active-route",
                        distanceMeters = distanceMeters,
                        baselineSpeedMetersPerSecond =
                            (distanceMeters / baselineSeconds).coerceAtLeast(1.4),
                        bearingDegrees = currentGps?.bearing,
                        speedSamples = speedSamplesByTrip[ride.requestId].orEmpty(),
                        incidents = roadIncidents
                            .filterNot { it.isExpired(System.currentTimeMillis()) }
                            .map { it.copy(roadSegmentId = "active-route") },
                    ),
                ),
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }
    LaunchedEffect(isDriver, ride.requestId, ride.status, collaborativeEta?.durationSeconds) {
        if (isDriver && ride.status == "IN_PROGRESS") {
            collaborativeEta?.durationSeconds?.let { seconds ->
                rideNotifications.notifyDestinationEtaSevenMinutes(ride.requestId, seconds)
            }
        }
    }
    val roadReportAvailability = remember(
        isDriver,
        ride.status,
        ride.serverState,
        ride.serverVersion,
        currentGps != null,
    ) {
        RideRoadReportAvailabilityPolicy.evaluate(
            isDriver = isDriver,
            localStatus = ride.status,
            serverState = ride.serverState,
            serverVersion = ride.serverVersion,
            hasCurrentGps = currentGps != null,
        )
    }
    LaunchedEffect(roadReportAvailability.allowed) {
        if (!roadReportAvailability.allowed) showRoadReportDialog = false
    }

    if (showRoadReportDialog && roadReportAvailability.allowed) {
        RideRoadReportDialog(
            onDismiss = { showRoadReportDialog = false },
            onReport = { type, side, severity ->
                viewModel.reportRideRoadIncident(
                    tripId = ride.requestId,
                    type = type,
                    side = side,
                    severity = severity,
                )
                showRoadReportDialog = false
            },
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = Color(0xFF071019),
            title = { Text("CONFIRMAR PASAJERO", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Solicita al pasajero su código privado de cuatro dígitos.",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(4) },
                        label = { Text("PIN del viaje") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyRideBoardingPin(ride.requestId, pinInput)
                        pinInput = ""
                        showPinDialog = false
                    },
                    enabled = pinInput.length == 4,
                ) {
                    Text("VERIFICAR E INICIAR ABORDAJE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancelar") } },
        )
    }

    if (showActiveStopsDialog && !isDriver) {
        AlertDialog(
            onDismissRequest = { showActiveStopsDialog = false },
            containerColor = Color(0xFF071019),
            title = {
                Text(
                    "ACTUALIZAR PARADAS",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La ruta y el estimado se recalcularán. El total definitivo seguirá el tiempo y la distancia reales.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                    RideStopsEditor(
                        stops = pendingActiveStops,
                        provider = activePlaceSearchProvider,
                        biasLatitude = currentGps?.latitude,
                        biasLongitude = currentGps?.longitude,
                        onStopsChanged = {
                            pendingActiveStops = RideTripPlanPolicy.normalize(it)
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pendingActiveStops.all(RideStopSnapshot::isResolved),
                    onClick = {
                        val origin = if (ride.status == "IN_PROGRESS") localPoint else pickupPoint
                        val destination = destinationPoint
                        if (origin == null || destination == null) {
                            Toast.makeText(context, "Ubicación de ruta incompleta", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val stopPoints = pendingActiveStops.mapNotNull { stop ->
                                if (!stop.isResolved) null else rideGeoPointOrNull(
                                    requireNotNull(stop.latitude),
                                    requireNotNull(stop.longitude),
                                    null,
                                    System.currentTimeMillis(),
                                )
                            }
                            val route = runCatching {
                                routingProvider.route(listOf(origin) + stopPoints + destination)
                            }.getOrNull()
                            if (route == null) {
                                Toast.makeText(
                                    context,
                                    "No se pudo verificar una ruta vial real; no se guardó el cambio.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            viewModel.replaceRideStops(
                                requestId = ride.requestId,
                                stopsJson = Json.encodeToString(pendingActiveStops),
                                estimatedDistanceMeters = route.distanceMeters.toLong(),
                                estimatedDurationSeconds = route.durationSeconds.toLong(),
                            )
                            showActiveStopsDialog = false
                        }
                    },
                ) {
                    Text("RECALCULAR Y GUARDAR", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveStopsDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showCancellationDialog) {
        AuthoritativeRideCancellationDialog(
            viewModel, ride.requestId,
            if (isDriver) RideActorRole.DRIVER else RideActorRole.PASSENGER,
        ) { showCancellationDialog = false }
    }
    if (showGuardianDialog) {
        RideGuardianDialog(
            onDismiss = { showGuardianDialog = false },
            onConfirm = { signalType, detail ->
                viewModel.activateRideGuardian(
                    requestId = ride.requestId,
                    signalType = signalType,
                    detail = detail,
                )
                showGuardianDialog = false
            },
            onShareTrip = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Elysium Guardian · Viaje ${ride.requestId.take(8)} · Estado ${ride.status}. " +
                            "No contiene teléfono ni ubicación exacta.",
                    )
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Compartir estado del viaje"),
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        "No hay una aplicación disponible para compartir.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onOpenEmergencyDialer = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL))
                }.onFailure {
                    Toast.makeText(
                        context,
                        "No se pudo abrir el marcador del dispositivo.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Ride Status Header
        val statusColor = when (ride.status) {
            "PENDING_PUBLICATION" -> MeetColors.warning
            "OPEN" -> MeetColors.warning
            "ACCEPTED" -> MeetColors.cyberCyan
            "ARRIVED" -> MeetColors.electricBlue
            "PASSENGER_ONBOARD" -> MeetColors.neonGreen
            "IN_PROGRESS" -> MeetColors.neonGreen
            "COMPLETED" -> MeetColors.neonGreen
            "EXPIRED" -> MeetColors.textMuted
            else -> MeetColors.error
        }
        val statusLabel = when (ride.status) {
            "PENDING_PUBLICATION" -> "Confirmando publicación…"
            "OPEN" -> if (ride.serverVersion > 0L && ride.syncState == "SYNCED") {
                "Buscando Chofer ⏳"
            } else {
                "Confirmando publicación…"
            }
            "ACCEPTED" -> "Chofer en Camino 🚕"
            "ARRIVED" -> "Chofer en el Punto 📍"
            "PASSENGER_ONBOARD" -> "Pasajero confirmado 🔐"
            "IN_PROGRESS" -> "Viaje en Curso 🏁"
            "COMPLETED" -> "Viaje Completado 🎉"
            "CANCELLED" -> "Viaje Cancelado ❌"
            "EXPIRED" -> "Solicitud Expirada ⏰"
            else -> ride.status
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ═══ Pulsing live indicator ═══
                        val isLiveState = ride.status in setOf(
                            "OPEN", "ACCEPTED", "DRIVER_EN_ROUTE",
                            "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS"
                        )
                        if (isLiveState) {
                            val pulseTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 800),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "pulse_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = pulseAlpha))
                            )
                        }
                        Text(
                            text = "Estado: $statusLabel",
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 15.sp
                        )
                    }

                    if (ride.status == "PENDING_PUBLICATION" && ride.serverVersion <= 0L &&
                        ride.syncState == "PENDING"
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.localCancelStuckRide(ride.requestId)
                                onCloseRide()
                            },
                        ) {
                            Text(
                                "Cancelar",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    IconButton(onClick = onCloseRide) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Desde: ${ride.pickupAddress}",
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hasta: ${ride.destAddress}",
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                orderedStops.forEach { stop ->
                    Text(
                        text = "Parada ${stop.order}: ${stop.label}",
                        fontSize = 11.sp,
                        color = MeetColors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Pago: ${when (ride.paymentMethod) {
                        "SINPE_MOVIL" -> "SINPE"
                        "SINPE" -> "SINPE"
                        "CASH" -> "Efectivo"
                        else -> "No confirmado"
                    }} · " +
                        if (ride.fareMode == RideFareMode.METERED_TIME_DISTANCE.name) {
                            "Estimado actual: ${ride.estimatedFareMinor} CRC"
                        } else {
                            "Oferta aceptada: ${ride.finalPrice ?: ride.priceOffer} ${ride.currency}"
                        },
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                val activeRidePrefs = remember(ride.fareBreakdownJson) {
                    RidePassengerPreferences.fromJson(ride.fareBreakdownJson)
                }
                if (activeRidePrefs.hasSpecialPreferences) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeRidePrefs.toBadges().forEach { badge ->
                            Surface(
                                color = badge.color.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, badge.color.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(badge.icon, fontSize = 11.sp)
                                    Text(
                                        badge.label,
                                        color = badge.color,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
                if (ride.status == "IN_PROGRESS" && ride.fareMode == RideFareMode.METERED_TIME_DISTANCE.name) {
                    Spacer(Modifier.height(6.dp))
                    LiveRideMetrics(
                        ride = ride,
                        currentSpeed = (currentGps?.speed ?: 0f) * 3.6f,
                        activeRouteMeters = activeRoadRoute?.distanceMeters?.toLong(),
                        ownerId = notificationPrincipal.id,
                    )
                }
                if (!isDriver && ride.status !in setOf("COMPLETED", "CANCELLED")) {
                    if (ride.allowsInTripStops) {
                        OutlinedButton(
                            onClick = { showActiveStopsDialog = true },
                            border = BorderStroke(1.dp, MeetColors.neonGreen),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Default.AddLocation, null)
                            Spacer(Modifier.width(8.dp))
                            Text("AÑADIR O CAMBIAR PARADAS", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Text(
                            "🔒 Pon tu precio: las paradas quedaron cerradas al publicar.",
                            color = MeetColors.warning,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                collaborativeEta?.let { estimate ->
                    val minutes = ((estimate.durationSeconds + 59) / 60).coerceAtLeast(1)
                    val evidence = when (estimate.evidenceLevel) {
                        RideEtaEvidenceLevel.BASELINE_ONLY -> "ruta base"
                        RideEtaEvidenceLevel.LIVE_SPEED -> "velocidad reciente"
                        RideEtaEvidenceLevel.COMMUNITY_CORROBORATED -> "tráfico colaborativo"
                    }
                    Text(
                        text = "ETA Elysium: $minutes min · $evidence",
                        color = if (estimate.blockingSegmentIds.isEmpty()) {
                            MeetColors.cyberCyan
                        } else {
                            MeetColors.error
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (estimate.blockingSegmentIds.isNotEmpty()) {
                        Text(
                            text = "Cierre corroborado: recalcular ruta antes de continuar.",
                            color = MeetColors.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (ride.status == "COMPLETED") {
                    val finalFare = com.elysium369.meet.ride.domain.RideFinalFarePresentation.from(
                        ride.priceOfferMinor, ride.finalPriceMinor, ride.currency,
                        ride.serverState, ride.serverVersion,
                    )
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "DESGLOSE FINAL",
                                color = MeetColors.neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "Tarifa ofrecida: ${finalFare.offered}",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                            Text(
                                "Diferencia respecto a tarifa ofrecida: ${finalFare.difference}",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                            Text(
                                "TOTAL: ${finalFare.total} · " + when (ride.paymentMethod) {
                                    "SINPE_MOVIL" -> "SINPE"
                                    "SINPE" -> "SINPE"
                                    "CASH" -> "Efectivo"
                                    "UNKNOWN" -> "Pendiente de confirmación"
                                    else -> "Pendiente de confirmación"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                if (ride.status == "COMPLETED") {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.exportGpsForensicTrail(ride.requestId) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                            contentColor = MeetColors.cyberCyan,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("EXPORTAR TRAZA GPS FORENSE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isDriver && ride.status in listOf("ACCEPTED", "ARRIVED")) {
                    ride.boardingPin?.let { pin ->
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MeetColors.neonGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("TU PIN PRIVADO DE ABORDAJE", color = MeetColors.textMuted, fontSize = 9.sp)
                                Text(
                                    pin.chunked(1).joinToString("  "),
                                    color = MeetColors.neonGreen,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text("Díselo únicamente al conductor asignado", color = MeetColors.warning, fontSize = 9.sp)
                            }
                        }
                    }
                    if (ride.boardingPin == null && ride.status == "ACCEPTED") {
                        Surface(
                            color = MeetColors.cyberCyan.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PIN DE ABORDAJE PROTEGIDO", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                                Text(
                                    "Se mostrará automáticamente cuando el conductor llegue al pin de recogida.",
                                    color = MeetColors.textMuted,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                if (isDriver && ride.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                if (ride.status == "ACCEPTED" && ride.serverState == "ASSIGNED") {
                                    viewModel.updateRideStatus(ride.requestId, "DRIVER_EN_ROUTE")
                                }
                                viewModel.openWaze(context, navTargetLat, navTargetLng, navTargetLabel)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF062235)),
                        border = BorderStroke(1.5.dp, Color(0xFF33CCFF)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = Color(0xFF33CCFF),
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🧭", fontSize = 20.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        "NAVEGAR CON WAZE",
                                        color = Color(0xFF33CCFF),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                    )
                                    Surface(
                                        color = Color(0xFF33CCFF).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            navPhaseLabel,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    navTargetLabel,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Navegar con Waze",
                                tint = Color(0xFF33CCFF),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Actions according to state and role
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDriver) {
                        when (ride.status) {
                            "ACCEPTED", "DRIVER_EN_ROUTE" -> {
                                Button(
                                    onClick = { viewModel.updateRideStatus(ride.requestId, "ARRIVED") },
                                    enabled = ((ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L &&
                                        (ride.serverState == "ASSIGNED" || arrivalDecision?.allowed == true))),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text(
                                        if (ride.serverState == "ASSIGNED") {
                                            "INICIAR RUTA 🚗"
                                        } else {
                                            arrivalDecision?.distanceMeters?.let { "YA LLEGUÉ · ${it.toInt()} m" }
                                                ?: "YA LLEGUÉ · GPS"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (ride.serverState == "DRIVER_EN_ROUTE" && arrivalDecision?.allowed != true) {
                                    Text(
                                        arrivalDecision?.reason ?: "Acércate al pin de recogida",
                                        color = MeetColors.warning,
                                        fontSize = 9.sp,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "ARRIVED" -> {
                                Button(
                                    onClick = { showPinDialog = true },
                                    enabled = (ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Ingresar PIN 🔐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "PASSENGER_ONBOARD" -> {
                                Button(
                                    onClick = {
                                        viewModel.updateRideStatus(ride.requestId, "IN_PROGRESS")
                                    },
                                    enabled = (ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1.2f),
                                ) {
                                    Text("INICIAR SERVICIO 🏁", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "IN_PROGRESS" -> {
                                Button(
                                    onClick = {
                                        viewModel.updateRideStatus(ride.requestId, "COMPLETED")
                                    },
                                    enabled = (ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Completar Viaje ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Pasajero — SIEMPRE pasa por el backend (Supabase RPC)
                        if (ride.status in listOf("PENDING_PUBLICATION", "OPEN")) {
                            Button(
                                onClick = { showCancellationDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Cancelar Viaje", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (ride.status == "ARRIVED" && ride.boardingPin == null) {
                            Button(
                                onClick = {
                                    viewModel.issueRideBoardingPin(ride.requestId)
                                },
                                enabled = true,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeetColors.neonGreen,
                                    contentColor = Color.Black,
                                ),
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text(
                                    "GENERAR PIN 🔐",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        if (ride.status == "COMPLETED" && ride.passengerRating == null) {
                            Button(
                                onClick = { showRatingDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Calificar Chofer ⭐", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (ride.status == "COMPLETED" && ride.passengerRating != null && ride.tipAmountMinor == null) {
                            var showTipDialog by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showTipDialog = true },
                                border = BorderStroke(1.dp, MeetColors.neonGreen),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Text("Propina 💚", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (showTipDialog) {
                                TipDialog(
                                    currency = ride.currency,
                                    onDismiss = { showTipDialog = false },
                                    onConfirm = { tipMinor, deliveryMethod ->
                                        showTipDialog = false
                                        viewModel.submitTip(ride.requestId, tipMinor, ride.currency, deliveryMethod)
                                    },
                                )
                            }
                        }
                        if (ride.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED")) {
                            Button(
                                onClick = { showCancellationDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("Cancelar Viaje ❌", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = .72f)),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                RideMapPanel(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                    userLocation = currentGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, System.currentTimeMillis()) },
                    onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .size(68.dp),
                    shape = CircleShape,
                    color = Color(0xEE07131E),
                    border = BorderStroke(2.dp, MeetColors.cyberCyan),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            ((currentGps?.speed ?: 0f) * 3.6f).toInt().toString(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text("km/h", color = MeetColors.cyberCyan, fontSize = 9.sp)
                    }
                }
                if (isDriver && ride.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable {
                                if (ride.status == "ACCEPTED" && ride.serverState == "ASSIGNED") {
                                    viewModel.updateRideStatus(ride.requestId, "DRIVER_EN_ROUTE")
                                }
                                viewModel.openWaze(context, navTargetLat, navTargetLng, navTargetLabel)
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xEE071A28),
                        border = BorderStroke(1.5.dp, Color(0xFF33CCFF)),
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🧭", fontSize = 14.sp)
                            Text("WAZE", color = Color(0xFF33CCFF), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Text(
            text = if (isDriver && mapState.marker(com.elysium369.meet.ride.map.RideMarkerRole.PASSENGER_GPS) == null) {
                "GPS exacto del pasajero: esperando sincronización autenticada."
            } else {
                "U: pasajero · R: recogida · P: parada · D: destino · C: conductor"
            },
            color = MeetColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        Text(
            text = when {
                activeRoadRoute != null -> {
                    val route = requireNotNull(activeRoadRoute)
                    val km = route.distanceMeters / 1_000.0
                    val roundedKm = kotlin.math.round(km * 10.0) / 10.0
                    val minutes = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                    "Ruta vial $roundedKm km · $minutes min · ${route.attribution}"
                }
                activeRouteUnavailable ->
                    "Ruta vial temporalmente no disponible; el mapa conserva puntos reales sin unirlos con una línea falsa."
                else -> "Esperando puntos suficientes para calcular la ruta vial."
            },
            color = if (activeRouteUnavailable) MeetColors.warning else MeetColors.textMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
        )
        activeRoadRoute?.let { route ->
            val etaMinutes = kotlin.math.ceil(route.durationSeconds / 60.0)
                .toInt()
                .coerceAtLeast(1)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MeetColors.cyberCyan.copy(alpha = 0.12f),
                ),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.72f)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⏱", fontSize = 21.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ETA CALCULADO AUTOMÁTICAMENTE",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "Aproximadamente $etaMinutes min por la ruta recomendada",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Se actualiza al cambiar origen, destino o estado del viaje.",
                            color = MeetColors.textMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
        if (roadReportAvailability.allowed) {
            OutlinedButton(
                onClick = { showRoadReportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, Color(0xFFFF2D55)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B81)),
            ) {
                Icon(Icons.Default.ReportProblem, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("REPORTAR CONDICIÓN DE LA VÍA", fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        } else if (isDriver && ride.status in setOf("ARRIVED", "PASSENGER_ONBOARD")) {
            Text(
                text = "Los reportes viales aparecerán al iniciar la ruta confirmada.",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        OutlinedButton(
            onClick = { showGuardianDialog = true },
            enabled = ride.serverVersion > 0L &&
                ride.serverState in setOf(
                    "ASSIGNED",
                    "DRIVER_EN_ROUTE",
                    "ARRIVED",
                    "PASSENGER_ONBOARD",
                    "IN_PROGRESS",
                ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, Color(0xFFFF2D55)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B81)),
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ELYSIUM GUARDIAN · SEGURIDAD", fontWeight = FontWeight.Black, fontSize = 11.sp)
        }

        OutlinedButton(
            onClick = {
                val session = viewModel.rideLiveSharingEngine.createSession(
                    rideId = ride.requestId,
                    passengerId = ride.passengerId,
                    passengerName = "Pasajero Elysium",
                    driverName = ride.assignedDriverId ?: "Conductor Asignado",
                    vehicleDescription = "Vehículo Verificado",
                    vehiclePlate = "Elysium",
                    pickupName = ride.pickupAddress,
                    dropoffName = ride.destAddress,
                )
                val shareText = "🚗 Sigue mi viaje en tiempo real con seguridad certificada Elysium:\n" +
                    "ID: ${ride.requestId.take(8)}\n" +
                    "Token SHA-256: ${session.shareToken.take(16)}...\n" +
                    "Destino: ${ride.destAddress}"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Compartir ruta en vivo"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, MeetColors.cyberCyan),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("COMPARTIR RUTA EN VIVO · SHA-256", fontWeight = FontWeight.Black, fontSize = 11.sp)
        }

        // Partner identity is only authoritative after the server has assigned a
        // concrete driver. A local pending publication must never render a
        // generic driver as if one had accepted the ride.
        val hasAuthoritativeAssignment = ride.serverVersion > 0L &&
            !ride.assignedDriverId.isNullOrBlank() &&
            ride.serverState in setOf(
                "ASSIGNED",
                "DRIVER_EN_ROUTE",
                "ARRIVED",
                "PASSENGER_ONBOARD",
                "IN_PROGRESS",
            )
        val acceptedOffer = remember(offers) {
            offers.firstOrNull { it.status == "ACCEPTED" }
        }
        if (hasAuthoritativeAssignment) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isDriver) {
                        Text(
                            text = "🧑🏻‍💻 PASAJERO ASIGNADO",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ride.passengerName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "Contacto protegido: usa chat o nota de voz dentro del viaje.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    } else {
                        // Pasajero viendo chofer
                        Text(
                            text = "👨🏻‍✈️ TU CHOFER ASIGNADO",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ride.assignedDriverName
                                        ?: acceptedOffer?.driverName?.takeUnless { it == "Conductor" }
                                        ?: "Conductor verificado",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = ride.assignedDriverVehicle
                                        ?: acceptedOffer?.vehicleDescription
                                        ?: "Vehículo: dato no capturado",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp
                                )
                                if (acceptedOffer != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (acceptedOffer.driverTotalTrips > 0) {
                                            "⭐ ${acceptedOffer.driverRating} (${acceptedOffer.driverTotalTrips} viajes)"
                                        } else {
                                            "Conductor nuevo · sin historial de viajes capturado"
                                        },
                                        color = MeetColors.warning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // ═══ Driver ETA chip for passenger ═══
                                if (ride.serverState in setOf("DRIVER_EN_ROUTE", "ASSIGNED") && acceptedDriverPoint != null && pickupPoint != null) {
                                    val etaDistKm = calculateDistance(
                                        acceptedDriverPoint.latitude, acceptedDriverPoint.longitude,
                                        pickupPoint.latitude, pickupPoint.longitude,
                                    )
                                    val etaMinutes = (etaDistKm / 0.5).toInt().coerceAtLeast(1) // ~30 km/h city avg
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MeetColors.cyberCyan.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("🕐", fontSize = 14.sp)
                                            Text(
                                                text = "ETA: ~$etaMinutes min · ${String.format(java.util.Locale.US, "%.1f", etaDistKm)} km",
                                                color = MeetColors.cyberCyan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }

                        }
                        Text(
                            text = "Contacto protegido: usa chat o nota de voz dentro del viaje.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenMessages,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = .75f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.electricBlue),
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CHAT SEGURO", fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.startRideCall(ride.requestId, if (isDriver) "DRIVER" else "PASSENGER")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("LLAMADA EN VIVO", fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                    }
                    Text(
                        text = "Voz en vivo y mensajería encriptada vía Supabase Realtime y Red Mesh Local sin exponer números.",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

        if (!isDriver && hasAuthoritativeAssignment) {
            RidePassengerTrustCard(
                vehicleDescription = ride.assignedDriverVehicle
                    ?: acceptedOffer?.vehicleDescription,
                sharedCategories = sharingSelections[ride.requestId].orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ═══ EXPIRED state feedback ═══
        if (ride.status == "EXPIRED") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.textMuted),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏰", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Solicitud Expirada",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tu solicitud no fue aceptada a tiempo. Puedes crear una nueva solicitud.",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCloseRide,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("CERRAR", color = MeetColors.backgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ═══ COMPLETED: Payment confirmation + Receipt + Support ═══
        if (ride.status == "COMPLETED") {
            val paymentConfirmed = ride.syncState == "PAYMENT_CONFIRMED" ||
                ride.serverState == "PAYMENT_CONFIRMED"
            if (isDriver && !paymentConfirmed) {
                var sinpeRef by remember { mutableStateOf("") }
                val isSinpe = ride.paymentMethod == "SINPE_MOVIL" || ride.paymentMethod == "SINPE"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.warning.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, MeetColors.warning),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CONFIRMAR PAGO",
                            color = MeetColors.warning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            when (ride.paymentMethod) {
                                "CASH" -> "Confirmar que el pasajero pagó en efectivo"
                                "SINPE_MOVIL", "SINPE" -> "Confirmar que recibiste el pago por SINPE Móvil"
                                else -> "Confirmar que el pago fue procesado"
                            },
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                        )
                        if (isSinpe) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sinpeRef,
                                onValueChange = { sinpeRef = it.filter { c -> c.isDigit() } },
                                label = { Text("N.° de referencia SINPE (opcional)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.warning,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.confirmRidePayment(
                                        ride.requestId,
                                        sinpeRef.takeIf { it.isNotBlank() },
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("PAGO RECIBIDO", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Ride receipt / summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "RESUMEN DEL VIAJE",
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MeetColors.borderSubtle)
                    ReceiptRow("Origen", ride.pickupAddress)
                    ReceiptRow("Destino", ride.destAddress)
                    if (orderedStops.isNotEmpty()) {
                        ReceiptRow("Paradas", "${orderedStops.size} parada(s)")
                    }
                    ReceiptRow("Distancia", "${String.format(java.util.Locale.US, "%.1f", ride.estimatedDistanceKm)} km")
                    ReceiptRow("Duración", "${ride.estimatedDurationMin} min")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MeetColors.borderSubtle)
                    ReceiptRow(
                        "Pago",
                        when (ride.paymentMethod) {
                            "CASH" -> "Efectivo"
                            "SINPE_MOVIL", "SINPE" -> "SINPE Móvil"
                            else -> "No especificado"
                        }
                    )
                    ReceiptRow(
                        "Tarifa",
                        if (ride.fareMode == RideFareMode.METERED_TIME_DISTANCE.name) "Tiempo + Distancia" else "Pon tu precio"
                    )
                    Text(
                        "${ride.currency} ${ride.finalPrice ?: ride.priceOffer}",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                    if (ride.tipAmountMinor != null && ride.tipAmountMinor > 0) {
                        ReceiptRow("Propina", "+${ride.currency} ${ride.tipAmountMinor}")
                    }
                }
            }
        }

        // ═══ Support case & Lost & Found buttons ═══
        if (ride.status in listOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS", "COMPLETED")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ride.status != "COMPLETED") {
                    OutlinedButton(
                        onClick = { showCancellationDialog = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.warning),
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SOPORTE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = { showLostAndFoundDialog = true },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OBJETO OLVIDADO", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }

        // Bids / Negotiation Panel if status is OPEN
        if (ride.status == "OPEN") {
            if (isDriver) {
                DriverNegotiationPanel(
                    viewModel = viewModel,
                    ride = ride,
                    offers = offers,
                    onCloseRide = onCloseRide
                )
            } else {
                PassengerLiveOffersPanel(
                    viewModel = viewModel,
                    ride = ride,
                    offers = offers,
                    onCloseRide = onCloseRide,
                )
            }
        }
    }

    if (showLostAndFoundDialog) {
        val myId = viewModel.currentUserId ?: if (isDriver) ride.assignedDriverId.orEmpty() else ride.passengerId
        RideLostAndFoundDialog(
            ride = ride,
            isDriver = isDriver,
            onDismiss = { showLostAndFoundDialog = false },
            onSendMessage = { text ->
                viewModel.sendRideChatMessage(
                    requestId = ride.requestId,
                    senderId = myId,
                    senderName = if (isDriver) (ride.assignedDriverName ?: "Chofer") else (ride.passengerName ?: "Pasajero"),
                    role = if (isDriver) "DRIVER" else "PASSENGER",
                    text = text,
                )
            },
            onOpenChat = onOpenMessages,
        )
    }

    if (showRatingDialog) {
        var ratingStars by remember { mutableDoubleStateOf(5.0) }
        var ratingComment by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            containerColor = MeetColors.backgroundDeep,
            title = {
                Text(
                    text = if (isDriver) "Calificar al Pasajero" else "Calificar al Conductor",
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¡Tu opinión ayuda a mantener la comunidad segura!", color = MeetColors.textSecondary)

                    // 5 Star rating selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val active = ratingStars >= i.toDouble()
                            IconButton(onClick = { ratingStars = i.toDouble() }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (active) MeetColors.warning else MeetColors.textMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ratingComment,
                        onValueChange = { ratingComment = it },
                        label = { Text("Escribe un comentario...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitRideRating(ride.requestId, !isDriver, ratingStars, ratingComment)
                        showRatingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                ) {
                    Text("Guardar", color = MeetColors.backgroundDark)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.markRatingSettled(ride.requestId, ratingRole)
                    showRatingDialog = false
                }) {
                    Text("Omitir", color = MeetColors.textMuted)
                }
            }
        )
    }

    // ═══ Celebratory Trip Summary Overlay ═══
    if (showCompletedSummary && ride.status == "COMPLETED") {
        LaunchedEffect(ride.requestId) {
            delay(30_000L)
            showCompletedSummary = false
        }
        Dialog(
            onDismissRequest = { showCompletedSummary = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628)),
                border = BorderStroke(2.dp, MeetColors.neonGreen),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Checkmark
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✅", fontSize = 36.sp)
                        }
                    }

                    Text(
                        "¡Viaje Completado!",
                        color = MeetColors.neonGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )

                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    // Trip stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📏", fontSize = 20.sp)
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", ride.estimatedDistanceKm)} km",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text("Distancia", color = MeetColors.textMuted, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏱", fontSize = 20.sp)
                            Text(
                                "${ride.estimatedDurationMin} min",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text("Duración", color = MeetColors.textMuted, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💰", fontSize = 20.sp)
                            val fareText = if (ride.currency == "USD") {
                                "$${(ride.finalPrice ?: ride.priceOffer).toInt()}"
                            } else {
                                "${CoreMoney.ofCrc((ride.finalPriceMinor ?: ride.priceOfferMinor)).formatted()} CRC"
                            }
                            Text(
                                fareText,
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                            Text(
                                when (ride.paymentMethod) {
                                    "SINPE_MOVIL", "SINPE" -> "SINPE"
                                    "CASH" -> "Efectivo"
                                    else -> "Pago"
                                },
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }

                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    // CTA Buttons
                    if (!isRatingAlreadySettled && ride.passengerRating == null) {
                        Button(
                            onClick = {
                                showCompletedSummary = false
                                showRatingDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("⭐ Calificar Servicio", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showCompletedSummary = false
                            showLostAndFoundDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("¿Olvidaste un objeto en el vehículo?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.markRatingSettled(ride.requestId, ratingRole)
                            showCompletedSummary = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Text("Cerrar", color = MeetColors.textSecondary)
                    }
                }
            }
        }
    }
}

private fun RideRoadIncidentType.rideRoadLabel(): String = when (this) {
    RideRoadIncidentType.SLOW_TRAFFIC -> "Tráfico lento"
    RideRoadIncidentType.VERY_SLOW_TRAFFIC -> "Tráfico muy lento"
    RideRoadIncidentType.STALLED_VEHICLE -> "Vehículo varado"
    RideRoadIncidentType.POTHOLE -> "Bache"
    RideRoadIncidentType.OBSTACLE -> "Obstáculo"
    RideRoadIncidentType.ROAD_CLOSED -> "Calle cerrada"
    RideRoadIncidentType.WRONG_WAY_HAZARD -> "Riesgo de contravía"
    RideRoadIncidentType.POLICE_PRESENCE -> "Presencia policial"
    RideRoadIncidentType.TRAFFIC_CONTROL -> "Control de tránsito"
    RideRoadIncidentType.PUBLIC_POLICE -> "Policía pública"
    RideRoadIncidentType.TRAFFIC_POLICE -> "Policía de tránsito"
    RideRoadIncidentType.SPEED_BUMP -> "Reductor / muerto"
    RideRoadIncidentType.FLOODING -> "Calle inundada"
}

@Composable
private fun RideRoadReportDialog(
    onDismiss: () -> Unit,
    onReport: (RideRoadIncidentType, RideRoadSide, Int) -> Unit,
) {
    var type by remember { mutableStateOf(RideRoadIncidentType.SLOW_TRAFFIC) }
    var side by remember { mutableStateOf(RideRoadSide.NOT_APPLICABLE) }
    var severity by remember { mutableIntStateOf(2) }
    val sideRelevant = type in setOf(
        RideRoadIncidentType.STALLED_VEHICLE,
        RideRoadIncidentType.OBSTACLE,
        RideRoadIncidentType.POTHOLE,
        RideRoadIncidentType.SPEED_BUMP,
        RideRoadIncidentType.FLOODING,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xEE071019),
        title = {
            Column {
                Text("INTELIGENCIA VIAL", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                Text("Reporta solo cuando sea seguro y estés detenido.", color = MeetColors.warning, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RideRoadIncidentType.entries.forEach { candidate ->
                    Surface(
                        color = if (type == candidate) {
                            MeetColors.cyberCyan.copy(alpha = 0.18f)
                        } else {
                            MeetColors.cardBackground
                        },
                        border = BorderStroke(
                            1.dp,
                            if (type == candidate) MeetColors.cyberCyan else MeetColors.borderSubtle,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            type = candidate
                            if (candidate !in setOf(
                                    RideRoadIncidentType.STALLED_VEHICLE,
                                    RideRoadIncidentType.OBSTACLE,
                                    RideRoadIncidentType.POTHOLE,
                                    RideRoadIncidentType.SPEED_BUMP,
                                    RideRoadIncidentType.FLOODING,
                                )
                            ) side = RideRoadSide.NOT_APPLICABLE
                        },
                    ) {
                        Text(
                            candidate.rideRoadLabel(),
                            color = Color.White,
                            modifier = Modifier.padding(11.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (sideRelevant) {
                    Text("UBICACIÓN EN LA VÍA", color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RideRoadSide.LEFT to "Izquierda",
                            RideRoadSide.CENTER to "Centro",
                            RideRoadSide.RIGHT to "Derecha",
                        ).forEach { (candidate, label) ->
                            FilterChip(
                                selected = side == candidate,
                                onClick = { side = candidate },
                                label = { Text(label, fontSize = 9.sp) },
                            )
                        }
                    }
                }
                Text("SEVERIDAD $severity/3", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Slider(
                    value = severity.toFloat(),
                    onValueChange = { severity = it.toInt().coerceIn(1, 3) },
                    valueRange = 1f..3f,
                    steps = 1,
                )
                Text(
                    "El reporte caduca automáticamente y se compara con confirmaciones, dirección, precisión GPS y velocidades observadas.",
                    color = MeetColors.textMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onReport(type, if (sideRelevant) side else RideRoadSide.NOT_APPLICABLE, severity) },
                enabled = !sideRelevant || side != RideRoadSide.NOT_APPLICABLE,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55)),
            ) {
                Text("PUBLICAR REPORTE", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// Haversine formula to compute distance in km between two GPS coordinates
internal fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

/**
 * Sanitiza un `addressName` para que no filtre lat/lon cuando el geocoder falló.
 *
 * Defensa en profundidad: builds anteriores podian guardar
 * `addressName = "Ubicación GPS (lat, lng)"` cuando fallaba el geocoder.
 * Si eso se persiste en una Room row o se envía al backend, las coords exactas
 * quedan expuestas en un canal que no debería tenerlas.
 *
 * Detecta el patrón "Ubicación GPS (<number>, <number>)" y lo reemplaza por un label
 * genérico. Si no coincide el patrón, devuelve la entrada sin modificar.
 *
 * `internal` para que SanitizeGpsAddressTest del mismo módulo pueda validar el regex.
 *
 * (RISK-3: GPS data leak via fallback address string)
 */
internal fun sanitizeGpsAddress(raw: String): String {
    val regex = Regex("^Ubicación GPS \\(-?\\d+\\.?\\d*, -?\\d+\\.?\\d*\\)$")
    return if (regex.matches(raw)) "Ubicación GPS detectada" else raw
}

// ─── Passenger Verification Dialog ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaxVerificationDialog(
    paxName: String,
    onNameChange: (String) -> Unit,
    paxPhone: String,
    onPhoneChange: (String) -> Unit,
    paxProfilePhoto: String,
    onProfileCapture: (String) -> Unit,
    paxCedulaFront: String,
    onCedulaCapture: (String) -> Unit,
    paxSelfieWithCedula: String,
    onSelfieCapture: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val accent = MeetColors.electricBlue
    val isValid = paxName.isNotBlank() && paxPhone.isNotBlank() &&
            paxProfilePhoto.isNotBlank() && paxCedulaFront.isNotBlank() &&
            paxSelfieWithCedula.isNotBlank()

    val launchVerificationPhoto = rememberVerificationPhotoCapture { documentType, path ->
        when (documentType) {
            "SELFIE_PROFILE" -> onProfileCapture(path)
            "CEDULA_FRONT" -> onCedulaCapture(path)
            "SELFIE_WITH_CEDULA" -> onSelfieCapture(path)
        }
    }
    var captureGuideType by rememberSaveable { mutableStateOf<String?>(null) }

    val triggerPhotoCapture = { docType: String -> captureGuideType = docType }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MeetColors.backgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Verificación de Pasajero",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Cerrar", tint = accent)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val compact = maxWidth < 360.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Text(
                                text = "Por favor, completa los siguientes datos para verificar tu identidad y mantener los viajes seguros:",
                                color = MeetColors.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MeetColors.electricBlue.copy(alpha = 0.10f),
                                border = BorderStroke(
                                    1.dp,
                                    MeetColors.electricBlue.copy(alpha = 0.35f),
                                ),
                            ) {
                                Text(
                                    text = "Tu expediente se enviará a revisión. Los viajes se habilitan únicamente cuando el Centro de Confianza lo aprueba.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = paxName,
                                onValueChange = onNameChange,
                                label = { Text("Nombre Completo") },
                                placeholder = { Text("Ej: María López Rodríguez", color = MeetColors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = accent,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedLabelColor = accent,
                                    unfocusedLabelColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.cardBackground,
                                    unfocusedContainerColor = MeetColors.cardBackground
                                )
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = paxPhone,
                                onValueChange = onPhoneChange,
                                label = { Text("Teléfono") },
                                placeholder = { Text("Ej: +506 8888-8888", color = MeetColors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = accent,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedLabelColor = accent,
                                    unfocusedLabelColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.cardBackground,
                                    unfocusedContainerColor = MeetColors.cardBackground
                                )
                            )
                        }

                        item {
                            PaxDocButton("📸 Foto de Perfil", "Foto frontal clara de tu rostro", paxProfilePhoto.isNotBlank()) {
                                triggerPhotoCapture("SELFIE_PROFILE")
                            }
                        }

                        item {
                            PaxDocButton("🪪 Cédula de Identidad (Frente)", "Foto legible de tu documento de identidad", paxCedulaFront.isNotBlank()) {
                                triggerPhotoCapture("CEDULA_FRONT")
                            }
                        }

                        item {
                            PaxDocButton("🤳 Selfie Sosteniendo Cédula", "Foto tuya sosteniendo la cédula junto a tu cara", paxSelfieWithCedula.isNotBlank()) {
                                triggerPhotoCapture("SELFIE_WITH_CEDULA")
                            }
                        }
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (compact) 12.dp else 24.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                            disabledContentColor = MeetColors.textMuted
                        )
                    ) {
                        Text("🚀 ENVIAR VERIFICACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }

                // Overlay Guide Dialog
                if (captureGuideType != null) {
                    CaptureGuideOverlay(
                        documentType = captureGuideType!!,
                        onDismiss = { captureGuideType = null },
                        onProceed = {
                            captureGuideType?.let { documentType ->
                                captureGuideType = null
                                launchVerificationPhoto("passenger", documentType)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaxDocButton(
    label: String,
    description: String,
    isCaptured: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) Color(0xFF0D2818) else MeetColors.cardBackground
        ),
        border = BorderStroke(
            1.dp,
            if (isCaptured) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (isCaptured) MeetColors.neonGreen else MeetColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(description, color = MeetColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isCaptured) {
                Icon(Icons.Filled.CheckCircle, null, tint = MeetColors.neonGreen, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.CameraAlt, null, tint = MeetColors.electricBlue, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Animations and Indicators ───────────────────────────────────────────────

@Composable
fun BouncingRadarIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .clip(CircleShape)
            .background(MeetColors.cyberCyan.copy(alpha = 0.2f))
            .border(2.dp, MeetColors.cyberCyan, CircleShape)
    )
}

// ─── Passenger Live Bids Panel ───────────────────────────────────────────────

@Composable
fun PassengerLiveOffersPanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    offers: List<RideOfferEntity>,
    onCloseRide: () -> Unit,
) {
    val currentLocale = rememberRideJavaLocale()
    val trustedDriverScope = rememberCoroutineScope()
    var showCancellationDialog by remember { mutableStateOf(false) }
    var trustedDrivers by remember(ride.requestId) { mutableStateOf(emptyList<TrustedRideDriver>()) }
    var trustedDriverMessage by remember(ride.requestId) { mutableStateOf<String?>(null) }
    var trustedDriversLoading by remember(ride.requestId) { mutableStateOf(true) }
    var driverRejectionCount by remember(ride.requestId) { mutableIntStateOf(0) }
    var stagedPrice by remember(ride.priceOffer) { mutableDoubleStateOf(ride.priceOffer) }
    val hasPriceChanged = stagedPrice != ride.priceOffer
    val pendingOffers = remember(offers) { offers.filter { it.status == "PENDING" } }
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "hace un momento" else "hace $elapsedMins min"

    LaunchedEffect(ride.requestId) {
        trustedDriversLoading = true
        runCatching { RideDispatchGateway.trustedDrivers() }
            .onSuccess { trustedDrivers = it }
            .onFailure { trustedDriverMessage = "No se pudo consultar tu historial de choferes." }
        trustedDriversLoading = false
    }
    LaunchedEffect(ride.requestId) {
        while (true) {
            driverRejectionCount = runCatching { RideDispatchGateway.rejectionCount(ride.requestId) }
                .getOrDefault(driverRejectionCount)
            delay(10_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Bid Display Card with +/- stepper
        Card(
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, if (hasPriceChanged) MeetColors.neonGreen else MeetColors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (hasPriceChanged) "Nueva Oferta Propuesta (Pulsa Confirmar)" else "Tu Oferta Actual",
                    color = if (hasPriceChanged) MeetColors.neonGreen else MeetColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Elysium Vanguard fare stepper
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val priceMinor = stagedPrice.toLong()
                            val adjusted = RideFareBidPolicy.adjustMinor(priceMinor, ride.currency, -1)
                            stagedPrice = adjusted.toDouble()
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (ride.currency == "USD") {
                            "$${stagedPrice.toInt()}"
                        } else {
                            "${CoreMoney.ofCrc(stagedPrice.toLong()).formatted()} CRC"
                        },
                        color = if (hasPriceChanged) MeetColors.neonGreen else Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = {
                            val priceMinor = stagedPrice.toLong()
                            val adjusted = RideFareBidPolicy.adjustMinor(priceMinor, ride.currency, 1)
                            stagedPrice = adjusted.toDouble()
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "⚡ Incrementar tarifa rápidamente:",
                    color = MeetColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(300.0, 900.0, 1500.0).forEach { amount ->
                        val label = if (ride.currency == "USD") "+$${(amount / 500).toInt()}" else "+${CoreMoney.ofCrc(amount.toLong()).formatted()}"
                        val valToAdd = if (ride.currency == "USD") amount / 500.0 else amount
                        Button(
                            onClick = { stagedPrice = stagedPrice + valToAdd },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.cardBackground,
                                contentColor = MeetColors.cyberCyan
                            ),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (hasPriceChanged) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.publishRidePriceIncrease(ride.requestId, stagedPrice)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "⚡ CONFIRMAR AUMENTO · ${if (ride.currency == "USD") "$${stagedPrice.toInt()}" else "${CoreMoney.ofCrc(stagedPrice.toLong()).formatted()} CRC"}",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showCancellationDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("❌ CANCELAR SOLICITUD DE VIAJE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        if (showCancellationDialog) {
            AuthoritativeRideCancellationDialog(viewModel, ride.requestId, RideActorRole.PASSENGER) {
                showCancellationDialog = false
            }
        }

        if (trustedDriversLoading || trustedDrivers.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("CHOFERES DE TU CONFIANZA", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
                    Text(
                        "Puedes invitar directamente a un chofer que ya completó un viaje contigo. La solicitud seguirá disponible para la red.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                    if (trustedDriversLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        trustedDrivers.take(5).forEach { driver ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(driver.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${driver.completedTrips} viaje(s) juntos · ${driver.vehicleName ?: "Vehículo pendiente"}",
                                        color = MeetColors.textMuted,
                                        fontSize = 9.sp,
                                    )
                                }
                                Button(
                                    onClick = {
                                        trustedDriverScope.launch {
                                            runCatching { RideDispatchGateway.inviteTrustedDriver(ride.requestId, driver.driverId) }
                                                .onSuccess { trustedDriverMessage = "Invitación enviada a ${driver.displayName}." }
                                                .onFailure { trustedDriverMessage = "No se pudo enviar la invitación: ${it.message?.take(90)}" }
                                        }
                                    },
                                    enabled = driver.isAvailable,
                                ) {
                                    Text(if (driver.isAvailable) "INVITAR" else "NO DISPONIBLE")
                                }
                            }
                        }
                    }
                    trustedDriverMessage?.let { Text(it, color = MeetColors.cyberCyan, fontSize = 10.sp) }
                }
            }
        }

        // Radar search indicator with elapsed time
        if (driverRejectionCount > 0) {
            Surface(
                color = MeetColors.warning.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MeetColors.warning),
            ) {
                Text(
                    if (driverRejectionCount == 1) {
                        "Un chofer rechazó tu oferta. Tu solicitud sigue activa para otros choferes."
                    } else {
                        "$driverRejectionCount choferes rechazaron tu oferta. Tu solicitud sigue activa; considera subir el precio."
                    },
                    modifier = Modifier.padding(12.dp),
                    color = MeetColors.warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Radar search indicator with elapsed time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BouncingRadarIndicator()
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Buscando choferes...",
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Iniciado $timeText. Ofertas abajo.",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Offers list
        Text(
            text = "Ofertas recibidas (${pendingOffers.size})",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        if (pendingOffers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Esperando contraofertas de conductores...",
                    color = MeetColors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pendingOffers.forEach { offer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = offer.driverName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Identidad revisada",
                                                    color = MeetColors.cyberCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (offer.driverTotalTrips > 0) {
                                                "⭐ ${offer.driverRating} (${offer.driverTotalTrips} viajes)"
                                            } else {
                                                "Conductor nuevo · sin historial capturado"
                                            },
                                            color = MeetColors.warning,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(text = offer.vehicleDescription, color = MeetColors.textSecondary, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "${offer.counterPrice.toInt()} ${offer.currency}", fontWeight = FontWeight.Black, color = MeetColors.neonGreen, fontSize = 18.sp)
                                        Text(text = "Llega en: ${offer.estimatedArrivalMin} min", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                if (!offer.message.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "\"${offer.message}\"", color = MeetColors.textMuted, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectRideOffer(ride.requestId, offer.offerId) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Rechazar", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.acceptRideOffer(ride.requestId, offer.offerId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Aceptar Chofer 🚕", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// ─── Driver Negotiation Panel ────────────────────────────────────────────────

@Composable
fun DriverNegotiationPanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    offers: List<RideOfferEntity>,
    onCloseRide: () -> Unit
) {
    val context = LocalContext.current
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val driverVer by viewModel.driverVerification.collectAsState()

    val myDriverId = driverVer?.driverId
    // Only find pending offer from this driver
    val myOffer = offers.firstOrNull { it.driverId == myDriverId && it.status == "PENDING" }
    // Detect if this driver's offer was recently rejected
    val wasRejected = remember(offers) { offers.any { it.driverId == myDriverId && it.status == "REJECTED" } }
    var showOfferConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(myOffer?.offerId) {
        if (myOffer != null) {
            showOfferConfirmation = true
            delay(2800)
            showOfferConfirmation = false
        }
    }

    AnimatedVisibility(
        visible = showOfferConfirmation,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        val confirmationMotion = rememberInfiniteTransition(label = "offer-confirmation-3d")
        val confirmationPulse by confirmationMotion.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "offer-confirmation-pulse",
        )
        val confirmationTilt by confirmationMotion.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "offer-confirmation-tilt",
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.16f)),
            border = BorderStroke(1.5.dp, MeetColors.neonGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    scaleX = confirmationPulse
                    scaleY = confirmationPulse
                    rotationY = confirmationTilt
                    shadowElevation = 22.dp.toPx()
                    cameraDistance = 18f * density
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(32.dp))
                Column {
                    Text("OFERTA ENVIADA", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
                    Text("La red la está confirmando; el pasajero la verá sólo si la reserva de comisión es válida.", color = MeetColors.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    if (myOffer != null) {
        // Driver has submitted an offer and is waiting for response
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BouncingRadarIndicator()

                    Text(
                        "OFERTA ENVIADA CON ÉXITO",
                        color = MeetColors.warning,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )

                    androidx.compose.material3.HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tu tarifa propuesta:", color = MeetColors.textSecondary, fontSize = 13.sp)
                        Text("${myOffer.counterPrice.toInt()} ${myOffer.currency}", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tiempo estimado:", color = MeetColors.textSecondary, fontSize = 13.sp)
                        Text("${myOffer.estimatedArrivalMin} min", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text(
                        text = "Esperando que el cliente acepte tu oferta. Si es aceptada, la app te notificará y abrirá el chat de inmediato.",
                        color = MeetColors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.rejectRideOffer(ride.requestId, myOffer.offerId)
                    onCloseRide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("❌ RETIRAR MI OFERTA", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Driver has not made an offer yet, show the auction bidding UI
        var counterPrice by remember { mutableDoubleStateOf(ride.priceOffer) }
        var selectedEta by remember { mutableIntStateOf(10) }
        var driverMsg by remember { mutableStateOf("") }

        val distanceText = remember(currentGps) {
            if (currentGps != null) {
                val dist = calculateDistance(
                    currentGps!!.latitude, currentGps!!.longitude,
                    ride.pickupLatitude, ride.pickupLongitude
                )
                String.format(java.util.Locale.US, "📍 A %.1f km de tu posición", dist)
            } else {
                "📍 Ubicación de recogida disponible"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wasRejected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F21)),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "El pasajero rechazó tu oferta anterior. Puedes proponer una nueva tarifa si lo deseas.",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Ride details card
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = distanceText, color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Tarifa propuesta por cliente: ${ride.priceOffer.toInt()} ${ride.currency}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Price negotiation
            Text(
                text = "Determina tu Tarifa:",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            // Current price display
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            if (counterPrice > step) counterPrice -= step
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${counterPrice.toInt()} ${ride.currency}",
                        color = MeetColors.neonGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = {
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            counterPrice += step
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick bid chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val offsets = if (ride.currency == "USD") listOf(0.0, 1.0, 2.0, 4.0) else listOf(0.0, 500.0, 1000.0, 2000.0)
                offsets.forEach { offset ->
                    val total = ride.priceOffer + offset
                    val label = if (offset == 0.0) "Aceptar" else {
                        if (ride.currency == "USD") "+$${offset.toInt()}" else "+${CoreMoney.ofCrc(offset.toLong()).formatted()}"
                    }
                    Button(
                        onClick = { counterPrice = total },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (counterPrice == total) MeetColors.cyberCyan else MeetColors.cardBackground,
                            contentColor = if (counterPrice == total) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            // ETA Selector
            Text(
                text = "Tiempo estimado de llegada (ETA):",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(5, 10, 15, 20).forEach { mins ->
                    Button(
                        onClick = { selectedEta = mins },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedEta == mins) MeetColors.cyberCyan else MeetColors.cardBackground,
                            contentColor = if (selectedEta == mins) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${mins} min", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            // Message field
            OutlinedTextField(
                value = driverMsg,
                onValueChange = { driverMsg = it },
                label = { Text("Nota al pasajero (opcional)") },
                placeholder = { Text("Ej: Llevo aire acondicionado, auto limpio") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle
                )
            )

            // Submit offer button
            Button(
                onClick = {
                    val verifiedDriver = driverVer
                    val gps = currentGps
                    if (
                        verifiedDriver == null ||
                        !RideVerificationPolicy.grantsAccess(verifiedDriver.status)
                    ) {
                        Toast.makeText(
                            context,
                            "Se requiere identidad de conductor aprobada",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    if (gps == null) {
                        Toast.makeText(
                            context,
                            "Esperando una ubicación GPS válida",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    viewModel.makeRideOffer(
                        requestId = ride.requestId,
                        driverId = verifiedDriver.driverId,
                        driverName = verifiedDriver.fullName,
                        driverPhone = verifiedDriver.phone,
                        driverRating = 0.0,
                        driverTotalTrips = 0,
                        vehicleDesc = "${verifiedDriver.vehicleMake} ${verifiedDriver.vehicleModel} ${verifiedDriver.vehicleYear} (${verifiedDriver.vehicleColor}) [${verifiedDriver.vehiclePlate}]",
                        counterPrice = counterPrice,
                        currency = ride.currency,
                        estArrivalMin = selectedEta,
                        driverLat = gps.latitude,
                        driverLng = gps.longitude,
                        message = driverMsg.takeIf { it.isNotBlank() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🚀 ENVIAR TARIFACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Capture Guide Overlay with Cyber Scanner Animation ──────────────────────

private @Composable
fun CaptureGuideOverlay(
    documentType: String,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val accent = MeetColors.cyberCyan
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val title = when (documentType) {
                    "CEDULA_FRONT" -> "🆔 Cédula de Identidad (Frente)"
                    "CEDULA_BACK" -> "🆔 Cédula de Identidad (Reverso)"
                    "LICENCIA_FRONT" -> "🪪 Licencia de Conducir (Frente)"
                    "LICENCIA_BACK" -> "🪪 Licencia de Conducir (Reverso)"
                    "SELFIE_PROFILE" -> "📸 Foto de Perfil (Selfie)"
                    "SELFIE_WITH_CEDULA" -> "🤳 Selfie con Cédula al Lado del Rostro"
                    "SELFIE_WITH_LICENCIA" -> "🤳 Selfie con Licencia Lado del Rostro"
                    "MARCHAMO" -> "📜 Foto de Marchamo"
                    "DEKRA" -> "🔧 Foto de DEKRA / RTV"
                    "SEGURO" -> "🛡️ Foto de Seguro Vehicular"
                    "HOJA" -> "📋 Hoja de Delincuencia"
                    "VEHICLE_FRONT" -> "🚗 Foto Frontal del Vehículo"
                    "VEHICLE_BACK" -> "🚗 Foto Trasera del Vehículo"
                    "VEHICLE_INT" -> "🪑 Foto Interior del Vehículo"
                    else -> "Toma de Foto"
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Interactive Scanner Animation
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw corner guidelines
                        val strokeW = 3.dp.toPx()
                        val cornerLen = 20.dp.toPx()

                        // Guidelines corners
                        listOf(
                            Offset(12.dp.toPx(), 12.dp.toPx()) to Offset(12.dp.toPx() + cornerLen, 12.dp.toPx()),
                            Offset(12.dp.toPx(), 12.dp.toPx()) to Offset(12.dp.toPx(), 12.dp.toPx() + cornerLen),
                            Offset(w - 12.dp.toPx(), 12.dp.toPx()) to Offset(w - 12.dp.toPx() - cornerLen, 12.dp.toPx()),
                            Offset(w - 12.dp.toPx(), 12.dp.toPx()) to Offset(w - 12.dp.toPx(), 12.dp.toPx() + cornerLen),
                            Offset(12.dp.toPx(), h - 12.dp.toPx()) to Offset(12.dp.toPx() + cornerLen, h - 12.dp.toPx()),
                            Offset(12.dp.toPx(), h - 12.dp.toPx()) to Offset(12.dp.toPx(), h - 12.dp.toPx() - cornerLen),
                            Offset(w - 12.dp.toPx(), h - 12.dp.toPx()) to Offset(w - 12.dp.toPx() - cornerLen, h - 12.dp.toPx()),
                            Offset(w - 12.dp.toPx(), h - 12.dp.toPx()) to Offset(w - 12.dp.toPx(), h - 12.dp.toPx() - cornerLen)
                        ).forEach { (start, end) ->
                            drawLine(accent, start, end, strokeW)
                        }

                        // Draw silhouettes
                        when (documentType) {
                            "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(30.dp.toPx(), 50.dp.toPx()),
                                    size = Size(w - 60.dp.toPx(), h - 100.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 70.dp.toPx()),
                                    end = Offset(w - 55.dp.toPx(), 70.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 85.dp.toPx()),
                                    end = Offset(w - 85.dp.toPx(), 85.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            "SELFIE_PROFILE" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 30.dp.toPx(), h/2 - 45.dp.toPx()),
                                    size = Size(60.dp.toPx(), 80.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawPath(Path().apply {
                                    moveTo(w/2 - 45.dp.toPx(), h - 25.dp.toPx())
                                    quadraticBezierTo(w/2, h - 60.dp.toPx(), w/2 + 45.dp.toPx(), h - 25.dp.toPx())
                                }, Color.White.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
                            }
                            "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 40.dp.toPx(), h/2 - 40.dp.toPx()),
                                    size = Size(50.dp.toPx(), 70.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(w/2 + 15.dp.toPx(), h/2 - 5.dp.toPx()),
                                    size = Size(35.dp.toPx(), 22.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(1.5f.dp.toPx())
                                )
                            }
                            "VEHICLE_FRONT", "VEHICLE_BACK" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(25.dp.toPx(), 60.dp.toPx()),
                                    size = Size(w - 50.dp.toPx(), h - 110.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(50.dp.toPx(), h - 50.dp.toPx()))
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(w - 50.dp.toPx(), h - 50.dp.toPx()))
                            }
                            "VEHICLE_INT" -> {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.15f),
                                    radius = 30.dp.toPx(),
                                    center = Offset(w/2, h/2),
                                    style = Stroke(3.dp.toPx())
                                )
                            }
                        }

                        // Scanning green bar
                        val scanY = 16.dp.toPx() + (h - 32.dp.toPx()) * scanLineProgress
                        drawLine(
                            color = MeetColors.neonGreen,
                            start = Offset(16.dp.toPx(), scanY),
                            end = Offset(w - 16.dp.toPx(), scanY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Guidelines
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val guidelines = when (documentType) {
                        "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> listOf(
                            "Coloca el documento sobre una superficie plana y oscura.",
                            "Evita los reflejos directos de luz y destellos de flash.",
                            "Asegúrate de que todo el texto sea legible y no esté borroso."
                        )
                        "SELFIE_PROFILE" -> listOf(
                            "Busca un entorno con buena iluminación frontal.",
                            "Mira fijamente a la cámara con una expresión neutra.",
                            "Quítate gorras, lentes oscuros y mascarillas."
                        )
                        "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> listOf(
                            "Sostén tu documento al lado de tu cara sin cubrir tu rostro.",
                            "Asegúrate de no tapar tus ojos, boca u orejas con el documento.",
                            "Tanto tu cara como el texto de la identificación deben ser nítidos."
                        )
                        "VEHICLE_FRONT", "VEHICLE_BACK" -> listOf(
                            "Captura el vehículo completo a una distancia adecuada.",
                            "Asegúrate de que las placas sean perfectamente visibles.",
                            "Toma la foto a la luz del día o con buena iluminación."
                        )
                        else -> listOf(
                            "Busca un lugar iluminado de frente.",
                            "Sostén firmemente el celular para evitar fotos borrosas.",
                            "Verifica que el elemento principal esté enfocado."
                        )
                    }

                    guidelines.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💡 ", fontSize = 13.sp)
                            Text(
                                text = tip,
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MeetColors.textMuted)
                    }
                    Button(
                        onClick = onProceed,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("ABRIR CÁMARA 📸", color = MeetColors.backgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveRideMetrics(
    ride: RideRequestEntity,
    ownerId: String,
    currentSpeed: Float,
    activeRouteMeters: Long?,
    modifier: Modifier = Modifier,
) {
    val isTripActive = ride.status in listOf("IN_PROGRESS", "COMPLETED") ||
            ride.serverState in listOf("IN_PROGRESS", "COMPLETED")

    val isArrivedWaiting = (ride.status == "ARRIVED" || ride.serverState == "ARRIVED") && !isTripActive

    var meter by remember(ride.requestId, ownerId) { mutableStateOf<com.elysium369.meet.ride.meter.SharedRideMeterSnapshot?>(null) }
    var meterConnected by remember(ride.requestId, ownerId) { mutableStateOf(false) }
    LaunchedEffect(ride.requestId, ownerId) {
        if (ownerId != ride.passengerId && ownerId != ride.assignedDriverId) return@LaunchedEffect
        while (true) {
            try {
                val snapshot = com.elysium369.meet.ride.meter.SharedRideMeterGateway.fetch(ride.requestId)
                meter = snapshot
                meterConnected = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                meterConnected = false
                meter = null
            }
            kotlinx.coroutines.delay(5_000L)
        }
    }
    val startedAtMs = meter?.started_at_ms
    val elapsedSeconds = meter?.elapsed_seconds ?: 0L
    val elapsedMinutes = (elapsedSeconds / 60).toInt()
    val elapsedSecs = (elapsedSeconds % 60).toInt()
    val distanceTraveledKm = (meter?.validated_distance_meters ?: 0L) / 1000.0
    val isOpenBid = ride.fareMode == RideFareMode.OPEN_BID.name
    val gpsFresh = meter?.let { it.last_capture_ms != null && it.server_as_of_ms - it.last_capture_ms <= 30_000L } == true
    val liveFareMinor = if (meter?.is_final == true) meter?.final_fare_minor else meter?.measured_fare_minor

    Surface(
        color = MeetColors.cyberCyan.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (meter?.is_final == true) "MARÍA · TOTAL CONFIRMADO" else if (meterConnected && gpsFresh) "MARÍA · MEDICIÓN COMPARTIDA" else "MARÍA · SINCRONIZACIÓN / GPS PENDIENTE",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                if (meter?.is_final == true) {
                    Text("FINALIZADO", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else if (isTripActive) {
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "EN CURSO 🏁",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isArrivedWaiting) {
                    Surface(
                        color = MeetColors.warning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "EN ESPERA DE INICIO (PIN)",
                            color = MeetColors.warning,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricPill(
                    label = "TIEMPO VIAJE",
                    value = if (startedAtMs != null && isTripActive) "%02d:%02d".format(elapsedMinutes, elapsedSecs) else "Pendiente",
                )
                MetricPill(
                    label = "MI VELOCIDAD",
                    value = "${currentSpeed.toInt()} km/h",
                )
                MetricPill(
                    label = "DISTANCIA",
                    value = if (distanceTraveledKm > 0.0) "${"%.1f".format(distanceTraveledKm)} km" else "—",
                )
            }

            Spacer(Modifier.height(10.dp))

            if (!isOpenBid) {
                if (liveFareMinor != null) {
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (meter?.is_final == true) "Tarifa final confirmada:" else if (gpsFresh && meterConnected) "Medición provisional (no cobro final):" else "Última medición parcial:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "₡ $liveFareMinor ${ride.currency}",
                                color = MeetColors.neonGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    Text(
                        "Esperando inicio confirmado y puntos GPS del conductor. Sin datos válidos no se calcula un cobro.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Surface(
                    color = Color(0xFFFFB300).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🤝 Modalidad 'Pon tu precio':",
                                color = Color(0xFFFFCC80),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "₡ ${ride.priceOffer.toInt()} ${ride.currency}",
                                color = Color(0xFFFFD54F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            "Tarifa fija acordada — No varía con tiempo ni distancia.",
                            color = Color(0xFFFFE082),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (activeRouteMeters != null && activeRouteMeters > 0) {
                val remainingKm = activeRouteMeters / 1000.0
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ruta estimada: ~${"%.1f".format(remainingKm)} km",
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp,
                )
            }

            // ── Avisos transparentes y obligatorios
            Spacer(Modifier.height(8.dp))
            Divider(color = MeetColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "⚠️ Los peajes los paga siempre el usuario, no el chofer.",
                    color = Color(0xFFFFCC80),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "⚠️ No se permite dejar viajes pendientes.",
                    color = Color(0xFFFFAB91),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            label,
            color = MeetColors.textMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun TipDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    val presetTips = if (currency == "CRC") {
        listOf(500L, 1000L, 2000L, 5000L)
    } else {
        listOf(100L, 200L, 500L, 1000L)
    }
    var customTip by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<Long?>(null) }
    var deliveryMethod by remember { mutableStateOf("CASH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propina para el chofer 💚", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agradecé y dignificá el trabajo del chofer con una propina voluntaria.", color = MeetColors.textSecondary, fontSize = 12.sp)
                Text("La propina se entrega directamente al chofer. Elysium no la suma a ningún saldo.", color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetTips.forEach { amount ->
                        val isSelected = selectedPreset == amount
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPreset = if (isSelected) null else amount
                                customTip = ""
                            },
                            label = { Text("${com.elysium369.meet.ride.domain.RideTipPolicy.display(amount, currency)} $currency") },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customTip,
                    onValueChange = { customTip = it.filter { c -> c.isDigit() }; selectedPreset = null },
                    label = { Text("Otra cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("¿Cómo la entregarás?", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = deliveryMethod == "CASH",
                        onClick = { deliveryMethod = "CASH" },
                        label = { Text("💵 En persona") },
                    )
                    FilterChip(
                        selected = deliveryMethod == "SINPE",
                        onClick = { deliveryMethod = "SINPE" },
                        label = { Text("📲 SINPE al chofer") },
                    )
                }
                Text(
                    if (deliveryMethod == "CASH") "Entrégala personalmente al finalizar." else "Envíala al SINPE que el chofer te confirme.",
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            val tipMinor = selectedPreset ?: com.elysium369.meet.ride.domain.RideTipPolicy.parseMajor(customTip, currency)
            TextButton(
                onClick = { tipMinor?.let { onConfirm(it, deliveryMethod) } },
                enabled = tipMinor != null && com.elysium369.meet.ride.domain.RideTipPolicy.isValid(tipMinor, currency),
            ) {
                Text("REGISTRAR COMPROMISO", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("SALTEAR") } },
    )
}

/** The dialog observes durable commands and the canonical projection across recreation. */
@Composable
private fun AuthoritativeRideCancellationDialog(
    viewModel: ObdViewModel,
    requestId: String,
    role: RideActorRole,
    onDismiss: () -> Unit,
) {
    val commands by remember(requestId) { viewModel.cancellationCommands(requestId) }
        .collectAsState(initial = emptyList())
    val request by remember(requestId) { viewModel.observeRideForCancellation(requestId) }
        .collectAsState(initial = null)
    val command = commands.firstOrNull { it.actorSessionUserId == viewModel.currentUserId }
    // A queued/retryable command is recoverable: allow the user to wake the
    // worker again after auth/network recovery. Only an actively leased RPC
    // must disable the confirm action to prevent concurrent submissions.
    var observedAt by remember(requestId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(command?.status, command?.leaseStartedAt) {
        while (command?.status == "IN_FLIGHT") {
            observedAt = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val leaseIsActive = command?.leaseStartedAt?.let { observedAt - it < 2 * 60 * 1000L } == true
    val pending = command?.status == "IN_FLIGHT" && leaseIsActive && request?.serverState != "CANCELLED"
    val failureMessage = when {
        command?.status == "IN_FLIGHT" && !leaseIsActive ->
            "La confirmación anterior se interrumpió. Puedes reintentar ahora."
        command?.status in setOf("FAILED", "CONFLICT", "DEAD_LETTER") ->
            "La cancelación no fue confirmada. Actualiza el viaje y revisa su estado antes de reintentar."
        else -> null
    }
    val currentRequest = request
    LaunchedEffect(currentRequest?.serverState, currentRequest?.syncState, currentRequest?.status) {
        if (currentRequest == null ||
            com.elysium369.meet.ride.domain.RideAuthorityEvidence.isTerminalSnapshot(currentRequest.serverState,currentRequest.serverVersion)
        ) {
            delay(300L)
            onDismiss()
        }
    }
    RideCancellationDialog(
        actorRole = role,
        onDismiss = onDismiss,
        submitting = pending,
        failureMessage = failureMessage,
        onConfirm = { reason, detail -> viewModel.cancelRide(requestId, reason, detail, role.name) },
    )
}
