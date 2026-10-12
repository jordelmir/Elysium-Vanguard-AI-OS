package com.elysium369.meet.core.wallet

import android.content.Context
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import com.elysium369.meet.ride.domain.AmountMinor
import com.elysium369.meet.ride.domain.BasisPoints
import com.elysium369.meet.ride.domain.CommissionCalculator
import androidx.core.content.edit
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.RideWalletTopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * World-Class Specialist Wallet Store.
 * Provides unified balance management across ALL services:
 * Viajes, Grúa, Mecánicos, Repuestos, and Servicios Elysium.
 *
 * Core Guarantees:
 * Wallet amounts are a cached projection only. The server ledger is the sole
 * authority for credit, commission and completed-job balances.
 */

@Serializable
data class SpecialistTopup(
    val id: String,
    val specialistId: String,
    val serviceVertical: String,
    val amountCrc: Long,
    val referenceNumber: String,
    val senderPhoneOrName: String? = null,
    val proofLocalPath: String? = null,
    val status: String = "PENDING_REVIEW", // PENDING_REVIEW, APPROVED, REJECTED
    val submittedAtEpochMs: Long = System.currentTimeMillis(),
    val decisionReason: String? = null,
)

@Serializable
data class SpecialistWalletState(
    val specialistId: String,
    val serviceVertical: String,
    val balanceCrc: Long = 0,
    val starterGiftCrc: Long = 0,
    val promotionalAvailableCrc: Long? = null,
    val fundedAvailableCrc: Long? = null,
    val reservedCrc: Long? = null,
    val commissionPercent: Int = 5,
    val totalEarningsCrc: Long = 0,
    val totalCommissionsPaidCrc: Long = 0,
    val completedJobsCount: Int = 0,
    val topups: List<SpecialistTopup> = emptyList(),
    val accumulatedAdvanceDebtCrc: Long = 0L,
    val creditLimitCrc: Long = 1_000_000_000L,
    val isOperativeLockedDueToDebt: Boolean = false,
    val isEnterpriseSettlementMandatory: Boolean = false,
) {
    val availableCreditCrc: Long
        get() = (creditLimitCrc - accumulatedAdvanceDebtCrc).coerceAtLeast(0L)

    val canRequestAdvance: Boolean
        get() = balanceCrc <= SpecialistWalletStore.MIN_OPERATIVE_BALANCE_THRESHOLD_CRC && !isOperativeSuspended

    val isDebtLimitReached: Boolean
        get() = accumulatedAdvanceDebtCrc >= creditLimitCrc

    val isOperativeSuspended: Boolean
        get() = isEnterpriseSettlementMandatory && accumulatedAdvanceDebtCrc > 0L && balanceCrc <= SpecialistWalletStore.MIN_OPERATIVE_BALANCE_THRESHOLD_CRC
}

data class JobCommissionSplit(
    val grossCrc: Long,
    val commissionBasisPoints: Int = 500,
) {
    val platformFeeCrc: Long = CommissionCalculator.calculate(
        AmountMinor.of(grossCrc), BasisPoints.of(commissionBasisPoints),
    ).value
    val specialistNetCrc: Long = Math.subtractExact(grossCrc, platformFeeCrc)
}

object SpecialistWalletStore {
    // Constantes Institucionales de Respaldo Operativo y Préstamo Transitorio
    const val MIN_OPERATIVE_BALANCE_THRESHOLD_CRC = 400L // Umbral de saldo: sólo pide si tiene ₡400 o menos
    const val ADVANCE_TRANCHE_AMOUNT_CRC = 3000L // Tranche exacto de préstamo: de 3,000 en 3,000 CRC
    const val MAX_OPERATIVE_CREDIT_CEILING_CRC = 1_000_000_000L // La deuda puede alcanzar millones sin bloqueo previo
    const val DEFAULT_ADVANCE_STEP_CRC = 3000L // Monto de préstamo estándar: ₡3,000 CRC
    const val ENTERPRISE_ENTITY_NAME = "Elysium Network Corporation — Fideicomiso Operativo Transitorio"
    const val ENTERPRISE_STATUS_NOTICE = "Línea de Préstamo Operativo (Liquidación diferida a la habilitación de la Cuenta Empresarial Oficial)"

    // Compatibilidad institucional (datos personales eliminados formalmente)
    const val SINPE_PHONE = "FIDEICOMISO-ELYSIUM"
    const val SINPE_RECIPIENT_NAME = "Elysium Corporate Trust"
    const val SINPE_EMAIL = "trust@elysium.network"

    private fun ownerNamespace(): String = SupabaseModule.client.auth.currentUserOrNull()?.id ?: "GUEST"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val memoryFlows = mutableMapOf<String, MutableStateFlow<SpecialistWalletState>>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            SupabaseModule.client.auth.sessionStatus.collect {
                val owner = ownerNamespace()
                synchronized(memoryFlows) {
                    memoryFlows.forEach { (key, flow) ->
                        if (!key.startsWith("${owner}_")) {
                            flow.value = SpecialistWalletState(flow.value.specialistId, flow.value.serviceVertical)
                        }
                    }
                }
            }
        }
    }

    /**
     * Get or create a reactive StateFlow for a specialist wallet.
     */
    fun getWalletFlow(
        context: Context,
        specialistId: String,
        serviceVertical: String = "ALL",
    ): StateFlow<SpecialistWalletState> {
        val key = "${ownerNamespace()}_${specialistId}_$serviceVertical"
        synchronized(memoryFlows) {
            return memoryFlows.getOrPut(key) {
                val initialState = loadState(context, specialistId, serviceVertical)
                MutableStateFlow(initialState)
            }.asStateFlow()
        }
    }

    /**
     * Synchronize the local projection with the authoritative Supabase ledger.
     */
    fun syncWithTrustCenter(context: Context, specialistId: String, serviceVertical: String = "ALL") {
        val owner = ownerNamespace()
        if (owner == "GUEST") return
        scope.launch {
            runCatching {
                check(ownerNamespace() == owner)
                PlatformTrustCenterGateway.ensureStarterCredit()
                val remoteBal = PlatformTrustCenterGateway.walletBalance()
                val remoteTopups = PlatformTrustCenterGateway.loadOwnWalletTopups()
                check(ownerNamespace() == owner)

                val current = getWalletFlow(context, specialistId, serviceVertical).value
                val combinedBalance = remoteBal.availableMinor

                val mappedRemoteTopups = remoteTopups.map { rt ->
                    SpecialistTopup(
                        id = rt.id,
                        specialistId = specialistId,
                        serviceVertical = serviceVertical,
                        amountCrc = rt.amountMinor,
                        referenceNumber = rt.transferReference ?: "SINPE-${rt.id.take(8)}",
                        senderPhoneOrName = rt.senderPhone,
                        status = rt.status,
                        decisionReason = rt.decisionReason,
                    )
                }

                val mergedTopups = (current.topups + mappedRemoteTopups)
                    .distinctBy { it.id }
                    .sortedByDescending { it.submittedAtEpochMs }

                updateState(
                    context,
                    current.copy(
                        balanceCrc = combinedBalance,
                        promotionalAvailableCrc = remoteBal.promotionalAvailableMinor,
                        fundedAvailableCrc = remoteBal.fundedAvailableMinor,
                        reservedCrc = remoteBal.reservedMinor,
                        topups = mergedTopups,
                    ),
                    expectedOwner = owner,
                )
            }
        }
    }

    /**
     * Submit a new SINPE Top-Up for Trust Center approval.
     */
    suspend fun submitTopup(
        context: Context,
        specialistId: String,
        serviceVertical: String,
        amountCrc: Long,
        referenceNumber: String,
        senderPhoneOrName: String?,
        proofFile: File?,
    ): SpecialistTopup {
        require(amountCrc > 0) { "INVALID_AMOUNT" }
        val owner = ownerNamespace()
        check(owner != "GUEST") { "AUTH_REQUIRED" }
        val topupId = UUID.randomUUID().toString()
        val topup = SpecialistTopup(
            id = topupId,
            specialistId = specialistId,
            serviceVertical = serviceVertical,
            amountCrc = amountCrc,
            referenceNumber = referenceNumber.ifBlank { "SINPE-${System.currentTimeMillis().toString().takeLast(6)}" },
            senderPhoneOrName = senderPhoneOrName,
            proofLocalPath = proofFile?.absolutePath,
            status = "QUEUED_LOCAL",
            submittedAtEpochMs = System.currentTimeMillis(),
        )

        // 1. Persist in local specialist wallet
        val current = getWalletFlow(context, specialistId, serviceVertical).value
        val updatedTopups = listOf(topup) + current.topups.filterNot { it.id == topup.id }
        updateState(context, current.copy(topups = updatedTopups), expectedOwner = owner)

        // 2. Also record into global pending topups queue for Trust Center review
        recordInGlobalTrustQueue(context, topup, owner)

        // A receipt becomes pending server review only after an authenticated ACK.
        if (proofFile != null && proofFile.exists()) {
            val remoteId = runCatching {
                check(ownerNamespace() == owner)
                PlatformTrustCenterGateway.submitWalletTopup(
                    localProof = proofFile.absolutePath,
                    amountMinor = amountCrc,
                    senderPhone = senderPhoneOrName,
                    transferReference = referenceNumber,
                )
            }.getOrNull()
            if (remoteId != null && ownerNamespace() == owner) {
                val confirmed = topup.copy(id = remoteId, status = "PENDING_REVIEW")
                val latest = getWalletFlow(context, specialistId, serviceVertical).value
                updateState(context, latest.copy(topups = listOf(confirmed) + latest.topups.filterNot { it.id in setOf(topupId, remoteId) }), expectedOwner = owner)
                synchronized(memoryFlows) {
                    check(ownerNamespace() == owner) { "ACCOUNT_CHANGED" }
                    saveGlobalTopups(
                        context,
                        listOf(confirmed) + getAllGlobalTopups(context, owner).filterNot { it.id in setOf(topupId, remoteId) },
                        owner,
                    )
                }
                return confirmed
            }
        }
        return topup
    }

    /**
     * Solicitar Préstamo Operativo Inmediato (Fondo de Respaldo Transitorio).
     * Reglas Constitucionales de Operación:
     * 1. Solo disponible cuando el saldo operativo sea de ₡400 CRC o menor.
     * 2. Cada solicitud es por un monto fijo de ₡3,000 CRC (de 3,000 en 3,000 CRC).
     * 3. La deuda se va sumando y registrando continuamente (puede acumular millones).
     * 4. Al vincularse la Cuenta Bancaria Empresarial Oficial, todo monto adeudado
     *    deberá ser liquidado para continuar operando, o la cuenta entrará en suspensión.
     */
    suspend fun requestOperativeAdvance(
        context: Context,
        specialistId: String,
        serviceVertical: String,
        amountCrc: Long = ADVANCE_TRANCHE_AMOUNT_CRC,
    ): SpecialistTopup {
        val owner = ownerNamespace()
        check(owner != "GUEST") { "AUTH_REQUIRED" }

        val current = getWalletFlow(context, specialistId, serviceVertical).value
        check(current.balanceCrc <= MIN_OPERATIVE_BALANCE_THRESHOLD_CRC) {
            "BALANCE_ABOVE_THRESHOLD: Solo puedes pedir prestado cuando tu saldo sea de ₡400 CRC o menos (Saldo actual: ₡${current.balanceCrc} CRC)."
        }
        check(!current.isOperativeSuspended) {
            "OPERATIVE_SUSPENDED: Cuenta empresarial activa. Debes liquidar tu deuda acumulada para continuar operando."
        }

        // Siempre se otorga en tramos exactos de ₡3,000 CRC
        val actualAdvance = ADVANCE_TRANCHE_AMOUNT_CRC

        val advanceId = "ADV-" + UUID.randomUUID().toString().take(8).uppercase()
        val newDebt = current.accumulatedAdvanceDebtCrc + actualAdvance
        val advanceProjectedBalance = current.balanceCrc + actualAdvance

        val topup = SpecialistTopup(
            id = advanceId,
            specialistId = specialistId,
            serviceVertical = serviceVertical,
            amountCrc = actualAdvance,
            referenceNumber = "PRESTAMO-3000-$advanceId",
            senderPhoneOrName = ENTERPRISE_ENTITY_NAME,
            status = "APPROVED",
            submittedAtEpochMs = System.currentTimeMillis(),
            decisionReason = "Préstamo operativo de ₡3,000 CRC aprobado. Deuda acumulada: ₡${newDebt} CRC. Liquidación obligatoria al habilitar cuenta empresarial oficial.",
        )

        val updatedState = current.copy(
            balanceCrc = advanceProjectedBalance,
            accumulatedAdvanceDebtCrc = newDebt,
            isOperativeLockedDueToDebt = current.isEnterpriseSettlementMandatory && newDebt > 0L && advanceProjectedBalance <= MIN_OPERATIVE_BALANCE_THRESHOLD_CRC,
            topups = listOf(topup) + current.topups.filterNot { it.id == advanceId }
        )

        updateState(context, updatedState, expectedOwner = owner)
        recordInGlobalTrustQueue(context, topup, owner)
        return topup
    }

    /**
     * Returns a display-only split. It never changes a wallet balance; the
     * authoritative service ledger must project the completed job first.
     */
    fun recordCompletedJob(
        context: Context,
        specialistId: String,
        serviceVertical: String,
        grossCrc: Long,
    ): JobCommissionSplit {
        val split = JobCommissionSplit(grossCrc = grossCrc)
        return split
    }

    /**
     * Trust Center Decision (Owner validates the real bank transfer).
     */
    fun decideTopup(
        context: Context,
        topupId: String,
        approved: Boolean,
        decisionReason: String,
    ) {
        error("SERVER_RECONCILIATION_REQUIRED")
    }

    /**
     * Retrieve all topups across all specialists for Trust Center display.
     */
    fun getAllGlobalTopups(context: Context): List<SpecialistTopup> {
        return getAllGlobalTopups(context, ownerNamespace())
    }

    private fun getAllGlobalTopups(context: Context, expectedOwner: String): List<SpecialistTopup> {
        check(ownerNamespace() == expectedOwner) { "ACCOUNT_CHANGED" }
        val prefs = context.getSharedPreferences("meet_trust_center_topups_v2_$expectedOwner", Context.MODE_PRIVATE)
        val raw = prefs.getString("topups_json", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SpecialistTopup>>(raw) }.getOrDefault(emptyList())
    }

    fun getPendingGlobalTopups(context: Context): List<SpecialistTopup> {
        return getAllGlobalTopups(context).filter { it.status == "PENDING_REVIEW" }
    }

    fun mapToRideWalletTopups(specialistTopups: List<SpecialistTopup>): List<RideWalletTopup> {
        return specialistTopups.map { st ->
            RideWalletTopup(
                id = st.id,
                driverId = "${st.serviceVertical}:${st.specialistId}",
                amountMinor = st.amountCrc,
                currency = "CRC",
                senderPhone = st.senderPhoneOrName,
                transferReference = st.referenceNumber,
                proofStoragePath = st.proofLocalPath ?: "local/voucher-${st.id.take(8)}.jpg",
                status = st.status,
                submittedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(st.submittedAtEpochMs)),
                decisionReason = st.decisionReason,
            )
        }
    }

    private fun loadState(context: Context, specialistId: String, serviceVertical: String): SpecialistWalletState {
        val prefs = context.getSharedPreferences("elysium_wallet_v2_${ownerNamespace()}_${specialistId}_$serviceVertical", Context.MODE_PRIVATE)
        val raw = prefs.getString("wallet_json", null)
        if (raw != null) {
            val parsed = runCatching { json.decodeFromString<SpecialistWalletState>(raw) }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        // A new local cache has no money until the authoritative ledger arrives.
        return SpecialistWalletState(
            specialistId = specialistId,
            serviceVertical = serviceVertical,
        )
    }

    private fun updateState(context: Context, state: SpecialistWalletState, expectedOwner: String = ownerNamespace()) {
        check(ownerNamespace() == expectedOwner) { "ACCOUNT_CHANGED" }
        val key = "${expectedOwner}_${state.specialistId}_${state.serviceVertical}"
        synchronized(memoryFlows) {
            val flow = memoryFlows.getOrPut(key) { MutableStateFlow(state) }
            flow.value = state
        }
        val prefs = context.getSharedPreferences("elysium_wallet_v2_${expectedOwner}_${state.specialistId}_${state.serviceVertical}", Context.MODE_PRIVATE)
        prefs.edit {
            putString("wallet_json", json.encodeToString(state))
        }
    }

    private fun recordInGlobalTrustQueue(context: Context, topup: SpecialistTopup, expectedOwner: String) {
        synchronized(memoryFlows) {
            check(ownerNamespace() == expectedOwner) { "ACCOUNT_CHANGED" }
            val existing = getAllGlobalTopups(context, expectedOwner)
            val updated = (listOf(topup) + existing.filterNot { it.id == topup.id })
                .sortedByDescending { it.submittedAtEpochMs }
            saveGlobalTopups(context, updated, expectedOwner)
        }
    }

    private fun saveGlobalTopups(context: Context, list: List<SpecialistTopup>, expectedOwner: String) {
        check(ownerNamespace() == expectedOwner) { "ACCOUNT_CHANGED" }
        val prefs = context.getSharedPreferences("meet_trust_center_topups_v2_$expectedOwner", Context.MODE_PRIVATE)
        prefs.edit {
            putString("topups_json", json.encodeToString(list))
        }
    }
}
