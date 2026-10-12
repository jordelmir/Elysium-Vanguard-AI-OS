package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.communications.*
import com.elysium369.meet.core.agent.ui.*
import com.elysium369.meet.ui.CommunicationViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.DateFormat
import java.util.Date

private enum class MessagesPane { INBOX, DISCOVER, CONTACTS, CALLS, MESH, SETTINGS, BLOCKED }

@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    serviceVertical: String? = null,
    serviceReferenceId: String? = null,
    serviceTitle: String? = null,
    viewModel: CommunicationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshNearby()
                viewModel.refreshOnline()
                kotlinx.coroutines.delay(10_000)
            }
        }
    }
    DisposableEffect(viewModel,lifecycleOwner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.stopNearby()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer);viewModel.stopNearby() }
    }
    val nearbyStatus by viewModel.nearbyStatus.collectAsStateWithLifecycle()
    val nearbyPeers by viewModel.nearbyPeers.collectAsStateWithLifecycle()
    val nearbyInvitations by viewModel.nearbyInvitations.collectAsStateWithLifecycle()
    val nearbyPermissions=remember {
        buildList {
            if(android.os.Build.VERSION.SDK_INT>=31) {
                add(Manifest.permission.BLUETOOTH_SCAN);add(Manifest.permission.BLUETOOTH_CONNECT);add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if(android.os.Build.VERSION.SDK_INT>=33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            else { add(Manifest.permission.ACCESS_COARSE_LOCATION);add(Manifest.permission.ACCESS_FINE_LOCATION) }
            if(android.os.Build.VERSION.SDK_INT>=37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }.toTypedArray()
    }
    val nearbyPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(nearbyPermissions.all { result[it]==true || ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }) viewModel.startNearby()
        else viewModel.nearbyPermissionDenied()
    }
    val onlineStatus by viewModel.onlineStatus.collectAsStateWithLifecycle()
    val pairingQr by viewModel.pairingQr.collectAsStateWithLifecycle()
    var scanningQr by remember { mutableStateOf(false) }
    if (scanningQr) {
        com.elysium369.meet.core.share.QrScannerOverlay(
            onResult = { scanningQr = false; viewModel.scanPairingQr(it) },
            onDismiss = { scanningQr = false },
        )
    }
    pairingQr?.let { value ->
        val bitmap = remember(value) { com.elysium369.meet.core.share.QrCodeSharing.generateQrBitmap(value) }
        AlertDialog(
            onDismissRequest = viewModel::dismissPairingQr,
            title = { Text("Conectar en persona") },
            text = { Column {
                Text("Muéstralo solo a la persona que quieres conectar. Vence en 5 minutos y se usa una vez.")
                bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "QR para conectar", Modifier.fillMaxWidth().aspectRatio(1f)) }
            } },
            confirmButton = { TextButton(viewModel::dismissPairingQr) { Text("Cerrar") } },
        )
    }
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selected by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val speakerEnabled by viewModel.speakerEnabled.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCall.collectAsStateWithLifecycle()
    var answeringCall by remember { mutableStateOf(false) }
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val privacy by viewModel.privacy.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedContacts.collectAsStateWithLifecycle()
    val searchOutcome by viewModel.searchOutcome.collectAsStateWithLifecycle()
    val searching by viewModel.searchInProgress.collectAsStateWithLifecycle()
    val voiceNoteState by viewModel.voiceNoteState.collectAsStateWithLifecycle()
    var pane by remember { mutableStateOf(MessagesPane.INBOX) }

    val invite = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Únete a Elysium Vanguard AI OS para conversar y llamar de forma privada. Descarga oficial: ${BuildConfig.ELYSIUM_DOWNLOAD_URL}",
            )
        }
        context.startActivity(Intent.createChooser(send, "Invitar a Elysium"))
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) viewModel.sendImage(uri)
    }
    val attachImage: () -> Unit = { if(viewModel.beginImageSelection()) imagePicker.launch("image/*") }

    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if(answeringCall) viewModel.answerCall(true) else viewModel.startCall() } else viewModel.microphonePermissionDenied()
    }
    val voiceMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceNote() else viewModel.microphonePermissionDenied()
    }
    val startCall = {
        answeringCall=false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCall()
        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    val startVoiceNote = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceNote()
        } else voiceMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(serviceVertical, serviceReferenceId, serviceTitle) {
        if (!serviceVertical.isNullOrBlank() && !serviceReferenceId.isNullOrBlank()) {
            viewModel.openServiceContext(
                serviceVertical,
                serviceReferenceId,
                serviceTitle?.takeIf(String::isNotBlank) ?: "Conversación del servicio",
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = MeetColors.backgroundDeep) {
        Column(Modifier.fillMaxSize()) {
            MessagesHeader(
                title = selected?.title ?: pane.title,
                subtitle = selected?.let { "Conversación privada Elysium" }
                    ?: identity?.elysiumId?.let { "@$it · Elysium Communications" }
                    ?: "Elysium Communications",
                onBack = {
                    when {
                        selected != null -> viewModel.selectConversation(null)
                        pane != MessagesPane.INBOX -> pane = MessagesPane.INBOX
                        else -> onBack()
                    }
                },
                onSearch = if (selected == null && pane != MessagesPane.DISCOVER) ({ pane = MessagesPane.DISCOVER }) else null,
                onSettings = if (selected == null && pane != MessagesPane.SETTINGS) ({ pane = MessagesPane.SETTINGS }) else null,
                onCall = selected?.let { startCall },
                onEndCall = if (callState in setOf(CallConnectionState.ACTIVE,CallConnectionState.CONNECTING,CallConnectionState.RINGING)) viewModel::endCall else null,
            )
            Text(onlineStatus, color = MeetColors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp))
            notice?.let { HonestBanner(it) }
            if(incomingCall!=null) {
                HonestBanner("Llamada de voz entrante")
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val acceptCall = {
                        answeringCall=true
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) viewModel.answerCall(true)
                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Button(acceptCall,Modifier.weight(1f).messageAction("accept_call", "Aceptar llamada", action=acceptCall)) { Text("Aceptar llamada") }
                    OutlinedButton({ viewModel.answerCall(false) },Modifier.weight(1f).messageAction("reject_call", "Rechazar llamada", action={ viewModel.answerCall(false) })) { Text("Rechazar llamada") }
                }
            }
            if(callState in setOf(CallConnectionState.RINGING,CallConnectionState.CONNECTING,CallConnectionState.ACTIVE)) {
                OutlinedButton({ viewModel.setSpeakerEnabled(!speakerEnabled) }, enabled = callState == CallConnectionState.ACTIVE) {
                    Text(if(speakerEnabled) "Altavoz activado · cambiar a auricular" else "Auricular · activar altavoz")
                }
                HonestBanner(when(callState) { CallConnectionState.ACTIVE -> "Audio conectado";CallConnectionState.RINGING -> "Llamando · esperando respuesta";else -> "Conectando audio" })
            }

            if (callState == CallConnectionState.FAILED) {
                HonestBanner("No se pudo conectar el audio. La llamada terminó; puedes volver a intentarlo.")
            }

            if (selected?.canRespondToRequest == true) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({ viewModel.respondToRequest(true) },Modifier.weight(1f).messageAction("request.accept", "Aceptar conversación") { viewModel.respondToRequest(true) }) { Text("Aceptar conversación") }
                    OutlinedButton({ viewModel.respondToRequest(false) },Modifier.weight(1f).messageAction("request.reject", "Rechazar") { viewModel.respondToRequest(false) }) { Text("Rechazar") }
                }
            }
            when {
                selected != null -> ConversationBody(
                    conversation = selected!!,
                    messages = messages,
                    voiceNoteState = voiceNoteState,
                    onSend = viewModel::sendText,
                    onStartVoice = startVoiceNote,
                    onStopVoice = viewModel::stopAndSendVoiceNote,
                    onCancelVoice = viewModel::cancelVoiceNote,
                    onImage = attachImage,
                )
                pane == MessagesPane.INBOX -> Inbox(conversations, contacts, serviceVertical, { viewModel.selectConversation(it.id) }) { pane = it }
                pane == MessagesPane.DISCOVER -> DiscoverPane(searchOutcome, searching, viewModel::searchContact, viewModel::requestContact, invite, viewModel::createPairingQr, { scanningQr = true })
                pane == MessagesPane.CONTACTS -> ContactsPane(contacts, viewModel::openContact, viewModel::block, invite)
                pane == MessagesPane.CALLS -> CallsPane(conversations) { viewModel.selectConversation(it.id) }
                pane == MessagesPane.MESH -> MeshPane(privacy, viewModel::savePrivacy,nearbyStatus,nearbyPeers,nearbyInvitations,
                    { nearbyPermissionLauncher.launch(nearbyPermissions) },viewModel::stopNearby,viewModel::connectNearby,viewModel::answerNearby,
                    onNativeMesh = {
                        identity?.principalId?.takeIf { it.isNotBlank() }?.let { owner ->
                            context.startActivity(Intent(context, com.elysium369.meet.core.mesh.MeshActivity::class.java)
                                .putExtra(com.elysium369.meet.core.mesh.MeshActivity.EXTRA_OWNER, owner))
                        } ?: android.widget.Toast.makeText(context, "Identidad de cuenta pendiente; vuelve a iniciar sesión", android.widget.Toast.LENGTH_SHORT).show()
                    })
                pane == MessagesPane.SETTINGS -> SettingsPane(identity, privacy, viewModel::saveIdentity, viewModel::savePrivacy) { pane = MessagesPane.BLOCKED }
                pane == MessagesPane.BLOCKED -> BlockedPane(blocked, viewModel::unblock)
            }
        }
    }
}

private val MessagesPane.title: String get() = when (this) {
    MessagesPane.INBOX -> "Mensajes y llamadas"
    MessagesPane.DISCOVER -> "Encontrar personas"
    MessagesPane.CONTACTS -> "Contactos Elysium"
    MessagesPane.CALLS -> "Llamadas"
    MessagesPane.MESH -> "Vanguard Mesh"
    MessagesPane.SETTINGS -> "Privacidad y cuenta"
    MessagesPane.BLOCKED -> "Usuarios bloqueados"
}

@Composable
private fun MessagesHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSearch: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onCall: (() -> Unit)?,
    onEndCall: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.messageAction("back", "Volver", action = onBack)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
        Box(Modifier.size(38.dp).background(MeetColors.cyberCyan.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp, maxLines = 1)
        }
        onSearch?.let { IconButton(onClick = it, modifier = Modifier.messageAction("open_search", "Buscar", aliases = setOf("encontrar personas"), action = it)) { Icon(Icons.Outlined.Search, "Buscar", tint = Color.White) } }
        onSettings?.let { IconButton(onClick = it, modifier = Modifier.messageAction("settings", "Ajustes", aliases = setOf("privacidad y cuenta"), action = it)) { Icon(Icons.Outlined.Settings, "Ajustes", tint = Color.White) } }
        if (onEndCall != null) IconButton(onClick = onEndCall) { Icon(Icons.Outlined.CallEnd, "Finalizar", tint = MeetColors.error) }
        else onCall?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Call, "Llamar", tint = MeetColors.neonGreen) } }
    }
}

@Composable
private fun Inbox(
    conversations: List<ConversationSummary>,
    contacts: List<ElysiumContact>,
    requestedVertical: String?,
    onOpen: (ConversationSummary) -> Unit,
    onPane: (MessagesPane) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        if (!requestedVertical.isNullOrBlank()) item { HonestBanner("Habla con el proveedor autorizado sin revelar tu número telefónico.") }
        item {
            Column(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f), RoundedCornerShape(20.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .28f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                Text("Tu red, bajo tus reglas", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Mensajes, llamadas, servicios y comunicación cercana. El teléfono es opcional; no usamos SMS para iniciar sesión.", color = MeetColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction("Nuevo mensaje", Icons.Outlined.PersonAdd, Modifier.weight(1f)) { onPane(MessagesPane.DISCOVER) }
                    PrimaryAction("Llamar", Icons.Outlined.Call, Modifier.weight(1f)) { onPane(MessagesPane.CALLS) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactAction("Contactos", Icons.Outlined.Contacts, contacts.size.toString(), Modifier.weight(1f)) { onPane(MessagesPane.CONTACTS) }
                CompactAction("Mesh", Icons.Outlined.WifiTethering, "sin internet", Modifier.weight(1f)) { onPane(MessagesPane.MESH) }
                CompactAction("Privacidad", Icons.Outlined.Shield, "control", Modifier.weight(1f)) { onPane(MessagesPane.SETTINGS) }
            }
        }
        item { SectionTitle("Conversaciones", if (conversations.isEmpty()) "sin actividad" else "${conversations.size} activas") }
        if (conversations.isEmpty()) item { EmptyPanel("Busca un ID Elysium, correo o teléfono. Si aún no participa, invítalo con el enlace oficial de GitHub.") }
        else items(conversations, key = { it.id }) { row -> ConversationRow(row) { onOpen(row) } }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun DiscoverPane(
    outcome: ContactSearchOutcome?,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onRequest: (ContactSearchResult) -> Unit,
    onInvite: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Habla con quien tú elijas", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
            Text("Búsqueda exacta y privada. No exponemos una lista global de usuarios.", color = MeetColors.textSecondary, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(
                query,
                { query = it.take(320) },
                Modifier.fillMaxWidth().agentTextInput(AgentUiControlId("messages.discover.query"), "Buscar persona", AgentTextFieldRole.SEARCH,
                    readValue = { query }, writeValue = { query = it.take(320) }, commit = { if (query.isNotBlank() && !searching) onSearch(query) }),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("@ID Elysium, correo o teléfono") },
                supportingText = { Text("Teléfono opcional. Nunca se usa para SMS.") },
                singleLine = true,
            )
        }
        item {
            Button({ onSearch(query) }, Modifier.fillMaxWidth().messageAction("search", "Buscar de forma exacta", enabled = query.isNotBlank() && !searching) { onSearch(query) }, enabled = query.isNotBlank() && !searching) {
                if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Search, null)
                Text(if (searching) "Buscando…" else "Buscar de forma exacta", Modifier.padding(start = 8.dp))
            }
        }
        when (outcome) {
            is ContactSearchOutcome.Found -> items(outcome.contacts, key = { it.principalId }) { contact -> SearchResultCard(contact) { onRequest(contact) } }
            is ContactSearchOutcome.NotFound -> item { NotFoundCard(onInvite) }
            ContactSearchOutcome.InvalidQuery -> item { HonestBanner("Escribe un ID válido, correo completo o teléfono con código de país.") }
            ContactSearchOutcome.AuthenticationRequired -> item { HonestBanner("Inicia sesión para consultar el directorio privado. También podrás emparejar en persona por QR.") }
            ContactSearchOutcome.RateLimited -> item { HonestBanner("Pausa de seguridad: se alcanzó el límite de búsquedas. Inténtalo más tarde.") }
            ContactSearchOutcome.ServiceUnavailable -> item { HonestBanner("El directorio no está disponible. No se inventaron resultados.") }
            null -> Unit
        }
        item { SectionTitle("Emparejar sin revelar tu libreta", "en persona") }
        item {
            Button(onShowQr, Modifier.fillMaxWidth().messageAction("show_qr", "Mostrar mi QR para conectar", aliases = setOf("mostrar mi QR", "mi QR"), action = onShowQr)) { Text("Mostrar mi QR para conectar") }
            OutlinedButton(onScanQr, Modifier.fillMaxWidth().messageAction("scan_qr", "Abrir cámara y escanear QR", aliases = setOf("escanear QR", "abrir cámara"), action = onScanQr)) { Text("Abrir cámara y escanear QR") }
            Spacer(Modifier.height(8.dp))
            PairingMethod(Icons.Outlined.WifiTethering, "Encuentro cercano", "Sin anunciar correo ni teléfono; aprobación mutua.")
        }
        item { Text("El QR requiere conexión al servidor. Mesh sin internet sigue pendiente de verificación física; no equivale al chat en línea.", color = MeetColors.warning, fontSize = 10.sp) }
    }
}

@Composable
private fun SearchResultCard(contact: ContactSearchResult, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(18.dp)).border(1.dp, MeetColors.neonGreen.copy(alpha = .28f), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(contact.displayName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                Text("@${contact.elysiumId}", color = MeetColors.cyberCyan, fontSize = 12.sp)
                Text("${contact.matchedMedium.name.lowercase()} · ${contact.aliasProofState.name.lowercase()}", color = MeetColors.textSecondary, fontSize = 9.sp)
            }
            Icon(Icons.Outlined.DoneAll, "Encontrado", tint = MeetColors.neonGreen)
        }
        Button(onRequest, Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Outlined.ChatBubbleOutline, null)
            Text("Enviar solicitud de conversación", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NotFoundCard(onInvite: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(18.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.PersonAdd, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(40.dp))
        Text("No encontramos una cuenta visible", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Puede que no use Elysium o haya desactivado esa búsqueda.", color = MeetColors.textSecondary, fontSize = 11.sp)
        OutlinedButton(onInvite, Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Outlined.Share, null)
            Text("Compartir descarga oficial de GitHub", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ContactsPane(contacts: List<ElysiumContact>, onOpen:(ElysiumContact)->Unit, onBlock: (ElysiumContact) -> Unit, onInvite: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedButton(onInvite, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Text("Invitar a Elysium", Modifier.padding(start = 6.dp)) } }
        if (contacts.isEmpty()) item { EmptyPanel("No hay contactos todavía. Busca a alguien o comparte el enlace oficial.") }
        else items(contacts, key = { it.principalId }) { contact ->
            Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(contact.displayName)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(contact.elysiumId?.let { "@$it · ${contact.relationshipState.lowercase()}" } ?: contact.relationshipState.lowercase(), color = MeetColors.textSecondary, fontSize = 11.sp)
                }
                if(!contact.isBlocked) IconButton({ onOpen(contact) }) { Icon(Icons.Outlined.Chat,"Abrir conversación",tint=MeetColors.cyberCyan) }
                if (!contact.isBlocked) IconButton({ onBlock(contact) }) { Icon(Icons.Outlined.Block, "Bloquear", tint = MeetColors.warning) }
            }
        }
    }
}

@Composable
private fun CallsPane(conversations: List<ConversationSummary>, onOpen: (ConversationSummary) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HonestBanner("Las llamadas usan infraestructura Elysium. Si el servidor de voz no está configurado, se bloquean sin abrir el marcador externo.") }
        item { SectionTitle("Iniciar una llamada", "elige un chat") }
        if (conversations.isEmpty()) item { EmptyPanel("Crea una conversación y espera aceptación antes de llamar.") }
        else items(conversations, key = { it.id }) { row -> ConversationRow(row) { onOpen(row) } }
    }
}

@Composable
private fun MeshPane(privacy: CommunicationPrivacySettings, onSave: (CommunicationPrivacySettings) -> Unit,
    status:String, peers:List<NearbyPeer>, invitations:List<NearbyInvitation>,onStart:()->Unit,onStop:()->Unit,
    onConnect:(String)->Unit,onAnswer:(String,Boolean)->Unit, onNativeMesh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f), RoundedCornerShape(20.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .3f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                Icon(Icons.Outlined.WifiTethering, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(42.dp))
                Text("Vanguard Mesh", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Conexión cercana mediante Nearby. Participación voluntaria.", color = MeetColors.textSecondary, fontSize = 12.sp)
            }
        }
        item {
            Text(status,color=MeetColors.cyberCyan)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onStart,Modifier.weight(1f).messageAction("nearby.start", "Buscar teléfonos", action = onStart)) { Text("Buscar teléfonos") }
                OutlinedButton(onStop,Modifier.weight(1f).messageAction("nearby.stop", "Desconectar", action = onStop)) { Text("Desconectar") }
            }
            Text("Ambos teléfonos deben aceptar el mismo código. Solo se intercambian mensajes de contactos autorizados con claves sincronizadas en las últimas 24 horas.",color=MeetColors.textSecondary,fontSize=11.sp)
        }
        items(invitations,key={ "invite:"+it.id }) { invitation ->
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("${invitation.name} · código ${invitation.code}",color=Color.White)
                Text("Comprueba que el otro teléfono muestra el mismo código antes de aceptar.",color=MeetColors.warning,fontSize=11.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({ onAnswer(invitation.id,true) }) { Text("Código coincide") }
                    OutlinedButton({ onAnswer(invitation.id,false) }) { Text("Rechazar") }
                }
            }
        }
        items(peers,key={ "peer:"+it.id }) { peer ->
            OutlinedButton({ onConnect(peer.id) },Modifier.fillMaxWidth()) { Text("Conectar con ${peer.name}") }
        }
        item { ToggleRow("Visible para solicitudes cercanas", "No anuncia correo ni teléfono.", privacy.meshDiscoverability != "OFF") { onSave(privacy.copy(meshDiscoverability = if (it) "NEARBY_REQUESTS" else "OFF")) } }
        item { ToggleRow("Ayudar como relevo cifrado", "El relevo no puede leer el contenido.", privacy.relayParticipation != "OFF") { onSave(privacy.copy(relayParticipation = if (it) "CONTACTS_ONLY" else "OFF")) } }
        item { ToggleRow("Relevar solo mientras carga", "Reduce el impacto en batería.", privacy.relayOnlyWhileCharging) { onSave(privacy.copy(relayOnlyWhileCharging = it)) } }
        item { HonestBanner("El enlace directo usa radios del teléfono. La entrega solo se confirma con un acuse firmado; voz y retransmisión por varios saltos siguen pendientes.") }
        item { OutlinedButton(onNativeMesh, Modifier.fillMaxWidth()) { Text("Abrir Mesh nativo · BLE / Wi-Fi / LAN") } }
        item { Text("Capas nativas disponibles para descubrimiento; transferencia bloqueada hasta revisar criptografía\n\n• BLE: controles pequeños\n• Wi-Fi Aware/Direct y LAN: sockets acotados\n• Room: custodia, replay, chunks y expiración\n• Pruebas físicas y revisión E2EE pendientes", color = MeetColors.textSecondary, fontSize = 12.sp) }
        item { Text("Capas previstas\n\n• BLE: descubrimiento y señalización\n• Wi‑Fi Aware/Direct: datos y voz local\n• Custodia cifrada con TTL y límite de saltos\n• Reconciliación al recuperar Internet", color = MeetColors.textSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun SettingsPane(
    identity: ElysiumIdentityProfile?,
    privacy: CommunicationPrivacySettings,
    onSaveIdentity: (String, String, String, String?) -> Unit,
    onSavePrivacy: (CommunicationPrivacySettings) -> Unit,
    onBlocked: () -> Unit,
) {
    var id by remember(identity?.principalId, identity?.elysiumId) { mutableStateOf(identity?.elysiumId.orEmpty()) }
    var name by remember(identity?.principalId, identity?.displayName) { mutableStateOf(identity?.displayName.orEmpty()) }
    var about by remember(identity?.principalId, identity?.about) { mutableStateOf(identity?.about.orEmpty()) }
    var phone by remember(identity?.principalId, identity?.phone) { mutableStateOf(identity?.phone.orEmpty()) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Identidad Elysium", identity?.identityState?.replace('_', ' ') ?: "cargando") }
        item { OutlinedTextField(id, { id = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("ID Elysium") }, leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, prefix = { Text("@") }, singleLine = true) }
        item { OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nombre visible") }, singleLine = true) }
        item { OutlinedTextField(about, { about = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("Acerca de ti") }, maxLines = 3) }
        item { OutlinedTextField(phone, { phone = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Teléfono opcional") }, leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) }, supportingText = { Text("Solo búsqueda exacta si la habilitas. Sin SMS.") }, singleLine = true) }
        item { Button({ onSaveIdentity(id, name, about, phone.takeIf(String::isNotBlank)) }, Modifier.fillMaxWidth()) { Text("Guardar identidad") } }
        item { SectionTitle("Quién puede encontrarte", "tú decides") }
        item { ChoiceRow("Por ID Elysium", privacy.findByElysiumId) { onSavePrivacy(privacy.copy(findByElysiumId = cycle(it, listOf("EVERYONE", "NOBODY")))) } }
        item { ChoiceRow("Por correo", privacy.findByEmail) { onSavePrivacy(privacy.copy(findByEmail = cycle(it, visibilityChoices))) } }
        item { ChoiceRow("Por teléfono", privacy.findByPhone) { onSavePrivacy(privacy.copy(findByPhone = cycle(it, visibilityChoices))) } }
        item { SectionTitle("Presencia y conversaciones", "controles") }
        item { ChoiceRow("Última vez", privacy.lastActiveVisibility) { onSavePrivacy(privacy.copy(lastActiveVisibility = cycle(it, profileChoices))) } }
        item { ChoiceRow("Quién puede llamarte", privacy.callPermission) { onSavePrivacy(privacy.copy(callPermission = cycle(it, profileChoices))) } }
        item { ToggleRow("Confirmaciones de lectura", "Controla el visto.", privacy.readReceiptsEnabled) { onSavePrivacy(privacy.copy(readReceiptsEnabled = it)) } }
        item { ToggleRow("Indicador escribiendo", "Controla si otros ven cuando redactas.", privacy.typingIndicatorsEnabled) { onSavePrivacy(privacy.copy(typingIndicatorsEnabled = it)) } }
        item { OutlinedButton(onBlocked, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Block, null); Text("Administrar bloqueados", Modifier.padding(start = 8.dp)) } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private val visibilityChoices = listOf("EVERYONE", "CONTACTS", "NOBODY")
private val profileChoices = listOf("EVERYONE", "CONTACTS", "CONTACTS_EXCEPT", "NOBODY")
private fun cycle(current: String, values: List<String>): String {
    val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
    return values[(index + 1) % values.size]
}

@Composable
private fun BlockedPane(blocked: List<BlockedContact>, onUnblock: (BlockedContact) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Los bloqueados no pueden encontrarte, solicitar conversación ni llamarte.", color = MeetColors.textSecondary, fontSize = 12.sp) }
        if (blocked.isEmpty()) item { EmptyPanel("No has bloqueado a nadie.") }
        else items(blocked, key = { it.principalId }) { contact ->
            Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = MeetColors.error)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text(contact.syncState.lowercase(), color = MeetColors.textSecondary, fontSize = 10.sp) }
                OutlinedButton({ onUnblock(contact) }) { Text("Desbloquear") }
            }
        }
    }
}

@Composable
private fun ConversationBody(
    conversation: ConversationSummary,
    messages: List<DecryptedMessage>,
    voiceNoteState: VoiceNoteRecordingState,
    onSend: (String, String?, () -> Unit) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: (String?) -> Unit,
    onCancelVoice: () -> Unit,
    onImage: () -> Unit,
) {
    var text by rememberSaveable(conversation.id) { mutableStateOf("") }
    var search by rememberSaveable(conversation.id) { mutableStateOf("") }
    var replyToEventId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    val playback=remember(conversation.id) { CommunicationVoicePlayback() }
    DisposableEffect(playback) { onDispose { playback.close() } }
    val visibleMessages = remember(messages, search) {
        if (search.isBlank()) messages else messages.filter { it.body.contains(search.trim(), ignoreCase = true) }
    }
    val recordingThisConversation = voiceNoteState is VoiceNoteRecordingState.Recording &&
        voiceNoteState.conversationId == conversation.id
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
            Text(if ((conversation.participantCount ?: 0) < 2) "Esperando participante autorizado · envío bloqueado" else "Mensajes cifrados · consulta la confirmación de entrega en cada mensaje", color = MeetColors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(160) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).agentTextInput(AgentUiControlId("messages.search.${conversation.id}"), "Buscar en esta conversación", AgentTextFieldRole.SEARCH, readValue={search}, writeValue={search=it.take(160)}),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Buscar en esta conversación") },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            items(visibleMessages, key = { it.id }) { message ->
                MessageBubble(message,playback::play) { replyToEventId = message.id }
            }
            if (messages.isEmpty()) item { EmptyPanel("Escribe cuando la otra persona esté autorizada.") }
            else if (visibleMessages.isEmpty()) item { EmptyPanel("No hay mensajes que coincidan con la búsqueda.") }
        }
        replyToEventId?.let { replyId ->
            val replied = messages.firstOrNull { it.id == replyId }
            Row(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Respondiendo a: ${replied?.body?.take(80) ?: "mensaje"}", color = MeetColors.cyberCyan, fontSize = 10.sp, modifier = Modifier.weight(1f))
                IconButton({ replyToEventId = null }) { Icon(Icons.Outlined.Close, "Cancelar respuesta") }
            }
        }
        Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Press-and-hold mic button — ALWAYS visible so pointerInput gesture survives recomposition
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (recordingThisConversation) Color(0xFFFF1744) else MeetColors.backgroundDark)
                    .border(1.dp, if (recordingThisConversation) Color(0xFFFF1744) else MeetColors.neonGreen, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    onStartVoice()
                                    val released = tryAwaitRelease()
                                    if (released) {
                                        onStopVoice(replyToEventId)
                                        replyToEventId = null
                                    } else {
                                        onCancelVoice()
                                    }
                                } else {
                                    onStartVoice() // triggers permission launcher
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (recordingThisConversation) Icons.Outlined.StopCircle else Icons.Outlined.Mic,
                    contentDescription = if (recordingThisConversation) "Soltar para enviar" else "Mantener para grabar",
                    tint = if (recordingThisConversation) Color.White else MeetColors.neonGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (recordingThisConversation) {
                Text("🔴 Grabando…", color = MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                IconButton(onCancelVoice, Modifier.messageAction("discard_voice", "Descartar nota", action = onCancelVoice)) {
                    Icon(Icons.Outlined.Delete, "Descartar nota", tint = MeetColors.warning)
                }
            }
            OutlinedTextField(text, { text = it.take(4000) }, Modifier.weight(1f).agentTextInput(
                AgentUiControlId("messages.compose.${conversation.id}"), "Mensaje", AgentTextFieldRole.MESSAGE,
                readValue = { text }, writeValue = { text = it.take(4000) }, sensitivity = AgentUiSensitivity.PERSONAL),
                placeholder = { Text("Escribe un mensaje") }, maxLines = 4)
            val send = {
                onSend(text, replyToEventId) {
                    text = ""
                    replyToEventId = null
                }
            }
            val canSend = text.isNotBlank() && !recordingThisConversation && conversation.requestState == MessageRequestState.ACCEPTED && (conversation.participantCount ?: 0) >= 2
            IconButton(onImage, enabled = conversation.requestState == MessageRequestState.ACCEPTED && !recordingThisConversation,
                modifier = Modifier.messageAction("image.${conversation.id}", "Adjuntar imagen", enabled = conversation.requestState == MessageRequestState.ACCEPTED && !recordingThisConversation, action = onImage)) {
                Icon(Icons.Outlined.AddPhotoAlternate, "Adjuntar imagen", tint = MeetColors.cyberCyan)
            }
            IconButton(send, modifier = Modifier.messageAction("send.${conversation.id}", "Enviar", enabled = canSend, action = send), enabled = canSend) { Icon(Icons.Outlined.Send, "Enviar", tint = MeetColors.cyberCyan) }
        }
    }
}

@Composable
private fun MessageBubble(message: DecryptedMessage, onPlayVoice:(String)->Unit, onReply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(.82f).background(if (message.isMine) MeetColors.electricBlue.copy(alpha = .28f) else MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(12.dp)) {
            message.replyToEventId?.let { Text("↪ Respuesta", color = MeetColors.cyberCyan, fontSize = 9.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.eventType in setOf("VOICE_NOTE","AUDIO") && message.localMediaPath != null) {
                    IconButton({ onPlayVoice(message.localMediaPath) }) {
                        Icon(Icons.Outlined.PlayCircle, "Reproducir nota de voz", tint = MeetColors.neonGreen)
                    }
                }
                Text(message.body, color = if (message.decryptionFailed) MeetColors.warning else Color.White, modifier = Modifier.weight(1f))
                IconButton(onReply) { Icon(Icons.Outlined.Reply, "Responder", tint = MeetColors.cyberCyan) }
            }
            if(message.eventType == "IMAGE" && message.localMediaPath != null && !message.decryptionFailed) {
                coil.compose.AsyncImage(model = java.io.File(message.localMediaPath), contentDescription = "Imagen adjunta", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
            }
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAtEpochMs)) + " · " + when(message.deliveryState) {
                "SERVER_ACK" -> "Confirmado por servidor"
                "NEARBY_ACK" -> "Recibido por otro teléfono"
                "NEARBY_RECEIVED" -> "Recibido por enlace cercano"
                "REMOTE_UNREADABLE" -> "Sin clave válida en este dispositivo"
                "PENDING_REMOTE" -> "Pendiente de envío"
                else -> "Solo en este dispositivo"
            }, color = MeetColors.textSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
        }
    }
}

private class CommunicationVoicePlayback {
    private var current:android.media.MediaPlayer?=null
    fun close() { runCatching { current?.release() };current=null }
    fun play(path:String) {
        close()
        runCatching {
            val player=android.media.MediaPlayer();current=player
            player.setDataSource(path)
            player.setOnCompletionListener { if(current===it) current=null;it.release() }
            player.setOnErrorListener { failed,_,_ -> if(current===failed) current=null;failed.release();true }
            player.setOnPreparedListener { if(current===it) it.start() }
            player.prepareAsync()
        }.onFailure { close() }
    }
}

@Composable
private fun ConversationRow(row: ConversationSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().messageAction("conversation.${row.id}", row.title, aliases = setOf("abrir conversación con ${row.title}"), action = onClick).clickable(onClick = onClick).background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .18f), RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ContactAvatar(row.title)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(row.serviceVertical?.let { "Servicio · ${it.replace('_', ' ')}" } ?: "${row.requestState.name.lowercase()} · conversación privada", color = MeetColors.textSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan)
    }
}

@Composable private fun ContactAvatar(name: String) {
    Box(Modifier.size(42.dp).background(MeetColors.cyberCyan.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "E", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
    }
}

@Composable private fun PrimaryAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.height(48.dp).messageAction("primary.$label", label, action = onClick)) { Icon(icon, null); Text(label, Modifier.padding(start = 6.dp), maxLines = 1, fontSize = 12.sp) }
}

@Composable private fun CompactAction(label: String, icon: ImageVector, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.messageAction("pane.$label", label, action = onClick).clickable(onClick = onClick).background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MeetColors.cyberCyan)
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        Text(detail, color = MeetColors.textSecondary, fontSize = 8.sp, maxLines = 1)
    }
}

private fun Modifier.messageAction(id: String, label: String, aliases: Set<String> = emptySet(), enabled: Boolean = true, action: () -> Unit): Modifier =
    agentAction(AgentUiControlId("messages.$id"), label, aliases = aliases, route = "messages", enabled = enabled, onActivate = action)

@Composable private fun PairingMethod(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(30.dp))
        Column(Modifier.padding(start = 12.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp) }
    }
}

@Composable private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp) }
        Switch(checked, onChange)
    }
}

@Composable private fun ChoiceRow(title: String, value: String, onCycle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCycle(value) }.background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value.replace('_', ' ').lowercase(), color = MeetColors.cyberCyan, fontSize = 11.sp)
    }
}

@Composable private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp)
    }
}

@Composable private fun EmptyPanel(message: String) {
    Box(Modifier.fillMaxWidth().background(MeetColors.cardBackground.copy(alpha = .72f), RoundedCornerShape(16.dp)).padding(22.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MeetColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable private fun HonestBanner(message: String) {
    Row(Modifier.fillMaxWidth().background(MeetColors.warning.copy(alpha = .12f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
