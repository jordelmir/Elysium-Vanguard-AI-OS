package com.jsm.core.webrtc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.elysium369.meet.R

/** Foreground service for a user-approved screen capture session. */
class CaptureForegroundService : Service() {

    companion object {
        // New ID prevents older installs from retaining the previous high-priority channel settings.
        const val CHANNEL_ID = "elysium_screen_share_v2"
        const val NOTIFICATION_ID = 999
        @Volatile
        var isForegroundActive: Boolean = false
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (e: Exception) {
            isForegroundActive = false
            android.util.Log.e("CaptureForegroundService", "Could not start media-projection foreground service", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        // Evita que la CPU entre en sueño profundo (Doze) y mate el hilo C++ de WebRTC
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElysiumVanguard::StreamingWakeLock")
        wakeLock?.acquire(60 * 60 * 1000L)

        // Mantiene el chip de WiFi activo a máxima potencia, crucial para WebRTC P2P
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ElysiumVanguard::StreamingWifiLock")
        wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, com.jsm.core.MainActivity::class.java)
            .setAction(com.jsm.core.MainActivity.ACTION_STOP_SCREEN_SHARE)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compartición de pantalla activa")
            .setContentText("Elysium está transmitiendo tu pantalla. Toca Detener para finalizar.")
            .setSmallIcon(R.drawable.ic_elysium_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_elysium_notification, "Detener", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Compartición de pantalla",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica cuándo Elysium está compartiendo la pantalla y permite detener la sesión."
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Usado exclusivamente para Background Lifecycle, no binding IPC.
    }

    override fun onDestroy() {
        isForegroundActive = false
        super.onDestroy()
        releaseLocks()
    }
}
