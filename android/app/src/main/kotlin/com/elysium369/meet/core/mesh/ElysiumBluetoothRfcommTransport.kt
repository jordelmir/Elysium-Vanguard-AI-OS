package com.elysium369.meet.core.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Environment
import android.util.Log
import com.elysium369.meet.data.local.entities.ChatMessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   B L U E T O O T H   R F C O M M   T R A N S P O R T
 * ══════════════════════════════════════════════════════════════════════
 *
 * Transporte Bluetooth Classic RFCOMM para comunicación directa sin Wi-Fi.
 * Funciona solo con Bluetooth encendido y dispositivos pareados.
 *
 * Capacidades:
 * - Mensajes de texto
 * - Archivos (audio, imágenes, documentos, APK, etc.) hasta ~50MB por transferencia
 * - Señales de llamada (RING_AUDIO, RING_VIDEO)
 * - Descubrimiento automático de peers pareados con MEET instalado
 */
@SuppressLint("MissingPermission")
object ElysiumBluetoothRfcommTransport {

    private const val TAG = "BtRfcomm"
    // UUID estable para el servicio MEET Mesh Bluetooth
    private val SERVICE_UUID = UUID.fromString("e1e51003-ae50-4b1e-b100-c00001ca0100")
    private const val SERVICE_NAME = "ElysiumMeshBT"

    // Protocol headers
    private const val HEADER_TEXT = "BT_TEXT"
    private const val HEADER_AUDIO = "BT_AUDIO"
    private const val HEADER_IMAGE = "BT_IMAGE"
    private const val HEADER_FILE = "BT_FILE"
    private const val HEADER_CALL = "BT_CALL"
    private const val HEADER_ACK = "BT_ACK"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var isRunning = false

    // Connected peers via Bluetooth RFCOMM
    private val connectedPeers = ConcurrentHashMap<String, BluetoothSocket>()

    private val _status = MutableStateFlow("Bluetooth RFCOMM: Inactivo")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _btPeersCount = MutableStateFlow(0)
    val btPeersCount: StateFlow<Int> = _btPeersCount.asStateFlow()

    /**
     * Inicia el servidor RFCOMM y escanea peers pareados
     */
    fun start(context: Context) {
        if (isRunning) return
        appContext = context.applicationContext
        isRunning = true

        val adapter = getAdapter(context) ?: run {
            _status.value = "Bluetooth no disponible"
            return
        }
        if (!adapter.isEnabled) {
            _status.value = "Bluetooth apagado"
            return
        }

        // Start RFCOMM server to accept incoming connections
        startServer(adapter)

        // Try to connect to all paired devices
        connectToPairedDevices(adapter)

        _status.value = "Bluetooth RFCOMM: Escuchando"
        Log.i(TAG, "Bluetooth RFCOMM transport iniciado")
    }

    fun stop() {
        isRunning = false
        scope.coroutineContext.cancelChildren()
        connectedPeers.values.forEach { runCatching { it.close() } }
        connectedPeers.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        _status.value = "Bluetooth RFCOMM: Detenido"
        _btPeersCount.value = 0
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    // ── SERVER: Accept incoming RFCOMM connections ──

    private fun startServer(adapter: BluetoothAdapter) {
        scope.launch {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                Log.i(TAG, "RFCOMM server escuchando con UUID=$SERVICE_UUID")

                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept(30_000) ?: continue
                        val address = socket.remoteDevice?.address ?: continue
                        Log.i(TAG, "Conexión entrante de $address")
                        connectedPeers[address] = socket
                        _btPeersCount.value = connectedPeers.size
                        _status.value = "BT: ${connectedPeers.size} peer(s) conectados"

                        // Handle incoming data in separate coroutine
                        launch { handleIncomingData(socket, address) }

                        // Register as physical mesh peer with a synthetic IP
                        registerAsMeshPeer(socket.remoteDevice)
                    } catch (e: java.io.IOException) {
                        if (isRunning) delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en RFCOMM server", e)
            }
        }
    }

    // ── CLIENT: Connect to paired devices ──

    private fun connectToPairedDevices(adapter: BluetoothAdapter) {
        scope.launch {
            val bondedDevices = try { adapter.bondedDevices } catch (_: Exception) { emptySet() }
            if (bondedDevices.isNullOrEmpty()) {
                _status.value = "BT: Sin dispositivos pareados"
                return@launch
            }

            Log.i(TAG, "Intentando conectar a ${bondedDevices.size} dispositivos pareados")
            for (device in bondedDevices) {
                if (!isRunning) break
                if (connectedPeers.containsKey(device.address)) continue
                launch { connectToDevice(device) }
            }
        }
    }

    private suspend fun connectToDevice(device: BluetoothDevice) {
        repeat(3) { attempt ->
            if (!isRunning || connectedPeers.containsKey(device.address)) return
            try {
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                connectedPeers[device.address] = socket
                _btPeersCount.value = connectedPeers.size
                _status.value = "BT: ${connectedPeers.size} peer(s) conectados"
                Log.i(TAG, "Conectado a ${device.name ?: device.address} (intento ${attempt + 1})")

                // Register as mesh peer
                registerAsMeshPeer(device)

                // Handle incoming data
                scope.launch { handleIncomingData(socket, device.address) }
                return
            } catch (e: Exception) {
                Log.d(TAG, "Intento ${attempt + 1}/3 fallido para ${device.name ?: device.address}: ${e.message}")
                delay(2000L * (attempt + 1))
            }
        }
    }

    private fun registerAsMeshPeer(device: BluetoothDevice) {
        val deviceName = device.name ?: device.address
        // Use a synthetic IP "bt:<address>" to distinguish BT peers
        val peer = PhysicalMeshPeer(
            nodeId = "BT-${device.address.replace(":", "")}",
            deviceName = deviceName,
            ipAddress = "bt:${device.address}",
            port = 0,
            role = "CONDUCTOR",
            lastSeenMs = System.currentTimeMillis(),
            isLinked = true
        )
        ElysiumMeshGovernance.registerDiscoveredPeer(peer)
    }

    // ── RECEIVING DATA ──

    private suspend fun handleIncomingData(socket: BluetoothSocket, address: String) {
        try {
            val input = BufferedInputStream(socket.inputStream)
            val reader = DataInputStream(input)

            while (isRunning && socket.isConnected) {
                try {
                    val headerLen = reader.readInt()
                    if (headerLen <= 0 || headerLen > 1024) continue
                    val headerBytes = ByteArray(headerLen)
                    reader.readFully(headerBytes)
                    val header = String(headerBytes, Charsets.UTF_8)
                    val parts = header.split("|")
                    val type = parts.getOrNull(0) ?: continue

                    val dataLen = reader.readInt()
                    val data = if (dataLen > 0 && dataLen < 50_000_000) {
                        ByteArray(dataLen).also { reader.readFully(it) }
                    } else ByteArray(0)

                    when (type) {
                        HEADER_TEXT -> {
                            val senderId = parts.getOrElse(1) { "BT-$address" }
                            val msgId = parts.getOrElse(2) { UUID.randomUUID().toString() }
                            val timestamp = parts.getOrElse(3) { "${System.currentTimeMillis()}" }.toLongOrNull() ?: System.currentTimeMillis()
                            val text = String(data, Charsets.UTF_8)
                            val msg = ChatMessageEntity(
                                id = msgId, businessId = "elysium_bt", senderId = senderId,
                                receiverId = ElysiumPhysicalMeshEngine.localNodeId,
                                messageText = text, messageType = "TEXT",
                                fileLocalPath = null, fileRemoteUrl = null,
                                durationSeconds = 0, timestamp = timestamp, status = "RECEIVED"
                            )
                            ElysiumPhysicalMeshEngine.emitReceivedMessage(msg)
                            sendAck(socket, msgId)
                            Log.i(TAG, "Texto recibido por BT de $senderId: ${text.take(50)}")
                        }
                        HEADER_AUDIO -> {
                            val senderId = parts.getOrElse(1) { "BT-$address" }
                            val msgId = parts.getOrElse(2) { UUID.randomUUID().toString() }
                            val ctx = appContext ?: continue
                            val cacheDir = File(ctx.cacheDir, "bt_audio").apply { mkdirs() }
                            val audioFile = File(cacheDir, "$msgId.m4a")
                            audioFile.writeBytes(data)
                            val msg = ChatMessageEntity(
                                id = msgId, businessId = "elysium_bt", senderId = senderId,
                                receiverId = ElysiumPhysicalMeshEngine.localNodeId,
                                messageText = null, messageType = "AUDIO",
                                fileLocalPath = audioFile.absolutePath, fileRemoteUrl = null,
                                durationSeconds = 0, timestamp = System.currentTimeMillis(), status = "RECEIVED"
                            )
                            ElysiumPhysicalMeshEngine.emitReceivedMessage(msg)
                            sendAck(socket, msgId)
                            Log.i(TAG, "Audio recibido por BT de $senderId: ${data.size} bytes")
                        }
                        HEADER_IMAGE, HEADER_FILE -> {
                            val senderId = parts.getOrElse(1) { "BT-$address" }
                            val msgId = parts.getOrElse(2) { UUID.randomUUID().toString() }
                            val isImg = type == HEADER_IMAGE
                            val defaultName = if (isImg) "mesh_img_$msgId.jpg" else "mesh_file_$msgId"
                            val fileName = parts.getOrElse(3) { defaultName }
                            val downloadsDir = try {
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                            } catch (e: Exception) {
                                appContext?.cacheDir ?: File("/tmp").apply { mkdirs() }
                            }
                            var targetFile = File(downloadsDir, fileName)
                            var counter = 1
                            while (targetFile.exists()) {
                                val baseName = fileName.substringBeforeLast(".")
                                val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
                                targetFile = File(downloadsDir, "${baseName}_$counter$ext")
                                counter++
                            }
                            targetFile.writeBytes(data)

                            val msg = ChatMessageEntity(
                                id = msgId, businessId = "elysium_bt", senderId = senderId,
                                receiverId = ElysiumPhysicalMeshEngine.localNodeId,
                                messageText = targetFile.name, messageType = if (isImg) "IMAGE" else "FILE",
                                fileLocalPath = targetFile.absolutePath, fileRemoteUrl = null,
                                durationSeconds = 0, timestamp = System.currentTimeMillis(), status = "DELIVERED_MESH_WIFI"
                            )
                            ElysiumPhysicalMeshEngine.database?.chatDao()?.insertMessage(msg)
                            ElysiumPhysicalMeshEngine.emitReceivedMessage(msg)
                            sendAck(socket, msgId)
                            Log.i(TAG, "✅ Archivo recibido por BT de $senderId: ${targetFile.name} (${data.size} bytes) en Downloads")
                        }
                        HEADER_CALL -> {
                            val callType = parts.getOrElse(1) { "AUDIO" }
                            val callerId = parts.getOrElse(2) { "BT-$address" }
                            ElysiumPhysicalMeshEngine.emitIncomingCall(
                                callType = if (callType.contains("VIDEO")) "VIDEO" else "AUDIO",
                                callerId = callerId,
                                callerName = "Bluetooth: ${connectedPeers.keys.firstOrNull { it == address } ?: address}",
                                callerIp = "bt:$address"
                            )
                            Log.i(TAG, "Señal de llamada $callType recibida por BT de $callerId")
                        }
                    }
                } catch (e: java.io.EOFException) { break }
                catch (e: Exception) {
                    if (socket.isConnected) Log.w(TAG, "Error leyendo BT data: ${e.message}")
                    else break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT connection lost: $address — ${e.message}")
        } finally {
            connectedPeers.remove(address)
            _btPeersCount.value = connectedPeers.size
            _status.value = if (connectedPeers.isEmpty()) "BT: Sin peers" else "BT: ${connectedPeers.size} peer(s)"
            runCatching { socket.close() }
            // Try to reconnect after 5s
            if (isRunning) {
                scope.launch {
                    delay(5000)
                    getAdapter(appContext ?: return@launch)?.bondedDevices
                        ?.firstOrNull { it.address == address }
                        ?.let { connectToDevice(it) }
                }
            }
        }
    }

    private fun sendAck(socket: BluetoothSocket, msgId: String) {
        try {
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val header = "$HEADER_ACK|$msgId"
            val headerBytes = header.toByteArray(Charsets.UTF_8)
            output.writeInt(headerBytes.size)
            output.write(headerBytes)
            output.writeInt(0) // no data
            output.flush()
        } catch (_: Exception) {}
    }

    // ── SENDING DATA ──

    fun sendText(targetAddress: String?, senderId: String, msgId: String, text: String): Boolean {
        val socket = findSocket(targetAddress) ?: return false
        return sendPacket(socket, "$HEADER_TEXT|$senderId|$msgId|${System.currentTimeMillis()}", text.toByteArray(Charsets.UTF_8))
    }

    fun sendAudioFile(targetAddress: String?, senderId: String, msgId: String, audioFile: File): Boolean {
        val socket = findSocket(targetAddress) ?: return false
        if (!audioFile.exists() || audioFile.length() > 50_000_000) return false
        return sendPacket(socket, "$HEADER_AUDIO|$senderId|$msgId", audioFile.readBytes())
    }

    fun sendImageFile(targetAddress: String?, senderId: String, msgId: String, imageFile: File): Boolean {
        return sendGenericFile(targetAddress, senderId, msgId, imageFile.name, imageFile)
    }

    fun sendGenericFile(targetAddress: String?, senderId: String, msgId: String, fileName: String, file: File): Boolean {
        val socket = findSocket(targetAddress) ?: return false
        if (!file.exists() || file.length() > 50_000_000) return false
        val isImg = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")
        val headerType = if (isImg) HEADER_IMAGE else HEADER_FILE
        return sendPacket(socket, "$headerType|$senderId|$msgId|$fileName", file.readBytes())
    }

    fun sendCallSignal(targetAddress: String?, callType: String, callerId: String): Boolean {
        val socket = findSocket(targetAddress) ?: return false
        return sendPacket(socket, "$HEADER_CALL|$callType|$callerId", ByteArray(0))
    }

    private fun findSocket(targetAddress: String?): BluetoothSocket? {
        if (targetAddress != null) {
            val cleanAddress = targetAddress.removePrefix("bt:")
            connectedPeers[cleanAddress]?.let { if (it.isConnected) return it }
        }
        // Fallback: first connected peer
        return connectedPeers.values.firstOrNull { it.isConnected }
    }

    @Synchronized
    private fun sendPacket(socket: BluetoothSocket, header: String, data: ByteArray): Boolean {
        return try {
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val headerBytes = header.toByteArray(Charsets.UTF_8)
            output.writeInt(headerBytes.size)
            output.write(headerBytes)
            output.writeInt(data.size)
            if (data.isNotEmpty()) output.write(data)
            output.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando por BT: ${e.message}")
            false
        }
    }

    /** Returns true if there's at least one connected Bluetooth peer */
    fun hasConnectedPeer(): Boolean = connectedPeers.values.any { it.isConnected }

    /** Get the address of the first connected peer */
    fun firstPeerAddress(): String? = connectedPeers.entries.firstOrNull { it.value.isConnected }?.key
}
