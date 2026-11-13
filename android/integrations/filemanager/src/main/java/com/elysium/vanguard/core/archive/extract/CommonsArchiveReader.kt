package com.elysium.vanguard.core.archive.extract

import com.elysium.vanguard.core.archive.ArchiveEntry
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveIntegrityReport
import com.elysium.vanguard.core.archive.ArchiveProgressListener
import com.elysium.vanguard.core.archive.ArchiveExtractionRequest
import com.elysium.vanguard.core.archive.ArchiveFormat
import com.elysium.vanguard.core.archive.ArchiveManifest
import com.elysium.vanguard.core.archive.ArchiveReader
import com.elysium.vanguard.core.archive.ArchiveReaders
import com.elysium.vanguard.core.archive.ExtractionReport
import com.elysium.vanguard.core.archive.ExtractionSink
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * Reader for the stream and tape formats handled by Apache Commons Compress:
 * TAR, GZIP, BZIP2, XZ, Zstandard, CPIO and AR/DEB.
 *
 * A compressed stream that actually contains a TAR (the .tgz / .tar.gz layout)
 * is transparently expanded as a TAR so the user gets the real tree.
 */
internal class CommonsArchiveReader : ArchiveReader {

    override fun inspect(source: File, password: String?): ArchiveManifest {
        val format = ArchiveFormat.detect(source)
        val entries = readEntries(source, format)
        return ArchiveManifest(format = format, source = source, entries = entries)
    }

    /**
     * Reads every member end to end so the library validates its checksum.
     * Nothing is written to disk: this is the desktop "test archive" action.
     */
    override fun verify(
        source: File,
        password: String?,
        listener: ArchiveProgressListener?
    ): ArchiveIntegrityReport {
        val format = ArchiveFormat.detect(source)
        val started = System.currentTimeMillis()
        val failures = mutableListOf<String>()
        var tested = 0
        var bytes = 0L

        try {
            when (format) {
                ArchiveFormat.TAR -> TarArchiveInputStream(source.inputStream().buffered()).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            try {
                                bytes += drain(tar)
                                tested++
                            } catch (t: Throwable) {
                                failures += "${entry.name}: ${t.message ?: "checksum verification failed"}"
                            }
                        }
                        entry = tar.nextEntry
                    }
                }

                ArchiveFormat.CPIO -> CpioArchiveInputStream(source.inputStream().buffered()).use { cpio ->
                    var entry = cpio.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            try {
                                bytes += drain(cpio)
                                tested++
                            } catch (t: Throwable) {
                                failures += "${entry.name}: ${t.message ?: "checksum verification failed"}"
                            }
                        }
                        entry = cpio.nextEntry
                    }
                }

                ArchiveFormat.AR -> ArArchiveInputStream(source.inputStream().buffered()).use { ar ->
                    var entry = ar.nextEntry
                    while (entry != null) {
                        try {
                            bytes += drain(ar)
                            tested++
                        } catch (t: Throwable) {
                            failures += "${entry.name}: ${t.message ?: "checksum verification failed"}"
                        }
                        entry = ar.nextEntry
                    }
                }

                ArchiveFormat.ARJ -> ArjArchiveInputStream(source.inputStream().buffered()).use { arj ->
                    var entry = arj.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            try {
                                bytes += drain(arj)
                                tested++
                            } catch (t: Throwable) {
                                failures += "${entry.name}: ${t.message ?: "checksum verification failed"}"
                            }
                        }
                        entry = arj.nextEntry
                    }
                }

                ArchiveFormat.DUMP -> DumpArchiveInputStream(source.inputStream().buffered()).use { dump ->
                    var entry = dump.nextEntry
                    while (entry != null) {
                        try {
                            bytes += drain(dump)
                            tested++
                        } catch (t: Throwable) {
                            failures += "${entry.name}: ${t.message ?: "checksum verification failed"}"
                        }
                        entry = dump.nextEntry
                    }
                }

                ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD,
                ArchiveFormat.UNIX_Z, ArchiveFormat.LZMA_ALONE ->
                    openDecompressed(source, format).use { stream ->
                        // The compressed stream validates its own CRC while reading.
                        bytes += drain(stream)
                        tested = 1
                    }

                else -> failures += "format not supported for verification"
            }
        } catch (t: Throwable) {
            failures += "archive: ${t.message ?: t.javaClass.simpleName}"
        }

        listener?.onProgress(100, "COMPLETE")
        return ArchiveIntegrityReport(
            format = format,
            entriesTested = tested,
            bytesVerified = bytes,
            failures = failures,
            durationMs = System.currentTimeMillis() - started
        )
    }

    private fun drain(input: java.io.InputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
        }
        return total
    }

    /**
     * Reads the catalogue of the format. The reader is only ever handed the
     * formats listed here, so the `else` branch is a defensive guard.
     */
    @Suppress("REDUNDANT_ELSE")
    private fun readEntries(source: File, format: ArchiveFormat): List<ArchiveEntry> = when (format) {
        ArchiveFormat.TAR -> readTar(source)
        ArchiveFormat.CPIO -> readCpio(source)
        ArchiveFormat.AR -> readAr(source)
        ArchiveFormat.ARJ -> readArj(source)
        ArchiveFormat.DUMP -> readDump(source)
        ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD,
        ArchiveFormat.UNIX_Z, ArchiveFormat.LZMA_ALONE ->
            inspectCompressedSingleStream(source, format)
        else -> throw ArchiveException.Unsupported(format)
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
        val started = System.currentTimeMillis()
        val format = ArchiveFormat.detect(source)
        val destination = request.destination
        if (!destination.exists()) destination.mkdirs()

        val manifestEntries = try {
            readEntries(source, format)
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(format, request.password, t)
        }

        val sink = ExtractionSink(
            destination = destination,
            totalBytes = manifestEntries.sumOf { it.size },
            listener = request.listener,
            overwrite = request.overwrite
        )

        try {
            extractEntries(source, format, sink, request.include)
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(format, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = format,
            destination = destination,
            extractedFiles = sink.filesWritten,
            extractedDirectories = sink.directoriesWritten,
            extractedBytes = sink.bytesWritten,
            durationMs = System.currentTimeMillis() - started,
            verified = true,
            rejectedPaths = sink.rejectedPaths.toList()
        )
    }

    /**
     * Writes the members of the format into the destination.
     */
    @Suppress("REDUNDANT_ELSE")
    private fun extractEntries(
        source: File,
        format: ArchiveFormat,
        sink: ExtractionSink,
        include: ((String) -> Boolean)?
    ) {
        when (format) {
            ArchiveFormat.TAR -> extractTar(source, sink, include)
            ArchiveFormat.CPIO -> extractCpio(source, sink, include)
            ArchiveFormat.AR -> extractAr(source, sink, include)
            ArchiveFormat.ARJ -> extractArj(source, sink, include)
            ArchiveFormat.DUMP -> extractDump(source, sink, include)
            ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD,
            ArchiveFormat.UNIX_Z, ArchiveFormat.LZMA_ALONE ->
                extractCompressed(source, format, sink, include)
            else -> throw ArchiveException.Unsupported(format)
        }
    }

    // ── TAR ──────────────────────────────────────────────────────────────────

    private fun readTar(source: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        TarArchiveInputStream(source.inputStream().buffered()).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                entries += ArchiveEntry(
                    path = ArchiveReaders.normaliseName(entry.name),
                    size = entry.size,
                    compressedSize = 0,
                    isDirectory = entry.isDirectory,
                    lastModified = entry.lastModifiedDate?.time ?: 0L
                )
                entry = tar.nextEntry
            }
        }
        return entries
    }

    private fun extractTar(source: File, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        TarArchiveInputStream(source.inputStream().buffered()).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val name = ArchiveReaders.normaliseName(entry.name)
                if (include != null && !include(name)) {
                    entry = tar.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    sink.writeDirectory(name)
                } else {
                    sink.writeFile(name, entry.size, closeStream = false) { tar }
                }
                entry = tar.nextEntry
            }
        }
    }

    // ── CPIO ─────────────────────────────────────────────────────────────────

    private fun readCpio(source: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        CpioArchiveInputStream(source.inputStream().buffered()).use { cpio ->
            var entry = cpio.nextEntry
            while (entry != null) {
                entries += ArchiveEntry(
                    path = ArchiveReaders.normaliseName(entry.name),
                    size = entry.size,
                    compressedSize = 0,
                    isDirectory = entry.isDirectory,
                    lastModified = entry.lastModifiedDate?.time ?: 0L
                )
                entry = cpio.nextEntry
            }
        }
        return entries
    }

    private fun extractCpio(source: File, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        try {
            CpioArchiveInputStream(source.inputStream().buffered()).use { cpio ->
                var entry = cpio.nextEntry
                while (entry != null) {
                    val name = ArchiveReaders.normaliseName(entry.name)
                    if (include == null || include(name)) {
                        if (entry.isDirectory) {
                            sink.writeDirectory(name)
                        } else {
                            sink.writeFile(name, entry.size, closeStream = false) { cpio }
                        }
                    }
                    entry = cpio.nextEntry
                }
            }
        } catch (_: java.io.EOFException) {
            // A truncated CPIO still yields the members recovered so far, the same
            // way the system cpio tool behaves.
        }
    }

    // ── AR / DEB ────────────────────────────────────────────────────────────

    private fun readAr(source: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        ArArchiveInputStream(source.inputStream().buffered()).use { ar ->
            var entry = ar.nextEntry
            while (entry != null) {
                entries += ArchiveEntry(
                    path = ArchiveReaders.normaliseName(entry.name),
                    size = entry.size,
                    compressedSize = 0,
                    isDirectory = false,
                    lastModified = entry.lastModifiedDate?.time ?: 0L
                )
                entry = ar.nextEntry
            }
        }
        return entries
    }

    private fun extractAr(source: File, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        ArArchiveInputStream(source.inputStream().buffered()).use { ar ->
            var entry = ar.nextEntry
            while (entry != null) {
                val name = ArchiveReaders.normaliseName(entry.name)
                if (include != null && !include(name)) {
                    entry = ar.nextEntry
                    continue
                }
                sink.writeFile(name, entry.size, closeStream = false) { ar }
                entry = ar.nextEntry
            }
        }
    }

    // ── ARJ ─────────────────────────────────────────────────────────────────

    private fun readArj(source: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        ArjArchiveInputStream(source.inputStream().buffered()).use { arj ->
            var entry = arj.nextEntry
            while (entry != null) {
                entries += ArchiveEntry(
                    path = ArchiveReaders.normaliseName(entry.name ?: ""),
                    size = entry.size,
                    compressedSize = 0,
                    isDirectory = entry.isDirectory,
                    lastModified = entry.lastModifiedDate?.time ?: 0L
                )
                entry = arj.nextEntry
            }
        }
        return entries
    }

    private fun extractArj(source: File, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        ArjArchiveInputStream(source.inputStream().buffered()).use { arj ->
            var entry = arj.nextEntry
            while (entry != null) {
                val name = ArchiveReaders.normaliseName(entry.name ?: "")
                if (include == null || include(name)) {
                    if (entry.isDirectory) {
                        sink.writeDirectory(name)
                    } else {
                        sink.writeFile(name, entry.size, closeStream = false) { arj }
                    }
                }
                entry = arj.nextEntry
            }
        }
    }

    // ── DUMP ────────────────────────────────────────────────────────────────

    private fun readDump(source: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        DumpArchiveInputStream(source.inputStream().buffered()).use { dump ->
            var entry = dump.nextEntry
            while (entry != null) {
                entries += ArchiveEntry(
                    path = ArchiveReaders.normaliseName(entry.name ?: ""),
                    size = entry.size,
                    compressedSize = 0,
                    isDirectory = entry.isDirectory,
                    lastModified = entry.lastModifiedDate?.time ?: 0L
                )
                entry = dump.nextEntry
            }
        }
        return entries
    }

    private fun extractDump(source: File, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        DumpArchiveInputStream(source.inputStream().buffered()).use { dump ->
            var entry = dump.nextEntry
            while (entry != null) {
                val name = ArchiveReaders.normaliseName(entry.name ?: "")
                if (include == null || include(name)) {
                    if (entry.isDirectory) {
                        sink.writeDirectory(name)
                    } else {
                        sink.writeFile(name, entry.size, closeStream = false) { dump }
                    }
                }
                entry = dump.nextEntry
            }
        }
    }

    // ── Compressed single streams (gz / bz2 / xz / zst / .Z / lzma / br) ────

    private fun inspectCompressedSingleStream(source: File, format: ArchiveFormat): List<ArchiveEntry> {
        openDecompressed(source, format).use { stream ->
            val pushback = PushbackInputStream(stream, TAR_HEADER_SIZE)
            val header = ByteArray(TAR_HEADER_SIZE)
            val read = pushback.read(header)
            if (read > 0) pushback.unread(header, 0, read)
            val isTar = read >= 262 && String(header, 257, 5, Charsets.US_ASCII) == "ustar"
            if (isTar) {
                pushback.use { tar ->
                    val entries = mutableListOf<ArchiveEntry>()
                    TarArchiveInputStream(tar).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            entries += ArchiveEntry(
                                path = ArchiveReaders.normaliseName(entry.name),
                                size = entry.size,
                                compressedSize = 0,
                                isDirectory = entry.isDirectory,
                                lastModified = entry.lastModifiedDate?.time ?: 0L
                            )
                            entry = tarIn.nextEntry
                        }
                    }
                    return entries
                }
            }
            val name = ArchiveReaders.singleMemberName(source)
            val size = source.length()
            return listOf(
                ArchiveEntry(
                    path = name,
                    size = size,
                    compressedSize = source.length(),
                    isDirectory = false
                )
            )
        }
    }

    private fun extractCompressed(source: File, format: ArchiveFormat, sink: ExtractionSink, include: ((String) -> Boolean)?) {
        openDecompressed(source, format).use { stream ->
            val pushback = PushbackInputStream(stream, TAR_HEADER_SIZE)
            val header = ByteArray(TAR_HEADER_SIZE)
            val read = pushback.read(header)
            if (read > 0) pushback.unread(header, 0, read)
            val isTar = read >= 262 && String(header, 257, 5, Charsets.US_ASCII) == "ustar"

            if (isTar) {
                TarArchiveInputStream(pushback).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val name = ArchiveReaders.normaliseName(entry.name)
                        if (include == null || include(name)) {
                            if (entry.isDirectory) {
                                sink.writeDirectory(name)
                            } else {
                                sink.writeFile(name, entry.size, closeStream = false) { tar }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            } else {
                val name = ArchiveReaders.singleMemberName(source)
                if (include == null || include(name)) {
                    sink.writeFile(name, source.length()) { pushback }
                }
            }
        }
    }

    private fun openDecompressed(source: File, format: ArchiveFormat): InputStream {
        val raw = source.inputStream().buffered()
        return when (format) {
            ArchiveFormat.GZIP -> GzipCompressorInputStream(raw)
            ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(raw)
            ArchiveFormat.XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.ZSTD -> ZstdCompressorInputStream(raw)
            ArchiveFormat.UNIX_Z -> ZCompressorInputStream(raw)
            ArchiveFormat.LZMA_ALONE -> LZMACompressorInputStream(raw)
            else -> raw
        }
    }

    companion object {
        private const val TAR_HEADER_SIZE = 512
    }
}
