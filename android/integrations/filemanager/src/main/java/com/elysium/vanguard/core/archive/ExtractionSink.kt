package com.elysium.vanguard.core.archive

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Shared extraction sink.
 *
 * Owns the integrity contract (safe paths, atomic writes, no silent overwrite)
 * and the progress accounting for every reader.
 */
internal class ExtractionSink(
    private val destination: File,
    private val totalBytes: Long,
    private val listener: ArchiveProgressListener?,
    private val overwrite: Boolean
) {
    var bytesWritten: Long = 0L
        private set
    var filesWritten: Int = 0
        private set
    var directoriesWritten: Int = 0
        private set
    val rejectedPaths: MutableList<String> = mutableListOf()

    private var lastPercent = -1

    fun writeDirectory(relativePath: String) {
        val dir = ArchiveSafety.prepareDirectory(destination, relativePath)
        if (dir == null) {
            rejectedPaths += relativePath
            return
        }
        directoriesWritten++
    }

    /**
     * Streams one archive member into the destination.
     *
     * @param sizeHint declared uncompressed size, used to detect oversized members.
     * @param closeStream false for readers that hand over a shared, sequential
     *        stream (TAR, CPIO, AR): closing it would break the next member.
     */
    fun writeFile(
        relativePath: String,
        sizeHint: Long,
        closeStream: Boolean = true,
        open: () -> InputStream
    ) {
        if (sizeHint > ArchiveSafety.MAX_ENTRY_SIZE) {
            rejectedPaths += relativePath
            return
        }

        val target = ArchiveSafety.resolveTarget(destination, relativePath)
        if (target == null) {
            rejectedPaths += relativePath
            return
        }

        ArchiveSafety.writeAtomically(target, overwrite) { out ->
            val input = open()
            try {
                copyWithProgress(input, out, relativePath)
            } finally {
                if (closeStream) input.close()
            }
        }
        filesWritten++
    }

    /**
     * Copies a fully materialised [sourceFile] into the destination.
     * Used by readers that must materialise a stream before they can inspect it.
     */
    fun writeFileFromDisk(relativePath: String, sourceFile: File) {
        val target = ArchiveSafety.resolveTarget(destination, relativePath)
        if (target == null) {
            rejectedPaths += relativePath
            return
        }
        ArchiveSafety.writeAtomically(target, overwrite) { out ->
            sourceFile.inputStream().use { input ->
                copyWithProgress(input, out, relativePath)
            }
        }
        filesWritten++
    }

    private fun copyWithProgress(input: InputStream, out: OutputStream, label: String) {
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            bytesWritten += read
            notifyProgress(label)
            if (bytesWritten > ArchiveSafety.MAX_TOTAL_SIZE) {
                throw ArchiveException.Corrupted("extracted size exceeds the safety limit")
            }
        }
    }

    private fun notifyProgress(label: String) {
        val listener = listener ?: return
        val percent = if (totalBytes > 0) {
            ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 99)
        } else {
            0
        }
        if (percent != lastPercent) {
            lastPercent = percent
            listener.onProgress(percent, label)
        }
    }

    fun finish(): Int = if (totalBytes > 0) 100 else lastPercent.coerceAtLeast(0)

    companion object {
        const val COPY_BUFFER = 64 * 1024
    }
}
