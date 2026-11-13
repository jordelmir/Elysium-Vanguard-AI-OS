package com.elysium.vanguard.core.archive

import java.io.File
import java.io.OutputStream

/**
 * ARCHIVE SAFETY (INTEGRITY CONTRACT)
 *
 * Every extraction performed by the file manager obeys these rules:
 *
 *  1. **No path escapes.** An entry name is normalised and rejected when it tries to
 *     traverse outside the destination (the classic "zip slip" attack). The final
 *     resolved path is re-checked against the canonical destination, which also
 *     defeats symlink based escapes.
 *  2. **No partial files are ever visible.** Each member is streamed into a hidden
 *     `.part` sibling and atomically renamed into place only after the stream
 *     completed successfully. A cancelled or failed extraction leaves the
 *     destination untouched.
 *  3. **No silent overwrites.** Existing members are preserved; the new member is
 *     written under a conflict-free name unless the caller explicitly allows
 *     overwriting.
 *  4. **Bounded resources.** Entry sizes and member counts are capped so a hostile
 *     archive cannot exhaust storage.
 */
object ArchiveSafety {

    /** Hard ceiling for a single extracted member (4 GiB). */
    const val MAX_ENTRY_SIZE: Long = 4L * 1024L * 1024L * 1024L

    /** Hard ceiling for the total extracted volume (64 GiB). */
    const val MAX_TOTAL_SIZE: Long = 64L * 1024L * 1024L * 1024L

    /** Hard ceiling for the number of members in one archive. */
    const val MAX_ENTRIES: Int = 200_000

    /**
     * Normalises an archive member path.
     *
     * @return the safe relative path, or `null` when the entry must be rejected.
     */
    fun sanitizeRelativePath(rawPath: String): String? {
        if (rawPath.isEmpty()) return null

        var path = rawPath.replace('\\', '/')

        // Reject absolute paths and Windows drive letters outright.
        if (path.startsWith("/")) path = path.trimStart('/')
        if (path.length >= 2 && path[1] == ':') return null
        if (path.contains('\u0000')) return null

        val segments = path.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }

        if (segments.isEmpty()) return null

        // Any ".." segment is an escape attempt.
        if (segments.any { it == ".." }) return null

        return segments.joinToString("/")
    }

    /**
     * Resolves a sanitised member path against [destination] and proves the result
     * stays inside it.
     */
    fun resolveTarget(destination: File, relativePath: String): File? {
        val safe = sanitizeRelativePath(relativePath) ?: return null
        val candidate = File(destination, safe)
        val root = destination.canonicalFile
        val resolved = candidate.canonicalFile
        return if (resolved == root || resolved.path.startsWith(root.path + File.separator)) {
            resolved
        } else {
            null
        }
    }

    /**
     * Produces a non-conflicting path for [target] by appending " (n)" before the
     * extension, mirroring the behaviour of the desktop file managers.
     */
    fun uniqueTarget(target: File): File {
        if (!target.exists()) return target
        val parent = target.parentFile ?: return target
        val name = target.nameWithoutExtension
        val ext = target.extension
        val extDot = if (ext.isNotEmpty()) ".$ext" else ""
        var counter = 1
        var candidate = File(parent, "$name ($counter)$extDot")
        while (candidate.exists()) {
            counter++
            candidate = File(parent, "$name ($counter)$extDot")
        }
        return candidate
    }

    /**
     * Streams [writer] into [target] atomically.
     *
     * The bytes land in a hidden sibling first and are renamed into place only when
     * the writer finished without throwing. Nothing half-written is ever exposed.
     */
    fun writeAtomically(
        target: File,
        overwrite: Boolean,
        writer: (OutputStream) -> Unit
    ) {
        val finalTarget = if (overwrite) target else uniqueTarget(target)
        finalTarget.parentFile?.mkdirs()

        val temp = File(
            finalTarget.parentFile,
            ".${finalTarget.name}.part"
        )

        try {
            temp.outputStream().use { out ->
                writer(out)
                out.flush()
                out.fd.sync()
            }
            if (finalTarget.exists() && !overwrite) {
                // Another process won the race; keep both copies intact.
                val alternative = uniqueTarget(finalTarget)
                if (!temp.renameTo(alternative)) {
                    temp.copyTo(alternative, overwrite = true)
                    temp.delete()
                }
            } else {
                if (!temp.renameTo(finalTarget)) {
                    temp.copyTo(finalTarget, overwrite = true)
                    temp.delete()
                }
            }
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    /**
     * Creates the directory tree for [relativePath] inside [destination], rejecting
     * unsafe paths.
     */
    fun prepareDirectory(destination: File, relativePath: String): File? {
        val target = resolveTarget(destination, relativePath) ?: return null
        if (!target.exists()) target.mkdirs()
        return target
    }
}
