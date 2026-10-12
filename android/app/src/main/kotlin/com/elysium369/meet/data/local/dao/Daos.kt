package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllVehiclesForUser(userId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND syncedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getPendingVehiclesForUser(userId: String): List<VehicleEntity>

    @Query("UPDATE vehicles SET syncedAt = :syncedAt WHERE id = :id AND userId = :userId")
    suspend fun markVehicleSynced(userId: String, id: String, syncedAt: Long)

    @Query("SELECT * FROM vehicles WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getVehicleByIdForUser(userId: String, id: String): VehicleEntity?

    // Detail screens already receive an opaque vehicle ID from the owner-scoped
    // Garage list. Identity discovery itself must use the scoped queries above.
    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND UPPER(vin) = UPPER(:vin) LIMIT 1")
    suspend fun getVehicleByVinForUser(userId: String, vin: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}

@Dao
interface DiagnosticSessionDao {
    @Query("SELECT * FROM diagnostic_sessions WHERE vehicleId = :vehicleId ORDER BY startedAt DESC")
    fun getSessionsForVehicle(vehicleId: String): Flow<List<DiagnosticSessionEntity>>

    @Query("SELECT * FROM diagnostic_sessions WHERE synced = 0 AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun getPendingSync(ownerPrincipalId: String): List<DiagnosticSessionEntity>

    @Query("UPDATE diagnostic_sessions SET synced = 1 WHERE id IN (:ids) AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun markAsSynced(ids: List<String>, ownerPrincipalId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DiagnosticSessionEntity)
}

@Dao
interface DtcDao {
    @Query("SELECT * FROM dtc_events WHERE sessionId = :sessionId")
    fun getDtcsForSession(sessionId: String): Flow<List<DtcEventEntity>>

    @Query("SELECT * FROM dtc_events WHERE vehicleId = :vehicleId AND resolvedAt IS NULL")
    fun getUnresolvedDtcsForVehicle(vehicleId: String): Flow<List<DtcEventEntity>>

    @Query("SELECT * FROM dtc_events WHERE vehicleId = :vehicleId AND resolvedAt IS NOT NULL ORDER BY resolvedAt DESC")
    fun getVerifiedResolvedDtcsForVehicle(vehicleId: String): Flow<List<DtcEventEntity>>

    @Query("SELECT * FROM dtc_events WHERE vehicleId = :vehicleId AND ownerPrincipalId = :ownerPrincipalId AND resolvedAt IS NULL")
    suspend fun getUnresolvedDtcsList(vehicleId: String, ownerPrincipalId: String): List<DtcEventEntity>
    
    @Query(
        """SELECT * FROM dtc_events
           WHERE vehicleId = :vehicleId
             AND ownerPrincipalId = :ownerPrincipalId
             AND diagnosticNamespace = :namespace
             AND moduleIdentity = :moduleIdentity
             AND rawDtcIdentity = :rawDtcIdentity
             AND COALESCE(failureType, -1) = :failureType
             AND resolvedAt IS NULL
           LIMIT 1"""
    )
    suspend fun getUnresolvedFinding(
        vehicleId: String,
        ownerPrincipalId: String,
        namespace: String,
        moduleIdentity: String,
        rawDtcIdentity: String,
        failureType: Int,
    ): DtcEventEntity?

    @Query("SELECT * FROM dtc_events WHERE id = :findingId AND ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    suspend fun getFindingById(findingId: String, ownerPrincipalId: String): DtcEventEntity?

    @Query("UPDATE dtc_events SET resolvedAt = :resolvedAt, observationState = 'VERIFIED_ABSENT', synced = 0 WHERE id IN (:findingIds) AND ownerPrincipalId = :ownerPrincipalId AND resolvedAt IS NULL")
    suspend fun resolveVerifiedFindings(
        findingIds: List<String>,
        ownerPrincipalId: String,
        resolvedAt: Long,
    )

    @Query("SELECT * FROM dtc_events WHERE synced = 0 AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun getPendingSyncDtcs(ownerPrincipalId: String): List<DtcEventEntity>

    @Query("UPDATE dtc_events SET synced = 1 WHERE id IN (:ids) AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun markDtcsAsSynced(ids: List<String>, ownerPrincipalId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtc(dtc: DtcEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDtcs(dtcs: List<DtcEventEntity>)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND ownerPrincipalId = :ownerPrincipalId ORDER BY startedAt DESC")
    fun getTripsForVehicle(vehicleId: String, ownerPrincipalId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE synced = 0 AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun getPendingSync(ownerPrincipalId: String): List<TripEntity>

    @Query("UPDATE trips SET synced = 1 WHERE id IN (:ids) AND ownerPrincipalId = :ownerPrincipalId")
    suspend fun markAsSynced(ids: List<String>, ownerPrincipalId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE startedAt < :timestamp")
    suspend fun deleteTripsOlderThan(timestamp: Long)
}

@Dao
interface AdapterProfileDao {
    @Query("SELECT * FROM adapter_profiles WHERE deviceAddress = :address")
    suspend fun getProfile(address: String): AdapterProfileEntity?

    @Query("SELECT * FROM adapter_profiles ORDER BY lastUsedAt DESC")
    suspend fun getAllProfiles(): List<AdapterProfileEntity>

    @Query("SELECT * FROM adapter_profiles WHERE successfulConnections > 0 ORDER BY avgLatencyMs ASC LIMIT 1")
    suspend fun getFastestAdapter(): AdapterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: AdapterProfileEntity)

    @Query("DELETE FROM adapter_profiles WHERE deviceAddress = :address")
    suspend fun deleteProfile(address: String)
}

@Dao
interface DtcDefinitionDao {
    @Query("SELECT * FROM dtc_definitions WHERE code = :code AND (manufacturer = :manufacturer OR manufacturer = 'GENERIC') ORDER BY CASE WHEN manufacturer = :manufacturer THEN 0 ELSE 1 END LIMIT 1")
    suspend fun getDefinitionForCode(code: String, manufacturer: String): DtcDefinitionEntity?

    @Query(
        """SELECT * FROM dtc_definitions
           WHERE code = :code
             AND diagnosticNamespace = :namespace
             AND (manufacturer = :manufacturer OR manufacturer = 'GENERIC')
             AND (rawDtcIdentity = :rawDtcIdentity OR rawDtcIdentity = '')
             AND (failureType = :failureType OR failureType IS NULL)
           ORDER BY
             CASE WHEN manufacturer = :manufacturer THEN 0 ELSE 1 END,
             CASE WHEN rawDtcIdentity = :rawDtcIdentity THEN 0 ELSE 1 END,
             CASE WHEN failureType = :failureType THEN 0 ELSE 1 END,
             CASE verificationStatus WHEN 'VERIFIED' THEN 0 WHEN 'REVIEWED' THEN 1 ELSE 2 END
           LIMIT 1"""
    )
    suspend fun getDefinitionForFinding(
        code: String,
        manufacturer: String,
        namespace: String,
        rawDtcIdentity: String,
        failureType: Int?,
    ): DtcDefinitionEntity?

    @Query("SELECT * FROM dtc_definitions WHERE code = :code")
    suspend fun getDefinitions(code: String): List<DtcDefinitionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinitions(definitions: List<DtcDefinitionEntity>)
    
    @Query("SELECT COUNT(*) FROM dtc_definitions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM dtc_definitions WHERE code LIKE '%' || :query || '%'")
    suspend fun searchDefinitions(query: String): List<DtcDefinitionEntity>
}

@Dao
interface MaintenanceAlertDao {
    @Query("SELECT * FROM maintenance_alerts WHERE vehicleId = :vehicleId")
    fun getAlertsForVehicle(vehicleId: String): Flow<List<MaintenanceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: MaintenanceAlertEntity)
}

@Dao
interface AiConsultDao {
    @Query("SELECT * FROM ai_consults WHERE sessionId = :sessionId")
    fun getConsultsForSession(sessionId: String): Flow<List<AiConsultEntity>>

    @Query("SELECT * FROM ai_consults WHERE dtcCodes = :dtcCodes ORDER BY createdAt DESC LIMIT 1")
    suspend fun getCachedConsult(dtcCodes: String): AiConsultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsult(consult: AiConsultEntity)

    @Query("DELETE FROM ai_consults WHERE createdAt < :timestamp")
    suspend fun pruneOldConsults(timestamp: Long)
}

@Dao
interface MaintenanceLogDao {
    @Query("SELECT * FROM maintenance_logs WHERE vehicleId = :vehicleId ORDER BY datePerformed DESC")
    fun getLogsForVehicle(vehicleId: String): Flow<List<MaintenanceLogEntity>>

    @Query("SELECT * FROM maintenance_logs WHERE vehicleId = :vehicleId AND category = :category ORDER BY datePerformed DESC")
    fun getLogsByCategory(vehicleId: String, category: String): Flow<List<MaintenanceLogEntity>>

    @Query("SELECT * FROM maintenance_logs WHERE vehicleId = :vehicleId AND category = :category ORDER BY datePerformed DESC LIMIT 1")
    suspend fun getLastService(vehicleId: String, category: String): MaintenanceLogEntity?

    @Query("SELECT * FROM maintenance_logs WHERE vehicleId = :vehicleId AND nextDueKm <= :currentKm ORDER BY nextDueKm ASC")
    fun getOverdueServices(vehicleId: String, currentKm: Long): Flow<List<MaintenanceLogEntity>>

    @Query("SELECT * FROM maintenance_logs WHERE vehicleId = :vehicleId AND nextDueKm BETWEEN :currentKm AND :maxKm ORDER BY nextDueKm ASC")
    fun getUpcomingServices(vehicleId: String, currentKm: Long, maxKm: Long): Flow<List<MaintenanceLogEntity>>

    @Query("SELECT SUM(cost) FROM maintenance_logs WHERE vehicleId = :vehicleId")
    suspend fun getTotalCost(vehicleId: String): Float?

    @Query("SELECT SUM(cost) FROM maintenance_logs WHERE vehicleId = :vehicleId AND datePerformed BETWEEN :fromDate AND :toDate")
    suspend fun getCostInPeriod(vehicleId: String, fromDate: Long, toDate: Long): Float?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MaintenanceLogEntity)

    @Delete
    suspend fun deleteLog(log: MaintenanceLogEntity)
}

@Dao
interface RepairHistoryDao {
    @Query("SELECT * FROM repair_history WHERE vehicleId = :vehicleId ORDER BY datePerformed DESC")
    fun getRepairsForVehicle(vehicleId: String): Flow<List<RepairHistoryEntity>>

    @Query("SELECT * FROM repair_history WHERE vehicleId = :vehicleId AND partCategory = :category ORDER BY datePerformed DESC")
    fun getRepairsByCategory(vehicleId: String, category: String): Flow<List<RepairHistoryEntity>>

    @Query("SELECT * FROM repair_history WHERE vehicleId = :vehicleId AND isPeriodic = 1 AND nextReplacementKm IS NOT NULL AND nextReplacementKm <= :currentKm")
    fun getOverdueReplacements(vehicleId: String, currentKm: Long): Flow<List<RepairHistoryEntity>>

    @Query("SELECT SUM(totalCost) FROM repair_history WHERE vehicleId = :vehicleId")
    suspend fun getTotalRepairCost(vehicleId: String): Float?

    @Query("SELECT SUM(totalCost) FROM repair_history WHERE vehicleId = :vehicleId AND datePerformed BETWEEN :fromDate AND :toDate")
    suspend fun getRepairCostInPeriod(vehicleId: String, fromDate: Long, toDate: Long): Float?

    @Query("SELECT * FROM repair_history WHERE vehicleId = :vehicleId AND relatedDtc = :dtcCode")
    fun getRepairsForDtc(vehicleId: String, dtcCode: String): Flow<List<RepairHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepair(repair: RepairHistoryEntity)

    @Delete
    suspend fun deleteRepair(repair: RepairHistoryEntity)
}

@Dao
interface FleetDao {
    @Query("SELECT * FROM business_profiles WHERE ownerUserId = :ownerUserId")
    fun getBusinessProfilesForOwner(ownerUserId: String): kotlinx.coroutines.flow.Flow<List<BusinessProfileEntity>>

    @Query("SELECT * FROM business_profiles WHERE id = :businessId")
    suspend fun getBusinessProfile(businessId: String): BusinessProfileEntity?

    @Query("SELECT * FROM fleets WHERE businessId = :businessId OR businessId = 'mesh_p2p' OR :businessId = 'mesh_p2p' ORDER BY createdAt DESC")
    fun getFleetsForBusiness(businessId: String): kotlinx.coroutines.flow.Flow<List<FleetEntity>>

    @Query("SELECT * FROM fleet_members WHERE businessId = :businessId")
    fun getMembersForBusiness(businessId: String): kotlinx.coroutines.flow.Flow<List<FleetMemberEntity>>

    @Query("SELECT * FROM vehicles WHERE businessId = :businessId")
    fun getVehiclesForBusiness(businessId: String): kotlinx.coroutines.flow.Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE fleetId = :fleetId")
    fun getVehiclesForFleet(fleetId: String): kotlinx.coroutines.flow.Flow<List<VehicleEntity>>

    @Query("SELECT * FROM fleets WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getFleetByInviteCode(inviteCode: String): FleetEntity?

    @Query("SELECT * FROM fleets WHERE id IN (SELECT fleetId FROM fleet_members WHERE userId = :userId)")
    fun getFleetsForDriver(userId: String): kotlinx.coroutines.flow.Flow<List<FleetEntity>>

    @Query("SELECT * FROM vehicles WHERE assignedDriverId = :driverId")
    fun getAssignedVehiclesForDriver(driverId: String): kotlinx.coroutines.flow.Flow<List<VehicleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusinessProfile(profile: BusinessProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFleet(fleet: FleetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFleetMember(member: FleetMemberEntity)

    @Query("DELETE FROM fleets WHERE id = :fleetId")
    suspend fun deleteFleet(fleetId: String)

    @Query("DELETE FROM fleet_members WHERE id = :memberId")
    suspend fun deleteFleetMember(memberId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE businessId = :businessId AND ((senderId = :userA AND receiverId = :userB) OR (senderId = :userB AND receiverId = :userA)) ORDER BY timestamp ASC")
    fun getChatHistory(businessId: String, userA: String, userB: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    // Retrieve active chat partners with their last message
    @Query("""
        SELECT * FROM chat_messages 
        WHERE businessId = :businessId AND (senderId = :userId OR receiverId = :userId)
        GROUP BY CASE WHEN senderId = :userId THEN receiverId ELSE senderId END
        ORDER BY timestamp DESC
    """)
    fun getRecentChats(businessId: String, userId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(blockEntry: ChatBlocklistEntity)

    @Query("DELETE FROM chat_blocklist WHERE businessId = :businessId AND blockerUserId = :blockerId AND blockedUserId = :blockedId")
    suspend fun unblockUser(businessId: String, blockerId: String, blockedId: String)

    @Query("SELECT COUNT(*) FROM chat_blocklist WHERE businessId = :businessId AND blockerUserId = :blockerId AND blockedUserId = :blockedId")
    suspend fun isUserBlockedBy(businessId: String, blockerId: String, blockedId: String): Int

    @Query("SELECT COUNT(*) FROM chat_blocklist WHERE businessId = :businessId AND ((blockerUserId = :userA AND blockedUserId = :userB) OR (blockerUserId = :userB AND blockedUserId = :userA))")
    suspend fun hasBlockBetween(businessId: String, userA: String, userB: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE receiverId = :userId AND status != 'READ'")
    fun observeUnreadCount(userId: String): Flow<Int>

    // Universal Direct / Mesh conversations without business restriction (Multi-alias support for off-grid Mesh Node IDs & Cloud UUIDs)
    @Query("""
        SELECT * FROM chat_messages 
        WHERE (
            (senderId IN (:userAIds) AND receiverId IN (:userBIds)) 
            OR (senderId IN (:userBIds) AND receiverId IN (:userAIds))
        ) 
        ORDER BY timestamp ASC
    """)
    fun getChatHistoryMultiAlias(userAIds: List<String>, userBIds: List<String>): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT * FROM chat_messages 
        WHERE (
            (senderId IN (:userA, 'local_owner') AND receiverId = :userB) 
            OR (senderId = :userB AND receiverId IN (:userA, 'local_owner'))
            OR (senderId = :userA AND receiverId = :userB)
            OR (senderId = :userB AND receiverId = :userA)
        ) 
        ORDER BY timestamp ASC
    """)
    fun getChatHistoryAnyBusiness(userA: String, userB: String): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT * FROM chat_messages 
        WHERE (
            senderId IN (:userIds) OR receiverId IN (:userIds) 
            OR senderId = 'local_owner' OR receiverId = 'local_owner'
        )
        GROUP BY CASE 
            WHEN senderId IN (:userIds) OR senderId = 'local_owner' THEN receiverId 
            ELSE senderId 
        END
        ORDER BY timestamp DESC
    """)
    fun getAllRecentChatsMultiAlias(userIds: List<String>): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT * FROM chat_messages 
        WHERE (
            senderId = :userId OR receiverId = :userId 
            OR senderId = 'local_owner' OR receiverId = 'local_owner'
        )
        GROUP BY CASE 
            WHEN senderId = :userId OR senderId = 'local_owner' THEN receiverId 
            ELSE senderId 
        END
        ORDER BY timestamp DESC
    """)
    fun getAllRecentChats(userId: String): Flow<List<ChatMessageEntity>>
}

@Dao
interface ChatReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ChatReportEntity)

    @Query("SELECT COUNT(*) FROM chat_reports WHERE reporterUserId = :reporterId AND reportedUserId = :reportedId AND status != 'DISMISSED'")
    suspend fun hasActiveReportBetween(reporterId: String, reportedId: String): Int

    @Query("SELECT * FROM chat_reports WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getReportsForBusiness(businessId: String): Flow<List<ChatReportEntity>>

    @Query("UPDATE chat_reports SET status = :status, reviewedAt = :reviewedAt WHERE id = :reportId")
    suspend fun updateReportStatus(reportId: String, status: String, reviewedAt: Long)
}

@Dao
interface DvirReportDao {
    @Query("SELECT * FROM dvir_reports WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getReportsForVehicle(vehicleId: String): Flow<List<DvirReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DvirReportEntity)
}
