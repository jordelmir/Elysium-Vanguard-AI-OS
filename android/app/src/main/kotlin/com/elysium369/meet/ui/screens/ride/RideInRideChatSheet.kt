package com.elysium369.meet.ui.screens.ride

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideInRideChatSheet(
    rideRequestId: String,
    myId: String,
    myName: String,
    myRole: String,
    isDriver: Boolean,
    chatMessages: List<RideChatMessageEntity>,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendPreset: (String) -> Unit,
    onSendVoiceNote: (File, Long) -> Unit,
    onSendImage: (ByteArray) -> Unit,
    playingAudioPath: String?,
    onPlayAudio: (String) -> Unit,
    isRecordingAudio: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    onSendImage(bytes)
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val driverPresets = listOf(
        "Llegando en 2 min",
        "Ya estoy afuera",
        "Estoy en la esquina",
        "Hay un poco de tráfico",
        "Ok, entendido"
    )

    val passengerPresets = listOf(
        "Ya salgo",
        "Estoy bajando",
        "Voy saliendo",
        "Camisa roja / esperándote",
        "Ok, gracias"
    )

    val activePresets = if (isDriver) driverPresets else passengerPresets

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070C15),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Surface(
                color = Color(0xFF0D1826),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MeetColors.cyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MeetColors.cyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isDriver) "CHAT CON PASAJERO" else "CHAT CON CHOFER",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(8.dp)
                                )
                                Text(
                                    text = "Canal Seguro Directo (Nube + Mesh)",
                                    color = MeetColors.neonGreen,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cardBackground)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MeetColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (chatMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MeetColors.textMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Conversación cifrada en tiempo real",
                                    color = MeetColors.textMuted,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = "Usa respuestas rápidas o envía notas de voz sin salir del viaje.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                items(chatMessages) { message ->
                    val isMe = message.senderId == myId || message.senderRole.equals(myRole, ignoreCase = true)
                    val alignment = if (isMe) Alignment.End else Alignment.Start

                    val bubbleBrush = if (isMe) {
                        Brush.linearGradient(listOf(Color(0xFF003D5B), Color(0xFF0D253F)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF0B1728), Color(0xFF07101C)))
                    }
                    val borderColor = if (isMe) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFF162A45)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalAlignment = alignment
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 285.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 3.dp,
                                        bottomEnd = if (isMe) 3.dp else 16.dp
                                    )
                                )
                                .background(bubbleBrush)
                                .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 3.dp,
                                        bottomEnd = if (isMe) 3.dp else 16.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                if (!isMe) {
                                    Text(
                                        text = message.senderName.takeIf { it.isNotBlank() } ?: if (message.senderRole == "DRIVER") "Chofer" else "Pasajero",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF00D9FF)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                when (message.messageType) {
                                    "TEXT", "PRESET" -> {
                                        Text(
                                            text = message.textContent ?: "",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                    "AUDIO" -> {
                                        val isPlaying = playingAudioPath == message.audioFilePath
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable {
                                                    message.audioFilePath?.let { onPlayAudio(it) }
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFF00E5FF), Color(0xFF39FF66))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Waveform bars simulation
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val barHeights = listOf(8, 14, 22, 12, 18, 24, 16, 20, 10, 15, 8)
                                                barHeights.forEachIndexed { idx, h ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.dp)
                                                            .height(h.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(
                                                                if (isPlaying && idx % 2 == 0) Color(0xFF39FF66) else Color(0xFF00E5FF).copy(alpha = 0.6f)
                                                            )
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${((message.audioDurationMs ?: 0L) / 1000).coerceAtLeast(1)}s",
                                                color = Color(0xFF39FF66),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    "IMAGE" -> {
                                        val imagePath = message.imageFilePath
                                        if (imagePath != null) {
                                            AsyncImage(
                                                model = File(imagePath),
                                                contentDescription = "Imagen de viaje",
                                                modifier = Modifier
                                                    .widthIn(max = 240.dp)
                                                    .heightIn(min = 120.dp, max = 220.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Text(
                                                "Descargando foto…",
                                                color = MeetColors.cyberCyan,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val timeString = remember(message.createdAt) {
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.createdAt))
                                    }
                                    Text(
                                        text = timeString,
                                        color = MeetColors.textMuted,
                                        fontSize = 9.sp,
                                    )
                                    Text(
                                        text = "• En vivo",
                                        color = MeetColors.neonGreen.copy(alpha = 0.8f),
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Preset Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1420))
            ) {
                items(activePresets) { preset ->
                    SuggestionChip(
                        onClick = { onSendPreset(preset) },
                        label = { Text(preset, color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF142436),
                        ),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            // Pulsating Audio Recording Strip
            if (isRecordingAudio) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF28070D),
                    border = BorderStroke(1.2.dp, Color(0xFFFF1744))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1744))
                        )
                        Text(
                            text = "🎙️ Grabando nota de voz en vivo... (Toca el micrófono para enviar)",
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                color = Color(0xFF0D1826),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF182A40))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Enviar foto",
                            tint = MeetColors.hotMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isRecordingAudio) {
                                onStopRecording()
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    onStartRecording()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingAudio) MeetColors.error else Color(0xFF182A40))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = if (isRecordingAudio) "Detener grabación" else "Grabar nota de voz",
                            tint = if (isRecordingAudio) Color.White else MeetColors.neonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isRecordingAudio) "Grabando audio…" else "Escribe un mensaje…",
                                color = MeetColors.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = MeetColors.cyberCyan,
                        )
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) MeetColors.cyberCyan else Color(0xFF182A40))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = if (inputText.isNotBlank()) Color.Black else MeetColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
