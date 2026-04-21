package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import javax.inject.Inject
import javax.inject.Singleton

private const val ENTITY_TYPE = "task_template"

/**
 * Collapses built-in `task_templates` rows that share a
 * [TaskTemplateEntity.templateKey] so fresh installs + repeat syncs can't
 * regrow duplicates the way they did before [BuiltInHabitReconciler] existed
 * for the habit side.
 *
 * Runs once, post-first-successful-cloud-sync, gated by
 * [BuiltInSyncPreferences.isBuiltInTaskTemplatesReconciled]. Triggered from
 * [SyncService.fullSync] alongside [BuiltInHabitReconciler.reconcileAfterSyncIfNeeded].
 *
 * Merge rule (mirrors the habit reconciler in spirit, but task_templates have
 * no completion table and no inbound FKs, so the pass is simpler):
 *  - Group by `templateKey`. Rows without a key are skipped.
 *  - Winner = lex-smallest `cloud_id` within the group (per spec). Rows with
 *    a null cloud_id lose to any row that has one; ties among nulls break by
 *    smallest local id.
 *  - The winner's cloud_id mapping in [SyncMetadataDao] is filled in from a
 *    loser's mapping if the winner doesn't have one yet.
 *  - Losers are deleted.
 */
@Singleton
class BuiltInTaskTemplateReconciler
@Inject
constructor(
    private val taskTemplateDao: TaskTemplateDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val logger: PrismSyncLogger
) {
    suspend fun reconcileAfterSyncIfNeeded() {
        if (builtInSyncPreferences.isBuiltInTaskTemplatesReconciled()) return
        try {
            mergeDuplicateBuiltIns()
        } finally {
            builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(true)
        }
    }

    private suspend fun mergeDuplicateBuiltIns() {
        val builtIns = taskTemplateDao.getBuiltInTemplatesOnce()
        if (builtIns.size <= 1) return

        val groups = builtIns
            .filter { !it.templateKey.isNullOrBlank() }
            .groupBy { it.templateKey!! }

        for ((groupKey, templates) in groups) {
            if (templates.size <= 1) continue

            val keeper = pickKeeper(templates)
            val losers = templates.filter { it.id != keeper.id }

            for (loser in losers) {
                logger.info(
                    operation = "reconcile.builtin_template",
                    detail = "key=$groupKey loser=${loser.id} keeper=${keeper.id}"
                )

                val loserCloudId = syncMetadataDao.getCloudId(loser.id, ENTITY_TYPE)
                val keeperCloudId = syncMetadataDao.getCloudId(keeper.id, ENTITY_TYPE)
                if (!loserCloudId.isNullOrBlank() && keeperCloudId.isNullOrBlank()) {
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = keeper.id,
                            entityType = ENTITY_TYPE,
                            cloudId = loserCloudId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }

                syncMetadataDao.delete(loser.id, ENTITY_TYPE)
                taskTemplateDao.deleteById(loser.id)
            }
        }
    }

    private fun pickKeeper(templates: List<TaskTemplateEntity>): TaskTemplateEntity {
        val withCloud = templates.filter { !it.cloudId.isNullOrBlank() }
        if (withCloud.isNotEmpty()) {
            return withCloud.minByOrNull { it.cloudId!! }!!
        }
        return templates.minByOrNull { it.id }!!
    }
}
