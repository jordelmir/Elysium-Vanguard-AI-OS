package com.elysium.nexus.core.transport.mac

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elysium.nexus.R
import com.elysium.nexus.ui.MainActivity

/** Visible, user-stoppable foreground service while Android's screen is being transmitted. */
class NexusScreenShareService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Compartir pantalla de Nexus", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NexusGlobalSessionRegistry.active?.stopAndroidScreenShare()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val grant = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_GRANT)
        if (resultCode == 0 || grant == null) {
            Log.e(TAG, "MediaProjection grant missing; refusing to start screen share")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        try {
            requireNotNull(NexusGlobalSessionRegistry.active) {
                "Conecta con el Mac antes de compartir la pantalla de Android."
            }.startAndroidScreenShare(grant)
            Log.i(TAG, "Foreground MediaProjection share started with explicit Android consent")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start the Android screen stream", error)
            NexusGlobalSessionRegistry.active?.reportScreenShareFailure(
                error.message ?: "No se pudo iniciar la captura de Android."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NexusGlobalSessionRegistry.active?.stopAndroidScreenShare()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("Compartiendo pantalla con Elysium Nexus")
        .setContentText("La pantalla se transmite al dispositivo Mac emparejado.")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .addAction(
            0,
            "Detener",
            PendingIntent.getService(
                this,
                0,
                Intent(this, NexusScreenShareService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        const val ACTION_START = "com.elysium.nexus.action.START_GLOBAL_SCREEN_SHARE"
        const val ACTION_STOP = "com.elysium.nexus.action.STOP_GLOBAL_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_GRANT = "projection_grant"
        private const val CHANNEL_ID = "nexus_global_screen_share"
        private const val NOTIFICATION_ID = 7812
        private const val TAG = "NexusScreenShareService"

        fun start(context: Context, resultCode: Int, grant: Intent) {
            val intent = Intent(context, NexusScreenShareService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_PROJECTION_GRANT, grant)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NexusScreenShareService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
