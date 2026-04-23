package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.domain.model.medication.MedicationTier

/**
 * Pure auto-compute logic for `medication_tier_states.tier`. Mirrors the
 * legacy `SelfCareRoutines.medicationTierOrder` cumulative-tier semantic:
 * a slot reaches tier T iff every medication of tier T or lower has been
 * marked taken for that slot on that day.
 *
 * The function operates on already-resolved per-slot membership: caller
 * passes the medications that belong to the slot for the day (via
 * `medication_medication_slots`) plus the set of medication ids marked
 * taken in that slot. No DAO access — all I/O happens at the call site.
 *
 * Edge cases:
 *  - Empty `medsForSlot` → `SKIPPED` (nothing scheduled).
 *  - `markedTaken` is empty subset of `medsForSlot` → `SKIPPED`.
 *  - All `ESSENTIAL` meds taken, no `PRESCRIPTION` meds exist → `COMPLETE`
 *    when `COMPLETE` meds are also absent. The "next tier requires nothing
 *    further" rule lets users on a single-tier regimen actually reach the
 *    top of the ladder.
 */
object MedicationTierComputer {
    /**
     * @param medsForSlot every medication assigned to the slot for the day
     *   (id → tier).
     * @param markedTaken ids of medications that have a logged dose for the
     *   slot on the day.
     */
    fun computeAchievedTier(
        medsForSlot: Map<Long, MedicationTier>,
        markedTaken: Set<Long>
    ): AchievedTier {
        if (medsForSlot.isEmpty()) return AchievedTier.SKIPPED
        val takenSubset = medsForSlot.filterKeys { it in markedTaken }
        if (takenSubset.isEmpty()) return AchievedTier.SKIPPED

        // Walk the ladder bottom-up; the slot's achieved tier is the highest
        // ladder rung where every med at that rung or below (that exists in
        // the slot's membership) is also in markedTaken.
        var achieved: AchievedTier = AchievedTier.SKIPPED
        for (rung in MedicationTier.LADDER) {
            val rungMembers = medsForSlot.filterValues { it.ordinal <= rung.ordinal }.keys
            if (rungMembers.isEmpty()) continue
            if (rungMembers.all { it in markedTaken }) {
                achieved = AchievedTier.from(rung)
            } else {
                // First missing rung stops the ladder — higher rungs can't
                // be reached if a lower one isn't satisfied.
                break
            }
        }
        return achieved
    }
}
