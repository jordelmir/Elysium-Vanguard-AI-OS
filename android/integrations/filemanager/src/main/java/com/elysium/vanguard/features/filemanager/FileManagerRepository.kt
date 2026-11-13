package com.elysium.vanguard.features.filemanager

import android.content.Context
import android.provider.MediaStore
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import com.elysium.vanguard.core.util.FileCategory
import com.elysium.vanguard.core.util.FileThematics
import com.elysium.vanguard.core.util.SafeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.elysium.vanguard.core.util.StorageStats

/**
 * TITAN FILE REPOSITORY (LIVE-WIRE IMPLEMENTATION)
 * Direct connection to the filesystem with a MediaStore safety net.
 *
 * PERFORMANCE CONTRACT:
 *  - A directory scan NEVER performs per-child readdir calls. Folder child counts are
 *    resolved lazily by [countChildren] so the first paint is immediate.
 *  - [MimeTypeMap] is resolved once instead of once per file.
 *  - Recently visited directories are served from a bounded LRU cache.
 */
@Singleton
class FileManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mimeTypeMap: MimeTypeMap by lazy { MimeTypeMap.getSingleton() }

    /** Bounded LRU cache of recently scanned directories (path -> listing). */
    private val directoryCache = object : LinkedHashMap<String, List<TitanFile>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TitanFile>>?): Boolean =
            size > MAX_CACHED_DIRECTORIES
    }

    /**
     * Scans a directory and emits its entries.
     *
     * When direct filesystem access is refused (scoped storage edge cases, OEM
     * restrictions on /Android/data), the scan transparently falls back to
     * MediaStore so the user never sees a phantom "empty" folder.
     */
    fun getFiles(path: String): Flow<List<TitanFile>> = flow {
        try {
            val directory = File(path)
            val exists = directory.exists() && directory.isDirectory

            if (!exists) {
                emit(emptyList())
                return@flow
            }

            val raw = directory.listFiles()
            val listing = if (raw != null) {
                raw.map { toTitanFile(it) }
            } else {
                // Direct listing refused -> ask the media index instead of lying with "empty".
                val fallback = queryMediaStore(path)
                if (fallback.isEmpty()) {
                    SafeLog.w(TAG, "listFiles() denied and MediaStore empty for: $path")
                }
                fallback
            }

            val sorted = listing.sortedWith(
                compareByDescending<TitanFile> { it.isFolder }.thenBy { it.name.lowercase() }
            )

            synchronized(directoryCache) {
                directoryCache[path] = sorted
            }

            emit(sorted)
        } catch (e: Exception) {
            SafeLog.e(TAG, "Critical crash in getFiles: ${e.message}", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Counts the direct children of a folder without materialising File objects
     * for every descendant. Used for progressive, off the critical path enrichment.
     */
    fun countChildren(path: String): Int = try {
        File(path).list()?.size ?: -1
    } catch (_: Exception) {
        -1
    }

    /**
     * Size of a folder in bytes, computed in the background so the listing is
     * never blocked.
     */
    fun folderSizeBytes(path: String): Long = try {
        getFolderSizeRecursive(File(path))
    } catch (_: Exception) {
        0L
    }

    /**
     * Returns a cached listing for [path] when it is still warm.
     */
    fun cachedListing(path: String): List<TitanFile>? = synchronized(directoryCache) {
        directoryCache[path]
    }

    fun clearCache() {
        synchronized(directoryCache) { directoryCache.clear() }
    }

    private fun toTitanFile(file: File): TitanFile {
        val isDirectory = file.isDirectory
        val category = FileThematics.getCategory(file.name, isDirectory)
        return TitanFile(
            name = file.name,
            isFolder = isDirectory,
            // Folder size is resolved later by countChildren(): never pay a readdir per child here.
            size = if (isDirectory) "" else Formatter.formatFileSize(context, file.length()),
            path = file.absolutePath,
            mimeType = if (isDirectory) {
                "resource/folder"
            } else {
                mimeTypeMap.getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
            },
            category = category,
            thematicColor = FileThematics.getCategoryColor(category),
            lastModified = file.lastModified(),
            permissions = getPermissionsString(file)
        )
    }

    /**
     * MediaStore safety net for directories the filesystem refuses to list.
     * Best effort by design: it reports what the media index knows, nothing more.
     */
    private fun queryMediaStore(path: String): List<TitanFile> {
        val results = mutableListOf<TitanFile>()
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("$path/%")

            context.contentResolver.query(
                collection, projection, selection, selectionArgs, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val fullPath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    if (fullPath == null || name == null) continue
                    if (!fullPath.startsWith("$path/")) continue

                    val isDirectory = fullPath.endsWith("/")
                    val displayName = if (isDirectory) {
                        fullPath.trimEnd('/').substringAfterLast('/')
                    } else {
                        name
                    }
                    val category = FileThematics.getCategory(displayName, isDirectory)
                    results += TitanFile(
                        name = displayName,
                        isFolder = isDirectory,
                        size = if (isDirectory) "" else Formatter.formatFileSize(
                            context,
                            if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        ),
                        path = fullPath.trimEnd('/'),
                        mimeType = if (isDirectory) {
                            "resource/folder"
                        } else {
                            (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null)
                                ?: mimeTypeMap.getMimeTypeFromExtension(displayName.substringAfterLast(".", "").lowercase())
                                ?: "application/octet-stream"
                        },
                        category = category,
                        thematicColor = FileThematics.getCategoryColor(category),
                        lastModified = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        permissions = "[rw-]"
                    )
                }
            }
        } catch (e: Exception) {
            SafeLog.w(TAG, "MediaStore fallback failed for $path: ${e.message}")
        }
        return results
    }

    private fun getPermissionsString(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "[$r$w$x]"
    }

    /**
     * Detection Logic for the "Common Third-Party Folders" requirement.
     */
    fun getCommonShortcuts(): List<Pair<String, String>> {
        val base = "/storage/emulated/0"
        val possible = listOf(
            "Music" to "$base/Music",
            "Pictures" to "$base/Pictures",
            "Downloads" to "$base/Download",
            "Videos" to "$base/Download/videos",
            "DCIM" to "$base/DCIM",
            "Android" to "$base/Android",
            "WhatsApp" to "$base/Android/media/com.whatsapp/WhatsApp/Media",
            "Documents" to "$base/Documents",
            "Snaptube" to "$base/snaptube",
            "Tencent" to "$base/Tencent",
            "PSP" to "$base/PSP"
        )
        return possible.filter { File(it.second).exists() }
    }

    /**
     * INDUSTRIAL FILE OPERATIONS (MASTER ORDER)
     * Includes automatic conflict resolution (renaming if target exists).
     */
    private fun resolveConflictPath(destPath: String): String {
        var file = File(destPath)
        if (!file.exists()) return destPath

        val parent = file.parent
        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extDot = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        var newFile = File(parent, "$nameWithoutExt ($counter)$extDot")
        while (newFile.exists()) {
            counter++
            newFile = File(parent, "$nameWithoutExt ($counter)$extDot")
        }
        return newFile.absolutePath
    }

    /**
     * Outcome of a copy or move run.
     */
    data class TransferReport(
        val transferred: Int,
        val failed: Int,
        val bytesTransferred: Long
    )

    /**
     * Copies (or moves) the given paths into [destination] reporting real progress.
     *
     * The percentage counts the members actually transferred, so moving a 40 GB
     * folder shows an honest 0-100% instead of freezing the interface.
     *
     * @param listener receives (percentage, currentPath).
     */
    fun transferWithProgress(
        sources: List<String>,
        destination: File,
        move: Boolean,
        listener: (Int, String) -> Unit
    ): TransferReport {
        if (!destination.exists()) destination.mkdirs()

        // First pass: count the members so the progress bar is honest.
        var totalMembers = 0
        val plans = mutableListOf<Pair<File, File>>()
        sources.forEach { path ->
            val source = File(path)
            if (!source.exists()) return@forEach
            val target = File(destination, source.name)
            plans += source to target
            totalMembers += if (source.isDirectory) source.walkTopDown().count() else 1
        }

        val total = totalMembers.coerceAtLeast(1)
        var processed = 0
        var transferred = 0
        var failed = 0
        var bytes = 0L

        plans.forEach { (source, target) ->
            try {
                if (source.isDirectory) {
                    val finalTarget = if (target.exists()) resolveConflictPath(target.absolutePath) else target.absolutePath
                    val root = File(finalTarget)
                    root.mkdirs()
                    source.walkTopDown().forEach { member ->
                        val relative = member.relativeTo(source).path
                        val memberTarget = File(root, relative)
                        if (member.isDirectory) {
                            memberTarget.mkdirs()
                        } else {
                            memberTarget.parentFile?.mkdirs()
                            member.copyTo(memberTarget, overwrite = true)
                            bytes += member.length()
                        }
                        transferred++
                        processed++
                        listener(
                            ((processed * 100L) / total).toInt().coerceIn(0, 100),
                            memberTarget.path
                        )
                    }
                    if (move) source.deleteRecursively()
                } else {
                    val finalTarget =
                        if (target.exists()) resolveConflictPath(target.absolutePath) else target.absolutePath
                    if (move && source.renameTo(File(finalTarget))) {
                        bytes += source.length()
                    } else {
                        source.copyTo(File(finalTarget), overwrite = true)
                        if (move) source.delete()
                    }
                    transferred++
                    processed++
                    listener(((processed * 100L) / total).toInt().coerceIn(0, 100), finalTarget)
                }
            } catch (_: Exception) {
                failed++
                processed++
                listener(((processed * 100L) / total).toInt().coerceIn(0, 100), source.path)
            }
        }

        listener(100, "COMPLETE")
        return TransferReport(transferred, failed, bytes)
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Outcome of a deletion run.
     */
    data class DeleteReport(
        val deleted: Int,
        val failed: Int,
        val bytesFreed: Long
    )

    /**
     * Deletes the given paths while reporting real progress.
     *
     * The percentage is derived from the number of members actually removed, so a
     * 40 GB folder reports a truthful 0-100% instead of freezing the interface.
     *
     * @param listener receives (percentage, currentPath).
     */
    fun deleteWithProgress(paths: List<String>, listener: (Int, String) -> Unit): DeleteReport {
        // First pass: count the members so the progress bar is honest.
        val targets = mutableListOf<File>()
        var totalMembers = 0
        paths.forEach { path ->
            val file = File(path)
            if (!file.exists()) return@forEach
            val members = if (file.isDirectory) {
                file.walkTopDown().count()
            } else {
                1
            }
            totalMembers += members
            targets += file
        }

        var processed = 0
        var deleted = 0
        var failed = 0
        var bytesFreed = 0L

        val total = totalMembers.coerceAtLeast(1)

        targets.forEach { target ->
            if (target.isDirectory) {
                // Delete depth first so the folders can be removed afterwards.
                val members = target.walkBottomUp().toList()
                members.forEach { member ->
                    val size = if (member.isFile) member.length() else 0L
                    if (member.delete()) {
                        deleted++
                        bytesFreed += size
                    } else {
                        failed++
                    }
                    processed++
                    listener(
                        ((processed * 100L) / total).toInt().coerceIn(0, 100),
                        member.path
                    )
                }
            } else {
                val size = target.length()
                if (target.delete()) {
                    deleted++
                    bytesFreed += size
                } else {
                    failed++
                }
                processed++
                listener(((processed * 100L) / total).toInt().coerceIn(0, 100), target.path)
            }
        }

        listener(100, "COMPLETE")
        return DeleteReport(deleted, failed, bytesFreed)
    }

    fun copyFile(sourcePath: String, destPath: String, autoRename: Boolean = true): Boolean {
        return try {
            val source = File(sourcePath)
            val finalDestPath = if (autoRename) resolveConflictPath(destPath) else destPath
            val dest = File(finalDestPath)

            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = !autoRename)
            } else {
                source.copyTo(dest, overwrite = !autoRename)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun moveFile(sourcePath: String, destPath: String, autoRename: Boolean = true): Boolean {
        return try {
            val source = File(sourcePath)
            val finalDestPath = if (autoRename) resolveConflictPath(destPath) else destPath
            val dest = File(finalDestPath)

            if (source.renameTo(dest)) {
                true
            } else {
                // Fallback if move across partitions
                if (copyFile(sourcePath, finalDestPath, autoRename = false)) {
                    deleteFile(sourcePath)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun renameFile(path: String, newName: String): Boolean {
        return try {
            val source = File(path)
            val dest = File(source.parent, newName)
            source.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Real device storage, read from the single shared source so the
     * dashboard and the file manager always show the same numbers.
     */
    fun getStorageStats(): StorageStats = com.elysium.vanguard.core.util.StorageMetrics.read(context)

    fun getFolderSizeRecursive(file: File): Long {
        if (!file.exists()) return 0L
        if (!file.isDirectory) return file.length()

        var size = 0L
        file.listFiles()?.forEach {
            size += if (it.isDirectory) getFolderSizeRecursive(it) else it.length()
        }
        return size
    }

    companion object {
        private const val TAG = "TitanFileManager"
        private const val MAX_CACHED_DIRECTORIES = 12
    }
}

