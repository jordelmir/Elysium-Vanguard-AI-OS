package com.elysium369.meet.ride.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elysium369.meet.MainActivity
import com.elysium369.meet.communications.DeviceMessageCipher
import com.elysium369.meet.core.operations.ActiveOperationScope
import com.elysium369.meet.core.operations.ActiveOperationType
import com.elysium369.meet.core.operations.ActiveOperationsKernel
import com.elysium369.meet.ride.work.RideLocationBreadcrumb
import com.elysium369.meet.ride.work.RideLocationBreadcrumbWorker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Visible, user-controlled foreground tracking for an assigned active trip.
 * Exact samples are encrypted before entering WorkManager and delivered later
 * when connectivity returns. The service is never started for passengers.
 */
@AndroidEntryPoint
class RideLocationTrackingService : Service() {
    @Inject lateinit var cipher: DeviceMessageCipher
    @Inject lateinit var activeOpsKernel: ActiveOperationsKernel

    private var operationId: String? = null

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var activeTripId: String? = null
    private var lastCaptureAt = 0L
    private var lastSequence = 0L
    private var requestingUpdates = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val tripId = activeTripId ?: return
            val location = result.lastLocation ?: return
            enqueueIfUsable(tripId, location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        // A restarted service must receive a fresh explicit lease. Reusing a
        // global preference could resume exact GPS for a stale trip/account.
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID)?.takeIf { it.isNotBlank() }
            ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
        activeTripId = tripId
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(KEY_ACTIVE_TRIP, tripId).apply()
        startVisibleNotification()
        startLocationUpdates()

        val op = activeOpsKernel.register(
            operationType = ActiveOperationType.RIDE_LOCATION_TRACKING,
            scope = ActiveOperationScope.FOREGROUND_SERVICE_SCOPED,
            rideId = tripId,
            metadata = mapOf("sampleIntervalMs" to SAMPLE_INTERVAL_MS.toString()),
        )
        operationId = op.operationId

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        operationId?.let { activeOpsKernel.complete(it) }
        locationClient.removeLocationUpdates(callback)
        requestingUpdates = false
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        if (requestingUpdates || !hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SAMPLE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_SAMPLE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_SAMPLE_DISTANCE_METERS)
            .build()
        try {
            locationClient.requestLocationUpdates(request, callback, mainLooper)
            requestingUpdates = true
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun enqueueIfUsable(tripId: String, location: Location) {
        val now = System.currentTimeMillis()
        val capturedAt = location.time.takeIf { it > 0L } ?: now
        if (now - capturedAt > MAX_LOCATION_AGE_MS || location.accuracy !in 0f..MAX_ACCURACY_METERS) return
        if (now - lastCaptureAt < MIN_SAMPLE_INTERVAL_MS) return
        lastCaptureAt = now
        lastSequence = maxOf(capturedAt, lastSequence + 1L)
        val breadcrumb = RideLocationBreadcrumb(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            headingDegrees = location.bearing.toInt().takeIf { location.hasBearing() && it in 0..359 },
            speedMetersPerSecond = location.speed.coerceIn(0f, 100f).takeIf { location.hasSpeed() },
            capturedAtEpochMs = capturedAt,
        )
        RideLocationBreadcrumbWorker.enqueue(
            context = applicationContext,
            cipher = cipher,
            tripId = tripId,
            sequence = lastSequence,
            sample = breadcrumb,
        )
        // Also record locally for forensic PDF export
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            GpsTrailRecorder.recordPoint(
                context = applicationContext,
                rideId = tripId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                speedMetersPerSecond = breadcrumb.speedMetersPerSecond,
                headingDegrees = breadcrumb.headingDegrees,
                capturedAtEpochMs = capturedAt,
            )
        }
    }

    private fun startVisibleNotification() {
        val openApp = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.elysium369.meet.R.drawable.ic_elysium_notification)
            .setContentTitle("🚕 Elysium Viajes: Rastreo de Ruta en Vivo")
            .setContentText("Navegación GPS y telemetría de viaje activa. Seguridad forense habilitada.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopTracking() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().remove(KEY_ACTIVE_TRIP).apply()
        activeTripId = null
        locationClient.removeLocationUpdates(callback)
        requestingUpdates = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Elysium Viajes: Rastreo de Ruta", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Telemetría y navegación GPS de viaje activo"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            },
        )
    }

    companion object {
        private const val ACTION_START = "com.elysium369.meet.ride.location.START"
        private const val ACTION_STOP = "com.elysium369.meet.ride.location.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val PREFERENCES = "meet_ride_tracking"
        private const val KEY_ACTIVE_TRIP = "active_trip_id"
        private const val CHANNEL_ID = "elysium_ride_safety_tracking"
        private const val NOTIFICATION_ID = 7_301
        private const val SAMPLE_INTERVAL_MS = 15_000L
        private const val MIN_SAMPLE_INTERVAL_MS = 12_000L
        private const val MAX_LOCATION_AGE_MS = 30_000L
        private const val MAX_ACCURACY_METERS = 100f
        private const val MIN_SAMPLE_DISTANCE_METERS = 5f

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, RideLocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(KEY_ACTIVE_TRIP).apply()
            context.stopService(Intent(context, RideLocationTrackingService::class.java))
        }
    }
}
