package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.AutomationRuleDao
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collapses cross-device duplicates of the same automation template into a
 * single row, keyed by `template_key`. Bridges the small window between
 * `automation_rule` SyncService routing landing (#1070, 2026-05-03) and
 * the at-insert `naturalKeyLookup` adoption fix that prevents new
 * duplicates from forming.
 *
 * Runs once per install, gated by [BuiltInSyncPreferences.isAutomationDupBackfillDone].
 *
 * Merge rule: for each group of rules sharing the same non-null
 * `templateKey`, keep the row with the newest `updatedAt` (forgiveness-first
 * UX — the user's latest customization wins). Tie-break on smallest `id`.
 * Losers are deleted via [SyncTracker]-aware deletes so the cloud
 * tombstone propagates to peers; rules with `templateKey == null` (user-
 * authored) are never touched.
 */
@Singleton
class AutomationDuplicateBackfiller
@Inject
constructor(
    private val automationRuleDao: AutomationRuleDao,
    private val syncTracker: SyncTracker,
    private val syncMetadataDao: com.averycorp.prismtask.data.local.dao.SyncMetadataDao,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val logger: PrismSyncLogger
) {
    suspend fun runIfNeeded() {
        if (builtInSyncPreferences.isAutomationDupBackfillDone()) return
        try {
            val collapsed = collapseDuplicates()
            logger.info(
                operation = "automation.dup_backfill",
                status = "success",
                detail = "groups=${collapsed.first} deleted=${collapsed.second}"
            )
            builtInSyncPreferences.setAutomationDupBackfillDone(true)
        } catch (e: Exception) {
            logger.error(
                operation = "automation.dup_backfill",
                detail = "exception=${e.message}",
                throwable = e
            )
            // Flag intentionally NOT set — retry on next app start.
        }
    }

    private suspend fun collapseDuplicates(): Pair<Int, Int> {
        val rules = automationRuleDao.getAllOnce()
        val groups = rules
            .filter { !it.templateKey.isNullOrEmpty() }
            .groupBy { it.templateKey!! }
            .filterValues { it.size > 1 }
        if (groups.isEmpty()) return 0 to 0

        var deletedTotal = 0
        for ((templateKey, dupes) in groups) {
            val keeper = pickKeeper(dupes)
            val losers = dupes.filter { it.id != keeper.id }
            for (loser in losers) {
                logger.info(
                    operation = "automation.dup_backfill",
                    detail = "templateKey=$templateKey loser=${loser.id} keeper=${keeper.id}"
                )
                automationRuleDao.deleteById(loser.id)
                syncMetadataDao.delete(loser.id, "automation_rule")
                syncTracker.trackDelete(loser.id, "automation_rule")
                deletedTotal++
            }
        }
        return groups.size to deletedTotal
    }

    private fun pickKeeper(dupes: List<AutomationRuleEntity>): AutomationRuleEntity =
        dupes.maxWithOrNull(
            compareBy<AutomationRuleEntity> { it.updatedAt }.thenByDescending { it.id }
        )!!
}
