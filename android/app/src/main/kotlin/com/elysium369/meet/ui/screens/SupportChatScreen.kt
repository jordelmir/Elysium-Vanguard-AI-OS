package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.core.ai.ChatMessage
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.SupportChatViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.EliteIconButton
import com.elysium369.meet.ui.components.EliteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    onBack: () -> Unit,
    viewModel: SupportChatViewModel = hiltViewModel(),
    vehicleInfo: String = "Vehículo Genérico (OBD2)"
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val qosMetrics by viewModel.qosMetrics.collectAsState()
    val oscilloscopeBuffer by viewModel.oscilloscopeBuffer.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var isTelemetryExpanded by remember { mutableStateOf(true) }
    var selectedPid by remember { mutableStateOf("0142") } // Default: Battery Voltage

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Copiloto IA & Soporte",
                onBackClick = onBack,
                actions = {
                    EliteTextButton(
                        text = "Limpiar",
                        onClick = { viewModel.clearChat() },
                        color = MeetColors.error
                    )
                },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
        ) {
            // Live Telemetry Copilot Panel
            TelemetryCopilotPanel(
                connectionState = connectionState,
                liveData = liveData,
                qosMetrics = qosMetrics,
                oscilloscopeBuffer = oscilloscopeBuffer,
                selectedPid = selectedPid,
                onPidSelected = { selectedPid = it },
                isExpanded = isTelemetryExpanded,
                onToggleExpand = { isTelemetryExpanded = !isTelemetryExpanded },
                onAnalyzeWaveform = { pid ->
                    viewModel.analyzeWaveform(pid, vehicleInfo)
                }
            )

            // Chat Messages Area
            Box(modifier = Modifier.weight(1f)) {
                EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .eliteScrollbar(listState)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                WelcomeAssistantCard(vehicleInfo = vehicleInfo)
                            }
                        }

                        items(messages) { message ->
                            ChatBubble(message)
                        }

                        if (isLoading) {
                            item {
                                CyberConsoleTypingIndicator()
                            }
                        }
                    }
                }
            }

            // Suggestion Chips Row
            SuggestionChipsRow(
                connectionState = connectionState,
                liveData = liveData,
                onChipClicked = { promptText ->
                    viewModel.sendMessage(promptText, vehicleInfo)
                }
            )

            // Input Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MeetColors.backgroundDark)
                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Pregúntame sobre cualquier valor o DTC...", color = MeetColors.textSecondary) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4
                    )
                    
                    EliteIconButton(
                        icon = {
                            AnimatedNeonIcon(
                                Icons.Default.Send, 
                                contentDescription = "Enviar", 
                                tint = if (!isLoading && inputText.isNotBlank()) MeetColors.neonGreen else MeetColors.textSecondary
                            )
                        },
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText, vehicleInfo)
                                inputText = ""
                            }
                        },
                        isEnabled = !isLoading && inputText.isNotBlank()
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryCopilotPanel(
    connectionState: ObdState,
    liveData: Map<String, Float>,
    qosMetrics: com.elysium369.meet.core.obd.QosMetrics,
    oscilloscopeBuffer: Map<String, List<Pair<Long, Float>>>,
    selectedPid: String,
    onPidSelected: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAnalyzeWaveform: (String) -> Unit
) {
    val isConnected = connectionState == ObdState.CONNECTED
    
    // Status color
    val statusColor = when (connectionState) {
        ObdState.CONNECTED -> MeetColors.neonGreen
        ObdState.CONNECTING, ObdState.NEGOTIATING -> Color(0xFFFFB300)
        else -> MeetColors.error
    }

    val statusText = when (connectionState) {
        ObdState.CONNECTED -> "CONECTADO"
        ObdState.CONNECTING -> "CONECTANDO..."
        ObdState.NEGOTIATING -> "NEGOCIANDO..."
        else -> "DESCONECTADO"
    }

    // Glow Animation for connected status dot
    val infiniteTransition = rememberInfiniteTransition(label = "status_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    EliteCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor.copy(alpha = glowAlpha))
                            .border(1.dp, statusColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COPILOTO DE TELEMETRÍA AI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    AnimatedNeonIcon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expandir/Colapsar",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Selected PIDs Tab Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("0142", "BATERÍA", "V"),
                            Triple("010C", "RPM", "rpm"),
                            Triple("0105", "ECT REFR.", "°C"),
                            Triple("0111", "TPS ACEL.", "%")
                        ).forEach { (pid, label, unit) ->
                            val isSelected = selectedPid == pid
                            val rawVal = liveData[pid]
                            val displayVal = if (rawVal != null) "%.1f %s".format(rawVal, unit) else "N/A"

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else MeetColors.backgroundDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) MeetColors.electricBlue else MeetColors.borderSubtle,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onPidSelected(pid) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, color = if (isSelected) MeetColors.electricBlue else MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(displayVal, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-Time Oscilloscope Canvas Draw
                    val dataPoints = oscilloscopeBuffer[selectedPid] ?: emptyList()
                    OscilloscopeDisplay(isConnected = isConnected, dataPoints = dataPoints)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom info and analyze action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Frecuencia de Muestreo: %.1f Hz".format(qosMetrics.cmdsPerSecond),
                                color = MeetColors.textSecondary,
                                fontSize = 9.sp
                            )
                            Text(
                                "Latencia de Enlace: %d ms".format(qosMetrics.latencyMs),
                                color = MeetColors.textSecondary,
                                fontSize = 9.sp
                            )
                        }

                        Button(
                            onClick = { onAnalyzeWaveform(selectedPid) },
                            enabled = isConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (isConnected) MeetColors.neonGreen else MeetColors.textSecondary.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                "ANALIZAR ONDA CON IA 🔍",
                                color = if (isConnected) MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OscilloscopeDisplay(
    isConnected: Boolean,
    dataPoints: List<Pair<Long, Float>>
) {
    val phaseTransition = rememberInfiniteTransition(label = "sim_oscilloscope")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF03070C))
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw digital reticle grids
            val cols = 12
            val rows = 6
            for (i in 1 until cols) {
                val x = (width / cols) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }
            for (j in 1 until rows) {
                val y = (height / rows) * j
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // Draw center axis lines
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 1.5f
            )

            // Plot Waveform
            val path = Path()
            
            if (isConnected && dataPoints.size > 1) {
                // Plot real OBD wave
                val values = dataPoints.map { it.second }
                val min = values.minOrNull() ?: 0f
                val max = values.maxOrNull() ?: 1f
                val range = (max - min).coerceAtLeast(0.01f)

                dataPoints.forEachIndexed { index, pair ->
                    val x = index.toFloat() * (width / (dataPoints.size - 1))
                    val pct = (pair.second - min) / range
                    val y = height - (pct * (height * 0.8f) + height * 0.1f)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                drawPath(
                    path = path,
                    color = MeetColors.neonGreen,
                    style = Stroke(width = 2.dp.toPx(), miter = 1f)
                )
            } else {
                // Draw simulated sinus laser scan line
                val points = 180
                for (x in 0..points) {
                    val xPos = x.toFloat() * (width / points)
                    val angle = (x.toFloat() / points) * 4f * Math.PI.toFloat() + phase
                    val yPos = height / 2f + Math.sin(angle.toDouble()).toFloat() * 20f

                    if (x == 0) {
                        path.moveTo(xPos, yPos)
                    } else {
                        path.lineTo(xPos, yPos)
                    }
                }
                drawPath(
                    path = path,
                    color = MeetColors.electricBlue.copy(alpha = 0.7f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun WelcomeAssistantCard(vehicleInfo: String) {
    EliteCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🤖",
                fontSize = 42.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Elysium AI Copilot",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Soporte automotriz inteligente integrado con telemetría OBD-II en tiempo real.",
                color = MeetColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MeetColors.borderSubtle)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VEHÍCULO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(vehicleInfo, color = MeetColors.electricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MOTOR", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("Gemini 1.5 L1", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SuggestionChipsRow(
    connectionState: ObdState,
    liveData: Map<String, Float>,
    onChipClicked: (String) -> Unit
) {
    val chips = remember(connectionState, liveData) {
        val list = mutableListOf<Pair<String, String>>()
        val batteryVolt = liveData["0142"] ?: 12.2f
        val rpmVal = liveData["010C"] ?: 0f
        
        list.add(Pair("⚡ Batería", "Analiza el estado de salud de mi batería y alternador considerando la lectura de voltaje OBD-II actual de %.2f V.".format(batteryVolt)))
        
        if (rpmVal > 0f) {
            list.add(Pair("⚙️ Ralentí", "Mi motor está encendido a %.0f RPM. Analiza si este ralentí es estable o si indica fugas de vacío u otras anomalías.".format(rpmVal)))
        } else {
            list.add(Pair("⚙️ Ralentí", "Analiza las causas de ralentí inestable en mi vehículo y cómo diagnosticarlo con telemetría activa."))
        }
        
        list.add(Pair("🌡️ ECT Termostato", "Analizar el comportamiento del sensor ECT de temperatura y cómo validar si el termostato abre a tiempo."))
        list.add(Pair("🛡️ DTCs de Red", "Explica cómo diagnosticar fallas de comunicación U0100 en el bus CAN y qué pines probar."))
        
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (label, promptText) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C1322))
                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onChipClicked(promptText) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = MeetColors.electricBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CyberConsoleTypingIndicator() {
    var stepIndex by remember { mutableIntStateOf(0) }
    
    val logs = listOf(
        "[SYS] Conectando enlace de datos seguros...",
        "[OBD] Recuperando mapeo de PIDs activos en tiempo real...",
        "[COR] Correlacionando DTCs y señales analógicas...",
        "[NET] Leyendo integridad eléctrica del bus CAN...",
        "[OUT] Compilando veredicto médico y plan de acción..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            if (stepIndex < logs.size - 1) {
                stepIndex++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF040A12))
            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MeetColors.neonGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ELYSIVM CO-PROCESSOR ACTIVE",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Show all active steps up to current stepIndex
        for (i in 0..stepIndex) {
            val isCurrent = i == stepIndex
            Text(
                text = if (isCurrent) "${logs[i]} ▮" else "${logs[i]} ✓",
                color = if (isCurrent) MeetColors.electricBlue else Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    
    val bubbleBg = if (isUser) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF003D5B), Color(0xFF0D253F))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF071424), Color(0xFF030A14))
        )
    }

    val glowColor = if (isUser) Color(0xFF00E5FF) else Color(0xFF39FF66)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 16.dp
                    )
                )
                .background(bubbleBg)
                .border(
                    1.dp,
                    glowColor.copy(alpha = if (isUser) 0.6f else 0.4f),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                text = message.content,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isUser) "TÚ" else "🤖 CO-PILOTO IA ELYSIUM",
                color = if (isUser) Color(0xFF00E5FF) else Color(0xFF39FF66),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

