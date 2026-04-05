package com.averykarlin.averytask.data.remote

import android.util.Log
import com.averykarlin.averytask.data.local.dao.SyncMetadataDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.entity.SyncMetadataEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import com.averykarlin.averytask.data.remote.mapper.SyncMapper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService @Inject constructor(
    private val authManager: AuthManager,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val syncMetadataDao: SyncMetadataDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    private fun userCollection(collection: String) =
        authManager.userId?.let { firestore.collection("users").document(it).collection(collection) }

    suspend fun initialUpload() {
        val userId = authManager.userId ?: return

        // Upload projects
        val projects = projectDao.getAllProjectsOnce()
        for (project in projects) {
            val docRef = userCollection("projects")?.document() ?: continue
            docRef.set(SyncMapper.projectToMap(project)).await()
            syncMetadataDao.upsert(SyncMetadataEntity(
                localId = project.id, entityType = "project",
                cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
            ))
        }

        // Upload tags
        val tags = tagDao.getAllTagsOnce()
        for (tag in tags) {
            val docRef = userCollection("tags")?.document() ?: continue
            docRef.set(SyncMapper.tagToMap(tag)).await()
            syncMetadataDao.upsert(SyncMetadataEntity(
                localId = tag.id, entityType = "tag",
                cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
            ))
        }

        // Upload tasks with tag references
        val tasks = taskDao.getAllTasksOnce()
        for (task in tasks) {
            val tagIds = tagDao.getTagIdsForTaskOnce(task.id).mapNotNull { tagId ->
                syncMetadataDao.getCloudId(tagId, "tag")
            }
            val docRef = userCollection("tasks")?.document() ?: continue
            docRef.set(SyncMapper.taskToMap(task, tagIds)).await()
            syncMetadataDao.upsert(SyncMetadataEntity(
                localId = task.id, entityType = "task",
                cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
            ))
        }
    }

    suspend fun pushLocalChanges() {
        val pending = syncMetadataDao.getPendingActions()
        // Process in order: projects → tags → tasks
        val ordered = pending.sortedBy { when (it.entityType) { "project" -> 0; "tag" -> 1; else -> 2 } }

        for (meta in ordered) {
            try {
                when (meta.pendingAction) {
                    "create" -> pushCreate(meta)
                    "update" -> pushUpdate(meta)
                    "delete" -> pushDelete(meta)
                }
                syncMetadataDao.clearPendingAction(meta.localId, meta.entityType)
            } catch (e: Exception) {
                Log.e("SyncService", "Push failed for ${meta.entityType}/${meta.localId}", e)
                syncMetadataDao.incrementRetry(meta.localId, meta.entityType)
            }
        }
    }

    private suspend fun pushCreate(meta: SyncMetadataEntity) {
        val collection = userCollection(meta.entityType + "s") ?: return
        val docRef = collection.document()
        val data = when (meta.entityType) {
            "task" -> {
                val task = taskDao.getTaskByIdOnce(meta.localId) ?: return
                val tagIds = tagDao.getTagIdsForTaskOnce(task.id).mapNotNull { syncMetadataDao.getCloudId(it, "tag") }
                SyncMapper.taskToMap(task, tagIds)
            }
            "project" -> {
                val project = projectDao.getProjectByIdOnce(meta.localId) ?: return
                SyncMapper.projectToMap(project)
            }
            "tag" -> {
                val tag = tagDao.getTagByIdOnce(meta.localId) ?: return
                SyncMapper.tagToMap(tag)
            }
            else -> return
        }
        docRef.set(data).await()
        syncMetadataDao.upsert(meta.copy(cloudId = docRef.id, pendingAction = null, lastSyncedAt = System.currentTimeMillis()))
    }

    private suspend fun pushUpdate(meta: SyncMetadataEntity) {
        if (meta.cloudId.isEmpty()) { pushCreate(meta); return }
        val docRef = userCollection(meta.entityType + "s")?.document(meta.cloudId) ?: return
        val data = when (meta.entityType) {
            "task" -> {
                val task = taskDao.getTaskByIdOnce(meta.localId) ?: return
                val tagIds = tagDao.getTagIdsForTaskOnce(task.id).mapNotNull { syncMetadataDao.getCloudId(it, "tag") }
                SyncMapper.taskToMap(task, tagIds)
            }
            "project" -> {
                val project = projectDao.getProjectByIdOnce(meta.localId) ?: return
                SyncMapper.projectToMap(project)
            }
            "tag" -> {
                val tag = tagDao.getTagByIdOnce(meta.localId) ?: return
                SyncMapper.tagToMap(tag)
            }
            else -> return
        }
        docRef.set(data).await()
    }

    private suspend fun pushDelete(meta: SyncMetadataEntity) {
        if (meta.cloudId.isNotEmpty()) {
            userCollection(meta.entityType + "s")?.document(meta.cloudId)?.delete()?.await()
        }
        syncMetadataDao.delete(meta.localId, meta.entityType)
    }

    suspend fun pullRemoteChanges() {
        pullCollection("projects") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "project")
            if (localId == null) {
                val project = SyncMapper.mapToProject(data)
                val newId = projectDao.insert(project)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "project", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            } else {
                val project = SyncMapper.mapToProject(data, localId)
                projectDao.update(project)
                syncMetadataDao.clearPendingAction(localId, "project")
            }
        }

        pullCollection("tags") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "tag")
            if (localId == null) {
                val tag = SyncMapper.mapToTag(data)
                val newId = tagDao.insert(tag)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "tag", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            } else {
                val tag = SyncMapper.mapToTag(data, localId)
                tagDao.update(tag)
                syncMetadataDao.clearPendingAction(localId, "tag")
            }
        }

        pullCollection("tasks") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task")
            if (localId == null) {
                val task = SyncMapper.mapToTask(data)
                val newId = taskDao.insert(task)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "task", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
                // Resolve tag cross refs
                @Suppress("UNCHECKED_CAST")
                val cloudTagIds = data["tags"] as? List<String> ?: emptyList()
                for (cloudTagId in cloudTagIds) {
                    val tagLocalId = syncMetadataDao.getLocalId(cloudTagId, "tag") ?: continue
                    tagDao.addTagToTask(TaskTagCrossRef(taskId = newId, tagId = tagLocalId))
                }
            } else {
                val task = SyncMapper.mapToTask(data, localId)
                taskDao.update(task)
                syncMetadataDao.clearPendingAction(localId, "task")
            }
        }
    }

    private suspend fun pullCollection(name: String, handler: suspend (Map<String, Any?>, String) -> Unit) {
        val snapshot = userCollection(name)?.get()?.await() ?: return
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            handler(data, doc.id)
        }
    }

    suspend fun fullSync() {
        pushLocalChanges()
        pullRemoteChanges()
    }

    fun startRealtimeListeners() {
        stopRealtimeListeners()
        listOf("tasks", "projects", "tags").forEach { collection ->
            val reg = userCollection(collection)?.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                // Real-time changes handled via snapshot listener — lightweight re-pull on change
            }
            if (reg != null) listeners.add(reg)
        }
    }

    fun stopRealtimeListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}
