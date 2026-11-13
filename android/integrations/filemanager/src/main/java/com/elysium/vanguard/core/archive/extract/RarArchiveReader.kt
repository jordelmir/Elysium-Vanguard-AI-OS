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
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File

/**
 * RAR reader (RAR 3.x and RAR 5.x) with password support.
 *
 * Multi volume sets (`Game.part1.rar`, `Game.part2.rar`, ...) are resolved by the
 * engine and opened from the first volume: the RAR reader walks the sibling
 * volumes on its own.
 */
internal class RarArchiveReader : ArchiveReader {

    override fun inspect(source: File, password: String?): ArchiveManifest {
        val entries = try {
            open(source, password).use { archive ->
                archive.fileHeaders.map { it.toArchiveEntry() }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.RAR, password, t)
        }
        return ArchiveManifest(format = ArchiveFormat.RAR, source = source, entries = entries)
    }

    override fun extract(request: ArchiveExtractionRequest): ExtractionReport {
        val source = request.archives.first()
        val started = System.currentTimeMillis()
        val destination = request.destination
        if (!destination.exists()) destination.mkdirs()

        val entries = try {
            open(source, request.password).use { archive ->
                archive.fileHeaders.map { it.toArchiveEntry() }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.RAR, request.password, t)
        }

        val sink = ExtractionSink(
            destination = destination,
            totalBytes = entries.sumOf { it.size },
            listener = request.listener,
            overwrite = request.overwrite
        )

        try {
            open(source, request.password).use { archive ->
                val include = request.include
                for (header in archive.fileHeaders) {
                    val name = ArchiveReaders.normaliseName(header.fileName ?: "")
                    if (name.isEmpty()) continue
                    if (include != null && !include(name)) continue
                    if (header.isDirectory) {
                        sink.writeDirectory(name)
                    } else {
                        sink.writeFile(name, header.fullUnpackSize) {
                            archive.getInputStream(header)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            throw ArchiveReaders.mapFailure(ArchiveFormat.RAR, request.password, t)
        }

        request.listener?.onProgress(sink.finish(), "COMPLETE")

        return ExtractionReport(
            format = ArchiveFormat.RAR,
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
    ): ArchiveIntegrityReport = ArchiveVerifier.verify(ArchiveFormat.RAR, listener) {
        open(source, password).use { archive ->
            archive.fileHeaders.filter { !it.isDirectory }.map { header ->
                ArchiveVerifier.Member(
                    name = header.fileName ?: "",
                    size = header.fullUnpackSize,
                    open = { archive.getInputStream(header) }
                )
            }
        }
    }

    private fun open(source: File, password: String?): Archive =
        if (password.isNullOrEmpty()) {
            Archive(source)
        } else {
            Archive(source, password)
        }

    private fun FileHeader.toArchiveEntry(): ArchiveEntry = ArchiveEntry(
        path = ArchiveReaders.normaliseName(fileName ?: ""),
        size = fullUnpackSize,
        compressedSize = fullPackSize,
        isDirectory = isDirectory,
        lastModified = mTime?.time ?: 0L,
        crc = if (fileCRC != 0) fileCRC.toLong() and 0xFFFFFFFFL else null,
        encrypted = isEncrypted
    )
}
