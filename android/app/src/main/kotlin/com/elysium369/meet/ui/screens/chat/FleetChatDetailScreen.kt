package com.elysium369.meet.ui.screens.chat

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.ChatMessageEntity
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.theme.MeetColors
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetChatDetailScreen(
    viewModel: FleetChatViewModel,
    onBack: () -> Unit,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val partner by viewModel.selectedPartner.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isRecording by viewModel.isRecording.collectAsState()
    val isBlocked by viewModel.isPartnerBlocked.collectAsState()

    val playingMessageId by viewModel.playingMessageId.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val audioPositionText by viewModel.audioPositionText.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedImageForPreview by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val activeVoiceCall by viewModel.activeVoiceCall.collectAsState()
    val activeVideoCall by viewModel.activeVideoCall.collectAsState()
    val meshRoute by com.elysium369.meet.core.mesh.ElysiumMeshGovernance.activeRoute.collectAsState()
    val remoteVideoFrame by com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.remoteVideoFrame.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("meet_attachment_", "_file", context.cacheDir)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.sendFileAttachment(tempFile, "FILE")
            } catch (e: Exception) {
                android.util.Log.e("FleetChatDetail", "Failed to resolve file attachment", e)
            }
        }
    }

    if (partner == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MeetColors.electricBlue)
        }
        return
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = partner?.userId ?: "Chat",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { viewModel.startVoiceCall() }) {
                        AnimatedNeonIcon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Llamada de voz Mesh",
                            tint = MeetColors.neonGreen
                        )
                    }
                    IconButton(onClick = { viewModel.startVideoCall() }) {
                        AnimatedNeonIcon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Videollamada Mesh",
                            tint = MeetColors.cyberCyan
                        )
                    }
                    IconButton(onClick = { showReportDialog = true }) {
                        AnimatedNeonIcon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Reportar usuario",
                            tint = MeetColors.warning
                        )
                    }
                    IconButton(onClick = { viewModel.toggleBlockActivePartner() }) {
                        AnimatedNeonIcon(
                            imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                            contentDescription = if (isBlocked) "Desbloquear" else "Bloquear",
                            tint = if (isBlocked) MeetColors.electricBlue else MeetColors.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF070B14),
                            Color(0xFF091222),
                            Color(0xFF05080E)
                        )
                    )
                )
        ) {
            // ── Mesh Route Status Pill (WhatsApp / Elysium Telemetry) ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0A1424),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF162A45))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(meshRoute.badgeIcon, fontSize = 12.sp)
                        Text(
                            text = "Canal: ${meshRoute.displayName}",
                            color = Color(0xFF00D9FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "E2EE Simulado · Sin Nube",
                        color = MeetColors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // ── Banner de Llamada de Voz Mesh en Vivo (si está activa) ──
            activeVoiceCall?.let { call ->
                val min = call.durationSeconds / 60
                val sec = call.durationSeconds % 60
                val durationStr = String.format("%02d:%02d", min, sec)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0A1828),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF39FF66)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF39FF66).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF39FF66), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📞", fontSize = 16.sp)
                            }
                            Column {
                                Text("LLAMADA MESH EN VIVO", color = Color(0xFF39FF66), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                Text("$durationStr · ${call.route.displayName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleCallMute() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (call.isMuted) Color(0xFFFF3366) else Color(0xFF14243B))
                            ) {
                                Icon(
                                    imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.endVoiceCall() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF1744))
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ── Visor de Videollamada Mesh en Vivo P2P (si está activa) ──
            activeVideoCall?.let { call ->
                val min = call.durationSeconds / 60
                val sec = call.durationSeconds % 60
                val durationStr = String.format("%02d:%02d", min, sec)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF060D1A),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00D9FF)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header del Stream
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00D9FF).copy(alpha = 0.2f))
                                        .border(1.dp, Color(0xFF00D9FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📹", fontSize = 15.sp)
                                }
                                Column {
                                    Text("VIDEOLLAMADA MESH EN VIVO", color = Color(0xFF00D9FF), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    Text("$durationStr · ${call.route.displayName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF39FF66).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF66))
                            ) {
                                Text(
                                    text = "P2P DIRECTO",
                                    color = Color(0xFF39FF66),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Remote Video Screen with HUD
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF0A1C30), Color(0xFF040A14))
                                    )
                                )
                                .border(1.dp, Color(0xFF1B3B60), RoundedCornerShape(14.dp))
                        ) {
                            // Central Video stream placeholder / cyber reticle o Video Físico Real
                            if (call.isVideoActive && remoteVideoFrame != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = remoteVideoFrame!!.asImageBitmap(),
                                    contentDescription = "Video Remoto en Vivo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else if (call.isVideoActive) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00D9FF).copy(alpha = 0.15f))
                                            .border(1.5.dp, Color(0xFF00D9FF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("👤", fontSize = 28.sp)
                                    }
                                    Text(
                                        text = partner?.userId ?: "Peer Remoto",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Stream H.264 P2P Activo (<25ms)",
                                        color = Color(0xFF39FF66),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                    Text("Cámara remota en pausa", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            // HUD Telemetry Overlay (Top Left)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${call.resolution} · ${call.bitrateKbps} kbps · ${call.latencyMs}ms",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            // Local Camera Picture-in-Picture (PiP) Window (Bottom End)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(width = 80.dp, height = 100.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0E1F35),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF39FF66))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🤳", fontSize = 18.sp)
                                        Text(
                                            text = if (call.isFrontCamera) "Frontal" else "Trasera",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mic Mute
                            IconButton(
                                onClick = { viewModel.toggleVideoCallMute() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (call.isMuted) Color(0xFFFF3366) else Color(0xFF142A45))
                            ) {
                                Icon(
                                    imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Silenciar audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Camera Toggle
                            IconButton(
                                onClick = { viewModel.toggleVideoCallCamera() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (!call.isVideoActive) Color(0xFFFF9900) else Color(0xFF142A45))
                            ) {
                                Icon(
                                    imageVector = if (call.isVideoActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Alternar cámara",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Flip Camera (Front / Back)
                            IconButton(
                                onClick = { viewModel.flipVideoCallCamera() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF142A45))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Girar cámara",
                                    tint = Color(0xFF00D9FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // End Call
                            IconButton(
                                onClick = { viewModel.endVideoCall() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF1744))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Finalizar videollamada",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                items(messages) { message ->
                    val isOwn = message.senderId == viewModel.currentUserId
                    MessageBubble(
                        message = message,
                        isOwn = isOwn,
                        playingMessageId = playingMessageId,
                        audioProgress = audioProgress,
                        audioPositionText = audioPositionText,
                        onPlayAudio = { viewModel.togglePlayVoice(message) },
                        onImageClick = { selectedImageForPreview = it },
                        onCallClick = { viewModel.startVoiceCall() },
                        navController = navController
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MeetColors.cardBackground,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Has bloqueado a este usuario o estás bloqueado",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (isRecording) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MeetColors.backgroundDeep)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.error)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grabando nota de voz...", color = Color.White, fontSize = 12.sp)
                            }
                            Text(
                                text = "Cancelar",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { viewModel.cancelRecordingVoice() }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.stopAndSendVoice() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MeetColors.electricBlue)
                        ) {
                            AnimatedNeonIcon(Icons.Default.Send, contentDescription = "Enviar Audio", tint = Color.Black)
                        }
                    } else {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Adjuntar archivo", tint = MeetColors.electricBlue)
                        }

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Escribe un mensaje...", color = MeetColors.textSecondary) },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MeetColors.backgroundDeep,
                                unfocusedContainerColor = MeetColors.backgroundDeep,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (textInput.trim().isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.sendTextMessage(textInput)
                                    textInput = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.electricBlue)
                            ) {
                                AnimatedNeonIcon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.Black)
                            }
                        } else {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.backgroundDark)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = { offset ->
                                                viewModel.startRecordingVoice()
                                                try {
                                                    tryAwaitRelease()
                                                } finally {
                                                    viewModel.stopAndSendVoice()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                AnimatedNeonIcon(Icons.Default.Mic, contentDescription = "Grabar", tint = MeetColors.electricBlue)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        val reportReasons = listOf(
            "SPAM" to "Spam o contenido no solicitado",
            "HARASSMENT" to "Acoso o intimidación",
            "INAPPROPRIATE_CONTENT" to "Contenido inapropiado",
            "FRAUD" to "Fraude o actividad sospechosa",
            "OTHER" to "Otro motivo"
        )
        var selectedReason by remember { mutableStateOf("SPAM") }
        var reportDescription by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Text("Reportar usuario", color = MeetColors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona el motivo del reporte. Tu reporte será revisado por nuestro equipo de moderación.",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp
                    )
                    reportReasons.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedReason == key) MeetColors.electricBlue.copy(alpha = 0.15f)
                                    else MeetColors.backgroundDeep
                                )
                                .clickable { selectedReason = key }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == key,
                                onClick = { selectedReason = key },
                                colors = RadioButtonDefaults.colors(selectedColor = MeetColors.electricBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = MeetColors.textPrimary, fontSize = 13.sp)
                        }
                    }
                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.textSecondary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportUser(selectedReason, reportDescription)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error)
                ) {
                    Text("REPORTAR", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("CANCELAR", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }

    selectedImageForPreview?.let { path ->
        AlertDialog(
            onDismissRequest = { selectedImageForPreview = null },
            confirmButton = {
                TextButton(onClick = { selectedImageForPreview = null }) {
                    Text("Cerrar", color = MeetColors.electricBlue)
                }
            },
            title = { Text("Visualizador de Medios Mesh", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF09101E))
                            .border(1.dp, Color(0xFF00F0FF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🖼️", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Fotografía de Diagnóstico / Flota", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Ruta: $path", color = MeetColors.textMuted, fontSize = 10.sp, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            Text("✓ Verificado por Canal Wi-Fi Mesh", color = Color(0xFF39FF66), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    isOwn: Boolean,
    playingMessageId: String?,
    audioProgress: Float,
    audioPositionText: String,
    onPlayAudio: () -> Unit,
    onImageClick: (String) -> Unit = {},
    onCallClick: () -> Unit = {},
    navController: NavController? = null
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) Color(0xFF0F223D) else Color(0xFF0B1424)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        EliteCard(
            modifier = Modifier.widthIn(max = 300.dp),
            glowColor = if (isOwn) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color.Transparent,
            backgroundColor = bubbleColor,
            borderColor = if (isOwn) Color(0xFF00F0FF).copy(alpha = 0.4f) else Color(0xFF1E3355).copy(alpha = 0.4f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.messageType) {
                    "IMAGE" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF070E1A))
                                .clickable { message.fileLocalPath?.let { onImageClick(it) } }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF0A1526), Color(0xFF060B12))
                                        )
                                    )
                                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📷", fontSize = 42.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = message.messageText ?: "Fotografía de Evidencia",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "⚡ Canal Wi-Fi Mesh P2P · Toca para abrir",
                                        color = Color(0xFF00F0FF),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    "CALL" -> {
                        val isVideo = message.messageText?.contains("Videollamada") == true
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isVideo) Color(0xFF00D9FF).copy(alpha = 0.2f) else Color(0xFF39FF66).copy(alpha = 0.2f))
                                        .border(1.dp, if (isVideo) Color(0xFF00D9FF) else Color(0xFF39FF66), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (isVideo) "📹" else "📞", fontSize = 16.sp)
                                }
                                Column {
                                    Text(
                                        text = if (isVideo) "Videollamada Mesh P2P" else "Llamada de voz Mesh",
                                        color = if (isVideo) Color(0xFF00D9FF) else Color(0xFF39FF66),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = message.messageText ?: "Finalizada",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(onClick = onCallClick, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                                    contentDescription = "Llamar",
                                    tint = if (isVideo) Color(0xFF00D9FF) else Color(0xFF39FF66),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    "AUDIO" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onPlayAudio) {
                                AnimatedNeonIcon(
                                    imageVector = if (playingMessageId == message.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir nota de voz",
                                    tint = Color(0xFF39FF66)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (playingMessageId == message.id) {
                                    LinearProgressIndicator(
                                        progress = { audioProgress },
                                        color = Color(0xFF39FF66),
                                        trackColor = MeetColors.backgroundDark,
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = audioPositionText,
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(12) { index ->
                                            val height = (index % 4 * 4) + 6
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(height.dp)
                                                    .background(Color(0xFF39FF66).copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🎙️ Nota de voz (${message.durationSeconds}s) · Wi-Fi Mesh",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    "DTC_ALERT" -> {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.Warning, contentDescription = null, tint = MeetColors.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Falla de Motor Activa", color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message.messageText ?: "",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            val text = message.messageText ?: ""
                            val dtcRegex = Regex("[A-Z][0-9]{4}")
                            val foundCodes = dtcRegex.findAll(text).map { it.value }.toList()
                            if (foundCodes.isNotEmpty() && navController != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                foundCodes.forEach { code ->
                                    EliteButton(
                                        text = "🤖 CONSULTAR IA: $code",
                                        onClick = { navController.navigate("ai/$code") },
                                        color = MeetColors.cyberCyan,
                                        textColor = Color.Black,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    "FILE" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonIcon(Icons.Default.AttachFile, contentDescription = null, tint = MeetColors.electricBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.messageText ?: "Archivo",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                    "CALL" -> {
                        val isVideo = message.messageText?.contains("Videollamada") == true
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isVideo) Color(0xFF00D9FF).copy(alpha = 0.2f) else Color(0xFF39FF66).copy(alpha = 0.2f))
                                    .border(1.dp, if (isVideo) Color(0xFF00D9FF) else Color(0xFF39FF66), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isVideo) "📹" else "📞", fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    text = if (isVideo) "Videollamada Mesh P2P" else "Llamada de voz Mesh",
                                    color = if (isVideo) Color(0xFF00D9FF) else Color(0xFF39FF66),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = message.messageText ?: "Llamada finalizada",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = message.messageText ?: "",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp
                    )
                    if (isOwn) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            "DELIVERED_MESH_WIFI" -> {
                                Text("⚡ Wi-Fi Mesh", color = Color(0xFF00F0FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(2.dp))
                                AnimatedNeonIcon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF00F0FF),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            "DELIVERED_MESH_BLE" -> {
                                Text("📡 BLE", color = Color(0xFF9D00FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(2.dp))
                                AnimatedNeonIcon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF9D00FF),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            "DELIVERED", "READ", "DELIVERED_CLOUD" -> {
                                AnimatedNeonIcon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF39FF66),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            else -> {
                                AnimatedNeonIcon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MeetColors.textSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
