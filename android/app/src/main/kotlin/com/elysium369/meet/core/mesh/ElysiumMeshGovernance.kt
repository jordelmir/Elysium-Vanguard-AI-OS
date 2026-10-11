package com.elysium369.meet.core.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

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

    // ── Switches gobernadores lógicos ──
    private val _isWifiMeshEnabled = MutableStateFlow(true)
    val isWifiMeshEnabled: StateFlow<Boolean> = _isWifiMeshEnabled.asStateFlow()

    private val _isBleMeshEnabled = MutableStateFlow(true)
    val isBleMeshEnabled: StateFlow<Boolean> = _isBleMeshEnabled.asStateFlow()

    private val _isDualSyncEnabled = MutableStateFlow(true)
    val isDualSyncEnabled: StateFlow<Boolean> = _isDualSyncEnabled.asStateFlow()

    // ── Estados de Hardware REAL (Detección en tiempo real en Android) ──
    private val _isWifiHardwareOn = MutableStateFlow(true)
    val isWifiHardwareOn: StateFlow<Boolean> = _isWifiHardwareOn.asStateFlow()

    private val _isBleHardwareOn = MutableStateFlow(true)
    val isBleHardwareOn: StateFlow<Boolean> = _isBleHardwareOn.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _localIpAddress = MutableStateFlow<String?>(null)
    val localIpAddress: StateFlow<String?> = _localIpAddress.asStateFlow()

    private val _hardwareStatusSummary = MutableStateFlow("Radios inicializando...")
    val hardwareStatusSummary: StateFlow<String> = _hardwareStatusSummary.asStateFlow()

    // ── Pares físicos reales descubiertos en la Malla ──
    private val _discoveredPhysicalPeers = MutableStateFlow<List<PhysicalMeshPeer>>(emptyList())
    val discoveredPhysicalPeers: StateFlow<List<PhysicalMeshPeer>> = _discoveredPhysicalPeers.asStateFlow()

    private val _activeLinkedPeer = MutableStateFlow<PhysicalMeshPeer?>(null)
    val activeLinkedPeer: StateFlow<PhysicalMeshPeer?> = _activeLinkedPeer.asStateFlow()

    // ── Estados en vivo ──
    private val _activeRoute = MutableStateFlow(MeshTransportLevel.INTERNET)
    val activeRoute: StateFlow<MeshTransportLevel> = _activeRoute.asStateFlow()

    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private val _wifiPeersCount = MutableStateFlow(0)
    val wifiPeersCount: StateFlow<Int> = _wifiPeersCount.asStateFlow()

    private val _blePeersCount = MutableStateFlow(0)
    val blePeersCount: StateFlow<Int> = _blePeersCount.asStateFlow()

    private val _meshSignalQuality = MutableStateFlow(94) // 0 - 100%
    val meshSignalQuality: StateFlow<Int> = _meshSignalQuality.asStateFlow()

    private val _hasInternet = MutableStateFlow(true)
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()

    private val _statusDetail = MutableStateFlow("Red Mesh Operativa · Enrutamiento inteligente activado")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    // Estadísticas operativas
    private val _messagesRelayed = MutableStateFlow(0)
    val messagesRelayed: StateFlow<Int> = _messagesRelayed.asStateFlow()

    private val _audioPacketsTransferred = MutableStateFlow(0)
    val audioPacketsTransferred: StateFlow<Int> = _audioPacketsTransferred.asStateFlow()

    private val _imagesTransferred = MutableStateFlow(0)
    val imagesTransferred: StateFlow<Int> = _imagesTransferred.asStateFlow()

    private val _activeCallsCount = MutableStateFlow(0)
    val activeCallsCount: StateFlow<Int> = _activeCallsCount.asStateFlow()

    private var isObserverInitialized = false

    init {
        recalculateActiveRoute()
    }

    private var applicationContextRef: Context? = null

    /**
     * Refresca síncronamente el estado real de hardware de radios
     */
    fun refreshHardwareState(context: Context? = null) {
        val ctx = context?.applicationContext ?: applicationContextRef ?: return
        try {
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            val btEnabled = btAdapter?.isEnabled == true
            val wifiEnabled = wifiManager?.isWifiEnabled == true
            val ip = resolveLocalIpAddress()

            _isBleHardwareOn.value = btEnabled
            _isBleMeshEnabled.value = btEnabled
            val effectiveWifi = wifiEnabled || (ip != null)
            _isWifiHardwareOn.value = effectiveWifi
            _isWifiMeshEnabled.value = effectiveWifi
            _localIpAddress.value = ip

            if (btEnabled && effectiveWifi) {
                _isDualSyncEnabled.value = true
            } else if (!btEnabled && !effectiveWifi) {
                _isDualSyncEnabled.value = false
            }

            updateHardwareSummary()
            recalculateActiveRoute()
        } catch (e: Exception) {
            Log.w(TAG, "Error refrescando hardware state", e)
        }
    }

    /**
     * Inicializa la observación continua de hardware (Bluetooth y Wi-Fi) en tiempo real
     * mediante BroadcastReceivers del sistema y NetworkCallbacks de Android.
     */
    fun initHardwareObserver(context: Context) {
        if (isObserverInitialized) return
        isObserverInitialized = true
        val appContext = context.applicationContext
        applicationContextRef = appContext

        refreshHardwareState(appContext)

        // 1. BroadcastReceiver para eventos de hardware de Bluetooth y Wi-Fi
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        val isBtOn = state == BluetoothAdapter.STATE_ON
                        _isBleHardwareOn.value = isBtOn
                        _isBleMeshEnabled.value = isBtOn
                        if (!isBtOn) {
                            if (_isDualSyncEnabled.value) _isDualSyncEnabled.value = false
                        } else if (_isWifiHardwareOn.value) {
                            _isDualSyncEnabled.value = true
                        }
                        updateHardwareSummary()
                        recalculateActiveRoute()
                        Log.i(TAG, "Hardware Bluetooth reactivo -> $isBtOn (state=$state)")
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        val isWifiOn = state == WifiManager.WIFI_STATE_ENABLED
                        _isWifiHardwareOn.value = isWifiOn
                        _isWifiMeshEnabled.value = isWifiOn
                        if (!isWifiOn) {
                            _isWifiConnected.value = false
                            _localIpAddress.value = null
                            if (_isDualSyncEnabled.value) _isDualSyncEnabled.value = false
                        } else {
                            _localIpAddress.value = resolveLocalIpAddress()
                            if (_isBleHardwareOn.value) _isDualSyncEnabled.value = true
                        }
                        updateHardwareSummary()
                        recalculateActiveRoute()
                        Log.i(TAG, "Hardware Wi-Fi reactivo -> $isWifiOn (state=$state)")
                    }
                    "android.net.wifi.WIFI_AP_STATE_CHANGED" -> {
                        val apState = intent.getIntExtra("wifi_state", 11)
                        val isApOn = apState == 13 // WIFI_AP_STATE_ENABLED
                        val ip = resolveLocalIpAddress()
                        _isWifiHardwareOn.value = isApOn || (ip != null)
                        _isWifiMeshEnabled.value = isApOn || (ip != null)
                        _localIpAddress.value = ip
                        updateHardwareSummary()
                        recalculateActiveRoute()
                        Log.i(TAG, "Hotspot Wi-Fi AP reactivo -> apState=$apState (ip=$ip)")
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    @Suppress("DEPRECATION")
                    ConnectivityManager.CONNECTIVITY_ACTION -> {
                        _localIpAddress.value = resolveLocalIpAddress()
                        updateHardwareSummary()
                        recalculateActiveRoute()
                    }
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(TAG, "Receptor dinámico de hardware Bluetooth & Wi-Fi registrado exitosamente")
        } catch (e: Exception) {
            try {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
                Log.i(TAG, "Receptor de hardware registrado como fallback sin flags")
            } catch (ex: Exception) {
                Log.e(TAG, "Error registrando broadcast receiver de radios", ex)
            }
        }

        // 2. ConnectivityManager NetworkCallback para vigilar internet real y transporte Wi-Fi
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.let { cm ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            val caps = cm.getNetworkCapabilities(network)
                            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                            val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true ||
                                         caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                            _hasInternet.value = hasNet
                            if (isWifi) {
                                _isWifiConnected.value = true
                                _localIpAddress.value = resolveLocalIpAddress()
                            }
                            updateHardwareSummary()
                            recalculateActiveRoute()
                        }

                        override fun onLost(network: Network) {
                            val activeNet = cm.activeNetwork
                            val caps = cm.getNetworkCapabilities(activeNet)
                            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                            val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                            _hasInternet.value = hasNet
                            _isWifiConnected.value = isWifi
                            _localIpAddress.value = resolveLocalIpAddress()
                            updateHardwareSummary()
                            recalculateActiveRoute()
                        }

                        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            val hasNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                                         networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            _hasInternet.value = hasNet
                            if (isWifi) {
                                _isWifiConnected.value = true
                                _localIpAddress.value = resolveLocalIpAddress()
                            }
                            updateHardwareSummary()
                            recalculateActiveRoute()
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando default network callback", e)
            }

            try {
                val wifiRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                cm.registerNetworkCallback(wifiRequest, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isWifiHardwareOn.value = true
                        _isWifiMeshEnabled.value = true
                        _isWifiConnected.value = true
                        _localIpAddress.value = resolveLocalIpAddress()
                        updateHardwareSummary()
                        recalculateActiveRoute()
                    }

                    override fun onLost(network: Network) {
                        _isWifiConnected.value = false
                        _localIpAddress.value = resolveLocalIpAddress()
                        updateHardwareSummary()
                        recalculateActiveRoute()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando wifi network callback", e)
            }
        }
    }

    /**
     * Resuelve la dirección IPv4 local asignada por Wi-Fi o Hotspot (ignora IP celular)
     */
    fun resolveLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

            val preferredInterfaces = interfaces.filter { intf ->
                !intf.isLoopback && intf.isUp && (
                    intf.name.contains("wlan", ignoreCase = true) ||
                    intf.name.contains("ap", ignoreCase = true) ||
                    intf.name.contains("p2p", ignoreCase = true) ||
                    intf.name.contains("eth", ignoreCase = true)
                )
            }
            for (intf in preferredInterfaces) {
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                if (intf.name.contains("rmnet", ignoreCase = true) ||
                    intf.name.contains("ccmni", ignoreCase = true) ||
                    intf.name.contains("dummy", ignoreCase = true) ||
                    intf.name.contains("tun", ignoreCase = true)) continue

                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo IP local", e)
        }
        return null
    }

    private fun updateHardwareSummary() {
        val wifi = if (_isWifiHardwareOn.value) "Wi-Fi HW ON" else "Wi-Fi HW OFF"
        val ble = if (_isBleHardwareOn.value) "BLE HW ON" else "BLE HW OFF"
        val ip = _localIpAddress.value ?: "Sin IP local"
        val net = if (_hasInternet.value) "Nube OK" else "Off-Grid Directo"
        _hardwareStatusSummary.value = "$wifi · $ble · $net · $ip"
    }

    /**
     * Actualiza la lista de nodos físicos en alcance y recalcula los contadores del HUD
     */
    fun updateDiscoveredPeers(peers: List<PhysicalMeshPeer>) {
        _discoveredPhysicalPeers.value = peers
        _connectedPeersCount.value = peers.size
        _wifiPeersCount.value = peers.size
        _blePeersCount.value = peers.size
        _meshSignalQuality.value = if (peers.isNotEmpty()) 98 else if (_isWifiHardwareOn.value || _isBleHardwareOn.value) 75 else 0
    }

    /**
     * Vincula un nodo físico específico mediante el botón 1-Toque
     */
    fun linkPeer(peer: PhysicalMeshPeer) {
        _activeLinkedPeer.value = peer
        _discoveredPhysicalPeers.update { list ->
            list.map { if (it.nodeId == peer.nodeId) it.copy(isLinked = true) else it.copy(isLinked = false) }
        }
        Log.i(TAG, "Nodo enlazado 1-Toque: ${peer.deviceName} (${peer.ipAddress}:${peer.port})")
    }

    fun unlinkPeer() {
        _activeLinkedPeer.value = null
        _discoveredPhysicalPeers.update { list ->
            list.map { it.copy(isLinked = false) }
        }
    }

    /**
     * Activa o desactiva Wi-Fi Mesh
     */
    fun setWifiMesh(enabled: Boolean) {
        _isWifiMeshEnabled.value = enabled
        if (!enabled && _isDualSyncEnabled.value) {
            _isDualSyncEnabled.value = false
        } else if (enabled && _isBleMeshEnabled.value && _isBleHardwareOn.value) {
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
        } else if (enabled && _isWifiMeshEnabled.value && _isWifiHardwareOn.value) {
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
     * Recalcula la jerarquía de transporte activa en función de las reglas maestras
     * y el estado REAL de los chips de hardware del teléfono:
     * 1° Internet -> 2° Wi-Fi Mesh -> 3° Bluetooth Mesh -> 4° Offline Outbox
     */
    private fun recalculateActiveRoute() {
        val hasNet = _hasInternet.value
        val wifiHw = _isWifiHardwareOn.value
        val bleHw = _isBleHardwareOn.value
        val wifiSw = _isWifiMeshEnabled.value
        val bleSw = _isBleMeshEnabled.value

        val effectiveWifi = wifiHw && wifiSw
        val effectiveBle = bleHw && bleSw

        val route = when {
            hasNet -> MeshTransportLevel.INTERNET
            effectiveWifi -> MeshTransportLevel.WIFI_DIRECT_MESH
            effectiveBle -> MeshTransportLevel.BLUETOOTH_BLE_MESH
            else -> MeshTransportLevel.OFFLINE_STORED
        }

        _activeRoute.value = route

        _statusDetail.value = when {
            !wifiHw && !bleHw ->
                "⚠️ Radios Wi-Fi y Bluetooth apagadas en el teléfono · Actívalas para operar Mesh"
            !wifiHw && wifiSw ->
                "⚠️ Wi-Fi apagado en dispositivo · Enrutamiento degradado a BLE Mesh"
            !bleHw && bleSw ->
                "⚠️ Bluetooth apagado en dispositivo · Wi-Fi P2P activo"
            route == MeshTransportLevel.INTERNET ->
                if (effectiveWifi && effectiveBle) "🌐 Modo Híbrido: Internet Nube activo + Malla P2P en espera"
                else "🌐 Enrutado vía Internet (Supabase Cloud Gateway)"
            route == MeshTransportLevel.WIFI_DIRECT_MESH ->
                if (effectiveBle) "⚡ Malla Dual Sinérgica: Prioridad Wi-Fi (Llamadas & Medios) + Respaldo BLE"
                else "⚡ Wi-Fi Mesh P2P Direct Activo (Alta Velocidad / Soporta Llamadas)"
            route == MeshTransportLevel.BLUETOOTH_BLE_MESH ->
                "📡 Bluetooth BLE Mesh Activo (Supervivencia Off-Grid / Textos & PTT)"
            else ->
                "⚠️ Radios Mesh Desactivadas · Mensajes retenidos en Outbox local seguro"
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

data class PhysicalMeshPeer(
    val nodeId: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int = 36901,
    val role: String = "CONDUCTOR",
    val lastSeenMs: Long = System.currentTimeMillis(),
    val isLinked: Boolean = false
)

