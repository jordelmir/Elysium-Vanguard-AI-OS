package com.elysium369.meet.ui.screens.services

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.screens.UniversalServicesScreen
import com.elysium369.meet.ui.theme.MeetColors

data class ServicesOfferDraft(val priceCrc: Long, val hours: Double, val warrantyDays: Int, val note: String)
val LocalServiceOfferDraft = staticCompositionLocalOf<(ServicesOfferDraft) -> Unit> { {} }

val LocalServiceOnlineAction = staticCompositionLocalOf<(String) -> Unit> { {} }

/** A detailed catalog draft is intent only; publishing remains in the authenticated online flow. */
data class ServicesRequestDraft(
    val definitionId: String, val title: String, val description: String,
    val location: String, val priceCrc: Long, val modality: String,
    val latitude: Double? = null, val longitude: Double? = null,
)

@Composable
fun ServicesExperienceScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onMessages: () -> Unit,
    onActive: () -> Unit,
    onHistory: () -> Unit,
    onProviderConfig: () -> Unit,
    onServiceMessages: (String) -> Unit,
    initialPane: Int = 0,
    initialRequestDraft: ServicesRequestDraft? = null,
    initialHistory: Boolean = false,
) {
    val principal by viewModel.activePrincipal.collectAsState()
    val actor = principal?.id
    var pane by rememberSaveable(actor) { mutableIntStateOf(initialPane.coerceIn(0, 2)) }
    var draft by remember(actor, initialRequestDraft) { mutableStateOf(initialRequestDraft) }
    var offerDraft by remember(actor) { mutableStateOf<ServicesOfferDraft?>(null) }
    var authorityNotice by remember(actor) { mutableStateOf<String?>(null) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isSmallScreen = screenWidth < 380
    val isLargeScreen = screenWidth >= 600

    val tabs = remember(isSmallScreen) {
        listOf(
            Triple("🏪", if (isSmallScreen) "Catálogo" else "Catálogo & Oficios", MeetColors.cyberCyan),
            Triple("⚡", "En Línea", MeetColors.neonGreen),
            Triple("🎯", if (isSmallScreen) "Subasta" else "Subasta Avanzada", MeetColors.electricBlue)
        )
    }

    CompositionLocalProvider(
        LocalServiceOnlineAction provides { message -> authorityNotice = message; pane = 1 },
        LocalServiceOfferDraft provides { value ->
            offerDraft = value
            pane = 1
            authorityNotice = "Contraoferta conservada. Selecciona su solicitud en línea y revisa los datos antes de enviarla."
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF080D17),
                            Color(0xFF0B111E),
                            Color(0xFF05080E)
                        )
                    )
                )
        ) {
            // ── Top Header Navigation Bar ──
            Surface(
                color = Color(0xDD0A0F1D),
                border = BorderStroke(1.dp, MeetColors.borderSubtle.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isSmallScreen) 10.dp else 14.dp,
                                vertical = 8.dp
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(if (isSmallScreen) 32.dp else 36.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.cardBackground)
                                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Regresar",
                                        tint = MeetColors.cyberCyan,
                                        modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "ELYSIUM SERVICIOS",
                                            fontWeight = FontWeight.Black,
                                            fontSize = if (isSmallScreen) 13.sp else 15.sp,
                                            color = Color.White,
                                            letterSpacing = 0.7.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MeetColors.neonGreen.copy(alpha = 0.15f))
                                                .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                "OS v2",
                                                color = MeetColors.neonGreen,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isSmallScreen) "Mano de obra & Subasta" else "Mano de obra · Repuestos & Materiales · Subasta dual",
                                        color = MeetColors.textSecondary,
                                        fontSize = if (isSmallScreen) 9.sp else 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right actions (Active / History quick pills)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 4.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = onActive,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (isSmallScreen) 5.dp else 7.dp,
                                            vertical = if (isSmallScreen) 3.dp else 4.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("⚡", fontSize = if (isSmallScreen) 9.sp else 10.sp)
                                        Text(
                                            text = if (isSmallScreen) "Activos" else "Activos",
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isSmallScreen) 9.sp else 10.sp
                                        )
                                    }
                                }
                                Surface(
                                    onClick = onHistory,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MeetColors.cyberCyan.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (isSmallScreen) 5.dp else 7.dp,
                                            vertical = if (isSmallScreen) 3.dp else 4.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("📜", fontSize = if (isSmallScreen) 9.sp else 10.sp)
                                        Text(
                                            text = if (isSmallScreen) "Hist." else "Historial",
                                            color = MeetColors.cyberCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isSmallScreen) 9.sp else 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(if (isSmallScreen) 6.dp else 8.dp))

                        // ── Segmented Cyber Dock Switcher ──
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0D1424),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tabs.forEachIndexed { index, (icon, label, accent) ->
                                    val isSelected = pane == index
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent
                                            )
                                            .border(
                                                width = if (isSelected) 1.2.dp else 0.dp,
                                                color = if (isSelected) accent else Color.Transparent,
                                                shape = RoundedCornerShape(9.dp)
                                            )
                                            .clickable { pane = index }
                                            .padding(vertical = if (isSmallScreen) 6.dp else 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 3.dp else 4.dp)
                                        ) {
                                            Text(icon, fontSize = if (isSmallScreen) 11.sp else 12.sp)
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else MeetColors.textSecondary,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                fontSize = if (isSmallScreen) 10.sp else 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notice / Authority Banner
            authorityNotice?.let { notice ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (isSmallScreen) 10.dp else 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MeetColors.cyberCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ℹ️", fontSize = 14.sp)
                            Text(
                                text = notice,
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { authorityNotice = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Main Content Area (Centered and responsive across screen sizes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 1100.dp)
                        .fillMaxSize()
                ) {
                    when (pane) {
                        0 -> UniversalServicesScreen(
                            viewModel = viewModel,
                            onBack = onBack,
                            onOpenMessages = onMessages,
                            onNavigateToActiveServices = onActive,
                            onNavigateToHistory = onHistory,
                            onNavigateToProviderConfig = onProviderConfig,
                            onOpenAdvancedMarketplace = { pane = 1 },
                            onPrepareRequest = { draft = it; pane = 1 },
                            showTopBar = false,
                        )
                        1 -> UnifiedServicesScreen(
                            viewModel = viewModel,
                            onBack = onBack,
                            onOpenMessages = onMessages,
                            onProviderConfig = onProviderConfig,
                            onAdvanced = { pane = 2 },
                            onServiceMessages = onServiceMessages,
                            requestDraft = draft,
                            onDraftConsumed = { draft = null },
                            offerDraft = offerDraft,
                            onOfferDraftConsumed = { offerDraft = null },
                            initialHistory = initialHistory,
                            onNavigateToAuth = { navController.navigate("auth") },
                            showTopBar = false,
                        )
                        else -> ElysiumServicesMarketplaceScreen(
                            navController = navController,
                            viewModel = viewModel,
                            onPrepareRequest = { draft = it; pane = 1 },
                            showTopBar = false,
                        )
                    }
                }
            }
        }
    }
}
