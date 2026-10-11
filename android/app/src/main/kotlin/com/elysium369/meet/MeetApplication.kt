package com.elysium369.meet

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import com.elysium369.meet.core.maintenance.DatabaseMaintenanceWorker
import com.elysium369.meet.core.obd.DtcDatabaseLoader
import com.elysium369.meet.core.vanguard.VanguardOutboxSyncWorker
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.ride.work.RideCommandSyncWorker
import com.elysium369.meet.platform.marketos.work.MarketOsSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MeetApplication : Application(), Configuration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        com.google.android.play.core.splitcompat.SplitCompat.install(this)
    }

    @Inject lateinit var db: MeetDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    override fun onCreate() {
        super.onCreate()
        com.elysium.vanguard.recordshield.RecordShieldIdentity.install {
            com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id
                ?: ("local_device_" + (android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "default_shield"))
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is java.net.SocketException ||
                throwable is java.io.EOFException ||
                throwable is java.io.InterruptedIOException ||
                throwable.cause is java.net.SocketException
            ) {
                android.util.Log.w("ElysiumApplication", "Suppressed transient network abort on ${thread.name}: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        
        // Initialize custom theme colors
        com.elysium369.meet.ui.theme.MeetColors.initialize(this)
        
        // Initialize monetization analytics
        com.elysium369.meet.core.monetization.MonetizationAnalytics.initialize(this)
        
        // Initialize persistent KnownGoodAdapterStore
        com.elysium369.meet.core.obd.KnownGoodAdapterStore.initialize(this)
        
        // Load DTC JSON if empty
        val dtcLoader = DtcDatabaseLoader(this, db)
        dtcLoader.loadIfEmpty()
        dtcLoader.loadKnowledgeGraphIfEmpty()

        // ── Iniciar motor físico P2P Mesh y observador de hardware en tiempo real ──
        com.elysium369.meet.core.mesh.ElysiumPhysicalMeshEngine.start(this, db)

        // ── Schedule automatic database maintenance (daily) ──
        scheduleDatabaseMaintenance()
        scheduleVanguardCommerceOutboxSync()
        scheduleRideCommandOutboxSync()
        scheduleMarketOsCommandOutboxSync()

        // Elite Cloud Dynamic Update Integration
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncDtcDefinitionsFromCloud(db.dtcDefinitionDao())
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncCommunityCustomPIDs(db.customPidDao())
            } catch (e: Exception) {
                android.util.Log.e("ElysiumApplication", "Error during automatic cloud dynamic update", e)
            }
        }
    }

    /**
     * Schedules the DatabaseMaintenanceWorker to run once every 24 hours.
     *
     * Constraints:
     *   - Device must NOT be low on battery (avoid draining user's phone)
     *
     * Policy: KEEP — if a schedule already exists from a prior launch, keep it.
     * This makes the call idempotent and safe on every onCreate().
     */
    private fun scheduleDatabaseMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val maintenanceRequest = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on first install
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DatabaseMaintenanceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            maintenanceRequest
        )

        android.util.Log.i("ElysiumApplication", "📋 Database maintenance scheduled (daily, battery-safe)")
    }

    private fun scheduleVanguardCommerceOutboxSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<VanguardOutboxSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            VanguardOutboxSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        android.util.Log.i("ElysiumApplication", "Vanguard commerce outbox sync scheduled (network-gated)")
    }

    private fun scheduleRideCommandOutboxSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<RideCommandSyncWorker>(
            15,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RideCommandSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        // Also run immediately on cold start to clean stale PENDING_PUBLICATION rides
        RideCommandSyncWorker.enqueueNow(this)
        android.util.Log.i(
            "ElysiumApplication",
            "Ride command outbox sync scheduled (network-gated) + immediate run",
        )
    }

    private fun scheduleMarketOsCommandOutboxSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MarketOsSyncWorker>(
            15,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setInitialDelay(3, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MarketOsSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        android.util.Log.i(
            "ElysiumApplication",
            "Market OS outbox sync scheduled (network-gated)",
        )
    }
}
