package com.elysium.vanguard.core.archive

import com.elysium.vanguard.core.archive.ArchiveIntegrityReport
import java.io.File

/**
 * Shared machinery for the "test archive" action.
 */
internal object ArchiveVerifier {

    /** One member that can be read to validate its checksum. */
    class Member(val name: String, val size: Long, val open: () -> java.io.InputStream)

    fun verify(
        format: ArchiveFormat,
        listener: ArchiveProgressListener?,
        members: () -> List<Member>
    ): ArchiveIntegrityReport {
        val started = System.currentTimeMillis()
        val failures = mutableListOf<String>()
        var tested = 0
        var bytes = 0L

        val list = try {
            members()
        } catch (t: Throwable) {
            failures += "archive: ${t.message ?: t.javaClass.simpleName}"
            emptyList()
        }

        val total = list.sumOf { it.size }
        var done = 0L

        for (member in list) {
            try {
                member.open().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        bytes += read
                        if (total > 0) {
                            listener?.onProgress(
                                ((done * 100L) / total).toInt().coerceIn(0, 99),
                                member.name
                            )
                        }
                    }
                }
                tested++
            } catch (t: Throwable) {
                failures += "${member.name}: ${t.message ?: "checksum verification failed"}"
            }
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
}

/**
 * Contract implemented by every format reader.
 */
internal interface ArchiveReader {
    fun inspect(source: File, password: String?): ArchiveManifest
    fun extract(request: ArchiveExtractionRequest): ExtractionReport

    /**
     * Reads every member end to end so the library validates its checksum.
     * Nothing is written to disk: this is the desktop "test archive" action.
     */
    fun verify(
        source: File,
        password: String?,
        listener: ArchiveProgressListener?
    ): ArchiveIntegrityReport
}

/**
 * Shared helpers for the readers.
 */
internal object ArchiveReaders {

    /**
     * Maps a low level failure onto a typed archive failure.
     */
    fun mapFailure(format: ArchiveFormat, password: String?, t: Throwable): ArchiveException {
        val message = (t.message ?: t.javaClass.simpleName).lowercase()

        if (password == null) {
            if (message.contains("password") || message.contains("encrypt") ||
                message.contains("decipher") || message.contains("aes")
            ) {
                return ArchiveException.PasswordRequired(format)
            }
        } else {
            if (message.contains("password") || message.contains("decipher") ||
                message.contains("incorrect password") || message.contains("wrong password") ||
                message.contains("cannot decrypt") || message.contains("invalid password")
            ) {
                return ArchiveException.WrongPassword(format)
            }
        }

        if (t is ArchiveException) return t

        if (message.contains("crc") || message.contains("corrupt") || message.contains("damaged") ||
            message.contains("checksum") || message.contains("unexpected end") ||
            message.contains("truncated")
        ) {
            return ArchiveException.Corrupted(t.message ?: "checksum mismatch")
        }

        return when (t) {
            is java.io.FileNotFoundException -> ArchiveException.Corrupted("file not found: ${t.message}")
            else -> ArchiveException.Corrupted(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Normalises the member name of an archive entry.
     */
    fun normaliseName(name: String): String =
        name.replace('\\', '/').trimStart('/')

    /**
     * Picks a human readable file name for single-stream archives (gz, bz2, xz, zst...).
     * The comparison is case insensitive so ".Z" (Unix compress) is handled too.
     */
    fun singleMemberName(source: File): String {
        val name = source.name
        val lower = name.lowercase()
        for (suffix in SINGLE_STREAM_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return name.substring(0, name.length - suffix.length)
            }
        }
        return source.nameWithoutExtension
    }

    private val SINGLE_STREAM_SUFFIXES = listOf(
        ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tbz", ".tar.xz", ".txz",
        ".tar.zst", ".tar.lzma", ".gz", ".gzip", ".bz2", ".xz", ".zst",
        ".z", ".lzma"
    )
}
