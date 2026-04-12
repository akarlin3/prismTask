package com.averycorp.prismtask.data.export

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.AssignmentEntity
import com.averycorp.prismtask.data.local.entity.CourseCompletionEntity
import com.averycorp.prismtask.data.local.entity.CourseEntity
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.preferences.ArchivePreferences
import com.averycorp.prismtask.data.preferences.BuiltInSortOrders
import com.averycorp.prismtask.data.preferences.CalendarPreferences
import com.averycorp.prismtask.data.preferences.CustomLeisureActivity
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.MedicationScheduleMode
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class ImportMode { MERGE, REPLACE }

data class ImportResult(
    val tasksImported: Int = 0,
    val projectsImported: Int = 0,
    val tagsImported: Int = 0,
    val habitsImported: Int = 0,
    val habitCompletionsImported: Int = 0,
    val habitLogsImported: Int = 0,
    val leisureLogsImported: Int = 0,
    val selfCareLogsImported: Int = 0,
    val selfCareStepsImported: Int = 0,
    val coursesImported: Int = 0,
    val assignmentsImported: Int = 0,
    val courseCompletionsImported: Int = 0,
    val configImported: Boolean = false,
    val duplicatesSkipped: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * Imports app data from a JSON file produced by [DataExporter].
 *
 * === Version handling & backwards compatibility ===
 *  - `version >= 3`: generic Gson path. Each entity JSON object is overlaid onto a
 *    freshly-constructed default instance via [mergeEntityWithDefaults]. This means any
 *    fields added to an entity *after* the file was exported automatically get their
 *    Kotlin constructor default values on import — so old backups keep working as the
 *    schema evolves.
 *  - `version < 3` (or missing): legacy flat-field path preserved verbatim so users
 *    can still restore backups produced by earlier app versions.
 *
 * The v3 format does not require any changes to this file when a new field is added to
 * an existing entity. Only adding a brand-new entity/collection requires a new branch
 * in the import loop.
 */
@Singleton
class DataImporter @Inject constructor(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val habitLogDao: com.averycorp.prismtask.data.local.dao.HabitLogDao,
    private val leisureDao: LeisureDao,
    private val selfCareDao: SelfCareDao,
    private val schoolworkDao: SchoolworkDao,
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    private val dashboardPreferences: DashboardPreferences,
    private val tabPreferences: TabPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val calendarPreferences: CalendarPreferences,
    private val habitListPreferences: HabitListPreferences,
    private val leisurePreferences: LeisurePreferences,
    private val medicationPreferences: MedicationPreferences,
    private val userPreferencesDataStore: com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
) {
    private val gson = Gson()

    /** Mutable accumulator for import counts, shared across sub-methods. */
    private class ImportContext {
        val errors = mutableListOf<String>()
        var tasksImported = 0
        var projectsImported = 0
        var tagsImported = 0
        var habitsImported = 0
        var habitCompletionsImported = 0
        var habitLogsImported = 0
        var leisureLogsImported = 0
        var selfCareLogsImported = 0
        var selfCareStepsImported = 0
        var coursesImported = 0
        var assignmentsImported = 0
        var courseCompletionsImported = 0
        var configImported = false
        var duplicatesSkipped = 0

        fun toResult() = ImportResult(
            tasksImported = tasksImported,
            projectsImported = projectsImported,
            tagsImported = tagsImported,
            habitsImported = habitsImported,
            habitCompletionsImported = habitCompletionsImported,
            habitLogsImported = habitLogsImported,
            leisureLogsImported = leisureLogsImported,
            selfCareLogsImported = selfCareLogsImported,
            selfCareStepsImported = selfCareStepsImported,
            coursesImported = coursesImported,
            assignmentsImported = assignmentsImported,
            courseCompletionsImported = courseCompletionsImported,
            configImported = configImported,
            duplicatesSkipped = duplicatesSkipped,
            errors = errors
        )
    }

    suspend fun importFromJson(jsonString: String, mode: ImportMode): ImportResult {
        val ctx = ImportContext()

        try {
            val root = JsonParser.parseString(jsonString).asJsonObject

            if (mode == ImportMode.REPLACE) {
                taskDao.getAllTasksOnce().forEach { taskDao.deleteById(it.id) }
                projectDao.getAllProjectsOnce().forEach { projectDao.delete(it) }
                tagDao.getAllTagsOnce().forEach { tagDao.delete(it) }
                habitCompletionDao.getAllCompletionsOnce().forEach {
                    habitCompletionDao.deleteByHabitAndDate(it.habitId, it.completedDate)
                }
                habitDao.getAllHabitsOnce().forEach { habitDao.delete(it) }
            }

            val existingProjects = projectDao.getAllProjectsOnce()
            val existingTags = tagDao.getAllTagsOnce()
            val existingTasks = taskDao.getAllTasksOnce()

            val projectNameToId = importProjects(ctx, root, mode, existingProjects)
            val tagNameToId = importTags(ctx, root, mode, existingTags)
            importTasks(ctx, root, mode, existingTasks, projectNameToId, tagNameToId)

            val habitNameToId = importHabits(ctx, root, mode)
            importHabitCompletions(ctx, root, habitNameToId)
            importHabitLogs(ctx, root, habitNameToId)

            importLeisureLogs(ctx, root)
            importSelfCareLogs(ctx, root)
            importSelfCareSteps(ctx, root)

            val courseNameToId = importCourses(ctx, root, mode)
            importAssignments(ctx, root, courseNameToId)
            importCourseCompletions(ctx, root, courseNameToId)

            importConfig(ctx, root)
        } catch (e: Exception) {
            ctx.errors.add("Import failed: ${e.message}")
        }

        return ctx.toResult()
    }

    private suspend fun importProjects(
        ctx: ImportContext,
        root: JsonObject,
        mode: ImportMode,
        existingProjects: List<ProjectEntity>
    ): MutableMap<String, Long> {
        val projectNameToId = mutableMapOf<String, Long>()
        existingProjects.forEach { projectNameToId[it.name.lowercase()] = it.id }

        root.getAsJsonArray("projects")?.forEach { elem ->
            val obj = elem.asJsonObject
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            if (mode == ImportMode.MERGE && name.lowercase() in projectNameToId) {
                ctx.duplicatesSkipped++
            } else {
                val default = ProjectEntity(name = name)
                val merged = mergeEntityWithDefaults(default, obj)
                val project = merged.copy(id = 0, updatedAt = System.currentTimeMillis())
                val id = projectDao.insert(project)
                projectNameToId[name.lowercase()] = id
                ctx.projectsImported++
            }
        }
        return projectNameToId
    }

    private suspend fun importTags(
        ctx: ImportContext,
        root: JsonObject,
        mode: ImportMode,
        existingTags: List<TagEntity>
    ): MutableMap<String, Long> {
        val tagNameToId = mutableMapOf<String, Long>()
        existingTags.forEach { tagNameToId[it.name.lowercase()] = it.id }

        root.getAsJsonArray("tags")?.forEach { elem ->
            val obj = elem.asJsonObject
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            if (mode == ImportMode.MERGE && name.lowercase() in tagNameToId) {
                ctx.duplicatesSkipped++
            } else {
                val default = TagEntity(name = name)
                val merged = mergeEntityWithDefaults(default, obj)
                val tag = merged.copy(id = 0)
                val id = tagDao.insert(tag)
                tagNameToId[name.lowercase()] = id
                ctx.tagsImported++
            }
        }
        return tagNameToId
    }

    private suspend fun importTasks(
        ctx: ImportContext,
        root: JsonObject,
        mode: ImportMode,
        existingTasks: List<TaskEntity>,
        projectNameToId: Map<String, Long>,
        tagNameToId: Map<String, Long>
    ) {
        root.getAsJsonArray("tasks")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                if (title.isBlank()) return@forEach

                if (mode == ImportMode.MERGE) {
                    val createdAt = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asLong ?: 0
                    val isDup = existingTasks.any { it.title == title && abs(it.createdAt - createdAt) < 60000 }
                    if (isDup) { ctx.duplicatesSkipped++; return@forEach }
                }

                // Foreign key resolution: v3 uses "_projectName" helper, v2 uses "project".
                val projectName = (obj.get("_projectName") ?: obj.get("project"))
                    ?.takeIf { !it.isJsonNull }?.asString
                val projectId = projectName?.let { projectNameToId[it.lowercase()] }

                val default = TaskEntity(title = title)
                val merged = mergeEntityWithDefaults(default, obj)
                val task = merged.copy(
                    id = 0,
                    projectId = projectId,
                    parentTaskId = null,
                    sourceHabitId = null,
                    updatedAt = System.currentTimeMillis()
                )
                val taskId = taskDao.insert(task)

                // Tags: v3 uses "_tagNames", v2 uses "tags".
                val tagArr = obj.getAsJsonArray("_tagNames") ?: obj.getAsJsonArray("tags")
                tagArr?.forEach { tagElem ->
                    if (tagElem.isJsonNull) return@forEach
                    val tagName = tagElem.asString
                    val tagId = tagNameToId[tagName.lowercase()]
                    if (tagId != null) {
                        tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
                    }
                }

                ctx.tasksImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import task: ${e.message}")
            }
        }
    }

    private suspend fun importHabits(
        ctx: ImportContext,
        root: JsonObject,
        mode: ImportMode
    ): MutableMap<String, Long> {
        val habitNameToId = mutableMapOf<String, Long>()
        if (mode == ImportMode.MERGE) {
            habitDao.getAllHabitsOnce().forEach { habitNameToId[it.name.lowercase()] = it.id }
        }

        root.getAsJsonArray("habits")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in habitNameToId) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = HabitEntity(name = name)
                val merged = mergeEntityWithDefaults(default, obj)
                val habit = merged.copy(id = 0, updatedAt = System.currentTimeMillis())
                val id = habitDao.insert(habit)
                habitNameToId[name.lowercase()] = id
                ctx.habitsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import habit: ${e.message}")
            }
        }
        return habitNameToId
    }

    private suspend fun importHabitCompletions(
        ctx: ImportContext,
        root: JsonObject,
        habitNameToId: Map<String, Long>
    ) {
        val existingCompletionKeys = habitCompletionDao.getAllCompletionsOnce()
            .map { it.habitId to it.completedDate }
            .toMutableSet()
        root.getAsJsonArray("habitCompletions")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val habitName = (obj.get("_habitName") ?: obj.get("habitName"))
                    ?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val habitId = habitNameToId[habitName.lowercase()] ?: return@forEach
                val completedDate = obj.get("completedDate")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return@forEach
                val key = habitId to completedDate
                if (key in existingCompletionKeys) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = HabitCompletionEntity(habitId = habitId, completedDate = completedDate)
                val merged = mergeEntityWithDefaults(default, obj)
                val completion = merged.copy(id = 0, habitId = habitId)
                habitCompletionDao.insert(completion)
                existingCompletionKeys.add(key)
                ctx.habitCompletionsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import habit completion: ${e.message}")
            }
        }
    }

    private suspend fun importHabitLogs(
        ctx: ImportContext,
        root: JsonObject,
        habitNameToId: Map<String, Long>
    ) {
        val existingHabitLogKeys = habitLogDao.getAllLogsOnce()
            .map { it.habitId to it.date }.toMutableSet()
        root.getAsJsonArray("habitLogs")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val habitName = obj.get("_habitName")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("habitName")?.takeIf { !it.isJsonNull }?.asString
                val habitId = if (habitName != null) habitNameToId[habitName.lowercase()] else null
                if (habitId == null) return@forEach
                val date = obj.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
                val key = habitId to date
                if (key in existingHabitLogKeys) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = com.averycorp.prismtask.data.local.entity.HabitLogEntity(habitId = habitId, date = date)
                val merged = mergeEntityWithDefaults(default, obj)
                val log = merged.copy(id = 0, habitId = habitId)
                habitLogDao.insertLog(log)
                existingHabitLogKeys.add(key)
                ctx.habitLogsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import habit log: ${e.message}")
            }
        }
    }

    private suspend fun importLeisureLogs(ctx: ImportContext, root: JsonObject) {
        val existingLeisureDates = leisureDao.getAllLogsOnce().map { it.date }.toMutableSet()
        root.getAsJsonArray("leisureLogs")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val date = obj.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
                if (date in existingLeisureDates) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = LeisureLogEntity(date = date)
                val merged = mergeEntityWithDefaults(default, obj)
                leisureDao.insertLog(merged.copy(id = 0))
                existingLeisureDates.add(date)
                ctx.leisureLogsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import leisure log: ${e.message}")
            }
        }
    }

    private suspend fun importSelfCareLogs(ctx: ImportContext, root: JsonObject) {
        val existingSelfCareLogKeys = selfCareDao.getAllLogsOnce()
            .map { it.routineType to it.date }
            .toMutableSet()
        root.getAsJsonArray("selfCareLogs")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val routineType = obj.get("routineType")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@forEach
                val date = obj.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
                val key = routineType to date
                if (key in existingSelfCareLogKeys) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = SelfCareLogEntity(routineType = routineType, date = date)
                val merged = mergeEntityWithDefaults(default, obj)
                selfCareDao.insertLog(merged.copy(id = 0))
                existingSelfCareLogKeys.add(key)
                ctx.selfCareLogsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import self-care log: ${e.message}")
            }
        }
    }

    private suspend fun importSelfCareSteps(ctx: ImportContext, root: JsonObject) {
        val existingStepIds = selfCareDao.getAllStepsOnce().map { it.stepId }.toMutableSet()
        root.getAsJsonArray("selfCareSteps")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val stepId = obj.get("stepId")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                if (stepId in existingStepIds) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val routineType = obj.get("routineType")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@forEach
                val label = obj.get("label")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val duration = obj.get("duration")?.takeIf { !it.isJsonNull }?.asString ?: "0"
                val tier = obj.get("tier")?.takeIf { !it.isJsonNull }?.asString ?: "solid"
                val phase = obj.get("phase")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val default = SelfCareStepEntity(
                    stepId = stepId,
                    routineType = routineType,
                    label = label,
                    duration = duration,
                    tier = tier,
                    phase = phase
                )
                val merged = mergeEntityWithDefaults(default, obj)
                selfCareDao.insertStep(merged.copy(id = 0))
                existingStepIds.add(stepId)
                ctx.selfCareStepsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import self-care step: ${e.message}")
            }
        }
    }

    private suspend fun importCourses(
        ctx: ImportContext,
        root: JsonObject,
        mode: ImportMode
    ): MutableMap<String, Long> {
        val courseNameToId = mutableMapOf<String, Long>()
        if (mode == ImportMode.MERGE) {
            schoolworkDao.getAllCoursesOnce().forEach { courseNameToId[it.name.lowercase()] = it.id }
        }

        root.getAsJsonArray("courses")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in courseNameToId) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val code = obj.get("code")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val default = CourseEntity(name = name, code = code)
                val merged = mergeEntityWithDefaults(default, obj)
                val id = schoolworkDao.insertCourse(merged.copy(id = 0))
                courseNameToId[name.lowercase()] = id
                ctx.coursesImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import course: ${e.message}")
            }
        }
        return courseNameToId
    }

    private suspend fun importAssignments(
        ctx: ImportContext,
        root: JsonObject,
        courseNameToId: Map<String, Long>
    ) {
        val existingAssignmentKeys = schoolworkDao.getAllAssignmentsOnce()
            .map { Triple(it.courseId, it.title, it.dueDate) }
            .toMutableSet()
        root.getAsJsonArray("assignments")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val courseName = (obj.get("_courseName") ?: obj.get("courseName"))
                    ?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val courseId = courseNameToId[courseName.lowercase()] ?: return@forEach
                val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val dueDate = obj.get("dueDate")?.takeIf { !it.isJsonNull }?.asLong
                val key = Triple(courseId, title, dueDate)
                if (key in existingAssignmentKeys) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = AssignmentEntity(courseId = courseId, title = title)
                val merged = mergeEntityWithDefaults(default, obj)
                schoolworkDao.insertAssignment(merged.copy(id = 0, courseId = courseId))
                existingAssignmentKeys.add(key)
                ctx.assignmentsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import assignment: ${e.message}")
            }
        }
    }

    private suspend fun importCourseCompletions(
        ctx: ImportContext,
        root: JsonObject,
        courseNameToId: Map<String, Long>
    ) {
        val existingCourseCompletionKeys = schoolworkDao.getAllCompletionsOnce()
            .map { it.courseId to it.date }
            .toMutableSet()
        root.getAsJsonArray("courseCompletions")?.forEach { elem ->
            try {
                val obj = elem.asJsonObject
                val courseName = (obj.get("_courseName") ?: obj.get("courseName"))
                    ?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val courseId = courseNameToId[courseName.lowercase()] ?: return@forEach
                val date = obj.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
                val key = courseId to date
                if (key in existingCourseCompletionKeys) {
                    ctx.duplicatesSkipped++
                    return@forEach
                }
                val default = CourseCompletionEntity(date = date, courseId = courseId)
                val merged = mergeEntityWithDefaults(default, obj)
                schoolworkDao.insertCompletion(merged.copy(id = 0, courseId = courseId))
                existingCourseCompletionKeys.add(key)
                ctx.courseCompletionsImported++
            } catch (e: Exception) {
                ctx.errors.add("Failed to import course completion: ${e.message}")
            }
        }
    }

    private suspend fun importConfig(ctx: ImportContext, root: JsonObject) {
        root.getAsJsonObject("config")?.let { config ->
            try {
                importThemeConfig(config)
                importArchiveConfig(config)
                importDashboardConfig(config)
                importTabsConfig(config)
                importTaskBehaviorConfig(config)
                importHabitListConfig(config)
                importLeisureConfig(ctx, config)
                importMedicationConfig(config)
                importCalendarConfig(config)
                importUserPreferencesConfig(config)
                ctx.configImported = true
            } catch (e: Exception) {
                ctx.errors.add("Failed to import config: ${e.message}")
            }
        }
    }

    private suspend fun importThemeConfig(config: JsonObject) {
        config.getAsJsonObject("theme")?.let { theme ->
            theme.get("themeMode")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setThemeMode(it) }
            theme.get("accentColor")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setAccentColor(it) }
            theme.get("backgroundColor")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setBackgroundColor(it) }
            theme.get("surfaceColor")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setSurfaceColor(it) }
            theme.get("errorColor")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setErrorColor(it) }
            theme.get("fontScale")?.takeIf { !it.isJsonNull }?.asFloat?.let { themePreferences.setFontScale(it) }
            theme.get("priorityColorNone")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setPriorityColor(0, it) }
            theme.get("priorityColorLow")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setPriorityColor(1, it) }
            theme.get("priorityColorMedium")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setPriorityColor(2, it) }
            theme.get("priorityColorHigh")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setPriorityColor(3, it) }
            theme.get("priorityColorUrgent")?.takeIf { !it.isJsonNull }?.asString?.let { themePreferences.setPriorityColor(4, it) }
        }
    }

    private suspend fun importArchiveConfig(config: JsonObject) {
        config.getAsJsonObject("archive")?.let { archive ->
            archive.get("autoArchiveDays")?.takeIf { !it.isJsonNull }?.asInt?.let { archivePreferences.setAutoArchiveDays(it) }
        }
    }

    private suspend fun importDashboardConfig(config: JsonObject) {
        config.getAsJsonObject("dashboard")?.let { dashboard ->
            dashboard.get("sectionOrder")?.takeIf { !it.isJsonNull }?.asString?.let { order ->
                dashboardPreferences.setSectionOrder(order.split(",").filter { it.isNotBlank() })
            }
            dashboard.getAsJsonArray("hiddenSections")?.let { arr ->
                dashboardPreferences.setHiddenSections(arr.mapNotNull { if (it.isJsonNull) null else it.asString }.toSet())
            }
            dashboard.get("progressStyle")?.takeIf { !it.isJsonNull }?.asString?.let { dashboardPreferences.setProgressStyle(it) }
        }
    }

    private suspend fun importTabsConfig(config: JsonObject) {
        config.getAsJsonObject("tabs")?.let { tabs ->
            tabs.get("tabOrder")?.takeIf { !it.isJsonNull }?.asString?.let { order ->
                tabPreferences.setTabOrder(order.split(",").filter { it.isNotBlank() })
            }
            tabs.getAsJsonArray("hiddenTabs")?.let { arr ->
                tabPreferences.setHiddenTabs(arr.mapNotNull { if (it.isJsonNull) null else it.asString }.toSet())
            }
        }
    }

    private suspend fun importTaskBehaviorConfig(config: JsonObject) {
        config.getAsJsonObject("taskBehavior")?.let { tb ->
            tb.get("defaultSort")?.takeIf { !it.isJsonNull }?.asString?.let { taskBehaviorPreferences.setDefaultSort(it) }
            tb.get("defaultViewMode")?.takeIf { !it.isJsonNull }?.asString?.let { taskBehaviorPreferences.setDefaultViewMode(it) }
            val dueDate = tb.get("urgencyWeightDueDate")?.takeIf { !it.isJsonNull }?.asFloat ?: 0.40f
            val priority = tb.get("urgencyWeightPriority")?.takeIf { !it.isJsonNull }?.asFloat ?: 0.30f
            val age = tb.get("urgencyWeightAge")?.takeIf { !it.isJsonNull }?.asFloat ?: 0.15f
            val subtasks = tb.get("urgencyWeightSubtasks")?.takeIf { !it.isJsonNull }?.asFloat ?: 0.15f
            taskBehaviorPreferences.setUrgencyWeights(UrgencyWeights(dueDate, priority, age, subtasks))
            tb.get("reminderPresets")?.takeIf { !it.isJsonNull }?.asString?.let { presets ->
                taskBehaviorPreferences.setReminderPresets(presets.split(",").mapNotNull { it.trim().toLongOrNull() })
            }
            tb.get("firstDayOfWeek")?.takeIf { !it.isJsonNull }?.asString?.let {
                try { taskBehaviorPreferences.setFirstDayOfWeek(DayOfWeek.valueOf(it)) } catch (_: Exception) {}
            }
            tb.get("dayStartHour")?.takeIf { !it.isJsonNull }?.asInt?.let { taskBehaviorPreferences.setDayStartHour(it) }
        }
    }

    private suspend fun importHabitListConfig(config: JsonObject) {
        config.getAsJsonObject("habitList")?.let { hl ->
            habitListPreferences.setBuiltInSortOrders(BuiltInSortOrders(
                morning = hl.get("morningSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -6,
                bedtime = hl.get("bedtimeSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -5,
                medication = hl.get("medicationSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -4,
                school = hl.get("schoolSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -2,
                leisure = hl.get("leisureSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -1,
                housework = hl.get("houseworkSortOrder")?.takeIf { !it.isJsonNull }?.asInt ?: -3
            ))
            hl.get("selfCareEnabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { habitListPreferences.setSelfCareEnabled(it) }
            hl.get("medicationEnabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { habitListPreferences.setMedicationEnabled(it) }
            hl.get("schoolEnabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { habitListPreferences.setSchoolEnabled(it) }
            hl.get("leisureEnabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { habitListPreferences.setLeisureEnabled(it) }
            hl.get("houseworkEnabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { habitListPreferences.setHouseworkEnabled(it) }
        }
    }

    private suspend fun importLeisureConfig(ctx: ImportContext, config: JsonObject) {
        config.getAsJsonObject("leisure")?.let { lp ->
            val listType = object : TypeToken<List<CustomLeisureActivity>>() {}.type
            lp.getAsJsonArray("customMusicActivities")?.let { arr ->
                val activities: List<CustomLeisureActivity> = gson.fromJson(arr, listType)
                val existingLabels = leisurePreferences.getCustomMusicActivities()
                    .first().map { it.label.lowercase() }.toMutableSet()
                activities.forEach {
                    if (it.label.lowercase() !in existingLabels) {
                        leisurePreferences.addMusicActivity(it.label, it.icon)
                        existingLabels.add(it.label.lowercase())
                    } else {
                        ctx.duplicatesSkipped++
                    }
                }
            }
            lp.getAsJsonArray("customFlexActivities")?.let { arr ->
                val activities: List<CustomLeisureActivity> = gson.fromJson(arr, listType)
                val existingLabels = leisurePreferences.getCustomFlexActivities()
                    .first().map { it.label.lowercase() }.toMutableSet()
                activities.forEach {
                    if (it.label.lowercase() !in existingLabels) {
                        leisurePreferences.addFlexActivity(it.label, it.icon)
                        existingLabels.add(it.label.lowercase())
                    } else {
                        ctx.duplicatesSkipped++
                    }
                }
            }
        }
    }

    private suspend fun importMedicationConfig(config: JsonObject) {
        config.getAsJsonObject("medication")?.let { med ->
            med.get("reminderIntervalMinutes")?.takeIf { !it.isJsonNull }?.asInt?.let { medicationPreferences.setReminderIntervalMinutes(it) }
            med.get("scheduleMode")?.takeIf { !it.isJsonNull }?.asString?.let {
                try { medicationPreferences.setScheduleMode(MedicationScheduleMode.valueOf(it)) } catch (_: Exception) {}
            }
            med.getAsJsonArray("specificTimes")?.let { arr ->
                medicationPreferences.setSpecificTimes(arr.mapNotNull { if (it.isJsonNull) null else it.asString }.toSet())
            }
        }
    }

    private suspend fun importCalendarConfig(config: JsonObject) {
        config.getAsJsonObject("calendar")?.let { cal ->
            cal.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean?.let { calendarPreferences.setEnabled(it) }
            cal.get("calendarId")?.takeIf { !it.isJsonNull }?.asLong?.let { calendarPreferences.setCalendarId(it) }
            cal.get("calendarName")?.takeIf { !it.isJsonNull }?.asString?.let { calendarPreferences.setCalendarName(it) }
        }
    }

    private suspend fun importUserPreferencesConfig(config: JsonObject) {
        config.getAsJsonObject("userPreferences")?.let { userPrefs ->
            importAppearancePrefs(userPrefs)
            importSwipePrefs(userPrefs)
            importTaskDefaultsPrefs(userPrefs)
            importQuickAddPrefs(userPrefs)
            importWorkLifeBalancePrefs(userPrefs)
        }
    }

    private suspend fun importAppearancePrefs(userPrefs: JsonObject) {
        userPrefs.getAsJsonObject("appearance")?.let { a ->
            val current = userPreferencesDataStore.appearanceFlow.first()
            userPreferencesDataStore.setAppearance(
                com.averycorp.prismtask.data.preferences.AppearancePrefs(
                    compactMode = a.get("compactMode")?.takeIf { !it.isJsonNull }?.asBoolean ?: current.compactMode,
                    showTaskCardBorders = a.get("showTaskCardBorders")?.takeIf { !it.isJsonNull }?.asBoolean ?: current.showTaskCardBorders,
                    cardCornerRadius = a.get("cardCornerRadius")?.takeIf { !it.isJsonNull }?.asInt ?: current.cardCornerRadius
                )
            )
        }
    }

    private suspend fun importSwipePrefs(userPrefs: JsonObject) {
        userPrefs.getAsJsonObject("swipe")?.let { s ->
            userPreferencesDataStore.setSwipe(
                com.averycorp.prismtask.data.preferences.SwipePrefs(
                    right = com.averycorp.prismtask.domain.model.SwipeAction.fromName(
                        s.get("right")?.takeIf { !it.isJsonNull }?.asString
                    ),
                    left = com.averycorp.prismtask.domain.model.SwipeAction.fromName(
                        s.get("left")?.takeIf { !it.isJsonNull }?.asString
                            ?: com.averycorp.prismtask.domain.model.SwipeAction.DELETE.name
                    )
                )
            )
        }
    }

    private suspend fun importTaskDefaultsPrefs(userPrefs: JsonObject) {
        userPrefs.getAsJsonObject("taskDefaults")?.let { d ->
            val current = userPreferencesDataStore.taskDefaultsFlow.first()
            userPreferencesDataStore.setTaskDefaults(
                com.averycorp.prismtask.data.preferences.TaskDefaults(
                    defaultPriority = d.get("defaultPriority")?.takeIf { !it.isJsonNull }?.asInt ?: current.defaultPriority,
                    defaultReminderOffset = d.get("defaultReminderOffset")?.takeIf { !it.isJsonNull }?.asLong ?: current.defaultReminderOffset,
                    defaultProjectId = d.get("defaultProjectId")?.takeIf { !it.isJsonNull }?.asLong ?: current.defaultProjectId,
                    startOfWeek = com.averycorp.prismtask.domain.model.StartOfWeek.fromName(
                        d.get("startOfWeek")?.takeIf { !it.isJsonNull }?.asString
                    ),
                    defaultDuration = d.get("defaultDuration")?.takeIf { !it.isJsonNull }?.asInt ?: current.defaultDuration,
                    autoSetDueDate = com.averycorp.prismtask.domain.model.AutoDueDate.fromName(
                        d.get("autoSetDueDate")?.takeIf { !it.isJsonNull }?.asString
                    ),
                    smartDefaultsEnabled = d.get("smartDefaultsEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: current.smartDefaultsEnabled
                )
            )
        }
    }

    private suspend fun importQuickAddPrefs(userPrefs: JsonObject) {
        userPrefs.getAsJsonObject("quickAdd")?.let { q ->
            userPreferencesDataStore.setQuickAdd(
                com.averycorp.prismtask.data.preferences.QuickAddPrefs(
                    showConfirmation = q.get("showConfirmation")?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                    autoAssignProject = q.get("autoAssignProject")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                )
            )
        }
    }

    private suspend fun importWorkLifeBalancePrefs(userPrefs: JsonObject) {
        userPrefs.getAsJsonObject("workLifeBalance")?.let { w ->
            val current = userPreferencesDataStore.workLifeBalanceFlow.first()
            userPreferencesDataStore.setWorkLifeBalance(
                com.averycorp.prismtask.data.preferences.WorkLifeBalancePrefs(
                    workTarget = w.get("workTarget")?.takeIf { !it.isJsonNull }?.asInt ?: current.workTarget,
                    personalTarget = w.get("personalTarget")?.takeIf { !it.isJsonNull }?.asInt ?: current.personalTarget,
                    selfCareTarget = w.get("selfCareTarget")?.takeIf { !it.isJsonNull }?.asInt ?: current.selfCareTarget,
                    healthTarget = w.get("healthTarget")?.takeIf { !it.isJsonNull }?.asInt ?: current.healthTarget,
                    autoClassifyEnabled = w.get("autoClassifyEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: current.autoClassifyEnabled,
                    showBalanceBar = w.get("showBalanceBar")?.takeIf { !it.isJsonNull }?.asBoolean ?: current.showBalanceBar,
                    overloadThresholdPct = w.get("overloadThresholdPct")?.takeIf { !it.isJsonNull }?.asInt ?: current.overloadThresholdPct
                )
            )
        }
    }
}
