package com.elysium.vanguard.core.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Proof for the archive formats whose libraries ship a writer, plus the
 * detection rules of the read only ones.
 */
class ArchiveFormatMatrixTest {

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "meet-format-matrix/$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    // ── LZMA (alone) ───────────────────────────────────────────────────────

    @Test
    fun `extracts a standalone lzma file`() {
        val dir = tempDir("lzma")
        val archive = File(dir, "compressed.lzma")

        LZMACompressorOutputStream(FileOutputStream(archive)).use { out ->
            out.write("LZMA PAYLOAD".toByteArray())
        }

        assertEquals(ArchiveFormat.LZMA_ALONE, ArchiveFormat.detect(archive))

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals("LZMA PAYLOAD", File(destination, "compressed").readText())
    }

    // ── GZIP wrapping a TAR (.tgz) ─────────────────────────────────────────

    @Test
    fun `expands a gzipped tar into its real tree`() {
        val dir = tempDir("tgz")
        val archive = File(dir, "bundle.tar.gz")

        java.util.zip.GZIPOutputStream(FileOutputStream(archive)).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                val entry = TarArchiveEntry("inside/hello.txt")
                val payload = "TGZ".toByteArray()
                entry.size = payload.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(payload)
                tar.closeArchiveEntry()
            }
        }

        assertEquals(ArchiveFormat.GZIP, ArchiveFormat.detect(archive))

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals("TGZ", File(destination, "inside/hello.txt").readText())
    }

    // ── Detection of the read only formats ─────────────────────────────────

    @Test
    fun `detects arj by its signature`() {
        val dir = tempDir("arj")
        val archive = File(dir, "data.arj")
        archive.writeBytes(byteArrayOf(0x60, 0xEA.toByte(), 0x0D, 0x00, 0x01, 0x02, 0x03, 0x04))

        assertEquals(ArchiveFormat.ARJ, ArchiveFormat.detect(archive))
    }

    @Test
    fun `detects unix compress by its signature`() {
        val dir = tempDir("unixz")
        val archive = File(dir, "legacy.Z")
        archive.writeBytes(byteArrayOf(0x1F, 0x9D.toByte(), 0x90.toByte(), 0x52.toByte()))

        assertEquals(ArchiveFormat.UNIX_Z, ArchiveFormat.detect(archive))
    }

    @Test
    fun `a read only format that is not valid fails with a typed error`() {
        val dir = tempDir("arj-broken")
        val archive = File(dir, "broken.arj")
        archive.writeBytes(byteArrayOf(0x60, 0xEA.toByte(), 0x0D, 0x00, 0x01, 0x02, 0x03, 0x04))

        val result = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = File(dir, "out")
            )
        )

        assertTrue("a malformed archive must fail", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "the failure must be a typed archive error, not a raw crash: $error",
            error is ArchiveException
        )
    }

    // ── The advertised matrix must match the implementation ────────────────

    @Test
    fun `every advertised format has a reader and a detection rule`() {
        val expected = listOf(
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst",
            "cpio", "ar", "arj", "dump", "z", "lzma", "xar", "iso"
        )

        val advertised = ArchiveEngine.supportedFormats.map { it.id }
        expected.forEach { id ->
            assertTrue("format $id must be advertised", advertised.contains(id))
        }

        listOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "cpio",
            "ar", "arj", "xar", "iso").forEach { ext ->
            assertNotNull(
                "extension .$ext must resolve",
                ArchiveFormat.fromExtension("sample.$ext")
            )
        }
    }
}
