package com.elysium.vanguard.features.dashboard

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.components.MatrixRain
import com.elysium.vanguard.ui.components.NeonGlowIcon
import com.elysium.vanguard.ui.components.TitanLogo
import com.elysium.vanguard.ui.components.TitanLogoStyle
import com.elysium.vanguard.ui.components.SovereignLifeWrapper
import com.elysium.vanguard.ui.components.SovereignCard
import com.elysium.vanguard.ui.components.OrbitalIcon
import com.elysium.vanguard.ui.components.AnimatedCounter
import com.elysium.vanguard.ui.components.GlassPillBadge
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.holographicGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import com.elysium.vanguard.ui.theme.neonGlass
import com.elysium.vanguard.ui.theme.premiumGlass
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.components.ColorCustomizerIcon
import com.elysium.vanguard.ui.components.ColorSelectionDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onNavigateToStorage: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToMusic: () -> Unit
) {
    val context = LocalContext.current
    var showColorDialog by remember { mutableStateOf(false) }
    val accentColor = SectionColorManager.dashboardAccent

    // ── LIVE DEVICE METRICS ──
    // The storage is read live so the dashboard always shows the real numbers.
    var storageInfo by remember { mutableStateOf(getStorageInfo(context)) }
    val ramInfo = remember { getRamInfo(context) }
    val batteryLevel = remember { getBatteryLevel(context) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            storageInfo = getStorageInfo(context)
        }
    }

    val portalItems = listOf(
        // RENAME: "Administrador de archivos" -> "File Manager"
        PortalItem("File Manager", "MANAGE · EXTRACT · CONVERT · EXPLORE", Icons.Default.Storage, TitanColors.NeonCyan, onNavigateToStorage,
            Brush.linearGradient(listOf(TitanColors.NeonCyan.copy(alpha = 0.15f), TitanColors.ElectricBlue.copy(alpha = 0.05f)))),
        PortalItem("GALERÍA", "PHOTOS · VIDEOS · ALBUMS", Icons.Default.Image, TitanColors.QuantumPink, onNavigateToGallery,
            Brush.linearGradient(listOf(TitanColors.QuantumPink.copy(alpha = 0.15f), TitanColors.PlasmaPurple.copy(alpha = 0.05f)))),
        PortalItem("MÚSICA", "MUSIC · PLAYLISTS · BASS", Icons.Default.MusicNote, TitanColors.RadioactiveGreen, onNavigateToMusic,
            Brush.linearGradient(listOf(TitanColors.RadioactiveGreen.copy(alpha = 0.15f), TitanColors.NeonYellow.copy(alpha = 0.05f))))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack)
    ) {
        // ── MATRIX RAIN BACKGROUND ──
        MatrixRain(
            color = TitanColors.RadioactiveGreen.copy(alpha = 0.5f),
            speed = 70L,
            trailLength = 16,
            alpha = 0.25f,
            isMulticolor = SectionColorManager.isMulticolor
        )

        // ── COLOR CUSTOMIZER ──
        ColorCustomizerIcon(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp),
            onClick = { showColorDialog = true }
        )

        if (showColorDialog) {
            ColorSelectionDialog(
                sectionName = "DASHBOARD",
                onColorSelected = { color, applyToAll ->
                    SectionColorManager.updateAccent(SectionColorManager.SECTION_DASHBOARD, color, applyToAll)
                },
                onDismiss = { showColorDialog = false }
            )
        }

        // ── CONTENT ──
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 40.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ══════════ HERO HEADER ══════════
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SovereignLifeWrapper {
                        TitanLogo(style = TitanLogoStyle.HERO, size = 100.dp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ELYSIUM VANGUARD",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "NEURAL COMMAND CENTER",
                        color = TitanColors.NeonCyan.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ══════════ LIVE METRICS STRIP ══════════
            item(span = { GridItemSpan(maxLineSpan) }) {
                SovereignCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    glassAlpha = 0.4f, // Increased opacity to prevent "hole" look
                    glowRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LiveMetricGauge(
                            label = "STORAGE",
                            value = storageInfo.usedPercent,
                            color = when {
                                storageInfo.usedPercent > 85 -> TitanColors.NeonRed
                                storageInfo.usedPercent > 60 -> TitanColors.NeonOrange
                                else -> accentColor
                            },
                            // REAL storage, exactly the same numbers the file
                            // manager shows inside STORAGE CENTRAL.
                            detail = "${storageInfo.usedGb}/${storageInfo.totalGb} GB",
                            subDetail = storageInfo.freeLabel
                        )
                        LiveMetricGauge(
                            label = "MEMORY",
                            value = ramInfo.usedPercent,
                            color = when {
                                ramInfo.usedPercent > 80 -> TitanColors.NeonRed
                                ramInfo.usedPercent > 50 -> TitanColors.NeonOrange
                                else -> TitanColors.RadioactiveGreen
                            },
                            detail = "${ramInfo.usedGb}/${ramInfo.totalGb} GB"
                        )
                        LiveMetricGauge(
                            label = "BATTERY",
                            value = batteryLevel,
                            color = when {
                                batteryLevel < 20 -> TitanColors.NeonRed
                                batteryLevel < 50 -> TitanColors.NeonYellow
                                else -> TitanColors.RadioactiveGreen
                            },
                            detail = "$batteryLevel%"
                        )
                    }
                }
            }

            // ══════════ SECTION HEADER ══════════
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "OPERATIONAL NODES",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ══════════ PORTAL CARDS ══════════
            items(portalItems) { item ->
                PortalCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    neonColor = item.neonColor,
                    gradientBg = item.gradientBg,
                    onClick = item.onClick
                )
            }

            // ══════════ STATUS STRIP ══════════
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .pulsingNeonBorder(cornerRadius = 16.dp, glowColor = TitanColors.NeonCyan, glassAlpha = 0.1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(label = "CORE", value = "STABLE", color = TitanColors.NeonCyan)
                    StatusChip(label = "SHIELD", value = "ACTIVE", color = TitanColors.RadioactiveGreen)
                    StatusChip(label = "THREATS", value = "ZERO", color = TitanColors.NeonRed)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// COMPONENTS
// ══════════════════════════════════════════════════════════════

private data class PortalItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val neonColor: Color,
    val onClick: () -> Unit,
    val gradientBg: Brush
)

/**
 * LIVE METRIC GAUGE — Circular arc gauge with animated fill.
 */
@Composable
private fun LiveMetricGauge(
    label: String,
    value: Int,
    color: Color,
    detail: String,
    subDetail: String? = null
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (value / 100f) * 270f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "sweep"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 5.dp.toPx()
                // Background arc
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Filled arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            AnimatedCounter(
                targetValue = value,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                suffix = "%"
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        Text(detail, color = Color.White.copy(alpha = 0.3f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        if (subDetail != null) {
            Text(subDetail, color = color.copy(alpha = 0.75f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * PORTAL CARD — Premium card with orbital icon and gradient background.
 */
@Composable
private fun PortalCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    neonColor: Color,
    gradientBg: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SovereignCard(
        modifier = modifier
            .aspectRatio(0.82f)
            .clickable { onClick() },
        cornerRadius = 24.dp,
        glassAlpha = 0.18f,
        glowRadius = 20.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TitanColors.DeepVoid.copy(alpha = 0.3f)) // Safety layer for Poco/HyperOS transparency issues
                .background(
                    Brush.verticalGradient(
                        listOf(
                            neonColor.copy(alpha = 0.35f),
                            neonColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Orbital icon
                OrbitalIcon(
                    icon = icon,
                    color = neonColor,
                    iconSize = 28.dp,
                    ringSize = 52.dp,
                    ringColor = neonColor.copy(alpha = 0.4f)
                )

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = neonColor.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        lineHeight = 12.sp
                    )
                }

                // Enter arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENTER",
                        color = neonColor.copy(alpha = 0.4f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = neonColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * STATUS CHIP — Compact status indicator with label and value.
 */
@Composable
private fun StatusChip(label: String, value: String, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_$label")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color.copy(alpha = glowAlpha), CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Text(value, color = color.copy(alpha = glowAlpha), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ══════════════════════════════════════════════════════════════
// DEVICE INFO HELPERS
// ══════════════════════════════════════════════════════════════

private data class StorageInfo(val totalGb: String, val usedGb: String, val usedPercent: Int, val freeLabel: String)
private data class RamInfo(val totalGb: String, val usedGb: String, val usedPercent: Int)

/**
 * REAL device storage. It reads the very same source the file manager uses, so
 * both screens always show the same numbers, and it also reports the free
 * space in gigabytes.
 */
private fun getStorageInfo(context: Context): StorageInfo {
    return try {
        val stats = com.elysium.vanguard.core.util.StorageMetrics.read(context)
        val totalGb = "%.0f".format(stats.totalBytes / 1_000_000_000.0)
        val usedGb = "%.0f".format(stats.usedBytes / 1_000_000_000.0)
        StorageInfo(totalGb, usedGb, stats.percentUsed, stats.freeLabel)
    } catch (_: Exception) {
        StorageInfo("0", "0", 0, "0 MB FREE")
    }
}

private fun getRamInfo(context: Context): RamInfo {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalGb = "%.1f".format(memInfo.totalMem / 1_073_741_824.0)
        val usedGb = "%.1f".format((memInfo.totalMem - memInfo.availMem) / 1_073_741_824.0)
        val percent = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem) * 100).roundToInt()
        RamInfo(totalGb, usedGb, percent)
    } catch (_: Exception) {
        RamInfo("0", "0", 0)
    }
}

private fun getBatteryLevel(context: Context): Int {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) {
        100
    }
}
