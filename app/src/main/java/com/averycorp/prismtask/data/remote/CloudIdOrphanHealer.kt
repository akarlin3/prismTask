package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
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
 * Per spec `docs/SPEC_SELF_CARE_STEPS_SYNC_PIPELINE.md`, the scope is
 * limited to the "new entity" families added in v1.4 that share the
 * same one-shot-upload fragility: `self_care_steps`, `self_care_logs`,
 * `courses`, `course_completions`, `leisure_logs`. Tier-1 entities
 * (tasks, projects, tags, habits) are deferred to a follow-up once
 * this has proven stable for a release — their upload path goes through
 * [SyncService.doInitialUpload] which has a slightly different guard
 * shape and more inbound foreign-key considerations.
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
 * The healer does not gate on a one-shot flag. It is cheap to run every
 * sync (one `.get()` per tracked collection) and correct — non-orphans
 * exit the per-row branch immediately. A family with empty local rows
 * short-circuits before any Firestore round trip.
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
