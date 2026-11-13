package com.elysium.nexus.core.transport.mac

import org.json.JSONObject
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticates SDP/ICE signaling before it reaches WebRTC. */
data class NexusSignalEnvelope(
    val sessionId: String,
    val sender: Sender,
    val sequence: Long,
    val sentAtMillis: Long,
    val kind: Kind,
    val bodyBase64Url: String,
    val macBase64Url: String
) {
    enum class Sender(val wireValue: String) { MAC("mac"), ANDROID("android") }
    enum class Kind(val wireValue: String) { OFFER("offer"), ANSWER("answer"), ICE("ice"), CLOSE("close") }

    fun toJson(): JSONObject = JSONObject()
        .put("v", VERSION)
        .put("sessionId", sessionId)
        .put("sender", sender.wireValue)
        .put("sequence", sequence)
        .put("sentAtMillis", sentAtMillis)
        .put("kind", kind.wireValue)
        .put("body", bodyBase64Url)
        .put("mac", macBase64Url)

    fun verify(
        pairingSecret: ByteArray,
        expectedSessionId: String,
        expectedSender: Sender,
        nowMillis: Long = System.currentTimeMillis()
    ): ByteArray? {
        if (pairingSecret.size != SECRET_BYTES || sessionId != expectedSessionId) return null
        if (!sessionId.matches(SESSION_ID_PATTERN)) return null
        if (sender != expectedSender || sequence <= 0) return null
        if (sentAtMillis > nowMillis + MAX_FUTURE_SKEW_MS || sentAtMillis < nowMillis - MAX_AGE_MS) return null
        val body = decodeUrlSafe(bodyBase64Url) ?: return null
        if (body.size !in MIN_ENCRYPTED_BODY_BYTES..MAX_ENCRYPTED_BODY_BYTES) return null
        val receivedMac = decodeUrlSafe(macBase64Url) ?: return null
        if (receivedMac.size != MAC_BYTES) return null
        val macKey = deriveKey(pairingSecret, sessionId, MAC_KEY_INFO)
        val expectedMac = hmac(macKey, canonicalBytes())
        if (!MessageDigest.isEqual(expectedMac, receivedMac)) return null
        return decryptBody(pairingSecret, sessionId, body, headerBytes())
    }

    private fun headerBytes(): ByteArray = listOf(
        "NEXUS-SIGNAL-v$VERSION",
        sessionId,
        sender.wireValue,
        sequence.toString(),
        sentAtMillis.toString(),
        kind.wireValue
    ).joinToString("\n").toByteArray(US_ASCII)

    private fun canonicalBytes(): ByteArray =
        (String(headerBytes(), US_ASCII) + "\n" + bodyBase64Url).toByteArray(US_ASCII)

    companion object {
        const val VERSION = 1
        const val SECRET_BYTES = 32
        const val MAX_BODY_BYTES = 128 * 1024
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MIN_ENCRYPTED_BODY_BYTES = NONCE_BYTES + TAG_BYTES
        private const val MAX_ENCRYPTED_BODY_BYTES = MAX_BODY_BYTES + MIN_ENCRYPTED_BODY_BYTES
        private const val MAC_BYTES = 32
        private const val MAX_FUTURE_SKEW_MS = 30_000L
        private const val MAX_AGE_MS = 120_000L
        private const val MAC_ALGORITHM = "HmacSHA256"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val MAC_KEY_INFO = "NEXUS-SIGNAL-MAC-v1"
        private const val ENCRYPTION_KEY_INFO = "NEXUS-SIGNAL-ENC-v1"
        private const val HKDF_ALGORITHM = "HmacSHA256"

        fun sign(
            pairingSecret: ByteArray,
            sessionId: String,
            sender: Sender,
            sequence: Long,
            sentAtMillis: Long,
            kind: Kind,
            body: ByteArray
        ): NexusSignalEnvelope {
            val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            return signWithNonce(pairingSecret, sessionId, sender, sequence, sentAtMillis, kind, body, nonce)
        }

        internal fun signWithNonceForTesting(
            pairingSecret: ByteArray,
            sessionId: String,
            sender: Sender,
            sequence: Long,
            sentAtMillis: Long,
            kind: Kind,
            body: ByteArray,
            nonce: ByteArray
        ): NexusSignalEnvelope = signWithNonce(pairingSecret, sessionId, sender, sequence, sentAtMillis, kind, body, nonce)

        private fun signWithNonce(
            pairingSecret: ByteArray,
            sessionId: String,
            sender: Sender,
            sequence: Long,
            sentAtMillis: Long,
            kind: Kind,
            body: ByteArray,
            nonce: ByteArray
        ): NexusSignalEnvelope {
            require(pairingSecret.size == SECRET_BYTES) { "Pairing secret must be 32 bytes" }
            require(sequence > 0) { "Signal sequence must be positive" }
            require(body.size <= MAX_BODY_BYTES) { "Signaling payload exceeds limit" }
            require(sessionId.matches(SESSION_ID_PATTERN)) { "Invalid signaling session id" }
            require(nonce.size == NONCE_BYTES) { "Signal nonce must be 12 bytes" }
            val unsigned = NexusSignalEnvelope(
                sessionId = sessionId,
                sender = sender,
                sequence = sequence,
                sentAtMillis = sentAtMillis,
                kind = kind,
                bodyBase64Url = "",
                macBase64Url = ""
            )
            val encryptedBody = encryptBody(pairingSecret, sessionId, body, nonce, unsigned.headerBytes())
            val withBody = unsigned.copy(bodyBase64Url = encodeUrlSafe(encryptedBody))
            val macKey = deriveKey(pairingSecret, sessionId, MAC_KEY_INFO)
            return withBody.copy(macBase64Url = encodeUrlSafe(hmac(macKey, withBody.canonicalBytes())))
        }

        fun fromJson(json: JSONObject): NexusSignalEnvelope? {
            return try {
                if (json.optInt("v", -1) != VERSION) return null
                val sessionId = json.getString("sessionId")
                if (!sessionId.matches(SESSION_ID_PATTERN)) return null
                val sender = Sender.entries.firstOrNull { it.wireValue == json.getString("sender") } ?: return null
                val kind = Kind.entries.firstOrNull { it.wireValue == json.getString("kind") } ?: return null
                val sequence = json.getLong("sequence")
                val sentAtMillis = json.getLong("sentAtMillis")
                val body = json.getString("body")
                val mac = json.getString("mac")
                if (sequence <= 0 || body.length > MAX_ENCODED_BODY_CHARS || mac.length != 43) return null
                if (!body.matches(BASE64_URL_PATTERN) || !mac.matches(BASE64_URL_PATTERN)) return null
                NexusSignalEnvelope(sessionId, sender, sequence, sentAtMillis, kind, body, mac)
            } catch (_: Exception) {
                null
            }
        }

        private const val MAX_ENCODED_BODY_CHARS = ((MAX_ENCRYPTED_BODY_BYTES + 2) / 3) * 4
        private val SESSION_ID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        private val BASE64_URL_PATTERN = Regex("[A-Za-z0-9_-]+")

        private fun hmac(secret: ByteArray, input: ByteArray): ByteArray {
            val mac = Mac.getInstance(MAC_ALGORITHM)
            mac.init(SecretKeySpec(secret, MAC_ALGORITHM))
            return mac.doFinal(input)
        }

        private fun deriveKey(secret: ByteArray, sessionId: String, info: String): ByteArray {
            val id = UUID.fromString(sessionId)
            val salt = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putLong(id.mostSignificantBits)
                .putLong(id.leastSignificantBits)
                .array()
            val extract = Mac.getInstance(HKDF_ALGORITHM)
            extract.init(SecretKeySpec(salt, HKDF_ALGORITHM))
            val pseudoRandomKey = extract.doFinal(secret)
            val expand = Mac.getInstance(HKDF_ALGORITHM)
            expand.init(SecretKeySpec(pseudoRandomKey, HKDF_ALGORITHM))
            return expand.doFinal(info.toByteArray(Charsets.UTF_8) + byteArrayOf(0x01))
        }

        private fun encryptBody(secret: ByteArray, sessionId: String, plaintext: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
            val key = SecretKeySpec(deriveKey(secret, sessionId, ENCRYPTION_KEY_INFO), "AES")
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(aad)
            return nonce + cipher.doFinal(plaintext)
        }

        private fun decryptBody(secret: ByteArray, sessionId: String, encrypted: ByteArray, aad: ByteArray): ByteArray? {
            if (encrypted.size < MIN_ENCRYPTED_BODY_BYTES) return null
            return runCatching {
                val nonce = encrypted.copyOfRange(0, NONCE_BYTES)
                val ciphertextAndTag = encrypted.copyOfRange(NONCE_BYTES, encrypted.size)
                val key = SecretKeySpec(deriveKey(secret, sessionId, ENCRYPTION_KEY_INFO), "AES")
                val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
                cipher.updateAAD(aad)
                cipher.doFinal(ciphertextAndTag)
            }.getOrNull()
        }

        private fun encodeUrlSafe(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun decodeUrlSafe(value: String): ByteArray? {
            if (value.isEmpty()) return ByteArray(0)
            if (value.length > MAX_ENCODED_BODY_CHARS || !value.matches(BASE64_URL_PATTERN)) return null
            return runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
        }
    }
}

/** Sliding anti-replay window permits small delivery reordering, rejects duplicates. */
class NexusSignalReplayWindow(private val width: Int = 64) {
    private var highestSequence = 0L
    private var seenMask = 0L

    init { require(width in 1..64) }

    @Synchronized
    fun accept(sequence: Long): Boolean {
        if (sequence <= 0) return false
        if (sequence > highestSequence) {
            val shift = sequence - highestSequence
            seenMask = if (shift >= width) 1L else (seenMask shl shift.toInt()) or 1L
            highestSequence = sequence
            return true
        }
        val offset = highestSequence - sequence
        if (offset >= width) return false
        val bit = 1L shl offset.toInt()
        if (seenMask and bit != 0L) return false
        seenMask = seenMask or bit
        return true
    }
}
