package com.elysium.vanguard.core.util

import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * COMPRESSION ENGINE
 *
 * Writes every archive layout the desktop tools produce, with real progress and
 * optional encryption:
 *
 *  - ZIP through zip4j (ZIP64 + AES encryption with a password)
 *  - 7Z through Commons Compress (LZMA2 + AES with a password)
 *  - TAR, TAR+GZIP, TAR+BZIP2, TAR+XZ, TAR+ZSTD
 *  - CPIO and AR / DEB
 *  - GZIP, BZIP2, XZ and ZSTD for a single file
 */
object CompressionEngine {

    interface ProgressListener {
        fun onProgress(percentage: Int, currentFile: String)
    }

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Compresses files and directories into [outputFile] using [format].
     *
     * @param password optional password for the formats that support encryption.
     */
    fun compress(
        files: List<File>,
        outputFile: File,
        format: CompressionFormat = CompressionFormat.ZIP,
        password: String? = null,
        listener: ProgressListener? = null
    ): Result<File> {
        return try {
            if (files.isEmpty()) throw IllegalArgumentException("Nothing to compress")

            if (!format.supportsMultipleFiles && files.size > 1) {
                throw IllegalArgumentException(
                    "${format.displayName} can only hold a single file"
                )
            }

            val entries = mutableListOf<Pair<File, String>>()
            files.forEach { file ->
                if (file.isDirectory) {
                    collectFilesRecursive(file, file.name, entries)
                } else {
                    entries.add(file to file.name)
                }
            }

            outputFile.parentFile?.mkdirs()
            val totalSize = entries.sumOf { it.first.length() }.coerceAtLeast(1L)

            when (format) {
                CompressionFormat.ZIP -> writeZip(entries, outputFile, password, totalSize, listener)
                CompressionFormat.SEVEN_Z -> writeSevenZ(entries, outputFile, password, totalSize, listener)
                CompressionFormat.TAR -> writeTar(entries, outputFile, null, totalSize, listener)
                CompressionFormat.TAR_GZ -> writeTar(
                    entries, outputFile,
                    { out -> GzipCompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.TAR_BZ2 -> writeTar(
                    entries, outputFile,
                    { out -> BZip2CompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.TAR_XZ -> writeTar(
                    entries, outputFile,
                    { out -> XZCompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.TAR_ZST -> writeTar(
                    entries, outputFile,
                    { out -> ZstdCompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.CPIO -> writeCpio(entries, outputFile, totalSize, listener)
                CompressionFormat.AR -> writeAr(entries, outputFile, totalSize, listener)
                CompressionFormat.GZIP -> writeSingleStream(
                    entries, outputFile, { out -> GzipCompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.BZIP2 -> writeSingleStream(
                    entries, outputFile, { out -> BZip2CompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.XZ -> writeSingleStream(
                    entries, outputFile, { out -> XZCompressorOutputStream(out) }, totalSize, listener
                )
                CompressionFormat.ZSTD -> writeSingleStream(
                    entries, outputFile, { out -> ZstdCompressorOutputStream(out) }, totalSize, listener
                )
            }

            listener?.onProgress(100, "COMPLETE")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── ZIP (zip4j, AES capable) ───────────────────────────────────────────

    private fun writeZip(
        entries: List<Pair<File, String>>,
        outputFile: File,
        password: String?,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var processed = 0L

        val out = BufferedOutputStream(FileOutputStream(outputFile))
        val zipOut = if (password.isNullOrEmpty()) {
            net.lingala.zip4j.io.outputstream.ZipOutputStream(out)
        } else {
            net.lingala.zip4j.io.outputstream.ZipOutputStream(out, password.toCharArray())
        }

        zipOut.use { zos ->
            for ((file, relativePath) in entries) {
                if (file.isDirectory) {
                    val parameters = ZipParameters().apply {
                        fileNameInZip = "$relativePath/"
                        compressionMethod = CompressionMethod.DEFLATE
                        if (!password.isNullOrEmpty()) {
                            // BUG FIX: zip4j only encrypts when isEncryptFiles is set.
                            // Without it the archive was written in the clear.
                            isEncryptFiles = true
                            encryptionMethod = EncryptionMethod.AES
                            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                        }
                    }
                    zos.putNextEntry(parameters)
                    zos.closeEntry()
                    continue
                }

                listener?.onProgress(
                    ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                    file.name
                )

                val parameters = ZipParameters().apply {
                    fileNameInZip = relativePath
                    compressionMethod = CompressionMethod.DEFLATE
                    lastModifiedFileTime = file.lastModified()
                    if (!password.isNullOrEmpty()) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                }
                zos.putNextEntry(parameters)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        zos.write(buffer, 0, read)
                        processed += read
                    }
                }
                zos.closeEntry()
            }
        }
    }

    // ── 7Z (Commons Compress, LZMA2 + AES) ─────────────────────────────────

    private fun writeSevenZ(
        entries: List<Pair<File, String>>,
        outputFile: File,
        password: String?,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        var processed = 0L
        val sevenZ = if (password.isNullOrEmpty()) {
            SevenZOutputFile(outputFile)
        } else {
            SevenZOutputFile(outputFile, password.toCharArray())
        }

        sevenZ.use { out ->
            out.setContentCompression(SevenZMethod.LZMA2)
            for ((file, relativePath) in entries) {
                if (file.isDirectory) {
                    val directory = out.createArchiveEntry(file, "$relativePath/")
                    out.putArchiveEntry(directory)
                    out.closeArchiveEntry()
                    continue
                }
                listener?.onProgress(
                    ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                    file.name
                )
                val entry = out.createArchiveEntry(file, relativePath)
                out.putArchiveEntry(entry)
                // BUG FIX: createArchiveEntry only declares the member, the payload
                // has to be written explicitly or the file lands empty.
                file.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
                out.closeArchiveEntry()
                processed += file.length()
            }
            out.finish()
        }
    }

    // ── TAR (optionally compressed) ─────────────────────────────────────────

    private fun writeTar(
        entries: List<Pair<File, String>>,
        outputFile: File,
        wrapper: ((OutputStream) -> OutputStream)?,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var processed = 0L

        val fileOut = BufferedOutputStream(FileOutputStream(outputFile))
        val sink = wrapper?.invoke(fileOut) ?: fileOut

        TarArchiveOutputStream(sink).use { tar ->
            for ((file, relativePath) in entries) {
                if (file.isDirectory) {
                    val entry = TarArchiveEntry("$relativePath/")
                    entry.mode = 0b111_101_101
                    tar.putArchiveEntry(entry)
                    tar.closeArchiveEntry()
                    continue
                }
                listener?.onProgress(
                    ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                    file.name
                )
                val entry = TarArchiveEntry(relativePath)
                entry.size = file.length()
                entry.modTime = Date(file.lastModified())
                tar.putArchiveEntry(entry)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        tar.write(buffer, 0, read)
                        processed += read
                    }
                }
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        if (wrapper != null) fileOut.close() else Unit
    }

    // ── Single stream compressors ───────────────────────────────────────────

    private fun writeSingleStream(
        entries: List<Pair<File, String>>,
        outputFile: File,
        wrapper: (OutputStream) -> OutputStream,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        val (file, _) = entries.first()
        val buffer = ByteArray(BUFFER_SIZE)
        var processed = 0L

        val fileOut = BufferedOutputStream(FileOutputStream(outputFile))
        val sink = wrapper(fileOut)
        sink.use { out ->
            listener?.onProgress(0, file.name)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    processed += read
                    listener?.onProgress(
                        ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                        file.name
                    )
                }
            }
        }
        fileOut.close()
    }

    // ── CPIO and AR ────────────────────────────────────────────────────────

    private fun writeCpio(
        entries: List<Pair<File, String>>,
        outputFile: File,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var processed = 0L
        val out = BufferedOutputStream(FileOutputStream(outputFile))
        CpioArchiveOutputStream(out, org.apache.commons.compress.archivers.cpio.CpioConstants.FORMAT_NEW).use { cpio ->
            for ((file, relativePath) in entries) {
                if (file.isDirectory) continue
                listener?.onProgress(
                    ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                    file.name
                )
                val entry = CpioArchiveEntry(relativePath)
                entry.size = file.length()
                entry.setTime(file.lastModified() / 1000)
                cpio.putArchiveEntry(entry)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        cpio.write(buffer, 0, read)
                        processed += read
                    }
                }
                cpio.closeArchiveEntry()
            }
        }
        out.close()
    }

    private fun writeAr(
        entries: List<Pair<File, String>>,
        outputFile: File,
        totalSize: Long,
        listener: ProgressListener?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var processed = 0L
        val out = BufferedOutputStream(FileOutputStream(outputFile))
        ArArchiveOutputStream(out).use { ar ->
            for ((file, relativePath) in entries) {
                if (file.isDirectory) continue
                listener?.onProgress(
                    ((processed * 100) / totalSize).toInt().coerceIn(0, 99),
                    file.name
                )
                // The classic AR format limits the member name to 16 characters.
                val arName = relativePath.takeLast(16)
                val entry = ArArchiveEntry(arName, file.length(), 0, 0, 33188, file.lastModified() / 1000)
                ar.putArchiveEntry(entry)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        ar.write(buffer, 0, read)
                        processed += read
                    }
                }
                ar.closeArchiveEntry()
            }
        }
        out.close()
    }

    private fun collectFilesRecursive(dir: File, basePath: String, result: MutableList<Pair<File, String>>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val childPath = "$basePath/${child.name}"
            if (child.isDirectory) {
                result.add(child to childPath)
                collectFilesRecursive(child, childPath, result)
            } else {
                result.add(child to childPath)
            }
        }
    }

    /**
     * Decompress a ZIP archive with entry-count based progress.
     * Optionally provide a password — if the ZIP is password-protected
     * and standard Java ZIP doesn't support it, this will fail gracefully.
     */
    fun decompress(
        zipFile: File,
        outputDir: File,
        password: String? = null,
        listener: ProgressListener? = null
    ): Result<File> {
        return try {
            if (!outputDir.exists()) outputDir.mkdirs()

            val entryCount = countZipEntries(zipFile)
            var processedEntries = 0
            val buffer = ByteArray(1024 * 8)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(outputDir, entry.name)

                    val canonicalOutput = outputDir.canonicalPath
                    val canonicalFile = newFile.canonicalPath
                    if (!canonicalFile.startsWith(canonicalOutput)) {
                        throw SecurityException("ZIP entry outside target directory: ${entry.name}")
                    }

                    listener?.onProgress(
                        if (entryCount > 0) ((processedEntries * 100) / entryCount).coerceAtMost(99) else 0,
                        entry.name.substringAfterLast("/").ifEmpty { entry.name }
                    )

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (fisRead(zis, buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    processedEntries++
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            listener?.onProgress(100, "Complete")
            Result.success(outputDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fisRead(input: java.io.InputStream, buffer: ByteArray): Int = input.read(buffer)

    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (zis.nextEntry != null) {
                    count++
                    zis.closeEntry()
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return count
    }
}
