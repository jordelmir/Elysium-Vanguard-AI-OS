package com.elysium369.meet.communications

import com.elysium369.meet.data.local.dao.CommunicationDao
import com.elysium369.meet.data.local.entities.CommunicationCallEntity
import com.elysium369.meet.data.local.entities.CommunicationConversationEntity
import com.elysium369.meet.data.local.entities.CommunicationEventEntity
import com.elysium369.meet.data.local.entities.CommunicationParticipantEntity
import com.elysium369.meet.data.local.entities.CommunicationIdentityProfileEntity
import com.elysium369.meet.data.local.entities.CommunicationLocalBlockEntity
import com.elysium369.meet.data.local.entities.CommunicationPrivacySettingsEntity
import com.elysium369.meet.data.local.entities.CommunicationRelationshipEntity
import com.elysium369.meet.identity.ActivePrincipalKernel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ElysiumCommunicationRepository @Inject constructor(
    private val dao: CommunicationDao,
    private val principalKernel: ActivePrincipalKernel,
    private val cipher: DeviceMessageCipher,
    private val transportCipher: CommunicationTransportCipher,
    private val nearbyTransport: CommunicationNearbyTransport,
    private val communicationScope: kotlinx.coroutines.CoroutineScope,
    private val callTransport: ElysiumCallTransport,
    private val remoteGateway: CommunicationRemoteGateway,
    private val aliasMatcher: DeviceAliasMatcher,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) {
    val speakerEnabled = callTransport.speakerEnabled
    fun setSpeakerEnabled(enabled:Boolean):Boolean = callTransport.setSpeakerEnabled(enabled)
    val callState = callTransport.state
    val incomingCall = callTransport.incoming
    val conversations: Flow<List<ConversationSummary>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeConversations(principal.id).map { rows -> rows.map(::toSummary) }
        }

    val identity: Flow<ElysiumIdentityProfile> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeIdentityProfile(principal.id).map { row -> row?.toIdentity() ?: defaultIdentity(principal.id) }
    }

    val privacy: Flow<CommunicationPrivacySettings> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observePrivacySettings(principal.id).map { row -> row?.toPrivacy() ?: CommunicationPrivacySettings() }
    }

    val contacts: Flow<List<ElysiumContact>> = principalKernel.activePrincipal.flatMapLatest { principal ->
        combine(
            dao.observeRelationships(principal.id),
            dao.observeBlocks(principal.id),
        ) { relationships, blocks ->
            val blocked = blocks.mapTo(mutableSetOf()) { it.blockedPrincipalId }
            relationships.map { row ->
                ElysiumContact(
                    principalId = row.peerPrincipalId,
                    elysiumId = row.peerElysiumId,
                    displayName = row.peerDisplayName,
                    relationshipState = row.relationshipState,
                    aliasProofState = row.aliasProofState,
                    isBlocked = row.peerPrincipalId in blocked,
                )
            }
        }
    }

    val blockedContacts: Flow<List<BlockedContact>> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeBlocks(principal.id).map { rows ->
            rows.map { BlockedContact(it.blockedPrincipalId, it.blockedDisplayName, it.syncState, it.createdAtEpochMs) }
        }
    }

    private val applicationContext = context
    private val onlineCursors = context.getSharedPreferences("meet_communication_online_cursor_v1", android.content.Context.MODE_PRIVATE)
    private val onlineMutex = kotlinx.coroutines.sync.Mutex()
    val onlineStatus = kotlinx.coroutines.flow.MutableStateFlow("Sin sincronización remota confirmada")

    private val nearbyJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val nearbyStatus = nearbyTransport.status
    val nearbyPeers = nearbyTransport.discovered
    val nearbyInvitations = nearbyTransport.invitations

    init {
        communicationScope.launch {
            principalKernel.activePrincipal.collect { principal -> nearbyTransport.stop();callTransport.watch(principal.id.takeIf { remoteGateway.authenticatedPrincipalId()==it }) }
        }
        nearbyTransport.receive = { endpoint,bytes ->
            communicationScope.launch(kotlinx.coroutines.Dispatchers.IO) { receiveNearby(endpoint,bytes) }
        }
    }

    fun startNearby() { nearbyTransport.start() }
    fun stopNearby() { nearbyTransport.stop() }
    fun connectNearby(id:String) { nearbyTransport.connect(id) }
    fun answerNearby(id:String,accept:Boolean) { nearbyTransport.answer(id,accept) }

    private fun cacheNearbyKeys(owner:String,conversation:String,keys:List<CommunicationDeviceKeyWire>) {
        onlineCursors.edit().putString("keys:$owner:$conversation",nearbyJson.encodeToString(keys))
            .putLong("keysTime:$owner:$conversation",System.currentTimeMillis()).commit()
    }

    private fun cachedNearbyKeys(owner:String,conversation:String):List<CommunicationDeviceKeyWire> {
        val timestamp=onlineCursors.getLong("keysTime:$owner:$conversation",0)
        if(System.currentTimeMillis()-timestamp !in 0..86_400_000L) return emptyList()
        val encoded=onlineCursors.getString("keys:$owner:$conversation",null) ?: return emptyList()
        return runCatching { nearbyJson.decodeFromString<List<CommunicationDeviceKeyWire>>(encoded) }.getOrDefault(emptyList())
    }

    /** Offline delivery retains the same event id for eventual server reconciliation. */
    suspend fun flushNearby() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // First try the Physical Mesh Engine (TCP/UDP direct sockets — works over any Wi-Fi/Hotspot)
        flushMeshBridge()
        // Then try Google Nearby Connections (BLE/Wi-Fi Direct)
        onlineMutex.withLock {
        val owner=principalKernel.current().id
        val now=System.currentTimeMillis()
        for(event in dao.pendingOnlineEvents(owner)) {
            if(event.syncState=="NEARBY_ACK" || event.syncState=="DELIVERED_MESH_WIFI" || event.eventType!="TEXT") continue
            if(principalKernel.current().id!=owner) return@withContext
            val conversation=dao.getOnlineConversation(event.conversationId,owner) ?: continue
            if(conversation.requestState!="ACCEPTED" || conversation.proofState!="SERVER_AUTHORITATIVE") continue
            val peers=dao.activePeerPrincipalIds(event.conversationId,owner)
            if(peers.any { dao.isBlocked(owner,it) }) continue
            val keys=cachedNearbyKeys(owner,event.conversationId)
            if(peers.isEmpty() || peers.any { p -> keys.none { it.principalId==p } }) continue
            if(now-event.createdAtEpochMs !in 0..86_400_000L) continue
            try {
                val envelope=event.remoteEnvelopeJson ?: transportCipher.encrypt(
                    cipher.decrypt(LocalCipherPayload(event.localCiphertextBase64,event.localNonceBase64),
                        associatedData(event.conversationId,event.eventId,owner)),
                    transportAad(event.conversationId,event.eventId,owner,event.senderDeviceId,event.eventType),
                    keys.associate { it.deviceId to it.publicKey }
                ).also { dao.persistTransportEnvelope(event.eventId,owner,it) }
                val message=CommunicationNearbyMessage(purpose="DATA",eventId=event.eventId,
                    conversationId=event.conversationId,senderId=owner,senderDeviceId=event.senderDeviceId,
                    envelope=envelope,createdAt=event.createdAtEpochMs,expiresAt=event.createdAtEpochMs+86_400_000L)
                val payload=nearbyJson.encodeToString(message)
                val packet=nearbyJson.encodeToString(CommunicationNearbyPacket(payload,transportCipher.sign(owner,payload)))
                nearbyTransport.send(packet.toByteArray(Charsets.UTF_8))
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(_:Exception) { nearbyStatus.value="Mensaje conservado; faltan claves o enlace cercano autorizado" }
        }
    }

    }

    /**
     * Bridge: envía mensajes pendientes a través del motor físico Mesh (TCP/UDP o Bluetooth RFCOMM).
     * - TCP/UDP: funciona sobre cualquier Wi-Fi (hotspot, mesh, LAN)
     * - Bluetooth RFCOMM: funciona solo con Bluetooth pareado, sin Wi-Fi
     * Complementa el transporte Nearby de Google, no lo reemplaza.
     */
    private suspend fun flushMeshBridge() {
        try {
            val meshPeer = com.elysium369.meet.core.mesh.ElysiumMeshGovernance.activeLinkedPeer.value
                ?: com.elysium369.meet.core.mesh.ElysiumMeshGovernance.discoveredPhysicalPeers.value.firstOrNull()
            val hasBtPeer = com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.hasConnectedPeer()

            if (meshPeer == null && !hasBtPeer) return // No hay ningún peer conectado

            val owner = principalKernel.current().id
            for (event in dao.pendingOnlineEvents(owner)) {
                if (event.syncState == "NEARBY_ACK" || event.syncState == "DELIVERED_MESH_WIFI") continue
                if (event.eventType !in setOf("TEXT", "AUDIO", "IMAGE", "VOICE_NOTE")) continue
                val plaintext = try {
                    cipher.decrypt(
                        LocalCipherPayload(event.localCiphertextBase64, event.localNonceBase64),
                        associatedData(event.conversationId, event.eventId, owner)
                    )
                } catch (_: Exception) { continue }

                // Determine transport: Bluetooth RFCOMM vs TCP
                val isBtPeer = meshPeer?.ipAddress?.startsWith("bt:") == true || (meshPeer == null && hasBtPeer)
                var delivered = false

                when (event.eventType) {
                    "TEXT" -> {
                        delivered = if (isBtPeer || (meshPeer == null && hasBtPeer)) {
                            com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendText(
                                meshPeer?.ipAddress, owner, event.eventId, plaintext
                            )
                        } else {
                            val chatMsg = com.elysium369.meet.data.local.entities.ChatMessageEntity(
                                id = event.eventId, businessId = "elysium_comm", senderId = owner,
                                receiverId = meshPeer!!.nodeId, messageText = plaintext, messageType = "TEXT",
                                fileLocalPath = null, fileRemoteUrl = null, durationSeconds = 0,
                                timestamp = event.createdAtEpochMs, status = "PENDING"
                            )
                            com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.sendTextMessage(
                                meshPeer.ipAddress, meshPeer.port, chatMsg
                            )
                        }
                        // TCP fallback to BT if TCP failed
                        if (!delivered && hasBtPeer && !isBtPeer) {
                            delivered = com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendText(
                                null, owner, event.eventId, plaintext
                            )
                        }
                        if (delivered) android.util.Log.i("CommRepo", "TEXT enviado por ${if (isBtPeer) "BT" else "TCP"}")
                    }
                    "AUDIO", "VOICE_NOTE" -> {
                        val audioFile = CommunicationMediaCodec.file(applicationContext, owner, event.eventId, "AUDIO")
                        if (!audioFile.exists()) continue
                        delivered = if (isBtPeer || (meshPeer == null && hasBtPeer)) {
                            com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendAudioFile(
                                meshPeer?.ipAddress, owner, event.eventId, audioFile
                            )
                        } else {
                            val chatMsg = com.elysium369.meet.data.local.entities.ChatMessageEntity(
                                id = event.eventId, businessId = "elysium_comm", senderId = owner,
                                receiverId = meshPeer!!.nodeId, messageText = null, messageType = "AUDIO",
                                fileLocalPath = audioFile.absolutePath, fileRemoteUrl = null, durationSeconds = 0,
                                timestamp = event.createdAtEpochMs, status = "PENDING"
                            )
                            com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.sendAudioFile(
                                meshPeer.ipAddress, meshPeer.port, chatMsg, audioFile, 0
                            )
                        }
                        if (!delivered && hasBtPeer && !isBtPeer) {
                            delivered = com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendAudioFile(
                                null, owner, event.eventId, audioFile
                            )
                        }
                        if (delivered) android.util.Log.i("CommRepo", "AUDIO enviado por ${if (isBtPeer) "BT" else "TCP"}")
                    }
                    "IMAGE" -> {
                        val imageFile = CommunicationMediaCodec.file(applicationContext, owner, event.eventId, "IMAGE")
                        if (!imageFile.exists()) continue
                        delivered = if (isBtPeer || (meshPeer == null && hasBtPeer)) {
                            com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendImageFile(
                                meshPeer?.ipAddress, owner, event.eventId, imageFile
                            )
                        } else {
                            val chatMsg = com.elysium369.meet.data.local.entities.ChatMessageEntity(
                                id = event.eventId, businessId = "elysium_comm", senderId = owner,
                                receiverId = meshPeer!!.nodeId, messageText = null, messageType = "IMAGE",
                                fileLocalPath = imageFile.absolutePath, fileRemoteUrl = null, durationSeconds = 0,
                                timestamp = event.createdAtEpochMs, status = "PENDING"
                            )
                            com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.sendImageFile(
                                meshPeer.ipAddress, meshPeer.port, chatMsg, imageFile
                            )
                        }
                        if (!delivered && hasBtPeer && !isBtPeer) {
                            delivered = com.elysium369.meet.core.mesh.ElysiumBluetoothRfcommTransport.sendImageFile(
                                null, owner, event.eventId, imageFile
                            )
                        }
                        if (delivered) android.util.Log.i("CommRepo", "IMAGE enviado por ${if (isBtPeer) "BT" else "TCP"}")
                    }
                }
                if (delivered) {
                    dao.markEventSynced(event.eventId, owner, "DELIVERED_MESH_WIFI")
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (e: Exception) {
            android.util.Log.w("CommRepo", "flushMeshBridge error: ${e.message}")
        }
    }

    private suspend fun receiveNearby(endpoint:String,bytes:ByteArray) {
        try {
            val owner=principalKernel.current().id
            val now=System.currentTimeMillis()
            val packet=nearbyJson.decodeFromString<CommunicationNearbyPacket>(String(bytes,Charsets.UTF_8))
            val message=nearbyJson.decodeFromString<CommunicationNearbyMessage>(packet.payload)
            require(message.version==1 && message.purpose in setOf("DATA","ACK"))
            require(message.createdAt<=now+60_000 && message.expiresAt>now && message.expiresAt-message.createdAt in 1..86_400_000L)
            val conversation=dao.getOnlineConversation(message.conversationId,owner) ?: return
            require(conversation.requestState=="ACCEPTED" && conversation.proofState=="SERVER_AUTHORITATIVE")
            require(message.senderId in dao.activePeerPrincipalIds(message.conversationId,owner))
            require(!dao.isBlocked(owner,message.senderId))
            val senderKey=cachedNearbyKeys(owner,message.conversationId).firstOrNull {
                it.principalId==message.senderId && it.deviceId==message.senderDeviceId
            } ?: return
            require(transportCipher.verify(senderKey.publicKey,packet.payload,packet.signature))
            require(principalKernel.current().id==owner)
            if(message.purpose=="ACK") {
                dao.acknowledgeNearbyEvent(message.eventId,owner,message.conversationId)
                nearbyStatus.value="Recepción confirmada por el otro teléfono"
                return
            }
            val envelope=requireNotNull(message.envelope)
            val text=transportCipher.decrypt(envelope,owner,transportDeviceId(owner),
                transportAad(message.conversationId,message.eventId,message.senderId,message.senderDeviceId))
            require(text.length<=4000)
            val local=cipher.encrypt(text,associatedData(message.conversationId,message.eventId,message.senderId))
            val previous=dao.getOnlineEvent(message.eventId,owner,message.conversationId)
            require(previous==null || (previous.senderPrincipalId==message.senderId && previous.remoteEnvelopeJson==envelope))
            dao.appendEvent(CommunicationEventEntity(eventId=message.eventId,conversationId=message.conversationId,
                ownerPrincipalId=owner,senderPrincipalId=message.senderId,senderDeviceId=message.senderDeviceId,
                eventType="TEXT",localCiphertextBase64=local.ciphertextBase64,localNonceBase64=local.nonceBase64,
                remoteEnvelopeJson=envelope,syncState="NEARBY_RECEIVED",createdAtEpochMs=message.createdAt,receivedAtEpochMs=now))
            require(principalKernel.current().id==owner)
            val ack=CommunicationNearbyMessage(purpose="ACK",eventId=message.eventId,conversationId=message.conversationId,
                senderId=owner,senderDeviceId=transportDeviceId(owner),envelope=null,createdAt=now,expiresAt=now+60_000)
            val payload=nearbyJson.encodeToString(ack)
            nearbyTransport.send(nearbyJson.encodeToString(CommunicationNearbyPacket(payload,transportCipher.sign(owner,payload)))
                .toByteArray(Charsets.UTF_8),endpoint)
            nearbyStatus.value="Mensaje cercano recibido y guardado cifrado"
        } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_:Exception) { nearbyStatus.value="Paquete cercano rechazado: identidad, firma, vigencia o permiso no válidos" }
    }
    private fun transportDeviceId(owner: String): String = UUID.nameUUIDFromBytes(
        ("meet.communication.v2:" + owner + ":" + principalKernel.localDeviceId + ":" +
            transportCipher.publicKey(owner)).toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private fun transportAad(conversation: String, event: String, sender: String, device: String, type: String = "TEXT"): String =
        "meet.communication.v1|$conversation|$event|$sender|$device|$type"

    /** Reconcile only the active authenticated owner; Room remains an encrypted projection. */
    suspend fun synchronizeOnline(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        onlineMutex.lock()
        try {
            val owner = remoteGateway.authenticatedPrincipalId()
            if (owner == null || principalKernel.current().id != owner) {
                onlineStatus.value = "Inicia sesión para enviar y recibir en línea"
                return@withContext false
            }
            callTransport.watch(owner)
            val device = transportDeviceId(owner)
            check(remoteGateway.registerTransportDevice(device, transportCipher.publicKey(owner)))
            fun checkOwner() = check(remoteGateway.authenticatedPrincipalId() == owner && principalKernel.current().id == owner)
            val conversations = remoteGateway.onlineConversations()
            for (conversation in conversations) {
                checkOwner()
                if (dao.getOnlineConversation(conversation.id, owner) == null) {
                    val now = System.currentTimeMillis()
                    dao.insertConversation(CommunicationConversationEntity(
                        conversationId=conversation.id, ownerPrincipalId=owner, kind=conversation.kind,
                        title=conversation.title, requestState=conversation.requestState,
                        proofState="SERVER_AUTHORITATIVE", createdAtEpochMs=now, updatedAtEpochMs=now,
                    ))
                }
                dao.projectOnlineConversation(conversation.id, owner, conversation.requestState)
                for (participant in remoteGateway.onlineParticipants(conversation.id)) {
                    checkOwner()
                    dao.upsertParticipant(CommunicationParticipantEntity(
                        conversationId=conversation.id, ownerPrincipalId=owner,
                        participantPrincipalId=participant.principalId, displayName="Usuario Elysium",
                        role=participant.role, membershipState=participant.membershipState,
                        joinedAtEpochMs=System.currentTimeMillis(),
                    ))
                }
            }
            for (contact in remoteGateway.onlineContactProfiles()) {
                checkOwner()
                val now=System.currentTimeMillis()
                dao.upsertRelationship(CommunicationRelationshipEntity(
                    ownerPrincipalId=owner,peerPrincipalId=contact.principalId,
                    peerElysiumId=contact.elysiumId,peerDisplayName=contact.displayName,
                    relationshipState="ACCEPTED",initiatedByMe=false,
                    aliasProofState="SERVER_ASSERTED",createdAtEpochMs=now,updatedAtEpochMs=now,
                ))
            }
            for(legacy in dao.legacyVoiceNotes(owner)) {
                checkOwner()
                val conversation=dao.getOnlineConversation(legacy.conversationId,owner)
                if(conversation?.requestState!="ACCEPTED" || conversation.proofState!="SERVER_AUTHORITATIVE") continue
                runCatching {
                    val parts=cipher.decrypt(LocalCipherPayload(legacy.localCiphertextBase64,legacy.localNonceBase64),associatedData(legacy.conversationId,legacy.eventId,owner)).split('\n',limit=2)
                    check(parts.size==2)
                    val file=java.io.File(parts[0])
                    check(file.isFile && file.canonicalPath.startsWith(java.io.File(applicationContext.filesDir,"communication_voice_notes").canonicalPath+java.io.File.separator) && file.length() in 1..CommunicationMediaCodec.MAX_BYTES.toLong())
                    val payload=CommunicationMediaCodec.encode(file.readBytes(),"audio/mp4",parts[1].toLong())
                    CommunicationMediaCodec.materialize(applicationContext,owner,legacy.eventId,"AUDIO",payload)
                    checkOwner()
                    val encrypted=cipher.encrypt(payload,associatedData(legacy.conversationId,legacy.eventId,owner))
                    dao.stageLegacyVoiceNote(legacy.eventId,owner,device,encrypted.ciphertextBase64,encrypted.nonceBase64)
                }
            }
            var unreadable = false
            var failed = false
            for (event in dao.pendingOnlineEvents(owner)) {
                checkOwner()
                try {
                    val keys = remoteGateway.conversationKeys(event.conversationId).filter { transportCipher.isValidRecipientKey(it.publicKey) }
                    cacheNearbyKeys(owner,event.conversationId,keys)
                    checkOwner()
                    val peers = dao.activePeerPrincipalIds(event.conversationId, owner)
                    check(peers.isNotEmpty() && peers.all { peer -> keys.any { it.principalId == peer } })
                    check(keys.any { it.deviceId == device })
                    val published=remoteGateway.publishedEvent(event.eventId)
                    checkOwner()
                    if(published!=null) {
                        check(published.senderId==owner && published.conversationId==event.conversationId && published.senderDeviceId==event.senderDeviceId)
                        val original=cipher.decrypt(LocalCipherPayload(event.localCiphertextBase64,event.localNonceBase64),associatedData(event.conversationId,event.eventId,owner))
                        check(transportCipher.decrypt(published.envelope,owner,device,transportAad(event.conversationId,event.eventId,owner,event.senderDeviceId,event.eventType))==original)
                        dao.reconcileReceivedEvent(event.eventId,owner,published.envelope,requireNotNull(published.serverSequence).also { check(it>0) },System.currentTimeMillis())
                        continue
                    }
                    val currentDevices=keys.map { it.deviceId }.toSet()
                    val draftValid=event.remoteEnvelopeJson?.let { encoded ->
                        runCatching { nearbyJson.decodeFromString<CommunicationTransportEnvelope>(encoded).recipients.keys==currentDevices }.getOrDefault(false)
                    } ?: false
                    val envelope = event.remoteEnvelopeJson?.takeIf { draftValid } ?: transportCipher.encrypt(
                        cipher.decrypt(LocalCipherPayload(event.localCiphertextBase64, event.localNonceBase64),
                            associatedData(event.conversationId,event.eventId,owner)),
                        transportAad(event.conversationId,event.eventId,owner,event.senderDeviceId,event.eventType),
                        keys.associate { it.deviceId to it.publicKey },
                    ).also { dao.persistTransportEnvelope(event.eventId,owner,it) }
                    checkOwner()
                    val receipt = remoteGateway.publishEvent(CommunicationEventWire(
                        eventId=event.eventId, conversationId=event.conversationId, senderId=owner,
                        senderDeviceId=event.senderDeviceId, eventType=event.eventType, envelope=envelope,
                        replyTo=event.replyToEventId, idempotencyKey=event.eventId,
                        clientCreatedAt=java.time.Instant.ofEpochMilli(event.createdAtEpochMs).toString(),
                    ))
                    checkOwner()
                    check(receipt.eventId==event.eventId && receipt.senderId==owner && receipt.envelope==envelope)
                    val sequence = requireNotNull(receipt.serverSequence).also { check(it>0) }
                    dao.acknowledgeOnlineEvent(event.eventId,owner,sequence,System.currentTimeMillis())
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
            for (conversation in conversations.filter { it.requestState == "ACCEPTED" }) {
                checkOwner()
                try {
                val keys=remoteGateway.conversationKeys(conversation.id).filter { transportCipher.isValidRecipientKey(it.publicKey) }
                checkOwner()
                cacheNearbyKeys(owner,conversation.id,keys)
                val cursorKey = "$owner:${conversation.id}"
                var pages=0
                do {
                val events = remoteGateway.onlineEvents(conversation.id,onlineCursors.getLong(cursorKey,0))
                for (event in events) {
                    checkOwner()
                    if (event.eventType !in setOf("TEXT","AUDIO","IMAGE")) {
                        onlineCursors.edit().putLong(cursorKey, requireNotNull(event.serverSequence)).commit()
                        continue
                    }
                    try {
                        val decrypted = runCatching { transportCipher.decrypt(event.envelope,owner,device,
                            transportAad(event.conversationId,event.eventId,event.senderId,event.senderDeviceId,event.eventType)) }.getOrNull()
                        val text=decrypted?.takeIf { event.eventType=="TEXT" || runCatching { CommunicationMediaCodec.materialize(applicationContext,owner,event.eventId,event.eventType,it) }.isSuccess }
                        if (text == null) unreadable = true
                        val sequence = requireNotNull(event.serverSequence).also { check(it>0) }
                        val local = cipher.encrypt(text ?: "Mensaje cifrado no disponible: este dispositivo no posee una clave válida para este mensaje",associatedData(event.conversationId,event.eventId,event.senderId))
                        val previous=dao.getOnlineEvent(event.eventId,owner,event.conversationId)
                        if(previous!=null) {
                            check(previous.senderPrincipalId==event.senderId && previous.senderDeviceId==event.senderDeviceId)
                            val previousText=cipher.decrypt(LocalCipherPayload(previous.localCiphertextBase64,previous.localNonceBase64),associatedData(event.conversationId,event.eventId,event.senderId))
                            check(text!=null && previousText==text)
                            dao.reconcileReceivedEvent(event.eventId,owner,event.envelope,sequence,System.currentTimeMillis())
                        } else dao.appendEvent(CommunicationEventEntity(
                            eventId=event.eventId, conversationId=event.conversationId, ownerPrincipalId=owner,
                            senderPrincipalId=event.senderId, senderDeviceId=event.senderDeviceId,
                            eventType=event.eventType, localCiphertextBase64=local.ciphertextBase64,
                            localNonceBase64=local.nonceBase64, remoteEnvelopeJson=event.envelope,
                            replyToEventId=event.replyTo, syncState=if(text==null) "REMOTE_UNREADABLE" else "SERVER_ACK", serverSequence=sequence,
                            createdAtEpochMs=java.time.Instant.parse(event.clientCreatedAt).toEpochMilli(),
                            receivedAtEpochMs=System.currentTimeMillis(),
                        ))
                        onlineCursors.edit().putLong(cursorKey,sequence).commit()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { failed=true; break }
                }
                pages++
                if(events.size<100) break
                if(pages>=10) { failed=true;break }
                } while(true)
                } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_:Exception) { failed=true }
            }
            if(dao.pendingOnlineEvents(owner).isNotEmpty()) failed=true
            onlineStatus.value = if (failed) "Hay mensajes pendientes; se reintentará sin duplicarlos" else if(unreadable) "Sincronizado; hay históricos sin clave válida para este dispositivo" else "Sincronizado con el servidor"
            !failed
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            onlineStatus.value = "Servidor no disponible o contrato pendiente; mensajes conservados en este dispositivo"
            false
        } finally { onlineMutex.unlock() }
    }

    suspend fun respondToRequest(id: String, accept: Boolean): Boolean {
        if (!remoteGateway.respondToMessageRequest(id,accept)) return false
        synchronizeOnline()
        return true
    }

    private suspend fun ensurePairingIdentity(owner:String) {
        val remote=remoteGateway.ownOnlineIdentity()
        if(remote==null) {
            val identity=dao.getIdentityProfile(owner)?.toIdentity() ?: defaultIdentity(owner)
            check(remoteGateway.ensureIdentity(identity.elysiumId,identity.displayName,identity.about,null,"NOBODY"))
        } else {
            val local=dao.getIdentityProfile(owner)
            dao.upsertIdentityProfile(local?.copy(elysiumId=remote.elysiumId,displayName=remote.displayName,about=remote.about,
                identityState=remote.identityState,updatedAtEpochMs=System.currentTimeMillis()) ?: CommunicationIdentityProfileEntity(
                    ownerPrincipalId=owner,elysiumId=remote.elysiumId,displayName=remote.displayName,about=remote.about,
                    identityState=remote.identityState,updatedAtEpochMs=System.currentTimeMillis()))
        }
        check(remoteGateway.authenticatedPrincipalId()==owner && principalKernel.current().id==owner)
        check(remoteGateway.registerTransportDevice(transportDeviceId(owner),transportCipher.publicKey(owner)))
    }

    suspend fun contactConversation(peer:String):String? {
        val owner=principalKernel.current().id
        if(dao.isBlocked(owner,peer)) return null
        return dao.acceptedContactConversation(owner,peer)
    }

    suspend fun issuePairingQr(): String {
        val owner = requireNotNull(remoteGateway.authenticatedPrincipalId())
        check(owner == principalKernel.current().id)
        ensurePairingIdentity(owner)
        val token=remoteGateway.issuePairingToken()
        check(remoteGateway.authenticatedPrincipalId()==owner && principalKernel.current().id==owner)
        return "elysium://communications/pair/" + token
    }

    suspend fun consumePairingQr(value: String): String {
        val prefix = "elysium://communications/pair/"
        require(value.startsWith(prefix))
        val token = value.removePrefix(prefix)
        require(token.matches(Regex("[a-f0-9]{64}")))
        val owner=requireNotNull(remoteGateway.authenticatedPrincipalId())
        check(principalKernel.current().id==owner)
        ensurePairingIdentity(owner)
        val id = remoteGateway.consumePairingToken(token)
        check(remoteGateway.authenticatedPrincipalId()==owner && principalKernel.current().id==owner)
        synchronizeOnline() // The pairing receipt remains valid even if projection refresh temporarily fails.
        return id
    }

    suspend fun initializeSocialDefaults() {
        val principal = principalKernel.current()
        val now = System.currentTimeMillis()
        if (dao.getIdentityProfile(principal.id) == null) {
            val fallback = defaultIdentity(principal.id)
            dao.upsertIdentityProfile(
                CommunicationIdentityProfileEntity(
                    ownerPrincipalId = principal.id,
                    elysiumId = fallback.elysiumId,
                    displayName = fallback.displayName,
                    about = fallback.about,
                    identityState = fallback.identityState,
                    updatedAtEpochMs = now,
                ),
            )
        }
        if (dao.getPrivacySettings(principal.id) == null) {
            dao.upsertPrivacySettings(CommunicationPrivacySettings().toEntity(principal.id, now))
        }
    }

    suspend fun searchContact(query: String): ContactSearchOutcome {
        val parsed = parseDiscoveryQuery(query) ?: return ContactSearchOutcome.InvalidQuery
        val principal = principalKernel.current()
        val local = when (parsed.first) {
            ContactDiscoveryMedium.ELYSIUM_ID -> dao.findRelationshipByElysiumId(principal.id, parsed.second)
            ContactDiscoveryMedium.EMAIL -> dao.findRelationshipByEmailToken(principal.id, aliasMatcher.tag(parsed.first, parsed.second))
            ContactDiscoveryMedium.PHONE -> dao.findRelationshipByPhoneToken(principal.id, aliasMatcher.tag(parsed.first, parsed.second))
            else -> null
        }
        if (local != null && !dao.isBlocked(principal.id, local.peerPrincipalId)) {
            return ContactSearchOutcome.Found(listOf(local.toSearchResult(parsed.first, alreadyKnown = true)))
        }
        return when (val remote = remoteGateway.lookupExact(parsed.first, parsed.second)) {
            is RemoteDiscoveryOutcome.Found -> {
                val now = System.currentTimeMillis()
                val contacts = remote.contacts.mapNotNull { row ->
                    if (dao.isBlocked(principal.id, row.principalId)) return@mapNotNull null
                    val proof = enumValueOrDefault(row.aliasProofState, AliasProofState.SERVER_ASSERTED)
                    val relationship = CommunicationRelationshipEntity(
                        ownerPrincipalId = principal.id,
                        peerPrincipalId = row.principalId,
                        peerElysiumId = row.elysiumId,
                        peerDisplayName = row.displayName,
                        relationshipState = "DISCOVERED",
                        initiatedByMe = false,
                        aliasProofState = proof.name,
                        emailLookupToken = if (parsed.first == ContactDiscoveryMedium.EMAIL) aliasMatcher.tag(parsed.first, parsed.second) else null,
                        phoneLookupToken = if (parsed.first == ContactDiscoveryMedium.PHONE) aliasMatcher.tag(parsed.first, parsed.second) else null,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    )
                    dao.upsertRelationship(relationship)
                    relationship.toSearchResult(parsed.first, alreadyKnown = false)
                }
                if (contacts.isEmpty()) ContactSearchOutcome.NotFound(parsed.second) else ContactSearchOutcome.Found(contacts)
            }
            RemoteDiscoveryOutcome.AuthenticationRequired -> ContactSearchOutcome.AuthenticationRequired
            RemoteDiscoveryOutcome.RateLimited -> ContactSearchOutcome.RateLimited
            RemoteDiscoveryOutcome.Unavailable -> ContactSearchOutcome.ServiceUnavailable
        }
    }

    suspend fun requestContact(contact: ContactSearchResult): ContactRequestOutcome {
        val principal = principalKernel.current()
        if (dao.isBlocked(principal.id, contact.principalId)) return ContactRequestOutcome.Blocked
        if (!principal.isAuthenticated) return ContactRequestOutcome.AuthenticationRequired
        val conversationId = remoteGateway.createDirectRequest(contact.principalId)
            ?: return ContactRequestOutcome.ServiceUnavailable
        val now = System.currentTimeMillis()
        runCatching {
            dao.insertConversation(
                CommunicationConversationEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    kind = ConversationKind.DIRECT.name,
                    title = contact.displayName,
                    requestState = MessageRequestState.PENDING.name,
                    proofState = CommunicationProofState.SERVER_AUTHORITATIVE.name,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        dao.upsertParticipant(CommunicationParticipantEntity(conversationId, principal.id, principal.id, "Tú", "OWNER", joinedAtEpochMs = now))
        dao.upsertParticipant(CommunicationParticipantEntity(conversationId, principal.id, contact.principalId, contact.displayName, "MEMBER", joinedAtEpochMs = now))
        dao.upsertRelationship(
            CommunicationRelationshipEntity(
                ownerPrincipalId = principal.id,
                peerPrincipalId = contact.principalId,
                peerElysiumId = contact.elysiumId,
                peerDisplayName = contact.displayName,
                relationshipState = "PENDING",
                initiatedByMe = true,
                aliasProofState = contact.aliasProofState.name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        return ContactRequestOutcome.Created(conversationId)
    }

    suspend fun saveIdentity(
        elysiumId: String,
        displayName: String,
        about: String,
        phone: String?,
    ): Boolean {
        val principal = principalKernel.current()
        val id = elysiumId.trim().removePrefix("@").lowercase()
        require(id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) { "INVALID_ELYSIUM_ID" }
        require(displayName.trim().length in 1..120) { "INVALID_DISPLAY_NAME" }
        val normalizedPhone = phone?.let(::normalizePhone)?.takeIf(String::isNotBlank)
        val encryptedPhone = normalizedPhone?.let {
            cipher.encrypt(it, identityAliasAad(principal.id, ContactDiscoveryMedium.PHONE))
        }
        val current = dao.getIdentityProfile(principal.id)
        val remoteSaved = principal.isAuthenticated && remoteGateway.ensureIdentity(
            id,
            displayName.trim(),
            about.trim().take(280),
            normalizedPhone,
            dao.getPrivacySettings(principal.id)?.findByPhone ?: "NOBODY",
        )
        dao.upsertIdentityProfile(
            CommunicationIdentityProfileEntity(
                ownerPrincipalId = principal.id,
                elysiumId = id,
                displayName = displayName.trim(),
                about = about.trim().take(280),
                identityState = if (remoteSaved) "SERVER_AUTHORITATIVE" else "LOCAL_ONLY",
                emailAliasCiphertextBase64 = current?.emailAliasCiphertextBase64,
                emailAliasNonceBase64 = current?.emailAliasNonceBase64,
                emailVerificationState = current?.emailVerificationState ?: "ABSENT",
                phoneAliasCiphertextBase64 = encryptedPhone?.ciphertextBase64,
                phoneAliasNonceBase64 = encryptedPhone?.nonceBase64,
                phoneVerificationState = if (normalizedPhone == null) "ABSENT" else "DECLARED",
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return remoteSaved
    }

    suspend fun savePrivacy(settings: CommunicationPrivacySettings): Boolean {
        val principal = principalKernel.current()
        val safe = settings.copy(relayMinimumBatteryPercent = settings.relayMinimumBatteryPercent.coerceIn(10, 90))
        dao.upsertPrivacySettings(safe.toEntity(principal.id, System.currentTimeMillis()))
        return principal.isAuthenticated && remoteGateway.savePrivacy(safe)
    }

    suspend fun blockContact(contact: ElysiumContact): Boolean {
        val principal = principalKernel.current()
        val synced = principal.isAuthenticated && remoteGateway.blockPrincipal(contact.principalId)
        dao.upsertBlock(
            CommunicationLocalBlockEntity(
                ownerPrincipalId = principal.id,
                blockedPrincipalId = contact.principalId,
                blockedDisplayName = contact.displayName,
                reasonCode = "USER_REQUESTED",
                syncState = if (synced) "SYNCED" else "LOCAL_ONLY",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return synced
    }

    suspend fun unblockContact(contact: BlockedContact): Boolean {
        val principal = principalKernel.current()
        val synced = !principal.isAuthenticated || remoteGateway.unblockPrincipal(contact.principalId)
        if (synced) dao.deleteBlock(principal.id, contact.principalId)
        return synced
    }

    suspend fun ensureServiceConversation(
        serviceVertical: String,
        serviceReferenceId: String,
        title: String,
        authorizedPeerPrincipalId: String? = null,
        authorizedPeerName: String? = null,
    ): String {
        require(serviceVertical.isNotBlank()) { "Service vertical is required" }
        require(serviceReferenceId.isNotBlank()) { "Service reference is required" }
        val principal = principalKernel.current()
        val authoritative = remoteGateway.ensureServiceConversation(serviceVertical, serviceReferenceId)
        dao.findServiceConversation(principal.id, serviceVertical, serviceReferenceId)?.let {
            return it.conversationId
        }

        val now = System.currentTimeMillis()
        val conversationId = authoritative?.id
            ?: stableConversationId(principal.id, serviceVertical, serviceReferenceId)
        runCatching {
            dao.insertConversation(
                CommunicationConversationEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    kind = ConversationKind.SERVICE.name,
                    title = title.trim().take(160),
                    serviceVertical = serviceVertical,
                    serviceReferenceId = serviceReferenceId,
                    proofState = if (authoritative != null) {
                        CommunicationProofState.SERVER_AUTHORITATIVE.name
                    } else {
                        CommunicationProofState.CLIENT_IMPLEMENTED.name
                    },
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        dao.upsertParticipant(
            CommunicationParticipantEntity(
                conversationId = conversationId,
                ownerPrincipalId = principal.id,
                participantPrincipalId = principal.id,
                displayName = "Tú",
                role = "CUSTOMER",
                joinedAtEpochMs = now,
            ),
        )
        if (!authorizedPeerPrincipalId.isNullOrBlank()) {
            dao.upsertParticipant(
                CommunicationParticipantEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    participantPrincipalId = authorizedPeerPrincipalId,
                    displayName = authorizedPeerName?.trim().takeUnless { it.isNullOrBlank() } ?: "Proveedor autorizado",
                    role = "SERVICE_PROVIDER",
                    joinedAtEpochMs = now,
                ),
            )
        }
        authoritative?.participantIds
            ?.filter { it != principal.id }
            ?.forEach { participantId ->
                dao.upsertParticipant(
                    CommunicationParticipantEntity(
                        conversationId = conversationId,
                        ownerPrincipalId = principal.id,
                        participantPrincipalId = participantId,
                        displayName = "Participante autorizado",
                        role = "SERVICE_PROVIDER",
                        joinedAtEpochMs = now,
                    ),
                )
            }
        return conversationId
    }

    fun observeConversation(conversationId: String): Flow<ConversationSummary?> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            combine(
                dao.observeConversation(conversationId, principal.id),
                dao.observeParticipants(conversationId, principal.id),
            ) { conversation, participants ->
                conversation?.let { toSummary(it).copy(
                    participantCount = participants.count { row -> row.membershipState == "ACTIVE" },
                    canRespondToRequest = it.kind == "DIRECT" && it.requestState == "PENDING" && participants.any { row ->
                        row.participantPrincipalId == principal.id && row.role == "MEMBER" && row.membershipState == "ACTIVE"
                    },
                ) }
            }
        }

    fun observeMessages(conversationId: String): Flow<List<DecryptedMessage>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeEvents(conversationId, principal.id).map { events ->
                events.map { event ->
                    val body = runCatching {
                        cipher.decrypt(
                            LocalCipherPayload(event.localCiphertextBase64, event.localNonceBase64),
                            associatedData(event.conversationId, event.eventId, event.senderPrincipalId),
                        )
                    }
                    val plaintext = body.getOrNull()
                    val media = if(event.eventType in setOf("AUDIO","IMAGE") && plaintext!=null) runCatching { CommunicationMediaCodec.decode(plaintext,event.eventType).first }.getOrNull() else null
                    val voiceParts = plaintext?.split('\n', limit = 2)
                        ?.takeIf { event.eventType == "VOICE_NOTE" && it.size == 2 }
                    DecryptedMessage(
                        id = event.eventId,
                        senderPrincipalId = event.senderPrincipalId,
                        body = when {
                            body.isFailure -> "Mensaje cifrado no disponible en este dispositivo"
                            media != null -> if(event.eventType=="IMAGE") "Imagen" else "Nota de voz · ${media.durationMs/1000} s"
                            voiceParts != null -> "Nota de voz · ${voiceParts[1].toLongOrNull()?.div(1000L) ?: 0L} s"
                            else -> plaintext.orEmpty()
                        },
                        isMine = event.senderPrincipalId == principal.id,
                        createdAtEpochMs = event.createdAtEpochMs,
                        deliveryState = event.syncState,
                        eventType = event.eventType,
                        replyToEventId = event.replyToEventId,
                        localMediaPath = if(media!=null) CommunicationMediaCodec.file(applicationContext,principal.id,event.eventId,event.eventType).takeIf { it.isFile }?.absolutePath else voiceParts?.firstOrNull(),
                        decryptionFailed = body.isFailure || event.syncState == "REMOTE_UNREADABLE",
                    )
                }
            }
        }

    suspend fun sendText(
        conversationId: String,
        text: String,
        replyToEventId: String? = null,
    ): SendMessageOutcome {
        val normalized = text.trim()
        if (normalized.isEmpty()) return SendMessageOutcome.EmptyMessage
        val principal = principalKernel.current()
        if (dao.activePeerPrincipalIds(conversationId, principal.id).any { dao.isBlocked(principal.id, it) }) {
            return SendMessageOutcome.ConversationUnavailable
        }
        if (dao.activeParticipantCount(conversationId, principal.id) < 2) {
            return SendMessageOutcome.WaitingForAuthorizedParticipant
        }

        val eventId = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(
            normalized.take(4000),
            associatedData(conversationId, eventId, principal.id),
        )
        val inserted = dao.appendEvent(
            CommunicationEventEntity(
                eventId = eventId,
                conversationId = conversationId,
                ownerPrincipalId = principal.id,
                senderPrincipalId = principal.id,
                senderDeviceId = transportDeviceId(principal.id),
                eventType = "TEXT",
                localCiphertextBase64 = encrypted.ciphertextBase64,
                localNonceBase64 = encrypted.nonceBase64,
                replyToEventId = replyToEventId,
                syncState = if (dao.getOnlineConversation(conversationId,principal.id)?.proofState == "SERVER_AUTHORITATIVE") "PENDING_REMOTE" else "LOCAL_ONLY",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        if (inserted && principal.canSyncToCloud) {
            val work = androidx.work.OneTimeWorkRequestBuilder<CommunicationOnlineSyncWorker>()
                .setInputData(androidx.work.workDataOf("owner" to principal.id))
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "communication-online:${principal.id}", androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, work
            )
        }
        return if (inserted) SendMessageOutcome.SentLocally(eventId) else SendMessageOutcome.ConversationUnavailable
    }

    suspend fun sendVoiceNote(conversationId: String, draft: VoiceNoteDraft, replyToEventId: String? = null): SendMessageOutcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if(draft.ownerPrincipalId!=principalKernel.current().id || draft.conversationId!=conversationId) return@withContext SendMessageOutcome.ConversationUnavailable
        if (!draft.file.isFile || draft.file.length() !in 1..CommunicationMediaCodec.MAX_BYTES.toLong()) return@withContext SendMessageOutcome.ConversationUnavailable
        queueMedia(conversationId, "AUDIO", CommunicationMediaCodec.encode(draft.file.readBytes(), "audio/mp4", draft.durationMs), replyToEventId,draft.ownerPrincipalId)
    }

    suspend fun sendImage(conversationId: String, uri: android.net.Uri, expectedOwner:String, replyToEventId: String? = null): SendMessageOutcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        queueMedia(conversationId,"IMAGE",CommunicationMediaCodec.encode(CommunicationMediaCodec.readImage(applicationContext,uri),"image/jpeg"),replyToEventId,expectedOwner)
    }

    private fun scheduleOnlineSync(owner:String) {
        val work=androidx.work.OneTimeWorkRequestBuilder<CommunicationOnlineSyncWorker>()
            .setInputData(androidx.work.workDataOf("owner" to owner))
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork("communication-online:$owner",androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,work)
    }

    private suspend fun queueMedia(conversationId:String,type:String,payload:String,replyTo:String?,expectedOwner:String):SendMessageOutcome {
        val owner=principalKernel.current()
        if(owner.id!=expectedOwner || remoteGateway.authenticatedPrincipalId()!=expectedOwner) return SendMessageOutcome.ConversationUnavailable
        val conversation=dao.getOnlineConversation(conversationId,owner.id)
        if(conversation?.requestState!="ACCEPTED" || conversation.proofState!="SERVER_AUTHORITATIVE") return SendMessageOutcome.ConversationUnavailable
        if(dao.activeParticipantCount(conversationId,owner.id)<2) return SendMessageOutcome.WaitingForAuthorizedParticipant
        if(dao.activePeerPrincipalIds(conversationId,owner.id).any { dao.isBlocked(owner.id,it) }) return SendMessageOutcome.ConversationUnavailable
        val id=UUID.randomUUID().toString()
        if(principalKernel.current().id!=expectedOwner || remoteGateway.authenticatedPrincipalId()!=expectedOwner) return SendMessageOutcome.ConversationUnavailable
        CommunicationMediaCodec.materialize(applicationContext,owner.id,id,type,payload)
        val local=cipher.encrypt(payload,associatedData(conversationId,id,owner.id))
        val inserted=dao.appendEvent(CommunicationEventEntity(eventId=id,conversationId=conversationId,ownerPrincipalId=owner.id,senderPrincipalId=owner.id,senderDeviceId=transportDeviceId(owner.id),eventType=type,localCiphertextBase64=local.ciphertextBase64,localNonceBase64=local.nonceBase64,replyToEventId=replyTo,syncState="PENDING_REMOTE",createdAtEpochMs=System.currentTimeMillis()))
        if(inserted) { scheduleOnlineSync(expectedOwner); communicationScope.launch { synchronizeOnline() }; communicationScope.launch { flushMeshBridge() }; return SendMessageOutcome.SentLocally(id) }
        return SendMessageOutcome.ConversationUnavailable
    }

    suspend fun startAudioCall(conversationId: String): StartCallOutcome {
        val principal = principalKernel.current()
        if (dao.activePeerPrincipalIds(conversationId, principal.id).any { dao.isBlocked(principal.id, it) }) {
            return StartCallOutcome.Failed("CONTACT_BLOCKED")
        }
        if (dao.activeParticipantCount(conversationId, principal.id) < 2) {
            return StartCallOutcome.WaitingForAuthorizedParticipant
        }

        val callId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val base = CommunicationCallEntity(
            callId = callId,
            conversationId = conversationId,
            ownerPrincipalId = principal.id,
            direction = "OUTGOING",
            mediaType = "AUDIO",
            state = "CONNECTING",
            transportProofState = CommunicationProofState.CLIENT_IMPLEMENTED.name,
            startedAtEpochMs = startedAt,
        )
        dao.upsertCall(base)

        return when (val outcome = callTransport.connectAudio(conversationId, principal.id,callId)) {
            is CallTransportOutcome.Ringing -> {
                dao.upsertCall(base.copy(state="RINGING",transportProofState=CommunicationProofState.SERVER_AUTHORITATIVE.name))
                StartCallOutcome.Ringing(outcome.callId)
            }
            CallTransportOutcome.Connected -> {
                dao.upsertCall(
                    base.copy(
                        state = "ACTIVE",
                        transportProofState = CommunicationProofState.SERVER_AUTHORITATIVE.name,
                        answeredAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                StartCallOutcome.Ready(callId)
            }
            CallTransportOutcome.NotConfigured -> {
                // Fallback: try Mesh P2P when server call transport is not configured
                val meshPeerFallback = com.elysium369.meet.core.mesh.ElysiumMeshGovernance.activeLinkedPeer.value
                    ?: com.elysium369.meet.core.mesh.ElysiumMeshGovernance.discoveredPhysicalPeers.value.firstOrNull()
                if (meshPeerFallback != null) {
                    try {
                        com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.sendCallSignal(
                            meshPeerFallback.ipAddress, meshPeerFallback.port, "RING_AUDIO", principal.id
                        )
                        com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.startAudioCallStreaming(meshPeerFallback.ipAddress)
                        dao.upsertCall(base.copy(state = "RINGING", transportProofState = "MESH_P2P"))
                        android.util.Log.i("CommRepo", "Llamada audio por Mesh P2P (server no configurado) a ${meshPeerFallback.ipAddress}")
                        StartCallOutcome.Ringing(callId)
                    } catch (e: Exception) {
                        failedCall(base, "SERVER_TRANSPORT_NOT_CONFIGURED").let { StartCallOutcome.ServerTransportNotConfigured }
                    }
                } else {
                    failedCall(base, "SERVER_TRANSPORT_NOT_CONFIGURED").let { StartCallOutcome.ServerTransportNotConfigured }
                }
            }
            CallTransportOutcome.AuthenticationRequired -> failedCall(base, "AUTHENTICATION_REQUIRED").let {
                StartCallOutcome.AuthenticationRequired
            }
            CallTransportOutcome.RejectedInsecureEndpoint -> failedCall(base, "INSECURE_ENDPOINT_REJECTED").let {
                StartCallOutcome.InsecureEndpointRejected
            }
            is CallTransportOutcome.Failed -> {
                // Fallback: try Mesh TCP P2P call if a mesh peer is connected
                val meshPeer = com.elysium369.meet.core.mesh.ElysiumMeshGovernance.activeLinkedPeer.value
                    ?: com.elysium369.meet.core.mesh.ElysiumMeshGovernance.discoveredPhysicalPeers.value.firstOrNull()
                if (meshPeer != null) {
                    try {
                        com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.sendCallSignal(
                            meshPeer.ipAddress, meshPeer.port, "RING_AUDIO", principal.id
                        )
                        com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.startAudioCallStreaming(meshPeer.ipAddress)
                        dao.upsertCall(base.copy(state = "RINGING", transportProofState = "MESH_P2P"))
                        android.util.Log.i("CommRepo", "Llamada audio iniciada por Mesh TCP a ${meshPeer.ipAddress}")
                        StartCallOutcome.Ringing(callId)
                    } catch (e: Exception) {
                        android.util.Log.w("CommRepo", "Mesh call fallback failed: ${e.message}")
                        failedCall(base, outcome.safeCode).let { StartCallOutcome.Failed(outcome.safeCode) }
                    }
                } else {
                    failedCall(base, outcome.safeCode).let { StartCallOutcome.Failed(outcome.safeCode) }
                }
            }
        }
    }

    suspend fun answerCall(accept:Boolean):Boolean = callTransport.answer(accept)

    suspend fun endCall() {
        callTransport.end()
    }

    private suspend fun failedCall(base: CommunicationCallEntity, code: String) {
        dao.upsertCall(
            base.copy(
                state = "FAILED",
                endedAtEpochMs = System.currentTimeMillis(),
                failureCode = code,
            ),
        )
    }

    private fun toSummary(row: CommunicationConversationEntity): ConversationSummary = ConversationSummary(
        id = row.conversationId,
        title = row.title,
        kind = enumValueOrDefault(row.kind, ConversationKind.SERVICE),
        serviceVertical = row.serviceVertical,
        serviceReferenceId = row.serviceReferenceId,
        requestState = enumValueOrDefault(row.requestState, MessageRequestState.PENDING),
        lastActivityAtEpochMs = row.lastEventAtEpochMs,
        proofState = enumValueOrDefault(row.proofState, CommunicationProofState.MODEL_EXISTS),
    )

    private fun stableConversationId(owner: String, vertical: String, reference: String): String {
        val bytes = "$owner\u001f$vertical\u001f$reference".toByteArray(StandardCharsets.UTF_8)
        return "svc_" + MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(40)
    }

    private fun associatedData(conversationId: String, eventId: String, senderId: String): String =
        "elysium-communications-v1|$conversationId|$eventId|$senderId"

    private fun identityAliasAad(ownerPrincipalId: String, medium: ContactDiscoveryMedium): String =
        "elysium-identity-v1|$ownerPrincipalId|${medium.name}"

    private fun defaultIdentity(principalId: String): ElysiumIdentityProfile {
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(principalId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)
        return ElysiumIdentityProfile(
            principalId = principalId,
            elysiumId = "elysium-$suffix",
            displayName = "Usuario Elysium",
            about = "",
            identityState = "LOCAL_ONLY",
            email = null,
            emailVerificationState = "ABSENT",
            phone = null,
            phoneVerificationState = "ABSENT",
        )
    }

    private fun CommunicationIdentityProfileEntity.toIdentity(): ElysiumIdentityProfile {
        fun decryptAlias(ciphertext: String?, nonce: String?, medium: ContactDiscoveryMedium): String? {
            if (ciphertext == null || nonce == null) return null
            return runCatching {
                cipher.decrypt(LocalCipherPayload(ciphertext, nonce), identityAliasAad(ownerPrincipalId, medium))
            }.getOrNull()
        }
        return ElysiumIdentityProfile(
            principalId = ownerPrincipalId,
            elysiumId = elysiumId,
            displayName = displayName,
            about = about,
            identityState = identityState,
            email = decryptAlias(emailAliasCiphertextBase64, emailAliasNonceBase64, ContactDiscoveryMedium.EMAIL),
            emailVerificationState = emailVerificationState,
            phone = decryptAlias(phoneAliasCiphertextBase64, phoneAliasNonceBase64, ContactDiscoveryMedium.PHONE),
            phoneVerificationState = phoneVerificationState,
        )
    }

    private fun CommunicationPrivacySettingsEntity.toPrivacy() = CommunicationPrivacySettings(
        findByElysiumId, findByEmail, findByPhone, profilePhotoVisibility, profileVisibility,
        lastActiveVisibility, onlineVisibility, readReceiptsEnabled, typingIndicatorsEnabled,
        callPermission, groupInvitePermission, meshDiscoverability, relayParticipation,
        relayOnlyWhileCharging, relayMinimumBatteryPercent,
    )

    private fun CommunicationPrivacySettings.toEntity(owner: String, now: Long) = CommunicationPrivacySettingsEntity(
        owner, findByElysiumId, findByEmail, findByPhone, profilePhotoVisibility, profileVisibility,
        lastActiveVisibility, onlineVisibility, readReceiptsEnabled, typingIndicatorsEnabled,
        callPermission, groupInvitePermission, meshDiscoverability, relayParticipation,
        relayOnlyWhileCharging, relayMinimumBatteryPercent, now,
    )

    private fun CommunicationRelationshipEntity.toSearchResult(medium: ContactDiscoveryMedium, alreadyKnown: Boolean) =
        ContactSearchResult(
            peerPrincipalId,
            peerElysiumId.orEmpty(),
            peerDisplayName,
            medium,
            enumValueOrDefault(aliasProofState, AliasProofState.LOCAL_ONLY),
            alreadyKnown,
        )

    private fun parseDiscoveryQuery(raw: String): Pair<ContactDiscoveryMedium, String>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("@")) {
            val id = trimmed.removePrefix("@").lowercase()
            return if (id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) ContactDiscoveryMedium.ELYSIUM_ID to id else null
        }
        if (trimmed.contains('@')) {
            val email = trimmed.lowercase()
            return if (email.length <= 320 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) ContactDiscoveryMedium.EMAIL to email else null
        }
        val phone = normalizePhone(trimmed)
        if (phone.matches(Regex("^\\+?[0-9]{7,15}$"))) return ContactDiscoveryMedium.PHONE to phone
        val id = trimmed.lowercase()
        return if (id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) ContactDiscoveryMedium.ELYSIUM_ID to id else null
    }

    private fun normalizePhone(raw: String): String = raw.trim().replace(Regex("[^0-9+]"), "")

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
