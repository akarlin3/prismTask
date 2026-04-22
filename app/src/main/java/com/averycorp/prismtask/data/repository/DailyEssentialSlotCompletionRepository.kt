package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.DailyEssentialSlotCompletionDao
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.usecase.MedicationSlotGrouper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin repository over [DailyEssentialSlotCompletionDao] that centralizes the
 * materialize-on-toggle rule used by the Today screen's medication slot list.
 *
 * Before the user taps a slot, no row exists and the UI relies entirely on the
 * virtual derivation in [com.averycorp.prismtask.domain.usecase.MedicationStatusUseCase].
 * The first tap calls [toggleSlot] which either inserts a new row or updates
 * the existing one's ``taken_at`` — from that point forward the materialized
 * row is authoritative for that ``(date, slotKey)`` pair.
 */
@Singleton
class DailyEssentialSlotCompletionRepository
@Inject
constructor(
    private val dao: DailyEssentialSlotCompletionDao,
    private val syncTracker: SyncTracker
) {
    fun observeForDate(date: Long): Flow<List<DailyEssentialSlotCompletionEntity>> =
        dao.observeForDate(date)

    suspend fun getForDateOnce(date: Long): List<DailyEssentialSlotCompletionEntity> =
        dao.getForDateOnce(date)

    /**
     * Materialize (or update) a slot completion row for the given day + slot.
     *
     * @param date           day-start millis (respects the user's rollover hour)
     * @param slotKey        canonical slot identifier (``"HH:mm"`` or ``"anytime"``)
     * @param doseKeys       dose identifiers captured at toggle-time — persisted
     *                       so the materialized row knows which virtual doses
     *                       were batched together
     * @param taken          whether the slot is currently checked
     * @param now            current wall-clock millis (injected so tests can
     *                       pin time without a clock abstraction)
     */
    suspend fun toggleSlot(
        date: Long,
        slotKey: String,
        doseKeys: List<String>,
        taken: Boolean,
        now: Long = System.currentTimeMillis()
    ): DailyEssentialSlotCompletionEntity {
        val existing = dao.getBySlotOnce(date, slotKey)
        val encodedIds = MedicationSlotGrouper.encodeMedIdsJson(doseKeys)
        val row = if (existing == null) {
            DailyEssentialSlotCompletionEntity(
                id = 0,
                date = date,
                slotKey = slotKey,
                medIdsJson = encodedIds,
                takenAt = if (taken) now else null,
                createdAt = now,
                updatedAt = now
            )
        } else {
            existing.copy(
                // Keep the snapshot fresh on every interaction — derivation may
                // have added or removed meds since the row was first written.
                medIdsJson = if (doseKeys.isNotEmpty()) encodedIds else existing.medIdsJson,
                takenAt = if (taken) now else null,
                updatedAt = now
            )
        }
        val id = dao.upsert(row)
        if (existing == null) {
            syncTracker.trackCreate(id, "daily_essential_slot_completion")
        } else {
            syncTracker.trackUpdate(id, "daily_essential_slot_completion")
        }
        return if (existing == null) row.copy(id = id) else row
    }
}
