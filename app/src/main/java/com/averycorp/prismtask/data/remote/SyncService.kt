package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.MilestoneDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskCompletionDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.remote.sync.SyncStateRepository
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.averycorp.prismtask.util.DayBoundary
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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
    private val taskCompletionDao: TaskCompletionDao,
    private val proFeatureGate: ProFeatureGate,
    private val logger: PrismSyncLogger,
    private val syncStateRepository: SyncStateRepository,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val builtInHabitReconciler: BuiltInHabitReconciler
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
                val projectCloudId = task.projectId?.let { syncMetadataDao.getCloudId(it, "project") }
                val parentTaskCloudId = task.parentTaskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val sourceHabitCloudId = task.sourceHabitId?.let { syncMetadataDao.getCloudId(it, "habit") }
                val docRef = userCollection("tasks")?.document() ?: continue
                docRef.set(SyncMapper.taskToMap(task, tagIds, projectCloudId, parentTaskCloudId, sourceHabitCloudId)).await()
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

        // task_completions after tasks so task cloud IDs are available for FK serialization.
        val taskCompletions = taskCompletionDao.getAllCompletionsOnce()
        logger.debug("upload.task_completions", status = "begin", detail = "count=${taskCompletions.size}")
        for (completion in taskCompletions) {
            try {
                val taskCloudId = completion.taskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val projectCloudId = completion.projectId?.let { syncMetadataDao.getCloudId(it, "project") }
                val docRef = userCollection("task_completions")?.document() ?: continue
                docRef.set(SyncMapper.taskCompletionToMap(completion, taskCloudId, projectCloudId)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = completion.id,
                        entityType = "task_completion",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.task_completion",
                    entity = "task_completion",
                    id = completion.id.toString(),
                    throwable = e
                )
            }
        }

        val templates = taskTemplateDao.getAllTemplatesOnce()
        logger.debug("upload.task_templates", status = "begin", detail = "count=${templates.size}")
        for (template in templates) {
            try {
                val templateProjectCloudId = template.templateProjectId?.let { syncMetadataDao.getCloudId(it, "project") }
                val docRef = userCollection("task_templates")?.document() ?: continue
                docRef.set(SyncMapper.taskTemplateToMap(template, templateProjectCloudId)).await()
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
        // Process in order: projects → tags → everything else → task_completions
        // task_completions must go last because they reference task cloud IDs.
        val ordered = pending.sortedBy {
            when (it.entityType) {
                "project" -> 0
                "tag" -> 1
                "task_completion" -> 3
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
        "task_completion" -> "task_completions"
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
                val projectCloudId = task.projectId?.let { syncMetadataDao.getCloudId(it, "project") }
                val parentTaskCloudId = task.parentTaskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val sourceHabitCloudId = task.sourceHabitId?.let { syncMetadataDao.getCloudId(it, "habit") }
                SyncMapper.taskToMap(task, tagIds, projectCloudId, parentTaskCloudId, sourceHabitCloudId)
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
                val completion = habitCompletionDao.getAllCompletionsOnce().find { it.id == meta.localId }
                if (completion == null) {
                    logger.error(
                        operation = "push.create",
                        entity = "habit_completion",
                        id = meta.localId.toString(),
                        status = "error",
                        detail = "completion not found for localId=${meta.localId}"
                    )
                    return
                }
                val habitCloudId = syncMetadataDao.getCloudId(completion.habitId, "habit") ?: return
                SyncMapper.habitCompletionToMap(completion, habitCloudId)
            }
            "habit_log" -> {
                val logs = habitLogDao.getAllLogsOnce()
                val log = logs.find { it.id == meta.localId } ?: return
                val habitCloudId = syncMetadataDao.getCloudId(log.habitId, "habit") ?: return
                SyncMapper.habitLogToMap(log, habitCloudId)
            }
            "task_completion" -> {
                val completion = taskCompletionDao.getAllCompletionsOnce().find { it.id == meta.localId }
                    ?: return
                val taskCloudId = completion.taskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val projectCloudId = completion.projectId?.let { syncMetadataDao.getCloudId(it, "project") }
                SyncMapper.taskCompletionToMap(completion, taskCloudId, projectCloudId)
            }
            "task_template" -> {
                val template = taskTemplateDao.getTemplateById(meta.localId) ?: return
                val templateProjectCloudId = template.templateProjectId?.let { syncMetadataDao.getCloudId(it, "project") }
                SyncMapper.taskTemplateToMap(template, templateProjectCloudId)
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
                val projectCloudId = task.projectId?.let { syncMetadataDao.getCloudId(it, "project") }
                val parentTaskCloudId = task.parentTaskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val sourceHabitCloudId = task.sourceHabitId?.let { syncMetadataDao.getCloudId(it, "habit") }
                SyncMapper.taskToMap(task, tagIds, projectCloudId, parentTaskCloudId, sourceHabitCloudId)
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
                val templateProjectCloudId = template.templateProjectId?.let { syncMetadataDao.getCloudId(it, "project") }
                SyncMapper.taskTemplateToMap(template, templateProjectCloudId)
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
     *
     * Pull order is dependency-first so FK resolution always finds a
     * registered cloud→local mapping when it is needed:
     *   projects → tags → habits → tasks → task_completions →
     *   habit_completions → habit_logs → milestones → task_templates
     */
    suspend fun pullRemoteChanges(): Int {
        var applied = 0
        var skipped = 0

        val projectsResult = pullCollection("projects") { data, cloudId ->
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
            true
        }
        applied += projectsResult.applied
        skipped += projectsResult.skipped

        val tagsResult = pullCollection("tags") { data, cloudId ->
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
            true
        }
        applied += tagsResult.applied
        skipped += tagsResult.skipped

        // Habits before tasks: tasks may reference habits via sourceHabitId.
        val habitsResult = pullCollection("habits") { data, cloudId ->
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
            true
        }
        applied += habitsResult.applied
        skipped += habitsResult.skipped

        val tasksResult = pullCollection("tasks") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task")
            val projectCloudId = data["projectId"] as? String
            val projectLocalId = projectCloudId?.let { syncMetadataDao.getLocalId(it, "project") }
            // parentTaskId is a self-reference; parent may not be landed yet on first pull — accept null.
            val parentTaskCloudId = data["parentTaskId"] as? String
            val parentTaskLocalId = parentTaskCloudId?.let { syncMetadataDao.getLocalId(it, "task") }
            val sourceHabitCloudId = data["sourceHabitId"] as? String
            val sourceHabitLocalId = sourceHabitCloudId?.let { syncMetadataDao.getLocalId(it, "habit") }
            if (localId == null) {
                val task = SyncMapper.mapToTask(data, 0, projectLocalId, parentTaskLocalId, sourceHabitLocalId)
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
                val task = SyncMapper.mapToTask(data, localId, projectLocalId, parentTaskLocalId, sourceHabitLocalId)
                taskDao.update(task)
                syncMetadataDao.clearPendingAction(localId, "task")
            }
            true
        }
        applied += tasksResult.applied
        skipped += tasksResult.skipped

        // task_completions after tasks and projects so FK cloud IDs can be resolved.
        val taskCompletionsResult = pullCollection("task_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task_completion")
            val taskCloudId = data["taskId"] as? String
            val taskLocalId = taskCloudId?.let { syncMetadataDao.getLocalId(it, "task") }
            val projectCloudId = data["projectId"] as? String
            val projectLocalId = projectCloudId?.let { syncMetadataDao.getLocalId(it, "project") }
            if (localId == null) {
                val completion = SyncMapper.mapToTaskCompletion(data, 0, taskLocalId, projectLocalId)
                val newId = taskCompletionDao.insert(completion)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "task_completion",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            true
        }
        applied += taskCompletionsResult.applied
        skipped += taskCompletionsResult.skipped

        val localDayStartHour = taskBehaviorPreferences.getDayStartHour().first()
        val habitCompletionsResult = pullCollection("habit_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_completion")
            val habitCloudId = data["habitCloudId"] as? String
                ?: return@pullCollection false
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit")
                ?: return@pullCollection false
            if (localId == null) {
                val completion = SyncMapper.mapToHabitCompletion(data, habitLocalId = habitLocalId)
                val normalizedDate = DayBoundary.normalizeToDayStart(completion.completedDate, localDayStartHour)
                val normalized = completion.copy(completedDate = normalizedDate)
                logger.debug(
                    operation = "pull.normalize",
                    entity = "habit_completion",
                    detail = "raw=${completion.completedDate} normalized=$normalizedDate dayStartHour=$localDayStartHour"
                )
                val newId = habitCompletionDao.insert(normalized)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "habit_completion",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            true
        }
        applied += habitCompletionsResult.applied
        skipped += habitCompletionsResult.skipped

        val habitLogsResult = pullCollection("habit_logs") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_log")
            val habitCloudId = data["habitCloudId"] as? String
                ?: return@pullCollection false
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit")
                ?: return@pullCollection false
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
            true
        }
        applied += habitLogsResult.applied
        skipped += habitLogsResult.skipped

        // Milestones after projects: projectCloudId must already be in sync_metadata.
        val milestonesResult = pullCollection("milestones") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "milestone")
            val projectCloudId = data["projectCloudId"] as? String
                ?: return@pullCollection false
            val projectLocalId = syncMetadataDao.getLocalId(projectCloudId, "project")
                ?: return@pullCollection false
            if (localId == null) {
                val milestone = SyncMapper.mapToMilestone(data, projectLocalId)
                val newId = milestoneDao.insert(milestone)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "milestone",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val milestone = SyncMapper.mapToMilestone(data, projectLocalId, localId)
                milestoneDao.update(milestone)
                syncMetadataDao.clearPendingAction(localId, "milestone")
            }
            true
        }
        applied += milestonesResult.applied
        skipped += milestonesResult.skipped

        val taskTemplatesResult = pullCollection("task_templates") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "task_template")
            val templateProjectCloudId = data["templateProjectId"] as? String
            val templateProjectLocalId = templateProjectCloudId?.let { syncMetadataDao.getLocalId(it, "project") }
            if (localId == null) {
                val template = SyncMapper.mapToTaskTemplate(data, 0, templateProjectLocalId)
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
                val template = SyncMapper.mapToTaskTemplate(data, localId, templateProjectLocalId)
                taskTemplateDao.updateTemplate(template)
                syncMetadataDao.clearPendingAction(localId, "task_template")
            }
            true
        }
        applied += taskTemplatesResult.applied
        skipped += taskTemplatesResult.skipped

        if (skipped > 0) {
            logger.warn(
                operation = "pull.summary",
                entity = "all",
                status = "warning",
                detail = "applied=$applied skipped=$skipped — check pull.apply status=failed logs for details"
            )
        } else {
            logger.info(
                operation = "pull.summary",
                entity = "all",
                status = "success",
                detail = "applied=$applied skipped=0"
            )
        }
        return applied
    }

    /**
     * Handler returns `true` if the document was applied, `false` if it was
     * intentionally skipped (e.g. missing FK reference). Exceptions are
     * caught and counted as skipped.
     */
    private suspend fun pullCollection(
        name: String,
        handler: suspend (Map<String, Any?>, String) -> Boolean
    ): PullResult {
        val snapshot = userCollection(name)?.get()?.await() ?: return PullResult(0, 0)
        var applied = 0
        var skipped = 0
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            try {
                if (handler(data, doc.id)) applied++ else skipped++
            } catch (e: Exception) {
                skipped++
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
        return PullResult(applied, skipped)
    }

    private data class PullResult(val applied: Int, val skipped: Int)

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
            builtInHabitReconciler.reconcileAfterSyncIfNeeded()
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
        listOf("tasks", "projects", "tags", "habits", "habit_completions", "habit_logs", "task_completions", "milestones", "task_templates").forEach { collection ->
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
                val removedCloudIds = snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { it.document.id }
                scope.launch {
                    if (isSyncing) return@launch
                    val start = System.currentTimeMillis()
                    syncStateRepository.markSyncStarted(source = SOURCE_FIREBASE, trigger = "listener:$collection")
                    try {
                        if (removedCloudIds.isNotEmpty()) {
                            processRemoteDeletions(collection, removedCloudIds)
                        }
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

    private suspend fun processRemoteDeletions(collection: String, cloudIds: List<String>) {
        val entityType = when (collection) {
            "tasks" -> "task"
            "projects" -> "project"
            "tags" -> "tag"
            "habits" -> "habit"
            "habit_completions" -> "habit_completion"
            "habit_logs" -> "habit_log"
            "task_completions" -> "task_completion"
            "milestones" -> "milestone"
            "task_templates" -> "task_template"
            else -> return
        }
        var deleted = 0
        for (cloudId in cloudIds) {
            val localId = syncMetadataDao.getLocalId(cloudId, entityType) ?: continue
            try {
                when (entityType) {
                    "task" -> taskDao.deleteById(localId)
                    "project" -> projectDao.deleteById(localId)
                    "tag" -> tagDao.getTagByIdOnce(localId)?.let { tagDao.delete(it) }
                    "habit" -> habitDao.deleteById(localId)
                    "habit_completion" -> { /* HabitCompletionDao has no by-ID delete; metadata is still cleaned up below */ }
                    "habit_log" -> { /* HabitLogDao has no by-ID delete; metadata is still cleaned up below */ }
                    "task_completion" -> taskCompletionDao.deleteById(localId)
                    "milestone" -> milestoneDao.deleteById(localId)
                    "task_template" -> taskTemplateDao.deleteTemplate(localId)
                }
                syncMetadataDao.delete(localId, entityType)
                logger.info(
                    operation = "pull.delete",
                    entity = entityType,
                    id = cloudId,
                    status = "success"
                )
                deleted++
            } catch (e: Exception) {
                logger.error(
                    operation = "pull.delete",
                    entity = entityType,
                    id = cloudId,
                    throwable = e
                )
            }
        }
        logger.info(
            operation = "pull.delete.summary",
            entity = entityType,
            status = "success",
            detail = "deleted=$deleted"
        )
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
