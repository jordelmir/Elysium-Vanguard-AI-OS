package com.elysium.vanguard.core.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proof against a REAL macOS installer package.
 *
 * The reference is the Command Line Tools package that ships inside macOS
 * updates: a genuine XAR container whose payload uses Apple's "pbzx" chunked
 * compression. The test is skipped when the reference is not present.
 */
class MacPackageTest {

    private fun referencePackage(): File? {
        val candidates = listOf(
            "/Library/Updates/047-91568/CLTools_macOS_SDK.pkg",
            "/Library/Updates/047-91568/CLTools_Executables_Universal.pkg",
            "/Library/Updates/Rosetta/SoftwareUpdate/RosettaUpdateAuto.pkg"
        )
        candidates.forEach { path ->
            val file = File(path)
            if (file.isFile && file.length() > 0) return file
        }
        // Any Command Line Tools package present on the machine.
        val updates = File("/Library/Updates")
        if (updates.isDirectory) {
            updates.listFiles()?.forEach { folder ->
                folder.listFiles()?.forEach { pkg ->
                    if (pkg.name.endsWith(".pkg") && pkg.length() > 0) return pkg
                }
            }
        }
        return null
    }

    @Test
    fun `detects a macos installer package as xar`() {
        val pkg = referencePackage() ?: return
        val format = ArchiveFormat.detect(pkg)
        assertTrue("a macOS package must be detected, got $format", format == ArchiveFormat.XAR)
    }

    @Test
    fun `reads the catalogue of a real macos installer package`() {
        val pkg = referencePackage() ?: return
        val manifest = ArchiveEngine.inspect(pkg).getOrThrow()

        assertTrue("the package must list members", manifest.entries.isNotEmpty())
        val names = manifest.entries.map { it.path }
        assertTrue(
            "a macOS package always carries a Payload, got $names",
            names.any { it.contains("Payload", ignoreCase = true) }
        )
    }

    @Test
    fun `extracts a real macos installer package including its pbzx payload`() {
        val pkg = referencePackage() ?: return
        val destination = File(
            System.getProperty("java.io.tmpdir"),
            "meet-pkg-test-${System.nanoTime()}"
        )

        val report = ArchiveEngine.extract(
            ArchiveExtractionRequest(archives = listOf(pkg), destination = destination)
        ).getOrThrow()

        assertTrue("the package must produce members", report.extractedFiles > 0)

        val extracted = destination.walkTopDown().filter { it.isFile }.toList()
        assertTrue("the extracted tree must not be empty", extracted.isNotEmpty())

        // The payload of a modern package is a pbzx container: it must have been
        // inflated into real files instead of left as an opaque blob.
        val payload = extracted.firstOrNull { it.name == "Payload" }
        if (payload != null) {
            val bytes = payload.readBytes()
            assertTrue(
                "the payload must be inflated, not a raw pbzx blob",
                !PbzxProbe.isPbzx(bytes)
            )
        }

        destination.deleteRecursively()
    }
}

/** Small helper so the test can assert on the inflated payload. */
private object PbzxProbe {
    fun isPbzx(bytes: ByteArray): Boolean =
        bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "pbzx"
}
