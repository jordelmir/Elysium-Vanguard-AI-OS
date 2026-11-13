package com.elysium.vanguard.core.archive.extract

import com.elysium.vanguard.core.archive.ArchiveEngine
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveIntegrityReport
import com.elysium.vanguard.core.archive.ArchiveProgressListener
import com.elysium.vanguard.core.archive.ArchiveVerifier
import com.elysium.vanguard.core.archive.ArchiveExtractionRequest
import com.elysium.vanguard.core.archive.ArchiveFormat
import com.elysium.vanguard.core.archive.ArchiveManifest
import com.elysium.vanguard.core.archive.ArchiveReader
import com.elysium.vanguard.core.archive.ArchiveReaders
import com.elysium.vanguard.core.archive.ArchiveSafety
import com.elysium.vanguard.core.archive.ArchiveEntry
import com.elysium.vanguard.core.archive.ExtractionReport
import com.elysium.vanguard.core.archive.ExtractionSink
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File

/**
 * ZIP reader backed by zip4j.
 *
 * Gives us the full desktop ZIP feature set: ZIP64, data descriptors, split
 * volumes (.z01), and AES / ZipCrypto encryption with a password.
 */
internal class ZipArchiveReader : ArchiveReader {

    override fun inspect(source: File, password: String?): ArchiveManifest {
        val headers = try {
            open(source, password).use { it.fileHeaders }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.ZIP, password, t)
        }

        val entries = headers.map { header ->
            ArchiveEntry(
                path = ArchiveReaders.normaliseName(header.fileName),
                size = header.uncompressedSize,
                compressedSize = header.compressedSize,
                isDirectory = header.isDirectory,
                lastModified = header.lastModifiedTimeEpoch,
                crc = header.crc,
                encrypted = header.isEncrypted
            )
        }

        return ArchiveManifest(
            format = ArchiveFormat.ZIP,
            source = source,
            entries = entries
        )
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
        val started = System.currentTimeMillis()
        val destination = request.destination
        if (!destination.exists()) destination.mkdirs()

        val headers = try {
            open(source, request.password).use { it.fileHeaders }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.ZIP, request.password, t)
        }

        val manifestEntries = headers.map { header ->
            ArchiveEntry(
                path = ArchiveReaders.normaliseName(header.fileName),
                size = header.uncompressedSize,
                compressedSize = header.compressedSize,
                isDirectory = header.isDirectory,
                lastModified = header.lastModifiedTimeEpoch,
                crc = header.crc,
                encrypted = header.isEncrypted
            )
        }

        val totalBytes = manifestEntries.sumOf { it.size }
        val sink = ExtractionSink(
            destination = destination,
            totalBytes = totalBytes,
            listener = request.listener,
            overwrite = request.overwrite
        )

        try {
            open(source, request.password).use { zip ->
                val include = request.include
                for (header in headers) {
                    val entryName = ArchiveReaders.normaliseName(header.fileName)
                    if (include != null && !include(entryName)) continue
                    if (header.isDirectory) {
                        sink.writeDirectory(entryName)
                        continue
                    }
                    sink.writeFile(entryName, header.uncompressedSize) {
                        zip.getInputStream(header)
                    }
                }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.ZIP, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = ArchiveFormat.ZIP,
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
    ): ArchiveIntegrityReport = ArchiveVerifier.verify(ArchiveFormat.ZIP, listener) {
        open(source, password).use { zip ->
            zip.fileHeaders.filter { !it.isDirectory }.map { header ->
                ArchiveVerifier.Member(
                    name = header.fileName,
                    size = header.uncompressedSize,
                    open = { zip.getInputStream(header) }
                )
            }
        }
    }

    private fun open(source: File, password: String?): ZipFile =
        if (password.isNullOrEmpty()) {
            ZipFile(source)
        } else {
            ZipFile(source, password.toCharArray())
        }
}
