package com.elysium.vanguard.core.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.elysium.vanguard.core.archive.ArchiveEngine
import com.elysium.vanguard.core.archive.ArchiveException
import com.elysium.vanguard.core.archive.ArchiveExtractionRequest
import com.elysium.vanguard.core.archive.ArchiveProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * ARCHIVE SERVICE (SOVEREIGN BACKGROUND ENGINE)
 *
 * Runs the universal extraction engine inside a Foreground Service so long
 * extractions survive the UI, report real progress, can be cancelled, and
 * support password protected archives.
 */
class ArchiveService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "archive_service_channel"
        private const val NOTIFICATION_ID = 102

        const val ACTION_EXTRACT = "com.elysium.vanguard.ACTION_EXTRACT"
        const val ACTION_CANCEL = "com.elysium.vanguard.ACTION_ARCHIVE_CANCEL"

        const val EXTRA_FILES = "archive_files"
        const val EXTRA_DESTINATION = "archive_destination"
        const val EXTRA_PASSWORD = "archive_password"
        const val EXTRA_OVERWRITE = "archive_overwrite"
        const val EXTRA_INCLUDE = "archive_include"
        const val EXTRA_KEEP_SCREEN_ON = "archive_keep_screen_on"

        const val BROADCAST_PROGRESS = "com.elysium.vanguard.ARCHIVE_PROGRESS"
        const val BROADCAST_RESULT = "com.elysium.vanguard.ARCHIVE_RESULT"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_CANCEL) {
            currentJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val files = intent?.getStringArrayExtra(EXTRA_FILES)?.map { File(it) }
        val destination = intent?.getStringExtra(EXTRA_DESTINATION)?.let { File(it) }
        val password = intent?.getStringExtra(EXTRA_PASSWORD)
        val overwrite = intent?.getBooleanExtra(EXTRA_OVERWRITE, false) ?: false
        val includePaths = intent?.getStringArrayExtra(EXTRA_INCLUDE)?.toSet()
        val keepScreenOn = intent?.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, false) ?: false

        if (files.isNullOrEmpty() || destination == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (keepScreenOn) acquireWakeLock()

        startForeground(NOTIFICATION_ID, createNotification("Preparing extraction...", 0))

        currentJob = serviceScope.launch {
            val scope = this

            // DEFENCE IN DEPTH: the UI already performs this check, but the service
            // must never fill the device when it is started on its own.
            val preflight = runCatching {
                val required = files.sumOf { file ->
                    ArchiveEngine.inspect(file).getOrNull()?.totalSize ?: 0L
                }
                val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
                val available = stat.availableBlocksLong * stat.blockSizeLong
                required to available
            }.getOrNull()

            if (preflight != null && preflight.first > preflight.second) {
                val message = "NOT ENOUGH SPACE: needs ${preflight.first / (1024 * 1024)} MB, " +
                    "only ${preflight.second / (1024 * 1024)} MB free"
                stopForeground(STOP_FOREGROUND_REMOVE)
                broadcastProgress(-1, message, true)
                broadcastResult(false, message, "INSUFFICIENT_SPACE")
                stopSelf()
                return@launch
            }

            val result = ArchiveEngine.extractAll(
                files = files,
                destination = destination,
                password = password?.takeIf { it.isNotEmpty() },
                overwrite = overwrite,
                include = includePaths?.let { selected -> { path: String -> path in selected } },
                listener = ArchiveProgressListener { percent, entry ->
                    if (!scope.isActive) throw CancellationException("User cancelled")
                    updateNotification(entry, percent)
                    broadcastProgress(percent, entry)
                }
            )

            result.onSuccess { reports ->
                val totalFiles = reports.sumOf { it.extractedFiles }
                val totalBytes = reports.sumOf { it.extractedBytes }
                stopForeground(STOP_FOREGROUND_DETACH)
                updateNotification("Extracted $totalFiles files successfully", 100, true)
                broadcastProgress(100, "COMPLETE", true)
                broadcastResult(true, "$totalFiles files extracted (${totalBytes / 1024} KB)", null)
            }.onFailure { error ->
                if (error is CancellationException) {
                    broadcastProgress(0, "CANCELLED", true)
                    broadcastResult(false, "Extraction cancelled", null)
                } else {
                    val message = when (error) {
                        is ArchiveException.PasswordRequired -> "PASSWORD REQUIRED"
                        is ArchiveException.WrongPassword -> "WRONG PASSWORD"
                        is ArchiveException.Corrupted -> "ARCHIVE DAMAGED: ${error.detail}"
                        is ArchiveException.UnsafeEntry -> "UNSAFE ENTRY BLOCKED"
                        is ArchiveException.Unsupported -> "FORMAT NOT SUPPORTED"
                        else -> error.message ?: "EXTRACTION FAILED"
                    }
                    updateNotification(message, 0, true)
                    broadcastProgress(-1, message, true)
                    broadcastResult(false, message, error.javaClass.simpleName)
                }
            }

            releaseWakeLock()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun broadcastProgress(percentage: Int, currentFile: String, done: Boolean = false) {
        sendBroadcast(
            Intent(BROADCAST_PROGRESS).apply {
                setPackage(packageName)
                putExtra("percentage", percentage)
                putExtra("currentFile", currentFile)
                putExtra("done", done)
            }
        )
    }

    private fun broadcastResult(success: Boolean, message: String, errorType: String?) {
        sendBroadcast(
            Intent(BROADCAST_RESULT).apply {
                setPackage(packageName)
                putExtra("success", success)
                putExtra("message", message)
                putExtra("errorType", errorType)
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Archive Operations",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int, done: Boolean = false): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (done) "Extraction Finished" else "Extracting Archives...")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(!done)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun updateNotification(content: String, progress: Int, done: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(content, progress, done))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ElysiumVanguard:ArchiveWakeLock"
        )
        wakeLock?.acquire(30 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
