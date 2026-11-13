package com.elysium.vanguard.core.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter

/**
 * STORAGE METRICS
 *
 * Single source of truth for the real device storage. Both the dashboard and
 * the file manager read from here, so they can never show different numbers.
 *
 * The labels use [Formatter], which reports decimal gigabytes (10^9), the way
 * the phone storage screen does.
 */
data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    /** Total size, for example "492 GB". */
    val totalLabel: String,
    /** Used space, for example "319 GB". */
    val usedLabel: String,
    /** Free space, for example "161 GB". */
    val freeLabel: String,
    /** Percentage of the storage in use, 0..100. */
    val percentUsed: Int
)

object StorageMetrics {

    /**
     * Reads the real storage of the shared internal volume.
     */
    fun read(context: Context): StorageStats {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val availableBytes = stat.availableBlocksLong * blockSize
            val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

            StorageStats(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                availableBytes = availableBytes,
                totalLabel = Formatter.formatShortFileSize(context, totalBytes),
                usedLabel = Formatter.formatShortFileSize(context, usedBytes),
                freeLabel = formatFreeSpace(availableBytes),
                percentUsed = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0
            )
        } catch (_: Exception) {
            StorageStats(0, 0, 0, "0 GB", "0 GB", "0 MB FREE", 0)
        }
    }

    /**
     * Free space the way the user asked for it: whole gigabytes, megabytes
     * when there is not a full gigabyte left.
     */
    fun formatFreeSpace(bytes: Long): String {
        if (bytes <= 0L) return "0 MB FREE"
        val gb = bytes / (1024L * 1024L * 1024L)
        if (gb >= 1L) return "$gb GB FREE"
        val mb = bytes / (1024L * 1024L)
        if (mb >= 1L) return "$mb MB FREE"
        return "${bytes / 1024L} KB FREE"
    }
}
