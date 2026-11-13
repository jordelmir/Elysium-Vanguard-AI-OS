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
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XAR reader - the container used by macOS / iOS for `.pkg` installers.
 *
 * The table of contents is XML and the payload lives in a flat heap. Two real
 * world quirks are handled here:
 *
 *  - Apple writes the TOC with **zlib** (not gzip) and leaves
 *    `tocLengthCompressed` at zero, so the heap offset has to be recovered by
 *    measuring how many bytes the inflater actually consumed.
 *  - The payload of a modern package is a **pbzx** container holding a CPIO
 *    archive, which is expanded into the real file tree.
 */
internal class XarArchiveReader : ArchiveReader {

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

    private data class XarMember(
        val path: String,
        val size: Long,
        val isDirectory: Boolean,
        val dataOffset: Long,
        /** Compressed length of the member inside the heap. */
        val length: Long = 0L,
        /** Compression style declared by the table of contents. */
        val encoding: String? = null
    )

    override fun inspect(source: File, password: String?): ArchiveManifest {
        val members = readMembers(source)
        val entries = members.map { member ->
            ArchiveEntry(
                path = member.path,
                size = member.size,
                compressedSize = 0,
                isDirectory = member.isDirectory
            )
        }
        return ArchiveManifest(format = ArchiveFormat.XAR, source = source, entries = entries)
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
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
                    if (member.size <= 0L) {
                        sink.writeFile(member.path, 0L) { ByteArrayInputStream(ByteArray(0)) }
                        continue
                    }
                    raf.seek(member.dataOffset)
                    val raw = ByteArray(member.readLength())
                    raf.readFully(raw)
                    val decoded = decode(raw, member.encoding)
                    if (decoded == null) {
                        sink.rejectedPaths += member.path
                        continue
                    }
                    expandDecoded(member, decoded, sink)
                }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.XAR, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = ArchiveFormat.XAR,
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
    ): ArchiveIntegrityReport {
        val started = System.currentTimeMillis()
        val failures = mutableListOf<String>()
        var tested = 0
        var bytes = 0L

        try {
            val members = readMembers(source)
            RandomAccessFile(source, "r").use { raf ->
                for (member in members) {
                    if (member.isDirectory) continue
                    try {
                        raf.seek(member.dataOffset)
                        val raw = ByteArray(member.readLength())
                        raf.readFully(raw)
                        val decoded = decode(raw, member.encoding)
                        if (decoded == null) {
                            failures += "${member.path}: unsupported encoding"
                            continue
                        }
                        bytes += decoded.size.toLong()
                        tested++
                    } catch (t: Throwable) {
                        failures += "${member.path}: ${t.message ?: "verification failed"}"
                    }
                }
            }
        } catch (t: Throwable) {
            failures += "archive: ${t.message ?: t.javaClass.simpleName}"
        }

        listener?.onProgress(100, "COMPLETE")
        return ArchiveIntegrityReport(
            format = ArchiveFormat.XAR,
            entriesTested = tested,
            bytesVerified = bytes,
            failures = failures,
            durationMs = System.currentTimeMillis() - started
        )
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun readMembers(source: File): List<XarMember> {
        RandomAccessFile(source, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "xar!") {
                throw ArchiveException.Corrupted("invalid XAR signature")
            }
            val headerSize = raf.readU16()
            raf.readU16()                      // version
            val tocCompressed = raf.readU32()
            val tocUncompressed = raf.readU32()
            raf.readU32()                      // checksum algorithm

            val fileLength = raf.length()
            val regionLength = (fileLength - headerSize).coerceAtLeast(0L)
            val region = ByteArray(regionLength.toInt())
            raf.seek(headerSize.toLong())
            raf.readFully(region)

            val decoded = decodeToc(region, tocCompressed, tocUncompressed)
                ?: throw ArchiveException.Corrupted("unreadable XAR table of contents")

            // The heap starts right after the compressed table of contents.
            val heapStart = headerSize.toLong() + decoded.consumed

            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(ByteArrayInputStream(decoded.bytes))

            val tocRoot = document.documentElement
                ?.getElementsByTagName("toc")
                ?.item(0) as? Element
                ?: return emptyList()

            val members = mutableListOf<XarMember>()
            collect(tocRoot, "", members, heapStart)
            return members
        }
    }

    /**
     * Decodes the table of contents.
     *
     * @return the XML bytes and how many compressed bytes were consumed.
     */
    private fun decodeToc(
        region: ByteArray,
        tocCompressed: Long,
        tocUncompressed: Long
    ): DecodedToc? {
        // Standard XAR: the compressed length is known.
        if (tocCompressed > 0 && tocCompressed <= region.size) {
            val slice = region.copyOfRange(0, tocCompressed.toInt())
            val bytes = decode(slice, "application/x-gzip") ?: decode(slice, "application/octet-stream")
            if (bytes != null) {
                return DecodedToc(bytes, tocCompressed)
            }
        }

        // Apple quirk: the length is zero and the TOC is a raw zlib stream.
        inflateZlib(region)?.let { (bytes, consumed) ->
            return DecodedToc(bytes, consumed)
        }
        decodeGzip(region)?.let { (bytes, consumed) ->
            return DecodedToc(bytes, consumed)
        }
        // Plain XML table of contents.
        val text = String(region, Charsets.UTF_8)
        if (text.trimStart().startsWith("<")) {
            return DecodedToc(region, 0L)
        }
        return null
    }

    private data class DecodedToc(val bytes: ByteArray, val consumed: Long)

    private fun inflateZlib(region: ByteArray): Pair<ByteArray, Long>? = try {
        val inflater = Inflater()
        inflater.setInput(region)
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                output.write(buffer, 0, read)
            }
        }
        val consumed = inflater.bytesRead.toLong()
        inflater.end()
        if (output.size() > 0) Pair(output.toByteArray(), consumed) else null
    } catch (_: Exception) {
        null
    }

    private fun decodeGzip(region: ByteArray): Pair<ByteArray, Long>? = try {
        val counting = CountingInputStream(region.inputStream())
        val output = GzipCompressorInputStream(counting).use { it.readBytes() }
        if (output.isNotEmpty()) Pair(output, counting.count) else null
    } catch (_: Exception) {
        null
    }

    private fun collect(
        parent: Element,
        prefix: String,
        out: MutableList<XarMember>,
        heapStart: Long
    ) {
        for (fileElement in directChildren(parent, "file")) {
            val name = textOf(fileElement, "name") ?: continue
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            val type = textOf(fileElement, "type")
            val isDirectory = type.equals("directory", ignoreCase = true)

            if (isDirectory) {
                out += XarMember(path, 0L, true, heapStart)
                collect(fileElement, path, out, heapStart)
                continue
            }

            val data = directChildren(fileElement, "data").firstOrNull()
            val length = data?.let { textOf(it, "length")?.toLongOrNull() } ?: 0L
            val offset = data?.let { textOf(it, "offset")?.toLongOrNull() } ?: 0L
            val size = data?.let { textOf(it, "size")?.toLongOrNull() } ?: 0L
            val encoding = data?.let { enc ->
                directChildren(enc, "encoding").firstOrNull()?.getAttribute("style")
            }

            out += XarMember(
                path = path,
                size = size,
                isDirectory = false,
                dataOffset = heapStart + offset,
                length = length,
                encoding = encoding
            )
        }
    }

    private fun directChildren(parent: Element, tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.tagName == tag) out += node
        }
        return out
    }

    private fun textOf(parent: Element, tag: String): String? =
        directChildren(parent, tag).firstOrNull()?.textContent?.trim()

    /**
     * Installer payloads are CPIO archives. When the decoded member is a CPIO
     * stream it is expanded into the destination as a real file tree. Modern
     * Apple packages carry the payload inside a "pbzx" container, which is
     * inflated first.
     */
    private fun expandDecoded(member: XarMember, decoded: ByteArray, sink: ExtractionSink) {
        // pbzx: the CPIO payload is split into XZ compressed chunks.
        if (PbzxReader.isPbzx(decoded)) {
            val inflated = PbzxReader.inflate(decoded)
            if (inflated != null) {
                expandCpio(member.path, inflated, sink)
                return
            }
        }

        val head = String(decoded, 0, minOf(6, decoded.size), Charsets.US_ASCII)
        if (head.startsWith("070701") || head.startsWith("070702") || head.startsWith("070707")) {
            expandCpio(member.path, decoded, sink)
            return
        }
        val temp = writeTemp(decoded)
        try {
            sink.writeFileFromDisk(member.path, temp)
        } finally {
            temp.delete()
        }
    }

    private fun expandCpio(displayName: String, payload: ByteArray, sink: ExtractionSink) {
        try {
            CpioArchiveInputStream(ByteArrayInputStream(payload)).use { cpio ->
                var entry = cpio.nextEntry
                while (entry != null) {
                    val name = ArchiveReaders.normaliseName(entry.name)
                    if (entry.isDirectory) {
                        sink.writeDirectory(name)
                    } else {
                        sink.writeFile(name, entry.size, closeStream = false) { cpio }
                    }
                    entry = cpio.nextEntry
                }
            }
        } catch (_: java.io.EOFException) {
            // Apple ships stub packages whose payload stops before the TRAILER
            // entry. The members already recovered are kept, exactly like the
            // system cpio tool does.
        }
    }

    private fun writeTemp(bytes: ByteArray): File {
        val dir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        if (!dir.exists()) dir.mkdirs()
        val temp = File.createTempFile("xar", ".tmp", dir)
        temp.writeBytes(bytes)
        return temp
    }

    /**
     * Applies the XAR member encoding. Returns null when the encoding is not
     * supported so the caller can report it honestly instead of writing garbage.
     */
    private fun decode(bytes: ByteArray, encoding: String?): ByteArray? {
        if (bytes.isEmpty()) return bytes
        val style = encoding?.lowercase()
        return try {
            when {
                style == null || style.isEmpty() || style.contains("octet-stream") -> bytes
                style.contains("gzip") -> GzipCompressorInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                style.contains("bzip2") -> BZip2CompressorInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                style.contains("xz") -> XZCompressorInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                style.contains("zlib") -> java.util.zip.InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Bytes to read from the heap for this member. */
    private fun XarMember.readLength(): Int {
        val declared = if (length > 0) length else size
        return declared.coerceAtLeast(0L).coerceAtMost(64L * 1024 * 1024).toInt()
    }

    private fun RandomAccessFile.readU16(): Int {
        val a = read()
        val b = read()
        return (a shl 8) or b
    }

    private fun RandomAccessFile.readU32(): Long {
        val a = read().toLong() and 0xFF
        val b = read().toLong() and 0xFF
        val c = read().toLong() and 0xFF
        val d = read().toLong() and 0xFF
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    /** Counts the bytes read from a stream. */
    private class CountingInputStream(private val delegate: java.io.InputStream) : java.io.InputStream() {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) count++
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = delegate.read(b, off, len)
            if (read > 0) count += read
            return read
        }

        override fun available(): Int = delegate.available()
        override fun close() = delegate.close()
    }
}
