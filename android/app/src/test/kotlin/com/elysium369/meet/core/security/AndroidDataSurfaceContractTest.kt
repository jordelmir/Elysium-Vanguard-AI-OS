package com.elysium369.meet.core.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataSurfaceContractTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `release source disables platform backup and excludes the debug AI receiver`() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:fullBackupContent="false""""))
        assertFalse(manifest.contains("AiTestReceiver"))
        assertFalse(manifest.contains("com.elysium369.meet.AI_TEST"))
    }

    @Test
    fun `AI test receiver exists only in the debug source set`() {
        val manifest = projectFile("src/debug/AndroidManifest.xml").readText()
        val receiver = projectFile(
            "src/debug/kotlin/com/elysium369/meet/ai/debug/AiTestReceiver.kt",
        )

        assertTrue(receiver.isFile)
        assertTrue(manifest.contains("AiTestReceiver"))
        assertTrue(manifest.contains("""android:exported="true""""))
        assertTrue(manifest.contains("com.elysium369.meet.AI_TEST"))
    }

    @Test
    fun `file provider exposes only purpose-specific application directories`() {
        val paths = projectFile("src/main/res/xml/file_paths.xml").readText()
        val allowed = listOf(
            """name="reports" path="Reports/"""",
            """name="manuals" path="Manuals/"""",
            """name="telemetry_exports" path="TelemetryExports/"""",
            """name="gauge_qr_shares" path="gauge_qr_shares/"""",
            """name="verification_captures" path="meet_verifications/"""",
            """name="mesh_transfers" path="Download/"""",
            """name="all_cache" path="mesh_share_cache/"""",
        )

        allowed.forEach { expected -> assertTrue(paths.contains(expected)) }
        assertFalse(paths.contains("<external-path"))
        assertFalse(paths.contains("""path=".""""))
    }
}
