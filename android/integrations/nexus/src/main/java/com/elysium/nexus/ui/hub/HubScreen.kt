package com.elysium.nexus.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.ui.help.HelpButton
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.responsive.ScreenInfo
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The new "Control Universal" home screen.
 *
 * `MASTER_ORDER.md` §15 says the editor shall be
 * a hierarchy: **Control Universal → category →
 * brand → model → control surface**. The Hub
 * screen is the top of that hierarchy.
 *
 * The Hub has:
 *
 *  - A **hero card** at the top with the app name
 *    + the user's first device (or a welcome
 *    message if they have no devices yet).
 *  - A **dedicated TV section** — a prominent
 *    card that links to the "Controles de TV"
 *    screen (the full TV brand list, search,
 *    filters).
 *  - A **category grid** below, with one card per
 *    [DeviceCategory]. The TV card is the
 *    "first step" — it has a special "Empezar"
 *    (Start) pill that opens the device picker.
 *  - A **settings chip** in the top-right for the
 *    §15 settings dialog.
 *  - A **help button** (?) in the top-left that
 *    opens the in-app help card.
 *
 * The grid is **responsive**: 1 column on phones,
 * 2 on small tablets, 3 on large tablets, 4 on
 * desktops / foldables.
 */
@Composable
fun HubScreen(
    onCategorySelected: (DeviceCategory) -> Unit,
    onTvControlsSelected: () -> Unit,
    onLearnSelected: () -> Unit = {},
    onInstalledProfilesSelected: () -> Unit = {},
    onMacSelected: () -> Unit,
    onUsbCSelected: () -> Unit = {},
    onUniversalRemoteSelected: () -> Unit = {},
    onWifiUniversalSelected: () -> Unit = {},
    onAutomationSelected: () -> Unit = {},
    onScenesSelected: () -> Unit = {},
    onSettings: () -> Unit,
    onShowHelp: () -> Unit,
    firstDeviceLabel: String? = null,
    quickConnect: com.elysium.nexus.core.profile.LastDevice? = null,
    onQuickConnect: () -> Unit = {},
    onForgetQuickConnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===========================================
            // A small top bar with the help button on
            // the left, the settings chip on the right.
            // The top bar is fixed at the top of the
            // scrollable column.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HelpButton(onClick = { showHelp = true })
                NeonChip(
                    label = "Ajustes",
                    onClick = onSettings,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                )
            }

            // === QUICK CONNECT (Phase ULT.8) =====================
            // The last device the user connected
            // to. One tap reconnects. Shown only
            // when there is a remembered device.
            if (quickConnect != null) {
                QuickConnectCard(
                    device = quickConnect,
                    onConnect = onQuickConnect,
                    onForget = onForgetQuickConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = info.sidePadding,
                            vertical = 4.dp
                        )
                )
            }

            // === HERO CARD =========================================
            // The "you are here" hero card. Shows the
            // app name + a welcome message + (if the
            // user has already connected a device) the
            // device's name as a status pill.
            NeonHeroCard(
                title = "Control Universal",
                subtitle = if (firstDeviceLabel != null) {
                    "Tu dispositivo: $firstDeviceLabel"
                } else {
                    "Elige un dispositivo para empezar"
                },
                accent = ElysiumColors.NeonPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    if (firstDeviceLabel != null) {
                        NeonStatusPill(
                            label = "Conectado",
                            color = ElysiumColors.NeonGreen
                        )
                    } else {
                        NeonStatusPill(
                            label = "Sin dispositivos",
                            color = ElysiumColors.NeonOrange
                        )
                    }
                }
            )

            // === EXPLANATION =======================================
            // A short plain-language explanation of
            // what the hub is. The user (who may not
            // have any technical background) sees this
            // and understands what the app does.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    ),
                accent = ElysiumColors.NeonCyan,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = "¿Qué es Control Universal?",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Convierte tu teléfono en el control remoto de todo lo que tienes en casa. " +
                        "Toca una categoría para ver los dispositivos que puedes controlar. " +
                        "Luego conecta uno y úsalo como un control normal.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = ElysiumColors.OnSurface
                )
            }

            // === DEDICATED MAC / PC SECTION ==========================
            // Trackpad + Teclado + Mouse + Gestos.
            // This card is the Mac/PC companion
            // (screen sharing + input surface), NOT
            // the universal control path. The phone
            // connects to the agent over USB-C or
            // Wi-Fi LAN (mDNS discovery + X25519).
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onMacSelected() },
                accent = ElysiumColors.NeonGreen,
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
                            .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Laptop,
                            contentDescription = null,
                            tint = ElysiumColors.NeonGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                        text = "ELYSIUM VANGUARD · WI‑FI",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                        text = "Mac detectado en la misma red · emparejamiento con código",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === USB-C TRANSPORT =====================================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onUsbCSelected() },
                accent = ElysiumColors.NeonYellow,
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
                            .background(ElysiumColors.NeonYellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = ElysiumColors.NeonYellow,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ELYSIUM VANGUARD · USB-C",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonYellow
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Puente ADB Reverse · depuración USB autorizada",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonYellow,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === CONTROL UNIVERSAL · WI-FI ========================
            // Phase ULT.21 — Android TV / Google TV / Fire TV
            // via ADB over the local network (mDNS + subnet
            // sweep + manual IP). Pair once with the standard
            // "Allow USB debugging" dialog; the RSA key is
            // persisted, so every later connection reuses it.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onWifiUniversalSelected() },
                accent = ElysiumColors.NeonCyan,
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
                            .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = ElysiumColors.NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTROL UNIVERSAL · WI-FI",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Android TV · Google TV · Fire TV — control real por ADB",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === DEDICATED UNIVERSAL REMOTE (BT HID) =============
            // Phase ULT.5 — the phone presents itself
            // as a Bluetooth keyboard + mouse to ANY
            // host. Works for Mac, Windows, Linux,
            // Android TV, smart TVs, Raspberry Pi,
            // set-top boxes. No software on the host.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onUniversalRemoteSelected() },
                accent = ElysiumColors.NeonCyan,
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
                            .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = ElysiumColors.NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UNIVERSAL REMOTE",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Bluetooth · funciona con cualquier dispositivo",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === DEDICATED TV CONTROLS SECTION =====================
            // A big, prominent card that links to
            // the full "Controles de TV" screen —
            // the complete TV brand list with
            // search. The user (per Jor) wants TV
            // controls to be a dedicated section,
            // not just a row in the category grid.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onTvControlsSelected() },
                accent = ElysiumColors.NeonOrange,
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
                            .background(ElysiumColors.NeonOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = ElysiumColors.NeonOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTROLES DE TV",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonOrange
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TODAS las marcas · búsqueda · filtros",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === APRENDER SEÑAL IR =================================
            // §5 / §46 real learning: capture any remote's
            // signal via the Nexus Receiver (Wi-Fi/USB-C) and
            // keep it in the local IR database. This is the
            // fallback for TVs that are not in the catalog.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onLearnSelected() },
                accent = ElysiumColors.NeonOrange,
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
                            .background(ElysiumColors.NeonOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = ElysiumColors.NeonOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "APRENDER SEÑAL IR",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonOrange
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Captura la señal de cualquier control remoto",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === MIS CONTROLES (INSTALLED PROFILES) ================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onInstalledProfilesSelected() },
                accent = ElysiumColors.NeonGreen,
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
                            .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = ElysiumColors.NeonGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mis Controles",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = ElysiumColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ver tus controles IR guardados y configurados",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === AUTOMATIZACIONES ==================================
            // §28 — deterministic trigger + conditions
            // + actions automations. The user creates
            // rules like "when motion detected, turn
            // on the lights".
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onAutomationSelected() },
                accent = ElysiumColors.NeonCyan,
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
                            .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = ElysiumColors.NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTOMATIZACIONES",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "§28 · triggers · condiciones · acciones",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // === ESCENAS ================================================
            // §36 — multi-device scenes, durable via Room
            // (SceneRegistry). A scene chains steps across
            // devices: "Modo cine" = TV on + volume up
            // + lights down.
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 4.dp
                    )
                    .clickable { onScenesSelected() },
                accent = ElysiumColors.NeonGreen,
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
                            .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ElysiumColors.NeonGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ESCENAS",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = ElysiumColors.NeonGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "§36 · pasos multi-dispositivo · guardado durable",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // The §15 hierarchy. One card per
            // [DeviceCategory]. The grid is responsive:
            // 1 column on phones, up to 4 on
            // desktops. The TV card is the first
            // card and has a special "Empezar" pill.
            NeonSectionHeader(
                text = "Categorías",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier.padding(
                    horizontal = info.sidePadding,
                    vertical = 8.dp
                )
            )
            CategoryGrid(
                info = info,
                onCategorySelected = onCategorySelected
            )

            // === FOOTER SPACER =====================================
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Control Universal",
            whatIsThis = "Esta es la pantalla principal. Desde aquí puedes elegir qué " +
                "dispositivo quieres controlar (TV, PlayStation, Xbox, etc.).",
            howToUse = listOf(
                "Toca el botón '?' morado en cualquier momento para ver esta ayuda.",
                "Toca 'Ajustes' arriba a la derecha para cambiar las preferencias.",
                "Toca una categoría (TV, Consola, etc.) para ver los dispositivos disponibles."
            ),
            tip = "Si es la primera vez, te recomendamos empezar por la TV. " +
                "Toca la tarjeta 'TV' y sigue los pasos.",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * The responsive category grid.
 *
 * The grid is a flow layout that places the
 * category cards in columns. The number of
 * columns depends on the screen size (see
 * [com.elysium.nexus.ui.responsive.ScreenSize.columnCount]).
 *
 * The grid is built manually (not with
 * `LazyVerticalGrid`) because the total number
 * of categories is small (9) and the grid lives
 * inside a vertically scrollable column.
 */
@Composable
private fun CategoryGrid(
    info: ScreenInfo,
    onCategorySelected: (DeviceCategory) -> Unit
) {
    val categories = DeviceCategory.hubOrder
    val columns = info.columns
    val rows = categories.chunked(columns)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = info.sidePadding),
        verticalArrangement = Arrangement.spacedBy(info.cardSpacing)
    ) {
        rows.forEach { rowCategories ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(info.cardSpacing)
            ) {
                rowCategories.forEach { category ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat(columns - rowCategories.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A single category card.
 *
 * The card shows:
 *
 *  - The category icon (large, colored).
 *  - The category name (English + Spanish, the
 *    larger of the two).
 *  - A 1-line plain-language blurb.
 *  - A chevron (›) on the right indicating
 *    "tap to go deeper".
 *
 * The TV category card is **hero-styled**:
 * bigger, with a violet accent, a "Empezar" pill
 * inside, and a different layout (the icon is
 * larger, the blurb is more prominent).
 */
@Composable
private fun CategoryCard(
    category: DeviceCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTv = category == DeviceCategory.TV
    val accent = if (isTv) ElysiumColors.NeonPurple else ElysiumColors.NeonCyan
    val icon = iconForCategory(category)
    NeonCard(
        modifier = modifier
            .clickable(onClick = onClick),
        accent = accent,
        cornerRadius = if (isTv) 20.dp else 16.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = if (isTv) 18.dp else 14.dp
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isTv) 48.dp else 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (isTv) 28.dp else 22.dp)
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = ElysiumColors.OnSurfaceMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = category.labelEs,
                style = TextStyle(
                    fontSize = if (isTv) 20.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.2.sp
                ),
                color = ElysiumColors.OnSurface,
                maxLines = 1
            )
            Text(
                text = category.labelEn,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = ElysiumColors.OnSurfaceMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.blurbEs,
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = ElysiumColors.OnSurfaceVariant,
                maxLines = 3
            )
            if (isTv) {
                Spacer(modifier = Modifier.height(8.dp))
                NeonChip(
                    label = "Empezar",
                    onClick = onClick,
                    accent = accent,
                    active = true,
                    icon = { Icon(Icons.Filled.Bolt, contentDescription = null) }
                )
            }
        }
    }
}

/**
 * Map a [DeviceCategory] to a Material icon.
 */
private fun iconForCategory(category: DeviceCategory): ImageVector = when (category) {
    DeviceCategory.TV -> Icons.Filled.LiveTv
    DeviceCategory.ANDROID_TV -> Icons.Filled.VideoLabel
    DeviceCategory.PLAYSTATION -> Icons.Filled.VideogameAsset
    DeviceCategory.XBOX -> Icons.Filled.Gamepad
    DeviceCategory.NINTENDO -> Icons.Filled.Gamepad
    DeviceCategory.COMPUTER -> Icons.Filled.Computer
    DeviceCategory.STREAMING -> Icons.Filled.PlayCircle
    DeviceCategory.SOUNDBAR -> Icons.Filled.Speaker
    DeviceCategory.PROJECTOR -> Icons.Filled.Headphones
    DeviceCategory.AIR_CONDITIONER -> Icons.Filled.Settings
}
