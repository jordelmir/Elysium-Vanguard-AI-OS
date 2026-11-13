package com.elysium.vanguard.core.archive.extract

import com.elysium.vanguard.core.archive.ArchiveEntry
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveExtractionRequest
import com.elysium.vanguard.core.archive.ArchiveFormat
import com.elysium.vanguard.core.archive.ArchiveIntegrityReport
import com.elysium.vanguard.core.archive.ArchiveManifest
import com.elysium.vanguard.core.archive.ArchiveProgressListener
import com.elysium.vanguard.core.archive.ArchiveReader
import com.elysium.vanguard.core.archive.ArchiveReaders
import com.elysium.vanguard.core.archive.ArchiveVerifier
import com.elysium.vanguard.core.archive.ExtractionReport
import com.elysium.vanguard.core.archive.ExtractionSink
import java.io.File
import java.io.RandomAccessFile

/**
 * ISO 9660 reader (also covers the UDF bridge used by most optical media).
 *
 * Supports the Primary Volume Descriptor, the Joliet supplementary descriptor
 * for real unicode names, and the usual ".ISO;1" version suffix.
 */
internal class IsoArchiveReader : ArchiveReader {

    private data class DirectoryRecord(
        val extent: Long,
        val dataLength: Long,
        val isDirectory: Boolean,
        val identifier: String
    )

    private data class VolumeDescriptor(
        val logicalBlockSize: Int,
        val root: DirectoryRecord
    )

    private data class IsoMember(
        val path: String,
        val size: Long,
        val isDirectory: Boolean,
        val dataOffset: Long
    )

    override fun inspect(source: File, password: String?): ArchiveManifest {
        detectUnsupportedLayout(source)
        val members = readMembers(source)
        val entries = members.map { member ->
            ArchiveEntry(
                path = member.path,
                size = member.size,
                compressedSize = 0,
                isDirectory = member.isDirectory
            )
        }
        return ArchiveManifest(format = ArchiveFormat.ISO, source = source, entries = entries)
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
        detectUnsupportedLayout(source)
        val started = System.currentTimeMillis()
        val destination = request.destination
        if (!destination.exists()) destination.mkdirs()

        val members = readMembers(source)
        val sink = ExtractionSink(
            destination = destination,
            totalBytes = members.filter { !it.isDirectory }.sumOf { it.size },
            listener = request.listener,
            overwrite = request.overwrite
        )

        try {
            RandomAccessFile(source, "r").use { raf ->
                val include = request.include
                for (member in members) {
                    if (include != null && !include(member.path)) continue
                    if (member.isDirectory) {
                        sink.writeDirectory(member.path)
                        continue
                    }
                    sink.writeFile(member.path, member.size) {
                        raf.seek(member.dataOffset)
                        object : java.io.InputStream() {
                            private var remaining = member.size
                            override fun read(): Int {
                                if (remaining <= 0) return -1
                                val value = raf.read()
                                if (value < 0) return -1
                                remaining--
                                return value
                            }

                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                if (remaining <= 0) return -1
                                val toRead = minOf(len.toLong(), remaining).toInt()
                                val read = raf.read(b, off, toRead)
                                if (read <= 0) return -1
                                remaining -= read
                                return read
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.ISO, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = ArchiveFormat.ISO,
            destination = destination,
            extractedFiles = sink.filesWritten,
            extractedDirectories = sink.directoriesWritten,
            extractedBytes = sink.bytesWritten,
            durationMs = System.currentTimeMillis() - started,
            verified = true,
            rejectedPaths = sink.rejectedPaths.toList()
        )
    }

    override fun verify(
        source: File,
        password: String?,
        listener: ArchiveProgressListener?
    ): ArchiveIntegrityReport = ArchiveVerifier.verify(ArchiveFormat.ISO, listener) {
        readMembers(source).filter { !it.isDirectory }.map { member ->
            ArchiveVerifier.Member(
                name = member.path,
                size = member.size,
                open = {
                    RandomAccessFile(source, "r").use { raf ->
                        raf.seek(member.dataOffset)
                        object : java.io.InputStream() {
                            private var remaining = member.size
                            override fun read(): Int {
                                if (remaining <= 0) return -1
                                val value = raf.read()
                                if (value < 0) return -1
                                remaining--
                                return value
                            }

                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                if (remaining <= 0) return -1
                                val toRead = minOf(len.toLong(), remaining).toInt()
                                val read = raf.read(b, off, toRead)
                                if (read <= 0) return -1
                                remaining -= read
                                return read
                            }
                        }
                    }
                }
            )
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /**
     * Detects the commercial UDF layout used by "EZB UltraISO" images.
     *
     * Those images carry a UDF 2.50 volume structure with the OSTA Compressed
     * Unicode character set and a non standard directory layout. Reading them
     * with the documented offsets would produce a wrong file tree, so the engine
     * refuses them with an explicit message instead of extracting garbage.
     */
    private fun detectUnsupportedLayout(source: File) {
        val head = try {
            val buffer = ByteArray(64 * 1024)
            source.inputStream().use { it.read(buffer) }
            buffer
        } catch (_: Exception) {
            return
        }

        val text = String(head, Charsets.ISO_8859_1)
        val isCommercial = text.contains("EZB UltraISO") ||
            text.contains("OSTA Compressed Unicode") ||
            text.contains("UDF LV Info")

        if (isCommercial) {
            throw ArchiveException.Corrupted(
                "This ISO uses the commercial UDF 2.50 layout (EZB UltraISO). " +
                    "It is not extracted yet because a partial reader would produce " +
                    "a wrong file tree."
            )
        }
    }

    private fun readMembers(source: File): List<IsoMember> {
        RandomAccessFile(source, "r").use { raf ->
            val primary = readDescriptor(raf, 16) ?: throw ArchiveException.Corrupted("missing ISO volume descriptor")
            val joliet = findJolietDescriptor(raf)

            val descriptor = joliet ?: primary
            val members = mutableListOf<IsoMember>()
            traverse(raf, descriptor, descriptor.root.extent, descriptor.root.dataLength, "", members, joliet != null)
            return members
        }
    }

    private fun readDescriptor(raf: RandomAccessFile, sector: Int): VolumeDescriptor? {
        if (raf.length() < (sector.toLong() + 1) * SECTOR_SIZE) return null
        val buffer = ByteArray(SECTOR_SIZE)
        raf.seek(sector.toLong() * SECTOR_SIZE)
        raf.readFully(buffer)
        if (buffer.size < 224) return null
        val signature = String(buffer, 1, 5, Charsets.US_ASCII)
        if (signature != "CD001") return null
        val type = buffer[0].toInt() and 0xFF
        if (type != 1 && type != 2) return null

        val logicalBlockSize = le16(buffer, 128)
        val root = parseDirectoryRecord(buffer, 190, 34) ?: return null
        return VolumeDescriptor(if (logicalBlockSize > 0) logicalBlockSize else SECTOR_SIZE, root)
    }

    /**
     * Locates the Joliet supplementary descriptor, which carries real unicode names.
     */
    private fun findJolietDescriptor(raf: RandomAccessFile): VolumeDescriptor? {
        var sector = 16
        while (sector < 64) {
            if (raf.length() < (sector.toLong() + 1) * SECTOR_SIZE) break
            val buffer = ByteArray(SECTOR_SIZE)
            raf.seek(sector.toLong() * SECTOR_SIZE)
            raf.readFully(buffer)
            if (buffer.size < 224) break
            val signature = String(buffer, 1, 5, Charsets.US_ASCII)
            if (signature != "CD001") break
            val type = buffer[0].toInt() and 0xFF
            if (type == 255) break

            if (type == 2) {
                // Joliet escape sequences live at offset 88 of the descriptor.
                val escape = buffer.copyOfRange(88, 91)
                if (escape.size == 3 &&
                    escape[0] == 0x25.toByte() && escape[1] == 0x2F.toByte() && escape[2] == 0xE4.toByte()
                ) {
                    val logicalBlockSize = le16(buffer, 128)
                    val root = parseDirectoryRecord(buffer, 190, 34) ?: return null
                    return VolumeDescriptor(if (logicalBlockSize > 0) logicalBlockSize else SECTOR_SIZE, root)
                }
            }
            sector++
        }
        return null
    }

    private fun traverse(
        raf: RandomAccessFile,
        descriptor: VolumeDescriptor,
        extent: Long,
        length: Long,
        prefix: String,
        out: MutableList<IsoMember>,
        joliet: Boolean,
        depth: Int = 0,
        visited: MutableSet<Long> = HashSet()
    ) {
        if (length <= 0) return
        // ROBUSTNESS: a malformed image can point a directory at itself. The depth
        // limit and the visited set stop the walk instead of looping forever.
        if (depth > MAX_DEPTH) return
        if (!visited.add(extent)) return
        if (visited.size > MAX_VISITED_EXTENTS) return

        val blockSize = descriptor.logicalBlockSize
        val totalBytes = length
        var read = 0L
        val buffer = ByteArray(blockSize)

        while (read < totalBytes) {
            val chunk = minOf(blockSize.toLong(), totalBytes - read).toInt()
            raf.seek(extent * blockSize + read)
            val readNow = raf.read(buffer, 0, chunk)
            if (readNow <= 0) break
            read += readNow

            var offset = 0
            while (offset < readNow) {
                val recordLength = buffer[offset].toInt() and 0xFF
                if (recordLength == 0) {
                    // Records never straddle a logical block: jump to the next one.
                    offset = ((offset / blockSize) + 1) * blockSize
                    continue
                }
                if (offset + recordLength > readNow) break

                val record = parseDirectoryRecord(buffer, offset, recordLength)
                offset += recordLength

                if (record == null) continue
                if (record.identifier == "\u0000" || record.identifier == "\u0001") continue

                val path = if (prefix.isEmpty()) record.identifier else "$prefix/${record.identifier}"
                if (record.isDirectory) {
                    out += IsoMember(path, 0L, true, record.extent * blockSize)
                    traverse(
                        raf, descriptor, record.extent, record.dataLength, path, out,
                        joliet, depth + 1, visited
                    )
                } else {
                    out += IsoMember(path, record.dataLength, false, record.extent * blockSize)
                }
            }
        }
    }

    /**
     * Parses one directory record. [joliet] switches the identifier decoding to
     * UTF-16 big endian.
     */
    private fun parseDirectoryRecord(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): DirectoryRecord? {
        if (offset + 33 > buffer.size) return null
        val extent = le32(buffer, offset + 2)
        val dataLength = le32(buffer, offset + 10)
        val flags = buffer[offset + 25].toInt() and 0xFF
        val isDirectory = (flags and 0x02) != 0
        val identifierLength = buffer[offset + 32].toInt() and 0xFF
        if (identifierLength <= 0) return null
        // A malformed record that claims a longer identifier than its own length
        // must be rejected instead of reading into the next record.
        if (33 + identifierLength > length) return null
        val start = offset + 33
        if (start + identifierLength > buffer.size) return null

        val raw = buffer.copyOfRange(start, start + identifierLength)
        val identifier = if (raw.size >= 2 && raw[0] == 0x00.toByte() && raw[1] == 0x00.toByte()) {
            // Joliet names are stored as UTF-16 big endian.
            decodeUtf16Be(raw)
        } else {
            decodeAscii(raw)
        }

        return DirectoryRecord(
            extent = extent,
            dataLength = dataLength,
            isDirectory = isDirectory,
            identifier = identifier
        )
    }

    private fun decodeAscii(raw: ByteArray): String {
        var text = String(raw, Charsets.US_ASCII)
        // Drop the ";1" version suffix and the trailing separator.
        val semicolon = text.indexOf(';')
        if (semicolon >= 0) text = text.substring(0, semicolon)
        if (text.endsWith(".")) text = text.dropLast(1)
        return text.trim()
    }

    private fun decodeUtf16Be(raw: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < raw.size) {
            val code = ((raw[i].toInt() and 0xFF) shl 8) or (raw[i + 1].toInt() and 0xFF)
            sb.append(code.toChar())
            i += 2
        }
        return sb.toString().trim()
    }

    private fun le16(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(buffer: ByteArray, offset: Int): Long =
        (buffer[offset].toLong() and 0xFF) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 8) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 3].toLong() and 0xFF) shl 24)

    companion object {
        private const val SECTOR_SIZE = 2048
        private const val MAX_DEPTH = 32
        private const val MAX_VISITED_EXTENTS = 20_000
    }
}
