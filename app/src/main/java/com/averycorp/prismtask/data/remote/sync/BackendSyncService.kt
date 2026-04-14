package com.averycorp.prismtask.data.remote.sync

import android.util.Log
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.preferences.BackendSyncPreferences
import com.averycorp.prismtask.data.preferences.TemplatePreferences
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs local Room data with the FastAPI backend sync endpoints
 * (`/api/v1/sync/push` and `/api/v1/sync/pull`).
 *
 * This is independent of the Firebase [com.averycorp.prismtask.data.remote.SyncService]
 * — both can be enabled side-by-side.
 *
 * Conflict resolution: last-write-wins by `updated_at` timestamp. When a pulled
 * entity has an older `updated_at` than the local copy, the local copy is kept.
 *
 * The payload shape mirrors the Pydantic models in `backend/app/schemas/sync.py`.
 * All timestamps crossing the wire are ISO 8601 strings (the backend uses
 * `datetime` fields, which Pydantic rejects with 422 if we send epoch millis).
 */
@Singleton
class BackendSyncService
@Inject
constructor(
    private val api: PrismTaskApi,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val taskTemplateDao: TaskTemplateDao,
    private val authTokenPreferences: AuthTokenPreferences,
    private val backendSyncPreferences: BackendSyncPreferences,
    private val templatePreferences: TemplatePreferences,
    private val billingManager: BillingManager
) {
    /**
     * True when the user has backend JWTs stored (i.e. they've logged into or
     * registered with the FastAPI backend at least once).
     */
    suspend fun isConnected(): Boolean =
        !authTokenPreferences.getAccessToken().isNullOrBlank()

    /**
     * Run a full sync: push local changes, then pull remote changes. Returns
     * the server timestamp from the pull response on success.
     */
    suspend fun fullSync(): Result<SyncSummary> = runCatching {
        check(isConnected()) { "Not connected to backend. Sign in first." }
        // Check admin status and apply tier override if needed
        checkAdminStatus()
        // On the very first connect, push all local templates in one shot
        // (including the built-ins). Subsequent syncs fall through to the
        // normal updated_at-based incremental push.
        ensureTemplatesPushedOnFirstConnect()
        val pushed = pushChanges()
        val pulled = pullChanges()
        SyncSummary(
            pushed = pushed,
            pulled = pulled,
            lastSyncAt = backendSyncPreferences.getLastSyncAt()
        )
    }

    /**
     * Fetch user info from the backend and update admin status on the
     * BillingManager. Admin users automatically receive ULTRA tier access.
     *
     * Public so callers can refresh admin status independently of a full
     * sync — e.g., on app launch once Firebase auth confirms the user is
     * signed in, so the UI reflects admin state without waiting for the
     * next manual sync. Safely no-ops when the user has no backend JWT.
     */
    suspend fun checkAdminStatus() {
        if (!isConnected()) return
        try {
            val userInfo = api.getMe()
            billingManager.setAdminStatus(userInfo.isAdmin)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch admin status", e)
        }
    }

    /**
     * First-connect helper: pushes *every* local template to the backend the
     * first time the user signs in with a JWT, regardless of the template's
     * `updatedAt` timestamp. After this runs once, subsequent syncs use the
     * normal `updatedAt > since` incremental filter in [pushChanges].
     *
     * The flag is stored in [TemplatePreferences] so it persists across app
     * restarts but resets when the user signs out (via [TemplatePreferences.clear]).
     *
     * Merge behavior for the opposite direction (remote templates landing
     * locally on a new device) is handled in [applyTemplateChanges] using
     * [com.averycorp.prismtask.data.repository.TaskTemplateRepository.mergeTemplatesByName].
     */
    suspend fun ensureTemplatesPushedOnFirstConnect() {
        if (!isConnected()) return
        if (templatePreferences.isFirstSyncDone()) return

        val templates = taskTemplateDao.getAllTemplatesOnce()
        if (templates.isEmpty()) {
            templatePreferences.setFirstSyncDone(true)
            return
        }

        val operations = templates.map { taskTemplateToOperation(it) }
        val request = SyncPushRequest(
            operations = operations,
            lastSync = null
        )
        try {
            api.syncPush(request)
            templatePreferences.setFirstSyncDone(true)
        } catch (e: Exception) {
            Log.e(TAG, "First-connect template push failed", e)
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .recordException(e)
            // Leave the flag unset so we retry next sync.
            throw e
        }
    }

    /**
     * Serialize all locally modified entities since last sync and send them
     * to `/api/v1/sync/push`. Returns the number of operations pushed.
     */
    suspend fun pushChanges(): Int {
        val since = backendSyncPreferences.getLastSyncAt()
        val operations = mutableListOf<SyncOperation>()

        // Tasks — use updatedAt
        taskDao
            .getAllTasksOnce()
            .filter { it.updatedAt > since }
            .forEach { operations += taskToOperation(it) }

        // Projects — use updatedAt
        projectDao
            .getAllProjectsOnce()
            .filter { it.updatedAt > since }
            .forEach { operations += projectToOperation(it) }

        // Tags — TagEntity only has createdAt
        tagDao
            .getAllTagsOnce()
            .filter { it.createdAt > since }
            .forEach { operations += tagToOperation(it) }

        // Habits — use updatedAt
        habitDao
            .getAllHabitsOnce()
            .filter { it.updatedAt > since }
            .forEach { operations += habitToOperation(it) }

        // Habit completions — use completedAt (no updatedAt field)
        habitCompletionDao
            .getAllCompletionsOnce()
            .filter { it.completedAt > since }
            .forEach { operations += habitCompletionToOperation(it) }

        // Task templates — use updatedAt. First-connect push is handled
        // separately by ensureTemplatesPushedOnFirstConnect; here we only pick
        // up templates the user has touched since the last successful sync.
        taskTemplateDao
            .getAllTemplatesOnce()
            .filter { it.updatedAt > since }
            .forEach { operations += taskTemplateToOperation(it) }

        if (operations.isEmpty()) return 0

        val request = SyncPushRequest(
            operations = operations,
            lastSync = if (since > 0) millisToIso(since) else null
        )
        try {
            api.syncPush(request)
        } catch (e: Exception) {
            Log.e(TAG, "Push failed", e)
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .recordException(e)
            throw e
        }
        return operations.size
    }

    /**
     * Fetch all changes since the last sync timestamp and upsert them into
     * Room using last-write-wins conflict resolution. Returns the number of
     * rows applied locally.
     */
    suspend fun pullChanges(): Int {
        val since = backendSyncPreferences.getLastSyncAt()
        val sinceParam = if (since > 0) millisToIso(since) else null
        val response = try {
            api.syncPull(sinceParam)
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed", e)
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .recordException(e)
            throw e
        }

        var applied = 0
        // The backend applies projects before tasks so FK references resolve
        // correctly; mirror that ordering here.
        applied += applyProjectChanges(response.changes.filter { it.entityType == "project" })
        applied += applyTagChanges(response.changes.filter { it.entityType == "tag" })
        applied += applyHabitChanges(response.changes.filter { it.entityType == "habit" })
        applied += applyTaskChanges(response.changes.filter { it.entityType == "task" })
        applied += applyHabitCompletionChanges(
            response.changes.filter { it.entityType == "habit_completion" }
        )
        applied += applyTemplateChanges(
            response.changes.filter { it.entityType == "task_template" }
        )

        val timestampMillis = response.serverTimestamp?.let { isoToMillisOrNull(it) }
            ?: System.currentTimeMillis()
        backendSyncPreferences.setLastSyncAt(timestampMillis)
        return applied
    }

    // region Push serialization

    // endregion

    // region Pull application

    private suspend fun applyTaskChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                taskDao.deleteById(clientId)
                applied++
                continue
            }
            val data = change.data ?: continue
            val remoteUpdatedAt = data.optLong("updated_at")
                ?: change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()
            val existing = taskDao.getTaskByIdOnce(clientId)
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) {
                // Local copy is newer or equal — keep it (last-write-wins).
                continue
            }
            val task = TaskEntity(
                id = clientId,
                title = data.optString("title") ?: "",
                description = data.optString("description"),
                dueDate = data.optLong("due_date"),
                dueTime = data.optLong("due_time"),
                priority = data.optInt("priority") ?: 0,
                isCompleted = data.optBool("is_completed") ?: false,
                projectId = data.optLong("project_id"),
                parentTaskId = data.optLong("parent_task_id"),
                recurrenceRule = data.optString("recurrence_rule"),
                reminderOffset = data.optLong("reminder_offset"),
                createdAt = data.optLong("created_at") ?: System.currentTimeMillis(),
                updatedAt = remoteUpdatedAt,
                completedAt = data.optLong("completed_at"),
                archivedAt = data.optLong("archived_at"),
                notes = data.optString("notes"),
                plannedDate = data.optLong("planned_date"),
                estimatedDuration = data.optInt("estimated_duration"),
                scheduledStartTime = data.optLong("scheduled_start_time"),
                sourceHabitId = data.optLong("source_habit_id"),
                lifeCategory = data.optString("life_category")
            )
            taskDao.insert(task)
            applied++
        }
        return applied
    }

    private suspend fun applyProjectChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                projectDao.getProjectByIdOnce(clientId)?.let { projectDao.delete(it) }
                applied++
                continue
            }
            val data = change.data ?: continue
            val remoteUpdatedAt = data.optLong("updated_at")
                ?: change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()
            val existing = projectDao.getProjectByIdOnce(clientId)
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue
            val project = ProjectEntity(
                id = clientId,
                name = data.optString("name") ?: "",
                color = data.optString("color") ?: "#4A90D9",
                icon = data.optString("icon") ?: "\uD83D\uDCC1",
                createdAt = data.optLong("created_at") ?: System.currentTimeMillis(),
                updatedAt = remoteUpdatedAt
            )
            projectDao.insert(project)
            applied++
        }
        return applied
    }

    private suspend fun applyTagChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                tagDao.getTagByIdOnce(clientId)?.let { tagDao.delete(it) }
                applied++
                continue
            }
            val data = change.data ?: continue
            val remoteCreatedAt = data.optLong("created_at")
                ?: change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()
            val existing = tagDao.getTagByIdOnce(clientId)
            if (existing != null && existing.createdAt >= remoteCreatedAt) continue
            val tag = TagEntity(
                id = clientId,
                name = data.optString("name") ?: "",
                color = data.optString("color") ?: "#6B7280",
                createdAt = remoteCreatedAt
            )
            tagDao.insert(tag)
            applied++
        }
        return applied
    }

    private suspend fun applyHabitChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                habitDao.deleteById(clientId)
                applied++
                continue
            }
            val data = change.data ?: continue
            val remoteUpdatedAt = data.optLong("updated_at")
                ?: change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()
            val existing = habitDao.getHabitByIdOnce(clientId)
            if (existing != null && existing.updatedAt >= remoteUpdatedAt) continue
            val habit = HabitEntity(
                id = clientId,
                name = data.optString("name") ?: "",
                description = data.optString("description"),
                targetFrequency = data.optInt("target_frequency") ?: 1,
                frequencyPeriod = data.optString("frequency_period") ?: "daily",
                activeDays = data.optString("active_days"),
                color = data.optString("color") ?: "#4A90D9",
                icon = data.optString("icon") ?: "\u2B50",
                reminderTime = data.optLong("reminder_time"),
                sortOrder = data.optInt("sort_order") ?: 0,
                isArchived = data.optBool("is_archived") ?: false,
                category = data.optString("category"),
                createDailyTask = data.optBool("create_daily_task") ?: false,
                reminderIntervalMillis = data.optLong("reminder_interval_millis"),
                reminderTimesPerDay = data.optInt("reminder_times_per_day") ?: 1,
                hasLogging = data.optBool("has_logging") ?: false,
                trackBooking = data.optBool("track_booking") ?: false,
                trackPreviousPeriod = data.optBool("track_previous_period") ?: false,
                isBookable = data.optBool("is_bookable") ?: false,
                isBooked = data.optBool("is_booked") ?: false,
                bookedDate = data.optLong("booked_date"),
                bookedNote = data.optString("booked_note"),
                showStreak = data.optBool("show_streak") ?: false,
                createdAt = data.optLong("created_at") ?: System.currentTimeMillis(),
                updatedAt = remoteUpdatedAt
            )
            habitDao.insert(habit)
            applied++
        }
        return applied
    }

    /**
     * Apply incoming template changes using merge-by-name semantics. When a
     * remote template arrives that shares a name with a local template:
     *
     *  - If the remote copy has a strictly higher `usage_count`, overwrite the
     *    local row with the remote one (keeping the local row id intact so
     *    nothing else references a stale id).
     *  - Otherwise the local copy wins and we skip the remote update.
     *
     * Templates with a new name are inserted as-is. Deletes are honored
     * unconditionally.
     */
    private suspend fun applyTemplateChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                taskTemplateDao.deleteTemplate(clientId)
                applied++
                continue
            }
            val data = change.data ?: continue
            val remoteName = data.optString("name") ?: continue
            val remoteUsage = data.optInt("usage_count") ?: 0
            val remoteUpdatedAt = data.optLong("updated_at")
                ?: change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()

            val remote = TaskTemplateEntity(
                id = clientId,
                name = remoteName,
                description = data.optString("description"),
                icon = data.optString("icon"),
                category = data.optString("category"),
                templateTitle = data.optString("template_title"),
                templateDescription = data.optString("template_description"),
                templatePriority = data.optInt("template_priority"),
                templateProjectId = data.optLong("template_project_id"),
                templateTagsJson = data.optString("template_tags_json"),
                templateRecurrenceJson = data.optString("template_recurrence_json"),
                templateDuration = data.optInt("template_duration"),
                templateSubtasksJson = data.optString("template_subtasks_json"),
                isBuiltIn = data.optBool("is_built_in") ?: false,
                usageCount = remoteUsage,
                lastUsedAt = data.optLong("last_used_at"),
                createdAt = data.optLong("created_at") ?: System.currentTimeMillis(),
                updatedAt = remoteUpdatedAt
            )

            // Merge-by-name: if there's already a local template with this
            // name (possibly at a different id, e.g., seeded built-in on a
            // new device), keep the higher-usage copy.
            val existingByName = taskTemplateDao.getTemplateByName(remoteName)
            if (existingByName != null) {
                if (remote.usageCount > existingByName.usageCount) {
                    taskTemplateDao.updateTemplate(
                        remote.copy(id = existingByName.id)
                    )
                    applied++
                }
                // Otherwise: local wins, no-op.
                continue
            }

            // No name collision — upsert as a normal sync.
            taskTemplateDao.insertTemplate(remote)
            applied++
        }
        return applied
    }

    private suspend fun applyHabitCompletionChanges(changes: List<SyncChange>): Int {
        var applied = 0
        for (change in changes) {
            val clientId = change.entityId
            if (change.operation == "delete") {
                // No delete-by-id on HabitCompletionDao; fall back to habit+date.
                val data = change.data ?: continue
                val habitId = data.optLong("habit_id") ?: continue
                val completedDate = data.optLong("completed_date") ?: continue
                habitCompletionDao.deleteByHabitAndDate(habitId, completedDate)
                applied++
                continue
            }
            val data = change.data ?: continue
            val habitId = data.optLong("habit_id") ?: continue
            val fallbackTimestamp = change.timestamp?.let { isoToMillisOrNull(it) }
                ?: System.currentTimeMillis()
            val completion = HabitCompletionEntity(
                id = clientId,
                habitId = habitId,
                completedDate = data.optLong("completed_date") ?: 0L,
                completedAt = data.optLong("completed_at") ?: fallbackTimestamp,
                notes = data.optString("notes")
            )
            habitCompletionDao.insert(completion)
            applied++
        }
        return applied
    }

    // endregion

    // region JsonObject helpers

    companion object {
        private const val TAG = "BackendSyncService"
    }
}

/**
 * Summary of a completed sync round returned by [BackendSyncService.fullSync].
 */
data class SyncSummary(
    val pushed: Int,
    val pulled: Int,
    val lastSyncAt: Long
)
