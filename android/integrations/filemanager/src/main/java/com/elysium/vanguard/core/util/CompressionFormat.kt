package com.elysium.vanguard.core.util

/**
 * Compression targets supported by the sovereign engine.
 *
 * The list mirrors the desktop archivers the user already knows, and every
 * entry declares what it can actually do so the UI never offers an
 * impossible combination.
 */
enum class CompressionFormat(
    val id: String,
    val displayName: String,
    val extension: String,
    /** Can hold several files and folders. */
    val supportsMultipleFiles: Boolean,
    /** Can encrypt the archive with a password. */
    val supportsPassword: Boolean,
    /** Preserves the internal folder structure. */
    val supportsFolders: Boolean
) {
    ZIP("zip", "ZIP", "zip", true, true, true),
    SEVEN_Z("7z", "7Z (LZMA2)", "7z", true, true, true),
    TAR("tar", "TAR", "tar", true, false, true),
    TAR_GZ("tar.gz", "TAR + GZIP", "tar.gz", true, false, true),
    TAR_BZ2("tar.bz2", "TAR + BZIP2", "tar.bz2", true, false, true),
    TAR_XZ("tar.xz", "TAR + XZ", "tar.xz", true, false, true),
    TAR_ZST("tar.zst", "TAR + ZSTD", "tar.zst", true, false, true),
    CPIO("cpio", "CPIO", "cpio", true, false, true),
    AR("ar", "AR / DEB", "ar", true, false, true),
    GZIP("gz", "GZIP (archivo único)", "gz", false, false, false),
    BZIP2("bz2", "BZIP2 (archivo único)", "bz2", false, false, false),
    XZ("xz", "XZ (archivo único)", "xz", false, false, false),
    ZSTD("zst", "ZSTD (archivo único)", "zst", false, false, false);

    /** The file name the engine gives to the produced archive. */
    fun outputNameFor(baseName: String): String = "$baseName.$extension"
}
