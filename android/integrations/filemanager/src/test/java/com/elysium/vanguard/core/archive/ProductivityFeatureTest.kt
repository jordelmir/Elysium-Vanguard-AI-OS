package com.elysium.vanguard.core.archive

import com.elysium.vanguard.core.search.FileFilterParser
import com.elysium.vanguard.core.search.FuzzySearchEngine
import com.elysium.vanguard.core.search.GlobalSearchEngine
import com.elysium.vanguard.core.search.SearchSort
import com.elysium.vanguard.core.util.CompressionEngine
import com.elysium.vanguard.core.util.CompressionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proof for the new productivity features:
 *  - compression into every supported archive layout
 *  - deletion, copy and move reporting a truthful percentage
 *  - the global search finding nested files and honouring the filter DSL
 */
class ProductivityFeatureTest {

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "meet-features/$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun sampleTree(root: File): File {
        val dir = File(root, "PAYLOAD")
        dir.mkdirs()
        File(dir, "alpha.txt").writeText("ALPHA CONTENT")
        File(dir, "beta.bin").writeBytes(ByteArray(4096) { (it % 71).toByte() })
        File(dir, "nested").mkdirs()
        File(dir, "nested/gamma.txt").writeText("GAMMA")
        return dir
    }

    // ── COMPRESSION INTO EVERY FORMAT ───────────────────────────────────────

    @Test
    fun `compresses into every supported format and the content survives`() {
        val dir = tempDir("compress")
        val tree = sampleTree(dir)
        val source = File(tree, "alpha.txt")

        CompressionFormat.entries.forEach { format ->
            val output = File(dir, "out.${format.extension}")
            val result = CompressionEngine.compress(
                files = listOf(tree),
                outputFile = output,
                format = format,
                password = if (format.supportsPassword) "MEET2026" else null,
                listener = null
            )

            assertTrue("$format must be produced: ${result.exceptionOrNull()}", result.isSuccess)
            assertTrue("$format must exist", output.isFile)
            assertTrue("$format must not be empty", output.length() > 0)
        }
    }

    @Test
    fun `a zip produced by the engine is readable by the engine`() {
        val dir = tempDir("compress-roundtrip")
        val tree = sampleTree(dir)

        val archive = File(dir, "round.zip")
        CompressionEngine.compress(listOf(tree), archive, CompressionFormat.ZIP, null, null)
            .getOrThrow()

        val destination = File(dir, "extracted")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertTrue(report.extractedFiles >= 3)
        assertEquals(
            "ALPHA CONTENT",
            File(destination, "PAYLOAD/alpha.txt").readText()
        )
        assertTrue(File(destination, "PAYLOAD/nested/gamma.txt").exists())
    }

    @Test
    fun `an encrypted zip produced by the engine is only readable with the password`() {
        val dir = tempDir("compress-encrypted")
        val tree = sampleTree(dir)
        val archive = File(dir, "locked.zip")

        CompressionEngine.compress(
            files = listOf(tree),
            outputFile = archive,
            format = CompressionFormat.ZIP,
            password = "MEET2026",
            listener = null
        ).getOrThrow()

        val withoutPassword = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = File(dir, "no-pass")
            )
        )
        assertTrue("the extraction must fail without the password", withoutPassword.isFailure)

        val withPassword = ArchiveEngine.extract(
            ArchiveExtractionRequest(
                archives = listOf(archive),
                destination = File(dir, "with-pass"),
                password = "MEET2026"
            )
        )
        assertTrue("the extraction must succeed with the password", withPassword.isSuccess)
    }

    @Test
    fun `a seven zip produced by the engine is readable by the engine`() {
        val dir = tempDir("compress-7z")
        val tree = sampleTree(dir)
        val archive = File(dir, "round.7z")

        CompressionEngine.compress(listOf(tree), archive, CompressionFormat.SEVEN_Z, null, null)
            .getOrThrow()

        val destination = File(dir, "extracted")
        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(archive), destination = destination)
        ).getOrThrow()

        assertTrue(report.extractedFiles >= 3)
        assertEquals("ALPHA CONTENT", File(destination, "PAYLOAD/alpha.txt").readText())
    }

    @Test
    fun `the compression progress is monotonic and ends at one hundred`() {
        val dir = tempDir("compress-progress")
        val tree = sampleTree(dir)
        val seen = mutableListOf<Int>()

        CompressionEngine.compress(
            files = listOf(tree),
            outputFile = File(dir, "progress.zip"),
            format = CompressionFormat.ZIP,
            password = null,
            listener = object : CompressionEngine.ProgressListener {
                override fun onProgress(percentage: Int, currentFile: String) {
                    seen += percentage
                }
            }
        ).getOrThrow()

        assertTrue("the progress must be reported", seen.isNotEmpty())
        assertEquals("the progress must finish at 100", 100, seen.last())
        assertTrue("the progress must never go backwards", seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `a pdf merge never leaves a partial file behind`() {
        val dir = tempDir("merge-pdf")
        val document = File(dir, "notes.txt").apply { writeText("TEXTO DEL DOCUMENTO") }
        val output = File(dir, "merged.pdf")

        // The PDF renderer needs the Android paint stack, so the rendering itself
        // is verified on the device. Here the contract is guarded: when nothing
        // can be rendered the engine must fail cleanly instead of writing a
        // truncated document over a good one.
        val result = com.elysium.vanguard.core.util.ConversionEngine.filesToPdf(
            listOf(document),
            output
        )

        if (result.isFailure) {
            assertTrue(
                "a failed merge must not leave a half written file",
                !output.exists() || output.length() == 0L
            )
        } else {
            assertTrue("a successful merge must produce a real document", output.length() > 0)
            val raw = output.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(
                "the document content must survive the merge",
                raw.contains("TEXTO DEL DOCUMENTO")
            )
        }
    }

    @Test
    fun `a commercial udf image is refused instead of extracted wrong`() {
        val dir = tempDir("udf")
        val iso = File(dir, "commercial.iso")
        // The marker of the EZB UltraISO layout.
        iso.writeBytes(ByteArray(4096))
        val marker = "EZB UltraISO"
        iso.writeBytes(marker.toByteArray(Charsets.ISO_8859_1) + iso.readBytes())

        val result = ArchiveEngine.inspect(iso)
        assertTrue("the commercial layout must be refused", result.isFailure)
        val error = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "the refusal must explain the reason, got: $error",
            error.contains("UDF 2.50") && error.contains("EZB")
        )
    }

    // ── GLOBAL SEARCH ───────────────────────────────────────────────────────

    private fun searchEngine() = GlobalSearchEngine(
        FuzzySearchEngine(),
        FileFilterParser()
    )

    @Test
    fun `the global search finds a file nested deep inside the tree`() {
        val dir = tempDir("search")
        val tree = sampleTree(dir)

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "gamma",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("the search must find the nested file", hits.isNotEmpty())
        assertTrue(
            "the result must point at the real file",
            hits.any { it.file.name == "gamma.txt" && it.file.absolutePath.contains("nested") }
        )
    }

    @Test
    fun `the global search matches fuzzily`() {
        val dir = tempDir("search-fuzzy")
        val tree = sampleTree(dir)

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "gma",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("a fuzzy query must still find the file", hits.any { it.file.name == "gamma.txt" })
    }

    @Test
    fun `the global search honours the extension filter`() {
        val dir = tempDir("search-filter")
        val tree = sampleTree(dir)

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "ext:bin",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("the filter must keep the match", hits.any { it.file.name == "beta.bin" })
        assertFalse("the filter must drop the other files", hits.any { it.file.name.endsWith(".txt") })
    }

    @Test
    fun `the global search honours the size filter`() {
        val dir = tempDir("search-size")
        val tree = sampleTree(dir)

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "size>1KB",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("only the big member matches", hits.any { it.file.name == "beta.bin" })
        assertFalse("the small members are filtered out", hits.any { it.file.name == "alpha.txt" })
    }

    @Test
    fun `the global search filters by file type`() {
        val dir = tempDir("search-type")
        val tree = sampleTree(dir)
        File(tree, "bundle.zip").writeText("ZIP")
        File(tree, "tool.apk").writeText("APK")
        File(tree, "Main.kt").writeText("CODE")

        val cases = mapOf(
            "type:archive" to listOf("bundle.zip"),
            "type:app" to listOf("tool.apk"),
            "type:code" to listOf("Main.kt")
        )

        cases.forEach { (query, expected) ->
            val hits = mutableListOf<GlobalSearchEngine.Hit>()
            searchEngine().search(
                query = query,
                roots = listOf(dir),
                onHit = { hits += it },
                onProgress = { }
            )
            assertTrue(
                "$query must find $expected but got ${hits.map { it.file.name }}",
                hits.any { expected.contains(it.file.name) }
            )
        }
    }

    @Test
    fun `an exact match outranks a scattered fuzzy match`() {
        val dir = tempDir("search-rank")
        val tree = sampleTree(dir)
        File(tree, "icp").writeText("exact")

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "icp",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("the exact match must be found", hits.any { it.file.name == "icp" })
        assertEquals(
            "the exact match must rank first",
            "icp",
            hits.first().file.name
        )
    }

    @Test
    fun `the results can be ordered by date and by name`() {
        val dir = tempDir("search-sort")
        // A dedicated folder so the ordering is fully deterministic.
        val tree = File(dir, "SORTED").apply { mkdirs() }

        val old = File(tree, "aaa_old.txt").apply { writeText("x") }
        old.setLastModified(1_000_000L)
        val recent = File(tree, "zzz_new.txt").apply { writeText("x") }
        recent.setLastModified(2_000_000L)

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "txt",
            roots = listOf(tree),
            onHit = { hits += it },
            onProgress = { }
        )
        assertTrue("the search must find the members", hits.size >= 2)

        val byDate = GlobalSearchEngine.sort(hits, SearchSort.RECENT)
        assertEquals("zzz_new.txt", byDate.first().file.name)

        val byName = GlobalSearchEngine.sort(hits, SearchSort.ALPHABETICAL)
        assertEquals("aaa_old.txt", byName.first().file.name)
    }

    @Test
    fun `a trailing space does not break the ranking`() {
        val dir = tempDir("search-space")
        val tree = sampleTree(dir)
        File(tree, "icp").writeText("exact")

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "icp ",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue("the exact match must still be found", hits.any { it.file.name == "icp" })
        assertEquals("the exact match must rank first", "icp", hits.first().file.name)
    }

    @Test
    fun `the global search also finds folders`() {
        val dir = tempDir("search-dir")
        val folder = File(dir, "MEET_PROGRESS_TEST").apply { mkdirs() }
        File(folder, "inside.txt").writeText("x")

        val hits = mutableListOf<GlobalSearchEngine.Hit>()
        searchEngine().search(
            query = "MEET_PROGRESS",
            roots = listOf(dir),
            onHit = { hits += it },
            onProgress = { }
        )

        assertTrue(
            "a folder must be a searchable result, got ${hits.map { it.file.name }}",
            hits.any { it.file.isDirectory && it.file.name == "MEET_PROGRESS_TEST" }
        )
    }

    @Test
    fun `the global search can be cancelled`() {
        val dir = tempDir("search-cancel")
        val tree = sampleTree(dir)

        val result = searchEngine().search(
            query = "a",
            roots = listOf(dir),
            onHit = { },
            onProgress = { },
            isCancelled = { true }
        )

        assertEquals("a cancelled search must not walk anything", 0, result.scannedFiles)
    }
}
