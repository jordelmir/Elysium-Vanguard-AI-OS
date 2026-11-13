package com.elysium.vanguard.core.archive

import java.io.File
import java.io.RandomAccessFile

/**
 * Archive formats the sovereign file manager can read.
 *
 * Detection is content-first (magic bytes) and extension-second, so a
 * mislabelled archive is still handled correctly.
 */
enum class ArchiveFormat(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val supportsPassword: Boolean = false,
    val supportsMultiVolume: Boolean = false
) {
    ZIP("zip", "ZIP", listOf("zip", "zipx", "jar", "apk", "epub", "xapk", "apks"), true, true),
    SEVEN_Z("7z", "7Z", listOf("7z"), true, true),
    RAR("rar", "RAR", listOf("rar"), true, true),
    TAR("tar", "TAR", listOf("tar"), false, false),
    GZIP("gz", "GZIP", listOf("gz", "gzip", "tgz"), false, false),
    BZIP2("bz2", "BZIP2", listOf("bz2", "tbz2", "tbz"), false, false),
    XZ("xz", "XZ", listOf("xz", "txz"), false, false),
    ZSTD("zst", "ZSTD", listOf("zst", "tzst"), false, false),
    CPIO("cpio", "CPIO", listOf("cpio"), false, false),
    AR("ar", "AR / DEB", listOf("ar", "deb"), false, false),
    ARJ("arj", "ARJ", listOf("arj"), false, false),
    DUMP("dump", "DUMP", listOf("dump"), false, false),
    UNIX_Z("z", "UNIX COMPRESS", listOf("z", "taz"), false, false),
    LZMA_ALONE("lzma", "LZMA", listOf("lzma"), false, false),
    XAR("xar", "PKG / XAR", listOf("pkg", "xar", "mpkg"), false, false),
    ISO("iso", "ISO", listOf("iso"), false, false),
    UNKNOWN("unknown", "Unknown", emptyList(), false, false);

    val isSupported: Boolean get() = this != UNKNOWN

    companion object {

        /** Resolves a format from the file extension alone. */
        fun fromExtension(fileName: String): ArchiveFormat? {
            val name = fileName.lowercase()
            val ext = name.substringAfterLast('.', "")
            if (ext.isEmpty()) return null
            // Compound extensions first (.tar.gz, .tar.bz2 ...)
            COMPOUND_EXTENSIONS.forEach { (compound, format) ->
                if (name.endsWith(compound)) return format
            }
            return entries.firstOrNull { it.extensions.contains(ext) && it != UNKNOWN }
        }

        /**
         * Detects the real format by reading the container signature.
         * Falls back to the extension when the content is not conclusive.
         */
        fun detect(file: File): ArchiveFormat {
            if (!file.isFile) return UNKNOWN
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    val length = raf.length()
                    // BUG FIX: reading a fixed 512 bytes with readFully() threw on
                    // archives smaller than the buffer, so small archives were not
                    // detected at all. Read what actually exists.
                    val head = ByteArray(minOf(HEAD_SIZE.toLong(), length).toInt())
                    if (head.isNotEmpty()) raf.readFully(head)

                    val tailSize = minOf(TAIL_SIZE.toLong(), length).toInt()
                    val tail = ByteArray(tailSize)
                    if (tail.isNotEmpty()) {
                        raf.seek(length - tail.size)
                        raf.readFully(tail)
                    }

                    detectFromMagic(head, tail).let { detected ->
                        if (detected != UNKNOWN) return@use detected

                        // ISO 9660 keeps its descriptor at a fixed offset, so it
                        // cannot be detected from the head of the file.
                        if (length >= ISO_DESCRIPTOR_OFFSET + 6) {
                            raf.seek(ISO_DESCRIPTOR_OFFSET)
                            val iso = ByteArray(6)
                            raf.readFully(iso)
                            if (String(iso, 1, 5, Charsets.US_ASCII) == "CD001") {
                                return@use ISO
                            }
                        }
                        UNKNOWN
                    }.let { detected ->
                        // BUG FIX: several formats have no reliable signature
                        // (LZMA, Brotli, DUMP...). When the content is not
                        // conclusive the extension is the remaining authority.
                        if (detected != UNKNOWN) detected
                        else fromExtension(file.name) ?: UNKNOWN
                    }
                }
            } catch (_: Exception) {
                fromExtension(file.name) ?: UNKNOWN
            }
        }

        private fun detectFromMagic(head: ByteArray, tail: ByteArray): ArchiveFormat {
            fun startsWith(vararg signature: Int): Boolean {
                if (head.size < signature.size) return false
                return signature.withIndex().all { (i, b) -> head[i] == b.toByte() }
            }

            // ZIP (also covers APK / JAR / EPUB / Office Open XML containers)
            if (startsWith(0x50, 0x4B, 0x03, 0x04) ||
                startsWith(0x50, 0x4B, 0x05, 0x06) ||
                startsWith(0x50, 0x4B, 0x07, 0x08)
            ) return ZIP

            // 7-Zip
            if (startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) return SEVEN_Z

            // RAR 4.x and RAR 5.x
            if (startsWith(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)) return RAR

            // GZIP
            if (startsWith(0x1F, 0x8B)) return GZIP

            // BZIP2
            if (startsWith(0x42, 0x5A, 0x68)) return BZIP2

            // XZ
            if (startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)) return XZ

            // Zstandard
            if (startsWith(0x28, 0xB5, 0x2F, 0xFD)) return ZSTD

            // Apple XAR (used by macOS/iOS .pkg installers)
            if (startsWith(0x78, 0x61, 0x72, 0x21)) return XAR

            // AR / DEB
            if (startsWith(0x21, 0x3C, 0x61, 0x72, 0x63, 0x68, 0x3E, 0x0A)) return AR

            // ARJ
            if (startsWith(0x60, 0xEA)) return ARJ

            // Unix compress (.Z)
            if (startsWith(0x1F, 0x9D)) return UNIX_Z

            // CPIO (newc / odc / binary)
            if (startsWith(0x30, 0x37, 0x30, 0x37) || startsWith(0xC7, 0x71)) return CPIO

            // TAR: "ustar" magic lives at offset 257
            if (head.size >= 262) {
                val magic = String(head, 257, 5, Charsets.US_ASCII)
                if (magic == "ustar") return TAR
            }

            // ISO 9660: "CD001" at offset 0x8001, checked separately because it
            // lives at a fixed offset instead of the head of the file.
            return UNKNOWN
        }

        private val COMPOUND_EXTENSIONS = listOf(
            ".tar.gz" to GZIP,
            ".tgz" to GZIP,
            ".tar.bz2" to BZIP2,
            ".tbz2" to BZIP2,
            ".tar.xz" to XZ,
            ".txz" to XZ,
            ".tar.zst" to ZSTD,
            ".tar.lzma" to LZMA_ALONE,
            ".tar" to TAR
        )

        private const val HEAD_SIZE = 512
        private const val TAIL_SIZE = 16
        private const val ISO_DESCRIPTOR_OFFSET = 0x8000L
    }
}
