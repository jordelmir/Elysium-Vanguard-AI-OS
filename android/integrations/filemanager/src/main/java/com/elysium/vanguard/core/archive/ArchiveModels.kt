package com.elysium.vanguard.core.archive

import java.io.File

/** A single member of an archive. */
data class ArchiveEntry(
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long = 0L,
    val crc: Long? = null,
    val encrypted: Boolean = false
)

/** The catalogue of an archive, obtained without extracting it. */
data class ArchiveManifest(
    val format: ArchiveFormat,
    val source: File,
    val entries: List<ArchiveEntry>,
    val totalSize: Long = entries.sumOf { it.size },
    val encrypted: Boolean = entries.any { it.encrypted }
) {
    val fileCount: Int get() = entries.count { !it.isDirectory }
    val directoryCount: Int get() = entries.count { it.isDirectory }
}

/** Outcome of a successful extraction. */
data class ExtractionReport(
    val format: ArchiveFormat,
    val destination: File,
    val extractedFiles: Int,
    val extractedDirectories: Int,
    val extractedBytes: Long,
    val durationMs: Long,
    val verified: Boolean,
    val rejectedPaths: List<String> = emptyList()
)

/**
 * Outcome of an integrity check. The archive is read end to end and every
 * checksum is validated, without writing a single byte to the disk.
 */
data class ArchiveIntegrityReport(
    val format: ArchiveFormat,
    val entriesTested: Int,
    val bytesVerified: Long,
    val failures: List<String>,
    val durationMs: Long
) {
    val isIntact: Boolean get() = failures.isEmpty()
    val summary: String
        get() = if (isIntact) {
            "ARCHIVE IS INTACT ($entriesTested entries, $bytesVerified bytes verified)"
        } else {
            "ARCHIVE IS DAMAGED: ${failures.size} failed member(s)"
        }
}

/**
 * Progress channel. The percentage is derived from real byte counters, never faked.
 */
fun interface ArchiveProgressListener {
    fun onProgress(percent: Int, currentEntry: String)
}

data class ArchiveExtractionRequest(
    val archives: List<File>,
    val destination: File,
    val password: String? = null,
    val overwrite: Boolean = false,
    /**
     * Optional filter. When present, only the entries whose path is accepted are
     * written. This is what powers "extract only the selected members".
     */
    val include: ((String) -> Boolean)? = null,
    val listener: ArchiveProgressListener? = null
)

/**
 * Typed failures. The UI maps them to honest, actionable messages.
 */
sealed class ArchiveException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** The archive is encrypted and no password was supplied. */
    class PasswordRequired(val format: ArchiveFormat) :
        ArchiveException("${format.displayName} archive is password protected")

    /** The supplied password did not decrypt the archive. */
    class WrongPassword(val format: ArchiveFormat) :
        ArchiveException("Incorrect password for this ${format.displayName} archive")

    /** The archive is damaged or truncated. */
    class Corrupted(val detail: String) :
        ArchiveException("Archive is damaged or incomplete: $detail")

    /** The format is recognised but cannot be read. */
    class Unsupported(val format: ArchiveFormat) :
        ArchiveException("${format.displayName} archives are not supported yet")

    /** An entry tried to escape the destination directory. */
    class UnsafeEntry(val path: String) :
        ArchiveException("Blocked an entry that tried to escape the destination: $path")

    /** The archive is not a recognised container. */
    class NotAnArchive(val file: File) :
        ArchiveException("Not a recognised archive: ${file.name}")
}
