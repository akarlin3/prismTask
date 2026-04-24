package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotOverrideDao
import com.averycorp.prismtask.data.local.dao.MedicationTierStateDao
import com.averycorp.prismtask.data.local.entity.MedicationSlotCrossRef
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import com.averycorp.prismtask.data.local.entity.MedicationSlotOverrideEntity
import com.averycorp.prismtask.data.local.entity.MedicationTierStateEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.TierSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-source-of-truth repository for the medication slot system
 * (slots, per-medication overrides, slot ↔ medication junction, and
 * per-day achieved tier states). Mirrors [MedicationRepository]'s
 * pattern: every write hits the DAO and then notifies [SyncTracker]
 * with the matching entity type so the next sync push picks it up.
 *
 * Junction rows do NOT call [SyncTracker] directly — the parent
 * `MedicationEntity`'s push embeds the slot cloud-id list, and the pull
 * side rebuilds the junction from that list. This matches the
 * `task_tags` / `TaskTagCrossRef` pattern.
 */
@Singleton
class MedicationSlotRepository
@Inject
constructor(
    private val slotDao: MedicationSlotDao,
    private val overrideDao: MedicationSlotOverrideDao,
    private val tierStateDao: MedicationTierStateDao,
    private val syncTracker: SyncTracker
) {
    // ── Slots ───────────────────────────────────────────────────────────

    fun observeActiveSlots(): Flow<List<MedicationSlotEntity>> = slotDao.observeActive()

    fun observeAllSlots(): Flow<List<MedicationSlotEntity>> = slotDao.observeAll()

    fun observeSlotById(id: Long): Flow<MedicationSlotEntity?> = slotDao.observeById(id)

    suspend fun getSlotByIdOnce(id: Long): MedicationSlotEntity? = slotDao.getByIdOnce(id)

    suspend fun getActiveSlotsOnce(): List<MedicationSlotEntity> = slotDao.getActiveOnce()

    suspend fun getAllSlotsOnce(): List<MedicationSlotEntity> = slotDao.getAllOnce()

    suspend fun insertSlot(slot: MedicationSlotEntity): Long {
        val now = System.currentTimeMillis()
        val id = slotDao.insert(slot.copy(createdAt = now, updatedAt = now))
        syncTracker.trackCreate(id, "medication_slot")
        return id
    }

    suspend fun updateSlot(slot: MedicationSlotEntity) {
        val updated = slot.copy(updatedAt = System.currentTimeMillis())
        slotDao.update(updated)
        syncTracker.trackUpdate(updated.id, "medication_slot")
    }

    suspend fun softDeleteSlot(id: Long) {
        val now = System.currentTimeMillis()
        slotDao.softDelete(id, now)
        syncTracker.trackUpdate(id, "medication_slot")
    }

    suspend fun restoreSlot(id: Long) {
        val now = System.currentTimeMillis()
        slotDao.restore(id, now)
        syncTracker.trackUpdate(id, "medication_slot")
    }

    suspend fun deleteSlot(slot: MedicationSlotEntity) {
        slotDao.delete(slot)
        syncTracker.trackDelete(slot.id, "medication_slot")
    }

    // ── Junction (medication ↔ slot) ────────────────────────────────────

    suspend fun getSlotsForMedicationOnce(medicationId: Long): List<MedicationSlotEntity> =
        slotDao.getSlotsForMedicationOnce(medicationId)

    fun observeSlotsForMedication(medicationId: Long): Flow<List<MedicationSlotEntity>> =
        slotDao.observeSlotsForMedication(medicationId)

    suspend fun getSlotIdsForMedicationOnce(medicationId: Long): List<Long> =
        slotDao.getSlotIdsForMedicationOnce(medicationId)

    suspend fun getMedicationIdsForSlotOnce(slotId: Long): List<Long> =
        slotDao.getMedicationIdsForSlotOnce(slotId)

    /**
     * Replace the link set for a medication with [slotIds]. Bumps the
     * parent medication's sync state so the embedded slot cloud-id list
     * gets re-pushed. The caller is responsible for invoking
     * [com.averycorp.prismtask.data.remote.SyncTracker.trackUpdate] on
     * the medication itself if the change isn't already wrapped in a
     * `MedicationRepository.update` call.
     */
    suspend fun replaceLinksForMedication(medicationId: Long, slotIds: List<Long>) {
        slotDao.deleteLinksForMedication(medicationId)
        if (slotIds.isNotEmpty()) {
            slotDao.insertLinks(slotIds.distinct().map { MedicationSlotCrossRef(medicationId, it) })
        }
    }

    suspend fun addLink(medicationId: Long, slotId: Long) {
        slotDao.insertLink(MedicationSlotCrossRef(medicationId, slotId))
    }

    suspend fun removeLink(medicationId: Long, slotId: Long) {
        slotDao.deleteLink(medicationId, slotId)
    }

    // ── Overrides ───────────────────────────────────────────────────────

    fun observeOverridesForMedication(medicationId: Long): Flow<List<MedicationSlotOverrideEntity>> =
        overrideDao.observeForMedication(medicationId)

    suspend fun getOverridesForMedicationOnce(medicationId: Long): List<MedicationSlotOverrideEntity> =
        overrideDao.getForMedicationOnce(medicationId)

    suspend fun getOverrideForPairOnce(medicationId: Long, slotId: Long): MedicationSlotOverrideEntity? =
        overrideDao.getForPairOnce(medicationId, slotId)

    suspend fun upsertOverride(override: MedicationSlotOverrideEntity): Long {
        val now = System.currentTimeMillis()
        val existing = overrideDao.getForPairOnce(override.medicationId, override.slotId)
        return if (existing == null) {
            val id = overrideDao.insert(override.copy(createdAt = now, updatedAt = now))
            syncTracker.trackCreate(id, "medication_slot_override")
            id
        } else {
            val merged = existing.copy(
                overrideIdealTime = override.overrideIdealTime,
                overrideDriftMinutes = override.overrideDriftMinutes,
                updatedAt = now
            )
            overrideDao.update(merged)
            syncTracker.trackUpdate(merged.id, "medication_slot_override")
            merged.id
        }
    }

    suspend fun deleteOverride(override: MedicationSlotOverrideEntity) {
        overrideDao.delete(override)
        syncTracker.trackDelete(override.id, "medication_slot_override")
    }

    suspend fun deleteOverrideForPair(medicationId: Long, slotId: Long) {
        val existing = overrideDao.getForPairOnce(medicationId, slotId) ?: return
        overrideDao.delete(existing)
        syncTracker.trackDelete(existing.id, "medication_slot_override")
    }

    // ── Tier states ─────────────────────────────────────────────────────

    fun observeTierStatesForDate(date: String): Flow<List<MedicationTierStateEntity>> =
        tierStateDao.observeForDate(date)

    suspend fun getTierStatesForDateOnce(date: String): List<MedicationTierStateEntity> =
        tierStateDao.getForDateOnce(date)

    suspend fun getTierStateForTripleOnce(
        medicationId: Long,
        date: String,
        slotId: Long
    ): MedicationTierStateEntity? = tierStateDao.getForTripleOnce(medicationId, date, slotId)

    /**
     * Upsert a tier-state row. When the existing row is `USER_SET` and the
     * incoming write is `COMPUTED`, the user override wins — the row is
     * left untouched. Callers that want to clear a user override should
     * delete the row first, then re-upsert (or call [setComputedTier]
     * with a forced overwrite, which is not currently exposed because
     * the UI flow always goes through explicit user action).
     */
    suspend fun upsertTierState(
        medicationId: Long,
        slotId: Long,
        date: String,
        tier: AchievedTier,
        source: TierSource
    ): Long {
        val now = System.currentTimeMillis()
        val existing = tierStateDao.getForTripleOnce(medicationId, date, slotId)
        return if (existing == null) {
            val id = tierStateDao.insert(
                MedicationTierStateEntity(
                    medicationId = medicationId,
                    slotId = slotId,
                    logDate = date,
                    tier = tier.toStorage(),
                    tierSource = source.toStorage(),
                    createdAt = now,
                    updatedAt = now
                )
            )
            syncTracker.trackCreate(id, "medication_tier_state")
            id
        } else {
            val existingSource = TierSource.fromStorage(existing.tierSource)
            if (existingSource == TierSource.USER_SET && source == TierSource.COMPUTED) {
                return existing.id
            }
            val merged = existing.copy(
                tier = tier.toStorage(),
                tierSource = source.toStorage(),
                updatedAt = now
            )
            tierStateDao.update(merged)
            syncTracker.trackUpdate(merged.id, "medication_tier_state")
            merged.id
        }
    }

    suspend fun deleteTierState(state: MedicationTierStateEntity) {
        tierStateDao.delete(state)
        syncTracker.trackDelete(state.id, "medication_tier_state")
    }

    /**
     * Stamp a user-claimed intended_time on the existing tier-state row
     * for `(medication, slot, date)`. Returns the row id, or null if no
     * row exists yet (caller should `upsertTierState` first to materialize
     * the row, then set intended time).
     *
     * Distinct from [upsertTierState] because intended_time is a *user
     * intention* about wall-clock — not part of auto-compute. Letting
     * `upsertTierState` accept it would mean every refreshTierState
     * call clobbers user-set times.
     */
    suspend fun setTierStateIntendedTime(
        medicationId: Long,
        slotId: Long,
        date: String,
        intendedTime: Long
    ): Long? {
        val existing = tierStateDao.getForTripleOnce(medicationId, date, slotId) ?: return null
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            intendedTime = intendedTime,
            updatedAt = now
        )
        tierStateDao.update(updated)
        syncTracker.trackUpdate(updated.id, "medication_tier_state")
        return updated.id
    }
}
