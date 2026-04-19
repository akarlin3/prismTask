package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import javax.inject.Inject
import javax.inject.Singleton

private const val REPAIR_TAG = "PrismRepair"

/**
 * Reconciles built-in habits that were independently seeded on multiple devices.
 *
 * Two passes exist:
 *  - [runDriftCleanupIfNeeded]: local-only, runs at app start before sign-in.
 *    Collapses duplicates that already exist in Room (e.g. from a prior partial
 *    sync on the same device).
 *  - [reconcileAfterSyncIfNeeded]: runs once after the first successful cloud
 *    sync, catching any duplicates introduced by pulling the cloud copy of a
 *    habit that was already seeded locally.
 *
 * Both passes use the same merge rule: for each group of habits sharing the
 * same templateKey (or name when templateKey is absent), keep the one with the
 * most habit_completions. Completions from discarded duplicates are reassigned
 * to the winner. The cloud ID mapping in sync_metadata is transferred to the
 * winner if it was only known for the loser.
 */
@Singleton
class BuiltInHabitReconciler
@Inject
constructor(
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val syncTracker: SyncTracker,
    private val logger: PrismSyncLogger
) {
    suspend fun runBackfillIfNeeded() {
        if (builtInSyncPreferences.isBuiltInBackfillDone()) return
        try {
            val count = habitDao.backfillAllBuiltIns()
            // Queue updated habits for Firestore push so the cloud document
            // also gets isBuiltIn/templateKey populated.
            habitDao.getBuiltInHabitsOnce().forEach { syncTracker.trackUpdate(it.id, "habit") }
            android.util.Log.i(REPAIR_TAG, "builtin_backfill | status=success | detail=updated=$count")
            builtInSyncPreferences.setBuiltInBackfillDone(true)
        } catch (e: Exception) {
            android.util.Log.e(REPAIR_TAG, "builtin_backfill | status=failed | exception=${e.message}")
            // Flag intentionally NOT set — retry on next app start.
        }
    }

    suspend fun runDriftCleanupIfNeeded() {
        if (builtInSyncPreferences.isDriftCleanupDone()) return
        try {
            mergeDuplicateBuiltIns()
        } finally {
            builtInSyncPreferences.setDriftCleanupDone(true)
        }
    }

    suspend fun reconcileAfterSyncIfNeeded() {
        if (builtInSyncPreferences.isBuiltInsReconciled()) return
        try {
            mergeDuplicateBuiltIns()
        } finally {
            builtInSyncPreferences.setBuiltInsReconciled(true)
        }
    }

    private suspend fun mergeDuplicateBuiltIns() {
        val builtIns = habitDao.getBuiltInHabitsOnce()
        if (builtIns.size <= 1) return

        // Group by templateKey when present, fall back to name for legacy rows
        val groups = builtIns.groupBy { it.templateKey ?: it.name }

        for ((groupKey, habits) in groups) {
            if (habits.size <= 1) continue

            val withCounts = habits.map { h ->
                h to habitCompletionDao.countByHabitOnce(h.id)
            }
            val (keeper, _) = withCounts.maxByOrNull { (_, count) -> count }!!
            val losers = habits.filter { it.id != keeper.id }

            for (loser in losers) {
                logger.info(
                    operation = "reconcile.builtin",
                    detail = "key=$groupKey loser=${loser.id} keeper=${keeper.id}"
                )

                // Move completions before deleting the loser so CASCADE doesn't
                // remove them first.
                habitCompletionDao.reassignHabitId(
                    oldHabitId = loser.id,
                    newHabitId = keeper.id
                )

                // Transfer the cloud ID to the keeper if the keeper doesn't
                // have one yet (e.g. loser was the synced copy, keeper was the
                // local seed that never pushed).
                val loserCloudId = syncMetadataDao.getCloudId(loser.id, "habit")
                val keeperCloudId = syncMetadataDao.getCloudId(keeper.id, "habit")
                if (loserCloudId != null && keeperCloudId == null) {
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = keeper.id,
                            entityType = "habit",
                            cloudId = loserCloudId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }

                syncMetadataDao.delete(loser.id, "habit")
                habitDao.deleteById(loser.id)
            }
        }
    }
}
