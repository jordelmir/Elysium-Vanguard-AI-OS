package com.elysium369.meet.linksphere.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.linksphere.domain.PlaybackPreference
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Pantalla de Ajustes de Elysium LinkSphere.
 * Configuración de reproducción, preferencias de datos,
 * privacidad y términos del servicio social.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSphereSettingsScreen(
    viewModel: LinkSphereViewModel,
    onNavigateBack: () -> Unit,
) {
    val currentPref by viewModel.playbackPreference.collectAsState()

    Scaffold(
        containerColor = Color(0xFF040A12),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ajustes de LinkSphere", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                        Text("Preferencias de Reproducción y Datos", fontSize = 10.sp, color = MeetColors.cyberCyan)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07121F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección: Preferencia de Reproducción
            Text(
                "PREFERENCIAS DE REPRODUCCIÓN",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )

            Surface(
                color = Color(0xFF091626),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaybackPreference.values().forEach { pref ->
                        val isSelected = currentPref == pref
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pref.label,
                                    color = if (isSelected) Color.White else MeetColors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setPlaybackPreference(pref) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MeetColors.neonGreen,
                                    unselectedColor = Color(0x55FFFFFF)
                                )
                            )
                        }
                    }
                }
            }

            // Sección: Transparencia y Alojamiento
            Text(
                "PRINCIPIO ARQUITECTÓNICO DE ELYSIUM",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )

            Surface(
                color = Color(0xFF06101D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🛡️ Red Social de Enlaces sin Descarga Propietaria",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Elysium Vanguard OS respeta la autoría y la infraestructura de origen. Los medios audiovisuales (videos, directos, música) permanecen alojados en sus plataformas oficiales (YouTube, Spotify, Vimeo, Twitch, etc.). Elysium gestiona únicamente la capa social, las recomendaciones y los metadatos permitidos, asegurando mínimo consumo de datos y cero costos indebidos de hosting.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
