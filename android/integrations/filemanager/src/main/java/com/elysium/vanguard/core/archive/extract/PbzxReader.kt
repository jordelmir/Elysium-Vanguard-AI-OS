package com.elysium.vanguard.core.archive.extract

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * PBZX DECODER
 *
 * Apple uses a "pbzx" container for the payload of modern macOS installer
 * packages. It is a sequence of XZ compressed chunks whose concatenation is a
 * CPIO archive.
 *
 * Layout (big endian):
 * ```
 * 0x00  magic       4 bytes  "pbzx"
 * 0x04  flags       4 bytes
 * 0x08  chunkCount  4 bytes
 * 0x0C  reserved    4 bytes
 * 0x10  inflated    4 bytes  size of the inflated CPIO stream
 * 0x14  chunkFlags  4 bytes  0xFFFFFFFF marks the end of the stream
 * 0x18  chunkSize   4 bytes  size of the XZ chunk that follows
 * 0x1C  chunk data
 * ```
 * Every following chunk repeats the `chunkFlags` / `chunkSize` pair.
 */
internal object PbzxReader {

    private const val MAGIC = "pbzx"
    private const val HEADER_SIZE = 28
    private const val END_MARKER = 0xFFFFFFFFL

    /** True when [bytes] starts with the pbzx signature. */
    fun isPbzx(bytes: ByteArray): Boolean =
        bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == MAGIC

    /**
     * Inflates a pbzx stream into the raw CPIO bytes it carries.
     *
     * @return the CPIO payload, or null when the stream is not a valid pbzx.
     */
    fun inflate(bytes: ByteArray): ByteArray? {
        if (!isPbzx(bytes)) return null

        return try {
            val output = ByteArrayOutputStream()
            var offset = HEADER_SIZE

            // The first chunk is described by the fixed header.
            var chunkFlags = readUInt32(bytes, 0x14)
            var chunkSize = readUInt32(bytes, 0x18)

            while (chunkFlags != END_MARKER && chunkSize > 0) {
                if (offset + chunkSize > bytes.size) break
                val chunk = bytes.copyOfRange(offset, offset + chunkSize.toInt())
                inflateChunk(chunk)?.let { output.write(it) }
                offset += chunkSize.toInt()

                if (offset + 8 > bytes.size) break
                chunkFlags = readUInt32(bytes, offset)
                chunkSize = readUInt32(bytes, offset + 4)
                offset += 8
            }

            output.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun inflateChunk(chunk: ByteArray): ByteArray? = try {
        XZCompressorInputStream(chunk.inputStream()).use { input ->
            input.readBytes()
        }
    } catch (_: Exception) {
        null
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
