package com.elysium369.meet.core.mesh

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.elysium369.meet.data.local.entities.ChatMessageEntity

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   F I L E   T R A N S F E R   E N G I N E
 * ══════════════════════════════════════════════════════════════════════
 *
 * Motor de transferencia de archivos P2P — 100% local, NUNCA toca el servidor.
 *
 * Capacidades:
 * - Cualquier tipo de archivo (APK, ISO, PDF, DOCX, XLSX, ROMs, PKG, etc.)
 * - Archivos de hasta 120GB+ via streaming por chunks
 * - Integridad SHA-256 por chunk + hash final completo
 * - Guardado automático en /Downloads/ del Android receptor
 * - Progreso en tiempo real con velocidad (MB/s)
 * - Iconos por tipo de archivo
 * - Transportes: TCP WiFi, Bluetooth RFCOMM, Internet directo
 */
object ElysiumFileTransferEngine {

    private const val TAG = "FileTransfer"
    private const val FILE_TRANSFER_PORT = 36904
    private const val CHUNK_SIZE = 1024 * 1024 // 1MB chunks
    private const val HEADER_MAGIC = "ELYSIUM_FILE_V1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var appContext: Context? = null

    // ── Transfer State ──

    data class FileTransferInfo(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val direction: Direction,
        val peerId: String,
        val peerName: String,
        var bytesTransferred: Long = 0,
        var status: TransferStatus = TransferStatus.PENDING,
        var localPath: String? = null,
        var sha256: String? = null,
        var speedBytesPerSec: Long = 0
    ) {
        val progressPercent: Int get() = if (fileSize > 0) ((bytesTransferred * 100) / fileSize).toInt() else 0
        val icon: String get() = getFileIcon(fileName)
    }

    enum class Direction { SENDING, RECEIVING }
    enum class TransferStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

    private val _activeTransfers = MutableStateFlow<Map<String, FileTransferInfo>>(emptyMap())
    val activeTransfers: StateFlow<Map<String, FileTransferInfo>> = _activeTransfers.asStateFlow()

    private val _transferEvents = MutableSharedFlow<FileTransferInfo>(replay = 0, extraBufferCapacity = 16)
    val transferEvents: SharedFlow<FileTransferInfo> = _transferEvents.asSharedFlow()

    private val _incomingFileRequests = MutableSharedFlow<FileTransferInfo>(replay = 0, extraBufferCapacity = 8)
    val incomingFileRequests: SharedFlow<FileTransferInfo> = _incomingFileRequests.asSharedFlow()

    // ── Start / Stop ──

    fun start(context: Context) {
        if (isRunning) return
        appContext = context.applicationContext
        isRunning = true
        startFileServer()
        Log.i(TAG, "File Transfer Engine iniciado en puerto $FILE_TRANSFER_PORT")
    }

    fun stop() {
        isRunning = false
        scope.coroutineContext.cancelChildren()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    // ── FILE SERVER (Receiving) ──

    private fun startFileServer() {
        scope.launch {
            while (isActive && isRunning) {
                try {
                    serverSocket = ServerSocket(FILE_TRANSFER_PORT).apply { reuseAddress = true }
                    Log.i(TAG, "File server escuchando en puerto $FILE_TRANSFER_PORT")
                    while (isActive && isRunning) {
                        val client = serverSocket?.accept() ?: continue
                        launch { handleIncomingFile(client) }
                    }
                } catch (e: Exception) {
                    if (isRunning) delay(2000)
                } finally {
                    runCatching { serverSocket?.close() }
                }
            }
        }
    }

    private suspend fun handleIncomingFile(socket: Socket) = withContext(Dispatchers.IO) {
        val dis = DataInputStream(BufferedInputStream(socket.inputStream, CHUNK_SIZE))
        val dos = DataOutputStream(BufferedOutputStream(socket.outputStream))
        var transfer: FileTransferInfo? = null

        try {
            // 1. Read header
            val magic = dis.readUTF()
            if (magic != HEADER_MAGIC) {
                dos.writeUTF("ERROR|INVALID_MAGIC")
                dos.flush()
                return@withContext
            }

            val transferId = dis.readUTF()
            val fileName = dis.readUTF()
            val fileSize = dis.readLong()
            val mimeType = dis.readUTF()
            val totalChunks = dis.readInt()
            val senderId = dis.readUTF()
            val senderName = dis.readUTF()
            val fileSha256 = dis.readUTF()

            Log.i(TAG, "Recibiendo archivo: $fileName ($fileSize bytes, $totalChunks chunks) de $senderName")

            transfer = FileTransferInfo(
                transferId = transferId, fileName = fileName, fileSize = fileSize,
                mimeType = mimeType, direction = Direction.RECEIVING,
                peerId = senderId, peerName = senderName,
                status = TransferStatus.IN_PROGRESS, sha256 = fileSha256
            )
            updateTransfer(transfer)
            _incomingFileRequests.emit(transfer)

            // Accept
            dos.writeUTF("ACCEPT")
            dos.flush()

            // 2. Prepare output file in Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            var targetFile = File(downloadsDir, fileName)
            // Avoid overwriting existing files
            var counter = 1
            while (targetFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
                targetFile = File(downloadsDir, "${nameWithoutExt} ($counter)$ext")
                counter++
            }

            transfer.localPath = targetFile.absolutePath

            // 3. Receive chunks with SHA-256 verification
            val fileDigest = MessageDigest.getInstance("SHA-256")
            val fos = FileOutputStream(targetFile)
            val bos = BufferedOutputStream(fos, CHUNK_SIZE)
            var totalReceived = 0L
            val startTime = System.currentTimeMillis()

            for (chunkIndex in 0 until totalChunks) {
                val chunkLen = dis.readInt()
                if (chunkLen <= 0 || chunkLen > CHUNK_SIZE * 2) {
                    throw IOException("Chunk $chunkIndex inválido: $chunkLen bytes")
                }
                val chunkData = ByteArray(chunkLen)
                dis.readFully(chunkData)

                // Verify chunk hash
                val expectedChunkHash = dis.readUTF()
                val chunkDigest = MessageDigest.getInstance("SHA-256")
                val actualChunkHash = chunkDigest.digest(chunkData).joinToString("") { "%02x".format(it) }

                if (actualChunkHash != expectedChunkHash) {
                    dos.writeUTF("CHUNK_FAIL|$chunkIndex")
                    dos.flush()
                    throw IOException("Chunk $chunkIndex corrupto: esperado $expectedChunkHash, recibido $actualChunkHash")
                }

                bos.write(chunkData)
                fileDigest.update(chunkData)
                totalReceived += chunkLen

                // Update progress
                val elapsed = System.currentTimeMillis() - startTime
                val speed = if (elapsed > 0) (totalReceived * 1000) / elapsed else 0
                transfer.bytesTransferred = totalReceived
                transfer.speedBytesPerSec = speed
                updateTransfer(transfer)

                // ACK chunk
                dos.writeUTF("CHUNK_OK|$chunkIndex")
                dos.flush()
            }

            bos.flush()
            bos.close()
            fos.close()

            // 4. Verify final SHA-256
            val finalHash = fileDigest.digest().joinToString("") { "%02x".format(it) }
            if (finalHash != fileSha256) {
                dos.writeUTF("FAIL|HASH_MISMATCH|$finalHash")
                dos.flush()
                targetFile.delete()
                transfer.status = TransferStatus.FAILED
                updateTransfer(transfer)
                Log.e(TAG, "Hash mismatch: esperado=$fileSha256, recibido=$finalHash")
            } else {
                dos.writeUTF("COMPLETE|$finalHash")
                dos.flush()
                transfer.status = TransferStatus.COMPLETED
                transfer.bytesTransferred = fileSize
                updateTransfer(transfer)

                val isImg = fileName.substringAfterLast(".").lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")
                val entity = ChatMessageEntity(
                    id = transferId,
                    businessId = "mesh_p2p",
                    senderId = senderId,
                    receiverId = ElysiumPhysicalMeshEngine.localNodeId,
                    messageText = targetFile.name,
                    messageType = if (isImg) "IMAGE" else "FILE",
                    fileLocalPath = targetFile.absolutePath,
                    fileRemoteUrl = null,
                    durationSeconds = 0,
                    timestamp = System.currentTimeMillis(),
                    status = "DELIVERED_MESH_WIFI"
                )
                ElysiumPhysicalMeshEngine.database?.chatDao()?.insertMessage(entity)
                ElysiumPhysicalMeshEngine.emitReceivedMessage(entity)
                ElysiumMeshGovernance.recordPayloadSent(if (isImg) MeshPayloadType.IMAGE else MeshPayloadType.TEXT)

                Log.i(TAG, "✅ Archivo recibido íntegro: $fileName → ${targetFile.absolutePath} (SHA-256: $finalHash)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error recibiendo archivo", e)
            transfer?.let {
                it.status = TransferStatus.FAILED
                updateTransfer(it)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    // ── FILE SENDER ──

    /**
     * Envía un archivo seleccionado por el usuario a un peer via TCP.
     * El archivo NUNCA toca el servidor — transferencia 100% P2P directa.
     */
    fun sendFile(
        context: Context,
        fileUri: Uri,
        targetIp: String,
        targetPort: Int = FILE_TRANSFER_PORT,
        peerId: String,
        peerName: String
    ) {
        scope.launch {
            var transfer: FileTransferInfo? = null
            try {
                val cr = context.contentResolver
                val fileName = getFileName(context, fileUri) ?: "archivo_${System.currentTimeMillis()}"
                val fileSize = getFileSize(context, fileUri)
                val mimeType = cr.getType(fileUri) ?: getMimeFromExtension(fileName)
                val transferId = UUID.randomUUID().toString()

                transfer = FileTransferInfo(
                    transferId = transferId, fileName = fileName, fileSize = fileSize,
                    mimeType = mimeType, direction = Direction.SENDING,
                    peerId = peerId, peerName = peerName,
                    status = TransferStatus.IN_PROGRESS
                )
                updateTransfer(transfer)
                _transferEvents.emit(transfer)

                Log.i(TAG, "Enviando archivo: $fileName ($fileSize bytes) a $peerName ($targetIp:$targetPort)")

                // 1. Calculate SHA-256 of entire file first
                val sha256 = calculateFileSha256(context, fileUri)
                transfer.sha256 = sha256

                // 2. Calculate total chunks
                val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

                // 3. Connect and send header
                val socket = Socket()
                socket.connect(InetSocketAddress(targetIp, targetPort), 5000)
                socket.sendBufferSize = CHUNK_SIZE
                val dos = DataOutputStream(BufferedOutputStream(socket.outputStream, CHUNK_SIZE))
                val dis = DataInputStream(BufferedInputStream(socket.inputStream))

                dos.writeUTF(HEADER_MAGIC)
                dos.writeUTF(transferId)
                dos.writeUTF(fileName)
                dos.writeLong(fileSize)
                dos.writeUTF(mimeType)
                dos.writeInt(totalChunks)
                dos.writeUTF(ElysiumPhysicalMeshEngine.localNodeId)
                dos.writeUTF(ElysiumPhysicalMeshEngine.localDeviceName)
                dos.writeUTF(sha256)
                dos.flush()

                // 4. Wait for acceptance
                val response = dis.readUTF()
                if (response != "ACCEPT") {
                    throw IOException("Peer rechazó la transferencia: $response")
                }

                // 5. Stream chunks
                val inputStream = cr.openInputStream(fileUri) ?: throw IOException("No se puede abrir $fileUri")
                val bis = BufferedInputStream(inputStream, CHUNK_SIZE)
                val buffer = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                val startTime = System.currentTimeMillis()

                for (chunkIndex in 0 until totalChunks) {
                    val bytesRead = readFully(bis, buffer)
                    if (bytesRead <= 0) break

                    val chunkData = if (bytesRead < buffer.size) buffer.copyOf(bytesRead) else buffer

                    // Calculate chunk hash
                    val chunkDigest = MessageDigest.getInstance("SHA-256")
                    val chunkHash = chunkDigest.digest(chunkData).joinToString("") { "%02x".format(it) }

                    // Send chunk
                    dos.writeInt(chunkData.size)
                    dos.write(chunkData)
                    dos.writeUTF(chunkHash)
                    dos.flush()

                    // Wait for ACK
                    val ack = dis.readUTF()
                    if (!ack.startsWith("CHUNK_OK")) {
                        throw IOException("Chunk $chunkIndex rechazado: $ack")
                    }

                    totalSent += bytesRead
                    val elapsed = System.currentTimeMillis() - startTime
                    val speed = if (elapsed > 0) (totalSent * 1000) / elapsed else 0
                    transfer.bytesTransferred = totalSent
                    transfer.speedBytesPerSec = speed
                    updateTransfer(transfer)
                }

                bis.close()

                // 6. Wait for final confirmation
                val finalResponse = dis.readUTF()
                socket.close()

                if (finalResponse.startsWith("COMPLETE")) {
                    transfer.status = TransferStatus.COMPLETED
                    transfer.bytesTransferred = fileSize
                    updateTransfer(transfer)
                    ElysiumPhysicalMeshEngine.database?.chatDao()?.updateMessageStatus(transferId, "DELIVERED_MESH_WIFI")
                    Log.i(TAG, "✅ Archivo enviado íntegro: $fileName (SHA-256: $sha256)")
                } else {
                    transfer.status = TransferStatus.FAILED
                    updateTransfer(transfer)
                    Log.e(TAG, "Archivo falló verificación final: $finalResponse")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error enviando archivo", e)
                transfer?.let {
                    it.status = TransferStatus.FAILED
                    updateTransfer(it)
                }
            }
        }
    }

    /** Send file via Bluetooth RFCOMM (for smaller files < 500MB) */
    fun sendFileViaBluetooth(
        context: Context,
        fileUri: Uri,
        targetAddress: String?,
        peerId: String,
        peerName: String
    ) {
        scope.launch {
            // For Bluetooth, read the file and send as one blob (limited to ~500MB due to BT speed)
            val cr = context.contentResolver
            val fileName = getFileName(context, fileUri) ?: "archivo_bt"
            val fileSize = getFileSize(context, fileUri)

            if (fileSize > 500_000_000) {
                Log.w(TAG, "Archivo demasiado grande para Bluetooth ($fileSize bytes). Usa Wi-Fi.")
                return@launch
            }

            val transfer = FileTransferInfo(
                transferId = UUID.randomUUID().toString(), fileName = fileName, fileSize = fileSize,
                mimeType = cr.getType(fileUri) ?: "application/octet-stream",
                direction = Direction.SENDING, peerId = peerId, peerName = peerName,
                status = TransferStatus.IN_PROGRESS
            )
            updateTransfer(transfer)

            try {
                val inputStream = cr.openInputStream(fileUri) ?: return@launch
                val tempFile = File(context.cacheDir, "bt_send_$fileName")
                tempFile.outputStream().use { out -> inputStream.copyTo(out) }

                val sent = ElysiumBluetoothRfcommTransport.sendAudioFile(
                    targetAddress, ElysiumPhysicalMeshEngine.localNodeId,
                    transfer.transferId, tempFile
                )

                transfer.status = if (sent) TransferStatus.COMPLETED else TransferStatus.FAILED
                transfer.bytesTransferred = if (sent) fileSize else 0
                updateTransfer(transfer)
                tempFile.delete()
            } catch (e: Exception) {
                transfer.status = TransferStatus.FAILED
                updateTransfer(transfer)
            }
        }
    }

    // ── Helpers ──

    private fun updateTransfer(transfer: FileTransferInfo) {
        _activeTransfers.value = _activeTransfers.value.toMutableMap().apply {
            put(transfer.transferId, transfer.copy())
        }
        scope.launch { _transferEvents.emit(transfer.copy()) }
    }

    private fun calculateFileSha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se puede abrir URI para SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        inputStream.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment?.substringAfterLast("/")
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) return cursor.getLong(sizeIndex)
            }
        }
        return try { File(uri.path ?: "").length() } catch (_: Exception) { 0L }
    }

    private fun getMimeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast(".").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "iso" -> "application/x-iso9660-image"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar", "gz", "tgz" -> "application/x-tar"
            "mp4", "mkv", "avi" -> "video/mp4"
            "mp3", "flac", "ogg", "m4a" -> "audio/mpeg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pkg" -> "application/x-newton-compatible-pkg"
            "dmg" -> "application/x-apple-diskimage"
            "exe" -> "application/x-msdownload"
            "bin", "rom", "nes", "snes", "gba", "nds", "3ds", "nsp", "xci" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    // ── Static Helpers ──
        /** Returns an emoji icon based on file extension */
        fun getFileIcon(fileName: String): String {
            val ext = fileName.substringAfterLast(".").lowercase()
            return when (ext) {
                // Documents
                "pdf" -> "📕"
                "doc", "docx" -> "📝"
                "xls", "xlsx" -> "📊"
                "ppt", "pptx" -> "📽️"
                "txt", "md", "rtf" -> "📄"
                // Archives
                "zip", "rar", "7z", "tar", "gz", "tgz" -> "📦"
                // Apps & Installers
                "apk" -> "📲"
                "exe", "msi" -> "💻"
                "dmg", "pkg" -> "🍎"
                "deb", "rpm" -> "🐧"
                // Disc images
                "iso", "img", "bin" -> "💿"
                // Media
                "mp4", "mkv", "avi", "mov", "wmv", "flv" -> "🎬"
                "mp3", "flac", "ogg", "m4a", "wav", "aac" -> "🎵"
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "🖼️"
                // Gaming ROMs & files
                "nes", "snes", "sfc" -> "🎮"
                "gba", "gbc", "gb" -> "🎮"
                "nds", "3ds" -> "🎮"
                "nsp", "xci" -> "🎮"
                "n64", "z64" -> "🎮"
                "gcm", "gcz", "wbfs" -> "🎮"
                "cso", "pbp" -> "🎮"
                "sav", "srm" -> "💾"
                // Code
                "html", "css", "js", "ts", "kt", "java", "py" -> "👨‍💻"
                "json", "xml", "yaml", "yml" -> "⚙️"
                // Database
                "db", "sqlite", "sql" -> "🗄️"
                // Fonts
                "ttf", "otf", "woff", "woff2" -> "🔤"
                else -> "📁"
            }
        }

        /** Human-readable file size */
        fun formatFileSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }

        /** Human-readable speed */
        fun formatSpeed(bytesPerSec: Long): String = when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024))
        }
}
