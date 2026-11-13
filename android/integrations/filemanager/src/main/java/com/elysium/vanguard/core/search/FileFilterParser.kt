package com.elysium.vanguard.core.search

import java.io.File

/**
 * PHASE 1.10 — Filter DSL for combining common file predicates.
 *
 * Supported filters:
 *   - `name:`        — substring or regex on filename (use `name~:` for regex)
 *   - `ext:`         — file extension (no dot, comma-separated for multiple)
 *   - `size:>N`      — minimum size in bytes (also `>=`, `<`, `<=`)
 *   - `modified:`    — relative ("last_week", "yesterday", "2024-01-15")
 *   - `type:`        — "file" | "dir" | "image" | "video" | "audio" | "doc"
 *
 * Multiple filters AND together. Unrecognised tokens are ignored (treated as
 * part of the name filter).
 */
class FileFilterParser @javax.inject.Inject constructor() {

    data class Filter(
        val nameContains: String? = null,
        val nameRegex: Regex? = null,
        val extensions: Set<String> = emptySet(),
        val minSize: Long? = null,
        val maxSize: Long? = null,
        val modifiedAfterMs: Long? = null,
        val modifiedBeforeMs: Long? = null,
        val type: TypeFilter = TypeFilter.ANY
    )

    enum class TypeFilter { ANY, FILE, DIR, IMAGE, VIDEO, AUDIO, DOC, ARCHIVE, APP, CODE, FONT }

    fun parse(query: String): Filter {
        var nameContains: String? = null
        var nameRegex: Regex? = null
        var extensions: Set<String> = emptySet()
        var minSize: Long? = null
        var maxSize: Long? = null
        var modifiedAfter: Long? = null
        var modifiedBefore: Long? = null
        var type: TypeFilter = TypeFilter.ANY

        val tokens = query.split(' ').filter { it.isNotBlank() }

        for (raw in tokens) {
            val token = raw.trim()
            when {
                token.startsWith("name~:") -> {
                    val pattern = token.removePrefix("name~:")
                    runCatching { nameRegex = Regex(pattern, RegexOption.IGNORE_CASE) }
                }
                token.startsWith("name:") -> {
                    nameContains = token.removePrefix("name:")
                }
                token.startsWith("ext:") || (token.startsWith("ext") && token.length > 3 && !token[3].isLetterOrDigit()) -> {
                    val raw2 = token.removePrefix("ext:").removePrefix("ext").removePrefix(":")
                    extensions = raw2.split(',').map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }
                token.startsWith("size:") || token.startsWith("size") && token.drop(4).firstOrNull()?.let { it in "><=" } == true -> {
                    val op = token.removePrefix("size:").removePrefix("size").removePrefix(":")
                    val (cmp, num) = when {
                        op.startsWith(">=") -> ">=" to op.removePrefix(">=")
                        op.startsWith("<=") -> "<=" to op.removePrefix("<=")
                        op.startsWith(">") -> ">" to op.removePrefix(">")
                        op.startsWith("<") -> "<" to op.removePrefix("<")
                        else -> "=" to op
                    }
                    val bytes = parseSize(num)
                    when (cmp) {
                        ">" -> minSize = (minSize ?: 0).coerceAtLeast(bytes + 1)
                        ">=" -> minSize = (minSize ?: 0).coerceAtLeast(bytes)
                        "<" -> maxSize = (maxSize ?: Long.MAX_VALUE).coerceAtMost(bytes - 1)
                        "<=" -> maxSize = (maxSize ?: Long.MAX_VALUE).coerceAtMost(bytes)
                        "=" -> {
                            minSize = bytes
                            maxSize = bytes
                        }
                    }
                }
                token.startsWith("modified:") -> {
                    val expr = token.removePrefix("modified:")
                    parseRelativeDate(expr)?.let {
                        modifiedAfter = it.first
                        if (it.second != null) modifiedBefore = it.second
                    }
                }
                token.startsWith("type:") || (token.startsWith("type") && token.length > 4 && !token[4].isLetterOrDigit()) -> {
                    val t = token.removePrefix("type:").removePrefix("type").removePrefix(":").lowercase()
                    type = when (t) {
                        "file", "f" -> TypeFilter.FILE
                        "dir", "folder", "d" -> TypeFilter.DIR
                        "image", "img" -> TypeFilter.IMAGE
                        "video", "vid" -> TypeFilter.VIDEO
                        "audio", "music" -> TypeFilter.AUDIO
                        "doc", "document" -> TypeFilter.DOC
                        "archive", "archives", "compressed", "zip" -> TypeFilter.ARCHIVE
                        "app", "apps", "apk" -> TypeFilter.APP
                        "code", "source" -> TypeFilter.CODE
                        "font", "fonts" -> TypeFilter.FONT
                        else -> TypeFilter.ANY
                    }
                }
                else -> {
                    // Free text — fold into nameContains so the filter still matches something.
                    nameContains = if (nameContains == null) token else "$nameContains $token"
                }
            }
        }

        return Filter(
            nameContains = nameContains,
            nameRegex = nameRegex,
            extensions = extensions,
            minSize = minSize,
            maxSize = maxSize,
            modifiedAfterMs = modifiedAfter,
            modifiedBeforeMs = modifiedBefore,
            type = type
        )
    }

    /**
     * @param ignoreName when true the name constraint is not applied. The global
     *        search uses the fuzzy engine for the name, so a substring filter
     *        would reject perfectly good fuzzy matches.
     */
    fun matches(file: File, filter: Filter, ignoreName: Boolean = false): Boolean {
        if (!file.exists()) return false

        // type
        when (filter.type) {
            TypeFilter.FILE -> if (file.isDirectory) return false
            TypeFilter.DIR -> if (!file.isDirectory) return false
            TypeFilter.IMAGE -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in IMAGE_EXTS) return false
            }
            TypeFilter.VIDEO -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in VIDEO_EXTS) return false
            }
            TypeFilter.AUDIO -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in AUDIO_EXTS) return false
            }
            TypeFilter.DOC -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in DOC_EXTS) return false
            }
            TypeFilter.ARCHIVE -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in ARCHIVE_EXTS) return false
            }
            TypeFilter.APP -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in APP_EXTS) return false
            }
            TypeFilter.CODE -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in CODE_EXTS) return false
            }
            TypeFilter.FONT -> {
                if (file.isDirectory) return false
                if (extensionOf(file) !in FONT_EXTS) return false
            }
            TypeFilter.ANY -> Unit
        }

        if (!ignoreName) {
            filter.nameContains?.let {
                if (!file.name.contains(it, ignoreCase = true)) return false
            }
        }
        filter.nameRegex?.let {
            if (!it.containsMatchIn(file.name)) return false
        }
        if (filter.extensions.isNotEmpty()) {
            if (extensionOf(file) !in filter.extensions) return false
        }
        filter.minSize?.let {
            if (file.length() < it) return false
        }
        filter.maxSize?.let {
            if (file.length() > it) return false
        }
        filter.modifiedAfterMs?.let {
            if (file.lastModified() < it) return false
        }
        filter.modifiedBeforeMs?.let {
            if (file.lastModified() > it) return false
        }
        return true
    }

    private fun extensionOf(file: File): String = file.extension.lowercase()

    private fun parseSize(s: String): Long {
        val trimmed = s.trim().uppercase()
        val mult = when {
            trimmed.endsWith("KB") -> 1024L
            trimmed.endsWith("MB") -> 1024L * 1024
            trimmed.endsWith("GB") -> 1024L * 1024 * 1024
            trimmed.endsWith("B") -> 1L
            else -> 1L
        }
        val num = trimmed.removeSuffix("KB").removeSuffix("MB").removeSuffix("GB").removeSuffix("B").trim()
        return (num.toDoubleOrNull() ?: 0.0).toLong() * mult
    }

    private fun parseRelativeDate(expr: String): Pair<Long, Long?>? {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        // Each entry returns (afterMs, beforeMs). For "open-ended" relative
        // windows we return null in the second slot so the parser doesn't
        // impose an artificial upper bound on the search.
        return when (expr.lowercase()) {
            "today" -> (now - day) to null
            "yesterday" -> (now - 2 * day) to (now - day)
            "last_week", "this_week" -> (now - 7 * day) to null
            "last_month" -> (now - 30 * day) to null
            "last_year" -> (now - 365 * day) to null
            else -> {
                val parsed = runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(expr)?.time
                }.getOrNull()
                if (parsed != null) parsed to (parsed + day) else null
            }
        }
    }

    companion object {
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tiff")
        private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "webm", "avi", "flv", "wmv", "m4v")
        private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
        private val DOC_EXTS = setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "epub")
        private val ARCHIVE_EXTS = setOf(
            "zip", "zipx", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz",
            "zst", "iso", "pkg", "xar", "cpio", "ar", "deb", "lzh", "cab", "jar", "apk", "xapk", "apks"
        )
        private val APP_EXTS = setOf("apk", "apks", "xapk", "ipa", "deb", "msi", "appimage")
        private val CODE_EXTS = setOf(
            "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp", "cs",
            "swift", "go", "rs", "rb", "php", "sh", "bash", "zsh", "sql", "json", "xml", "yaml",
            "yml", "toml", "gradle", "properties", "html", "css", "vue", "dart", "lua", "r", "m"
        )
        private val FONT_EXTS = setOf("ttf", "otf", "ttc", "woff", "woff2", "fon")
    }
}
