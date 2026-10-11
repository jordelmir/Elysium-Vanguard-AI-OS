package com.elysium369.meet.ui.screens.services

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.agent.ui.*
import com.elysium369.meet.core.services.UniversalServiceCatalog
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.identity.PrincipalProvisioningStore
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.wallet.SpecialistWalletStore
import com.elysium369.meet.ui.components.SpecialistSinpeTopupDialog
import com.elysium369.meet.ui.components.SpecialistWalletCard
import com.elysium369.meet.ui.theme.MeetColors
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable
private data class ServiceDefinitionWire(
    val id: String,
    val domain: String,
    @SerialName("display_name") val name: String,
    @SerialName("supported_modalities") val modalities: List<String>
)

@Serializable
private data class ServiceRequestWire(
    val id: String,
    @SerialName("client_id") val client: String,
    @SerialName("assigned_provider_id") val provider: String? = null,
    @SerialName("service_definition_id") val definition: String,
    val modality: String,
    val title: String,
    val description: String,
    val intake: JsonObject = buildJsonObject {},
    @SerialName("location_label") val location: String? = null,
    @SerialName("offered_price_minor") val price: Long,
    @SerialName("final_price_minor") val finalPrice: Long? = null,
    @SerialName("provider_payment_attested_at") val providerPaymentAttestedAt: String? = "",
    val currency: String,
    val state: String,
    val version: Long
)

@Serializable
private data class ServiceOfferWire(
    val id: String,
    @SerialName("request_id") val request: String,
    @SerialName("provider_id") val provider: String,
    @SerialName("price_minor") val price: Long,
    val currency: String,
    val state: String,
    @SerialName("eta_minutes") val etaMinutes: Int? = null,
    @SerialName("warranty_days") val warrantyDays: Int = 0,
    val scope: JsonObject = buildJsonObject {}
)

@Serializable
private data class ProviderSummary(
    @SerialName("provider_id") val providerId: String? = null,
    val name: String? = null,
    val completed: Long = 0,
    val reviews: Long = 0,
    val rating: Double? = null,
    @SerialName("balance_minor") val balance: Long? = null,
    val eligible: Boolean = false
)

/** Both historical entry points share this server-authoritative client/provider experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedServicesScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onOpenMessages: () -> Unit,
    onProviderConfig: () -> Unit,
    onAdvanced: () -> Unit,
    onServiceMessages: (String) -> Unit,
    requestDraft: ServicesRequestDraft? = null,
    onDraftConsumed: () -> Unit = {},
    offerDraft: ServicesOfferDraft? = null,
    onOfferDraftConsumed: () -> Unit = {},
    initialHistory: Boolean = false,
    onNavigateToAuth: () -> Unit = {},
    showTopBar: Boolean = true,
) {
    val context = LocalContext.current
    val principal by viewModel.activePrincipal.collectAsState()
    val currentUserId = viewModel.currentUserId
    val scope = rememberCoroutineScope()
    val client = SupabaseModule.client

    fun isValidUuid(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        return runCatching { UUID.fromString(str) }.isSuccess
    }

    /** Robustly resolves the real user account across all authentication tiers. */
    fun resolveEffectiveUserId(): String? {
        val gotrueUser = client.auth.currentUserOrNull()?.id
            ?: SupabaseManager.client.auth.currentUserOrNull()?.id
        if (isValidUuid(gotrueUser)) return gotrueUser

        val pId = principal?.id
        if (principal?.isAuthenticated == true && isValidUuid(pId)) return pId

        if (isValidUuid(currentUserId)) return currentUserId

        val storedId = PrincipalProvisioningStore.principalId(context)
        if (isValidUuid(storedId)) return storedId

        if (isValidUuid(pId)) return pId
        return null
    }

    val effectiveOwner = resolveEffectiveUserId()
    val currentUserEmail = client.auth.currentUserOrNull()?.email
        ?: SupabaseManager.client.auth.currentUserOrNull()?.email

    val walletState by SpecialistWalletStore.getWalletFlow(context, effectiveOwner ?: "GUEST", "SERVICES").collectAsState()
    LaunchedEffect(effectiveOwner) {
        if (effectiveOwner != null) {
            SpecialistWalletStore.syncWithTrustCenter(context, effectiveOwner, "SERVICES")
        }
    }
    var showWalletTopupDialog by remember { mutableStateOf(false) }

    var providerMode by remember(effectiveOwner) { mutableStateOf(false) }
    var pages by remember(effectiveOwner) { mutableStateOf(1) }
    var history by remember(effectiveOwner, initialHistory) { mutableStateOf(initialHistory) }
    var definitions by remember(effectiveOwner) { mutableStateOf(emptyList<ServiceDefinitionWire>()) }
    var requests by remember(effectiveOwner) { mutableStateOf(emptyList<ServiceRequestWire>()) }
    var offers by remember(effectiveOwner) { mutableStateOf(emptyList<ServiceOfferWire>()) }
    var query by remember(effectiveOwner) { mutableStateOf("") }
    var providerSummaries by remember(effectiveOwner) { mutableStateOf(emptyMap<String, ProviderSummary>()) }
    var summary by remember(effectiveOwner) { mutableStateOf(ProviderSummary(providerId = effectiveOwner)) }
    var error by remember(effectiveOwner) { mutableStateOf<String?>(null) }
    var busy by remember(effectiveOwner) { mutableStateOf(false) }
    var selectedDefinition by remember(effectiveOwner) { mutableStateOf<ServiceDefinitionWire?>(null) }
    var bidTarget by remember(effectiveOwner) { mutableStateOf<ServiceRequestWire?>(null) }
    var title by remember(effectiveOwner) { mutableStateOf("") }
    var description by remember(effectiveOwner) { mutableStateOf("") }
    var location by remember(effectiveOwner) { mutableStateOf("") }
    var price by remember(effectiveOwner) { mutableStateOf("") }
    var modality by remember(effectiveOwner) { mutableStateOf("") }
    var offerHours by remember(effectiveOwner) { mutableStateOf("1") }
    var warrantyDays by remember(effectiveOwner) { mutableStateOf("0") }
    var offerNote by remember(effectiveOwner) { mutableStateOf("") }
    var requestIntake by remember(effectiveOwner) { mutableStateOf<JsonObject>(buildJsonObject {}) }
    var courierVehicleKind by remember(effectiveOwner) { mutableStateOf("MOTORCYCLE") }
    var packageWeight by remember(effectiveOwner) { mutableStateOf("") }
    var packageLength by remember(effectiveOwner) { mutableStateOf("") }
    var packageWidth by remember(effectiveOwner) { mutableStateOf("") }
    var packageHeight by remember(effectiveOwner) { mutableStateOf("") }
    var transferAmount by remember(effectiveOwner) { mutableStateOf("") }
    var transferId by remember(effectiveOwner) { mutableStateOf(UUID.randomUUID().toString()) }
    var draftId by remember(effectiveOwner) { mutableStateOf(UUID.randomUUID().toString()) }

    suspend fun ensureSessionValid(): String? {
        var user = client.auth.currentUserOrNull()
        if (user == null) {
            runCatching { client.auth.loadFromStorage() }
            user = client.auth.currentUserOrNull()
        }
        if (user == null) {
            runCatching { client.auth.refreshCurrentSession() }
            user = client.auth.currentUserOrNull()
        }
        return user?.id ?: resolveEffectiveUserId()
    }

    suspend fun refresh() {
        // 1. Comprehensive service catalog with local fallback
        val serverCatalog = runCatching {
            client.postgrest["service_definitions"].select {
                filter { eq("active", true) }
            }.decodeList<ServiceDefinitionWire>()
        }.getOrNull()

        val fallbackCatalog = UniversalServiceCatalog.definitions.map { def ->
            ServiceDefinitionWire(
                id = def.id,
                domain = def.domain,
                name = def.name,
                modalities = def.modalities.map { it.name }
            )
        }

        val mergedCatalog = if (!serverCatalog.isNullOrEmpty()) {
            val serverIds = serverCatalog.map { it.id }.toSet()
            serverCatalog + fallbackCatalog.filter { it.id !in serverIds }
        } else {
            fallbackCatalog
        }
        definitions = mergedCatalog

        // 2. Resolve owner and session
        ensureSessionValid()
        val owner = resolveEffectiveUserId()

        // 3. Map local Room requests for immediate availability
        val localRoomRequests = viewModel.serviceRequests.value.map { req ->
            val parsedDefinition = req.description.lineSequence()
                .firstOrNull { it.startsWith("definition_id=") }
                ?.substringAfter("definition_id=")
                ?.trim()
                ?: "custom"
            ServiceRequestWire(
                id = req.requestId,
                client = owner ?: "local_client",
                provider = req.assignedMechanicId,
                definition = parsedDefinition,
                modality = "PHYSICAL",
                title = req.problem,
                description = req.description,
                location = req.location,
                price = (req.priceOffer * 100).toLong(),
                currency = "CRC",
                state = req.status,
                version = 1L
            )
        }

        if (owner == null) {
            // Guest mode: show local requests without crashing or false error
            requests = localRoomRequests
            offers = emptyList()
            summary = ProviderSummary()
            error = null
            return
        }

        // 4. Authenticated mode: Load cloud requests
        val mine = mutableListOf<ServiceRequestWire>()
        val assigned = mutableListOf<ServiceRequestWire>()
        val market = mutableListOf<ServiceRequestWire>()

        for (page in 0 until pages) {
            runCatching {
                mine += client.postgrest["universal_service_requests"].select {
                    filter { eq("client_id", owner) }
                    order("updated_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range((page * 100).toLong(), (page * 100 + 99).toLong())
                }.decodeList<ServiceRequestWire>()
            }
            runCatching {
                assigned += client.postgrest["universal_service_requests"].select {
                    filter { eq("assigned_provider_id", owner) }
                    order("updated_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range((page * 100).toLong(), (page * 100 + 99).toLong())
                }.decodeList<ServiceRequestWire>()
            }
            runCatching {
                market += client.postgrest["universal_service_requests"].select {
                    filter { eq("state", "OPEN"); neq("client_id", owner) }
                    order("created_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range((page * 100).toLong(), (page * 100 + 99).toLong())
                }.decodeList<ServiceRequestWire>()
            }
        }

        val rows = (mine + assigned + market + localRoomRequests).distinctBy { it.id }

        val proposals = mutableListOf<ServiceOfferWire>()
        for (row in mine.filter { it.state == "OPEN" }) {
            runCatching {
                client.postgrest["universal_service_offers"].select {
                    filter { eq("request_id", row.id) }
                }.decodeList<ServiceOfferWire>()
            }.getOrNull()?.let { proposals += it }
        }

        val stats = runCatching {
            client.postgrest.rpc("universal_service_provider_summary_v1", buildJsonObject {
                put("p_provider_id", owner)
            }).decodeSingle<ProviderSummary>()
        }.getOrDefault(ProviderSummary(providerId = owner))

        val summaries = mutableListOf<ProviderSummary>()
        for (ids in proposals.map { it.provider }.distinct().chunked(100)) {
            runCatching {
                client.postgrest.rpc("universal_service_provider_summaries_v1", buildJsonObject {
                    put("p_provider_ids", JsonArray(ids.map(::JsonPrimitive)))
                }).decodeList<ProviderSummary>()
            }.getOrNull()?.let { summaries += it }
        }

        providerSummaries = summaries.mapNotNull { entry -> entry.providerId?.let { it to entry } }.toMap()
        requests = rows
        offers = proposals
        summary = stats
        error = null
    }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                ensureSessionValid()
                block()
                refresh()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                val msg = e.localizedMessage.orEmpty()
                error = when {
                    msg.contains("timeout", ignoreCase = true) || msg.contains("ConnectException", ignoreCase = true) || msg.contains("UnknownHost", ignoreCase = true) ->
                        "Conexión con el servidor inestable. Tu operación se conservó localmente."
                    msg.contains("JWT", ignoreCase = true) || msg.contains("401", ignoreCase = true) ->
                        "Tu sesión expiró. Inicia sesión de nuevo para confirmar en línea."
                    else -> "Aviso del servidor: ${e.localizedMessage ?: "No se pudo sincronizar en línea"}"
                }
            } finally {
                busy = false
            }
        }
    }

    fun transition(request: ServiceRequestWire, command: String, offer: String? = null) = action {
        client.postgrest.rpc("universal_service_transition_v1", buildJsonObject {
            put("p_request_id", request.id)
            put("p_action", command)
            if (offer != null) put("p_offer_id", offer)
        })
    }

    LaunchedEffect(offerDraft) {
        offerDraft?.let {
            providerMode = true
            price = it.priceCrc.toString()
            offerHours = it.hours.toString()
            warrantyDays = it.warrantyDays.toString()
            offerNote = it.note
        }
    }

    LaunchedEffect(requestDraft, definitions) {
        val draft = requestDraft ?: return@LaunchedEffect
        if (definitions.isEmpty()) return@LaunchedEffect
        val definition = definitions.firstOrNull { it.id == draft.definitionId }
            ?: definitions.firstOrNull { it.domain.equals(draft.definitionId, ignoreCase = true) }
            ?: definitions.firstOrNull { it.name.contains(draft.title, ignoreCase = true) }
            ?: definitions.firstOrNull()
        if (definition == null) {
            error = "Este servicio todavía no está habilitado en el catálogo. Tu formulario no se ha publicado."
            return@LaunchedEffect
        }
        providerMode = false
        selectedDefinition = definition
        title = draft.title
        description = draft.description
        location = draft.location
        price = draft.priceCrc.toString()
        modality = draft.modality.takeIf { it in definition.modalities } ?: definition.modalities.firstOrNull().orEmpty()
        requestIntake = buildJsonObject {
            put("detailed_specification", draft.description)
            draft.latitude?.let { put("latitude", it) }
            draft.longitude?.let { put("longitude", it) }
        }
        draftId = UUID.randomUUID().toString()
        onDraftConsumed()
    }

    LaunchedEffect(principal, currentUserId, pages) {
        while (true) {
            try {
                refresh()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                val owner = resolveEffectiveUserId()
                if (owner != null) {
                    error = "Sin conexión en vivo con el servidor. Modo local activo."
                } else {
                    error = null
                }
            }
            delay(12000)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Servicios Elysium") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } },
                    actions = { TextButton(onClick = onOpenMessages) { Text("Mensajes") } }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // ── Real Account Status Card ──
                if (effectiveOwner != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("👤", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = currentUserEmail ?: "Cuenta MEET Conectada",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ID: ${effectiveOwner.take(8)}... · Red Nacional Verificada",
                                        color = MeetColors.neonGreen,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Surface(
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, MeetColors.neonGreen)
                            ) {
                                Text(
                                    text = "EN LÍNEA",
                                    color = MeetColors.neonGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚡ Modo Local Activo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Inicia sesión para sincronizar y recibir ofertas en tiempo real de toda Costa Rica.", color = MeetColors.textSecondary, fontSize = 10.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onNavigateToAuth,
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Iniciar sesión", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = !providerMode,
                        onClick = { providerMode = false },
                        label = { Text("Necesito un servicio") }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = providerMode,
                        onClick = { providerMode = true },
                        label = { Text("Ofrezco servicios") }
                    )
                }

                if (providerMode) {
                    SpecialistWalletCard(
                        walletState = walletState,
                        roleTitle = "MULTISERVICIOS Y OFICIOS",
                        accentColor = MeetColors.neonGreen,
                        onRechargeClick = { showWalletTopupDialog = true },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Text(if (summary.eligible) "Proveedor verificado" else "Registra y verifica tu perfil para ofrecer servicios.")
                    Text("${summary.completed} trabajos completados · ${summary.reviews} calificaciones")
                    Text(summary.rating?.let { "Calificación: %.1f / 5".format(it) } ?: "Aún sin calificaciones")
                    OutlinedButton(onClick = onProviderConfig) { Text("Mi perfil y configuración") }
                } else {
                    Text("Compara propuestas. El proveedor confirma el trabajo y el pago recibido; tú confirmas después el cierre.")
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                Row {
                    TextButton(onClick = { history = false }) { Text("Activos") }
                    TextButton(onClick = { history = true }) { Text("Historial") }
                    TextButton(onClick = { action { } }, enabled = !busy) { Text("Actualizar") }
                }
            }

            if (!providerMode && !history) {
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .agentTextInput(AgentUiControlId("services.search"), "Buscar servicio u oficio", AgentTextFieldRole.SEARCH, readValue = { query }, writeValue = { query = it }),
                        label = { Text("Buscar servicio u oficio") }
                    )
                }
            }

            if (!providerMode && !history) {
                items(
                    definitions.filter { query.isBlank() || (it.name + " " + it.domain).contains(query.trim(), ignoreCase = true) },
                    key = { "definition:${it.id}" }
                ) { definition ->
                    OutlinedButton(
                        onClick = {
                            selectedDefinition = definition
                            modality = definition.modalities.firstOrNull().orEmpty()
                            draftId = UUID.randomUUID().toString()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        Text("${definition.domain} · ${definition.name}")
                    }
                }
            }

            val visible = requests.filter { r ->
                val isMyClient = effectiveOwner != null && r.client == effectiveOwner
                val isMyProvider = effectiveOwner != null && r.provider == effectiveOwner
                (if (providerMode) isMyProvider || (r.state == "OPEN" && !isMyClient) else isMyClient || effectiveOwner == null) &&
                    (if (history) r.state in setOf("COMPLETED", "CANCELLED", "DISPUTED") else r.state !in setOf("COMPLETED", "CANCELLED", "DISPUTED"))
            }

            if (visible.isEmpty()) {
                item { Text(if (history) "Sin servicios en el historial." else "Sin solicitudes en esta vista.") }
            }

            items(visible, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium)
                        Text(r.description)
                        r.location?.let { Text(it) }
                        Text("${r.finalPrice ?: r.price} ${r.currency} · ${r.state}")

                        if (r.provider != null && (effectiveOwner == r.client || effectiveOwner == r.provider)) {
                            TextButton(onClick = { onServiceMessages(r.id) }) { Text("Mensajes de este servicio") }
                        }

                        if (providerMode && r.state == "OPEN" && summary.eligible) {
                            Button(
                                onClick = {
                                    bidTarget = r
                                    if (offerDraft == null) {
                                        price = ""
                                        offerHours = "1"
                                        warrantyDays = "0"
                                        offerNote = ""
                                    }
                                },
                                enabled = !busy
                            ) {
                                Text("Enviar propuesta")
                            }
                        }

                        if (effectiveOwner == r.client && r.state == "OPEN") {
                            offers.filter { it.request == r.id && it.state == "PENDING" }.forEach { offer ->
                                val publicProfile = providerSummaries[offer.provider]
                                Text(publicProfile?.name ?: "Proveedor sin perfil público disponible")
                                Text(publicProfile?.let { "${it.completed} trabajos · ${it.reviews} calificaciones" } ?: "Métricas no disponibles")
                                Text(publicProfile?.rating?.let { "★ %.1f / 5".format(it) } ?: "Aún sin calificaciones")
                                Text("Propuesta: ${offer.price} ${offer.currency} · ${offer.etaMinutes?.let { "$it min" } ?: "Duración no capturada"} · Garantía ${offer.warrantyDays} días")
                                offer.scope["note"]?.jsonPrimitive?.contentOrNull?.let { Text(it) }
                                Button(
                                    modifier = Modifier.serviceAction("accept.${offer.id}", "Aceptar propuesta", !busy, AgentUiSensitivity.FINANCIAL) { transition(r, "ACCEPT", offer.id) },
                                    onClick = { transition(r, "ACCEPT", offer.id) },
                                    enabled = !busy
                                ) {
                                    Text("Aceptar propuesta")
                                }
                            }
                            TextButton(
                                modifier = Modifier.serviceAction("cancel.${r.id}", "Cancelar solicitud", !busy) { transition(r, "CANCEL") },
                                onClick = { transition(r, "CANCEL") },
                                enabled = !busy
                            ) {
                                Text("Cancelar solicitud")
                            }
                        }

                        if (r.provider == effectiveOwner && r.state == "ASSIGNED") {
                            Button(
                                modifier = Modifier.serviceAction("start.${r.id}", "Iniciar trabajo", !busy) { transition(r, "START") },
                                onClick = { transition(r, "START") },
                                enabled = !busy
                            ) {
                                Text("Iniciar trabajo")
                            }
                        }

                        if (r.provider == effectiveOwner && r.state == "IN_PROGRESS") {
                            if (r.providerPaymentAttestedAt == null) {
                                Button(
                                    modifier = Modifier.serviceAction("finish.${r.id}", "Confirmar trabajo realizado y pago recibido", !busy, AgentUiSensitivity.FINANCIAL) { transition(r, "FINISH") },
                                    onClick = { transition(r, "FINISH") },
                                    enabled = !busy
                                ) {
                                    Text("Trabajo realizado y pago recibido")
                                }
                            } else if (r.providerPaymentAttestedAt.isNotBlank()) {
                                Text("Esperando confirmación de la persona cliente. La comisión sigue reservada.")
                            }
                        }

                        if (r.client == effectiveOwner && r.state == "IN_PROGRESS") {
                            if (r.providerPaymentAttestedAt == null) {
                                Text("Esperando a que el proveedor confirme trabajo y pago recibido.")
                            } else {
                                Button(
                                    modifier = Modifier.serviceAction("complete.${r.id}", "Confirmar servicio y pago", !busy, AgentUiSensitivity.FINANCIAL) { transition(r, "COMPLETE") },
                                    onClick = { transition(r, "COMPLETE") },
                                    enabled = !busy
                                ) {
                                    Text("Confirmar servicio y pago")
                                }
                            }
                        }

                        if (r.client == effectiveOwner && r.state == "COMPLETED") {
                            Text("Califica tu servicio (una vez)")
                            Row {
                                (1..5).forEach { stars ->
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            action {
                                                client.postgrest.rpc("universal_service_rate_v1", buildJsonObject {
                                                    put("p_request_id", r.id)
                                                    put("p_stars", stars)
                                                })
                                            }
                                        }
                                    ) {
                                        Text("$stars ★")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { TextButton(onClick = { pages += 1 }, enabled = !busy) { Text("Cargar más servicios") } }
            item {
                OutlinedButton(onClick = onAdvanced) { Text("Herramientas avanzadas de cotización") }
                Text("Las herramientas anteriores conservan su historial local. Una operación local no confirma un servicio en línea.")
            }
        }
    }

    selectedDefinition?.let { d ->
        AlertDialog(
            onDismissRequest = { if (!busy) selectedDefinition = null },
            title = { Text(d.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        modifier = Modifier.agentTextInput(AgentUiControlId("services.title"), "Título", readValue = { title }, writeValue = { title = it }, route = "elysium_services"),
                        label = { Text("Título") }
                    )
                    OutlinedTextField(
                        description,
                        { description = it },
                        modifier = Modifier.agentTextInput(AgentUiControlId("services.description"), "Describe lo que necesitas", readValue = { description }, writeValue = { description = it }, route = "elysium_services"),
                        label = { Text("Describe lo que necesitas") }
                    )
                    OutlinedTextField(
                        location,
                        { location = it },
                        modifier = Modifier.agentTextInput(AgentUiControlId("services.location"), "Lugar o instrucciones para servicio remoto", readValue = { location }, writeValue = { location = it }, route = "elysium_services"),
                        label = { Text("Lugar o instrucciones para servicio remoto") }
                    )
                    OutlinedTextField(
                        price,
                        { price = it },
                        modifier = Modifier.agentTextInput(AgentUiControlId("services.price"), "Presupuesto", readValue = { price }, writeValue = { price = it }, route = "elysium_services"),
                        label = { Text("Presupuesto en CRC (colones enteros)") }
                    )
                    if (d.id == "courier") {
                        Text("Objeto pequeño: indica peso y medidas reales. No transporta personas.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MOTORCYCLE" to "🛵 Moto", "CAR" to "🚗 Carro").forEach { (kind, label) ->
                                FilterChip(selected = courierVehicleKind == kind, onClick = { courierVehicleKind = kind }, label = { Text(label) })
                            }
                        }
                        OutlinedTextField(packageWeight, { packageWeight = it }, label = { Text("Peso en kg") })
                        OutlinedTextField(packageLength, { packageLength = it }, label = { Text("Largo en cm") })
                        OutlinedTextField(packageWidth, { packageWidth = it }, label = { Text("Ancho en cm") })
                        OutlinedTextField(packageHeight, { packageHeight = it }, label = { Text("Alto en cm") })
                        Text(if (courierVehicleKind == "MOTORCYCLE") "Moto: hasta 10 kg · 45 × 35 × 35 cm" else "Carro: hasta 20 kg · 80 × 60 × 60 cm")
                    }
                    d.modalities.forEach { option ->
                        FilterChip(selected = modality == option, onClick = { modality = option }, label = { Text(option) })
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selectedDefinition = null }, enabled = !busy) { Text("Volver") } },
            confirmButton = {
                Button(
                    enabled = !busy && title.trim().length in 3..160 && description.trim().length in 10..5000 && (price.toLongOrNull() ?: 0) > 0 && location.isNotBlank() && (d.id != "courier" || CourierPackagePolicy.valid(courierVehicleKind, packageWeight.toDoubleOrNull(), packageLength.toDoubleOrNull(), packageWidth.toDoubleOrNull(), packageHeight.toDoubleOrNull())),
                    onClick = {
                        action {
                            val intake = if (d.id == "courier") CourierPackagePolicy.intake(courierVehicleKind, requireNotNull(packageWeight.toDoubleOrNull()), requireNotNull(packageLength.toDoubleOrNull()), requireNotNull(packageWidth.toDoubleOrNull()), requireNotNull(packageHeight.toDoubleOrNull()), description.trim()) else requestIntake
                            val activeActor = ensureSessionValid() ?: "local_client"

                            // 1. Durably save into Room first (Local-first guarantee)
                            viewModel.createServiceRequest(
                                vehicleId = "",
                                problem = title.trim(),
                                description = description.trim(),
                                location = location.trim(),
                                priority = "NORMAL",
                                priceOffer = price.toDoubleOrNull() ?: 0.0,
                                serviceId = d.id,
                                serviceCategory = d.domain,
                                serviceMetadata = intake.toString(),
                                requestId = draftId
                            )

                            // 2. Publish to Supabase if valid UUID
                            if (isValidUuid(activeActor)) {
                                try {
                                    client.postgrest["universal_service_requests"].insert(buildJsonObject {
                                        put("id", draftId)
                                        put("client_id", activeActor)
                                        put("service_definition_id", d.id)
                                        put("modality", modality)
                                        put("title", title.trim())
                                        put("description", description.trim())
                                        put("intake", intake)
                                        put("location_label", location.trim())
                                        put("offered_price_minor", requireNotNull(price.toLongOrNull()))
                                        put("currency", "CRC")
                                        put("state", "OPEN")
                                        put("payment_state", "NOT_STARTED")
                                        put("version", 1)
                                    })
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (e: Exception) {
                                    val existing = runCatching {
                                        client.postgrest["universal_service_requests"].select {
                                            filter { eq("id", draftId); eq("client_id", activeActor) }
                                        }.decodeList<ServiceRequestWire>().singleOrNull()
                                    }.getOrNull()
                                    if (existing == null) {
                                        error = "Solicitud guardada en tu dispositivo. Se sincronizará con la red en línea al detectar señal."
                                    }
                                }
                            }
                            selectedDefinition = null
                            title = ""
                            description = ""
                            location = ""
                            price = ""
                            draftId = UUID.randomUUID().toString()
                        }
                    }
                ) {
                    Text("Publicar solicitud")
                }
            }
        )
    }

    bidTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { if (!busy) bidTarget = null },
            title = { Text("Propuesta para ${r.title}") },
            text = {
                Column {
                    OutlinedTextField(
                        price,
                        { price = it },
                        modifier = Modifier.agentTextInput(AgentUiControlId("services.price"), "Presupuesto", readValue = { price }, writeValue = { price = it }, route = "elysium_services"),
                        label = { Text("Precio en ${r.currency}") }
                    )
                    OutlinedTextField(offerHours, { offerHours = it }, label = { Text("Duración estimada en horas") })
                    OutlinedTextField(warrantyDays, { warrantyDays = it }, label = { Text("Garantía en días") })
                    OutlinedTextField(offerNote, { offerNote = it }, label = { Text("Alcance, materiales y nota") })
                }
            },
            dismissButton = { TextButton(onClick = { bidTarget = null }, enabled = !busy) { Text("Volver") } },
            confirmButton = {
                Button(
                    enabled = !busy && (price.toLongOrNull() ?: 0) > 0 && (offerHours.toDoubleOrNull()?.takeIf { it.isFinite() } ?: -1.0) in 0.0..720.0 && (warrantyDays.toIntOrNull() ?: -1) in 0..3650,
                    onClick = {
                        action {
                            val activeActor = ensureSessionValid() ?: resolveEffectiveUserId() ?: "local_provider"
                            try {
                                client.postgrest["universal_service_offers"].insert(
                                    servicesOfferPayload(
                                        r.id,
                                        activeActor,
                                        requireNotNull(price.toLongOrNull()),
                                        r.currency,
                                        requireNotNull(offerHours.toDoubleOrNull()),
                                        requireNotNull(warrantyDays.toIntOrNull()),
                                        offerNote
                                    )
                                )
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                val existing = runCatching {
                                    client.postgrest["universal_service_offers"].select {
                                        filter { eq("request_id", r.id); eq("provider_id", activeActor) }
                                    }.decodeList<ServiceOfferWire>().singleOrNull()
                                }.getOrNull()
                                if (existing == null || existing.price != price.toLongOrNull() || existing.currency != r.currency || existing.etaMinutes != (offerHours.toDoubleOrNull()?.times(60)?.toInt()) || existing.warrantyDays != warrantyDays.toIntOrNull() || existing.scope["note"]?.jsonPrimitive?.contentOrNull != offerNote.trim()) {
                                    throw e
                                }
                            }
                            bidTarget = null
                            onOfferDraftConsumed()
                        }
                    }
                ) {
                    Text("Enviar propuesta")
                }
            }
        )
    }

    if (showWalletTopupDialog) {
        SpecialistSinpeTopupDialog(
            serviceTitle = "Servicios y Oficios Elysium",
            specialistId = effectiveOwner ?: "GUEST",
            serviceVertical = "SERVICES",
            onDismiss = { showWalletTopupDialog = false },
        )
    }
}

private fun Modifier.serviceAction(
    id: String,
    label: String,
    enabled: Boolean = true,
    sensitivity: AgentUiSensitivity = AgentUiSensitivity.NORMAL,
    action: () -> Unit
): Modifier = agentAction(AgentUiControlId("services.$id"), label, route = "elysium_services", enabled = enabled, sensitivity = sensitivity, onActivate = action)

/** Payload validation is shared with the offer form; authority and charging remain on the server. */
internal fun servicesOfferPayload(
    request: String,
    provider: String,
    price: Long,
    currency: String,
    hours: Double,
    warranty: Int,
    note: String
): JsonObject {
    require(request.isNotBlank() && provider.isNotBlank())
    require(price > 0 && hours.isFinite() && hours in 0.0..720.0 && warranty in 0..3650)
    require(currency.matches(Regex("[A-Z]{3}")) && note.length <= 5000)
    return buildJsonObject {
        put("request_id", request)
        put("provider_id", provider)
        put("price_minor", price)
        put("currency", currency)
        put("eta_minutes", (hours * 60).toInt())
        put("warranty_days", warranty)
        put("scope", buildJsonObject { put("note", note.trim()) })
    }
}
