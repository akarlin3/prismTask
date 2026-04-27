package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.local.dao.AttachmentDao
import com.averycorp.prismtask.data.local.dao.BoundaryRuleDao
import com.averycorp.prismtask.data.local.dao.CheckInLogDao
import com.averycorp.prismtask.data.local.dao.CustomSoundDao
import com.averycorp.prismtask.data.local.dao.DailyEssentialSlotCompletionDao
import com.averycorp.prismtask.data.local.dao.FocusReleaseLogDao
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.HabitTemplateDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.MedicationDao
import com.averycorp.prismtask.data.local.dao.MedicationDoseDao
import com.averycorp.prismtask.data.local.dao.MedicationRefillDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotDao
import com.averycorp.prismtask.data.local.dao.MedicationSlotOverrideDao
import com.averycorp.prismtask.data.local.dao.MedicationTierStateDao
import com.averycorp.prismtask.data.local.dao.MilestoneDao
import com.averycorp.prismtask.data.local.dao.MoodEnergyLogDao
import com.averycorp.prismtask.data.local.dao.NlpShortcutDao
import com.averycorp.prismtask.data.local.dao.NotificationProfileDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectTemplateDao
import com.averycorp.prismtask.data.local.dao.SavedFilterDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskCompletionDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.dao.WeeklyReviewDao
import com.averycorp.prismtask.data.local.entity.SyncMetadataEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences
import com.averycorp.prismtask.data.remote.mapper.MedicationSyncMapper
import com.averycorp.prismtask.data.remote.mapper.SyncMapper
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.averycorp.prismtask.data.remote.sync.SyncStateRepository
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// TODO(sync-refactor): split SyncService — separate push, pull, listener,
// and initial-upload surfaces. Each PR that touches this file widens the
// file further; the next refactor should land before the next feature.
@Suppress("LargeClass")
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
    private val builtInHabitReconciler: BuiltInHabitReconciler,
    private val builtInTaskTemplateReconciler: BuiltInTaskTemplateReconciler,
    private val builtInTaskTemplateBackfiller: BuiltInTaskTemplateBackfiller,
    private val builtInUpdateDetector: com.averycorp.prismtask.domain.usecase.BuiltInUpdateDetector,
    private val cloudIdOrphanHealer: CloudIdOrphanHealer,
    private val builtInMedicationReconciler: BuiltInMedicationReconciler,
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val medicationSlotDao: MedicationSlotDao,
    private val medicationSlotOverrideDao: MedicationSlotOverrideDao,
    private val medicationTierStateDao: MedicationTierStateDao,
    private val medicationMigrationPreferences: com.averycorp.prismtask.data.preferences.MedicationMigrationPreferences,
    private val sortPreferencesSyncService: SortPreferencesSyncService,
    private val schoolworkDao: SchoolworkDao,
    private val leisureDao: LeisureDao,
    private val selfCareDao: SelfCareDao,
    private val notificationProfileDao: NotificationProfileDao,
    private val customSoundDao: CustomSoundDao,
    private val savedFilterDao: SavedFilterDao,
    private val nlpShortcutDao: NlpShortcutDao,
    private val habitTemplateDao: HabitTemplateDao,
    private val projectTemplateDao: ProjectTemplateDao,
    private val boundaryRuleDao: BoundaryRuleDao,
    private val checkInLogDao: CheckInLogDao,
    private val moodEnergyLogDao: MoodEnergyLogDao,
    private val focusReleaseLogDao: FocusReleaseLogDao,
    private val medicationRefillDao: MedicationRefillDao,
    private val weeklyReviewDao: WeeklyReviewDao,
    private val dailyEssentialSlotCompletionDao: DailyEssentialSlotCompletionDao,
    private val attachmentDao: AttachmentDao,
    private val builtInSyncPreferences: BuiltInSyncPreferences,
    private val database: com.averycorp.prismtask.data.local.database.PrismTaskDatabase
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var isSyncing = false

    private fun userCollection(collection: String) =
        authManager.userId?.let { firestore.collection("users").document(it).collection(collection) }

    suspend fun initialUpload() {
        if (authManager.userId == null) return

        // Fix A — one-shot guard. Every sign-in used to re-run the entire
        // upload loop and mint brand-new Firestore docs for every local row,
        // fueling the duplication spiral. The flag is only set on successful
        // completion so a mid-run failure stays retryable on the next sign-in.
        if (builtInSyncPreferences.isInitialUploadDone()) {
            logger.info(operation = "initialUpload.skipped", detail = "reason=already_done")
            return
        }

        // Fix B — hold [isSyncing] for the duration of the upload loop so
        // listener-triggered pulls defer (they already check this flag at
        // line ~1300). Mirrors the pattern used by [fullSync]. AuthViewModel
        // serializes fullSync → initialUpload so there is normally no
        // contention here, but the guard is kept as a defense-in-depth.
        if (isSyncing) {
            logger.info(
                operation = "initialUpload.deferred",
                detail = "reason=another_sync_running"
            )
            return
        }
        isSyncing = true
        logger.info(operation = "initialUpload.started")
        val uploadStart = System.currentTimeMillis()
        var success = false
        try {
            doInitialUpload()
            builtInSyncPreferences.setInitialUploadDone(true)
            success = true
            logger.info(
                operation = "initialUpload.completed",
                status = "success",
                durationMs = System.currentTimeMillis() - uploadStart
            )
        } catch (e: Throwable) {
            logger.error(
                operation = "initialUpload.failed",
                durationMs = System.currentTimeMillis() - uploadStart,
                throwable = e
            )
            throw e
        } finally {
            isSyncing = false
        }

        // Fix B mitigation — while [isSyncing] was held above, any
        // listener-triggered pull callback short-circuited at
        // `if (isSyncing) return@launch` (line ~1298). Those callbacks are
        // fire-and-forget; once dropped, they don't re-run by themselves.
        // Run exactly one pullRemoteChanges() after release so any cloud
        // state that changed during the upload window still lands locally.
        // Realtime listeners keep firing normally for everything after this.
        if (success) {
            try {
                val applied = pullRemoteChanges()
                logger.debug(
                    operation = "initialUpload.post_release_pull",
                    status = "success",
                    detail = "applied=$applied"
                )
            } catch (e: Throwable) {
                logger.error(
                    operation = "initialUpload.post_release_pull",
                    throwable = e
                )
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun doInitialUpload() {
        val projects = projectDao.getAllProjectsOnce()
        logger.debug("upload.projects", status = "begin", detail = "count=${projects.size}")
        for (project in projects) {
            try {
                // Fix C — skip rows that already have a cloud mapping. On
                // fresh-install after a fullSync pull, every local row already
                // has a sync_metadata entry from the pull path; without this
                // guard initialUpload would re-upload all of them as new
                // auto-ID docs and duplicate the cloud state.
                if (syncMetadataDao.getCloudId(project.id, "project") != null) continue
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
                    if (syncMetadataDao.getCloudId(milestone.id, "milestone") != null) continue
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
                if (syncMetadataDao.getCloudId(tag.id, "tag") != null) continue
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
                if (syncMetadataDao.getCloudId(habit.id, "habit") != null) continue
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
                    if (syncMetadataDao.getCloudId(completion.id, "habit_completion") != null) continue
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
                    if (syncMetadataDao.getCloudId(log.id, "habit_log") != null) continue
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
                if (syncMetadataDao.getCloudId(task.id, "task") != null) continue
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
                if (syncMetadataDao.getCloudId(completion.id, "task_completion") != null) continue
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
                if (syncMetadataDao.getCloudId(template.id, "task_template") != null) continue
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

        uploadRoomConfigFamily(
            entityType = "notification_profile",
            collection = "notification_profiles",
            rows = notificationProfileDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.notificationProfileToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "custom_sound",
            collection = "custom_sounds",
            rows = customSoundDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.customSoundToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "saved_filter",
            collection = "saved_filters",
            rows = savedFilterDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.savedFilterToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "nlp_shortcut",
            collection = "nlp_shortcuts",
            rows = nlpShortcutDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.nlpShortcutToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "habit_template",
            collection = "habit_templates",
            rows = habitTemplateDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.habitTemplateToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "project_template",
            collection = "project_templates",
            rows = projectTemplateDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.projectTemplateToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "boundary_rule",
            collection = "boundary_rules",
            rows = boundaryRuleDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.boundaryRuleToMap(it) }
        )

        // --- v1.4.38 content families (FK-free) ---
        uploadRoomConfigFamily(
            entityType = "check_in_log",
            collection = "check_in_logs",
            rows = checkInLogDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.checkInLogToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "mood_energy_log",
            collection = "mood_energy_logs",
            rows = moodEnergyLogDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.moodEnergyLogToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "medication_refill",
            collection = "medication_refills",
            rows = medicationRefillDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.medicationRefillToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "weekly_review",
            collection = "weekly_reviews",
            rows = weeklyReviewDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.weeklyReviewToMap(it) }
        )
        uploadRoomConfigFamily(
            entityType = "daily_essential_slot_completion",
            collection = "daily_essential_slot_completions",
            rows = dailyEssentialSlotCompletionDao.getAllOnce(),
            rowId = { it.id },
            toMap = { SyncMapper.dailyEssentialSlotCompletionToMap(it) }
        )

        // --- v1.4.38 content families (FK-bearing) ---
        // focus_release_log.taskId, assignment.courseId, attachment.taskId,
        // study_log.coursePick + .assignmentPick need local→cloud translation
        // at push time. If a parent row isn't synced yet (no cloud_id), we
        // skip the child and let the next upload pass retry.
        uploadFocusReleaseLogs()
        uploadAssignments()
        uploadAttachments()
        uploadStudyLogs()

        maybeRunEntityBackfill()
    }

    private suspend fun uploadFocusReleaseLogs() {
        val rows = focusReleaseLogDao.getAllOnce()
        logger.debug("upload.focus_release_logs", status = "begin", detail = "count=${rows.size}")
        for (row in rows) {
            try {
                if (syncMetadataDao.getCloudId(row.id, "focus_release_log") != null) continue
                val taskCloudId = row.taskId?.let { syncMetadataDao.getCloudId(it, "task") }
                val docRef = userCollection("focus_release_logs")?.document() ?: continue
                docRef.set(SyncMapper.focusReleaseLogToMap(row, taskCloudId)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = row.id,
                        entityType = "focus_release_log",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.focus_release_log",
                    entity = "focus_release_log",
                    id = row.id.toString(),
                    throwable = e
                )
            }
        }
    }

    private suspend fun uploadAssignments() {
        val rows = schoolworkDao.getAllAssignmentsOnce()
        logger.debug("upload.assignments", status = "begin", detail = "count=${rows.size}")
        for (row in rows) {
            try {
                if (syncMetadataDao.getCloudId(row.id, "assignment") != null) continue
                val courseCloudId = syncMetadataDao.getCloudId(row.courseId, "course") ?: continue
                val docRef = userCollection("assignments")?.document() ?: continue
                docRef.set(SyncMapper.assignmentToMap(row, courseCloudId)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = row.id,
                        entityType = "assignment",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.assignment",
                    entity = "assignment",
                    id = row.id.toString(),
                    throwable = e
                )
            }
        }
    }

    private suspend fun uploadAttachments() {
        val rows = attachmentDao.getAllOnce()
        logger.debug("upload.attachments", status = "begin", detail = "count=${rows.size}")
        for (row in rows) {
            try {
                if (syncMetadataDao.getCloudId(row.id, "attachment") != null) continue
                val taskCloudId = syncMetadataDao.getCloudId(row.taskId, "task") ?: continue
                val docRef = userCollection("attachments")?.document() ?: continue
                docRef.set(SyncMapper.attachmentToMap(row, taskCloudId)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = row.id,
                        entityType = "attachment",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.attachment",
                    entity = "attachment",
                    id = row.id.toString(),
                    throwable = e
                )
            }
        }
    }

    private suspend fun uploadStudyLogs() {
        val rows = schoolworkDao.getAllStudyLogsOnce()
        logger.debug("upload.study_logs", status = "begin", detail = "count=${rows.size}")
        for (row in rows) {
            try {
                if (syncMetadataDao.getCloudId(row.id, "study_log") != null) continue
                val coursePickCloudId = row.coursePick?.let { syncMetadataDao.getCloudId(it, "course") }
                val assignmentPickCloudId = row.assignmentPick?.let {
                    syncMetadataDao.getCloudId(it, "assignment")
                }
                val docRef = userCollection("study_logs")?.document() ?: continue
                docRef.set(SyncMapper.studyLogToMap(row, coursePickCloudId, assignmentPickCloudId)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = row.id,
                        entityType = "study_log",
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.study_log",
                    entity = "study_log",
                    id = row.id.toString(),
                    throwable = e
                )
            }
        }
    }

    /**
     * Upload helper for the v1.4.37 Room-entity config families
     * (`notification_profile`, `custom_sound`, `saved_filter`, `nlp_shortcut`,
     * `habit_template`, `project_template`, `boundary_rule`). Each row is
     * skipped if it already has a cloud_id in sync_metadata, and each
     * failure is logged + swallowed so one bad row doesn't block the rest.
     */
    private suspend fun <T> uploadRoomConfigFamily(
        entityType: String,
        collection: String,
        rows: List<T>,
        rowId: (T) -> Long,
        toMap: (T) -> Map<String, Any?>
    ) {
        logger.debug("upload.$collection", status = "begin", detail = "count=${rows.size}")
        for (row in rows) {
            val id = rowId(row)
            try {
                if (syncMetadataDao.getCloudId(id, entityType) != null) continue
                val docRef = userCollection(collection)?.document() ?: continue
                docRef.set(toMap(row)).await()
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = id,
                        entityType = entityType,
                        cloudId = docRef.id,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(
                    operation = "upload.$entityType",
                    entity = entityType,
                    id = id.toString(),
                    throwable = e
                )
            }
        }
    }

    /**
     * Per-family backfill for the v1.4 "new entity" upload families
     * (`courses`, `course_completions`, `leisure_logs`, `self_care_steps`,
     * `self_care_logs`). Each family is guarded by its own DataStore flag
     * in [BuiltInSyncPreferences] with a legacy fallback to
     * [BuiltInSyncPreferences.isNewEntitiesBackfillDone], so users who
     * already ran the old single-flag master backfill don't re-run any
     * family's loop after upgrade. Each family's flag flips to true only
     * on successful completion of its own loop — a family that partially
     * failed stays retryable on the next app start independently of the
     * other families' success.
     *
     * Called from [doInitialUpload] (sign-in path) AND from [startAutoSync]
     * (already-signed-in path) so devices authenticated before this code
     * shipped are not silently skipped.
     *
     * Per-row guards (`if (getCloudId(row.id, type) != null) continue`)
     * make re-running a family's loop idempotent against already-synced
     * rows — resetting a per-family flag for targeted re-upload after an
     * out-of-band Firestore wipe is safe.
     *
     * The master flag ([BuiltInSyncPreferences.setNewEntitiesBackfillDone])
     * is still flipped to true when all families succeed, preserving
     * backwards-compat signaling for code paths that still check it.
     */
    private suspend fun maybeRunEntityBackfill() {
        val coursesOk = runCoursesBackfillIfNeeded()
        val courseCompletionsOk = runCourseCompletionsBackfillIfNeeded()
        val leisureLogsOk = runLeisureLogsBackfillIfNeeded()
        val selfCareStepsOk = runSelfCareStepsBackfillIfNeeded()
        val selfCareLogsOk = runSelfCareLogsBackfillIfNeeded()
        // medications BEFORE medication_doses so the dose helper can
        // resolve cloud_ids for the parents. Each has its own one-shot
        // flag in MedicationMigrationPreferences.
        val medicationsOk = runMedicationsBackfillIfNeeded()
        val medicationDosesOk = runMedicationDosesBackfillIfNeeded()

        val allSucceeded = coursesOk && courseCompletionsOk && leisureLogsOk &&
            selfCareStepsOk && selfCareLogsOk &&
            medicationsOk && medicationDosesOk
        if (allSucceeded) {
            builtInSyncPreferences.setNewEntitiesBackfillDone(true)
            logger.info("upload.new_entities_backfill", status = "success")
        }
    }

    /**
     * Runs the courses upload loop if its per-family flag is false.
     * Returns true if the family is complete (either was already done
     * or completed successfully in this pass), false if it ran but
     * encountered an exception. The flag flips only on clean completion.
     */
    private suspend fun runCoursesBackfillIfNeeded(): Boolean {
        if (builtInSyncPreferences.isCoursesBackfillDone()) return true
        return try {
            val courses = schoolworkDao.getAllCoursesOnce()
            logger.debug("upload.courses", status = "begin", detail = "count=${courses.size}")
            for (course in courses) {
                try {
                    if (syncMetadataDao.getCloudId(course.id, "course") != null) continue
                    val docRef = userCollection("courses")?.document() ?: continue
                    docRef.set(SyncMapper.courseToMap(course)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = course.id,
                            entityType = "course",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.course",
                        entity = "course",
                        id = course.id.toString(),
                        detail = course.name,
                        throwable = e
                    )
                }
            }
            builtInSyncPreferences.setCoursesBackfillDone(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.courses", throwable = e)
            false
        }
    }

    private suspend fun runCourseCompletionsBackfillIfNeeded(): Boolean {
        if (builtInSyncPreferences.isCourseCompletionsBackfillDone()) return true
        return try {
            // course_completions AFTER courses so course cloud IDs are in sync_metadata.
            val courseCompletions = schoolworkDao.getAllCompletionsOnce()
            logger.debug("upload.course_completions", status = "begin", detail = "count=${courseCompletions.size}")
            for (completion in courseCompletions) {
                try {
                    if (syncMetadataDao.getCloudId(completion.id, "course_completion") != null) continue
                    val courseCloudId = syncMetadataDao.getCloudId(completion.courseId, "course") ?: continue
                    val docRef = userCollection("course_completions")?.document() ?: continue
                    docRef.set(SyncMapper.courseCompletionToMap(completion, courseCloudId)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = completion.id,
                            entityType = "course_completion",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.course_completion",
                        entity = "course_completion",
                        id = completion.id.toString(),
                        throwable = e
                    )
                }
            }
            builtInSyncPreferences.setCourseCompletionsBackfillDone(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.course_completions", throwable = e)
            false
        }
    }

    private suspend fun runLeisureLogsBackfillIfNeeded(): Boolean {
        if (builtInSyncPreferences.isLeisureLogsBackfillDone()) return true
        return try {
            val leisureLogs = leisureDao.getAllLogsOnce()
            logger.debug("upload.leisure_logs", status = "begin", detail = "count=${leisureLogs.size}")
            for (log in leisureLogs) {
                try {
                    if (syncMetadataDao.getCloudId(log.id, "leisure_log") != null) continue
                    val docRef = userCollection("leisure_logs")?.document() ?: continue
                    docRef.set(SyncMapper.leisureLogToMap(log)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = log.id,
                            entityType = "leisure_log",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(operation = "upload.leisure_log", entity = "leisure_log", id = log.id.toString(), throwable = e)
                }
            }
            builtInSyncPreferences.setLeisureLogsBackfillDone(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.leisure_logs", throwable = e)
            false
        }
    }

    private suspend fun runSelfCareStepsBackfillIfNeeded(): Boolean {
        if (builtInSyncPreferences.isSelfCareStepsBackfillDone()) return true
        return try {
            val selfCareSteps = selfCareDao.getAllStepsOnce()
            logger.debug("upload.self_care_steps", status = "begin", detail = "count=${selfCareSteps.size}")
            for (step in selfCareSteps) {
                try {
                    if (syncMetadataDao.getCloudId(step.id, "self_care_step") != null) continue
                    val docRef = userCollection("self_care_steps")?.document() ?: continue
                    docRef.set(SyncMapper.selfCareStepToMap(step)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = step.id,
                            entityType = "self_care_step",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(operation = "upload.self_care_step", entity = "self_care_step", id = step.id.toString(), throwable = e)
                }
            }
            builtInSyncPreferences.setSelfCareStepsBackfillDone(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.self_care_steps", throwable = e)
            false
        }
    }

    private suspend fun runSelfCareLogsBackfillIfNeeded(): Boolean {
        if (builtInSyncPreferences.isSelfCareLogsBackfillDone()) return true
        return try {
            // self_care_logs AFTER self_care_steps (logical dependency).
            val selfCareLogs = selfCareDao.getAllLogsOnce()
            logger.debug("upload.self_care_logs", status = "begin", detail = "count=${selfCareLogs.size}")
            for (log in selfCareLogs) {
                try {
                    if (syncMetadataDao.getCloudId(log.id, "self_care_log") != null) continue
                    val docRef = userCollection("self_care_logs")?.document() ?: continue
                    docRef.set(SyncMapper.selfCareLogToMap(log)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = log.id,
                            entityType = "self_care_log",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(operation = "upload.self_care_log", entity = "self_care_log", id = log.id.toString(), throwable = e)
                }
            }
            builtInSyncPreferences.setSelfCareLogsBackfillDone(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.self_care_logs", throwable = e)
            false
        }
    }

    /**
     * Uploads every medication row that doesn't yet have a cloud mapping.
     * Must run BEFORE [runMedicationDosesBackfillIfNeeded] so dose rows
     * can resolve their parent medication's cloud_id.
     *
     * One-shot guard flag lives in [MedicationMigrationPreferences] (not
     * [BuiltInSyncPreferences]) because the medication migration owns
     * its own preference store.
     */
    private suspend fun runMedicationsBackfillIfNeeded(): Boolean {
        if (medicationMigrationPreferences.isMigrationPushedToCloud()) return true
        return try {
            val medications = medicationDao.getAllOnce()
            logger.debug("upload.medications", status = "begin", detail = "count=${medications.size}")
            for (med in medications) {
                try {
                    if (syncMetadataDao.getCloudId(med.id, "medication") != null) continue
                    val docRef = userCollection("medications")?.document() ?: continue
                    val slotCloudIds = medicationSlotDao.getSlotIdsForMedicationOnce(med.id)
                        .mapNotNull { syncMetadataDao.getCloudId(it, "medication_slot") }
                    docRef.set(MedicationSyncMapper.medicationToMap(med, slotCloudIds)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = med.id,
                            entityType = "medication",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.medication",
                        entity = "medication",
                        id = med.id.toString(),
                        detail = med.name,
                        throwable = e
                    )
                }
            }
            true
            // isMigrationPushedToCloud flag is set by the dose helper once
            // BOTH medications + doses are uploaded — setting it too early
            // would skip dose uploads on a partial-success retry.
        } catch (e: Exception) {
            logger.error(operation = "upload.medications", throwable = e)
            false
        }
    }

    private suspend fun runMedicationDosesBackfillIfNeeded(): Boolean {
        if (medicationMigrationPreferences.isMigrationPushedToCloud()) return true
        return try {
            val allDoses = medicationDoseDao.getAllOnce()
            logger.debug("upload.medication_doses", status = "begin", detail = "count=${allDoses.size}")
            for (dose in allDoses) {
                try {
                    if (syncMetadataDao.getCloudId(dose.id, "medication_dose") != null) continue
                    val medCloudId = syncMetadataDao.getCloudId(dose.medicationId, "medication")
                        ?: continue
                    val docRef = userCollection("medication_doses")?.document() ?: continue
                    docRef.set(MedicationSyncMapper.medicationDoseToMap(dose, medCloudId)).await()
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = dose.id,
                            entityType = "medication_dose",
                            cloudId = docRef.id,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        operation = "upload.medication_dose",
                        entity = "medication_dose",
                        id = dose.id.toString(),
                        throwable = e
                    )
                }
            }
            medicationMigrationPreferences.setMigrationPushedToCloud(true)
            true
        } catch (e: Exception) {
            logger.error(operation = "upload.medication_doses", throwable = e)
            false
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
        "course_completion" -> "course_completions"
        "leisure_log" -> "leisure_logs"
        "self_care_step" -> "self_care_steps"
        "self_care_log" -> "self_care_logs"
        "medication" -> "medications"
        "medication_dose" -> "medication_doses"
        "medication_slot" -> "medication_slots"
        "medication_slot_override" -> "medication_slot_overrides"
        "medication_tier_state" -> "medication_tier_states"
        "notification_profile" -> "notification_profiles"
        "custom_sound" -> "custom_sounds"
        "saved_filter" -> "saved_filters"
        "nlp_shortcut" -> "nlp_shortcuts"
        "habit_template" -> "habit_templates"
        "project_template" -> "project_templates"
        "boundary_rule" -> "boundary_rules"
        "check_in_log" -> "check_in_logs"
        "mood_energy_log" -> "mood_energy_logs"
        "focus_release_log" -> "focus_release_logs"
        "medication_refill" -> "medication_refills"
        "weekly_review" -> "weekly_reviews"
        "daily_essential_slot_completion" -> "daily_essential_slot_completions"
        "assignment" -> "assignments"
        "attachment" -> "attachments"
        "study_log" -> "study_logs"
        else -> entityType + "s"
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    // Dispatch across every synced entityType — splitting the `when` is not
    // worth the indirection since each branch is only a DAO lookup + mapper
    // call. TODO: refactor pushCreate to reduce early return statements.
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
            "course" -> {
                val course = schoolworkDao.getCourseById(meta.localId) ?: return
                SyncMapper.courseToMap(course)
            }
            "course_completion" -> {
                val completion = schoolworkDao.getAllCompletionsOnce().find { it.id == meta.localId } ?: return
                val courseCloudId = syncMetadataDao.getCloudId(completion.courseId, "course") ?: return
                SyncMapper.courseCompletionToMap(completion, courseCloudId)
            }
            "leisure_log" -> {
                val log = leisureDao.getAllLogsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.leisureLogToMap(log)
            }
            "self_care_step" -> {
                val step = selfCareDao.getAllStepsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.selfCareStepToMap(step)
            }
            "self_care_log" -> {
                val log = selfCareDao.getAllLogsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.selfCareLogToMap(log)
            }
            "medication" -> {
                val med = medicationDao.getByIdOnce(meta.localId) ?: return
                val slotCloudIds = medicationSlotDao.getSlotIdsForMedicationOnce(med.id)
                    .mapNotNull { syncMetadataDao.getCloudId(it, "medication_slot") }
                MedicationSyncMapper.medicationToMap(med, slotCloudIds)
            }
            "medication_dose" -> {
                val dose = medicationDoseDao.getAllOnce().find { it.id == meta.localId } ?: return
                val medCloudId = syncMetadataDao.getCloudId(dose.medicationId, "medication") ?: return
                MedicationSyncMapper.medicationDoseToMap(dose, medCloudId)
            }
            "medication_slot" -> {
                val slot = medicationSlotDao.getByIdOnce(meta.localId) ?: return
                MedicationSyncMapper.medicationSlotToMap(slot)
            }
            "medication_slot_override" -> {
                val override = medicationSlotOverrideDao.getByIdOnce(meta.localId) ?: return
                val medCloudId = syncMetadataDao.getCloudId(override.medicationId, "medication") ?: return
                val slotCloudId = syncMetadataDao.getCloudId(override.slotId, "medication_slot") ?: return
                MedicationSyncMapper.medicationSlotOverrideToMap(override, medCloudId, slotCloudId)
            }
            "medication_tier_state" -> {
                val state = medicationTierStateDao.getByIdOnce(meta.localId) ?: return
                val medCloudId = syncMetadataDao.getCloudId(state.medicationId, "medication") ?: return
                val slotCloudId = syncMetadataDao.getCloudId(state.slotId, "medication_slot") ?: return
                MedicationSyncMapper.medicationTierStateToMap(state, medCloudId, slotCloudId)
            }
            "notification_profile" -> {
                val profile = notificationProfileDao.getById(meta.localId) ?: return
                SyncMapper.notificationProfileToMap(profile)
            }
            "custom_sound" -> {
                val sound = customSoundDao.getById(meta.localId) ?: return
                SyncMapper.customSoundToMap(sound)
            }
            "saved_filter" -> {
                val filter = savedFilterDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.savedFilterToMap(filter)
            }
            "nlp_shortcut" -> {
                val shortcut = nlpShortcutDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.nlpShortcutToMap(shortcut)
            }
            "habit_template" -> {
                val template = habitTemplateDao.getById(meta.localId) ?: return
                SyncMapper.habitTemplateToMap(template)
            }
            "project_template" -> {
                val template = projectTemplateDao.getById(meta.localId) ?: return
                SyncMapper.projectTemplateToMap(template)
            }
            "boundary_rule" -> {
                val rule = boundaryRuleDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.boundaryRuleToMap(rule)
            }
            "check_in_log" -> {
                val log = checkInLogDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.checkInLogToMap(log)
            }
            "mood_energy_log" -> {
                val log = moodEnergyLogDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.moodEnergyLogToMap(log)
            }
            "focus_release_log" -> {
                val log = focusReleaseLogDao.getByIdOnce(meta.localId) ?: return
                val taskCloudId = log.taskId?.let { syncMetadataDao.getCloudId(it, "task") }
                SyncMapper.focusReleaseLogToMap(log, taskCloudId)
            }
            "medication_refill" -> {
                val refill = medicationRefillDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.medicationRefillToMap(refill)
            }
            "weekly_review" -> {
                val review = weeklyReviewDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.weeklyReviewToMap(review)
            }
            "daily_essential_slot_completion" -> {
                val row = dailyEssentialSlotCompletionDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.dailyEssentialSlotCompletionToMap(row)
            }
            "assignment" -> {
                val assignment = schoolworkDao.getAssignmentById(meta.localId) ?: return
                val courseCloudId = syncMetadataDao.getCloudId(assignment.courseId, "course")
                    ?: return // course not yet synced — retry on next pass
                SyncMapper.assignmentToMap(assignment, courseCloudId)
            }
            "attachment" -> {
                val attachment = attachmentDao.getByIdOnce(meta.localId) ?: return
                val taskCloudId = syncMetadataDao.getCloudId(attachment.taskId, "task")
                    ?: return // parent task not yet synced — retry on next pass
                SyncMapper.attachmentToMap(attachment, taskCloudId)
            }
            "study_log" -> {
                val log = schoolworkDao.getStudyLogByIdOnce(meta.localId) ?: return
                val coursePickCloudId = log.coursePick?.let { syncMetadataDao.getCloudId(it, "course") }
                val assignmentPickCloudId = log.assignmentPick?.let {
                    syncMetadataDao.getCloudId(it, "assignment")
                }
                SyncMapper.studyLogToMap(log, coursePickCloudId, assignmentPickCloudId)
            }
            else -> return
        }
        docRef.set(data).await()
        syncMetadataDao.upsert(meta.copy(cloudId = docRef.id, pendingAction = null, lastSyncedAt = System.currentTimeMillis()))
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    // Dispatch across every synced entityType — see pushCreate for the same
    // trade-off. TODO: refactor pushUpdate to reduce early return statements.
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
            "course" -> {
                val course = schoolworkDao.getCourseById(meta.localId) ?: return
                SyncMapper.courseToMap(course)
            }
            "course_completion" -> {
                val completion = schoolworkDao.getAllCompletionsOnce().find { it.id == meta.localId } ?: return
                val courseCloudId = syncMetadataDao.getCloudId(completion.courseId, "course") ?: return
                SyncMapper.courseCompletionToMap(completion, courseCloudId)
            }
            "leisure_log" -> {
                val log = leisureDao.getAllLogsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.leisureLogToMap(log)
            }
            "self_care_step" -> {
                val step = selfCareDao.getAllStepsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.selfCareStepToMap(step)
            }
            "self_care_log" -> {
                val log = selfCareDao.getAllLogsOnce().find { it.id == meta.localId } ?: return
                SyncMapper.selfCareLogToMap(log)
            }
            "medication" -> {
                val med = medicationDao.getByIdOnce(meta.localId) ?: return
                val slotCloudIds = medicationSlotDao.getSlotIdsForMedicationOnce(med.id)
                    .mapNotNull { syncMetadataDao.getCloudId(it, "medication_slot") }
                MedicationSyncMapper.medicationToMap(med, slotCloudIds)
            }
            "medication_dose" -> {
                val dose = medicationDoseDao.getAllOnce().find { it.id == meta.localId } ?: return
                val medCloudId = syncMetadataDao.getCloudId(dose.medicationId, "medication") ?: return
                MedicationSyncMapper.medicationDoseToMap(dose, medCloudId)
            }
            "medication_slot" -> {
                val slot = medicationSlotDao.getByIdOnce(meta.localId) ?: return
                MedicationSyncMapper.medicationSlotToMap(slot)
            }
            "medication_slot_override" -> {
                val override = medicationSlotOverrideDao.getByIdOnce(meta.localId) ?: return
                val medCloudId = syncMetadataDao.getCloudId(override.medicationId, "medication") ?: return
                val slotCloudId = syncMetadataDao.getCloudId(override.slotId, "medication_slot") ?: return
                MedicationSyncMapper.medicationSlotOverrideToMap(override, medCloudId, slotCloudId)
            }
            "medication_tier_state" -> {
                val state = medicationTierStateDao.getByIdOnce(meta.localId) ?: return
                val medCloudId = syncMetadataDao.getCloudId(state.medicationId, "medication") ?: return
                val slotCloudId = syncMetadataDao.getCloudId(state.slotId, "medication_slot") ?: return
                MedicationSyncMapper.medicationTierStateToMap(state, medCloudId, slotCloudId)
            }
            "notification_profile" -> {
                val profile = notificationProfileDao.getById(meta.localId) ?: return
                SyncMapper.notificationProfileToMap(profile)
            }
            "custom_sound" -> {
                val sound = customSoundDao.getById(meta.localId) ?: return
                SyncMapper.customSoundToMap(sound)
            }
            "saved_filter" -> {
                val filter = savedFilterDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.savedFilterToMap(filter)
            }
            "nlp_shortcut" -> {
                val shortcut = nlpShortcutDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.nlpShortcutToMap(shortcut)
            }
            "habit_template" -> {
                val template = habitTemplateDao.getById(meta.localId) ?: return
                SyncMapper.habitTemplateToMap(template)
            }
            "project_template" -> {
                val template = projectTemplateDao.getById(meta.localId) ?: return
                SyncMapper.projectTemplateToMap(template)
            }
            "boundary_rule" -> {
                val rule = boundaryRuleDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.boundaryRuleToMap(rule)
            }
            "check_in_log" -> {
                val log = checkInLogDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.checkInLogToMap(log)
            }
            "mood_energy_log" -> {
                val log = moodEnergyLogDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.moodEnergyLogToMap(log)
            }
            "focus_release_log" -> {
                val log = focusReleaseLogDao.getByIdOnce(meta.localId) ?: return
                val taskCloudId = log.taskId?.let { syncMetadataDao.getCloudId(it, "task") }
                SyncMapper.focusReleaseLogToMap(log, taskCloudId)
            }
            "medication_refill" -> {
                val refill = medicationRefillDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.medicationRefillToMap(refill)
            }
            "weekly_review" -> {
                val review = weeklyReviewDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.weeklyReviewToMap(review)
            }
            "daily_essential_slot_completion" -> {
                val row = dailyEssentialSlotCompletionDao.getByIdOnce(meta.localId) ?: return
                SyncMapper.dailyEssentialSlotCompletionToMap(row)
            }
            "assignment" -> {
                val assignment = schoolworkDao.getAssignmentById(meta.localId) ?: return
                val courseCloudId = syncMetadataDao.getCloudId(assignment.courseId, "course")
                    ?: return
                SyncMapper.assignmentToMap(assignment, courseCloudId)
            }
            "attachment" -> {
                val attachment = attachmentDao.getByIdOnce(meta.localId) ?: return
                val taskCloudId = syncMetadataDao.getCloudId(attachment.taskId, "task")
                    ?: return
                SyncMapper.attachmentToMap(attachment, taskCloudId)
            }
            "study_log" -> {
                val log = schoolworkDao.getStudyLogByIdOnce(meta.localId) ?: return
                val coursePickCloudId = log.coursePick?.let { syncMetadataDao.getCloudId(it, "course") }
                val assignmentPickCloudId = log.assignmentPick?.let {
                    syncMetadataDao.getCloudId(it, "assignment")
                }
                SyncMapper.studyLogToMap(log, coursePickCloudId, assignmentPickCloudId)
            }
            else -> return
        }
        // Delete-wins contract: use `docRef.update(...)` rather than `docRef.set(...)`.
        // `set` on a non-existent path silently creates the doc, which would
        // resurrect a row that another device already deleted — exactly the
        // bug Test 10 flagged. `update` throws with code NOT_FOUND (SDK 24+)
        // or FAILED_PRECONDITION (older) when the doc is missing; we treat
        // that as "remote deleted, propagate to local" and reuse the same
        // cleanup path the realtime listener takes in `processRemoteDeletions`.
        try {
            @Suppress("UNCHECKED_CAST")
            docRef.update(data as Map<String, Any>).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND ||
                e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
            ) {
                logger.info(
                    operation = "push.update.remoteDeleted",
                    entity = meta.entityType,
                    id = meta.cloudId,
                    status = "cleanup",
                    detail = "Remote doc missing — delete wins; removing local row."
                )
                processRemoteDeletions(
                    collectionNameFor(meta.entityType),
                    listOf(meta.cloudId)
                )
                return
            }
            throw e
        }
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
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun pullRemoteChanges(): Int {
        var applied = 0
        var skipped = 0

        val projectsResult = pullCollection("projects") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "project")
            if (localId == null) {
                val project = SyncMapper.mapToProject(data, cloudId = cloudId)
                val newId = projectDao.insert(project)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "project",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                // Resolve any sort preference that was stashed while waiting for this project.
                sortPreferencesSyncService.notifyProjectSynced(cloudId)
            } else {
                val localProject = projectDao.getProjectByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localProject == null || remoteUpdatedAt > localProject.updatedAt) {
                    projectDao.update(SyncMapper.mapToProject(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "project")
                }
            }
            true
        }
        applied += projectsResult.applied
        skipped += projectsResult.skipped

        val tagsResult = pullCollection("tags") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "tag")
            if (localId == null) {
                val tag = SyncMapper.mapToTag(data, cloudId = cloudId)
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
                val tag = SyncMapper.mapToTag(data, localId, cloudId = cloudId)
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
                val habit = SyncMapper.mapToHabit(data, cloudId = cloudId)
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
                val localHabit = habitDao.getHabitByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localHabit == null || remoteUpdatedAt > localHabit.updatedAt) {
                    habitDao.update(SyncMapper.mapToHabit(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "habit")
                }
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
                val task = SyncMapper.mapToTask(data, 0, projectLocalId, parentTaskLocalId, sourceHabitLocalId, cloudId = cloudId)
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
                val localTask = taskDao.getTaskByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localTask == null || remoteUpdatedAt > localTask.updatedAt) {
                    taskDao.update(
                        SyncMapper.mapToTask(data, localId, projectLocalId, parentTaskLocalId, sourceHabitLocalId, cloudId = cloudId)
                    )
                    syncMetadataDao.clearPendingAction(localId, "task")
                }
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
                val completion = SyncMapper.mapToTaskCompletion(data, 0, taskLocalId, projectLocalId, cloudId = cloudId)
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

        val habitCompletionsResult = pullCollection("habit_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "habit_completion")
            val habitCloudId = data["habitCloudId"] as? String
                ?: return@pullCollection false
            val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit")
                ?: return@pullCollection false
            // P0 sync audit PR-A. Defensive guard for stale sync_metadata:
            // HabitRepository.deleteHabit and BackendSyncService.applyHabitChanges
            // (delete branch) both call habitDao.deleteById without removing the
            // sync_metadata row first, leaving cloud_id → local_id mappings that
            // resolve to a now-gone habits.id. Subsequent habit_completion pulls
            // for that cloudId resolve a non-null habitLocalId here, then
            // habitCompletionDao.insert below throws SQLiteConstraintException:
            // FOREIGN KEY constraint failed (Test 3, Session 1, 2026-04-27).
            //
            // Treat missing parent as a transient skip — the eventual pushDelete
            // will tombstone the Firestore doc, and the next pull's
            // processRemoteDeletions will reap the orphan completion uniformly
            // across devices. Do NOT delete the stale sync_metadata here: the
            // pending_action='delete' row is what pushDelete needs to find the
            // Firestore target. Architectural sweep (pair every deleteById with
            // syncMetadataDao.delete on the receive side) is PR-A2.
            if (habitDao.getHabitByIdOnce(habitLocalId) == null) {
                logger.warn(
                    operation = "pull.apply",
                    entity = "habit_completions",
                    id = cloudId,
                    status = "skipped_stale_parent",
                    detail = "habit local_id=$habitLocalId is gone; metadata pending eventual pushDelete"
                )
                return@pullCollection false
            }
            if (localId == null) {
                // mapToHabitCompletion always produces a non-null completedDateLocal
                // (either from the Firestore doc or derived from the epoch for
                // legacy docs), so no post-hoc re-normalization is needed.
                val completion = SyncMapper.mapToHabitCompletion(data, habitLocalId = habitLocalId, cloudId = cloudId)
                // Dedup by natural key (habitId, completedDateLocal) to avoid
                // duplicating completions seeded locally on both devices before sign-in.
                val existingByNaturalKey = completion.completedDateLocal?.let {
                    habitCompletionDao.getByHabitAndDateLocal(habitLocalId, it)
                }
                if (existingByNaturalKey != null) {
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = existingByNaturalKey.id,
                            entityType = "habit_completion",
                            cloudId = cloudId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } else {
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
            // P0 sync audit PR-A — same defensive guard as habit_completions
            // above. habit_logs.habit_id has the same FK CASCADE shape, so
            // stale sync_metadata after a local habit delete causes an
            // identical SQLiteConstraintException on insert.
            if (habitDao.getHabitByIdOnce(habitLocalId) == null) {
                logger.warn(
                    operation = "pull.apply",
                    entity = "habit_logs",
                    id = cloudId,
                    status = "skipped_stale_parent",
                    detail = "habit local_id=$habitLocalId is gone; metadata pending eventual pushDelete"
                )
                return@pullCollection false
            }
            if (localId == null) {
                val log = SyncMapper.mapToHabitLog(data, habitLocalId = habitLocalId, cloudId = cloudId)
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
                val milestone = SyncMapper.mapToMilestone(data, projectLocalId, cloudId = cloudId)
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
                val localMilestone = milestoneDao.getByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localMilestone == null || remoteUpdatedAt > localMilestone.updatedAt) {
                    milestoneDao.update(SyncMapper.mapToMilestone(data, projectLocalId, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "milestone")
                }
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
                val template = SyncMapper.mapToTaskTemplate(data, 0, templateProjectLocalId, cloudId = cloudId)
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
                val localTemplate = taskTemplateDao.getTemplateById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localTemplate == null || remoteUpdatedAt > localTemplate.updatedAt) {
                    taskTemplateDao.updateTemplate(SyncMapper.mapToTaskTemplate(data, localId, templateProjectLocalId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "task_template")
                }
            }
            true
        }
        applied += taskTemplatesResult.applied
        skipped += taskTemplatesResult.skipped

        // Courses before course_completions so courseCloudId FK can be resolved.
        val coursesResult = pullCollection("courses") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "course")
            if (localId == null) {
                val course = SyncMapper.mapToCourse(data, cloudId = cloudId)
                val newId = schoolworkDao.insertCourse(course)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "course",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val localCourse = schoolworkDao.getCourseById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localCourse == null || remoteUpdatedAt > localCourse.updatedAt) {
                    schoolworkDao.updateCourse(SyncMapper.mapToCourse(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "course")
                }
            }
            true
        }
        applied += coursesResult.applied
        skipped += coursesResult.skipped

        val courseCompletionsResult = pullCollection("course_completions") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "course_completion")
            val courseCloudId = data["courseCloudId"] as? String
                ?: return@pullCollection false
            val courseLocalId = syncMetadataDao.getLocalId(courseCloudId, "course")
                ?: return@pullCollection false
            if (localId == null) {
                val completion = SyncMapper.mapToCourseCompletion(data, courseLocalId = courseLocalId, cloudId = cloudId)
                val newId = schoolworkDao.insertCompletion(completion)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "course_completion",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val localCompletion = schoolworkDao.getCompletionById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localCompletion == null || remoteUpdatedAt > localCompletion.updatedAt) {
                    schoolworkDao.updateCompletion(SyncMapper.mapToCourseCompletion(data, localId, courseLocalId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "course_completion")
                }
            }
            true
        }
        applied += courseCompletionsResult.applied
        skipped += courseCompletionsResult.skipped

        val leisureLogsResult = pullCollection("leisure_logs") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "leisure_log")
            if (localId == null) {
                val log = SyncMapper.mapToLeisureLog(data, cloudId = cloudId)
                val newId = leisureDao.insertLog(log)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "leisure_log",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val localLog = leisureDao.getLogById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localLog == null || remoteUpdatedAt > localLog.updatedAt) {
                    leisureDao.updateLog(SyncMapper.mapToLeisureLog(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "leisure_log")
                }
            }
            true
        }
        applied += leisureLogsResult.applied
        skipped += leisureLogsResult.skipped

        // self_care_steps before self_care_logs (logical dependency).
        // Dedup by stepId+routineType to avoid duplicating built-in default steps
        // that are seeded locally on both devices before sign-in.
        val selfCareStepsResult = pullCollection("self_care_steps") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "self_care_step")
            if (localId == null) {
                val stepId = data["stepId"] as? String
                val routineType = data["routineType"] as? String
                val existingByStepId = if (stepId != null && routineType != null) {
                    selfCareDao.getStepByStepIdOnce(stepId, routineType)
                } else {
                    null
                }
                if (existingByStepId != null) {
                    // Link existing local step to this cloud doc instead of duplicating.
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = existingByStepId.id,
                            entityType = "self_care_step",
                            cloudId = cloudId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    val step = SyncMapper.mapToSelfCareStep(data, cloudId = cloudId)
                    val newId = selfCareDao.insertStep(step)
                    syncMetadataDao.upsert(
                        SyncMetadataEntity(
                            localId = newId,
                            entityType = "self_care_step",
                            cloudId = cloudId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                val localStep = selfCareDao.getStepById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localStep == null || remoteUpdatedAt > localStep.updatedAt) {
                    selfCareDao.updateStep(SyncMapper.mapToSelfCareStep(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "self_care_step")
                }
            }
            true
        }
        applied += selfCareStepsResult.applied
        skipped += selfCareStepsResult.skipped

        val selfCareLogsResult = pullCollection("self_care_logs") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "self_care_log")
            if (localId == null) {
                val log = SyncMapper.mapToSelfCareLog(data, cloudId = cloudId)
                val newId = selfCareDao.insertLog(log)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "self_care_log",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val localLog = selfCareDao.getLogById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localLog == null || remoteUpdatedAt > localLog.updatedAt) {
                    selfCareDao.updateLog(SyncMapper.mapToSelfCareLog(data, localId, cloudId = cloudId))
                    syncMetadataDao.clearPendingAction(localId, "self_care_log")
                }
            }
            true
        }
        applied += selfCareLogsResult.applied
        skipped += selfCareLogsResult.skipped

        // medication_slots BEFORE medications so the junction rebuild lands
        // cleanly (medication pull embeds slotCloudIds that need local IDs).
        val medicationSlotsResult = pullCollection("medication_slots") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "medication_slot")
            if (localId == null) {
                val slot = MedicationSyncMapper.mapToMedicationSlot(data, cloudId = cloudId)
                val newId = medicationSlotDao.insert(slot)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "medication_slot",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val local = medicationSlotDao.getByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    medicationSlotDao.update(
                        MedicationSyncMapper.mapToMedicationSlot(data, localId, cloudId = cloudId)
                    )
                    syncMetadataDao.clearPendingAction(localId, "medication_slot")
                }
            }
            true
        }
        applied += medicationSlotsResult.applied
        skipped += medicationSlotsResult.skipped

        // medications BEFORE medication_doses so the FK resolution lands.
        // Junction rebuild: after every medication pull, replace its
        // `medication_medication_slots` row set with the slot cloud-ids
        // embedded on the Firestore doc (resolved via sync_metadata).
        val medicationsResult = pullCollection("medications") { data, cloudId ->
            val localId = syncMetadataDao.getLocalId(cloudId, "medication")
            val resolvedLocalId = if (localId == null) {
                val med = MedicationSyncMapper.mapToMedication(data, cloudId = cloudId)
                val newId = medicationDao.insert(med)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "medication",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                newId
            } else {
                val localMed = medicationDao.getByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (localMed == null || remoteUpdatedAt > localMed.updatedAt) {
                    medicationDao.update(
                        MedicationSyncMapper.mapToMedication(data, localId, cloudId = cloudId)
                    )
                    syncMetadataDao.clearPendingAction(localId, "medication")
                }
                localId
            }
            // Always rebuild junction from the embedded list — even on a
            // seen-update skip, the other device may have added / removed a
            // slot link without bumping `updatedAt` in a way we observed.
            val slotCloudIds = MedicationSyncMapper.extractSlotCloudIds(data)
            val slotLocalIds = slotCloudIds.mapNotNull {
                syncMetadataDao.getLocalId(it, "medication_slot")
            }
            medicationSlotDao.deleteLinksForMedication(resolvedLocalId)
            if (slotLocalIds.isNotEmpty()) {
                medicationSlotDao.insertLinks(
                    slotLocalIds.distinct().map {
                        com.averycorp.prismtask.data.local.entity.MedicationSlotCrossRef(
                            medicationId = resolvedLocalId,
                            slotId = it
                        )
                    }
                )
            }
            true
        }
        applied += medicationsResult.applied
        skipped += medicationsResult.skipped

        val medicationDosesResult = pullCollection("medication_doses") { data, cloudId ->
            val medCloudId = data["medicationCloudId"] as? String ?: return@pullCollection false
            val medLocalId = syncMetadataDao.getLocalId(medCloudId, "medication")
                ?: return@pullCollection false
            val localId = syncMetadataDao.getLocalId(cloudId, "medication_dose")
            if (localId == null) {
                val dose = MedicationSyncMapper.mapToMedicationDose(
                    data,
                    medicationLocalId = medLocalId,
                    cloudId = cloudId
                )
                val newId = medicationDoseDao.insert(dose)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "medication_dose",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // Doses are append-only — no updates. If the same
                // cloudId arrives again, it's a no-op duplicate push
                // and the existing row wins.
                syncMetadataDao.clearPendingAction(localId, "medication_dose")
            }
            true
        }
        applied += medicationDosesResult.applied
        skipped += medicationDosesResult.skipped

        val medicationSlotOverridesResult = pullCollection("medication_slot_overrides") { data, cloudId ->
            val medCloudId = data["medicationCloudId"] as? String ?: return@pullCollection false
            val slotCloudId = data["slotCloudId"] as? String ?: return@pullCollection false
            val medLocalId = syncMetadataDao.getLocalId(medCloudId, "medication")
                ?: return@pullCollection false
            val slotLocalId = syncMetadataDao.getLocalId(slotCloudId, "medication_slot")
                ?: return@pullCollection false
            val localId = syncMetadataDao.getLocalId(cloudId, "medication_slot_override")
            if (localId == null) {
                val override = MedicationSyncMapper.mapToMedicationSlotOverride(
                    data,
                    medicationLocalId = medLocalId,
                    slotLocalId = slotLocalId,
                    cloudId = cloudId
                )
                val newId = medicationSlotOverrideDao.insert(override)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "medication_slot_override",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val local = medicationSlotOverrideDao.getByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    medicationSlotOverrideDao.update(
                        MedicationSyncMapper.mapToMedicationSlotOverride(
                            data,
                            localId = localId,
                            medicationLocalId = medLocalId,
                            slotLocalId = slotLocalId,
                            cloudId = cloudId
                        )
                    )
                    syncMetadataDao.clearPendingAction(localId, "medication_slot_override")
                }
            }
            true
        }
        applied += medicationSlotOverridesResult.applied
        skipped += medicationSlotOverridesResult.skipped

        val medicationTierStatesResult = pullCollection("medication_tier_states") { data, cloudId ->
            val medCloudId = data["medicationCloudId"] as? String ?: return@pullCollection false
            val slotCloudId = data["slotCloudId"] as? String ?: return@pullCollection false
            val medLocalId = syncMetadataDao.getLocalId(medCloudId, "medication")
                ?: return@pullCollection false
            val slotLocalId = syncMetadataDao.getLocalId(slotCloudId, "medication_slot")
                ?: return@pullCollection false
            val localId = syncMetadataDao.getLocalId(cloudId, "medication_tier_state")
            if (localId == null) {
                val state = MedicationSyncMapper.mapToMedicationTierState(
                    data,
                    medicationLocalId = medLocalId,
                    slotLocalId = slotLocalId,
                    cloudId = cloudId
                )
                val newId = medicationTierStateDao.insert(state)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "medication_tier_state",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val local = medicationTierStateDao.getByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    medicationTierStateDao.update(
                        MedicationSyncMapper.mapToMedicationTierState(
                            data,
                            localId = localId,
                            medicationLocalId = medLocalId,
                            slotLocalId = slotLocalId,
                            cloudId = cloudId
                        )
                    )
                    syncMetadataDao.clearPendingAction(localId, "medication_tier_state")
                }
            }
            true
        }
        applied += medicationTierStatesResult.applied
        skipped += medicationTierStatesResult.skipped

        // v1.4.37 Room config families — last-write-wins per-row using updatedAt.
        val notificationProfilesResult = pullRoomConfigFamily(
            collection = "notification_profiles",
            entityType = "notification_profile",
            getLocalUpdatedAt = { notificationProfileDao.getById(it)?.updatedAt },
            insert = { data, cloudId ->
                notificationProfileDao.insert(SyncMapper.mapToNotificationProfile(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                notificationProfileDao.update(SyncMapper.mapToNotificationProfile(data, localId, cloudId))
            }
        )
        applied += notificationProfilesResult.applied
        skipped += notificationProfilesResult.skipped

        val customSoundsResult = pullRoomConfigFamily(
            collection = "custom_sounds",
            entityType = "custom_sound",
            getLocalUpdatedAt = { customSoundDao.getById(it)?.updatedAt },
            insert = { data, cloudId ->
                customSoundDao.insert(SyncMapper.mapToCustomSound(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                customSoundDao.update(SyncMapper.mapToCustomSound(data, localId, cloudId))
            }
        )
        applied += customSoundsResult.applied
        skipped += customSoundsResult.skipped

        val savedFiltersResult = pullRoomConfigFamily(
            collection = "saved_filters",
            entityType = "saved_filter",
            getLocalUpdatedAt = { savedFilterDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                savedFilterDao.insert(SyncMapper.mapToSavedFilter(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                savedFilterDao.update(SyncMapper.mapToSavedFilter(data, localId, cloudId))
            }
        )
        applied += savedFiltersResult.applied
        skipped += savedFiltersResult.skipped

        val nlpShortcutsResult = pullRoomConfigFamily(
            collection = "nlp_shortcuts",
            entityType = "nlp_shortcut",
            getLocalUpdatedAt = { nlpShortcutDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                nlpShortcutDao.insert(SyncMapper.mapToNlpShortcut(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                nlpShortcutDao.update(SyncMapper.mapToNlpShortcut(data, localId, cloudId))
            }
        )
        applied += nlpShortcutsResult.applied
        skipped += nlpShortcutsResult.skipped

        val habitTemplatesResult = pullRoomConfigFamily(
            collection = "habit_templates",
            entityType = "habit_template",
            getLocalUpdatedAt = { habitTemplateDao.getById(it)?.updatedAt },
            insert = { data, cloudId ->
                habitTemplateDao.insert(SyncMapper.mapToHabitTemplate(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                habitTemplateDao.update(SyncMapper.mapToHabitTemplate(data, localId, cloudId))
            }
        )
        applied += habitTemplatesResult.applied
        skipped += habitTemplatesResult.skipped

        val projectTemplatesResult = pullRoomConfigFamily(
            collection = "project_templates",
            entityType = "project_template",
            getLocalUpdatedAt = { projectTemplateDao.getById(it)?.updatedAt },
            insert = { data, cloudId ->
                projectTemplateDao.insert(SyncMapper.mapToProjectTemplate(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                projectTemplateDao.update(SyncMapper.mapToProjectTemplate(data, localId, cloudId))
            }
        )
        applied += projectTemplatesResult.applied
        skipped += projectTemplatesResult.skipped

        val boundaryRulesResult = pullRoomConfigFamily(
            collection = "boundary_rules",
            entityType = "boundary_rule",
            getLocalUpdatedAt = { boundaryRuleDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                boundaryRuleDao.insert(SyncMapper.mapToBoundaryRule(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                boundaryRuleDao.update(SyncMapper.mapToBoundaryRule(data, localId, cloudId))
            }
        )
        applied += boundaryRulesResult.applied
        skipped += boundaryRulesResult.skipped

        // v1.4.38 content families (FK-free) — same LWW semantics as above.
        val checkInLogsResult = pullRoomConfigFamily(
            collection = "check_in_logs",
            entityType = "check_in_log",
            getLocalUpdatedAt = { checkInLogDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                checkInLogDao.upsert(SyncMapper.mapToCheckInLog(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                checkInLogDao.upsert(SyncMapper.mapToCheckInLog(data, localId, cloudId))
            }
        )
        applied += checkInLogsResult.applied
        skipped += checkInLogsResult.skipped

        val moodEnergyLogsResult = pullRoomConfigFamily(
            collection = "mood_energy_logs",
            entityType = "mood_energy_log",
            getLocalUpdatedAt = { moodEnergyLogDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                moodEnergyLogDao.insert(SyncMapper.mapToMoodEnergyLog(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                moodEnergyLogDao.update(SyncMapper.mapToMoodEnergyLog(data, localId, cloudId))
            }
        )
        applied += moodEnergyLogsResult.applied
        skipped += moodEnergyLogsResult.skipped

        val medicationRefillsResult = pullRoomConfigFamily(
            collection = "medication_refills",
            entityType = "medication_refill",
            getLocalUpdatedAt = { medicationRefillDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                medicationRefillDao.upsert(SyncMapper.mapToMedicationRefill(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                medicationRefillDao.update(SyncMapper.mapToMedicationRefill(data, localId, cloudId))
            }
        )
        applied += medicationRefillsResult.applied
        skipped += medicationRefillsResult.skipped

        val weeklyReviewsResult = pullRoomConfigFamily(
            collection = "weekly_reviews",
            entityType = "weekly_review",
            getLocalUpdatedAt = { weeklyReviewDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                weeklyReviewDao.upsert(SyncMapper.mapToWeeklyReview(data, cloudId = cloudId))
            },
            update = { data, localId, cloudId ->
                weeklyReviewDao.upsert(SyncMapper.mapToWeeklyReview(data, localId, cloudId))
            }
        )
        applied += weeklyReviewsResult.applied
        skipped += weeklyReviewsResult.skipped

        val dailyEssentialSlotResult = pullRoomConfigFamily(
            collection = "daily_essential_slot_completions",
            entityType = "daily_essential_slot_completion",
            getLocalUpdatedAt = { dailyEssentialSlotCompletionDao.getByIdOnce(it)?.updatedAt },
            insert = { data, cloudId ->
                dailyEssentialSlotCompletionDao.upsert(
                    SyncMapper.mapToDailyEssentialSlotCompletion(data, cloudId = cloudId)
                )
            },
            update = { data, localId, cloudId ->
                dailyEssentialSlotCompletionDao.upsert(
                    SyncMapper.mapToDailyEssentialSlotCompletion(data, localId, cloudId)
                )
            }
        )
        applied += dailyEssentialSlotResult.applied
        skipped += dailyEssentialSlotResult.skipped

        // v1.4.38 content families with FK translation.
        val focusReleaseLogsResult = pullCollection("focus_release_logs") { data, cloudId ->
            val taskCloudId = data["taskId"] as? String
            val taskLocalId = taskCloudId?.let { syncMetadataDao.getLocalId(it, "task") }
            val localId = syncMetadataDao.getLocalId(cloudId, "focus_release_log")
            if (localId == null) {
                val log = SyncMapper.mapToFocusReleaseLog(data, 0, taskLocalId, cloudId)
                val newId = focusReleaseLogDao.insert(log)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "focus_release_log",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // Focus-release logs are append-only; same cloudId arriving again is a no-op.
                syncMetadataDao.clearPendingAction(localId, "focus_release_log")
            }
            true
        }
        applied += focusReleaseLogsResult.applied
        skipped += focusReleaseLogsResult.skipped

        val assignmentsResult = pullCollection("assignments") { data, cloudId ->
            val courseCloudId = data["courseId"] as? String ?: return@pullCollection false
            val courseLocalId = syncMetadataDao.getLocalId(courseCloudId, "course")
                ?: return@pullCollection false
            val localId = syncMetadataDao.getLocalId(cloudId, "assignment")
            if (localId == null) {
                val assignment = SyncMapper.mapToAssignment(data, 0, courseLocalId, cloudId)
                val newId = schoolworkDao.insertAssignment(assignment)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "assignment",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val local = schoolworkDao.getAssignmentById(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    schoolworkDao.updateAssignment(
                        SyncMapper.mapToAssignment(data, localId, courseLocalId, cloudId)
                    )
                    syncMetadataDao.clearPendingAction(localId, "assignment")
                }
            }
            true
        }
        applied += assignmentsResult.applied
        skipped += assignmentsResult.skipped

        val attachmentsResult = pullCollection("attachments") { data, cloudId ->
            val taskCloudId = data["taskId"] as? String ?: return@pullCollection false
            val taskLocalId = syncMetadataDao.getLocalId(taskCloudId, "task")
                ?: return@pullCollection false
            val localId = syncMetadataDao.getLocalId(cloudId, "attachment")
            if (localId == null) {
                val attachment = SyncMapper.mapToAttachment(data, 0, taskLocalId, cloudId)
                val newId = attachmentDao.insert(attachment)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "attachment",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // Attachments are effectively immutable after insert — same cloudId
                // arriving again is a no-op. Clearing pending_action keeps the
                // sync_metadata tidy.
                syncMetadataDao.clearPendingAction(localId, "attachment")
            }
            true
        }
        applied += attachmentsResult.applied
        skipped += attachmentsResult.skipped

        val studyLogsResult = pullCollection("study_logs") { data, cloudId ->
            val coursePickCloudId = data["coursePick"] as? String
            val assignmentPickCloudId = data["assignmentPick"] as? String
            val coursePickLocalId = coursePickCloudId?.let { syncMetadataDao.getLocalId(it, "course") }
            val assignmentPickLocalId = assignmentPickCloudId?.let {
                syncMetadataDao.getLocalId(it, "assignment")
            }
            val localId = syncMetadataDao.getLocalId(cloudId, "study_log")
            if (localId == null) {
                val log = SyncMapper.mapToStudyLog(
                    data,
                    0,
                    coursePickLocalId,
                    assignmentPickLocalId,
                    cloudId
                )
                val newId = schoolworkDao.insertLog(log)
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        localId = newId,
                        entityType = "study_log",
                        cloudId = cloudId,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                val local = schoolworkDao.getStudyLogByIdOnce(localId)
                val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                if (local == null || remoteUpdatedAt > local.updatedAt) {
                    schoolworkDao.updateLog(
                        SyncMapper.mapToStudyLog(
                            data,
                            localId,
                            coursePickLocalId,
                            assignmentPickLocalId,
                            cloudId
                        )
                    )
                    syncMetadataDao.clearPendingAction(localId, "study_log")
                }
            }
            true
        }
        applied += studyLogsResult.applied
        skipped += studyLogsResult.skipped

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

    /**
     * Pull helper for the v1.4.37 Room-entity config families. Identical
     * upsert semantics across all 7: insert-if-missing, else apply remote
     * only when `remoteUpdatedAt > localUpdatedAt` (last-write-wins).
     */
    private suspend fun pullRoomConfigFamily(
        collection: String,
        entityType: String,
        getLocalUpdatedAt: suspend (Long) -> Long?,
        insert: suspend (Map<String, Any?>, String) -> Long,
        update: suspend (Map<String, Any?>, Long, String) -> Unit
    ): PullResult = pullCollection(collection) { data, cloudId ->
        val localId = syncMetadataDao.getLocalId(cloudId, entityType)
        if (localId == null) {
            val newId = insert(data, cloudId)
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    localId = newId,
                    entityType = entityType,
                    cloudId = cloudId,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        } else {
            val localUpdatedAt = getLocalUpdatedAt(localId) ?: 0L
            val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            if (remoteUpdatedAt > localUpdatedAt) {
                update(data, localId, cloudId)
                syncMetadataDao.clearPendingAction(localId, entityType)
            }
        }
        true
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
            // Re-queue pushes for any local row with a cloud_id that no
            // longer has a matching Firestore doc. See
            // [CloudIdOrphanHealer] — covers post-Fix-D out-of-band wipe.
            try {
                cloudIdOrphanHealer.healOrphans()
            } catch (e: Exception) {
                logger.error(operation = "healer.error", throwable = e)
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
            builtInHabitReconciler.reconcileAfterSyncIfNeeded()
            builtInTaskTemplateReconciler.reconcileAfterSyncIfNeeded()
            builtInMedicationReconciler.reconcileAfterSyncIfNeeded()
            try {
                builtInUpdateDetector.refreshPendingUpdates()
            } catch (e: Exception) {
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
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

    /**
     * Phase 2.5 one-shot restore. Migration_51_52 backfilled
     * `cloud_id` on every syncable entity table from `sync_metadata` at
     * upgrade time, but every subsequent `pullRemoteChanges` then NULLed
     * the column because `SyncMapper.mapToX` didn't yet accept a `cloudId`
     * parameter. This patch fixes the mapper AND runs this restore once
     * to re-populate the column on rows that were pre-existing when the
     * patch landed. Gated by [BuiltInSyncPreferences.isCloudIdRestoreDone]
     * so it runs exactly once; flag only flips to true on success.
     *
     * Uses `UPDATE OR IGNORE` so any row that would collide with the
     * unique index on `cloud_id` (if two local rows still point at the
     * same cloud doc via `sync_metadata`) is silently skipped — a belt-
     * and-suspenders for collision cases beyond what Migration_51_52
     * already resolved. Collisions are logged per-table as the `updated`
     * count being less than the null-cloud_id row count; the skipped
     * row keeps `cloud_id = NULL` and a later sync cycle can mend it.
     *
     * Does NOT mutate `sync_metadata` (read-only) and does NOT write to
     * Firestore.
     */
    private suspend fun restoreCloudIdFromMetadata() {
        if (builtInSyncPreferences.isCloudIdRestoreDone()) return

        // Mirrors [Migration_51_52.syncableTables] — the two must stay in
        // sync. If a new syncable entity is added in a future release,
        // extend both lists.
        val syncableTables = listOf(
            "tasks" to "task",
            "projects" to "project",
            "tags" to "tag",
            "habits" to "habit",
            "habit_completions" to "habit_completion",
            "habit_logs" to "habit_log",
            "task_completions" to "task_completion",
            "task_templates" to "task_template",
            "milestones" to "milestone",
            "courses" to "course",
            "course_completions" to "course_completion",
            "leisure_logs" to "leisure_log",
            "self_care_steps" to "self_care_step",
            "self_care_logs" to "self_care_log"
        )

        val db = database.openHelper.writableDatabase
        var totalUpdated = 0
        try {
            for ((table, entityType) in syncableTables) {
                val sql = """
                    UPDATE OR IGNORE `$table` SET `cloud_id` = (
                        SELECT NULLIF(sm.cloud_id, '')
                        FROM sync_metadata sm
                        WHERE sm.local_id = `$table`.id
                          AND sm.entity_type = '$entityType'
                    )
                    WHERE cloud_id IS NULL
                """.trimIndent()
                val updated = db.compileStatement(sql).use { it.executeUpdateDelete() }
                totalUpdated += updated
                logger.info(
                    operation = "cloudId.restore",
                    entity = entityType,
                    status = "success",
                    detail = "table=$table updated=$updated"
                )
            }
            builtInSyncPreferences.setCloudIdRestoreDone(true)
            logger.info(
                operation = "cloudId.restore",
                status = "success",
                detail = "total_updated=$totalUpdated"
            )
        } catch (e: Throwable) {
            logger.error(operation = "cloudId.restore", throwable = e)
            // Flag intentionally NOT set — retry on next boot.
        }
    }

    fun startAutoSync() {
        if (authManager.userId == null) return
        startRealtimeListeners()
        scope.launch {
            // Phase 2.5 — re-populate `cloud_id` on pre-existing rows
            // before any pull activity. Runs once; see [restoreCloudIdFromMetadata].
            try {
                restoreCloudIdFromMetadata()
            } catch (e: Exception) {
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
            // Per-family backfill guards mean maybeRunEntityBackfill is now
            // safe and cheap to call unconditionally — each family's internal
            // flag short-circuits the loop when already done. Previously
            // guarded by [isNewEntitiesBackfillDone]; that master flag is
            // still written on full success and remains the legacy-user
            // fallback inside each per-family accessor.
            try {
                maybeRunEntityBackfill()
            } catch (e: Exception) {
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
            // Heal pre-template_key task_templates rows before the first
            // fullSync so the reconciler sees a correctly-shaped dataset on
            // the same cycle. See [BuiltInTaskTemplateBackfiller].
            try {
                builtInTaskTemplateBackfiller.runBackfillIfNeeded()
            } catch (e: Exception) {
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                        .recordException(e)
                } catch (_: Exception) {
                }
            }
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
        listOf(
            "tasks", "projects", "tags", "habits", "habit_completions",
            "habit_logs", "task_completions", "milestones", "task_templates",
            "courses", "course_completions", "leisure_logs", "self_care_steps", "self_care_logs",
            "medications", "medication_doses",
            "medication_slots", "medication_slot_overrides", "medication_tier_states",
            "notification_profiles", "custom_sounds", "saved_filters", "nlp_shortcuts",
            "habit_templates", "project_templates", "boundary_rules",
            "check_in_logs", "mood_energy_logs", "focus_release_logs",
            "medication_refills", "weekly_reviews", "daily_essential_slot_completions",
            "assignments", "attachments", "study_logs"
        ).forEach { collection ->
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
            "courses" -> "course"
            "course_completions" -> "course_completion"
            "leisure_logs" -> "leisure_log"
            "self_care_steps" -> "self_care_step"
            "self_care_logs" -> "self_care_log"
            "medications" -> "medication"
            "medication_doses" -> "medication_dose"
            "medication_slots" -> "medication_slot"
            "medication_slot_overrides" -> "medication_slot_override"
            "medication_tier_states" -> "medication_tier_state"
            "notification_profiles" -> "notification_profile"
            "custom_sounds" -> "custom_sound"
            "saved_filters" -> "saved_filter"
            "nlp_shortcuts" -> "nlp_shortcut"
            "habit_templates" -> "habit_template"
            "project_templates" -> "project_template"
            "boundary_rules" -> "boundary_rule"
            "check_in_logs" -> "check_in_log"
            "mood_energy_logs" -> "mood_energy_log"
            "focus_release_logs" -> "focus_release_log"
            "medication_refills" -> "medication_refill"
            "weekly_reviews" -> "weekly_review"
            "daily_essential_slot_completions" -> "daily_essential_slot_completion"
            "assignments" -> "assignment"
            "attachments" -> "attachment"
            "study_logs" -> "study_log"
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
                    "habit_completion" -> habitCompletionDao.deleteById(localId)
                    "habit_log" -> { /* HabitLogDao has no by-ID delete; metadata is still cleaned up below */ }
                    "task_completion" -> taskCompletionDao.deleteById(localId)
                    "milestone" -> milestoneDao.deleteById(localId)
                    "task_template" -> taskTemplateDao.deleteTemplate(localId)
                    "course" -> schoolworkDao.deleteCourse(localId)
                    "course_completion" -> schoolworkDao.deleteCompletionById(localId)
                    "leisure_log" -> leisureDao.deleteLogById(localId)
                    "self_care_step" -> selfCareDao.deleteStepById(localId)
                    "self_care_log" -> selfCareDao.deleteLogById(localId)
                    "medication" -> medicationDao.deleteById(localId)
                    "medication_dose" -> medicationDoseDao.deleteById(localId)
                    "medication_slot" -> medicationSlotDao.deleteById(localId)
                    "medication_slot_override" -> medicationSlotOverrideDao.deleteById(localId)
                    "medication_tier_state" -> medicationTierStateDao.deleteById(localId)
                    "notification_profile" ->
                        notificationProfileDao.getById(localId)?.let { notificationProfileDao.delete(it) }
                    "custom_sound" -> customSoundDao.deleteById(localId)
                    "saved_filter" -> savedFilterDao.deleteById(localId)
                    "nlp_shortcut" -> nlpShortcutDao.deleteById(localId)
                    "habit_template" -> habitTemplateDao.deleteById(localId)
                    "project_template" -> projectTemplateDao.deleteById(localId)
                    "boundary_rule" -> boundaryRuleDao.delete(localId)
                    "check_in_log" -> checkInLogDao.deleteById(localId)
                    "mood_energy_log" -> moodEnergyLogDao.deleteById(localId)
                    "focus_release_log" -> focusReleaseLogDao.deleteById(localId)
                    "medication_refill" -> medicationRefillDao.deleteById(localId)
                    "weekly_review" -> weeklyReviewDao.deleteById(localId)
                    "daily_essential_slot_completion" -> dailyEssentialSlotCompletionDao.deleteById(localId)
                    "assignment" -> schoolworkDao.deleteAssignment(localId)
                    "attachment" -> attachmentDao.deleteById(localId)
                    "study_log" -> schoolworkDao.deleteStudyLogById(localId)
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
