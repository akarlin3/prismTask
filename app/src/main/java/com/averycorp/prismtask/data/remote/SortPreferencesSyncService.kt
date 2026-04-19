package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs sort preferences to/from Firestore at /users/{uid}/settings/sort_preferences.
 *
 * Push: observes [SortPreferences.flowOfChanges], debounces 500 ms, writes a flat
 * map of all sort keys. Per-project keys are translated from local Room IDs to
 * Firestore cloud IDs at the boundary.
 *
 * Pull: Firestore snapshot listener applies remote prefs when remote updated_at is
 * newer than local (last-write-wins). Per-project cloud IDs are translated back to
 * local IDs via [SyncMetadataDao].
 *
 * Catchup: when a new project is pulled and its cloud ID becomes available, call
 * [notifyProjectSynced] so any previously-unresolvable sort_project_cloud_* key in
 * the last remote snapshot is applied retroactively.
 */
@Singleton
class SortPreferencesSyncService @Inject constructor(
    private val sortPreferences: SortPreferences,
    private val authManager: AuthManager,
    private val syncMetadataDao: SyncMetadataDao,
    private val logger: PrismSyncLogger
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var settingsListener: ListenerRegistration? = null

    // Cached last remote snapshot — used by notifyProjectSynced for catchup
    @Volatile private var lastRemoteData: Map<String, Any>? = null

    private fun settingsDocRef() =
        authManager.userId?.let {
            firestore.collection("users").document(it)
                .collection("settings").document("sort_preferences")
        }

    /** Start the reactive push observer. Safe to call unconditionally; no-ops when signed out. */
    fun startPushObserver() {
        scope.launch {
            sortPreferences.flowOfChanges
                .debounce(500L)
                .collect {
                    if (authManager.userId == null) return@collect
                    val updatedAt = sortPreferences.getPrefsUpdatedAt()
                    val lastSynced = sortPreferences.getLastSyncedAt()
                    if (updatedAt <= lastSynced) return@collect
                    pushNow()
                }
        }
    }

    /** Called from AuthViewModel after a successful sign-in. */
    fun startAfterSignIn() {
        startPullListener()
        scope.launch { pushNow(force = true) }
    }

    /** Called from AuthViewModel on sign-out. Does NOT clear local DataStore. */
    fun stopAfterSignOut() {
        stopPullListener()
        lastRemoteData = null
    }

    private fun startPullListener() {
        stopPullListener()
        val docRef = settingsDocRef() ?: return
        settingsListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logger.warn(operation = "[PrismPrefs] listener.error", throwable = error)
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            lastRemoteData = data
            scope.launch { applyRemoteData(data) }
        }
    }

    private fun stopPullListener() {
        settingsListener?.remove()
        settingsListener = null
    }

    /**
     * Called from [com.averycorp.prismtask.data.remote.SyncService] immediately
     * after a new project row is inserted into sync_metadata with [projectCloudId].
     * Tries to resolve any sort_project_cloud_<cloudId> key that was stashed in
     * [lastRemoteData] but couldn't be translated before this project synced.
     */
    fun notifyProjectSynced(projectCloudId: String) {
        val remoteData = lastRemoteData ?: return
        scope.launch {
            val localId = syncMetadataDao.getLocalId(projectCloudId, "project") ?: return@launch

            val keysToApply = mutableMapOf<String, String>()
            val modeValue = remoteData["sort_project_cloud_$projectCloudId"] as? String
            val directionValue = remoteData["sort_direction_project_cloud_$projectCloudId"] as? String

            if (modeValue != null) keysToApply["sort_project_$localId"] = modeValue
            if (directionValue != null) keysToApply["sort_direction_project_$localId"] = directionValue

            if (keysToApply.isEmpty()) return@launch

            val updatedAt = (remoteData["updated_at"] as? Number)?.toLong()
                ?: System.currentTimeMillis()
            sortPreferences.applyRemoteSnapshot(keysToApply, updatedAt)
            logger.info(
                operation = "[PrismPrefs] catchup",
                detail = "newProject=$projectCloudId | rekeyedPrefs=${keysToApply.size}"
            )
        }
    }

    private suspend fun applyRemoteData(data: Map<String, Any>) {
        val remoteUpdatedAt = (data["updated_at"] as? Number)?.toLong() ?: return
        val localUpdatedAt = sortPreferences.getPrefsUpdatedAt()
        if (remoteUpdatedAt <= localUpdatedAt) return

        val sortKeys = mutableMapOf<String, String>()
        var appliedCount = 0

        for ((key, value) in data) {
            if (key == "updated_at") continue
            val stringValue = value as? String ?: continue
            when {
                key.startsWith("sort_direction_project_cloud_") -> {
                    val cloudId = key.removePrefix("sort_direction_project_cloud_")
                    val localId = syncMetadataDao.getLocalId(cloudId, "project")
                    if (localId != null) {
                        sortKeys["sort_direction_project_$localId"] = stringValue
                        appliedCount++
                    }
                    // If localId is null, project not yet synced; notifyProjectSynced() will
                    // resolve it once SyncService inserts the project into sync_metadata.
                }
                key.startsWith("sort_project_cloud_") -> {
                    val cloudId = key.removePrefix("sort_project_cloud_")
                    val localId = syncMetadataDao.getLocalId(cloudId, "project")
                    if (localId != null) {
                        sortKeys["sort_project_$localId"] = stringValue
                        appliedCount++
                    }
                }
                key.startsWith("sort_") -> {
                    sortKeys[key] = stringValue
                    appliedCount++
                }
            }
        }

        sortPreferences.applyRemoteSnapshot(sortKeys, remoteUpdatedAt)
        logger.info(
            operation = "[PrismPrefs] pull",
            detail = "applied=$appliedCount | status=success"
        )
    }

    private suspend fun pushNow(force: Boolean = false) {
        val uid = authManager.userId ?: return

        if (!force) {
            val updatedAt = sortPreferences.getPrefsUpdatedAt()
            val lastSynced = sortPreferences.getLastSyncedAt()
            if (updatedAt <= lastSynced) return
        }

        val snapshot = sortPreferences.snapshot()
        val firestoreData = mutableMapOf<String, Any>()

        for ((key, value) in snapshot) {
            when {
                // Direction check must precede mode check — "sort_direction_project_" ⊂ "sort_project_"?
                // No: "sort_direction_project_" does NOT start with "sort_project_", but be explicit anyway.
                key.startsWith("sort_direction_project_") -> {
                    val localId = key.removePrefix("sort_direction_project_").toLongOrNull() ?: continue
                    val cloudId = syncMetadataDao.getCloudId(localId, "project") ?: continue
                    firestoreData["sort_direction_project_cloud_$cloudId"] = value
                }
                key.startsWith("sort_project_") -> {
                    val localId = key.removePrefix("sort_project_").toLongOrNull() ?: continue
                    val cloudId = syncMetadataDao.getCloudId(localId, "project") ?: continue
                    firestoreData["sort_project_cloud_$cloudId"] = value
                }
                else -> firestoreData[key] = value
            }
        }

        val now = System.currentTimeMillis()
        firestoreData["updated_at"] = now

        val docRef = firestore.collection("users").document(uid)
            .collection("settings").document("sort_preferences")
        try {
            docRef.set(firestoreData, SetOptions.merge()).await()
            sortPreferences.setLastSyncedAt(now)
            logger.info(
                operation = "[PrismPrefs] push",
                detail = "keys=${firestoreData.size} | status=success"
            )
        } catch (e: Exception) {
            logger.error(
                operation = "[PrismPrefs] push",
                detail = "status=failed",
                throwable = e
            )
        }
    }
}
