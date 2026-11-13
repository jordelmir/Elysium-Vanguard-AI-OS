package com.elysium.nexus.core.transport.mac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Mac/PC channel crypto.
 *
 * The tests cover the key derivation, AEAD
 * encrypt/decrypt round-trip, and the nonce
 * counter's monotonicity.
 *
 * These tests run on the JVM (the standard JDK
 * 17 build), so they do **not** exercise the
 * Android-specific XECPrivateKey.getScalar()
 * quirk; that part is validated on the device.
 */
class MacCryptoTest {

    @Test
    fun `generateKeyPair produces 32-byte public key`() {
        val pair = MacCrypto.generateKeyPair()
        assertEquals(32, pair.publicKeyBytes.size)
        assertEquals(32, pair.privateScalar.size)
    }

    @Test
    fun `two key pairs are different`() {
        val a = MacCrypto.generateKeyPair()
        val b = MacCrypto.generateKeyPair()
        // Overwhelming probability (2^-256) that the
        // two public keys are different.
        assertNotEquals(a.publicKeyBytes.toList(), b.publicKeyBytes.toList())
    }

    @Test
    fun `deriveChannelKey is symmetric for the same shared secret`() {
        // Alice and Bob each generate a key pair.
        // They exchange public keys and derive the
        // same channel key.
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val aliceView = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val bobView = MacCrypto.deriveChannelKey(bob, alice.publicKeyBytes)
        assertArrayEquals(aliceView.keyBytes, bobView.keyBytes)
    }

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val aliceChannel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val plaintext = "Hola Mac!".toByteArray(Charsets.UTF_8)
        val ciphertext = aliceChannel.encrypt(plaintext)
        val decrypted = aliceChannel.decrypt(ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted Mac JPEG screen frame is authenticated and decoded`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val channel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())

        assertArrayEquals(jpeg, MacProtocol.decryptScreenJpeg(channel.encrypt(jpeg), channel))
    }

    @Test
    fun `tampered or non JPEG Mac screen frame is discarded`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val channel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val tampered = channel.encrypt(jpeg).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertEquals(null, MacProtocol.decryptScreenJpeg(tampered, channel))
        assertEquals(null, MacProtocol.decryptScreenJpeg(channel.encrypt("not jpeg".toByteArray()), channel))
    }

    @Test
    fun `authenticated JPEG permits and trims a short encoder trailer`() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02,
            0xFF.toByte(), 0xD9.toByte(), 0x00, 0x00
        )

        assertArrayEquals(
            jpeg.copyOfRange(0, 6),
            MacProtocol.validateScreenJpeg(jpeg)
        )
    }

    @Test
    fun `each encrypt uses a fresh nonce`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val channel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val pt = "X".toByteArray(Charsets.UTF_8)
        val c1 = channel.encrypt(pt)
        val c2 = channel.encrypt(pt)
        // The same plaintext encrypted twice must
        // produce different ciphertext (fresh nonce).
        // The nonce is the first 12 bytes; the
        // ciphertext starts at byte 12. We just
        // compare the 12-byte nonces.
        val n1 = c1.copyOfRange(0, 12)
        val n2 = c2.copyOfRange(0, 12)
        assertNotEquals(n1.toList(), n2.toList())
    }

    @Test
    fun `nonce counter is monotonic`() {
        val counter = MacCrypto.NonceCounter()
        val n1 = counter.next()
        val n2 = counter.next()
        val n3 = counter.next()
        // The bottom 8 bytes of each nonce are a
        // big-endian counter. They must be 1, 2, 3.
        assertEquals(1L, bytesToLong(n1, offset = 4))
        assertEquals(2L, bytesToLong(n2, offset = 4))
        assertEquals(3L, bytesToLong(n3, offset = 4))
        // The top 4 bytes are zero.
        for (i in 0 until 4) {
            assertEquals(0, n1[i].toInt() and 0xFF)
            assertEquals(0, n2[i].toInt() and 0xFF)
            assertEquals(0, n3[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `wrong channel key cannot decrypt`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val eve = MacCrypto.generateKeyPair()
        val aliceChannel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val eveChannel = MacCrypto.deriveChannelKey(eve, bob.publicKeyBytes)
        val ciphertext = aliceChannel.encrypt("secret".toByteArray())
        // Eve derives a different channel key and
        // cannot decrypt Alice's ciphertext.
        var threw = false
        try {
            eveChannel.decrypt(ciphertext)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("decryption with wrong key must throw", threw)
    }

    @Test
    fun `short ciphertext is rejected`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val channel = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        var threw = false
        try {
            channel.decrypt(ByteArray(10))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("short ciphertext must be rejected", threw)
    }

    @Test
    fun `ChannelKey equality is value-based`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()
        val a = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        val b = MacCrypto.deriveChannelKey(alice, bob.publicKeyBytes)
        // Two ChannelKey objects with the same
        // underlying bytes are equal.
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `KeyPair equality is value-based`() {
        val pair = MacCrypto.generateKeyPair()
        val pair2 = MacCrypto.KeyPair(pair.publicKeyBytes, pair.privateScalar)
        assertEquals(pair, pair2)
        assertEquals(pair.hashCode(), pair2.hashCode())
    }

    private fun bytesToLong(b: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (b[offset + i].toLong() and 0xFF)
        }
        return v
    }
}
