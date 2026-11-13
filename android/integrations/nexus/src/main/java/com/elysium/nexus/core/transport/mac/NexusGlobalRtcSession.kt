package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class NexusGlobalPhase { IDLE, CONNECTING, WAITING_FOR_MAC, CONNECTED, FAILED }

data class NexusGlobalUiState(
    val phase: NexusGlobalPhase = NexusGlobalPhase.IDLE,
    val message: String = "Conecta con el código privado que aparece en Elysium Nexus para Mac.",
    val relayAvailable: Boolean = false,
    val androidScreenSharing: Boolean = false
)

/** Owns the Android side of the global, direct WebRTC screen and control session. */
class NexusGlobalRtcSession(
    context: Context,
    private val signaling: NexusCloudSignaling
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val egl = EglBase.create()
    val eglContext: EglBase.Context get() = egl.eglBaseContext
    private val stateFlow = kotlinx.coroutines.flow.MutableStateFlow(NexusGlobalUiState())
    val state: kotlinx.coroutines.flow.StateFlow<NexusGlobalUiState> = stateFlow
    private val remoteTrackFlow = kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: kotlinx.coroutines.flow.StateFlow<VideoTrack?> = remoteTrackFlow
    private var macScreenTrack: VideoTrack? = null

    private var peer: PeerConnection? = null
    private var signalSession: NexusCloudSession? = null
    private var signalConnection: NexusCloudSignalConnection? = null
    private var controlChannel: DataChannel? = null
    private var videoTransceiver: RtpTransceiver? = null
    private var screenCapturer: NexusScreenCapturer? = null
    private val hasRemoteDescription = AtomicBoolean(false)
    private val pendingRemoteCandidates = ArrayDeque<IceCandidate>()
    private val candidateLock = Any()
    private var disposed = false
    @Volatile private var iceConnected = false

    fun connect(pairingCode: String) {
        if (stateFlow.value.phase == NexusGlobalPhase.CONNECTING || peer != null) return
        stateFlow.value = NexusGlobalUiState(phase = NexusGlobalPhase.CONNECTING, message = "Validando invitación privada…")
        scope.launch {
            try {
                val session = signaling.joinInvitation(pairingCode)
                signalSession = session
                check(session.relayAvailable && session.iceServers.any { iceServer ->
                    iceServer.urls.any { it.startsWith("turn:") || it.startsWith("turns:") }
                }) {
                    "El servicio TURN global no está disponible; por privacidad no se conectará directamente."
                }
                signalConnection = signaling.openSignalChannel(
                    scope = scope,
                    session = session,
                    localRole = NexusSignalEnvelope.Sender.ANDROID,
                    onSignal = ::handleSignal,
                    onFailure = { reason ->
                        Log.e(TAG, "Private signaling failed reason=$reason")
                        updateMessage("Se perdió la señalización segura. Vuelve a conectar con un código vigente.")
                    }
                )
                createPeer(session)
                stateFlow.value = NexusGlobalUiState(
                    phase = NexusGlobalPhase.WAITING_FOR_MAC,
                    message = "Negociando vídeo y control mediante relay cifrado…",
                    relayAvailable = session.relayAvailable
                )
                val offer = createOffer()
                val payload = JSONObject().put("sdp", offer).toString().toByteArray(Charsets.UTF_8)
                signalConnection?.send(NexusSignalEnvelope.Kind.OFFER, payload)
                Log.i(TAG, "Encrypted cloud rendezvous accepted; WebRTC offer sent session=${session.sessionId}")
            } catch (error: Exception) {
                Log.e(TAG, "Global Nexus connection failed", error)
                val failedSession = signalSession
                signalSession = null
                runCatching { signalConnection?.close() }
                signalConnection = null
                if (failedSession != null) signaling.closeSession(failedSession)
                resetPeerForRetry()
                stateFlow.value = NexusGlobalUiState(
                    phase = NexusGlobalPhase.FAILED,
                    message = error.message?.take(180) ?: "No se pudo conectar con Elysium Nexus para Mac."
                )
            }
        }
    }

    fun sendControlFrame(frame: ByteArray): Boolean {
        val channel = controlChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN || frame.size > MAX_CONTROL_FRAME_BYTES) return false
        return runCatching {
            channel.send(DataChannel.Buffer(ByteBuffer.wrap(frame), true))
        }.getOrDefault(false)
    }

    fun startAndroidScreenShare(projectionGrant: Intent) {
        val activePeer = peer ?: error("Conecta con el Mac antes de compartir la pantalla de Android.")
        val activeTransceiver = videoTransceiver ?: error("La pista de vídeo no está negociada.")
        check(screenCapturer == null) { "La pantalla de Android ya se está compartiendo." }
        val capturer = NexusScreenCapturer(appContext)
        try {
            val track = capturer.start(
                factory = requireNotNull(factory),
                eglContext = eglContext,
                projectionGrant = projectionGrant,
                onProjectionStopped = {
                    Log.i(TAG, "Android revoked the active MediaProjection")
                    stopAndroidScreenShare()
                }
            )
            check(activeTransceiver.sender.setTrack(track, false)) {
                "WebRTC rechazó la pista de pantalla de Android."
            }
            track.setEnabled(true)
            screenCapturer = capturer
            stateFlow.value = stateFlow.value.copy(androidScreenSharing = true, message = "Compartiendo la pantalla de Android con el Mac.")
            Log.i(TAG, "Android screen track attached to negotiated WebRTC video transceiver")
        } catch (error: Exception) {
            capturer.stop()
            throw error
        }
        @Suppress("UNUSED_VARIABLE")
        val keepPeerAlive = activePeer
    }

    fun stopAndroidScreenShare() {
        val capturer = screenCapturer ?: return
        screenCapturer = null
        capturer.stop()
        runCatching { videoTransceiver?.sender?.setTrack(null, false) }
        stateFlow.value = stateFlow.value.copy(androidScreenSharing = false, message = "Pantalla de Android detenida.")
        Log.i(TAG, "Android screen capture stopped")
    }

    fun reportScreenShareFailure(message: String) {
        stateFlow.value = stateFlow.value.copy(androidScreenSharing = false, message = message.take(180))
    }

    fun close() {
        if (disposed) return
        disposed = true
        stopAndroidScreenShare()
        val oldSession = signalSession
        val oldConnection = signalConnection
        signalSession = null
        signalConnection = null
        scope.launch {
            runCatching { oldConnection?.send(NexusSignalEnvelope.Kind.CLOSE, ByteArray(0)) }
            runCatching { oldConnection?.close() }
            if (oldSession != null) runCatching { signaling.closeSession(oldSession) }
            scope.cancel()
        }
        controlChannel?.unregisterObserver()
        controlChannel?.close()
        controlChannel?.dispose()
        controlChannel = null
        remoteTrackFlow.value = null
        macScreenTrack = null
        peer?.close()
        peer?.dispose()
        peer = null
        videoTransceiver = null
        runCatching { factory?.dispose() }
        factory = null
        egl.release()
        stateFlow.value = NexusGlobalUiState(phase = NexusGlobalPhase.IDLE)
    }

    private var factory: PeerConnectionFactory? = null

    private fun createPeer(session: NexusCloudSession) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
        val config = PeerConnection.RTCConfiguration(session.iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        peer = requireNotNull(factory).createPeerConnection(config, observer())
            ?: error("No se pudo crear la conexión WebRTC.")
        videoTransceiver = peer?.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        ) ?: error("No se pudo preparar el canal de vídeo bidireccional.")
        val channel = peer?.createDataChannel("nexus-control", DataChannel.Init().apply { ordered = true })
            ?: error("No se pudo crear el canal seguro para trackpad y teclado.")
        controlChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                Log.i(TAG, "WebRTC control DataChannel state=${channel.state()}")
                updateConnectionReadiness()
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary || buffer.data.remaining() > MAX_CONTROL_FRAME_BYTES) return
                val bytes = ByteArray(buffer.data.remaining()).also(buffer.data::get)
                handleMacDataChannelFrame(bytes)
            }
        })
        Log.i(TAG, "WebRTC peer ready; sendrecv video transceiver and control channel prepared")
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    iceConnected = true
                    updateConnectionReadiness()
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    iceConnected = false
                    stateFlow.value = stateFlow.value.copy(
                        phase = NexusGlobalPhase.FAILED,
                        message = if (signalSession?.relayAvailable == true)
                            "No se pudo abrir el enlace de medios. Revisa la red o vuelve a emparejar."
                        else "Esta red bloqueó la conexión directa; el servicio TURN global aún no está configurado."
                    )
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    iceConnected = false
                    stateFlow.value = stateFlow.value.copy(phase = NexusGlobalPhase.WAITING_FOR_MAC)
                    updateMessage("Enlace interrumpido; WebRTC intenta reconectar…")
                }
                else -> {
                    iceConnected = false
                    updateConnectionReadiness()
                }
            }
            Log.i(TAG, "WebRTC ICE state=${state?.name}")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            val body = JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .toString().toByteArray(Charsets.UTF_8)
            scope.launch {
                runCatching { signalConnection?.send(NexusSignalEnvelope.Kind.ICE, body) }
                    .onFailure { Log.w(TAG, "Could not send ICE candidate: ${it.javaClass.simpleName}") }
            }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) {
            stream?.videoTracks?.firstOrNull()?.let(::publishRemoteTrack)
        }
        override fun onRemoveStream(stream: MediaStream?) {
            if (remoteTrackFlow.value in stream?.videoTracks.orEmpty()) remoteTrackFlow.value = null
        }
        override fun onDataChannel(channel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            (receiver?.track() as? VideoTrack)?.let(::publishRemoteTrack)
        }
    }

    private suspend fun handleSignal(signal: NexusVerifiedSignal) {
        when (signal.kind) {
            NexusSignalEnvelope.Kind.ANSWER -> {
                val sdp = JSONObject(signal.body.toString(Charsets.UTF_8)).optString("sdp").takeIf(String::isNotBlank) ?: return
                setRemoteDescription(SessionDescription.Type.ANSWER, sdp)
                hasRemoteDescription.set(true)
                synchronized(candidateLock) {
                    while (pendingRemoteCandidates.isNotEmpty()) peer?.addIceCandidate(pendingRemoteCandidates.removeFirst())
                }
                Log.i(TAG, "Mac SDP answer applied; queued ICE candidates flushed")
            }
            NexusSignalEnvelope.Kind.ICE -> {
                val payload = JSONObject(signal.body.toString(Charsets.UTF_8))
                val candidate = payload.optString("candidate").takeIf(String::isNotBlank) ?: return
                val ice = IceCandidate(
                    payload.optString("sdpMid").takeIf(String::isNotBlank),
                    payload.optInt("sdpMLineIndex", 0),
                    candidate
                )
                if (hasRemoteDescription.get()) peer?.addIceCandidate(ice)
                else synchronized(candidateLock) { pendingRemoteCandidates.addLast(ice) }
            }
            NexusSignalEnvelope.Kind.CLOSE -> {
                stateFlow.value = NexusGlobalUiState(phase = NexusGlobalPhase.FAILED, message = "El Mac cerró la sesión.")
                close()
            }
            NexusSignalEnvelope.Kind.OFFER -> Log.w(TAG, "Unexpected remote offer ignored; Mac is the offer answerer")
        }
    }

    private suspend fun createOffer(): String = suspendCancellableCoroutine { continuation ->
        val activePeer = peer ?: return@suspendCancellableCoroutine continuation.resumeWithException(
            IllegalStateException("WebRTC peer no está listo.")
        )
        activePeer.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return continuation.resumeWithException(IllegalStateException("WebRTC no creó una oferta SDP."))
                activePeer.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { if (continuation.isActive) continuation.resume(description.description) }
                    override fun onSetFailure(error: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "No se pudo aplicar SDP local.")) }
                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "No se pudo crear la oferta SDP.")) }
            override fun onSetFailure(error: String?) = Unit
        }, org.webrtc.MediaConstraints())
    }

    private suspend fun setRemoteDescription(type: SessionDescription.Type, sdp: String) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val activePeer = peer ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                IllegalStateException("WebRTC peer no está listo.")
            )
            activePeer.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onSetFailure(error: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "El Mac rechazó la respuesta SDP.")) }
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            }, SessionDescription(type, sdp))
        }

    private fun publishRemoteTrack(track: VideoTrack) {
        macScreenTrack = track
        remoteTrackFlow.value = track
        Log.i(TAG, "Mac screen VideoTrack received id=${track.id()}")
    }

    private fun handleMacDataChannelFrame(bytes: ByteArray) {
        when (val result = MacProtocol.readFrame(bytes)) {
            is MacProtocol.FrameParseResult.Ok -> if (result.consumedBytes == bytes.size) {
                if (result.frame.type == MacProtocol.FrameType.SCREEN_STATUS) {
                    val code = result.frame.payload.firstOrNull()
                    val message = when (MacProtocol.ScreenShareStatus.fromWireCode(code ?: 0)) {
                        MacProtocol.ScreenShareStatus.STREAMING -> "El Mac está compartiendo su pantalla."
                        MacProtocol.ScreenShareStatus.STOPPED -> "Activa Compartir pantalla de Mac en la app del Mac."
                        MacProtocol.ScreenShareStatus.SCREEN_RECORDING_PERMISSION_REQUIRED -> "El Mac necesita permiso de Grabación de pantalla."
                        MacProtocol.ScreenShareStatus.CAPTURE_FAILED -> "Falló la captura de pantalla en el Mac."
                        MacProtocol.ScreenShareStatus.ALREADY_STREAMING -> "El Mac ya comparte su pantalla en otra sesión."
                        null -> "Estado de pantalla del Mac no reconocido."
                    }
                    when (MacProtocol.ScreenShareStatus.fromWireCode(code ?: 0)) {
                        MacProtocol.ScreenShareStatus.STREAMING -> remoteTrackFlow.value = macScreenTrack
                        else -> remoteTrackFlow.value = null
                    }
                    updateMessage(message)
                }
            }
            is MacProtocol.FrameParseResult.Error -> Log.w(TAG, "Rejected Mac DataChannel frame: ${result.reason}")
            MacProtocol.FrameParseResult.NeedMore -> Log.w(TAG, "Rejected incomplete Mac DataChannel frame")
        }
    }

    private fun updateMessage(message: String) {
        stateFlow.value = stateFlow.value.copy(message = message)
    }

    private fun updateConnectionReadiness() {
        val controlsOpen = controlChannel?.state() == DataChannel.State.OPEN
        if (iceConnected && controlsOpen) {
            stateFlow.value = stateFlow.value.copy(
                phase = NexusGlobalPhase.CONNECTED,
                message = "Conexión directa lista; vídeo y controles protegidos por WebRTC."
            )
        } else if (stateFlow.value.phase != NexusGlobalPhase.FAILED) {
            stateFlow.value = stateFlow.value.copy(
                phase = NexusGlobalPhase.WAITING_FOR_MAC,
                message = if (iceConnected) "Medios conectados; abriendo canal de controles…" else "Negociando el enlace seguro de medios…"
            )
        }
    }

    private fun resetPeerForRetry() {
        iceConnected = false
        controlChannel?.unregisterObserver()
        controlChannel?.close()
        controlChannel?.dispose()
        controlChannel = null
        peer?.close()
        peer?.dispose()
        peer = null
        videoTransceiver = null
        remoteTrackFlow.value = null
        macScreenTrack = null
        synchronized(candidateLock) { pendingRemoteCandidates.clear() }
        hasRemoteDescription.set(false)
        factory?.dispose()
        factory = null
    }

    private companion object {
        const val TAG = "NexusGlobalRtc"
        const val MAX_CONTROL_FRAME_BYTES = 16 * 1024
    }
}

/** Active single-device share for the consent-gated MediaProjection foreground service. */
object NexusGlobalSessionRegistry {
    @Volatile var active: NexusGlobalRtcSession? = null
}
