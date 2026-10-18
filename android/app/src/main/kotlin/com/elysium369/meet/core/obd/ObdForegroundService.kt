package com.elysium369.meet.core.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elysium369.meet.MainActivity
import com.elysium369.meet.core.alerts.AlertManager
import com.elysium369.meet.core.operations.ActiveOperationState
import com.elysium369.meet.core.operations.ActiveOperationType
import com.elysium369.meet.core.operations.ActiveOperationsKernel
import com.elysium369.meet.core.operations.ActiveOperationScope
import com.elysium369.meet.core.trips.TripManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ObdForegroundService — Manual-Intent & Truthful Kernel V3.
 *
 * Laws:
 * - START_NOT_STICKY: Never auto-restart without user launching a diagnostic session.
 * - Observation-only watchdog: Never triggers transport.reconnect() or obdSession.connect().
 * - When disconnected, stops itself cleanly instead of looping.
 */
@AndroidEntryPoint
class ObdForegroundService : Service() {

    @Inject lateinit var obdSession: ObdSession
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var tripManager: TripManager
    @Inject lateinit var activeOpsKernel: ActiveOperationsKernel

    private var operationId: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "obd_channel"
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAtMs: Long = 0L
    private var telemetryJob: Job? = null
    private var watchdogJob: Job? = null
    
    val liveData: StateFlow<Map<String, Float>> get() = obdSession.liveData
    val connectionState: StateFlow<ObdState> get() = obdSession.state

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val connect = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val scan = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return connect || scan
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vehicleId = intent?.getStringExtra("vehicle_id") ?: "unknown_vehicle"
        
        createNotificationChannel()
        acquireWakeLock()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasBluetoothPermissions()) {
                startForeground(
                    NOTIF_ID,
                    buildNotification("Sesión OBD activa"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIF_ID, buildNotification("Sesión OBD activa"))
            }
        } catch (e: SecurityException) {
            Log.e("ObdForegroundService", "SecurityException on startForeground, retrying without type", e)
            try {
                startForeground(NOTIF_ID, buildNotification("Sesión OBD activa"))
            } catch (e2: Exception) {
                Log.e("ObdForegroundService", "Could not start foreground at all, stopping", e2)
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Unexpected error on startForeground", e)
            try {
                startForeground(NOTIF_ID, buildNotification("Sesión OBD activa"))
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // If obdSession is disconnected and no connection is requested, do not run service
        val truth = obdSession.connectionTruth.value
        if (truth.intent != ConnectionIntent.CONNECT_REQUESTED && obdSession.state.value == ObdState.DISCONNECTED) {
            Log.i("ObdForegroundService", "Stopping service: No active connection intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val op = activeOpsKernel.register(
            operationType = ActiveOperationType.OBD_SESSION,
            scope = ActiveOperationScope.FOREGROUND_SERVICE_SCOPED,
            vehicleId = vehicleId,
            metadata = mapOf("sessionId" to java.util.UUID.randomUUID().toString()),
        )
        operationId = op.operationId
        Log.i("ObdForegroundService", "Registered active operation: ${op.operationId}")

        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            alertManager.startMonitoring(liveData, serviceScope)
            
            val sessionId = java.util.UUID.randomUUID().toString()
            tripManager.startMonitoring(vehicleId, sessionId)
            
            var lastUpdate = 0L
            liveData.collect { data ->
                if (data.isEmpty()) return@collect
                
                val now = System.currentTimeMillis()
                val isCritical = data["0105"]?.let { it > 115f } ?: false
                
                if (now - lastUpdate > 3000 || isCritical) {
                    val temp = data["0105"]?.toInt()?.toString() ?: "--"
                    val rpm = data["010C"]?.toInt()?.toString() ?: "--"
                    val speed = data["010D"]?.toInt()?.toString() ?: "--"
                    val alertText = if (isCritical) "⚠️ ¡SOBRECALENTAMIENTO! " else ""
                    val text = "${alertText}Motor: ${temp}°C | ${rpm} RPM | ${speed} km/h"
                    
                    try {
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager?.notify(NOTIF_ID, buildNotification(text))
                        lastUpdate = now
                    } catch (_: Exception) {}
                }
            }
        }

        startContinuityWatchdog()
        
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val now = System.currentTimeMillis()
            if (wakeLock?.isHeld == true && now - wakeLockAcquiredAtMs < WAKE_LOCK_RENEW_MS) return
            releaseWakeLock()
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElysiumVanguard:ObdForegroundServiceWakeLock").apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLockAcquiredAtMs = now
            Log.d("ObdForegroundService", "WakeLock acquired successfully")
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("ObdForegroundService", "WakeLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e("ObdForegroundService", "Failed to release WakeLock", e)
        } finally {
            wakeLock = null
            wakeLockAcquiredAtMs = 0L
        }
    }

    private fun startContinuityWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                acquireWakeLock()

                val now = System.currentTimeMillis()
                val state = connectionState.value

                when (state) {
                    ObdState.CONNECTED -> {
                        val silenceMs = obdSession.liveDataSilenceMs(now)
                        if (silenceMs > LIVE_DATA_STALE_MS) {
                            // Observation only. ObdLinkHealthCoordinator is the sole recovery authority.
                            updateNotification("Telemetría pausada; enlace bajo observación")
                            obdSession.healthCoordinator.onTelemetryStale()
                        }
                    }
                    ObdState.ERROR, ObdState.DISCONNECTED -> {
                        // When disconnected or error: Never auto-reconnect!
                        // Update status and stop service cleanly.
                        val truth = obdSession.connectionTruth.value
                        if (truth.intent == ConnectionIntent.DISCONNECTED) {
                            Log.i("ObdForegroundService", "Watchdog: Connection is DISCONNECTED under manual intent. Stopping service.")
                            updateNotification("Sesión OBD finalizada")
                            stopSelf()
                            break
                        }
                    }
                    ObdState.CONNECTING, ObdState.NEGOTIATING -> Unit
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Elysium Diagnóstico OBD2 & LiveLink", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitoreo en tiempo real de motor, DTCs y telemetría vehicular"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = "OPEN_SCANNER"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Elysium Vanguard — Diagnóstico OBD2")
            .setContentText(text)
            .setSmallIcon(com.elysium369.meet.R.drawable.ic_elysium_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    override fun onDestroy() {
        operationId?.let { activeOpsKernel.complete(it) }
        runBlocking(Dispatchers.IO) {
            try {
                tripManager.endTrip()
            } catch (_: Exception) {}
        }
        telemetryJob?.cancel()
        watchdogJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LIVE_DATA_STALE_MS = 18_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 24 * 60 * 1000L
    }
}
