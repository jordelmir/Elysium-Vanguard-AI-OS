package com.elysium369.meet.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elysium369.meet.MainActivity
import com.elysium369.meet.R

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   N O T I F I C A T I O N   H U B   v2
 * ══════════════════════════════════════════════════════════════════════
 *
 * Centro unificado de notificaciones y banners emergentes (Heads-Up Pop-ups)
 * para TODOS los usuarios (pasajeros, clientes, conductores, mecánicos).
 *
 * Secciones cubiertas:
 *  - Viajes (Elysium Rides): Ofertas, aceptaciones, llegada, tracking
 *  - Servicios Elysium: Cotizaciones, asignaciones, completados
 *  - Chat / Mensajes: Mensajes de viaje, mesh y directos
 *  - Llamadas: Llamadas entrantes via Mesh/P2P
 *  - Diagnóstico Automotriz (OBD2, LiveLink, DTCs)
 *  - Red Mesh P2P: Radar, enlace de nodos, mensajes
 *  - ScreenMirror / Proyección de Pantalla
 *  - Seguridad & Sistema
 *
 * REGLA: Toda notificación usa Heads-Up (PRIORITY_MAX) para que aparezca
 * como pop-up flotante incluso con la app en background.
 */
object ElysiumNotificationHub {

    // ── Canales de Notificación ──
    const val CHANNEL_RIDES_DISPATCH   = "elysium_rides_dispatch_high_priority"
    const val CHANNEL_RIDES_TRACKING   = "elysium_ride_safety_tracking"
    const val CHANNEL_OBD_DIAGNOSTICS  = "elysium_obd_channel"
    const val CHANNEL_MESH_NETWORK     = "elysium_mesh_channel"
    const val CHANNEL_SYSTEM_ALERTS    = "elysium_system_alerts"
    const val CHANNEL_SERVICES         = "elysium_services_marketplace"
    const val CHANNEL_CHAT_MESSAGES    = "elysium_chat_messages"
    const val CHANNEL_INCOMING_CALLS   = "elysium_incoming_calls"
    const val CHANNEL_SCREEN_MIRROR    = "elysium_screen_mirror"

    private var isInitialized = false

    // ── Track previous counts to detect new items (ViewModel-level) ──
    @Volatile private var lastKnownOfferCount = 0
    @Volatile private var lastKnownChatCount = 0
    @Volatile private var lastKnownServiceBidCounts = mutableMapOf<String, Int>()
    @Volatile private var lastKnownRideState = ""

    fun init(context: Context) {
        if (isInitialized) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                // 1. Viajes — Despacho y Alertas
                NotificationChannel(
                    CHANNEL_RIDES_DISPATCH,
                    "Elysium Viajes: Despacho y Alertas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Ofertas de viaje, aceptación de choferes, llegada y estado en vivo"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                    enableLights(true)
                    lightColor = Color.CYAN
                },
                // 2. Viajes — Rastreo de Ruta
                NotificationChannel(
                    CHANNEL_RIDES_TRACKING,
                    "Elysium Viajes: Rastreo de Ruta",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Telemetría y navegación GPS de viaje activo"
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                },
                // 3. Diagnóstico OBD2 & LiveLink
                NotificationChannel(
                    CHANNEL_OBD_DIAGNOSTICS,
                    "Elysium Diagnóstico OBD2 & LiveLink",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Monitoreo en tiempo real de motor, DTCs y telemetría"
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                },
                // 4. Red Mesh P2P
                NotificationChannel(
                    CHANNEL_MESH_NETWORK,
                    "Red Mesh Elysium P2P",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Comunicaciones, radar y mensajes directos sin internet"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 100, 250)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                },
                // 5. Alertas Críticas
                NotificationChannel(
                    CHANNEL_SYSTEM_ALERTS,
                    "Alertas Críticas Elysium",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos urgentes del vehículo y del sistema"
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                },
                // 6. Servicios Marketplace (mecánico, cotizaciones, etc.)
                NotificationChannel(
                    CHANNEL_SERVICES,
                    "Elysium Servicios: Cotizaciones y Estado",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Cotizaciones de mecánicos, asignaciones, y estado de reparaciones"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                    enableLights(true)
                    lightColor = Color.GREEN
                },
                // 7. Chat y Mensajes
                NotificationChannel(
                    CHANNEL_CHAT_MESSAGES,
                    "Elysium Mensajes y Chat",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Mensajes de viaje, chat con mecánicos, y mensajes directos"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                    enableLights(true)
                    lightColor = Color.BLUE
                },
                // 8. Llamadas Entrantes
                NotificationChannel(
                    CHANNEL_INCOMING_CALLS,
                    "Elysium Llamadas Entrantes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Llamadas de voz entrantes por Red Mesh y P2P"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                    enableLights(true)
                    lightColor = Color.RED
                },
                // 9. ScreenMirror / Proyección
                NotificationChannel(
                    CHANNEL_SCREEN_MIRROR,
                    "Elysium ScreenMirror",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Proyección de pantalla activa"
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                },
            )
            manager.createNotificationChannels(channels)
        }
        isInitialized = true
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 1: VIAJES (ELYSIUM RIDES) — Para USUARIOS y CONDUCTORES
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up al PASAJERO cuando un chofer le envía una contraoferta */
    fun showOfferReceivedPopUp(context: Context, tripId: String, driverName: String, priceCrc: Double) {
        postHeadsUpPopUp(
            context = context,
            id = 7_250 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = "🚕 ¡NUEVA OFERTA DE VIAJE!",
            body = "$driverName ofrece llevarte por ₡${priceCrc.toInt()} CRC. Toca para ver la oferta.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_CALL
        )
    }

    /** Pop-up cuando la oferta es ACEPTADA — tanto para pasajero como conductor */
    fun showOfferAcceptedPopUp(context: Context, tripId: String, partyName: String, priceCrc: Double, isDriver: Boolean) {
        val title = if (isDriver) "🎉 ¡OFERTA ACEPTADA!" else "✅ ¡VIAJE CONFIRMADO!"
        val body = if (isDriver) {
            "$partyName aceptó tu tarifa de ₡${priceCrc.toInt()} CRC. Dirígete al punto de recogida."
        } else {
            "Tu conductor $partyName está en camino. Tarifa: ₡${priceCrc.toInt()} CRC."
        }
        postHeadsUpPopUp(
            context = context,
            id = 7_300 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = title, body = body,
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up al PASAJERO cuando el chofer llega */
    fun showDriverArrivedPopUp(context: Context, tripId: String, pickupAddress: String) {
        postHeadsUpPopUp(
            context = context,
            id = 7_500 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = "📍 ¡TU CHOFER HA LLEGADO!",
            body = "Esperando en: $pickupAddress. Comparte el PIN de abordaje.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up al CONDUCTOR cuando se le asigna un viaje (nueva solicitud) */
    fun showNewRideRequestPopUp(context: Context, tripId: String, pickupAddress: String, destAddress: String, priceCrc: Double) {
        postHeadsUpPopUp(
            context = context,
            id = 7_150 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = "🔔 ¡NUEVA SOLICITUD DE VIAJE!",
            body = "De: $pickupAddress → $destAddress. Tarifa: ₡${priceCrc.toInt()} CRC. Toca para ofertar.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_CALL
        )
    }

    /** Pop-up cuando el viaje está EN CURSO */
    fun showTripStartedPopUp(context: Context, tripId: String, destination: String) {
        postHeadsUpPopUp(
            context = context,
            id = 7_550 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_TRACKING,
            title = "🚀 VIAJE EN CURSO",
            body = "En ruta hacia: $destination. Monitoreo de seguridad activo.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_NAVIGATION
        )
    }

    /** Pop-up cuando el viaje se COMPLETA */
    fun showTripCompletedPopUp(context: Context, tripId: String, totalFare: String) {
        postHeadsUpPopUp(
            context = context,
            id = 7_600 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = "🏁 ¡VIAJE COMPLETADO!",
            body = "Has llegado a tu destino. Total: $totalFare. Toca para calificar.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    /** Pop-up cuando un viaje se CANCELA */
    fun showTripCancelledPopUp(context: Context, tripId: String, byWhom: String, reason: String) {
        val reasonText = if (reason.isNotBlank()) ": $reason" else ""
        postHeadsUpPopUp(
            context = context,
            id = 7_700 + (tripId.hashCode() and 0x3FFF_FFFF),
            channelId = CHANNEL_RIDES_DISPATCH,
            title = "❌ VIAJE CANCELADO",
            body = "Cancelado por $byWhom$reasonText. Puedes solicitar uno nuevo.",
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 2: SERVICIOS ELYSIUM (MARKETPLACE) — Para USUARIOS
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up al USUARIO cuando un mecánico/taller envía una cotización */
    fun showServiceBidReceivedPopUp(context: Context, requestId: String, shopName: String, price: Double, estimatedHours: Double) {
        postHeadsUpPopUp(
            context = context,
            id = 6_100 + (requestId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = "🔧 ¡NUEVA COTIZACIÓN RECIBIDA!",
            body = "$shopName cotiza ₡${price.toInt()} CRC (~${estimatedHours}h). Toca para comparar ofertas.",
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up al USUARIO cuando un mecánico acepta y se asigna a su solicitud */
    fun showServiceAcceptedPopUp(context: Context, requestId: String, mechanicName: String) {
        postHeadsUpPopUp(
            context = context,
            id = 6_200 + (requestId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = "✅ ¡MECÁNICO ASIGNADO!",
            body = "$mechanicName ha sido asignado a tu solicitud. En camino a atenderte.",
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up al USUARIO cuando su servicio ha sido completado */
    fun showServiceCompletedPopUp(context: Context, requestId: String, mechanicName: String?) {
        val who = mechanicName ?: "El mecánico"
        postHeadsUpPopUp(
            context = context,
            id = 6_300 + (requestId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = "🏆 ¡SERVICIO COMPLETADO!",
            body = "$who finalizó la reparación. Toca para verificar y calificar.",
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    /** Pop-up al USUARIO cuando su solicitud de servicio ha sido cancelada */
    fun showServiceCancelledPopUp(context: Context, requestId: String) {
        postHeadsUpPopUp(
            context = context,
            id = 6_400 + (requestId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = "❌ SOLICITUD DE SERVICIO CANCELADA",
            body = "Tu solicitud de servicio ha sido cancelada. Puedes crear una nueva.",
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up al PROVEEDOR cuando recibe una nueva solicitud de servicio */
    fun showNewServiceRequestPopUp(context: Context, requestId: String, problem: String, location: String) {
        postHeadsUpPopUp(
            context = context,
            id = 6_500 + (requestId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = "🔔 ¡NUEVA SOLICITUD DE SERVICIO!",
            body = "Problema: $problem. Ubicación: $location. Toca para cotizar.",
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_CALL
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 3: CHAT Y MENSAJES — Para TODOS los usuarios
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up cuando llega un mensaje de chat en un viaje */
    fun showRideChatMessagePopUp(context: Context, senderName: String, senderRole: String, messageText: String?) {
        val roleLabel = if (senderRole == "DRIVER") "Conductor" else "Pasajero"
        val body = messageText?.take(120) ?: "Mensaje de audio recibido"
        postHeadsUpPopUp(
            context = context,
            id = 10_100 + (senderName.hashCode() and 0xFFFF),
            channelId = CHANNEL_CHAT_MESSAGES,
            title = "💬 $roleLabel: $senderName",
            body = body,
            action = "OPEN_RIDE",
            category = NotificationCompat.CATEGORY_MESSAGE
        )
    }

    /** Pop-up cuando llega un mensaje directo (mesh o chat general) */
    fun showDirectMessagePopUp(context: Context, senderName: String, messageText: String) {
        postHeadsUpPopUp(
            context = context,
            id = 10_200 + (senderName.hashCode() and 0xFFFF),
            channelId = CHANNEL_CHAT_MESSAGES,
            title = "💬 Mensaje de $senderName",
            body = messageText.take(120),
            action = "OPEN_CHAT",
            category = NotificationCompat.CATEGORY_MESSAGE
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 4: LLAMADAS ENTRANTES — Para TODOS los usuarios
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up de llamada entrante por Red Mesh P2P */
    fun showIncomingCallPopUp(context: Context, callerName: String, callType: String = "Voz") {
        postHeadsUpPopUp(
            context = context,
            id = 11_100 + (callerName.hashCode() and 0xFFFF),
            channelId = CHANNEL_INCOMING_CALLS,
            title = "📞 ¡LLAMADA ENTRANTE!",
            body = "$callerName te está llamando ($callType). Toca para contestar.",
            action = "OPEN_CHAT",
            category = NotificationCompat.CATEGORY_CALL
        )
    }

    /** Cancelar la notificación de llamada entrante cuando se contesta o rechaza */
    fun dismissIncomingCallNotification(context: Context, callerName: String) {
        try {
            NotificationManagerCompat.from(context)
                .cancel(11_100 + (callerName.hashCode() and 0xFFFF))
        } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 5: DIAGNÓSTICO OBD2 & LIVELINK
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up al conectar escáner OBD2 */
    fun showObdConnectedPopUp(context: Context, adapterName: String) {
        postHeadsUpPopUp(
            context = context,
            id = 8_100,
            channelId = CHANNEL_OBD_DIAGNOSTICS,
            title = "🚗 ¡CONEXIÓN OBD2 ESTABLECIDA!",
            body = "Conectado a $adapterName. Telemetría de motor en vivo.",
            action = "OPEN_SCANNER",
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    /** Pop-up cuando se detecta un DTC */
    fun showDtcDetectedPopUp(context: Context, dtcCode: String, description: String) {
        val body = description.ifBlank { "Se detectó una anomalía en la computadora del motor." }
        postHeadsUpPopUp(
            context = context,
            id = 8_200 + (dtcCode.hashCode() and 0xFFFF),
            channelId = CHANNEL_OBD_DIAGNOSTICS,
            title = "⚠️ CÓDIGO DE FALLA: $dtcCode",
            body = body,
            action = "OPEN_DTCS",
            category = NotificationCompat.CATEGORY_ALARM
        )
    }

    /** Pop-up al finalizar escaneo completo */
    fun showScanCompletedPopUp(context: Context, dtcCount: Int) {
        val body = if (dtcCount == 0) {
            "Sistemas del motor en perfecto estado. Cero fallas detectadas."
        } else {
            "Se registraron $dtcCount códigos de falla. Toca para ver el informe."
        }
        postHeadsUpPopUp(
            context = context,
            id = 8_300,
            channelId = CHANNEL_OBD_DIAGNOSTICS,
            title = "✅ ESCANEO OBD2 COMPLETADO",
            body = body,
            action = "OPEN_SCANNER",
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    /** Notificación activa de LiveLink remoto */
    fun showLiveLinkActiveNotification(context: Context, sessionId: String, modeText: String) {
        postHeadsUpPopUp(
            context = context,
            id = 8_400,
            channelId = CHANNEL_OBD_DIAGNOSTICS,
            title = "📡 LiveLink Remoto Activo",
            body = "Sesión $sessionId — Telemetría vehicular en modo $modeText.",
            action = "OPEN_SCANNER",
            category = NotificationCompat.CATEGORY_SERVICE
        )
    }

    /** Pop-up cuando el Scanner inicia escaneo activo */
    fun showScannerActivePopUp(context: Context) {
        postHeadsUpPopUp(
            context = context,
            id = 8_050,
            channelId = CHANNEL_OBD_DIAGNOSTICS,
            title = "🔍 ESCÁNER ACTIVO",
            body = "Escaneando sistemas del vehículo. No desconecte el adaptador OBD2.",
            action = "OPEN_SCANNER",
            category = NotificationCompat.CATEGORY_SERVICE
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 6: RED MESH P2P
    // ══════════════════════════════════════════════════════════════════

    /**
     * Notificación cuando el radar Mesh está activo.
     * NOTA: Ya NO es setOngoing(true) para evitar que quede permanente
     * y bloquee las siguientes notificaciones al deslizar.
     */
    fun showMeshRadarActiveNotification(context: Context, activePeersCount: Int) {
        init(context)
        val text = if (activePeersCount > 0) {
            "$activePeersCount nodo(s) enlazado(s) vía radio P2P"
        } else {
            "Radar emitiendo balizas Wi-Fi & Bluetooth (Sin internet)"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_HOME"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9_100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESH_NETWORK)
            .setSmallIcon(R.drawable.ic_elysium_notification)
            .setContentTitle("🌐 Red Mesh Elysium: Radar Activo")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(false) // Ya NO permanente — se puede deslizar sin bloquear
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))

        try {
            NotificationManagerCompat.from(context).notify(9_100, builder.build())
        } catch (_: Exception) {}
    }

    /** Cancelar la notificación de Mesh Radar cuando se detiene */
    fun dismissMeshRadarNotification(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(9_100) } catch (_: Exception) {}
    }

    /** Pop-up cuando se enlaza un nuevo peer en la Red Mesh */
    fun showMeshPeerConnectedPopUp(context: Context, peerName: String, ipAddress: String) {
        postHeadsUpPopUp(
            context = context,
            id = 9_200 + (ipAddress.hashCode() and 0xFFFF),
            channelId = CHANNEL_MESH_NETWORK,
            title = "📡 ¡DISPOSITIVO MESH ENLAZADO!",
            body = "Conectado a $peerName ($ipAddress). Chat P2P habilitado.",
            action = "OPEN_CHAT",
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    /** Pop-up cuando llega un mensaje por Red Mesh */
    fun showMeshMessagePopUp(context: Context, senderName: String, messageText: String) {
        postHeadsUpPopUp(
            context = context,
            id = 9_300 + (messageText.hashCode() and 0xFFFF),
            channelId = CHANNEL_MESH_NETWORK,
            title = "💬 Mensaje Mesh de $senderName",
            body = messageText.take(120),
            action = "OPEN_CHAT",
            category = NotificationCompat.CATEGORY_MESSAGE
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 7: SCREENMIRROR / PROYECCIÓN DE PANTALLA
    // ══════════════════════════════════════════════════════════════════

    /** Notificación persistente cuando ScreenMirror está transmitiendo */
    fun showScreenMirrorActiveNotification(context: Context) {
        init(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_SCREEN_MIRROR"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 12_100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SCREEN_MIRROR)
            .setSmallIcon(R.drawable.ic_elysium_notification)
            .setContentTitle("📺 ScreenMirror Activo")
            .setContentText("Transmitiendo pantalla. Toca para gestionar.")
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Este SÍ debe ser persistente mientras transmite
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        try {
            NotificationManagerCompat.from(context).notify(12_100, builder.build())
        } catch (_: Exception) {}
    }

    fun dismissScreenMirrorNotification(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(12_100) } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECCIÓN 8: COMMERCE / DELIVERY (Pulperías & Sodas)
    // ══════════════════════════════════════════════════════════════════

    /** Pop-up al USUARIO cuando su pedido cambia de estado */
    fun showCommerceOrderUpdatePopUp(context: Context, orderId: String, merchantName: String, newStatus: String) {
        val (title, body) = when (newStatus) {
            "ACCEPTED" -> "✅ ¡PEDIDO ACEPTADO!" to "$merchantName aceptó tu pedido. Preparando..."
            "PREPARING" -> "👨‍🍳 PREPARANDO TU PEDIDO" to "$merchantName está preparando tu pedido."
            "READY" -> "📦 ¡PEDIDO LISTO!" to "Tu pedido en $merchantName está listo para recoger."
            "IN_TRANSIT" -> "🚴 EN CAMINO" to "Tu pedido de $merchantName está en camino."
            "DELIVERED" -> "🏁 ¡ENTREGADO!" to "Tu pedido de $merchantName ha sido entregado."
            "CANCELLED" -> "❌ PEDIDO CANCELADO" to "Tu pedido en $merchantName fue cancelado."
            else -> "📦 Actualización de pedido" to "Estado: $newStatus"
        }
        postHeadsUpPopUp(
            context = context,
            id = 13_100 + (orderId.hashCode() and 0xFFFF),
            channelId = CHANNEL_SERVICES,
            title = title,
            body = body,
            action = "OPEN_SERVICES",
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  VIEWMODEL-LEVEL OBSERVATION HELPERS
    //  (Llámese desde el ViewModel, NO desde un @Composable)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Procesa cambios de estado de un viaje y dispara la notificación apropiada.
     * Llamar desde el collector de `rideDao.observeRequest()` en el ViewModel.
     */
    fun processRideStateChange(
        context: Context,
        requestId: String,
        effectiveState: String,
        isDriver: Boolean,
        partyName: String?,
        price: Double,
        pickupAddress: String,
        destAddress: String,
        currency: String
    ) {
        if (effectiveState == lastKnownRideState) return
        lastKnownRideState = effectiveState

        when (effectiveState) {
            "ACCEPTED" -> showOfferAcceptedPopUp(
                context, requestId,
                partyName ?: if (isDriver) "El pasajero" else "Tu conductor",
                price, isDriver
            )
            "ARRIVED" -> if (!isDriver) showDriverArrivedPopUp(context, requestId, pickupAddress)
            "IN_PROGRESS" -> showTripStartedPopUp(context, requestId, destAddress)
            "COMPLETED" -> showTripCompletedPopUp(context, requestId, "$price $currency")
            "CANCELLED" -> showTripCancelledPopUp(
                context, requestId,
                if (isDriver) "el pasajero" else "el conductor", ""
            )
        }
    }

    /**
     * Procesa nuevas ofertas de viaje y dispara pop-up si hay nuevas.
     * Llamar desde el collector de `rideDao.getOffersForRequest()` en el ViewModel.
     */
    fun processNewOffers(context: Context, tripId: String, offers: List<Any>, isDriver: Boolean) {
        if (!isDriver && offers.size > lastKnownOfferCount) {
            // Usar reflexión ligera para acceder a campos — evitar import directo circular
            val latestOffer = offers.lastOrNull()
            if (latestOffer != null) {
                try {
                    val driverName = latestOffer.javaClass.getMethod("getDriverName").invoke(latestOffer) as? String ?: "Conductor"
                    val counterPrice = latestOffer.javaClass.getMethod("getCounterPrice").invoke(latestOffer) as? Double ?: 0.0
                    val status = latestOffer.javaClass.getMethod("getStatus").invoke(latestOffer) as? String ?: ""
                    if (status == "PENDING") {
                        showOfferReceivedPopUp(context, tripId, driverName, counterPrice)
                    }
                } catch (_: Exception) {}
            }
        }
        lastKnownOfferCount = offers.size
    }

    /**
     * Procesa nuevos mensajes de chat de viaje.
     * Llamar desde el collector de `rideDao.getChatMessagesFlow()` en el ViewModel.
     */
    fun processNewChatMessages(
        context: Context,
        messages: List<Any>,
        currentUserId: String
    ) {
        if (messages.size > lastKnownChatCount && messages.isNotEmpty()) {
            val latest = messages.lastOrNull()
            if (latest != null) {
                try {
                    val senderId = latest.javaClass.getMethod("getSenderId").invoke(latest) as? String ?: ""
                    if (senderId != currentUserId) {
                        val senderName = latest.javaClass.getMethod("getSenderName").invoke(latest) as? String ?: "Usuario"
                        val senderRole = latest.javaClass.getMethod("getSenderRole").invoke(latest) as? String ?: ""
                        val text = latest.javaClass.getMethod("getTextContent").invoke(latest) as? String
                        showRideChatMessagePopUp(context, senderName, senderRole, text)
                    }
                } catch (_: Exception) {}
            }
        }
        lastKnownChatCount = messages.size
    }

    /** Resetear contadores al cambiar de viaje activo */
    fun resetRideCounters() {
        lastKnownOfferCount = 0
        lastKnownChatCount = 0
        lastKnownRideState = ""
    }

    // ══════════════════════════════════════════════════════════════════
    //  MOTOR CENTRAL DE DISPARO HEADS-UP (POP-UP FLOTANTE)
    // ══════════════════════════════════════════════════════════════════

    private fun postHeadsUpPopUp(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        action: String = "OPEN_HOME",
        category: String? = null
    ) {
        init(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_elysium_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)

        if (category != null) {
            builder.setCategory(category)
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: Exception) {}
    }
}
