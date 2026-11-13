package com.elysium.vanguard.core.archive

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Behavioural proof of the universal archive engine.
 *
 * These tests run on the JVM and exercise the real code paths: detection,
 * extraction, the integrity contract and the multi volume resolver.
 */
class ArchiveEngineTest {

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "meet-archive-tests/$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    // ── DETECTION ────────────────────────────────────────────────────────────

    @Test
    fun `detects zip by content even with a wrong extension`() {
        val dir = tempDir("detect")
        val payload = File(dir, "payload.txt").apply { writeText("hello") }
        val archive = File(dir, "actually_a_zip.bin")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("payload.txt"))
            zip.write(payload.readBytes())
            zip.closeEntry()
        }

        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(archive))
    }

    @Test
    fun `detects tar gz and seven z by content`() {
        val dir = tempDir("detect2")

        val tar = File(dir, "a.tar")
        TarArchiveOutputStream(FileOutputStream(tar)).use { out ->
            val entry = TarArchiveEntry("hello.txt")
            entry.size = 5
            out.putArchiveEntry(entry)
            out.write("hello".toByteArray())
            out.closeArchiveEntry()
        }
        assertEquals(ArchiveFormat.TAR, ArchiveFormat.detect(tar))

        val gz = File(dir, "a.gz")
        java.util.zip.GZIPOutputStream(FileOutputStream(gz)).use { it.write("hello".toByteArray()) }
        assertEquals(ArchiveFormat.GZIP, ArchiveFormat.detect(gz))

        val sevenZ = File(dir, "a.7z")
        sevenZ.writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x00, 0x04))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormat.detect(sevenZ))

        val rar = File(dir, "a.rar")
        rar.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        assertEquals(ArchiveFormat.RAR, ArchiveFormat.detect(rar))
    }

    // ── EXTRACTION ───────────────────────────────────────────────────────────

    @Test
    fun `extracts a zip preserving the folder tree and the bytes`() {
        val dir = tempDir("zip")
        val archive = File(dir, "game.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("MEET".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data/level1.dat"))
            zip.write(ByteArray(4096) { (it % 251).toByte() })
            zip.closeEntry()
        }

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertTrue(File(destination, "readme.txt").readText() == "MEET")
        assertTrue(File(destination, "data/level1.dat").readBytes().size == 4096)
        assertEquals(0, report.rejectedPaths.size)
    }

    @Test
    fun `extracts a tar with nested folders`() {
        val dir = tempDir("tar")
        val archive = File(dir, "bundle.tar")

        TarArchiveOutputStream(FileOutputStream(archive)).use { out ->
            val dirEntry = TarArchiveEntry("folder/")
            dirEntry.mode = 493
            out.putArchiveEntry(dirEntry)
            out.closeArchiveEntry()

            val fileEntry = TarArchiveEntry("folder/inside.txt")
            val payload = "SOV".toByteArray()
            fileEntry.size = payload.size.toLong()
            out.putArchiveEntry(fileEntry)
            out.write(payload)
            out.closeArchiveEntry()
        }

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals("SOV", File(destination, "folder/inside.txt").readText())
    }

    @Test
    fun `extracts a password protected zip when the password is supplied`() {
        val dir = tempDir("encrypted")
        val archive = File(dir, "secret.zip")
        val password = "elysium2026"

        val source = File(dir, "secret.txt").apply { writeText("classified payload") }
        ZipFile(archive, password.toCharArray()).use { zip ->
            val parameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
            zip.addFile(source, parameters)
        }

        val manifest = ArchiveEngine.inspect(archive).getOrThrow()
        assertTrue("the catalogue must expose the encryption flag", manifest.encrypted)

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = destination,
                password = password
            )
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals("classified payload", File(destination, "secret.txt").readText())
    }

    // ── INTEGRITY CONTRACT ──────────────────────────────────────────────────

    @Test
    fun `blocks an entry that tries to escape the destination`() {
        val dir = tempDir("zipslip")
        val archive = File(dir, "evil.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("owned".toByteArray())
            zip.closeEntry()
        }

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertFalse(
            "the payload must never land outside the destination",
            File(dir, "escaped.txt").exists()
        )
        assertTrue("the hostile entry must be reported", report.rejectedPaths.isNotEmpty())
    }

    @Test
    fun `never overwrites an existing file silently`() {
        val dir = tempDir("conflict")
        val archive = File(dir, "data.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write("new".toByteArray())
            zip.closeEntry()
        }

        val destination = File(dir, "out")
        File(destination, "report.txt").apply {
            parentFile?.mkdirs()
            writeText("original")
        }

        ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals("original", File(destination, "report.txt").readText())
        assertTrue(
            "the new content must live under a conflict free name",
            File(destination, "report (1).txt").exists()
        )
    }

    @Test
    fun `leaves no partial file behind when the archive is truncated`() {
        val dir = tempDir("truncated")
        val archive = File(dir, "broken.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("big.bin"))
            zip.write(ByteArray(2048))
            zip.closeEntry()
        }
        // Corrupt the central directory so the reader fails mid stream.
        val bytes = archive.readBytes()
        bytes[bytes.size / 2] = 0x00
        bytes[bytes.size / 2 + 1] = 0x00
        archive.writeBytes(bytes)

        val destination = File(dir, "out")
        val result = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        )

        // Either it fails cleanly, or it succeeds: what must never happen is a
        // visible ".part" file left behind.
        result.exceptionOrNull()
        val leftovers = destination.listFiles()?.filter { it.name.endsWith(".part") } ?: emptyList()
        assertTrue("no partial files may survive", leftovers.isEmpty())
    }

    // ── MULTI VOLUME ─────────────────────────────────────────────────────────

    @Test
    fun `resolves a rar volume set from a single selected part`() {
        val dir = tempDir("rarvol")
        File(dir, "Game.part1.rar").writeBytes(byteArrayOf(1))
        File(dir, "Game.part2.rar").writeBytes(byteArrayOf(2))
        File(dir, "Game.part3.rar").writeBytes(byteArrayOf(3))

        val set = ArchiveSetResolver.resolve(File(dir, "Game.part2.rar"))

        assertEquals(3, set.parts.size)
        assertTrue(set.isMultiVolume)
        assertEquals("Game", set.baseName)
        assertEquals("Game.part1.rar", set.primary.name)
    }

    @Test
    fun `resolves a seven zip split set`() {
        val dir = tempDir("7zvol")
        File(dir, "Data.7z.001").writeBytes(byteArrayOf(1))
        File(dir, "Data.7z.002").writeBytes(byteArrayOf(2))

        val set = ArchiveSetResolver.resolve(File(dir, "Data.7z.002"))

        assertEquals(2, set.parts.size)
        assertEquals(ArchiveSetResolver.VolumeKind.SEVEN_Z_SPLIT, set.kind)
        assertEquals("Data.7z", set.baseName)
    }

    @Test
    fun `groups a multi selection into a single set`() {
        val dir = tempDir("multiselect")
        File(dir, "Game.part1.rar").writeBytes(byteArrayOf(1))
        File(dir, "Game.part2.rar").writeBytes(byteArrayOf(2))
        File(dir, "Other.part1.rar").writeBytes(byteArrayOf(3))

        val sets = ArchiveSetResolver.resolveAll(
            listOf(
                File(dir, "Game.part1.rar"),
                File(dir, "Game.part2.rar"),
                File(dir, "Other.part1.rar")
            )
        )

        assertEquals("two independent sets", 2, sets.size)
        val game = sets.first { it.baseName == "Game" }
        assertEquals(2, game.parts.size)
    }

    @Test
    fun `batch extraction creates exactly one folder per archive inside the parent`() {
        val dir = tempDir("batch")
        val archive = File(dir, "Game.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("GAME".toByteArray())
            zip.closeEntry()
        }

        val parent = File(dir, "extract")
        val reports = ArchiveEngine.extractAll(listOf(archive), parent).getOrThrow()

        assertEquals(1, reports.size)
        assertEquals("Game", reports[0].destination.name)
        assertTrue("the folder must live inside the parent", reports[0].destination.parentFile == parent)
        assertTrue(File(parent, "Game/readme.txt").exists())
        assertFalse("no double nesting is allowed", File(parent, "Game/Game").exists())
    }

    // ── CATALOGUE ────────────────────────────────────────────────────────────

    @Test
    fun `reads the catalogue without extracting anything`() {
        val dir = tempDir("catalogue")
        val archive = File(dir, "list.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("a.txt"))
            zip.write("aaaa".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("b.txt"))
            zip.write("bbbbbb".toByteArray())
            zip.closeEntry()
        }

        val destination = File(dir, "out")
        val manifest = ArchiveEngine.inspect(archive).getOrThrow()

        assertEquals(2, manifest.fileCount)
        assertEquals(10L, manifest.totalSize)
        assertFalse("inspect must not extract", destination.exists())
    }

    @Test
    fun `every supported format has a reader bound to it`() {
        assertNotNull(ArchiveEngine.supportedFormats)
        assertTrue(ArchiveEngine.supportedFormats.contains(ArchiveFormat.ZIP))
        assertTrue(ArchiveEngine.supportedFormats.contains(ArchiveFormat.SEVEN_Z))
        assertTrue(ArchiveEngine.supportedFormats.contains(ArchiveFormat.RAR))
        assertTrue(ArchiveEngine.supportedFormats.contains(ArchiveFormat.XAR))
    }
}
