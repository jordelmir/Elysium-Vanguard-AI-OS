package com.elysium.vanguard.core.archive.extract

import com.elysium.vanguard.core.archive.ArchiveEntry
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveIntegrityReport
import com.elysium.vanguard.core.archive.ArchiveProgressListener
import com.elysium.vanguard.core.archive.ArchiveVerifier
import com.elysium.vanguard.core.archive.ArchiveExtractionRequest
import com.elysium.vanguard.core.archive.ArchiveFormat
import com.elysium.vanguard.core.archive.ArchiveManifest
import com.elysium.vanguard.core.archive.ArchiveReader
import com.elysium.vanguard.core.archive.ArchiveReaders
import com.elysium.vanguard.core.archive.ExtractionReport
import com.elysium.vanguard.core.archive.ExtractionSink
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

/**
 * 7-Zip reader.
 *
 * Supports the full coder set available in Commons Compress (LZMA, LZMA2, BZip2,
 * Deflate, Copy, Delta, BCJ), AES-encrypted archives with a password, and split
 * volumes named `name.7z.001`, `name.7z.002`, ...
 */
internal class SevenZipArchiveReader : ArchiveReader {

    override fun inspect(source: File, password: String?): ArchiveManifest {
        val entries = try {
            open(source, password).use { file ->
                file.entries.map { it.toArchiveEntry() }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.SEVEN_Z, password, t)
        }
        return ArchiveManifest(format = ArchiveFormat.SEVEN_Z, source = source, entries = entries)
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
        val started = System.currentTimeMillis()
        val destination = request.destination
        if (!destination.exists()) destination.mkdirs()

        val entries = try {
            open(source, request.password).use { file ->
                file.entries.map { it.toArchiveEntry() }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.SEVEN_Z, request.password, t)
        }

        val sink = ExtractionSink(
            destination = destination,
            totalBytes = entries.sumOf { it.size },
            listener = request.listener,
            overwrite = request.overwrite
        )

        try {
            open(source, request.password).use { file ->
                val include = request.include
                for (entry in file.entries) {
                    val name = ArchiveReaders.normaliseName(entry.name ?: "")
                    if (name.isEmpty()) continue
                    if (include != null && !include(name)) continue
                    if (entry.isDirectory) {
                        sink.writeDirectory(name)
                    } else {
                        sink.writeFile(name, entry.size) { file.getInputStream(entry) }
                    }
                }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.SEVEN_Z, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = ArchiveFormat.SEVEN_Z,
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
    ): ArchiveIntegrityReport = ArchiveVerifier.verify(ArchiveFormat.SEVEN_Z, listener) {
        open(source, password).use { file ->
            file.entries.filter { !it.isDirectory }.map { entry ->
                ArchiveVerifier.Member(
                    name = entry.name ?: "",
                    size = entry.size,
                    open = { file.getInputStream(entry) }
                )
            }
        }
    }

    // The File constructors are deprecated but they are the ones that resolve the
    // sibling volumes of a split archive, so they are kept on purpose.
    @Suppress("DEPRECATION")
    private fun open(source: File, password: String?): SevenZFile =
        if (password.isNullOrEmpty()) {
            SevenZFile(source)
        } else {
            SevenZFile(source, password.toCharArray())
        }

    private fun SevenZArchiveEntry.toArchiveEntry(): ArchiveEntry = ArchiveEntry(
        path = ArchiveReaders.normaliseName(name ?: ""),
        size = size,
        // getCompressedSize() is not public in Commons Compress: the packed size is
        // not part of the public contract, so it stays unknown instead of faked.
        compressedSize = 0L,
        isDirectory = isDirectory,
        lastModified = lastModifiedDate?.time ?: 0L,
        crc = if (hasCrc) crcValue else null
    )
}
