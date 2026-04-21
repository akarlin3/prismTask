package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.usecase.LifeCategoryClassifier
import javax.inject.Inject
import javax.inject.Singleton

private const val REPAIR_TAG = "PrismSync"

/**
 * One-shot backfill that populates `tasks.life_category` on every legacy row
 * left over from before the centralized resolver in
 * [com.averycorp.prismtask.data.repository.TaskRepository] landed.
 *
 * Runs at most once per install, gated by
 * [BuiltInSyncPreferences.isLifeCategoryBackfillDone]. Invoked from
 * [com.averycorp.prismtask.PrismTaskApplication.onCreate] alongside the other
 * built-in backfills.
 *
 * The pass reads every task with `life_category IS NULL`, runs the keyword
 * classifier on title + description, and writes back either the matched
 * category name or `UNCATEGORIZED` when nothing matches. Each updated row is
 * queued on [SyncTracker] so the next push reflects the new value — existing
 * Firestore docs stay null until the device reconnects.
 */
@Singleton
class LifeCategoryBackfiller
@Inject
constructor(
    private val taskDao: TaskDao,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val syncTracker: SyncTracker
) {
    private val classifier = LifeCategoryClassifier()

    suspend fun runIfNeeded() {
        if (builtInSyncPreferences.isLifeCategoryBackfillDone()) return
        try {
            val rows = taskDao.getAllTasksOnce()
            val pending = rows.filter { it.lifeCategory.isNullOrBlank() }
            if (pending.isEmpty()) {
                android.util.Log.i(REPAIR_TAG, "lifeCategory.backfill | status=noop | detail=updated=0")
                builtInSyncPreferences.setLifeCategoryBackfillDone(true)
                return
            }

            val now = System.currentTimeMillis()
            var classified = 0
            var defaulted = 0
            for (task in pending) {
                val guess = classifier.classify(task.title, task.description)
                val resolved = guess.name
                if (guess == LifeCategory.UNCATEGORIZED) defaulted++ else classified++
                taskDao.update(task.copy(lifeCategory = resolved, updatedAt = now))
                syncTracker.trackUpdate(task.id, "task")
            }
            android.util.Log.i(
                REPAIR_TAG,
                "lifeCategory.backfill | status=success | detail=updated=${pending.size} " +
                    "classified=$classified defaulted=$defaulted"
            )
            builtInSyncPreferences.setLifeCategoryBackfillDone(true)
        } catch (e: Exception) {
            android.util.Log.e(REPAIR_TAG, "lifeCategory.backfill | status=failed | exception=${e.message}")
            // Flag intentionally NOT set — retry on next app start.
        }
    }
}
