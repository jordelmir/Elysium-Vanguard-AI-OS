package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.util.Log
import com.elysium.nexus.BuildConfig
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Cloud rendezvous and private, end-to-end encrypted SDP/ICE exchange. */
@OptIn(SupabaseExperimental::class)
class NexusCloudSignaling(context: Context) {
    private val applicationContext = context.applicationContext
    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.NEXUS_SUPABASE_URL,
        supabaseKey = BuildConfig.NEXUS_SUPABASE_PUBLISHABLE_KEY
    ) {
        httpEngine = OkHttp.create()
        install(Auth) {
            sessionManager = NexusAuthSessionManager(applicationContext)
            alwaysAutoRefresh = true
        }
        install(Functions)
        install(Realtime)
    }

    val isConfigured: Boolean
        get() = BuildConfig.NEXUS_SUPABASE_URL.startsWith("https://") &&
            BuildConfig.NEXUS_SUPABASE_PUBLISHABLE_KEY.startsWith("sb_publishable_")

    suspend fun createInvitation(): NexusCloudSession {
        requireConfigured()
        ensureAnonymousSession()
        val secretBytes = ByteArray(NexusSignalEnvelope.SECRET_BYTES).also(SecureRandom()::nextBytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        val response = invokeSession(
            buildJsonObject {
                put("action", "create")
                put("requestId", UUID.randomUUID().toString())
                put("pairingSecret", secret)
            }
        )
        require(response.pairingSecret == secret) { "Cloud session returned a mismatched pairing secret" }
        return response.toCloudSession(secret)
    }

    suspend fun joinInvitation(pairingCode: String): NexusCloudSession {
        requireConfigured()
        ensureAnonymousSession()
        val invite = NexusPairingCode.parse(pairingCode)
            ?: throw IllegalArgumentException("El código de emparejamiento no es válido o venció.")
        val response = invokeSession(
            buildJsonObject {
                put("action", "join")
                put("sessionId", invite.sessionId)
                put("pairingSecret", invite.secretBase64Url)
            }
        )
        return response.toCloudSession(invite.secretBase64Url)
    }

    suspend fun openSignalChannel(
        scope: CoroutineScope,
        session: NexusCloudSession,
        localRole: NexusSignalEnvelope.Sender,
        onSignal: suspend (NexusVerifiedSignal) -> Unit,
        onFailure: (String) -> Unit = {}
    ): NexusCloudSignalConnection {
        requireConfigured()
        require(session.pairingSecretBytes.size == NexusSignalEnvelope.SECRET_BYTES)
        val channel = client.channel(session.topic) { isPrivate = true }
        val replayWindow = NexusSignalReplayWindow()
        val sequence = AtomicLong(0)
        val remoteRole = when (localRole) {
            NexusSignalEnvelope.Sender.MAC -> NexusSignalEnvelope.Sender.ANDROID
            NexusSignalEnvelope.Sender.ANDROID -> NexusSignalEnvelope.Sender.MAC
        }
        val signals = MutableSharedFlow<NexusVerifiedSignal>(extraBufferCapacity = 64)

        val listener = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                channel.broadcastFlow<JsonObject>(SIGNAL_EVENT).collect { message ->
                    val envelope = NexusSignalEnvelope.fromJson(JSONObject(message.toString())) ?: return@collect
                    val body = envelope.verify(
                        pairingSecret = session.pairingSecretBytes,
                        expectedSessionId = session.sessionId,
                        expectedSender = remoteRole
                    ) ?: return@collect
                    if (!replayWindow.accept(envelope.sequence)) return@collect
                    val verified = NexusVerifiedSignal(envelope.kind, body, envelope.sequence)
                    signals.emit(verified)
                    onSignal(verified)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Private signaling receive loop ended: ${error.javaClass.simpleName}")
                onFailure("signaling_receive_failed")
            }
        }

        try {
            channel.subscribe(blockUntilSubscribed = true)
        } catch (error: Exception) {
            listener.cancel()
            throw error
        }

        return NexusCloudSignalConnection(
            channel = channel,
            session = session,
            sender = localRole,
            sequence = sequence,
            listener = listener,
            signals = signals.asSharedFlow()
        )
    }

    suspend fun closeSession(session: NexusCloudSession) {
        if (!isConfigured) return
        runCatching {
            ensureAnonymousSession()
            invokeSession(buildJsonObject {
                put("action", "close")
                put("sessionId", session.sessionId)
            })
        }.onFailure { Log.w(TAG, "Could not close Nexus cloud session: ${it.javaClass.simpleName}") }
    }

    private suspend fun ensureAnonymousSession() {
        if (client.auth.currentUserOrNull() == null) {
            client.auth.signInAnonymously()
        }
        check(client.auth.currentSessionOrNull() != null) { "Nexus cloud authentication did not produce a session" }
    }

    private suspend fun invokeSession(body: JsonObject): NexusSessionFunctionResponse {
        val response = client.functions.invoke("nexus-session", body = body)
        return response.body()
    }

    private fun requireConfigured() {
        check(isConfigured) { "La conexión global no está configurada en esta compilación." }
    }

    private fun NexusSessionFunctionResponse.toCloudSession(secret: String): NexusCloudSession {
        require(sessionId.isNotBlank() && topic == "nexus-session:$sessionId") {
            "Cloud returned an invalid Nexus session"
        }
        return NexusCloudSession(
            sessionId = sessionId,
            topic = topic,
            pairingSecret = secret,
            iceServers = iceServers.map { it.toRtcConfiguration() },
            relayAvailable = relayAvailable,
            expiresAt = expiresAt ?: joinExpiresAt.orEmpty()
        )
    }

    private fun NexusIceServer.toRtcConfiguration(): org.webrtc.PeerConnection.IceServer =
        org.webrtc.PeerConnection.IceServer.builder(urls)
            .apply {
                username?.let(::setUsername)
                credential?.let(::setPassword)
            }
            .createIceServer()

    private companion object {
        const val TAG = "NexusCloudSignaling"
        const val SIGNAL_EVENT = "nexus-signal"
    }
}

@Serializable
data class NexusSessionFunctionResponse(
    val sessionId: String,
    val topic: String,
    val pairingSecret: String? = null,
    val joinExpiresAt: String? = null,
    val expiresAt: String? = null,
    val iceServers: List<NexusIceServer> = emptyList(),
    val relayAvailable: Boolean = false
)

@Serializable
data class NexusIceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class NexusCloudSession(
    val sessionId: String,
    val topic: String,
    val pairingSecret: String,
    val iceServers: List<org.webrtc.PeerConnection.IceServer>,
    val relayAvailable: Boolean,
    val expiresAt: String
) {
    val pairingCode: String get() = NexusPairingCode(sessionId, pairingSecret).encode()
    internal val pairingSecretBytes: ByteArray
        get() = Base64.getUrlDecoder().decode(pairingSecret)
}

data class NexusVerifiedSignal(
    val kind: NexusSignalEnvelope.Kind,
    val body: ByteArray,
    val sequence: Long
)

class NexusCloudSignalConnection internal constructor(
    private val channel: io.github.jan.supabase.realtime.RealtimeChannel,
    private val session: NexusCloudSession,
    private val sender: NexusSignalEnvelope.Sender,
    private val sequence: AtomicLong,
    private val listener: Job,
    val signals: kotlinx.coroutines.flow.SharedFlow<NexusVerifiedSignal>
) {
    suspend fun send(kind: NexusSignalEnvelope.Kind, body: ByteArray) {
        val envelope = NexusSignalEnvelope.sign(
            pairingSecret = session.pairingSecretBytes,
            sessionId = session.sessionId,
            sender = sender,
            sequence = sequence.incrementAndGet(),
            sentAtMillis = System.currentTimeMillis(),
            kind = kind,
            body = body
        )
        val json = Json.parseToJsonElement(envelope.toJson().toString()) as JsonObject
        channel.broadcast(event = "nexus-signal", message = json)
    }

    suspend fun close() {
        listener.cancel()
        channel.unsubscribe()
        session.pairingSecretBytes.fill(0)
    }
}
