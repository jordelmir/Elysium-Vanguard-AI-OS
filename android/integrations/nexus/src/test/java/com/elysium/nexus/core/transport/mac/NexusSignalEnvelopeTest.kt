package com.elysium.nexus.core.transport.mac

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusSignalEnvelopeTest {
    private val sessionId = "123e4567-e89b-42d3-a456-426614174000"
    private val secret = ByteArray(32) { it.toByte() }
    private val body = "{\"sdp\":\"test\"}".toByteArray(Charsets.UTF_8)

    @Test
    fun signingMatchesCrossPlatformVectorAndRoundTrips() {
        val signed = NexusSignalEnvelope.signWithNonceForTesting(
            pairingSecret = secret,
            sessionId = sessionId,
            sender = NexusSignalEnvelope.Sender.ANDROID,
            sequence = 7,
            sentAtMillis = 1_700_000_000_000,
            kind = NexusSignalEnvelope.Kind.OFFER,
            body = body,
            nonce = ByteArray(12) { it.toByte() }
        )

        assertEquals("D6GTvNzb6CgitNuWYHvHYY5Ppp1UckyMbZkbp5l6r3I", signed.macBase64Url)
        assertEquals("AAECAwQFBgcICQoLm68j8PJCWlW1tGFR69kzJPbHqmasyNfdjFOhIfZG", signed.bodyBase64Url)
        val parsed = NexusSignalEnvelope.fromJson(JSONObject(signed.toJson().toString()))
        assertNotNull(parsed)
        assertArrayEquals(
            body,
            parsed!!.verify(
                pairingSecret = secret,
                expectedSessionId = sessionId,
                expectedSender = NexusSignalEnvelope.Sender.ANDROID,
                nowMillis = 1_700_000_000_000
            )!!
        )
    }

    @Test
    fun rejectsTamperingWrongPeerWrongSessionAndExpiredSignals() {
        val signed = NexusSignalEnvelope.sign(
            pairingSecret = secret,
            sessionId = sessionId,
            sender = NexusSignalEnvelope.Sender.MAC,
            sequence = 1,
            sentAtMillis = 1_700_000_000_000,
            kind = NexusSignalEnvelope.Kind.ANSWER,
            body = body
        )

        assertNull(signed.copy(bodyBase64Url = "eyJzZHAiOiJib2ciZX0").verify(
            secret, sessionId, NexusSignalEnvelope.Sender.MAC, 1_700_000_000_000
        ))
        assertNull(signed.verify(secret, sessionId, NexusSignalEnvelope.Sender.ANDROID, 1_700_000_000_000))
        assertNull(signed.verify(secret, "123e4567-e89b-42d3-a456-426614174001", NexusSignalEnvelope.Sender.MAC, 1_700_000_000_000))
        assertNull(signed.verify(secret, sessionId, NexusSignalEnvelope.Sender.MAC, 1_700_000_121_000))
        assertNull(signed.verify(ByteArray(31), sessionId, NexusSignalEnvelope.Sender.MAC, 1_700_000_000_000))
    }

    @Test
    fun replayWindowAcceptsBoundedReorderingAndRejectsDuplicates() {
        val window = NexusSignalReplayWindow(width = 4)
        assertTrue(window.accept(10))
        assertTrue(window.accept(12))
        assertTrue(window.accept(11))
        assertFalse(window.accept(11))
        assertTrue(window.accept(16))
        assertFalse(window.accept(12))
        assertFalse(window.accept(0))
    }
}
