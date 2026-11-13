package com.elysium.nexus.ui.hub

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.profile.LastDevice
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * Phase ULT.8 — Quick Connect card.
 *
 * The Hub shows a prominent "Reconnect" card at
 * the top for the last device the user connected
 * to. Tapping the card jumps straight to the
 * pairing / control surface, saving a trip
 * through the full hierarchy. The card
 * disappears when there is no last device (the
 * first time the user opens the app).
 *
 * The card is **one tap to reconnect**: the
 * Hub → Hub → MacDiscovery → MacPairing →
 * MacControl flow is collapsed to Hub → tap →
 * MacControl. (We still show the pairing
 * screen for security — the user has to type
 * the PIN — but they don't have to navigate.)
 *
 * ## Why a card and not a "Continue" button
 *
 * The card carries the device's name, the
 * transport type, and the connection icon. The
 * user gets a visual confirmation of what
 * they're about to connect to. A button would
 * be less informative.
 */
@Composable
fun QuickConnectCard(
    device: LastDevice,
    onConnect: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, accent, transportLabel) = when (device) {
        is LastDevice.Mac -> Triple(
            Icons.Filled.Laptop,
            ElysiumColors.NeonGreen,
            "Wi-Fi · Elysium Link"
        )
        is LastDevice.Bluetooth -> Triple(
            Icons.Filled.Bluetooth,
            ElysiumColors.NeonCyan,
            "Bluetooth HID"
        )
    }
    NeonCard(
        modifier = modifier
            .clickable(onClick = onConnect),
        accent = accent,
        cornerRadius = 18.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (device is LastDevice.Mac) "BUSCAR MAC" else "RECONECTAR",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = accent
                    )
                    NeonStatusPill(
                        label = transportLabel,
                        color = ElysiumColors.OnSurfaceMuted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.name,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = ElysiumColors.OnSurface,
                    maxLines = 1
                )
                // Show the IP / address on a
                // second line so the user can
                // verify the right device.
                val detail = when (device) {
                    is LastDevice.Mac -> "Última IP: ${device.host}:${device.port}"
                    is LastDevice.Bluetooth -> device.address
                }
                Text(
                    text = detail,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = ElysiumColors.OnSurfaceMuted,
                    maxLines = 1
                )
            }
            // Action buttons: the "Connect"
            // primary + the "Forget" secondary.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.3f))
                        .border(1.5.dp, accent, CircleShape)
                        .clickable(onClick = onConnect),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Conectar",
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .clickable(onClick = onForget),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Olvidar",
                        tint = ElysiumColors.OnSurfaceMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
