package com.elysium.vanguard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.holographicGlass
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Palette
import com.elysium.vanguard.ui.theme.neonGlass
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults

/**
 * SOVEREIGN LIFE WRAPPER
 * Injects a subtle 3D pulsing "life" into any component.
 * It uses scale and rotationY/X to create a sense of depth and breathing.
 */
@Composable
fun SovereignLifeWrapper(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pulseSpeed: Int = 2500,
    maxScale: Float = 1.02f,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "life_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                rotationY = rotation
                rotationX = rotation * 0.5f
                cameraDistance = 12f * density
            }
    ) {
        content()
    }
}

/**
 * PULSE CONTAINER
 * A simpler pulse focused only on scale and opacity, ideal for small interactive bits.
 */
@Composable
fun PulseContainer(
    modifier: Modifier = Modifier,
    pulseEnabled: Boolean = true,
    targetScale: Float = 1.1f,
    duration: Int = 1500,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulseEnabled) targetScale else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(modifier = modifier.scale(scale)) {
        content()
    }
}

/**
 * SOVEREIGN CARD
 * The standard container for high-end "Living" UI elements.
 * Combines the 3D life pulse with the premium holographic glass.
 */
@Composable
fun SovereignCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    glassAlpha: Float = 0.2f,
    glowRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .holographicGlass(
                cornerRadius = cornerRadius,
                glassAlpha = glassAlpha,
                glowRadius = glowRadius
            )
    ) {
        SovereignLifeWrapper {
            Box(
                modifier = Modifier,
                content = content
            )
        }
    }
}

/**
 * ANIMATED COUNTER
 * A number that animates to its target value with a spring effect.
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 24.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(
        text = "$animatedValue$suffix",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/**
 * ORBITAL ICON
 * An icon with a spinning neon orbital ring around it.
 */
@Composable
fun OrbitalIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    ringSize: Dp = 56.dp,
    ringColor: Color = color.copy(alpha = 0.5f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbital")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        // Orbital ring
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        ) {
            val strokeWidth = 2.dp.toPx()
            drawArc(
                color = ringColor,
                startAngle = 0f,
                sweepAngle = 200f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor.copy(alpha = 0.3f),
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                style = Stroke(width = strokeWidth * 0.5f, cap = StrokeCap.Round)
            )
        }
        // Center icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * GLASS PILL BADGE
 * A compact neon-glass status badge.
 */
@Composable
fun GlassPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TitanColors.NeonCyan,
    fontSize: TextUnit = 10.sp
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = modifier
            .neonGlass(cornerRadius = 20.dp, glowColor = color.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * ANIMATED EMPTY STATE
 * A standard empty-state component with pulsing icon and message.
 */
@Composable
fun AnimatedEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    color: Color = TitanColors.NeonCyan
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PulseContainer(targetScale = 1.15f, duration = 2000) {
            NeonGlowIcon(icon = icon, color = color.copy(alpha = 0.4f), size = 64.dp, glowRadius = 24.dp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * EQUALIZER BARS — Animated bars for audio visualization.
 */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = TitanColors.NeonCyan,
    barCount: Int = 5,
    isAnimating: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "bar_$index")
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (400..800).random(),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "height"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (isAnimating) heightScale else 0.3f)
                    .background(color, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        }
    }
}

/**
 * COLOR CUSTOMIZER ICON — A rotating, neon-glowing color wheel icon.
 */
@Composable
fun ColorCustomizerIcon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "color_customizer")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
            val strokeWidth = 3.dp.toPx()
            val gradientBrush = Brush.sweepGradient(
                listOf(
                    TitanColors.NeonCyan,
                    TitanColors.QuantumPink,
                    TitanColors.RadioactiveGreen,
                    TitanColors.NeonYellow,
                    TitanColors.ElectricBlue,
                    TitanColors.NeonCyan
                )
            )
            drawCircle(
                brush = gradientBrush,
                radius = size.minDimension / 2,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * COLOR SELECTION DIALOG — Premium glass dialog for picking accent colors.
 *
 * The selection is GLOBAL by default: the chosen color becomes the theme of
 * the whole app. Turning the global switch off applies the color to this
 * section only, and a global selection can always be refined later with an
 * individual one.
 */
@Composable
fun ColorSelectionDialog(
    sectionName: String,
    onColorSelected: (Color, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        TitanColors.RadioactiveGreen,
        TitanColors.NeonCyan,
        TitanColors.QuantumPink,
        TitanColors.ElectricBlue,
        TitanColors.NeonYellow,
        TitanColors.PlasmaPurple,
        TitanColors.AbsoluteWhite
    )
    val currentAccent = SectionColorManager.accentFor(sectionName)
    val hasIndividual = SectionColorManager.hasOverride(sectionName)

    // GLOBAL BY DEFAULT: the selection drives the whole app unless the user
    // explicitly turns it off for an individual section.
    var applyToAll by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitanColors.AbsoluteBlack.copy(alpha = 0.95f),
        title = {
            Column {
                Text(
                    "SELECT $sectionName ACCENT",
                    color = TitanColors.AbsoluteWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (applyToAll) "GLOBAL THEME · TODAS LAS SECCIONES" else "SOLO $sectionName",
                    color = if (applyToAll) TitanColors.NeonCyan else TitanColors.NeonYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column {
                Text(
                    "GLOBAL por defecto · APAGADO = SOLO ESTA SECCIÓN",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // GLOBAL TOGGLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { applyToAll = !applyToAll },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = TitanColors.NeonCyan,
                            uncheckedColor = Color.White.copy(alpha = 0.5f),
                            checkmarkColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APPLY GLOBAL THEME", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // MULTICOLOR OPTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    TitanColors.NeonCyan,
                                    TitanColors.QuantumPink,
                                    TitanColors.RadioactiveGreen,
                                    TitanColors.NeonYellow
                                )
                            )
                        )
                        .clickable {
                            SectionColorManager.enableMulticolor()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ACTIVATE MULTICOLOR MODE",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // COLOR GRID — the active color is ringed so the current
                // selection is always visible.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { color ->
                        val isActive = color.toArgb() == currentAccent.toArgb()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    // Apply for real: the screen decides what a
                                    // global or individual selection means.
                                    onColorSelected(color, applyToAll)
                                    onDismiss()
                                }
                                .border(
                                    width = if (isActive) 3.dp else 2.dp,
                                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // INDIVIDUAL OVERRIDE: go back to the global theme.
                if (hasIndividual) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            SectionColorManager.clearOverride(sectionName)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "VOLVER AL TEMA GLOBAL",
                            color = TitanColors.NeonYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = TitanColors.AbsoluteWhite.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier
            .pulsingNeonBorder(cornerRadius = 28.dp, glowColor = TitanColors.NeonCyan)
            .clip(RoundedCornerShape(28.dp))
    )
}
