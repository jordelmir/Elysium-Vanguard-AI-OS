package com.elysium369.meet.ui.screens.chat

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import androidx.compose.ui.text.style.TextAlign
import com.elysium369.meet.core.mesh.ElysiumMeshGovernance
import com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine
import com.elysium369.meet.core.mesh.PhysicalMeshPeer
import com.elysium369.meet.core.share.QrCodeImage
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetChatListScreen(
    navController: NavController,
    viewModel: FleetChatViewModel,
    businessId: String,
    initialTab: Int = 2,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Active selected business ID
    LaunchedEffect(businessId) {
        viewModel.syncPendingFleetOperatorApplications()
        if (businessId.isNotEmpty()) {
            viewModel.selectBusiness(businessId)
        }
    }

    val selectedBizId by viewModel.selectedBusinessId.collectAsState()
    val businessProfiles by viewModel.businessProfiles.collectAsState(initial = emptyList())
    val fleets by viewModel.fleetsForActiveBusiness.collectAsState(initial = emptyList())
    val members by viewModel.membersForActiveBusiness.collectAsState(initial = emptyList())
    val vehicles by viewModel.vehiclesForActiveBusiness.collectAsState(initial = emptyList())
    val recentChats by viewModel.recentChats.collectAsState(initial = emptyList())

    // Tabs state (por defecto pestaña 2: Radar Mesh & Chat)
    var selectedTab by remember { mutableStateOf(initialTab) }

    // Dialog & Form states
    var showCreateBusinessDialog by remember { mutableStateOf(false) }
    var showCreateFleetDialog by remember { mutableStateOf(false) }
    var showAssignVehicleDialog by remember { mutableStateOf<VehicleEntity?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = if (selectedTab == 2) "Radar Mesh & Chat Flota" else "Ecosistema de Flotas",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeetColors.backgroundDark,
                contentColor = MeetColors.electricBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MeetColors.electricBlue
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Panel", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Vehículos", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("⚡ Radar Mesh & Chat", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> PanelTabContent(
                    fleets = fleets,
                    businessProfiles = businessProfiles,
                    selectedBizId = selectedBizId,
                    onSelectBusiness = { viewModel.selectBusiness(it) },
                    onCreateBusiness = { showCreateBusinessDialog = true },
                    onCreateFleet = { showCreateFleetDialog = true },
                    onJoinFleet = { code ->
                        viewModel.joinFleetByCode(code) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Código copiado: $code", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> VehiclesTabContent(
                    vehicles = vehicles,
                    members = members,
                    fleets = fleets,
                    onAssignClick = { showAssignVehicleDialog = it },
                    onDiagnoseClick = { code ->
                        navController.navigate("ai/$code")
                    }
                )
                2 -> MessagingTabContent(
                    recentChats = recentChats,
                    vehicles = vehicles,
                    fleets = fleets,
                    viewModel = viewModel,
                    navController = navController,
                    onSubmitDvir = { vId, b, l, t, f, bat ->
                        viewModel.submitDvirReport(vId, b, l, t, f, bat) {
                            Toast.makeText(context, "Reporte DVIR enviado con éxito al panel", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // CREATE BUSINESS DIALOG
    if (showCreateBusinessDialog) {
        var bizName by remember { mutableStateOf("") }
        var taxId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBusinessDialog = false },
            title = { Text("Registrar Empresa de Flota", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configura un perfil administrativo de flotillas:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Nombre de la Empresa") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = taxId,
                        onValueChange = { taxId = it },
                        label = { Text("Identificación Fiscal / RFC (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Registrar",
                    onClick = {
                        if (bizName.isNotBlank()) {
                            viewModel.createBusinessProfile(
                                bizName,
                                taxId.takeIf { it.isNotBlank() },
                            ) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (success) showCreateBusinessDialog = false
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateBusinessDialog = false }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }

    // CREATE FLEET DIALOG
    if (showCreateFleetDialog) {
        var fleetName by remember { mutableStateOf("") }
        var fleetDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFleetDialog = false },
            title = { Text("Crear Nueva Flota", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Las flotas dividen a tu empresa en sub-rutas o grupos específicos de trabajo:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = fleetName,
                        onValueChange = { fleetName = it },
                        label = { Text("Nombre de la Flota") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = fleetDesc,
                        onValueChange = { fleetDesc = it },
                        label = { Text("Descripción del Trabajo / Ruta") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Crear",
                    onClick = {
                        if (fleetName.isNotBlank()) {
                            viewModel.createFleet(fleetName, fleetDesc.takeIf { it.isNotBlank() })
                            showCreateFleetDialog = false
                            Toast.makeText(context, "Flota creada exitosamente", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateFleetDialog = false }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }

    // ASSIGN VEHICLE DIALOG
    if (showAssignVehicleDialog != null) {
        val vehicle = showAssignVehicleDialog!!
        var selectedDriverId by remember { mutableStateOf<String?>(vehicle.assignedDriverId) }
        var selectedFleetId by remember { mutableStateOf<String?>(vehicle.fleetId) }
        
        AlertDialog(
            onDismissRequest = { showAssignVehicleDialog = null },
            title = { Text("Asignar ${vehicle.make} ${vehicle.model}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Asigna un conductor responsable y agrégalo a una flota operativa:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    
                    // Driver Selector
                    Text("Conductor Responsable:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { driver ->
                            val isSelected = selectedDriverId == driver.userId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MeetColors.electricBlue else MeetColors.backgroundDeep)
                                    .clickable { selectedDriverId = if (isSelected) null else driver.userId }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    driver.userId,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Fleet Selector
                    Text("Flota Operativa:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fleets.forEach { fleet ->
                            val isSelected = selectedFleetId == fleet.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else MeetColors.backgroundDeep)
                                    .clickable { selectedFleetId = if (isSelected) null else fleet.id }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedFleetId = if (isSelected) null else fleet.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = MeetColors.electricBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(fleet.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(fleet.description ?: "", color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Guardar Asignación",
                    onClick = {
                        viewModel.assignVehicleToDriverAndFleet(vehicle.id, selectedDriverId, selectedFleetId)
                        showAssignVehicleDialog = null
                        Toast.makeText(context, "Asignación actualizada con éxito", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAssignVehicleDialog = null }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }
}

// ══════════════════════════════════════════════════════
// TAB CONTENT: PANEL / CONTROL
// ══════════════════════════════════════════════════════
@Composable
fun PanelTabContent(
    fleets: List<FleetEntity>,
    businessProfiles: List<BusinessProfileEntity>,
    selectedBizId: String?,
    onSelectBusiness: (String) -> Unit,
    onCreateBusiness: () -> Unit,
    onCreateFleet: () -> Unit,
    onJoinFleet: (String) -> Unit,
    onCopyCode: (String) -> Unit
) {
    var inviteCodeInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Business Profile Selection
        item {
            EliteCard(
                glowColor = MeetColors.electricBlue.copy(alpha = 0.1f),
                backgroundColor = MeetColors.cardBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Perfil Corporativo / Empresa", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (businessProfiles.isEmpty()) {
                        Text(
                            "No tienes empresas de flotas configuradas. Registra una para comenzar a administrar choferes y vehículos.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        EliteButton(
                            text = "Registrar Empresa",
                            onClick = onCreateBusiness,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val activeBiz = businessProfiles.find { it.id == selectedBizId }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(activeBiz?.name ?: "Seleccione Empresa", color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("RFC: ${activeBiz?.taxId ?: "No Registrado"}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                Text("Plan: ${activeBiz?.planType ?: "Básico"}", color = MeetColors.cyberCyan, fontSize = 11.sp)
                            }
                            IconButton(onClick = onCreateBusiness) {
                                AnimatedNeonIcon(Icons.Default.AddBusiness, contentDescription = "Añadir negocio", tint = MeetColors.electricBlue)
                            }
                        }
                    }
                }
            }
        }

        // Fleets List & Creation
        if (selectedBizId != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhantomSectionHeader(label = "FLOTAS ACTIVAS (${fleets.size})", accentColor = MeetColors.electricBlue)
                    IconButton(onClick = onCreateFleet) {
                        AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Añadir Flota", tint = MeetColors.electricBlue)
                    }
                }
            }

            if (fleets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No has creado sub-flotas en esta empresa.", color = MeetColors.textSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                items(fleets) { fleet ->
                    EliteCard(
                        glowColor = MeetColors.cyberCyan.copy(alpha = 0.1f),
                        backgroundColor = MeetColors.cardBackground
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fleet.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(fleet.description ?: "Sin descripción", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Código de Invitación", color = MeetColors.textSecondary, fontSize = 9.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MeetColors.backgroundDeep)
                                        .clickable { onCopyCode(fleet.inviteCode) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        fleet.inviteCode,
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AnimatedNeonIcon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MeetColors.cyberCyan, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Driver Section: Join Fleet
        item {
            PhantomSectionHeader(label = "SECCIÓN PARA CONDUCTORES", accentColor = MeetColors.cyberCyan)
            Spacer(modifier = Modifier.height(4.dp))
            EliteCard(
                glowColor = MeetColors.cyberCyan.copy(alpha = 0.1f),
                backgroundColor = MeetColors.cardBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("¿Eres conductor? Únete a una Flota", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Pide el código de invitación a tu administrador para vincular tu cuenta.", color = MeetColors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it },
                            placeholder = { Text("Ej: EVG-METRO", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.electricBlue,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                if (inviteCodeInput.isNotBlank()) {
                                    onJoinFleet(inviteCodeInput.trim().uppercase())
                                    inviteCodeInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan)
                        ) {
                            Text("Unirse", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TAB CONTENT: VEHICLES
// ══════════════════════════════════════════════════════
@Composable
fun VehiclesTabContent(
    vehicles: List<VehicleEntity>,
    members: List<FleetMemberEntity>,
    fleets: List<FleetEntity>,
    onAssignClick: (VehicleEntity) -> Unit,
    onDiagnoseClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (vehicles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay vehículos registrados. Presiona el botón superior de refresco para generar los datos de prueba.", color = MeetColors.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            items(vehicles) { vehicle ->
                val assignedDriver = members.find { it.userId == vehicle.assignedDriverId }
                val assignedFleet = fleets.find { it.id == vehicle.fleetId }

                // Simulated active DTC codes for presentation (specifically Kenworth has engine faults)
                val hasDtcFaults = vehicle.make == "Kenworth"
                val faultCodes = if (hasDtcFaults) listOf("P0301", "P0420") else emptyList()

                EliteCard(
                    glowColor = if (hasDtcFaults) MeetColors.error.copy(alpha = 0.2f) else MeetColors.electricBlue.copy(alpha = 0.1f),
                    backgroundColor = MeetColors.cardBackground
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text("Placas: ${vehicle.plate} | Odiómetro: ${vehicle.odometerKm} km", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                            
                            IconButton(onClick = { onAssignClick(vehicle) }) {
                                AnimatedNeonIcon(Icons.Default.AssignmentInd, contentDescription = "Asignar", tint = MeetColors.electricBlue)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MeetColors.backgroundDeep)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Conductor Asignado", color = MeetColors.textSecondary, fontSize = 10.sp)
                                Text(assignedDriver?.userId ?: "Sin asignar ⚠️", color = if (assignedDriver != null) Color.White else MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Flota Operativa", color = MeetColors.textSecondary, fontSize = 10.sp)
                                Text(assignedFleet?.name ?: "Sin flota ⚠️", color = if (assignedFleet != null) MeetColors.cyberCyan else MeetColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (hasDtcFaults) {
                            Spacer(modifier = Modifier.height(12.dp))
                            EliteCard(
                                glowColor = MeetColors.error.copy(alpha = 0.3f),
                                backgroundColor = MeetColors.error.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("⚠️ CÓDIGOS DE FALLA ACTIVOS", color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("P0301 (Falla Cilindro 1) & P0420 (Eficiencia Catalizador)", color = Color.White, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { onDiagnoseClick("P0301") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Consultar IA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

data class ChatListItemData(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val type: String, // "DIRECT", "GROUP", "CHANNEL", "FLEET", "CALL"
    val rawChat: ChatMessageEntity? = null,
    val rawFleet: FleetEntity? = null,
    val status: String? = null
)

@Composable
fun MessagingTabContent(
    recentChats: List<ChatMessageEntity>,
    vehicles: List<VehicleEntity>,
    fleets: List<FleetEntity> = emptyList(),
    viewModel: FleetChatViewModel,
    navController: NavController,
    onSubmitDvir: (String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("Todos") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showQrMeshDialog by remember { mutableStateOf(false) }
    var isScanningRadar by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val meshRoute by ElysiumMeshGovernance.activeRoute.collectAsState()
    val isWifiHwOn by ElysiumMeshGovernance.isWifiHardwareOn.collectAsState()
    val isBleHwOn by ElysiumMeshGovernance.isBleHardwareOn.collectAsState()
    val localIp by ElysiumMeshGovernance.localIpAddress.collectAsState()
    val discoveredPeers by ElysiumMeshGovernance.discoveredPhysicalPeers.collectAsState()
    val peersCount by ElysiumMeshGovernance.connectedPeersCount.collectAsState()
    val isWifiMeshOn by ElysiumMeshGovernance.isWifiMeshEnabled.collectAsState()
    val isBleMeshOn by ElysiumMeshGovernance.isBleMeshEnabled.collectAsState()

    val activeVoiceCall by viewModel.activeVoiceCall.collectAsState()
    val activeVideoCall by viewModel.activeVideoCall.collectAsState()

    // Observador periódico activo de hardware de radios
    LaunchedEffect(Unit) {
        while (isActive) {
            ElysiumMeshGovernance.refreshHardwareState(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // ── Banner de Llamada Mesh Entrante / Activa ──
        if (activeVoiceCall?.isActive == true || activeVideoCall?.isActive == true) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable { navController.navigate("fleet_chat_detail") },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF072013),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF39FF66))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (activeVideoCall?.isActive == true) "📹" else "📞", fontSize = 20.sp)
                        Column {
                            Text(
                                text = if (activeVideoCall?.isActive == true) "VIDEOLLAMADA MESH ACTIVA" else "LLAMADA DE VOZ MESH ACTIVA",
                                color = Color(0xFF39FF66),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Toca para abrir el panel de llamada",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { navController.navigate("fleet_chat_detail") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF66)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("ABRIR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                if (activeVideoCall?.isActive == true) viewModel.endVideoCall()
                                else viewModel.endVoiceCall()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1744))
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (activeFilter == "Radar Mesh") {
            // ── Master Card: RADAR MESH P2P DIRECTO (100% OFF-GRID) ──
            EliteCard(
                glowColor = if (isWifiHwOn && isBleHwOn) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFFFF9100).copy(alpha = 0.2f),
                backgroundColor = Color(0xFF07111E),
                borderColor = if (isWifiHwOn || isBleHwOn) Color(0xFF163255) else Color(0xFF4A1A1A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header con estado de red
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(meshRoute.badgeIcon, fontSize = 14.sp)
                        Column {
                            Text(
                                text = "RADAR MESH P2P DIRECTO",
                                color = Color(0xFF00D9FF),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (isWifiHwOn && isBleHwOn) "Modo Dual Sinérgico (100% Off-Grid)"
                                       else if (isWifiHwOn) "Wi-Fi Mesh Activo · BLE apagado"
                                       else if (isBleHwOn) "BLE Mesh Activo · Wi-Fi apagado"
                                       else "⚠️ RADIOS APAGADAS · ACTIVA WI-FI O BLE",
                                color = if (isWifiHwOn || isBleHwOn) MeetColors.textSecondary else Color(0xFFFF5252),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isWifiHwOn || isBleHwOn) Color(0xFF00F0FF).copy(alpha = 0.15f) else Color(0xFFFF1744).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isWifiHwOn || isBleHwOn) Color(0xFF00F0FF) else Color(0xFFFF1744))
                    ) {
                        Text(
                            text = "${discoveredPeers.size} NODOS",
                            color = if (isWifiHwOn || isBleHwOn) Color(0xFF00F0FF) else Color(0xFFFF1744),
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Badges de detección física de hardware en tiempo real
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isWifiHwOn) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFF222B35),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isWifiHwOn) Color(0xFF00E5FF) else Color(0xFF37474F)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isWifiHwOn) "📶 WI-FI ON" else "📶 WI-FI OFF",
                                color = if (isWifiHwOn) Color(0xFF00E5FF) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBleHwOn) Color(0xFFB388FF).copy(alpha = 0.12f) else Color(0xFF222B35),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isBleHwOn) Color(0xFFB388FF) else Color(0xFF37474F)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isBleHwOn) "📡 BLE ON" else "📡 BLE OFF",
                                color = if (isBleHwOn) Color(0xFFB388FF) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF102033),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🌐 ${localIp ?: "Sin IP local"}",
                                color = Color(0xFF64B5F6),
                                fontWeight = FontWeight.Medium,
                                fontSize = 8.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Banner guía explícito de Enlace con 1-Toque
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF003822).copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF66).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚡", fontSize = 13.sp)
                        Text(
                            text = "Enlace con 1 Toque: Presiona [⚡ ESCANEAR RADAR MESH]. El otro dispositivo aparecerá en la lista de nodos descubiertos. Pulsa [⚡ ENLAZAR 1-TOQUE] y entrarás directamente al chat.",
                            color = Color(0xFFD4FFD4),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 12.sp
                        )
                    }
                }

                // ── Los 2 Botones Maestros de Conexión ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón 1: ESCANEAR RADAR MESH
                    Button(
                        onClick = {
                            isScanningRadar = true
                            ElysiumPhysicalMeshEngine.triggerQuickDiscovery()
                            ElysiumMeshGovernance.refreshHardwareState(context)
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1200)
                                isScanningRadar = false
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanningRadar) Color(0xFF00838F) else Color(0xFF00B0FF)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (isScanningRadar) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "ESCANEANDO...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            } else {
                                Text("⚡", fontSize = 12.sp)
                                Text(
                                    text = "ESCANEAR RADAR MESH",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Botón 2: QR MESH P2P
                    OutlinedButton(
                        onClick = { showQrMeshDialog = true },
                        modifier = Modifier
                            .weight(0.9f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00D9FF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D9FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📱", fontSize = 12.sp)
                            Text("QR MESH P2P", fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                        }
                    }
                }

                // ── Lista de Pares Físicos Descubiertos (Enlace 1-Toque) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISPOSITIVOS DETECTADOS (1-TOQUE PARA ENLAZAR):",
                            color = Color(0xFF39FF66),
                            fontWeight = FontWeight.Black,
                            fontSize = 9.5.sp
                        )
                        Text(
                            text = if (discoveredPeers.isNotEmpty()) "${discoveredPeers.size} EN ALCANCE" else "A LA ESCUCHA",
                            color = if (discoveredPeers.isNotEmpty()) Color(0xFF39FF66) else MeetColors.textMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (discoveredPeers.isNotEmpty()) {
                        discoveredPeers.forEach { peer ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0B1728),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                .border(1.dp, Color(0xFF00E5FF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("📡", fontSize = 15.sp)
                                        }
                                        Column {
                                            Text(
                                                text = peer.deviceName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                            Text(
                                                text = "${peer.nodeId} · IP: ${peer.ipAddress}:${peer.port}",
                                                color = Color(0xFF80D8FF),
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.linkPhysicalPeer(peer)
                                            navController.navigate("fleet_chat_detail")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF66)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("⚡", fontSize = 10.sp, color = Color.Black)
                                            Text(
                                                text = "ENLAZAR 1-TOQUE",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Tarjeta de estado y guía de enlace si no hay nodos todavía
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF08121E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF162A45))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("📡", fontSize = 13.sp)
                                    Text(
                                        text = "Ningún nodo detectado en alcance aún",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = "1. Conecta ambos teléfonos a la misma red Wi-Fi (o crea un Hotspot).\n2. Abre MEET en ambos y presiona [⚡ ESCANEAR RADAR MESH].\n3. Aparecerá aquí el dispositivo con el botón [⚡ ENLAZAR 1-TOQUE].",
                                    color = MeetColors.textSecondary,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
            // ── Compact Radar Banner (Sleek & Space-Efficient) ──
            Surface(
                onClick = { activeFilter = "Radar Mesh" },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF07111E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(meshRoute.badgeIcon, fontSize = 16.sp)
                        Column {
                            Text(
                                text = "RADAR MESH P2P DIRECTO",
                                color = Color(0xFF00D9FF),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (discoveredPeers.isNotEmpty()) "${discoveredPeers.size} nodos detectados · 1-Toque activo"
                                       else if (isWifiHwOn && isBleHwOn) "Modo Dual Sinérgico (100% Off-Grid)"
                                       else "Radios a la escucha · Toca para abrir radar",
                                color = if (discoveredPeers.isNotEmpty()) Color(0xFF39FF66) else MeetColors.textSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Text(
                            text = "VER RADAR",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }


        // Search conversations bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar contacto, chat o reporte...", color = MeetColors.textSecondary, fontSize = 12.sp) },
            leadingIcon = { AnimatedNeonIcon(Icons.Default.Search, contentDescription = null, tint = MeetColors.electricBlue) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeetColors.electricBlue,
                unfocusedBorderColor = Color(0xFF152238),
                focusedContainerColor = Color(0xFF070E1A),
                unfocusedContainerColor = Color(0xFF070E1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        // ── WhatsApp-Inspired Quick Filter Chips (Todos, Directos, Grupos, Canales, Radar Mesh, Llamadas) ──
        val filterTabs = listOf("Todos", "Directos", "Grupos", "Canales", "Radar Mesh", "Llamadas")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEach { filter ->
                val isSelected = activeFilter == filter
                val badgeText = when (filter) {
                    "Radar Mesh" -> if (discoveredPeers.isNotEmpty()) "${discoveredPeers.size}" else null
                    "Llamadas" -> if (activeVoiceCall?.isActive == true || activeVideoCall?.isActive == true) "●" else null
                    "Grupos" -> {
                        val groupCount = fleets.count { it.id.startsWith("grp_") || it.businessId != "mesh_p2p" }
                        if (groupCount > 0) "$groupCount" else null
                    }
                    "Canales" -> {
                        val channelCount = fleets.count { it.id.startsWith("chn_") }
                        if (channelCount > 0) "$channelCount" else null
                    }
                    else -> null
                }

                Surface(
                    onClick = { activeFilter = filter },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFF0C1628),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00F0FF) else Color(0xFF1B2C47)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color.White else MeetColors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        )
                        if (badgeText != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (filter == "Llamadas") Color(0xFF39FF66) else Color(0xFF00E5FF).copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = if (filter == "Llamadas") Color.Black else Color(0xFF00E5FF),
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

        Spacer(modifier = Modifier.height(10.dp))

        // ── Build Unified Conversation List (Direct, Groups, Channels, Calls) ──
        val conversationItems = remember(recentChats, fleets) {
            val list = mutableListOf<ChatListItemData>()
            val processedIds = mutableSetOf<String>()

            // 1. Group recentChats by partnerId
            val chatsByPartner = recentChats.groupBy { chat ->
                if (chat.senderId == viewModel.currentUserId) chat.receiverId else chat.senderId
            }

            for ((partnerId, msgs) in chatsByPartner) {
                processedIds.add(partnerId)
                val latest = msgs.maxByOrNull { it.timestamp } ?: continue
                val isGrp = partnerId.startsWith("grp_")
                val isChn = partnerId.startsWith("chn_")
                val isFlt = partnerId.startsWith("flt_")
                val isCall = latest.messageType == "CALL"

                val fleetMatch = fleets.find { it.id == partnerId }
                val title = fleetMatch?.name ?: partnerId
                val itemType = if (isCall) "CALL" else if (isChn) "CHANNEL" else if (isGrp) "GROUP" else if (isFlt) "FLEET" else "DIRECT"

                val sub = when (latest.messageType) {
                    "IMAGE" -> "📷 Foto"
                    "AUDIO" -> "🎙️ Nota de voz (${latest.durationSeconds}s)"
                    "CALL" -> if (latest.messageText?.contains("Videollamada") == true) "📹 Videollamada Mesh" else "📞 Llamada de voz Mesh"
                    "DTC_ALERT" -> "⚠️ Alerta de Motor"
                    "FILE" -> "📁 Archivo"
                    else -> latest.messageText ?: ""
                }

                list.add(
                    ChatListItemData(
                        id = partnerId,
                        title = title,
                        subtitle = sub,
                        timestamp = latest.timestamp,
                        type = itemType,
                        rawChat = latest,
                        rawFleet = fleetMatch,
                        status = latest.status
                    )
                )
            }

            // 2. Add fleets/groups/channels that don't have messages yet
            for (f in fleets) {
                if (!processedIds.contains(f.id)) {
                    val isGrp = f.id.startsWith("grp_")
                    val isChn = f.id.startsWith("chn_")
                    val itemType = if (isChn) "CHANNEL" else if (isGrp) "GROUP" else "FLEET"
                    val sub = if (isChn) "Canal de difusión · Toca para transmitir"
                              else if (isGrp) "Grupo de flota · Toca para escribir"
                              else "Flota de trabajo · Toca para iniciar chat"

                    list.add(
                        ChatListItemData(
                            id = f.id,
                            title = f.name,
                            subtitle = sub,
                            timestamp = f.createdAt,
                            type = itemType,
                            rawFleet = f
                        )
                    )
                }
            }

            list.sortedByDescending { it.timestamp }
        }

        val filteredItems = remember(conversationItems, searchQuery, activeFilter) {
            conversationItems.filter { item ->
                val matchesSearch = searchQuery.isEmpty() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.subtitle.contains(searchQuery, ignoreCase = true)

                val matchesTab = when (activeFilter) {
                    "Todos" -> item.type != "CALL"
                    "Directos" -> item.type == "DIRECT"
                    "Grupos" -> item.type == "GROUP" || item.type == "FLEET"
                    "Canales" -> item.type == "CHANNEL"
                    "Llamadas" -> item.type == "CALL"
                    "Radar Mesh" -> true
                    else -> true
                }
                matchesSearch && matchesTab
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sectionLabel = when (activeFilter) {
                "Directos" -> "CHATS DIRECTOS (${filteredItems.size})"
                "Grupos" -> "GRUPOS DE FLOTA (${filteredItems.size})"
                "Canales" -> "CANALES DE DIFUSIÓN (${filteredItems.size})"
                "Llamadas" -> "REGISTRO DE LLAMADAS (${filteredItems.size})"
                "Radar Mesh" -> "NODOS EN ALCANCE (${discoveredPeers.size})"
                else -> "CONVERSACIONES (${filteredItems.size})"
            }
            PhantomSectionHeader(label = sectionLabel, accentColor = MeetColors.electricBlue)
            TextButton(onClick = { showNewChatDialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonIcon(Icons.Default.AddComment, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo Chat", color = MeetColors.neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (activeFilter == "Radar Mesh") {
            // ── VISTA DE RADAR MESH: LISTA DE NODOS DETECTADOS (1-TOQUE) ──
            if (discoveredPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📡", fontSize = 32.sp)
                        Text("No se detectan otros dispositivos en alcance.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Verifica que ambos tengan Wi-Fi o Hotspot activo y pulsa [⚡ ESCANEAR RADAR MESH].", color = MeetColors.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(discoveredPeers) { peer ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0B1728),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                            .border(1.dp, Color(0xFF00E5FF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📡", fontSize = 18.sp)
                                    }
                                    Column {
                                        Text(peer.deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${peer.nodeId} · IP: ${peer.ipAddress}:${peer.port}", color = Color(0xFF80D8FF), fontSize = 9.5.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.linkPhysicalPeer(peer)
                                        navController.navigate("fleet_chat_detail")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF66)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("⚡ ENLAZAR", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val emptyMsg = when (activeFilter) {
                        "Directos" -> "No hay chats directos iniciados."
                        "Grupos" -> "No hay grupos de flota creados aún."
                        "Canales" -> "No hay canales de difusión activos."
                        "Llamadas" -> "No hay registro de llamadas recientes."
                        else -> "No hay conversaciones disponibles."
                    }
                    Text(emptyMsg, color = MeetColors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    EliteButton(
                        text = if (activeFilter == "Grupos") "Crear Nuevo Grupo" else if (activeFilter == "Canales") "Crear Nuevo Canal" else "Iniciar Nueva Conversación",
                        onClick = { showNewChatDialog = true }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredItems) { item ->
                    ChatRowItem(
                        item = item,
                        onClick = {
                            val dummyPartner = FleetMemberEntity(
                                id = item.id,
                                businessId = item.rawFleet?.businessId ?: item.rawChat?.businessId ?: "mesh_p2p",
                                userId = item.title,
                                role = when (item.type) {
                                    "CHANNEL" -> "CANAL"
                                    "GROUP" -> "GRUPO"
                                    "FLEET" -> "FLOTA"
                                    else -> "CONDUCTOR"
                                },
                                email = "${item.id}@meet.mesh",
                                inviteStatus = "ACCEPTED",
                                joinedAt = item.timestamp,
                                fleetId = item.id
                            )
                            viewModel.selectPartner(dummyPartner)
                            navController.navigate("fleet_chat_detail")
                        }
                    )
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            viewModel = viewModel,
            onDismiss = { showNewChatDialog = false },
            onSelectPartner = { partner ->
                viewModel.selectPartner(partner)
                showNewChatDialog = false
                navController.navigate("fleet_chat_detail")
            }
        )
    }

    if (showQrMeshDialog) {
        MeshOfflineQrDialog(
            viewModel = viewModel,
            onDismiss = { showQrMeshDialog = false },
            onSuccessLink = {
                navController.navigate("fleet_chat_detail")
            }
        )
    }
}

// ══════════════════════════════════════════════════════
// CHAT ROW ITEM (WhatsApp Layout + Elysium 3D Neon)
// ══════════════════════════════════════════════════════

@Composable
fun ChatRowItem(
    item: ChatListItemData,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    val glowColor = when (item.type) {
        "CHANNEL" -> Color(0xFFFF9100).copy(alpha = 0.12f)
        "GROUP" -> Color(0xFF39FF66).copy(alpha = 0.12f)
        "FLEET" -> Color(0xFF00D9FF).copy(alpha = 0.12f)
        "CALL" -> Color(0xFF7C4DFF).copy(alpha = 0.12f)
        else -> MeetColors.electricBlue.copy(alpha = 0.08f)
    }

    val borderColor = when (item.type) {
        "CHANNEL" -> Color(0xFF4A2500)
        "GROUP" -> Color(0xFF003D1A)
        "CALL" -> Color(0xFF281452)
        else -> Color(0xFF162540)
    }

    EliteCard(
        glowColor = glowColor,
        backgroundColor = Color(0xFF0B1424),
        borderColor = borderColor,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar 3D según el tipo (Directo, Grupo, Canal, Llamada) ──
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.type) {
                            "CHANNEL" -> Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF1744)))
                            "GROUP", "FLEET" -> Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00897B)))
                            "CALL" -> Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00B0FF)))
                            else -> Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7928CA)))
                        }
                    )
                    .border(
                        1.5.dp,
                        when (item.type) {
                            "CHANNEL" -> Color(0xFFFF9100).copy(alpha = 0.8f)
                            "GROUP", "FLEET" -> Color(0xFF39FF66).copy(alpha = 0.8f)
                            "CALL" -> Color(0xFF7C4DFF).copy(alpha = 0.8f)
                            else -> Color(0xFF00F0FF).copy(alpha = 0.6f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (item.type) {
                    "CHANNEL" -> Text("📢", fontSize = 18.sp)
                    "GROUP", "FLEET" -> Text("👥", fontSize = 18.sp)
                    "CALL" -> Text("📞", fontSize = 18.sp)
                    else -> {
                        Text(
                            text = item.title.take(2).uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        // Indicador de presencia en Malla para contactos directos
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF39FF66))
                                .border(1.5.dp, Color(0xFF050B15), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                        // Badge contextual
                        when (item.type) {
                            "CHANNEL" -> {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF9100).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFFFF9100))
                                ) {
                                    Text("CANAL", color = Color(0xFFFF9100), fontSize = 7.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            "GROUP" -> {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF39FF66).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF39FF66))
                                ) {
                                    Text("GRUPO", color = Color(0xFF39FF66), fontSize = 7.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            "FLEET" -> {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF00D9FF).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF00D9FF))
                                ) {
                                    Text("FLOTA", color = Color(0xFF00D9FF), fontSize = 7.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            "CALL" -> {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF7C4DFF))
                                ) {
                                    Text("LLAMADA", color = Color(0xFFB388FF), fontSize = 7.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }

                    Text(
                        text = formattedTime,
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.subtitle,
                        color = when (item.type) {
                            "CHANNEL" -> Color(0xFFFFB74D)
                            "GROUP" -> Color(0xFFB9F6CA)
                            "CALL" -> Color(0xFFB388FF)
                            else -> MeetColors.textSecondary
                        },
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Ticks de entrega tipo WhatsApp para mensajes enviados
                    item.status?.let { st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (st) {
                                "DELIVERED_MESH_WIFI" -> {
                                    Text("⚡", fontSize = 10.sp)
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(12.dp))
                                }
                                "DELIVERED_MESH_BLE" -> {
                                    Text("📡", fontSize = 10.sp)
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF9D00FF), modifier = Modifier.size(12.dp))
                                }
                                "DELIVERED", "READ", "DELIVERED_CLOUD" -> {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF39FF66), modifier = Modifier.size(12.dp))
                                }
                                else -> {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.textSecondary, modifier = Modifier.size(12.dp))
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
fun ChatRowItem(
    message: ChatMessageEntity,
    partnerId: String,
    onClick: () -> Unit
) {
    val sub = when (message.messageType) {
        "IMAGE" -> "📷 Foto"
        "AUDIO" -> "🎙️ Nota de voz (${message.durationSeconds}s)"
        "CALL" -> if (message.messageText?.contains("Videollamada") == true) "📹 Videollamada Mesh" else "📞 Llamada de voz Mesh"
        "DTC_ALERT" -> "⚠️ Alerta de Motor"
        "FILE" -> "📁 Archivo"
        else -> message.messageText ?: ""
    }
    val item = ChatListItemData(
        id = partnerId,
        title = partnerId,
        subtitle = sub,
        timestamp = message.timestamp,
        type = if (message.messageType == "CALL") "CALL" else if (partnerId.startsWith("chn_")) "CHANNEL" else if (partnerId.startsWith("grp_")) "GROUP" else "DIRECT",
        rawChat = message,
        status = message.status
    )
    ChatRowItem(item = item, onClick = onClick)
}

@Composable
fun NewChatDialog(
    viewModel: FleetChatViewModel,
    onDismiss: () -> Unit,
    onSelectPartner: (FleetMemberEntity) -> Unit
) {
    NewConversationHubDialog(
        viewModel = viewModel,
        onDismiss = onDismiss,
        onSelectPartner = onSelectPartner
    )
}

@Composable
fun NewConversationHubDialog(
    viewModel: FleetChatViewModel,
    onDismiss: () -> Unit,
    onSelectPartner: (FleetMemberEntity) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Directo / Mesh, 1: Grupo, 2: Canal
    val members by viewModel.membersForActiveBusiness.collectAsState(initial = emptyList())
    val discoveredPeers by ElysiumMeshGovernance.discoveredPhysicalPeers.collectAsState()

    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }

    var channelName by remember { mutableStateOf("") }
    var channelDesc by remember { mutableStateOf("") }

    var manualPeerInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (selectedTab == 0) "💬" else if (selectedTab == 1) "👥" else "📢", fontSize = 20.sp)
                    Text("Nueva Comunicación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Switcher de 3 pestañas estilo WhatsApp / Telegram
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070F1C))
                        .border(1.dp, Color(0xFF162540), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Directo", "Grupo", "Canal").forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    if (isSelected) 1.dp else 0.dp,
                                    if (isSelected) MeetColors.electricBlue else Color.Transparent,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) Color.White else MeetColors.textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        text = {
            when (selectedTab) {
                0 -> {
                    // ── 1. CHAT DIRECTO / MESH ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "DISPOSITIVOS MESH DETECTADOS (1-TOQUE P2P):",
                            color = Color(0xFF00D9FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )

                        if (discoveredPeers.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0B1624),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF162A45))
                            ) {
                                Text(
                                    text = "Buscando nodos mesh cercanos... Enciende Wi-Fi o Bluetooth.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.heightIn(max = 130.dp)
                            ) {
                                items(discoveredPeers) { peer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF08182B))
                                            .border(1.dp, Color(0xFF00D9FF).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.linkPhysicalPeer(peer)
                                                val dummy = FleetMemberEntity(
                                                    id = peer.nodeId,
                                                    businessId = "mesh_p2p",
                                                    userId = peer.nodeId,
                                                    role = peer.role,
                                                    email = "${peer.nodeId.lowercase()}@mesh.direct",
                                                    inviteStatus = "ACCEPTED",
                                                    joinedAt = System.currentTimeMillis()
                                                )
                                                onSelectPartner(dummy)
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00D9FF).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("⚡", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(peer.deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${peer.nodeId} · IP: ${peer.ipAddress}", color = Color(0xFF80D8FF), fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Conexión manual directa por IP o ID
                        Text(
                            text = "O INGRESAR ID O IP DIRECTA:",
                            color = MeetColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualPeerInput,
                                onValueChange = { manualPeerInput = it },
                                placeholder = { Text("Ej: 192.168.1.15 o EVG-VER...", fontSize = 11.sp, color = MeetColors.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = Color(0xFF1E3A5F),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Button(
                                onClick = {
                                    if (manualPeerInput.isNotBlank()) {
                                        viewModel.startDirectChat(manualPeerInput.trim(), manualPeerInput.trim())
                                        val dummy = FleetMemberEntity(
                                            id = manualPeerInput.trim(),
                                            businessId = "mesh_p2p",
                                            userId = manualPeerInput.trim(),
                                            role = "DIRECTO",
                                            email = "${manualPeerInput.trim()}@mesh.direct",
                                            inviteStatus = "ACCEPTED",
                                            joinedAt = System.currentTimeMillis()
                                        )
                                        onSelectPartner(dummy)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Abrir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        // Miembros registrados en la nube si existen
                        if (members.isNotEmpty()) {
                            Text(
                                text = "MIEMBROS DE LA FLOTA:",
                                color = MeetColors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.heightIn(max = 100.dp)
                            ) {
                                items(members) { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.backgroundDeep)
                                            .clickable { onSelectPartner(member) }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MeetColors.electricBlue.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AnimatedNeonIcon(Icons.Default.Person, contentDescription = null, tint = MeetColors.electricBlue, modifier = Modifier.size(12.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(member.userId, color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ── 2. NUEVO GRUPO ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "CREAR GRUPO DE FLOTA O CUADRILLA:",
                            color = Color(0xFF39FF66),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Nombre del Grupo (ej: Taller San José, Cuadrilla Sur)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF39FF66),
                                unfocusedBorderColor = Color(0xFF1E3A5F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = groupDesc,
                            onValueChange = { groupDesc = it },
                            label = { Text("Descripción / Propósito (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF39FF66),
                                unfocusedBorderColor = Color(0xFF1E3A5F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                if (groupName.isNotBlank()) {
                                    viewModel.createGroup(groupName.trim(), groupDesc.trim().ifEmpty { null }) { created ->
                                        val dummy = FleetMemberEntity(
                                            id = created.id,
                                            businessId = created.businessId,
                                            userId = created.name,
                                            role = "GRUPO",
                                            email = "${created.id}@mesh.fleet",
                                            inviteStatus = "ACCEPTED",
                                            joinedAt = created.createdAt,
                                            fleetId = created.id
                                        )
                                        onSelectPartner(dummy)
                                    }
                                }
                            },
                            enabled = groupName.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF66)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("👥 CREAR GRUPO", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                }
                2 -> {
                    // ── 3. NUEVO CANAL ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "CREAR CANAL DE DIFUSIÓN O EMERGENCIAS:",
                            color = Color(0xFFFF9100),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            label = { Text("Nombre del Canal (ej: 📢 Canal SOS Terremotos, Alertas Vía)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF9100),
                                unfocusedBorderColor = Color(0xFF1E3A5F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = channelDesc,
                            onValueChange = { channelDesc = it },
                            label = { Text("Descripción del canal de difusión") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF9100),
                                unfocusedBorderColor = Color(0xFF1E3A5F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                if (channelName.isNotBlank()) {
                                    viewModel.createChannel(channelName.trim(), channelDesc.trim().ifEmpty { null }) { created ->
                                        val dummy = FleetMemberEntity(
                                            id = created.id,
                                            businessId = created.businessId,
                                            userId = created.name,
                                            role = "CANAL",
                                            email = "${created.id}@mesh.channel",
                                            inviteStatus = "ACCEPTED",
                                            joinedAt = created.createdAt,
                                            fleetId = created.id
                                        )
                                        onSelectPartner(dummy)
                                    }
                                }
                            },
                            enabled = channelName.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📢 CREAR CANAL DE DIFUSIÓN", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = MeetColors.electricBlue)
            }
        },
        containerColor = MeetColors.backgroundDark
    )
}

@Composable
fun MeshOfflineQrDialog(
    viewModel: FleetChatViewModel,
    onDismiss: () -> Unit,
    onSuccessLink: () -> Unit
) {
    val localIp by ElysiumMeshGovernance.localIpAddress.collectAsState()
    val qrPayload = remember(localIp) { ElysiumPhysicalMeshEngine.createOfflineQrPayload() }
    var targetInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📱", fontSize = 18.sp)
                Text("QR Mesh P2P (100% Offline)", color = Color(0xFF00D9FF), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "No requiere internet ni señal móvil. Muestra este código o ingresa la IP local del otro teléfono.",
                    color = MeetColors.textSecondary,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center
                )

                // QR Code Image (Offline)
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QrCodeImage(
                        text = qrPayload,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF081220))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "TU NODO: ${ElysiumPhysicalMeshEngine.localNodeId}",
                        color = Color(0xFF00D9FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp
                    )
                    Text(
                        text = "TU IP LOCAL: ${localIp ?: "Sin IP (Conecta Wi-Fi o comparte Hotspot)"}",
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }

                // Input para conectar por IP o QR
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = {
                        targetInput = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("IP (ej: 192.168.1.15) o código QR", color = MeetColors.textSecondary, fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D9FF),
                        unfocusedBorderColor = Color(0xFF1B2D45),
                        focusedContainerColor = Color(0xFF060D17),
                        unfocusedContainerColor = Color(0xFF060D17),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (targetInput.isBlank()) {
                            errorMessage = "Ingresa la IP del otro teléfono"
                            return@Button
                        }
                        isConnecting = true
                        viewModel.linkPeerByIpOrPayload(targetInput) { ok, msg ->
                            isConnecting = false
                            if (ok) {
                                onDismiss()
                                onSuccessLink()
                            } else {
                                errorMessage = msg
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF66))
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("⚡ ENLAZAR POR IP DIRECTA", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Color(0xFF00D9FF))
            }
        },
        containerColor = Color(0xFF060E1A)
    )
}

