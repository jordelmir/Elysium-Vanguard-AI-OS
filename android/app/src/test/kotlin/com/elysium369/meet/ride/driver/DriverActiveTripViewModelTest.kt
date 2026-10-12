package com.elysium369.meet.ride.driver

import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.local.entities.DriverVerificationEntity
import com.elysium369.meet.data.local.entities.PassengerVerificationEntity
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.application.RideCommandBus
import com.elysium369.meet.ride.application.RideCommandEnqueueResult
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.RideRoutingProvider
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverActiveTripViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mainDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private val fakeDao = TestFakeRideDao()
    private val fakeBus = TestFakeRideCommandBus()
    private val fakeRouting = TestFakeRoutingProvider()
    private val fakeClock = TestMonotonicClock()

    private lateinit var viewModel: DriverActiveTripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        viewModel = DriverActiveTripViewModel(
            rideDao = fakeDao,
            commandBus = fakeBus,
            routingProvider = fakeRouting,
            context = null,
        ).apply {
            clock = fakeClock
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize updates state from initial projection and observes updates`() = runTest(testDispatcher) {
        val initialEntity = createTestRideEntity(
            requestId = "ride-101",
            status = "DRIVER_EN_ROUTE",
            serverState = "DRIVER_EN_ROUTE",
            serverVersion = 4L,
        )

        viewModel.initialize("ride-101", initialEntity)
        advanceUntilIdle()

        assertEquals("ride-101", viewModel.state.value.rideId)
        assertEquals(DriverTripPhase.ToPickup, viewModel.state.value.phase)
        assertEquals(4L, viewModel.state.value.serverVersion)

        // Remote/Room update emitted
        fakeDao.requestFlow.value = initialEntity.copy(
            serverState = "ARRIVED",
            serverVersion = 5L,
            driverArrivedAt = 1726500500000L,
        )
        advanceUntilIdle()

        assertEquals(DriverTripPhase.AtPickup, viewModel.state.value.phase)
        assertEquals(5L, viewModel.state.value.serverVersion)
        assertEquals(1726500500000L, viewModel.state.value.driverArrivedAtEpochMs)
    }

    @Test
    fun `toggle future offers separates presence from active trip state`() = runTest(testDispatcher) {
        val initialEntity = createTestRideEntity(
            requestId = "ride-102",
            status = "IN_PROGRESS",
            serverState = "IN_PROGRESS",
            serverVersion = 10L,
        )
        viewModel.initialize("ride-102", initialEntity)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.acceptingFutureOffers)

        viewModel.dispatch(DriverTripIntent.ToggleAcceptingFutureOffers)
        assertFalse(viewModel.state.value.acceptingFutureOffers)
        assertEquals(DriverTripPhase.InProgress, viewModel.state.value.phase)

        viewModel.dispatch(DriverTripIntent.ToggleAcceptingFutureOffers)
        assertTrue(viewModel.state.value.acceptingFutureOffers)
        assertEquals(DriverTripPhase.InProgress, viewModel.state.value.phase)
    }

    @Test
    fun `confirm arrival rejected by preflight when far from pickup`() = runTest(testDispatcher) {
        val initialEntity = createTestRideEntity(
            requestId = "ride-103",
            status = "DRIVER_EN_ROUTE",
            serverState = "DRIVER_EN_ROUTE",
            serverVersion = 2L,
            pickupLat = 9.9300,
            pickupLng = -84.0800,
        )
        viewModel.initialize("ride-103", initialEntity)
        advanceUntilIdle()

        // Driver is 1 km away
        val farLocation = DriverLocationSample(
            latitude = 9.9400,
            longitude = -84.0800,
            accuracyMeters = 10.0,
            capturedAt = Instant.ofEpochMilli(fakeClock.epochMs),
            capturedAtElapsedRealtimeNanos = fakeClock.nanos,
        )
        viewModel.dispatch(DriverTripIntent.UpdateDriverLocation(farLocation))

        viewModel.dispatch(DriverTripIntent.ConfirmArrival)
        advanceUntilIdle()

        // No command was enqueued because preflight failed
        assertTrue(fakeBus.enqueuedCommands.isEmpty())
        assertNotNull(viewModel.state.value.userMessage)
        assertTrue(viewModel.state.value.userMessage!!.contains("lejos"))
    }

    @Test
    fun `confirm arrival enqueues command when preflight passes`() = runTest(testDispatcher) {
        val initialEntity = createTestRideEntity(
            requestId = "ride-104",
            status = "DRIVER_EN_ROUTE",
            serverState = "DRIVER_EN_ROUTE",
            serverVersion = 3L,
            pickupLat = 9.9300,
            pickupLng = -84.0800,
        )
        viewModel.initialize("ride-104", initialEntity)
        advanceUntilIdle()

        // Driver is 20m from pickup with 5m accuracy and fresh monotonic timestamp
        val nearLocation = DriverLocationSample(
            latitude = 9.9301,
            longitude = -84.0801,
            accuracyMeters = 5.0,
            capturedAt = Instant.ofEpochMilli(fakeClock.epochMs),
            capturedAtElapsedRealtimeNanos = fakeClock.nanos,
        )
        viewModel.dispatch(DriverTripIntent.UpdateDriverLocation(nearLocation))

        viewModel.dispatch(DriverTripIntent.ConfirmArrival)
        advanceUntilIdle()

        assertEquals(1, fakeBus.enqueuedCommands.size)
        val cmd = fakeBus.enqueuedCommands.first()
        assertEquals("ride-104", cmd.rideId)
        assertEquals(3L, cmd.expectedVersion)
        assertEquals(RideCommandType.DRIVER_ARRIVED, cmd.type)
        assertEquals(RideCommandType.DRIVER_ARRIVED, viewModel.state.value.pendingCommand)
    }

    @Test
    fun `start trip requires passenger onboard state`() = runTest(testDispatcher) {
        val initialEntity = createTestRideEntity(
            requestId = "ride-105",
            status = "ARRIVED",
            serverState = "ARRIVED",
            serverVersion = 4L,
        )
        viewModel.initialize("ride-105", initialEntity)
        advanceUntilIdle()

        // Starting trip while only ARRIVED is prohibited
        viewModel.dispatch(DriverTripIntent.StartTrip)
        advanceUntilIdle()
        assertTrue(fakeBus.enqueuedCommands.isEmpty())

        // Update state to PASSENGER_ONBOARD
        fakeDao.requestFlow.value = initialEntity.copy(
            status = "PASSENGER_ONBOARD",
            serverState = "PASSENGER_ONBOARD",
            serverVersion = 5L,
        )
        advanceUntilIdle()

        viewModel.dispatch(DriverTripIntent.StartTrip)
        advanceUntilIdle()

        assertEquals(1, fakeBus.enqueuedCommands.size)
        val cmd = fakeBus.enqueuedCommands.first()
        assertEquals(RideCommandType.START, cmd.type)
        assertEquals(5L, cmd.expectedVersion)
    }

    private fun createTestRideEntity(
        requestId: String,
        status: String,
        serverState: String,
        serverVersion: Long,
        pickupLat: Double = 9.9300,
        pickupLng: Double = -84.0800,
        destLat: Double = 9.9500,
        destLng: Double = -84.1000,
    ) = RideRequestEntity(
        requestId = requestId,
        passengerId = "pass-1",
        passengerName = "Ana Lorena",
        passengerPhone = "50688888888",
        pickupLatitude = pickupLat,
        pickupLongitude = pickupLng,
        pickupAddress = "Radial San Antonio",
        pickupAccuracy = 5f,
        destLatitude = destLat,
        destLongitude = destLng,
        destAddress = "Escazú Centro",
        priceOffer = 2500.0,
        priceOfferMinor = 250000L,
        currency = "CRC",
        estimatedDistanceKm = 4.5,
        estimatedDurationMin = 10,
        status = status,
        serverState = serverState,
        serverVersion = serverVersion,
        assignedDriverId = "driver-me",
        createdAt = 1726500000000L,
    )
}

private class TestMonotonicClock : MonotonicClock {
    var nanos: Long = 10_000_000_000L
    var epochMs: Long = 1726500000000L
    override fun nowNanos(): Long = nanos
    override fun nowEpochMs(): Long = epochMs
}

private class TestFakeRideCommandBus : RideCommandBus {
    val enqueuedCommands = mutableListOf<RideQueuedCommand>()
    override suspend fun enqueue(command: RideQueuedCommand): RideCommandEnqueueResult {
        enqueuedCommands.add(command)
        return RideCommandEnqueueResult.Enqueued
    }
}

private class TestFakeRoutingProvider : RideRoutingProvider {
    override suspend fun route(waypoints: List<RideGeoPoint>): RideRoadRoute {
        val points = if (waypoints.size >= 2) waypoints else listOf(
            RideGeoPoint(9.9300, -84.0800, null, 0L),
            RideGeoPoint(9.9500, -84.1000, null, 0L),
        )
        return RideRoadRoute(
            geometry = points,
            distanceMeters = 2400.0,
            durationSeconds = 480.0,
            attribution = "Test Routing",
        )
    }
}

private class TestFakeRideDao : RideDao {
    val requestFlow = MutableStateFlow<RideRequestEntity?>(null)

    override fun observeRequest(requestId: String): Flow<RideRequestEntity?> = requestFlow

    override suspend fun getRequestById(requestId: String): RideRequestEntity? = requestFlow.value

    override suspend fun getActiveRideSelection(ownerPrincipalId: String): ActiveRideSelectionEntity? = null
    override suspend fun upsertActiveRideSelection(selection: ActiveRideSelectionEntity) {}
    override suspend fun clearActiveRideSelection(ownerPrincipalId: String) {}
    override suspend fun clearActiveRideSelectionsForRide(requestId: String) {}
    override fun getAllRequestsFlow(): Flow<List<RideRequestEntity>> = throw NotImplementedError()
    override fun getLostItemReportsFlow(): Flow<List<RideChatMessageEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun getOpenRequestsFlow(): Flow<List<RideRequestEntity>> = throw NotImplementedError()
    override fun getRequestsByPassenger(passengerId: String): Flow<List<RideRequestEntity>> = throw NotImplementedError()
    override fun observeAuthoritativeActiveRidesForDriver(driverId: String): Flow<List<RideRequestEntity>> = throw NotImplementedError()
    override suspend fun recordConfirmedTip(requestId: String, passengerId: String, amount: Long): Int = 0
    override suspend fun insertRequest(request: RideRequestEntity) {}
    override suspend fun markCommandPending(requestId: String): Int = 0
    override suspend fun applyServerProjection(
        requestId: String,
        legacyStatus: String,
        serverState: String,
        serverVersion: Long,
        finalPriceMinor: Long?,
        syncedAt: Long,
        correlationId: String?,
    ): Int = 0
    override suspend fun reconcileServerSnapshot(
        requestId: String,
        legacyStatus: String,
        serverState: String,
        serverVersion: Long,
        offeredFareMinor: Long,
        finalFareMinor: Long?,
        assignedDriverId: String?,
        assignedVehicleId: String?,
        syncState: String,
        syncedAt: Long,
        correlationId: String?,
    ): Int = 0

    override suspend fun insertRemoteProjection(remote: RideRequestEntity) {}
    override suspend fun markServerConflict(requestId: String, currentServerVersion: Long?, observedAt: Long): Int = 0
    override suspend fun storeAuthoritativeBoardingPin(requestId: String, pin: String, expiresAt: Long?): Int = 0
    override suspend fun clearAuthoritativeBoardingPin(requestId: String): Int = 0
    override suspend fun transitionRequestStatusAsDriver(requestId: String, driverId: String, expectedStatus: String, newStatus: String, completedAt: Long?): Int = 0
    override suspend fun cancelActiveRequest(requestId: String, actorId: String, actorRole: String, cancelledAt: Long): Int = 0
    override suspend fun markRequestCancelledLocally(requestId: String, cancelledAt: Long): Int = 0
    override suspend fun cancelUnpublishedRidesForPassenger(passengerId: String, cancelledAt: Long): Int = 0
    override suspend fun claimOpenRequestWithOffer(requestId: String, offerId: String, driverId: String, driverName: String, driverPhone: String, vehicle: String, price: Double): Int = 0
    override suspend fun claimOpenRequest(requestId: String, driverId: String, driverName: String, driverPhone: String, vehicle: String): Int = 0
    override suspend fun updatePassengerRating(requestId: String, rating: Double) {}
    override suspend fun updateDriverRating(requestId: String, rating: Double) {}
    override suspend fun updateRideStatus(requestId: String, status: String) {}
    override suspend fun markRideCompleted(requestId: String, completedAt: Long) {}
    override suspend fun deleteRequest(requestId: String) {}
    override fun getOffersForRequest(requestId: String): Flow<List<RideOfferEntity>> = emptyFlow()
    override suspend fun getOffersForRequestSync(requestId: String): List<RideOfferEntity> = emptyList()
    override suspend fun getOfferById(offerId: String): RideOfferEntity? = null
    override suspend fun insertOffer(offer: RideOfferEntity) {}
    override suspend fun updateOfferStatus(offerId: String, status: String) {}
    override suspend fun rejectOtherOffers(requestId: String, acceptedOfferId: String) {}
    override fun getChatMessagesFlow(rideRequestId: String): Flow<List<RideChatMessageEntity>> = emptyFlow()
    override suspend fun insertChatMessage(message: RideChatMessageEntity) {}
    override suspend fun getPendingChatMessages(limit: Int): List<RideChatMessageEntity> = emptyList()
    override suspend fun updateChatMessageSyncState(messageId: String, syncState: String, remoteMediaPath: String?) {}
    override suspend fun markMessagesAsRead(rideRequestId: String, userId: String) {}
    override fun getUnreadCountFlow(rideRequestId: String, userId: String): Flow<Int> = emptyFlow()
    override fun getDriverVerificationFlow(driverId: String): Flow<DriverVerificationEntity?> = emptyFlow()
    override suspend fun getDriverVerification(driverId: String): DriverVerificationEntity? = null
    override suspend fun insertDriverVerification(entity: DriverVerificationEntity) {}
    override suspend fun updateDriverVerificationStatus(driverId: String, status: String, approvedAt: Long?, updatedAt: Long) {}
    override suspend fun rejectDriverVerification(driverId: String, reason: String, updatedAt: Long) {}
    override suspend fun deleteDriverVerification(driverId: String) {}
    override fun getPassengerVerificationFlow(passengerId: String): Flow<PassengerVerificationEntity?> = emptyFlow()
    override suspend fun getPassengerVerification(passengerId: String): PassengerVerificationEntity? = null
    override suspend fun insertPassengerVerification(entity: PassengerVerificationEntity) {}
    override suspend fun updatePassengerVerificationStatus(passengerId: String, status: String, approvedAt: Long?) {}
    override suspend fun deletePassengerVerification(passengerId: String) {}
}
