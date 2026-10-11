package com.elysium369.meet.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.audio.VoicePlayer
import com.elysium369.meet.core.audio.VoiceRecorder
import com.elysium369.meet.data.local.dao.ChatDao
import com.elysium369.meet.data.local.dao.ChatReportDao
import com.elysium369.meet.data.local.dao.FleetDao
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.core.mesh.ElysiumMeshGovernance
import com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine
import com.elysium369.meet.core.mesh.MeshPayloadType
import com.elysium369.meet.core.mesh.MeshTransportLevel
import com.elysium369.meet.core.mesh.PhysicalMeshPeer
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.ServiceVerificationSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FleetChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val chatReportDao: ChatReportDao,
    private val fleetDao: FleetDao,
    private val vehicleDao: VehicleDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val voiceRecorder = VoiceRecorder(context)
    private val voicePlayer = VoicePlayer(context)

    // Current authenticated user ID
    val currentUserId: String
        get() = SupabaseManager.client.auth.currentUserOrNull()?.id ?: com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.localNodeId

    // Active selected business ID
    private val _selectedBusinessId = MutableStateFlow<String?>(null)
    val selectedBusinessId = _selectedBusinessId.asStateFlow()

    // All businesses for the owner
    val businessProfiles: Flow<List<BusinessProfileEntity>> = flow {
        emitAll(fleetDao.getBusinessProfilesForOwner(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Fleets for the selected business
    val fleetsForActiveBusiness: Flow<List<FleetEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getFleetsForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Members for the selected business
    val membersForActiveBusiness: Flow<List<FleetMemberEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getMembersForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Vehicles for the selected business
    val vehiclesForActiveBusiness: Flow<List<VehicleEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getVehiclesForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Fleets the user belongs to as a Driver
    val fleetsAsDriver: Flow<List<FleetEntity>> = flow {
        emitAll(fleetDao.getFleetsForDriver(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Vehicles assigned to the current user (as a Driver)
    val assignedVehiclesAsDriver: Flow<List<VehicleEntity>> = flow {
        emitAll(fleetDao.getAssignedVehiclesForDriver(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Current active chat partner
    private val _selectedPartner = MutableStateFlow<FleetMemberEntity?>(null)
    val selectedPartner = _selectedPartner.asStateFlow()

    // Recording status
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var activeRecordFile: File? = null
    private var recordStartTime: Long = 0

    // Audio Playback status
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId = _playingMessageId.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val audioProgress = _audioProgress.asStateFlow()

    private val _audioPositionText = MutableStateFlow("0:00")
    val audioPositionText = _audioPositionText.asStateFlow()

    // Block status
    private val _isPartnerBlocked = MutableStateFlow(false)
    val isPartnerBlocked = _isPartnerBlocked.asStateFlow()

    // ── Voice Call Session State (Mesh VoIP / PTT) ──
    data class VoiceCallSession(
        val isActive: Boolean = false,
        val partnerId: String = "",
        val durationSeconds: Int = 0,
        val route: MeshTransportLevel = MeshTransportLevel.WIFI_DIRECT_MESH,
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = true
    )

    private val _activeVoiceCall = MutableStateFlow<VoiceCallSession?>(null)
    val activeVoiceCall: StateFlow<VoiceCallSession?> = _activeVoiceCall.asStateFlow()
    private var callTimerJob: kotlinx.coroutines.Job? = null

    // ── Video Call Session State (Mesh Video P2P en Vivo) ──
    data class VideoCallSession(
        val isActive: Boolean = false,
        val partnerId: String = "",
        val durationSeconds: Int = 0,
        val route: MeshTransportLevel = MeshTransportLevel.WIFI_DIRECT_MESH,
        val isMuted: Boolean = false,
        val isVideoActive: Boolean = true,
        val isFrontCamera: Boolean = true,
        val resolution: String = "720p · 30 FPS",
        val bitrateKbps: Int = 1850,
        val latencyMs: Int = 18
    )

    private val _activeVideoCall = MutableStateFlow<VideoCallSession?>(null)
    val activeVideoCall: StateFlow<VideoCallSession?> = _activeVideoCall.asStateFlow()
    private var videoCallTimerJob: kotlinx.coroutines.Job? = null

    init {
        // Escuchar llamadas P2P entrantes desde el motor físico
        viewModelScope.launch {
            ElysiumPhysicalMeshEngine.incomingCallEvents.collect { incoming ->
                val dummyPartner = FleetMemberEntity(
                    id = incoming.callerId,
                    businessId = "mesh_p2p",
                    userId = incoming.callerId,
                    role = "CONDUCTOR",
                    email = "${incoming.callerName}@mesh.direct",
                    inviteStatus = "ACCEPTED",
                    joinedAt = System.currentTimeMillis()
                )
                _selectedPartner.value = dummyPartner

                if (incoming.callType == "VIDEO") {
                    _activeVideoCall.value = VideoCallSession(
                        isActive = true,
                        partnerId = incoming.callerId,
                        durationSeconds = 0,
                        route = MeshTransportLevel.WIFI_DIRECT_MESH
                    )
                } else {
                    _activeVoiceCall.value = VoiceCallSession(
                        isActive = true,
                        partnerId = incoming.callerId,
                        durationSeconds = 0,
                        route = MeshTransportLevel.WIFI_DIRECT_MESH
                    )
                }
            }
        }
        viewModelScope.launch {
            ElysiumPhysicalMeshEngine.callTerminatedEvents.collect {
                _activeVoiceCall.value = null
                _activeVideoCall.value = null
                callTimerJob?.cancel()
                videoCallTimerJob?.cancel()
            }
        }
    }

    /**
     * Enlaza un nodo físico descubierto y abre el chat directamente
     */
    fun linkPhysicalPeer(peer: PhysicalMeshPeer) {
        viewModelScope.launch {
            val member = ElysiumPhysicalMeshEngine.linkPeerOneTouch(peer)
            selectPartner(member)
        }
    }

    /**
     * Enlaza directamente por IP (IPv4) o por payload de QR offline
     */
    fun linkPeerByIpOrPayload(input: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = input.trim()
            val parsedPeer = if (trimmed.startsWith("elysium-mesh://")) {
                ElysiumPhysicalMeshEngine.parseOfflineQrPayload(trimmed)
            } else {
                val ipPart = trimmed.substringBefore(":")
                val portPart = trimmed.substringAfter(":", "36901").toIntOrNull() ?: ElysiumPhysicalMeshEngine.TCP_DATA_PORT
                if (ipPart.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                    PhysicalMeshPeer(
                        nodeId = "EVG-PEER-${ipPart.replace(".", "")}",
                        deviceName = "Nodo Directo ($ipPart)",
                        ipAddress = ipPart,
                        port = portPart,
                        lastSeenMs = System.currentTimeMillis()
                    )
                } else {
                    null
                }
            }

            if (parsedPeer == null) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Formato no válido. Ingresa una IP IPv4 (ej: 192.168.1.20) o un código QR elysium-mesh.")
                }
                return@launch
            }

            val member = ElysiumPhysicalMeshEngine.linkPeerOneTouch(parsedPeer)
            withContext(Dispatchers.Main) {
                selectPartner(member)
                onResult(true, "Nodo enlazado con éxito: ${parsedPeer.deviceName}")
            }
        }
    }


    /**
     * Resuelve el par físico para transmisión directa por socket P2P
     */
    fun findTargetMeshPeer(partnerUserId: String): PhysicalMeshPeer? {
        val linked = ElysiumMeshGovernance.activeLinkedPeer.value
        if (linked != null && (linked.nodeId == partnerUserId || linked.nodeId.contains(partnerUserId, ignoreCase = true) || partnerUserId.contains(linked.nodeId, ignoreCase = true))) {
            return linked
        }
        return ElysiumMeshGovernance.discoveredPhysicalPeers.value.find {
            it.nodeId == partnerUserId || it.nodeId.contains(partnerUserId, ignoreCase = true) || partnerUserId.contains(it.nodeId, ignoreCase = true)
        } ?: linked ?: ElysiumMeshGovernance.discoveredPhysicalPeers.value.firstOrNull()
    }

    /**
     * Set active business ID
     */
    fun selectBusiness(businessId: String) {
        _selectedBusinessId.value = businessId
    }

    /**
     * Get recent chat conversations for the current business or all direct mesh chats
     */
    val recentChats: Flow<List<ChatMessageEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        chatDao.getAllRecentChats(currentUserId)
    }.flowOn(Dispatchers.IO)

    /**
     * Get chat messages between current user and selected partner across business or direct mesh
     */
    val messages: Flow<List<ChatMessageEntity>> = selectedPartner.flatMapLatest { partner ->
        if (partner == null) flowOf(emptyList())
        else chatDao.getChatHistoryAnyBusiness(currentUserId, partner.userId)
    }.flowOn(Dispatchers.IO)

    /**
     * Select active chat partner
     */
    fun selectPartner(partner: FleetMemberEntity?) {
        _selectedPartner.value = partner
        _playingMessageId.value = null
        voicePlayer.stop()
        checkBlockStatus()
    }

    private fun checkBlockStatus() {
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"
        val partner = _selectedPartner.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val blocked = chatDao.isUserBlockedBy(businessId, currentUserId, partner.userId) > 0
            _isPartnerBlocked.value = blocked
        }
    }

    /**
     * Toggle block state for active partner
     */
    fun toggleBlockActivePartner() {
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"
        val partner = _selectedPartner.value ?: return
        val currentlyBlocked = _isPartnerBlocked.value

        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyBlocked) {
                chatDao.unblockUser(businessId, currentUserId, partner.userId)
                _isPartnerBlocked.value = false
            } else {
                val blockEntry = ChatBlocklistEntity(
                    id = "${businessId}_${currentUserId}_${partner.userId}",
                    businessId = businessId,
                    blockerUserId = currentUserId,
                    blockedUserId = partner.userId,
                    blockedAt = System.currentTimeMillis()
                )
                chatDao.blockUser(blockEntry)
                _isPartnerBlocked.value = true
            }
        }
    }

    /**
     * Report a user for policy violation (UGC moderation).
     * Google Play requires accessible reporting for UGC apps.
     */
    fun reportUser(reason: String, description: String, reportedMessageId: String? = null) {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = ChatReportEntity(
                    id = "${businessId}_${currentUserId}_${partner.userId}_${System.currentTimeMillis()}",
                    businessId = businessId,
                    reporterUserId = currentUserId,
                    reportedUserId = partner.userId,
                    reportedMessageId = reportedMessageId,
                    reason = reason,
                    description = description,
                    status = "PENDING",
                    createdAt = System.currentTimeMillis(),
                    reviewedAt = null
                )
                chatReportDao.insertReport(report)
                Log.i("FleetChatVM", "User report submitted: ${report.id}")
            } catch (e: Exception) {
                Log.e("FleetChatVM", "Failed to submit user report", e)
            }
        }
    }

    /**
     * Send standard text message
     */
    fun sendTextMessage(text: String) {
        val partner = _selectedPartner.value ?: return
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"
        if (text.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Check if there is block restriction
            if (chatDao.hasBlockBetween(businessId, currentUserId, partner.userId) > 0) {
                Log.w("FleetChatVM", "Cannot send message: User is blocked or has blocked partner")
                return@launch
            }

            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = text,
                messageType = "TEXT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)

            // 1. Intentar transmisión física Mesh P2P directa si hay nodo en alcance o estamos off-grid
            val peer = findTargetMeshPeer(partner.userId)
            if (peer != null) {
                val ok = ElysiumPhysicalMeshEngine.sendTextMessage(peer.ipAddress, peer.port, msg)
                if (ok) {
                    return@launch
                }
            }

            // 2. Si no se pudo por socket P2P directo o estamos conectados a la nube
            simulateMessageDelivery(msg.id, MeshPayloadType.TEXT)
        }
    }

    /**
     * Start recording voice note
     */
    fun startRecordingVoice() {
        if (_isRecording.value) return
        activeRecordFile = voiceRecorder.startRecording()
        if (activeRecordFile != null) {
            _isRecording.value = true
            recordStartTime = System.currentTimeMillis()
        }
    }

    /**
     * Stop and send voice note
     */
    fun stopAndSendVoice() {
        if (!_isRecording.value) return
        val file = voiceRecorder.stopRecording()
        _isRecording.value = false
        val durationMs = System.currentTimeMillis() - recordStartTime
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)

        val partner = _selectedPartner.value ?: return
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"

        if (file != null && file.exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                if (chatDao.hasBlockBetween(businessId, currentUserId, partner.userId) > 0) {
                    file.delete()
                    return@launch
                }

                val msg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    senderId = currentUserId,
                    receiverId = partner.userId,
                    messageText = null,
                    messageType = "AUDIO",
                    fileLocalPath = file.absolutePath,
                    fileRemoteUrl = null,
                    durationSeconds = durationSec,
                    timestamp = System.currentTimeMillis(),
                    status = "PENDING"
                )
                chatDao.insertMessage(msg)

                val peer = findTargetMeshPeer(partner.userId)
                if (peer != null) {
                    val ok = ElysiumPhysicalMeshEngine.sendAudioFile(peer.ipAddress, peer.port, msg, file, durationSec)
                    if (ok) {
                        return@launch
                    }
                }

                simulateMessageDelivery(msg.id, MeshPayloadType.AUDIO_NOTE)
            }
        }
        activeRecordFile = null
    }

    /**
     * Cancel active recording
     */
    fun cancelRecordingVoice() {
        if (!_isRecording.value) return
        voiceRecorder.cancelRecording()
        _isRecording.value = false
        activeRecordFile = null
    }

    /**
     * Play or Pause voice note
     */
    fun togglePlayVoice(message: ChatMessageEntity) {
        val path = message.fileLocalPath ?: message.fileRemoteUrl
        if (path.isNullOrEmpty()) return

        if (_playingMessageId.value == message.id) {
            if (voicePlayer.isPlaying()) {
                voicePlayer.pause()
            } else {
                voicePlayer.resume()
            }
        } else {
            _playingMessageId.value = message.id
            _audioProgress.value = 0f
            _audioPositionText.value = "0:00"

            voicePlayer.play(
                sourcePath = path,
                onProgress = { current, total ->
                    if (total > 0) {
                        _audioProgress.value = current.toFloat() / total.toFloat()
                        val sec = (current / 1000) % 60
                        val min = (current / 1000) / 60
                        _audioPositionText.value = String.format("%d:%02d", min, sec)
                    }
                },
                onComplete = {
                    _playingMessageId.value = null
                    _audioProgress.value = 0f
                    _audioPositionText.value = "0:00"
                },
                onError = { err ->
                    Log.e("FleetChatVM", "Audio play failed: $err")
                    _playingMessageId.value = null
                    _audioProgress.value = 0f
                }
            )
        }
    }

    /**
     * Send a special DTC Alert message to the driver
     */
    fun sendDtcAlertMessage(vehicleName: String, dtcCodes: List<String>) {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return
        if (dtcCodes.isEmpty()) return

        val text = "⚠️ ALERTA DE DIAGNÓSTICO VEHICULAR\nVehículo: $vehicleName\nCódigos de falla activos: ${dtcCodes.joinToString(", ")}\nSe recomienda verificar el estado de inmediato."

        viewModelScope.launch(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = text,
                messageType = "DTC_ALERT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id, MeshPayloadType.DTC_ALERT)
        }
    }

    /**
     * Send file or image attachment
     */
    fun sendFileAttachment(file: File, type: String) { // "IMAGE", "PDF", etc.
        val partner = _selectedPartner.value ?: return

        val isImg = type.contains("IMAGE", ignoreCase = true) || file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
        val effectiveType = if (isImg) "IMAGE" else "FILE"
        val payloadType = if (isImg) MeshPayloadType.IMAGE else MeshPayloadType.TEXT
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"

        viewModelScope.launch(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = file.name,
                messageType = effectiveType,
                fileLocalPath = file.absolutePath,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)

            val peer = findTargetMeshPeer(partner.userId)
            if (peer != null) {
                val ok = ElysiumPhysicalMeshEngine.sendImageFile(peer.ipAddress, peer.port, msg, file)
                if (ok) {
                    return@launch
                }
            }

            simulateMessageDelivery(msg.id, payloadType)
        }
    }

    /**
     * Create a business profile
     */
    fun createBusinessProfile(
        name: String,
        taxId: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val authenticatedUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (authenticatedUserId == null) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Inicia sesión antes de registrar una empresa de flota.")
                }
                return@launch
            }
            val profile = BusinessProfileEntity(
                id = "b_${UUID.randomUUID().toString().take(6)}",
                name = name,
                taxId = taxId,
                planType = "PREMIUM_FLEET",
                maxVehicles = 50,
                createdAt = System.currentTimeMillis(),
                ownerUserId = authenticatedUserId
            )
            fleetDao.insertBusinessProfile(profile)
            _selectedBusinessId.value = profile.id
            val delivery = runCatching {
                PlatformTrustCenterGateway.submit(
                    ServiceVerificationSubmission(
                        serviceType = "FLEET_OPERATOR",
                        profileReference = profile.id,
                        displayName = name.trim(),
                        businessName = name.trim(),
                    ),
                )
            }
            withContext(Dispatchers.Main) {
                if (delivery.isSuccess) {
                    onResult(
                        true,
                        "Empresa creada y solicitud enviada al Centro de Confianza. Estado: pendiente.",
                    )
                } else {
                    onResult(
                        true,
                        "Empresa guardada; la solicitud remota queda pendiente y se reintentará al abrir este panel.",
                    )
                }
            }
        }
    }

    /** Replays locally durable fleet applications that have no remote receipt yet. */
    fun syncPendingFleetOperatorApplications() {
        val authenticatedUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val delivered = PlatformTrustCenterGateway.loadOwnApplications()
                    .asSequence()
                    .filter { it.serviceType == "FLEET_OPERATOR" }
                    .mapTo(mutableSetOf()) { it.profileReference }
                fleetDao.getBusinessProfilesForOwner(authenticatedUserId).first()
                    .filterNot { it.id in delivered }
                    .forEach { profile ->
                        PlatformTrustCenterGateway.submit(
                            ServiceVerificationSubmission(
                                serviceType = "FLEET_OPERATOR",
                                profileReference = profile.id,
                                displayName = profile.name,
                                businessName = profile.name,
                            ),
                        )
                    }
            }.onFailure {
                Log.w("ElysiumTrustCenter", "Fleet operator review sync unavailable", it)
            }
        }
    }

    /**
     * Create a fleet with an invite code
     */
    fun createFleet(name: String, description: String?) {
        val businessId = _selectedBusinessId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val randomCode = "EVG-FLT-${(1000..9999).random()}"
            val fleet = FleetEntity(
                id = "f_${UUID.randomUUID().toString().take(6)}",
                businessId = businessId,
                name = name,
                description = description,
                inviteCode = randomCode,
                createdAt = System.currentTimeMillis()
            )
            fleetDao.insertFleet(fleet)
        }
    }

    /**
     * Join fleet by invite code
     */
    fun joinFleetByCode(inviteCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fleet = fleetDao.getFleetByInviteCode(inviteCode.trim().uppercase())
            if (fleet != null) {
                // Add driver as member of the fleet
                val member = FleetMemberEntity(
                    id = "m_${currentUserId}_${fleet.id}",
                    businessId = fleet.businessId,
                    userId = currentUserId,
                    role = "DRIVER",
                    email = "conductor_${currentUserId.take(4)}@meet.com",
                    inviteStatus = "ACCEPTED",
                    joinedAt = System.currentTimeMillis(),
                    fleetId = fleet.id
                )
                fleetDao.insertFleetMember(member)
                launch(Dispatchers.Main) {
                    onResult(true, "Te has unido a la flota ${fleet.name} correctamente.")
                }
            } else {
                launch(Dispatchers.Main) {
                    onResult(false, "Código de invitación no válido o inexistente.")
                }
            }
        }
    }

    /**
     * Assign driver and fleet to vehicle
     */
    fun assignVehicleToDriverAndFleet(vehicleId: String, driverId: String?, fleetId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            if (vehicle != null) {
                val updated = vehicle.copy(
                    assignedDriverId = driverId,
                    fleetId = fleetId,
                    businessId = _selectedBusinessId.value
                )
                vehicleDao.insertVehicle(updated)
            }
        }
    }

    /**
     * Submit DVIR Report
     */
    fun submitDvirReport(
        vehicleId: String,
        brakesOk: Boolean,
        lightsOk: Boolean,
        tiresOk: Boolean,
        fluidsOk: Boolean,
        batteryOk: Boolean,
        onResult: () -> Unit
    ) {
        val businessId = _selectedBusinessId.value ?: "b1"
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            val vehicleName = vehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo $vehicleId"
            
            val issues = mutableListOf<String>()
            if (!brakesOk) issues.add("Frenos 🛑")
            if (!lightsOk) issues.add("Luces 💡")
            if (!tiresOk) issues.add("Llantas 🛞")
            if (!fluidsOk) issues.add("Fluidos/Aceite 💧")
            if (!batteryOk) issues.add("Batería ⚡")

            val statusText = if (issues.isEmpty()) {
                "✅ TODO EN ORDEN. Listo para circular."
            } else {
                "⚠️ REPORTÓ FALLAS EN: ${issues.joinToString(", ")}"
            }

            val messageText = "📋 REPORTE DE INSPECCIÓN DIARIA (DVIR)\nVehículo: $vehicleName\nEstado: $statusText\nConductor: Conductor ($currentUserId)"

            // Send chat alert to fleet channel
            // Find business owner/admin to receive it, or just broadcast it
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = "local_owner", // Send to owner
                messageText = messageText,
                messageType = "DTC_ALERT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id)

            launch(Dispatchers.Main) {
                onResult()
            }
        }
    }

    // ── Voice Call Controls (Mesh VoIP / Walkie-Talkie) ──
    fun startVoiceCall() {
        val partner = _selectedPartner.value ?: return
        val resolution = ElysiumMeshGovernance.resolveRouteForPayload(MeshPayloadType.VOICE_CALL_STREAM)
        val peer = findTargetMeshPeer(partner.userId)

        _activeVoiceCall.value = VoiceCallSession(
            isActive = true,
            partnerId = partner.userId,
            durationSeconds = 0,
            route = resolution.level
        )

        if (peer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ElysiumPhysicalMeshEngine.sendCallSignal(peer.ipAddress, peer.port, "RING_AUDIO", partner.userId)
                ElysiumPhysicalMeshEngine.startAudioCallStreaming(peer.ipAddress)
            }
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true && _activeVoiceCall.value?.isActive == true) {
                kotlinx.coroutines.delay(1000)
                _activeVoiceCall.update { current ->
                    current?.copy(durationSeconds = current.durationSeconds + 1)
                }
            }
        }
    }

    fun toggleCallMute() {
        _activeVoiceCall.update { current ->
            current?.copy(isMuted = !current.isMuted)
        }
    }

    fun toggleCallSpeaker() {
        _activeVoiceCall.update { current ->
            current?.copy(isSpeakerOn = !current.isSpeakerOn)
        }
    }

    fun endVoiceCall() {
        val call = _activeVoiceCall.value ?: return
        val partner = _selectedPartner.value ?: return
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"
        val peer = findTargetMeshPeer(partner.userId)

        if (peer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ElysiumPhysicalMeshEngine.sendCallSignal(peer.ipAddress, peer.port, "END", partner.userId)
            }
        }
        ElysiumPhysicalMeshEngine.stopAudioCallStreaming()

        callTimerJob?.cancel()
        callTimerJob = null
        val duration = call.durationSeconds
        _activeVoiceCall.value = null

        val min = duration / 60
        val sec = duration % 60
        val durationText = String.format("%d:%02d", min, sec)

        viewModelScope.launch(Dispatchers.IO) {
            val routeDesc = when (call.route) {
                MeshTransportLevel.WIFI_DIRECT_MESH -> "Wi-Fi Mesh P2P"
                MeshTransportLevel.BLUETOOTH_BLE_MESH -> "BLE Mesh (PTT)"
                MeshTransportLevel.INTERNET -> "Internet Nube"
                else -> "Mesh"
            }
            val callMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = "📞 Llamada de voz finalizada · $durationText ($routeDesc)",
                messageType = "CALL",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = duration,
                timestamp = System.currentTimeMillis(),
                status = "DELIVERED"
            )
            chatDao.insertMessage(callMsg)
        }
    }

    // ── Video Call Controls (Mesh Video P2P en Vivo) ──
    fun startVideoCall() {
        val partner = _selectedPartner.value ?: return
        val resolution = ElysiumMeshGovernance.resolveRouteForPayload(MeshPayloadType.VIDEO_CALL_STREAM)
        val peer = findTargetMeshPeer(partner.userId)

        _activeVideoCall.value = VideoCallSession(
            isActive = true,
            partnerId = partner.userId,
            durationSeconds = 0,
            route = resolution.level
        )

        if (peer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ElysiumPhysicalMeshEngine.sendCallSignal(peer.ipAddress, peer.port, "RING_VIDEO", partner.userId)
                ElysiumPhysicalMeshEngine.startAudioCallStreaming(peer.ipAddress)
                ElysiumPhysicalMeshEngine.startVideoCallStreaming(peer.ipAddress)
            }
        }

        videoCallTimerJob?.cancel()
        videoCallTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true && _activeVideoCall.value?.isActive == true) {
                kotlinx.coroutines.delay(1000)
                _activeVideoCall.update { current ->
                    current?.copy(durationSeconds = current.durationSeconds + 1)
                }
            }
        }
    }

    fun toggleVideoCallMute() {
        _activeVideoCall.update { current ->
            current?.copy(isMuted = !current.isMuted)
        }
    }

    fun toggleVideoCallCamera() {
        _activeVideoCall.update { current ->
            current?.copy(isVideoActive = !current.isVideoActive)
        }
    }

    fun flipVideoCallCamera() {
        _activeVideoCall.update { current ->
            current?.copy(isFrontCamera = !current.isFrontCamera)
        }
    }

    fun endVideoCall() {
        val call = _activeVideoCall.value ?: return
        val partner = _selectedPartner.value ?: return
        val businessId = _selectedBusinessId.value ?: "mesh_p2p"
        val peer = findTargetMeshPeer(partner.userId)

        if (peer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ElysiumPhysicalMeshEngine.sendCallSignal(peer.ipAddress, peer.port, "END", partner.userId)
            }
        }
        ElysiumPhysicalMeshEngine.stopAudioCallStreaming()
        ElysiumPhysicalMeshEngine.stopVideoCallStreaming()

        videoCallTimerJob?.cancel()
        videoCallTimerJob = null
        val duration = call.durationSeconds
        _activeVideoCall.value = null

        val min = duration / 60
        val sec = duration % 60
        val durationText = String.format("%d:%02d", min, sec)

        viewModelScope.launch(Dispatchers.IO) {
            val routeDesc = when (call.route) {
                MeshTransportLevel.WIFI_DIRECT_MESH -> "Wi-Fi Mesh P2P Directo"
                MeshTransportLevel.INTERNET -> "Internet Nube"
                else -> "Mesh Local"
            }
            val callMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = "📹 Videollamada finalizada · $durationText ($routeDesc)",
                messageType = "CALL",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = duration,
                timestamp = System.currentTimeMillis(),
                status = "DELIVERED"
            )
            chatDao.insertMessage(callMsg)
        }
    }

    private suspend fun simulateMessageDelivery(msgId: String, payloadType: MeshPayloadType = MeshPayloadType.TEXT) {
        val resolution = ElysiumMeshGovernance.resolveRouteForPayload(payloadType)
        ElysiumMeshGovernance.recordPayloadSent(payloadType)
        kotlinx.coroutines.delay(400)
        chatDao.updateMessageStatus(msgId, "SENT")
        kotlinx.coroutines.delay(400)
        val finalStatus = when (resolution.level) {
            MeshTransportLevel.INTERNET -> "DELIVERED"
            MeshTransportLevel.WIFI_DIRECT_MESH -> "DELIVERED_MESH_WIFI"
            MeshTransportLevel.BLUETOOTH_BLE_MESH -> "DELIVERED_MESH_BLE"
            MeshTransportLevel.OFFLINE_STORED -> "PENDING_MESH_OUTBOX"
        }
        chatDao.updateMessageStatus(msgId, finalStatus)
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
        videoCallTimerJob?.cancel()
        voicePlayer.stop()
        voiceRecorder.cancelRecording()
    }
}
