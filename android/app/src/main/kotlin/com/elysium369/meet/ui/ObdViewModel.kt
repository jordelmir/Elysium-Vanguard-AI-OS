package com.elysium369.meet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.domain.diagnostics.ClearDiagnosticMemory
import com.elysium369.meet.domain.diagnostics.FindingResolutionState
import com.elysium369.meet.domain.diagnostics.LatestDiagnosticScanProjection
import com.elysium369.meet.domain.diagnostics.RunDiagnosticScan
import com.elysium369.meet.domain.diagnostics.VehicleSessionBinding
import com.elysium369.meet.domain.diagnostics.VehicleSessionBindingState
import com.elysium369.meet.domain.diagnostics.VinVehicleIdentity
import com.elysium369.meet.domain.diagnostics.toSummary
import com.elysium369.meet.core.monetization.MonetizationPolicy
import com.elysium369.meet.core.monetization.FeatureKey
import com.elysium369.meet.data.supabase.SubscriptionRepository
import io.github.jan.supabase.gotrue.auth
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.SessionLogRepository
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.supabase.RepairCase
import com.elysium369.meet.data.supabase.RepairCaseRepository
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.core.blackbox.EvidenceCompiler
import com.elysium369.meet.core.parts.PartsMarketplaceContract
import com.elysium369.meet.core.services.WorkshopServiceCatalog
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.dao.MaintenanceAlertDao
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.data.local.entities.CustomPidEntity
import com.elysium369.meet.data.local.entities.PredictionEventEntity
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.elysium369.meet.core.alerts.AlertManager
import com.elysium369.meet.safejourney.SafeJourneyKernel
import com.elysium369.meet.ptt.PttKernel
import com.elysium369.meet.ui.home.activity.ActiveJourney
import com.elysium369.meet.ui.home.activity.ActivePttChannel
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import com.elysium369.meet.core.fleet.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.UUID
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.entities.DtcEventEntity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elysium369.meet.core.sync.SyncWorker
import com.elysium369.meet.core.livelink.*
import com.elysium369.meet.core.vanguard.VanguardOutboxSyncWorker
import com.elysium369.meet.ui.screens.TerminalLine
import com.elysium369.meet.ui.screens.TerminalLineType
import com.elysium369.meet.core.obd.ObdTrafficListener
import com.elysium369.meet.core.obd.PredictiveTelemetryEstimator
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.domain.RideCancellationPolicy
import com.elysium369.meet.ride.observability.RideObservability
import com.elysium369.meet.ride.reputation.RideReputationGateway
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideArrivalPolicy
import com.elysium369.meet.ride.domain.RideDriverVehicleSummary
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideFareEngine
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideShareCategory
import com.elysium369.meet.ride.domain.RideGuardianPolicy
import com.elysium369.meet.ride.domain.RideSafetySignalType
import com.elysium369.meet.ride.domain.RideSupportCategory
import com.elysium369.meet.ride.domain.RideSupportPolicy
import com.elysium369.meet.ride.domain.RideVerificationEvidencePolicy
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ride.domain.VerificationFileEvidence
import com.elysium369.meet.ride.domain.PlatformOwnerAccess
import com.elysium369.meet.ride.domain.RideCommandEnvelope
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RidePayloadVersion
import com.elysium369.meet.ride.domain.RideVersion
import com.elysium369.meet.ride.domain.RidePassengerPreferences
import com.elysium369.meet.ride.data.RideCommandEnqueueResult
import com.elysium369.meet.ride.data.RideCommandRepository
import com.elysium369.meet.ride.data.RideProjectionRefreshResult
import com.elysium369.meet.ride.data.RideProjectionConnectionState
import com.elysium369.meet.ride.data.RideProjectionSyncPolicy
import com.elysium369.meet.ride.data.RideRemoteProjectionRepository
import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.data.remote.RideDriverPilotEnrollment
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.ServiceVerificationSubmission
import com.elysium369.meet.ride.data.remote.TrustEvidenceFile
import com.elysium369.meet.ride.data.remote.ServiceVerificationTypePolicy
import com.elysium369.meet.ride.work.RideCommandSyncWorker
import com.elysium369.meet.ride.work.RideDriverEnrollmentWorker
import com.elysium369.meet.ride.location.GpsTrailRecorder
import com.elysium369.meet.ride.location.GpsTrailPdfExporter
import com.elysium369.meet.ride.location.RideLocationTrackingService
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.vehicle.ActiveVehicleChangeReason
import com.elysium369.meet.vehicle.ActiveVehicleKernel
import com.elysium369.meet.operations.ActiveOperation
import com.elysium369.meet.operations.ActiveOperationsRegistry
import com.elysium369.meet.operations.OperationOwner
import com.elysium369.meet.operations.OperationRecoverability
import com.elysium369.meet.operations.OperationState
import com.elysium369.meet.identity.OfflineOwnership
import com.elysium369.meet.ride.traffic.RideRoadIncident
import com.elysium369.meet.ride.traffic.RideRoadIncidentType
import com.elysium369.meet.ride.traffic.RideRoadSide
import com.elysium369.meet.ride.traffic.RideRoadReportAvailabilityPolicy
import com.elysium369.meet.ride.traffic.RideGeoCell
import com.elysium369.meet.ride.traffic.RideSegmentSpeedSample
import com.elysium369.meet.identity.OnboardingUsageProfile
import com.elysium369.meet.identity.UsageProfileGateway
import com.elysium369.meet.identity.UsageProfileSyncResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant

@Serializable
private data class RemotePartsStore(
    val storeId: String,
    val storeName: String,
    val rating: Double,
    val phone: String,
    val location: String,
    val deliveryRadiusKm: Double,
    val averageEtaMinutes: Int,
    val verified: Boolean,
    val createdAt: Long,
    val owner_id: String
) {
    fun toLocal() = PartsStoreEntity(
        storeId = storeId,
        storeName = storeName,
        rating = rating,
        phone = phone,
        location = location,
        deliveryRadiusKm = deliveryRadiusKm,
        averageEtaMinutes = averageEtaMinutes,
        verified = verified,
        createdAt = createdAt
    )
}

@Serializable
private data class RemoteRideRoadIncident(
    val id: String,
    val reporter_id: String,
    val trip_id: String?,
    val road_segment_id: String,
    val incident_type: String,
    val road_side: String,
    val severity: Int,
    val latitude: Double,
    val longitude: Double,
    val bearing_degrees: Float?,
    val accuracy_meters: Float?,
    val geohash_coarse: String,
    val created_at: String? = null,
    val expires_at: String,
)

@Serializable
private data class RemoteRideDriverVehicleSummary(
    val id: String,
    @kotlinx.serialization.SerialName("display_name") val displayName: String,
    val make: String? = null,
    val model: String? = null,
    @kotlinx.serialization.SerialName("model_year") val modelYear: Int? = null,
    val color: String? = null,
    @kotlinx.serialization.SerialName("plate_masked") val plateMasked: String? = null,
    @kotlinx.serialization.SerialName("fleet_name") val fleetName: String? = null,
    val seats: Int,
    @kotlinx.serialization.SerialName("verification_status") val verificationStatus: String,
    @kotlinx.serialization.SerialName("is_active") val active: Boolean,
    @kotlinx.serialization.SerialName("vehicle_kind") val vehicleKind: String = "CAR",
) {
    fun toDomain() = RideDriverVehicleSummary(
        id, displayName, make, model, modelYear, color, plateMasked,
        fleetName, seats, verificationStatus, active, vehicleKind,
    )
}

@Serializable
private data class RemoteRideSpeedObservation(
    val observer_id: String,
    val trip_id: String,
    val road_segment_id: String,
    val speed_mps: Float,
    val accuracy_meters: Float?,
    val bearing_degrees: Float?,
    val captured_at: String,
    val time_bucket: String,
)

data class RideClaimFeedback(
    val requestId: String,
    val won: Boolean,
    val pending: Boolean = false,
    val message: String,
    val emittedAtEpochMs: Long = System.currentTimeMillis(),
)

sealed class DekraConciergeSubmissionState {
    data object Idle : DekraConciergeSubmissionState()
    data object Publishing : DekraConciergeSubmissionState()
    data class Saved(
        val requestId: String,
        val cloudPublished: Boolean,
    ) : DekraConciergeSubmissionState()
    data class Failed(val message: String) : DekraConciergeSubmissionState()
}

private val dekraConciergeJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class RemotePartRequest(
    val requestId: String,
    val serviceRequestId: String? = null,
    val vehicleId: String = "",
    val dtcCode: String? = null,
    val partName: String = "",
    val partNumber: String? = null,
    val quantity: Int = 1,
    val oemPreference: String = "ANY",
    val deliveryLocation: String = "",
    val urgencyMinutes: Int = 60,
    val customerNotes: String = "",
    val status: String = "OPEN",
    val acceptedOfferId: String? = null,
    val createdAt: Long = 0L,
    val customer_id: String? = null,
    val statusV2: String? = null,
    val sourceContext: String? = null,
    val dtcCodes: List<String> = emptyList(),
    val category: String? = null,
    val preference: String? = null,
    val position: String? = null,
    val oemNumber: String? = null,
    val photoUrls: List<String> = emptyList(),
    val notes: String? = null,
    val deliveryAddress: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val urgencyLevel: String? = null,
    val vin: String? = null
) {
    fun toLocal() = PartRequestEntity(
        requestId = requestId,
        serviceRequestId = serviceRequestId,
        vehicleId = vehicleId,
        dtcCode = dtcCode ?: dtcCodes.firstOrNull(),
        partName = partName,
        partNumber = partNumber ?: oemNumber,
        quantity = quantity,
        oemPreference = PartsMarketplaceContract.preferenceToLegacy(preference ?: oemPreference),
        deliveryLocation = deliveryAddress?.takeIf { it.isNotBlank() } ?: deliveryLocation,
        urgencyMinutes = urgencyMinutes,
        customerNotes = notes?.takeIf { it.isNotBlank() } ?: customerNotes,
        status = PartsMarketplaceContract.requestStatusToLegacy(statusV2 ?: status),
        acceptedOfferId = acceptedOfferId,
        createdAt = createdAt,
        partPosition = PartsMarketplaceContract.positionToLegacy(position),
        latitude = locationLat ?: 0.0,
        longitude = locationLng ?: 0.0
    )
}

@Serializable
private data class RemotePartOffer(
    val offerId: String,
    val partRequestId: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val brand: String = "",
    val partNumber: String = "",
    val condition: String = "NEW",
    val price: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val etaMinutes: Int = 0,
    val warrantyDays: Int = 0,
    val message: String = "",
    val status: String = "PENDING",
    val createdAt: Long = 0L,
    val store_owner_id: String? = null,
    val supplierQuoteId: String? = null,
    val oemNumber: String? = null,
    val currency: String = "CRC",
    val availability: String? = null,
    val estimatedDeliveryHours: Int? = null,
    val includesDelivery: Boolean? = null,
    val compatibilityConfidence: String? = null,
    val compatibilityNotes: String? = null,
    val photoUrls: List<String> = emptyList(),
    val statusV2: String? = null,
    val conditionDetail: String? = null,
    val quoteVersion: Int? = null,
    val expiresAt: Long? = null
) {
    fun toLocal() = PartOfferEntity(
        offerId = offerId,
        partRequestId = partRequestId,
        storeId = storeId,
        storeName = storeName,
        brand = brand,
        partNumber = partNumber.ifBlank { oemNumber ?: "Por confirmar" },
        condition = PartsMarketplaceContract.conditionToLegacy(conditionDetail ?: condition),
        price = price,
        deliveryFee = deliveryFee,
        etaMinutes = etaMinutes.takeIf { it > 0 } ?: ((estimatedDeliveryHours ?: 1) * 60),
        warrantyDays = warrantyDays,
        message = message.ifBlank { compatibilityNotes.orEmpty() },
        status = PartsMarketplaceContract.quoteStatusToLegacy(statusV2 ?: status),
        createdAt = createdAt
    )
}

private fun PartRequestEntity.toRemote(customerId: String) = RemotePartRequest(
    requestId = requestId,
    serviceRequestId = serviceRequestId,
    vehicleId = vehicleId,
    dtcCode = dtcCode,
    partName = partName,
    partNumber = partNumber,
    quantity = quantity,
    oemPreference = oemPreference,
    deliveryLocation = deliveryLocation,
    urgencyMinutes = urgencyMinutes,
    customerNotes = customerNotes,
    status = status,
    acceptedOfferId = acceptedOfferId,
    createdAt = createdAt,
    customer_id = customerId,
    statusV2 = PartsMarketplaceContract.requestStatusToV2(status),
    sourceContext = when {
        !dtcCode.isNullOrBlank() -> "FROM_DTC"
        !serviceRequestId.isNullOrBlank() -> "FROM_MECHANIC_WORK_ORDER"
        else -> "MANUAL"
    },
    dtcCodes = listOfNotNull(dtcCode?.takeIf { it.isNotBlank() }),
    preference = PartsMarketplaceContract.preferenceToV2(oemPreference),
    position = PartsMarketplaceContract.positionToV2(partPosition),
    oemNumber = partNumber?.takeIf { oemPreference.equals("OEM", ignoreCase = true) },
    notes = customerNotes,
    deliveryAddress = deliveryLocation,
    locationLat = latitude.takeUnless { it == 0.0 },
    locationLng = longitude.takeUnless { it == 0.0 },
    urgencyLevel = when {
        urgencyMinutes <= 30 -> "URGENT"
        urgencyMinutes >= 180 -> "LOW"
        else -> "NORMAL"
    }
)

@Serializable
private data class RemoteActiveRideVehicle(
    val id: String,
    @kotlinx.serialization.SerialName("verification_method")
    val verificationMethod: String = "LEGACY_REVIEW",
    @kotlinx.serialization.SerialName("pilot_access_expires_at")
    val pilotAccessExpiresAt: String? = null,
)

private fun PartsStoreEntity.toRemote(ownerId: String) = RemotePartsStore(
    storeId = storeId,
    storeName = storeName,
    rating = rating,
    phone = phone,
    location = location,
    deliveryRadiusKm = deliveryRadiusKm,
    averageEtaMinutes = averageEtaMinutes,
    verified = verified,
    createdAt = createdAt,
    owner_id = ownerId
)

private fun PartOfferEntity.toRemote(
    ownerId: String,
    compatibilityConfidence: String = "UNKNOWN",
    compatibilityNotes: String = ""
) = RemotePartOffer(
    offerId = offerId,
    partRequestId = partRequestId,
    storeId = storeId,
    storeName = storeName,
    brand = brand,
    partNumber = partNumber,
    condition = condition,
    price = price,
    deliveryFee = deliveryFee,
    etaMinutes = etaMinutes,
    warrantyDays = warrantyDays,
    message = message,
    status = status,
    createdAt = createdAt,
    store_owner_id = ownerId,
    supplierQuoteId = offerId,
    currency = "CRC",
    availability = when {
        etaMinutes <= 120 -> "SAME_DAY"
        etaMinutes <= 24 * 60 -> "NEXT_DAY"
        else -> "IMPORT_REQUIRED"
    },
    estimatedDeliveryHours = ((etaMinutes + 59) / 60).coerceAtLeast(1),
    includesDelivery = deliveryFee <= 0.0,
    compatibilityConfidence = compatibilityConfidence,
    compatibilityNotes = compatibilityNotes,
    statusV2 = PartsMarketplaceContract.quoteStatusToV2(status),
    conditionDetail = PartsMarketplaceContract.conditionToV2(condition),
    quoteVersion = 1,
    expiresAt = createdAt + 24L * 60L * 60L * 1000L
)

@HiltViewModel
class ObdViewModel @Inject constructor(
    private val obdSession: ObdSession,
    private val runDiagnosticScan: RunDiagnosticScan,
    private val clearDiagnosticMemory: ClearDiagnosticMemory,
    private val vehicleRepository: VehicleRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val sessionLogRepository: SessionLogRepository,
    private val geminiDiagnostic: com.elysium369.meet.core.ai.GeminiDiagnostic,
    private val tripManager: com.elysium369.meet.core.trips.TripManager,
    private val tripDao: TripDao,
    private val maintenanceAlertDao: MaintenanceAlertDao,
    private val customPidDao: CustomPidDao,
    private val dtcDao: com.elysium369.meet.data.local.dao.DtcDao,
    private val diagnosticEvidenceDao: com.elysium369.meet.data.local.dao.DiagnosticEvidenceDao,
    private val diagnosticFindingRepository: com.elysium369.meet.domain.diagnostics.DiagnosticFindingRepository,
    private val dtcDefinitionDao: com.elysium369.meet.data.local.dao.DtcDefinitionDao,
    private val aiConsultDao: com.elysium369.meet.data.local.dao.AiConsultDao,
    @ApplicationContext private val context: Context,
    private val reportGenerator: com.elysium369.meet.core.export.ReportGenerator,
    private val diagnosticManager: com.elysium369.meet.core.obd.AdvancedDiagnosticManager,
    private val localExpertSystem: com.elysium369.meet.core.obd.LocalExpertSystem,
    private val alertManager: AlertManager,
    private val predictiveHealthEngine: com.elysium369.meet.core.health.PredictiveHealthEngine,
    private val sensorHistoryDao: com.elysium369.meet.data.local.dao.SensorHistoryDao,
    private val usbOscilloscopeManager: com.elysium369.meet.core.usb.UsbOscilloscopeManager,
    private val performanceCalculator: PerformanceCalculator,
    private val dataLogger: DataLogger,
    private val alertThresholdEngine: AlertThresholdEngine,
    private val prePurchaseInspection: PrePurchaseInspection,
    private val fuelEconomyTracker: FuelEconomyTracker,
    private val batteryHealthAnalyzer: BatteryHealthAnalyzer,
    private val turboBoostGauge: TurboBoostGauge,
    private val dvirReportDao: com.elysium369.meet.data.local.dao.DvirReportDao,
    private val predictionEventDao: com.elysium369.meet.data.local.dao.PredictionEventDao,
    private val healthSnapshotDao: com.elysium369.meet.data.local.dao.HealthSnapshotDao,
    private val phoneSpeedTracker: PhoneSpeedTracker,
    val voiceFeedbackManager: com.elysium369.meet.core.audio.VoiceFeedbackManager,
    private val voiceCommandManager: com.elysium369.meet.core.audio.VoiceCommandManager,
    private val gaugeStyleManager: com.elysium369.meet.ui.components.gauges.GaugeStyleManager,
    private val meetDnaEngine: com.elysium369.meet.core.dna.MeetDnaEngine,
    private val ruleEngine: com.elysium369.meet.core.copilot.RuleEngine,
    private val speechService: com.elysium369.meet.core.copilot.SpeechService,
    private val notificationService: com.elysium369.meet.core.copilot.NotificationService,
    private val liveSessionDao: LiveSessionDao,
    private val repairNetworkAddonsDao: RepairNetworkAddonsDao,
    private val marketplaceDao: MarketplaceDao,
    private val blackBoxDao: BlackBoxDao,
    private val vehicleTwinDao: VehicleTwinDao,
    private val vehicleTwinEngine: VehicleTwinEngine,
    private val repairCaseRepository: RepairCaseRepository,
    private val dtcKnowledgeGraphDao: com.elysium369.meet.data.local.dao.DtcKnowledgeGraphDao,
    private val towTruckDao: TowTruckDao,
    private val towJobDao: com.elysium369.meet.data.local.dao.TowJobDao,
    private val ratingDao: RatingDao,
    private val providerProfileDao: ProviderProfileDao,
    private val rideDao: com.elysium369.meet.data.local.dao.RideDao,
    private val chatDao: com.elysium369.meet.data.local.dao.ChatDao,
    private val rideCommandRepository: RideCommandRepository,
    private val rideRemoteProjectionRepository: RideRemoteProjectionRepository,
    private val rideReputationGateway: RideReputationGateway,
    private val activePrincipalKernel: ActivePrincipalKernel,
    private val activeVehicleKernel: ActiveVehicleKernel,
    private val activeOperationsRegistry: ActiveOperationsRegistry,
    private val vehicleRuntimeServer: com.elysium369.meet.core.evair.bridge.VehicleRuntimeServer,
    val vehicleAccessManager: com.elysium369.meet.core.vehicleaccess.application.VehicleAccessManager,
    val entitlementManager: com.elysium369.meet.core.monetization.EntitlementManager,
    val adGateManager: com.elysium369.meet.core.monetization.AdGateManager,
    val usageMeter: com.elysium369.meet.core.monetization.UsageMeter,
    private val aiRepository: com.elysium369.meet.ai.data.AiRepository,
    private val safeJourneyKernel: SafeJourneyKernel,
    private val pttKernel: PttKernel,
    private val ridePaymentGateway: com.elysium369.meet.ride.payment.RidePaymentGateway,
    private val rideLiveVoiceBridge: com.elysium369.meet.communications.RideLiveVoiceBridge? = null,
    private val rideChatBridge: com.elysium369.meet.communications.RideRealtimeChatBridge? = null,
    private val commerceOrderDao: com.elysium369.meet.commerce.data.local.CommerceOrderDao? = null,
) : ViewModel() {

    // Device-level identity must be initialized before init{} calls provider role refresh.
    private val localDeviceId: String = activePrincipalKernel.localDeviceId
    val activePrincipal = activePrincipalKernel.activePrincipal

    val liveCallState: StateFlow<com.elysium369.meet.communications.LiveCallState> =
        rideLiveVoiceBridge?.callState ?: MutableStateFlow(com.elysium369.meet.communications.LiveCallState.Idle).asStateFlow()

    fun startRideCall(rideId: String, role: String) {
        viewModelScope.launch {
            rideLiveVoiceBridge?.startCall(rideId, role)
        }
    }

    fun endRideCall(reason: String = "USER_HANGUP") {
        viewModelScope.launch {
            rideLiveVoiceBridge?.endCall(reason)
        }
    }

    fun toggleRideCallMute(): Boolean {
        return rideLiveVoiceBridge?.toggleMute() ?: false
    }

    fun observeRideCalls(rideId: String, role: String) {
        rideLiveVoiceBridge?.startObservingCalls(rideId, role)
    }

    fun stopObservingRideCalls() {
        rideLiveVoiceBridge?.stopObservingCalls()
    }

    val connectionState: StateFlow<ObdState> = obdSession.state
    val activeOperations = activeOperationsRegistry.operations
    val statusMessage: StateFlow<String> = obdSession.statusMessage
    val telemetrySamples: StateFlow<Map<String, TelemetrySample>> = obdSession.telemetrySamples
    val detectedDtcs: StateFlow<Set<String>> = obdSession.allDetectedDtcs

    // --- Active Journeys (SafeJourneyKernel → HomeActivityStrip) ---
    val activeJourneys: StateFlow<List<ActiveJourney>> = flow {
        while (true) {
            val journeys = safeJourneyKernel.getActiveJourneys().map { j ->
                ActiveJourney(
                    journeyId = j.journeyId,
                    name = j.name.ifBlank { j.destinationName ?: "Viaje activo" },
                    stateName = j.journeyState.name,
                    isActive = j.state.isActive,
                    progress = when {
                        j.journeyState.isInProgress -> 0.6f
                        j.journeyState == com.elysium369.meet.safejourney.SafeJourneyState.ARRIVED -> 1.0f
                        else -> 0.3f
                    }
                )
            }
            emit(journeys)
            kotlinx.coroutines.delay(5000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active PTT Channels (PttKernel → HomeActivityStrip) ---
    val activePttChannels: StateFlow<List<ActivePttChannel>> = flow {
        while (true) {
            val principalId = activePrincipal.value?.id ?: ""
            val channels = pttKernel.getChannelsForPrincipal(principalId).map { ch ->
                ActivePttChannel(
                    channelId = ch.channelId,
                    name = ch.name,
                    stateName = ch.state.name,
                    isLive = ch.state.isActive
                )
            }
            emit(channels)
            kotlinx.coroutines.delay(5000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UDS Protocol Manager (lazy, uses existing obdSession) ---
    private val udsProtocolManager by lazy {
        com.elysium369.meet.core.obd.UdsProtocolManager(obdSession)
    }

    // --- Elysium Authoritative Tow Command Repository ---
    val towCommandRepository: com.elysium369.meet.core.services.tow.TowCommandRepository by lazy {
        com.elysium369.meet.core.services.tow.TowCommandRepository(towJobDao, towTruckDao, viewModelScope)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ELYSIUM CIVILIZATIONAL & MOBILITY ENGINES
    // ═══════════════════════════════════════════════════════════════════════════
    val vehicleHealthScoreEngine by lazy { com.elysium369.meet.core.vehicle.VehicleHealthScoreEngine() }
    val vehicleHistoryTimeline by lazy { com.elysium369.meet.core.vehicle.VehicleHistoryTimeline() }
    val maintenancePredictorEngine by lazy { com.elysium369.meet.core.maintenance.MaintenancePredictorEngine() }
    val antifraudQuoteEngine by lazy { com.elysium369.meet.core.antifraud.AntifraudQuoteEngine() }
    val warrantyEngine by lazy { com.elysium369.meet.core.warranty.WarrantyEngine() }
    val diagnosticWisdomEngine by lazy { com.elysium369.meet.core.diagnostic.DiagnosticWisdomEngine() }
    val disputeResolutionEngine by lazy { com.elysium369.meet.core.dispute.DisputeResolutionEngine() }
    val fleetCommandEngine by lazy { com.elysium369.meet.core.fleet.FleetCommandEngine() }
    val insuranceReportBridge by lazy { com.elysium369.meet.core.insurance.InsuranceReportBridge() }
    val mechanicMentorshipEngine by lazy { com.elysium369.meet.core.mentorship.MechanicMentorshipEngine() }

    val rideDriverEarningsEngine by lazy { com.elysium369.meet.ride.earnings.RideDriverEarningsEngine() }
    val rideLiveSharingEngine by lazy { com.elysium369.meet.ride.liveshare.RideLiveSharingEngine() }
    val rideFareComparatorEngine by lazy { com.elysium369.meet.ride.farecompare.RideFareComparatorEngine() }
    val rideScheduleEngine by lazy {
        val dao = (context.applicationContext as? com.elysium369.meet.MeetApplication)?.db?.scheduledRideDao()
            ?: object : com.elysium369.meet.data.local.dao.ScheduledRideDao {
                override suspend fun upsert(schedule: com.elysium369.meet.data.local.entities.ScheduledRideEntity) {}
                override suspend fun upsertAll(schedules: List<com.elysium369.meet.data.local.entities.ScheduledRideEntity>) {}
                override suspend fun update(schedule: com.elysium369.meet.data.local.entities.ScheduledRideEntity) {}
                override suspend fun getById(scheduleId: String): com.elysium369.meet.data.local.entities.ScheduledRideEntity? = null
                override fun getUpcomingFlow(userId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.elysium369.meet.data.local.entities.ScheduledRideEntity>())
                override suspend fun getUpcoming(userId: String) = emptyList<com.elysium369.meet.data.local.entities.ScheduledRideEntity>()
                override suspend fun getDueForDispatch(nowEpochMs: Long) = emptyList<com.elysium369.meet.data.local.entities.ScheduledRideEntity>()
                override suspend fun updateStatus(scheduleId: String, status: String) {}
                override suspend fun assignDriver(scheduleId: String, status: String, driverId: String, rideId: String) {}
                override suspend fun delete(scheduleId: String) = 0
                override suspend fun purgeTerminal(cutoffEpochMs: Long) = 0
                override suspend fun upsertFavorite(route: com.elysium369.meet.data.local.entities.FavoriteRouteEntity) {}
                override fun getFavoritesFlow() = kotlinx.coroutines.flow.flowOf(emptyList<com.elysium369.meet.data.local.entities.FavoriteRouteEntity>())
                override suspend fun getFavorites() = emptyList<com.elysium369.meet.data.local.entities.FavoriteRouteEntity>()
                override suspend fun getFavoriteById(routeId: String) = null
                override suspend fun incrementFavoriteUsage(routeId: String, nowEpochMs: Long) {}
                override suspend fun deleteFavorite(routeId: String) = 0
            }
        com.elysium369.meet.ride.schedule.RideScheduleEngine(dao)
    }
    val rideTripSummaryEngine by lazy { com.elysium369.meet.ride.summary.RideTripSummaryEngine() }
    val rideAccessibilityEngine by lazy { com.elysium369.meet.ride.accessibility.RideAccessibilityEngine() }

    private val _civilizationalHealthScore = MutableStateFlow<com.elysium369.meet.core.vehicle.HealthScoreEntry?>(null)
    val civilizationalHealthScore: StateFlow<com.elysium369.meet.core.vehicle.HealthScoreEntry?> = _civilizationalHealthScore.asStateFlow()

    private val _civilizationalTimeline = MutableStateFlow<List<com.elysium369.meet.core.vehicle.VehicleTimelineEvent>>(emptyList())
    val civilizationalTimeline: StateFlow<List<com.elysium369.meet.core.vehicle.VehicleTimelineEvent>> = _civilizationalTimeline.asStateFlow()

    fun computeCivilizationalScore() {
        val v = selectedVehicle.value
        val vehicleId = v?.id ?: "current"
        val dtcs = detectedDtcs.value
        val km = currentOdometer.value.toLong().coerceAtLeast(50_000L)
        val age = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - (v?.year ?: 2020)).coerceAtLeast(0)
        val factors = com.elysium369.meet.core.vehicle.HealthScoreFactors(
            activeDtcCount = dtcs.size,
            criticalDtcCount = dtcs.count { it.startsWith("P0") || it.startsWith("U0") },
            totalMileageKm = km,
            vehicleAgeYears = age,
            missedMaintenanceCount = 0,
            accidentCount = 0,
        )
        val result = vehicleHealthScoreEngine.computeScore(vehicleId, factors)
        _civilizationalHealthScore.value = result
    }

    fun recordVehicleTimelineEvent(
        eventType: com.elysium369.meet.core.vehicle.VehicleEventType,
        title: String,
        description: String,
        dtcs: List<String> = emptyList(),
    ) {
        val vehicleId = selectedVehicle.value?.id ?: "current"
        val km = currentOdometer.value.toLong().coerceAtLeast(0L)
        vehicleHistoryTimeline.addEvent(
            vehicleId = vehicleId,
            type = eventType,
            title = title,
            description = description,
            mileageKm = km,
            relatedDtcCodes = dtcs,
        )
        _civilizationalTimeline.value = vehicleHistoryTimeline.getTimeline(vehicleId)
    }

    fun getMaintenancePlan(): com.elysium369.meet.core.maintenance.MaintenancePlan {
        val vehicleId = selectedVehicle.value?.id ?: "current"
        val km = currentOdometer.value.toLong().coerceAtLeast(50_000L)
        return maintenancePredictorEngine.predict(vehicleId, km)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // C I V I L I Z A T I O N A L   E N G I N E S   H E L P E R S
    // ══════════════════════════════════════════════════════════════════════════

    fun getWisdomSolutionsForDtc(dtcCode: String): List<com.elysium369.meet.core.diagnostic.DtcSolution> {
        val v = selectedVehicle.value
        return diagnosticWisdomEngine.findSolutionsForVehicle(
            dtcCode = dtcCode,
            brand = v?.make ?: "",
            model = v?.model ?: "",
            year = v?.year,
        ).ifEmpty {
            diagnosticWisdomEngine.findSolutions(dtcCode)
        }
    }

    fun voteWisdomSolution(solutionId: String, isUpvote: Boolean): Boolean {
        return if (isUpvote) diagnosticWisdomEngine.upvote(solutionId) else diagnosticWisdomEngine.downvote(solutionId)
    }

    fun recordWisdomOutcome(solutionId: String, dtcCode: String, success: Boolean, dtcCleared: Boolean) {
        val vId = selectedVehicle.value?.id ?: "current"
        val mId = currentCloudUserId() ?: "local-mechanic"
        diagnosticWisdomEngine.recordOutcome(
            solutionId = solutionId,
            mechanicId = mId,
            vehicleId = vId,
            dtcCode = dtcCode,
            success = success,
            dtcClearedAfterRepair = dtcCleared,
        )
    }

    fun getActiveWarrantiesForVehicle(): List<com.elysium369.meet.core.warranty.Warranty> {
        val vId = selectedVehicle.value?.id ?: "current"
        return warrantyEngine.getActiveWarranties(vId)
    }

    fun submitWarrantyClaim(warrantyId: String, description: String, dtcCodes: List<String>): com.elysium369.meet.core.warranty.WarrantyClaim? {
        val evidence = com.elysium369.meet.core.warranty.ClaimEvidence(
            dtcCodes = dtcCodes,
            description = description,
            currentMileageKm = currentOdometer.value.toLong(),
        )
        return warrantyEngine.submitClaim(
            warrantyId = warrantyId,
            description = description,
            category = com.elysium369.meet.core.warranty.ClaimCategory.SAME_ISSUE_RETURNED,
            evidence = evidence,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // F L E E T   M O G U L   ( D U E Ñ O S   D E   F L O T I L L A S )
    // ══════════════════════════════════════════════════════════════════════════
    private val _fleetMogulUnits = MutableStateFlow<List<FleetMogulVehicle>>(
        listOf(
            FleetMogulVehicle(
                id = "mogul-v1",
                plate = "SJC-782",
                brand = "Toyota",
                model = "Corolla Hybrid",
                year = 2023,
                assignedDriverName = "Esteban Solís",
                driverPhone = "+506 8834-1122",
                contractType = FleetContractType.PROFIT_SPLIT,
                dailyCanonCrc = 0,
                ownerSplitPercent = 25,
                status = FleetUnitStatus.ON_ROUTE,
                todayGrossRevenueCrc = 48500,
                todayOwnerEarningsCrc = 12125,
                balanceDueFromDriverCrc = 0,
                healthScore = 920,
                engineTempC = 88.5f,
                currentSpeedKph = 42.0f,
                rpm = 1850f,
                activeDtcs = emptyList(),
                isAbuseDetected = false,
                isDispatchLocked = false,
            ),
            FleetMogulVehicle(
                id = "mogul-v2",
                plate = "SJC-914",
                brand = "Hyundai",
                model = "Elantra",
                year = 2022,
                assignedDriverName = "Mauricio Brenes",
                driverPhone = "+506 8712-4490",
                contractType = FleetContractType.FIXED_CANON,
                dailyCanonCrc = 22000,
                ownerSplitPercent = 0,
                status = FleetUnitStatus.ON_ROUTE,
                todayGrossRevenueCrc = 54000,
                todayOwnerEarningsCrc = 22000,
                balanceDueFromDriverCrc = 0,
                healthScore = 860,
                engineTempC = 91.0f,
                currentSpeedKph = 35.0f,
                rpm = 2100f,
                activeDtcs = emptyList(),
                isAbuseDetected = false,
                isDispatchLocked = false,
            ),
            FleetMogulVehicle(
                id = "mogul-v3",
                plate = "SJC-403",
                brand = "Nissan",
                model = "Versa",
                year = 2021,
                assignedDriverName = "David Castro",
                driverPhone = "+506 8301-9988",
                contractType = FleetContractType.PROFIT_SPLIT,
                dailyCanonCrc = 0,
                ownerSplitPercent = 25,
                status = FleetUnitStatus.AVAILABLE,
                todayGrossRevenueCrc = 18000,
                todayOwnerEarningsCrc = 4500,
                balanceDueFromDriverCrc = 12000,
                healthScore = 540,
                engineTempC = 104.2f,
                currentSpeedKph = 0.0f,
                rpm = 750f,
                activeDtcs = listOf("P0420"),
                isAbuseDetected = true,
                isDispatchLocked = false,
            )
        )
    )
    val fleetMogulUnits: StateFlow<List<FleetMogulVehicle>> = _fleetMogulUnits.asStateFlow()

    fun toggleFleetDispatchLock(unitId: String) {
        _fleetMogulUnits.value = _fleetMogulUnits.value.map { unit ->
            if (unit.id == unitId) {
                val newLocked = !unit.isDispatchLocked
                unit.copy(
                    isDispatchLocked = newLocked,
                    status = if (newLocked) FleetUnitStatus.DISPATCH_LOCKED else FleetUnitStatus.AVAILABLE
                )
            } else unit
        }
    }

    fun linkNewFleetVehicle(
        plate: String,
        brand: String,
        model: String,
        year: Int,
        driverName: String,
        driverPhone: String,
        contractType: FleetContractType,
        dailyCanonCrc: Long,
        splitPercent: Int,
    ) {
        val newUnit = FleetMogulVehicle(
            id = "mogul-v${System.currentTimeMillis()}",
            plate = plate.uppercase().trim(),
            brand = brand.trim(),
            model = model.trim(),
            year = year,
            assignedDriverName = driverName.trim(),
            driverPhone = driverPhone.trim(),
            contractType = contractType,
            dailyCanonCrc = dailyCanonCrc,
            ownerSplitPercent = splitPercent,
            status = FleetUnitStatus.AVAILABLE,
            todayGrossRevenueCrc = 0L,
            todayOwnerEarningsCrc = 0L,
            balanceDueFromDriverCrc = 0L,
            healthScore = 1000,
            engineTempC = 85.0f,
            currentSpeedKph = 0f,
            rpm = 0f,
        )
        _fleetMogulUnits.value = _fleetMogulUnits.value + newUnit
    }

    fun settleDriverDebt(unitId: String, amountSettledCrc: Long) {
        _fleetMogulUnits.value = _fleetMogulUnits.value.map { unit ->
            if (unit.id == unitId) {
                unit.copy(balanceDueFromDriverCrc = (unit.balanceDueFromDriverCrc - amountSettledCrc).coerceAtLeast(0L))
            } else unit
        }
    }

    // --- Force Clone Mode ---
    private val _forceCloneMode = MutableStateFlow(false)
    val forceCloneMode: StateFlow<Boolean> = _forceCloneMode.asStateFlow()

    // --- Voice Copilot Enabled ---
    private val _voiceCopilotEnabled = MutableStateFlow(
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .getBoolean("voice_copilot_enabled", false)
    )
    val voiceCopilotEnabled: StateFlow<Boolean> = _voiceCopilotEnabled.asStateFlow()

    // --- Fused Speed Enabled (Waze style) ---
    private val _fusedSpeedEnabled = MutableStateFlow(true)
    val fusedSpeedEnabled: StateFlow<Boolean> = _fusedSpeedEnabled.asStateFlow()

    // --- Phone Sensor: G-Force & Inclinometer (60Hz) ---
    val lateralG: StateFlow<Float> = phoneSpeedTracker.lateralG
    val longitudinalG: StateFlow<Float> = phoneSpeedTracker.longitudinalG
    val phonePitch: StateFlow<Float> = phoneSpeedTracker.pitch
    val phoneRoll: StateFlow<Float> = phoneSpeedTracker.roll

    fun calibratePhoneSensors() = phoneSpeedTracker.calibrateSensors()
    fun resetPhoneSensorCalibration() = phoneSpeedTracker.resetCalibration()

    // ── LiveLink (Opt-in remote telemetry) ──
    private var _liveLinkServer: LiveLinkServer? = null
    fun attachLiveLinkServer(server: LiveLinkServer) { _liveLinkServer = server }
    fun detachLiveLinkServer() { _liveLinkServer = null }

    // isAdapterPro respects forceCloneMode override
    val isAdapterPro: StateFlow<Boolean> = combine(
        obdSession.isAdapterPro,
        _forceCloneMode
    ) { realPro, forceClone ->
        if (forceClone) false else realPro
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- AI Configuration ---
    data class AiConfig(
        val provider: String = "gemini",  // gemini, openai, anthropic, ollama, custom
        val apiKey: String = "",
        val endpoint: String = "",
        val modelName: String = ""
    )
    private val _aiConfig = MutableStateFlow(AiConfig())
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()

    // --- Terminal & Command History ---
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _terminalSessionLogs = MutableStateFlow<List<TerminalLine>>(
        listOf(
            TerminalLine("╔══════════════════════════════════════════╗", TerminalLineType.SYSTEM),
            TerminalLine("║  Elysium Vanguard AI OS Expert Terminal v3.0               ║", TerminalLineType.SYSTEM),
            TerminalLine("║  Motor de Diagnóstico Inteligente        ║", TerminalLineType.SYSTEM),
            TerminalLine("╚══════════════════════════════════════════╝", TerminalLineType.SYSTEM),
            TerminalLine("Escribe un comando OBD2 o AT. Cada comando incluye", TerminalLineType.SYSTEM),
            TerminalLine("una explicación técnica automática.", TerminalLineType.SYSTEM),
            TerminalLine("────────────────────────────────────────────", TerminalLineType.SYSTEM)
        )
    )
    val terminalSessionLogs: StateFlow<List<TerminalLine>> = _terminalSessionLogs.asStateFlow()

    private val shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Sustituto de Termux para Terminal de Android
    val localShellManager = com.elysium369.meet.core.utils.LocalShellManager(
        context,
        geminiDiagnostic,
        obdSession,
        tripManager,
        shellScope,
        vehicleRuntimeServer,
    )
    val localShellLines: StateFlow<List<String>> = localShellManager.terminalLines
    val activeDistro: StateFlow<String> = localShellManager.activeDistro
    val installedDistros: StateFlow<Set<String>> = localShellManager.installedDistros

    fun switchActiveDistro(distro: String) {
        localShellManager.switchDistro(distro)
    }

    fun isDistroInstalled(distro: String): Boolean {
        return localShellManager.isDistroInstalled(distro)
    }

    private val _selectedVehicle: StateFlow<Vehicle?> = activeVehicleKernel.activeVehicle
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle

    // ── LiveLink PRO remote session state ──
    private val liveLinkProEngine = LiveLinkSessionEngine()

    private val _liveLinkProSession = MutableStateFlow<LiveSessionEntity?>(null)
    val liveLinkProSession: StateFlow<LiveSessionEntity?> = _liveLinkProSession.asStateFlow()

    private val _liveLinkProCoreSession = MutableStateFlow<LiveLinkSession?>(null)
    val liveLinkProCoreSession: StateFlow<LiveLinkSession?> = _liveLinkProCoreSession.asStateFlow()

    private val _liveLinkProCredentials = MutableStateFlow<LiveLinkAccessCredentials?>(null)
    val liveLinkProCredentials: StateFlow<LiveLinkAccessCredentials?> = _liveLinkProCredentials.asStateFlow()

    private val _liveLinkProPermissions = MutableStateFlow<LiveLinkPermission?>(null)
    val liveLinkProPermissions: StateFlow<LiveLinkPermission?> = _liveLinkProPermissions.asStateFlow()

    private val _liveLinkProEvents = MutableStateFlow<List<LiveLinkEvent>>(emptyList())
    val liveLinkProEvents: StateFlow<List<LiveLinkEvent>> = _liveLinkProEvents.asStateFlow()

    private val _liveLinkProRequests = MutableStateFlow<List<LiveLinkRemoteRequest>>(emptyList())
    val liveLinkProRequests: StateFlow<List<LiveLinkRemoteRequest>> = _liveLinkProRequests.asStateFlow()

    private val _liveLinkProMessages = MutableStateFlow<List<LiveLinkChatMessage>>(emptyList())
    val liveLinkProMessages: StateFlow<List<LiveLinkChatMessage>> = _liveLinkProMessages.asStateFlow()

    private val _liveLinkProLatestPacket = MutableStateFlow<LiveLinkTelemetryPacket?>(null)
    val liveLinkProLatestPacket: StateFlow<LiveLinkTelemetryPacket?> = _liveLinkProLatestPacket.asStateFlow()

    private val _liveLinkProReport = MutableStateFlow<LiveLinkReport?>(null)
    val liveLinkProReport: StateFlow<LiveLinkReport?> = _liveLinkProReport.asStateFlow()

    private val liveLinkProPackets = mutableListOf<LiveLinkTelemetryPacket>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val mechanicNotes: StateFlow<List<MechanicNoteEntity>> = _liveLinkProSession
        .flatMapLatest { session ->
            session?.let { liveSessionDao.getNotesForSession(it.sessionId) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var telemetryUploadJob: Job? = null
    private var notesPollingJob: Job? = null

    fun startLiveLinkPro(durationMinutes: Int, readOnly: Boolean, videoCall: Boolean) {
        val vehicle = selectedVehicle.value ?: return
        val ownerId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous"
        val envelope = liveLinkProEngine.createSession(
            CreateLiveLinkSessionInput(
                ownerUserId = ownerId,
                vehicleId = vehicle.id,
                mode = if (readOnly) LiveLinkMode.REMOTE_READ_ONLY else LiveLinkMode.REMOTE_ASSISTED,
                title = "${vehicle.year} ${vehicle.make} ${vehicle.model}".trim(),
                durationMinutes = durationMinutes,
                readOnly = readOnly,
                allowVideo = videoCall,
                allowAudio = false,
                allowCamera = false,
                allowLocation = false,
                allowVehicleHistory = false,
                allowReports = true,
                allowDtc = true,
                allowLivePids = true,
            )
        )
        val coreSession = envelope.session
        val publicSessionUrl = "https://meet.elysium369.com/livelink?session_id=${coreSession.sessionId}"
        val session = LiveSessionEntity(
            sessionId = coreSession.sessionId,
            vehicleId = vehicle.id,
            ownerId = ownerId,
            mechanicId = null,
            status = coreSession.state.name,
            startedAt = coreSession.startedAtMs ?: coreSession.createdAtMs,
            endedAt = null,
            permissions = Json.encodeToString(envelope.permissions),
            sessionCode = envelope.credentials.displayCode,
            shareUrl = publicSessionUrl,
            durationMinutes = durationMinutes,
            videoCallUrl = if (videoCall) "https://meet.elysium369.com/call/${coreSession.sessionId}" else null
        )

        _liveLinkProCoreSession.value = coreSession
        _liveLinkProCredentials.value = envelope.credentials
        _liveLinkProPermissions.value = envelope.permissions
        _liveLinkProEvents.value = listOf(envelope.createdEvent)
        _liveLinkProRequests.value = emptyList()
        _liveLinkProMessages.value = emptyList()
        _liveLinkProLatestPacket.value = null
        _liveLinkProReport.value = null
        liveLinkProPackets.clear()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveSessionDao.insertLiveSession(session)
                _liveLinkProSession.value = session

                // Upload the legacy-compatible session shell. The real access token stays in memory only;
                // persisted state keeps the token hash inside LiveLinkSession.
                runCatching {
                    SupabaseManager.client.postgrest["live_sessions"].insert(session)
                }.onFailure { error ->
                    Log.w("ObdViewModel", "LiveLink session created locally; cloud session shell sync failed", error)
                }

                // Start background upload loop for telemetry
                startLiveLinkTelemetryLoop(coreSession.sessionId)
                com.elysium369.meet.core.notification.ElysiumNotificationHub.showLiveLinkActiveNotification(
                    context, coreSession.sessionId, if (readOnly) "Solo Lectura" else "Asistido"
                )

                // Start background note polling loop
                startNotesPollingLoop(coreSession.sessionId)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error starting remote LiveLink PRO session", e)
            }
        }
    }

    fun stopLiveLinkPro() {
        val session = _liveLinkProSession.value ?: return
        _liveLinkProCoreSession.value?.let { coreSession ->
            val (completed, event) = liveLinkProEngine.complete(coreSession)
            _liveLinkProCoreSession.value = completed
            addLiveLinkEvent(event)
        }
        telemetryUploadJob?.cancel()
        notesPollingJob?.cancel()
        _liveLinkProSession.value = null
        _liveLinkProCoreSession.value = null
        _liveLinkProCredentials.value = null
        _liveLinkProPermissions.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                liveSessionDao.updateSessionStatus(session.sessionId, "COMPLETED")
                liveSessionDao.updateSessionEndedAt(session.sessionId, now)

                // Sync status to cloud
                SupabaseManager.client.postgrest["live_sessions"].update(mapOf("status" to "COMPLETED", "endedAt" to now)) {
                    filter {
                        eq("sessionId", session.sessionId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error stopping remote LiveLink PRO session", e)
            }
        }
    }

    fun revokeLiveLinkPro() {
        val session = _liveLinkProSession.value ?: return
        val coreSession = _liveLinkProCoreSession.value ?: return
        val (revoked, event) = liveLinkProEngine.revoke(coreSession)
        _liveLinkProCoreSession.value = revoked
        _liveLinkProSession.value = session.copy(status = revoked.state.name, endedAt = revoked.endedAtMs)
        addLiveLinkEvent(event)
        telemetryUploadJob?.cancel()
        notesPollingJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveSessionDao.updateSessionStatus(session.sessionId, revoked.state.name)
                revoked.endedAtMs?.let { liveSessionDao.updateSessionEndedAt(session.sessionId, it) }
                runCatching {
                    SupabaseManager.client.postgrest["live_sessions"].update(
                        mapOf("status" to revoked.state.name, "endedAt" to revoked.endedAtMs)
                    ) {
                        filter { eq("sessionId", session.sessionId) }
                    }
                }.onFailure { error ->
                    Log.w("ObdViewModel", "LiveLink revoke stored locally; cloud sync failed", error)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error revoking LiveLink PRO session", e)
            }
        }
    }

    fun requestLiveLinkSnapshot() {
        val session = _liveLinkProCoreSession.value ?: return
        val permissions = _liveLinkProPermissions.value ?: return
        val result = liveLinkProEngine.createRemoteRequest(
            session = session,
            permissions = permissions,
            type = LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT
        )
        _liveLinkProRequests.value = (listOf(result.request) + _liveLinkProRequests.value).take(40)
        addLiveLinkEvent(result.event)
    }

    fun requestLiveLinkClearDtcForAudit() {
        val session = _liveLinkProCoreSession.value ?: return
        val permissions = _liveLinkProPermissions.value ?: return
        val result = liveLinkProEngine.createRemoteRequest(
            session = session,
            permissions = permissions,
            type = LiveLinkRemoteRequestType.CLEAR_DTCS
        )
        _liveLinkProRequests.value = (listOf(result.request) + _liveLinkProRequests.value).take(40)
        addLiveLinkEvent(result.event)
    }

    fun approveLiveLinkRequest(requestId: String) {
        resolveLiveLinkRequest(requestId, approved = true)
    }

    fun denyLiveLinkRequest(requestId: String) {
        resolveLiveLinkRequest(requestId, approved = false)
    }

    fun sendLiveLinkChat(body: String) {
        val session = _liveLinkProCoreSession.value ?: return
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        val now = System.currentTimeMillis()
        val message = LiveLinkChatMessage(
            messageId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            authorRole = LiveLinkActorRole.OWNER,
            body = trimmed,
            createdAtMs = now
        )
        _liveLinkProMessages.value = (listOf(message) + _liveLinkProMessages.value).take(80)
        addLiveLinkEvent(
            LiveLinkEvent(
                eventId = UUID.randomUUID().toString(),
                sessionId = session.sessionId,
                type = LiveLinkEventType.CHAT_MESSAGE,
                actorRole = LiveLinkActorRole.OWNER,
                message = "Mensaje enviado en chat LiveLink.",
                createdAtMs = now
            )
        )
    }

    fun generateLiveLinkReport() {
        val session = _liveLinkProCoreSession.value ?: return
        val (report, event) = liveLinkProEngine.buildReport(
            session = session,
            packets = liveLinkProPackets.toList(),
            events = _liveLinkProEvents.value,
            requests = _liveLinkProRequests.value
        )
        _liveLinkProReport.value = report
        addLiveLinkEvent(event)
    }

    private fun resolveLiveLinkRequest(requestId: String, approved: Boolean) {
        val request = _liveLinkProRequests.value.firstOrNull { it.requestId == requestId } ?: return
        val (resolved, event) = liveLinkProEngine.resolveRequest(request, approved)
        _liveLinkProRequests.value = _liveLinkProRequests.value.map {
            if (it.requestId == requestId) resolved else it
        }
        addLiveLinkEvent(event)

        if (approved && request.type == LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT) {
            val packet = buildLiveLinkTelemetryPacket(request.sessionId)
            val (snapshot, snapshotEvent) = liveLinkProEngine.captureSnapshot(packet)
            rememberLiveLinkPacket(snapshot.telemetryPacket)
            addLiveLinkEvent(snapshotEvent)
        }
    }

    private fun startLiveLinkTelemetryLoop(sessionId: String) {
        telemetryUploadJob?.cancel()
        telemetryUploadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _liveLinkProCoreSession.value?.isOpen == true) {
                try {
                    val coreSession = _liveLinkProCoreSession.value ?: break
                    val (checkedSession, expiredEvent) = liveLinkProEngine.expireIfNeeded(coreSession)
                    if (expiredEvent != null) {
                        _liveLinkProCoreSession.value = checkedSession
                        _liveLinkProSession.value = _liveLinkProSession.value?.copy(
                            status = checkedSession.state.name,
                            endedAt = checkedSession.endedAtMs
                        )
                        addLiveLinkEvent(expiredEvent)
                        liveSessionDao.updateSessionStatus(sessionId, checkedSession.state.name)
                        checkedSession.endedAtMs?.let { liveSessionDao.updateSessionEndedAt(sessionId, it) }
                        break
                    }

                    val packet = buildLiveLinkTelemetryPacket(sessionId)
                    rememberLiveLinkPacket(packet)
                    val pidsJson = Json.encodeToString(packet)
                    val snapshot = LiveSnapshotEntity(
                        snapshotId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        pidValues = pidsJson,
                        notes = packet.degradedReason ?: packet.sourceQuality.name
                    )

                    // Save local snapshot
                    liveSessionDao.insertLiveSnapshot(snapshot)

                    // Upload to cloud
                    runCatching {
                        SupabaseManager.client.postgrest["live_snapshots"].insert(snapshot)
                    }.onFailure { error ->
                        Log.w("ObdViewModel", "LiveLink telemetry stored locally; cloud snapshot sync failed", error)
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error uploading telemetry snapshot", e)
                }
                delay(
                    LiveLinkFrequencyPolicy.intervalMs(
                        mode = _liveLinkProCoreSession.value?.mode ?: LiveLinkMode.REMOTE_READ_ONLY,
                        averageLatencyMs = _liveLinkProLatestPacket.value?.samples?.map { it.latencyMs }?.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
                    )
                )
            }
        }
    }

    private fun startNotesPollingLoop(sessionId: String) {
        notesPollingJob?.cancel()
        notesPollingJob = viewModelScope.launch(Dispatchers.IO) {
            var lastPollTime = 0L
            while (isActive && _liveLinkProCoreSession.value?.isOpen == true) {
                try {
                    val newNotes = SupabaseManager.client.postgrest["mechanic_notes"]
                        .select {
                            filter {
                                eq("sessionId", sessionId)
                                gt("createdAt", lastPollTime)
                            }
                        }.decodeList<MechanicNoteEntity>()

                    if (newNotes.isNotEmpty()) {
                        newNotes.forEach { note ->
                            liveSessionDao.insertMechanicNote(note)
                            addRemoteLiveLinkNote(note)
                            if (note.createdAt > lastPollTime) {
                                lastPollTime = note.createdAt
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error polling mechanic notes", e)
                }
                delay(4000L) // Poll recommendations/notes every 4 seconds
            }
        }
    }

    private fun buildLiveLinkTelemetryPacket(sessionId: String): LiveLinkTelemetryPacket {
        return LiveLinkTelemetryMapper.fromObdSamples(
            sessionId = sessionId,
            connectionState = connectionState.value.name,
            adapterQuality = if (isAdapterPro.value) "PRO_ADAPTER" else "STANDARD_OR_UNKNOWN",
            samples = telemetrySamples.value.values,
            activeDtcs = activeDtcs.value,
            freezeFrameAvailable = _freezeFrameData.value.isNotEmpty()
        )
    }

    private fun rememberLiveLinkPacket(packet: LiveLinkTelemetryPacket) {
        _liveLinkProLatestPacket.value = packet
        liveLinkProPackets.add(packet)
        if (liveLinkProPackets.size > 900) {
            liveLinkProPackets.removeAt(0)
        }
    }

    private fun addLiveLinkEvent(event: LiveLinkEvent) {
        _liveLinkProEvents.value = (listOf(event) + _liveLinkProEvents.value).take(100)
    }

    private fun addRemoteLiveLinkNote(note: MechanicNoteEntity) {
        val message = LiveLinkChatMessage(
            messageId = note.noteId,
            sessionId = note.sessionId,
            authorRole = LiveLinkActorRole.REMOTE_MECHANIC,
            authorId = note.authorId,
            type = LiveLinkMessageType.NOTE,
            body = note.content,
            createdAtMs = note.createdAt
        )
        _liveLinkProMessages.value = (listOf(message) + _liveLinkProMessages.value).take(80)
    }

    // ── Marketplace (Bids and requests) ──
    val serviceRequests: StateFlow<List<ServiceRequestEntity>> = marketplaceDao.getRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openServiceRequests: StateFlow<List<ServiceRequestEntity>> = marketplaceDao.getOpenRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openPartRequests: StateFlow<List<PartRequestEntity>> = marketplaceDao.getOpenPartRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Local Commerce & Delivery (Pulperías & Sodas) ──
    val activeCommerceOrders: StateFlow<List<com.elysium369.meet.commerce.data.local.CommerceOrderEntity>> =
        commerceOrderDao?.getActiveOrders()
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            ?: MutableStateFlow(emptyList())

    val completedCommerceOrders: StateFlow<List<com.elysium369.meet.commerce.data.local.CommerceOrderEntity>> =
        commerceOrderDao?.getCompletedOrders()
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            ?: MutableStateFlow(emptyList())

    val activeCourierMissions: StateFlow<List<com.elysium369.meet.commerce.data.local.CommerceOrderEntity>> =
        commerceOrderDao?.getActiveCourierMissions()
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            ?: MutableStateFlow(emptyList())

    fun createCommerceOrder(
        commerceType: String,
        merchantId: String,
        merchantName: String,
        merchantPhone: String,
        merchantAddress: String,
        merchantLat: Double,
        merchantLng: Double,
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        deliveryLat: Double,
        deliveryLng: Double,
        itemsJson: String,
        itemsSubtotalMinor: Long,
        deliveryFeeMinor: Long,
        paymentMethod: String,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val orderId = java.util.UUID.randomUUID().toString()
            val pin = (1000..9999).random().toString()
            val total = itemsSubtotalMinor + deliveryFeeMinor
            val order = com.elysium369.meet.commerce.data.local.CommerceOrderEntity(
                orderId = orderId,
                commerceType = commerceType,
                merchantId = merchantId,
                merchantName = merchantName,
                merchantPhone = merchantPhone,
                merchantAddress = merchantAddress,
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                customerId = localDeviceId,
                customerName = customerName,
                customerPhone = customerPhone,
                deliveryAddress = deliveryAddress,
                deliveryLat = deliveryLat,
                deliveryLng = deliveryLng,
                itemsJson = itemsJson,
                itemsSubtotalMinor = itemsSubtotalMinor,
                deliveryFeeMinor = deliveryFeeMinor,
                totalAmountMinor = total,
                paymentMethod = paymentMethod,
                paymentStatus = "PENDING", // A selected payment method is not confirmed funds.
                deliveryPin = pin,
                status = "PLACED",
                createdAt = System.currentTimeMillis()
            )
            commerceOrderDao?.insertOrder(order)
            onCreated(orderId)
        }
    }

    fun advanceCommerceMerchantStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            val readyAt = if (newStatus == "READY_FOR_PICKUP") System.currentTimeMillis() else null
            commerceOrderDao?.updateMerchantStatus(orderId, newStatus, readyAt)
        }
    }

    fun assignCommerceCourier(
        orderId: String,
        courierId: String,
        courierName: String,
        courierPhone: String,
        courierVehicle: String
    ) {
        viewModelScope.launch {
            commerceOrderDao?.assignCourier(orderId, courierId, courierName, courierPhone, courierVehicle)
        }
    }

    fun advanceCommerceCourierStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            val pickedUpAt = if (newStatus == "IN_TRANSIT") System.currentTimeMillis() else null
            commerceOrderDao?.updateCourierTransitStatus(orderId, newStatus, pickedUpAt)
        }
    }

    fun verifyCommerceDeliveryPin(
        orderId: String,
        enteredPin: String,
        rating: Int = 5,
        review: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val order = commerceOrderDao?.getOrderById(orderId)
            if (order == null) {
                onError("Orden no encontrada")
                return@launch
            }
            if (order.deliveryPin.trim() != enteredPin.trim()) {
                onError("PIN de entrega incorrecto. Pide el código de 4 dígitos al cliente.")
                return@launch
            }
            val epochMs = System.currentTimeMillis()
            val raw = "$orderId|${order.totalAmountMinor}|${order.customerId}|${order.courierId}|$epochMs|DELIVERED_PIN_VERIFIED"
            val hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(raw)
            commerceOrderDao?.completeDeliveryWithPin(orderId, epochMs, hash)
            commerceOrderDao?.rateOrder(orderId, rating, review)
            onSuccess()
        }
    }

    fun cancelCommerceOrder(orderId: String) {
        viewModelScope.launch {
            commerceOrderDao?.cancelOrder(orderId)
        }
    }

    fun cancelServiceRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = marketplaceDao.getRequestById(requestId)
            marketplaceDao.markServiceCancelled(requestId)
            // Reembolso constitucional: si el especialista asignado pagó la comisión del 5%, se le reembolsa
            val mechanicId = req?.assignedMechanicId
            if (req != null && !mechanicId.isNullOrBlank()) {
                val providerProfile = providerProfileDao.getProfile(mechanicId)
                    ?: providerProfileDao.getProfilesForUser(mechanicId).firstOrNull()?.firstOrNull()
                if (providerProfile != null && providerProfile.specialties.isNotBlank()) {
                    runCatching {
                        val profile = com.elysium369.meet.provider.domain.models.ProviderServiceProfileData.fromJsonString(providerProfile.specialties)
                        val fee = profile.calculateFee(req.priceOffer.toLong())
                        val refunded = profile.withTopUp(fee, "REFUND-$requestId")
                        providerProfileDao.updateSpecialties(providerProfile.profileId, refunded.toJsonString(), System.currentTimeMillis())
                    }.onFailure { Log.w("ObdViewModel", "Service cancellation refund failed", it) }
                }
            }
        }
    }

    private val _dekraConciergeSubmission = MutableStateFlow<DekraConciergeSubmissionState>(
        DekraConciergeSubmissionState.Idle,
    )
    val dekraConciergeSubmission: StateFlow<DekraConciergeSubmissionState> =
        _dekraConciergeSubmission.asStateFlow()

    // Shop/Workshop bids derived dynamically
    private val _shopId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val shopBids: StateFlow<List<ServiceBidEntity>> = _shopId
        .flatMapLatest { shopId ->
            shopId?.let { marketplaceDao.getBidsByShop(it) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getBidsForRequest(requestId: String): Flow<List<ServiceBidEntity>> {
        return marketplaceDao.getBidsForRequest(requestId)
    }

    fun createServiceRequest(
        vehicleId: String,
        problem: String,
        description: String,
        location: String,
        priority: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        phone: String = "",
        priceOffer: Double = 0.0,
        serviceId: String? = null,
        serviceCategory: String? = null,
        dtcCodes: List<String> = activeDtcs.value,
        serviceMetadata: String = "",
        requestId: String = UUID.randomUUID().toString(),
        createdAtMs: Long = System.currentTimeMillis(),
    ) {
        val catalogService = WorkshopServiceCatalog.serviceById(serviceId)
        val enrichedDescription = buildString {
            if (catalogService != null) {
                appendLine(WorkshopServiceCatalog.requestSummary(catalogService, dtcCodes))
                appendLine()
            } else if (serviceCategory?.isNotBlank() == true || serviceMetadata.isNotBlank()) {
                appendLine("[Elysium_SERVICE_CATALOG]")
                serviceCategory?.takeIf { it.isNotBlank() }?.let { appendLine("service_category=$it") }
                if (serviceMetadata.isNotBlank()) appendLine(serviceMetadata.trim())
                if (dtcCodes.isNotEmpty()) appendLine("dtc_codes=${dtcCodes.joinToString()}")
                appendLine("[/Elysium_SERVICE_CATALOG]")
                appendLine()
            }
            append(description.trim())
        }.trim()

        val request = ServiceRequestEntity(
            requestId = requestId,
            vehicleId = vehicleId,
            problem = problem,
            priority = priority,
            description = enrichedDescription.ifBlank { description },
            location = location,
            radiusKm = 15.0,
            status = "OPEN",
            autoDtcCode = dtcCodes.firstOrNull() ?: activeDtcs.value.firstOrNull(),
            createdAt = createdAtMs,
            latitude = latitude,
            longitude = longitude,
            phone = phone,
            priceOffer = priceOffer
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertRequest(request)
                runCatching {
                    SupabaseManager.client.postgrest["service_requests"].insert(request)
                }.onFailure { Log.w("ObdViewModel", "Service request saved locally; cloud unavailable", it) }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to create service request", e)
            }
        }
    }

    fun createServiceRequestV2(
        request: com.elysium369.meet.core.services.serviceos.ServiceRequestV2,
    ) {
        val evidence = request.evidence
        val metadata = org.json.JSONObject().apply {
            put("schema", "Elysium_SERVICE_REQUEST_V2")
            put("request_id", request.id)
            put("created_at_ms", request.createdAtMs)
            put("urgency", request.urgency.name)
            put("mobility", request.mobility.name)
            put("preferred_modality", request.preferredModality.name)
            put("state", request.state.name)
            put("location_zone", org.json.JSONObject().apply {
                put("name", request.locationZone.approximateZoneName)
                put("radius_km", request.locationZone.approximateRadiusKm)
                put("latitude", request.locationZone.latitude ?: org.json.JSONObject.NULL)
                put("longitude", request.locationZone.longitude ?: org.json.JSONObject.NULL)
                put("exact_address_masked", request.locationZone.exactAddressMasked)
            })
            put("evidence", org.json.JSONObject().apply {
                put("vehicle_id", evidence.vehicleId)
                put("vehicle_display_name", evidence.vehicleDisplayName)
                put("masked_vin", evidence.maskedVin ?: org.json.JSONObject.NULL)
                put("active_dtcs", org.json.JSONArray(evidence.activeDtcs))
                put("freeze_frame_summary", evidence.freezeFrameSummary ?: org.json.JSONObject.NULL)
                put("odometer_km", evidence.odometertKm ?: org.json.JSONObject.NULL)
                put("battery_voltage", evidence.batteryVoltage ?: org.json.JSONObject.NULL)
                put("symptom", evidence.userReportedSymptom)
                put("symptom_category", evidence.userSymptomCategory)
                put("photo_uris", org.json.JSONArray(evidence.photoUris))
                put("video_uris", org.json.JSONArray(evidence.videoUris))
                put("audio_uris", org.json.JSONArray(evidence.audioUris))
            })
            request.estimatedBudget?.let { budget ->
                put("estimated_budget", org.json.JSONObject().apply {
                    put("amount_minor", budget.amountMinor)
                    put("currency", budget.currency.name)
                })
            }
        }.toString()

        createServiceRequest(
            vehicleId = evidence.vehicleId,
            problem = evidence.userReportedSymptom,
            description = evidence.userSymptomCategory,
            location = request.locationZone.approximateZoneName,
            priority = if (request.urgency == com.elysium369.meet.core.services.serviceos.ServiceRequestUrgency.URGENT_BREAKDOWN) "HIGH" else "MEDIUM",
            latitude = request.locationZone.latitude ?: 0.0,
            longitude = request.locationZone.longitude ?: 0.0,
            dtcCodes = evidence.activeDtcs,
            serviceMetadata = metadata,
            requestId = request.id,
            createdAtMs = request.createdAtMs,
        )
    }

    fun publishDekraConciergeRequest(
        request: com.elysium369.meet.core.services.dekra.DekraConciergeRequest,
    ) {
        val validationErrors =
            com.elysium369.meet.core.services.dekra.DekraConciergePolicy.validate(request)
        if (validationErrors.isNotEmpty()) {
            _dekraConciergeSubmission.value = DekraConciergeSubmissionState.Failed(
                "La solicitud está incompleta o el plan de traslado no es seguro.",
            )
            return
        }
        if (_dekraConciergeSubmission.value is DekraConciergeSubmissionState.Publishing) return

        _dekraConciergeSubmission.value = DekraConciergeSubmissionState.Publishing
        viewModelScope.launch(Dispatchers.IO) {
            val metadata = dekraConciergeJson.encodeToString(request)
            val description = buildString {
                appendLine("[Elysium_DEKRA_CONCIERGE_V1]")
                appendLine(metadata)
                appendLine("[/Elysium_DEKRA_CONCIERGE_V1]")
                appendLine()
                appendLine("Servicio de custodia, prechequeo y traslado a inspección técnica vehicular.")
                appendLine("El resultado es emitido exclusivamente por DEKRA; Elysium no garantiza aprobación.")
                appendLine("La tarifa oficial DEKRA y la tarifa del servicio Elysium se cotizan por separado.")
            }.trim()
            val entity = ServiceRequestEntity(
                requestId = request.id,
                vehicleId = request.vehicleId,
                problem = "Traslado y acompañamiento a DEKRA",
                priority = "MEDIUM",
                description = description,
                location = request.pickupZone,
                radiusKm = 15.0,
                status = "OPEN",
                autoDtcCode = request.activeDtcs.firstOrNull(),
                createdAt = request.createdAtMs,
                phone = request.contactPhone,
                priceOffer = 0.0,
            )

            try {
                marketplaceDao.insertRequest(entity)
                val cloudPublished = runCatching {
                    SupabaseManager.client.postgrest["service_requests"].insert(entity)
                }.onFailure {
                    Log.w("ObdViewModel", "DEKRA concierge saved locally; cloud unavailable", it)
                }.isSuccess
                _dekraConciergeSubmission.value = DekraConciergeSubmissionState.Saved(
                    requestId = request.id,
                    cloudPublished = cloudPublished,
                )
            } catch (error: Exception) {
                Log.e("ObdViewModel", "Failed to persist DEKRA concierge request", error)
                _dekraConciergeSubmission.value = DekraConciergeSubmissionState.Failed(
                    "No fue posible guardar la solicitud. Revisa el almacenamiento e inténtalo de nuevo.",
                )
            }
        }
    }

    fun resetDekraConciergeSubmission() {
        if (_dekraConciergeSubmission.value !is DekraConciergeSubmissionState.Publishing) {
            _dekraConciergeSubmission.value = DekraConciergeSubmissionState.Idle
        }
    }

    fun placeServiceBid(
        requestId: String,
        price: Double,
        estimatedHours: Double,
        warrantyDays: Int,
        message: String,
        providerPhone: String = "",
        providerName: String = "",
        providerId: String? = null,
        providerRating: Double = 0.0,
    ) {
        val principal = activePrincipal.value
        val resolvedShopId = providerId ?: _shopId.value ?: principal.id
        val resolvedProviderName = providerName.ifBlank { "Proveedor de Servicio" }
        val resolvedPhone = providerPhone

        val bid = ServiceBidEntity(
            bidId = UUID.randomUUID().toString(),
            requestId = requestId,
            shopId = resolvedShopId,
            shopName = resolvedProviderName,
            shopRating = providerRating,
            providerPhone = resolvedPhone,
            price = price,
            estimatedHours = estimatedHours,
            warrantyDays = warrantyDays,
            message = message,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.upsertBidRespectingRequestClaim(bid)
                SupabaseManager.client.postgrest["service_bids"].insert(bid)
                // ── Notification: avisarle al USUARIO que le llegó una cotización ──
                withContext(Dispatchers.Main) {
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.showServiceBidReceivedPopUp(
                        context = context,
                        requestId = requestId,
                        shopName = resolvedProviderName,
                        price = price,
                        estimatedHours = estimatedHours
                    )
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to place bid", e)
            }
        }
    }

    /** Idempotent: only accepts if request is still OPEN */
    fun acceptBid(requestId: String, bidId: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bid = marketplaceDao.getBidById(bidId)
                val requestBeforeAcceptance = marketplaceDao.getRequestById(requestId)
                if (requestBeforeAcceptance?.description?.contains("[Elysium_DEKRA_CONCIERGE_V1]") == true) {
                    val eligible = bid?.let { candidate -> hasEligibleDekraProvider(candidate.shopId) } == true
                    if (!eligible) {
                        withContext(Dispatchers.Main) {
                            context?.let {
                                android.widget.Toast.makeText(
                                    it,
                                    "Esta oferta no tiene un conductor de transporte verificado con licencia registrada.",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        return@launch
                    }
                }
                val accepted = marketplaceDao.acceptBidAtomically(
                    requestId = requestId,
                    bidId = bidId,
                    mechanicPhone = bid?.providerPhone.orEmpty(),
                )
                if (!accepted) {
                    withContext(Dispatchers.Main) {
                        context?.let {
                            android.widget.Toast.makeText(it, "⚠️ Este servicio ya fue asignado a otro mecánico", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }
                scheduleVanguardCommerceSync()

                // ── Notification: avisarle al USUARIO que su servicio fue aceptado ──
                val assignedBid = marketplaceDao.getBidById(bidId)
                val mechanicName = assignedBid?.shopName ?: "Un mecánico"
                withContext(Dispatchers.Main) {
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.showServiceAcceptedPopUp(
                        context = this@ObdViewModel.context,
                        requestId = requestId,
                        mechanicName = mechanicName
                    )
                }

                runCatching {
                    val request = marketplaceDao.getRequestById(requestId)
                    val bid = marketplaceDao.getBidById(bidId)
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "assignedMechanicId" to request?.assignedMechanicId,
                            "assignedMechanicName" to request?.assignedMechanicName,
                            "assignedMechanicPhone" to request?.assignedMechanicPhone,
                            "priceOffer" to request?.priceOffer,
                            "escrowStatus" to request?.escrowStatus
                        )
                    ) {
                        filter { eq("requestId", requestId) }
                    }
                    SupabaseManager.client.postgrest["service_bids"].update(mapOf("status" to bid?.status.orEmpty().ifBlank { "ACCEPTED" })) {
                        filter { eq("bidId", bidId) }
                    }
                }.onFailure { Log.w("ObdViewModel", "Bid accepted locally; cloud sync unavailable", it) }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to accept bid", e)
            }
        }
    }

    /** Mechanic takes an open service request — idempotent, first-come-first-served */
    fun takeMechanicRequest(requestId: String, mechanicId: String, mechanicName: String, mechanicPhone: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = marketplaceDao.getRequestById(requestId)
            if (request?.description?.contains("[Elysium_DEKRA_CONCIERGE_V1]") == true) {
                val eligible = hasEligibleDekraProvider(mechanicId)
                if (!eligible) {
                    withContext(Dispatchers.Main) {
                        context?.let {
                            android.widget.Toast.makeText(
                                it,
                                "Este servicio requiere un conductor de transporte verificado con licencia registrada.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    return@launch
                }
            }
            val accepted = request != null && marketplaceDao.takeMechanicRequestAtomically(
                requestId = requestId,
                mechanicId = mechanicId,
                mechanicName = mechanicName,
                mechanicPhone = mechanicPhone,
                finalPrice = request.priceOffer
            )
            if (!accepted) {
                withContext(Dispatchers.Main) {
                    context?.let {
                        android.widget.Toast.makeText(it, "⚠️ Este servicio ya fue tomado por otro mecánico", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                scheduleVanguardCommerceSync()
                // ─── Direct Supabase sync for mechanic take ───
                runCatching {
                    val updatedReq = marketplaceDao.getRequestById(requestId)
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "assignedMechanicId" to mechanicId,
                            "assignedMechanicName" to mechanicName,
                            "assignedMechanicPhone" to mechanicPhone,
                            "priceOffer" to (updatedReq?.priceOffer ?: 0.0),
                        )
                    ) {
                        filter { eq("requestId", requestId) }
                    }
                }.onFailure { Log.w("ObdViewModel", "Mechanic take: cloud sync failed, outbox will retry", it) }
                withContext(Dispatchers.Main) {
                    context?.let {
                        android.widget.Toast.makeText(it, "✅ Servicio tomado exitosamente", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun hasEligibleDekraProvider(userId: String): Boolean =
        providerProfileDao.getActiveProfilesForUser(userId).first().any { profile ->
            com.elysium369.meet.core.services.dekra.DekraProviderEligibilityPolicy.isEligible(
                com.elysium369.meet.core.services.dekra.DekraProviderSnapshot(
                    providerType = profile.providerType,
                    active = profile.isActive,
                    verified = profile.verified,
                    licenseNumber = profile.licenseNumber,
                ),
            )
        }

    fun completeMechanicRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = marketplaceDao.completeAcceptedServiceOnce(requestId, System.currentTimeMillis())
            if (!completed) {
                Log.w("ObdViewModel", "Ignoring completion for non-accepted service request: $requestId")
            } else {
                scheduleVanguardCommerceSync()
                // V2 spec Phase 7: completion must trigger a Post-Scan prompt.
                com.elysium369.meet.core.reports.PostScanPrompt.request(requestId)
                // ── Notification: avisarle al USUARIO que su servicio fue completado ──
                val completedReq = marketplaceDao.getRequestById(requestId)
                withContext(Dispatchers.Main) {
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.showServiceCompletedPopUp(
                        context = this@ObdViewModel.context,
                        requestId = requestId,
                        mechanicName = completedReq?.assignedMechanicName
                    )
                }
                // ─── Direct Supabase sync ───
                runCatching {
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf("status" to "COMPLETED", "completedAt" to System.currentTimeMillis().toString())
                    ) { filter { eq("requestId", requestId) } }
                }.onFailure { Log.w("ObdViewModel", "Mechanic complete: Supabase sync failed", it) }
            }
        }
    }

    fun cancelMechanicRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (marketplaceDao.refundServiceAfterPaymentFailure(requestId)) {
                scheduleVanguardCommerceSync()
                // ── Notification: avisarle al USUARIO que su servicio fue cancelado ──
                withContext(Dispatchers.Main) {
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.showServiceCancelledPopUp(
                        context = this@ObdViewModel.context,
                        requestId = requestId
                    )
                }
                // ─── Direct Supabase sync ───
                runCatching {
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf("status" to "CANCELLED", "cancelledAt" to System.currentTimeMillis().toString())
                    ) { filter { eq("requestId", requestId) } }
                }.onFailure { Log.w("ObdViewModel", "Mechanic cancel: Supabase sync failed", it) }
            }
        }
    }

    val partsStores: StateFlow<List<PartsStoreEntity>> = marketplaceDao.getPartsStores()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val partRequests: StateFlow<List<PartRequestEntity>> = marketplaceDao.getPartRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getPartOffersForRequest(requestId: String): Flow<List<PartOfferEntity>> {
        return marketplaceDao.getPartOffersForRequest(requestId)
    }

    private fun currentCloudUserId(): String? {
        return SupabaseManager.client.auth.currentUserOrNull()?.id
            ?: com.elysium369.meet.data.remote.SupabaseModule.client.auth
                .currentUserOrNull()?.id
    }

    val currentUserId: String? get() = currentCloudUserId()
    val currentRideActorId: String get() = activePrincipalKernel.current().id

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ ACCOUNT DELETION — Required by Google Play Store policy ████
    // ═══════════════════════════════════════════════════════════════════════════

    private val _accountDeletionState = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    val accountDeletionState: StateFlow<AccountDeletionState> = _accountDeletionState.asStateFlow()

    sealed class AccountDeletionState {
        object Idle : AccountDeletionState()
        object InProgress : AccountDeletionState()
        data class Completed(val message: String) : AccountDeletionState()
        data class Failed(val error: String) : AccountDeletionState()
    }

    fun deleteAccount(context: android.content.Context) {
        if (_accountDeletionState.value is AccountDeletionState.InProgress) return
        _accountDeletionState.value = AccountDeletionState.InProgress

        viewModelScope.launch(Dispatchers.IO) {
            val userId = currentCloudUserId()
            if (userId == null) {
                _accountDeletionState.value = AccountDeletionState.Failed(
                    "No hay sesión activa. Inicia sesión antes de eliminar la cuenta."
                )
                return@launch
            }

            try {
                // 1. Mark user data as deleted in Supabase (soft-delete for legal retention)
                val deletedAt = System.currentTimeMillis().toString()

                // Anonymize ride requests
                runCatching {
                    SupabaseManager.client.postgrest["ride_requests"].update(
                        mapOf("passenger_name" to "Usuario eliminado", "phone" to "", "status" to "ACCOUNT_DELETED")
                    ) { filter { eq("passenger_id", userId) } }
                }
                // Anonymize as driver
                runCatching {
                    SupabaseManager.client.postgrest["ride_requests"].update(
                        mapOf("assigned_driver_name" to "Conductor eliminado", "assigned_driver_phone" to "")
                    ) { filter { eq("assigned_driver_id", userId) } }
                }
                // Anonymize service requests
                runCatching {
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf("phone" to "", "status" to "ACCOUNT_DELETED")
                    ) { filter { eq("vehicle_id", userId) } }
                }
                // Deactivate provider profiles
                runCatching {
                    SupabaseManager.client.postgrest["provider_profiles"].update(
                        mapOf("is_active" to false, "deleted_at" to deletedAt, "phone" to "", "owner_name" to "Eliminado")
                    ) { filter { eq("user_id", userId) } }
                }
                // Anonymize ratings
                runCatching {
                    SupabaseManager.client.postgrest["ratings"].update(
                        mapOf("source_name" to "Eliminado")
                    ) { filter { eq("source_id", userId) } }
                }
                // Mark tow truck requests
                runCatching {
                    SupabaseManager.client.postgrest["tow_truck_requests"].update(
                        mapOf("status" to "ACCOUNT_DELETED", "phone" to "")
                    ) { filter { eq("requester_id", userId) } }
                }

                // 2. Record deletion request for audit trail
                runCatching {
                    SupabaseManager.client.postgrest["account_deletion_requests"].insert(
                        mapOf(
                            "user_id" to userId,
                            "requested_at" to deletedAt,
                            "status" to "COMPLETED",
                            "app_version" to com.elysium369.meet.BuildConfig.VERSION_NAME,
                        )
                    )
                }

                // 3. Sign out from Supabase
                runCatching {
                    SupabaseManager.client.auth.signOut()
                }
                runCatching {
                    com.elysium369.meet.data.remote.SupabaseModule.client.auth.signOut()
                }

                // 4. Clear ALL local databases
                val db = context.getDatabasePath("meet_database")
                if (db.exists()) {
                    context.deleteDatabase("meet_database")
                }
                // Clear shared preferences
                listOf(
                    "meet_prefs", "elysium_universal_services", "meet_ride_prefs",
                    "meet_driver_prefs", "meet_obd_prefs",
                ).forEach { prefName ->
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }

                android.util.Log.i("ElysiumAccount", "Account deleted for userId=$userId")

                _accountDeletionState.value = AccountDeletionState.Completed(
                    "Tu cuenta ha sido eliminada exitosamente. La app se reiniciará."
                )

            } catch (e: Exception) {
                android.util.Log.e("ElysiumAccount", "Account deletion failed", e)
                _accountDeletionState.value = AccountDeletionState.Failed(
                    "Error al eliminar la cuenta: ${e.message?.take(120) ?: "Error desconocido"}. " +
                    "Contacta privacy@elysiumvanguard.com si el problema persiste."
                )
            }
        }
    }

    fun resetAccountDeletionState() {
        _accountDeletionState.value = AccountDeletionState.Idle
    }

    private val _usageProfileSyncState = MutableStateFlow(
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .getString("usage_profile_sync_state", "PENDING") ?: "PENDING",
    )
    val usageProfileSyncState: StateFlow<String> = _usageProfileSyncState.asStateFlow()

    /**
     * Reconciles the local onboarding intent with the authenticated authority.
     * Safe to call repeatedly: server activation and the local user/profile key
     * make it idempotent, including after process death or account switching.
     */
    fun syncSelectedUsageProfile() {
        val userId = currentCloudUserId() ?: run {
            _usageProfileSyncState.value = "AUTHENTICATION_REQUIRED"
            return
        }
        val preferences = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val profile = OnboardingUsageProfile.fromStorageId(
            preferences.getString("user_profile", OnboardingUsageProfile.OWNER.storageId),
        ) ?: run {
            _usageProfileSyncState.value = "INVALID_LOCAL_PROFILE"
            return
        }
        if (
            preferences.getString("usage_profile_synced_user_id", null) == userId &&
            preferences.getString("usage_profile_synced_value", null) == profile.storageId &&
            preferences.getString("usage_profile_sync_state", null) == "SYNCED"
        ) return

        _usageProfileSyncState.value = "SYNCING"
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = UsageProfileGateway.activate(profile)) {
                is UsageProfileSyncResult.Activated -> {
                    preferences.edit()
                        .putString("usage_profile_synced_user_id", userId)
                        .putString("usage_profile_synced_value", profile.storageId)
                        .putString("usage_profile_sync_state", "SYNCED")
                        .apply()
                    _usageProfileSyncState.value = if (result.verificationRequired) {
                        "SYNCED_VERIFICATION_REQUIRED"
                    } else {
                        "SYNCED"
                    }
                }
                UsageProfileSyncResult.AuthenticationRequired ->
                    _usageProfileSyncState.value = "AUTHENTICATION_REQUIRED"
                is UsageProfileSyncResult.Rejected -> {
                    Log.w("ElysiumIdentity", "Usage profile rejected: ${result.code}")
                    _usageProfileSyncState.value = "REJECTED"
                }
                is UsageProfileSyncResult.Unavailable -> {
                    Log.w("ElysiumIdentity", "Usage profile sync unavailable: ${result.message}")
                    _usageProfileSyncState.value = "RETRY_PENDING"
                }
            }
        }
    }

    private val _platformOwnerAccess = MutableStateFlow(PlatformOwnerAccess.UNKNOWN)
    val platformOwnerAccess: StateFlow<PlatformOwnerAccess> =
        _platformOwnerAccess.asStateFlow()

    fun refreshPlatformOwnerAccess() {
        if (currentCloudUserId() == null) {
            _platformOwnerAccess.value = PlatformOwnerAccess.SIGNED_OUT
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _platformOwnerAccess.value = try {
                if (PlatformTrustCenterGateway.hasOwnerAccess()) {
                    PlatformOwnerAccess.GRANTED
                } else {
                    PlatformOwnerAccess.DENIED
                }
            } catch (error: Exception) {
                Log.w("ElysiumTrustCenter", "Owner authority check unavailable", error)
                PlatformOwnerAccess.UNAVAILABLE
            }
        }
    }

    fun refreshOwnTrustDecisions() {
        val actorId = currentCloudUserId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PlatformTrustCenterGateway.loadOwnApplications() }
                .onSuccess { applications ->
                    val hasPassengerApplication = applications.any {
                        it.serviceType == "PASSENGER" && it.profileReference == "primary"
                    }
                    val localPassenger = if (!hasPassengerApplication) {
                        rideDao.getPassengerVerification(actorId)?.takeIf {
                            it.status in setOf("PENDING", RideVerificationPolicy.PILOT_APPROVED) &&
                                evaluatePassengerEvidence(it).isReady
                        }
                    } else {
                        null
                    }
                    val passengerSubmitted = localPassenger?.let {
                        submitPassengerTrustApplication(it).isSuccess
                    } == true
                    val reconciledApplications = if (passengerSubmitted) {
                        runCatching { PlatformTrustCenterGateway.loadOwnApplications() }
                            .getOrDefault(applications)
                    } else {
                        applications
                    }
                    val latestByType = reconciledApplications
                        .groupBy { it.serviceType }
                        .mapValues { (_, values) -> values.maxBy { it.submittedAt } }
                    val latestDriverApplication = latestByType["RIDE_DRIVER"]
                    latestDriverApplication?.let { application ->
                        if (currentCloudUserId() == actorId) {
                            com.elysium369.meet.ride.domain.RideDriverSessionRestoration.restore(
                                actorId, application, rideDao.getDriverVerification(actorId),
                            )?.let { rideDao.insertDriverVerification(it) }
                            ensurePassengerRoleForDriver(actorId)?.let { passenger ->
                                // An approved driver passed the stricter identity
                                // review and receives passenger access immediately.
                                // Pending drivers still submit the passenger record
                                // so both decisions can progress independently.
                                if (
                                    application.status != "APPROVED" &&
                                    latestByType["PASSENGER"] == null
                                ) {
                                    submitPassengerTrustApplication(passenger)
                                }
                            }
                        }
                    }
                    latestByType["PASSENGER"]?.let { application ->
                        if (
                            currentCloudUserId() == actorId &&
                            latestDriverApplication?.status != "APPROVED"
                        ) {
                            com.elysium369.meet.ride.domain.RideDriverSessionRestoration.restorePassenger(
                                actorId, application, rideDao.getPassengerVerification(actorId),
                            )?.let { rideDao.insertPassengerVerification(it) }
                        }
                    }
                }
                .onFailure {
                    Log.w("ElysiumTrustCenter", "Own verification decision sync unavailable", it)
                }
        }
    }

    private fun currentProviderUserId(): String {
        return currentCloudUserId() ?: activePrincipalKernel.current().id
    }

    // ═══════════════════════════════════════════════════════════════
    // PROVIDER ROLE MANAGEMENT SYSTEM
    // ═══════════════════════════════════════════════════════════════

    /** Active provider profiles for the current user */
    private val _userProviderProfiles = MutableStateFlow<List<ProviderProfileEntity>>(emptyList())
    val userProviderProfiles: StateFlow<List<ProviderProfileEntity>> = _userProviderProfiles.asStateFlow()

    /** Convenience booleans — is the current user registered as each type? */
    private val _isMechanic = MutableStateFlow(false)
    val isMechanic: StateFlow<Boolean> = _isMechanic.asStateFlow()

    private val _isTowTruckDriver = MutableStateFlow(false)
    val isTowTruckDriver: StateFlow<Boolean> = _isTowTruckDriver.asStateFlow()

    private val _isPartsStore = MutableStateFlow(false)
    val isPartsStore: StateFlow<Boolean> = _isPartsStore.asStateFlow()

    private var providerRolesJob: Job? = null

    /** Load and refresh provider profiles for the current user */
    fun refreshProviderRoles() {
        val userId = currentProviderUserId()
        providerRolesJob?.cancel()
        providerRolesJob = viewModelScope.launch(Dispatchers.IO) {
            if (currentCloudUserId() != null) {
                runCatching { PlatformTrustCenterGateway.loadOwnApplications() }
                    .onSuccess { applications ->
                        val profiles = providerProfileDao.getProfilesForUser(userId).first()
                        val decisions = applications
                            .associateBy { it.profileReference }
                        profiles.forEach { profile ->
                            val decision = decisions[profile.profileId] ?: return@forEach
                            providerProfileDao.setProfileVerified(
                                profileId = profile.profileId,
                                verified = decision.status == "APPROVED",
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                    }
                    .onFailure {
                        Log.w("ElysiumTrustCenter", "Provider review sync unavailable", it)
                    }
            }
            providerProfileDao.getProfilesForUser(userId).collect { profiles ->
                val activeProfiles = profiles.filter { it.isActive && (it.verified || com.elysium369.meet.BuildConfig.DEBUG) }
                _userProviderProfiles.value = profiles
                _isMechanic.value = activeProfiles.any {
                    val t = com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType)
                    t == com.elysium369.meet.core.services.kernel.ProviderType.MECHANIC || t == com.elysium369.meet.core.services.kernel.ProviderType.WORKSHOP
                }
                _isTowTruckDriver.value = activeProfiles.any {
                    com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType) == com.elysium369.meet.core.services.kernel.ProviderType.TOW_PROVIDER
                }
                _isPartsStore.value = activeProfiles.any {
                    com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType) == com.elysium369.meet.core.services.kernel.ProviderType.PARTS_STORE
                }
            }
        }
    }

    /** Register an authenticated user in any supported service-provider category. */
    fun registerAsProvider(
        providerType: String,
        businessName: String,
        ownerName: String,
        phone: String,
        location: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        specialties: String = "",
        radiusKm: Double = 25.0,
        licenseNumber: String = "",
        context: android.content.Context? = null
    ) {
        val cloudUserId = currentCloudUserId()
        val userId = cloudUserId
            ?: activePrincipalKernel.current().id.takeIf(String::isNotBlank)
            ?: "local_provider_user"
        val canonicalProviderType =
            com.elysium369.meet.core.services.kernel.ProviderType
                .fromDbValueStrict(providerType)

        viewModelScope.launch(Dispatchers.IO) {
            // Idempotent: check if already registered
            val existing = providerProfileDao.getProfilesForUser(userId).first()
                .firstOrNull {
                    com.elysium369.meet.core.services.kernel.ProviderType
                        .fromDbValue(it.providerType) == canonicalProviderType
                }
            if (existing != null) {
                val updated = existing.copy(
                    businessName = businessName.ifBlank { existing.businessName },
                    ownerName = ownerName.ifBlank { existing.ownerName },
                    phone = phone.ifBlank { existing.phone },
                    location = location.ifBlank { existing.location },
                    latitude = if (latitude != 0.0) latitude else existing.latitude,
                    longitude = if (longitude != 0.0) longitude else existing.longitude,
                    specialties = specialties.ifBlank { existing.specialties },
                    radiusKm = radiusKm,
                    licenseNumber = licenseNumber.ifBlank { existing.licenseNumber },
                    isActive = true,
                    updatedAt = System.currentTimeMillis()
                )
                providerProfileDao.insertProfile(updated)
                withContext(Dispatchers.Main) {
                    context?.let {
                        val typeLabel = providerTypeLabel(canonicalProviderType.dbValue)
                        val msg = if (!existing.isActive) "✅ Perfil de $typeLabel reactivado" else "✅ Perfil de $typeLabel actualizado"
                        android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                refreshProviderRoles()
                return@launch
            }

            val profile = ProviderProfileEntity(
                profileId = java.util.UUID.randomUUID().toString(),
                userId = userId,
                providerType = canonicalProviderType.dbValue,
                businessName = businessName,
                ownerName = ownerName,
                phone = phone,
                location = location,
                latitude = latitude,
                longitude = longitude,
                specialties = specialties,
                radiusKm = radiusKm,
                licenseNumber = licenseNumber,
                isActive = true,
                verified = com.elysium369.meet.BuildConfig.DEBUG,
                rating = 0.0,
                totalJobs = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            providerProfileDao.insertProfile(profile)

            val cloudSubmission = if (cloudUserId != null) {
                runCatching {
                    PlatformTrustCenterGateway.submit(
                        ServiceVerificationSubmission(
                            serviceType = canonicalProviderType.dbValue,
                            profileReference = profile.profileId,
                            displayName = ownerName,
                            businessName = businessName,
                            phone = phone,
                            locationLabel = location,
                            licenseReference = licenseNumber.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            } else null

            withContext(Dispatchers.Main) {
                context?.let {
                    val typeLabel = providerTypeLabel(canonicalProviderType.dbValue)
                    val message = when {
                        cloudSubmission != null && cloudSubmission.isSuccess ->
                            "Solicitud de $typeLabel enviada al Centro de Confianza. Estado: pendiente."
                        cloudSubmission != null ->
                            "Perfil guardado localmente; la verificación remota está pendiente de sincronización."
                        else ->
                            "✅ Perfil de $typeLabel registrado localmente con éxito."
                    }
                    android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            refreshProviderRoles()
        }
    }

    /** Register or update a service provider profile */
    fun registerProviderProfile(
        providerType: String,
        businessName: String,
        ownerName: String,
        phone: String,
        location: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        specialties: String = "",
        radiusKm: Double = 25.0,
        licenseNumber: String = "",
        context: android.content.Context? = null
    ) {
        registerAsProvider(
            providerType = providerType,
            businessName = businessName,
            ownerName = ownerName,
            phone = phone,
            location = location,
            latitude = latitude,
            longitude = longitude,
            specialties = specialties,
            radiusKm = radiusKm,
            licenseNumber = licenseNumber,
            context = context
        )
    }

    /**
     * Replays every durable local trust application after authentication and
     * reconciles authoritative decisions. Safe to call on every app/session resume.
     */
    fun syncPendingTrustApplications() {
        val actorId = currentCloudUserId() ?: return
        refreshProviderRoles()
        refreshOwnTrustDecisions()
        viewModelScope.launch(Dispatchers.IO) {
            val driver = rideDao.getDriverVerification(actorId)
            if (
                driver != null &&
                driver.status in setOf("PENDING", RideVerificationPolicy.PILOT_APPROVED) &&
                evaluateDriverEvidence(driver).isReady
            ) {
                enqueueDriverPilotEnrollment(driver)
            }
        }
    }

    /** Toggle a provider profile active/inactive */
    fun toggleProviderProfile(profileId: String, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            providerProfileDao.setProfileActive(profileId, isActive, System.currentTimeMillis())
            refreshProviderRoles()
            // ─── Supabase sync ───
            runCatching {
                SupabaseManager.client.postgrest["provider_profiles"].update(
                    mapOf("is_active" to isActive, "updated_at" to System.currentTimeMillis().toString())
                ) { filter { eq("profile_id", profileId) } }
            }.onFailure { Log.w("ObdViewModel", "Provider toggle: Supabase sync failed", it) }
        }
    }

    /** Delete a provider profile entirely */
    fun deleteProviderProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            providerProfileDao.deleteProfile(profileId)
            refreshProviderRoles()
            // ─── Supabase sync ───
            runCatching {
                SupabaseManager.client.postgrest["provider_profiles"].update(
                    mapOf("is_active" to false, "deleted_at" to System.currentTimeMillis().toString())
                ) { filter { eq("profile_id", profileId) } }
            }.onFailure { Log.w("ObdViewModel", "Provider delete: Supabase sync failed", it) }
        }
    }

    /** Update specialties JSON (including wallet balance and transactions) for a provider profile */
    fun updateProviderProfileSpecialties(profileId: String, specialtiesJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            providerProfileDao.updateSpecialties(profileId, specialtiesJson, now)
            refreshProviderRoles()
            runCatching {
                SupabaseManager.client.postgrest["provider_profiles"].update(
                    mapOf("specialties" to specialtiesJson, "updated_at" to now.toString())
                ) { filter { eq("profile_id", profileId) } }
            }.onFailure { Log.w("ObdViewModel", "Provider specialties: Supabase sync failed", it) }
        }
    }

    /** Check if the current user can see provider-facing content for a given type */
    suspend fun canViewProviderContent(providerType: String): Boolean {
        val canonical = com.elysium369.meet.core.services.kernel.ProviderType
            .fromDbValueStrict(providerType)
        return providerProfileDao.getProfilesForUser(currentProviderUserId()).first().any {
            it.isActive &&
                it.verified &&
                com.elysium369.meet.core.services.kernel.ProviderType
                    .fromDbValue(it.providerType) == canonical
        }
    }

    private fun providerTypeLabel(providerType: String): String {
        return when (com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(providerType)) {
            com.elysium369.meet.core.services.kernel.ProviderType.MECHANIC, com.elysium369.meet.core.services.kernel.ProviderType.WORKSHOP -> "Mecánico"
            com.elysium369.meet.core.services.kernel.ProviderType.TOW_PROVIDER -> "Gruista"
            com.elysium369.meet.core.services.kernel.ProviderType.PARTS_STORE -> "Repuestera"
            com.elysium369.meet.core.services.kernel.ProviderType.RIDE_DRIVER -> "Chofer de Viajes"
            com.elysium369.meet.core.services.kernel.ProviderType.SERVICE_PROVIDER -> "Proveedor de Servicios"
            com.elysium369.meet.core.services.kernel.ProviderType.AUTO_LOCKSMITH -> "Cerrajería Automotriz & Llaves"
            com.elysium369.meet.core.services.kernel.ProviderType.UNKNOWN -> "Desconocido"
        }
    }

    private fun buildPartsStoreId(storeName: String, ownerId: String?): String {
        val base = storeName.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "repuestera_local" }
        return ownerId?.take(8)?.let { "${base}_$it" } ?: base
    }

    fun createPartRequest(
        serviceRequestId: String?,
        vehicleId: String,
        dtcCode: String?,
        partName: String,
        partNumber: String?,
        quantity: Int,
        oemPreference: String,
        deliveryLocation: String,
        urgencyMinutes: Int,
        customerNotes: String,
        partPosition: String = "N/A",
        phone: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ) {
        val request = PartRequestEntity(
            requestId = UUID.randomUUID().toString(),
            serviceRequestId = serviceRequestId,
            vehicleId = vehicleId,
            dtcCode = dtcCode,
            partName = partName,
            partNumber = partNumber?.takeIf { it.isNotBlank() },
            quantity = quantity.coerceAtLeast(1),
            oemPreference = oemPreference,
            deliveryLocation = deliveryLocation,
            urgencyMinutes = urgencyMinutes.coerceAtLeast(15),
            customerNotes = customerNotes,
            status = "OPEN",
            acceptedOfferId = null,
            createdAt = System.currentTimeMillis(),
            partPosition = partPosition,
            phone = phone,
            latitude = latitude,
            longitude = longitude
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertPartRequest(request)
                val userId = currentCloudUserId()
                if (userId != null) {
                    runCatching { SupabaseManager.client.postgrest["part_requests"].insert(request.toRemote(userId)) }
                        .onFailure { Log.w("ObdViewModel", "Part request saved locally; cloud table unavailable", it) }
                } else {
                    Log.i("ObdViewModel", "Part request saved locally; sign in to publish it to the live auction.")
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to create part request", e)
            }
        }
    }

    fun placePartOffer(
        partRequestId: String,
        storeName: String,
        brand: String,
        partNumber: String,
        condition: String,
        price: Double,
        deliveryFee: Double,
        etaMinutes: Int,
        warrantyDays: Int,
        message: String,
        compatibilityConfidence: String = "UNKNOWN",
        compatibilityNotes: String = ""
    ) {
        val ownerId = currentCloudUserId()
        val storeId = buildPartsStoreId(storeName, ownerId)
        val now = System.currentTimeMillis()
        val store = PartsStoreEntity(
            storeId = storeId,
            storeName = storeName.ifBlank { "Repuestera local" },
            rating = 4.8,
            phone = "",
            location = "Zona local",
            deliveryRadiusKm = 20.0,
            averageEtaMinutes = etaMinutes.coerceAtLeast(15),
            verified = false,
            createdAt = now
        )
        val offer = PartOfferEntity(
            offerId = UUID.randomUUID().toString(),
            partRequestId = partRequestId,
            storeId = store.storeId,
            storeName = store.storeName,
            brand = brand.ifBlank { "Marca por confirmar" },
            partNumber = partNumber.ifBlank { "Por confirmar" },
            condition = condition,
            price = price,
            deliveryFee = deliveryFee.coerceAtLeast(0.0),
            etaMinutes = etaMinutes.coerceAtLeast(15),
            warrantyDays = warrantyDays.coerceAtLeast(0),
            message = message,
            status = "PENDING",
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertPartsStore(store)
                marketplaceDao.upsertPartOfferRespectingRequestClaim(offer)
                if (ownerId != null) {
                    runCatching { SupabaseManager.client.postgrest["parts_stores"].upsert(store.toRemote(ownerId)) }
                        .onFailure { Log.w("ObdViewModel", "Parts store saved locally; cloud table unavailable", it) }
                    runCatching {
                        SupabaseManager.client.postgrest["part_offers"].insert(
                            offer.toRemote(ownerId, compatibilityConfidence, compatibilityNotes)
                        )
                    }
                        .onFailure { Log.w("ObdViewModel", "Part offer saved locally; cloud table unavailable", it) }
                } else {
                    Log.i("ObdViewModel", "Part offer saved locally; sign in as a parts store to publish it.")
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to place part offer", e)
            }
        }
    }

    /** Idempotent: only accepts if part request is still OPEN */
    fun acceptPartOffer(partRequestId: String, offerId: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accepted = marketplaceDao.acceptPartOfferAtomically(partRequestId, offerId)
                if (!accepted) {
                    withContext(Dispatchers.Main) {
                        context?.let {
                            android.widget.Toast.makeText(it, "⚠️ Este pedido de repuesto ya fue tomado por otro proveedor", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }
                scheduleVanguardCommerceSync()

                val requestSync = runCatching {
                    SupabaseManager.client.postgrest["part_requests"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "statusV2" to PartsMarketplaceContract.requestStatusToV2("ACCEPTED"),
                            "acceptedOfferId" to offerId
                        )
                    ) { filter { eq("requestId", partRequestId) } }
                }
                if (requestSync.isFailure) {
                    runCatching {
                        SupabaseManager.client.postgrest["part_requests"].update(
                            mapOf("status" to "ACCEPTED", "acceptedOfferId" to offerId)
                        ) { filter { eq("requestId", partRequestId) } }
                    }
                }

                val offerSync = runCatching {
                    SupabaseManager.client.postgrest["part_offers"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "statusV2" to PartsMarketplaceContract.quoteStatusToV2("ACCEPTED")
                        )
                    ) { filter { eq("offerId", offerId) } }
                }
                if (offerSync.isFailure) {
                    runCatching {
                        SupabaseManager.client.postgrest["part_offers"].update(
                            mapOf("status" to "ACCEPTED")
                        ) { filter { eq("offerId", offerId) } }
                    }
                }

                requestSync.exceptionOrNull()?.let {
                    Log.d("ObdViewModel", "Part request accepted with legacy cloud fallback", it)
                }
                offerSync.exceptionOrNull()?.let {
                    Log.d("ObdViewModel", "Part offer accepted with legacy cloud fallback", it)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to accept part offer", e)
            }
        }
    }

    /** Confirms receipt and delivery of the ordered part */
    fun confirmPartReceipt(partRequestId: String, offerId: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.updatePartRequestStatus(partRequestId, "COMPLETED", offerId)
                scheduleVanguardCommerceSync()

                runCatching {
                    SupabaseManager.client.postgrest["part_requests"].update(
                        mapOf(
                            "status" to "COMPLETED",
                            "statusV2" to "DELIVERED"
                        )
                    ) { filter { eq("requestId", partRequestId) } }
                }
                withContext(Dispatchers.Main) {
                    context?.let {
                        android.widget.Toast.makeText(it, "🎉 ¡Repuesto recibido y confirmado!", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to confirm part receipt", e)
            }
        }
    }

    private var serviceMarketplaceCloudPollingEnabled = true

    // Background periodic task to poll/sync local Room data with Supabase for Marketplace
    fun startMarketplaceSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (serviceMarketplaceCloudPollingEnabled) {
                    runCatching { syncServiceMarketplaceFromCloud() }
                        .onFailure { error ->
                            if (
                                MarketplaceCloudSyncPolicy.isMissingLegacyServiceSchema(
                                    error.message,
                                )
                            ) {
                                serviceMarketplaceCloudPollingEnabled = false
                                Log.w(
                                    "ObdViewModel",
                                    "Legacy service marketplace cloud poll disabled; local records remain available",
                                )
                            } else {
                                Log.w(
                                    "ObdViewModel",
                                    "Service marketplace cloud sync unavailable",
                                    error,
                                )
                            }
                        }
                }
                runCatching { syncPartsMarketplaceFromCloud() }
                    .onFailure { Log.w("ObdViewModel", "Parts marketplace cloud sync unavailable", it) }
                delay(10000L) // Poll every 10 seconds
            }
        }
    }

    private suspend fun syncServiceMarketplaceFromCloud() {
        val cloudRequests = SupabaseManager.client.postgrest["service_requests"]
            .select().decodeList<ServiceRequestEntity>()
        cloudRequests.forEach { req ->
            marketplaceDao.upsertServiceRequestFromSync(req)
        }

        cloudRequests.forEach { req ->
            val cloudBids = SupabaseManager.client.postgrest["service_bids"]
                .select {
                    filter {
                        eq("requestId", req.requestId)
                    }
                }.decodeList<ServiceBidEntity>()
            cloudBids.forEach { bid ->
                marketplaceDao.upsertBidRespectingRequestClaim(bid)
            }
        }
    }

    private suspend fun syncPartsMarketplaceFromCloud() {
        currentCloudUserId() ?: return

        val stores = SupabaseManager.client.postgrest["parts_stores"]
            .select(
                columns = Columns.list(
                    "storeId",
                    "storeName",
                    "rating",
                    "phone",
                    "location",
                    "deliveryRadiusKm",
                    "averageEtaMinutes",
                    "verified",
                    "createdAt",
                    "owner_id"
                )
            )
            .decodeList<RemotePartsStore>()
        marketplaceDao.insertPartsStores(stores.map { it.toLocal() })

        val requests = SupabaseManager.client.postgrest["part_requests"]
            .select()
            .decodeList<RemotePartRequest>()
        requests.forEach { marketplaceDao.upsertPartRequestFromSync(it.toLocal()) }

        val offers = SupabaseManager.client.postgrest["part_offers"]
            .select()
            .decodeList<RemotePartOffer>()
        offers.forEach { marketplaceDao.upsertPartOfferRespectingRequestClaim(it.toLocal()) }
    }

    // ── Black Box ──
    val evidencePackages: StateFlow<List<EvidencePackageEntity>> = blackBoxDao.getEvidencePackages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveEvidencePackage(evidence: EvidencePackageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                blackBoxDao.insertEvidencePackage(evidence)
                SupabaseManager.client.postgrest["evidence_packages"].insert(evidence)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to save evidence package", e)
            }
        }
    }

    // ── Vehicle Twin ──
    @OptIn(ExperimentalCoroutinesApi::class)
    val twinAnomalies: StateFlow<List<TwinAnomalyEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { vehicleTwinDao.getAnomaliesForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Tarea 34 & 35: Configuración persistente de sistema de unidades, precio de combustible, moneda y tipo de carburante
    private val _useImperialUnits = MutableStateFlow(false)
    val useImperialUnits: StateFlow<Boolean> = _useImperialUnits.asStateFlow()

    private val _fuelPrice = MutableStateFlow(1.25f)
    val fuelPrice: StateFlow<Float> = _fuelPrice.asStateFlow()

    private val _currencySymbol = MutableStateFlow("$")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _fuelType = MutableStateFlow("GASOLINE")
    val fuelType: StateFlow<String> = _fuelType.asStateFlow()

    fun setUseImperialUnits(useImperial: Boolean) {
        _useImperialUnits.value = useImperial
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("pref_imperial_units", useImperial).apply()
    }

    fun setFuelPrice(price: Float) {
        _fuelPrice.value = price
        fuelEconomyTracker.setFuelPrice(price)
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("pref_fuel_price", price).apply()
    }

    fun setCurrencySymbol(symbol: String) {
        _currencySymbol.value = symbol
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("pref_currency_symbol", symbol).apply()
    }

    fun setFuelType(type: String) {
        _fuelType.value = type
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("pref_fuel_type", type).apply()
    }

    // Tarea 39: Depuración automática de trayectos viejos (>90 días)
    fun purgeOldTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L)
                tripDao.deleteTripsOlderThan(ninetyDaysAgo)
                android.util.Log.d("ObdViewModel", "Purged trips older than 90 days successfully.")
            } catch (e: Exception) {
                android.util.Log.e("ObdViewModel", "Error purging old trips: ${e.message}", e)
            }
        }
    }

    fun selectVehicle(
        vehicle: Vehicle?,
        reason: ActiveVehicleChangeReason = ActiveVehicleChangeReason.USER_SELECTED,
    ) {
        if (vehicle == null) {
            Log.w("ObdVM", "Ignored unaudited attempt to clear active vehicle")
            return
        }
        activeVehicleKernel.select(vehicle, reason)
        obdSession.setVehicleCapabilityContext(
            manufacturer = vehicle?.make,
            modelFamily = vehicle?.model,
            year = vehicle?.year,
        )
        if (_latestDiagnosticScan.value?.belongsTo(vehicle?.id) != true) {
            _latestDiagnosticScan.value = null
        }
        reconcileSelectedVehicleBinding(vehicle)
        // Reset sensor smoothers when switching vehicles to prevent cross-vehicle data contamination
        sensorSmoother.resetAll()
        predictiveEstimator.reset()
        evaluateDnaInference()
    }

    private fun reconcileSelectedVehicleBinding(vehicle: Vehicle?) {
        val binding = _vehicleSessionBinding.value
        val observedVin = binding.observedVin ?: return
        if (vehicle == null) {
            _vehicleSessionBinding.value = VehicleSessionBinding.unbound(
                diagnosticSessionId = binding.diagnosticSessionId,
                physicalConnectionId = binding.physicalConnectionId,
            )
            return
        }
        val expectedVin = normalizeVin(vehicle.vin)
        _vehicleSessionBinding.value = if (expectedVin == observedVin) {
            binding.bindVerifiedVin(
                vehicleId = vehicle.id,
                observedVin = observedVin,
                expectedVin = expectedVin,
                evidenceId = "ecu-vin:${sha256Hex(observedVin.toByteArray()).take(16)}",
            )
        } else {
            binding.conflict(
                observedVin = observedVin,
                expectedVin = expectedVin,
                reason = "El VIN leído de la ECU no coincide con el vehículo seleccionado.",
            )
        }
    }

    fun confirmConnectedVehicle(vehicle: Vehicle) {
        val binding = _vehicleSessionBinding.value
        check(obdSession.state.value == ObdState.CONNECTED) {
            "No hay una conexión física que pueda vincularse"
        }
        check(binding.bindingState == VehicleSessionBindingState.UNBOUND && binding.observedVin == null) {
            "La confirmación manual solo está permitida cuando la ECU no entregó VIN y no existe conflicto"
        }
        activeVehicleKernel.select(vehicle, ActiveVehicleChangeReason.VERIFIED_ECU_BINDING)
        obdSession.setVehicleCapabilityContext(vehicle.make, vehicle.model, vehicle.year)
        _vehicleSessionBinding.value = binding.bindUserConfirmed(
            vehicleId = vehicle.id,
            expectedVin = vehicle.vin,
            evidenceId = "user-confirmation:${UUID.randomUUID()}",
        )
        // The startup pre-scan was intentionally ephemeral while UNBOUND.
        // Force the destructive flow to acquire and persist a new scan under
        // this explicit binding before it can build a clear plan.
        hasCompletedInitialDtcScan = false
        _latestDiagnosticScan.value = null
        addTerminalLog(
            "[IDENTIDAD] Vehículo confirmado manualmente para esta sesión física. Se exigirá un nuevo pre-scan antes de borrar memoria DTC.",
            TerminalLineType.SYSTEM,
        )
    }

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    // Smooth sensor interpolation — eliminates erratic jumps from raw ELM327 readings
    private val sensorSmoother = SensorSmootherManager()
    private val predictiveEstimator = PredictiveTelemetryEstimator()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private val _vehicleSessionBinding = MutableStateFlow(
        VehicleSessionBinding.unbound(currentSessionId, "NOT_CONNECTED")
    )
    val vehicleSessionBinding: StateFlow<VehicleSessionBinding> = _vehicleSessionBinding.asStateFlow()
    private val _latestDiagnosticScan = MutableStateFlow<LatestDiagnosticScanProjection?>(null)
    val latestDiagnosticScan: StateFlow<LatestDiagnosticScanProjection?> = _latestDiagnosticScan.asStateFlow()
    private val initialDtcScanMutex = Mutex()
    private val vinVehicleRecognitionMutex = Mutex()
    private val diagnosticEvidencePersistenceMutex = Mutex()
    private var lastAutoRecognizedVin: String? = null
    @Volatile private var hasCompletedInitialDtcScan = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val canonicalOpenFindings = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let { diagnosticFindingRepository.observeOpenFindings(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val canonicalResolvedFindings = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let { diagnosticFindingRepository.observeResolvedFindings(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canonicalFindingSummaries = canonicalOpenFindings
        .map { findings -> findings.map { it.toSummary() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val canonicalActiveFindingSummaries = canonicalFindingSummaries
        .map { findings -> findings.filter { it.status == "ACTIVE" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val canonicalPendingFindingSummaries = canonicalFindingSummaries
        .map { findings -> findings.filter { it.status == "PENDING" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val canonicalPermanentFindingSummaries = canonicalFindingSummaries
        .map { findings -> findings.filter { it.status == "PERMANENT" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val canonicalHistoricalFindingSummaries = canonicalFindingSummaries
        .map { findings -> findings.filter { it.status == "HISTORY" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun latestCodesForSelectedVehicle(bucket: DtcBucket): Flow<List<String>> =
        combine(_selectedVehicle, _latestDiagnosticScan) { vehicle, projection ->
            if (projection?.belongsTo(vehicle?.id) == true) projection.codesFor(bucket) else emptyList()
        }

    // Canonical finding timeline is authoritative. Latest scan data bridges the
    // short interval before Room commits or when no vehicle has been selected.
    val activeDtcs: StateFlow<List<String>> = combine(canonicalOpenFindings, latestCodesForSelectedVehicle(DtcBucket.ACTIVE)) { findings, latest ->
        val canonical = findings.filter { finding ->
            val semantics = finding.projection.latestObservation?.semantics.orEmpty()
            finding.projection.latestObservation?.observationState == "OBSERVED" &&
                ("SAE_ACTIVE_DTC" in semantics || "UDS_TEST_FAILED" in semantics)
        }.map { it.identity.displayCode }
        (canonical + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pendingDtcs: StateFlow<List<String>> = combine(canonicalOpenFindings, latestCodesForSelectedVehicle(DtcBucket.PENDING)) { findings, latest ->
        val canonical = findings.filter { finding ->
            val semantics = finding.projection.latestObservation?.semantics.orEmpty()
            finding.projection.latestObservation?.observationState == "OBSERVED" &&
                ("SAE_PENDING_DTC" in semantics || "UDS_PENDING" in semantics)
        }.map { it.identity.displayCode }
        (canonical + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val permanentDtcs: StateFlow<List<String>> = combine(canonicalOpenFindings, latestCodesForSelectedVehicle(DtcBucket.PERMANENT)) { findings, latest ->
        val canonical = findings.filter { finding ->
            val semantics = finding.projection.latestObservation?.semantics.orEmpty()
            finding.projection.latestObservation?.observationState == "OBSERVED" &&
                ("SAE_PERMANENT_DTC" in semantics ||
                    ("UDS_CONFIRMED" in semantics && "UDS_FAILED_SINCE_CLEAR" in semantics))
        }.map { it.identity.displayCode }
        (canonical + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val historicalDtcs: StateFlow<List<String>> = combine(canonicalOpenFindings, latestCodesForSelectedVehicle(DtcBucket.HISTORY)) { findings, latest ->
        val canonical = findings.filter { finding ->
            finding.timeline.any { "HISTORY" in it.semantics || "INTERMITTENT" in it.semantics }
        }.map { it.identity.displayCode }
        (canonical + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastDtcScanReport: StateFlow<DtcScanReport?> = obdSession.lastDtcScanReport
    val diagnosticScanEvents: SharedFlow<DiagnosticScanEvent> = obdSession.diagnosticScanEvents

    private val _readinessMonitors = MutableStateFlow<ReadinessResult?>(null)
    val readinessMonitors: StateFlow<ReadinessResult?> = _readinessMonitors.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _freezeFrameData = MutableStateFlow<Map<String, String>>(emptyMap())
    val freezeFrameData: StateFlow<Map<String, String>> = _freezeFrameData.asStateFlow()
    private val _freezeFrameStatus = MutableStateFlow("")
    val freezeFrameStatus: StateFlow<String> = _freezeFrameStatus.asStateFlow()

    private val _clearDtcResult = MutableStateFlow<String?>(null)
    val clearDtcResult: StateFlow<String?> = _clearDtcResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isDeletingVehicle = MutableStateFlow(false)
    val isDeletingVehicle: StateFlow<Boolean> = _isDeletingVehicle.asStateFlow()

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    private val _dtcDefinitions = MutableStateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>>(emptyMap())
    val dtcDefinitions: StateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>> = _dtcDefinitions.asStateFlow()

    private val _manualSearchResults = MutableStateFlow<List<com.elysium369.meet.data.local.entities.DtcDefinitionEntity>>(emptyList())
    val manualSearchResults: StateFlow<List<com.elysium369.meet.data.local.entities.DtcDefinitionEntity>> = _manualSearchResults.asStateFlow()

    private val _manufacturer = MutableStateFlow<String>("GENERIC")
    val manufacturer: StateFlow<String> = _manufacturer.asStateFlow()

    private val _aiDtcExplanations = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiDtcExplanations: StateFlow<Map<String, String>> = _aiDtcExplanations.asStateFlow()

    fun fetchAiExplanationForDtc(dtc: String, fallbackDescription: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_aiDtcExplanations.value.containsKey(dtc)) return@launch

            val vehicle = _selectedVehicle.value
            val vInfo = if (vehicle != null) "${vehicle.year} ${vehicle.make} ${vehicle.model}" else "Vehículo Genérico"

            try {
                // Update AI status to loading
                _aiDtcExplanations.update { it + (dtc to "CARGANDO...") }

                val result = geminiDiagnostic.analyzeDtc(
                    dtcList = listOf(dtc),
                    vehicleInfo = vInfo,
                    liveData = emptyMap()
                )

                _aiDtcExplanations.update { it + (dtc to result.analysisText) }
            } catch (e: Exception) {
                _aiDtcExplanations.update { it + (dtc to "ERROR: No se pudo conectar con la IA. Use el manual offline.") }
            }
        }
    }

    private val _isPremium = MutableStateFlow(MonetizationPolicy.LOCAL_FULL_ACCESS)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _currentOdometer = MutableStateFlow(0f)
    val currentOdometer: StateFlow<Float> = _currentOdometer.asStateFlow()

    private val _cloudSyncState = MutableStateFlow("")
    val cloudSyncState: StateFlow<String> = _cloudSyncState.asStateFlow()

    private val _language = MutableStateFlow("es") // "es" or "en"
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) {
        _language.value = lang
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", lang).apply()
    }

    private val _qosMetrics = MutableStateFlow(QosMetrics())
    val qosMetrics: StateFlow<QosMetrics> = _qosMetrics.asStateFlow()

    // Telemetry History for Graphs
    private val _telemetryHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val telemetryHistory: StateFlow<Map<String, List<Float>>> = _telemetryHistory.asStateFlow()

    // Oscilloscope Buffer (High-Frequency with Timestamps)
    private val _oscilloscopeBuffer = MutableStateFlow<Map<String, List<Pair<Long, Float>>>>(emptyMap())
    val oscilloscopeBuffer: StateFlow<Map<String, List<Pair<Long, Float>>>> = _oscilloscopeBuffer.asStateFlow()

    val highSpeedMode: StateFlow<Boolean> = obdSession.highSpeedMode
    val pinnedPids: StateFlow<Set<String>> = obdSession.pinnedPids

    val activeTestStatus: StateFlow<ActiveTestStatus> = obdSession.activeTestStatus
    val availableActiveTests: List<ActiveTest> = PidRegistry.ACTIVE_TESTS

    // ═══════════════════════════════════════
    //  PERFORMANCE CALCULATOR
    // ═══════════════════════════════════════
    private val _performanceSnapshot = MutableStateFlow<PerformanceCalculator.PerformanceSnapshot?>(null)
    val performanceSnapshot: StateFlow<PerformanceCalculator.PerformanceSnapshot?> = _performanceSnapshot.asStateFlow()

    private val _dragStripResult = MutableStateFlow<PerformanceCalculator.DragStripResult?>(null)
    val dragStripResult: StateFlow<PerformanceCalculator.DragStripResult?> = _dragStripResult.asStateFlow()

    fun updatePerformance(data: Map<String, Float>) {
        _performanceSnapshot.value = performanceCalculator.calculate(data)
        if (performanceCalculator.isDragActive) {
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val hp = _performanceSnapshot.value?.horsepowerMAF
            val torque = _performanceSnapshot.value?.torqueNm
            _dragStripResult.value = performanceCalculator.updateDragRun(speed, hp, torque)
        }
        if (_isDynoRunning.value) {
            val rpm = data["RPM"] ?: data["rpm"] ?: 0f
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val accel = if (lastLinearAccelY > 0.05f) {
                lastLinearAccelY
            } else {
                _performanceSnapshot.value?.acceleration ?: 0f
            }
            _dynoPoints.value = performanceCalculator.updateDynoRun(rpm, accel, speed)
        }
        if (_isRecordingDashcam.value) {
            val elapsed = System.currentTimeMillis() - dashcamStartTimeMs
            val rpm = data["RPM"] ?: data["rpm"] ?: 0f
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val load = data["LOAD"] ?: data["load"] ?: 0f
            val throttle = data["THROTTLE"] ?: data["throttle"] ?: data["0111"] ?: 0f
            val gForce = _performanceSnapshot.value?.gForce ?: 0f

            dashcamTelemetryBuffer.add(
                DashcamTelemetryFrame(
                    timestampMs = elapsed,
                    rpm = rpm,
                    speedKph = speed,
                    gForce = gForce,
                    throttle = throttle,
                    load = load
                )
            )
        }
    }

    fun startDragStrip() {
        performanceCalculator.startDragRun()
        _dragStripResult.value = PerformanceCalculator.DragStripResult(null, null, null, null, null, null, null, true)
    }

    fun stopDragStrip() {
        _dragStripResult.value = performanceCalculator.stopDragRun()
    }

    // ═══════════════════════════════════════
    //  DATA LOGGER
    // ═══════════════════════════════════════
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _lastLogSession = MutableStateFlow<DataLogger.LogSession?>(null)
    val lastLogSession: StateFlow<DataLogger.LogSession?> = _lastLogSession.asStateFlow()

    fun toggleRecording() {
        if (dataLogger.recording) {
            _lastLogSession.value = dataLogger.stopRecording()
            _isRecording.value = false
            voiceFeedbackManager.speak("Grabación detenida.", "Recording stopped.")
        } else {
            val started = dataLogger.startRecording(context, _vin.value)
            _isRecording.value = started
            if (started) {
                voiceFeedbackManager.speak("Grabación iniciada.", "Recording started.")
            }
        }
    }

    fun recordDataSample(data: Map<String, Float>) {
        if (dataLogger.recording) dataLogger.recordSample(data)
    }

    // ═══════════════════════════════════════
    //  REAL-TIME ALERTS
    // ═══════════════════════════════════════
    private val _thresholdAlerts = MutableStateFlow<List<AlertThresholdEngine.ThresholdAlert>>(emptyList())
    val thresholdAlerts: StateFlow<List<AlertThresholdEngine.ThresholdAlert>> = _thresholdAlerts.asStateFlow()

    fun evaluateAlerts(data: Map<String, Float>) {
        val newAlerts = alertThresholdEngine.evaluate(data)
        if (newAlerts.isNotEmpty()) {
            _thresholdAlerts.value = newAlerts
        }
        // Run Elysium Vanguard Copilot RuleEngine check
        ruleEngine.evaluate(data, canonicalActiveFindingSummaries.value.map { it.code })
    }

    fun getAlertHistory() = alertThresholdEngine.getAlertHistory()
    fun clearAlertHistory() { alertThresholdEngine.clearHistory(); _thresholdAlerts.value = emptyList() }

    // ═══════════════════════════════════════
    //  PRE-PURCHASE INSPECTION
    // ═══════════════════════════════════════
    private val _inspectionResult = MutableStateFlow<PrePurchaseInspection.InspectionResult?>(null)
    val inspectionResult: StateFlow<PrePurchaseInspection.InspectionResult?> = _inspectionResult.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _prePurchaseReportFile = MutableStateFlow<java.io.File?>(null)
    val prePurchaseReportFile: StateFlow<java.io.File?> = _prePurchaseReportFile.asStateFlow()

    fun runPrePurchaseInspection() {
        viewModelScope.launch(Dispatchers.IO) {
            if (connectionState.value != ObdState.CONNECTED) {
                _inspectionResult.value = null
                addTerminalLog("[REAL] Inspección pre-compra cancelada: requiere enlace OBD-II físico activo.", TerminalLineType.WARNING)
                return@launch
            }
            _isInspecting.value = true
            voiceFeedbackManager.speak("Iniciando inspección de pre-compra del vehículo.", "Starting vehicle pre-purchase inspection.")
            try {
                val readiness = _readinessMonitors.value?.monitors?.associate {
                    it.name to it.complete
                } ?: emptyMap()
                val result = prePurchaseInspection.runInspection(
                    activeDtcs = activeDtcs.value,
                    pendingDtcs = pendingDtcs.value,
                    permanentDtcs = permanentDtcs.value,
                    readinessMonitors = readiness,
                    liveData = _liveData.value,
                    freezeFrame = _freezeFrameData.value.ifEmpty { null },
                    mode06Results = null
                )
                _inspectionResult.value = result
                voiceFeedbackManager.speak(
                    "Inspección completada. Puntuación del vehículo: ${result.overallScore} de 100.",
                    "Inspection complete. Vehicle score: ${result.overallScore} out of 100."
                )
            } catch (e: Exception) {
                Log.e("Elysium Vanguard AI OS", "Pre-purchase inspection failed", e)
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun generatePrePurchasePdf(result: PrePurchaseInspection.InspectionResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vinStr = _vin.value ?: "DESCONOCIDO"
                val manufacturerStr = _manufacturer.value ?: "GENERIC"
                val file = reportGenerator.generatePrePurchaseReport(
                    result = result,
                    vin = vinStr,
                    manufacturer = manufacturerStr,
                    themeName = "ELYSIUM_CYAN"
                )
                _prePurchaseReportFile.value = file
                reportGenerator.shareReport(file)
            } catch (e: Exception) {
                Log.e("Elysium Vanguard AI OS", "Failed to generate pre-purchase PDF", e)
            }
        }
    }

    // ──── Elysium Vanguard PERITO STATES ────
    private val _isInspectingPerito = MutableStateFlow(false)
    val isInspectingPerito: StateFlow<Boolean> = _isInspectingPerito.asStateFlow()

    private val _peritoConsoleLogs = MutableStateFlow<List<String>>(emptyList())
    val peritoConsoleLogs: StateFlow<List<String>> = _peritoConsoleLogs.asStateFlow()

    private val _activePeritoReport = MutableStateFlow<com.elysium369.meet.core.obd.VehicleInspectionReport?>(null)
    val activePeritoReport: StateFlow<com.elysium369.meet.core.obd.VehicleInspectionReport?> = _activePeritoReport.asStateFlow()

    private val _peritoHistory = MutableStateFlow<List<com.elysium369.meet.core.obd.VehicleInspectionReport>>(emptyList())
    val peritoHistory: StateFlow<List<com.elysium369.meet.core.obd.VehicleInspectionReport>> = _peritoHistory.asStateFlow()

    private val _currentPeritoStep = MutableStateFlow(0)
    val currentPeritoStep: StateFlow<Int> = _currentPeritoStep.asStateFlow()

    private val _peritoReportFile = MutableStateFlow<java.io.File?>(null)
    val peritoReportFile: StateFlow<java.io.File?> = _peritoReportFile.asStateFlow()

    private val meetPerito = com.elysium369.meet.core.obd.MeetPerito()
    private val peritoReportGenerator = com.elysium369.meet.core.export.PeritoReportGenerator(context)

    // --- Elysium Vanguard DNA ---
    private val _dnaResult = MutableStateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult>(com.elysium369.meet.core.dna.DnaEvaluationResult(isCalibrated = false))
    val dnaResult: StateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult> = _dnaResult.asStateFlow()

    private val _isTrainingDna = MutableStateFlow(false)
    val isTrainingDna: StateFlow<Boolean> = _isTrainingDna.asStateFlow()

    val connectionTruth: StateFlow<ConnectionTruthSnapshot> = obdSession.connectionTruth
    private var connectionJob: Job? = null
    private var lastAdapterAddress: String? = null
    private var dnaEvaluationJob: Job? = null
    private var lastDnaEvaluationAtMs: Long = 0L
    private val dnaEvaluationIntervalMs = 5_000L

    fun trainVehicleDna() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            _isTrainingDna.value = true
            try {
                val progress = meetDnaEngine.getTrainingProgress(vehicle.id)
                if (!progress.isReady) {
                    val result = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
                    _dnaResult.value = result.copy(
                        message = "Faltan ${progress.missingSamples} lecturas alineadas para calibrar una firma real. No se inicia entrenamiento hasta cumplir el minimo.",
                        nextAction = "Conduzca normal y mantenga el scanner conectado hasta llegar a ${progress.requiredSamples} muestras."
                    )
                    return@launch
                }
                val trained = meetDnaEngine.trainDnaProfile(vehicle.id, quickMode = progress.isQuickMode)
                val result = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
                _dnaResult.value =
                    if (trained == null) {
                        val missing = (progress.requiredSamples - progress.currentSamples).coerceAtLeast(0)
                        result.copy(
                            message = "Aun faltan $missing lecturas alineadas para entrenar la firma Elysium Vanguard AI OS DNA. Mientras tanto se muestra telemetria provisional."
                        )
                    } else {
                        result
                    }
                vehicleTwinEngine.evaluateFrame(vehicle.id, _liveData.value)
            } catch (e: Exception) {
                _dnaResult.value = com.elysium369.meet.core.dna.DnaEvaluationResult(
                    isCalibrated = false,
                    message = "Elysium Vanguard AI OS DNA no pudo calibrar: ${e.message ?: "error interno"}"
                )
            } finally {
                _isTrainingDna.value = false
            }
        }
    }

    fun evaluateDnaInference(force: Boolean = false) {
        val vehicle = _selectedVehicle.value ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastDnaEvaluationAtMs < dnaEvaluationIntervalMs) return
        if (dnaEvaluationJob?.isActive == true) return
        lastDnaEvaluationAtMs = now
        dnaEvaluationJob = viewModelScope.launch {
            val res = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
            _dnaResult.value = res
            vehicleTwinEngine.evaluateFrame(vehicle.id, _liveData.value)
        }
    }

    fun loadPeritoHistory() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val history = meetPerito.getInspectionHistory(context, vehicle.id)
            _peritoHistory.value = history
        }
    }

    fun runMeetPeritoInspection() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            if (obdSession.state.value != ObdState.CONNECTED) {
                _vinReadFeedback.value = "⚠️ El peritaje requiere una sesión OBD física conectada."
                return@launch
            }
            _isInspectingPerito.value = true
            _activePeritoReport.value = null
            _peritoConsoleLogs.value = emptyList()
            _currentPeritoStep.value = 0
            _peritoReportFile.value = null

            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add(msg)
                _peritoConsoleLogs.value = ArrayList(logs)
            }

            try {
            voiceFeedbackManager.speak("Iniciando peritaje clínico Elysium Vanguard AI OS Perito.", "Starting Elysium Vanguard AI OS Perito clinical vehicle check.")

            // Step 1: VIN
            _currentPeritoStep.value = 1
            addLog("📡 Estableciendo comunicación con ECU mediante protocolo OBD2...")
            delay(1000)
            addLog("🔍 Solicitando VIN del vehículo (PID 09 02)...")
            val vinProof = obdSession.readVinFromVehicle()
            val vinVal = vinProof.vin
            addLog(vinVal?.let { "ℹ️ VIN verificado físicamente: $it (${vinProof.command})" }
                ?: "⚠️ VIN no verificado: ${vinProof.outcome}")

            // Step 2: DTC Activos
            _currentPeritoStep.value = 2
            addLog("⚠️ Solicitando códigos de falla activos (Mode 03)...")
            val diagnosticReport = obdSession.readProfessionalDtcScan()
            val dtcs = diagnosticReport.codesForBucket(DtcBucket.ACTIVE)
            addLog("ℹ️ DTCs activos encontrados: ${dtcs.size}")

            // Step 3: DTC Pendientes
            _currentPeritoStep.value = 3
            addLog("⏳ Solicitando códigos de falla pendientes (Mode 07)...")
            val pDtcs = diagnosticReport.codesForBucket(DtcBucket.PENDING)
            addLog("ℹ️ DTCs pendientes encontrados: ${pDtcs.size}")

            // Step 4: Freeze Frame
            _currentPeritoStep.value = 4
            addLog("📦 Solicitando captura de pantalla de falla congelada (Freeze Frame Mode 02)...")
            val freezeFrameDtc = (dtcs + pDtcs).firstOrNull()
            val freezeFrameResult = freezeFrameDtc?.let { obdSession.readFreezeFrame(it) }
            val ff = freezeFrameResult?.values.orEmpty()
            val freezeFrameReadComplete = freezeFrameResult?.outcome == FreezeFrameOutcome.MATCHED ||
                (freezeFrameDtc == null && diagnosticReport.completeness == ScanCompleteness.COMPLETE)
            addLog("ℹ️ Freeze Frame: ${freezeFrameResult?.outcome ?: "NO_APLICA_SIN_DTC_EN_SCAN_COMPLETO"}")

            // Step 5: Fuel Trims
            _currentPeritoStep.value = 5
            addLog("⛽ Analizando sensores de ajuste de mezcla (Long Term Fuel Trims)...")
            delay(1000)
            val trim = _liveData.value["LTFT_B1"] ?: _liveData.value["ltft_b1"]
            addLog(trim?.let { "ℹ️ LTFT Banco 1: ${String.format("%.1f", it)}%" }
                ?: "⚠️ LTFT no disponible; no se asumirá 0%.")

            // Step 6: Coolant Temperature
            _currentPeritoStep.value = 6
            addLog("🌡️ Leyendo sensor de temperatura del refrigerante (ECT PID 01 05)...")
            delay(1000)
            val ect = _liveData.value["COOLANT"] ?: _liveData.value["coolant"]
            addLog(ect?.let { "ℹ️ ECT: ${it.toInt()}°C" } ?: "⚠️ ECT no disponible; no se asumirá una temperatura normal.")

            // Step 7: Alternator/Battery Voltage
            _currentPeritoStep.value = 7
            addLog("🔋 Midiendo voltaje del alternador y regulación eléctrica...")
            delay(1000)
            val volt = _liveData.value["VOLTAGE"] ?: _liveData.value["voltage"]
            addLog(volt?.let { "ℹ️ Voltaje del sistema: ${String.format("%.1f", it)}V" } ?: "⚠️ Voltaje no disponible; no se asumirá 13.8 V.")

            // Step 8: Critical admission sensors
            _currentPeritoStep.value = 8
            addLog("🌬️ Admisión de aire: verificando sensores MAF / MAP...")
            delay(1200)
            val mafVal = _liveData.value["MAF"] ?: _liveData.value["maf"]
            val mapVal = _liveData.value["MAP"] ?: _liveData.value["map"]
            addLog("ℹ️ MAF: ${mafVal ?: "N/A"} g/s, MAP: ${mapVal ?: "N/A"} kPa")

            // Step 9: Odometer Comparison
            _currentPeritoStep.value = 9
            addLog("🚗 Solicitando kilometraje almacenado en ECU (odómetro OBD)...")
            delay(1000)
            val obdOdo = obdSession.readOdometer()
            addLog("ℹ️ Kilometraje tablero: ${currentOdometer.value.toInt()} km | Kilometraje ECU: ${obdOdo?.let { "${it.toInt()} km" } ?: "No disponible"}")

            // Step 10: General State / Readiness
            _currentPeritoStep.value = 10
            addLog("🔬 Verificando estado general de monitores de emisiones (Readiness)...")
            delay(1000)
            val readinessResult = obdSession.readReadinessMonitors()
            _readinessMonitors.value = readinessResult
            val readiness = readinessResult?.monitors?.associate { it.name to it.complete } ?: emptyMap()
            addLog("ℹ️ Monitores listos: ${readiness.count { it.value }}/${readiness.size}")
            delay(800)

            addLog("⚡ Compilando diagnóstico y generando reporte clínico Elysium Vanguard AI OS Perito...")
            delay(1500)

            val inspectionLiveData = _liveData.value.toMutableMap().apply {
                obdOdo?.let { put("DISTANCE_WITH_MIL", it) }
            }
            val report = meetPerito.performInspection(
                context = context,
                vehicleId = vehicle.id,
                vin = vinVal,
                activeDtcs = dtcs,
                pendingDtcs = pDtcs,
                freezeFrame = ff.ifEmpty { null },
                liveData = inspectionLiveData,
                odometerKmCluster = currentOdometer.value.toLong(),
                readinessMonitors = readiness,
                dtcScanComplete = diagnosticReport.completeness == ScanCompleteness.COMPLETE,
                freezeFrameReadComplete = freezeFrameReadComplete,
            )

            _activePeritoReport.value = report
            voiceFeedbackManager.speak(
                "Evaluación finalizada. Puntuación: ${report.score0to100} de 100. Categoría: ${report.category}.",
                "Evaluation complete. Score: ${report.score0to100} out of 100. Category: ${report.category}."
            )
            loadPeritoHistory()
            } catch (e: Exception) {
                addLog("✗ Peritaje interrumpido sin conclusión: ${e.message ?: e::class.simpleName}")
                android.util.Log.e("ObdVM", "Physical Perito inspection failed", e)
            } finally {
                _isInspectingPerito.value = false
            }
        }
    }

    fun selectPeritoReport(report: com.elysium369.meet.core.obd.VehicleInspectionReport) {
        _activePeritoReport.value = report
    }

    fun generatePeritoPdf() {
        val report = _activePeritoReport.value ?: return
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = peritoReportGenerator.generateReportPdf(
                    report = report,
                    make = vehicle.make,
                    model = vehicle.model,
                    year = vehicle.year,
                    odometer = currentOdometer.value.toLong()
                )
                _peritoReportFile.value = file
                reportGenerator.shareReport(file)
            } catch (e: Exception) {
                Log.e("Elysium Vanguard AI OS", "Failed to generate Perito PDF report", e)
            }
        }
    }


    // ═══════════════════════════════════════
    //  VIN DECODER
    // ═══════════════════════════════════════
    private val _vinDecoded = MutableStateFlow<VinDecoder.VinInfo?>(null)
    val vinDecoded: StateFlow<VinDecoder.VinInfo?> = _vinDecoded.asStateFlow()

    fun decodeVin() {
        val vinStr = _vin.value ?: return
        _vinDecoded.value = VinDecoder.decode(vinStr)
    }

    private suspend fun autoSelectVehicleFromVin(rawVin: String): Vehicle? {
        val cleanVin = normalizeVin(rawVin) ?: return null
        if (_selectedVehicle.value?.vin?.equals(cleanVin, ignoreCase = true) == true) {
            lastAutoRecognizedVin = cleanVin
            return _selectedVehicle.value
        }
        return vinVehicleRecognitionMutex.withLock {
            if (_selectedVehicle.value?.vin?.equals(cleanVin, ignoreCase = true) == true) {
                lastAutoRecognizedVin = cleanVin
                return@withLock _selectedVehicle.value
            }

            val ownerId = currentProviderUserId()

            // 1. If an existing vehicle in the garage already has this exact VIN, select it
            vehicleRepository.getVehicleByVin(ownerId, cleanVin)?.let { existing ->
                selectVehicle(existing, ActiveVehicleChangeReason.VERIFIED_ECU_BINDING)
                lastAutoRecognizedVin = cleanVin
                voiceFeedbackManager.speak(
                    "Vehículo reconocido por VIN. ${existing.make} ${existing.model} seleccionado.",
                    "Vehicle recognized by VIN. ${existing.make} ${existing.model} selected."
                )
                addTerminalLog("[VIN] Vehículo existente seleccionado automáticamente: ${existing.make} ${existing.model}.", TerminalLineType.SYSTEM)
                return@withLock existing
            }

            // 2. If the user currently has an active vehicle without a VIN, bind this VIN to it permanently
            _selectedVehicle.value?.let { currentVehicle ->
                if (currentVehicle.vin.isBlank() || currentVehicle.vin == "NOT_SET" || currentVehicle.vin == "PENDIENTE" || currentVehicle.vin == "N/A") {
                    val updatedVehicle = currentVehicle.copy(vin = cleanVin)
                    when (val result = vehicleRepository.insertVehicle(updatedVehicle)) {
                        is com.elysium369.meet.core.remote.RemoteResult.Success -> {
                            selectVehicle(updatedVehicle, ActiveVehicleChangeReason.VERIFIED_ECU_BINDING)
                            lastAutoRecognizedVin = cleanVin
                            addTerminalLog("[VIN] VIN $cleanVin vinculado y guardado permanentemente para ${updatedVehicle.make} ${updatedVehicle.model}.", TerminalLineType.SYSTEM)
                            return@withLock updatedVehicle
                        }
                        else -> {
                            Log.w("ObdVM", "VIN bind failed: $result")
                        }
                    }
                }
            }

            // 3. Save the vehicle to the garage but NEVER auto-select it.
            //    Active vehicle changes only by explicit user intent.
            val decoded = VinDecoder.decode(cleanVin)
            _vinDecoded.value = decoded
            val parsedYear = decoded?.modelYear?.toIntOrNull() ?: 0
            val parsedMake = decoded?.manufacturer?.takeIf { it.isNotBlank() && !it.contains("Desconocido") } ?: "Vehículo Identificado"
            val parsedModel = if (decoded != null && decoded.manufacturer.contains("Hyundai", ignoreCase = true)) "Accent / Verna" else "Modelo por VIN"
            val remembered = Vehicle(
                id = VinVehicleIdentity.stableVehicleId(ownerId, cleanVin),
                user_id = ownerId,
                year = parsedYear,
                make = parsedMake,
                model = parsedModel,
                engine = "Motor Registrado",
                vin = cleanVin,
                plate = "NOT_SET",
            )
            when (val result = vehicleRepository.insertVehicle(remembered)) {
                is com.elysium369.meet.core.remote.RemoteResult.Success -> {
                    lastAutoRecognizedVin = cleanVin
                }
                else -> {
                    Log.w("ObdVM", "New vehicle save failed: $result")
                }
            }
            val currentVehicleName = _selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "ninguno"
            voiceFeedbackManager.speak(
                "VIN detectado. Nuevo vehículo $parsedMake $parsedModel guardado en Garage. Tu vehículo activo sigue siendo $currentVehicleName.",
                "VIN detected. New vehicle $parsedMake $parsedModel saved in Garage. Your active vehicle remains $currentVehicleName.",
            )
            addTerminalLog(
                "[VIN] Nuevo vehículo guardado en Garage (no activado): $parsedMake $parsedModel (VIN: $cleanVin). Selecciona manualmente desde Garage para activarlo.",
                TerminalLineType.SYSTEM,
            )
            remembered
        }
    }

    private fun normalizeVin(rawVin: String): String? {
        return VinVehicleIdentity.normalize(rawVin)
    }

    // ═══════════════════════════════════════
    //  FUEL ECONOMY TRACKER
    // ═══════════════════════════════════════
    private val _fuelSnapshot = MutableStateFlow<FuelEconomyTracker.FuelSnapshot?>(null)
    val fuelSnapshot: StateFlow<FuelEconomyTracker.FuelSnapshot?> = _fuelSnapshot.asStateFlow()

    fun updateFuelEconomy(data: Map<String, Float>) {
        val displacement = _selectedVehicle.value?.displacement_cc ?: 2000
        _fuelSnapshot.value = fuelEconomyTracker.calculate(data, displacement)
    }
    fun resetFuelSession() { fuelEconomyTracker.resetSession(); _fuelSnapshot.value = null }

    // ═══════════════════════════════════════
    //  BATTERY HEALTH ANALYZER
    // ═══════════════════════════════════════
    private val _batteryReport = MutableStateFlow<BatteryHealthAnalyzer.BatteryReport?>(null)
    val batteryReport: StateFlow<BatteryHealthAnalyzer.BatteryReport?> = _batteryReport.asStateFlow()

    fun updateBatteryHealth(data: Map<String, Float>) {
        _batteryReport.value = batteryHealthAnalyzer.analyze(data)
    }

    fun getVoltageCalibrationOffset(): Float {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat("voltage_calibration_offset", 0f)
    }

    fun setVoltageCalibrationOffset(offset: Float) {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("voltage_calibration_offset", offset).apply()
    }

    // ═══════════════════════════════════════
    //  TURBO BOOST GAUGE
    // ═══════════════════════════════════════
    private val _boostSnapshot = MutableStateFlow<TurboBoostGauge.BoostSnapshot?>(null)
    val boostSnapshot: StateFlow<TurboBoostGauge.BoostSnapshot?> = _boostSnapshot.asStateFlow()

    fun updateBoost(data: Map<String, Float>) {
        _boostSnapshot.value = turboBoostGauge.calculate(data)
    }
    fun resetBoostPeak() { turboBoostGauge.resetPeak() }

    // ═══════════════════════════════════════
    //  SMOG CHECK PREDICTOR
    // ═══════════════════════════════════════
    private val _smogPrediction = MutableStateFlow<SmogCheckPredictor.SmogPrediction?>(null)
    val smogPrediction: StateFlow<SmogCheckPredictor.SmogPrediction?> = _smogPrediction.asStateFlow()

    fun runSmogCheck() {
        val readiness = _readinessMonitors.value?.monitors?.associate {
            it.name to it.complete
        } ?: emptyMap()
        _smogPrediction.value = SmogCheckPredictor.predict(
            activeDtcs = activeDtcs.value,
            pendingDtcs = pendingDtcs.value,
            permanentDtcs = permanentDtcs.value,
            readinessMonitors = readiness,
            liveData = _liveData.value
        )
    }

    // ═══════════════════════════════════════
    //  MAINTENANCE PREDICTOR
    // ═══════════════════════════════════════
    private val _maintenanceItems = MutableStateFlow<List<MaintenancePredictor.MaintenanceItem>>(emptyList())
    val maintenanceItems: StateFlow<List<MaintenancePredictor.MaintenanceItem>> = _maintenanceItems.asStateFlow()

    fun predictMaintenance() {
        val km = _currentOdometer.value
        val coolant = _liveData.value["0105"]
        _maintenanceItems.value = MaintenancePredictor.predict(currentKm = km, coolantTemp = coolant)
    }

    // ── Vehicle Identification (Car Scanner Pro style) ──
    val detectedProtocol: StateFlow<String> = obdSession.detectedProtocolFlow
    val adapterVersion: StateFlow<String> = obdSession.adapterVersionFlow
    val isCloneAdapter: StateFlow<Boolean> = obdSession.isCloneAdapterFlow
    val calibrationId: StateFlow<String?> = obdSession.calibrationId
    val ecuName: StateFlow<String?> = obdSession.ecuName

    // --- Network Topology ---
    private val _networkTopology = MutableStateFlow<List<NetworkModule>>(emptyList())
    val networkTopology: StateFlow<List<NetworkModule>> = _networkTopology.asStateFlow()
    private val _isScanningTopology = MutableStateFlow(false)
    val isScanningTopology: StateFlow<Boolean> = _isScanningTopology.asStateFlow()
    private var networkTopologyJob: Job? = null

    fun scanNetworkTopology() {
        networkTopologyJob?.cancel()
        networkTopologyJob = viewModelScope.launch(Dispatchers.IO) {
            _isScanningTopology.value = true
            try {
                _networkTopology.value = scanModules()
            } finally {
                _isScanningTopology.value = false
            }
        }
    }

    fun cancelNetworkTopologyScan() {
        networkTopologyJob?.cancel()
    }

    // --- Digital Oscilloscope ---
    val oscilloscopeStream: SharedFlow<Pair<Long, Float>> = obdSession.oscilloscopeStream
    private val _oscilloscopeCapture = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val oscilloscopeCapture: StateFlow<List<Pair<Long, Float>>> = _oscilloscopeCapture.asStateFlow()
    private val _isOscilloscopeRunning = MutableStateFlow(false)
    val isOscilloscopeRunning: StateFlow<Boolean> = _isOscilloscopeRunning.asStateFlow()
    private val _activeOscilloscopePid = MutableStateFlow<String?>(null)
    val activeOscilloscopePid: StateFlow<String?> = _activeOscilloscopePid.asStateFlow()
    private val _oscilloscopeStartedAt = MutableStateFlow(0L)
    val oscilloscopeStartedAt: StateFlow<Long> = _oscilloscopeStartedAt.asStateFlow()
    private var oscilloscopeCaptureJob: Job? = null

    fun startOscilloscope(pidCode: String) {
        if (_isOscilloscopeRunning.value && _activeOscilloscopePid.value == pidCode) return
        oscilloscopeCaptureJob?.cancel()
        _oscilloscopeCapture.value = emptyList()
        _activeOscilloscopePid.value = pidCode
        _oscilloscopeStartedAt.value = System.currentTimeMillis()
        _isOscilloscopeRunning.value = true
        val now = System.currentTimeMillis()
        activeOperationsRegistry.upsert(
            ActiveOperation(
                operationId = "oscilloscope-obd",
                type = "OBD_OSCILLOSCOPE",
                vehicleId = selectedVehicle.value?.id,
                startedAtEpochMs = now,
                state = OperationState.RUNNING,
                progress = null,
                owner = OperationOwner.VEHICLE_SCOPED,
                recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                lastHeartbeatEpochMs = now,
            ),
        )
        oscilloscopeCaptureJob = viewModelScope.launch {
            obdSession.oscilloscopeStream.collect { point ->
                _oscilloscopeCapture.update { current -> (current + point).takeLast(2_000) }
            }
        }
        obdSession.startOscilloscope(pidCode)
    }

    fun stopOscilloscope() {
        _isOscilloscopeRunning.value = false
        oscilloscopeCaptureJob?.cancel()
        oscilloscopeCaptureJob = null
        obdSession.stopOscilloscope()
        activeOperationsRegistry.complete("oscilloscope-obd")
    }

    fun clearOscilloscopeCapture() {
        if (!_isOscilloscopeRunning.value) _oscilloscopeCapture.value = emptyList()
    }

    // ── Physical USB Oscilloscope (Hantek 6022BE) ──
    val usbCh1Data: StateFlow<FloatArray> = usbOscilloscopeManager.ch1Data
    val usbCh2Data: StateFlow<FloatArray> = usbOscilloscopeManager.ch2Data
    val usbIsStreaming: StateFlow<Boolean> = usbOscilloscopeManager.isStreaming
    val usbDeviceConnected: StateFlow<Boolean> = usbOscilloscopeManager.deviceConnected
    val usbCh1Attenuation: StateFlow<Float> = usbOscilloscopeManager.ch1Attenuation
    val usbCh2Attenuation: StateFlow<Float> = usbOscilloscopeManager.ch2Attenuation
    val usbTriggerLevel: StateFlow<Float> = usbOscilloscopeManager.triggerLevel
    val usbTriggerEdgeRising: StateFlow<Boolean> = usbOscilloscopeManager.triggerEdgeRising
    val usbSamplingRate: StateFlow<Long> = usbOscilloscopeManager.samplingRate

    fun toggleUsbOscilloscopeStream() {
        if (usbOscilloscopeManager.isStreaming.value) {
            usbOscilloscopeManager.stopStreaming()
            activeOperationsRegistry.complete("oscilloscope-usb")
        } else {
            usbOscilloscopeManager.startStreaming()
            val now = System.currentTimeMillis()
            activeOperationsRegistry.upsert(
                ActiveOperation(
                    operationId = "oscilloscope-usb",
                    type = "USB_OSCILLOSCOPE",
                    vehicleId = selectedVehicle.value?.id,
                    startedAtEpochMs = now,
                    state = OperationState.RUNNING,
                    progress = null,
                    owner = OperationOwner.VEHICLE_SCOPED,
                    recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                    lastHeartbeatEpochMs = now,
                ),
            )
        }
    }

    fun setUsbCh1Attenuation(factor: Float) {
        usbOscilloscopeManager.setCh1Attenuation(factor)
    }

    fun setUsbCh2Attenuation(factor: Float) {
        usbOscilloscopeManager.setCh2Attenuation(factor)
    }

    fun changeUsbTriggerLevel(volts: Float) {
        usbOscilloscopeManager.setTriggerLevel(volts)
    }

    fun setUsbTriggerEdge(rising: Boolean) {
        usbOscilloscopeManager.setTriggerEdge(rising)
    }

    fun setUsbSamplingRate(hz: Long) {
        usbOscilloscopeManager.setSamplingRate(hz)
    }

    fun stopUsbOscilloscopeStream() {
        usbOscilloscopeManager.stopStreaming()
        activeOperationsRegistry.complete("oscilloscope-usb")
    }

    // --- Oscilloscope Capture Persistence ---
    data class OscilloscopeCapture(
        val pidCode: String,
        val pidName: String,
        val severity: String,
        val diagnosisText: String,
        val recommendationText: String,
        val durationMs: Long,
        val sampleCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _oscilloscopeHistory = MutableStateFlow<List<OscilloscopeCapture>>(emptyList())
    val oscilloscopeHistory: StateFlow<List<OscilloscopeCapture>> = _oscilloscopeHistory.asStateFlow()

    fun saveOscilloscopeCapture(
        pidCode: String, pidName: String, values: List<Float>, durationMs: Long,
        diagnosisSeverity: String, diagnosisText: String, recommendationText: String
    ) {
        val capture = OscilloscopeCapture(pidCode, pidName, diagnosisSeverity,
            diagnosisText, recommendationText, durationMs, values.size)
        _oscilloscopeHistory.update { it + capture }

        // Persist as part of the diagnostic session
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = _selectedVehicle.value ?: return@launch
            try {
                val session = com.elysium369.meet.data.supabase.DiagnosticSession(
                    id = UUID.randomUUID().toString(),
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_vin = vehicle.vin,
                    vehicle_make = vehicle.make,
                    vehicle_model = vehicle.model,
                    vehicle_year = vehicle.year,
                    scan_type = "oscilloscope",
                    severity = diagnosisSeverity,
                    notes = "OSCILLOSCOPE|$pidCode|$pidName|${values.size}pts|${durationMs}ms",
                    live_data_snapshot = Json.encodeToString(mapOf(
                        "pid" to pidCode,
                        "pidName" to pidName,
                        "sampleCount" to values.size.toString(),
                        "durationMs" to durationMs.toString(),
                        "diagnosis" to diagnosisText,
                        "recommendation" to recommendationText
                    ))
                )
                sessionLogRepository.saveSession(session, vehicle.id)
                Log.d("ObdVM", "✅ Oscilloscope capture saved: $pidName ($diagnosisSeverity)")
            } catch (e: Exception) {
                Log.e("ObdVM", "Failed to save oscilloscope capture", e)
            }
        }
    }

    // --- AI and Health State ---
    private val _anomalousPids = MutableStateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>>(emptyList())
    val anomalousPids: StateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>> = _anomalousPids.asStateFlow()

    private val _isAiMonitoring = MutableStateFlow(false)
    val isAiMonitoring: StateFlow<Boolean> = _isAiMonitoring.asStateFlow()
    private var aiMonitorJob: kotlinx.coroutines.Job? = null

    private val _healthScore = MutableStateFlow(100)
    val healthScore: StateFlow<Int> = _healthScore.asStateFlow()

    private val _localDiagnostics = MutableStateFlow<List<ExpertDiagnosticProcedure>>(emptyList())
    val localDiagnostics: StateFlow<List<ExpertDiagnosticProcedure>> = _localDiagnostics.asStateFlow()

    // --- Local Expert Manual Start/Stop ---
    private val _isLocalExpertActive = MutableStateFlow(true)
    val isLocalExpertActive: StateFlow<Boolean> = _isLocalExpertActive.asStateFlow()

    fun toggleLocalExpert() {
        _isLocalExpertActive.value = !_isLocalExpertActive.value
        if (!_isLocalExpertActive.value) {
            // Clear diagnostics immediately when stopped
            _localDiagnostics.value = emptyList()
        }
    }

    fun setLocalExpertActive(active: Boolean) {
        _isLocalExpertActive.value = active
        if (!active) {
            _localDiagnostics.value = emptyList()
        }
    }

    // --- Predictive Health Engine State ---
    private val _predictiveHealthReport = MutableStateFlow<com.elysium369.meet.core.health.PredictiveHealthReport?>(null)
    val predictiveHealthReport: StateFlow<com.elysium369.meet.core.health.PredictiveHealthReport?> = _predictiveHealthReport.asStateFlow()

    private val _isAnalyzingHealth = MutableStateFlow(false)
    val isAnalyzingHealth: StateFlow<Boolean> = _isAnalyzingHealth.asStateFlow()

    private val healthSessionId = UUID.randomUUID().toString()
    private var sensorRecordingCounter = 0

    // --- Logging State ---
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()
    private val _dataLog = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLog: StateFlow<List<DataLogEntry>> = _dataLog.asStateFlow()
    private var loggingJob: kotlinx.coroutines.Job? = null

    // --- Async Telemetry Buffer ---
    private val telemetryBuffer = kotlinx.coroutines.channels.Channel<com.elysium369.meet.data.local.entities.SensorHistoryEntity>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    // --- Reactive Data from Room ---
    val trips: StateFlow<List<TripEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let {
                tripDao.getTripsForVehicle(it.id, activePrincipalKernel.current().id)
            } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val maintenanceAlerts: StateFlow<List<MaintenanceAlertEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { maintenanceAlertDao.getAlertsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customPids: StateFlow<List<CustomPidEntity>> = customPidDao.getAllCustomPids()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val vehicles: StateFlow<List<Vehicle>> = activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
        vehicleRepository.getVehiclesForUser(principal.id)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- DVIR Inspection Flow & CRUD Helpers ---
    val dvirReports: StateFlow<List<com.elysium369.meet.data.local.entities.DvirReportEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { dvirReportDao.getReportsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val predictionEvents: StateFlow<List<PredictionEventEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { predictionEventDao.observeEventsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val healthHistory: StateFlow<List<HealthSnapshotEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { healthSnapshotDao.observeSnapshots(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insertDvirReport(report: com.elysium369.meet.data.local.entities.DvirReportEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dvirReportDao.insertReport(report)
        }
    }

    fun generateDvirReportPdf(
        report: com.elysium369.meet.data.local.entities.DvirReportEntity,
        vehicleInfo: String,
        onSuccess: (java.io.File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = reportGenerator.generateDvirReport(report, vehicleInfo)
                launch(Dispatchers.Main) {
                    onSuccess(file)
                    reportGenerator.shareReport(file)
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Failed to generate DVIR PDF", e)
                launch(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun shareReport(file: java.io.File) {
        reportGenerator.shareReport(file)
    }

    // ──── Virtual Dyno (Dinamómetro de Chasis Virtual) State & Sensors ────
    private val _vehicleMass = MutableStateFlow(1500f)
    val vehicleMass: StateFlow<Float> = _vehicleMass.asStateFlow()

    private val _drivetrainLoss = MutableStateFlow(15f)
    val drivetrainLoss: StateFlow<Float> = _drivetrainLoss.asStateFlow()

    private val _isDynoRunning = MutableStateFlow(false)
    val isDynoRunning: StateFlow<Boolean> = _isDynoRunning.asStateFlow()

    private val _dynoPoints = MutableStateFlow<List<PerformanceCalculator.DynoPoint>>(emptyList())
    val dynoPoints: StateFlow<List<PerformanceCalculator.DynoPoint>> = _dynoPoints.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION)
    private var lastLinearAccelY = 0f

    private val sensorEventListener = object : android.hardware.SensorEventListener {
        private var smoothedAccel = 0f
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event == null || event.sensor.type != android.hardware.Sensor.TYPE_LINEAR_ACCELERATION) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate linear acceleration magnitude
            val raw = kotlin.math.sqrt(x*x + y*y + z*z)
            // Apply low pass filter to eliminate engine vibration noise
            smoothedAccel = smoothedAccel * 0.85f + raw * 0.15f
            lastLinearAccelY = smoothedAccel
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    fun startDynoTest() {
        performanceCalculator.startDynoRun()
        _isDynoRunning.value = true
        _dynoPoints.value = emptyList()

        // Register accelerometer listener
        sensorManager?.let { sm ->
            accelerometer?.let { acc ->
                sm.registerListener(sensorEventListener, acc, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
        voiceFeedbackManager.speak("Prueba de dinamómetro iniciada. Acelere a fondo.", "Dyno test started. Accelerate fully.")
    }

    fun stopDynoTest() {
        val points = performanceCalculator.stopDynoRun()
        _dynoPoints.value = points
        _isDynoRunning.value = false

        // Unregister accelerometer listener
        sensorManager?.unregisterListener(sensorEventListener)
        voiceFeedbackManager.speak("Prueba de dinamómetro finalizada.", "Dyno test completed.")
    }

    fun resetDynoTest() {
        performanceCalculator.startDynoRun()
        _dynoPoints.value = emptyList()
    }

    fun updateDynoSettings(mass: Float, loss: Float) {
        _vehicleMass.value = mass
        _drivetrainLoss.value = loss
        performanceCalculator.vehicleMassKg = mass
        performanceCalculator.drivetrainLossPercent = loss

        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("dyno_vehicle_mass", mass)
            .putFloat("dyno_drivetrain_loss", loss)
            .apply()
    }

    // ──── Dashcam / Telemetry Recording State ────
    private val _isRecordingDashcam = MutableStateFlow(false)
    val isRecordingDashcam: StateFlow<Boolean> = _isRecordingDashcam.asStateFlow()

    private val _isTranscodingVideo = MutableStateFlow(false)
    val isTranscodingVideo: StateFlow<Boolean> = _isTranscodingVideo.asStateFlow()

    private val _transcodingProgress = MutableStateFlow(0)
    val transcodingProgress: StateFlow<Int> = _transcodingProgress.asStateFlow()

    private val dashcamTelemetryBuffer = mutableListOf<DashcamTelemetryFrame>()
    private var dashcamStartTimeMs: Long = 0L

    fun startDashcamRecording() {
        _isRecordingDashcam.value = true
        dashcamStartTimeMs = System.currentTimeMillis()
        dashcamTelemetryBuffer.clear()
        voiceFeedbackManager.speak("Grabación de video e información OBD2 iniciada.", "Video and OBD2 logging started.")
    }

    fun stopDashcamRecording(videoUri: android.net.Uri? = null) {
        _isRecordingDashcam.value = false

        if (videoUri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Guardar telemetría en directorio privado de la app (evita EPERM)
                    val telemetryDir = java.io.File(context.getExternalFilesDir(null), "Telemetry")
                    if (!telemetryDir.exists()) telemetryDir.mkdirs()
                    val telemetryFile = java.io.File(telemetryDir, "telemetry_${System.currentTimeMillis()}.meet.json")
                    val jsonString = Json.encodeToString(dashcamTelemetryBuffer)
                    telemetryFile.writeText(jsonString)
                    Log.d("ObdVM", "Saved synced telemetry log: ${telemetryFile.absolutePath}")

                    if (dashcamTelemetryBuffer.isEmpty()) {
                        Log.w("ObdVM", "Telemetry buffer is empty, skipping transcode")
                        voiceFeedbackManager.speak("Video guardado sin telemetría.", "Video saved without telemetry.")
                        return@launch
                    }

                    // Empezar transcodificación
                    _isTranscodingVideo.value = true
                    _transcodingProgress.value = 0
                    voiceFeedbackManager.speak("Procesando video e impregnando telemetría.", "Processing video and overlaying telemetry.")

                    // Archivo temporal de salida en caché de la app (sin restricciones Scoped Storage)
                    val tempOutputFile = java.io.File(context.cacheDir, "baked_${System.currentTimeMillis()}.mp4")
                    if (tempOutputFile.exists()) tempOutputFile.delete()

                    val burner = com.elysium369.meet.core.video.VideoTelemetryBurner(context)
                    burner.burnTelemetry(
                        inputUri = videoUri,
                        outputFile = tempOutputFile,
                        telemetryFrames = ArrayList(dashcamTelemetryBuffer),
                        onProgress = { progress ->
                            _transcodingProgress.value = progress
                        },
                        onComplete = {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    if (tempOutputFile.exists()) {
                                        // Escribir de vuelta al MediaStore via ContentResolver (compatible Scoped Storage)
                                        context.contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                                            tempOutputFile.inputStream().use { inputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                        tempOutputFile.delete()
                                    }
                                    _isTranscodingVideo.value = false
                                    voiceFeedbackManager.speak("Video procesado exitosamente con telemetría.", "Video telemetry overlay completed successfully.")
                                } catch (e: Exception) {
                                    Log.e("ObdVM", "Failed to write baked video back to MediaStore", e)
                                    _isTranscodingVideo.value = false
                                }
                            }
                        },
                        onError = { throwable ->
                            Log.e("ObdVM", "Failed to burn telemetry into video", throwable)
                            _isTranscodingVideo.value = false
                            if (tempOutputFile.exists()) tempOutputFile.delete()
                        }
                    )
                } catch (e: Exception) {
                    Log.e("ObdVM", "Failed to save dashcam telemetry", e)
                    _isTranscodingVideo.value = false
                }
            }
        } else {
            voiceFeedbackManager.speak("Grabación finalizada.", "Recording completed.")
        }
    }

    // This state is read by the principal collector below. It must be created
    // before init{} starts collecting; declaring it later used to crash the
    // application during cold start with a null MutableStateFlow.
    private val _rideDriverMode = MutableStateFlow(false)
    val rideDriverMode: StateFlow<Boolean> = _rideDriverMode.asStateFlow()
    private var rideDriverPresenceJob: Job? = null
    private val _rideDriverPresenceHealthy = MutableStateFlow(false)
    val rideDriverPresenceHealthy: StateFlow<Boolean> = _rideDriverPresenceHealthy.asStateFlow()

    // The outbox rejection collector in init{} emits through this flow. Keep
    // it initialized before init{} runs, just like rideDriverMode.
    private val _rideVerificationNotice = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
    )
    val rideVerificationNotice: SharedFlow<String> =
        _rideVerificationNotice.asSharedFlow()

    init {
        viewModelScope.launch {
            activePrincipalKernel.activePrincipal.drop(1).collectLatest { principal ->
                // A principal transition is a tenant boundary. Never retain a
                // vehicle, physical binding, provider role or remote projection
                // selected under the previous account.
                notesPollingJob?.cancel()
                providerRolesJob?.cancel()
                stopRideProjectionSync()
                _vehicleSessionBinding.value = VehicleSessionBinding.unbound(
                    diagnosticSessionId = currentSessionId,
                    physicalConnectionId = "PRINCIPAL_CHANGED",
                )
                _userProviderProfiles.value = emptyList()
                _isMechanic.value = false
                _isTowTruckDriver.value = false
                _isPartsStore.value = false
                refreshProviderRoles()
                if (principal.isAuthenticated) {
                    syncPendingTrustApplications()
                    withContext(Dispatchers.IO) {
                        when (val result = vehicleRepository.syncVehiclesFromCloud(principal.id)) {
                            is com.elysium369.meet.core.remote.RemoteResult.Success ->
                                Log.i("ObdVM", "Vehicle sync: ${result.value} vehicles synced")
                            is com.elysium369.meet.core.remote.RemoteResult.Unauthorized ->
                                Log.w("ObdVM", "Vehicle sync: auth expired, skipping")
                            else ->
                                Log.w("ObdVM", "Vehicle sync failed: $result")
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            entitlementManager.hasAccess(FeatureKey.SCAN_ADVANCED).collect { hasAccess ->
                _isPremium.value = hasAccess
            }
        }
        startMarketplaceSync()
        refreshProviderRoles()

        // Realtime subscription for own verification status — instant UI update after owner approval
        var ownVerificationJob: Job? = null
        viewModelScope.launch {
            activePrincipalKernel.activePrincipal.collectLatest { principal ->
                ownVerificationJob?.cancel()
                stopRideDriverPresenceHeartbeat()
                val modeKey = com.elysium369.meet.ride.domain.RideDriverSessionRestoration
                    .modeKey(principal.id.takeIf { principal.isAuthenticated })
                _rideDriverMode.value = modeKey?.let {
                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                        .getBoolean(it, false)
                } ?: false
                if (principal.isAuthenticated) {
                    refreshOwnTrustDecisions()
                    ownVerificationJob = viewModelScope.launch(Dispatchers.IO) {
                        PlatformTrustCenterGateway.ownVerificationChanges()
                            .retryWhen { _, attempt ->
                                delay((2_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L))
                                true
                            }
                            .collectLatest {
                                Log.i("ElysiumTrustCenter", "Own verification changed; syncing from server")
                                refreshOwnTrustDecisions()
                                refreshProviderRoles()
                            }
                    }
                }
            }
        }
        viewModelScope.launch {
            val sessionStartTime = System.currentTimeMillis()
            val notifiedFailures = mutableSetOf<String>()
            val actionableCommandTypes = setOf(
                RideCommandType.CLAIM.name,
                RideCommandType.ACCEPT_OFFER.name,
                RideCommandType.SUBMIT_OFFER.name,
                RideCommandType.VERIFY_BOARDING_PIN.name,
                RideCommandType.DRIVER_ARRIVED.name,
                RideCommandType.START.name,
                RideCommandType.COMPLETE.name,
            )
            rideCommandRepository.recentFailures()
                .distinctUntilChanged()
                .collectLatest { failures ->
                    failures.firstOrNull {
                        it.commandType in actionableCommandTypes &&
                            it.updatedAt >= sessionStartTime &&
                            notifiedFailures.add(it.idempotencyKey)
                    }?.let { failure ->
                        val code = failure.lastErrorCode
                            .orEmpty()
                            .ifBlank { "UNKNOWN" }
                        val detail = failure.lastErrorMessage.orEmpty()

                        if (failure.commandType == RideCommandType.CLAIM.name) {
                            val message = when (code) {
                                "VERSION_CONFLICT" ->
                                    "El viaje cambió mientras lo aceptabas. Actualizando."
                                "ALREADY_ASSIGNED" ->
                                    "Otro conductor obtuvo este viaje primero."
                                "VEHICLE_NOT_VERIFIED" ->
                                    "Tu vehículo activo aún no está VERIFIED."
                                "INSUFFICIENT_BALANCE" ->
                                    "Saldo insuficiente para la reserva requerida."
                                "AUTH_SESSION_MISMATCH",
                                "AUTHENTICATION_REQUIRED" ->
                                    "Tu sesión cambió. Vuelve a iniciar sesión."
                                else ->
                                    detail
                                        .take(160)
                                        .ifBlank {
                                            "El servidor rechazó la aceptación."
                                        }
                            }

                            _rideClaimUiState.value =
                                RideClaimUiState.Rejected(
                                    requestId = failure.rideId,
                                    code = code,
                                    message = message,
                                )

                            _rideClaimFeedback.emit(
                                RideClaimFeedback(
                                    requestId = failure.rideId,
                                    won = false,
                                    pending = false,
                                    message = message,
                                )
                            )

                            refreshRideProjectionNow()
                        } else {
                            val message = when (code) {
                                "INSUFFICIENT_BALANCE" ->
                                    if (failure.commandType == RideCommandType.SUBMIT_OFFER.name) {
                                        "La oferta no se publicó: necesitas saldo disponible para reservar el 5% de comisión."
                                    } else {
                                        "No se puede aceptar todavía: el chofer necesita saldo para reservar la comisión."
                                    }
                                "VERSION_CONFLICT" ->
                                    "La oferta cambió mientras la aceptabas; actualiza las solicitudes."
                                "OFFER_NOT_AVAILABLE" ->
                                    "La contraoferta ya no está disponible."
                                else -> "No se pudo procesar la solicitud: ${detail.take(140)}"
                            }
                            _rideVerificationNotice.emit(message)
                            RideObservability.event(
                                "command_failure_feedback",
                                outcome = "FAILED",
                                requestId = failure.rideId,
                                detail = code.ifBlank { "unknown" },
                            )
                        }
                    }
                }
        }
        // Voice command manager callbacks and initial startup checking
        voiceCommandManager.onCommandRecognized = { command ->
            handleVoiceCommand(command)
        }
        val initialPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        if (initialPrefs.getBoolean("voice_copilot_enabled", false)) {
            voiceCommandManager.startCopilot()
            speechService.startListeningToEvents()
            notificationService.startListeningToEvents()
        }
        _vehicleMass.value = initialPrefs.getFloat("dyno_vehicle_mass", 1500f)
        _drivetrainLoss.value = initialPrefs.getFloat("dyno_drivetrain_loss", 15f)
        performanceCalculator.vehicleMassKg = _vehicleMass.value
        performanceCalculator.drivetrainLossPercent = _drivetrainLoss.value

        // Initialize remembered adapter metadata in state (Zero physical connection without explicit user intent)
        prepareRememberedAdapter()

        // Wire real-time OBD traffic capture to the terminal log
        obdSession.setTrafficListener(object : ObdTrafficListener {
            override fun onCommandSent(command: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("TX ❯ $command", TerminalLineType.COMMAND))
                    // Keep last 500 lines to avoid memory bloat
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
            override fun onResponseReceived(command: String, response: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("RX ← $response", TerminalLineType.RESPONSE))
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
            override fun onError(command: String, error: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("ERR ✗ $command → $error", TerminalLineType.ERROR))
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
        })



        // PRODUCTION-GRADE: Each collector is isolated with try-catch to prevent
        // a single flow failure from crashing the entire ViewModel during startup.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            // Track and announce connection state changes
            launch {
                var lastState: ObdState? = null
                connectionState.collect { state ->
                    projectObdOperation(state)
                    if (state != lastState) {
                        lastState = state
                        when (state) {
                            ObdState.CONNECTING -> voiceFeedbackManager.speak("Iniciando enlace con el adaptador OBD", "Initiating link with OBD adapter")
                            ObdState.NEGOTIATING -> voiceFeedbackManager.speak("Estableciendo protocolo de comunicación", "Establishing communication protocol")
                            ObdState.CONNECTED -> {
                                voiceFeedbackManager.speak("Conexión establecida. Sistema de telemetría Elysium Vanguard AI OS activo.", "Connection established. Elysium Vanguard AI OS telemetry system active.")
                                com.elysium369.meet.core.notification.ElysiumNotificationHub.showObdConnectedPopUp(context, "Adaptador OBD2")
                                startForegroundService(_selectedVehicle.value?.id ?: "active_vehicle", lastAdapterAddress)
                            }
                            ObdState.ERROR -> voiceFeedbackManager.speak("Error de conexión. Por favor, verifique el adaptador.", "Connection error. Please check the adapter.")
                            else -> {}
                        }
                    }
                }
            }

            launch {
                // ActiveVehicleKernel may restore after this ViewModel is created.
                // Keep every OBD consumer on that single durable authority without
                // manufacturing a selection from a transient garage list.
                selectedVehicle.collect { vehicle ->
                    obdSession.setVehicleCapabilityContext(
                        manufacturer = vehicle?.make,
                        modelFamily = vehicle?.model,
                        year = vehicle?.year,
                    )
                }
            }

            // Auto-fetch DTC definitions whenever new events are loaded from database
            launch {
                try {
                    combine(
                        canonicalActiveFindingSummaries,
                        canonicalPendingFindingSummaries,
                        canonicalPermanentFindingSummaries,
                        canonicalHistoricalFindingSummaries
                    ) { active, pending, permanent, historical ->
                        (active + pending + permanent + historical).map { it.code }.distinct()
                    }.collect { codes ->
                        if (codes.isNotEmpty()) {
                            fetchDtcDefinitions(codes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error pre-fetching DTC definitions from flow", e)
                }
            }

            // Collect live data from session — smoothed for professional gauge transitions
            launch {
                try {
                    obdSession.liveData
                        .collect { rawData ->
                            val timestamp = System.currentTimeMillis()

                            // Feed raw OBD speed to the phone speed tracker if present
                            rawData["010D"]?.let { rawSpeed ->
                                phoneSpeedTracker.setObdSpeed(rawSpeed)
                            }

                            // Apply per-PID moving average + exponential interpolation
                            val smoothedData = sensorSmoother.smoothAll(rawData).toMutableMap()

                            // Restore raw voltage for oscilloscope and fast gauges (bypass second smoothing)
                            rawData["0142"]?.let { smoothedData["0142"] = it }
                            rawData["42"]?.let { smoothedData["42"] = it }

                            // Override speedometer speed with high-refresh fused speed if enabled
                            if (_fusedSpeedEnabled.value) {
                                val fusedSpeedVal = phoneSpeedTracker.fusedSpeed.value
                                smoothedData["010D"] = fusedSpeedVal
                                smoothedData["0D"] = fusedSpeedVal
                            }

                            // Update predictive estimator with newly received OBD-II values
                            predictiveEstimator.updateRawValues(smoothedData)

                            _liveData.value = smoothedData
                            if (_isLocalExpertActive.value) {
                                _localDiagnostics.value = localExpertSystem.analyzeLiveTelemetry(
                                    liveData = smoothedData,
                                    activeDtcs = activeDtcs.value,
                                    dtcDefinitions = dtcDefinitions.value
                                )
                            }
                            updateTelemetryHistory(smoothedData)
                            updateOscilloscopeBuffer(timestamp, rawData)

                            // ── Performance Calculator (HP/Torque) ──
                            updatePerformance(smoothedData)
                            // ── Data Logger (if recording) ──
                            recordDataSample(smoothedData)
                            // ── Real-time Alerts ──
                            evaluateAlerts(smoothedData)
                            // ── Fuel Economy ──
                            updateFuelEconomy(smoothedData)
                            // ── Battery Health ──
                            updateBatteryHealth(smoothedData)
                            // ── Turbo Boost ──
                            updateBoost(smoothedData)
                            // ── Elysium Vanguard DNA Real-time Inference ──
                            evaluateDnaInference()

                            // ── LiveLink broadcast (only if server is active) ──
                            _liveLinkServer?.let { server ->
                                if (server.isRunning.value) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            val packet = buildLiveLinkTelemetryPacket(
                                                sessionId = _liveLinkProCoreSession.value?.sessionId ?: "local_wifi"
                                            )
                                            server.broadcastTelemetry(packet)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }

                            // Enviar datos al búfer asíncrono en lugar de bloquear con inserciones directas
                            val vehicle = _selectedVehicle.value
                            if (vehicle != null && _isLogging.value) {
                                val now = System.currentTimeMillis()
                                smoothedData.forEach { (pid, value) ->
                                    val modeStr = if (pid.length >= 4) pid.substring(0, 2) else "01"
                                    val pidStr = if (pid.length >= 4) pid.substring(2) else pid
                                    val pidDef = PidRegistry.getPid(modeStr, pidStr)

                                    val entity = com.elysium369.meet.data.local.entities.SensorHistoryEntity(
                                        vehicleId = vehicle.id,
                                        sessionId = healthSessionId,
                                        pid = pid,
                                        pidLabel = pidDef?.name ?: pid,
                                        value = value,
                                        unit = pidDef?.unit ?: "",
                                        timestamp = now
                                    )
                                    telemetryBuffer.trySend(entity)
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "liveData collector crashed", e)
                }
            }

            // 120Hz (8ms) Predictive Telemetry Extrapolation Loop
            // Fills the visual gaps between OBD-II readings (which poll at ~5-10Hz)
            // to achieve 100% fluid 120fps sweeps on modern displays.
            launch {
                try {
                    var lastPredictTime = System.currentTimeMillis()
                    while (true) {
                        kotlinx.coroutines.delay(8L) // ~120 Hz
                        val now = System.currentTimeMillis()
                        val dtMs = (now - lastPredictTime).toFloat().coerceIn(1f, 100f)
                        lastPredictTime = now

                        val currentMap = _liveData.value
                        if (currentMap.isNotEmpty() && obdSession.state.value == com.elysium369.meet.core.obd.ObdState.CONNECTED) {
                            // 1. Get predictions for fast PIDs (RPM, Throttle, etc.)
                            val estimatedMap = predictiveEstimator.getEstimatedMap(currentMap, dtMs).toMutableMap()

                            // 2. If fused speed is enabled, override 010D/0D with phoneSpeedTracker's high-refresh fusion speed
                            if (_fusedSpeedEnabled.value) {
                                val fusedSpeedVal = phoneSpeedTracker.fusedSpeed.value
                                estimatedMap["010D"] = fusedSpeedVal
                                estimatedMap["0D"] = fusedSpeedVal
                            }

                            _liveData.value = estimatedMap
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "predictive loop crashed", e)
                }
            }

            // Update local diagnostics reactively when DTCs or definitions change
            launch {
                try {
                    kotlinx.coroutines.flow.combine(activeDtcs, dtcDefinitions, _isLocalExpertActive) { dtcs, definitions, expertActive ->
                        Triple(dtcs, definitions, expertActive)
                    }.collect { (dtcs, definitions, expertActive) ->
                        if (expertActive) {
                            _localDiagnostics.value = localExpertSystem.analyzeLiveTelemetry(
                                liveData = _liveData.value,
                                activeDtcs = dtcs,
                                dtcDefinitions = definitions
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "DTC-diagnostics collector crashed", e)
                }
            }

            // Collect VIN and detect manufacturer
            launch {
                try {
                    obdSession.vin
                        .collect { v ->
                            _vin.value = v
                            v?.let {
                                detectManufacturer(it)
                                bindVehicleFromObservedVin(it)
                            }
                            updateHealthScore()
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "vin collector crashed", e)
                }
            }

            // Connection startup is handled by runPostConnectDtcFirstStartup()
            // so DTC pre-scan always finishes before VIN, odometer, polling, or actions.
            launch {
                try {
                    obdSession.state
                        .collect { state ->
                            if (state == ObdState.DISCONNECTED || state == ObdState.ERROR) {
                                hasCompletedInitialDtcScan = false
                                _latestDiagnosticScan.value = null
                                _healthScore.value = 0
                                _qosMetrics.value = QosMetrics(
                                    cmdsPerSecond = 0f,
                                    latencyMs = 0,
                                    isStable = false,
                                    avgLatencyMs = 0f,
                                    reliability = 0f,
                                    totalRequests = 0,
                                    successfulRequests = 0,
                                )
                                _anomalousPids.value = emptyList()
                                _liveData.value = emptyMap()
                                _vehicleSessionBinding.value = VehicleSessionBinding.unbound(
                                    diagnosticSessionId = currentSessionId,
                                    physicalConnectionId = "NOT_CONNECTED",
                                )
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "state collector crashed", e)
                }
            }

            // Sync custom PIDs to session
            launch {
                try {
                    customPidDao.getAllCustomPids()
                        .collect { pids ->
                            obdSession.setCustomPids(pids)
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "customPids collector crashed", e)
                }
            }

            // Collect QoS from session
            launch {
                try {
                    obdSession.qosMetrics
                        .collect { metrics ->
                            _qosMetrics.value = metrics
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "qos collector crashed", e)
                }
            }

            // Auto-start AI monitoring if enabled
            launch {
                try {
                    isAiMonitoring.collect { enabled ->
                        if (enabled) startAiMonitoring() else aiMonitorJob?.cancel()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "AI monitor collector crashed", e)
                }
            }

            // Reactive Health Score calculation
            launch {
                try {
                    combine(
                        activeDtcs,
                        pendingDtcs,
                        _anomalousPids,
                        _liveData
                    ) { active, pending, anomalies, live ->
                        calculateHealthScore(active, pending, anomalies, live)
                    }.collect { score ->
                        _healthScore.value = score
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "healthScore collector crashed", e)
                }
            }
        }

        // Subscriptions and Cloud Sync — isolated from main flow collectors
        viewModelScope.launch {
            try {
                // ActiveVehicleKernel restores owner-scoped intent independently
                // from this ViewModel and transient authentication states. Cloud
                // reconciliation may enrich the garage but never picks a replacement.
                val user = try {
                    SupabaseManager.client.auth.currentUserOrNull()
                } catch (e: Exception) {
                    null
                }

                if (user != null) {
                    _cloudSyncState.value = "Sincronizando garaje..."
                    when (val result = vehicleRepository.syncVehiclesFromCloud(user.id)) {
                        is com.elysium369.meet.core.remote.RemoteResult.Success ->
                            _cloudSyncState.value = "Sincronización completa (${result.value} vehículos)"
                        is com.elysium369.meet.core.remote.RemoteResult.Unauthorized ->
                            _cloudSyncState.value = "Sesión expirada; inicia sesión de nuevo"
                        else ->
                            _cloudSyncState.value = "Error de sincronización"
                    }
                }

                // Prune old AI consultations from cache (TTL 30 days).
                launch(Dispatchers.IO) {
                    try {
                        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                        aiConsultDao.pruneOldConsults(thirtyDaysAgo)
                        android.util.Log.i("ObdVM", "Successfully pruned AI consultations older than 30 days.")
                    } catch (e: Exception) {
                        android.util.Log.w("ObdVM", "Failed to prune old AI consultations cache: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ObdVM", "Startup sync/restore failed", e)
                _cloudSyncState.value = "Error de sincronización"
            }

            // Monitor vehicles count for debugging
            launch {
                vehicles.collect { list ->
                    android.util.Log.d("ObdVM", "Total vehicles in DB: ${list.size}")
                }
            }
        }

        // Load persisted settings for Clone Mode, Fused Speed & AI Config
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        _forceCloneMode.value = prefs.getBoolean("force_clone_mode", false)
        _language.value = prefs.getString("app_language", "es") ?: "es"

        val fusedEnabled = prefs.getBoolean("fused_speed_enabled", true)
        _fusedSpeedEnabled.value = fusedEnabled
        if (fusedEnabled) {
            phoneSpeedTracker.start()
        }

        viewModelScope.launch {
            phoneSpeedTracker.fusedSpeed
                .map { speedKmh -> speedKmh >= 3f }
                .distinctUntilChanged()
                .filter { moving -> moving }
                .collect {
                    obdSession.healthCoordinator.onMotionObservedDuringNegotiation()
                }
        }

        val loadedConfig = AiConfig(
            provider = normalizeAiProvider(prefs.getString("ai_provider", "gemini") ?: "gemini"),
            apiKey = prefs.getString("ai_api_key", "") ?: "",
            endpoint = prefs.getString("ai_base_url", "") ?: "",
            modelName = prefs.getString("ai_model_name", "") ?: ""
        )
        _aiConfig.value = loadedConfig
        // Push to diagnostic engine on startup
        if (loadedConfig.apiKey.isNotBlank() || loadedConfig.provider == "ollama") {
            val resolvedEp = resolveAiEndpoint(loadedConfig.provider, loadedConfig.endpoint)
            geminiDiagnostic.updateConfig(loadedConfig.apiKey, resolvedEp, loadedConfig.provider, loadedConfig.modelName)
        }

        // Load terminal command history
        val savedHistory = prefs.getString("terminal_command_history", "") ?: ""
        _commandHistory.value = if (savedHistory.isBlank()) emptyList() else savedHistory.split(",")

        // Load units and fuel configurations
        _useImperialUnits.value = prefs.getBoolean("pref_imperial_units", false)
        _fuelPrice.value = prefs.getFloat("pref_fuel_price", 1.25f)
        _currencySymbol.value = prefs.getString("pref_currency_symbol", "$") ?: "$"
        _fuelType.value = prefs.getString("pref_fuel_type", "GASOLINE") ?: "GASOLINE"

        // Purge old trips (older than 90 days)
        purgeOldTrips()
    }

    // --- Settings Actions ---

    fun addTerminalCommand(cmd: String) {
        val trimmed = cmd.trim().uppercase()
        if (trimmed.isBlank()) return
        val current = _commandHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 30) {
            current.removeAt(current.size - 1)
        }
        _commandHistory.value = current
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("terminal_command_history", current.joinToString(","))
            .apply()
    }

    fun addTerminalLog(text: String, type: TerminalLineType) {
        _terminalSessionLogs.update { it + TerminalLine(text, type) }
    }

    fun addTerminalLogs(lines: List<TerminalLine>) {
        _terminalSessionLogs.update { it + lines }
    }

    fun clearTerminalLogs() {
        _terminalSessionLogs.value = listOf(
            TerminalLine("Terminal limpiada.", TerminalLineType.SYSTEM)
        )
    }

    /** Toggle Force Clone Mode — treats any adapter as a clone for compatibility testing */
    fun setForceCloneMode(enabled: Boolean) {
        _forceCloneMode.value = enabled
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("force_clone_mode", enabled).apply()
        Log.d("ObdVM", "Force Clone Mode: $enabled")
    }

    /** Save AI configuration and push to diagnostic engine */
    fun saveAiConfig(provider: String, apiKey: String, endpoint: String, modelName: String) {
        val normalizedProvider = normalizeAiProvider(provider)
        val config = AiConfig(normalizedProvider, apiKey.trim(), endpoint.trim(), modelName.trim())
        _aiConfig.value = config

        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit()
        prefs.putString("ai_provider", config.provider)
        prefs.putString("ai_api_key", config.apiKey)
        prefs.putString("ai_base_url", config.endpoint)
        prefs.putString("ai_model_name", config.modelName)
        prefs.apply()

        // Push config to the diagnostic engine immediately
        val resolvedEndpoint = resolveAiEndpoint(config.provider, config.endpoint)
        geminiDiagnostic.updateConfig(config.apiKey, resolvedEndpoint, config.provider, config.modelName)
        Log.d("ObdVM", "AI Config saved: provider=${config.provider}, model=${config.modelName.ifBlank { "default" }}")
    }

    /** Resolve endpoint URL based on provider selection */
    private fun resolveAiEndpoint(provider: String, customEndpoint: String): String? {
        return when (normalizeAiProvider(provider)) {
            "gemini" -> null // use default Gemini endpoint inside GeminiDiagnostic
            "openai" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.openai.com/v1/chat/completions"
            "anthropic" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.anthropic.com/v1/messages"
            "ollama" -> if (customEndpoint.isNotBlank()) customEndpoint else "http://localhost:11434/v1/chat/completions"
            "mavis", "custom" -> customEndpoint.ifBlank { null }
            else -> null
        }
    }

    private fun normalizeAiProvider(provider: String): String {
        val clean = provider.trim().lowercase()
        return when {
            clean.contains("gemini") -> "gemini"
            clean.contains("openai") || clean.contains("gpt") -> "openai"
            clean.contains("anthropic") || clean.contains("claude") -> "anthropic"
            clean.contains("ollama") || clean.contains("local") -> "ollama"
            clean.contains("mavis") -> "mavis"
            clean.contains("custom") -> "custom"
            clean.isBlank() -> "gemini"
            else -> clean
        }
    }

    // --- Actions ---

    /** Connect to an OBD2 adapter by MAC address or IP under explicit user intent */
    fun connect(address: String) {
        val isDemoAddress = address == "SIMULATOR"
        lastAdapterAddress = address
        context.getSharedPreferences("elysium_obd_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_adapter_address", address)
            .apply()
        if (!isDemoAddress) beginUnboundPhysicalSession(address)
        hasCompletedInitialDtcScan = false

        val previousJob = connectionJob
        if (previousJob != null) {
            previousJob.cancel()
            obdSession.cancelActiveConnectionAttempt()
        }
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                previousJob?.join()
                obdSession.setVehicleVinHint(_selectedVehicle.value?.vin)
                obdSession.beginUserConnectionAttempt(address)
                obdSession.setTargetAddressSequentially(address)
                obdSession.connect()
                if (obdSession.state.value == ObdState.CONNECTED) {
                    if (isDemoAddress) {
                        addTerminalLog(
                            "[DEMO] Simulación activa. No se persistirá historial ni se afirmará conexión física.",
                            TerminalLineType.WARNING,
                        )
                        obdSession.startLivePolling()
                    } else {
                        runPostConnectDtcFirstStartup()
                        startForegroundService(_selectedVehicle.value?.id ?: "active_vehicle", address)
                    }
                }
            } catch (e: Exception) {
                Log.w("ObdVM", "Connection attempt ended or cancelled: ${e.message}")
            }
        }
    }

    private suspend fun runPostConnectDtcFirstStartup() {
        if (obdSession.state.value != ObdState.CONNECTED) return
        runCatching {
            val detectedVin = obdSession.readVinFromVehicle().vin ?: return@runCatching
            _vin.value = detectedVin
            detectManufacturer(detectedVin)
            bindVehicleFromObservedVin(detectedVin)
            obdSession.readOdometer()?.let { _currentOdometer.value = it }
        }.onFailure { e ->
            android.util.Log.e("ObdVM", "Vehicle identity binding failed", e)
            addTerminalLog(
                "[IDENTIDAD] No se pudo vincular la sesión física. El scan será efímero y no persistirá evidencia.",
                TerminalLineType.WARNING,
            )
        }
        ensureDtcScanBeforeAction(force = true)
        obdSession.startLivePolling()
    }

    private fun beginUnboundPhysicalSession(address: String) {
        currentSessionId = UUID.randomUUID().toString()
        val physicalConnectionId = sha256Hex(address.toByteArray(Charsets.UTF_8)).take(24)
        _vehicleSessionBinding.value = VehicleSessionBinding.unbound(
            diagnosticSessionId = currentSessionId,
            physicalConnectionId = physicalConnectionId,
        )
        _latestDiagnosticScan.value = null
    }

    private suspend fun bindVehicleFromObservedVin(rawVin: String?): Vehicle? {
        val cleanVin = rawVin?.let(::normalizeVin)
        if (cleanVin == null) {
            addTerminalLog(
                "[IDENTIDAD] VIN no disponible: sesión UNBOUND. Requiere confirmación explícita antes de persistir o activar hardware.",
                TerminalLineType.WARNING,
            )
            return null
        }
        if (_vehicleSessionBinding.value.bindingState == VehicleSessionBindingState.UNBOUND) {
            _vehicleSessionBinding.value = _vehicleSessionBinding.value.observeVin(
                observedVin = cleanVin,
                evidenceId = "ecu-vin:${sha256Hex(cleanVin.toByteArray()).take(16)}",
            )
        }
        // The physical ECU is authoritative for the connected car. A stale
        // Garage selection is replaced by the owner-scoped VIN identity below;
        // it must never receive this car's scans or DTC history.
        val vehicle = autoSelectVehicleFromVin(cleanVin) ?: return null
        _vehicleSessionBinding.value = _vehicleSessionBinding.value.bindVerifiedVin(
            vehicleId = vehicle.id,
            observedVin = cleanVin,
            expectedVin = vehicle.vin,
            evidenceId = "ecu-vin:${sha256Hex(cleanVin.toByteArray()).take(16)}",
        )
        addTerminalLog(
            "[IDENTIDAD] Sesión vinculada por VIN verificado a ${vehicle.make} ${vehicle.model}.",
            TerminalLineType.SYSTEM,
        )
        return vehicle
    }

    fun stopSession() {
        connectionJob?.cancel()
        connectionJob = null
        obdSession.requestUserDisconnect()
        viewModelScope.launch {
            voiceFeedbackManager.speak("Sesión de diagnóstico finalizada. Guardando resultados.", "Diagnostic session ended. Saving results.")
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
            clearState()
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        obdSession.requestUserDisconnect()
        viewModelScope.launch {
            voiceFeedbackManager.speak("Desconectando del vehículo. Guardando datos de sesión.", "Disconnecting from vehicle. Saving session data.")
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        }
    }

    private fun projectObdOperation(state: ObdState) {
        val operationId = "obd-session"
        when (state) {
            ObdState.CONNECTING, ObdState.NEGOTIATING -> {
                if (activeOperationsRegistry.operations.value[operationId] == null) {
                    val now = System.currentTimeMillis()
                    activeOperationsRegistry.upsert(
                        ActiveOperation(
                            operationId = operationId,
                            type = "OBD_SESSION",
                            vehicleId = selectedVehicle.value?.id,
                            startedAtEpochMs = now,
                            state = OperationState.STARTING,
                            progress = null,
                            owner = OperationOwner.FOREGROUND_SERVICE_SCOPED,
                            recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                            lastHeartbeatEpochMs = now,
                        ),
                    )
                } else {
                    activeOperationsRegistry.heartbeat(operationId, OperationState.STARTING)
                }
            }
            ObdState.CONNECTED -> {
                if (activeOperationsRegistry.operations.value[operationId] == null) {
                    val now = System.currentTimeMillis()
                    activeOperationsRegistry.upsert(
                        ActiveOperation(
                            operationId = operationId,
                            type = "OBD_SESSION",
                            vehicleId = selectedVehicle.value?.id,
                            startedAtEpochMs = now,
                            state = OperationState.RUNNING,
                            progress = null,
                            owner = OperationOwner.FOREGROUND_SERVICE_SCOPED,
                            recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                            lastHeartbeatEpochMs = now,
                        ),
                    )
                } else {
                    activeOperationsRegistry.heartbeat(operationId, OperationState.RUNNING)
                }
            }
            ObdState.ERROR -> activeOperationsRegistry.heartbeat(
                operationId,
                OperationState.FAILED,
                errorCode = "OBD_CONNECTION_ERROR",
            )
            ObdState.DISCONNECTED -> activeOperationsRegistry.complete(operationId)
        }
    }

    fun cancelConnection() {
        val jobToCancel = connectionJob
        obdSession.cancelActiveConnectionAttempt()
        connectionJob?.cancel()
        connectionJob = null
        viewModelScope.launch(Dispatchers.IO) {
            jobToCancel?.join()
            voiceFeedbackManager.speak("Conexión cancelada.", "Connection cancelled.")
            obdSession.disconnectSequentially()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        }
    }

    fun forceResetConnection() {
        connectionJob?.cancel()
        connectionJob = null
        obdSession.requestUserDisconnect()
        viewModelScope.launch {
            voiceFeedbackManager.speak("Reiniciando interfaz de conexión y liberando puertos.", "Resetting connection interface and releasing ports.")
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        }
    }

    fun resetTrip() {
        obdSession.resetTrip()
    }

    fun getSpeedHistory(): List<Float> = tripManager.getSpeedHistory()
    fun getRpmHistory(): List<Float> = tripManager.getRpmHistory()
    fun getThrottleHistory(): List<Float> = tripManager.getThrottleHistory()

    // --- Mode 06 Noncontinuous Monitors ---
    val mode06Results = obdSession.mode06Results
    private val _isReadingMode06 = MutableStateFlow(false)
    val isReadingMode06: StateFlow<Boolean> = _isReadingMode06.asStateFlow()

    fun readMode06() {
        viewModelScope.launch(Dispatchers.IO) {
            _isReadingMode06.value = true
            try {
                obdSession.readMode06Results()
            } catch (e: Exception) {
                Log.e("ObdVM", "Mode 06 failed", e)
            } finally {
                _isReadingMode06.value = false
            }
        }
    }

    // --- Driving/Standing Time ---
    val drivingTimeSeconds = obdSession.drivingTimeSeconds
    val standingTimeSeconds = obdSession.standingTimeSeconds

    fun resetDrivingTime() {
        obdSession.resetDrivingTime()
    }

    // ═══════════════════════════════════════════════
    // MODE $05 — O2 SENSOR MONITORING TEST RESULTS
    // ═══════════════════════════════════════════════

    val o2SensorTests = obdSession.o2SensorTests
    val isReadingO2Tests = obdSession.isReadingO2Tests

    fun readO2SensorTests() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                obdSession.readO2SensorTests()
            } catch (e: Exception) {
                Log.e("ObdVM", "Mode 05 O2 Tests failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // CATEGORIZED DTCs ($03 / $07 / $0A)
    // ═══════════════════════════════════════════════

    private val _categorizedDtcs = MutableStateFlow(CategorizedDtcs())
    val categorizedDtcs: StateFlow<CategorizedDtcs> = _categorizedDtcs.asStateFlow()

    fun readCategorizedDtcs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshDiagnostics(mode = DiagnosticScanMode.FULL_VEHICLE)
                val report = lastDtcScanReport.value ?: return@launch
                fun descriptions(bucket: DtcBucket): List<Pair<String, String>> = report.records
                    .filter { it.bucket == bucket }
                    .distinctBy { record ->
                        listOf(
                            record.namespace.name,
                            DiagnosticModuleIdentity.canonical(
                                record.targetAddress,
                                record.responseAddress,
                                record.moduleName,
                            ),
                            record.codeIdentity.stableRawIdentity,
                        ).joinToString("|")
                    }
                    .map { record ->
                        record.code to (runCatching { DtcDecoder.getLocalDescription(record.code) }.getOrNull()
                            ?: "Definición pendiente de validación")
                    }
                _categorizedDtcs.value = CategorizedDtcs(
                    confirmed = descriptions(DtcBucket.ACTIVE),
                    pending = descriptions(DtcBucket.PENDING),
                    permanent = descriptions(DtcBucket.PERMANENT),
                )
            } catch (e: Exception) {
                Log.e("ObdVM", "Categorized DTCs failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // MODE $09 — EXTENDED VEHICLE INFORMATION
    // ═══════════════════════════════════════════════

    private val _vehicleInfoExtended = MutableStateFlow<Map<String, String>>(emptyMap())
    val vehicleInfoExtended: StateFlow<Map<String, String>> = _vehicleInfoExtended.asStateFlow()

    fun readExtendedVehicleInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _vehicleInfoExtended.value = obdSession.readAllVehicleInfo()
            } catch (e: Exception) {
                Log.e("ObdVM", "Extended vehicle info failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // UDS SERVICES (via UdsProtocolManager)
    // ═══════════════════════════════════════════════

    private val _udsCapabilities = MutableStateFlow<UdsCapabilities?>(null)
    val udsCapabilities: StateFlow<UdsCapabilities?> = _udsCapabilities.asStateFlow()

    private val _ecuInfo = MutableStateFlow<List<UdsReadResult>>(emptyList())
    val ecuInfo: StateFlow<List<UdsReadResult>> = _ecuInfo.asStateFlow()

    private val _lastUdsOperation = MutableStateFlow("")
    val lastUdsOperation: StateFlow<String> = _lastUdsOperation.asStateFlow()

    fun discoverUdsCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Descubriendo capacidades UDS..."
                _udsCapabilities.value = udsProtocolManager.discoverCapabilities()
                _lastUdsOperation.value = "Capacidades UDS descubiertas."
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS discovery failed", e)
                _lastUdsOperation.value = "Error: ${e.message}"
            }
        }
    }

    fun readEcuInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo información ECU (UDS \$22)..."
                _ecuInfo.value = udsProtocolManager.readEcuInfo()
                _lastUdsOperation.value = "${_ecuInfo.value.size} DIDs leídos."
            } catch (e: Exception) {
                Log.e("ObdVM", "ECU Info read failed", e)
            }
        }
    }

    fun resetEcu(resetType: String = "03") {
        Log.w("ObdVM", "Blocked generic ECU reset type=$resetType")
        _lastUdsOperation.value =
            "ECU Reset bloqueado: requiere capability pack revisado, ECU objetivo y precondiciones verificadas."
    }

    fun clearDtcUds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Preparando borrado UDS con evidencia y verificación post-borrado..."
                val result = clearDtcs()
                _lastUdsOperation.value = result.message
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS Clear DTC failed", e)
                _lastUdsOperation.value = "Borrado UDS inconcluso: ${e.message ?: "error no identificado"}."
            }
        }
    }

    fun readDtcUds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo DTCs UDS mediante adquisición canónica..."
                refreshDiagnostics(mode = DiagnosticScanMode.FULL_VEHICLE)
                val count = lastDtcScanReport.value?.records.orEmpty()
                    .count { it.namespace == DiagnosticNamespace.UDS }
                _lastUdsOperation.value = "$count hallazgos UDS adquiridos con evidencia canónica."
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS Read DTC failed", e)
            }
        }
    }

    fun readDataByIdentifier(did: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo DID \$$did..."
                val result = udsProtocolManager.readDataByIdentifier(did)
                _lastUdsOperation.value = if (result != null) "DID \$$did: $result" else "DID \$$did no disponible."
            } catch (e: Exception) {
                Log.e("ObdVM", "Read DID failed", e)
            }
        }
    }

    fun executeRoutine(routineId: String, params: String = "") {
        Log.w("ObdVM", "Blocked unsourced UDS routine id=$routineId paramsLength=${params.length}")
        _lastUdsOperation.value =
            "Rutina bloqueada: requiere paquete OEM revisado, vehículo aplicable, ECU objetivo y precondiciones verificadas."
    }

    // ═══════════════════════════════════════════════
    // MANUFACTURER-SPECIFIC MODES ($B0-$BF, $D0-$DF, $EA-$FF)
    // ═══════════════════════════════════════════════

    private val _manufacturerModes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val manufacturerModes: StateFlow<Map<String, Boolean>> = _manufacturerModes.asStateFlow()

    fun probeManufacturerModes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Probando modos del fabricante..."
                _manufacturerModes.value = obdSession.probeManufacturerModes()
                val supportedCount = _manufacturerModes.value.count { it.value }
                _lastUdsOperation.value = "$supportedCount modos del fabricante detectados."
            } catch (e: Exception) {
                Log.e("ObdVM", "Manufacturer probe failed", e)
            }
        }
    }

    fun sendManufacturerCommand(sid: String, sub: String = ""): StateFlow<String?> {
        val result = MutableStateFlow<String?>(null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                result.value = obdSession.sendManufacturerCommand(sid, sub)
            } catch (e: Exception) {
                Log.e("ObdVM", "Manufacturer command failed", e)
            }
        }
        return result.asStateFlow()
    }

    // ═══════════════════════════════════════════════
    // POST-SCAN PDF REPORT
    // ═══════════════════════════════════════════════

    private val _lastScanReportPath = MutableStateFlow<String?>(null)
    val lastScanReportPath: StateFlow<String?> = _lastScanReportPath.asStateFlow()


    private suspend fun saveSessionResults() {
        if (connectionTruth.value.isDemoSession) {
            addTerminalLog(
                "[DEMO] Historial y sesión persistente omitidos: los datos simulados nunca se atribuyen a un vehículo real.",
                TerminalLineType.WARNING,
            )
            return
        }
        val vehicle = _selectedVehicle.value ?: return
        val currentDtcs = activeDtcs.value
        val snapshot = _liveData.value

        val session = DiagnosticSession(
            id = UUID.randomUUID().toString(),
            user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
            vehicle_vin = vehicle.vin,
            vehicle_make = vehicle.make,
            vehicle_model = vehicle.model,
            vehicle_year = vehicle.year,
            dtcs_found = Json.encodeToString(currentDtcs),
            severity = when {
                permanentDtcs.value.isNotEmpty() -> "critical"
                currentDtcs.isNotEmpty() -> "high"
                pendingDtcs.value.isNotEmpty() -> "moderate"
                else -> "low"
            },
            live_data_snapshot = Json.encodeToString(snapshot.mapValues { it.value.toString() })
        )

        sessionLogRepository.saveSession(session, vehicle.id)
    }

    private fun clearState() {
        // DTCs are now persistent in DB, we don't clear them here manually
        // they will reload based on _selectedVehicle change
        _readinessMonitors.value = null
        _liveData.value = emptyMap()
        _healthScore.value = 0
        _qosMetrics.value = QosMetrics(
            cmdsPerSecond = 0f,
            latencyMs = 0,
            isStable = false,
            avgLatencyMs = 0f,
            reliability = 0f,
            totalRequests = 0,
            successfulRequests = 0,
        )
        _anomalousPids.value = emptyList()
        predictiveEstimator.reset()
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            _isDeletingVehicle.value = true
            try {
                when (val result = vehicleRepository.deleteVehicle(vehicle)) {
                    is com.elysium369.meet.core.remote.RemoteResult.Success -> {
                        if (_selectedVehicle.value?.id == vehicle.id) {
                            activeVehicleKernel.clearIfDeleted(vehicle)
                        }
                    }
                    is com.elysium369.meet.core.remote.RemoteResult.Unauthorized ->
                        Log.w("ObdVM", "Delete vehicle: auth expired")
                    else ->
                        Log.w("ObdVM", "Delete vehicle failed: $result")
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Error deleting vehicle", e)
            } finally {
                kotlinx.coroutines.delay(800)
                _isDeletingVehicle.value = false
            }
        }
    }

    private val _isReadingVin = MutableStateFlow<String?>(null)
    val isReadingVin: StateFlow<String?> = _isReadingVin.asStateFlow()

    private val _vinReadFeedback = MutableStateFlow<String?>(null)
    val vinReadFeedback: StateFlow<String?> = _vinReadFeedback.asStateFlow()

    fun clearVinFeedback() {
        _vinReadFeedback.value = null
    }

    fun readAndSaveVinFromEcu(vehicle: Vehicle) {
        viewModelScope.launch {
            if (_selectedVehicle.value?.id != vehicle.id) {
                _vinReadFeedback.value = "⚠️ Activa primero este vehículo en Garage. La lectura física no se asociará a una tarjeta inactiva."
                return@launch
            }
            if (obdSession.state.value != ObdState.CONNECTED) {
                _vinReadFeedback.value = "⚠️ El escáner OBD-II no está conectado. Conéctalo al vehículo para leer el VIN de la ECU."
                return@launch
            }
            _isReadingVin.value = vehicle.id
            _vinReadFeedback.value = "🔍 Consultando ECU de ${vehicle.make} ${vehicle.model} (Modo 09 / UDS / KWP)..."
            try {
                val vinResult = obdSession.readVinFromVehicle()
                val detectedVin = vinResult.vin
                if (vinResult.isVerified && detectedVin != null) {
                    val existingVin = VinValidator.normalize(vehicle.vin)
                    if (existingVin != null && existingVin != detectedVin) {
                        _vinReadFeedback.value = "⚠️ Conflicto de identidad: la ECU reportó $detectedVin, pero este vehículo conserva $existingVin. No se sobrescribió el Garage."
                        return@launch
                    }
                    val updated = vehicle.copy(vin = detectedVin)
                    when (val result = vehicleRepository.insertVehicle(updated)) {
                        is com.elysium369.meet.core.remote.RemoteResult.Success -> {
                            if (_selectedVehicle.value?.id == vehicle.id) {
                                activeVehicleKernel.updateIfActive(updated)
                            }
                            val route = listOfNotNull(vinResult.header, vinResult.command).joinToString(" → ")
                            _vinReadFeedback.value = "✓ VIN $detectedVin verificado físicamente por $route (${vinResult.protocol ?: "protocolo OBD detectado"}) y guardado."
                        }
                        else -> {
                            _vinReadFeedback.value = "⚠️ VIN guardado localmente; sincronización remota pendiente ($result)"
                            if (_selectedVehicle.value?.id == vehicle.id) {
                                activeVehicleKernel.updateIfActive(updated)
                            }
                        }
                    }
                } else {
                    _vinReadFeedback.value = when (vinResult.outcome) {
                        VinReadOutcome.NOT_CONNECTED -> "⚠️ El enlace OBD se desconectó antes de consultar la ECU."
                        VinReadOutcome.NO_RESPONSE -> "⚠️ Ninguna ECU respondió a las consultas VIN. Confirma contacto en ON y compatibilidad del adaptador."
                        VinReadOutcome.INVALID_RESPONSE -> "⚠️ La ECU respondió, pero no entregó un VIN ISO 3779 válido de 17 caracteres. No se guardó ningún valor."
                        VinReadOutcome.VERIFIED -> "Error de contrato: la prueba VIN fue marcada verificada sin un VIN válido."
                    }
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Error reading VIN from ECU", e)
                _vinReadFeedback.value = "Error al leer VIN de la ECU: ${e.message}"
            } finally {
                _isReadingVin.value = null
            }
        }
    }

    /**
     * Loads remembered adapter address in state without firing any background physical connection.
     */
    fun prepareRememberedAdapter() {
        val lastAddress = lastAdapterAddress?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("elysium_obd_prefs", Context.MODE_PRIVATE).getString("last_adapter_address", null)
        if (!lastAddress.isNullOrBlank()) {
            lastAdapterAddress = lastAddress
            Log.d("ObdVM", "Remembered adapter configured: $lastAddress (ready for explicit user connection)")
        }
    }

    fun retryLastAdapterByUserAction() {
        val address = lastAdapterAddress?.takeIf { it.isNotBlank() } ?: return
        connect(address)
    }

    fun copyRedactedConnectionTrace() {
        val trace = obdSession.healthCoordinator.getTrace().exportRedacted().joinToString("\n")
            .ifBlank { "Sin eventos de conexión registrados." }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Elysium OBD Connection Trace", trace))
    }

    fun getConnectionTrace(): List<String> {
        return obdSession.healthCoordinator.getTrace().exportRedacted()
    }

    private suspend fun ensureDtcScanBeforeAction(force: Boolean = false) {
        if (obdSession.state.value != ObdState.CONNECTED) return
        initialDtcScanMutex.withLock {
            if (!force && hasCompletedInitialDtcScan) return
            _cloudSyncState.value = "Pre-scan DTC obligatorio..."
            addTerminalLog("──── PRE-SCAN DTC OBLIGATORIO ANTES DE CUALQUIER ACCIÓN ────", TerminalLineType.SYSTEM)
            refreshDiagnostics(manageState = true)
            hasCompletedInitialDtcScan = true
            _cloudSyncState.value = ""
        }
    }

    fun cancelDiagnosticScan() {
        obdSession.cancelDiagnosticScan()
    }

    suspend fun refreshDiagnostics(
        manageState: Boolean = true,
        mode: DiagnosticScanMode = DiagnosticScanMode.FULL_VEHICLE,
    ) {
        if (manageState) _isScanning.value = true
        addTerminalLog(
            "──── INICIO ${if (mode == DiagnosticScanMode.QUICK) "QUICK SCAN" else "FULL VEHICLE SCAN"} ────",
            TerminalLineType.SYSTEM,
        )
        val truth = connectionTruth.value
        if (connectionState.value != ObdState.CONNECTED || !truth.isSessionReady || truth.isDemoSession) {
            addTerminalLog(
                "⚠ Se requiere adaptador y ECU físicos verificados para escanear DTCs. El modo demo no genera historial.",
                TerminalLineType.WARNING
            )
            voiceFeedbackManager.speak(
                "Conecta un vehículo real para escanear códigos de error.",
                "Connect to a real vehicle to scan fault codes."
            )
            if (manageState) _isScanning.value = false
            return
        }
        try {
            obdSession.pauseLivePolling()
            obdSession.clearCommandQueue()
            voiceFeedbackManager.speak("Iniciando escaneo de códigos de error.", "Starting fault code scan.")

            addTerminalLog(
                if (mode == DiagnosticScanMode.QUICK) {
                    "[SCAN] Lectura rápida: Mode 03/07/0A por broadcast funcional..."
                } else {
                    "[SCAN] Cobertura completa: Service 19/UDS + Mode 03/07/0A + módulos confirmados/candidatos..."
                },
                TerminalLineType.SYSTEM,
            )
            val professionalReport = runDiagnosticScan(mode)
            addProfessionalDtcReportLogs(professionalReport)

            val freshActive = professionalReport.codesForBucket(DtcBucket.ACTIVE)
            val freshPending = professionalReport.codesForBucket(DtcBucket.PENDING)
            val freshPermanent = professionalReport.codesForBucket(DtcBucket.PERMANENT)
            val freshHistory = professionalReport.codesForBucket(DtcBucket.HISTORY)

            updateLatestScanProjection(professionalReport)

            addTerminalLog(
                "[SCAN] Activos: ${freshActive.size} | Pendientes: ${freshPending.size} | Permanentes: ${freshPermanent.size} | Históricos: ${freshHistory.size}",
                TerminalLineType.SYSTEM
            )

            val vehicle = boundSelectedVehicleOrNull()
            if (vehicle != null) {
                val evidenceReferences = saveDiagnosticEvidence(professionalReport)
                saveDetectedDtcFindings(professionalReport, evidenceReferences)
            } else if ((freshActive + freshPending + freshPermanent + freshHistory).isNotEmpty()) {
                addTerminalLog(
                    "[SCAN] Resultado efímero UNBOUND/CONFLICTED: no se persistió ni se mezcló con ningún vehículo Garage.",
                    TerminalLineType.WARNING
                )
            }

            if (!professionalReport.wasCancelled) {
                // Mode 01 PID 01 → I/M Readiness Monitors
                addTerminalLog("[SCAN] Leyendo Monitores I/M (Mode 01 PID 01)...", TerminalLineType.SYSTEM)
                _readinessMonitors.value = obdSession.readReadinessMonitors()
            } else {
                addTerminalLog(
                    "[SCAN] Detenido por usuario; evidencia parcial preservada (${professionalReport.modules.count { it.serviceReads.isNotEmpty() }} módulos).",
                    TerminalLineType.WARNING,
                )
            }

            // Fetch definitions for all discovered DTCs from local DB
            val allCodes = (freshActive + freshPending + freshPermanent + freshHistory).distinct()
            fetchDtcDefinitions(allCodes)

            val total = allCodes.size
            if (total == 0) {
                when (professionalReport.completeness) {
                    com.elysium369.meet.core.obd.ScanCompleteness.COMPLETE ->
                        voiceFeedbackManager.speak(
                            "Escaneo completado. Los módulos cubiertos no reportaron códigos. Esto no descarta fallas no monitorizadas.",
                            "Scan complete. Covered modules reported no codes. This does not rule out unmonitored faults.",
                        )
                    com.elysium369.meet.core.obd.ScanCompleteness.PARTIAL ->
                        voiceFeedbackManager.speak(
                            "Escaneo parcial. Los módulos que respondieron no reportaron códigos, pero quedan módulos sin verificar.",
                            "Partial scan. Responding modules reported no codes, but some modules remain unverified.",
                        )
                    else -> voiceFeedbackManager.speak(
                        "Escaneo no concluyente. No hay evidencia suficiente para afirmar ausencia de códigos.",
                        "Inconclusive scan. There is not enough evidence to claim no fault codes.",
                    )
                }
            } else if (total == 1) {
                voiceFeedbackManager.speak("Escaneo completado. Se detectó un código de error en el sistema.", "Scan complete. One fault code detected in the system.")
            } else {
                voiceFeedbackManager.speak("Escaneo completado. Se detectaron $total códigos de error en el sistema.", "Scan complete. $total fault codes detected in the system.")
            }
            updateHealthScore()
            com.elysium369.meet.core.notification.ElysiumNotificationHub.showScanCompletedPopUp(context, total)
            addTerminalLog(
                "──── ESCANEO ${professionalReport.completeness.name} — $total códigos en total ────",
                if (professionalReport.completeness == com.elysium369.meet.core.obd.ScanCompleteness.COMPLETE) {
                    TerminalLineType.SYSTEM
                } else {
                    TerminalLineType.WARNING
                },
            )
        } catch (e: Exception) {
            android.util.Log.e("ObdVM", "Failed to refresh diagnostics", e)
            addTerminalLog("✗ Error en escaneo: ${e.message}", TerminalLineType.ERROR)
        } finally {
            obdSession.resumeLivePolling()
            if (manageState) _isScanning.value = false
        }
    }

    private fun addProfessionalDtcReportLogs(report: DtcScanReport) {
        val moduleLines = report.modules
            .take(12)
            .map { module ->
                val codes = module.dtcs.map { it.code }.distinct().joinToString(", ").ifBlank {
                    if (module.outcome == com.elysium369.meet.core.obd.ModuleScanOutcome.NO_DTC) {
                        "sin DTC en lectura completa"
                    } else {
                        "sin hallazgo verificable"
                    }
                }
                TerminalLine(
                    "[MOD] ${module.moduleName} ${module.responseAddress ?: module.targetAddress ?: ""} " +
                        "[${module.outcome}] -> $codes",
                    TerminalLineType.DECODED
                )
            }
        if (moduleLines.isNotEmpty()) addTerminalLogs(moduleLines)

        val detailedLines = report.records.take(20).map { record ->
            TerminalLine(
                "[DTC] ${record.code} ${record.bucket} ${record.statusFlags.joinToString("|")} " +
                    "mod=${record.moduleName ?: record.responseAddress ?: record.targetAddress ?: "-"} " +
                    "svc=${record.sourceService}",
                TerminalLineType.DECODED
            )
        }
        if (detailedLines.isNotEmpty()) addTerminalLogs(detailedLines)
    }

    private fun updateLatestScanProjection(report: DtcScanReport) {
        val binding = _vehicleSessionBinding.value
        _latestDiagnosticScan.value = LatestDiagnosticScanProjection(
            scanId = UUID.randomUUID().toString(),
            sessionId = currentSessionId,
            vehicleBindingId = binding.bindingId,
            vehicleId = binding.vehicleId.takeIf { binding.allowsPersistence },
            findings = report.records,
            completeness = report.completeness,
            capturedAt = System.currentTimeMillis(),
        )
    }

    private fun boundSelectedVehicleOrNull(): Vehicle? {
        val binding = _vehicleSessionBinding.value
        val selected = _selectedVehicle.value
        return selected?.takeIf {
            binding.allowsPersistence && binding.vehicleId == it.id
        }
    }

    private fun requireBoundVehicleForCriticalOperation(): Vehicle {
        return boundSelectedVehicleOrNull() ?: throw IllegalStateException(
            when (_vehicleSessionBinding.value.bindingState) {
                VehicleSessionBindingState.CONFLICTED ->
                    "VIN en conflicto: selecciona el vehículo físico correcto antes de continuar."
                VehicleSessionBindingState.UNBOUND ->
                    "Sesión sin vínculo de identidad: verifica VIN o confirma el vehículo explícitamente."
                else -> "El vehículo seleccionado no pertenece a esta conexión física."
            }
        )
    }

    private data class PersistedExchangeReference(
        val exchangeId: String,
        val targetAddress: String?,
        val responseAddress: String?,
        val service: String,
        val rawResponseHash: String,
        val outcome: ModuleScanOutcome,
        val requestScope: String,
    )

    private fun List<DiagnosticObservationEntity>.observedSemanticsFor(
        namespace: DiagnosticNamespace,
    ): Set<DiagnosticSemantic> = asSequence()
        .filter { it.observationState == "OBSERVED" }
        .flatMap { it.semantics.split('|').asSequence() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { raw -> runCatching { DiagnosticSemantic.valueOf(raw) }.getOrNull() }
        .filter { semantic ->
            when (namespace) {
                DiagnosticNamespace.SAE_OBD -> semantic.name.startsWith("SAE_")
                DiagnosticNamespace.UDS -> semantic.name.startsWith("UDS_")
                DiagnosticNamespace.KWP2000,
                DiagnosticNamespace.OEM -> true
            }
        }
        .toSet()

    private fun List<DiagnosticObservationEntity>.latestObservedSourceService(): String =
        asSequence()
            .filter { it.observationState == "OBSERVED" && it.sourceService.isNotBlank() }
            .maxWithOrNull(
                compareBy<DiagnosticObservationEntity> { it.observedAt }
                    .thenBy { it.sessionSequence }
                    .thenBy { it.id },
            )
            ?.sourceService
            .orEmpty()

    private fun Set<DiagnosticSemantic>.primaryBucketFor(
        namespace: DiagnosticNamespace,
    ): DtcBucket? = when (namespace) {
        DiagnosticNamespace.SAE_OBD -> when {
            DiagnosticSemantic.SAE_ACTIVE_DTC in this -> DtcBucket.ACTIVE
            DiagnosticSemantic.SAE_PENDING_DTC in this -> DtcBucket.PENDING
            DiagnosticSemantic.SAE_PERMANENT_DTC in this -> DtcBucket.PERMANENT
            else -> null
        }
        DiagnosticNamespace.UDS,
        DiagnosticNamespace.KWP2000,
        DiagnosticNamespace.OEM -> when {
            DiagnosticSemantic.UDS_TEST_FAILED in this -> DtcBucket.ACTIVE
            DiagnosticSemantic.UDS_PENDING in this -> DtcBucket.PENDING
            DiagnosticSemantic.UDS_CONFIRMED in this ||
                DiagnosticSemantic.UDS_FAILED_SINCE_CLEAR in this -> DtcBucket.HISTORY
            else -> null
        }
    }

    private suspend fun saveDiagnosticEvidence(report: DtcScanReport): List<PersistedExchangeReference> =
        diagnosticEvidencePersistenceMutex.withLock {
        requireBoundVehicleForCriticalOperation()
        if (report.rawExchanges.isEmpty()) return@withLock emptyList()
        val transport = when {
            report.protocol.contains("DOIP", ignoreCase = true) -> DiagnosticTransport.DOIP
            report.protocol.contains("CAN", ignoreCase = true) || report.protocol.contains("15765") -> DiagnosticTransport.CAN
            report.protocol.contains("KWP", ignoreCase = true) || report.protocol.contains("9141") -> DiagnosticTransport.K_LINE
            else -> DiagnosticTransport.UNKNOWN
        }
        val startingSequence = diagnosticEvidenceDao.maxExchangeSequence(currentSessionId)
        var previousHash = diagnosticEvidenceDao.latestExchangeHash(currentSessionId).orEmpty()
        val orderedExchanges = report.rawExchanges.withIndex().sortedWith(
            compareBy<IndexedValue<com.elysium369.meet.core.obd.DtcRawExchange>> { it.value.timestampMs }
                .thenBy { it.index },
        )
        val exchangesWithReferences = orderedExchanges.mapIndexed { offset, indexedExchange ->
            val exchange = indexedExchange.value
            val applicationProtocol = if (exchange.command.startsWith("19") || exchange.command.startsWith("14")) {
                DiagnosticApplicationProtocol.UDS
            } else {
                DiagnosticApplicationProtocol.SAE_OBD
            }
            val exchangeId = UUID.randomUUID().toString()
            val sequence = startingSequence + offset + 1L
            val elapsedRealtimeNanos = System.nanoTime()
            val rawRequestHash = sha256Hex(exchange.command.toByteArray(Charsets.UTF_8))
            val rawResponseHash = sha256Hex(exchange.rawResponse.toByteArray(Charsets.UTF_8))
            val persistedRequestScope = when (exchange.requestScope) {
                is DiagnosticRequestScope.Functional -> "FUNCTIONAL"
                is DiagnosticRequestScope.Physical -> "PHYSICAL"
                is DiagnosticRequestScope.Logical -> "LOGICAL"
                DiagnosticRequestScope.LegacyUnaddressed -> "LEGACY_UNADDRESSED"
            }
            val retentionClass = com.elysium369.meet.core.vanguard.DiagnosticRetentionClass.RAW_FORENSIC.name
            val encryptedBlob = DiagnosticEvidenceVault.encrypt(
                sessionId = currentSessionId,
                exchangeId = exchangeId,
                rawRequest = exchange.command,
                rawResponse = exchange.rawResponse,
                requestHash = rawRequestHash,
                responseHash = rawResponseHash,
                createdAtMs = exchange.timestampMs,
                retentionClass = retentionClass,
            )
            val draft = DiagnosticExchangeEntity(
                id = exchangeId,
                sessionId = currentSessionId,
                timestampMs = exchange.timestampMs,
                transport = exchange.transport.takeUnless { it == DiagnosticTransport.UNKNOWN }?.name
                    ?: transport.name,
                applicationProtocol = applicationProtocol.name,
                requestScope = persistedRequestScope,
                requestAddress = exchange.targetAddress,
                responseAddress = exchange.responseAddress,
                service = exchange.command,
                rawRequest = "",
                rawResponse = "",
                decodedOutcome = exchange.outcome.name,
                latencyMs = exchange.latencyMs,
                retryCount = exchange.retryCount,
                negativeResponseCode = exchange.negativeResponse?.responseCode,
                adapterConfiguration = report.protocol,
                parserVersion = exchange.parserVersion,
                sessionSequence = sequence,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                rawRequestHash = rawRequestHash,
                rawResponseHash = rawResponseHash,
                previousExchangeHash = previousHash,
                exchangeHash = "",
                retentionClass = retentionClass,
                expiresAtMs = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceRetentionPolicy.expiresAtMs(
                    com.elysium369.meet.core.vanguard.DiagnosticRetentionClass.RAW_FORENSIC,
                    exchange.timestampMs,
                ),
                canonicalizationVersion = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.EXCHANGE_CHAIN_V2,
                rawPayloadBlobId = encryptedBlob.blobId,
            )
            val exchangeHash = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.exchangeHash(draft)
            val entity = draft.copy(exchangeHash = exchangeHash)
            previousHash = exchangeHash
            Triple(entity, encryptedBlob, PersistedExchangeReference(
                exchangeId = exchangeId,
                targetAddress = exchange.targetAddress,
                responseAddress = exchange.responseAddress,
                service = exchange.command,
                rawResponseHash = rawResponseHash,
                outcome = exchange.outcome,
                requestScope = persistedRequestScope,
            ))
        }
        val entities = exchangesWithReferences.map { it.first }
        val scanId = UUID.randomUUID().toString()
        val parserVersion = entities.map { it.parserVersion }.distinct().joinToString("+")
        val merkleRoot = merkleRootSha256(entities.map { it.exchangeHash })
        val finalizedAt = System.currentTimeMillis()
        val bindingId = _vehicleSessionBinding.value.bindingId
        val canonicalManifest = listOf(
            scanId,
            currentSessionId,
            bindingId,
            merkleRoot,
            "SHA-256",
            parserVersion,
            com.elysium369.meet.BuildConfig.VERSION_NAME,
            finalizedAt,
        ).joinToString("|")
        val signedManifest = DiagnosticManifestSigner.sign(canonicalManifest, finalizedAt).getOrNull()
        diagnosticEvidenceDao.appendEncryptedSession(
            blobs = exchangesWithReferences.map { it.second },
            exchanges = entities,
            integrity = DiagnosticSessionIntegrityEntity(
                scanId = scanId,
                sessionId = currentSessionId,
                parserVersion = parserVersion,
                firstSequence = entities.first().sessionSequence,
                lastSequence = entities.last().sessionSequence,
                leafCount = entities.size,
                merkleRoot = merkleRoot,
                finalizedAtMs = finalizedAt,
                vehicleBindingId = bindingId,
                appVersion = com.elysium369.meet.BuildConfig.VERSION_NAME,
                deviceKeyId = signedManifest?.keyId,
                signatureAlgorithm = signedManifest?.signatureAlgorithm,
                signatureBase64 = signedManifest?.signatureBase64,
                signedAtMs = signedManifest?.signedAtMs,
                trustState = if (signedManifest != null) "HARDWARE_SIGNED" else "UNSIGNED_HARDWARE_UNAVAILABLE",
                signerPublicKeyBase64 = signedManifest?.publicKeyBase64,
                certificateChainJson = signedManifest?.certificateChainBase64
                    ?.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]"),
                keySecurityLevel = signedManifest?.keySecurityLevel,
            ),
        )
        exchangesWithReferences.map { it.third }
    }

    private suspend fun appendDiagnosticObservation(observation: DiagnosticObservationEntity) =
        diagnosticEvidencePersistenceMutex.withLock {
        val sequence = diagnosticEvidenceDao.maxObservationSequence(observation.sessionId) + 1L
        val findingSequence = diagnosticEvidenceDao.maxFindingSequence(observation.findingId) + 1L
        val previousHash = diagnosticEvidenceDao.latestObservationHash(observation.findingId).orEmpty()
        val draft = observation.copy(
            sessionSequence = sequence,
            findingSequence = findingSequence,
            elapsedRealtimeNanos = System.nanoTime(),
            previousObservationHash = previousHash,
            canonicalizationVersion = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.OBSERVATION_CHAIN_V2,
        )
        val observationHash = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.observationHash(draft)
        diagnosticEvidenceDao.appendObservationWithExpectedPredecessor(
            observation = draft.copy(observationHash = observationHash),
            expectedSessionSequence = sequence,
            expectedFindingSequence = findingSequence,
            expectedPreviousHash = previousHash,
        )
    }

    private suspend fun appendStandaloneDiagnosticExchange(
        timestampMs: Long,
        transport: String,
        applicationProtocol: String,
        requestScope: String,
        requestAddress: String?,
        responseAddress: String?,
        service: String,
        rawRequest: String,
        rawResponse: String,
        decodedOutcome: String,
        adapterConfiguration: String,
        parserVersion: String,
    ): String = diagnosticEvidencePersistenceMutex.withLock {
        val sequence = diagnosticEvidenceDao.maxExchangeSequence(currentSessionId) + 1L
        val previousHash = diagnosticEvidenceDao.latestExchangeHash(currentSessionId).orEmpty()
        val requestHash = sha256Hex(rawRequest.toByteArray(Charsets.UTF_8))
        val responseHash = sha256Hex(rawResponse.toByteArray(Charsets.UTF_8))
        val exchangeId = UUID.randomUUID().toString()
        val retentionClass = com.elysium369.meet.core.vanguard.DiagnosticRetentionClass.RAW_FORENSIC.name
        val blob = DiagnosticEvidenceVault.encrypt(
            sessionId = currentSessionId,
            exchangeId = exchangeId,
            rawRequest = rawRequest,
            rawResponse = rawResponse,
            requestHash = requestHash,
            responseHash = responseHash,
            createdAtMs = timestampMs,
            retentionClass = retentionClass,
        )
        val draft = DiagnosticExchangeEntity(
                id = exchangeId,
                sessionId = currentSessionId,
                timestampMs = timestampMs,
                transport = transport,
                applicationProtocol = applicationProtocol,
                requestScope = requestScope,
                requestAddress = requestAddress,
                responseAddress = responseAddress,
                service = service,
                rawRequest = "",
                rawResponse = "",
                decodedOutcome = decodedOutcome,
                latencyMs = null,
                retryCount = 0,
                negativeResponseCode = null,
                adapterConfiguration = adapterConfiguration,
                parserVersion = parserVersion,
                sessionSequence = sequence,
                elapsedRealtimeNanos = System.nanoTime(),
                rawRequestHash = requestHash,
                rawResponseHash = responseHash,
                previousExchangeHash = previousHash,
                exchangeHash = "",
                retentionClass = retentionClass,
                expiresAtMs = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceRetentionPolicy.expiresAtMs(
                    com.elysium369.meet.core.vanguard.DiagnosticRetentionClass.RAW_FORENSIC,
                    timestampMs,
                ),
                canonicalizationVersion = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.EXCHANGE_CHAIN_V2,
                rawPayloadBlobId = blob.blobId,
            )
        val entity = draft.copy(
            exchangeHash = com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.exchangeHash(draft),
        )
        diagnosticEvidenceDao.appendEncryptedExchange(blob, entity)
        exchangeId
    }

    private suspend fun saveDetectedDtcFindings(
        report: DtcScanReport,
        evidenceReferences: List<PersistedExchangeReference> = emptyList(),
    ) {
        val vehicle = requireBoundVehicleForCriticalOperation()
        val now = System.currentTimeMillis()
        val records = report.records
        val distinctFindings = records.distinctBy { it.findingKey(vehicle.id) }
        val ownerPrincipalId = activePrincipalKernel.current().id
        val legacyCandidates = dtcDao.getUnresolvedDtcsList(vehicle.id, ownerPrincipalId)
            .filter { it.diagnosticNamespace.isBlank() || it.moduleIdentity.isBlank() }
            .toMutableList()

        distinctFindings.forEach { record ->
            val status = storageStatusForDtcRecord(record)
            val findingKey = record.findingKey(vehicle.id)
            val canonicalFinding = diagnosticFindingRepository.getIdentityByStableKey(
                vehicleId = vehicle.id,
                ecuEndpointId = findingKey.moduleIdentity,
                namespace = findingKey.namespace.name,
                rawDtcIdentity = findingKey.rawDtcIdentity,
                failureType = findingKey.failureType,
            )
            val exactExisting = dtcDao.getUnresolvedFinding(
                vehicleId = vehicle.id,
                ownerPrincipalId = ownerPrincipalId,
                namespace = findingKey.namespace.name,
                moduleIdentity = findingKey.moduleIdentity,
                rawDtcIdentity = findingKey.rawDtcIdentity,
                failureType = findingKey.failureType,
            )
            val sameLegacyIdentityCount = distinctFindings.count {
                it.code.equals(record.code, ignoreCase = true) &&
                    storageStatusForDtcRecord(it) == status
            }
            val legacyExisting = if (exactExisting == null) {
                legacyCandidates.firstOrNull { candidate ->
                    candidate.code.equals(record.code, ignoreCase = true) &&
                        candidate.status == status &&
                        legacyEventMatchesFinding(
                            event = candidate,
                            findingKey = findingKey,
                            allowMissingModule = sameLegacyIdentityCount == 1,
                        )
                }?.also(legacyCandidates::remove)
            } else {
                null
            }
            val canonicalEvent = canonicalFinding?.let { dtcDao.getFindingById(it.id, ownerPrincipalId) }
            val existing = exactExisting ?: legacyExisting ?: canonicalEvent
            val metadata = dtcRecordMetadata(record)
            val findingId = canonicalFinding?.id ?: existing?.id ?: UUID.randomUUID().toString()
            diagnosticFindingRepository.insertIdentity(
                DiagnosticFindingEntity(
                    id = findingId,
                    vehicleId = vehicle.id,
                    ecuEndpointId = findingKey.moduleIdentity,
                    diagnosticNamespace = findingKey.namespace.name,
                    rawDtcIdentity = findingKey.rawDtcIdentity,
                    displayCode = record.code.uppercase(),
                    createdAtMs = existing?.firstSeenAt ?: now,
                    failureType = findingKey.failureType,
                    moduleRole = record.moduleName.orEmpty(),
                    requestAddress = record.targetAddress,
                    responseAddress = record.responseAddress,
                    vehicleBindingId = _vehicleSessionBinding.value.bindingId,
                )
            )
            if (existing != null) {
                val isNewSession = existing.sessionId != currentSessionId
                dtcDao.insertDtc(
                    existing.copy(
                        lastSeenAt = now,
                        resolvedAt = null,
                        occurrenceCount = if (isNewSession) existing.occurrenceCount + 1 else existing.occurrenceCount,
                        sessionId = currentSessionId,
                        freezeFrameJson = metadata,
                        observationState = "OBSERVED",
                        diagnosticNamespace = findingKey.namespace.name,
                        moduleIdentity = findingKey.moduleIdentity,
                        moduleName = record.moduleName.orEmpty(),
                        targetAddress = record.targetAddress.orEmpty(),
                        responseAddress = record.responseAddress.orEmpty(),
                        sourceService = record.sourceService,
                        statusByte = record.udsStatusByte,
                        observationSemantic = record.primaryObservationSemantic().name,
                        rawDtcIdentity = findingKey.rawDtcIdentity,
                        rawDtc24 = record.rawDtc24,
                        failureType = record.codeIdentity.failureType,
                        dtcFormat = record.dtcFormat.name,
                        synced = false,
                    )
                )
            } else {
                val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(vehicle.make)
                val def = dtcDefinitionDao.getDefinitionForFinding(
                    code = record.code,
                    manufacturer = vehicleMake,
                    namespace = record.namespace.name,
                    rawDtcIdentity = record.codeIdentity.rawCode,
                    failureType = record.codeIdentity.failureType,
                )
                val description = com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(def, record.code)
                val severity = if (def != null && !def.severity.isNullOrBlank() && def.severity != "UNKNOWN") {
                    def.severity
                } else {
                    com.elysium369.meet.ui.components.DtcUtils.getDynamicSeverity(record.code)
                }

                dtcDao.insertDtc(
                    DtcEventEntity(
                        id = findingId,
                        sessionId = currentSessionId,
                        vehicleId = vehicle.id,
                        code = record.code,
                        description = description,
                        severity = severity,
                        status = status,
                        firstSeenAt = now,
                        lastSeenAt = now,
                        resolvedAt = null,
                        occurrenceCount = 1,
                        freezeFrameJson = metadata,
                        diagnosticNamespace = findingKey.namespace.name,
                        moduleIdentity = findingKey.moduleIdentity,
                        moduleName = record.moduleName.orEmpty(),
                        targetAddress = record.targetAddress.orEmpty(),
                        responseAddress = record.responseAddress.orEmpty(),
                        sourceService = record.sourceService,
                        statusByte = record.udsStatusByte,
                        observationSemantic = record.primaryObservationSemantic().name,
                        rawDtcIdentity = findingKey.rawDtcIdentity,
                        rawDtc24 = record.rawDtc24,
                        failureType = record.codeIdentity.failureType,
                        dtcFormat = record.dtcFormat.name,
                        synced = false,
                        ownerPrincipalId = ownerPrincipalId,
                        tenantId = OfflineOwnership.PERSONAL_TENANT,
                        originDeviceId = activePrincipalKernel.localDeviceId,
                        createdOffline = true,
                    )
                )

                if (status == "ACTIVE") {
                    alertManager.triggerNewDtcAlert(record.code)
                }
            }

            records.filter { it.findingKey(vehicle.id) == findingKey }.forEach { observation ->
                appendDiagnosticObservation(
                    DiagnosticObservationEntity(
                        id = UUID.randomUUID().toString(),
                        findingId = findingId,
                        sessionId = currentSessionId,
                        observedAt = now,
                        observationState = "OBSERVED",
                        semantics = observation.observationSemantics().joinToString("|") { it.name },
                        statusByte = observation.udsStatusByte,
                        sourceService = observation.sourceService,
                        exchangeId = evidenceReferences.bestMatchFor(observation),
                        rawPayloadHash = sha256Hex(observation.rawPayload.toByteArray(Charsets.UTF_8)),
                    )
                )
            }
            diagnosticFindingRepository.rebuildProjection(findingId)
        }

        // Absence is an observation, not proof of repair. A prior DTC can only
        // become NOT_OBSERVED when the same module and status bucket completed
        // successfully; resolvedAt remains reserved for explicit clear or a
        // future verified post-scan/drive-cycle workflow.
        val unresolvedFindings = diagnosticFindingRepository.getVehicleFindings(vehicle.id).mapNotNull { finding ->
            if (finding.projection.state == FindingResolutionState.VERIFIED_RESOLVED) {
                null
            } else {
                finding.identity to finding.timeline
            }
        }
        val isAddressedProtocol = report.protocol.uppercase().let {
            it.contains("CAN") || it.contains("ISO15765") || it.contains("DOIP") || it.contains("13400")
        }

        unresolvedFindings.forEach { (finding, timeline) ->
            // A live probe is insufficient: require a successful DTC service
            // read covering the same bucket and matching diagnostic module.
            val namespace = runCatching {
                DiagnosticNamespace.valueOf(finding.diagnosticNamespace)
            }.getOrNull() ?: return@forEach
            val semantics = timeline.observedSemanticsFor(namespace)
            if (semantics.isEmpty()) return@forEach
            val bucket = semantics.primaryBucketFor(namespace) ?: return@forEach

            val matchedModule = if (isAddressedProtocol) {
                report.modules.firstOrNull { activeModule ->
                    activeModule.moduleIdentity == finding.ecuEndpointId
                }
            } else {
                report.modules.firstOrNull { it.moduleName == "Standard OBD-II" }
            }

            if (DtcObservationPolicy.canMarkNotObserved(
                    module = matchedModule,
                    bucket = bucket,
                    code = finding.displayCode,
                    records = records,
                    namespace = namespace,
                    moduleIdentity = finding.ecuEndpointId,
                    semantics = semantics,
                    rawDtcIdentity = finding.rawDtcIdentity,
                )
            ) {
                // Canonical append-only evidence is authoritative. The old table
                // is updated only as a compatibility read model when it exists.
                dtcDao.getFindingById(finding.id, activePrincipalKernel.current().id)?.let { compatibilityEvent ->
                    dtcDao.insertDtc(compatibilityEvent.copy(
                        observationState = "NOT_OBSERVED_LAST_SCAN",
                        synced = false
                    ))
                }
                val coverageEvidence = evidenceReferences.bestCoverageMatchFor(
                    module = matchedModule,
                    namespace = namespace,
                    bucket = bucket,
                    semantics = semantics,
                )
                appendDiagnosticObservation(
                    DiagnosticObservationEntity(
                        id = UUID.randomUUID().toString(),
                        findingId = finding.id,
                        sessionId = currentSessionId,
                        observedAt = now,
                        observationState = "NOT_OBSERVED_LAST_SCAN",
                        semantics = semantics.joinToString("|") { it.name },
                        statusByte = null,
                        sourceService = coverageEvidence?.service.orEmpty(),
                        exchangeId = coverageEvidence?.exchangeId,
                        rawPayloadHash = coverageEvidence?.rawResponseHash.orEmpty(),
                    )
                )
                diagnosticFindingRepository.rebuildProjection(finding.id)
                addTerminalLog(
                    "[NOT_OBSERVED] ${finding.displayCode} no apareció bajo cobertura completa del módulo; resolución aún no verificada.",
                    TerminalLineType.SYSTEM,
                )
            }
        }

        scheduleSync()
    }

    private fun List<PersistedExchangeReference>.bestCoverageMatchFor(
        module: DtcModuleReport?,
        namespace: DiagnosticNamespace,
        bucket: DtcBucket,
        semantics: Set<DiagnosticSemantic>,
    ): PersistedExchangeReference? {
        module ?: return null
        val authoritativeServices = module.serviceReads.filter { read ->
            read.outcome.provesBucketWasRead && read.coverage.namespace == namespace &&
                when (namespace) {
                    DiagnosticNamespace.SAE_OBD -> read.coverage.covers(bucket)
                    DiagnosticNamespace.UDS,
                    DiagnosticNamespace.KWP2000,
                    DiagnosticNamespace.OEM -> read.coverage.fullyCovers(semantics)
                }
        }.map { it.command.uppercase().filter(Char::isLetterOrDigit) }.toSet()
        if (authoritativeServices.isEmpty()) return null
        return asSequence()
            .filter { reference ->
                val service = reference.service.uppercase().filter(Char::isLetterOrDigit)
                authoritativeServices.any { it == service || it.startsWith(service) || service.startsWith(it) }
            }
            .filter { reference ->
                reference.outcome.provesBucketWasRead &&
                    (reference.requestScope == "FUNCTIONAL" || module.targetAddress.isNullOrBlank() || reference.targetAddress.isNullOrBlank() || reference.targetAddress.equals(module.targetAddress, true)) &&
                    (module.responseAddress.isNullOrBlank() || reference.responseAddress.isNullOrBlank() || reference.responseAddress.equals(module.responseAddress, true))
            }
            .firstOrNull()
    }

    private fun List<PersistedExchangeReference>.bestMatchFor(record: DtcRecord): String? {
        val recordService = record.sourceService.uppercase().filter(Char::isLetterOrDigit)
        val recordPayloadHash = sha256Hex(record.rawPayload.toByteArray(Charsets.UTF_8))
        return asSequence()
            .filter { exchange ->
                val exchangeService = exchange.service.uppercase().filter(Char::isLetterOrDigit)
                exchangeService == recordService ||
                    exchangeService.startsWith(recordService) ||
                    recordService.startsWith(exchangeService)
            }
            .maxByOrNull { exchange ->
            val exchangeService = exchange.service.uppercase().filter(Char::isLetterOrDigit)
            val serviceScore = when {
                exchangeService == recordService -> 8
                exchangeService.startsWith(recordService) || recordService.startsWith(exchangeService) -> 5
                else -> 0
            }
            val responseScore = if (
                !record.responseAddress.isNullOrBlank() &&
                record.responseAddress.equals(exchange.responseAddress, ignoreCase = true)
            ) 16 else 0
            val targetScore = if (
                !record.targetAddress.isNullOrBlank() &&
                record.targetAddress.equals(exchange.targetAddress, ignoreCase = true)
            ) 12 else 0
            val rawPayloadScore = if (exchange.rawResponseHash == recordPayloadHash) 64 else 0
            val outcomeScore = if (exchange.outcome == ModuleScanOutcome.COMPLETE) 8 else 0
            serviceScore + responseScore + targetScore + rawPayloadScore + outcomeScore
        }?.exchangeId
    }

    private fun storageStatusForDtcRecord(record: DtcRecord): String = when (record.bucket) {
        DtcBucket.PENDING -> "PENDING"
        DtcBucket.PERMANENT -> "PERMANENT"
        DtcBucket.HISTORY -> if (DtcStatusFlag.INTERMITTENT in record.statusFlags) "INTERMITTENT" else "HISTORY"
        DtcBucket.ACTIVE -> "ACTIVE"
    }

    private fun dtcRecordMetadata(record: DtcRecord): String {
        val moduleIdentity = DiagnosticModuleIdentity.canonical(
            record.targetAddress,
            record.responseAddress,
            record.moduleName,
        )
        val data = mapOf(
            "namespace" to record.namespace.name,
            "moduleIdentity" to moduleIdentity,
            "moduleName" to (record.moduleName ?: ""),
            "targetAddress" to (record.targetAddress ?: ""),
            "responseAddress" to (record.responseAddress ?: ""),
            "sourceService" to record.sourceService,
            "bucket" to record.bucket.name,
            "observationSemantic" to record.primaryObservationSemantic().name,
            "statusFlags" to record.statusFlags.joinToString("|") { it.name },
            "udsStatusByte" to (record.udsStatusByte?.let { String.format("0x%02X", it) } ?: ""),
            "udsFailureType" to (record.udsFailureType ?: ""),
            "rawDtcIdentity" to record.codeIdentity.stableRawIdentity,
            "rawDtc24" to (record.rawDtc24?.let { "%06X".format(it) } ?: ""),
            "dtcFormat" to record.dtcFormat.name,
        )
        return Json.encodeToString(data)
    }

    private fun legacyEventMatchesFinding(
        event: DtcEventEntity,
        findingKey: DiagnosticFindingKey,
        allowMissingModule: Boolean,
    ): Boolean {
        val metadata = runCatching {
            event.freezeFrameJson?.let { org.json.JSONObject(it) }
        }.getOrNull()
        val namespace = event.diagnosticNamespace.ifBlank {
            metadata?.optString("namespace").orEmpty().ifBlank {
                val service = event.sourceService.ifBlank {
                    metadata?.optString("sourceService").orEmpty()
                }
                if (service.startsWith("19")) DiagnosticNamespace.UDS.name else DiagnosticNamespace.SAE_OBD.name
            }
        }
        if (namespace != findingKey.namespace.name) return false

        val target = event.targetAddress.ifBlank { metadata?.optString("targetAddress").orEmpty() }
        val response = event.responseAddress.ifBlank { metadata?.optString("responseAddress").orEmpty() }
        val module = event.moduleName.ifBlank { metadata?.optString("moduleName").orEmpty() }
        val hasModuleEvidence = target.isNotBlank() || response.isNotBlank() || module.isNotBlank()
        if (!hasModuleEvidence) return allowMissingModule
        return DiagnosticModuleIdentity.canonical(target, response, module) == findingKey.moduleIdentity
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    private fun scheduleVanguardCommerceSync() {
        VanguardOutboxSyncWorker.enqueueNow(context)
    }

    private fun fetchDtcDefinitions(codes: List<String>) {
        viewModelScope.launch {
            val newDefinitions = _dtcDefinitions.value.toMutableMap()
            val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(_selectedVehicle.value?.make)
            codes.forEach { code ->
                if (!newDefinitions.containsKey(code)) {
                    val def = dtcDefinitionDao.getDefinitionForCode(code, vehicleMake)
                    if (def != null) {
                        newDefinitions[code] = localizeDtcDefinition(def)
                    } else {
                        newDefinitions[code] = generateFallbackDefinition(code)
                    }
                }
            }
            _dtcDefinitions.value = newDefinitions
        }
    }

    private fun generateFallbackDefinition(code: String): DtcDefinitionEntity {
        val letter = code.firstOrNull()?.uppercaseChar() ?: 'P'
        val descEs = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = true)
        val descEn = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = false)
        val severity = com.elysium369.meet.ui.components.DtcUtils.getDynamicSeverity(code)
        val urgency = com.elysium369.meet.ui.components.DtcUtils.getDynamicUrgency(code)

        return DtcDefinitionEntity(
            code = code,
            manufacturer = "GENERIC",
            descriptionEn = descEn,
            descriptionEs = descEs,
            system = when (letter) { 'P' -> "ENGINE"; 'C' -> "CHASSIS"; 'B' -> "BODY"; 'U' -> "NETWORK"; else -> "GENERAL" },
            severity = severity,
            possibleCauses = com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(code, null),
            urgency = urgency
        )
    }

    private fun localizeDtcDefinition(definition: DtcDefinitionEntity): DtcDefinitionEntity {
        return definition.copy(
            descriptionEs = com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(definition, definition.code),
            possibleCauses = com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(definition.code, definition.possibleCauses)
        )
    }

    fun searchDtcManual(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isBlank()) {
                _manualSearchResults.value = emptyList()
            } else {
                val dbResults = dtcDefinitionDao.searchDefinitions(query)
                if (dbResults.isEmpty() && query.matches(Regex("^[PBUCpbuc][0-9A-Fa-f]{4}$"))) {
                    // Generate dynamic fallback definition so search never fails for a valid DTC code!
                    val fallback = generateFallbackDefinition(query.uppercase())
                    _manualSearchResults.value = listOf(fallback)
                } else {
                    // Deduplicate: group by code, prefer manufacturer-specific over GENERIC
                    val deduped = dbResults
                        .map { localizeDtcDefinition(it) }
                        .groupBy { it.code }
                        .map { (_, entries) ->
                            entries.firstOrNull {
                                it.manufacturer != "GENERIC" &&
                                    !it.possibleCauses.isNullOrBlank() &&
                                    !it.possibleCauses.contains("manufacturer manual", ignoreCase = true)
                            } ?: entries.firstOrNull { it.manufacturer == "GENERIC" } ?: entries.first()
                        }
                        .take(50)
                    _manualSearchResults.value = deduped
                }
            }
        }
    }

    suspend fun getDtcDefinition(code: String): DtcDefinitionEntity {
        val make = _selectedVehicle.value?.make ?: "GENERIC"
        val normalizedMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(make)
        val normalizedCode = code.uppercase()
        return dtcDefinitionDao.getDefinitionForCode(normalizedCode, normalizedMake)
            ?.let { localizeDtcDefinition(it) }
            ?: generateFallbackDefinition(normalizedCode)
    }

    suspend fun getDtcDefinition(finding: DtcRecord): DtcDefinitionEntity {
        val make = _selectedVehicle.value?.make ?: "GENERIC"
        val normalizedMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(make)
        return dtcDefinitionDao.getDefinitionForFinding(
            code = finding.code.uppercase(),
            manufacturer = normalizedMake,
            namespace = finding.namespace.name,
            rawDtcIdentity = finding.codeIdentity.rawCode,
            failureType = finding.codeIdentity.failureType,
        )?.let(::localizeDtcDefinition)
            ?: generateFallbackDefinition(finding.code.uppercase()).copy(
                diagnosticNamespace = finding.namespace.name,
                dtcFormat = finding.dtcFormat.name,
                failureType = finding.codeIdentity.failureType,
                sourceAuthority = "UNVERIFIED",
                verificationStatus = "UNVERIFIED",
            )
    }

    // ══════════════════════════════════════════════════════════════════
    // KNOWLEDGE GRAPH QUERIES
    // ══════════════════════════════════════════════════════════════════

    suspend fun getDtcSymptoms(code: String): List<com.elysium369.meet.data.local.entities.DtcSymptomEntity> {
        return dtcKnowledgeGraphDao.getSymptomsForDtc(code.uppercase())
    }

    suspend fun getDtcCauses(code: String): List<com.elysium369.meet.data.local.entities.DtcCauseEntity> {
        return dtcKnowledgeGraphDao.getCausesForDtc(code.uppercase())
    }

    suspend fun getDtcProcedures(code: String): List<com.elysium369.meet.data.local.entities.DtcProcedureEntity> {
        return dtcKnowledgeGraphDao.getProceduresForDtc(code.uppercase())
    }

    suspend fun getDtcRelatedPids(code: String): List<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity> {
        return dtcKnowledgeGraphDao.getRelatedPidsForDtc(code.uppercase())
    }

    suspend fun getDtcCoOccurrences(code: String): List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity> {
        return dtcKnowledgeGraphDao.getCoOccurrencesForDtc(code.uppercase())
    }

    suspend fun getDtcCoOccurrencesForMultiple(codes: List<String>): List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity> {
        return dtcKnowledgeGraphDao.getCoOccurrencesForMultipleDtcs(codes.map { it.uppercase() })
    }

    suspend fun getDtcRepairCosts(code: String, region: String = "LATAM"): List<com.elysium369.meet.data.local.entities.DtcRepairCostEntity> {
        return dtcKnowledgeGraphDao.getRepairCostsForDtc(code.uppercase(), region)
    }

    suspend fun getDtcVerifiedFixes(code: String): List<com.elysium369.meet.data.local.entities.DtcVerifiedFixEntity> {
        return dtcKnowledgeGraphDao.getVerifiedFixesForDtc(code.uppercase())
    }

    suspend fun getDtcCommunityRepairCases(code: String): List<RepairCase> {
        return repairCaseRepository.searchCases(
            query = "",
            dtc = code.uppercase(),
            sortBy = "votes",
            onlyVerified = false
        )
    }

    suspend fun searchDtcKnowledgeGraph(query: String): List<DtcDefinitionEntity> {
        return dtcKnowledgeGraphDao.searchKnowledgeGraph(query)
    }

    suspend fun upvoteDtcFix(fixId: Long) {
        dtcKnowledgeGraphDao.upvoteFix(fixId)
    }

    suspend fun clearDtcs(): ClearDtcResult {
        val connectionProof = connectionTruth.value
        if (!connectionProof.isSessionReady || connectionProof.isDemoSession) {
            val message = "Borrado rechazado: se requiere adaptador, protocolo y ECU físicos verificados."
            _clearDtcResult.value = message
            return ClearDtcResult.Rejected(emptyList(), message = message)
        }
        val bindingFailure = runCatching { requireBoundVehicleForCriticalOperation() }.exceptionOrNull()
        if (bindingFailure != null) {
            val message = bindingFailure.message.orEmpty()
            _clearDtcResult.value = message
            return ClearDtcResult.Rejected(emptyList(), message = message)
        }
        ensureDtcScanBeforeAction()
        _isClearing.value = true
        voiceFeedbackManager.speak("Iniciando borrado de códigos de error de la memoria.", "Starting fault code memory erase.")
        _clearDtcResult.value = "Enviando comando de borrado..."

        if (connectionState.value != ObdState.CONNECTED) {
            voiceFeedbackManager.speak(
                "No hay conexión OBD activa. No se puede borrar la memoria real del vehículo.",
                "No active OBD connection. Vehicle fault memory cannot be erased."
            )
            _clearDtcResult.value = "Conecta el adaptador OBD y verifica ignición en ON antes de borrar DTCs reales."
            _isClearing.value = false
            return ClearDtcResult.Rejected(emptyList(), message = _clearDtcResult.value.orEmpty())
        }

        return try {
        val verificationPlan = buildClearVerificationPlan()
        val result = clearDiagnosticMemory(verificationPlan)
        var postClearEvidenceReferences: List<PersistedExchangeReference> = emptyList()
        result.postClearReport?.let { report ->
            postClearEvidenceReferences = saveDiagnosticEvidence(report)
            saveDetectedDtcFindings(report, postClearEvidenceReferences)
            updateLatestScanProjection(report)
        }
        if (result.verifiedFindingIds.isNotEmpty()) {
            result.postClearReport?.let { report ->
                appendVerifiedAbsenceObservations(
                    findingIds = result.verifiedFindingIds,
                    report = report,
                    evidenceReferences = postClearEvidenceReferences,
                )
            }
            dtcDao.resolveVerifiedFindings(
                result.verifiedFindingIds.toList(),
                activePrincipalKernel.current().id,
                System.currentTimeMillis(),
            )
            updateHealthScore()
            scheduleSync()
        }
        _clearDtcResult.value = result.message
        when (result) {
            is ClearDtcResult.Verified -> voiceFeedbackManager.speak(
                "Borrado verificado por módulo y servicio.",
                "Clear verified by module and service.",
            )
            is ClearDtcResult.PartiallyVerified -> voiceFeedbackManager.speak(
                "Borrado aceptado con verificación parcial. Revisa los módulos pendientes.",
                "Clear accepted with partial verification. Review pending modules.",
            )
            is ClearDtcResult.AcceptedButNotVerified -> voiceFeedbackManager.speak(
                "El borrado fue aceptado, pero todavía no está verificado.",
                "Clear was accepted but is not yet verified.",
            )
            else -> voiceFeedbackManager.speak(
                "No se pudo verificar el borrado. La evidencia anterior se conserva.",
                "Clear could not be verified. Previous evidence is preserved.",
            )
        }
        result
        } finally {
            _isClearing.value = false
        }
    }

    private suspend fun appendVerifiedAbsenceObservations(
        findingIds: Set<String>,
        report: DtcScanReport,
        evidenceReferences: List<PersistedExchangeReference>,
    ) {
        val observedAt = System.currentTimeMillis()
        findingIds.forEach { findingId ->
            val canonicalFinding = diagnosticFindingRepository.getFindingSnapshot(findingId) ?: return@forEach
            val finding = canonicalFinding.identity
            val timeline = canonicalFinding.timeline
            val namespace = runCatching {
                DiagnosticNamespace.valueOf(finding.diagnosticNamespace)
            }.getOrNull() ?: return@forEach
            val semantics = timeline.observedSemanticsFor(namespace)
            if (semantics.isEmpty()) return@forEach
            val bucket = semantics.primaryBucketFor(namespace) ?: return@forEach
            val module = report.modules.firstOrNull { it.moduleIdentity == finding.ecuEndpointId }
            val coverageEvidence = evidenceReferences.bestCoverageMatchFor(
                module = module,
                namespace = namespace,
                bucket = bucket,
                semantics = semantics,
            )
            appendDiagnosticObservation(
                DiagnosticObservationEntity(
                    id = UUID.randomUUID().toString(),
                    findingId = findingId,
                    sessionId = currentSessionId,
                    observedAt = observedAt,
                    observationState = "VERIFIED_RESOLVED",
                    semantics = semantics.joinToString("|") { it.name },
                    statusByte = null,
                    sourceService = coverageEvidence?.service
                        ?: timeline.latestObservedSourceService(),
                    exchangeId = coverageEvidence?.exchangeId,
                    rawPayloadHash = coverageEvidence?.rawResponseHash.orEmpty(),
                )
            )
            diagnosticFindingRepository.rebuildProjection(findingId)
        }
    }

    private suspend fun buildClearVerificationPlan(): ClearVerificationPlan {
        val vehicle = _selectedVehicle.value ?: return ClearVerificationPlan.empty()
        val targets = diagnosticFindingRepository.getVehicleFindings(vehicle.id).mapNotNull { canonicalFinding ->
            val finding = canonicalFinding.identity
            val timeline = canonicalFinding.timeline
            if (canonicalFinding.projection.state == FindingResolutionState.VERIFIED_RESOLVED) {
                return@mapNotNull null
            }
            val namespace = runCatching {
                DiagnosticNamespace.valueOf(finding.diagnosticNamespace)
            }.getOrNull() ?: return@mapNotNull null
            val requiredSemantics = timeline.observedSemanticsFor(namespace)
            // Do not guess a status bucket for imported or malformed history.
            // Without a typed observed semantic there is no safe clear target.
            if (requiredSemantics.isEmpty()) return@mapNotNull null
            ClearVerificationTarget(
                findingId = finding.id,
                vehicleId = vehicle.id,
                findingKey = DiagnosticFindingKey(
                    vehicleId = vehicle.id,
                    namespace = namespace,
                    moduleIdentity = finding.ecuEndpointId,
                    rawDtcIdentity = finding.rawDtcIdentity,
                    displayCode = finding.displayCode,
                    failureType = finding.failureType,
                ),
                requiredSemantics = requiredSemantics,
                sourceService = timeline.latestObservedSourceService(),
            )
        }
        return ClearVerificationPlan(
            requestedAtMs = System.currentTimeMillis(),
            targets = targets,
            preClearReport = lastDtcScanReport.value,
        )
    }

    /**
     * Executes a "Smart Scan" — A comprehensive health check of the vehicle.
     */
    suspend fun runSmartScan() {
        val truth = connectionTruth.value
        if (connectionState.value != ObdState.CONNECTED || !truth.isSessionReady || truth.isDemoSession) {
            _cloudSyncState.value = "Escaneo real no disponible: conecta y verifica una ECU física."
            return
        }

        _isScanning.value = true
        _cloudSyncState.value = "Iniciando Escaneo Inteligente Elite..."
        voiceFeedbackManager.speak("Iniciando escaneo inteligente completo del vehículo.", "Starting full smart scan of the vehicle.")

        try {
            // 1. Scan DTCs
            _cloudSyncState.value = "Buscando códigos de falla (DTCs)..."
            refreshDiagnostics(manageState = false)

            // 2. Freeze frame is associated to an evidence-scoped finding, never list order alone.
            val snapshotFinding = lastDtcScanReport.value?.records
                ?.firstOrNull { it.bucket == DtcBucket.ACTIVE && it.namespace == DiagnosticNamespace.SAE_OBD }
            if (snapshotFinding != null) {
                _cloudSyncState.value = "Capturando Cuadro Congelado Histórico..."
                val firstDtc = snapshotFinding.code
                val ff = obdSession.readFreezeFrame(firstDtc)
                if (ff.outcome == com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED) {
                    val scoped = ff.values.mapKeys { (key, _) -> "$firstDtc:$key" }
                    _freezeFrameData.value = _freezeFrameData.value + scoped
                    persistFindingSnapshot(snapshotFinding, ff)
                }
                _freezeFrameStatus.value = when (ff.outcome) {
                    com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED ->
                        "Cuadro Congelado de $firstDtc capturado."
                    com.elysium369.meet.core.obd.FreezeFrameOutcome.BELONGS_TO_ANOTHER_DTC ->
                        "El freeze frame 0 pertenece a ${ff.actualDtc}, no a $firstDtc. No se asoció."
                    com.elysium369.meet.core.obd.FreezeFrameOutcome.NO_RESPONSE ->
                        "La ECU no devolvió freeze frame para $firstDtc."
                    com.elysium369.meet.core.obd.FreezeFrameOutcome.MALFORMED_RESPONSE ->
                        "La identidad del freeze frame no fue verificable."
                }
            }

            // 3. Check Battery Voltage & Alternator health
            _cloudSyncState.value = "Analizando sistema eléctrico..."
            val (ecuVoltage, physicalVoltage) = obdSession.readBatteryVoltage()
            val voltage = if (physicalVoltage > 0f) physicalVoltage else ecuVoltage
            val batteryHealth = if (voltage > 12.4f) "Excelente" else if (voltage > 11.8f) "Normal" else "Baja (Cargar)"

            // 4. Update status with detailed report
            _cloudSyncState.value = "Escaneo completado. Batería: $batteryHealth (${voltage}V)"

            // 5. Auto-save session to cloud
            saveSessionResults()
        } finally {
            _isScanning.value = false
        }
    }

    private fun detectManufacturer(vin: String) {
        if (vin.length < 3) {
            _manufacturer.value = "GENERIC"
            return
        }

        val mfr = when {
            // North American Ford
            vin.startsWith("1FM") || vin.startsWith("1FT") || vin.startsWith("1FA") || vin.startsWith("3FA") -> "FORD"
            // Toyota / Lexus
            vin.startsWith("JTD") || vin.startsWith("JT1") || vin.startsWith("JTN") || vin.startsWith("JTH") -> "TOYOTA"
            // General Motors (Chevrolet, GMC, Cadillac, Buick)
            vin.startsWith("1GC") || vin.startsWith("1G1") || vin.startsWith("1G6") || vin.startsWith("3G1") -> "GM"
            // BMW
            vin.startsWith("WBA") || vin.startsWith("WBS") || vin.startsWith("5UX") -> "BMW"
            // Volkswagen / Audi / Seat / Skoda (VAG)
            vin.startsWith("WVW") || vin.startsWith("WV2") || vin.startsWith("WAU") || vin.startsWith("TRU") -> "VOLKSWAGEN"
            // Mercedes-Benz
            vin.startsWith("WDB") || vin.startsWith("WDC") || vin.startsWith("WDD") || vin.startsWith("55S") -> "MERCEDES"
            // Honda / Acura
            vin.startsWith("JHM") || vin.startsWith("1HG") || vin.startsWith("2HG") || vin.startsWith("SHH") -> "HONDA"
            // Nissan / Infiniti
            vin.startsWith("JN1") || vin.startsWith("1N4") || vin.startsWith("1N6") || vin.startsWith("5N1") -> "NISSAN"
            // Hyundai / Kia
            vin.startsWith("KMH") || vin.startsWith("5NP") -> "HYUNDAI"
            vin.startsWith("KNA") || vin.startsWith("KND") -> "KIA"
            // Mazda
            vin.startsWith("JM1") || vin.startsWith("JM3") || vin.startsWith("3MZ") -> "MAZDA"
            // Subaru
            vin.startsWith("JF1") || vin.startsWith("JF2") || vin.startsWith("4S3") -> "SUBARU"
            // Peugeot / Citroën
            vin.startsWith("VF3") || vin.startsWith("VF7") -> "PEUGEOT"
            // Fiat / Chrysler (Stellantis)
            vin.startsWith("ZFA") || vin.startsWith("1C4") || vin.startsWith("2C3") -> "FIAT"
            // Land Rover / Jaguar
            vin.startsWith("SAL") || vin.startsWith("SAJ") -> "LAND_ROVER"
            else -> "GENERIC"
        }

        _manufacturer.value = mfr
        obdSession.enableOemPids(mfr)
    }

    suspend fun refreshFreezeFrame(dtc: String): Boolean {
        if (connectionState.value != ObdState.CONNECTED) {
            _freezeFrameStatus.value = "Conecta el OBD para volver a leer el freeze frame de $dtc."
            return false
        }
        _freezeFrameStatus.value = "Refrescando Cuadro Congelado..."
        return try {
            val ff = obdSession.readFreezeFrame(dtc)
            if (ff.outcome == com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED) {
                val scoped = ff.values.mapKeys { (key, _) -> "$dtc:$key" }
                _freezeFrameData.value = _freezeFrameData.value + scoped
                val candidates = lastDtcScanReport.value?.records.orEmpty().filter {
                    it.code.equals(dtc, ignoreCase = true)
                }
                if (candidates.size == 1) persistFindingSnapshot(candidates.single(), ff)
            }
            _freezeFrameStatus.value = when (ff.outcome) {
                com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED ->
                    "Cuadro Congelado de $dtc actualizado."
                com.elysium369.meet.core.obd.FreezeFrameOutcome.BELONGS_TO_ANOTHER_DTC ->
                    "El freeze frame 0 pertenece a ${ff.actualDtc}, no a $dtc. No se asoció."
                com.elysium369.meet.core.obd.FreezeFrameOutcome.NO_RESPONSE ->
                    "La ECU no devolvió freeze frame para $dtc."
                com.elysium369.meet.core.obd.FreezeFrameOutcome.MALFORMED_RESPONSE ->
                    "La ECU devolvió una identidad de freeze frame no verificable."
            }
            ff.outcome == com.elysium369.meet.core.obd.FreezeFrameOutcome.MATCHED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e("ObdViewModel", "Freeze frame refresh failed for $dtc", error)
            _freezeFrameStatus.value = "No se pudo releer el freeze frame de $dtc: ${error.message ?: "respuesta OBD no disponible"}."
            false
        }
    }

    private suspend fun persistFindingSnapshot(record: DtcRecord, frame: FreezeFrameReadResult) {
        val vehicle = requireBoundVehicleForCriticalOperation()
        val key = record.findingKey(vehicle.id)
        val finding = diagnosticFindingRepository.getIdentityByStableKey(
            vehicleId = vehicle.id,
            ecuEndpointId = key.moduleIdentity,
            namespace = key.namespace.name,
            rawDtcIdentity = key.rawDtcIdentity,
            failureType = key.failureType,
        ) ?: return
        val reportProtocol = lastDtcScanReport.value?.protocol.orEmpty()
        val snapshotTransport = when {
            reportProtocol.contains("DOIP", ignoreCase = true) -> DiagnosticTransport.DOIP
            reportProtocol.contains("CAN", ignoreCase = true) || reportProtocol.contains("15765") -> DiagnosticTransport.CAN
            reportProtocol.contains("KWP", ignoreCase = true) || reportProtocol.contains("9141") -> DiagnosticTransport.K_LINE
            else -> DiagnosticTransport.UNKNOWN
        }
        val exchangeIds = frame.rawExchanges.map { (command, rawResponse) ->
            appendStandaloneDiagnosticExchange(
                timestampMs = System.currentTimeMillis(),
                transport = snapshotTransport.name,
                applicationProtocol = DiagnosticApplicationProtocol.SAE_OBD.name,
                requestScope = if (record.targetAddress.isNullOrBlank() || record.targetAddress == "LEGACY") {
                    "LEGACY_UNADDRESSED"
                } else {
                    "PHYSICAL"
                },
                requestAddress = record.targetAddress,
                responseAddress = record.responseAddress,
                service = command,
                rawRequest = command,
                rawResponse = rawResponse,
                decodedOutcome = if (rawResponse.isBlank()) "NO_RESPONSE" else "SNAPSHOT_CAPTURED",
                adapterConfiguration = reportProtocol,
                parserVersion = "mode02-snapshot-v2",
            )
        }
        val capturedAtMs = System.currentTimeMillis()
        val parameterEvidence = Json.encodeToString(
            com.elysium369.meet.domain.diagnostics.DiagnosticSnapshotPayloadV2(
                associationMethod = "DTC_IDENTITY_MATCH_NOT_LIST_ORDER",
                parameters = frame.values.map { (parameterId, rawValue) ->
                    com.elysium369.meet.domain.diagnostics.DiagnosticSnapshotParameterV2(
                        parameterId = parameterId,
                        rawValue = rawValue,
                        capturedAtMs = capturedAtMs,
                        quality = if (frame.outcome == FreezeFrameOutcome.MATCHED) "PARSED_MATCHED" else "UNVERIFIED",
                        freshness = "CAPTURED_NOW",
                        canonicalSiUnit = null,
                        displayUnit = null,
                        source = DiagnosticSnapshotSource.SAE_MODE_02.name,
                        formulaVersion = "mode02-parser-v2",
                        inputExchangeIds = exchangeIds,
                    )
                },
            ),
        )
        val snapshotId = UUID.randomUUID().toString()
        diagnosticEvidenceDao.appendFindingSnapshotWithExchangeRefs(
            snapshot = FindingDiagnosticSnapshotEntity(
                id = snapshotId,
                findingId = finding.id,
                moduleIdentity = key.moduleIdentity,
                capturedAtMs = capturedAtMs,
                source = DiagnosticSnapshotSource.SAE_MODE_02.name,
                parametersJson = parameterEvidence,
                rawExchangeIdsJson = Json.encodeToString(exchangeIds),
            ),
            exchangeIds = exchangeIds,
        )
    }

    suspend fun readVin(): String? {
        val detectedVin = obdSession.readVinFromVehicle().vin ?: return null
        _vin.value = detectedVin
        detectManufacturer(detectedVin)
        bindVehicleFromObservedVin(detectedVin)
        return detectedVin
    }

    suspend fun setProtocol(protocol: String): Boolean {
        return obdSession.setProtocol(protocol)
    }

    suspend fun scanModules(): List<com.elysium369.meet.core.obd.NetworkModule> {
        _isScanning.value = true
        try {
            voiceFeedbackManager.speak("Escaneando topología de red del vehículo.", "Scanning vehicle network topology.")
            _cloudSyncState.value = "Adquiriendo topología y DTCs con evidencia canónica..."
            refreshDiagnostics(manageState = false)
            val report = lastDtcScanReport.value
            if (report == null) {
                _cloudSyncState.value = "Escaneo no concluyente: no se generó un informe verificable."
                return emptyList()
            }
            val modules = report.modules.map { module ->
                com.elysium369.meet.core.obd.NetworkModule(
                    id = module.targetAddress ?: module.responseAddress ?: module.moduleIdentity,
                    name = module.moduleName,
                    isAlive = module.isAlive,
                    networkType = com.elysium369.meet.core.obd.NetworkType.UNKNOWN,
                    addressing = com.elysium369.meet.core.obd.AddressingType.UNKNOWN,
                    dtcs = module.dtcs.map { it.code }.distinct(),
                    responseId = module.responseAddress.orEmpty(),
                    protocolDetected = report.protocol,
                )
            }
            _cloudSyncState.value = when (report.completeness) {
                com.elysium369.meet.core.obd.ScanCompleteness.COMPLETE ->
                    "Escaneo completo: ${modules.size} módulos, ${report.records.size} hallazgos."
                com.elysium369.meet.core.obd.ScanCompleteness.PARTIAL ->
                    "Escaneo parcial: ${modules.size} módulos respondieron; quedan módulos sin verificar."
                com.elysium369.meet.core.obd.ScanCompleteness.INCONCLUSIVE ->
                    "Escaneo no concluyente: no afirmar ausencia de fallas."
                com.elysium369.meet.core.obd.ScanCompleteness.FAILED ->
                    "Escaneo fallido: revisa conexión, protocolo y cobertura antes de diagnosticar."
            }
            return modules
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun sendRawCommand(cmd: String): String {
        val decision = DiagnosticRawCommandPolicy.evaluate(cmd)
        if (!decision.allowed) {
            Log.w("ObdViewModel", "Terminal command blocked: ${decision.reason}")
            return "BLOCKED: ${decision.reason}"
        }
        ensureDtcScanBeforeAction()
        return DiagnosticTerminalTransaction(obdSession).execute(decision.normalizedCommand).rawResponse
    }

    /**
     * Executes a professional-grade diagnostic routine or active test.
     * Performs safety checks (voltage, adapter quality) before sending commands.
     */
    suspend fun runDiagnosticCommand(command: com.elysium369.meet.core.obd.ObdCommandDef): String {
        Log.w("ObdVM", "Blocked legacy raw diagnostic command: ${command.description}")
        return "BLOCKED: la ejecución genérica fue retirada; usa un flujo tipado y autorizado por capability pack."
    }

    suspend fun consultAi(
        apiKey: String?,
        endpointUrl: String?,
        dtcList: List<String>,
        providerOverride: String? = null,
        modelNameOverride: String? = null,
        groundedRepairContext: String? = null
    ): String {
        val dtcCodesStr = dtcList.sorted().joinToString(",")
        val savedConfig = _aiConfig.value
        var effectiveProvider = normalizeAiProvider(providerOverride ?: savedConfig.provider)
        var effectiveApiKey = apiKey ?: savedConfig.apiKey
        var effectiveModel = modelNameOverride ?: savedConfig.modelName
        var effectiveEndpoint = endpointUrl ?: resolveAiEndpoint(effectiveProvider, savedConfig.endpoint)

        // Fallback to integrated MiniMax debug provider if no custom API key is configured
        if (effectiveApiKey.isBlank() && effectiveProvider != "ollama") {
            effectiveProvider = "minimax"
            effectiveModel = "MiniMax-M1"
            effectiveApiKey = ""
            effectiveEndpoint = ""
        }

        val remoteAiConfigured = when (effectiveProvider) {
            "minimax" -> true
            "ollama" -> !effectiveEndpoint.isNullOrBlank()
            "gemini" -> effectiveApiKey.isNotBlank()
            "openai", "anthropic", "mavis", "custom" -> effectiveApiKey.isNotBlank() && !effectiveEndpoint.isNullOrBlank()
            else -> effectiveApiKey.isNotBlank() && !effectiveEndpoint.isNullOrBlank()
        }

        // 1. Check offline cache only when there is no remote provider configured.
        if (!remoteAiConfigured) {
            try {
                val cached = aiConsultDao.getCachedConsult(dtcCodesStr)
                if (cached != null) {
                    android.util.Log.i("ObdViewModel", "Retrieved cached AI consultation offline for DTCs: $dtcCodesStr")
                    val cleanResult = parseCachedResponsePids(cached.response)
                    _anomalousPids.value = cleanResult.anomalousPids.map {
                        com.elysium369.meet.core.ai.HealthAnomaly(it, "Anomalía detectada en diagnóstico profundo (Caché)")
                    }
                    return cached.response
                }
            } catch (e: Exception) {
                android.util.Log.w("ObdViewModel", "Error reading AI consult cache: ${e.message}")
            }
        }

        // 2. Cache miss -> Query remote AI (new multi-provider engine first, legacy fallback)
        var resultText = ""
        var modelUsed = "$effectiveProvider:${effectiveModel.ifBlank { "default" }}"
        try {
            // Try new multi-provider AI engine
            val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
            val aiContext = com.elysium369.meet.ai.domain.AiContext(
                vehicle = _selectedVehicle.value?.let { v ->
                    com.elysium369.meet.ai.domain.VehicleContext(
                        make = v.make, model = v.model, year = v.year,
                        engine = v.engine, transmission = v.transmission_type,
                        fuel = v.fuel_type, vin = null,
                        odometer = null
                    )
                },
                obd = com.elysium369.meet.ai.domain.ObdContext(
                    connected = connectionState.value == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                    activePidsCount = _liveData.value.size,
                    dtcActiveCount = dtcList.size,
                    batteryVoltage = (_liveData.value["CONTROL_MODULE_VOLTAGE"] ?: 0.0).toFloat()
                ),
                dtcs = dtcList.map { com.elysium369.meet.ai.domain.DtcContext(it, "Activo") },
                livePids = _liveData.value.map { (k, v) ->
                    com.elysium369.meet.ai.domain.PidReading(pid = k, name = k, value = "%.2f".format(v))
                },
                manualAvailability = null,
                appModule = "DIAGNOSTIC_DTC",
                locale = "es-MX",
                userRole = com.elysium369.meet.ai.domain.UserRole.MECHANIC,
                safetyMode = true
            )
            val aiRequest = com.elysium369.meet.ai.domain.AiRequest(
                feature = com.elysium369.meet.ai.domain.AiFeature.DIAGNOSTIC_DTC,
                providerId = effectiveProvider,
                model = effectiveModel.ifBlank { "MiniMax-M1" },
                messages = listOf(
                    com.elysium369.meet.ai.domain.AiMessage(
                        com.elysium369.meet.ai.domain.AiRole.USER,
                        com.elysium369.meet.ai.context.AiAutomotiveContextBuilder.buildContextPrompt(aiContext) +
                            groundedRepairContext?.let {
                                "\n\n=== CONOCIMIENTO ESTRUCTURADO CITADO ===\n$it"
                            }.orEmpty() +
                            "\n\nDiagnostica los siguientes DTCs para $info: ${dtcList.joinToString(", ")}"
                    )
                ),
                context = aiContext
            )
            val aiResult = aiRepository.complete(aiRequest)
            if (aiResult.isSuccess) {
                resultText = aiResult.getOrThrow().text
                modelUsed = "${aiResult.getOrThrow().providerId}:${aiResult.getOrThrow().model}"
            } else {
                // Fallback to legacy GeminiDiagnostic
                android.util.Log.w("ObdViewModel", "New AI engine failed, falling back to legacy: ${aiResult.exceptionOrNull()?.message}")
                geminiDiagnostic.updateConfig(effectiveApiKey, effectiveEndpoint, effectiveProvider, effectiveModel)
                val result = geminiDiagnostic.analyzeDtc(
                    dtcList, info,
                    _liveData.value.mapValues { "%.2f".format(it.value) },
                    _telemetryHistory.value,
                    groundedRepairContext
                )
                resultText = result.analysisText
            }

            // Update the UI with detected anomalies (only from legacy path)
            // The new AiRepository returns text-only; anomalousPids are extracted separately if needed
            _anomalousPids.value = emptyList()
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "Remote AI consultation failed, invoking offline expert diagnostic fallback: ${e.message}", e)
            val make = _selectedVehicle.value?.make ?: "GENERIC"
            val normalizedMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(make)

            // Query local definitions for active DTCs
            val localDefinitions = dtcList.mapNotNull { code ->
                dtcDefinitionDao.getDefinitionForCode(code, normalizedMake)
            }
            val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"

            resultText = com.elysium369.meet.ui.components.DtcUtils.generateOfflineDiagnosticReport(
                dtcList = dtcList,
                vehicleInfo = info,
                definitions = localDefinitions
            )

            _anomalousPids.value = emptyList()
            modelUsed = "Elysium Vanguard AI OS Local Expert Engine"
        }

        // 3. Save to offline cache (both successful remote and generated local fallback)
        try {
            val session = currentSessionId
            val consult = com.elysium369.meet.data.local.entities.AiConsultEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session,
                dtcCodes = dtcCodesStr,
                prompt = "OBD2 Diagnosis for $dtcList",
                response = resultText,
                model = modelUsed,
                createdAt = System.currentTimeMillis(),
                exportedAsPdf = false
            )
            aiConsultDao.insertConsult(consult)
            android.util.Log.i("ObdViewModel", "Successfully saved AI consultation to offline cache.")
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "Failed to cache AI consultation", e)
        }

        return resultText
    }

    private fun parseCachedResponsePids(rawText: String): com.elysium369.meet.core.ai.DiagnosticResult {
        val pattern = java.util.regex.Pattern.compile("```json([\\s\\S]*?)```")
        val matcher = pattern.matcher(rawText)
        var anomalousPids = emptyList<String>()
        if (matcher.find()) {
            try {
                val jsonStr = matcher.group(1)?.trim()
                if (jsonStr != null) {
                    val obj = org.json.JSONObject(jsonStr)
                    val pidsArr = obj.optJSONArray("anomalous_pids")
                    if (pidsArr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until pidsArr.length()) {
                            list.add(pidsArr.getString(i))
                        }
                        anomalousPids = list
                    }
                }
            } catch (_: Exception) {}
        }
        return com.elysium369.meet.core.ai.DiagnosticResult(rawText, anomalousPids)
    }

    fun generateFullReport(aiAnalysis: String?) {
        val currentTrip = tripManager.currentTrip
        val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val dtcs = activeDtcs.value
        val history = _telemetryHistory.value
        voiceFeedbackManager.speak("Generando reporte de diagnóstico completo.", "Generating full diagnostic report.")

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tripData = if (currentTrip != null) {
                com.elysium369.meet.data.supabase.Trip(
                    id = currentTrip.id,
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_id = currentTrip.vehicleId,
                    session_id = currentTrip.sessionId,
                    started_at = currentTrip.startedAt,
                    ended_at = currentTrip.endedAt,
                    distance_km = currentTrip.distanceKm,
                    duration_seconds = currentTrip.durationSeconds,
                    avg_speed_kmh = currentTrip.avgSpeedKmh,
                    max_speed_kmh = currentTrip.maxSpeedKmh,
                    max_rpm = currentTrip.maxRpm,
                    avg_rpm = currentTrip.avgRpm,
                    max_temp_c = currentTrip.maxTempC,
                    fuel_efficiency = currentTrip.fuelEfficiency,
                    eco_score = currentTrip.ecoScore,
                    gps_track_json = currentTrip.gpsTrackJson
                )
            } else {
                // Create a synthetic trip for the report if none exists
                com.elysium369.meet.data.supabase.Trip(
                    id = UUID.randomUUID().toString(),
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_id = _selectedVehicle.value?.id ?: "N/A",
                    session_id = "MANUAL_DIAGNOSTIC",
                    started_at = System.currentTimeMillis(),
                    ended_at = System.currentTimeMillis(),
                    distance_km = 0f,
                    duration_seconds = 0,
                    avg_speed_kmh = 0f,
                    max_speed_kmh = 0f,
                    max_rpm = _liveData.value["010C"] ?: 0f,
                    avg_rpm = _liveData.value["010C"] ?: 0f,
                    max_temp_c = _liveData.value["0105"] ?: 0f,
                    fuel_efficiency = null,
                    eco_score = 100,
                    gps_track_json = null
                )
            }

            val healthScore = _healthScore.value
            val alerts = maintenanceAlerts.value
            val predictiveReport = _predictiveHealthReport.value

            val file = reportGenerator.generatePdfReport(
                trip = tripData,
                dtcs = dtcs,
                aiAnalysis = aiAnalysis,
                vehicleDetails = vehicleInfo,
                telemetryHistory = history,
                anomalies = _anomalousPids.value,
                healthScore = healthScore,
                maintenanceAlerts = alerts,
                predictiveReport = predictiveReport
            )

            reportGenerator.shareReport(file)
        }
    }

    fun markMaintenanceDone(alert: MaintenanceAlertEntity) {
        viewModelScope.launch {
            // Fetch real odometer from ECU if available
            val currentOdoLong = obdSession.readOdometer()?.toLong()
            val nextDue = if (currentOdoLong != null && currentOdoLong > 0) currentOdoLong + alert.intervalKm else alert.nextDueKm + alert.intervalKm

            val updatedAlert = alert.copy(
                lastDoneKm = if (currentOdoLong != null && currentOdoLong > 0) currentOdoLong else alert.nextDueKm,
                nextDueKm = nextDue
            )
            maintenanceAlertDao.insertAlert(updatedAlert)
        }
    }

    fun addMaintenanceAlert(type: String, intervalKm: Long, nextDueKm: Long) {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val alert = MaintenanceAlertEntity(
                id = java.util.UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                type = type,
                intervalKm = intervalKm,
                lastDoneKm = 0L,
                nextDueKm = nextDueKm,
                notes = null
            )
            maintenanceAlertDao.insertAlert(alert)
        }
    }

    fun addCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.insertCustomPid(pid)
        }
    }

    fun deleteCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.deleteCustomPid(pid)
        }
    }

    fun syncCustomPidsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncCommunityCustomPIDs(customPidDao)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error syncing custom PIDs from cloud", e)
            }
        }
    }

    fun setHighSpeedMode(enabled: Boolean) {
        obdSession.setHighSpeedMode(enabled)
    }

    fun pinPid(pid: String) {
        obdSession.pinPid(pid)
    }

    fun unpinPid(pid: String) {
        obdSession.unpinPid(pid)
        // Optionally clear history for unpinned PID
        val current = _telemetryHistory.value.toMutableMap()
        current.remove(pid)
        _telemetryHistory.value = current
    }

    private fun updateTelemetryHistory(newData: Map<String, Float>) {
        val pinned = pinnedPids.value
        if (pinned.isEmpty()) return

        val currentHistory = _telemetryHistory.value
        val newHistory = currentHistory.toMutableMap()
        val maxPoints = 200

        pinned.forEach { pid ->
            val value = newData[pid] ?: return@forEach
            val list = currentHistory[pid]?.toMutableList() ?: mutableListOf()
            list.add(value)
            if (list.size > maxPoints) {
                list.removeAt(0)
            }
            newHistory[pid] = list
        }
        _telemetryHistory.value = newHistory
    }

    private fun updateOscilloscopeBuffer(timestamp: Long, rawData: Map<String, Float>) {
        val pinned = pinnedPids.value
        // We always want to buffer the battery voltage for the oscilloscope
        val hasVoltage = rawData.containsKey("0142") || rawData.containsKey("42")
        if (pinned.isEmpty() && !hasVoltage) return

        val currentBuffer = _oscilloscopeBuffer.value
        val newBuffer = currentBuffer.toMutableMap()
        val maxPoints = 1000 // High frequency buffer capacity

        val targets = pinned.toMutableSet()
        if (hasVoltage) {
            targets.add(if (rawData.containsKey("0142")) "0142" else "42")
        }

        targets.forEach { pid ->
            val value = rawData[pid] ?: return@forEach
            val list = currentBuffer[pid]?.toMutableList() ?: mutableListOf()
            list.add(Pair(timestamp, value))
            if (list.size > maxPoints) {
                list.removeAt(0) // Remove oldest
            }
            newBuffer[pid] = list
        }
        _oscilloscopeBuffer.value = newBuffer
    }

    // --- Active Testing (Bidirectional) ---
    fun runActiveTest(test: ActiveTest) {
        viewModelScope.launch {
            val bindingFailure = runCatching { requireBoundVehicleForCriticalOperation() }.exceptionOrNull()
            if (bindingFailure != null) {
                val message = bindingFailure.message.orEmpty()
                addTerminalLog("[SEGURIDAD] $message", TerminalLineType.ERROR)
                voiceFeedbackManager.speak(message, message)
                return@launch
            }
            ensureDtcScanBeforeAction()
            obdSession.runActiveTest(test)
        }
    }

    fun stopActiveTest() {
        obdSession.stopActiveTest()
    }

    fun clearActiveTestStatus() {
        obdSession.clearActiveTestStatus()
        clearActiveTestAiDiagnostic()
    }

    suspend fun resetOilService(): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Ejecutando reset de servicio de aceite.", "Executing oil service reset.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.resetOilService(mfr)
        if (result) {
            voiceFeedbackManager.speak("Reset de aceite completado con éxito.", "Oil service reset completed successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo en el reset de servicio de aceite.", "Oil service reset failed.")
        }
        return result
    }

    suspend fun registerBattery(capacityAh: Int): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.registerBattery(mfr, capacityAh)
    }

    suspend fun resetEPB(open: Boolean): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.resetEPB(mfr, open)
    }

    suspend fun calibrateSAS(): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.calibrateSAS(mfr)
    }

    suspend fun relearnThrottle(): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Ejecutando reaprendizaje del cuerpo de aceleración.", "Executing throttle body relearn.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.relearnThrottle(mfr)
        if (result) {
            voiceFeedbackManager.speak("Reaprendizaje completado con éxito.", "Throttle relearn completed successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo en el reaprendizaje del acelerador.", "Throttle relearn failed.")
        }
        return result
    }

    suspend fun regenerateDPF(): Boolean {
        requireBoundVehicleForCriticalOperation()
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Iniciando regeneración del filtro de partículas.", "Starting particulate filter regeneration.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.regenerateDPF(mfr)
        if (result) {
            voiceFeedbackManager.speak("Regeneración DPF iniciada exitosamente.", "DPF regeneration started successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo al iniciar regeneración DPF.", "Failed to start DPF regeneration.")
        }
        return result
    }

    suspend fun resetTPMS(): Boolean {
        Log.w("ObdViewModel", "Blocked generic TPMS relearn: no reviewed capability pack")
        return false
    }

    fun exportTripToPdf(trip: TripEntity) {
        viewModelScope.launch {
            val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Desconocido"

            // Convert Entity to Domain model for ReportGenerator
            val domainTrip = com.elysium369.meet.data.supabase.Trip(
                id = trip.id,
                user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                vehicle_id = trip.vehicleId,
                session_id = trip.sessionId,
                started_at = trip.startedAt,
                ended_at = trip.endedAt,
                distance_km = trip.distanceKm,
                duration_seconds = trip.durationSeconds,
                avg_speed_kmh = trip.avgSpeedKmh,
                max_speed_kmh = trip.maxSpeedKmh,
                max_rpm = trip.maxRpm,
                avg_rpm = trip.avgRpm,
                max_temp_c = trip.maxTempC,
                fuel_efficiency = trip.fuelEfficiency,
                eco_score = trip.ecoScore,
                gps_track_json = trip.gpsTrackJson
            )

            // In a real scenario, we might want to fetch DTCs for this trip
            // For now, use active DTCs if it's the current session, or empty
            val dtcs = if (trip.endedAt == null) activeDtcs.value else emptyList<String>()

            val file = reportGenerator.generatePdfReport(
                trip = domainTrip,
                dtcs = dtcs,
                aiAnalysis = null, // Could be fetched from a saved analysis
                vehicleDetails = vehicleInfo
            )
            reportGenerator.shareReport(file)
        }
    }

    /**
     * Export the GPS forensic trail for a completed ride as a PDF.
     * The PDF includes QR verification code and SHA-256 integrity hash
     * for presentation to law enforcement or judicial authorities.
     *
     * Delegated to [RideCompletionHelper] via strangler migration.
     */
    fun exportGpsForensicTrail(rideId: String) {
        rideCompletionHelper.exportGpsForensicTrail(rideId)
    }

    fun submitTip(rideId: String, tipMinor: Long, currency: String, deliveryMethod: String) {
        rideCompletionHelper.submitTip(rideId, tipMinor, currency, deliveryMethod)
    }

    fun getCurrentTrip(): TripEntity? {
        return tripManager.currentTrip
    }

    suspend fun runAdapterCloneTest(): List<com.elysium369.meet.ui.screens.TestResult> {
        val rawResults = obdSession.runAdapterTests()
        return rawResults.map { (name, value) ->
            val color = when {
                value.contains("ERROR", true) || value.contains("?") -> android.graphics.Color.RED
                value.contains("N/A") || value.contains("Pendiente") -> android.graphics.Color.YELLOW
                value.contains("Instrucción") || value.contains("Conecta") -> android.graphics.Color.CYAN
                value.contains("Offline") || value.contains("Sin conexión") -> android.graphics.Color.YELLOW
                value.contains("ms") && (value.replace(" ms", "").toIntOrNull() ?: 0) > 300 -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
            com.elysium369.meet.ui.screens.TestResult(name, value, color.toComposeColor())
        }
    }

    private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)

    // --- Helpers ---
    private fun startForegroundService(vehicleId: String, adapterAddress: String? = lastAdapterAddress) {
        val resolvedAddress = adapterAddress
            ?: context.getSharedPreferences("elysium_obd_prefs", Context.MODE_PRIVATE)
                .getString("last_adapter_address", null)
        val intent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java).apply {
            putExtra("vehicle_id", vehicleId)
            if (!resolvedAddress.isNullOrBlank()) putExtra("adapter_address", resolvedAddress)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            try { context.startService(intent) } catch (_: Exception) {}
        }
    }

    fun saveVehicle(
        make: String,
        model: String,
        year: String,
        engineDisplacement: String,
        engineTech: String,
        transmission: String,
        transmissionType: String,
        fuelType: String,
        plate: String,
        vin: String?,
        vehicleKind: String = "CAR",
        onResult: ((Boolean, String) -> Unit)? = null,
    ) {
        if (vehicleKind !in setOf("CAR", "MOTORCYCLE")) {
            onResult?.invoke(false, "Tipo de vehículo inválido")
            return
        }
        val ownerAtRequest=activePrincipalKernel.current().id
        viewModelScope.launch {
            if(activePrincipalKernel.current().id!=ownerAtRequest) return@launch
            val displacement = engineDisplacement.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            val enginePart = listOf(engineDisplacement, engineTech).filter { it.isNotBlank() }.joinToString(" ")
            val transPart = listOf(transmission, transmissionType).filter { it.isNotBlank() }.joinToString(" - ")
            val fullEngineDesc = listOf(enginePart, transPart, fuelType).filter { it.isNotBlank() }.joinToString(" | ")

            val vehicle = Vehicle(
                id = UUID.randomUUID().toString(),
                user_id = ownerAtRequest,
                year = year.toIntOrNull() ?: 2024,
                make = make,
                model = model,
                engine = if (fullEngineDesc.isBlank()) "N/A" else fullEngineDesc,
                displacement_cc = displacement,
                engine_tech = engineTech,
                transmission_type = transmission,
                transmission_subtype = transmissionType,
                fuel_type = fuelType,
                vin = vin?.ifBlank { "NOT_READ" } ?: "NOT_READ",
                plate = plate.ifBlank { "NOT_SET" },
                vehicle_kind = vehicleKind,
            )

            when (val result = vehicleRepository.insertVehicle(vehicle)) {
                is com.elysium369.meet.core.remote.RemoteResult.Success -> {
                    voiceFeedbackManager.speak(
                        "Vehículo $make $model guardado exitosamente.",
                        "Vehicle $make $model saved successfully."
                    )
                    onResult?.invoke(true, "Vehículo confirmado en línea")
                }
                is com.elysium369.meet.core.remote.RemoteResult.Forbidden,
                is com.elysium369.meet.core.remote.RemoteResult.Unauthorized -> {
                    onResult?.invoke(false, "No se pudo confirmar la propiedad del vehículo. Inicia sesión y reintenta.")
                    return@launch
                }
                else -> {
                    voiceFeedbackManager.speak(
                        "Vehículo guardado localmente; sincronización remota pendiente.",
                        "Vehicle saved locally; remote sync pending."
                    )
                    Log.w("ObdVM", "La sincronización del vehículo sigue pendiente")
                    onResult?.invoke(false, "Guardado en este teléfono; pendiente de sincronización en línea")
                }
            }

            // Fix: Call selectVehicle to ensure persistence of the selected ID
            if(activePrincipalKernel.current().id==ownerAtRequest) selectVehicle(vehicle, ActiveVehicleChangeReason.USER_CREATED)
        }
    }

    // AI and Logging moved to top

    fun toggleAiMonitoring(enabled: Boolean) {
        _isAiMonitoring.value = enabled
        if (enabled) {
            voiceFeedbackManager.speak("Monitoreo de inteligencia artificial activado.", "AI monitoring activated.")
            startAiMonitoring()
        } else {
            voiceFeedbackManager.speak("Monitoreo de inteligencia artificial desactivado.", "AI monitoring deactivated.")
            aiMonitorJob?.cancel()
        }
    }

    private fun startAiMonitoring() {
        aiMonitorJob?.cancel()
        aiMonitorJob = viewModelScope.launch {
            while (_isAiMonitoring.value) {
                if (connectionState.value == ObdState.CONNECTED && telemetryHistory.value.isNotEmpty()) {
                    val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "Generic Vehicle"
                    val anomalies = geminiDiagnostic.checkHealth(vehicleInfo, telemetryHistory.value)
                    _anomalousPids.value = anomalies
                    updateHealthScore()
                }
                kotlinx.coroutines.delay(30000) // Check every 30 seconds
            }
        }
    }


    fun startDataLogging() {
        _isLogging.value = true
        _dataLog.value = emptyList()
        voiceFeedbackManager.speak("Registro de datos iniciado.", "Data logging started.")
        loggingJob?.cancel()
        loggingJob = viewModelScope.launch(Dispatchers.IO) {
            val batch = mutableListOf<com.elysium369.meet.data.local.entities.SensorHistoryEntity>()
            var lastInsert = System.currentTimeMillis()

            // Corrutina para el logging CSV en memoria
            launch {
                while (_isLogging.value) {
                    val current = _liveData.value
                    if (current.isNotEmpty()) {
                        _dataLog.value = _dataLog.value + DataLogEntry(System.currentTimeMillis(), current)
                    }
                    kotlinx.coroutines.delay(500)
                }
            }

            // Corrutina recolectora para el búfer asíncrono hacia Room
            for (entity in telemetryBuffer) {
                batch.add(entity)
                val now = System.currentTimeMillis()
                // Insertar cada 100 registros o cada 1 segundo
                if (batch.size >= 100 || (now - lastInsert > 1000 && batch.isNotEmpty())) {
                    try {
                        sensorHistoryDao.insertAll(batch.toList())
                    } catch (e: Exception) {
                        Log.e("ObdVM", "Batch insert failed", e)
                    }
                    batch.clear()
                    lastInsert = now
                }
            }
        }
    }

    fun stopDataLogging() {
        _isLogging.value = false
        loggingJob?.cancel()
        voiceFeedbackManager.speak("Registro de datos detenido. Guardando muestras.", "Data logging stopped. Saving samples.")

        // Asegurar vaciado de canal
        viewModelScope.launch(Dispatchers.IO) {
            val remaining = mutableListOf<com.elysium369.meet.data.local.entities.SensorHistoryEntity>()
            var entity = telemetryBuffer.tryReceive().getOrNull()
            while (entity != null) {
                remaining.add(entity)
                entity = telemetryBuffer.tryReceive().getOrNull()
            }
            if (remaining.isNotEmpty()) {
                try {
                    sensorHistoryDao.insertAll(remaining)
                } catch (e: Exception) {
                    Log.e("ObdVM", "Final batch insert failed", e)
                }
            }
        }
    }

    fun saveCsvToFile() {
        val data = _dataLog.value
        if (data.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "EV_Log_${System.currentTimeMillis()}.csv"
                val exportDirectory = java.io.File(
                    context.getExternalFilesDir(null),
                    "TelemetryExports",
                ).apply { mkdirs() }
                val file = java.io.File(exportDirectory, fileName)
                java.io.FileWriter(file).use { writer ->
                    // Header
                    val pids = data.first().values.keys.toList()
                    writer.write("Timestamp," + pids.joinToString(",") + "\n")

                    // Data
                    data.forEach { entry ->
                        val row = mutableListOf<String>()
                        row.add(entry.timestamp.toString())
                        pids.forEach { pid ->
                            row.add(entry.values[pid]?.toString() ?: "")
                        }
                        writer.write(row.joinToString(",") + "\n")
                    }
                }

                // Share file
                shareFile(file)
            } catch (e: Exception) {
                android.util.Log.e("ObdVM", "CSV export failed: ${e.message}", e)
                _cloudSyncState.value = "❌ Error al exportar CSV: ${e.message}"
            }
        }
    }

    private fun shareFile(file: java.io.File) {
        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir Log de Elysium Vanguard AI OS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }


    private fun updateHealthScore() {
        _healthScore.value = calculateHealthScore()
    }

    private fun calculateHealthScore(
        active: List<String> = activeDtcs.value,
        pending: List<String> = pendingDtcs.value,
        anomalies: List<com.elysium369.meet.core.ai.HealthAnomaly> = _anomalousPids.value,
        live: Map<String, Float> = _liveData.value
    ): Int {
        var score = 100

        // Subtract for DTCs (High priority)
        score -= (active.size * 25)
        score -= (pending.size * 10)

        // Subtract for AI Anomalies (Medium priority)
        score -= (anomalies.size * 15)

        // Critical Sensor Thresholds
        // 1. Engine Coolant Temperature (PID 0105)
        val temp = live["0105"]
        if (temp != null) {
            if (temp > 115f) score -= 30 // Overheating
            else if (temp > 105f) score -= 10 // Warning
        }

        // 2. Control Module Voltage (PID 0142)
        // Note: ATRV (ELM327 internal voltage) is not stored in liveData map
        val voltage = live["0142"]
        if (voltage != null) {
            if (voltage < 11.5f) score -= 20 // Low battery/alternator
            else if (voltage < 12.2f && (live["010C"] ?: 0f) < 100f) score -= 5 // Weak battery at rest
        }

        // 3. Fuel Trims (PID 0106, 0107) - Long term trim
        val ltft = live["0107"] ?: live["0109"]
        if (ltft != null && (ltft > 15f || ltft < -15f)) {
            score -= 10 // Fuel system richness/leanness
        }

        // 4. Misfires (Count detected or erratic RPM)
        // (Placeholder for more complex logic if misfire PIDs are available)

        return score.coerceIn(5, 100)
    }

    // --- Predictive Health Analysis ---
    fun runPredictiveAnalysis() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingHealth.value = true
            voiceFeedbackManager.speak("Ejecutando análisis predictivo de salud del vehículo.", "Running predictive vehicle health analysis.")
            try {
                val vehicle = _selectedVehicle.value
                if (vehicle != null) {
                    val report = predictiveHealthEngine.computeHealthReport(
                        vehicleId = vehicle.id,
                        currentLiveData = _liveData.value,
                        activeDtcCount = activeDtcs.value.size,
                        pendingDtcCount = pendingDtcs.value.size,
                        anomalyCount = _anomalousPids.value.size
                    )
                    _predictiveHealthReport.value = report
                    voiceFeedbackManager.speak(
                        "Análisis predictivo completado. Revise el reporte de salud.",
                        "Predictive analysis complete. Review the health report."
                    )
                } else {
                    // No vehicle selected — compute with empty vehicle ID
                    val report = predictiveHealthEngine.computeHealthReport(
                        vehicleId = "default",
                        currentLiveData = _liveData.value,
                        activeDtcCount = activeDtcs.value.size,
                        pendingDtcCount = pendingDtcs.value.size,
                        anomalyCount = _anomalousPids.value.size
                    )
                    _predictiveHealthReport.value = report
                }
                computeCivilizationalScore()
                recordVehicleTimelineEvent(
                    eventType = com.elysium369.meet.core.vehicle.VehicleEventType.OBD_SCAN,
                    title = "Escaneo Preventivo & Salud General",
                    description = "Score de salud calculado: ${_civilizationalHealthScore.value?.score ?: 850}/1000. DTCs activos: ${activeDtcs.value.size}.",
                    dtcs = activeDtcs.value
                )
            } catch (e: Exception) {
                Log.e("ObdVM", "Predictive analysis failed", e)
            } finally {
                _isAnalyzingHealth.value = false
            }
        }
    }

    suspend fun analyzeOscilloscopeTelemetry(vehicleInfo: String, data: Map<String, List<Pair<Long, Float>>>): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
        )
        return geminiDiagnostic.analyzeLiveTelemetry(vehicleInfo, data)
    }

    suspend fun analyzeNetworkTopology(vehicleInfo: String, modules: List<com.elysium369.meet.core.obd.NetworkModule>): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
        )
        return geminiDiagnostic.analyzeNetworkTopology(vehicleInfo, modules)
    }

    suspend fun analyzeActiveTest(
        vehicleInfo: String,
        testName: String,
        testId: String,
        monitoredData: Map<String, Float>
    ): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
        )
        return geminiDiagnostic.analyzeActiveTest(vehicleInfo, testName, testId, monitoredData)
    }

    private val _aiActiveTestResult = MutableStateFlow<String?>(null)
    val aiActiveTestResult: StateFlow<String?> = _aiActiveTestResult.asStateFlow()

    private val _isAiActiveTestLoading = MutableStateFlow(false)
    val isAiActiveTestLoading: StateFlow<Boolean> = _isAiActiveTestLoading.asStateFlow()

    fun runActiveTestAiDiagnostic(testName: String, testId: String, monitoredData: Map<String, Float>) {
        viewModelScope.launch {
            _isAiActiveTestLoading.value = true
            _aiActiveTestResult.value = null
            try {
                val vehicleInfo = "${selectedVehicle.value?.make ?: "Genérico"} ${selectedVehicle.value?.model ?: "OBD2"} ${selectedVehicle.value?.year ?: ""}"
                val result = analyzeActiveTest(vehicleInfo, testName, testId, monitoredData)
                _aiActiveTestResult.value = result.analysisText
            } catch (e: Exception) {
                _aiActiveTestResult.value = "Error al solicitar diagnóstico de IA: ${e.message}"
            } finally {
                _isAiActiveTestLoading.value = false
            }
        }
    }

    fun clearActiveTestAiDiagnostic() {
        _aiActiveTestResult.value = null
        _isAiActiveTestLoading.value = false
    }

    private val _aiServiceResetResult = MutableStateFlow<String?>(null)
    val aiServiceResetResult: StateFlow<String?> = _aiServiceResetResult.asStateFlow()

    private val _isAiServiceResetLoading = MutableStateFlow(false)
    val isAiServiceResetLoading: StateFlow<Boolean> = _isAiServiceResetLoading.asStateFlow()

    fun runServiceResetAiDiagnostic(resetName: String, resetId: String, isSuccess: Boolean) {
        viewModelScope.launch {
            _isAiServiceResetLoading.value = true
            _aiServiceResetResult.value = null
            try {
                val vehicleInfo = "${selectedVehicle.value?.make ?: "Genérico"} ${selectedVehicle.value?.model ?: "OBD2"} ${selectedVehicle.value?.year ?: ""}"
                val mfr = _manufacturer.value
                val result = geminiDiagnostic.analyzeServiceReset(vehicleInfo, resetName, resetId, mfr, isSuccess)
                _aiServiceResetResult.value = result.analysisText
            } catch (e: Exception) {
                _aiServiceResetResult.value = "Error al solicitar procedimiento de IA: ${e.message}"
            } finally {
                _isAiServiceResetLoading.value = false
            }
        }
    }

    fun clearServiceResetAiDiagnostic() {
        _aiServiceResetResult.value = null
        _isAiServiceResetLoading.value = false
    }

    /** Toggle Fused Speed Enabled — uses phone sensors + GPS for smooth speed tracking */
    fun setFusedSpeedEnabled(enabled: Boolean) {
        _fusedSpeedEnabled.value = enabled
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("fused_speed_enabled", enabled).apply()
        Log.d("ObdVM", "Fused Speed: $enabled")
        if (enabled) {
            phoneSpeedTracker.start()
        } else {
            phoneSpeedTracker.stop()
        }
    }

    // --- Navigation Flow for Voice Copilot ---
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val navigationEvent = _navigationEvent.asSharedFlow()

    // Voice copilot active listening state
    val isVoiceCopilotListening: StateFlow<Boolean> = voiceCommandManager.isListeningState

    fun toggleVoiceCopilot(enabled: Boolean) {
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("voice_copilot_enabled", enabled).apply()
        _voiceCopilotEnabled.value = enabled
        if (enabled) {
            voiceCommandManager.startCopilot()
            speechService.startListeningToEvents()
            notificationService.startListeningToEvents()
        } else {
            voiceCommandManager.stopCopilot()
            speechService.stopListeningToEvents()
            notificationService.stopListeningToEvents()
        }
    }

    fun cycleGaugeStyle() {
        gaugeStyleManager.cycleNext()
    }

    private fun handleVoiceCommand(command: com.elysium369.meet.core.audio.VoiceCommand) {
        viewModelScope.launch {
            when (command) {
                com.elysium369.meet.core.audio.VoiceCommand.CYCLE_GAUGES -> {
                    cycleGaugeStyle()
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_DASHBOARD -> {
                    _navigationEvent.emit("scanner")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_DIAGNOSTICS -> {
                    _navigationEvent.emit("dtc")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_OSCILLOSCOPE -> {
                    _navigationEvent.emit("oscilloscope")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_LOCATOR -> {
                    _navigationEvent.emit("component_locator")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_SETTINGS -> {
                    _navigationEvent.emit("settings")
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_TEMPERATURE -> {
                    speakCoolantTemperature()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_VOLTAGE -> {
                    speakBatteryVoltage()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_RPM -> {
                    speakEngineRpm()
                }
                com.elysium369.meet.core.audio.VoiceCommand.CLEAR_DTCS -> {
                    clearDtcs()
                }
                com.elysium369.meet.core.audio.VoiceCommand.DISABLE_ALERTS -> {
                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_feedback_enabled", false).apply()
                }
                com.elysium369.meet.core.audio.VoiceCommand.ENABLE_ALERTS -> {
                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_feedback_enabled", true).apply()
                }
                com.elysium369.meet.core.audio.VoiceCommand.DEACTIVATE_COPILOT -> {
                    toggleVoiceCopilot(false)
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_DIAGNOSTICS -> {
                    speakDiagnostics()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_FUEL_ECONOMY -> {
                    speakFuelEconomy()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_VEHICLE_DNA -> {
                    speakVehicleDna()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_PERITO_REPORT -> {
                    speakPeritoReport()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_GENERAL_STATUS -> {
                    speakGeneralStatus()
                }
            }
        }
    }

    private fun speakDiagnostics() {
        val activeCount = canonicalActiveFindingSummaries.value.size
        val pendingCount = canonicalPendingFindingSummaries.value.size
        val report = activePeritoReport.value
        val scan = lastDtcScanReport.value
        val msgEs = if (activeCount == 0 && pendingCount == 0) {
            when (scan?.completeness) {
                ScanCompleteness.COMPLETE -> "Los módulos cubiertos no reportaron códigos activos ni pendientes. Esto no descarta fallas no monitorizadas."
                ScanCompleteness.PARTIAL -> "El escaneo fue parcial: los módulos cubiertos no reportaron códigos activos ni pendientes, pero quedan módulos sin verificar."
                else -> "No hay evidencia suficiente para afirmar ausencia de códigos activos o pendientes."
            }
        } else {
            "El diagnóstico registra $activeCount hallazgos activos y $pendingCount pendientes." +
                (report?.let { " El peritaje vigente estima ${it.estimatedRepairCost.toInt()} dólares; confirma cotización y alcance." } ?: " El costo aún no fue cotizado.")
        }
        val msgEn = if (activeCount == 0 && pendingCount == 0) {
            when (scan?.completeness) {
                ScanCompleteness.COMPLETE -> "Covered modules reported no active or pending codes. This does not rule out unmonitored faults."
                ScanCompleteness.PARTIAL -> "The scan was partial: covered modules reported no active or pending codes, but some modules remain unverified."
                else -> "There is not enough evidence to claim the absence of active or pending codes."
            }
        } else {
            "Diagnostics record $activeCount active findings and $pendingCount pending findings." +
                (report?.let { " The current inspection estimates ${it.estimatedRepairCost.toInt()} dollars; confirm scope and quote." } ?: " Repair cost has not been quoted.")
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakFuelEconomy() {
        val economy = liveData.value["015E"] ?: liveData.value["0110"]?.let { maf -> maf / 10.7f }
        if (economy == null) {
            voiceFeedbackManager.speak("Consumo no capturado en esta sesión.", "Fuel economy was not captured in this session.")
        } else {
            voiceFeedbackManager.speak(
                "El consumo calculado con los datos disponibles es de ${String.format("%.1f", economy)} litros por cada cien kilómetros.",
                "Calculated fuel economy from available data is ${String.format("%.1f", economy)} liters per hundred kilometers.",
            )
        }
    }

    private fun speakVehicleDna() {
        val dna = dnaResult.value
        val msgEs = if (!dna.isCalibrated) {
            "La firma digital Elysium Vanguard AI OS DNA aún no está calibrada para este vehículo. Por favor, realice una corrida de calibración en la pantalla DNA."
        } else if (dna.isAnomalous) {
            "Alerta preventiva de comportamiento: El score de salud es del ${dna.healthScore} por ciento. Se detecta una desviación estadística anómala en los sensores."
        } else {
            "Firma digital Elysium Vanguard AI OS DNA calibrada al ${dna.confidence.toInt()} por ciento de confianza. El vehículo se comporta de forma normal con un score de salud del ${dna.healthScore} por ciento."
        }
        val msgEn = if (!dna.isCalibrated) {
            "The Elysium Vanguard AI OS DNA digital signature is not yet calibrated for this vehicle. Please perform a calibration drive in the DNA section."
        } else if (dna.isAnomalous) {
            "Preventive behavior alert: The health score is ${dna.healthScore} percent. Statistical anomaly detected in sensors."
        } else {
            "Elysium Vanguard AI OS DNA signature calibrated at ${dna.confidence.toInt()} percent confidence. The vehicle behaves normally with a health score of ${dna.healthScore} percent."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakPeritoReport() {
        val report = activePeritoReport.value
        val msgEs = if (report != null) {
            "El último reporte Elysium Vanguard AI OS Perito indica un score clínico de ${report.score0to100} sobre cien, con clasificación ${report.category}."
        } else {
            "No se ha realizado ningún peritaje clínico Elysium Vanguard AI OS Perito para este vehículo en esta sesión."
        }
        val msgEn = if (report != null) {
            "The latest Elysium Vanguard AI OS Perito report shows a clinical score of ${report.score0to100} out of one hundred, categorized as ${report.category}."
        } else {
            "No Elysium Vanguard AI OS Perito clinical check has been executed for this vehicle in this session."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakGeneralStatus() {
        val temp = liveData.value["0105"]?.toInt()?.let { "$it grados centígrados" } ?: "dato no capturado"
        val volt = liveData.value["0142"]?.let { "${String.format("%.1f", it)} voltios" } ?: "dato no capturado"
        val activeCount = canonicalActiveFindingSummaries.value.size
        val dna = dnaResult.value
        val dnaEs = if (dna.isCalibrated) "score DNA ${dna.healthScore} por ciento" else "DNA no calibrado"
        val dnaEn = if (dna.isCalibrated) "DNA score ${dna.healthScore} percent" else "DNA not calibrated"
        val msgEs = "Resumen: refrigerante $temp; voltaje $volt; $activeCount hallazgos DTC activos; $dnaEs."
        val msgEn = "Summary: coolant $temp; voltage $volt; $activeCount active DTC findings; $dnaEn."
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakCoolantTemperature() {
        val temp = liveData.value["0105"]
        if (temp != null) {
            voiceFeedbackManager.speak(
                "La temperatura del refrigerante es de ${temp.toInt()} grados centígrados.",
                "The coolant temperature is ${temp.toInt()} degrees celsius."
            )
        } else {
            voiceFeedbackManager.speak(
                "Aún no hay datos de temperatura disponibles.",
                "Coolant temperature data is not available yet."
            )
        }
    }

    private fun speakBatteryVoltage() {
        val volt = liveData.value["0142"] ?: liveData.value["AT RV"]
        if (volt != null) {
            val rounded = String.format("%.1f", volt)
            voiceFeedbackManager.speak(
                "El voltaje de la batería es de $rounded voltios.",
                "The battery voltage is $rounded volts."
            )
        } else {
            voiceFeedbackManager.speak(
                "Aún no hay datos de voltaje disponibles.",
                "Battery voltage data is not available yet."
            )
        }
    }

    private fun speakEngineRpm() {
        val rpm = liveData.value["010C"]
        if (rpm != null) {
            voiceFeedbackManager.speak(
                "El motor está girando a ${rpm.toInt()} revoluciones por minuto.",
                "The engine is running at ${rpm.toInt()} RPM."
            )
        } else {
            voiceFeedbackManager.speak(
                "El motor está apagado o no hay datos de revoluciones.",
                "Engine is off or RPM data is not available yet."
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ TOW TRUCK SERVICE (Grúas) — Elysium bidding & GPS ████
    // ═══════════════════════════════════════════════════════════════════════════

    val towTruckRequests: StateFlow<List<TowTruckRequestEntity>> = towTruckDao.getRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openTowTruckRequests: StateFlow<List<TowTruckRequestEntity>> = towTruckDao.getOpenRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _towTruckDriverMode = MutableStateFlow(false)
    val towTruckDriverMode: StateFlow<Boolean> = _towTruckDriverMode.asStateFlow()

    fun toggleTowTruckDriverMode() {
        _towTruckDriverMode.value = !_towTruckDriverMode.value
    }

    /** Build a rich vehicle info string from the selected vehicle + active DTCs */
    fun buildVehicleInfoForRequest(): String {
        val v = _selectedVehicle.value
        val dtcs = canonicalActiveFindingSummaries.value
        val sb = StringBuilder()
        if (v != null) {
            sb.append("${v.year} ${v.make} ${v.model}")
            if (v.engine.isNotBlank()) sb.append(" | Motor: ${v.engine}")
            if (v.displacement_cc > 0) sb.append(" ${v.displacement_cc}cc")
            if (v.fuel_type.isNotBlank()) sb.append(" | Combustible: ${v.fuel_type}")
            if (v.transmission_type.isNotBlank()) sb.append(" | Trans: ${v.transmission_type}")
            if (v.vin.isNotBlank()) sb.append(" | VIN: ${v.vin}")
        } else {
            sb.append("Vehículo no registrado")
        }
        if (dtcs.isNotEmpty()) {
            sb.append(" | DTCs activos: ${dtcs.joinToString(", ") { it.code }}")
        }
        return sb.toString()
    }

    /** Build vehicle info from a specific DTC code for 1-click service request */
    fun buildVehicleInfoForDtc(dtcCode: String, dtcDescription: String, isManualEntry: Boolean = false): String {
        val v = _selectedVehicle.value
        val sb = StringBuilder()
        if (v != null) {
            sb.append("${v.year} ${v.make} ${v.model}")
            if (v.engine.isNotBlank()) sb.append(" | Motor: ${v.engine}")
            if (v.displacement_cc > 0) sb.append(" ${v.displacement_cc}cc")
            if (v.fuel_type.isNotBlank()) sb.append(" | Combustible: ${v.fuel_type}")
            if (v.transmission_type.isNotBlank()) sb.append(" | Trans: ${v.transmission_type}")
            if (v.vin.isNotBlank()) sb.append(" | VIN: ${v.vin}")
        } else {
            sb.append("Vehículo no registrado")
        }
        val tag = if (isManualEntry) "✍️ [INGRESADO MANUALMENTE POR USUARIO]" else "🔍 [DETECTADO VÍA ESCÁNER OBD-II]"
        sb.append(" | ⚠️ DTC: $dtcCode — $dtcDescription | $tag")
        return sb.toString()
    }

    /** Client creates a new tow truck / mechanic request via authoritative TowCommandRepository */
    fun createTowTruckRequest(
        latitude: Double,
        longitude: Double,
        locationName: String,
        destLat: Double? = null,
        destLng: Double? = null,
        destName: String? = null,
        phone: String,
        priceOffer: Double,
        vehicleInfoOverride: String? = null
    ) {
        val custId = runCatching { java.util.UUID.fromString(currentUserId ?: "") }
            .getOrElse { java.util.UUID.nameUUIDFromBytes((currentUserId ?: "anon").toByteArray()) }
        val veh = _selectedVehicle.value
        val summary = vehicleInfoOverride ?: buildVehicleInfoForRequest()
        viewModelScope.launch {
            towCommandRepository.requestTow(
                customerId = custId,
                customerName = passengerVerification.value?.fullName ?: "Cliente",
                customerPhone = phone,
                vehicleVin = veh?.vin,
                vehicleSummary = summary,
                pickupLocation = com.elysium369.meet.core.geo.GeoPoint(latitude, longitude),
                pickupAddress = locationName,
                destinationLocation = if (destLat != null && destLng != null) com.elysium369.meet.core.geo.GeoPoint(destLat, destLng) else null,
                destinationAddress = destName,
                requiredCapabilities = setOf(com.elysium369.meet.core.services.tow.TowCapabilities.FLATBED),
                estimatedPrice = if (priceOffer > 0.0) com.elysium369.meet.core.money.Money.ofCrc(priceOffer.toLong()) else null
            )
        }
    }

    /** Driver/mechanic takes an open request via authoritative TowCommandRepository */
    fun takeTowTruckRequest(requestId: String, driverId: String, driverName: String, driverPhone: String) {
        val jId = runCatching { java.util.UUID.fromString(requestId) }.getOrNull() ?: return
        val current = towCommandRepository.getJobById(jId) ?: return
        val opId = runCatching { java.util.UUID.fromString(driverId) }.getOrElse { java.util.UUID.nameUUIDFromBytes(driverId.toByteArray()) }
        towCommandRepository.executeAction(
            jobId = jId,
            action = com.elysium369.meet.core.services.tow.TowAction.AssignOperator(operatorId = opId, towUnitId = driverId),
            actorRole = com.elysium369.meet.core.services.kernel.ServiceRole.TOW_OPERATOR,
            expectedVersion = current.serverVersion
        )
        // ─── Direct Supabase sync for tow truck acceptance ───
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest["tow_truck_requests"].update(
                    mapOf(
                        "status" to "TAKEN",
                        "assigned_driver_id" to driverId,
                        "assigned_driver_name" to driverName,
                        "assigned_driver_phone" to driverPhone,
                        "taken_at" to System.currentTimeMillis().toString(),
                    )
                ) {
                    filter { eq("request_id", requestId) }
                }
            }.onFailure { Log.w("ObdViewModel", "Tow take: Supabase sync failed", it) }
        }
    }

    /** Mark a request as completed via authoritative TowCommandRepository */
    fun completeTowTruckRequest(requestId: String) {
        val jId = runCatching { java.util.UUID.fromString(requestId) }.getOrNull() ?: return
        val current = towCommandRepository.getJobById(jId) ?: return
        towCommandRepository.executeAction(
            jobId = jId,
            action = com.elysium369.meet.core.services.tow.TowAction.CompleteService,
            actorRole = com.elysium369.meet.core.services.kernel.ServiceRole.CUSTOMER,
            expectedVersion = current.serverVersion
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest["tow_truck_requests"].update(
                    mapOf("status" to "COMPLETED", "completed_at" to System.currentTimeMillis().toString())
                ) { filter { eq("request_id", requestId) } }
            }.onFailure { Log.w("ObdViewModel", "Tow complete: Supabase sync failed", it) }
        }
    }

    /** Cancel a request via authoritative TowCommandRepository */
    fun cancelTowTruckRequest(requestId: String) {
        val jId = runCatching { java.util.UUID.fromString(requestId) }.getOrNull() ?: return
        val current = towCommandRepository.getJobById(jId) ?: return
        towCommandRepository.executeAction(
            jobId = jId,
            action = com.elysium369.meet.core.services.tow.TowAction.Cancel("Cancelado por el usuario"),
            actorRole = com.elysium369.meet.core.services.kernel.ServiceRole.CUSTOMER,
            expectedVersion = current.serverVersion
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest["tow_truck_requests"].update(
                    mapOf("status" to "CANCELLED", "cancelled_at" to System.currentTimeMillis().toString())
                ) { filter { eq("request_id", requestId) } }
            }.onFailure { Log.w("ObdViewModel", "Tow cancel: Supabase sync failed", it) }
        }
    }

    /** User manually deletes a completed/cancelled request */
    fun deleteTowTruckRequest(requestId: String) {
        viewModelScope.launch {
            towJobDao.deleteJob(requestId)
            towTruckDao.deleteRequest(requestId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ ELYSIUM RATING SYSTEM — verified 5-star ratings ████
    // ═══════════════════════════════════════════════════════════════════════════

    val allRatings: StateFlow<List<RatingEntity>> = ratingDao.getAllRatingsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun submitRating(
        targetType: String,  // MECHANIC, STORE, TOW_TRUCK, CLIENT
        targetId: String,
        sourceName: String,
        stars: Double,
        comment: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ratingId = java.util.UUID.randomUUID().toString()
            val sourceId = _selectedVehicle.value?.user_id ?: "anonymous"
            val now = System.currentTimeMillis()
            val rating = RatingEntity(
                ratingId = ratingId,
                targetType = targetType,
                targetId = targetId,
                sourceId = sourceId,
                sourceName = sourceName,
                stars = stars.coerceIn(1.0, 5.0),
                comment = comment,
                createdAt = now,
            )
            ratingDao.insertRating(rating)
            // ─── Sync rating to Supabase ───
            runCatching {
                SupabaseManager.client.postgrest["ratings"].upsert(
                    mapOf(
                        "rating_id" to ratingId,
                        "target_type" to targetType,
                        "target_id" to targetId,
                        "source_id" to sourceId,
                        "source_name" to sourceName,
                        "stars" to stars.coerceIn(1.0, 5.0),
                        "comment" to comment,
                        "created_at" to now.toString(),
                    )
                )
            }.onFailure {
                Log.w("ObdViewModel", "Rating sync to Supabase failed for $targetType:$targetId", it)
            }
        }
    }

    suspend fun getAverageRating(targetType: String, targetId: String): Double {
        return ratingDao.getAverageRatingForTarget(targetType, targetId) ?: 5.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ 72-HOUR AUTO-CLEANUP — Purge completed/cancelled requests ████
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        // Launch a coroutine that runs every 15 minutes to clean up old requests
        viewModelScope.launch {
            while (true) {
                try {
                    val seventyTwoHoursAgo = System.currentTimeMillis() - (72 * 60 * 60 * 1000L)
                    towJobDao.purgeOldJobs(seventyTwoHoursAgo)
                    towTruckDao.purgeOldRequests(seventyTwoHoursAgo)
                    diagnosticEvidenceDao.purgeExpiredUnreferencedExchanges(System.currentTimeMillis())
                    diagnosticEvidenceDao.purgeOrphanedEncryptedBlobs()
                    diagnosticEvidenceDao.purgeOrphanedSessionIntegrity()
                } catch (e: Exception) {
                    android.util.Log.e("ObdViewModel", "Auto-cleanup failed", e)
                }
                kotlinx.coroutines.delay(15 * 60 * 1000L) // every 15 minutes
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ GPS / WAZE / WHATSAPP LOCATION SHARING UTILITIES ████
    // ═══════════════════════════════════════════════════════════════════════════

    data class GpsLocationInfo(
        val latitude: Double,
        val longitude: Double,
        val addressName: String,
        val countryCode: String,      // e.g. "CR"
        val dialingPrefix: String,     // e.g. "+506"
        val accuracy: Float = 0f,
        val speed: Float = 0f,
        val bearing: Float = 0f,
        val timestamp: Long = 0L
    )

    private val countryPrefixMap = mapOf(
        "CR" to "+506", "MX" to "+52", "CO" to "+57", "AR" to "+54",
        "CL" to "+56", "PE" to "+51", "EC" to "+593", "PA" to "+507",
        "UY" to "+598", "VE" to "+58", "GT" to "+502", "HN" to "+504",
        "SV" to "+503", "NI" to "+505", "PY" to "+595", "BO" to "+591",
        "DO" to "+1", "PR" to "+1", "US" to "+1", "ES" to "+34"
    )

    private val _currentGpsLocation = MutableStateFlow<GpsLocationInfo?>(null)
    val currentGpsLocation: StateFlow<GpsLocationInfo?> = _currentGpsLocation.asStateFlow()

    /** Trigger GPS detection, geocoding and dialing prefix detection using high-accuracy FusedLocationProviderClient */
    fun detectCurrentLocation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("ObdViewModel", "GPS permissions not granted")
                    return@launch
                }

                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)

                // Intentar obtener la última ubicación conocida primero
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    val ageMs = location?.let { System.currentTimeMillis() - it.time }
                    if (location != null && location.accuracy in 0f..30f && ageMs != null && ageMs in 0L..30_000L) {
                        resolveLocationDetails(context, location)
                    } else {
                        // Si es nula o imprecisa, forzar una actualización fresca de alta precisión
                        requestFreshLocation(context, fusedLocationClient)
                    }
                }.addOnFailureListener {
                    requestFreshLocation(context, fusedLocationClient)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error starting FusedLocation check", e)
            }
        }
    }

    private fun requestFreshLocation(context: Context, client: com.google.android.gms.location.FusedLocationProviderClient) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return

            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                2000
            ).apply {
                setMinUpdateDistanceMeters(0f)
                setWaitForAccurateLocation(true)
                setMaxUpdates(1)
            }.build()

            client.requestLocationUpdates(locationRequest, object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    val location = locationResult.lastLocation
                    val ageMs = location?.let { System.currentTimeMillis() - it.time }
                    if (location != null && location.accuracy > 0f && location.accuracy <= 100f && ageMs != null && ageMs in 0L..30_000L) {
                        resolveLocationDetails(context, location)
                    }
                }
            }, android.os.Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Error requesting fresh high-accuracy location", e)
        }
    }

    private fun resolveLocationDetails(context: Context, location: android.location.Location) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val address = addresses?.firstOrNull()
                val countryCode = address?.countryCode ?: ""
                val addressLine = address?.getAddressLine(0) ?: "Ubicación GPS detectada"
                val prefix = countryPrefixMap[countryCode.uppercase()] ?: "+506"

                _currentGpsLocation.value = GpsLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    addressName = addressLine,
                    countryCode = countryCode,
                    dialingPrefix = prefix,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing,
                    timestamp = location.time
                )
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Geocoder address look up failed", e)
                _currentGpsLocation.value = GpsLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    addressName = "Ubicación GPS detectada",
                    countryCode = "",
                    dialingPrefix = "+506",
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing,
                    timestamp = location.time
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Pending Messages (ChatDao → HomeActivityStrip)
    // ═══════════════════════════════════════════════════════════════

    val pendingMessageCount: StateFlow<Int> = flow {
        while (true) {
            val userId = currentCloudUserId() ?: run {
                emit(0)
                kotlinx.coroutines.delay(10_000L)
                return@flow
            }
            val count = chatDao.observeUnreadCount(userId).first()
            emit(count)
            kotlinx.coroutines.delay(15_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000), 0)

    // ═══════════════════════════════════════════════════════════════
    // Elysium Vanguard Viajes business logic
    // ═══════════════════════════════════════════════════════════════

    // Strangler migration: ride completion logic delegated to RideCompletionHelper
    private val rideCompletionHelper = com.elysium369.meet.ride.domain.RideCompletionHelper(
        scope = viewModelScope,
        context = context,
        noticeEmitter = { msg -> _rideVerificationNotice.emit(msg) },
        rideDao = rideDao,
        currentUserId = { currentCloudUserId() },
    )

    val rideRequests = rideDao.getAllRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openRideRequests = rideDao.getOpenRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeRideRequest = MutableStateFlow<RideRequestEntity?>(null)
    val activeRideRequest: StateFlow<RideRequestEntity?> = _activeRideRequest.asStateFlow()

    private val _pendingRatingRide = MutableStateFlow<RideRequestEntity?>(null)
    val pendingRatingRide: StateFlow<RideRequestEntity?> = _pendingRatingRide.asStateFlow()
    private val dismissedRatingRideIds = mutableSetOf<String>()
    private val ratingPrefs by lazy {
        context.getSharedPreferences("meet_ride_ratings_settled", Context.MODE_PRIVATE)
    }

    fun isRatingSettled(requestId: String, role: String): Boolean {
        if (requestId.isBlank()) return false
        val key = "settled_${requestId}_${role.uppercase()}"
        return ratingPrefs.getBoolean(key, false) || requestId in dismissedRatingRideIds
    }

    fun markRatingSettled(requestId: String, role: String) {
        if (requestId.isBlank()) return
        dismissedRatingRideIds.add(requestId)
        ratingPrefs.edit().putBoolean("settled_${requestId}_${role.uppercase()}", true).apply()
        if (_pendingRatingRide.value?.requestId == requestId) {
            _pendingRatingRide.value = null
        }
    }

    fun dismissPendingRating() {
        val ride = _pendingRatingRide.value
        if (ride != null) {
            val role = if (_rideDriverMode.value) "DRIVER" else "PASSENGER"
            markRatingSettled(ride.requestId, role)
            // If the ride is COMPLETED, clear it as the active ride so reconciliation
            // doesn't keep re-triggering the rating dialog on every projection refresh.
            if (ride.serverState == "COMPLETED" && _activeRideRequest.value?.requestId == ride.requestId) {
                viewModelScope.launch(Dispatchers.IO) {
                    val ownerId = currentCloudUserId() ?: return@launch
                    val roleKey = com.elysium369.meet.ride.domain.RideRoleContextPolicy
                        .selectionOwnerKey(ownerId, _rideDriverMode.value) ?: return@launch
                    rideDao.clearActiveRideSelection(roleKey)
                    withContext(Dispatchers.Main) {
                        applyActiveRide(null)
                    }
                }
            }
        }
        _pendingRatingRide.value = null
    }

    fun isRatingDismissed(requestId: String): Boolean {
        val role = if (_rideDriverMode.value) "DRIVER" else "PASSENGER"
        return isRatingSettled(requestId, role)
    }

    fun promptRideRating(ride: RideRequestEntity) {
        val role = if (_rideDriverMode.value) "DRIVER" else "PASSENGER"
        val existingRating = if (_rideDriverMode.value) ride.driverRating else ride.passengerRating
        // Guard: only prompt if the ride is truly server-COMPLETED, unrated for this role,
        // not already dismissed, and the current user actually participated in this ride.
        val myId = currentUserId ?: activePrincipalKernel.current().id
        val isMyRide = if (_rideDriverMode.value) {
            ride.assignedDriverId == myId
        } else {
            ride.passengerId == myId
        }
        if (ride.serverVersion > 0L && ride.serverState == "COMPLETED" &&
            existingRating == null && !isRatingSettled(ride.requestId, role) &&
            isMyRide
        ) {
            _pendingRatingRide.value = ride
        }
    }

    private val _rideOffers = MutableStateFlow<List<RideOfferEntity>>(emptyList())
    val rideOffers: StateFlow<List<RideOfferEntity>> = _rideOffers.asStateFlow()

    private val _rideChatMessages = MutableStateFlow<List<RideChatMessageEntity>>(emptyList())
    val rideChatMessages: StateFlow<List<RideChatMessageEntity>> = _rideChatMessages.asStateFlow()
    val rideLostItemReports = rideDao.getLostItemReportsFlow()

    fun refreshRideLostItemReports(requestIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            requestIds.distinct().forEach { syncRideChat(it) }
        }
    }

    private val _rideDriverVehicles = MutableStateFlow<List<RideDriverVehicleSummary>>(emptyList())
    val rideDriverVehicles: StateFlow<List<RideDriverVehicleSummary>> = _rideDriverVehicles.asStateFlow()

    private val _rideSharingSelections =
        MutableStateFlow<Map<String, Set<RideShareCategory>>>(emptyMap())
    val rideSharingSelections: StateFlow<Map<String, Set<RideShareCategory>>> =
        _rideSharingSelections.asStateFlow()

    private val _rideRoadIncidents = MutableStateFlow<List<RideRoadIncident>>(emptyList())
    val rideRoadIncidents: StateFlow<List<RideRoadIncident>> = _rideRoadIncidents.asStateFlow()

    private val _rideSpeedSamples =
        MutableStateFlow<Map<String, List<RideSegmentSpeedSample>>>(emptyMap())
    val rideSpeedSamples: StateFlow<Map<String, List<RideSegmentSpeedSample>>> =
        _rideSpeedSamples.asStateFlow()
    private val lastUploadedRideSpeedBucket = mutableMapOf<String, Long>()

    sealed interface RideClaimUiState {
        data object Idle : RideClaimUiState
        data class Pending(val requestId: String) : RideClaimUiState
        data class Won(val requestId: String) : RideClaimUiState
        data class Rejected(val requestId: String, val code: String, val message: String) : RideClaimUiState
    }

    private val _rideClaimUiState = MutableStateFlow<RideClaimUiState>(RideClaimUiState.Idle)
    val rideClaimUiState: StateFlow<RideClaimUiState> = _rideClaimUiState.asStateFlow()
    private val rideClaimEnqueueMutex = Mutex()

    private val _rideClaimFeedback = MutableSharedFlow<RideClaimFeedback>(extraBufferCapacity = 8)
    val rideClaimFeedback: SharedFlow<RideClaimFeedback> = _rideClaimFeedback.asSharedFlow()

    private val _ridePinFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ridePinFeedback: SharedFlow<String> = _ridePinFeedback.asSharedFlow()
    private val _rideSafetyFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSafetyFeedback: SharedFlow<String> = _rideSafetyFeedback.asSharedFlow()
    private val _rideRoadReportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideRoadReportFeedback: SharedFlow<String> = _rideRoadReportFeedback.asSharedFlow()
    private val _rideSupportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSupportFeedback: SharedFlow<String> = _rideSupportFeedback.asSharedFlow()

    private val _driverPresetMessages = MutableStateFlow<List<String>>(listOf(
        "Ya me encuentro en la ubicación",
        "Voy en camino, llego en unos 5 minutos",
        "Estoy parado en el semáforo/esquina",
        "Hola, ya inicié el viaje",
        "Estoy afuera con las luces intermitentes encendidas"
    ))
    val driverPresetMessages: StateFlow<List<String>> = _driverPresetMessages.asStateFlow()

    private var selectedRideRequestId: String? = null
    private var jobActiveRideCollection: Job? = null
    private var jobOffersCollection: Job? = null
    private var jobChatCollection: Job? = null
    private var jobChatRemoteSync: Job? = null
    private var jobChatBridgeObservation: Job? = null
    private var rideProjectionJob: Job? = null
    @Serializable
    private data class RidePresenceSequence(@SerialName("location_seq") val locationSeq: Long = 0)

    private suspend fun loadRidePresenceSequence(ownerId: String): Long =
        SupabaseManager.client.postgrest["ride_driver_presence"]
            .select {
                filter { eq("driver_id", ownerId) }
                limit(1)
            }
            .decodeList<RidePresenceSequence>()
            .firstOrNull()?.locationSeq ?: error("No existe presencia para este chofer")

    private fun stopRideDriverPresenceHeartbeat() {
        rideDriverPresenceJob?.cancel()
        rideDriverPresenceJob = null
        _rideDriverPresenceHealthy.value = false
    }

    private fun startRideDriverPresenceHeartbeat(ownerId: String) {
        stopRideDriverPresenceHeartbeat()
        rideDriverPresenceJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _rideDriverMode.value && currentCloudUserId() == ownerId) {
                if (!RideVerificationPolicy.grantsAccess(driverVerification.value?.status)) {
                    _rideDriverPresenceHealthy.value = false
                    delay(30_000L)
                    continue
                }
                val sequence = runCatching { loadRidePresenceSequence(ownerId) }.getOrNull()
                if (sequence == null) {
                    _rideDriverPresenceHealthy.value = false
                    delay(30_000L)
                    continue
                }
                detectCurrentLocation(context)
                delay(3_000L)
                val gps = _currentGpsLocation.value
                val usable = gps != null && com.elysium369.meet.ride.driver.RideDriverPresenceFixPolicy.mayPublish(
                    gps.latitude, gps.longitude, gps.accuracy, gps.timestamp, System.currentTimeMillis(),
                )
                if (usable) {
                    val fix = requireNotNull(gps)
                    runCatching {
                        SupabaseManager.client.postgrest.rpc(
                            "ride_update_driver_location_v1",
                            buildJsonObject {
                                put("p_latitude", fix.latitude)
                                put("p_longitude", fix.longitude)
                                put("p_accuracy", fix.accuracy)
                                put("p_heading", fix.bearing.toInt().coerceIn(0, 359))
                                put("p_speed", fix.speed.coerceAtLeast(0f))
                                put("p_seq", sequence + 1L)
                            },
                        )
                    }.onSuccess {
                        _rideDriverPresenceHealthy.value = true
                        Log.i("ElysiumRidesPresence", "PRESENCE_ACK")
                    }.onFailure { error ->
                        _rideDriverPresenceHealthy.value = false
                        Log.w("ElysiumRidesPresence", "Driver presence heartbeat failed", error)
                    }
                } else {
                    _rideDriverPresenceHealthy.value = false
                    Log.w("ElysiumRidesPresence", "Fresh GPS unavailable")
                }
                delay(27_000L)
            }
            _rideDriverPresenceHealthy.value = false
        }
    }
    private var rideProjectionOwnerId: String? = null
    private val _rideProjectionConnectionState =
        MutableStateFlow(RideProjectionConnectionState.IDLE)
    val rideProjectionConnectionState: StateFlow<RideProjectionConnectionState> =
        _rideProjectionConnectionState.asStateFlow()

    fun startRideProjectionSync() {
        val ownerId = currentCloudUserId()
        if (rideProjectionJob?.isActive == true && rideProjectionOwnerId == ownerId) return
        if (rideProjectionJob?.isActive == true) {
            rideProjectionJob?.cancel()
            rideProjectionJob = null
        }
        if (ownerId == null) {
            rideProjectionOwnerId = null
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.AUTHENTICATION_REQUIRED
            Log.d("ElysiumRides", "Ride projection deferred until authentication")
            return
        }
        rideProjectionOwnerId = ownerId
        val operationId = "ride-realtime-projection"
        val now = System.currentTimeMillis()
        activeOperationsRegistry.upsert(
            ActiveOperation(
                operationId = operationId,
                type = "RIDE_REALTIME_SYNC",
                vehicleId = null,
                startedAtEpochMs = now,
                state = OperationState.STARTING,
                progress = null,
                owner = OperationOwner.APPLICATION_SCOPED,
                recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                lastHeartbeatEpochMs = now,
            ),
        )
        rideProjectionJob = viewModelScope.launch(Dispatchers.IO) {
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.CONNECTING
            val realtimeWakeUps = rideRemoteProjectionRepository
                .realtimeWakeUps()
                .onEach {
                    _rideProjectionConnectionState.value =
                        RideProjectionConnectionState.LIVE
                    activeOperationsRegistry.heartbeat(operationId, OperationState.RUNNING)
                }
                .retryWhen { error, attempt ->
                    if (currentCloudUserId() == null) {
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.AUTHENTICATION_REQUIRED
                        false
                    } else {
                        val delayMs = RideProjectionSyncPolicy.reconnectDelayMs(attempt)
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.RECOVERING
                        activeOperationsRegistry.heartbeat(operationId, OperationState.RETRYING)
                        Log.w(
                            "ElysiumRides",
                            "Realtime wake-up interrupted; reconnecting in ${delayMs}ms",
                            error,
                        )
                        delay(delayMs)
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.CONNECTING
                        true
                    }
                }
            val foregroundHeartbeat = flow {
                while (currentCoroutineContext().isActive) {
                    delay(RideProjectionSyncPolicy.HEARTBEAT_INTERVAL_MS)
                    emit(Unit)
                }
            }
            merge(realtimeWakeUps, foregroundHeartbeat)
                .onStart { emit(Unit) }
                .conflate()
                .collect {
                    refreshRideProjection()
                }
        }
    }

    fun stopRideProjectionSync() {
        rideProjectionJob?.cancel()
        rideProjectionJob = null
        rideProjectionOwnerId = null
        _rideProjectionConnectionState.value = RideProjectionConnectionState.IDLE
        activeOperationsRegistry.complete("ride-realtime-projection")
    }

    fun refreshRideProjectionNow() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshRideProjection()
        }
    }

    private suspend fun refreshRideProjection() {
        RideObservability.event("projection_refresh_started")
        when (val result = rideRemoteProjectionRepository.refreshVisibleRides()) {
            is RideProjectionRefreshResult.Refreshed -> {
                Log.d("ElysiumRides", "Remote ride projection refreshed: ${result.count}")
                RideObservability.event("projection_refresh", count = result.count, detail = "refreshed")
                reconcileActiveRideAfterProjection()
                rideDao.getPendingChatMessages().map { it.rideRequestId }.distinct().forEach { syncRideChat(it) }
            }
            RideProjectionRefreshResult.AuthenticationRequired -> {
                _rideProjectionConnectionState.value =
                    RideProjectionConnectionState.AUTHENTICATION_REQUIRED
                Log.d("ElysiumRides", "Ride projection waiting for authenticated session")
                RideObservability.event("projection_refresh", outcome = "REJECTED", detail = "authentication_required")
            }
            is RideProjectionRefreshResult.Failed -> {
                Log.w("ElysiumRides", "Ride projection refresh failed: ${result.message}")
                RideObservability.event("projection_refresh", outcome = "FAILED", detail = result.message)
            }
        }
    }

    /**
     * A remote terminal cancellation must clear both the in-memory panel and
     * the owner-scoped Room pointer. Without this, a later projection or
     * process restart can resurrect the cancelled trip as "active".
     */
    private suspend fun reconcileActiveRideAfterProjection() {
        val selected = _activeRideRequest.value
        if (selected != null) {
            val latest = rideDao.getRequestById(selected.requestId)
            if (latest != null && (latest.status == "CANCELLED" || latest.serverState == "CANCELLED")) {
                rideDao.clearActiveRideSelectionsForRide(selected.requestId)
                withContext(Dispatchers.Main) {
                    if (_activeRideRequest.value?.requestId == selected.requestId) {
                        applyActiveRide(null)
                    }
                }
                return
            }
            if (latest != null && latest.serverVersion > 0L && latest.serverState == "COMPLETED") {
                val role = if (_rideDriverMode.value) "DRIVER" else "PASSENGER"
                if (isRatingSettled(latest.requestId, role)) {
                    // Already rated/dismissed — stop holding this as active ride
                    rideDao.clearActiveRideSelectionsForRide(latest.requestId)
                    withContext(Dispatchers.Main) {
                        if (_activeRideRequest.value?.requestId == selected.requestId) {
                            applyActiveRide(null)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _activeRideRequest.value = latest
                        promptRideRating(latest)
                    }
                }
            }
        }
        if (_activeRideRequest.value == null) {
            val ownerId = currentCloudUserId()
            if (ownerId != null) {
                restoreActiveRideSelection(ownerId, _rideDriverMode.value)
            }
        }
    }

    fun toggleRideDriverMode() {
        setRideDriverMode(!_rideDriverMode.value)
    }

    fun ensureRideDriverPresence() {
        val ownerId = currentCloudUserId() ?: return
        if (!_rideDriverMode.value || !RideVerificationPolicy.grantsAccess(driverVerification.value?.status) ||
            rideDriverPresenceJob?.isActive == true
        ) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_set_driver_availability_v1",
                    buildJsonObject { put("p_availability", "AVAILABLE") },
                )
            }.onSuccess {
                if (_rideDriverMode.value && currentCloudUserId() == ownerId) {
                    startRideDriverPresenceHeartbeat(ownerId)
                }
            }.onFailure { error ->
                stopRideDriverPresenceHeartbeat()
                Log.w("ElysiumRidesPresence", "Presence activation failed", error)
            }
        }
    }

    fun setRideDriverMode(enabled: Boolean) {
        val principal = activePrincipalKernel.current()
        val key = com.elysium369.meet.ride.domain.RideDriverSessionRestoration
            .modeKey(principal.id.takeIf { principal.isAuthenticated })
        val accessGranted = !enabled || RideVerificationPolicy.grantsAccess(driverVerification.value?.status)
        _rideDriverMode.value = enabled && key != null && accessGranted
        RideObservability.event(
            "driver_mode_changed",
            outcome = if (_rideDriverMode.value == enabled) "SUCCEEDED" else "REJECTED",
            detail = when {
                key == null -> "unauthenticated"
                !accessGranted -> "verification_not_approved"
                else -> if (enabled) "enabled" else "disabled"
            },
        )
        if (key != null) {
            context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(key, _rideDriverMode.value).apply()
        }
        // Switching the visible role must not cancel the driver's active work.
        // Each role restores its own durable navigation pointer.
        applyActiveRide(null)
        principal.id.takeIf { principal.isAuthenticated }?.let { ownerId ->
            viewModelScope.launch { restoreActiveRideSelection(ownerId, _rideDriverMode.value) }
        }
        if (_rideDriverMode.value) {
            startRideProjectionSync()
            refreshRideProjectionNow()
            ensureRideDriverPresence()
        } else {
            stopRideDriverPresenceHeartbeat()
            // Mark driver as OFFLINE when leaving driver mode
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val params = kotlinx.serialization.json.buildJsonObject {
                        put("p_availability", "OFFLINE")
                    }
                    SupabaseManager.client.postgrest.rpc("ride_set_driver_availability_v1", params)
                }.onFailure { error ->
                    android.util.Log.w("ElysiumRides", "Offline presence sync failed: ${error.message}")
                }
            }
        }
    }

    fun recordDriverLiveness(evidenceSha256: String, capturedAtEpochMs: Long) {
        if (!evidenceSha256.matches(Regex("[0-9a-f]{64}"))) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_record_driver_liveness_v1",
                    buildJsonObject {
                        put("p_evidence_sha256", evidenceSha256)
                        put("p_captured_at", Instant.ofEpochMilli(capturedAtEpochMs).toString())
                    },
                )
            }.onFailure { error ->
                Log.w("ElysiumRides", "Liveness evidence pending remote confirmation", error)
                _rideVerificationNotice.emit(
                    "Presencia validada en el dispositivo; la nube la confirmará al recuperar conexión.",
                )
            }
        }
    }

    fun refreshRideDriverVehicles() {
        viewModelScope.launch(Dispatchers.IO) {
            val userId = currentCloudUserId() ?: return@launch
            runCatching {
                SupabaseManager.client.postgrest["ride_driver_vehicles"]
                    .select {
                        filter { eq("driver_id", userId) }
                    }
                    .decodeList<RemoteRideDriverVehicleSummary>()
                    .map(RemoteRideDriverVehicleSummary::toDomain)
            }.onSuccess { _rideDriverVehicles.value = it }
                .onFailure { Log.w("ElysiumRides", "Vehicle fleet refresh failed", it) }
        }
    }

    fun addRideDriverVehicle(
        make: String,
        model: String,
        year: Int,
        color: String,
        plate: String,
        fleetName: String?,
        vehicleKind: String = "CAR",
    ) {
        val normalized = listOf(make, model, color, plate).map(String::trim)
        if (normalized.any(String::isBlank) || year !in 1900..2200 || vehicleKind !in setOf("CAR", "MOTORCYCLE")) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_upsert_driver_vehicle_v2",
                    buildJsonObject {
                        put("p_vehicle_id", UUID.randomUUID().toString())
                        put("p_display_name", "$make $model $year $color")
                        put("p_seats", if (vehicleKind == "MOTORCYCLE") 1 else 4)
                        put("p_vehicle_kind", vehicleKind)
                        put("p_make", make.trim())
                        put("p_model", model.trim())
                        put("p_model_year", year)
                        put("p_color", color.trim())
                        put("p_plate_masked", plate.trim().uppercase())
                        fleetName?.trim()?.takeIf(String::isNotBlank)?.let { put("p_fleet_name", it) }
                    },
                )
            }.onSuccess { refreshRideDriverVehicles() }
                .onFailure { _rideVerificationNotice.emit("No se pudo guardar el vehículo en la nube.") }
        }
    }

    fun activateRideDriverVehicle(vehicleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_set_active_vehicle_v1",
                    buildJsonObject { put("p_vehicle_id", vehicleId) },
                )
            }.onSuccess { refreshRideDriverVehicles() }
                .onFailure { _rideVerificationNotice.emit("Sólo puedes activar un vehículo verificado.") }
        }
    }

    fun selectActiveRide(request: RideRequestEntity?) {
        val ownerId = activePrincipalKernel.current().id
        val selectionOwnerKey = com.elysium369.meet.ride.domain.RideRoleContextPolicy
            .selectionOwnerKey(ownerId, _rideDriverMode.value) ?: return
        if (request != null && !canSelectRideForCurrentRole(request)) {
            viewModelScope.launch {
                _rideVerificationNotice.emit(
                    "Este viaje no pertenece al rol y a la sesión activa.",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                rideDao.clearActiveRideSelection(selectionOwnerKey)
            }
            applyActiveRide(null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (request == null) {
                rideDao.clearActiveRideSelection(selectionOwnerKey)
            } else {
                rideDao.upsertActiveRideSelection(
                    ActiveRideSelectionEntity(
                        ownerPrincipalId = selectionOwnerKey,
                        rideRequestId = request.requestId,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                val assignedId = request.assignedDriverId
                if (!assignedId.isNullOrBlank() && assignedId != ownerId) {
                    val driverKey = com.elysium369.meet.ride.domain.RideRoleContextPolicy
                        .selectionOwnerKey(assignedId, true)
                    if (driverKey != null) {
                        rideDao.upsertActiveRideSelection(
                            ActiveRideSelectionEntity(
                                ownerPrincipalId = driverKey,
                                rideRequestId = request.requestId,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
        applyActiveRide(request)
    }

    private fun canSelectRideForCurrentRole(request: RideRequestEntity): Boolean =
        canSelectRide(request, _rideDriverMode.value)

    private fun canSelectRide(request: RideRequestEntity, driverMode: Boolean): Boolean {
        if (request.status in setOf("COMPLETED", "CANCELLED", "EXPIRED", "VOIDED") ||
            request.serverState in setOf("COMPLETED", "CANCELLED", "EXPIRED", "VOIDED")
        ) {
            return false
        }
        val currentDriverId = driverVerification.value?.driverId
        val actorId = currentRideActorId
        val cloudUserId = currentCloudUserId()

        val myIds = listOfNotNull(currentDriverId, actorId, cloudUserId).toSet()
        val isAssignedDriver = request.assignedDriverId != null && myIds.contains(request.assignedDriverId)
        val isPassenger = request.passengerId != null && myIds.contains(request.passengerId)

        if (driverMode && isAssignedDriver) {
            return true
        }
        if (!driverMode && isPassenger) {
            return true
        }

        val ownerId = cloudUserId ?: actorId
        return com.elysium369.meet.ride.domain.RideActiveSelectionPolicy.canSelect(
            ownerId = ownerId,
            driverMode = driverMode,
            passengerId = request.passengerId,
            assignedDriverId = request.assignedDriverId,
            serverState = request.serverState,
            serverVersion = request.serverVersion,
            allowSelfRide = false,
        ) || (driverMode && isAssignedDriver)
    }

    private suspend fun restoreActiveRideSelection(ownerId: String, driverMode: Boolean) {
        val roleKey = com.elysium369.meet.ride.domain.RideRoleContextPolicy
            .selectionOwnerKey(ownerId, driverMode) ?: return
        var selection = withContext(Dispatchers.IO) {
            rideDao.getActiveRideSelection(roleKey)
        }
        // Adopt a pointer written by older builds only after actor/role validation.
        if (selection == null) {
            val legacy = withContext(Dispatchers.IO) { rideDao.getActiveRideSelection(ownerId) }
            val legacyRide = legacy?.let {
                withContext(Dispatchers.IO) { rideDao.getRequestById(it.rideRequestId) }
            }
            if (legacy != null && legacyRide != null && canSelectRide(legacyRide, driverMode)) {
                selection = legacy.copy(ownerPrincipalId = roleKey)
                withContext(Dispatchers.IO) {
                    rideDao.upsertActiveRideSelection(requireNotNull(selection))
                    rideDao.clearActiveRideSelection(ownerId)
                }
            }
        }
        val selected = selection
        if (selected == null) {
            applyActiveRide(null)
            return
        }
        val request = withContext(Dispatchers.IO) {
            rideDao.getRequestById(selected.rideRequestId)
        }
        if (request == null) {
            Log.w("ElysiumRides", "Active ride unavailable locally; durable pointer retained")
        } else if (!canSelectRide(request, driverMode)) {
            withContext(Dispatchers.IO) { rideDao.clearActiveRideSelection(roleKey) }
            Log.w("ElysiumRides", "Rejected active ride pointer outside current actor/role scope")
            applyActiveRide(null)
        } else if (_rideDriverMode.value == driverMode && (currentCloudUserId() == ownerId || activePrincipalKernel.current().id == ownerId)) {
            applyActiveRide(request)
        }
    }

    private fun applyActiveRide(request: RideRequestEntity?) {
        selectedRideRequestId = request?.requestId
        _activeRideRequest.value = request
        jobActiveRideCollection?.cancel()
        jobOffersCollection?.cancel()
        jobChatCollection?.cancel()
        jobChatRemoteSync?.cancel()
        jobChatBridgeObservation?.cancel()

        if (request != null) {
            val currentRole = if (_rideDriverMode.value) "DRIVER" else "PASSENGER"
            jobChatBridgeObservation = rideChatBridge?.startObserving(
                scope = viewModelScope,
                rideRequestId = request.requestId,
                localRole = currentRole,
            ) { incoming ->
                rideDao.insertChatMessage(incoming)
            }

            val ownerId = activePrincipalKernel.current().id
            jobActiveRideCollection = viewModelScope.launch {
                rideDao.observeRequest(request.requestId).collect { latest ->
                    if (activePrincipalKernel.current().id != ownerId ||
                        selectedRideRequestId != request.requestId
                    ) return@collect
                    if (latest == null) return@collect
                    if (latest.status == "CANCELLED" || latest.serverState == "CANCELLED") {
                        // ── Notification: viaje cancelado ──
                        val isDriver = _rideDriverMode.value
                        com.elysium369.meet.core.notification.ElysiumNotificationHub.processRideStateChange(
                            context = context,
                            requestId = latest.requestId,
                            effectiveState = "CANCELLED",
                            isDriver = isDriver,
                            partyName = if (isDriver) latest.passengerName else latest.assignedDriverName,
                            price = latest.finalPrice ?: latest.priceOffer,
                            pickupAddress = latest.pickupAddress,
                            destAddress = latest.destAddress,
                            currency = latest.currency
                        )
                        rideDao.clearActiveRideSelectionsForRide(request.requestId)
                        applyActiveRide(null)
                    } else {
                        // Room contains command acknowledgements as well as realtime refreshes.
                        // Keep completion visible for receipt and feedback; never freeze a version.
                        _activeRideRequest.value = latest

                        // ── Notification: disparar pop-up en cada cambio de estado ──
                        val effectiveState = latest.serverState?.ifBlank { latest.status } ?: latest.status
                        val isDriver = _rideDriverMode.value
                        com.elysium369.meet.core.notification.ElysiumNotificationHub.processRideStateChange(
                            context = context,
                            requestId = latest.requestId,
                            effectiveState = effectiveState,
                            isDriver = isDriver,
                            partyName = if (isDriver) latest.passengerName else latest.assignedDriverName,
                            price = latest.finalPrice ?: latest.priceOffer,
                            pickupAddress = latest.pickupAddress,
                            destAddress = latest.destAddress,
                            currency = latest.currency
                        )

                        val role = if (isDriver) "DRIVER" else "PASSENGER"
                        val rating = if (isDriver) latest.driverRating else latest.passengerRating
                        val isCompleted = latest.serverVersion > 0L && latest.serverState == "COMPLETED"
                        if (isCompleted && rating == null &&
                            !isRatingSettled(latest.requestId, role) &&
                            canSelectRideForCurrentRole(latest)
                        ) {
                            _pendingRatingRide.value = latest
                        }
                    }
                }
            }
            _rideSharingSelections.update { current ->
                if (request.requestId in current) {
                    current
                } else {
                    current + (
                        request.requestId to setOf(RideShareCategory.EXACT_LOCATION)
                        )
                }
            }
            jobOffersCollection = viewModelScope.launch {
                rideDao.getOffersForRequest(request.requestId).collect {
                    // ── Notification: nuevas ofertas de viaje ──
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.processNewOffers(
                        context = context,
                        tripId = request.requestId,
                        offers = it,
                        isDriver = _rideDriverMode.value
                    )
                    _rideOffers.value = it
                }
            }
            jobChatCollection = viewModelScope.launch {
                rideDao.getChatMessagesFlow(request.requestId).collect {
                    // ── Notification: nuevos mensajes de chat ──
                    val currentUserId = currentCloudUserId() ?: activePrincipalKernel.current().id
                    com.elysium369.meet.core.notification.ElysiumNotificationHub.processNewChatMessages(
                        context = context,
                        messages = it,
                        currentUserId = currentUserId
                    )
                    _rideChatMessages.value = it
                }
            }
            jobChatRemoteSync = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    syncRideChat(request.requestId)
                    delay(4_000)
                }
            }
        } else {
            _rideOffers.value = emptyList()
            _rideChatMessages.value = emptyList()
            com.elysium369.meet.core.notification.ElysiumNotificationHub.resetRideCounters()
        }
    }

    fun observeRideChatForRequestId(requestId: String) {
        jobChatCollection?.cancel()
        jobChatRemoteSync?.cancel()
        jobChatCollection = viewModelScope.launch {
            rideDao.getChatMessagesFlow(requestId).collect {
                _rideChatMessages.value = it
            }
        }
        jobChatRemoteSync = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                syncRideChat(requestId)
                delay(3_000)
            }
        }
    }

    private fun handleAiAutomationAction(action: com.elysium369.meet.automation.AiAction) {
        when (action) {
            is com.elysium369.meet.automation.AiAction.SwitchRole -> {
                setRideDriverMode(action.isDriver)
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.InjectGps -> {
                if (!BuildConfig.DEBUG) return
                _currentGpsLocation.value = GpsLocationInfo(
                    latitude = action.latitude,
                    longitude = action.longitude,
                    addressName = "Ubicación simulada por IA",
                    countryCode = "CR",
                    dialingPrefix = "+506",
                    accuracy = 5.0f,
                    speed = 0.0f,
                    bearing = 0.0f,
                    timestamp = System.currentTimeMillis(),
                )
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.CreateRide -> {
                val pId = if (_rideDriverMode.value) {
                    "passenger_${System.currentTimeMillis().toString().takeLast(6)}"
                } else {
                    currentUserId ?: activePrincipalKernel.current().id
                }
                val pVer = passengerVerification.value
                val phone = pVer?.phone?.takeIf { it.isNotBlank() } ?: "+50600000000"
                val name = if (_rideDriverMode.value) "Pasajero Elysium Test" else (pVer?.fullName ?: "Pasajero Elysium")
                val priceMinor = (action.priceOffer * 100).toLong()
                val entity = RideRequestEntity(
                    requestId = UUID.randomUUID().toString(),
                    passengerId = pId,
                    passengerName = name,
                    passengerPhone = phone,
                    pickupLatitude = action.pickupLat,
                    pickupLongitude = action.pickupLng,
                    pickupAddress = action.pickupAddress,
                    pickupAccuracy = 5.0f,
                    destLatitude = action.destLat,
                    destLongitude = action.destLng,
                    destAddress = action.destAddress,
                    priceOffer = action.priceOffer,
                    priceOfferMinor = priceMinor,
                    currency = action.currency,
                    estimatedDistanceKm = 15.0,
                    estimatedDurationMin = 25,
                    stopsJson = "[]",
                    paymentMethod = "CASH",
                    fareMode = com.elysium369.meet.ride.domain.RideFareMode.OPEN_BID.name,
                    status = "OPEN",
                    serverState = "SEARCHING",
                    serverVersion = 1L,
                    syncState = "SYNCED",
                    createdAt = System.currentTimeMillis(),
                )
                viewModelScope.launch(Dispatchers.IO) {
                    rideDao.insertRequest(entity)
                    dumpAiStateSnapshot()
                }
            }
            is com.elysium369.meet.automation.AiAction.SelectRide -> {
                viewModelScope.launch {
                    val ride = withContext(Dispatchers.IO) { rideDao.getRequestById(action.rideId) }
                    selectActiveRide(ride)
                    dumpAiStateSnapshot()
                }
            }
            is com.elysium369.meet.automation.AiAction.AdvanceRideStatus -> {
                Log.w("AiAutomation", "Arbitrary ride status advance rejected: transitions must be server-authoritative")
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.SubmitOffer -> {
                viewModelScope.launch {
                    val ride = withContext(Dispatchers.IO) { rideDao.getRequestById(action.requestId) } ?: return@launch
                    val price = if (action.counterPrice > 0.0) action.counterPrice else ride.priceOffer
                    makeRideOffer(
                        requestId = ride.requestId,
                        counterPrice = price,
                        currency = ride.currency,
                        estArrivalMin = action.estArrivalMin,
                        message = action.message,
                    )
                    dumpAiStateSnapshot()
                }
            }
            is com.elysium369.meet.automation.AiAction.AcceptOffer -> {
                viewModelScope.launch {
                    val offerId = action.offerId ?: withContext(Dispatchers.IO) {
                        val offers = rideDao.getOffersForRequestSync(action.requestId)
                        if (offers.isNotEmpty()) offers[0].offerId else null
                    }
                    if (offerId != null) {
                        acceptRideOffer(action.requestId, offerId)
                        dumpAiStateSnapshot()
                    }
                }
            }
            is com.elysium369.meet.automation.AiAction.TriggerObdDemo -> {
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.VoiceCommand -> {
                Log.i("ObdViewModel", "VoiceCommand received via AiAction: ${action.command}")
                val q = action.command.lowercase().trim()
                if (q.contains("viaje") || q.contains("ride") || q.contains("transporte")) {
                    setRideDriverMode(false)
                }
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.CancelRide -> {
                val targetId = action.rideId ?: _activeRideRequest.value?.requestId
                if (targetId != null) {
                    cancelRide(
                        requestId = targetId,
                        reason = RideCancellationReason.CHANGE_OF_PLANS,
                        detail = "Cancelado por operador AI",
                        actorRole = if (_rideDriverMode.value) "DRIVER" else "PASSENGER",
                    )
                }
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.ClearStuckRides -> {
                clearAllStuckRides()
                dumpAiStateSnapshot()
            }
            is com.elysium369.meet.automation.AiAction.DumpState -> {
                dumpAiStateSnapshot()
            }
        }
    }

    fun dumpAiStateSnapshot() {
        val activeRide = _activeRideRequest.value
        val openRides = openRideRequests.value
        val gps = _currentGpsLocation.value
        val vehicle = _selectedVehicle.value
        val dtcs = canonicalActiveFindingSummaries.value
        val driverVer = driverVerification.value
        val passVer = passengerVerification.value
        val balance = 0L

        val snapshot = com.elysium369.meet.automation.AiStateSnapshot(
            timestamp = System.currentTimeMillis(),
            currentRoute = com.elysium369.meet.automation.AiAutomationBridge.currentRoute,
            isDriverMode = _rideDriverMode.value,
            activeRideId = activeRide?.requestId,
            activeRideStatus = activeRide?.status,
            activeRidePickup = activeRide?.pickupAddress,
            activeRideDest = activeRide?.destAddress,
            activeRidePrice = activeRide?.priceOffer,
            activeRideCurrency = activeRide?.currency,
            activeRideDriverId = activeRide?.assignedDriverId,
            activeRidePassengerId = activeRide?.passengerId,
            openRidesCount = openRides.size,
            gpsLatitude = gps?.latitude,
            gpsLongitude = gps?.longitude,
            connectedVehicleVin = vehicle?.vin,
            activeDtcsCount = dtcs.size,
            driverVerificationStatus = driverVer?.status,
            passengerVerificationStatus = passVer?.status,
            walletBalanceCrc = balance,
        )

        try {
            val json = Json { prettyPrint = true }.encodeToString(
                com.elysium369.meet.automation.AiStateSnapshot.serializer(),
                snapshot,
            )
            val appFile = java.io.File(context.getExternalFilesDir(null), "meet_state.json")
            runCatching { appFile.writeText(json) }
            val internalFile = java.io.File(context.filesDir, "meet_state.json")
            runCatching { internalFile.writeText(json) }
            runCatching { java.io.File("/data/local/tmp/meet_state.json").writeText(json) }
            Log.i("AiAutomation", "Dumped AI state snapshot successfully")
        } catch (e: Exception) {
            Log.e("AiAutomation", "Failed to dump AI state snapshot", e)
        }
    }

    init {
        viewModelScope.launch {
            com.elysium369.meet.automation.AiAutomationBridge.actionEvents.collect { action ->
                handleAiAutomationAction(action)
            }
        }
        viewModelScope.launch {
            combine(activePrincipalKernel.activePrincipal, _rideDriverMode) { principal, driverMode ->
                Triple(principal.id, principal.isAuthenticated, driverMode)
            }
                .distinctUntilChanged()
                .collectLatest { (ownerId, authenticated, driverMode) ->
                    // Account and visible-role boundaries restore independent UI
                    // contexts; neither transition changes server trip state.
                    if (!authenticated) {
                        applyActiveRide(null)
                        return@collectLatest
                    }
                    restoreActiveRideSelection(ownerId, driverMode)
                }
        }
        viewModelScope.launch {
            activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
                if (!principal.isAuthenticated) flowOf(null)
                else rideDao.observeAuthoritativeActiveRidesForDriver(principal.id)
                    // Tracking the wrong assignment is worse than pausing when
                    // corrupted local data reports two active driver trips.
                    .map { rides -> rides.singleOrNull() }
            }.distinctUntilChangedBy { it?.requestId to it?.status }
                .collectLatest { ride ->
                    if (ride == null) {
                        // Stop GPS forensic trail recording
                        GpsTrailRecorder.getActiveRideId(context)?.let { activeRideId ->
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                GpsTrailRecorder.stopRecording(context, activeRideId)
                            }
                        }
                        RideLocationTrackingService.stop(context)
                        return@collectLatest
                    }
                    // Start GPS forensic trail recording
                    if (!GpsTrailRecorder.hasActiveRecording(context)) {
                        GpsTrailRecorder.startRecording(
                            context = context,
                            rideId = ride.requestId,
                            driverId = ride.assignedDriverId.orEmpty(),
                            passengerId = ride.passengerId,
                        )
                    }
                    runCatching { RideLocationTrackingService.start(context, ride.requestId) }
                        .onFailure { Log.w("ElysiumRides", "Unable to start visible trip tracking", it) }
                    while (isActive) {
                        detectCurrentLocation(context)
                        delay(2_500)
                        recordRideSpeedObservation(ride.requestId)
                        delay(12_500)
                    }
                }
        }
        viewModelScope.launch {
            combine(
                rideRequests,
                _rideClaimUiState,
            ) { rides, claimState ->
                rides to claimState
            }.collectLatest { (rides, claimState) ->
                val pending =
                    claimState as? RideClaimUiState.Pending
                        ?: return@collectLatest

                val ride = rides.find {
                    it.requestId == pending.requestId
                } ?: return@collectLatest

                val actorIds = buildSet {
                    currentCloudUserId()?.let(::add)
                    driverVerification.value
                        ?.driverId
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                    currentRideActorId
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }

                val assignedToMe =
                    ride.assignedDriverId != null &&
                        ride.assignedDriverId in actorIds

                val authoritativeActive =
                    ride.serverVersion > 0L &&
                        ride.serverState in setOf(
                            "ASSIGNED",
                            "DRIVER_EN_ROUTE",
                            "ARRIVED",
                            "PASSENGER_ONBOARD",
                            "IN_PROGRESS",
                        )

                if (assignedToMe && authoritativeActive) {
                    _rideClaimUiState.value =
                        RideClaimUiState.Won(
                            requestId = ride.requestId,
                        )

                    selectActiveRide(ride)

                    RideObservability.event(
                        "ride_claim",
                        outcome = "SUCCEEDED",
                        requestId = ride.requestId,
                        detail = "server_projection_confirmed",
                    )
                }
            }
        }
    }

    @Serializable
    private data class RideChatWireMessage(
        val id: String,
        @SerialName("ride_request_id") val rideRequestId: String,
        @SerialName("sender_id") val senderId: String,
        @SerialName("sender_name") val senderName: String,
        @SerialName("sender_role") val senderRole: String,
        @SerialName("message_type") val messageType: String,
        @SerialName("text_content") val textContent: String? = null,
        @SerialName("media_path") val mediaPath: String? = null,
        @SerialName("media_mime_type") val mediaMimeType: String? = null,
        @SerialName("audio_duration_ms") val audioDurationMs: Long? = null,
        @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    )

    private suspend fun syncRideChat(requestId: String) {
        val cloudUserId = currentCloudUserId() ?: return
        val request = rideDao.getRequestById(requestId) ?: return
        val ownedRoles = buildSet {
            if (request.passengerId == cloudUserId) add("PASSENGER")
            if (request.assignedDriverId == cloudUserId) add("DRIVER")
        }
        if (ownedRoles.isEmpty()) return
        runCatching {
            rideDao.getPendingChatMessages().filter {
                it.rideRequestId == requestId && it.senderRole in ownedRoles
            }.forEach { local ->
                var remotePath = local.remoteMediaPath
                val localMediaPath = local.imageFilePath ?: local.audioFilePath
                if (remotePath == null && localMediaPath != null) {
                    val file = java.io.File(localMediaPath)
                    if (file.exists()) {
                        val extension = file.extension.ifBlank { if (local.messageType == "AUDIO") "m4a" else "jpg" }
                        remotePath = "$requestId/$cloudUserId/${local.messageId}.$extension"
                        SupabaseManager.client.storage.from("ride-media").upload(remotePath, file.readBytes())
                    }
                }
                SupabaseManager.client.postgrest["ride_messages"].upsert(
                    RideChatWireMessage(
                        id = local.messageId,
                        rideRequestId = local.rideRequestId,
                        senderId = cloudUserId,
                        senderName = local.senderName,
                        senderRole = local.senderRole,
                        messageType = local.messageType,
                        textContent = local.textContent,
                        mediaPath = remotePath,
                        mediaMimeType = local.mediaMimeType,
                        audioDurationMs = local.audioDurationMs,
                        createdAtEpochMs = local.createdAt,
                    ),
                )
                rideDao.updateChatMessageSyncState(local.messageId, "SYNCED", remotePath)
            }

            val remoteMessages = SupabaseManager.client.postgrest["ride_messages"].select {
                filter { eq("ride_request_id", requestId) }
            }.decodeList<RideChatWireMessage>()
            remoteMessages.forEach { remote ->
                val localMediaFile = remote.mediaPath?.let { path ->
                    val extension = path.substringAfterLast('.', "bin")
                    val directory = java.io.File(
                        context.filesDir,
                        if (remote.messageType == "AUDIO") "meet_rides_audio" else "meet_rides_images",
                    ).apply { mkdirs() }
                    java.io.File(directory, "remote_${remote.id}.$extension").also { target ->
                        if (!target.exists()) {
                            target.writeBytes(
                                SupabaseManager.client.storage.from("ride-media").downloadAuthenticated(path),
                            )
                        }
                    }
                }
                val localSenderId = if (remote.senderId == cloudUserId) {
                    if (remote.senderRole == "DRIVER") {
                        driverVerification.value?.driverId ?: remote.senderId
                    } else {
                        passengerVerification.value?.passengerId ?: remote.senderId
                    }
                } else remote.senderId
                rideDao.insertChatMessage(
                    RideChatMessageEntity(
                        messageId = remote.id,
                        rideRequestId = remote.rideRequestId,
                        senderId = localSenderId,
                        senderName = remote.senderName,
                        senderRole = remote.senderRole,
                        messageType = remote.messageType,
                        textContent = remote.textContent,
                        audioFilePath = localMediaFile?.takeIf { remote.messageType == "AUDIO" }?.absolutePath,
                        imageFilePath = localMediaFile?.takeIf { remote.messageType == "IMAGE" }?.absolutePath,
                        remoteMediaPath = remote.mediaPath,
                        mediaMimeType = remote.mediaMimeType,
                        audioDurationMs = remote.audioDurationMs,
                        syncState = "SYNCED",
                        createdAt = remote.createdAtEpochMs,
                    ),
                )
            }
        }.onFailure { error ->
            Log.w("ElysiumRides", "Ride chat sync deferred", error)
        }
    }

    fun setRideShareCategory(
        requestId: String,
        category: RideShareCategory,
        enabled: Boolean,
    ) {
        if (requestId.isBlank()) return
        _rideSharingSelections.update { current ->
            val existing = current[requestId].orEmpty()
            val updated = if (enabled) existing + category else existing - category
            current + (requestId to updated)
        }
    }

    fun reportRideRoadIncident(
        tripId: String,
        type: RideRoadIncidentType,
        side: RideRoadSide,
        severity: Int,
    ) {
        if (tripId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(tripId)
            if (request == null) {
                _rideRoadReportFeedback.emit("No se encontró el viaje activo para registrar el reporte.")
                return@launch
            }
            val gps = _currentGpsLocation.value
            val availability = RideRoadReportAvailabilityPolicy.evaluate(
                isDriver = _rideDriverMode.value,
                localStatus = request.status,
                serverState = request.serverState,
                serverVersion = request.serverVersion,
                hasCurrentGps = gps != null,
            )
            if (!availability.allowed || gps == null) {
                _rideRoadReportFeedback.emit(availability.message)
                return@launch
            }
            val now = System.currentTimeMillis()
            val lifetimeMs = when (type) {
                RideRoadIncidentType.POLICE_PRESENCE,
                RideRoadIncidentType.PUBLIC_POLICE,
                RideRoadIncidentType.TRAFFIC_POLICE,
                -> 20 * 60 * 1000L
                RideRoadIncidentType.TRAFFIC_CONTROL,
                RideRoadIncidentType.SLOW_TRAFFIC,
                RideRoadIncidentType.VERY_SLOW_TRAFFIC,
                -> 30 * 60 * 1000L
                RideRoadIncidentType.ROAD_CLOSED,
                RideRoadIncidentType.WRONG_WAY_HAZARD,
                RideRoadIncidentType.STALLED_VEHICLE,
                RideRoadIncidentType.OBSTACLE,
                -> 90 * 60 * 1000L
                RideRoadIncidentType.POTHOLE,
                RideRoadIncidentType.SPEED_BUMP,
                RideRoadIncidentType.FLOODING,
                -> 6 * 60 * 60 * 1000L
            }
            val coarseCell = RideGeoCell.encode(gps.latitude, gps.longitude)
            val incident = RideRoadIncident(
                id = UUID.randomUUID().toString(),
                roadSegmentId = "cell:$coarseCell",
                type = type,
                side = side,
                severity = severity.coerceIn(1, 3),
                latitude = gps.latitude,
                longitude = gps.longitude,
                bearingDegrees = gps.bearing,
                accuracyMeters = gps.accuracy,
                reporterReliability = 0.50,
                independentConfirmations = 0,
                independentDenials = 0,
                observedSpeedRatio = null,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + lifetimeMs,
            )
            _rideRoadIncidents.update { current ->
                (current.filterNot { it.isExpired(now) } + incident).takeLast(100)
            }
            _rideRoadReportFeedback.emit(
                "Condición vial registrada durante la ruta. Gracias por ayudar a la comunidad.",
            )
            voiceFeedbackManager.speak(
                "Incidencia vial reportada. Gracias por ayudar a la comunidad.",
                "Road incident reported. Thank you for helping the community.",
            )

            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.w("ObdViewModel", "Road report retained locally; authenticated sync unavailable")
                return@launch
            }
            val isoExpiry = java.time.Instant.ofEpochMilli(incident.expiresAtEpochMs).toString()
            val remoteReport = RemoteRideRoadIncident(
                id = incident.id,
                reporter_id = userId,
                trip_id = request.requestId,
                road_segment_id = incident.roadSegmentId,
                incident_type = incident.type.name,
                road_side = incident.side.name,
                severity = incident.severity,
                latitude = incident.latitude,
                longitude = incident.longitude,
                bearing_degrees = incident.bearingDegrees,
                accuracy_meters = incident.accuracyMeters,
                geohash_coarse = coarseCell,
                expires_at = isoExpiry,
            )
            runCatching {
                SupabaseManager.client.postgrest["ride_road_incidents"].insert(
                    remoteReport,
                )
            }.onFailure { error ->
                Log.w(
                    "ObdViewModel",
                    "Road report retained locally; cloud sync unavailable",
                    error,
                )
            }
        }
    }

    fun recordRideSpeedObservation(tripId: String) {
        if (tripId.isBlank()) return
        val gps = _currentGpsLocation.value ?: return
        val now = System.currentTimeMillis()
        val capturedAt = gps.timestamp.takeIf { it > 0L } ?: now
        val ageMs = (now - capturedAt).coerceAtLeast(0L)
        if (ageMs > 30_000L || gps.accuracy !in 0f..100f) return
        val sample = RideSegmentSpeedSample(
            speedMetersPerSecond = gps.speed.toDouble().coerceAtLeast(0.0),
            capturedAtEpochMs = now,
        )
        _rideSpeedSamples.update { current ->
            val fresh = current[tripId]
                .orEmpty()
                .filter { now - it.capturedAtEpochMs <= 10 * 60 * 1000L }
                .plus(sample)
                .takeLast(120)
            current + (tripId to fresh)
        }

        val minuteBucket = now / 60_000L * 60_000L
        if (lastUploadedRideSpeedBucket[tripId] == minuteBucket) return
        lastUploadedRideSpeedBucket[tripId] = minuteBucket

        viewModelScope.launch(Dispatchers.IO) {
            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
            val capturedAtIso = java.time.Instant.ofEpochMilli(now).toString()
            val bucketAt = java.time.Instant.ofEpochMilli(minuteBucket).toString()
            runCatching {
                SupabaseManager.client.postgrest["ride_segment_speed_observations"].insert(
                    RemoteRideSpeedObservation(
                        observer_id = userId,
                        trip_id = tripId,
                        road_segment_id = "cell:${RideGeoCell.encode(gps.latitude, gps.longitude)}",
                        speed_mps = gps.speed.coerceIn(0f, 100f),
                        accuracy_meters = gps.accuracy.coerceAtLeast(0f),
                        bearing_degrees = gps.bearing.coerceIn(0f, 360f),
                        captured_at = capturedAtIso,
                        time_bucket = bucketAt,
                    ),
                )
            }.onFailure {
                Log.d("ObdViewModel", "Speed telemetry retained locally; cloud trip not synchronized")
            }
        }
    }

    fun announceRideEvent(spanish: String, english: String) {
        voiceFeedbackManager.speak(spanish, english)
    }

    private fun rideCommandEnvelope(
        requestId: String,
        serverVersion: Long,
        type: RideCommandType,
    ): RideCommandEnvelope = RideCommandEnvelope(
        rideId = RideId.of(requestId),
        expectedVersion = RideVersion.of(serverVersion),
        idempotencyKey = RideIdempotencyKey.of(
            "${type.name.lowercase()}:$requestId:${UUID.randomUUID()}",
        ),
        type = type,
        payloadVersion = RidePayloadVersion.of(1),
    )

    private fun rideFareToMinorUnits(
        majorUnits: Double,
        currency: String,
    ): Long {
        val fractionDigits = when (currency.uppercase()) {
            "CRC" -> 0
            else -> 2
        }
        return BigDecimal.valueOf(majorUnits)
            .movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private suspend fun activeVerifiedRemoteVehicleId(): String? {
        val userId = currentCloudUserId() ?: return null
        return runCatching {
            SupabaseManager.client.postgrest["ride_driver_vehicles"]
                .select(
                    columns = Columns.list(
                        "id",
                        "verification_method",
                        "pilot_access_expires_at",
                    ),
                ) {
                    filter {
                        eq("driver_id", userId)
                        eq("is_active", true)
                        eq("verification_status", "VERIFIED")
                    }
                    limit(1)
                }
                .decodeList<RemoteActiveRideVehicle>()
                .firstOrNull { vehicle ->
                    vehicle.verificationMethod != "PILOT_EVIDENCE_ATTESTATION" ||
                        vehicle.pilotAccessExpiresAt
                            ?.let { expiresAt ->
                                runCatching { Instant.parse(expiresAt) }
                                    .getOrNull()
                                    ?.isAfter(Instant.now()) == true
                            } == true
                }
                ?.id
        }.getOrNull()
    }

    private suspend fun enqueueAuthoritativeRideCommand(
        request: RideRequestEntity,
        type: RideCommandType,
        payload: RideCommandPayload = RideCommandPayload(),
        expectedVersion: Long = request.serverVersion,
    ): RideCommandEnqueueResult = rideCommandRepository.enqueue(
        envelope = rideCommandEnvelope(
            requestId = request.requestId,
            serverVersion = expectedVersion,
            type = type,
        ),
        payload = payload,
    )

    private suspend fun reportRideCommandEnqueue(
        result: RideCommandEnqueueResult,
        acceptedMessage: String,
    ): Boolean {
        val message = when (result) {
            RideCommandEnqueueResult.Enqueued -> acceptedMessage
            RideCommandEnqueueResult.AlreadyQueued ->
                "La operación ya estaba pendiente de confirmación."
            RideCommandEnqueueResult.AuthenticationRequired ->
                "Inicia sesión para usar Viajes con autoridad y protección de cuenta."
            is RideCommandEnqueueResult.IdempotencyConflict ->
                "La operación local entró en conflicto y no fue reenviada."
            is RideCommandEnqueueResult.InvalidCommand ->
                result.message
        }
        _rideVerificationNotice.emit(message)
        return result is RideCommandEnqueueResult.Enqueued ||
            result is RideCommandEnqueueResult.AlreadyQueued
    }

    fun createRideRequest(
        passengerId: String,
        passengerName: String,
        passengerPhone: String,
        countryCode: String,
        pickupLat: Double,
        pickupLng: Double,
        pickupAddr: String,
        pickupAcc: Float,
        destLat: Double,
        destLng: Double,
        destAddr: String,
        priceOffer: Double,
        currency: String,
        estDistance: Double,
        estDuration: Int,
        estimatedDistanceMeters: Long = (estDistance * 1_000.0).toLong(),
        estimatedDurationSeconds: Long = estDuration * 60L,
        stopsJson: String = "[]",
        paymentMethod: String = "UNKNOWN",
        fareMode: RideFareMode = RideFareMode.OPEN_BID,
        guestName: String? = null,
        guestPhoneE164: String? = null,
        passengerPreferences: RidePassengerPreferences? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (passengerPreferences?.vehicleKind == com.elysium369.meet.ride.domain.RideVehicleKind.MOTORCYCLE &&
                (passengerPreferences.kidsCount != 0 || passengerPreferences.fivePassengers ||
                    passengerPreferences.pet != com.elysium369.meet.ride.domain.RidePetType.NONE)) {
                _rideVerificationNotice.emit("La moto admite un solo pasajero, sin acompañantes ni mascotas.")
                return@launch
            }
            // A route cannot be calculated from a text label alone. Reject an
            // incomplete destination before writing a request locally or
            // publishing it remotely; otherwise both clients only receive two
            // unconnected map markers and no truthful ETA can be produced.
            val coordinatesAreValid = listOf(pickupLat, pickupLng, destLat, destLng)
                .all(Double::isFinite) &&
                pickupLat in -90.0..90.0 && pickupLng in -180.0..180.0 &&
                destLat in -90.0..90.0 && destLng in -180.0..180.0 &&
                !(destLat == 0.0 && destLng == 0.0)
            if (!coordinatesAreValid) {
                RideObservability.event("ride_request_rejected", outcome = "REJECTED", detail = "invalid_destination_coordinates")
                _rideVerificationNotice.emit(
                    "Selecciona un destino del mapa para calcular la ruta y el ETA.",
                )
                return@launch
            }
            val normalizedCountryCode = countryCode
                .trim()
                .uppercase()
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
            if (normalizedCountryCode == null) {
                _rideVerificationNotice.emit(
                    "No se pudo validar el país de recogida. Actualiza el GPS.",
                )
                return@launch
            }
            val meteredQuote = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                runCatching {
                    require(currency.equals("CRC", ignoreCase = true)) {
                        "La tarifa por tiempo y distancia inicia en CRC"
                    }
                    RideFareEngine.quoteCostaRica(
                        distanceMeters = estimatedDistanceMeters,
                        durationSeconds = estimatedDurationSeconds,
                    )
                }.getOrElse {
                    _rideVerificationNotice.emit(
                        it.message ?: "No se pudo calcular la tarifa por tiempo y distancia.",
                    )
                    return@launch
                }
            } else {
                null
            }
            val offeredFareMinor = meteredQuote?.estimatedTotalMinor
                ?: run {
                    val priceOfferMinor = rideFareToMinorUnits(priceOffer, currency)
                    RideFareBidPolicy.normalizeMinor(priceOfferMinor, currency)
                }
            if (offeredFareMinor <= 0L) {
                _rideVerificationNotice.emit("La tarifa debe ser mayor que cero.")
                return@launch
            }
            val authoritativePaymentMethod = when (paymentMethod.trim().uppercase()) {
                "CASH" -> "CASH"
                "SINPE", "SINPE_MOVIL" -> "SINPE"
                else -> {
                    _rideVerificationNotice.emit(
                        "Selecciona Efectivo o SINPE antes de solicitar el viaje.",
                    )
                    return@launch
                }
            }
            val normalizedGuestName = guestName?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedGuestPhone = guestPhoneE164?.trim()?.takeIf {
                it.matches(Regex("^\\+[1-9][0-9]{7,14}$"))
            }
            if ((normalizedGuestName == null) != (normalizedGuestPhone == null)) {
                _rideVerificationNotice.emit(
                    "Para pedir a otra persona, indica nombre y teléfono en formato internacional.",
                )
                return@launch
            }
            val isGuestRide = normalizedGuestName != null
            val prefsJson = passengerPreferences?.toJson() ?: "{}"
            val request = RideRequestEntity(
                requestId = UUID.randomUUID().toString(),
                passengerId = passengerId,
                passengerName = passengerName,
                passengerPhone = passengerPhone,
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                pickupAddress = pickupAddr,
                pickupAccuracy = pickupAcc,
                destLatitude = destLat,
                destLongitude = destLng,
                destAddress = destAddr,
                priceOffer = offeredFareMinor.toDouble(),
                priceOfferMinor = offeredFareMinor,
                currency = currency,
                estimatedDistanceKm = estDistance,
                estimatedDurationMin = estDuration,
                stopsJson = stopsJson,
                paymentMethod = authoritativePaymentMethod,
                fareMode = fareMode.name,
                distanceRateMinorPerKm = meteredQuote?.distanceRateMinorPerKm ?: 0L,
                timeRateMinorPerMinute = meteredQuote?.timeRateMinorPerMinute ?: 0L,
                estimatedFareMinor = offeredFareMinor,
                fareRateCardVersion = meteredQuote?.rateCardVersion ?: 1L,
                allowsInTripStops = RideFareEngine.allowsStopsDuringTrip(fareMode),
                fareBreakdownJson = if (meteredQuote == null) {
                    """{"mode":"OPEN_BID","acceptedFareMinor":$offeredFareMinor,"currency":"${currency.uppercase()}","preferences":$prefsJson}"""
                } else {
                    """{"mode":"METERED_TIME_DISTANCE","distanceFareMinor":${meteredQuote.distanceFareMinor},"timeFareMinor":${meteredQuote.timeFareMinor},"estimatedTotalMinor":${meteredQuote.estimatedTotalMinor},"currency":"CRC","rateCardVersion":${meteredQuote.rateCardVersion},"preferences":$prefsJson}"""
                },
                // Local persistence is not proof that the authoritative RPC
                // accepted the request. The worker replaces these values from
                // the server projection after a real receipt.
                status = "PENDING_PUBLICATION",
                serverState = "PENDING_PUBLICATION",
                serverVersion = 0L,
                syncState = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertRequest(request)
            val result = rideCommandRepository.enqueue(
                envelope = rideCommandEnvelope(
                    requestId = request.requestId,
                    serverVersion = 0L,
                    type = if (isGuestRide) {
                        RideCommandType.PUBLISH_GUEST
                    } else {
                        RideCommandType.PUBLISH
                    },
                ),
                payload = RideCommandPayload(
                    displayName = passengerName,
                    guestName = normalizedGuestName,
                    guestPhoneE164 = normalizedGuestPhone,
                    countryCode = normalizedCountryCode,
                    pickupLatitude = pickupLat.toString(),
                    pickupLongitude = pickupLng.toString(),
                    pickupAddress = pickupAddr,
                    destinationLatitude = destLat.toString(),
                    destinationLongitude = destLng.toString(),
                    destinationAddress = destAddr,
                    offeredFareMinor = offeredFareMinor,
                    currency = currency.uppercase(),
                    paymentMethod = authoritativePaymentMethod,
                    stopsJson = stopsJson,
                    fareMode = fareMode.name,
                    distanceRateMinorPerKm = meteredQuote?.distanceRateMinorPerKm ?: 0L,
                    timeRateMinorPerMinute = meteredQuote?.timeRateMinorPerMinute ?: 0L,
                    estimatedDistanceMeters = estimatedDistanceMeters,
                    estimatedDurationSeconds = estimatedDurationSeconds,
                    fareRateCardVersion = meteredQuote?.rateCardVersion ?: 1L,
                    allowsInTripStops = RideFareEngine.allowsStopsDuringTrip(fareMode),
                    detail = prefsJson,
                ),
            )
            val queued = reportRideCommandEnqueue(
                result = result,
                acceptedMessage =
                    "Solicitud enviada. La autoridad de Viajes está confirmándola.",
            )
            if (!queued) {
                rideDao.deleteRequest(request.requestId)
                return@launch
            }
            withContext(Dispatchers.Main) {
                selectActiveRide(request)
            }
        }
    }

    fun replaceRideStops(
        requestId: String,
        stopsJson: String,
        estimatedDistanceMeters: Long,
        estimatedDurationSeconds: Long,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.fareMode != RideFareMode.METERED_TIME_DISTANCE.name) {
                _rideVerificationNotice.emit(
                    "Pon tu precio bloquea las paradas después de publicar la solicitud.",
                )
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.UPDATE_ROUTE,
                payload = RideCommandPayload(
                    stopsJson = stopsJson,
                    estimatedDistanceMeters = estimatedDistanceMeters,
                    estimatedDurationSeconds = estimatedDurationSeconds,
                ),
            )
            reportRideCommandEnqueue(
                result,
                "¡Paradas actualizadas! Recalculando ruta...",
            )
        }
    }

    fun makeRideOffer(
        requestId: String,
        counterPrice: Double,
        currency: String,
        estArrivalMin: Int,
        message: String?,
    ) {
        RideObservability.event("offer_submit_started", requestId = requestId)
        viewModelScope.launch(Dispatchers.IO) {
            val actorId =
                currentCloudUserId()
                    ?: run {
                        _rideVerificationNotice.emit(
                            "Inicia sesión para ofertar."
                        )
                        return@launch
                    }

            val request =
                rideDao.getRequestById(requestId)
                    ?: return@launch

            if (request.passengerId == actorId) {
                _rideVerificationNotice.emit(
                    "No puedes ofertar en tu propia solicitud."
                )
                return@launch
            }

            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit(
                    "Espera la confirmación del servidor."
                )
                return@launch
            }

            val vehicleId =
                activeVerifiedRemoteVehicleId()
                    ?: run {
                        _rideVerificationNotice.emit(
                            "Activa un vehículo VERIFIED."
                        )
                        return@launch
                    }

            val gps =
                _currentGpsLocation.value
                    ?: run {
                        _rideVerificationNotice.emit(
                            "Esperando ubicación GPS real."
                        )
                        return@launch
                    }

            val counterPriceMinor = rideFareToMinorUnits(counterPrice, currency)
            val fareMinor = RideFareBidPolicy.normalizeMinor(counterPriceMinor, currency)
            val offerId = UUID.randomUUID().toString()

            val verifiedDriver = driverVerification.value
            val dName = verifiedDriver?.fullName?.takeIf { it.isNotBlank() } ?: "Chofer Elysium"
            val dPhone = verifiedDriver?.phone.orEmpty()
            val dVeh = "${verifiedDriver?.vehicleMake.orEmpty()} ${verifiedDriver?.vehicleModel.orEmpty()} ${verifiedDriver?.vehicleYear ?: ""}".trim()

            val offer = RideOfferEntity(
                offerId = offerId,
                requestId = requestId,
                driverId = actorId,
                driverName = dName,
                driverPhone = dPhone,
                driverRating = request.driverRating ?: 0.0,
                driverTotalTrips = 0,
                vehicleDescription = dVeh,
                counterPrice = counterPrice,
                currency = currency,
                estimatedArrivalMin = estArrivalMin,
                driverLatitude = gps.latitude,
                driverLongitude = gps.longitude,
                message = message,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )

            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.SUBMIT_OFFER,
                    payload = RideCommandPayload(
                        offerId = offerId,
                        vehicleId = vehicleId,
                        fareMinor = fareMinor,
                        currency = currency.uppercase(),
                        etaSeconds = estArrivalMin.coerceAtLeast(0) * 60,
                    ),
                ),
                acceptedMessage = "Oferta enviada. Esperando respuesta del pasajero.",
            )

            if (queued) {
                rideDao.insertOffer(offer)
            }
            RideObservability.event(
                "offer_submit",
                outcome = if (queued) "SUCCEEDED" else "REJECTED",
                requestId = requestId,
                detail = "command",
            )
        }
    }

    fun makeRideOffer(
        requestId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        driverRating: Double,
        driverTotalTrips: Int,
        vehicleDesc: String,
        counterPrice: Double,
        currency: String,
        estArrivalMin: Int,
        driverLat: Double,
        driverLng: Double,
        message: String?
    ) {
        makeRideOffer(
            requestId = requestId,
            counterPrice = counterPrice,
            currency = currency,
            estArrivalMin = estArrivalMin,
            message = message,
        )
    }

    fun acceptRideOffer(
        requestId: String,
        offerId: String,
    ) {
        RideObservability.event(
            "offer_accept_started",
            requestId = requestId,
        )

        viewModelScope.launch(Dispatchers.IO) {
            val request =
                rideDao.getRequestById(requestId)
                    ?: run {
                        _rideVerificationNotice.emit(
                            "El viaje ya no está disponible."
                        )
                        return@launch
                    }

            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit(
                    "Actualizando el viaje antes de aceptar la oferta."
                )

                refreshRideProjectionNow()
                return@launch
            }

            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.ACCEPT_OFFER,
                    payload = RideCommandPayload(offerId = offerId),
                ),
                acceptedMessage =
                    "Aceptando oferta. Esperando confirmación del servidor.",
            )

            RideObservability.event(
                "offer_accept",
                outcome =
                    if (queued) "PENDING"
                    else "REJECTED",
                requestId = requestId,
                detail =
                    "server_authority_required",
            )
        }
    }

    fun claimRideFirstCome(
        requestId: String,
    ) {
        if (requestId.isBlank()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val actorId = currentCloudUserId()

            if (actorId.isNullOrBlank()) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message = "Inicia sesión antes de aceptar un viaje.",
                    )
                )
                return@launch
            }

            val request = rideDao.getRequestById(requestId)

            if (request == null) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message = "La solicitud ya no está disponible.",
                    )
                )
                refreshRideProjectionNow()
                return@launch
            }

            if (request.passengerId == actorId) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message = "No puedes aceptar un viaje solicitado por tu propia cuenta.",
                    )
                )
                return@launch
            }

            if (request.serverVersion <= 0L) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message =
                            "La solicitud todavía no fue confirmada por el servidor.",
                    )
                )
                refreshRideProjectionNow()
                return@launch
            }

            if (
                request.assignedDriverId != null ||
                request.status != "OPEN"
            ) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message =
                            "El viaje cambió de estado. Actualizando solicitudes.",
                    )
                )
                refreshRideProjectionNow()
                return@launch
            }

            val remoteVehicleId =
                activeVerifiedRemoteVehicleId()

            if (remoteVehicleId == null) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        pending = false,
                        message =
                            "Necesitas un vehículo activo y VERIFIED para aceptar viajes.",
                    )
                )

                refreshRideDriverVehicles()
                return@launch
            }

            val result = rideClaimEnqueueMutex.withLock {
                if ((_rideClaimUiState.value as? RideClaimUiState.Pending)?.requestId == requestId) {
                    RideCommandEnqueueResult.AlreadyQueued
                } else {
                    enqueueAuthoritativeRideCommand(
                        request = request,
                        type = RideCommandType.CLAIM,
                        payload = RideCommandPayload(vehicleId = remoteVehicleId),
                    ).also { queuedResult ->
                        if (queuedResult is RideCommandEnqueueResult.Enqueued ||
                            queuedResult is RideCommandEnqueueResult.AlreadyQueued
                        ) _rideClaimUiState.value = RideClaimUiState.Pending(requestId)
                    }
                }
            }

            val queued =
                result is RideCommandEnqueueResult.Enqueued ||
                    result is RideCommandEnqueueResult.AlreadyQueued

            _rideClaimFeedback.emit(
                RideClaimFeedback(
                    requestId = requestId,
                    won = false,
                    pending = queued,
                    message = when {
                        queued ->
                            "Confirmando viaje con el servidor…"

                        result is
                            RideCommandEnqueueResult.AuthenticationRequired ->
                            "La sesión expiró. Inicia sesión nuevamente."

                        result is
                            RideCommandEnqueueResult.InvalidCommand ->
                            result.message

                        result is
                            RideCommandEnqueueResult.IdempotencyConflict ->
                            result.message

                        else ->
                            "No se pudo enviar la aceptación."
                    },
                )
            )

            if (queued) {
                RideObservability.event(
                    "ride_claim",
                    outcome = "PENDING",
                    requestId = requestId,
                    detail = "awaiting_server_authority",
                )
            }
        }
    }

    fun claimRideFirstCome(
        requestId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicleDescription: String,
    ) {
        claimRideFirstCome(requestId)
    }

    fun verifyRideBoardingPin(
        requestId: String,
        candidate: String,
    ) {
        if (!candidate.matches(Regex("[0-9]{4}"))) {
            _ridePinFeedback.tryEmit(
                "El PIN debe contener exactamente cuatro dígitos."
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val request =
                rideDao.getRequestById(requestId)
                    ?: run {
                        _ridePinFeedback.emit(
                            "El viaje ya no está disponible."
                        )
                        return@launch
                    }

            if (
                request.serverVersion <= 0L ||
                request.serverState != "ARRIVED"
            ) {
                _ridePinFeedback.emit(
                    "El servidor todavía no confirmó la llegada."
                )
                refreshRideProjectionNow()
                return@launch
            }

            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type =
                        RideCommandType.VERIFY_BOARDING_PIN,
                    payload = RideCommandPayload(
                        boardingPin = candidate,
                    ),
                ),
                acceptedMessage =
                    "Verificando PIN con el servidor.",
            )

            if (queued) {
                _ridePinFeedback.emit(
                    "Verificando PIN. El viaje no iniciará hasta recibir confirmación."
                )
            }
        }
    }

    fun issueRideBoardingPin(
        requestId: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val request =
                rideDao.getRequestById(requestId)
                    ?: return@launch

            if (
                request.serverVersion <= 0L ||
                request.serverState != "ARRIVED"
            ) {
                _ridePinFeedback.emit(
                    "El PIN sólo puede generarse cuando la llegada esté confirmada."
                )
                return@launch
            }

            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type =
                        RideCommandType.ISSUE_BOARDING_PIN,
                ),
                acceptedMessage =
                    "Generando PIN privado en el servidor.",
            )

            if (queued) {
                _ridePinFeedback.emit(
                    "Generando PIN…"
                )
            }
        }
    }

    fun updateRideStatus(requestId: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch

            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit(
                    "El viaje se está preparando. Intenta de nuevo en un momento.",
                )
                return@launch
            }
            val command = when (newStatus) {
                "ARRIVED" -> when (request.serverState) {
                    "ASSIGNED" -> RideCommandType.DRIVER_EN_ROUTE
                    "DRIVER_EN_ROUTE" -> RideCommandType.DRIVER_ARRIVED
                    else -> null
                }
                "IN_PROGRESS" -> RideCommandType.START
                "COMPLETED" -> RideCommandType.COMPLETE
                else -> null
            }
            if (command == null) {
                _rideVerificationNotice.emit(
                    "La autoridad rechazó esta transición desde ${request.serverState ?: request.status}.",
                )
                return@launch
            }
            val commandPayload = if (command == RideCommandType.DRIVER_ARRIVED) {
                val gps = _currentGpsLocation.value
                val pickup = RideGeoPoint(
                    latitude = request.pickupLatitude,
                    longitude = request.pickupLongitude,
                    accuracyMeters = request.pickupAccuracy,
                    capturedAtEpochMs = request.createdAt,
                )
                val driverPoint = gps?.let {
                    RideGeoPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracyMeters = it.accuracy,
                        capturedAtEpochMs = it.timestamp.coerceAtLeast(0L),
                    )
                }
                val arrival = RideArrivalPolicy.evaluate(
                    driver = driverPoint,
                    pickup = pickup,
                    nowEpochMs = System.currentTimeMillis(),
                )
                if (!arrival.allowed || gps == null) {
                    val distance = arrival.distanceMeters?.let { " · ${it.toInt()} m" }.orEmpty()
                    _rideVerificationNotice.emit(arrival.reason + distance)
                    return@launch
                }
                RideCommandPayload(
                    driverLatitude = gps.latitude.toString(),
                    driverLongitude = gps.longitude.toString(),
                    driverAccuracyMeters = gps.accuracy.toString(),
                    driverCapturedAt = Instant.ofEpochMilli(gps.timestamp).toString(),
                )
            } else {
                RideCommandPayload()
            }
            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = command,
                    payload = commandPayload,
                ),
                acceptedMessage = when (command) {
                    RideCommandType.DRIVER_EN_ROUTE ->
                        "Salida hacia el pasajero pendiente de confirmación."
                    RideCommandType.DRIVER_ARRIVED ->
                        "Llegada enviada; el pasajero podrá generar su PIN."
                    RideCommandType.START ->
                        "Inicio enviado; esperando confirmación autoritativa."
                    RideCommandType.COMPLETE ->
                        "Cierre enviado; calculando total y comisión exacta."
                    else -> "Operación enviada."
                },
            )
            if (queued && command == RideCommandType.START) {
                voiceFeedbackManager.speak(
                    "Validando el inicio del servicio.",
                    "Validating the start of the service.",
                )
            }
        }
    }

    /** Pending is durable outbox state, never a claim that the server cancelled. */
    fun observeRideForCancellation(requestId: String) = rideDao.observeRequest(requestId)

    fun latestBoardingPinVerification(requestId: String) =
        rideCommandRepository.latestBoardingPinVerification(requestId)

    fun cancellationCommands(requestId: String) = rideCommandRepository.cancellationCommands(requestId)

    fun localCancelStuckRide(requestId: String) {
        cancelRide(requestId,RideCancellationReason.DUPLICATE_OR_ACCIDENTAL,null,"PASSENGER")
    }

    fun clearAllStuckRides() {
        viewModelScope.launch(Dispatchers.IO) {
            val actor=currentRideActorId
            val requests=rideDao.getRequestsByPassenger(actor).first()
            requests.filter { it.serverVersion==0L && it.status in setOf("PENDING_PUBLICATION","OPEN") }
                .forEach { localCancelStuckRide(it.requestId) }
            _rideVerificationNotice.emit("Cancelaciones solicitadas; pendientes de confirmación del servidor.")
        }
    }

    fun cancelRide(
        requestId: String,
        reason: RideCancellationReason,
        detail: String?,
        actorRole: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val role = runCatching { RideActorRole.valueOf(actorRole.uppercase()) }.getOrNull()
                ?: if (_rideDriverMode.value) RideActorRole.DRIVER else RideActorRole.PASSENGER
            val safeReason = if (role == RideActorRole.DRIVER && reason !in RideCancellationPolicy.reasonsFor(role)) {
                RideCancellationReason.OTHER
            } else reason
            val error = when {
                !RideCancellationPolicy.isDetailValid(safeReason, detail) -> "Revisa el detalle de la cancelación."
                role !in setOf(RideActorRole.PASSENGER, RideActorRole.DRIVER) -> "El rol no permite cancelar este viaje."
                currentCloudUserId().isNullOrBlank() && currentRideActorId.isBlank() -> "Inicia sesión para cancelar el viaje."
                else -> null
            }
            if (error != null) {
                _rideVerificationNotice.emit(error)
                return@launch
            }
            val request = rideDao.getRequestById(requestId)
            if (request == null) {
                rideDao.clearActiveRideSelectionsForRide(requestId)
                applyActiveRide(null)
                _rideVerificationNotice.emit("No se encontró el viaje. Solicitud cerrada.")
                return@launch
            }
            // If already completed or cancelled, release local pointer immediately:
            if (com.elysium369.meet.ride.domain.RideAuthorityEvidence.isTerminalSnapshot(request.serverState,request.serverVersion)) {
                rideDao.clearActiveRideSelectionsForRide(requestId)
                applyActiveRide(null)
                _rideVerificationNotice.emit("El servicio ya se encuentra finalizado o cancelado.")
                return@launch
            }
            // If still in PENDING_PUBLICATION (version 0):
            if (request.serverVersion == 0L || request.status == "PENDING_PUBLICATION") {
                _rideVerificationNotice.emit("Cancelación guardada; pendiente de confirmación del servidor.")
                rideCommandRepository.enqueue(
                    envelope = rideCommandEnvelope(
                        requestId = requestId,
                        serverVersion = 0L,
                        type = RideCommandType.CANCEL,
                    ),
                    payload = RideCommandPayload(
                        reasonCode = safeReason.name,
                        detail = detail?.trim()?.takeIf(String::isNotEmpty),
                    ),
                )
                RideCommandSyncWorker.enqueueNow(context)
                return@launch
            }
            val expectedVersion: Long = request.serverVersion

            val requeued = rideCommandRepository.forceRequeueStuckCancellation(
                rideId = requestId,
            )
            if (requeued > 0) {
                RideCommandSyncWorker.enqueueNow(context)
                _rideVerificationNotice.emit("Cancelación pendiente de confirmación del servidor.")
            } else {
                val queued = reportRideCommandEnqueue(
                    result = rideCommandRepository.enqueue(
                        envelope = rideCommandEnvelope(
                            requestId = requestId,
                            serverVersion = expectedVersion,
                            type = RideCommandType.CANCEL,
                        ),
                        payload = RideCommandPayload(
                            reasonCode = safeReason.name,
                            detail = detail?.trim()?.takeIf(String::isNotEmpty),
                        ),
                    ),
                    acceptedMessage = "Cancelación pendiente de confirmación del servidor.",
                )
                if (queued) RideCommandSyncWorker.enqueueNow(context)
            }
        }
    }

    fun confirmRidePayment(requestId: String, referenceNumber: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = com.elysium369.meet.ride.payment.RidePaymentStatus.DRIVER_MARKED_RECEIVED
            val result = ridePaymentGateway.attestPaymentEvent(
                tripId = requestId,
                newStatus = status,
                referenceNumber = referenceNumber,
            )
            result.onSuccess { serverMessage ->
                _rideVerificationNotice.emit("Pago confirmado. $serverMessage")
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _rideVerificationNotice.emit("Error confirmando pago: ${error.message}")
            }
        }
    }

    fun activateRideGuardian(
        requestId: String,
        signalType: RideSafetySignalType,
        detail: String?,
    ) {
        if (detail != null && detail.length > 500) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (!RideGuardianPolicy.canSignal(request.serverState, request.serverVersion)) {
                _rideSafetyFeedback.emit(
                    "Guardian requiere un viaje activo para funcionar.",
                )
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.SAFETY_SIGNAL,
                payload = RideCommandPayload(
                    safetySignalType = signalType.name,
                    detail = detail?.trim()?.takeIf(String::isNotEmpty),
                ),
            )
            val message = when (result) {
                RideCommandEnqueueResult.Enqueued ->
                    "Guardian envió la señal para registro y revisión. La app no llamó automáticamente a autoridades."
                RideCommandEnqueueResult.AlreadyQueued ->
                    "Esta señal de Guardian ya está pendiente de confirmación."
                RideCommandEnqueueResult.AuthenticationRequired ->
                    "Guardian necesita una sesión autenticada para proteger el historial."
                is RideCommandEnqueueResult.IdempotencyConflict ->
                    "La señal no se duplicó porque existe un conflicto local."
                is RideCommandEnqueueResult.InvalidCommand -> result.message
            }
            _rideSafetyFeedback.emit(message)
        }
    }

    fun openRideSupportCase(
        requestId: String,
        category: RideSupportCategory,
        summary: String,
    ) {
        if (!RideSupportPolicy.isValidSummary(summary)) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideSupportFeedback.emit(
                    "El viaje se está preparando. Intenta de nuevo en un momento.",
                )
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.OPEN_SUPPORT_CASE,
                payload = RideCommandPayload(
                    supportCategory = category.name,
                    supportSummary = summary.trim(),
                ),
            )
            val message = when (result) {
                RideCommandEnqueueResult.Enqueued ->
                    "Caso enviado. Soporte lo confirmará con un identificador autoritativo."
                RideCommandEnqueueResult.AlreadyQueued ->
                    "Este caso ya está pendiente de confirmación."
                RideCommandEnqueueResult.AuthenticationRequired ->
                    "Inicia sesión para abrir un caso protegido."
                is RideCommandEnqueueResult.IdempotencyConflict ->
                    "El caso no se duplicó porque existe un conflicto local."
                is RideCommandEnqueueResult.InvalidCommand -> result.message
            }
            _rideSupportFeedback.emit(message)
        }
    }

    fun submitRideRating(requestId: String, isPassengerRating: Boolean, stars: Double, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            val actorId = currentCloudUserId()

            val result = com.elysium369.meet.ride.domain.RideRatingSubmission.submit(
                actorId = actorId,
                passengerId = request.passengerId,
                assignedDriverId = request.assignedDriverId,
                serverState = request.serverState,
                serverVersion = request.serverVersion,
                passengerRating = isPassengerRating,
                stars = stars,
                confirm = {
                    if (isPassengerRating) {
                        rideReputationGateway.recordTripFeedback(requestId, stars.toInt())
                    } else {
                        rideReputationGateway.recordPassengerFeedback(requestId, stars.toInt())
                    }
                },
                persistConfirmed = {
                    if (isPassengerRating) {
                        ratingDao.insertRating(
                            RatingEntity(
                                ratingId = "ride-feedback:$requestId:$actorId",
                                targetType = "DRIVER",
                                targetId = requireNotNull(request.assignedDriverId),
                                sourceId = requireNotNull(actorId),
                                sourceName = request.passengerName,
                                stars = stars,
                                comment = comment.trim(),
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        rideDao.updatePassengerRating(requestId, stars)
                    } else {
                        ratingDao.insertRating(
                            RatingEntity(
                                ratingId = "ride-feedback:$requestId:$actorId",
                                targetType = "PASSENGER",
                                targetId = request.passengerId,
                                sourceId = requireNotNull(actorId),
                                sourceName = request.assignedDriverName ?: "Chofer",
                                stars = stars,
                                comment = comment.trim(),
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        rideDao.updateDriverRating(requestId, stars)
                    }
                },
            )
            result.onSuccess {
                val role = if (isPassengerRating) "PASSENGER" else "DRIVER"
                markRatingSettled(requestId, role)
                _pendingRatingRide.value = null
                _rideVerificationNotice.emit("¡Calificación enviada! Gracias.")
            }.onFailure { error ->
                _rideVerificationNotice.emit(
                    "No se confirmó la calificación: ${error.message ?: "intenta nuevamente"}.",
                )
            }
            RideObservability.event(
                "rating_remote",
                outcome = if (result.isSuccess) "SUCCEEDED" else "FAILED",
                requestId = requestId,
            )
            val updated = rideDao.getRequestById(requestId)
            withContext(Dispatchers.Main) {
                if (_activeRideRequest.value?.requestId == requestId && currentCloudUserId() == actorId) {
                    _activeRideRequest.value = updated
                }
            }
        }
    }

    fun updateRidePrice(requestId: String, newPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            val newPriceMinor = rideFareToMinorUnits(newPrice, request.currency)
            val normalizedMinor = RideFareBidPolicy.normalizeMinor(newPriceMinor, request.currency)
            val normalizedPrice = normalizedMinor.toDouble()
            val updated = request.copy(priceOffer = normalizedPrice)
            rideDao.insertRequest(updated)

            val systemMsg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = "SYSTEM",
                senderName = "Sistema",
                senderRole = "SYSTEM",
                messageType = "TEXT",
                textContent = "El pasajero ajustó la oferta Elysium a $normalizedPrice ${request.currency}.",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(systemMsg)

            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updated
            }
        }
    }

    fun publishRidePriceIncrease(requestId: String, newPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            val newPriceMinor = rideFareToMinorUnits(newPrice, request.currency)
            val normalizedMinor = RideFareBidPolicy.normalizeMinor(newPriceMinor, request.currency)
            val normalizedPrice = normalizedMinor.toDouble()
            val updated = request.copy(priceOffer = normalizedPrice, priceOfferMinor = normalizedMinor)
            rideDao.insertRequest(updated)

            val systemMsg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = "SYSTEM",
                senderName = "Sistema",
                senderRole = "SYSTEM",
                messageType = "TEXT",
                textContent = "El pasajero aumentó la oferta a $normalizedPrice ${request.currency}.",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(systemMsg)

            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updated
            }

            // Authoritative Cloud Sync to notify all drivers via Supabase (CAS & Idempotent)
            val rpcResult = runCatching {
                SupabaseManager.client.postgrest.rpc(
                    function = "ride_change_fare_v1",
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_request_id", requestId)
                        put("p_fare_minor", normalizedMinor)
                        put("p_currency", request.currency.uppercase())
                        put("p_expected_version", request.serverVersion)
                        put("p_idempotency_key", java.util.UUID.randomUUID().toString())
                    }
                )
            }
            if (rpcResult.isFailure) {
                android.util.Log.w("ObdViewModel", "ride_change_fare_v1 failed closed: ${rpcResult.exceptionOrNull()?.message}")
            }
            _rideVerificationNotice.emit("Oferta aumentada a ${normalizedPrice.toInt()} ${request.currency}. Notificando a choferes...")
            refreshRideProjectionNow()
        }
    }

    fun rejectRideOffer(requestId: String, offerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            rideDao.updateOfferStatus(offerId, "REJECTED")
            if (_activeRideRequest.value?.requestId == requestId) {
                val updatedRequest = rideDao.getRequestById(requestId)
                withContext(Dispatchers.Main) {
                    _activeRideRequest.value = updatedRequest
                }
            }
        }
    }

    // Chat Functions
    fun sendRideChatMessage(requestId: String, senderId: String, senderName: String, role: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = senderId,
                senderName = senderName,
                senderRole = role,
                messageType = "TEXT",
                textContent = text,
                syncState = initialRideChatSyncState(),
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(msg)
            rideChatBridge?.broadcastMessage(msg)
            syncRideChat(requestId)
        }
    }

    fun sendRidePresetMessage(requestId: String, senderId: String, senderName: String, role: String, presetText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = senderId,
                senderName = senderName,
                senderRole = role,
                messageType = "PRESET",
                textContent = presetText,
                syncState = initialRideChatSyncState(),
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(msg)
            rideChatBridge?.broadcastMessage(msg)
        }
    }

    fun sendRideChatImage(
        context: Context,
        requestId: String,
        senderId: String,
        senderName: String,
        role: String,
        source: android.net.Uri,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageDirectory = java.io.File(context.filesDir, "meet_rides_images").apply { mkdirs() }
            val mimeType = context.contentResolver.getType(source) ?: "image/jpeg"
            val extension = when (mimeType.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = java.io.File(
                imageDirectory,
                "ride_${requestId.take(12)}_${System.currentTimeMillis()}.$extension",
            )
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: error("No fue posible abrir la imagen seleccionada")
                val entity = RideChatMessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    rideRequestId = requestId,
                    senderId = senderId,
                    senderName = senderName,
                    senderRole = role,
                    messageType = "IMAGE",
                    imageFilePath = destination.absolutePath,
                    mediaMimeType = mimeType,
                    syncState = initialRideChatSyncState(),
                    createdAt = System.currentTimeMillis(),
                )
                rideDao.insertChatMessage(entity)
                val imageBytes = destination.readBytes()
                rideChatBridge?.broadcastMessage(entity, imageBytes)
            } catch (error: Exception) {
                destination.delete()
                Log.e("ObdViewModel", "Failed to persist ride chat image", error)
            }
        }
    }

    fun sendRideImageBytes(
        context: Context,
        requestId: String,
        senderId: String,
        senderName: String,
        role: String,
        bytes: ByteArray,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageDirectory = java.io.File(context.filesDir, "meet_rides_images").apply { mkdirs() }
            val destination = java.io.File(
                imageDirectory,
                "ride_${requestId.take(12)}_${System.currentTimeMillis()}.jpg",
            )
            try {
                destination.writeBytes(bytes)
                val entity = RideChatMessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    rideRequestId = requestId,
                    senderId = senderId,
                    senderName = senderName,
                    senderRole = role,
                    messageType = "IMAGE",
                    imageFilePath = destination.absolutePath,
                    mediaMimeType = "image/jpeg",
                    syncState = initialRideChatSyncState(),
                    createdAt = System.currentTimeMillis(),
                )
                rideDao.insertChatMessage(entity)
                rideChatBridge?.broadcastMessage(entity, bytes)
            } catch (error: Exception) {
                destination.delete()
                Log.e("ObdViewModel", "Failed to persist ride chat image from bytes", error)
            }
        }
    }

    // Preset messages management
    fun addDriverPresetMessage(msg: String) {
        val list = _driverPresetMessages.value.toMutableList()
        if (!list.contains(msg) && msg.isNotBlank()) {
            list.add(msg)
            _driverPresetMessages.value = list
        }
    }

    fun removeDriverPresetMessage(msg: String) {
        val list = _driverPresetMessages.value.toMutableList()
        list.remove(msg)
        _driverPresetMessages.value = list
    }

    // local Audio Recording & Playback Logic
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentRecordingFile: java.io.File? = null
    private var recordingStartTime: Long = 0L

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow<String?>(null) // Muestra el path reproduciéndose
    val isPlayingAudio: StateFlow<String?> = _isPlayingAudio.asStateFlow()

    fun startAudioRecording(context: Context) {
        if (_isRecordingAudio.value) return
        try {
            val audioDir = java.io.File(context.filesDir, "meet_rides_audio")
            if (!audioDir.exists()) audioDir.mkdirs()

            currentRecordingFile = java.io.File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            mediaRecorder = android.media.MediaRecorder(context).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            _isRecordingAudio.value = true
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Failed to start audio recording", e)
            _isRecordingAudio.value = false
        }
    }

    fun stopAndSendAudioRecording(requestId: String, senderId: String, senderName: String, role: String) {
        if (!_isRecordingAudio.value) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecordingAudio.value = false

            val file = currentRecordingFile
            if (file != null && file.exists()) {
                val duration = System.currentTimeMillis() - recordingStartTime
                if (duration > 800) { // Al menos 0.8 segundos de audio
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = RideChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            rideRequestId = requestId,
                            senderId = senderId,
                            senderName = senderName,
                            senderRole = role,
                            messageType = "AUDIO",
                            audioFilePath = file.absolutePath,
                            mediaMimeType = "audio/mp4",
                            audioDurationMs = duration,
                            syncState = initialRideChatSyncState(),
                            createdAt = System.currentTimeMillis()
                        )
                        rideDao.insertChatMessage(msg)
                        val audioBytes = file.readBytes()
                        rideChatBridge?.broadcastMessage(msg, audioBytes)
                    }
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Failed to stop audio recording", e)
            _isRecordingAudio.value = false
        }
    }

    fun playAudioMessage(filePath: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (_isPlayingAudio.value == filePath) {
                    stopAudioMessage()
                    return@launch
                }
                stopAudioMessage()

                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopAudioMessage()
                    }
                }
                _isPlayingAudio.value = filePath
            } catch (e: Exception) {
                Log.e("ObdViewModel", "MediaPlayer failed", e)
                _isPlayingAudio.value = null
            }
        }
    }

    fun stopAudioMessage() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        _isPlayingAudio.value = null
    }

    private fun initialRideChatSyncState(): String =
        if (currentCloudUserId() == null) "LOCAL_ONLY" else "PENDING"


    /** Open Waze with navigation to coordinates. Automatically falls back to Google Maps if needed */
    fun openWaze(context: Context, lat: Double, lng: Double, label: String? = null) {
        if (lat == 0.0 && lng == 0.0) {
            android.widget.Toast.makeText(context, "Ubicación GPS no disponible", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val wazeUri = android.net.Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, wazeUri).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback 1: Google Maps turn-by-turn navigation
                val mapsUri = android.net.Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapsUri).apply {
                    setPackage("com.google.android.apps.maps")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                try {
                    // Fallback 2: Waze Web URL
                    val webUri = android.net.Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e3: Exception) {
                    try {
                        val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(${label ?: "Destino"})")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e4: Exception) {
                        android.widget.Toast.makeText(context, "No se pudo abrir Waze ni Google Maps", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Share location via WhatsApp */
    fun shareLocationViaWhatsApp(context: Context, lat: Double, lng: Double, label: String) {
        try {
            val mapsLink = "https://www.google.com/maps?q=$lat,$lng"
            val message = "📍 Ubicación: $label\n$mapsLink"
            val waUri = android.net.Uri.parse("https://api.whatsapp.com/send?text=${android.net.Uri.encode(message)}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, waUri)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "WhatsApp share failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE 9 — IDENTITY VERIFICATION (UBER-GRADE ONBOARDING)
    // ═══════════════════════════════════════════════════════════════════════════

    // ── Driver verification state ────────────────────────────────────────────
    val driverVerification: StateFlow<com.elysium369.meet.data.local.entities.DriverVerificationEntity?> =
        activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
            if (principal.isAuthenticated) {
                rideDao.getDriverVerificationFlow(principal.id)
            } else {
                val fallbackId = currentCloudUserId()
                if (fallbackId != null) {
                    rideDao.getDriverVerificationFlow(fallbackId)
                } else {
                    flowOf(null)
                }
            }
        }
            .onEach { verification ->
                if (
                    BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE &&
                    verification?.status == "PENDING"
                ) {
                    val evidence = evaluateDriverEvidence(verification)
                    if (evidence.isReady) {
                        val now = System.currentTimeMillis()
                        rideDao.updateDriverVerificationStatus(
                            driverId = verification.driverId,
                            status = RideVerificationPolicy.PILOT_APPROVED,
                            approvedAt = now,
                            updatedAt = now,
                        )
                        android.util.Log.i(
                            "ElysiumRides",
                            "Pending driver verification upgraded to local pilot access",
                        )
                    } else {
                        android.util.Log.w(
                            "ElysiumRides",
                            "Driver pilot access withheld: ${evidence.issues.joinToString()}",
                        )
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las evidencias del chofer; una o más fotos no están disponibles.",
                        )
                    }
                }
                if (
                    verification?.status in setOf("PENDING", RideVerificationPolicy.PILOT_APPROVED) &&
                    verification != null && evaluateDriverEvidence(verification).isReady
                ) {
                    // Re-enqueueing is deliberate: WorkManager is the durable
                    // authority and this recovers an interrupted/failed release
                    // worker after process recreation or an app upgrade.
                    enqueueDriverPilotEnrollment(verification)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ── Passenger verification state ─────────────────────────────────────────
    val passengerVerification: StateFlow<com.elysium369.meet.data.local.entities.PassengerVerificationEntity?> =
        activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
            if (principal.isAuthenticated) {
                rideDao.getPassengerVerificationFlow(principal.id)
            } else {
                val fallbackId = currentCloudUserId()
                if (fallbackId != null) {
                    rideDao.getPassengerVerificationFlow(fallbackId)
                } else {
                    flowOf(null)
                }
            }
        }
            .onEach { verification ->
                if (
                    BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE &&
                    verification?.status == "PENDING"
                ) {
                    val evidence = evaluatePassengerEvidence(verification)
                    if (evidence.isReady) {
                        val now = System.currentTimeMillis()
                        rideDao.updatePassengerVerificationStatus(
                            passengerId = verification.passengerId,
                            status = RideVerificationPolicy.PILOT_APPROVED,
                            approvedAt = now,
                        )
                        android.util.Log.i(
                            "ElysiumRides",
                            "Pending passenger verification upgraded to local pilot access",
                        )
                    } else {
                        android.util.Log.w(
                            "ElysiumRides",
                            "Passenger pilot access withheld: ${evidence.issues.joinToString()}",
                        )
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las fotos de identidad; una o más evidencias no están disponibles.",
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Create the local directory for storing identity verification photos.
     * Returns the base directory path.
     */
    fun getVerificationPhotosDir(context: android.content.Context): java.io.File {
        val dir = java.io.File(context.filesDir, "meet_verifications")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Create a temporary file for a photo capture (camera intent result).
     */
    fun createVerificationPhotoFile(context: android.content.Context, prefix: String): java.io.File {
        val dir = getVerificationPhotosDir(context)
        return java.io.File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    private fun verificationFileEvidence(
        label: String,
        path: String,
    ): VerificationFileEvidence {
        val file = java.io.File(path)
        return VerificationFileEvidence(
            label = label,
            path = path,
            byteCount = if (file.isFile) file.length() else 0L,
        )
    }

    private fun evaluatePassengerEvidence(
        verification: com.elysium369.meet.data.local.entities.PassengerVerificationEntity,
    ) = RideVerificationEvidencePolicy.evaluatePassenger(
        fullName = verification.fullName,
        phone = verification.phone,
        files = listOf(
            verificationFileEvidence("profile", verification.pathProfilePhoto),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
        ),
    )

    private fun evaluateDriverEvidence(
        verification: com.elysium369.meet.data.local.entities.DriverVerificationEntity,
    ) = RideVerificationEvidencePolicy.evaluateDriver(
        fullName = verification.fullName,
        phone = verification.phone,
        email = verification.email,
        dateOfBirth = verification.dateOfBirth,
        vehicleMake = verification.vehicleMake,
        vehicleModel = verification.vehicleModel,
        vehicleYear = verification.vehicleYear,
        vehicleColor = verification.vehicleColor,
        vehiclePlate = verification.vehiclePlate,
        vehicleSeats = verification.vehicleSeats,
        currentYear = Calendar.getInstance().get(Calendar.YEAR),
        files = listOf(
            verificationFileEvidence("license_front", verification.pathLicenciaFront),
            verificationFileEvidence("license_back", verification.pathLicenciaBack),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("id_back", verification.pathCedulaBack),
            verificationFileEvidence("criminal_record", verification.pathHojaDelincuencia),
            verificationFileEvidence("marchamo", verification.pathMarchamo),
            verificationFileEvidence("inspection", verification.pathDekra),
            verificationFileEvidence("insurance", verification.pathSeguro),
            verificationFileEvidence("profile", verification.pathSelfieProfile),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
            verificationFileEvidence("selfie_with_license", verification.pathSelfieWithLicencia),
            verificationFileEvidence("vehicle_front", verification.pathVehicleFront),
            verificationFileEvidence("vehicle_back", verification.pathVehicleBack),
            verificationFileEvidence("vehicle_interior", verification.pathVehicleInterior),
        ),
    )

    /**
     * A driver account is also a passenger account under the same authenticated
     * principal. Driver onboarding already captures every passenger identity
     * artifact, so duplicating registration would create divergent identities.
     */
    private suspend fun ensurePassengerRoleForDriver(
        actorId: String,
    ): com.elysium369.meet.data.local.entities.PassengerVerificationEntity? {
        val driver = rideDao.getDriverVerification(actorId) ?: return null
        val isApprovedDriver = RideVerificationPolicy.grantsAccess(driver.status)
        if (!isApprovedDriver && !evaluateDriverEvidence(driver).isReady) return null
        rideDao.getPassengerVerification(actorId)?.let { existing ->
            if (
                isApprovedDriver &&
                existing.status != driver.status
            ) {
                return existing.copy(
                    status = driver.status,
                    rejectionReason = null,
                    approvedAt = driver.approvedAt ?: driver.updatedAt,
                ).also { rideDao.insertPassengerVerification(it) }
            }
            return existing
        }
        val passenger = com.elysium369.meet.data.local.entities.PassengerVerificationEntity(
            passengerId = actorId,
            fullName = driver.fullName,
            phone = driver.phone,
            pathProfilePhoto = driver.pathSelfieProfile,
            pathCedulaFront = driver.pathCedulaFront,
            pathSelfieWithCedula = driver.pathSelfieWithCedula,
            status = driver.status,
            rejectionReason = driver.rejectionReason,
            createdAt = driver.createdAt,
            approvedAt = driver.approvedAt,
        )
        rideDao.insertPassengerVerification(passenger)
        return passenger
    }

    /**
     * Submit a complete driver verification application with all required
     * document file paths. This is the single entry point for the multi-step
     * onboarding wizard.
     */
    fun submitDriverVerification(
        fullName: String,
        phone: String,
        email: String,
        dateOfBirth: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleYear: Int,
        vehicleColor: String,
        vehiclePlate: String,
        vehicleSeats: Int,
        pathLicenciaFront: String,
        pathLicenciaBack: String,
        pathCedulaFront: String,
        pathCedulaBack: String,
        pathHojaDelincuencia: String,
        pathMarchamo: String,
        pathDekra: String,
        pathSeguro: String,
        pathSelfieProfile: String,
        pathSelfieWithCedula: String,
        pathSelfieWithLicencia: String,
        pathVehicleFront: String,
        pathVehicleBack: String,
        pathVehicleInterior: String
    ) {
        viewModelScope.launch {
            val actorId = currentCloudUserId()
            if (actorId == null) {
                _rideVerificationNotice.emit(
                    "Inicia sesión antes de enviar tu expediente de chofer.",
                )
                return@launch
            }
            val now = System.currentTimeMillis()
            val evidenceFiles = listOf(
                verificationFileEvidence("license_front", pathLicenciaFront),
                verificationFileEvidence("license_back", pathLicenciaBack),
                verificationFileEvidence("id_front", pathCedulaFront),
                verificationFileEvidence("id_back", pathCedulaBack),
                verificationFileEvidence("criminal_record", pathHojaDelincuencia),
                verificationFileEvidence("marchamo", pathMarchamo),
                verificationFileEvidence("inspection", pathDekra),
                verificationFileEvidence("insurance", pathSeguro),
                verificationFileEvidence("profile", pathSelfieProfile),
                verificationFileEvidence("selfie_with_id", pathSelfieWithCedula),
                verificationFileEvidence("selfie_with_license", pathSelfieWithLicencia),
                verificationFileEvidence("vehicle_front", pathVehicleFront),
                verificationFileEvidence("vehicle_back", pathVehicleBack),
                verificationFileEvidence("vehicle_interior", pathVehicleInterior),
            )
            val evidence = RideVerificationEvidencePolicy.evaluateDriver(
                fullName = fullName,
                phone = phone,
                email = email,
                dateOfBirth = dateOfBirth,
                vehicleMake = vehicleMake,
                vehicleModel = vehicleModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                vehicleSeats = vehicleSeats,
                currentYear = Calendar.getInstance().get(Calendar.YEAR),
                files = evidenceFiles,
            )
            val verificationDecision = RideVerificationPolicy.decide(
                localAutoApprovalEnabled = BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE,
                evidenceReady = evidence.isReady,
                nowEpochMs = now,
            )
            if (!evidence.isReady) {
                android.util.Log.w(
                    "ElysiumRides",
                    "Driver verification rejected as incomplete: ${evidence.issues.joinToString()}",
                )
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica los datos y vuelve a capturar cualquier foto faltante.",
                )
                return@launch
            }
            val entity = com.elysium369.meet.data.local.entities.DriverVerificationEntity(
                driverId = actorId,
                fullName = fullName,
                phone = phone,
                email = email,
                dateOfBirth = dateOfBirth,
                vehicleMake = vehicleMake,
                vehicleModel = vehicleModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                vehicleSeats = vehicleSeats,
                pathLicenciaFront = pathLicenciaFront,
                pathLicenciaBack = pathLicenciaBack,
                pathCedulaFront = pathCedulaFront,
                pathCedulaBack = pathCedulaBack,
                pathHojaDelincuencia = pathHojaDelincuencia,
                pathMarchamo = pathMarchamo,
                pathDekra = pathDekra,
                pathSeguro = pathSeguro,
                pathSelfieProfile = pathSelfieProfile,
                pathSelfieWithCedula = pathSelfieWithCedula,
                pathSelfieWithLicencia = pathSelfieWithLicencia,
                pathVehicleFront = pathVehicleFront,
                pathVehicleBack = pathVehicleBack,
                pathVehicleInterior = pathVehicleInterior,
                status = verificationDecision.status,
                createdAt = now,
                updatedAt = now,
                approvedAt = verificationDecision.approvedAtEpochMs,
            )
            rideDao.insertDriverVerification(entity)
            ensurePassengerRoleForDriver(actorId)?.let { passenger ->
                submitPassengerTrustApplication(passenger).onFailure {
                    Log.w("ElysiumTrustCenter", "Linked passenger role submission unavailable", it)
                    _rideVerificationNotice.emit(
                        "El acceso de pasajero quedó ligado localmente; la nube lo sincronizará al recuperar conexión.",
                    )
                }
            }
            enqueueDriverPilotEnrollment(entity, evidenceFiles)
            _rideVerificationNotice.emit(
                "Expediente guardado y enviado a revisión. El modo chofer seguirá bloqueado hasta la aprobación remota.",
            )
            android.util.Log.i(
                "ElysiumRides",
                "Driver verification submitted; status=${verificationDecision.status}",
            )
        }
    }

    private suspend fun enqueueDriverPilotEnrollment(
        verification: com.elysium369.meet.data.local.entities.DriverVerificationEntity,
        evidenceFiles: List<VerificationFileEvidence> = listOf(
            verificationFileEvidence("license_front", verification.pathLicenciaFront),
            verificationFileEvidence("license_back", verification.pathLicenciaBack),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("id_back", verification.pathCedulaBack),
            verificationFileEvidence("criminal_record", verification.pathHojaDelincuencia),
            verificationFileEvidence("marchamo", verification.pathMarchamo),
            verificationFileEvidence("inspection", verification.pathDekra),
            verificationFileEvidence("insurance", verification.pathSeguro),
            verificationFileEvidence("profile", verification.pathSelfieProfile),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
            verificationFileEvidence("selfie_with_license", verification.pathSelfieWithLicencia),
            verificationFileEvidence("vehicle_front", verification.pathVehicleFront),
            verificationFileEvidence("vehicle_back", verification.pathVehicleBack),
            verificationFileEvidence("vehicle_interior", verification.pathVehicleInterior),
        ),
    ) {
        val evidenceManifest = buildDriverEvidenceManifestSha256(
            verification = verification,
            evidenceFiles = evidenceFiles,
        )
        val countryCode = _currentGpsLocation.value?.countryCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
            ?: "CR"
        val currency = if (countryCode == "CR") "CRC" else "USD"
        RideDriverEnrollmentWorker.enqueue(
            context = context,
            enrollment = RideDriverPilotEnrollment(
                driverDisplayName = verification.fullName.trim(),
                countryCode = countryCode,
                currency = currency,
                vehicleReference = sha256Hex(
                    listOf(
                        "vehicle",
                        verification.vehicleMake.trim().uppercase(),
                        verification.vehicleModel.trim().uppercase(),
                        verification.vehicleYear.toString(),
                        verification.vehiclePlate.trim().uppercase(),
                    ).joinToString("|").toByteArray(),
                ),
                vehicleDisplayName = listOf(
                    verification.vehicleMake.trim(),
                    verification.vehicleModel.trim(),
                    verification.vehicleYear.toString(),
                    verification.vehicleColor.trim(),
                ).joinToString(" "),
                seats = verification.vehicleSeats,
                evidenceManifestSha256 = evidenceManifest,
                evidenceFiles = evidenceFiles.map {
                    TrustEvidenceFile(kind = it.label, localPath = it.path)
                },
                phone = verification.phone,
                email = verification.email,
                vehicleMake = verification.vehicleMake,
                vehicleModel = verification.vehicleModel,
                vehicleYear = verification.vehicleYear,
                vehicleColor = verification.vehicleColor,
                vehiclePlate = verification.vehiclePlate,
            ),
        )
    }

    private suspend fun buildDriverEvidenceManifestSha256(
        verification: com.elysium369.meet.data.local.entities.DriverVerificationEntity,
        evidenceFiles: List<VerificationFileEvidence>,
    ): String = withContext(Dispatchers.IO) {
        val manifestDigest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            manifestDigest.update(value.toByteArray(Charsets.UTF_8))
            manifestDigest.update(0)
        }
        append("meet-rides-driver-evidence-v1")
        append(verification.fullName.trim())
        append(verification.dateOfBirth.trim())
        append(verification.vehicleMake.trim())
        append(verification.vehicleModel.trim())
        append(verification.vehicleYear.toString())
        append(verification.vehicleColor.trim())
        append(verification.vehiclePlate.trim())
        append(verification.vehicleSeats.toString())
        evidenceFiles.sortedBy(VerificationFileEvidence::label).forEach { evidence ->
            append(evidence.label)
            append(evidence.byteCount.toString())
            val file = java.io.File(evidence.path)
            val contentHash = if (file.isFile) {
                file.inputStream().buffered().use { input ->
                    val fileDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fileDigest.update(buffer, 0, read)
                    }
                    fileDigest.digest().toHex()
                }
            } else {
                "missing"
            }
            append(contentHash)
        }
        manifestDigest.digest().toHex()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun merkleRootSha256(hashes: List<String>): String =
        com.elysium369.meet.domain.diagnostics.DiagnosticEvidenceIntegrity.merkleRootSha256(hashes)

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    /**
     * Submit a passenger identity verification (lighter than driver).
     */
    fun submitPassengerVerification(
        fullName: String,
        phone: String,
        pathProfilePhoto: String,
        pathCedulaFront: String,
        pathSelfieWithCedula: String
    ) {
        viewModelScope.launch {
            val actorId = currentCloudUserId()
            if (actorId == null) {
                _rideVerificationNotice.emit(
                    "Inicia sesión antes de enviar tu verificación de pasajero.",
                )
                return@launch
            }
            val now = System.currentTimeMillis()
            val evidence = RideVerificationEvidencePolicy.evaluatePassenger(
                fullName = fullName,
                phone = phone,
                files = listOf(
                    verificationFileEvidence("profile", pathProfilePhoto),
                    verificationFileEvidence("id_front", pathCedulaFront),
                    verificationFileEvidence("selfie_with_id", pathSelfieWithCedula),
                ),
            )
            val verificationDecision = RideVerificationPolicy.decide(
                localAutoApprovalEnabled = BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE,
                evidenceReady = evidence.isReady,
                nowEpochMs = now,
            )
            if (!evidence.isReady) {
                android.util.Log.w(
                    "ElysiumRides",
                    "Passenger verification rejected as incomplete: ${evidence.issues.joinToString()}",
                )
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica tus datos y vuelve a capturar las tres fotos.",
                )
                return@launch
            }
            val entity = com.elysium369.meet.data.local.entities.PassengerVerificationEntity(
                passengerId = actorId,
                fullName = fullName,
                phone = phone,
                pathProfilePhoto = pathProfilePhoto,
                pathCedulaFront = pathCedulaFront,
                pathSelfieWithCedula = pathSelfieWithCedula,
                status = verificationDecision.status,
                createdAt = now,
                approvedAt = verificationDecision.approvedAtEpochMs,
            )
            rideDao.insertPassengerVerification(entity)
            val cloudUserId = currentCloudUserId()
            if (cloudUserId != null) {
                submitPassengerTrustApplication(entity).onFailure {
                    Log.w("ElysiumTrustCenter", "Passenger review submission unavailable", it)
                    _rideVerificationNotice.emit(
                        "Registro local guardado; la revisión remota está pendiente de sincronización.",
                    )
                }
            }
            android.util.Log.i(
                "ElysiumRides",
                "Passenger verification submitted; status=${verificationDecision.status}",
            )
        }
    }

    private suspend fun submitPassengerTrustApplication(
        verification: com.elysium369.meet.data.local.entities.PassengerVerificationEntity,
    ): Result<Unit> {
        val evidenceFiles = listOf(
            verificationFileEvidence("profile", verification.pathProfilePhoto),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
        )
        val manifest = buildPassengerEvidenceManifestSha256(
            fullName = verification.fullName,
            phone = verification.phone,
            evidenceFiles = evidenceFiles,
        )
        return runCatching {
            PlatformTrustCenterGateway.submit(
                ServiceVerificationSubmission(
                    serviceType = "PASSENGER",
                    profileReference = "primary",
                    displayName = verification.fullName,
                    phone = verification.phone,
                    evidenceManifestSha256 = manifest,
                    evidenceFiles = evidenceFiles.map {
                        TrustEvidenceFile(kind = it.label, localPath = it.path)
                    },
                ),
            )
        }
    }

    private suspend fun buildPassengerEvidenceManifestSha256(
        fullName: String,
        phone: String,
        evidenceFiles: List<VerificationFileEvidence>,
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        append("meet-rides-passenger-evidence-v1")
        append(fullName.trim())
        append(phone.trim())
        evidenceFiles.sortedBy(VerificationFileEvidence::label).forEach { evidence ->
            append(evidence.label)
            append(evidence.byteCount.toString())
            val file = java.io.File(evidence.path)
            val contentHash = if (file.isFile) {
                file.inputStream().buffered().use { input ->
                    val fileDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fileDigest.update(buffer, 0, read)
                    }
                    fileDigest.digest().toHex()
                }
            } else {
                "missing"
            }
            append(contentHash)
        }
        digest.digest().toHex()
    }

    /**
     * Delete a driver verification (allows re-submission).
     */
    fun deleteDriverVerification() {
        viewModelScope.launch {
            currentCloudUserId()?.let { rideDao.deleteDriverVerification(it) }
        }
    }

    /**
     * Delete a passenger verification (allows re-submission).
     */
    fun deletePassengerVerification() {
        viewModelScope.launch {
            currentCloudUserId()?.let { rideDao.deletePassengerVerification(it) }
        }
    }

    /**
     * Check if the current device user is an approved driver.
     */
    fun isApprovedDriver(): Boolean {
        return RideVerificationPolicy.grantsAccess(driverVerification.value?.status)
    }

    /**
     * Check if the current device user is an approved passenger.
     */
    fun isApprovedPassenger(): Boolean {
        return RideVerificationPolicy.grantsAccess(passengerVerification.value?.status)
    }

    override fun onCleared() {
        stopRideDriverPresenceHeartbeat()
        super.onCleared()
        phoneSpeedTracker.stop()
        voiceCommandManager.stopCopilot()
        localShellManager.stopShell(stopControlServer = true)
        shellScope.cancel()
    }
}

data class DataLogEntry(
    val timestamp: Long,
    val values: Map<String, Float>
)

@kotlinx.serialization.Serializable
data class DashcamTelemetryFrame(
    val timestampMs: Long,
    val rpm: Float,
    val speedKph: Float,
    val gForce: Float,
    val throttle: Float,
    val load: Float
)
