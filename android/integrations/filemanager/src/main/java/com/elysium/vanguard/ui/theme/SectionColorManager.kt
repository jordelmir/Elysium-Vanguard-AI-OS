package com.elysium.vanguard.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.elysium.vanguard.ui.theme.TitanColors

/**
 * SECTION COLOR MANAGER
 *
 * Accent color control for every major app module, with a GLOBAL theme and
 * per section overrides:
 *
 *  1. GLOBAL SELECTION — pick a color with the global switch on and every
 *     section follows it. That becomes the base theme.
 *  2. INDIVIDUAL SELECTION — pick a color with the global switch off and only
 *     that section changes; the rest of the app keeps the global theme.
 *  3. A global selection can ALWAYS be refined later with an individual one:
 *     the override wins for its section, the global keeps the others.
 *  4. Every choice is persisted locally, so the selection really sticks.
 */
object SectionColorManager {

    const val SECTION_DASHBOARD = "DASHBOARD"
    const val SECTION_GALLERY = "GALLERY"
    const val SECTION_MUSIC = "MUSIC"
    const val SECTION_FILEMANAGER = "FILEMANAGER"

    private const val PREFS_NAME = "elysium_accent_prefs"
    private const val KEY_GLOBAL = "global_accent"
    private const val KEY_GLOBAL_ENABLED = "global_accent_enabled"
    private const val KEY_MULTICOLOR = "multicolor"
    private const val KEY_OVERRIDES = "section_overrides"

    /** Base theme used by every section without an individual override. */
    var globalAccent by mutableStateOf(TitanColors.NeonCyan)
        private set

    /** True when the global theme is the one driving the app. */
    var isGlobalTheme by mutableStateOf(true)
        private set

    /** Individual overrides, per section. They win over the global theme. */
    private val overrides = mutableStateMapOf<String, Color>()

    var isMulticolor by mutableStateOf(false)
        private set

    private var storage: android.content.SharedPreferences? = null

    // ── ACCENT RESOLUTION ────────────────────────────────────────────────

    /**
     * The accent a section must use right now: its own override when it has
     * one, the global theme otherwise.
     */
    fun accentFor(section: String): Color {
        val key = normalize(section)
        overrides[key]?.let { return it }
        return globalAccent
    }

    /** True when this section has an individual override. */
    fun hasOverride(section: String): Boolean = overrides.containsKey(normalize(section))

    // Read only views used across the app.
    val dashboardAccent: Color get() = accentFor(SECTION_DASHBOARD)
    val galleryAccent: Color get() = accentFor(SECTION_GALLERY)
    val musicAccent: Color get() = accentFor(SECTION_MUSIC)
    val fileAccent: Color get() = accentFor(SECTION_FILEMANAGER)

    // ── MUTATIONS ────────────────────────────────────────────────────────

    /**
     * Applies a color.
     *
     * @param applyToAll true -> the color becomes the GLOBAL theme and every
     * individual override is dropped, so the whole app follows it.
     * @param section ignored when [applyToAll] is true.
     */
    fun updateAccent(section: String, color: Color, applyToAll: Boolean = false) {
        isMulticolor = false
        if (applyToAll) {
            globalAccent = color
            isGlobalTheme = true
            overrides.clear()
        } else {
            // Individual selection: it wins for this section even when the
            // global theme is active, and the rest of the app is untouched.
            overrides[normalize(section)] = color
        }
        persist()
    }

    /** Drops the individual override of a section, bringing back the global. */
    fun clearOverride(section: String) {
        overrides.remove(normalize(section))
        persist()
    }

    /** Drops every individual override: everything follows the global again. */
    fun clearAllOverrides() {
        overrides.clear()
        persist()
    }

    fun enableMulticolor() {
        isMulticolor = true
        persist()
    }

    /** Back to the factory look. */
    fun resetAll() {
        globalAccent = TitanColors.NeonCyan
        isGlobalTheme = true
        isMulticolor = false
        overrides.clear()
        persist()
    }

    // ── PERSISTENCE ──────────────────────────────────────────────────────

    /**
     * Loads the saved selection. Safe to call more than once.
     */
    fun init(context: Context) {
        if (storage != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        storage = prefs
        globalAccent = prefs.getString(KEY_GLOBAL, null)?.toColorOrNull() ?: TitanColors.NeonCyan
        isGlobalTheme = prefs.getBoolean(KEY_GLOBAL_ENABLED, true)
        isMulticolor = prefs.getBoolean(KEY_MULTICOLOR, false)
        overrides.clear()
        val saved = prefs.getString(KEY_OVERRIDES, "") ?: ""
        saved.split(';')
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size != 2) null else parts[0] to parts[1].toColorOrNull()
            }
            .forEach { (key, color) -> if (color != null) overrides[key] = color }
    }

    private fun persist() {
        val prefs = storage ?: return
        val encoded = overrides.entries.joinToString(";") { (key, color) -> "$key=${color.toHex()}" }
        prefs.edit()
            .putString(KEY_GLOBAL, globalAccent.toHex())
            .putBoolean(KEY_GLOBAL_ENABLED, isGlobalTheme)
            .putBoolean(KEY_MULTICOLOR, isMulticolor)
            .putString(KEY_OVERRIDES, encoded)
            .apply()
    }

    // ── HELPERS ──────────────────────────────────────────────────────────

    /**
     * Normalizes the section name so "MUSIC HUB", "music_hub" and "MUSIC" all
     * resolve to the same section.
     */
    private fun normalize(section: String): String {
        val key = section.uppercase().replace(" ", "").replace("_", "")
        return when (key) {
            "MUSIC", "MUSICHUB", "AUDIO", "AUDIOHUB" -> SECTION_MUSIC
            "GALLERY", "GALERIA", "MEDIA", "MEDIAVAULT" -> SECTION_GALLERY
            "FILEMANAGER", "FILESYSTEM", "FILE", "MANAGER", "STORAGE" -> SECTION_FILEMANAGER
            "DASHBOARD", "HOME", "INICIO" -> SECTION_DASHBOARD
            else -> key
        }
    }

    private fun Color.toHex(): String {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = (red * 255).toInt().coerceIn(0, 255)
        val g = (green * 255).toInt().coerceIn(0, 255)
        val b = (blue * 255).toInt().coerceIn(0, 255)
        return "$a,$r,$g,$b"
    }

    private fun String.toColorOrNull(): Color? {
        val parts = split(",").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return null
        return Color(parts[1] / 255f, parts[2] / 255f, parts[3] / 255f, parts[0] / 255f)
    }
}
