package com.elysium.nexus.core.transport.mac

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary protocol shared with the Mac agent.
 *
 * Wire format
 * -----------
 * Every frame on the wire is length-prefixed:
 *
 * ```
 * ┌────────────┬────────┬───────────────────┐
 * │ length u32 │ type u8│      payload       │
 * │ (big-end)  │        │ (length - 1 bytes) │
 * └────────────┴────────┴───────────────────┘
 * ```
 *
 * `length` is the total number of bytes in the
 * frame INCLUDING the type byte but EXCLUDING
 * itself. So a one-byte payload has length=1.
 *
 * Frame types
 * -----------
 *  - 0x01 HELLO          client → server
 *                        payload: 32-byte X25519 public key
 *  - 0x02 HELLO_ACK      server → client
 *                        payload: 32-byte X25519 public key
 *  - 0x03 PIN_DIGIT      client → server (encrypted)
 *                        payload: 1 byte (digit 0-9)
 *  - 0x04 PAIR_OK        server → client (encrypted)
 *                        payload: 1 byte (0=fail, 1=ok)
 *  - 0x05 MOUSE_MOVE     client → server (encrypted)
 *                        payload: 8 bytes (float32 dx, dy normalized)
 *  - 0x06 MOUSE_BUTTON   client → server (encrypted)
 *                        payload: 2 bytes (button u8, state u8)
 *  - 0x07 SCROLL         client → server (encrypted)
 *                        payload: 8 bytes (float32 dx, dy)
 *  - 0x08 KEY            client → server (encrypted)
 *                        payload: 9 bytes (action u8, hid_usage u32, modifiers u32)
 *  - 0x09 PINCH          client → server (encrypted)
 *                        payload: 4 bytes (float32 factor)
 *  - 0x0A HEARTBEAT      bidirectional
 *                        payload: 0 bytes
 *  - 0x0B GOODBYE        bidirectional
 *                        payload: 0 bytes
 *
 * The encryption (after PAIR_OK) is ChaCha20-Poly1305
 * with a key derived from the X25519 shared secret via
 * HKDF-SHA256. Each frame uses a fresh 12-byte nonce
 * concatenated to the ciphertext. The encrypted
 * payload is what `length` measures on the wire, so
 * the peer can stream frames without knowing the
 * plaintext size.
 *
 * This file is a pure-data module: it has no Android
 * or network dependencies. It is JVM-testable from
 * the standard test sourceset.
 */
object MacProtocol {

    enum class ScreenShareStatus(val wireCode: Byte, val userMessage: String) {
        STREAMING(0x01, "Recibiendo pantalla del Mac"),
        SCREEN_RECORDING_PERMISSION_REQUIRED(0x02, "Activa Grabación de pantalla para Elysium Nexus en el Mac"),
        CAPTURE_FAILED(0x03, "macOS no pudo capturar la pantalla; revisa Grabación de pantalla"),
        STOPPED(0x04, "Compartir pantalla detenido"),
        ALREADY_STREAMING(0x05, "Ya hay otra sesión recibiendo la pantalla del Mac");

        companion object {
            fun fromWireCode(code: Byte): ScreenShareStatus? = entries.firstOrNull { it.wireCode == code }
        }
    }

    const val MAX_FRAME_SIZE: Int = 1_048_576

    /** Opens an authenticated screen frame and rejects incomplete/non-JPEG data. */
    fun decryptScreenJpeg(
        encryptedPayload: ByteArray,
        channelKey: MacCrypto.ChannelKey
    ): ByteArray? {
        val jpeg = runCatching { channelKey.decrypt(encryptedPayload) }.getOrNull() ?: return null
        return validateScreenJpeg(jpeg)
    }

    fun validateScreenJpeg(jpeg: ByteArray): ByteArray? {
        if (jpeg.size < 4) return null
        val startsWithJpegMarker = jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()
        if (!startsWithJpegMarker) return null

        // Some ImageIO/ScreenCapture encoders append a small trailer after
        // JPEG EOI. Requiring FF D9 at the absolute end rejects every valid
        // frame even though Android's decoder can render it. Find the final
        // EOI marker in a bounded trailer window and discard bytes after it.
        val searchStart = (jpeg.size - 64).coerceAtLeast(2)
        for (index in jpeg.lastIndex - 1 downTo searchStart) {
            if (jpeg[index] == 0xFF.toByte() && jpeg[index + 1] == 0xD9.toByte()) {
                return jpeg.copyOf(index + 2)
            }
        }
        return null
    }

    enum class FrameType(val byte: Byte) {
        HELLO(0x01),
        HELLO_ACK(0x02),
        PIN_DIGIT(0x03),
        PAIR_OK(0x04),
        MOUSE_MOVE(0x05),
        MOUSE_BUTTON(0x06),
        SCROLL(0x07),
        KEY(0x08),
        PINCH(0x09),
        HEARTBEAT(0x0A),
        GOODBYE(0x0B),
        MEDIA(0x0C),
        SCREEN_REQUEST(0x0D),
        SCREEN_FRAME(0x0E.toByte()),
        MOUSE_ABS_MOVE(0x0F.toByte()),
        /** Encrypted capture state returned by the Mac (permission, started, failed, stopped). */
        SCREEN_STATUS(0x10.toByte()),
        /** UTF-8 text submitted by the user over the DTLS-protected global DataChannel. */
        TEXT_INPUT(0x11.toByte());

        companion object {
            private val BY_INDEX = entries.associateBy { it.byte }
            fun fromByte(b: Byte): FrameType? = BY_INDEX[b]
        }
    }

    enum class MouseButton(val byte: Byte) {
        LEFT(0),
        RIGHT(1),
        MIDDLE(2)
    }

    enum class ButtonState(val byte: Byte) {
        UP(0),
        DOWN(1)
    }

    enum class KeyAction(val byte: Byte) {
        DOWN(0),
        UP(1),
        REPEAT(2)
    }

    /**
     * Modifiers bitmask. Mirrors macOS `CGEventFlags`.
     * The bit positions are fixed by the protocol; do
     * not renumber.
     */
    object Modifiers {
        const val SHIFT: Int = 1 shl 1     // kCGEventFlagMaskShift
        const val CONTROL: Int = 1 shl 18  // kCGEventFlagMaskControl
        const val OPTION: Int = 1 shl 19   // kCGEventFlagMaskAlternate
        const val COMMAND: Int = 1 shl 20  // kCGEventFlagMaskCommand

        fun encode(vararg mods: Int): Int = mods.fold(0) { acc, m -> acc or m }
    }

    /**
     * A decoded frame.
     */
    data class Frame(
        val type: FrameType,
        /** The plaintext payload (already decrypted if the frame was encrypted). Empty for HEARTBEAT/GOODBYE. */
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            if (type != other.type) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }
        override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
    }

    /**
     * Encodes a frame for the wire.
     *
     * Wire layout: `u32 BE length | u8 type | payload`.
     * `length = 1 + payload.size`.
     */
    fun encodeFrame(type: FrameType, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= MAX_FRAME_SIZE - 1) { "payload too large" }
        val total = 4 + 1 + payload.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(1 + payload.size) // length (excludes the 4 length bytes)
        buf.put(type.byte)
        buf.put(payload)
        return buf.array()
    }

    /**
     * Reads length-prefixed frames from a buffer.
     *
     * Returns the next frame + the remaining unconsumed
     * bytes, or `null` if there is not enough data yet.
     *
     * The buffer is consumed in-place (bytes are
     * removed) so the caller can keep streaming.
     */
    fun readFrame(buffer: ByteArray): FrameParseResult {
        if (buffer.size < 5) return FrameParseResult.NeedMore
        val length = readUInt32BE(buffer, 0)
        if (length < 1 || length > MAX_FRAME_SIZE) {
            return FrameParseResult.Error("refusing frame of length $length (max $MAX_FRAME_SIZE)")
        }
        val total = 4 + length.toInt()
        if (buffer.size < total) return FrameParseResult.NeedMore
        val typeByte = buffer[4]
        val type = FrameType.fromByte(typeByte) ?: return FrameParseResult.Error("unknown frame type $typeByte")
        val payload = ByteArray(length.toInt() - 1)
        System.arraycopy(buffer, 5, payload, 0, payload.size)
        return FrameParseResult.Ok(Frame(type, payload), total)
    }

    /**
     * Reads frames from a `ByteBuffer`-backed stream
     * (a TCP socket). Returns the next complete frame
     * or `null` if more bytes are needed.
     */
    fun readFrameFromStream(
        inBuf: ByteArray,
        inLen: Int,
        consumed: Int
    ): StreamReadResult {
        // `inLen` = bytes currently in `inBuf`.
        // `consumed` = bytes already processed.
        val available = inLen - consumed
        if (available < 5) return StreamReadResult.NeedMore(consumed)
        val length = readUInt32BE(inBuf, consumed)
        if (length < 1 || length > MAX_FRAME_SIZE) {
            return StreamReadResult.Error("refusing frame of length $length (max $MAX_FRAME_SIZE)", consumed)
        }
        val total = 4 + length.toInt()
        if (available < total) return StreamReadResult.NeedMore(consumed)
        val typeByte = inBuf[consumed + 4]
        val type = FrameType.fromByte(typeByte) ?: return StreamReadResult.Error("unknown frame type $typeByte", consumed)
        val payload = ByteArray(length.toInt() - 1)
        System.arraycopy(inBuf, consumed + 5, payload, 0, payload.size)
        return StreamReadResult.Ok(Frame(type, payload), consumed + total)
    }

    private fun readUInt32BE(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)
    }

    sealed class FrameParseResult {
        data class Ok(val frame: Frame, val consumedBytes: Int) : FrameParseResult()
        data object NeedMore : FrameParseResult()
        data class Error(val reason: String) : FrameParseResult()
    }

    sealed class StreamReadResult {
        data class Ok(val frame: Frame, val newConsumed: Int) : StreamReadResult()
        data class NeedMore(val stillConsumed: Int) : StreamReadResult()
        data class Error(val reason: String, val newConsumed: Int) : StreamReadResult()
    }
}
