package com.elysium.nexus.core.transport.mac

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.elysium.nexus.ui.MainActivity

/**
 * Interactive notification manager for Elysium Nexus Android client.
 *
 * Provides ongoing foreground notifications with real-time status and
 * quick actions (Disconnect, Switch Mode) while connected to the Mac.
 */
object MacControlNotificationManager {

    private const val CHANNEL_ID = "elysium_nexus_mac_control"
    private const val NOTIFICATION_ID = 78781

    const val ACTION_DISCONNECT = "com.elysium.nexus.action.DISCONNECT_MAC"
    const val ACTION_TOGGLE_SCREEN = "com.elysium.nexus.action.TOGGLE_SCREEN"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Elysium Nexus — Control Mac"
            val descriptionText = "Estado interactivo de la conexión remota con Mac"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showConnectedNotification(
        context: Context,
        hostName: String,
        hostIp: String,
        isScreenMirroring: Boolean
    ) {
        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_DISCONNECT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingDisconnect = PendingIntent.getActivity(
            context,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentStatus = if (isScreenMirroring) {
            "● Pantalla 1080p en vivo · Trackpad y mouse activos"
        } else {
            "● Modo Trackpad táctil activo"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("⚡ Elysium Nexus • $hostName")
            .setContentText(contentStatus)
            .setSubText(hostIp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", pendingDisconnect)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS granted
        }
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
