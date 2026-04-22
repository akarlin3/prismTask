package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskCompletionDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heals the "local row with a cloud_id whose Firestore doc no longer
 * exists" state. This arises when a user's Firestore subcollection is
 * wiped out of band (manual delete, account reset, phase-3 Fix-D cleanup)
 * while local rows still carry the now-stale cloud_id. Without recovery,
 * those rows stay locally usable but never push back to Firestore — the
 * reactive push observer only fires on Room-tracked changes, and the
 * one-shot [SyncService.initialUpload] never re-runs.
 *
 * The healer runs once per [SyncService.fullSync], after
 * [SyncService.pullRemoteChanges] and before the built-in reconcilers,
 * so the post-pull Firestore snapshot is the most recent possible view.
 *
 * Scope: all syncable entity families regardless of which upload path
 * originally minted them. The v1.4 "new entity" families
 * (`self_care_steps`, `self_care_logs`, `courses`, `course_completions`,
 * `leisure_logs`) land via [SyncService.maybeRunEntityBackfill]; Tier-1
 * entities (`tasks`, `projects`, `tags`, `habits`, `habit_completions`,
 * `habit_logs`, `task_completions`, `task_templates`) land via
 * [SyncService.doInitialUpload]. Both paths are one-shot and share the
 * same "local rows with stale cloud_id after out-of-band wipe" failure
 * mode — so the healer covers both uniformly.
 *
 * Algorithm per family:
 *  1. Enumerate local rows with a non-blank `cloud_id`.
 *  2. Fetch the current Firestore subcollection's document IDs.
 *  3. For each local row whose `cloud_id` is not in the remote ID set,
 *     upsert a [SyncMetadataEntity] with `pendingAction = "update"`
 *     AND `cloudId = row.cloud_id`. The upsert deliberately **preserves**
 *     the existing cloud_id so that [SyncService.pushUpdate]'s
 *     `docRef(cloudId).set(data)` re-creates the Firestore doc at the
 *     same ID — other devices that saw the original ID will converge
 *     without duplicate-detection churn.
 *  4. The reactive-push observer picks up the new pending actions
 *     ~500ms later (`observePending().debounce(500L)`) and pushes.
 *
 * FK considerations for Tier-1 families:
 *  - `habit_completions` / `habit_logs` reference `habits` via cloudId
 *    embedded in their Firestore doc body (resolved via
 *    `syncMetadataDao.getCloudId(completion.habitId, "habit")` at push
 *    time). In the orphan scenario, the parent habit's `sync_metadata`
 *    row is present with its stale cloud_id, so the child's push
 *    resolves to that same stale id — which is exactly what we want:
 *    the re-pushed parent will be at that id, so the child's reference
 *    is correct.
 *  - `task_completions` same reasoning for its `taskId` / `projectId`
 *    references.
 *  - [SyncService.pushLocalChanges] sorts pending actions so
 *    `project` / `tag` push before everything else, and
 *    `task_completion` pushes last; the healer's enqueue order is
 *    irrelevant because that sort reorders at push time.
 *
 * The healer does not gate on a one-shot flag. It is cheap to run every
 * sync (one `.get()` per tracked collection, skipped for families with
 * no local rows carrying a cloud_id) and correct — non-orphans exit the
 * per-row branch immediately.
 *
 * Milestones are intentionally out of scope in this iteration: the
 * [com.averycorp.prismtask.data.local.dao.MilestoneDao] only exposes a
 * per-project getter, not a global scan. Adding a global scan query +
 * wiring is straightforward but can wait for the first user report of
 * a milestone orphan.
 */
@Singleton
class CloudIdOrphanHealer
@Inject
constructor(
    private val authManager: AuthManager,
    private val syncMetadataDao: SyncMetadataDao,
    private val selfCareDao: SelfCareDao,
    private val schoolworkDao: SchoolworkDao,
    private val leisureDao: LeisureDao,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val habitLogDao: HabitLogDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val taskTemplateDao: TaskTemplateDao,
    private val logger: PrismSyncLogger
) {
    /**
     * Function type for fetching the document IDs currently present in a
     * Firestore subcollection. Extracted as a parameter so tests can
     * inject a fake without mocking the Firestore SDK's fluent chain.
     * Returns null to signal a fetch error (logged + treated as "skip
     * this family, retry next sync").
     */
    fun interface RemoteIdFetcher {
        suspend fun fetch(collection: String): Set<String>?
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val defaultFetcher = RemoteIdFetcher { collection ->
        val userId = authManager.userId ?: return@RemoteIdFetcher null
        val coll = firestore.collection("users").document(userId).collection(collection)
        try {
            coll.get().await().documents.map { it.id }.toSet()
        } catch (e: Exception) {
            logger.error(
                operation = "healer.$collection",
                entity = collection,
                throwable = e
            )
            null
        }
    }

    suspend fun healOrphans(fetcher: RemoteIdFetcher = defaultFetcher) {
        if (authManager.userId == null) return

        // ── v1.4 "new entity" families ──
        healFamily("self_care_steps", "self_care_step", fetcher) {
            selfCareDao.getAllStepsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("self_care_logs", "self_care_log", fetcher) {
            selfCareDao.getAllLogsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("courses", "course", fetcher) {
            schoolworkDao.getAllCoursesOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("course_completions", "course_completion", fetcher) {
            schoolworkDao.getAllCompletionsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("leisure_logs", "leisure_log", fetcher) {
            leisureDao.getAllLogsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }

        // ── Tier-1 families (via doInitialUpload) ──
        healFamily("projects", "project", fetcher) {
            projectDao.getAllProjectsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("tags", "tag", fetcher) {
            tagDao.getAllTagsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("habits", "habit", fetcher) {
            habitDao.getAllHabitsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("habit_completions", "habit_completion", fetcher) {
            habitCompletionDao.getAllCompletionsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("habit_logs", "habit_log", fetcher) {
            habitLogDao.getAllLogsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("tasks", "task", fetcher) {
            taskDao.getAllTasksOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("task_completions", "task_completion", fetcher) {
            taskCompletionDao.getAllCompletionsOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
        healFamily("task_templates", "task_template", fetcher) {
            taskTemplateDao.getAllTemplatesOnce().mapNotNull { entity ->
                entity.cloudId?.takeIf { it.isNotBlank() }?.let { CloudIdRow(entity.id, it) }
            }
        }
    }

    private suspend fun healFamily(
        collection: String,
        entityType: String,
        fetcher: RemoteIdFetcher,
        localSource: suspend () -> List<CloudIdRow>
    ) {
        val candidates = localSource()
        if (candidates.isEmpty()) return

        val remoteIds = fetcher.fetch(collection) ?: return

        var orphaned = 0
        for (row in candidates) {
            if (row.cloudId in remoteIds) continue
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    localId = row.localId,
                    entityType = entityType,
                    cloudId = row.cloudId,
                    pendingAction = "update",
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            orphaned++
        }

        if (orphaned > 0) {
            logger.info(
                operation = "healer.$collection",
                status = "healed",
                detail = "checked=${candidates.size} orphaned=$orphaned"
            )
        } else {
            logger.debug(
                operation = "healer.$collection",
                status = "no_op",
                detail = "checked=${candidates.size}"
            )
        }
    }

    private data class CloudIdRow(val localId: Long, val cloudId: String)
}
