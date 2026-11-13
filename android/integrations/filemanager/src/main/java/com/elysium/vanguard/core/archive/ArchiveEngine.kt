package com.elysium.vanguard.core.archive

import com.elysium.vanguard.core.archive.extract.CommonsArchiveReader
import com.elysium.vanguard.core.archive.extract.IsoArchiveReader
import com.elysium.vanguard.core.archive.extract.RarArchiveReader
import com.elysium.vanguard.core.archive.extract.SevenZipArchiveReader
import com.elysium.vanguard.core.archive.extract.XarArchiveReader
import com.elysium.vanguard.core.archive.extract.ZipArchiveReader
import java.io.File

/**
 * UNIVERSAL ARCHIVE ENGINE
 *
 * Single entry point that understands every desktop archive layout the file
 * manager supports, resolves multi volume sets, and guarantees that the
 * extracted result is intact.
 */
object ArchiveEngine {

    private val zipReader = ZipArchiveReader()
    private val sevenZipReader = SevenZipArchiveReader()
    private val rarReader = RarArchiveReader()
    private val commonsReader = CommonsArchiveReader()
    private val xarReader = XarArchiveReader()
    private val isoReader = IsoArchiveReader()

    /** Formats the engine can actually open. */
    val supportedFormats: List<ArchiveFormat> = listOf(
        ArchiveFormat.ZIP,
        ArchiveFormat.SEVEN_Z,
        ArchiveFormat.RAR,
        ArchiveFormat.TAR,
        ArchiveFormat.GZIP,
        ArchiveFormat.BZIP2,
        ArchiveFormat.XZ,
        ArchiveFormat.ZSTD,
        ArchiveFormat.CPIO,
        ArchiveFormat.AR,
        ArchiveFormat.ARJ,
        ArchiveFormat.DUMP,
        ArchiveFormat.UNIX_Z,
        ArchiveFormat.LZMA_ALONE,
        ArchiveFormat.XAR,
        ArchiveFormat.ISO
    )

    fun detect(file: File): ArchiveFormat = ArchiveFormat.detect(file)

    /** True when [file] is an archive the engine can open. */
    fun isArchive(file: File): Boolean {
        if (!file.isFile) return false
        val format = ArchiveFormat.detect(file)
        if (format != ArchiveFormat.UNKNOWN) return format in supportedFormats
        return ArchiveFormat.fromExtension(file.name) in supportedFormats
    }

    /**
     * Reads the catalogue of an archive without extracting it.
     */
    fun inspect(file: File, password: String? = null): Result<ArchiveManifest> = runCatching {
        val set = ArchiveSetResolver.resolve(file)
        val source = materialiseSource(set)
        try {
            readerFor(set.format).inspect(source, password)
        } finally {
            source.cleanupIfTemporary()
        }
    }

    /**
     * Tests the integrity of an archive without extracting anything.
     *
     * This is the desktop "Test archive" action: every member is read end to end
     * so the library validates its checksum, and nothing is written to disk.
     */
    fun verify(
        file: File,
        password: String? = null,
        listener: ArchiveProgressListener? = null
    ): Result<ArchiveIntegrityReport> = runCatching {
        val set = ArchiveSetResolver.resolve(file)
        val source = materialiseSource(set)
        try {
            readerFor(set.format).verify(source, password, listener)
        } finally {
            source.cleanupIfTemporary()
        }
    }

    /**
     * Extracts one archive (or one complete volume set) into [request.destination].
     */
    fun extract(request: ArchiveExtractionRequest): Result<ExtractionReport> = runCatching {
        val primary = request.archives.firstOrNull()
            ?: throw ArchiveException.NotAnArchive(File("<empty>"))
        val set = ArchiveSetResolver.resolve(primary)
        val source = materialiseSource(set)
        try {
            readerFor(set.format).extract(
                request.copy(archives = listOf(source))
            )
        } finally {
            source.cleanupIfTemporary()
        }
    }

    /**
     * Extracts several selected archives in one action.
     *
     * [destination] is the PARENT folder: every archive gets its own folder named
     * after the archive, so a batch of separate games never merges into a single
     * mess and never lands directly on top of the parent folder.
     */
    fun extractAll(
        files: List<File>,
        destination: File,
        password: String? = null,
        overwrite: Boolean = false,
        listener: ArchiveProgressListener? = null,
        include: ((String) -> Boolean)? = null
    ): Result<List<ExtractionReport>> = runCatching {
        val sets = ArchiveSetResolver.resolveAll(files)
        val reports = mutableListOf<ExtractionReport>()
        sets.forEachIndexed { index, set ->
            val target = File(destination, sanitiseFolderName(set.baseName))
            val scopedListener = listener?.let { base ->
                ArchiveProgressListener { percent, entry ->
                    val overall = ((index * 100L) / sets.size + percent / sets.size)
                    base.onProgress(overall.coerceIn(0L, 100L).toInt(), entry)
                }
            }
            val report = extract(
                ArchiveExtractionRequest(
                    archives = set.parts.ifEmpty { listOf(set.primary) },
                    destination = target,
                    password = password,
                    overwrite = overwrite,
                    include = include,
                    listener = scopedListener
                )
            ).getOrThrow()
            reports += report
        }
        reports
    }

    private fun readerFor(format: ArchiveFormat): ArchiveReader = when (format) {
        ArchiveFormat.ZIP -> zipReader
        ArchiveFormat.SEVEN_Z -> sevenZipReader
        ArchiveFormat.RAR -> rarReader
        ArchiveFormat.XAR -> xarReader
        ArchiveFormat.ISO -> isoReader
        ArchiveFormat.TAR,
        ArchiveFormat.GZIP,
        ArchiveFormat.BZIP2,
        ArchiveFormat.XZ,
        ArchiveFormat.ZSTD,
        ArchiveFormat.UNIX_Z,
        ArchiveFormat.LZMA_ALONE,
        ArchiveFormat.CPIO,
        ArchiveFormat.AR,
        ArchiveFormat.ARJ,
        ArchiveFormat.DUMP -> commonsReader
        else -> throw ArchiveException.Unsupported(format)
    }

    /**
     * Raw byte-stream split ZIPs (`name.zip.001`, `name.zip.002`, ...) are reassembled
     * into a single temporary container before they are opened.
     */
    private fun materialiseSource(set: ArchiveSetResolver.VolumeSet): File {
        if (set.kind != ArchiveSetResolver.VolumeKind.ZIP_RAW_SPLIT) return set.primary

        val ordered = set.parts.sortedBy { part ->
            Regex("(\\d{3})$").find(part.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        if (!tempDir.exists()) tempDir.mkdirs()
        val temp = File.createTempFile("zipmerge", ".zip", tempDir)
        temp.outputStream().use { out ->
            ordered.forEach { part ->
                part.inputStream().use { it.copyTo(out, ExtractionSink.COPY_BUFFER) }
            }
        }
        return temp
    }

    private fun File.cleanupIfTemporary() {
        if (name.startsWith("zipmerge") && name.endsWith(".zip")) {
            delete()
        }
    }

    private fun sanitiseFolderName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim()
        return cleaned.ifEmpty { "Extracted" }
    }
}
