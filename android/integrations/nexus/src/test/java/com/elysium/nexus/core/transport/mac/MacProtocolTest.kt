package com.elysium.nexus.core.transport.mac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests for the binary protocol shared with the
 * Mac agent.
 *
 * The tests exercise:
 *
 *  - Frame encoding: every frame type round-trips
 *    through `encodeFrame` / `readFrame`.
 *  - Stream reading: the stream parser correctly
 *    advances `consumed` through multi-frame
 *    buffers.
 *  - Edge cases: empty payloads, oversized
 *    payloads (rejected), short buffers (returns
 *    NeedMore).
 */
class MacProtocolTest {

    // === Frame encoding round-trip ===

    @Test
    fun `hello frame round-trips`() {
        val payload = ByteArray(32) { it.toByte() }
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.HELLO, payload)
        // 4 (length) + 1 (type) + 32 (payload) = 37 bytes
        assertEquals(37, encoded.size)
        // Length is the first 4 bytes (big-endian)
        assertEquals(33, readUInt32BE(encoded, 0))
        // Type byte is the 5th byte
        assertEquals(MacProtocol.FrameType.HELLO.byte, encoded[4])
        // Payload is the rest
        val payloadInEncoded = ByteArray(32)
        System.arraycopy(encoded, 5, payloadInEncoded, 0, 32)
        assertArrayEquals(payload, payloadInEncoded)
    }

    @Test
    fun `pin digit frame round-trips`() {
        val payload = byteArrayOf(0x07)
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.PIN_DIGIT, payload)
        assertEquals(6, encoded.size)
        val result = MacProtocol.readFrame(encoded)
        assertTrue(result is MacProtocol.FrameParseResult.Ok)
        val frame = (result as MacProtocol.FrameParseResult.Ok).frame
        assertEquals(MacProtocol.FrameType.PIN_DIGIT, frame.type)
        assertArrayEquals(byteArrayOf(0x07), frame.payload)
    }

    @Test
    fun `mouse move frame round-trips big-endian floats`() {
        val dx = 12.5f
        val dy = -7.25f
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(dx)
            .putFloat(dy)
            .array()
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.MOUSE_MOVE, payload)
        val result = MacProtocol.readFrame(encoded)
        assertTrue(result is MacProtocol.FrameParseResult.Ok)
        val frame = (result as MacProtocol.FrameParseResult.Ok).frame
        assertEquals(MacProtocol.FrameType.MOUSE_MOVE, frame.type)
        assertEquals(8, frame.payload.size)
        val parsedDx = ByteBuffer.wrap(frame.payload, 0, 4).order(ByteOrder.BIG_ENDIAN).float
        val parsedDy = ByteBuffer.wrap(frame.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).float
        assertEquals(dx, parsedDx, 0.0f)
        assertEquals(dy, parsedDy, 0.0f)
    }

    @Test
    fun `heartbeat frame has zero-byte payload`() {
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.HEARTBEAT)
        assertEquals(5, encoded.size) // 4 (length) + 1 (type) + 0
        assertEquals(1, readUInt32BE(encoded, 0))
        assertEquals(MacProtocol.FrameType.HEARTBEAT.byte, encoded[4])
    }

    // === Stream reading ===

    @Test
    fun `stream parser advances through multiple frames`() {
        val frame1 = MacProtocol.encodeFrame(MacProtocol.FrameType.PIN_DIGIT, byteArrayOf(0x01))
        val frame2 = MacProtocol.encodeFrame(MacProtocol.FrameType.PIN_DIGIT, byteArrayOf(0x02))
        val frame3 = MacProtocol.encodeFrame(MacProtocol.FrameType.PIN_DIGIT, byteArrayOf(0x03))
        val buf = frame1 + frame2 + frame3
        var consumed = 0
        val parsed = mutableListOf<MacProtocol.Frame>()
        while (true) {
            when (val r = MacProtocol.readFrameFromStream(buf, buf.size, consumed)) {
                is MacProtocol.StreamReadResult.Ok -> {
                    parsed.add(r.frame)
                    consumed = r.newConsumed
                }
                is MacProtocol.StreamReadResult.NeedMore -> break
                is MacProtocol.StreamReadResult.Error -> error("unexpected error: ${r.reason}")
            }
        }
        assertEquals(3, parsed.size)
        assertEquals(MacProtocol.FrameType.PIN_DIGIT, parsed[0].type)
        assertEquals(0x01.toByte(), parsed[0].payload[0])
        assertEquals(0x02.toByte(), parsed[1].payload[0])
        assertEquals(0x03.toByte(), parsed[2].payload[0])
    }

    @Test
    fun `stream parser returns NeedMore on truncated frame`() {
        val frame = MacProtocol.encodeFrame(MacProtocol.FrameType.MOUSE_MOVE, ByteArray(8))
        val truncated = frame.copyOfRange(0, frame.size - 4) // drop last 4 bytes
        val r = MacProtocol.readFrameFromStream(truncated, truncated.size, 0)
        assertTrue(r is MacProtocol.StreamReadResult.NeedMore)
    }

    @Test
    fun `stream parser returns NeedMore on truncated header`() {
        val frame = MacProtocol.encodeFrame(MacProtocol.FrameType.HEARTBEAT)
        val truncated = frame.copyOfRange(0, 3) // only 3 bytes of the length
        val r = MacProtocol.readFrameFromStream(truncated, truncated.size, 0)
        assertTrue(r is MacProtocol.StreamReadResult.NeedMore)
    }

    @Test
    fun `stream parser rejects oversized frame`() {
        // Construct a 5-byte buffer where the length is
        // bigger than the max (1 MB + 1).
        val buf = byteArrayOf(0x00, 0x10, 0x00, 0x01, 0x00) // length = 1 MB + 1
        val r = MacProtocol.readFrameFromStream(buf, buf.size, 0)
        assertTrue(r is MacProtocol.StreamReadResult.Error)
    }

    @Test
    fun `stream parser rejects unknown frame type`() {
        val buf = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0xFF.toByte())
        val r = MacProtocol.readFrameFromStream(buf, buf.size, 0)
        assertTrue(r is MacProtocol.StreamReadResult.Error)
    }

    @Test
    fun `frame type byte values are stable`() {
        // The wire format is a contract; do not renumber
        // these. Both the Mac agent and the Android
        // client must agree.
        assertEquals(0x01.toByte(), MacProtocol.FrameType.HELLO.byte)
        assertEquals(0x02.toByte(), MacProtocol.FrameType.HELLO_ACK.byte)
        assertEquals(0x03.toByte(), MacProtocol.FrameType.PIN_DIGIT.byte)
        assertEquals(0x04.toByte(), MacProtocol.FrameType.PAIR_OK.byte)
        assertEquals(0x05.toByte(), MacProtocol.FrameType.MOUSE_MOVE.byte)
        assertEquals(0x06.toByte(), MacProtocol.FrameType.MOUSE_BUTTON.byte)
        assertEquals(0x07.toByte(), MacProtocol.FrameType.SCROLL.byte)
        assertEquals(0x08.toByte(), MacProtocol.FrameType.KEY.byte)
        assertEquals(0x09.toByte(), MacProtocol.FrameType.PINCH.byte)
        assertEquals(0x0A.toByte(), MacProtocol.FrameType.HEARTBEAT.byte)
        assertEquals(0x0B.toByte(), MacProtocol.FrameType.GOODBYE.byte)
        assertEquals(0x0F.toByte(), MacProtocol.FrameType.MOUSE_ABS_MOVE.byte)
        assertEquals(0x10.toByte(), MacProtocol.FrameType.SCREEN_STATUS.byte)
        assertEquals(0x11.toByte(), MacProtocol.FrameType.TEXT_INPUT.byte)
    }

    @Test
    fun `modifier bitmask mirrors CGEventFlags`() {
        // These bit positions are pinned to the macOS
        // CGEventFlags layout. Do not change them or
        // the agent's modifier handling breaks.
        assertEquals(1 shl 1, MacProtocol.Modifiers.SHIFT)
        assertEquals(1 shl 18, MacProtocol.Modifiers.CONTROL)
        assertEquals(1 shl 19, MacProtocol.Modifiers.OPTION)
        assertEquals(1 shl 20, MacProtocol.Modifiers.COMMAND)
    }

    @Test
    fun `encode then read round-trips the entire frame`() {
        val payload = ByteArray(256) { (it and 0xFF).toByte() }
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.SCROLL, payload)
        val result = MacProtocol.readFrame(encoded)
        assertTrue(result is MacProtocol.FrameParseResult.Ok)
        val ok = result as MacProtocol.FrameParseResult.Ok
        assertEquals(MacProtocol.FrameType.SCROLL, ok.frame.type)
        assertArrayEquals(payload, ok.frame.payload)
    }

    @Test
    fun `global text input frame preserves Unicode characters`() {
        val text = "Prueba ñ, á, ¿sí? 🚗"
        val payload = text.toByteArray(Charsets.UTF_8)
        val encoded = MacProtocol.encodeFrame(MacProtocol.FrameType.TEXT_INPUT, payload)

        val result = MacProtocol.readFrame(encoded)
        assertTrue(result is MacProtocol.FrameParseResult.Ok)
        val parsed = result as MacProtocol.FrameParseResult.Ok
        assertEquals(MacProtocol.FrameType.TEXT_INPUT, parsed.frame.type)
        assertEquals(text, parsed.frame.payload.toString(Charsets.UTF_8))
        assertEquals(encoded.size, parsed.consumedBytes)
    }

    // === Helpers ===

    private fun readUInt32BE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }
}
