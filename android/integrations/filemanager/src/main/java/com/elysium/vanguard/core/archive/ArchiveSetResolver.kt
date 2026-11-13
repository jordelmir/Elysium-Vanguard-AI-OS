package com.elysium.vanguard.core.archive

import java.io.File

/**
 * MULTI-VOLUME RESOLVER
 *
 * Desktop archivers split large sets into volumes. The user must be able to hit
 * "extract" once on a single part and receive the whole set, and must be able to
 * select several parts at once and still receive one coherent extraction.
 *
 * Supported volume layouts:
 *
 *  - RAR (new style) : Game.part1.rar, Game.part2.rar, ...
 *  - RAR (old style) : Game.rar, Game.r00, Game.r01, ...
 *  - 7-Zip split     : Game.7z.001, Game.7z.002, ...
 *  - ZIP (raw split) : Game.zip.001, Game.zip.002, ...   (byte-stream split)
 *  - ZIP (zip4j)     : Game.zip + Game.z01, Game.z02, ...
 */
object ArchiveSetResolver {

    enum class VolumeKind {
        SINGLE,
        RAR_NEW,
        RAR_OLD,
        SEVEN_Z_SPLIT,
        ZIP_RAW_SPLIT,
        ZIP4J_SPLIT
    }

    data class VolumeSet(
        val format: ArchiveFormat,
        val kind: VolumeKind,
        val baseName: String,
        /** The file the reader engine must open. */
        val primary: File,
        /** Every volume of the set, ordered. */
        val parts: List<File>
    ) {
        val isMultiVolume: Boolean get() = parts.size > 1
        val totalBytes: Long get() = parts.sumOf { it.length() }
    }

    private data class Descriptor(
        val key: String,
        val format: ArchiveFormat,
        val kind: VolumeKind,
        val volumeIndex: Int,
        /** Original-case base name, used for the destination folder. */
        val displayName: String
    )

    /**
     * Resolves the complete volume set that contains [file], scanning its folder
     * for the sibling volumes.
     */
    fun resolve(file: File): VolumeSet {
        val descriptor = describe(file) ?: Descriptor(
            key = file.absolutePath,
            format = ArchiveFormat.detect(file),
            kind = VolumeKind.SINGLE,
            volumeIndex = 0,
            displayName = file.nameWithoutExtension
        )

        val siblings = collectSiblings(file, descriptor)
        val parts = if (siblings.isEmpty()) listOf(file) else siblings

        val primary = when (descriptor.kind) {
            // zip4j stores the tail in the .zip and the head in .z01 -> open the .zip
            VolumeKind.ZIP4J_SPLIT -> parts.firstOrNull { it.extension.equals("zip", true) } ?: parts.first()
            // every other layout is opened from its lowest numbered volume
            else -> parts.firstOrNull { volumeIndex(it, descriptor) == minVolumeIndex(parts, descriptor) }
                ?: parts.first()
        }

        return VolumeSet(
            format = descriptor.format,
            kind = descriptor.kind,
            baseName = descriptor.displayName,
            primary = primary,
            parts = parts
        )
    }

    /**
     * Groups a multi-selection into coherent volume sets. Selecting three parts of
     * the same game yields a single set, never three independent extractions.
     */
    fun resolveAll(selected: List<File>): List<VolumeSet> {
        if (selected.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<File>>()
        selected.forEach { file ->
            val descriptor = describe(file)
            val key = if (descriptor != null) {
                "${descriptor.kind}|${descriptor.format.id}|${descriptor.key}"
            } else {
                "single|${file.absolutePath}"
            }
            grouped.getOrPut(key) { mutableListOf() }.add(file)
        }
        return grouped.values.map { group ->
            // Complete every group from disk so one selected part yields the whole set.
            val resolved = group.map { resolve(it) }
            val kind = resolved.first().kind
            val key = resolved.first().baseName
            val format = resolved.first().format
            val allParts = resolved
                .flatMap { it.parts }
                .distinctBy { it.absolutePath }
                .sortedBy { volumeIndex(it, null) }
            VolumeSet(
                format = format,
                kind = kind,
                baseName = key,
                primary = resolved.first().primary,
                parts = allParts
            )
        }
    }

    private fun minVolumeIndex(parts: List<File>, descriptor: Descriptor): Int {
        var min = Int.MAX_VALUE
        parts.forEach { part ->
            val index = volumeIndex(part, descriptor)
            if (index in 1..9999 && index < min) min = index
        }
        return if (min == Int.MAX_VALUE) 1 else min
    }

    private fun volumeIndex(file: File, descriptor: Descriptor?): Int {
        val d = descriptor ?: describe(file) ?: return 0
        return d.volumeIndex
    }

    private fun collectSiblings(file: File, descriptor: Descriptor): List<File> {
        val parent = file.parentFile ?: return listOf(file)
        val candidates = try {
            parent.listFiles()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val matching = candidates.filter { candidate ->
            val d = describe(candidate)
            d != null && d.key == descriptor.key && d.kind == descriptor.kind &&
                d.format == descriptor.format
        }

        if (matching.isEmpty()) return listOf(file)

        return matching.sortedBy { candidate ->
            val d = describe(candidate)
            d?.volumeIndex ?: 0
        }
    }

    /**
     * Classifies a file into a volume set descriptor.
     */
    private fun describe(file: File): Descriptor? {
        val name = file.name
        val lower = name.lowercase()

        // Game.part1.rar / Game.part01.rar
        RAR_NEW.matchEntire(lower)?.let { m ->
            return Descriptor(
                m.groupValues[1], ArchiveFormat.RAR, VolumeKind.RAR_NEW,
                m.groupValues[2].toInt(), originalBase(name, ".part${m.groupValues[2]}.rar")
            )
        }

        // Game.r00 / Game.r01 (old style, the .rar itself is volume 1)
        RAR_OLD.matchEntire(lower)?.let { m ->
            return Descriptor(
                m.groupValues[1], ArchiveFormat.RAR, VolumeKind.RAR_OLD,
                m.groupValues[2].toInt() + 2, originalBase(name, ".r${m.groupValues[2]}")
            )
        }

        // Game.7z.001
        SEVEN_Z_SPLIT.matchEntire(lower)?.let { m ->
            return Descriptor(
                m.groupValues[1], ArchiveFormat.SEVEN_Z, VolumeKind.SEVEN_Z_SPLIT,
                m.groupValues[2].toInt(), originalBase(name, ".${m.groupValues[2]}")
            )
        }

        // Game.zip.001 (raw byte-stream split)
        ZIP_RAW_SPLIT.matchEntire(lower)?.let { m ->
            return Descriptor(
                m.groupValues[1], ArchiveFormat.ZIP, VolumeKind.ZIP_RAW_SPLIT,
                m.groupValues[2].toInt(), originalBase(name, ".${m.groupValues[2]}")
            )
        }

        // Game.z01 / Game.z02 (zip4j split; the .zip holds the tail)
        ZIP4J_SPLIT.matchEntire(lower)?.let { m ->
            return Descriptor(
                m.groupValues[1], ArchiveFormat.ZIP, VolumeKind.ZIP4J_SPLIT,
                m.groupValues[2].toInt(), originalBase(name, ".z${m.groupValues[2]}")
            )
        }

        // Plain single file archives
        when {
            lower.endsWith(".rar") -> return Descriptor(
                lower.removeSuffix(".rar"), ArchiveFormat.RAR, VolumeKind.SINGLE, 1,
                name.removeSuffix(".rar")
            )
            lower.endsWith(".7z") -> return Descriptor(
                lower.removeSuffix(".7z"), ArchiveFormat.SEVEN_Z, VolumeKind.SINGLE, 1,
                name.removeSuffix(".7z")
            )
            lower.endsWith(".zip") -> return Descriptor(
                lower.removeSuffix(".zip"), ArchiveFormat.ZIP, VolumeKind.SINGLE, 1,
                name.removeSuffix(".zip")
            )
        }

        return null
    }

    /**
     * Recovers the original-case base name by stripping the matched suffix from
     * the real file name (the grouping key is lowercased for comparison only).
     */
    private fun originalBase(fileName: String, lowerSuffix: String): String {
        if (fileName.length > lowerSuffix.length &&
            fileName.regionMatches(
                fileName.length - lowerSuffix.length,
                lowerSuffix,
                0,
                lowerSuffix.length,
                ignoreCase = true
            )
        ) {
            return fileName.substring(0, fileName.length - lowerSuffix.length)
        }
        return fileName.substringBeforeLast('.', fileName)
    }

    private val RAR_NEW = Regex("^(.+)\\.part(\\d+)\\.rar$")
    private val RAR_OLD = Regex("^(.+)\\.r(\\d+)$")
    private val SEVEN_Z_SPLIT = Regex("^(.+)\\.7z\\.(\\d{3})$")
    private val ZIP_RAW_SPLIT = Regex("^(.+)\\.zip\\.(\\d{3})$")
    private val ZIP4J_SPLIT = Regex("^(.+)\\.z(\\d{2})$")
}
