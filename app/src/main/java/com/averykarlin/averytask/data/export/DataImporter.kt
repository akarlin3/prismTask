package com.averykarlin.averytask.data.export

import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.LeisureDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.SchoolworkDao
import com.averykarlin.averytask.data.local.dao.SelfCareDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.AssignmentEntity
import com.averykarlin.averytask.data.local.entity.CourseCompletionEntity
import com.averykarlin.averytask.data.local.entity.CourseEntity
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.local.entity.LeisureLogEntity
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.SelfCareLogEntity
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.preferences.BuiltInSortOrders
import com.averykarlin.averytask.data.preferences.CalendarPreferences
import com.averykarlin.averytask.data.preferences.CustomLeisureActivity
import com.averykarlin.averytask.data.preferences.DashboardPreferences
import com.averykarlin.averytask.data.preferences.HabitListPreferences
import com.averykarlin.averytask.data.preferences.LeisurePreferences
import com.averykarlin.averytask.data.preferences.MedicationPreferences
import com.averykarlin.averytask.data.preferences.MedicationScheduleMode
import com.averykarlin.averytask.data.preferences.TabPreferences
import com.averykarlin.averytask.data.preferences.TaskBehaviorPreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.preferences.UrgencyWeights
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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

@Singleton
class DataImporter @Inject constructor(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
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
    private val medicationPreferences: MedicationPreferences
) {
    suspend fun importFromJson(jsonString: String, mode: ImportMode): ImportResult {
        val errors = mutableListOf<String>()
        var tasksImported = 0
        var projectsImported = 0
        var tagsImported = 0
        var habitsImported = 0
        var habitCompletionsImported = 0
        var leisureLogsImported = 0
        var selfCareLogsImported = 0
        var selfCareStepsImported = 0
        var coursesImported = 0
        var assignmentsImported = 0
        var courseCompletionsImported = 0
        var configImported = false
        var duplicatesSkipped = 0

        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val gson = Gson()

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

            // Import projects
            val projectNameToId = mutableMapOf<String, Long>()
            existingProjects.forEach { projectNameToId[it.name.lowercase()] = it.id }

            root.getAsJsonArray("projects")?.forEach { elem ->
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in projectNameToId) {
                    duplicatesSkipped++
                } else {
                    val project = ProjectEntity(
                        name = name,
                        color = obj.get("color")?.asString ?: "#4A90D9",
                        icon = obj.get("icon")?.asString ?: "\uD83D\uDCC1",
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val id = projectDao.insert(project)
                    projectNameToId[name.lowercase()] = id
                    projectsImported++
                }
            }

            // Import tags
            val tagNameToId = mutableMapOf<String, Long>()
            existingTags.forEach { tagNameToId[it.name.lowercase()] = it.id }

            root.getAsJsonArray("tags")?.forEach { elem ->
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in tagNameToId) {
                    duplicatesSkipped++
                } else {
                    val tag = TagEntity(
                        name = name,
                        color = obj.get("color")?.asString ?: "#6B7280",
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    val id = tagDao.insert(tag)
                    tagNameToId[name.lowercase()] = id
                    tagsImported++
                }
            }

            // Import tasks
            root.getAsJsonArray("tasks")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val title = obj.get("title")?.asString ?: return@forEach
                    if (title.isBlank()) return@forEach

                    if (mode == ImportMode.MERGE) {
                        val createdAt = obj.get("createdAt")?.asLong ?: 0
                        val isDup = existingTasks.any { it.title == title && abs(it.createdAt - createdAt) < 60000 }
                        if (isDup) { duplicatesSkipped++; return@forEach }
                    }

                    val projectName = obj.get("project")?.asString
                    val projectId = projectName?.let { projectNameToId[it.lowercase()] }

                    val task = TaskEntity(
                        title = title,
                        description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString,
                        notes = obj.get("notes")?.takeIf { !it.isJsonNull }?.asString,
                        dueDate = obj.get("dueDate")?.takeIf { !it.isJsonNull }?.asLong,
                        dueTime = obj.get("dueTime")?.takeIf { !it.isJsonNull }?.asLong,
                        priority = obj.get("priority")?.asInt ?: 0,
                        isCompleted = obj.get("isCompleted")?.asBoolean ?: false,
                        projectId = projectId,
                        recurrenceRule = obj.get("recurrenceRule")?.takeIf { !it.isJsonNull }?.asString,
                        reminderOffset = obj.get("reminderOffset")?.takeIf { !it.isJsonNull }?.asLong,
                        plannedDate = obj.get("plannedDate")?.takeIf { !it.isJsonNull }?.asLong,
                        estimatedDuration = obj.get("estimatedDuration")?.takeIf { !it.isJsonNull }?.asInt,
                        scheduledStartTime = obj.get("scheduledStartTime")?.takeIf { !it.isJsonNull }?.asLong,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        completedAt = obj.get("completedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        archivedAt = obj.get("archivedAt")?.takeIf { !it.isJsonNull }?.asLong
                    )
                    val taskId = taskDao.insert(task)

                    obj.getAsJsonArray("tags")?.forEach { tagElem ->
                        val tagName = tagElem.asString
                        val tagId = tagNameToId[tagName.lowercase()]
                        if (tagId != null) {
                            tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
                        }
                    }

                    tasksImported++
                } catch (e: Exception) {
                    errors.add("Failed to import task: ${e.message}")
                }
            }

            // Import habits
            val habitNameToId = mutableMapOf<String, Long>()
            if (mode == ImportMode.MERGE) {
                habitDao.getAllHabitsOnce().forEach { habitNameToId[it.name.lowercase()] = it.id }
            }

            root.getAsJsonArray("habits")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val name = obj.get("name")?.asString ?: return@forEach
                    if (mode == ImportMode.MERGE && name.lowercase() in habitNameToId) {
                        duplicatesSkipped++
                        return@forEach
                    }
                    val habit = HabitEntity(
                        name = name,
                        description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString,
                        targetFrequency = obj.get("targetFrequency")?.asInt ?: 1,
                        frequencyPeriod = obj.get("frequencyPeriod")?.asString ?: "daily",
                        activeDays = obj.get("activeDays")?.takeIf { !it.isJsonNull }?.asString,
                        color = obj.get("color")?.asString ?: "#4A90D9",
                        icon = obj.get("icon")?.asString ?: "\u2B50",
                        reminderTime = obj.get("reminderTime")?.takeIf { !it.isJsonNull }?.asLong,
                        sortOrder = obj.get("sortOrder")?.asInt ?: 0,
                        isArchived = obj.get("isArchived")?.asBoolean ?: false,
                        category = obj.get("category")?.takeIf { !it.isJsonNull }?.asString,
                        createDailyTask = obj.get("createDailyTask")?.asBoolean ?: false,
                        reminderIntervalMillis = obj.get("reminderIntervalMillis")?.takeIf { !it.isJsonNull }?.asLong,
                        reminderTimesPerDay = obj.get("reminderTimesPerDay")?.asInt ?: 1,
                        hasLogging = obj.get("hasLogging")?.asBoolean ?: false,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        updatedAt = obj.get("updatedAt")?.asLong ?: System.currentTimeMillis()
                    )
                    val id = habitDao.insert(habit)
                    habitNameToId[name.lowercase()] = id
                    habitsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import habit: ${e.message}")
                }
            }

            // Import habit completions
            root.getAsJsonArray("habitCompletions")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val habitName = obj.get("habitName")?.asString ?: return@forEach
                    val habitId = habitNameToId[habitName.lowercase()] ?: return@forEach
                    val completedDate = obj.get("completedDate")?.asLong ?: return@forEach
                    val completion = HabitCompletionEntity(
                        habitId = habitId,
                        completedDate = completedDate,
                        completedAt = obj.get("completedAt")?.asLong ?: System.currentTimeMillis(),
                        notes = obj.get("notes")?.takeIf { !it.isJsonNull }?.asString
                    )
                    habitCompletionDao.insert(completion)
                    habitCompletionsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import habit completion: ${e.message}")
                }
            }

            // Import leisure logs
            root.getAsJsonArray("leisureLogs")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val date = obj.get("date")?.asLong ?: return@forEach
                    val log = LeisureLogEntity(
                        date = date,
                        musicPick = obj.get("musicPick")?.takeIf { !it.isJsonNull }?.asString,
                        musicDone = obj.get("musicDone")?.asBoolean ?: false,
                        flexPick = obj.get("flexPick")?.takeIf { !it.isJsonNull }?.asString,
                        flexDone = obj.get("flexDone")?.asBoolean ?: false,
                        startedAt = obj.get("startedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    leisureDao.insertLog(log)
                    leisureLogsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import leisure log: ${e.message}")
                }
            }

            // Import self-care logs
            root.getAsJsonArray("selfCareLogs")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val log = SelfCareLogEntity(
                        routineType = obj.get("routineType")?.asString ?: return@forEach,
                        date = obj.get("date")?.asLong ?: return@forEach,
                        selectedTier = obj.get("selectedTier")?.asString ?: "solid",
                        completedSteps = obj.get("completedSteps")?.asString ?: "[]",
                        isComplete = obj.get("isComplete")?.asBoolean ?: false,
                        startedAt = obj.get("startedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    selfCareDao.insertLog(log)
                    selfCareLogsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import self-care log: ${e.message}")
                }
            }

            // Import self-care steps
            root.getAsJsonArray("selfCareSteps")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val step = SelfCareStepEntity(
                        stepId = obj.get("stepId")?.asString ?: return@forEach,
                        routineType = obj.get("routineType")?.asString ?: return@forEach,
                        label = obj.get("label")?.asString ?: return@forEach,
                        duration = obj.get("duration")?.asString ?: "0",
                        tier = obj.get("tier")?.asString ?: "solid",
                        note = obj.get("note")?.asString ?: "",
                        phase = obj.get("phase")?.asString ?: "",
                        sortOrder = obj.get("sortOrder")?.asInt ?: 0,
                        reminderDelayMillis = obj.get("reminderDelayMillis")?.takeIf { !it.isJsonNull }?.asLong,
                        timeOfDay = obj.get("timeOfDay")?.asString ?: "morning"
                    )
                    selfCareDao.insertStep(step)
                    selfCareStepsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import self-care step: ${e.message}")
                }
            }

            // Import courses
            val courseNameToId = mutableMapOf<String, Long>()
            if (mode == ImportMode.MERGE) {
                schoolworkDao.getAllCoursesOnce().forEach { courseNameToId[it.name.lowercase()] = it.id }
            }

            root.getAsJsonArray("courses")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val name = obj.get("name")?.asString ?: return@forEach
                    if (mode == ImportMode.MERGE && name.lowercase() in courseNameToId) {
                        duplicatesSkipped++
                        return@forEach
                    }
                    val course = CourseEntity(
                        name = name,
                        code = obj.get("code")?.asString ?: "",
                        color = obj.get("color")?.asInt ?: 0,
                        icon = obj.get("icon")?.asString ?: "\uD83D\uDCDA",
                        active = obj.get("active")?.asBoolean ?: true,
                        sortOrder = obj.get("sortOrder")?.asInt ?: 0,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    val id = schoolworkDao.insertCourse(course)
                    courseNameToId[name.lowercase()] = id
                    coursesImported++
                } catch (e: Exception) {
                    errors.add("Failed to import course: ${e.message}")
                }
            }

            // Import assignments
            root.getAsJsonArray("assignments")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val courseName = obj.get("courseName")?.asString ?: return@forEach
                    val courseId = courseNameToId[courseName.lowercase()] ?: return@forEach
                    val assignment = AssignmentEntity(
                        courseId = courseId,
                        title = obj.get("title")?.asString ?: return@forEach,
                        dueDate = obj.get("dueDate")?.takeIf { !it.isJsonNull }?.asLong,
                        completed = obj.get("completed")?.asBoolean ?: false,
                        completedAt = obj.get("completedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        notes = obj.get("notes")?.takeIf { !it.isJsonNull }?.asString,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    schoolworkDao.insertAssignment(assignment)
                    assignmentsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import assignment: ${e.message}")
                }
            }

            // Import course completions
            root.getAsJsonArray("courseCompletions")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val courseName = obj.get("courseName")?.asString ?: return@forEach
                    val courseId = courseNameToId[courseName.lowercase()] ?: return@forEach
                    val completion = CourseCompletionEntity(
                        date = obj.get("date")?.asLong ?: return@forEach,
                        courseId = courseId,
                        completed = obj.get("completed")?.asBoolean ?: false,
                        completedAt = obj.get("completedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    schoolworkDao.insertCompletion(completion)
                    courseCompletionsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import course completion: ${e.message}")
                }
            }

            // Import config/preferences
            root.getAsJsonObject("config")?.let { config ->
                try {
                    // Theme
                    config.getAsJsonObject("theme")?.let { theme ->
                        theme.get("themeMode")?.asString?.let { themePreferences.setThemeMode(it) }
                        theme.get("accentColor")?.asString?.let { themePreferences.setAccentColor(it) }
                        theme.get("backgroundColor")?.asString?.let { themePreferences.setBackgroundColor(it) }
                        theme.get("surfaceColor")?.asString?.let { themePreferences.setSurfaceColor(it) }
                        theme.get("errorColor")?.asString?.let { themePreferences.setErrorColor(it) }
                        theme.get("fontScale")?.asFloat?.let { themePreferences.setFontScale(it) }
                        theme.get("priorityColorNone")?.asString?.let { themePreferences.setPriorityColor(0, it) }
                        theme.get("priorityColorLow")?.asString?.let { themePreferences.setPriorityColor(1, it) }
                        theme.get("priorityColorMedium")?.asString?.let { themePreferences.setPriorityColor(2, it) }
                        theme.get("priorityColorHigh")?.asString?.let { themePreferences.setPriorityColor(3, it) }
                        theme.get("priorityColorUrgent")?.asString?.let { themePreferences.setPriorityColor(4, it) }
                    }

                    // Archive
                    config.getAsJsonObject("archive")?.let { archive ->
                        archive.get("autoArchiveDays")?.asInt?.let { archivePreferences.setAutoArchiveDays(it) }
                    }

                    // Dashboard
                    config.getAsJsonObject("dashboard")?.let { dashboard ->
                        dashboard.get("sectionOrder")?.asString?.let { order ->
                            dashboardPreferences.setSectionOrder(order.split(",").filter { it.isNotBlank() })
                        }
                        dashboard.getAsJsonArray("hiddenSections")?.let { arr ->
                            dashboardPreferences.setHiddenSections(arr.map { it.asString }.toSet())
                        }
                        dashboard.get("progressStyle")?.asString?.let { dashboardPreferences.setProgressStyle(it) }
                    }

                    // Tabs
                    config.getAsJsonObject("tabs")?.let { tabs ->
                        tabs.get("tabOrder")?.asString?.let { order ->
                            tabPreferences.setTabOrder(order.split(",").filter { it.isNotBlank() })
                        }
                        tabs.getAsJsonArray("hiddenTabs")?.let { arr ->
                            tabPreferences.setHiddenTabs(arr.map { it.asString }.toSet())
                        }
                    }

                    // Task Behavior
                    config.getAsJsonObject("taskBehavior")?.let { tb ->
                        tb.get("defaultSort")?.asString?.let { taskBehaviorPreferences.setDefaultSort(it) }
                        tb.get("defaultViewMode")?.asString?.let { taskBehaviorPreferences.setDefaultViewMode(it) }
                        val dueDate = tb.get("urgencyWeightDueDate")?.asFloat ?: 0.40f
                        val priority = tb.get("urgencyWeightPriority")?.asFloat ?: 0.30f
                        val age = tb.get("urgencyWeightAge")?.asFloat ?: 0.15f
                        val subtasks = tb.get("urgencyWeightSubtasks")?.asFloat ?: 0.15f
                        taskBehaviorPreferences.setUrgencyWeights(UrgencyWeights(dueDate, priority, age, subtasks))
                        tb.get("reminderPresets")?.asString?.let { presets ->
                            taskBehaviorPreferences.setReminderPresets(presets.split(",").mapNotNull { it.trim().toLongOrNull() })
                        }
                        tb.get("firstDayOfWeek")?.asString?.let {
                            try { taskBehaviorPreferences.setFirstDayOfWeek(DayOfWeek.valueOf(it)) } catch (_: Exception) {}
                        }
                        tb.get("dayStartHour")?.asInt?.let { taskBehaviorPreferences.setDayStartHour(it) }
                    }

                    // Habit List
                    config.getAsJsonObject("habitList")?.let { hl ->
                        habitListPreferences.setBuiltInSortOrders(BuiltInSortOrders(
                            morning = hl.get("morningSortOrder")?.asInt ?: -6,
                            bedtime = hl.get("bedtimeSortOrder")?.asInt ?: -5,
                            medication = hl.get("medicationSortOrder")?.asInt ?: -4,
                            school = hl.get("schoolSortOrder")?.asInt ?: -2,
                            leisure = hl.get("leisureSortOrder")?.asInt ?: -1,
                            housework = hl.get("houseworkSortOrder")?.asInt ?: -3
                        ))
                        hl.get("selfCareEnabled")?.asBoolean?.let { habitListPreferences.setSelfCareEnabled(it) }
                        hl.get("medicationEnabled")?.asBoolean?.let { habitListPreferences.setMedicationEnabled(it) }
                        hl.get("schoolEnabled")?.asBoolean?.let { habitListPreferences.setSchoolEnabled(it) }
                        hl.get("leisureEnabled")?.asBoolean?.let { habitListPreferences.setLeisureEnabled(it) }
                        hl.get("houseworkEnabled")?.asBoolean?.let { habitListPreferences.setHouseworkEnabled(it) }
                    }

                    // Leisure preferences
                    config.getAsJsonObject("leisure")?.let { lp ->
                        val listType = object : TypeToken<List<CustomLeisureActivity>>() {}.type
                        lp.getAsJsonArray("customMusicActivities")?.let { arr ->
                            val activities: List<CustomLeisureActivity> = gson.fromJson(arr, listType)
                            activities.forEach { leisurePreferences.addMusicActivity(it.label, it.icon) }
                        }
                        lp.getAsJsonArray("customFlexActivities")?.let { arr ->
                            val activities: List<CustomLeisureActivity> = gson.fromJson(arr, listType)
                            activities.forEach { leisurePreferences.addFlexActivity(it.label, it.icon) }
                        }
                    }

                    // Medication preferences
                    config.getAsJsonObject("medication")?.let { med ->
                        med.get("reminderIntervalMinutes")?.asInt?.let { medicationPreferences.setReminderIntervalMinutes(it) }
                        med.get("scheduleMode")?.asString?.let {
                            try { medicationPreferences.setScheduleMode(MedicationScheduleMode.valueOf(it)) } catch (_: Exception) {}
                        }
                        med.getAsJsonArray("specificTimes")?.let { arr ->
                            medicationPreferences.setSpecificTimes(arr.map { it.asString }.toSet())
                        }
                    }

                    configImported = true
                } catch (e: Exception) {
                    errors.add("Failed to import config: ${e.message}")
                }
            }

        } catch (e: Exception) {
            errors.add("Import failed: ${e.message}")
        }

        return ImportResult(
            tasksImported = tasksImported,
            projectsImported = projectsImported,
            tagsImported = tagsImported,
            habitsImported = habitsImported,
            habitCompletionsImported = habitCompletionsImported,
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
}
