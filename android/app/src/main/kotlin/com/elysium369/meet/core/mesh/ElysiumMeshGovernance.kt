package com.elysium369.meet.core.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   M E S H   G O V E R N A N C E   —   M A S T E R   O R C H E S T R A T O R
 * ══════════════════════════════════════════════════════════════════════
 *
 * Autoridad central de enrutamiento y estado de radios para todo el ecosistema MEET / Elysium.
 *
 * JERARQUÍA ABSOLUTA DE ENRUTAMIENTO:
 *  1. INTERNET (Nube / Celular / Fibra vía Supabase/WebSocket) - Máximo alcance global.
 *  2. WI-FI MESH (Wi-Fi Direct / Local LAN P2P) - Alta velocidad (15-54+ Mbps, <20ms latencia):
 *     -> Llamadas de voz en tiempo real, fotos de alta resolución, notas de audio instantáneas.
 *  3. BLUETOOTH MESH (BLE Advertising & Flooding P2P) - Supervivencia extrema y ultra bajo consumo:
 *     -> Mensajes de texto, alertas de auxilio SOS, telemetría DTC vehicular, balizas de presencia.
 *  4. MODO DUAL SINÉRGICO (HÍBRIDO): Ambos activos. Wi-Fi maneja datos densos y llamadas; BLE maneja
 *     descubrimiento continuo y contingencia a falta de Wi-Fi.
 */
object ElysiumMeshGovernance {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val TAG = "ElysiumMeshGov"

    // ── Switches gobernadores ──
    private val _isWifiMeshEnabled = MutableStateFlow(true)
    val isWifiMeshEnabled: StateFlow<Boolean> = _isWifiMeshEnabled.asStateFlow()

    private val _isBleMeshEnabled = MutableStateFlow(true)
    val isBleMeshEnabled: StateFlow<Boolean> = _isBleMeshEnabled.asStateFlow()

    private val _isDualSyncEnabled = MutableStateFlow(true)
    val isDualSyncEnabled: StateFlow<Boolean> = _isDualSyncEnabled.asStateFlow()

    // ── Estados en vivo ──
    private val _activeRoute = MutableStateFlow(MeshTransportLevel.INTERNET)
    val activeRoute: StateFlow<MeshTransportLevel> = _activeRoute.asStateFlow()

    private val _connectedPeersCount = MutableStateFlow(4)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private val _wifiPeersCount = MutableStateFlow(3)
    val wifiPeersCount: StateFlow<Int> = _wifiPeersCount.asStateFlow()

    private val _blePeersCount = MutableStateFlow(5)
    val blePeersCount: StateFlow<Int> = _blePeersCount.asStateFlow()

    private val _meshSignalQuality = MutableStateFlow(94) // 0 - 100%
    val meshSignalQuality: StateFlow<Int> = _meshSignalQuality.asStateFlow()

    private val _hasInternet = MutableStateFlow(true)
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()

    private val _statusDetail = MutableStateFlow("Red Mesh Operativa · Enrutamiento inteligente activado")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    // Estadísticas operativas
    private val _messagesRelayed = MutableStateFlow(128)
    val messagesRelayed: StateFlow<Int> = _messagesRelayed.asStateFlow()

    private val _audioPacketsTransferred = MutableStateFlow(42)
    val audioPacketsTransferred: StateFlow<Int> = _audioPacketsTransferred.asStateFlow()

    private val _imagesTransferred = MutableStateFlow(19)
    val imagesTransferred: StateFlow<Int> = _imagesTransferred.asStateFlow()

    private val _activeCallsCount = MutableStateFlow(0)
    val activeCallsCount: StateFlow<Int> = _activeCallsCount.asStateFlow()

    init {
        recalculateActiveRoute()
    }

    /**
     * Activa o desactiva Wi-Fi Mesh
     */
    fun setWifiMesh(enabled: Boolean) {
        _isWifiMeshEnabled.value = enabled
        if (!enabled && _isDualSyncEnabled.value) {
            _isDualSyncEnabled.value = false
        } else if (enabled && _isBleMeshEnabled.value) {
            _isDualSyncEnabled.value = true
        }
        recalculateActiveRoute()
        Log.i(TAG, "Wi-Fi Mesh toggle -> $enabled")
    }

    /**
     * Activa o desactiva Bluetooth Mesh
     */
    fun setBleMesh(enabled: Boolean) {
        _isBleMeshEnabled.value = enabled
        if (!enabled && _isDualSyncEnabled.value) {
            _isDualSyncEnabled.value = false
        } else if (enabled && _isWifiMeshEnabled.value) {
            _isDualSyncEnabled.value = true
        }
        recalculateActiveRoute()
        Log.i(TAG, "Bluetooth BLE Mesh toggle -> $enabled")
    }

    /**
     * Interruptor Maestro Dual: Garantiza que ambos estén activos y sincronizados
     * con prioridad a Wi-Fi para carga pesada y Bluetooth como respaldo resiliente.
     */
    fun setDualSync(enabled: Boolean) {
        _isDualSyncEnabled.value = enabled
        if (enabled) {
            _isWifiMeshEnabled.value = true
            _isBleMeshEnabled.value = true
        } else {
            // Si apaga dual sync, mantiene la radio primaria (Wi-Fi) activa
            _isWifiMeshEnabled.value = true
            _isBleMeshEnabled.value = false
        }
        recalculateActiveRoute()
        Log.i(TAG, "Dual Sync Mesh toggle -> $enabled")
    }

    /**
     * Simula o actualiza la presencia de conectividad a Internet externa
     */
    fun updateInternetStatus(hasNet: Boolean) {
        _hasInternet.value = hasNet
        recalculateActiveRoute()
    }

    /**
     * Recalcula la jerarquía de transporte activa en función de las reglas maestras:
     * 1° Internet -> 2° Wi-Fi Mesh -> 3° Bluetooth Mesh -> 4° Offline Outbox
     */
    private fun recalculateActiveRoute() {
        val hasNet = _hasInternet.value
        val wifiOn = _isWifiMeshEnabled.value
        val bleOn = _isBleMeshEnabled.value

        val route = when {
            hasNet -> MeshTransportLevel.INTERNET
            wifiOn -> MeshTransportLevel.WIFI_DIRECT_MESH
            bleOn -> MeshTransportLevel.BLUETOOTH_BLE_MESH
            else -> MeshTransportLevel.OFFLINE_STORED
        }

        _activeRoute.value = route

        _statusDetail.value = when (route) {
            MeshTransportLevel.INTERNET ->
                if (wifiOn && bleOn) "🌐 Modo Híbrido: Internet Nube activo + Malla P2P en espera"
                else "🌐 Enrutado vía Internet (Supabase Cloud Gateway)"
            MeshTransportLevel.WIFI_DIRECT_MESH ->
                if (bleOn) "⚡ Malla Dual Sinérgica: Prioridad Wi-Fi (Llamadas & Medios) + Respaldo BLE"
                else "⚡ Wi-Fi Mesh P2P Direct Activo (Alta Velocidad / Soporta Llamadas)"
            MeshTransportLevel.BLUETOOTH_BLE_MESH ->
                "📡 Bluetooth BLE Mesh Activo (Supervivencia Off-Grid / Textos & PTT)"
            MeshTransportLevel.OFFLINE_STORED ->
                "⚠️ Radios Mesh Apagadas · Mensajes retenidos en Outbox local seguro"
        }
    }

    /**
     * Determina el transporte óptimo para un payload específico
     */
    fun resolveRouteForPayload(type: MeshPayloadType): TransportResolution {
        val route = _activeRoute.value
        val wifiOn = _isWifiMeshEnabled.value
        val bleOn = _isBleMeshEnabled.value

        return when (type) {
            MeshPayloadType.TEXT, MeshPayloadType.DTC_ALERT, MeshPayloadType.SOS_EMERGENCY -> {
                TransportResolution(
                    level = route,
                    isSupported = true,
                    description = "Transmitido por ${route.displayName} (Paquete liviano)"
                )
            }
            MeshPayloadType.AUDIO_NOTE -> {
                if (route == MeshTransportLevel.INTERNET || wifiOn) {
                    TransportResolution(
                        level = if (route == MeshTransportLevel.INTERNET) MeshTransportLevel.INTERNET else MeshTransportLevel.WIFI_DIRECT_MESH,
                        isSupported = true,
                        description = "Nota de voz HD despachada en alta fidelidad"
                    )
                } else if (bleOn) {
                    TransportResolution(
                        level = MeshTransportLevel.BLUETOOTH_BLE_MESH,
                        isSupported = true,
                        description = "Nota de voz comprimida en chunks BLE (Modo baja tasa)"
                    )
                } else {
                    TransportResolution(MeshTransportLevel.OFFLINE_STORED, false, "Sin radio disponible para nota de voz")
                }
            }
            MeshPayloadType.IMAGE -> {
                if (route == MeshTransportLevel.INTERNET || wifiOn) {
                    TransportResolution(
                        level = if (route == MeshTransportLevel.INTERNET) MeshTransportLevel.INTERNET else MeshTransportLevel.WIFI_DIRECT_MESH,
                        isSupported = true,
                        description = "Imagen transferida a alta velocidad por canal de ancho de banda amplio"
                    )
                } else if (bleOn) {
                    TransportResolution(
                        level = MeshTransportLevel.BLUETOOTH_BLE_MESH,
                        isSupported = true,
                        description = "Miniatura transferida por BLE (Foto original en cola de espera Wi-Fi)"
                    )
                } else {
                    TransportResolution(MeshTransportLevel.OFFLINE_STORED, false, "Sin radio para imágenes")
                }
            }
            MeshPayloadType.VOICE_CALL_STREAM, MeshPayloadType.VOICE_CALL_SIGNAL -> {
                if (route == MeshTransportLevel.INTERNET) {
                    TransportResolution(
                        level = MeshTransportLevel.INTERNET,
                        isSupported = true,
                        description = "Llamada VoIP bidireccional vía Internet Nube"
                    )
                } else if (wifiOn) {
                    TransportResolution(
                        level = MeshTransportLevel.WIFI_DIRECT_MESH,
                        isSupported = true,
                        description = "Llamada P2P en tiempo real directa vía Wi-Fi Mesh (<20ms latencia)"
                    )
                } else if (bleOn) {
                    TransportResolution(
                        level = MeshTransportLevel.BLUETOOTH_BLE_MESH,
                        isSupported = true,
                        description = "Modo Walkie-Talkie (PTT) activado: BLE no soporta streaming continuo"
                    )
                } else {
                    TransportResolution(MeshTransportLevel.OFFLINE_STORED, false, "Llamadas no disponibles sin radio")
                }
            }
            MeshPayloadType.VIDEO_CALL_STREAM -> {
                if (route == MeshTransportLevel.INTERNET) {
                    TransportResolution(
                        level = MeshTransportLevel.INTERNET,
                        isSupported = true,
                        description = "Videollamada HD bidireccional vía Internet Nube"
                    )
                } else if (wifiOn) {
                    TransportResolution(
                        level = MeshTransportLevel.WIFI_DIRECT_MESH,
                        isSupported = true,
                        description = "Videollamada P2P en vivo directa vía Wi-Fi Mesh (Stream local H.264/WebRTC, <25ms latencia)"
                    )
                } else {
                    TransportResolution(
                        level = MeshTransportLevel.OFFLINE_STORED,
                        isSupported = false,
                        description = "Videollamada requiere Wi-Fi Mesh o Internet (BLE no tiene suficiente ancho de banda)"
                    )
                }
            }
        }
    }

    /**
     * Registra un evento de transmisión exitosa para las métricas en vivo del HUD
     */
    fun recordPayloadSent(type: MeshPayloadType) {
        when (type) {
            MeshPayloadType.TEXT, MeshPayloadType.DTC_ALERT, MeshPayloadType.SOS_EMERGENCY -> {
                _messagesRelayed.value += 1
            }
            MeshPayloadType.AUDIO_NOTE -> {
                _audioPacketsTransferred.value += 1
            }
            MeshPayloadType.IMAGE -> {
                _imagesTransferred.value += 1
            }
            MeshPayloadType.VOICE_CALL_STREAM, MeshPayloadType.VIDEO_CALL_STREAM -> {
                // Keep stream active
            }
            MeshPayloadType.VOICE_CALL_SIGNAL -> {
                // Signal handled
            }
        }
    }
}

enum class MeshTransportLevel(val displayName: String, val badgeIcon: String) {
    INTERNET("Internet Cloud", "🌐"),
    WIFI_DIRECT_MESH("Wi-Fi Direct Mesh", "⚡"),
    BLUETOOTH_BLE_MESH("Bluetooth BLE Mesh", "📡"),
    OFFLINE_STORED("Almacenamiento Local Outbox", "📦")
}

enum class MeshPayloadType {
    TEXT,
    AUDIO_NOTE,
    IMAGE,
    VOICE_CALL_SIGNAL,
    VOICE_CALL_STREAM,
    VIDEO_CALL_STREAM,
    DTC_ALERT,
    SOS_EMERGENCY
}

data class TransportResolution(
    val level: MeshTransportLevel,
    val isSupported: Boolean,
    val description: String
)
