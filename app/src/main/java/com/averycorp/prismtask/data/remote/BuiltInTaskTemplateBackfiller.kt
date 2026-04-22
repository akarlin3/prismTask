package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.seed.TemplateSeeder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heals `task_templates` rows that were pulled from Firestore before the
 * `template_key` + `is_built_in` columns existed on the cloud side. Those
 * rows arrive locally with `template_key = NULL` and `is_built_in = false`,
 * which makes them invisible to [BuiltInTaskTemplateReconciler] — the
 * reconciler's `getBuiltInTemplatesOnce()` query filters on `is_built_in = 1`
 * and its grouper requires a non-blank `template_key`. Without this pass,
 * those rows coexist forever with their freshly-reseeded local siblings
 * and the reconciler never fires a merge.
 *
 * The pass runs once, gated by
 * [BuiltInSyncPreferences.isTaskTemplateBackfillDone], and triggers from
 * [SyncService.startAutoSync] before the first `fullSync`. Its scan:
 *
 *  1. Load every row with a null/blank `template_key`.
 *  2. Match each row's `name` (case-insensitive, trimmed) against
 *     [TemplateSeeder.BUILT_IN_TEMPLATES]. Rows that don't match a
 *     built-in are user content and are left alone.
 *  3. Skip matched rows whose `usage_count > 0` or `last_used_at != null`
 *     — those show signs of user engagement (the spec's "user-content
 *     collision" guard) and are conservatively treated as user content
 *     that happens to share a name with a built-in.
 *  4. For each surviving match: UPDATE `template_key` + `is_built_in = 1`
 *     + bump `updated_at`, and mark the row as pending-update via
 *     [SyncTracker] so the reactive-push pipeline replaces the Firestore
 *     doc with the healed shape. Future pulls on other devices then
 *     receive correct values.
 *  5. On success, also flip
 *     [BuiltInSyncPreferences.setBuiltInTaskTemplatesReconciled] back to
 *     false so the reconciler re-runs on the next `fullSync` with the
 *     healed dataset. The reconciler sets the flag back to true after
 *     its own pass.
 *
 * Failure preserves the done-flag as false so the next app start retries.
 */
@Singleton
class BuiltInTaskTemplateBackfiller
@Inject
constructor(
    private val taskTemplateDao: TaskTemplateDao,
    private val syncTracker: SyncTracker,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val logger: PrismSyncLogger
) {
    suspend fun runBackfillIfNeeded() {
        if (builtInSyncPreferences.isTaskTemplateBackfillDone()) return
        try {
            val result = runBackfill()
            logger.info(
                operation = "builtin_template_backfill",
                status = "success",
                detail = "updated=${result.updated} skipped=${result.skipped}"
            )
            // Reset the reconciler flag so it re-runs with the correctly-
            // shaped dataset on the next fullSync. The reconciler sets it
            // back to true when its own pass completes.
            if (result.updated > 0) {
                builtInSyncPreferences.setBuiltInTaskTemplatesReconciled(false)
            }
            builtInSyncPreferences.setTaskTemplateBackfillDone(true)
        } catch (e: Exception) {
            logger.error(
                operation = "builtin_template_backfill",
                throwable = e
            )
            // Flag intentionally NOT set — next launch retries.
        }
    }

    private suspend fun runBackfill(): BackfillResult {
        val all = taskTemplateDao.getAllTemplatesOnce()
        val candidates = all.filter { it.templateKey.isNullOrBlank() }
        if (candidates.isEmpty()) return BackfillResult(updated = 0, skipped = 0)

        val nameToKey = buildNameToKeyMap()

        var updated = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        for (row in candidates) {
            val key = nameToKey[row.name.trim().lowercase()] ?: continue
            if (row.usageCount > 0 || row.lastUsedAt != null) {
                logger.info(
                    operation = "builtin_template_backfill.skip",
                    entity = "task_template",
                    id = row.id.toString(),
                    detail = "reason=user_content_collision name=${row.name}"
                )
                skipped++
                continue
            }
            val healed = row.copy(
                templateKey = key,
                isBuiltIn = true,
                updatedAt = now
            )
            taskTemplateDao.updateTemplate(healed)
            syncTracker.trackUpdate(row.id, "task_template")
            updated++
        }
        return BackfillResult(updated = updated, skipped = skipped)
    }

    private fun buildNameToKeyMap(): Map<String, String> =
        (TemplateSeeder.BUILT_IN_TEMPLATES.associate { it.name.trim().lowercase() to it.templateKey } +
            BUILT_IN_TEMPLATE_NAME_ALIASES)

    private data class BackfillResult(val updated: Int, val skipped: Int)

    companion object {
        /**
         * Name aliases for built-ins that have been renamed across releases.
         * The key is the historical (pre-rename) name; the value is the
         * current `template_key`. This lets a pre-rename Firestore doc that
         * survives the rename still match a current built-in.
         *
         * Canonical names remain in [TemplateSeeder.BUILT_IN_TEMPLATES] and
         * are merged with this map at lookup time. If a name appears in
         * both places the canonical value wins (it's added to the map
         * second in [buildNameToKeyMap]).
         *
         * Empty initially — built-ins haven't been renamed yet. This exists
         * so future renames don't need a new code file, just a new entry.
         */
        val BUILT_IN_TEMPLATE_NAME_ALIASES: Map<String, String> = emptyMap()
    }
}
