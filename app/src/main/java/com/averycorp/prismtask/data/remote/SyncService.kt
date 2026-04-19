package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.MilestoneDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.remote.sync.SyncStateRepository
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService
@Inject
constructor(
    private val authManager: AuthManager,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val habitLogDao: HabitLogDao,
    private val taskTemplateDao: TaskTemplateDao,
    private val milestoneDao: MilestoneDao,
    private val proFeatureGate: ProFeatureGate,
    private val logger: PrismSyncLogger,
    private val syncStateRepository: SyncStateRepository
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var isSyncing = false

    private fun userCollection(collection: String) =
        authManager.userId?.let { firestore.collection("users").document(it).collection(collection) }

    suspend fun initialUpload() {
        val userId = authManager.userId ?: return

        val projects = projectDao.getAllProjectsOnce()
        logger.debug("upload.projects", status = "begin", detail = "count=${projects.size}")
        for (project in projects) {
            try {
                val docRef = userCollection("projects")?.document() ?: continue
                docRef.set(SyncMapper.projectToMap(project)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = project.id,
                        entityType = "project",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.project",
                    entity = "project",
                    id = project.id.toString(),
                    detail = project.name,
                    throwable = e
                )
            }
        }

        // Upload milestones (v1.4.0 Projects Phase 5). Must come AFTER the
        // projects block so the cloud IDs for each project are registered
        // in sync_metadata and we can attach the milestone to its parent.
        logger.debug("upload.milestones", status = "begin")
        for (project in projects) {
            val projectCloudId = syncMetadataDao.getCloudId(project.id, "project") ?: continue
            val milestones = milestoneDao.getMilestonesOnce(project.id)
            for (milestone in milestones) {
                try {
                    val docRef = userCollection("milestones")?.document() ?: continue
                    docRef.set(SyncMapper.milestoneToMap(milestone, projectCloudId)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = milestone.id,
                            entityType = "milestone",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.milestone",
                        entity = "milestone",
                        id = milestone.id.toString(),
                        detail = milestone.title,
                        throwable = e
                    )
                }
            }
        }

        val tags = tagDao.getAllTagsOnce()
        logger.debug("upload.tags", status = "begin", detail = "count=${tags.size}")
        for (tag in tags) {
            try {
                val docRef = userCollection("tags")?.document() ?: continue
                docRef.set(SyncMapper.tagToMap(tag)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = tag.id,
                        entityType = "tag",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.tag",
                    entity = "tag",
                    id = tag.id.toString(),
                    detail = tag.name,
                    throwable = e
                )
            }
        }

        val habits = habitDao.getActiveHabitsOnce()
        logger.debug("upload.habits", status = "begin", detail = "count=${habits.size}")
        for (habit in habits) {
            try {
                val docRef = userCollection("habits")?.document() ?: continue
                docRef.set(SyncMapper.habitToMap(habit)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = habit.id,
                        entityType = "habit",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.habit",
                    entity = "habit",
                    id = habit.id.toString(),
                    detail = habit.name,
                    throwable = e
                )
            }
        }

        logger.debug("upload.habit_completions", status = "begin")
        for (habit in habits) {
            val completions = habitCompletionDao.getCompletionsForHabitOnce(habit.id)
            val habitCloudId = syncMetadataDao.getCloudId(habit.id, "habit") ?: continue
            for (completion in completions) {
                try {
                    val docRef = userCollection("habit_completions")?.document() ?: continue
                    docRef.set(SyncMapper.habitCompletionToMap(completion, habitCloudId)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = completion.id,
                            entityType = "habit_completion",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.habit_completion",
                        entity = "habit_completion",
                        id = completion.id.toString(),
                        throwable = e
                    )
                }
            }
        }

        logger.debug("upload.habit_logs", status = "begin")
        for (habit in habits) {
            val logs = habitLogDao.getAllLogsOnce().filter { it.habitId == habit.id }
            val habitCloudId = syncMetadataDao.getCloudId(habit.id, "habit") ?: continue
            for (log in logs) {
                try {
                    val docRef = userCollection("habit_logs")?.document() ?: continue
                    docRef.set(SyncMapper.habitLogToMap(log, habitCloudId)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = log.id,
                            entityType = "habit_log",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.habit_log",
                        entity = "habit_log",
                        id = log.id.toString(),
                        throwable = e
                    )
                }
            }
        }

        val tasks = taskDao.getAllTasksOnce()
        logger.debug("upload.tasks", status = "begin", detail = "count=${tasks.size}")
        for (task in tasks) {
            try {
                val tagIds = tagDao.getTagIdsForTaskOnce(task.id).mapNotNull { tagId ->
                    syncMetadataDao.getCloudId(tagId, "tag")
                }
                val docRef = userCollection("tasks")?.document() ?: continue
                docRef.set(SyncMapper.taskToMap(task, tagIds)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = task.id,
                        entityType = "task",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.task",
                    entity = "task",
                    id = task.id.toString(),
                    detail = task.title,
                    throwable = e
                )
            }
        }

        val templates = taskTemplateDao.getAllTemplatesOnce()
        logger.debug("upload.task_templates", status = "begin", detail = "count=${templates.size}")
        for (template in templates) {
            try {
                val docRef = userCollection("task_templates")?.document() ?: continue
                docRef.set(SyncMapper.taskTemplateToMap(template)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = template.id,
                        entityType = "task_template",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.task_template",
                    entity = "task_template",
                    id = template.id.toString(),
                    detail = template.name,
                    throwable = e
                )
            }
        }
    }

    fun launchInitialUpload() {
        scope.launch {
            val start = System.currentTimeMillis()
            try {
                initialUpload()
                logger.info(
                    operation = "upload.initial",
                    status = "success",
                    durationMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.initial",
                    durationMs = System.currentTimeMillis() - start,
                    throwable = e
                )
            }
        }
    }

    /**
     * Returns the number of pending operations processed (success + failure).
     * Callers use this to populate the "pushed=N" detail in sync completion
     * logs so we can see partial-push ratios at a glance.
     */
    suspend fun pushLocalChanges(): Int {
        val pending = syncMetadataDao.getPendingActions()
        // Process in order: projects → tags → tasks
        val ordered = pending.sortedBy {
            when (it.entityType) {
                "project" -> 0
                "tag" -> 1
                else -> 2
            }
        }

        var successCount = 0
        var failureCount = 0
        for (meta in ordered) {
            val start = System.currentTimeMillis()
            try {
                when (meta.pendingAction) {
                    "create" -> pushCreate(meta)
                    "update" -> pushUpdate(meta)
                    "delete" -> pushDelete(meta)
                }
                syncMetadataDao.clearPendingAction(meta.localId, meta.entityType)
                successCount++
                logger.debug(
                    operation = "push.${meta.pendingAction}",
                    entity = meta.entityType,
                    id = meta.localId.toString(),
                    status = "success",
                    durationMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                failureCount++
                logger.error(
                    operation = "push.${meta.pendingAction ?: "unknown"}",
                    entity = meta.entityType,
                    id = meta.localId.toString(),
                    durationMs = System.currentTimeMillis() - start,
                    detail = "retry=${meta.retryCount}",
                    throwable = e
                )
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
                syncMetadataDao.incrementRetry(meta.localId, meta.entityType)
            }
        }
        if (ordered.isNotEmpty()) {
            logger.info(
                operation = "push.summary",
                status = if (failureCount == 0) "success" else "partial",
                detail = "success=$successCount failed=$failureCount"
            )
        }
        return successCount + failureCount
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
        if (meta.cloudId.isEmpty()) {
            pushCreate(meta)
            return
        }
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

    /**
     * Returns the number of remote documents applied locally across all
     * collections.
     */
    suspend fun pullRemoteChanges(): Int {
        var applied = 0
        applied += pullCollection("projects") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "project")
            if (localId == null) {
                val project = SyncMapper.mapToProject(data)
                val newId = projectDao.insert(project)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "project",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val project = SyncMapper.mapToProject(data, localId)
                projectDao.update(project)
                syncMetadataDao.clearPendingAction(localId, "project")
            }
        }

        applied += pullCollection("tags") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "tag")
            if (localId == null) {
                val tag = SyncMapper.mapToTag(data)
                val newId = tagDao.insert(tag)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "tag",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val tag = SyncMapper.mapToTag(data, localId)
                tagDao.update(tag)
                syncMetadataDao.clearPendingAction(localId, "tag")
            }
        }

        applied += pullCollection("tasks") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task")
            if (localId == null) {
                val task = SyncMapper.mapToTask(data)
                val newId = taskDao.insert(task)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "task",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
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

        applied += pullCollection("habits") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit")
            if (localId == null) {
                val habit = SyncMapper.mapToHabit(data)
                val newId = habitDao.insert(habit)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "habit",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val habit = SyncMapper.mapToHabit(data, localId)
                habitDao.update(habit)
                syncMetadataDao.clearPendingAction(localId, "habit")
            }
        }

        applied += pullCollection("habit_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_completion")
            val habitCloudId = data["habitCloudId"] as? String ?: return@pullCollection
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit") ?: return@pullCollection
            if (localId == null) {
                val completion = SyncMapper.mapToHabitCompletion(data, habitLocalId = habitLocalId)
                val newId = habitCompletionDao.insert(completion)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "habit_completion",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        applied += pullCollection("habit_logs") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_log")
            val habitCloudId = data["habitCloudId"] as? String ?: return@pullCollection
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit") ?: return@pullCollection
            if (localId == null) {
                val log = SyncMapper.mapToHabitLog(data, habitLocalId = habitLocalId)
                val newId = habitLogDao.insertLog(log)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "habit_log",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        applied += pullCollection("task_templates") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task_template")
            if (localId == null) {
                val template = SyncMapper.mapToTaskTemplate(data)
                val newId = taskTemplateDao.insertTemplate(template)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "task_template",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val template = SyncMapper.mapToTaskTemplate(data, localId)
                taskTemplateDao.updateTemplate(template)
                syncMetadataDao.clearPendingAction(localId, "task_template")
            }
        }
        return applied
    }

    private suspend fun pullCollection(
        name: String,
        handler: suspend (Map<String, Any?>, String) -> Unit
    ): Int {
        val snapshot = userCollection(name)?.get()?.await() ?: return 0
        var applied = 0
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            try {
                handler(data, doc.id)
                applied++
            } catch (e: Exception) {
                logger.error(
                    operation = "pull.apply",
                    entity = name,
                    id = doc.id,
                    throwable = e
                )
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
        }
        return applied
    }

    suspend fun fullSync(trigger: String = "manual") {
        if (isSyncing) {
            logger.debug(
                operation = "sync.skipped",
                entity = "service",
                id = "firebase",
                status = "already_running",
                detail = "trigger=$trigger"
            )
            return
        }
        isSyncing = true
        val start = System.currentTimeMillis()
        syncStateRepository.markSyncStarted(source = SOURCE_FIREBASE, trigger = trigger)
        var pushed = 0
        var pulled = 0
        try {
            pushed = pushLocalChanges()
            pulled = pullRemoteChanges()
            syncStateRepository.markSyncCompleted(
                source = SOURCE_FIREBASE,
                success = true,
                durationMs = System.currentTimeMillis() - start,
                pushed = pushed,
                pulled = pulled
            )
        } catch (e: Exception) {
            syncStateRepository.markSyncCompleted(
                source = SOURCE_FIREBASE,
                success = false,
                durationMs = System.currentTimeMillis() - start,
                pushed = pushed,
                pulled = pulled,
                throwable = e
            )
            throw e
        } finally {
            isSyncing = false
        }
    }

    fun startAutoSync() {
        if (authManager.userId == null) return
        startRealtimeListeners()
        scope.launch {
            try {
                fullSync(trigger = "startAutoSync")
            } catch (e: Exception) {
                // Error already logged by fullSync / markSyncCompleted.
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
        }
        scope.launch {
            syncMetadataDao.observePending()
                .debounce(500L)
                .collect { entries ->
                    if (entries.isEmpty()) {
                        logger.debug(operation = "reactive.push.skipped", detail = "reason=queue_empty")
                        return@collect
                    }
                    if (isSyncing) {
                        logger.debug(operation = "reactive.push.skipped", detail = "reason=already_syncing")
                        return@collect
                    }
                    if (!syncStateRepository.isOnline.value) {
                        logger.debug(operation = "reactive.push.skipped", detail = "reason=offline")
                        return@collect
                    }
                    if (authManager.userId == null) {
                        logger.debug(operation = "reactive.push.skipped", detail = "reason=not_signed_in")
                        return@collect
                    }
                    isSyncing = true
                    logger.info(operation = "reactive.push.triggered", detail = "pendingCount=${entries.size}")
                    try {
                        pushLocalChanges()
                        logger.info(operation = "reactive.push.completed", detail = "pendingCount=${entries.size}")
                    } catch (e: Exception) {
                        logger.error(operation = "reactive.push.error", throwable = e)
                        try {
                            com.google.firebase.crashlytics.FirebaseCrashlytics
                                .getInstance()
                                .recordException(e)
                        } catch (_: Exception) {
                        }
                    } finally {
                        isSyncing = false
                    }
                }
        }
    }

    fun startRealtimeListeners() {
        stopRealtimeListeners()
        listOf("tasks", "projects", "tags", "habits", "habit_completions", "task_templates").forEach { collection ->
            val reg = userCollection(collection)?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logger.warn(
                        operation = "listener.error",
                        entity = "collection",
                        id = collection,
                        throwable = error
                    )
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                if (snapshot.documentChanges.isEmpty()) return@addSnapshotListener
                syncStateRepository.recordListenerSnapshot(collection, snapshot.documentChanges.size)
                scope.launch {
                    if (isSyncing) return@launch
                    val start = System.currentTimeMillis()
                    syncStateRepository.markSyncStarted(source = SOURCE_FIREBASE, trigger = "listener:$collection")
                    try {
                        val applied = pullRemoteChanges()
                        syncStateRepository.markSyncCompleted(
                            source = SOURCE_FIREBASE,
                            success = true,
                            durationMs = System.currentTimeMillis() - start,
                            pulled = applied
                        )
                    } catch (e: Exception) {
                        syncStateRepository.markSyncCompleted(
                            source = SOURCE_FIREBASE,
                            success = false,
                            durationMs = System.currentTimeMillis() - start,
                            throwable = e
                        )
                        try {
                            com.google.firebase.crashlytics.FirebaseCrashlytics
                                .getInstance()
                                .recordException(e)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            if (reg != null) listeners.add(reg)
        }
        syncStateRepository.markListenersActive(listeners.isNotEmpty())
    }

    fun stopRealtimeListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
        syncStateRepository.markListenersActive(false)
    }

    companion object {
        const val SOURCE_FIREBASE: String = "firebase"
    }
}
