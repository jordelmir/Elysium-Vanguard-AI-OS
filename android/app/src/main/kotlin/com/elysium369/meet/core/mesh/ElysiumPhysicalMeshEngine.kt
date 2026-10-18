package com.elysium369.meet.core.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.room.Room
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.ChatMessageEntity
import com.elysium369.meet.data.local.entities.FleetMemberEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   P H Y S I C A L   M E S H   E N G I N E
 * ══════════════════════════════════════════════════════════════════════
 *
 * Motor físico completo de comunicaciones Peer-to-Peer 100% Off-Grid para Android.
 * Funciona de forma completamente independiente de Supabase o Internet:
 *
 * 1. RADAR Y BALIZA UDP (Puerto 36900): Descubrimiento automático en <1 segundo
 *    entre cualquier par de teléfonos en la misma red Wi-Fi o Hotspot móvil.
 * 2. SERVIDOR Y CLIENTE TCP DE ALTA FIDELIDAD (Puerto 36901):
 *    - Mensajes de texto y alertas DTC instantáneas.
 *    - Notas de voz grabadas (AAC/M4A) transferidas íntegramente.
 *    - Fotos e imágenes HD transferidas y guardadas en caché local.
 * 3. LLAMADAS DE VOZ P2P EN TIEMPO REAL (UDP Puerto 36902):
 *    - Audio bidireccional AudioRecord <-> AudioTrack en vivo (<20ms latencia).
 * 4. VIDEOLLAMADA EN VIVO P2P (UDP/TCP Puerto 36903):
 *    - Streaming directo de fotogramas de cámara renderizados en el visor HUD.
 * 5. ENLACE 1-TOQUE Y EMPAREJAMIENTO QR OFFLINE:
 *    - Cero configuración, vinculación inmediata entre dispositivos cercanos.
 */
object ElysiumPhysicalMeshEngine {

    private const val TAG = "ElysiumMeshEngine"
    const val UDP_DISCOVERY_PORT = 36900
    const val TCP_DATA_PORT = 36901
    const val UDP_AUDIO_PORT = 36902
    const val UDP_VIDEO_PORT = 36903

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    var database: MeetDatabase? = null

    // ── Identidad local del nodo ──
    var localNodeId: String = "EVG-NODE"
        private set
    var localDeviceName: String = "Dispositivo MEET"
        private set

    // ── Estados en vivo ──
    private val _isEngineRunning = MutableStateFlow(false)
    val isEngineRunning: StateFlow<Boolean> = _isEngineRunning.asStateFlow()

    private val _remoteVideoFrame = MutableStateFlow<Bitmap?>(null)
    val remoteVideoFrame: StateFlow<Bitmap?> = _remoteVideoFrame.asStateFlow()

    val localVideoFrame: StateFlow<Bitmap?> = ElysiumRealCameraStreamer.localCameraFrame

    // ── Eventos entrantes para la UI ──
    data class IncomingCallSignal(
        val callType: String, // "AUDIO" o "VIDEO"
        val callerId: String,
        val callerName: String,
        val callerIp: String
    )

    private val _incomingCallEvents = MutableSharedFlow<IncomingCallSignal>(extraBufferCapacity = 8)
    val incomingCallEvents: SharedFlow<IncomingCallSignal> = _incomingCallEvents.asSharedFlow()

    private val _callAcceptedEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val callAcceptedEvents: SharedFlow<String> = _callAcceptedEvents.asSharedFlow()

    private val _callTerminatedEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val callTerminatedEvents: SharedFlow<String> = _callTerminatedEvents.asSharedFlow()

    private val _messageReceivedEvents = MutableSharedFlow<ChatMessageEntity>(extraBufferCapacity = 16)
    val messageReceivedEvents: SharedFlow<ChatMessageEntity> = _messageReceivedEvents.asSharedFlow()

    // Control de sockets
    private var discoverySocket: DatagramSocket? = null
    private var dataServerSocket: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // Streaming de llamada de voz
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isAudioStreamingActive = false
    private var audioSocket: DatagramSocket? = null

    // Tabla de pares descubiertos localmente
    private val discoveredPeersMap = ConcurrentHashMap<String, PhysicalMeshPeer>()

    /** Public API for BluetoothRfcommTransport to push received messages */
    fun emitReceivedMessage(msg: ChatMessageEntity) {
        engineScope.launch {
            _messageReceivedEvents.emit(msg)
            // ── Notification: mensaje recibido via Bluetooth ──
            appContext?.let { ctx ->
                val senderName = discoveredPeersMap[msg.senderId]?.deviceName ?: msg.senderId
                val content = msg.messageText ?: if (msg.messageType == "AUDIO") "Nota de voz recibida" else "Archivo recibido"
                com.elysium369.meet.core.notification.ElysiumNotificationHub.showMeshMessagePopUp(
                    ctx, senderName, content
                )
            }
        }
    }

    /** Public API for BluetoothRfcommTransport to push incoming call signals */
    fun emitIncomingCall(callType: String, callerId: String, callerName: String, callerIp: String) {
        engineScope.launch {
            _incomingCallEvents.emit(IncomingCallSignal(callType, callerId, callerName, callerIp))
            // ── Notification: llamada entrante via Bluetooth ──
            appContext?.let { ctx ->
                val typeLabel = if (callType == "VIDEO") "Video" else "Voz"
                com.elysium369.meet.core.notification.ElysiumNotificationHub.showIncomingCallPopUp(
                    ctx, callerName, typeLabel
                )
            }
        }
    }

    /**
     * Inicializa el motor físico en la aplicación
     */
    fun start(context: Context, db: MeetDatabase? = null) {
        if (_isEngineRunning.value) return
        appContext = context.applicationContext
        database = db ?: try {
            Room.databaseBuilder(context.applicationContext, MeetDatabase::class.java, "meet_database")
                .fallbackToDestructiveMigration()
                .build()
        } catch (e: Exception) {
            null
        }

        val ctx = appContext ?: return

        // Generar Node ID estable basado en hardware
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)?.take(4)?.uppercase() ?: "NODE"
        val cleanModel = Build.MODEL.replace(" ", "").take(8).uppercase()
        localNodeId = "EVG-$cleanModel-$androidId"
        localDeviceName = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()

        Log.i(TAG, "Iniciando ElysiumPhysicalMeshEngine: NodeId=$localNodeId DeviceName=$localDeviceName")

        // 1. Inicializar observador de hardware
        ElysiumMeshGovernance.initHardwareObserver(ctx)

        // 2. Adquirir MulticastLock para recepción UDP estable
        try {
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("ElysiumMeshMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir MulticastLock", e)
        }

        _isEngineRunning.value = true
        com.elysium369.meet.core.notification.ElysiumNotificationHub.showMeshRadarActiveNotification(ctx, 0)

        // 3. Iniciar listeners de red en background
        startUdpBeaconListener()
        startUdpBeaconBroadcaster()
        startTcpDataServer()
        startAudioStreamReceiver()
        startVideoStreamReceiver()
        startPeerPruningJob()

        // 4. Iniciar transporte Bluetooth RFCOMM (funciona sin Wi-Fi)
        try {
            ElysiumBluetoothRfcommTransport.start(ctx)
            Log.i(TAG, "Bluetooth RFCOMM transport iniciado")
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth RFCOMM no disponible: ${e.message}")
        }

        // 5. Iniciar motor de transferencia de archivos P2P (puerto 36904)
        try {
            ElysiumFileTransferEngine.start(ctx)
            Log.i(TAG, "File Transfer Engine iniciado")
        } catch (e: Exception) {
            Log.w(TAG, "File Transfer Engine no disponible: ${e.message}")
        }
    }

    /**
     * ── 1. BALIZA DE TRANSMISIÓN UDP (Broadcast a la subred cada 2.5s) ──
     */
    private fun startUdpBeaconBroadcaster() {
        engineScope.launch {
            while (isActive && _isEngineRunning.value) {
                if (ElysiumMeshGovernance.isWifiHardwareOn.value) {
                    broadcastBeaconPacket()
                }
                delay(2500)
            }
        }
    }

    fun triggerQuickDiscovery() {
        engineScope.launch {
            repeat(3) {
                broadcastBeaconPacket()
                delay(120)
            }
        }
    }

    private fun broadcastBeaconPacket() {
        val ip = ElysiumMeshGovernance.localIpAddress.value ?: return
        try {
            val socket = DatagramSocket()
            socket.broadcast = true

            val payload = "EVG_BEACON|$localNodeId|$localDeviceName|$ip|$TCP_DATA_PORT|CONDUCTOR|${System.currentTimeMillis()}"
            val bytes = payload.toByteArray(Charsets.UTF_8)

            // 1. Broadcast global
            val globalTarget = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(bytes, bytes.size, globalTarget, UDP_DISCOVERY_PORT)
            socket.send(packet)

            // 2. Broadcast de subred local calculada (ej. 192.168.1.255 o 192.168.43.255)
            val parts = ip.split(".")
            if (parts.size == 4) {
                val subnetBroadcastIp = "${parts[0]}.${parts[1]}.${parts[2]}.255"
                val subnetTarget = InetAddress.getByName(subnetBroadcastIp)
                val subnetPacket = DatagramPacket(bytes, bytes.size, subnetTarget, UDP_DISCOVERY_PORT)
                socket.send(subnetPacket)
            }

            socket.close()
        } catch (e: Exception) {
            // Ignorado si la interfaz de red está cambiando
        }
    }

    /**
     * ── 2. RECEPTOR DE BALIZAS UDP (Descubrimiento en tiempo real) ──
     */
    private fun startUdpBeaconListener() {
        engineScope.launch {
            var socket: DatagramSocket? = null
            while (isActive && _isEngineRunning.value) {
                try {
                    socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                    }
                    discoverySocket = socket
                    val buffer = ByteArray(1024)

                    while (isActive && _isEngineRunning.value) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (raw.startsWith("EVG_BEACON|")) {
                            handleBeaconReceived(raw, packet.address.hostAddress ?: "")
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && _isEngineRunning.value) {
                        delay(1500)
                    }
                } finally {
                    socket?.close()
                }
            }
        }
    }

    private fun handleBeaconReceived(raw: String, packetSenderIp: String) {
        val parts = raw.split("|")
        if (parts.size >= 6) {
            val peerNodeId = parts[1]
            val peerName = parts[2]
            val peerAdvertisedIp = parts[3]
            val peerPort = parts[4].toIntOrNull() ?: TCP_DATA_PORT
            val peerRole = parts[5]

            // Evitar auto-descubrimiento
            if (peerNodeId == localNodeId) return

            // Usar la IP del paquete si la anunciada es loopback o genérica
            val effectiveIp = if (peerAdvertisedIp.startsWith("127.") || peerAdvertisedIp.isEmpty()) packetSenderIp else peerAdvertisedIp

            val existing = discoveredPeersMap[peerNodeId]
            val isLinked = existing?.isLinked ?: (ElysiumMeshGovernance.activeLinkedPeer.value?.nodeId == peerNodeId)

            val updatedPeer = PhysicalMeshPeer(
                nodeId = peerNodeId,
                deviceName = peerName,
                ipAddress = effectiveIp,
                port = peerPort,
                role = peerRole,
                lastSeenMs = System.currentTimeMillis(),
                isLinked = isLinked
            )

            val isNew = (existing == null)
            discoveredPeersMap[peerNodeId] = updatedPeer
            publishPeers()

            if (isNew) {
                appContext?.let { ctx ->
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.showMeshPeerConnectedPopUp(
                        ctx, peerName, effectiveIp
                    )
                }
            }
        }
    }

    private fun publishPeers() {
        val peers = discoveredPeersMap.values.toList().sortedByDescending { it.lastSeenMs }
        ElysiumMeshGovernance.updateDiscoveredPeers(peers)
    }

    private fun startPeerPruningJob() {
        engineScope.launch {
            while (isActive && _isEngineRunning.value) {
                delay(4000)
                val now = System.currentTimeMillis()
                val removed = discoveredPeersMap.values.removeIf { now - it.lastSeenMs > 14000 }
                if (removed) {
                    publishPeers()
                }
            }
        }
    }

    /**
     * ── 3. SERVIDOR DE DATOS TCP (Puerto 36901: Textos, Audios, Fotos, Señales) ──
     */
    private fun startTcpDataServer() {
        engineScope.launch {
            var server: ServerSocket? = null
            while (isActive && _isEngineRunning.value) {
                try {
                    server = ServerSocket(TCP_DATA_PORT).apply {
                        reuseAddress = true
                    }
                    dataServerSocket = server
                    Log.i(TAG, "Servidor TCP Mesh iniciado en puerto $TCP_DATA_PORT")

                    while (isActive && _isEngineRunning.value) {
                        val client = server.accept()
                        engineScope.launch {
                            handleIncomingTcpConnection(client)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && _isEngineRunning.value) {
                        delay(1500)
                    }
                } finally {
                    server?.close()
                }
            }
        }
    }

    private suspend fun handleIncomingTcpConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val header = dis.readUTF() // Formato: "TIPO|ID|SENDER_ID|SENDER_NAME|RECEIVER_ID|EXTRA|DATA_LEN"
            val parts = header.split("|")
            val frameType = parts.getOrNull(0) ?: ""
            val msgId = parts.getOrNull(1) ?: UUID.randomUUID().toString()
            val senderId = parts.getOrNull(2) ?: "peer"
            val senderName = parts.getOrNull(3) ?: "Nodo Desconocido"
            val receiverId = parts.getOrNull(4) ?: localNodeId
            val extra = parts.getOrNull(5) ?: ""
            val dataLen = parts.getOrNull(6)?.toIntOrNull() ?: 0

            val ctx = appContext

            // ── AUTO-LINK: register sender as peer and auto-link if not linked ──
            val clientIpAddr = socket.inetAddress?.hostAddress ?: ""
            if (clientIpAddr.isNotBlank()) {
                val incomingPeer = PhysicalMeshPeer(
                    nodeId = senderId,
                    deviceName = senderName,
                    ipAddress = clientIpAddr,
                    port = TCP_DATA_PORT,
                    role = "CONDUCTOR",
                    lastSeenMs = System.currentTimeMillis(),
                    isLinked = true
                )
                discoveredPeersMap[senderId] = incomingPeer
                publishPeers()
                // Auto-link if we don't have an active linked peer, or it's the same node
                val currentLinked = ElysiumMeshGovernance.activeLinkedPeer.value
                if (currentLinked == null || currentLinked.nodeId == senderId) {
                    ElysiumMeshGovernance.linkPeer(incomingPeer)
                    Log.i(TAG, "Auto-linked peer $senderName ($clientIpAddr) — enlace bidireccional")
                }
            }

            // Handle LINK_REQUEST first (explicit bidirectional link)
            if (frameType == "LINK_REQUEST") {
                Log.i(TAG, "LINK_REQUEST recibido de $senderName ($clientIpAddr)")
                val dos2 = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                dos2.writeUTF("ACK|LINK_ACCEPTED")
                dos2.flush()
                socket.close()
                return@withContext
            }

            when (frameType) {
                "TEXT", "DTC_ALERT", "SOS_EMERGENCY" -> {
                    val textBytes = ByteArray(dataLen)
                    dis.readFully(textBytes)
                    val messageText = String(textBytes, Charsets.UTF_8)

                    val entity = ChatMessageEntity(
                        id = msgId,
                        businessId = "mesh_p2p",
                        senderId = senderId,
                        receiverId = localNodeId,
                        messageText = messageText,
                        messageType = frameType,
                        fileLocalPath = null,
                        fileRemoteUrl = null,
                        durationSeconds = 0,
                        timestamp = System.currentTimeMillis(),
                        status = "DELIVERED_MESH_WIFI"
                    )

                    database?.chatDao()?.insertMessage(entity)
                    _messageReceivedEvents.emit(entity)
                    ElysiumMeshGovernance.recordPayloadSent(MeshPayloadType.TEXT)
                    ctx?.let { c ->
                        com.elysium369.meet.core.notification.ElysiumNotificationHub.showMeshMessagePopUp(
                            c, senderName, messageText
                        )
                    }

                    // Responder confirmación
                    dos.writeUTF("ACK|$msgId")
                    dos.flush()
                }

                "AUDIO" -> {
                    val durationSec = extra.toIntOrNull() ?: 3
                    val audioBytes = ByteArray(dataLen)
                    dis.readFully(audioBytes)

                    val targetDir = ctx?.cacheDir ?: File("/tmp")
                    targetDir.mkdirs()
                    val audioFile = File(targetDir, "mesh_voice_${msgId}.m4a")
                    audioFile.writeBytes(audioBytes)

                    val entity = ChatMessageEntity(
                        id = msgId,
                        businessId = "mesh_p2p",
                        senderId = senderId,
                        receiverId = localNodeId,
                        messageText = null,
                        messageType = "AUDIO",
                        fileLocalPath = audioFile.absolutePath,
                        fileRemoteUrl = null,
                        durationSeconds = durationSec,
                        timestamp = System.currentTimeMillis(),
                        status = "DELIVERED_MESH_WIFI"
                    )

                    database?.chatDao()?.insertMessage(entity)
                    _messageReceivedEvents.emit(entity)
                    ElysiumMeshGovernance.recordPayloadSent(MeshPayloadType.AUDIO_NOTE)

                    dos.writeUTF("ACK|$msgId")
                    dos.flush()
                }

                "IMAGE", "FILE" -> {
                    val isImg = frameType == "IMAGE" || extra.substringAfterLast(".").lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")
                    val defaultName = if (isImg) "mesh_img_${msgId}.jpg" else "mesh_file_${msgId}"
                    val rawName = if (extra.isNotBlank()) extra else defaultName

                    val targetDir = try {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                    } catch (e: Exception) {
                        ctx?.cacheDir ?: File("/tmp").apply { mkdirs() }
                    }

                    var targetFile = File(targetDir, rawName)
                    var counter = 1
                    while (targetFile.exists()) {
                        val baseName = rawName.substringBeforeLast(".")
                        val ext = if (rawName.contains(".")) ".${rawName.substringAfterLast(".")}" else ""
                        targetFile = File(targetDir, "${baseName}_$counter$ext")
                        counter++
                    }

                    val fos = FileOutputStream(targetFile)
                    val bos = BufferedOutputStream(fos)
                    val buffer = ByteArray(32768)
                    var remaining = dataLen
                    while (remaining > 0) {
                        val read = dis.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read == -1) break
                        bos.write(buffer, 0, read)
                        remaining -= read
                    }
                    bos.flush()
                    bos.close()
                    fos.close()

                    val entity = ChatMessageEntity(
                        id = msgId,
                        businessId = "mesh_p2p",
                        senderId = senderId,
                        receiverId = localNodeId,
                        messageText = targetFile.name,
                        messageType = if (isImg) "IMAGE" else "FILE",
                        fileLocalPath = targetFile.absolutePath,
                        fileRemoteUrl = null,
                        durationSeconds = 0,
                        timestamp = System.currentTimeMillis(),
                        status = "DELIVERED_MESH_WIFI"
                    )

                    database?.chatDao()?.insertMessage(entity)
                    _messageReceivedEvents.emit(entity)
                    ElysiumMeshGovernance.recordPayloadSent(if (isImg) MeshPayloadType.IMAGE else MeshPayloadType.TEXT)

                    dos.writeUTF("ACK|$msgId")
                    dos.flush()
                    Log.i(TAG, "✅ Archivo ${targetFile.name} ($dataLen bytes) guardado en ${targetFile.absolutePath}")
                }

                "CALL_SIGNAL" -> {
                    val signal = extra // "RING_AUDIO", "RING_VIDEO", "ACCEPT", "REJECT", "END"
                    val clientIp = socket.inetAddress.hostAddress ?: ""
                    Log.i(TAG, "Señal de llamada recibida: $signal desde $senderId ($clientIp)")
                    when (signal) {
                        "RING_AUDIO" -> {
                            _incomingCallEvents.emit(IncomingCallSignal("AUDIO", senderId, senderName, clientIp))
                            appContext?.let { ctx ->
                                com.elysium369.meet.core.notification.ElysiumNotificationHub.showIncomingCallPopUp(
                                    ctx, senderName, "Voz"
                                )
                            }
                        }
                        "RING_VIDEO" -> {
                            _incomingCallEvents.emit(IncomingCallSignal("VIDEO", senderId, senderName, clientIp))
                            appContext?.let { ctx ->
                                com.elysium369.meet.core.notification.ElysiumNotificationHub.showIncomingCallPopUp(
                                    ctx, senderName, "Video"
                                )
                            }
                        }
                        "ACCEPT" -> {
                            startAudioCallStreaming(clientIp)
                            startVideoCallStreaming(clientIp)
                            _callAcceptedEvents.emit(senderId)
                            appContext?.let { ctx ->
                                com.elysium369.meet.core.notification.ElysiumNotificationHub.dismissIncomingCallNotification(ctx, senderName)
                            }
                        }
                        "END", "REJECT" -> {
                            stopAudioCallStreaming()
                            stopVideoCallStreaming()
                            _callTerminatedEvents.emit(senderId)
                            appContext?.let { ctx ->
                                com.elysium369.meet.core.notification.ElysiumNotificationHub.dismissIncomingCallNotification(ctx, senderName)
                            }
                        }
                    }
                    dos.writeUTF("ACK_SIGNAL")
                    dos.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando conexión TCP entrante", e)
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * ── 4. ENVÍO DE LINK_REQUEST BIDIRECCIONAL ──
     * Cuando un usuario da "conectar" a un peer, envía un LINK_REQUEST TCP
     * para que el otro lado auto-linkee de vuelta.
     */
    fun sendLinkRequest(targetIp: String, targetPort: Int) {
        engineScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetIp, targetPort), 3000)
                val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val header = "LINK_REQUEST|link|$localNodeId|$localDeviceName|||0"
                dos.writeUTF(header)
                dos.flush()
                val ack = dis.readUTF()
                socket.close()
                Log.i(TAG, "LINK_REQUEST enviado a $targetIp → respuesta: $ack")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar LINK_REQUEST a $targetIp: ${e.message}")
            }
        }
    }

    /**
     * ── 5. CLIENTE P2P DE ENVÍO DE MENSAJES Y ARCHIVOS ──
     */
    suspend fun sendTextMessage(
        targetIp: String,
        targetPort: Int,
        message: ChatMessageEntity,
        senderName: String = localDeviceName
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), 3000)
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val textBytes = (message.messageText ?: "").toByteArray(Charsets.UTF_8)
            val header = "${message.messageType}|${message.id}|$localNodeId|$senderName|${message.receiverId}||${textBytes.size}"
            dos.writeUTF(header)
            dos.write(textBytes)
            dos.flush()

            val ack = dis.readUTF()
            socket.close()

            if (ack.startsWith("ACK")) {
                database?.chatDao()?.updateMessageStatus(message.id, "DELIVERED_MESH_WIFI")
                ElysiumMeshGovernance.recordPayloadSent(MeshPayloadType.TEXT)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al enviar mensaje texto a $targetIp:$targetPort", e)
            false
        }
    }

    suspend fun sendAudioFile(
        targetIp: String,
        targetPort: Int,
        message: ChatMessageEntity,
        audioFile: File,
        durationSec: Int,
        senderName: String = localDeviceName
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists()) return@withContext false
            val audioBytes = audioFile.readBytes()

            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), 4000)
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val header = "AUDIO|${message.id}|$localNodeId|$senderName|${message.receiverId}|$durationSec|${audioBytes.size}"
            dos.writeUTF(header)
            dos.write(audioBytes)
            dos.flush()

            val ack = dis.readUTF()
            socket.close()

            if (ack.startsWith("ACK")) {
                database?.chatDao()?.updateMessageStatus(message.id, "DELIVERED_MESH_WIFI")
                ElysiumMeshGovernance.recordPayloadSent(MeshPayloadType.AUDIO_NOTE)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al enviar nota de voz a $targetIp:$targetPort", e)
            false
        }
    }

    suspend fun sendImageFile(
        targetIp: String,
        targetPort: Int,
        message: ChatMessageEntity,
        imageFile: File,
        senderName: String = localDeviceName
    ): Boolean = sendGenericFile(targetIp, targetPort, message, imageFile, senderName)

    suspend fun sendGenericFile(
        targetIp: String,
        targetPort: Int,
        message: ChatMessageEntity,
        file: File,
        senderName: String = localDeviceName
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext false
            val isImg = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")
            val frameType = if (isImg) "IMAGE" else "FILE"
            val fileLength = file.length()

            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), 5000)
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val header = "$frameType|${message.id}|$localNodeId|$senderName|${message.receiverId}|${file.name}|$fileLength"
            dos.writeUTF(header)

            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)
            val buffer = ByteArray(32768)
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
            }
            dos.flush()
            bis.close()
            fis.close()

            val ack = dis.readUTF()
            socket.close()

            if (ack.startsWith("ACK")) {
                database?.chatDao()?.updateMessageStatus(message.id, "DELIVERED_MESH_WIFI")
                ElysiumMeshGovernance.recordPayloadSent(if (isImg) MeshPayloadType.IMAGE else MeshPayloadType.TEXT)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al enviar archivo ${file.name} a $targetIp:$targetPort", e)
            false
        }
    }

    suspend fun sendCallSignal(
        targetIp: String,
        targetPort: Int,
        signal: String, // "RING_AUDIO", "RING_VIDEO", "ACCEPT", "REJECT", "END"
        targetUserId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), 2500)
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val header = "CALL_SIGNAL|${UUID.randomUUID()}|$localNodeId|$localDeviceName|$targetUserId|$signal|0"
            dos.writeUTF(header)
            dos.flush()

            dis.readUTF()
            socket.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando señal de llamada ($signal) a $targetIp", e)
            false
        }
    }

    /**
     * ── 5. TRANSMISIÓN Y RECEPCIÓN DE AUDIO EN VIVO (<20ms latencia) ──
     */
    @SuppressLint("MissingPermission")
    fun startAudioCallStreaming(targetIp: String) {
        if (isAudioStreamingActive) return
        isAudioStreamingActive = true

        engineScope.launch {
            try {
                val ctx = appContext
                val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.mode = AudioManager.MODE_IN_COMMUNICATION
                // Speakerphone OFF by default to prevent echo/feedback when phones are nearby
                am?.isSpeakerphoneOn = false

                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1280)

                // Use VOICE_COMMUNICATION source first — it has built-in AEC on most devices
                var record: AudioRecord? = null
                try {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VOICE_COMMUNICATION falló, probando MIC", e)
                }

                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    try {
                        record = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSize
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudo inicializar AudioRecord", e)
                        return@launch
                    }
                }

                // Enable hardware echo cancellation and noise suppression
                val sessionId = record.audioSessionId
                try {
                    if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                        android.media.audiofx.AcousticEchoCanceler.create(sessionId)?.let {
                            it.enabled = true
                            Log.i(TAG, "AcousticEchoCanceler activado")
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "AEC no disponible: ${e.message}") }

                try {
                    if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                        android.media.audiofx.NoiseSuppressor.create(sessionId)?.let {
                            it.enabled = true
                            Log.i(TAG, "NoiseSuppressor activado")
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "NS no disponible: ${e.message}") }

                try {
                    if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                        android.media.audiofx.AutomaticGainControl.create(sessionId)?.let {
                            it.enabled = true
                            Log.i(TAG, "AutomaticGainControl activado")
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "AGC no disponible: ${e.message}") }

                audioRecord = record
                val socket = DatagramSocket()
                audioSocket = socket
                val targetAddress = InetAddress.getByName(targetIp)
                val buffer = ByteArray(640) // 20ms por paquete

                record.startRecording()
                Log.i(TAG, "Iniciada captura de audio P2P con AEC/NS a $targetIp:$UDP_AUDIO_PORT (speakerphone=OFF)")

                while (isActive && isAudioStreamingActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read, targetAddress, UDP_AUDIO_PORT)
                        socket.send(packet)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en streaming de audio P2P", e)
            } finally {
                stopAudioCallStreaming()
            }
        }
    }

    fun stopAudioCallStreaming() {
        isAudioStreamingActive = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
        try {
            audioSocket?.close()
        } catch (ignored: Exception) {}
        audioSocket = null

        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.mode = AudioManager.MODE_NORMAL
    }

    private fun startAudioStreamReceiver() {
        engineScope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1280)

            var track: AudioTrack? = null
            var socket: DatagramSocket? = null

            try {
                track = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
                audioTrack = track
                track.play()

                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_AUDIO_PORT))
                }
                val buffer = ByteArray(1024)

                while (isActive && _isEngineRunning.value) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    track.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) {
                // Esperar o reiniciar si cambia la red
            } finally {
                try { track?.stop(); track?.release() } catch (ignored: Exception) {}
                try { socket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * ── 6. TRANSMISIÓN Y RECEPCIÓN DE VIDEO EN VIVO 100% REAL DE CÁMARA ──
     */
    fun startVideoCallStreaming(targetIp: String, frontCamera: Boolean = true) {
        val ctx = appContext ?: return
        ElysiumRealCameraStreamer.startStreaming(ctx, targetIp, frontCamera)
    }

    fun flipVideoCallCamera() {
        val ctx = appContext ?: return
        ElysiumRealCameraStreamer.flipCamera(ctx)
    }

    fun stopVideoCallStreaming() {
        ElysiumRealCameraStreamer.stopStreaming()
        _remoteVideoFrame.value = null
    }

    fun sendVideoFrame(targetIp: String, bitmap: Bitmap) {
        engineScope.launch {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                val frameBytes = baos.toByteArray()

                val socket = DatagramSocket()
                val targetAddress = InetAddress.getByName(targetIp)
                val packet = DatagramPacket(frameBytes, frameBytes.size, targetAddress, UDP_VIDEO_PORT)
                socket.send(packet)
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun startVideoStreamReceiver() {
        engineScope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_VIDEO_PORT))
                }
                val buffer = ByteArray(65507) // Máximo payload UDP

                while (isActive && _isEngineRunning.value) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val bmp = BitmapFactory.decodeByteArray(packet.data, 0, packet.length)
                    if (bmp != null) {
                        _remoteVideoFrame.value = bmp
                    }
                }
            } catch (e: Exception) {
                // Silenciado para reintentos
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * ── 7. EMPAREJAMIENTO 1-TOQUE Y QR OFFLINE ──
     */
    suspend fun linkPeerOneTouch(peer: PhysicalMeshPeer): FleetMemberEntity = withContext(Dispatchers.IO) {
        ElysiumMeshGovernance.linkPeer(peer)

        // Registrar o actualizar miembro de flota en la base de datos local para que el chat siempre exista
        val member = FleetMemberEntity(
            id = peer.nodeId,
            businessId = "mesh_p2p",
            userId = peer.nodeId,
            role = peer.role,
            email = "${peer.nodeId.lowercase()}@mesh.direct",
            inviteStatus = "ACCEPTED",
            joinedAt = System.currentTimeMillis()
        )
        database?.fleetDao()?.insertFleetMember(member)
        member
    }

    /**
     * Genera el payload de QR 100% offline para escanear y conectar sin internet
     */
    fun createOfflineQrPayload(): String {
        val ip = ElysiumMeshGovernance.localIpAddress.value ?: "0.0.0.0"
        return "elysium-mesh://peer?nodeId=$localNodeId&name=${URLEncoder.encode(localDeviceName, "UTF-8")}&ip=$ip&port=$TCP_DATA_PORT"
    }

    /**
     * Parsea el código QR offline y crea el PhysicalMeshPeer
     */
    fun parseOfflineQrPayload(qrText: String): PhysicalMeshPeer? {
        return try {
            val uri = Uri.parse(qrText)
            if (uri.scheme == "elysium-mesh" && uri.host == "peer") {
                val nodeId = uri.getQueryParameter("nodeId") ?: return null
                val name = URLDecoder.decode(uri.getQueryParameter("name") ?: "Nodo Mesh", "UTF-8")
                val ip = uri.getQueryParameter("ip") ?: return null
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: TCP_DATA_PORT

                PhysicalMeshPeer(
                    nodeId = nodeId,
                    deviceName = name,
                    ipAddress = ip,
                    port = port,
                    lastSeenMs = System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
