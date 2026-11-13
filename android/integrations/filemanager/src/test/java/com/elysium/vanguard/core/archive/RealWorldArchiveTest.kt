package com.elysium.vanguard.core.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REAL WORLD ARCHIVE PROOF
 *
 * The fixtures are produced by the actual desktop tools (zip, tar, gzip, 7-Zip,
 * xz, cpio, compress, brotli...) and then read back with the sovereign engine.
 * A format is skipped when its producer is not installed, so the suite stays
 * green on any machine.
 */
class RealWorldArchiveTest {

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "meet-realworld/$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun hasTool(tool: String): Boolean = try {
        val process = ProcessBuilder("which", tool).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun run(vararg command: String): Boolean = run(null, *command)

    /** Runs a command, optionally inside a working directory. */
    private fun run(workingDirectory: File?, vararg command: String): Boolean = try {
        val builder = ProcessBuilder(*command).redirectErrorStream(true)
        if (workingDirectory != null) builder.directory(workingDirectory)
        val process = builder.start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    /** Runs a shell pipeline such as "gzip -c file > out". */
    private fun runShell(pipeline: String): Boolean = try {
        val process = ProcessBuilder("sh", "-c", pipeline).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    /** Creates a small tree with one file and one nested file. */
    private fun sampleTree(root: File): File {
        val dir = File(root, "GAMEDATA")
        dir.mkdirs()
        File(dir, "readme.txt").writeText("MEET PAYLOAD")
        File(dir, "sub").mkdirs()
        File(dir, "sub/binary.bin").writeBytes(ByteArray(2048) { (it % 97).toByte() })
        return dir
    }

    private fun assertExtracted(directory: File, vararg expected: String) {
        expected.forEach { relative ->
            val target = File(directory, relative)
            assertTrue("$relative must be extracted", target.exists())
            assertTrue("$relative must not be empty", target.length() > 0)
        }
    }

    // ── ZIP ─────────────────────────────────────────────────────────────────

    @Test
    fun `extracts a zip produced by the desktop zip tool`() {
        if (!hasTool("zip")) return
        val dir = tempDir("zip")
        val tree = sampleTree(dir)
        val archive = File(dir, "game.zip")
        assertTrue(run(dir, "zip", "-r", "-q", archive.absolutePath, "GAMEDATA"))

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertExtracted(destination, "GAMEDATA/readme.txt", "GAMEDATA/sub/binary.bin")
        assertEquals("MEET PAYLOAD", File(destination, "GAMEDATA/readme.txt").readText())
    }

    @Test
    fun `extracts a password protected zip produced by the desktop zip tool`() {
        if (!hasTool("zip")) return
        val dir = tempDir("zip-pass")
        val tree = sampleTree(dir)
        val archive = File(dir, "locked.zip")
        assertTrue(
            run(dir, "zip", "-r", "-q", "-P", "MEET2026", archive.absolutePath, "GAMEDATA")
        )

        // Without the password the engine must fail with a typed error.
        val withoutPassword = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = File(dir, "out-nopass")
            )
        )
        assertTrue("the extraction must fail without the password", withoutPassword.isFailure)

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = destination,
                password = "MEET2026"
            )
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertExtracted(destination, "GAMEDATA/readme.txt")
    }

    // ── TAR family ──────────────────────────────────────────────────────────

    @Test
    fun `extracts the tar family produced by the desktop tar tool`() {
        if (!hasTool("tar")) return
        val dir = tempDir("tar")
        val tree = sampleTree(dir)

        val cases = listOf(
            "tar" to listOf("tar", "-cf"),
            "tar.gz" to listOf("tar", "-czf"),
            "tar.bz2" to listOf("tar", "-cjf"),
            "tar.xz" to listOf("tar", "-cJf")
        )

        cases.forEach { (extension, command) ->
            val archive = File(dir, "bundle.$extension")
            assertTrue(run(dir, *command.toTypedArray(), archive.absolutePath, "GAMEDATA"))

            val destination = File(dir, "out-$extension")
            val report = ArchiveEngine.extract(
                ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
            ).getOrThrow()

            assertEquals("$extension must yield 2 files", 2, report.extractedFiles)
            assertExtracted(destination, "GAMEDATA/readme.txt", "GAMEDATA/sub/binary.bin")
        }
    }

    // ── Single stream compressors ───────────────────────────────────────────

    @Test
    fun `extracts the single stream compressors produced by the desktop tools`() {
        val dir = tempDir("streams")
        val payload = File(dir, "payload.bin")
        payload.writeBytes(ByteArray(4096) { (it % 251).toByte() })

        val cases = listOf(
            "gzip" to listOf("gzip", "-c"),
            "bzip2" to listOf("bzip2", "-c"),
            "xz" to listOf("xz", "-c"),
            "zstd" to listOf("zstd", "-c"),
            "compress" to listOf("compress", "-c")
            // Brotli is intentionally absent: its decoder ships no Android natives.
        )

        cases.forEach { (tool, command) ->
            if (!hasTool(tool)) return@forEach
            val extension = when (tool) {
                "gzip" -> "gz"
                "bzip2" -> "bz2"
                "xz" -> "xz"
                "zstd" -> "zst"
                else -> "Z"
            }
            val archive = File(dir, "payload.$extension")
            assertTrue(
                runShell("${command.joinToString(" ")} '${payload.absolutePath}' > '${archive.absolutePath}'")
            )

            val destination = File(dir, "out-$tool")
            val report = ArchiveEngine.extract(
                ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
            ).getOrThrow()

            assertEquals("$tool must yield 1 file", 1, report.extractedFiles)
            val extracted = File(destination, "payload")
            assertTrue("$tool must produce the payload", extracted.exists())
            assertEquals(4096L, extracted.length())
        }
    }

    @Test
    fun `extracts a standalone lzma file produced by xz`() {
        if (!hasTool("xz")) return
        val dir = tempDir("lzma")
        val payload = File(dir, "payload.bin")
        payload.writeBytes(ByteArray(1024) { (it % 13).toByte() })
        val archive = File(dir, "payload.lzma")

        assertTrue(
            runShell("xz --format=lzma -c '${payload.absolutePath}' > '${archive.absolutePath}'")
        )

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals(1024L, File(destination, "payload").length())
    }

    // ── 7-Zip (including an AES encrypted archive) ──────────────────────────

    @Test
    fun `extracts a 7z archive produced by the desktop 7-Zip tool`() {
        if (!hasTool("7z")) return
        val dir = tempDir("7z")
        sampleTree(dir)
        val archive = File(dir, "game.7z")
        assertTrue(
            run(dir, "7z", "a", "-bso0", "-bsp0", archive.absolutePath, "GAMEDATA")
        )

        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormat.detect(archive))

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertExtracted(destination, "GAMEDATA/readme.txt", "GAMEDATA/sub/binary.bin")
    }

    @Test
    fun `extracts an AES encrypted 7z produced by the desktop 7-Zip tool`() {
        if (!hasTool("7z")) return
        val dir = tempDir("7z-aes")
        sampleTree(dir)
        val archive = File(dir, "locked.7z")
        assertTrue(
            run(
                dir, "7z", "a", "-bso0", "-bsp0", "-pMEET2026", "-mhe=on",
                archive.absolutePath, "GAMEDATA"
            )
        )

        // The 7z container does not expose an encryption flag per member, but
        // extracting without the password must fail, and that failure is exactly
        // what tells the UI to ask the user for it.
        val withoutPassword = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = File(dir, "out-nopass")
            )
        )
        assertTrue("the extraction must fail without the password", withoutPassword.isFailure)

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = destination,
                password = "MEET2026"
            )
        ).getOrThrow()

        assertEquals(2, report.extractedFiles)
        assertExtracted(destination, "GAMEDATA/readme.txt")
    }

    // ── CPIO and AR ─────────────────────────────────────────────────────────

    @Test
    fun `extracts a cpio archive produced by the desktop cpio tool`() {
        if (!hasTool("cpio")) return
        val dir = tempDir("cpio")
        sampleTree(dir)
        val archive = File(dir, "data.cpio")

        val listFile = File(dir, "files.txt").apply {
            writeText("GAMEDATA\nGAMEDATA/readme.txt\nGAMEDATA/sub\nGAMEDATA/sub/binary.bin\n")
        }
        val ok = try {
            val process = ProcessBuilder(
                "cpio", "-o", "-H", "newc", "--quiet"
            ).directory(File(dir, "GAMEDATA").parentFile)
                .redirectOutput(archive)
                .redirectErrorStream(false)
                .start()
            process.outputStream.use { it.write(listFile.readText().toByteArray()) }
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
        if (!ok) return

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertTrue("the cpio must yield members", report.extractedFiles > 0)
    }

    @Test
    fun `extracts an ar archive produced by the desktop ar tool`() {
        if (!hasTool("ar")) return
        val dir = tempDir("ar")
        val payload = File(dir, "member.txt").apply { writeText("AR PAYLOAD") }
        val archive = File(dir, "lib.a")
        assertTrue(run("ar", "rc", archive.absolutePath, payload.absolutePath))

        val destination = File(dir, "out")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertEquals(1, report.extractedFiles)
        assertEquals("AR PAYLOAD", File(destination, "member.txt").readText())
    }

    // ── Integrity over a real archive ───────────────────────────────────────

    @Test
    fun `verifies the integrity of a real desktop archive`() {
        if (!hasTool("zip")) return
        val dir = tempDir("verify-real")
        sampleTree(dir)
        val archive = File(dir, "game.zip")
        assertTrue(run(dir, "zip", "-r", "-q", archive.absolutePath, "GAMEDATA"))

        val report = ArchiveEngine.verify(archive).getOrThrow()

        assertTrue("the real archive must verify as intact: ${report.failures}", report.isIntact)
        assertEquals(2, report.entriesTested)
    }
}
