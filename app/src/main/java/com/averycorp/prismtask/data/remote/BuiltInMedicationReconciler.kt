package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.preferences.MedicationMigrationPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles medications that were independently migrated on multiple
 * devices. Runs once after the first successful cloud sync; dedups by
 * normalized name (same winner rule as the v53→v54 migration).
 *
 * Pattern mirrors [BuiltInHabitReconciler] — the habit-dedup story that
 * was the model for this one — but keyed on name rather than
 * `templateKey` (medications don't have built-in template identities).
 */
@Singleton
class BuiltInMedicationReconciler
@Inject
constructor(
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val migrationPreferences: MedicationMigrationPreferences,
    private val syncTracker: SyncTracker,
    private val logger: PrismSyncLogger
) {
    suspend fun reconcileAfterSyncIfNeeded() {
        if (migrationPreferences.isReconciliationDone()) return
        try {
            mergeDuplicatesByName()
        } finally {
            migrationPreferences.setReconciliationDone(true)
        }
    }

    private suspend fun mergeDuplicatesByName() {
        val all = medicationDao.getAllOnce()
        if (all.size <= 1) return

        val groups = all.groupBy { it.name.trim().lowercase() }

        for ((key, meds) in groups) {
            if (meds.size <= 1) continue

            // Winner = the row with the most dose history, tiebreak on
            // smallest id (stable). Preserves the surface a user is
            // most likely to recognize.
            val withCounts = meds.map { m -> m to medicationDoseDao.countForMedOnce(m.id) }
            val keeper = withCounts.maxByOrNull { (m, count) -> count * 1_000_000L - m.id }!!.first
            val losers = meds.filter { it.id != keeper.id }

            for (loser in losers) {
                logger.info(
                    operation = "reconcile.medication",
                    detail = "key=$key loser=${loser.id} keeper=${keeper.id}"
                )
                // Capture loser's dose IDs BEFORE reassign — after the
                // UPDATE statement they all point at the keeper and we
                // can no longer distinguish them from keeper's own doses.
                val reassignedDoseIds = medicationDoseDao.getAllForMedOnce(loser.id).map { it.id }
                medicationDoseDao.reassignMedicationId(
                    oldId = loser.id,
                    newId = keeper.id
                )
                // Each reassigned dose's cloud document still carries the
                // loser's medicationCloudId. Queue an update so push
                // re-uploads with the keeper's medicationCloudId resolved
                // via sync_metadata at push time. Without this, the cloud
                // copies of these doses dangle off a deleted parent doc.
                reassignedDoseIds.forEach { syncTracker.trackUpdate(it, "medication_dose") }
                // Queue the loser's cloud doc for deletion before scrubbing
                // it from Room — otherwise the cloud copy lingers and
                // re-pulls reintroduce the duplicate on the next sign-in.
                syncTracker.trackDelete(loser.id, "medication")
                medicationDao.deleteById(loser.id)
            }
        }
    }
}
