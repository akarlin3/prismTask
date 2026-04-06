package com.averykarlin.averytask.data.export

import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.LeisureDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.SchoolworkDao
import com.averykarlin.averytask.data.local.dao.SelfCareDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.preferences.CalendarPreferences
import com.averykarlin.averytask.data.preferences.DashboardPreferences
import com.averykarlin.averytask.data.preferences.HabitListPreferences
import com.averykarlin.averytask.data.preferences.LeisurePreferences
import com.averykarlin.averytask.data.preferences.MedicationPreferences
import com.averykarlin.averytask.data.preferences.TabPreferences
import com.averykarlin.averytask.data.preferences.TaskBehaviorPreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExporter @Inject constructor(
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
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJson(): String {
        val root = JsonObject()
        root.addProperty("version", 2)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.addProperty("appVersion", "0.7.0")

        // === Tasks ===
        val tasks = taskDao.getAllTasksOnce()
        val projects = projectDao.getAllProjectsOnce()
        val tags = tagDao.getAllTagsOnce()

        root.add("tasks", gson.toJsonTree(tasks.map { task ->
            val taskTags = tagDao.getTagIdsForTaskOnce(task.id)
            val tagNames = taskTags.mapNotNull { id -> tags.find { it.id == id }?.name }
            val projectName = task.projectId?.let { pid -> projects.find { it.id == pid }?.name }
            mapOf(
                "title" to task.title,
                "description" to task.description,
                "notes" to task.notes,
                "dueDate" to task.dueDate,
                "dueTime" to task.dueTime,
                "priority" to task.priority,
                "isCompleted" to task.isCompleted,
                "project" to projectName,
                "tags" to tagNames,
                "recurrenceRule" to task.recurrenceRule,
                "reminderOffset" to task.reminderOffset,
                "plannedDate" to task.plannedDate,
                "estimatedDuration" to task.estimatedDuration,
                "scheduledStartTime" to task.scheduledStartTime,
                "createdAt" to task.createdAt,
                "updatedAt" to task.updatedAt,
                "completedAt" to task.completedAt,
                "archivedAt" to task.archivedAt,
                "parentTaskId" to task.parentTaskId,
                "sourceHabitId" to task.sourceHabitId
            )
        }))

        root.add("projects", gson.toJsonTree(projects.map {
            mapOf("name" to it.name, "color" to it.color, "icon" to it.icon, "createdAt" to it.createdAt)
        }))

        root.add("tags", gson.toJsonTree(tags.map {
            mapOf("name" to it.name, "color" to it.color, "createdAt" to it.createdAt)
        }))

        // === Habits ===
        val habits = habitDao.getAllHabitsOnce()
        root.add("habits", gson.toJsonTree(habits.map {
            mapOf(
                "name" to it.name,
                "description" to it.description,
                "targetFrequency" to it.targetFrequency,
                "frequencyPeriod" to it.frequencyPeriod,
                "activeDays" to it.activeDays,
                "color" to it.color,
                "icon" to it.icon,
                "reminderTime" to it.reminderTime,
                "sortOrder" to it.sortOrder,
                "isArchived" to it.isArchived,
                "category" to it.category,
                "createDailyTask" to it.createDailyTask,
                "reminderIntervalMillis" to it.reminderIntervalMillis,
                "reminderTimesPerDay" to it.reminderTimesPerDay,
                "hasLogging" to it.hasLogging,
                "createdAt" to it.createdAt,
                "updatedAt" to it.updatedAt
            )
        }))

        // === Habit Completions ===
        val habitCompletions = habitCompletionDao.getAllCompletionsOnce()
        val habitNameById = habits.associate { it.id to it.name }
        root.add("habitCompletions", gson.toJsonTree(habitCompletions.map {
            mapOf(
                "habitName" to habitNameById[it.habitId],
                "completedDate" to it.completedDate,
                "completedAt" to it.completedAt,
                "notes" to it.notes
            )
        }))

        // === Leisure Logs ===
        val leisureLogs = leisureDao.getAllLogsOnce()
        root.add("leisureLogs", gson.toJsonTree(leisureLogs.map {
            mapOf(
                "date" to it.date,
                "musicPick" to it.musicPick,
                "musicDone" to it.musicDone,
                "flexPick" to it.flexPick,
                "flexDone" to it.flexDone,
                "startedAt" to it.startedAt,
                "createdAt" to it.createdAt
            )
        }))

        // === Self-Care Logs ===
        val selfCareLogs = selfCareDao.getAllLogsOnce()
        root.add("selfCareLogs", gson.toJsonTree(selfCareLogs.map {
            mapOf(
                "routineType" to it.routineType,
                "date" to it.date,
                "selectedTier" to it.selectedTier,
                "completedSteps" to it.completedSteps,
                "isComplete" to it.isComplete,
                "startedAt" to it.startedAt,
                "createdAt" to it.createdAt
            )
        }))

        // === Self-Care Steps ===
        val selfCareSteps = selfCareDao.getAllStepsOnce()
        root.add("selfCareSteps", gson.toJsonTree(selfCareSteps.map {
            mapOf(
                "stepId" to it.stepId,
                "routineType" to it.routineType,
                "label" to it.label,
                "duration" to it.duration,
                "tier" to it.tier,
                "note" to it.note,
                "phase" to it.phase,
                "sortOrder" to it.sortOrder,
                "reminderDelayMillis" to it.reminderDelayMillis,
                "timeOfDay" to it.timeOfDay
            )
        }))

        // === Courses ===
        val courses = schoolworkDao.getAllCoursesOnce()
        root.add("courses", gson.toJsonTree(courses.map {
            mapOf(
                "name" to it.name,
                "code" to it.code,
                "color" to it.color,
                "icon" to it.icon,
                "active" to it.active,
                "sortOrder" to it.sortOrder,
                "createdAt" to it.createdAt
            )
        }))

        // === Assignments ===
        val assignments = schoolworkDao.getAllAssignmentsOnce()
        val courseNameById = courses.associate { it.id to it.name }
        root.add("assignments", gson.toJsonTree(assignments.map {
            mapOf(
                "courseName" to courseNameById[it.courseId],
                "title" to it.title,
                "dueDate" to it.dueDate,
                "completed" to it.completed,
                "completedAt" to it.completedAt,
                "notes" to it.notes,
                "createdAt" to it.createdAt
            )
        }))

        // === Course Completions ===
        val courseCompletions = schoolworkDao.getAllCompletionsOnce()
        root.add("courseCompletions", gson.toJsonTree(courseCompletions.map {
            mapOf(
                "date" to it.date,
                "courseName" to courseNameById[it.courseId],
                "completed" to it.completed,
                "completedAt" to it.completedAt,
                "createdAt" to it.createdAt
            )
        }))

        // === Configurations / Preferences ===
        val config = JsonObject()

        // Theme
        val theme = JsonObject()
        theme.addProperty("themeMode", themePreferences.getThemeMode().first())
        theme.addProperty("accentColor", themePreferences.getAccentColor().first())
        theme.addProperty("backgroundColor", themePreferences.getBackgroundColor().first())
        theme.addProperty("surfaceColor", themePreferences.getSurfaceColor().first())
        theme.addProperty("errorColor", themePreferences.getErrorColor().first())
        theme.addProperty("fontScale", themePreferences.getFontScale().first())
        theme.addProperty("priorityColorNone", themePreferences.getPriorityColorNone().first())
        theme.addProperty("priorityColorLow", themePreferences.getPriorityColorLow().first())
        theme.addProperty("priorityColorMedium", themePreferences.getPriorityColorMedium().first())
        theme.addProperty("priorityColorHigh", themePreferences.getPriorityColorHigh().first())
        theme.addProperty("priorityColorUrgent", themePreferences.getPriorityColorUrgent().first())
        config.add("theme", theme)

        // Archive
        val archive = JsonObject()
        archive.addProperty("autoArchiveDays", archivePreferences.getAutoArchiveDays().first())
        config.add("archive", archive)

        // Dashboard
        val dashboard = JsonObject()
        dashboard.addProperty("sectionOrder", dashboardPreferences.getSectionOrder().first().joinToString(","))
        dashboard.add("hiddenSections", gson.toJsonTree(dashboardPreferences.getHiddenSections().first()))
        dashboard.addProperty("progressStyle", dashboardPreferences.getProgressStyle().first())
        config.add("dashboard", dashboard)

        // Tabs
        val tabs = JsonObject()
        tabs.addProperty("tabOrder", tabPreferences.getTabOrder().first().joinToString(","))
        tabs.add("hiddenTabs", gson.toJsonTree(tabPreferences.getHiddenTabs().first()))
        config.add("tabs", tabs)

        // Task Behavior
        val taskBehavior = JsonObject()
        taskBehavior.addProperty("defaultSort", taskBehaviorPreferences.getDefaultSort().first())
        taskBehavior.addProperty("defaultViewMode", taskBehaviorPreferences.getDefaultViewMode().first())
        val weights = taskBehaviorPreferences.getUrgencyWeights().first()
        taskBehavior.addProperty("urgencyWeightDueDate", weights.dueDate)
        taskBehavior.addProperty("urgencyWeightPriority", weights.priority)
        taskBehavior.addProperty("urgencyWeightAge", weights.age)
        taskBehavior.addProperty("urgencyWeightSubtasks", weights.subtasks)
        taskBehavior.addProperty("reminderPresets", taskBehaviorPreferences.getReminderPresets().first().joinToString(","))
        taskBehavior.addProperty("firstDayOfWeek", taskBehaviorPreferences.getFirstDayOfWeek().first().name)
        taskBehavior.addProperty("dayStartHour", taskBehaviorPreferences.getDayStartHour().first())
        config.add("taskBehavior", taskBehavior)

        // Habit List
        val habitList = JsonObject()
        val sortOrders = habitListPreferences.getBuiltInSortOrders().first()
        habitList.addProperty("morningSortOrder", sortOrders.morning)
        habitList.addProperty("bedtimeSortOrder", sortOrders.bedtime)
        habitList.addProperty("medicationSortOrder", sortOrders.medication)
        habitList.addProperty("schoolSortOrder", sortOrders.school)
        habitList.addProperty("leisureSortOrder", sortOrders.leisure)
        habitList.addProperty("houseworkSortOrder", sortOrders.housework)
        habitList.addProperty("selfCareEnabled", habitListPreferences.isSelfCareEnabled().first())
        habitList.addProperty("medicationEnabled", habitListPreferences.isMedicationEnabled().first())
        habitList.addProperty("schoolEnabled", habitListPreferences.isSchoolEnabled().first())
        habitList.addProperty("leisureEnabled", habitListPreferences.isLeisureEnabled().first())
        habitList.addProperty("houseworkEnabled", habitListPreferences.isHouseworkEnabled().first())
        config.add("habitList", habitList)

        // Leisure
        val leisure = JsonObject()
        leisure.add("customMusicActivities", gson.toJsonTree(leisurePreferences.getCustomMusicActivities().first()))
        leisure.add("customFlexActivities", gson.toJsonTree(leisurePreferences.getCustomFlexActivities().first()))
        config.add("leisure", leisure)

        // Medication
        val medication = JsonObject()
        medication.addProperty("reminderIntervalMinutes", medicationPreferences.getReminderIntervalMinutesOnce())
        medication.addProperty("scheduleMode", medicationPreferences.getScheduleModeOnce().name)
        medication.add("specificTimes", gson.toJsonTree(medicationPreferences.getSpecificTimesOnce()))
        config.add("medication", medication)

        root.add("config", config)

        return gson.toJson(root)
    }

    suspend fun exportToCsv(): String {
        val tasks = taskDao.getAllTasksOnce()
        val projects = projectDao.getAllProjectsOnce()
        val tags = tagDao.getAllTagsOnce()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Title,Description,Due Date,Due Time,Priority,Project,Tags,Status,Created,Completed")

        for (task in tasks) {
            val tagNames = tagDao.getTagIdsForTaskOnce(task.id)
                .mapNotNull { id -> tags.find { it.id == id }?.name }
                .joinToString("; ")
            val projectName = task.projectId?.let { pid -> projects.find { it.id == pid }?.name } ?: ""
            val status = if (task.isCompleted) "Completed" else "Incomplete"

            sb.appendLine(listOf(
                csvEscape(task.title),
                csvEscape(task.description ?: ""),
                task.dueDate?.let { dateFormat.format(Date(it)) } ?: "",
                task.dueTime?.let { dateFormat.format(Date(it)) } ?: "",
                task.priority.toString(),
                csvEscape(projectName),
                csvEscape(tagNames),
                status,
                dateFormat.format(Date(task.createdAt)),
                task.completedAt?.let { dateFormat.format(Date(it)) } ?: ""
            ).joinToString(","))
        }

        return sb.toString()
    }

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\""
        else value
}
