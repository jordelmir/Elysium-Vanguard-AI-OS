package com.elysium369.meet.ui.screens.services

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.elysium369.meet.core.agent.laya.EvairDigiSoulEngine
import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.LayaQuestion
import com.elysium369.meet.core.agent.laya.LayaSpatialSearchEngine
import com.elysium369.meet.core.agentstore.ui.Agent3dAvatarCanvas
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicle.VehicleEventType
import com.elysium369.meet.core.vehicle.VehicleHistoryTimeline
import com.elysium369.meet.data.local.entities.ServiceBidEntity
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.core.geo.*
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.ride.map.LayaEnhancedPlaceSearchProvider
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.ui.navigation.safeNavigate
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.provider.domain.models.*
import com.elysium369.meet.ui.util.WazeNavigationButton
import kotlinx.coroutines.launch
import java.util.UUID
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   S E R V I C I O S   &   S U B A S T A   D U A L
 *  ──────────────────────────────────────────────────────────────
 *  Next-gen On-Demand Marketplace with dual-sided matching:
 *  - Mode CLIENT: Request service, live Laya diagnostics, fair price
 *    benchmarking by Vanguard Titan, counter-offer review, live map tracking,
 *    security PIN validation by Aura Sentinel, and two-way rating.
 *  - Mode SPECIALIST: Radar of nearby requests, instant claim or counter-offer
 *    (price, arrival time, warranty, note), PIN entry and completion.
 *  - Laya Spatial Search Engine: hyper-local Costa Rica geocoding and
 *    speech-to-intent natural voice search.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElysiumServicesMarketplaceScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onPrepareRequest: ((ServicesRequestDraft) -> Unit)? = null,
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val openAuthoritativeServices = LocalServiceOnlineAction.current
    val prepareOffer = LocalServiceOfferDraft.current
    val scope = rememberCoroutineScope()

    var isSpecialistMode by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    val categories = remember {
        listOf(
            ServiceCategoryItem("Mecánica & OBD", "🔧", "AUTO_MECHANICAL", "Vanguard Titan", MeetColors.neonGreen, "Diagnóstico motor & escáner"),
            ServiceCategoryItem("Grúa & Rescate", "🛞", "AUTO_TOW", "Aura Sentinel", MeetColors.warning, "Plataforma & auxilio vial"),
            ServiceCategoryItem("Cerrajería", "🔐", "LOCKSMITH", "Aura Sentinel", MeetColors.hotMagenta, "Aperturas & llaves chip"),
            ServiceCategoryItem("Batería & Arranque", "⚡", "BATTERY", "Vanguard Titan", MeetColors.cyberCyan, "Jumpstart & cambio in situ"),
            ServiceCategoryItem("Ferretería & Materiales", "🔩", "HARDWARE", "Vanguard Titan", Color(0xFFFF9100), "Herramientas & suministros"),
            ServiceCategoryItem("Lavado & Detailing", "✨", "DETAILING", "Neo Concierge", Color(0xFF00E5FF), "Estética & pulido pro"),
            ServiceCategoryItem("Plomería & Fontanería", "🚰", "PLUMBING", "Vanguard Titan", MeetColors.electricBlue, "Fugas & cañerías"),
            ServiceCategoryItem("Electricidad & Redes", "💡", "ELECTRICAL", "Aura Sentinel", Color(0xFFFFD600), "Breakers & acometidas"),
            ServiceCategoryItem("Pulperías & Minisúper", "🏪", "PULPERIA", "Neo Concierge", MeetColors.neonGreen, "Abarrotes & víveres"),
            ServiceCategoryItem("Sodas & Restaurantes", "🍳", "SODA_RESTAURANT", "Neo Concierge", Color(0xFFFF6D00), "Comida típica express"),
            ServiceCategoryItem("Repuestos & Partes", "⚙️", "PARTS_STORE", "Vanguard Titan", Color(0xFF00E5FF), "OEM & compatibilidad VIN"),
            ServiceCategoryItem("Mensajería & Paquetes", "📦", "COURIER", "Neo Concierge", MeetColors.cyberCyan, "Envíos directos express"),
            ServiceCategoryItem("Mudanzas & Carga", "🚚", "MOVING", "Aura Sentinel", Color(0xFFFFAB00), "Fletes & acarreos"),
            ServiceCategoryItem("Servicios Profesionales", "💼", "PROFESSIONAL", "Neo Concierge", Color(0xFF7C4DFF), "Técnicos & asesorías"),
            ServiceCategoryItem("Educación & Tutorías", "🎓", "EDUCATION", "Vanguard Titan", Color(0xFFD4AF37), "Tutor socrático & PISA")
        )
    }

    val activeCategory = categories[selectedCategoryIndex]

    // Local-first spatial search engine
    val spatialEngine = remember { LayaSpatialSearchEngine() }
    val placeSearchProvider = remember { LayaEnhancedPlaceSearchProvider(spatialEngine = spatialEngine) }
    val layaDecisionEngine = remember { LayaDecisionEngine() }

    // Requests from local Room / ViewModel
    val allRequests by viewModel.serviceRequests.collectAsState()
    val gps by viewModel.currentGpsLocation.collectAsState()

    // Form state (Client Mode)
    var problemInput by rememberSaveable { mutableStateOf("") }
    var locationInput by rememberSaveable { mutableStateOf("San José Centro, Costa Rica") }
    var locationLat by rememberSaveable { mutableDoubleStateOf(9.9333) }
    var locationLon by rememberSaveable { mutableDoubleStateOf(-84.0833) }
    var priceOfferText by rememberSaveable { mutableStateOf("18000") }
    var layaDiagnosticHint by remember { mutableStateOf<String?>(null) }
    var searchSuggestions by remember { mutableStateOf<List<RidePlaceSuggestion>>(emptyList()) }
    var isSearchingPlaces by remember { mutableStateOf(false) }

    // Dialog state
    var counterOfferDialogRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }
    var ratingDialogRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }
    var completionCertificateData by remember { mutableStateOf<ServiceCompletionCertificateData?>(null) }
    var emergencyHavenDialogOpen by remember { mutableStateOf(false) }
    var voiceSearchActive by remember { mutableStateOf(false) }
    var selectedViewMode by rememberSaveable { mutableStateOf("LIST") } // "LIST" or "MAP"
    var focusedMapRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }

    // Provider profile & constitutional wallet state (5% platform fee)
    val userProfiles by viewModel.userProviderProfiles.collectAsState()
    val activePrincipal by viewModel.activePrincipal.collectAsState()
    val currentUserId = viewModel.currentUserId
    val activeProfile = remember(userProfiles, activePrincipal, currentUserId) {
        userProfiles.firstOrNull { it.isActive && (it.userId == activePrincipal?.id || it.userId == currentUserId) }
            ?: userProfiles.firstOrNull { it.isActive }
            ?: userProfiles.firstOrNull()
    }
    val profileData = remember(activeProfile) {
        if (activeProfile != null && activeProfile.specialties.isNotBlank()) {
            ProviderServiceProfileData.fromJsonString(activeProfile.specialties)
        } else {
            val domain = activeProfile?.providerType?.let { ProviderDomainCategory.fromId(it) }
                ?: ProviderDomainCategory.AUTOMOTIVE_MECHANIC
            ProviderServiceProfileData.defaultTemplateForCategory(domain)
        }
    }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var pendingTopUpRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }
    var requiredFeeForTopUp by remember { mutableLongStateOf(0L) }
    var pendingProofRequest by remember { mutableStateOf<Triple<Long, String, String>?>(null) }
    val topupProofPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pending = pendingProofRequest
        pendingProofRequest = null
        if (uri != null && pending != null) scope.launch {
            var proofFile: java.io.File? = null
            runCatching {
                check(viewModel.currentUserId == pending.third) { "La sesión cambió" }
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size().toLong() + count <= com.elysium369.meet.ride.domain.RideProofFormat.MAX_BYTES) {
                                "Comprobante mayor de 12 MB"
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("No se pudo leer el comprobante")
                    val extension = com.elysium369.meet.ride.domain.RideProofFormat.extension(bytes)
                        ?: error("Selecciona JPG, PNG, WEBP o PDF")
                    java.io.File.createTempFile("sinpe-proof-", ".$extension", context.cacheDir).apply { writeBytes(bytes) }
                }
                proofFile = file
                check(viewModel.currentUserId == pending.third) { "La sesión cambió" }
                com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway.submitWalletTopup(
                    file.absolutePath, pending.first, null, pending.second
                )
            }.onSuccess {
                Toast.makeText(context, "Comprobante recibido por el servidor. Pendiente de validación del propietario; saldo sin abonar.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "No se pudo enviar el comprobante. Reintenta; el saldo no fue abonado.", Toast.LENGTH_LONG).show()
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { proofFile?.delete() }
        }
    }


    // Multi-domain technical inspection state (Pillar 4)
    var dtcCodeInput by rememberSaveable { mutableStateOf("") }
    var mechanicalSystem by rememberSaveable { mutableStateOf("Motor / Inyección") }
    var plumbingLocation by rememberSaveable { mutableStateOf("Baño Principal") }
    var plumbingPipeSize by rememberSaveable { mutableStateOf("1/2 pulgada") }
    var plumbingSeverity by rememberSaveable { mutableStateOf("Fuga continua") }
    var locksmithType by rememberSaveable { mutableStateOf("Residencial alta seguridad") }
    var locksmithIssue by rememberSaveable { mutableStateOf("Llave quebrada adentro") }
    var electricalVoltage by rememberSaveable { mutableStateOf("110V Monofásica") }
    var electricalIssue by rememberSaveable { mutableStateOf("Breaker se dispara") }
    var towCondition by rememberSaveable { mutableStateOf("4 ruedas giran libremente") }
    var towDestination by rememberSaveable { mutableStateOf("Taller Mecánico Especializado") }

    // Auto-evaluate Laya System 1 diagnostic when problem description changes
    LaunchedEffect(problemInput, activeCategory) {
        if (problemInput.trim().length >= 4) {
            val questions = listOf(
                LayaQuestion.Choice(
                    name = "problem_severity",
                    options = listOf("NORMAL", "ELEVATED", "CRITICAL_SAFETY")
                ),
                LayaQuestion.Score(
                    name = "fair_price_index",
                    levels = 5
                )
            )
            val batch = layaDecisionEngine.evaluateSync("${activeCategory.name} - $problemInput", questions)
            val severity = batch.choice("problem_severity")?.value ?: "NORMAL"
            val priceLevel = batch.score("fair_price_index")?.level ?: 2
            val suggestedPrice = 12000 + (priceLevel * 6000)

            layaDiagnosticHint = when (severity) {
                "CRITICAL_SAFETY" -> "⚠️ Diagnóstico Crítico: Se sugiere no rodar el vehículo. Vanguard Titan estima precio justo de mano de obra en ₡${suggestedPrice} CRC."
                "ELEVATED" -> "⚡ Diagnóstico Elevado: Requiere atención técnica pronta. Rango estimado: ₡${suggestedPrice} CRC."
                else -> "✓ Diagnóstico Estándar: Vanguard Titan estima mano de obra justa en ₡${suggestedPrice} CRC."
            }
        } else {
            layaDiagnosticHint = null
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ELYSIUM SERVICIOS & SUBASTA",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = Color.White,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = if (isSpecialistMode) "Modo Especialista (Tomar / Contraofertar)" else "Modo Cliente (Preparar solicitud)",
                                fontSize = 11.sp,
                                color = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                        }
                    },
                    actions = {
                        // Acceso directo a Servicios Activos / Finalizados
                        IconButton(onClick = { navController.safeNavigate(MeetDestinations.SERVICES_ACTIVE) }) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "Mis Servicios", tint = MeetColors.neonGreen)
                        }

                        // Mode Switcher Chip
                        Surface(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { isSpecialistMode = !isSpecialistMode },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSpecialistMode) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.cyberCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSpecialistMode) Icons.Default.Engineering else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isSpecialistMode) "ESPECIALISTA" else "CLIENTE",
                                    color = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
                )
            }
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // View Mode Switcher: [ 📋 Solicitudes ] | [ 🗺️ Radar & Ruta en Vivo ]
            ViewModeSelector(
                selectedMode = selectedViewMode,
                onSelectMode = { selectedViewMode = it },
                activeAcceptedCount = allRequests.count { it.status == "ACCEPTED" }
            )

            // ── Barra Rápida de Servicios: Activos, Historial & Catálogo Proveedor ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { navController.safeNavigate(MeetDestinations.SERVICES_ACTIVE) },
                    shape = RoundedCornerShape(10.dp),
                    color = MeetColors.neonGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("⚡", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("Activos", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Surface(
                    onClick = { navController.safeNavigate(MeetDestinations.SERVICES_COMPLETED) },
                    shape = RoundedCornerShape(10.dp),
                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📜", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("Historial", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Surface(
                    onClick = { navController.safeNavigate(MeetDestinations.PROVIDER_SERVICES_CONFIG) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🛠️", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("Mis Servicios", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            if (selectedViewMode == "MAP") {
                ElysiumServicesLiveMapRadar(
                    allRequests = allRequests,
                    focusedRequest = focusedMapRequest,
                    onSelectRequest = { focusedMapRequest = it },
                    userGps = gps,
                    isSpecialistMode = isSpecialistMode,
                    activeCategory = activeCategory,
                    onOpenEmergencyHaven = { emergencyHavenDialogOpen = true },
                    onCompleteRequest = { req -> ratingDialogRequest = req }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // ── Section 1: Master Agent Advisory Banner ──
                    item {
                        AgentAdvisoryHeroCard(
                            category = activeCategory,
                            diagnosticHint = layaDiagnosticHint
                        )
                    }

                    // ── Section 2: Spatial Search Bar (Costa Rica First + Voice) ──
                    item {
                        SpatialSearchBarCard(
                            query = locationInput,
                            onQueryChange = { newQuery ->
                                locationInput = newQuery
                                if (newQuery.length >= 2) {
                                    scope.launch {
                                        isSearchingPlaces = true
                                        searchSuggestions = placeSearchProvider.search(
                                            query = newQuery,
                                            biasLatitude = gps?.latitude ?: 9.9333,
                                            biasLongitude = gps?.longitude ?: -84.0833,
                                            limit = 5
                                        )
                                        isSearchingPlaces = false
                                    }
                                } else {
                                    searchSuggestions = emptyList()
                                }
                            },
                            onVoiceClick = { voiceSearchActive = true },
                            suggestions = searchSuggestions,
                            onSelectSuggestion = { suggestion ->
                                locationInput = suggestion.displayLabel
                                locationLat = suggestion.latitude
                                locationLon = suggestion.longitude
                                searchSuggestions = emptyList()
                                Toast.makeText(context, "Ubicación fijada: ${suggestion.primaryLabel}", Toast.LENGTH_SHORT).show()
                            },
                            onSelectCategoryFilter = { catKeyword ->
                                locationInput = catKeyword
                                scope.launch {
                                    isSearchingPlaces = true
                                    searchSuggestions = placeSearchProvider.search(
                                        query = catKeyword,
                                        biasLatitude = gps?.latitude ?: 9.9333,
                                        biasLongitude = gps?.longitude ?: -84.0833,
                                        limit = 6
                                    )
                                    isSearchingPlaces = false
                                }
                            }
                        )
                    }

                    // ── Mode-Specific Flows ──
                    if (!isSpecialistMode) {
                // ══════════════════════════════════════════════
                //  C L I E N T   F L O W
                // ══════════════════════════════════════════════

                // Category selector pills
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECCIONA CATEGORÍA DE SERVICIO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(activeCategory.accentColor.copy(alpha = 0.15f))
                                .border(1.dp, activeCategory.accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${categories.size} ACTIVAS",
                                color = activeCategory.accentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(categories.indices.toList()) { index ->
                            val cat = categories[index]
                            val isSelected = (index == selectedCategoryIndex)
                            val accent = cat.accentColor
                            EliteCard(
                                glowColor = if (isSelected) accent else null,
                                borderColor = if (isSelected) accent else MeetColors.borderSubtle.copy(alpha = 0.4f),
                                backgroundColor = if (isSelected) Color(0xFF0F1A2E) else MeetColors.cardBackground,
                                shape = RoundedCornerShape(14.dp),
                                onClick = { selectedCategoryIndex = index },
                                modifier = Modifier.heightIn(min = 60.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) accent.copy(alpha = 0.22f) else Color(0xFF141F33))
                                            .border(1.dp, if (isSelected) accent else MeetColors.borderSubtle.copy(alpha = 0.35f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(cat.icon, fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(
                                            text = cat.name,
                                            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                        Text(
                                            text = cat.subtitle,
                                            color = if (isSelected) accent else MeetColors.textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Request Form Card
                item {
                    EliteCard(
                        glowColor = activeCategory.accentColor,
                        borderColor = activeCategory.accentColor.copy(alpha = 0.35f),
                        backgroundColor = MeetColors.cardBackground,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(activeCategory.icon, fontSize = 16.sp)
                                    Text(
                                        text = "DETALLE DE LA SOLICITUD",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(activeCategory.accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, activeCategory.accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activeCategory.name.uppercase(),
                                        color = activeCategory.accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = problemInput,
                                onValueChange = { problemInput = it },
                                label = { Text("Describe el problema o necesidad") },
                                placeholder = { Text("Ej: El carro no enciende, hace un clic pero no da marcha") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.cyberCyan,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3
                            )

                            Spacer(Modifier.height(8.dp))

                            // ── Multi-domain Dynamic Technical Inspection (Pillar 4) ──
                            MultiDomainInspectionFields(
                                domainKey = activeCategory.domainKey,
                                dtcCode = dtcCodeInput,
                                onDtcCodeChange = { dtcCodeInput = it },
                                mechanicalSystem = mechanicalSystem,
                                onMechanicalSystemChange = { mechanicalSystem = it },
                                plumbingLocation = plumbingLocation,
                                onPlumbingLocationChange = { plumbingLocation = it },
                                plumbingPipeSize = plumbingPipeSize,
                                onPlumbingPipeSizeChange = { plumbingPipeSize = it },
                                plumbingSeverity = plumbingSeverity,
                                onPlumbingSeverityChange = { plumbingSeverity = it },
                                locksmithType = locksmithType,
                                onLocksmithTypeChange = { locksmithType = it },
                                locksmithIssue = locksmithIssue,
                                onLocksmithIssueChange = { locksmithIssue = it },
                                electricalVoltage = electricalVoltage,
                                onElectricalVoltageChange = { electricalVoltage = it },
                                electricalIssue = electricalIssue,
                                onElectricalIssueChange = { electricalIssue = it },
                                towCondition = towCondition,
                                onTowConditionChange = { towCondition = it },
                                towDestination = towDestination,
                                onTowDestinationChange = { towDestination = it }
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = priceOfferText,
                                    onValueChange = { priceOfferText = it.filter(Char::isDigit) },
                                    label = { Text("Tu Oferta Base (CRC)") },
                                    prefix = { Text("₡ ", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MeetColors.cyberCyan,
                                        unfocusedBorderColor = MeetColors.borderSubtle,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (problemInput.isBlank()) {
                                            Toast.makeText(context, "Por favor describe el problema", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val price = priceOfferText.toDoubleOrNull() ?: 18000.0
                                        val techSummary = when (activeCategory.domainKey) {
                                            "AUTO_MECHANICAL" -> " [DTC: ${dtcCodeInput.ifBlank { "Sin DTC" }} | Sist: $mechanicalSystem]"
                                            "PLUMBING" -> " [Ubicación: $plumbingLocation | Tubería: $plumbingPipeSize | $plumbingSeverity]"
                                            "LOCKSMITH" -> " [Chapa: $locksmithType | $locksmithIssue]"
                                            "ELECTRICAL" -> " [Voltaje: $electricalVoltage | $electricalIssue]"
                                            "AUTO_TOW" -> " [Condición: $towCondition | Destino: $towDestination]"
                                            "BATTERY" -> " [Batería / Jumpstart]"
                                            "PARTS_STORE" -> " [Repuestos: VIN/OEM compatible]"
                                            "COURIER" -> " [Mensajería express]"
                                            "MOVING" -> " [Mudanza & Flete]"
                                            "DETAILING" -> " [Estética & Detailing]"
                                            "HARDWARE" -> " [Materiales & Herramientas]"
                                            "PULPERIA" -> " [Abarrotes express]"
                                            "SODA_RESTAURANT" -> " [Comida típica express]"
                                            "PROFESSIONAL" -> " [Servicio Profesional]"
                                            "EDUCATION" -> " [Tutoría & Educación PISA]"
                                            else -> ""
                                        }
                                        val fullProblemDescription = "${activeCategory.name}: $problemInput$techSummary"
                                        val definitionId = when(activeCategory.domainKey) {
                                            "AUTO_MECHANICAL" -> "mechanical"
                                            "AUTO_TOW" -> "roadside"
                                            "LOCKSMITH" -> "hardware_locksmith"
                                            "BATTERY" -> "mechanical"
                                            "HARDWARE" -> "hardware_materials"
                                            "DETAILING" -> "vehicle_detailing"
                                            "PLUMBING" -> "plumbing"
                                            "ELECTRICAL" -> "electrical_home"
                                            "PULPERIA" -> "pulperia_groceries"
                                            "SODA_RESTAURANT" -> "soda_traditional_food"
                                            "PARTS_STORE" -> "auto_parts"
                                            "COURIER" -> "courier"
                                            "MOVING" -> "moving"
                                            "PROFESSIONAL" -> "accounting"
                                            "EDUCATION" -> "tutoring"
                                            else -> "soda_traditional_food"
                                        }
                                        onPrepareRequest?.invoke(ServicesRequestDraft(definitionId,fullProblemDescription,
                                            "$problemInput$techSummary",locationInput,price.toLong(),"PHYSICAL",locationLat,locationLon))
                                        if(onPrepareRequest==null) openAuthoritativeServices("Continúa la solicitud técnica en línea; todavía no se ha publicado.")

                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(56.dp)
                                        .align(Alignment.CenterVertically),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MeetColors.backgroundDark)
                                    Spacer(Modifier.width(6.dp))
                                    Text("PUBLICAR ⚡", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Active Requests & Bids Review Section
                item {
                    Text(
                        text = "TUS SOLICITUDES ACTIVAS & CONTRAOFERTAS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                val openClientRequests = allRequests.filter { it.status == "OPEN" || it.status == "ACCEPTED" }
                if (openClientRequests.isEmpty()) {
                    item {
                        EliteCard(
                            glowColor = MeetColors.cyberCyan,
                            borderColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📡", fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "SIN SOLICITUDES ACTIVAS",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Publica una solicitud arriba para que especialistas cercanos te envíen ofertas en tiempo real.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(openClientRequests, key = { it.requestId }) { req ->
                        ClientActiveRequestCard(
                            request = req,
                            viewModel = viewModel,
                            onCompleteAndRate = { ratingDialogRequest = req },
                            onTrackOnMap = {
                                focusedMapRequest = req
                                selectedViewMode = "MAP"
                            }
                        )
                    }
                }

            } else {
                // ══════════════════════════════════════════════
                //  S P E C I A L I S T   F L O W
                // ══════════════════════════════════════════════

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RADAR DE SOLICITUDES EN TU RADIO (15 KM)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Text(
                                text = "RADAR ACTIVO",
                                color = MeetColors.neonGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val openMarketplaceRequests = allRequests.filter { it.status == "OPEN" }
                if (openMarketplaceRequests.isEmpty()) {
                    item {
                        EliteCard(
                            glowColor = MeetColors.neonGreen,
                            borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MeetColors.neonGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "RADAR EN VIVO: ESCANEANDO COSTA RICA",
                                        color = MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Buscando nuevas solicitudes en tu zona. Las alertas aparecerán aquí con distancia y ruta en tiempo real.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(openMarketplaceRequests, key = { it.requestId }) { req ->
                        SpecialistRequestItemCard(
                            request = req,
                            userGps = gps,
                            onViewOnMap = {
                                focusedMapRequest = req
                                selectedViewMode = "MAP"
                            },
                            onTakeDirect = {
                                prepareOffer(ServicesOfferDraft(req.priceOffer.toLong(),1.0,30,"Oferta para ${req.problem}"))
                                openAuthoritativeServices("Esta solicitud pertenece al historial anterior. La nueva propuesta y su comisión requieren confirmación en línea; no se ha cobrado saldo.")
                            },
                            onCounterOffer = {
                                counterOfferDialogRequest = req
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
    }

    // ── Dialog: Contraoferta del Especialista ──
    counterOfferDialogRequest?.let { req ->
        SpecialistCounterOfferDialog(
            request = req,
            onDismiss = { counterOfferDialogRequest = null },
            onSubmitCounterOffer = { price, hours, warranty, note ->
                prepareOffer(ServicesOfferDraft(price.toLong(),hours,warranty,note))
                openAuthoritativeServices("Borrador de contraoferta histórica: ₡$price · $hours horas · garantía $warranty días · $note. Aún no se ha enviado ni cobrado comisión; selecciona una solicitud en línea.")
                counterOfferDialogRequest = null
            }
        )
    }

    // ── Dialog: Recarga SINPE Móvil de Saldo Constitucional (5%) ──
    if (showTopUpDialog) {
        SinpeTopUpDialog(
            currentBalanceCrc = profileData.walletBalanceCrc,
            requiredFeeCrc = requiredFeeForTopUp,
            onDismiss = {
                showTopUpDialog = false
                pendingTopUpRequest = null
            },
            onConfirmTopUp = submit@ { amountCrc, ref ->
                // Only server review can confirm a top-up; never mutate a JSON balance.
                val owner = viewModel.currentUserId?.takeIf(String::isNotBlank)
                if (owner == null) {
                    Toast.makeText(context, "Inicia sesión antes de solicitar la validación.", Toast.LENGTH_LONG).show()
                    return@submit
                }
                pendingProofRequest = Triple(amountCrc, ref, owner)
                showTopUpDialog = false
                pendingTopUpRequest = null
                topupProofPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
            }
        )
    }

    // ── Dialog: Calificación Bidireccional Estilo Viajes & Cierre Forense ──
    ratingDialogRequest?.let { req ->
        TwoWayRatingDialog(
            request = req,
            onDismiss = { ratingDialogRequest = null },
            onSubmitRating = { stars, praiseTags, reviewText ->
                openAuthoritativeServices("Calificación histórica pendiente: $stars estrellas · ${praiseTags.joinToString()} · $reviewText. No se ha confirmado cierre, certificado ni liberación de fondos; utiliza el servicio en línea correspondiente.")
                ratingDialogRequest = null
            }
        )
    }

    // ── Dialog: Certificado Forense de Cierre de Servicio ──
    completionCertificateData?.let { cert ->
        ServiceCompletionCertificateDialog(
            certificate = cert,
            onDismiss = { completionCertificateData = null }
        )
    }

    // ── Dialog: Refugios Seguros Aura Sentinel ──
    if (emergencyHavenDialogOpen) {
        AuraSentinelEmergencyHavenDialog(
            userGps = gps,
            onDismiss = { emergencyHavenDialogOpen = false },
            onSelectHaven = { havenPlace: CostaRicaGisDatabase.GisPlace ->
                locationInput = havenPlace.name
                locationLat = havenPlace.latitude
                locationLon = havenPlace.longitude
                emergencyHavenDialogOpen = false
                AuraSentinelNotificationCoordinator.notifyEmergencyBeaconActivated(
                    context = context,
                    beaconId = UUID.randomUUID().toString().take(8),
                    locationLabel = havenPlace.name
                )
                Toast.makeText(context, "Ruta de auxilio trazada hacia: ${havenPlace.name}", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── Dialog: Asistente de Búsqueda por Voz ──
    if (voiceSearchActive) {
        VoiceSearchAssistantDialog(
            onDismiss = { voiceSearchActive = false },
            onQueryRecognized = { speech ->
                val parsed = spatialEngine.parseVoiceQuery(speech)
                if (parsed.targetPlaceName != null) {
                    locationInput = parsed.targetPlaceName
                    scope.launch {
                        val results = spatialEngine.search(parsed.targetPlaceName, 9.9333, -84.0833, 1)
                        if (results.isNotEmpty()) {
                            locationLat = results.first().latitude
                            locationLon = results.first().longitude
                        }
                    }
                } else {
                    locationInput = speech
                }
                if (parsed.recognizedIntent == "AUTO_TOW") selectedCategoryIndex = 1
                if (parsed.recognizedIntent == "LOCKSMITH") selectedCategoryIndex = 2
                voiceSearchActive = false
                Toast.makeText(context, "Intención Laya: ${parsed.recognizedIntent} en ${parsed.normalizedQuery}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

data class ServiceCategoryItem(
    val name: String,
    val icon: String,
    val domainKey: String,
    val masterAgentName: String,
    val accentColor: Color = MeetColors.cyberCyan,
    val subtitle: String = "Servicio Especializado",
)

@Composable
private fun AgentAdvisoryHeroCard(
    category: ServiceCategoryItem,
    diagnosticHint: String?,
) {
    EliteCard(
        glowColor = category.accentColor,
        borderColor = category.accentColor.copy(alpha = 0.45f),
        backgroundColor = Color(0xFF091424),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(category.accentColor.copy(alpha = 0.16f), CircleShape)
                    .border(1.dp, category.accentColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon, fontSize = 24.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(category.accentColor.copy(alpha = 0.18f))
                            .border(1.dp, category.accentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "GUÍA: ${category.masterAgentName.uppercase()}",
                            color = category.accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MeetColors.neonGreen.copy(alpha = 0.12f))
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SUBASTA AUDITADA",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = diagnosticHint ?: "${category.masterAgentName} audita precios de mercado en tiempo real para garantizar cobro justo en ${category.name}.",
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SpatialSearchBarCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    suggestions: List<RidePlaceSuggestion>,
    onSelectSuggestion: (RidePlaceSuggestion) -> Unit,
    onSelectCategoryFilter: (String) -> Unit = {},
) {
    val quickPois = remember {
        listOf(
            "🛠️ DEKRA" to "dekra",
            "⛽ Gasolineras" to "gasolinera",
            "🛒 Supermercados" to "automercado",
            "🏥 Hospitales" to "hospital",
            "🔩 Repuestos" to "repuestos",
            "🏬 Malls" to "multiplaza",
            "🏢 COSEVI" to "cosevi"
        )
    }

    EliteCard(
        glowColor = MeetColors.cyberCyan,
        borderColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Lugar (ej: San José, Escazú, La Sabana...)", color = MeetColors.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    singleLine = true
                )
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen.copy(alpha = 0.15f))
                        .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Búsqueda por voz Laya", tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                }
            }

            // Quick POI Discovery Chips (Costa Rica master GIS)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPois) { (label, keyword) ->
                    Surface(
                        modifier = Modifier.clickable { onSelectCategoryFilter(keyword) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F1A2E),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = label,
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSuggestion(suggestion) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(suggestion.primaryLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(suggestion.secondaryLabel, color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientActiveRequestCard(
    request: ServiceRequestEntity,
    viewModel: ObdViewModel,
    onCompleteAndRate: () -> Unit,
    onTrackOnMap: () -> Unit = {},
) {
    val openAuthoritativeServices = LocalServiceOnlineAction.current
    val bids by viewModel.getBidsForRequest(request.requestId).collectAsState(initial = emptyList())
    val isAccepted = request.status == "ACCEPTED"

    EliteCard(
        glowColor = if (isAccepted) MeetColors.neonGreen else MeetColors.cyberCyan,
        borderColor = if (isAccepted) MeetColors.neonGreen.copy(alpha = 0.6f) else MeetColors.cyberCyan.copy(alpha = 0.35f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isAccepted) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isAccepted) MeetColors.neonGreen else MeetColors.cyberCyan)
                ) {
                    Text(
                        text = if (isAccepted) "ESPECIALISTA EN CAMINO" else "SUBASTA ACTIVA (${bids.size} OFERTAS)",
                        color = if (isAccepted) MeetColors.neonGreen else MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(request.problem, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(request.location, color = MeetColors.textSecondary, fontSize = 11.sp)

            // Live ETA and Route Tracker if accepted
            if (isAccepted) {
                val clientLat = if (request.latitude != 0.0) request.latitude else 9.9333
                val clientLon = if (request.longitude != 0.0) request.longitude else -84.0833
                val specialistOrigin = GeoPoint(clientLat + 0.016, clientLon + 0.014)
                val routeEstimate = remember(request.requestId, clientLat, clientLon) {
                    LayaRouteEngine.calculateRoute(
                        origin = specialistOrigin,
                        destination = GeoPoint(clientLat, clientLon)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF071B26),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱️", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "ETA ESTIMADO: ${routeEstimate.etaMinutes} MIN",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${String.format("%.1f", routeEstimate.distanceKm)} km · ${routeEstimate.trafficLevel.label}",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (routeEstimate.trafficLevel) {
                                    LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                    LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning.copy(alpha = 0.2f)
                                    else -> MeetColors.error.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = "${routeEstimate.estimatedSpeedKmh.toInt()} km/h",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onTrackOnMap,
                                modifier = Modifier.weight(1.3f),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("RASTREAR EN MAPA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            WazeNavigationButton(
                                destinationLat = clientLat,
                                destinationLng = clientLon,
                                destinationLabel = request.problem,
                            )
                        }
                    }
                }

                // Security PIN if accepted (Aura Sentinel)
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F261C),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PIN DE SEGURIDAD AURA SENTINEL", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text("Proporciona este código al especialista al llegar", color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                        Text(
                            text = request.requestId.takeLast(4).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCompleteAndRate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.backgroundDark)
                    Spacer(Modifier.width(6.dp))
                    Text("TRABAJO COMPLETADO & CALIFICAR", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            } else {
                // List received counter-offers
                if (bids.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Contraofertas Recibidas:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))

                    bids.forEach { bid ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDark),
                            border = BorderStroke(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("⭐ ${bid.shopRating} · Garantía ${bid.warrantyDays} días", color = MeetColors.textSecondary, fontSize = 10.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₡${String.format("%,.0f", bid.price)} CRC",
                                            color = MeetColors.cyberCyan,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                        if (bid.price > request.priceOffer) {
                                            Text(
                                                text = "+₡${String.format("%,.0f", bid.price - request.priceOffer)} contraoferta",
                                                color = MeetColors.warning,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                if (bid.message.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(bid.message, color = MeetColors.textSecondary, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }

                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        openAuthoritativeServices("La oferta histórica necesita reconciliación con el servidor. No se ha asignado ni cobrado un servicio.")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ACEPTAR ESTA OFERTA", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialistRequestItemCard(
    request: ServiceRequestEntity,
    userGps: ObdViewModel.GpsLocationInfo? = null,
    onViewOnMap: () -> Unit = {},
    onTakeDirect: () -> Unit,
    onCounterOffer: () -> Unit,
) {
    val reqLat = if (request.latitude != 0.0) request.latitude else 9.9333
    val reqLon = if (request.longitude != 0.0) request.longitude else -84.0833
    val specialistOrigin = GeoPoint(userGps?.latitude ?: 9.9333, userGps?.longitude ?: -84.0833)
    val reqEstimate = remember(request.requestId, specialistOrigin) {
        LayaRouteEngine.calculateRoute(
            origin = specialistOrigin,
            destination = GeoPoint(reqLat, reqLon)
        )
    }

    EliteCard(
        glowColor = MeetColors.neonGreen,
        borderColor = MeetColors.neonGreen.copy(alpha = 0.4f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Text(
                        text = "SOLICITUD EN TU ZONA",
                        color = MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "Oferta: ₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(request.problem, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(request.location, color = MeetColors.textSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MeetColors.backgroundDark,
                border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${String.format("%.1f", reqEstimate.distanceKm)} km vial · ETA ~${reqEstimate.etaMinutes} min",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = reqEstimate.trafficLevel.label,
                        color = when (reqEstimate.trafficLevel) {
                            LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen
                            LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning
                            else -> MeetColors.error
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Desglose transparente de comisión constitucional (5%)
            val grossAmount = request.priceOffer.toLong()
            val feeAmount = maxOf(1L, (grossAmount * 500L) / 10000L)
            val netEarnings = maxOf(0L, grossAmount - feeAmount)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF061524), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ganancia neta: ₡${"%,d".format(netEarnings)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "Comisión 5%: -₡${"%,d".format(feeAmount)}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Fila 1: Navegación & Radar (Responsive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewOnMap,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Radar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                WazeNavigationButton(
                    destinationLat = reqLat,
                    destinationLng = reqLon,
                    destinationLabel = request.problem,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fila 2: Ofertas & Negociación (Responsive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCounterOffer,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Contraofertar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTakeDirect,
                    modifier = Modifier.weight(1.1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.backgroundDark, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tomar Directo", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SpecialistCounterOfferDialog(
    request: ServiceRequestEntity,
    onDismiss: () -> Unit,
    onSubmitCounterOffer: (price: Double, hours: Double, warranty: Int, note: String) -> Unit,
) {
    var priceText by remember { mutableStateOf(request.priceOffer.toInt().toString()) }
    var arrivalMinutesText by remember { mutableStateOf("25") }
    var warrantyDaysText by remember { mutableStateOf("30") }
    var technicalNote by remember { mutableStateOf("Servicio con herramientas profesionales certificadas y garantía local.") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081422)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ENVIAR CONTRAOFERTA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Ajusta el precio, tiempo de llegada y condiciones", color = MeetColors.textSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("Tu Precio Propuesto (CRC)") },
                    prefix = { Text("₡ ", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = arrivalMinutesText,
                        onValueChange = { arrivalMinutesText = it.filter(Char::isDigit) },
                        label = { Text("Llegada (min)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = warrantyDaysText,
                        onValueChange = { warrantyDaysText = it.filter(Char::isDigit) },
                        label = { Text("Garantía (días)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = technicalNote,
                    onValueChange = { technicalNote = it },
                    label = { Text("Nota Técnica para el Cliente") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: request.priceOffer
                            val warranty = warrantyDaysText.toIntOrNull() ?: 30
                            onSubmitCounterOffer(price, 1.0, warranty, technicalNote)
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ENVIAR CONTRAOFERTA", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoWayRatingDialog(
    request: ServiceRequestEntity,
    onDismiss: () -> Unit,
    onSubmitRating: (stars: Int, praiseTags: List<String>, review: String) -> Unit,
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reviewText by remember { mutableStateOf("") }

    val tags = listOf(
        "Puntualidad Impecable",
        "Diagnóstico Preciso",
        "Precio Honesto",
        "Herramienta Profesional",
        "Excelente Trato"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081422)),
            border = BorderStroke(1.dp, MeetColors.neonGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("CALIFICAR SERVICIO (ESTILO VIAJES)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Tu valoración honesta forja la confianza de la red", color = MeetColors.textSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))

                // Stars row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { selectedStars = i }) {
                            Icon(
                                imageVector = if (i <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i estrellas",
                                tint = if (i <= selectedStars) Color(0xFFFFB300) else MeetColors.textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Praise tags
                Text("Aspectos Destacados:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags) { tag ->
                        val isSelected = tag in selectedTags
                        Surface(
                            modifier = Modifier.clickable {
                                selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.backgroundDark,
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = tag,
                                color = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    placeholder = { Text("Comentario adicional opcional...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmitRating(selectedStars, selectedTags.toList(), reviewText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONFIRMAR & ENVIAR CALIFICACIÓN ⭐", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchAssistantDialog(
    onDismiss: () -> Unit,
    onQueryRecognized: (String) -> Unit,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Toca el micrófono o selecciona una consulta...") }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CR")
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusMessage = "Escuchando en vivo... Habla con Laya"
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        statusMessage = "Procesando intención espacial..."
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        statusMessage = "Audio no detectado. Toca de nuevo o usa un preset."
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            recognizedText = text
                            statusMessage = "¡Entendido! “$text”"
                            tts?.speak("Entendido. Ubicando servicio en Costa Rica.", TextToSpeech.QUEUE_FLUSH, null, "laya_resp")
                            onQueryRecognized(text)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            recognizedText = it
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizer = recognizer
        }

        onDispose {
            try {
                speechRecognizer?.destroy()
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
        }
    }

    fun startListening() {
        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                statusMessage = "No se pudo iniciar el reconocedor: ${e.message}"
            }
        } else {
            statusMessage = "Reconocimiento no disponible en este dispositivo."
        }
    }

    fun handleSelection(query: String) {
        tts?.speak("Entendido. Ubicando servicio para $query", TextToSpeech.QUEUE_FLUSH, null, "laya_preset")
        onQueryRecognized(query)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF071424)),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 3D Avatar (Pillar 5)
                Agent3dAvatarCanvas(
                    avatarVisualType = "ORACLE",
                    themeColor = MeetColors.cyberCyan,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0x2200E5FF)),
                    isInteractive = true,
                    isPulsing = isListening
                )

                Spacer(Modifier.height(10.dp))
                Text("ASISTENTE DE VOZ LAYA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    text = "Comprensión bidireccional de intención y geocodificación en Costa Rica",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                // Microphone activation button
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                            } else {
                                startListening()
                            }
                        },
                    shape = CircleShape,
                    color = if (isListening) MeetColors.neonGreen.copy(alpha = 0.25f) else MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, if (isListening) MeetColors.neonGreen else MeetColors.cyberCyan)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Micrófono",
                            tint = if (isListening) MeetColors.neonGreen else MeetColors.cyberCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusMessage,
                    color = if (isListening) MeetColors.neonGreen else MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (recognizedText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan)
                    ) {
                        Text(
                            text = "“$recognizedText”",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("O prueba una consulta táctica:", color = MeetColors.textSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))

                // Quick presets
                listOf(
                    "Ocupo una grúa en Cartago centro",
                    "Taller mecánico cerca de San Pedro",
                    "Cerrajero automotriz en Escazú",
                    "Vamos a La Sabana"
                ).forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { handleSelection(preset) },
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.backgroundDark,
                        border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎙️", fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "“$preset”",
                                color = MeetColors.cyberCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", color = Color.White)
                }
            }
        }
    }
}

/**
 * ── Selector de Modo de Vista (Solicitudes vs Radar en Vivo) ──
 */
@Composable
private fun ViewModeSelector(
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    activeAcceptedCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MeetColors.cardBackground,
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isList = selectedMode == "LIST"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode("LIST") },
                shape = RoundedCornerShape(9.dp),
                color = if (isList) MeetColors.cyberCyan.copy(alpha = 0.22f) else Color.Transparent,
                border = if (isList) BorderStroke(1.dp, MeetColors.cyberCyan) else null
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = if (isList) MeetColors.cyberCyan else MeetColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SOLICITUDES",
                        color = if (isList) MeetColors.cyberCyan else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isList) FontWeight.Black else FontWeight.Bold
                    )
                }
            }

            val isMap = selectedMode == "MAP"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode("MAP") },
                shape = RoundedCornerShape(9.dp),
                color = if (isMap) MeetColors.neonGreen.copy(alpha = 0.22f) else Color.Transparent,
                border = if (isMap) BorderStroke(1.dp, MeetColors.neonGreen) else null
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = if (isMap) MeetColors.neonGreen else MeetColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "RADAR & RUTA EN VIVO",
                        color = if (isMap) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isMap) FontWeight.Black else FontWeight.Bold
                    )
                    if (activeAcceptedCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen
                        ) {
                            Text(
                                text = "$activeAcceptedCount",
                                color = MeetColors.backgroundDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ── Radar & Mapa en Vivo con Rutas Reales, ETA Dinámico y Refugios Seguros ──
 */
@Composable
private fun ElysiumServicesLiveMapRadar(
    allRequests: List<ServiceRequestEntity>,
    focusedRequest: ServiceRequestEntity?,
    onSelectRequest: (ServiceRequestEntity) -> Unit,
    userGps: ObdViewModel.GpsLocationInfo?,
    isSpecialistMode: Boolean,
    activeCategory: ServiceCategoryItem,
    onOpenEmergencyHaven: () -> Unit,
    onCompleteRequest: (ServiceRequestEntity) -> Unit,
) {
    val context = LocalContext.current
    val activeRequest = focusedRequest
        ?: allRequests.firstOrNull { it.status == "ACCEPTED" }
        ?: allRequests.firstOrNull { it.status == "OPEN" }

    val clientLat = if (activeRequest != null && activeRequest.latitude != 0.0) activeRequest.latitude else (userGps?.latitude ?: 9.9333)
    val clientLon = if (activeRequest != null && activeRequest.longitude != 0.0) activeRequest.longitude else (userGps?.longitude ?: -84.0833)
    val clientPoint = GeoPoint(clientLat, clientLon)

    val specialistLat = if (isSpecialistMode && userGps != null) userGps.latitude else (clientLat + 0.016)
    val specialistLon = if (isSpecialistMode && userGps != null) userGps.longitude else (clientLon + 0.014)
    val specialistPoint = GeoPoint(specialistLat, specialistLon)

    val routeEstimate = remember(specialistPoint, clientPoint) {
        LayaRouteEngine.calculateRoute(
            origin = specialistPoint,
            destination = clientPoint
        )
    }

    val companionState = remember(clientPoint, specialistPoint, isSpecialistMode) {
        ElysiumRouteCompanion.evaluateCompanionState(
            currentLatitude = clientPoint.latitude,
            currentLongitude = clientPoint.longitude,
            destinationLatitude = specialistPoint.latitude,
            destinationLongitude = specialistPoint.longitude,
            isClientWaiting = !isSpecialistMode
        )
    }

    // Heads-up proximity alert: Aura Sentinel dispatches notification when ETA <= 3 min (Pillar 3)
    LaunchedEffect(companionState.isArrivingSoon, activeRequest?.requestId) {
        if (companionState.isArrivingSoon && activeRequest != null && activeRequest.status == "ACCEPTED") {
            AuraSentinelNotificationCoordinator.notifyArrivalImminent(
                context = context,
                requestId = activeRequest.requestId,
                securityPin = activeRequest.requestId.takeLast(4).uppercase(),
                etaMinutes = routeEstimate.etaMinutes
            )
        }
    }

    var selectedBottomTab by remember { mutableStateOf("STAGING") } // "STAGING" or "CHECKLIST"

    // Build CommonMapState
    val mapMarkers = remember(activeRequest, allRequests, clientPoint, specialistPoint, companionState) {
        val markers = mutableListOf<GeoMarker>()

        // 1. Client / Vehicle marker
        markers.add(
            GeoMarker(
                id = "client_vehicle",
                role = GeoMarkerRole.DESTINATION,
                point = clientPoint,
                label = if (activeRequest != null) activeRequest.problem else "Tu Ubicación",
                subtitle = if (activeRequest != null) "₡${String.format("%,.0f", activeRequest.priceOffer)} CRC" else "Costa Rica",
                isHighlighted = true
            )
        )

        // 2. Specialist marker
        markers.add(
            GeoMarker(
                id = "specialist_live",
                role = GeoMarkerRole.PROVIDER_LIVE,
                point = specialistPoint,
                label = if (isSpecialistMode) "Tu Posición (Especialista)" else "Especialista MEET",
                subtitle = "ETA ~${routeEstimate.etaMinutes} min (${routeEstimate.trafficLevel.label})",
                isHighlighted = true
            )
        )

        // 3. Safe Staging Zones (refugios seguros de Costa Rica)
        companionState.safeStagingZones.take(2).forEachIndexed { idx, zone ->
            markers.add(
                GeoMarker(
                    id = "safe_staging_$idx",
                    role = GeoMarkerRole.STORE_LOCATION,
                    point = GeoPoint(zone.place.latitude, zone.place.longitude),
                    label = "Zona Segura: ${zone.place.name}",
                    subtitle = "${zone.distanceKm} km · ${zone.recommendationReason}"
                )
            )
        }

        // 4. Other open requests in the radar
        allRequests.filter { it.requestId != activeRequest?.requestId && it.status == "OPEN" && it.latitude != 0.0 }.forEach { req ->
            markers.add(
                GeoMarker(
                    id = "req_${req.requestId}",
                    role = GeoMarkerRole.GENERIC_SERVICE,
                    point = GeoPoint(req.latitude, req.longitude),
                    label = "Solicitud: ${req.problem.take(20)}...",
                    subtitle = "₡${String.format("%,.0f", req.priceOffer)} CRC"
                )
            )
        }

        markers
    }

    val mapRoutes = remember(routeEstimate) {
        listOf(routeEstimate.geoRoute)
    }

    val cameraBounds = remember(clientPoint, specialistPoint) {
        GeoBounds.fromPoints(listOf(clientPoint, specialistPoint)) ?: GeoBounds(
            northLat = kotlin.math.max(clientPoint.latitude, specialistPoint.latitude) + 0.02,
            southLat = kotlin.math.min(clientPoint.latitude, specialistPoint.latitude) - 0.02,
            eastLng = kotlin.math.max(clientPoint.longitude, specialistPoint.longitude) + 0.02,
            westLng = kotlin.math.min(clientPoint.longitude, specialistPoint.longitude) - 0.02
        )
    }

    val mapState = remember(mapMarkers, mapRoutes, cameraBounds) {
        CommonMapState(
            markers = mapMarkers,
            routes = mapRoutes,
            cameraIntent = MapCameraIntent.FitBounds(cameraBounds, paddingDp = 64),
            isInteractive = true,
            showRecenterButton = true,
            showTrafficOverlay = true
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Universal MapLibre Engine
        CommonMapPanel(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            userLocation = GeoPoint(userGps?.latitude ?: clientLat, userGps?.longitude ?: clientLon),
            onMarkerClick = { markerId ->
                if (markerId.startsWith("req_")) {
                    val reqId = markerId.removePrefix("req_")
                    allRequests.firstOrNull { it.requestId == reqId }?.let { onSelectRequest(it) }
                }
            }
        )

        // ── Floating HUD Header (ETA & Traffic & PIN) ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEA06111D)),
                border = BorderStroke(1.5.dp, if (companionState.isArrivingSoon) MeetColors.warning else MeetColors.cyberCyan)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 3D Avatar (Pillar 5)
                            Agent3dAvatarCanvas(
                                avatarVisualType = when (activeCategory.masterAgentName) {
                                    "Aura Sentinel" -> "SENTINEL"
                                    "Neo Concierge" -> "CONCIERGE"
                                    else -> "TITAN"
                                },
                                themeColor = when (activeCategory.masterAgentName) {
                                    "Aura Sentinel" -> MeetColors.warning
                                    "Neo Concierge" -> MeetColors.cyberCyan
                                    else -> MeetColors.neonGreen
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3300E5FF)),
                                isInteractive = false
                            )

                            Spacer(Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "ETA: ${routeEstimate.etaMinutes} MIN",
                                    color = if (companionState.isArrivingSoon) MeetColors.warning else MeetColors.cyberCyan,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${String.format("%.1f", routeEstimate.distanceKm)} KM · Guía: ${activeCategory.masterAgentName}",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1-Click Waze Navigation Button
                            val destLat = if (isSpecialistMode) clientPoint.latitude else specialistPoint.latitude
                            val destLng = if (isSpecialistMode) clientPoint.longitude else specialistPoint.longitude
                            val destLabel = if (isSpecialistMode) (activeRequest?.problem ?: "Cliente") else "Especialista MEET"
                            if (destLat != 0.0 && destLng != 0.0) {
                                WazeNavigationButton(
                                    destinationLat = destLat,
                                    destinationLng = destLng,
                                    destinationLabel = destLabel,
                                )
                            }

                            // SOS Safe Haven Button
                            Surface(
                                modifier = Modifier.clickable { onOpenEmergencyHaven() },
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.error.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MeetColors.error)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🚨", fontSize = 10.sp)
                                    Spacer(Modifier.width(3.dp))
                                    Text("SOS", color = MeetColors.error, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            // Traffic chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (routeEstimate.trafficLevel) {
                                    LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                    LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning.copy(alpha = 0.2f)
                                    else -> MeetColors.error.copy(alpha = 0.2f)
                                },
                                border = BorderStroke(1.dp, when (routeEstimate.trafficLevel) {
                                    LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen
                                    LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning
                                    else -> MeetColors.error
                                })
                            ) {
                                Text(
                                    text = routeEstimate.trafficLevel.label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (companionState.isArrivingSoon) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MeetColors.warning.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MeetColors.warning)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "¡LLEGADA INMINENTE! Especialista a menos de 3 minutos del vehículo.",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Consejo táctico: ${routeEstimate.tacticalAdvice}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    // Security PIN display (Aura Sentinel)
                    if (activeRequest != null && activeRequest.status == "ACCEPTED") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PIN AURA SENTINEL:",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MeetColors.neonGreen)
                            ) {
                                Text(
                                    text = activeRequest.requestId.takeLast(4).uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick request selector chip row if multiple requests
            if (allRequests.size > 1) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allRequests) { req ->
                        val isSelected = req.requestId == activeRequest?.requestId
                        Surface(
                            modifier = Modifier.clickable { onSelectRequest(req) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MeetColors.cyberCyan else Color(0xCC06111D),
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = "${req.problem.take(16)}... (₡${req.priceOffer.toInt()})",
                                color = if (isSelected) MeetColors.backgroundDark else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Floating Bottom Panel (Safe Staging Zones & Safety Checklist) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2081524)),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Tab Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedBottomTab = "STAGING" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedBottomTab == "STAGING") MeetColors.cyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (selectedBottomTab == "STAGING") BorderStroke(1.dp, MeetColors.cyberCyan) else null
                        ) {
                            Text(
                                text = "🛡️ ZONAS SEGURAS",
                                color = if (selectedBottomTab == "STAGING") MeetColors.cyberCyan else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedBottomTab = "CHECKLIST" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedBottomTab == "CHECKLIST") MeetColors.neonGreen.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (selectedBottomTab == "CHECKLIST") BorderStroke(1.dp, MeetColors.neonGreen) else null
                        ) {
                            Text(
                                text = "📋 PROTOCOLO AURA",
                                color = if (selectedBottomTab == "CHECKLIST") MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (selectedBottomTab == "STAGING") {
                        Text(
                            text = "Puntos de espera recomendados en caso de inmovilización:",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        companionState.safeStagingZones.take(2).forEach { zone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${zone.place.name} (${zone.distanceKm} km)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = zone.recommendationReason,
                                        color = MeetColors.textSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    } else {
                        companionState.safetyChecklist.take(3).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    if (activeRequest != null && activeRequest.status == "ACCEPTED") {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onCompleteRequest(activeRequest) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.backgroundDark, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("COMPLETAR TRABAJO & CALIFICAR", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * ── Campos de Inspección Técnica Multidominio (Pillar 4) ──
 */
@Composable
private fun MultiDomainInspectionFields(
    domainKey: String,
    dtcCode: String,
    onDtcCodeChange: (String) -> Unit,
    mechanicalSystem: String,
    onMechanicalSystemChange: (String) -> Unit,
    plumbingLocation: String,
    onPlumbingLocationChange: (String) -> Unit,
    plumbingPipeSize: String,
    onPlumbingPipeSizeChange: (String) -> Unit,
    plumbingSeverity: String,
    onPlumbingSeverityChange: (String) -> Unit,
    locksmithType: String,
    onLocksmithTypeChange: (String) -> Unit,
    locksmithIssue: String,
    onLocksmithIssueChange: (String) -> Unit,
    electricalVoltage: String,
    onElectricalVoltageChange: (String) -> Unit,
    electricalIssue: String,
    onElectricalIssueChange: (String) -> Unit,
    towCondition: String,
    onTowConditionChange: (String) -> Unit,
    towDestination: String,
    onTowDestinationChange: (String) -> Unit,
) {
    EliteCard(
        glowColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
        borderColor = MeetColors.cyberCyan.copy(alpha = 0.35f),
        backgroundColor = MeetColors.backgroundDark,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "PARÁMETROS TÉCNICOS ESPECÍFICOS",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            when (domainKey) {
                "AUTO_MECHANICAL", "BATTERY" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dtcCode,
                            onValueChange = { onDtcCodeChange(it.uppercase()) },
                            label = { Text("Código DTC (OBD)") },
                            placeholder = { Text("Ej: P0300, P0420") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                        OutlinedTextField(
                            value = mechanicalSystem,
                            onValueChange = onMechanicalSystemChange,
                            label = { Text("Subsistema") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                    }
                }
                "PLUMBING" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = plumbingLocation,
                            onValueChange = onPlumbingLocationChange,
                            label = { Text("Ubicación") },
                            placeholder = { Text("Cocina, Baño...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = plumbingPipeSize,
                            onValueChange = onPlumbingPipeSizeChange,
                            label = { Text("Diámetro Tubo") },
                            placeholder = { Text("1/2\", 3/4\"...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = plumbingSeverity,
                        onValueChange = onPlumbingSeverityChange,
                        label = { Text("Condición / Gravedad") },
                        placeholder = { Text("Goteo, rotura total, baja presión...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
                "LOCKSMITH" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = locksmithType,
                            onValueChange = onLocksmithTypeChange,
                            label = { Text("Tipo de Cerrojo") },
                            placeholder = { Text("Vehicular, Multipunto...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = locksmithIssue,
                            onValueChange = onLocksmithIssueChange,
                            label = { Text("Problema") },
                            placeholder = { Text("Llaves adentro, traba...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                "ELECTRICAL" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = electricalVoltage,
                            onValueChange = onElectricalVoltageChange,
                            label = { Text("Voltaje / Fase") },
                            placeholder = { Text("110V, 220V Bifásico...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = electricalIssue,
                            onValueChange = onElectricalIssueChange,
                            label = { Text("Falla Eléctrica") },
                            placeholder = { Text("Cortocircuito, breaker...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                "AUTO_TOW" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = towCondition,
                            onValueChange = onTowConditionChange,
                            label = { Text("Rodaje Vehículo") },
                            placeholder = { Text("Neutro libre, bloqueado...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = towDestination,
                            onValueChange = onTowDestinationChange,
                            label = { Text("Destino Estimado") },
                            placeholder = { Text("Taller, domicilio...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                "PARTS_STORE" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dtcCode,
                            onValueChange = { onDtcCodeChange(it.uppercase()) },
                            label = { Text("VIN / Código OEM") },
                            placeholder = { Text("Ej: 1HGCR2F8... / OEM") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = mechanicalSystem,
                            onValueChange = onMechanicalSystemChange,
                            label = { Text("Pieza / Repuesto") },
                            placeholder = { Text("Frenos, radiador...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                "COURIER", "MOVING" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = towCondition,
                            onValueChange = onTowConditionChange,
                            label = { Text("Tipo de Carga / Bulto") },
                            placeholder = { Text("Documento, caja, muebles...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = towDestination,
                            onValueChange = onTowDestinationChange,
                            label = { Text("Destino de Entrega") },
                            placeholder = { Text("Cantón, distrito o Waze...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                "EDUCATION" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mechanicalSystem,
                            onValueChange = onMechanicalSystemChange,
                            label = { Text("Materia / Tema") },
                            placeholder = { Text("Matemáticas, Física...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = towCondition,
                            onValueChange = onTowConditionChange,
                            label = { Text("Grado / Nivel") },
                            placeholder = { Text("7° Colegio, Bachillerato...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Especificación guiada por IA Elysium. Los especialistas responderán con su equipo profesional certificado.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * ── Modelo de Datos de Certificado de Servicio Forense (Pillar 1) ──
 */
data class ServiceCompletionCertificateData(
    val reportId: String,
    val integrityHash: String,
    val vehicleId: String,
    val generatedAt: String,
    val reportType: String,
    val verifierUrl: String,
    val escrowStatus: String,
    val amountCrc: Double,
    val specialistName: String,
    val problemDescription: String,
    val ratingStars: Int,
    val qrMinimalPayload: String,
)

/**
 * ── Dialog: Certificado de Servicio Forense con Código QR & Escrow Liberado ──
 */
@Composable
fun ServiceCompletionCertificateDialog(
    certificate: ServiceCompletionCertificateData,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF071424)),
            border = BorderStroke(1.5.dp, MeetColors.neonGreen)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeetColors.neonGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.neonGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CERTIFICADO DE SERVICIO FORENSE",
                            color = MeetColors.neonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "CIERRE Y DESEMBOLSO DE ESCROW",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "El servicio ha concluido exitosamente bajo custodia criptográfica.",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                // Certificate details card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDark),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ID Reporte:", color = MeetColors.textSecondary, fontSize = 10.sp)
                            Text(certificate.reportId, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fecha / Hora:", color = MeetColors.textSecondary, fontSize = 10.sp)
                            Text(certificate.generatedAt, color = Color.White, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monto Liberado:", color = MeetColors.textSecondary, fontSize = 10.sp)
                            Text("₡${String.format("%,.0f", certificate.amountCrc)} CRC", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estado Custodia:", color = MeetColors.textSecondary, fontSize = 10.sp)
                            Text(certificate.escrowStatus, color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calificación:", color = MeetColors.textSecondary, fontSize = 10.sp)
                            Text("⭐ ${certificate.ratingStars} / 5", color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // SHA-256 Hash Display
                Text("INTEGRIDAD MATEMÁTICA SHA-256:", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x3300E5FF),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = certificate.integrityHash,
                        color = MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Rule 4 QR Minimal Payload representation
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF040A12)),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📱", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("PAYLOAD QR MINIMAL (REGLA 4 FORENSE)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = certificate.qrMinimalPayload,
                            color = MeetColors.textSecondary,
                            fontSize = 8.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Verificable en: ${certificate.verifierUrl}", color = MeetColors.cyberCyan, fontSize = 9.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CERRAR & GUARDAR EN HISTORIAL", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * ── Dialog: Refugios Seguros y Auxilio Rápido Aura Sentinel ──
 * Muestra estaciones de Fuerza Pública, Bomberos y Centros Médicos de Costa Rica.
 */
@Composable
private fun AuraSentinelEmergencyHavenDialog(
    userGps: ObdViewModel.GpsLocationInfo?,
    onDismiss: () -> Unit,
    onSelectHaven: (CostaRicaGisDatabase.GisPlace) -> Unit,
) {
    val havens = remember(userGps) {
        val lat = userGps?.latitude ?: 9.9333
        val lon = userGps?.longitude ?: -84.0833
        CostaRicaGisDatabase.nearest(
            latitude = lat,
            longitude = lon,
            category = CostaRicaGisDatabase.GisCategory.POLICE_FIRE_EMERGENCY,
            limit = 8
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF091422)),
            border = BorderStroke(1.5.dp, MeetColors.error)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MeetColors.error.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, MeetColors.error, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚨", fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "REFUGIOS SEGUROS AURA SENTINEL",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Delegaciones Fuerza Pública y Bomberos más cercanas",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(havens) { (haven, distKm) ->
                        val distanceKmFormatted = String.format("%.1f", distKm)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectHaven(haven) },
                            shape = RoundedCornerShape(10.dp),
                            color = MeetColors.backgroundDark,
                            border = BorderStroke(1.dp, MeetColors.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏛️", fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = haven.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${haven.canton}, ${haven.province} · $distanceKmFormatted km",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = haven.displayLabel,
                                        color = MeetColors.textSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Trazar ruta",
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SinpeTopUpDialog(
    currentBalanceCrc: Long,
    requiredFeeCrc: Long,
    onDismiss: () -> Unit,
    onConfirmTopUp: (amountCrc: Long, reference: String) -> Unit,
) {
    var amountText by remember { mutableStateOf(maxOf(5000L, requiredFeeCrc).toString()) }
    var referenceText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081422)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RECARGA DE SALDO SINPE MÓVIL",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Comisión Constitucional MEET (5%)",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(14.dp))

                // Estado actual del saldo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C1F33), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Saldo actual:", color = Color.LightGray, fontSize = 11.sp)
                        Text(
                            "₡${"%,d".format(currentBalanceCrc)} CRC",
                            color = if (currentBalanceCrc >= requiredFeeCrc) MeetColors.neonGreen else Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Comisión requerida:", color = Color.LightGray, fontSize = 11.sp)
                        Text(
                            "₡${"%,d".format(requiredFeeCrc)} CRC",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Fondo de Respaldo Transitorio & Préstamo Operativo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A1828), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF1B354D), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "🏢 Elysium Network Corporation — Fideicomiso Operativo",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "🛡️ Fondo de Respaldo Transitorio: Puedes pedir prestado en tramos de ₡3,000 CRC cada vez que tu saldo sea de ₡400 CRC o menor. La deuda se va sumando (puede acumular millones). Al activarse la Cuenta Bancaria Empresarial Oficial de Elysium, deberás liquidar lo adeudado para continuar trabajando o la cuenta entrará en pausa operativa.",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        lineHeight = 11.5.sp
                    )

                    if (currentBalanceCrc <= 400L) {
                        Button(
                            onClick = {
                                onConfirmTopUp(3000L, "PRESTAMO-3000-INMEDIATO")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "⚡ PEDIR PRESTADO (+₡3,000 CRC) · SALDO ≤ ₡400",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF0C1F33),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ℹ️ Saldo actual: ₡${"%,d".format(currentBalanceCrc)} CRC (> ₡400). Préstamo disponible cuando el saldo sea ≤ ₡400 CRC.",
                                color = MeetColors.cyberCyan,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter(Char::isDigit)
                        errorMessage = null
                    },
                    label = { Text("Monto a Recargar (₡ CRC)") },
                    prefix = { Text("₡ ", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = referenceText,
                    onValueChange = {
                        referenceText = it
                        errorMessage = null
                    },
                    label = { Text("Número de Comprobante / Referencia") },
                    placeholder = { Text("Ej: 20260925-123456", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0L
                            val ref = referenceText.trim()
                            if (amount < 1000L) {
                                errorMessage = "El monto mínimo de recarga es ₡1,000 CRC."
                                return@Button
                            }
                            if (ref.length < 4) {
                                errorMessage = "Ingresa el comprobante o referencia bancaria."
                                return@Button
                            }
                            onConfirmTopUp(amount, ref)
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "SOLICITAR VALIDACIÓN",
                            color = MeetColors.backgroundDark,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
