package com.elysium.vanguard.core.archive

import com.elysium.vanguard.core.rename.BatchRenameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Proof for the desktop grade archive features: integrity verification,
 * selective extraction, ISO 9660 and the batch rename engine.
 */
class ArchiveFeatureTest {

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "meet-archive-features/$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun writeZip(target: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    // ── INTEGRITY VERIFICATION ──────────────────────────────────────────────

    @Test
    fun `verifies an intact archive and reports every member`() {
        val dir = tempDir("verify-ok")
        val archive = File(dir, "good.zip")
        writeZip(archive, mapOf("a.txt" to "AAAA", "b.txt" to "BBBBBB", "c/d.txt" to "CC"))

        val report = ArchiveEngine.verify(archive).getOrThrow()

        assertTrue(report.isIntact)
        assertEquals(0, report.failures.size)
        assertEquals(3, report.entriesTested)
        assertEquals(12L, report.bytesVerified)
        assertTrue(report.summary.contains("INTACT"))
    }

    @Test
    fun `detects a corrupted member during verification`() {
        val dir = tempDir("verify-bad")
        val archive = File(dir, "corrupt.zip")

        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("payload.bin"))
            zip.write(ByteArray(512) { 0x41 })
            zip.closeEntry()
        }

        // Corrupt the stored bytes so the CRC no longer matches.
        val bytes = archive.readBytes()
        for (i in 40 until 120) bytes[i] = 0x00
        archive.writeBytes(bytes)

        val report = ArchiveEngine.verify(archive).getOrThrow()

        assertFalse("a corrupted archive must not be reported as intact", report.isIntact)
        assertTrue(report.failures.isNotEmpty())
        assertTrue(report.summary.contains("DAMAGED"))
    }

    @Test
    fun `verification never writes anything to disk`() {
        val dir = tempDir("verify-nowrite")
        val archive = File(dir, "x.zip")
        writeZip(archive, mapOf("a.txt" to "AAAA"))

        val before = dir.listFiles()?.size ?: 0
        ArchiveEngine.verify(archive).getOrThrow()
        val after = dir.listFiles()?.size ?: 0

        assertEquals("verification must not create files", before, after)
    }

    // ── SELECTIVE EXTRACTION ────────────────────────────────────────────────

    @Test
    fun `extracts only the members the user selected`() {
        val dir = tempDir("selective")
        val archive = File(dir, "multi.zip")
        writeZip(
            archive,
            mapOf(
                "keep1.txt" to "KEEP1",
                "skipme.txt" to "SKIPME",
                "keep2.txt" to "KEEP2"
            )
        )

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = destination,
                include = { path -> path.startsWith("keep") }
            )
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertTrue(File(destination, "keep1.txt").exists())
        assertTrue(File(destination, "keep2.txt").exists())
        assertFalse("the excluded member must not be written", File(destination, "skipme.txt").exists())
    }

    // ── ISO 9660 ────────────────────────────────────────────────────────────

    @Test
    fun `reads and extracts an iso 9660 image`() {
        val dir = tempDir("iso")
        val iso = File(dir, "disc.iso")

        // Two files laid out in a minimal but valid ISO 9660 image.
        val payloadA = "HELLO".toByteArray()
        val payloadB = "WORLD".toByteArray()

        val rootExtent = 20L
        val filesExtent = 21L
        val fileAExtent = 22L
        val fileBExtent = 23L

        val image = ByteArray(24 * 2048)

        // Primary volume descriptor at sector 16.
        val pvd = 16 * 2048
        image[pvd] = 1
        "CD001".toByteArray().copyInto(image, pvd + 1)
        image[pvd + 6] = 1
        "TESTVOL".toByteArray().copyInto(image, pvd + 40)
        writeLe32(image, pvd + 80, 24)      // volume space size
        writeLe16(image, pvd + 128, 2048)   // logical block size
        // Root directory record at 190.
        val root = pvd + 190
        image[root] = 34
        writeLe32(image, root + 2, rootExtent)
        writeLe32(image, root + 10, 2048)
        image[root + 25] = 0x02            // directory flag
        image[root + 32] = 1               // identifier length
        image[root + 33] = 0x00.toByte()    // "." identifier

        // Root directory (sector 20): "." , ".." and the FILES sub directory.
        val rootDir = (rootExtent * 2048).toInt()
        var cursor = rootDir
        cursor = writeDirRecord(image, cursor, rootExtent, 0x00)
        cursor = writeDirRecord(image, cursor, rootExtent, 0x01)
        cursor = writeNamedRecord(image, cursor, filesExtent, 0x02, "FILES")

        // FILES directory (sector 21): "." , ".." and the two files.
        val filesDir = (filesExtent * 2048).toInt()
        cursor = filesDir
        cursor = writeDirRecord(image, cursor, filesExtent, 0x00)
        cursor = writeDirRecord(image, cursor, rootExtent, 0x01)
        cursor = writeNamedRecord(image, cursor, fileAExtent, 0x00, "A.TXT", payloadA.size.toLong())
        writeNamedRecord(image, cursor, fileBExtent, 0x00, "B.TXT", payloadB.size.toLong())

        payloadA.copyInto(image, (fileAExtent * 2048).toInt())
        payloadB.copyInto(image, (fileBExtent * 2048).toInt())


        iso.writeBytes(image)

        assertEquals(ArchiveFormat.ISO, ArchiveFormat.detect(iso))

        val manifest = ArchiveEngine.inspect(iso).getOrThrow()
        assertTrue("the ISO catalogue must list the members", manifest.entries.isNotEmpty())

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(iso), destination = destination)
        ).getOrThrow()

        assertTrue("the ISO must yield files", report.extractedFiles > 0)
        val extracted = destination.walkTopDown().filter { it.isFile }.toList()
        assertTrue("A.TXT must be extracted", extracted.any { it.name == "A.TXT" })
        assertEquals("HELLO", extracted.first { it.name == "A.TXT" }.readText())
    }

    // ── BATCH RENAME ────────────────────────────────────────────────────────

    @Test
    fun `batch rename applies the template with a zero padded counter`() {
        val dir = tempDir("rename")
        val files = listOf("shot.jpg", "shot2.jpg", "shot3.jpg").map {
            File(dir, it).apply { writeText("x") }
        }

        val engine = BatchRenameEngine()
        val plan = engine.plan(
            files,
            BatchRenameEngine.Pattern(template = "IMG_{counter}", counterPadding = 3)
        )

        assertEquals(3, plan.renames.size)
        assertEquals("IMG_001.jpg", plan.renames[0].renamed.name)
        assertEquals("IMG_002.jpg", plan.renames[1].renamed.name)
        assertEquals("IMG_003.jpg", plan.renames[2].renamed.name)

        val renamed = engine.execute(plan)
        assertEquals(3, renamed)
        assertTrue(File(dir, "IMG_001.jpg").exists())
        assertFalse(File(dir, "shot.jpg").exists())
    }

    @Test
    fun `batch rename skips a conflicting target instead of overwriting it`() {
        val dir = tempDir("rename-conflict")
        File(dir, "target.jpg").writeText("original")
        val source = File(dir, "other.jpg").apply { writeText("x") }

        val engine = BatchRenameEngine()
        val plan = engine.plan(
            listOf(source),
            BatchRenameEngine.Pattern(
                template = "target",
                onConflict = BatchRenameEngine.ConflictResolution.SKIP
            )
        )

        assertEquals(0, plan.renames.size)
        assertEquals(1, plan.skipped.size)
        assertEquals("original", File(dir, "target.jpg").readText())
    }

    @Test
    fun `batch rename appends a suffix when the policy allows it`() {
        val dir = tempDir("rename-suffix")
        File(dir, "target.jpg").writeText("original")
        val source = File(dir, "other.jpg").apply { writeText("x") }

        val engine = BatchRenameEngine()
        val plan = engine.plan(
            listOf(source),
            BatchRenameEngine.Pattern(
                template = "target",
                onConflict = BatchRenameEngine.ConflictResolution.APPEND_SUFFIX
            )
        )

        assertEquals(1, plan.renames.size)
        assertEquals("target (1).jpg", plan.renames[0].renamed.name)
        assertEquals("original", File(dir, "target.jpg").readText())
    }

    @Test
    fun `batch rename supports the date and parent placeholders`() {
        val dir = tempDir("rename-placeholders")
        val file = File(dir, "photo.bin").apply { writeText("x") }

        val engine = BatchRenameEngine()
        val plan = engine.plan(
            listOf(file),
            BatchRenameEngine.Pattern(template = "{parent}_{name}", dateFormat = "yyyy")
        )

        assertEquals(1, plan.renames.size)
        assertTrue(plan.renames[0].renamed.name.startsWith("rename-placeholders"))
    }

    // ── REAL WORLD VOLUME SET ───────────────────────────────────────────────

    @Test
    fun `groups the real eight part game set into a single extraction`() {
        val dir = tempDir("real-set")

        // Exactly the naming used by the game sets in the wild:
        // RSEREQUIEM.SoloJuegosPc.part1.rar ... part8.rar
        val parts = (1..8).map { index ->
            File(dir, "RSEREQUIEM.SoloJuegosPc.part$index.rar").apply { writeBytes(ByteArray(64)) }
        }

        val set = ArchiveSetResolver.resolve(parts.first())

        assertEquals("every volume of the set must be picked up", 8, set.parts.size)
        assertTrue(set.isMultiVolume)
        assertEquals("RSEREQUIEM.SoloJuegosPc", set.baseName)
        assertEquals("the lowest volume is the entry point", "RSEREQUIEM.SoloJuegosPc.part1.rar", set.primary.name)
        assertEquals(ArchiveFormat.RAR, set.format)
        assertEquals(ArchiveSetResolver.VolumeKind.RAR_NEW, set.kind)
    }

    @Test
    fun `picking any middle volume still resolves the whole set`() {
        val dir = tempDir("real-set-middle")
        (1..8).forEach { index ->
            File(dir, "RSEREQUIEM.SoloJuegosPc.part$index.rar").writeBytes(ByteArray(64))
        }

        val set = ArchiveSetResolver.resolve(File(dir, "RSEREQUIEM.SoloJuegosPc.part5.rar"))

        assertEquals(8, set.parts.size)
        assertEquals("RSEREQUIEM.SoloJuegosPc.part1.rar", set.primary.name)
    }

    @Test
    fun `selecting three volumes of the same game yields one folder not three`() {
        val dir = tempDir("real-set-select")
        (1..8).forEach { index ->
            File(dir, "RSEREQUIEM.SoloJuegosPc.part$index.rar").writeBytes(ByteArray(64))
        }
        File(dir, "AnotherGame.part1.rar").writeBytes(ByteArray(64))

        val sets = ArchiveSetResolver.resolveAll(
            listOf(
                File(dir, "RSEREQUIEM.SoloJuegosPc.part2.rar"),
                File(dir, "RSEREQUIEM.SoloJuegosPc.part5.rar"),
                File(dir, "RSEREQUIEM.SoloJuegosPc.part7.rar"),
                File(dir, "AnotherGame.part1.rar")
            )
        )

        assertEquals("two games -> two folders", 2, sets.size)
        val game = sets.first { it.baseName == "RSEREQUIEM.SoloJuegosPc" }
        assertEquals("the whole set is completed from disk", 8, game.parts.size)
    }

    @Test
    fun `the extraction folder keeps the original capitalisation`() {
        val dir = tempDir("real-set-case")
        File(dir, "RSEREQUIEM.SoloJuegosPc.part1.rar").writeBytes(ByteArray(64))

        val set = ArchiveSetResolver.resolve(File(dir, "RSEREQUIEM.SoloJuegosPc.part1.rar"))

        assertEquals("RSEREQUIEM.SoloJuegosPc", set.baseName)
        assertFalse("the folder must not be lowercased", set.baseName == set.baseName.lowercase())
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Writes a "." / ".." style directory record (single byte identifier). */
    private fun writeDirRecord(image: ByteArray, offset: Int, extent: Long, identifier: Int): Int {
        image[offset] = 34
        writeLe32(image, offset + 2, extent)
        writeLe32(image, offset + 10, 2048)
        image[offset + 25] = 0x02
        image[offset + 32] = 1
        image[offset + 33] = identifier.toByte()
        return offset + 34
    }

    /** Writes a named record. Flags 0x00 is a file, 0x02 is a directory. */
    private fun writeNamedRecord(
        image: ByteArray,
        offset: Int,
        extent: Long,
        flags: Int,
        name: String,
        dataLength: Long = 2048
    ): Int {
        // The record length must cover the identifier, padded to an even size.
        var recordLength = 33 + name.length
        if (recordLength % 2 != 0) recordLength++
        image[offset] = recordLength.toByte()
        writeLe32(image, offset + 2, extent)
        writeLe32(image, offset + 10, dataLength)
        image[offset + 25] = flags.toByte()
        image[offset + 32] = name.length.toByte()
        name.toByteArray().copyInto(image, offset + 33)
        return offset + recordLength
    }

    private fun writeLe32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeLe16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
