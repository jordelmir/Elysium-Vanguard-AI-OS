package com.elysium.nexus.ui.mac

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard

const val GITHUB_MAC_RELEASES_URL = "https://github.com/jordelmir/elysium-nexus-control/releases"

/**
 * Step-by-step interactive guide for downloading, installing, and configuring
 * the Elysium Nexus Mac agent from GitHub on any Mac in the world.
 */
@Composable
fun MacAgentDownloadGuide(
    isInitiallyExpanded: Boolean = false,
    onRetryScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current

    NeonCard(
        modifier = modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Header: Clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        isExpanded = !isExpanded
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = ElysiumColors.NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Guía: Instalar App en tu Mac",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        Text(
                            text = if (isExpanded) "Toca para ocultar instrucciones" else "Paso a paso oficial desde GitHub · Toca para ver",
                            style = TextStyle(fontSize = 11.sp),
                            color = ElysiumColors.OnSurfaceMuted
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step 1
                    GuideStepItem(
                        stepNumber = "1",
                        icon = Icons.Filled.Download,
                        accentColor = ElysiumColors.NeonCyan,
                        title = "Descargar instalador desde GitHub",
                        description = "Descarga oficial de Elysium-Nexus-Mac.dmg para cualquier Mac con Apple Silicon o Intel."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Button Open GitHub
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f))
                                    .border(1.dp, ElysiumColors.NeonCyan, RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_MAC_RELEASES_URL)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se pudo abrir navegador: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = ElysiumColors.NeonCyan, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Abrir GitHub",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        color = ElysiumColors.NeonCyan
                                    )
                                }
                            }

                            // Button Copy Link
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElysiumColors.SurfaceHigh)
                                    .border(1.dp, ElysiumColors.OnSurfaceMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        clipboardManager.setText(AnnotatedString(GITHUB_MAC_RELEASES_URL))
                                        Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = ElysiumColors.OnSurface, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Copiar Enlace",
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                        color = ElysiumColors.OnSurface
                                    )
                                }
                            }
                        }
                    }

                    // Step 2
                    GuideStepItem(
                        stepNumber = "2",
                        icon = Icons.Filled.Settings,
                        accentColor = ElysiumColors.NeonPurple,
                        title = "Instalar en Aplicaciones",
                        description = "En tu Mac, abre el archivo .dmg descargado y arrastra 'Elysium Nexus.app' a tu carpeta /Aplicaciones."
                    )

                    // Step 3
                    GuideStepItem(
                        stepNumber = "3",
                        icon = Icons.Filled.Security,
                        accentColor = ElysiumColors.NeonGreen,
                        title = "Activar Permisos en macOS",
                        description = "En tu Mac abre: Ajustes del Sistema → Privacidad y Seguridad:\n" +
                                "• Grabación de pantalla: Activa 'Elysium Nexus' (transmite video 1080p).\n" +
                                "• Accesibilidad: Activa 'Elysium Nexus' (control de mouse, trackpad y clicks)."
                    )

                    // Step 4
                    GuideStepItem(
                        stepNumber = "4",
                        icon = Icons.Filled.Wifi,
                        accentColor = ElysiumColors.NeonMagenta,
                        title = "Abrir la App y Emparejar con PIN",
                        description = "Abre 'Elysium Nexus' en el Mac. Aparecerá en tu teléfono al instante con un código PIN de 6 dígitos que se introduce una sola vez para conexión cifrada punto a punto."
                    )

                    // Step 5: Action Button to re-scan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        ElysiumColors.NeonCyan.copy(alpha = 0.3f),
                                        ElysiumColors.NeonGreen.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .border(1.5.dp, ElysiumColors.NeonGreen, RoundedCornerShape(10.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onRetryScan()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = ElysiumColors.NeonGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Actualizar / Reintentar Escaneo de Mac",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = ElysiumColors.NeonGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepItem(
    stepNumber: String,
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    extraContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElysiumColors.SurfaceHigh.copy(alpha = 0.6f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                        color = accentColor
                    )
                }
                Text(
                    text = title,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = ElysiumColors.OnSurface
                )
            }
            Text(
                text = description,
                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
                color = ElysiumColors.OnSurfaceMuted,
                modifier = Modifier.padding(start = 32.dp)
            )
            Box(modifier = Modifier.padding(start = 32.dp)) {
                extraContent()
            }
        }
    }
}
