package com.elysium.vanguard.core.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.elysium.vanguard.R
import com.elysium.vanguard.core.util.CompressionEngine
import com.elysium.vanguard.ui.theme.TitanColors
import kotlinx.coroutines.*
import java.io.File

/**
 * COMPRESSION SERVICE (SOVEREIGN BACKGROUND ENGINE)
 * Handles ZIP/UNZIP operations in a Foreground Service to ensure survival.
 */
class CompressionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "compression_service_channel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_COMPRESS = "com.elysium.vanguard.ACTION_COMPRESS"
        const val ACTION_DECOMPRESS = "com.elysium.vanguard.ACTION_DECOMPRESS"
        const val ACTION_CANCEL = "com.elysium.vanguard.ACTION_CANCEL"

        const val EXTRA_FILES = "extra_files"
        const val EXTRA_OUTPUT = "extra_output"
        const val EXTRA_KEEP_SCREEN_ON = "extra_keep_screen_on"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_PASSWORD = "extra_password"
    }

    private var currentJob: Job? = null

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
        val output = intent?.getStringExtra(EXTRA_OUTPUT)?.let { File(it) }
        val keepScreenOn = intent?.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, false) ?: false
        val formatName = intent?.getStringExtra(EXTRA_FORMAT)
        val password = intent?.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotEmpty() }
        val format = formatName?.let { name ->
            com.elysium.vanguard.core.util.CompressionFormat.entries.firstOrNull { it.id == name }
        } ?: com.elysium.vanguard.core.util.CompressionFormat.ZIP

        if (files != null && output != null) {
            if (keepScreenOn) acquireWakeLock()

            startForeground(NOTIFICATION_ID, createNotification("Initializing...", 0))

            currentJob = serviceScope.launch {
                val result = when (action) {
                    ACTION_COMPRESS -> CompressionEngine.compress(
                        files,
                        output,
                        format,
                        password,
                        object : CompressionEngine.ProgressListener {
                            override fun onProgress(percentage: Int, currentFile: String) {
                                if (!isActive) throw CancellationException("User cancelled")
                                updateNotification(currentFile, percentage)
                                broadcastProgress(percentage, currentFile)
                            }
                        }
                    )
                    ACTION_DECOMPRESS -> CompressionEngine.decompress(files.first(), output, null, object : CompressionEngine.ProgressListener {
                        override fun onProgress(percentage: Int, currentFile: String) {
                            if (!isActive) throw CancellationException("User cancelled")
                            updateNotification(currentFile, percentage)
                            broadcastProgress(percentage, currentFile)
                        }
                    })
                    else -> Result.failure(Exception("Unknown action"))
                }

                result.onSuccess {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    updateNotification("Operation Complete", 100, true)
                    broadcastProgress(100, "Complete", true)
                }.onFailure {
                    if (it is CancellationException) {
                        output.delete() // Cleanup partially created file/folder
                        broadcastProgress(0, "Cancelled", true)
                    } else {
                        updateNotification("Operation Failed: ${it.message}", 0, true)
                        broadcastProgress(-1, it.message ?: "Error", true)
                    }
                }

                releaseWakeLock()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun broadcastProgress(percentage: Int, currentFile: String, done: Boolean = false) {
        val intent = Intent("com.elysium.vanguard.COMPRESSION_PROGRESS").apply {
            setPackage(packageName)
            putExtra("percentage", percentage)
            putExtra("currentFile", currentFile)
            putExtra("done", done)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Operations",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int, done: Boolean = false): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (done) "File Operation Finished" else "Processing Files...")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(!done)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(content: String, progress: Int, done: Boolean = false) {
        val notification = createNotification(content, progress, done)
        val manager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElysiumVanguard:CompressionWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
