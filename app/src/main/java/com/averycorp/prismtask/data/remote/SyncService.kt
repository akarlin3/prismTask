package com.averycorp.prismtask.data.remote

import android.util.Log
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService @Inject constructor(
    private val authManager: AuthManager,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val habitLogDao: HabitLogDao,
    private val taskTemplateDao: TaskTemplateDao,
    private val proFeatureGate: ProFeatureGate
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isSyncing = false

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

        // Upload habits
        val habits = habitDao.getActiveHabitsOnce()
        for (habit in habits) {
            val docRef = userCollection("habits")?.document() ?: continue
            docRef.set(SyncMapper.habitToMap(habit)).await()
            syncMetadataDao.upsert(SyncMetadataEntity(
                localId = habit.id, entityType = "habit",
                cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
            ))
        }

        // Upload habit completions
        for (habit in habits) {
            val completions = habitCompletionDao.getCompletionsForHabitOnce(habit.id)
            val habitCloudId = syncMetadataDao.getCloudId(habit.id, "habit") ?: continue
            for (completion in completions) {
                val docRef = userCollection("habit_completions")?.document() ?: continue
                docRef.set(SyncMapper.habitCompletionToMap(completion, habitCloudId)).await()
                syncMetadataDao.upsert(SyncMetadataEntity(
                    localId = completion.id, entityType = "habit_completion",
                    cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
                ))
            }
        }

        // Upload habit logs
        for (habit in habits) {
            val logs = habitLogDao.getAllLogsOnce().filter { it.habitId == habit.id }
            val habitCloudId = syncMetadataDao.getCloudId(habit.id, "habit") ?: continue
            for (log in logs) {
                val docRef = userCollection("habit_logs")?.document() ?: continue
                docRef.set(SyncMapper.habitLogToMap(log, habitCloudId)).await()
                syncMetadataDao.upsert(SyncMetadataEntity(
                    localId = log.id, entityType = "habit_log",
                    cloudId = docRef.id, lastSyncedAt = System.currentTimeMillis()
                ))
            }
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

        // Upload task templates
        val templates = taskTemplateDao.getAllTemplatesOnce()
        for (template in templates) {
            val docRef = userCollection("task_templates")?.document() ?: continue
            docRef.set(SyncMapper.taskTemplateToMap(template)).await()
            syncMetadataDao.upsert(SyncMetadataEntity(
                localId = template.id, entityType = "task_template",
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
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
                syncMetadataDao.incrementRetry(meta.localId, meta.entityType)
            }
        }
    }

    private fun collectionNameFor(entityType: String): String = when (entityType) {
        "habit_completion" -> "habit_completions"
        "habit_log" -> "habit_logs"
        "task_template" -> "task_templates"
        else -> entityType + "s"
    }

    private suspend fun pushCreate(meta: SyncMetadataEntity) {
        val collection = userCollection(collectionNameFor(meta.entityType)) ?: return
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
            "habit" -> {
                val habit = habitDao.getHabitByIdOnce(meta.localId) ?: return
                SyncMapper.habitToMap(habit)
            }
            "habit_completion" -> {
                val completion = habitCompletionDao.getCompletionsForHabitOnce(meta.localId).firstOrNull() ?: return
                val habitCloudId = syncMetadataDao.getCloudId(completion.habitId, "habit") ?: return
                SyncMapper.habitCompletionToMap(completion, habitCloudId)
            }
            "habit_log" -> {
                val logs = habitLogDao.getAllLogsOnce()
                val log = logs.find { it.id == meta.localId } ?: return
                val habitCloudId = syncMetadataDao.getCloudId(log.habitId, "habit") ?: return
                SyncMapper.habitLogToMap(log, habitCloudId)
            }
            "task_template" -> {
                val template = taskTemplateDao.getTemplateById(meta.localId) ?: return
                SyncMapper.taskTemplateToMap(template)
            }
            else -> return
        }
        docRef.set(data).await()
        syncMetadataDao.upsert(meta.copy(cloudId = docRef.id, pendingAction = null, lastSyncedAt = System.currentTimeMillis()))
    }

    private suspend fun pushUpdate(meta: SyncMetadataEntity) {
        if (meta.cloudId.isEmpty()) { pushCreate(meta); return }
        val docRef = userCollection(collectionNameFor(meta.entityType))?.document(meta.cloudId) ?: return
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
            "habit" -> {
                val habit = habitDao.getHabitByIdOnce(meta.localId) ?: return
                SyncMapper.habitToMap(habit)
            }
            "task_template" -> {
                val template = taskTemplateDao.getTemplateById(meta.localId) ?: return
                SyncMapper.taskTemplateToMap(template)
            }
            else -> return
        }
        docRef.set(data).await()
    }

    private suspend fun pushDelete(meta: SyncMetadataEntity) {
        if (meta.cloudId.isNotEmpty()) {
            userCollection(collectionNameFor(meta.entityType))?.document(meta.cloudId)?.delete()?.await()
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

        // Pull habits
        pullCollection("habits") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit")
            if (localId == null) {
                val habit = SyncMapper.mapToHabit(data)
                val newId = habitDao.insert(habit)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "habit", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            } else {
                val habit = SyncMapper.mapToHabit(data, localId)
                habitDao.update(habit)
                syncMetadataDao.clearPendingAction(localId, "habit")
            }
        }

        // Pull habit completions
        pullCollection("habit_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_completion")
            val habitCloudId = data["habitCloudId"] as? String ?: return@pullCollection
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit") ?: return@pullCollection
            if (localId == null) {
                val completion = SyncMapper.mapToHabitCompletion(data, habitLocalId = habitLocalId)
                val newId = habitCompletionDao.insert(completion)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "habit_completion", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            }
        }

        // Pull habit logs
        pullCollection("habit_logs") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_log")
            val habitCloudId = data["habitCloudId"] as? String ?: return@pullCollection
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit") ?: return@pullCollection
            if (localId == null) {
                val log = SyncMapper.mapToHabitLog(data, habitLocalId = habitLocalId)
                val newId = habitLogDao.insertLog(log)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "habit_log", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            }
        }

        // Pull task templates
        pullCollection("task_templates") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task_template")
            if (localId == null) {
                val template = SyncMapper.mapToTaskTemplate(data)
                val newId = taskTemplateDao.insertTemplate(template)
                syncMetadataDao.upsert(SyncMetadataEntity(localId = newId, entityType = "task_template", cloudId = cloudId, lastSyncedAt = System.currentTimeMillis()))
            } else {
                val template = SyncMapper.mapToTaskTemplate(data, localId)
                taskTemplateDao.updateTemplate(template)
                syncMetadataDao.clearPendingAction(localId, "task_template")
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
        if (!proFeatureGate.hasAccess(ProFeatureGate.CLOUD_SYNC)) return
        if (isSyncing) return
        isSyncing = true
        try {
            pushLocalChanges()
            pullRemoteChanges()
        } finally {
            isSyncing = false
        }
    }

    fun startAutoSync() {
        if (authManager.userId == null) return
        if (!proFeatureGate.hasAccess(ProFeatureGate.CLOUD_SYNC)) return
        startRealtimeListeners()
        scope.launch {
            try { fullSync() } catch (e: Exception) {
                Log.e("SyncService", "Auto-sync failed", e)
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun startRealtimeListeners() {
        stopRealtimeListeners()
        listOf("tasks", "projects", "tags", "habits", "habit_completions", "task_templates").forEach { collection ->
            val reg = userCollection(collection)?.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                if (snapshot.documentChanges.isEmpty()) return@addSnapshotListener
                // Remote change detected — trigger a pull
                scope.launch {
                    if (isSyncing) return@launch
                    try {
                        pullRemoteChanges()
                    } catch (e: Exception) {
                        Log.e("SyncService", "Real-time pull failed", e)
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }
            if (reg != null) listeners.add(reg)
        }
    }

    fun stopRealtimeListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}
