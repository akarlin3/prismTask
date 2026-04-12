package com.averycorp.prismtask.data.export

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskCompletionDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.preferences.ArchivePreferences
import com.averycorp.prismtask.data.preferences.CalendarPreferences
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.MedicationPreferences
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports all app data to JSON.
 *
 * === Export format (version 3) ===
 * As of v3, every entity is serialized using [com.google.gson.Gson.toJsonTree] directly
 * on the entity object. This means that whenever a new field is added to any `*Entity`
 * class, it is automatically included in the export without any changes to this file.
 *
 * Foreign-key relationships (which reference auto-generated IDs that won't match after
 * import) are written as sibling helper fields prefixed with an underscore, e.g.
 * `_projectName`, `_tagNames`, `_habitName`, `_courseName`. Helper fields are resolved
 * back to the correct IDs on import.
 *
 * === Backwards compatibility ===
 * Older exports (v1 and v2) use a hand-rolled flat field mapping. [DataImporter] detects
 * the `version` field and falls back to the legacy import path for those files.
 *
 * When evolving an entity, prefer additive changes (add nullable fields or fields with
 * defaults). Gson merges imported JSON onto a default instance, so missing fields in an
 * older export automatically pick up the new Kotlin constructor defaults on import.
 */
@Singleton
class DataExporter @Inject constructor(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val taskCompletionDao: TaskCompletionDao,
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
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    suspend fun exportToJson(): String {
        val root = JsonObject()
        root.addProperty("version", EXPORT_VERSION)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.addProperty("appVersion", "0.7.1")

        // === Tasks ===
        val tasks = taskDao.getAllTasksOnce()
        val projects = projectDao.getAllProjectsOnce()
        val tags = tagDao.getAllTagsOnce()
        val projectNameById = projects.associate { it.id to it.name }

        val tasksArr = JsonArray()
        for (task in tasks) {
            val obj = gson.toJsonTree(task).asJsonObject
            // Helper fields for cross-table references (IDs won't survive a round trip).
            obj.addProperty("_projectName", task.projectId?.let { projectNameById[it] })
            val tagNames = tagDao.getTagIdsForTaskOnce(task.id)
                .mapNotNull { id -> tags.find { it.id == id }?.name }
            obj.add("_tagNames", gson.toJsonTree(tagNames))
            tasksArr.add(obj)
        }
        root.add("tasks", tasksArr)

        root.add("projects", gson.toJsonTree(projects))
        root.add("tags", gson.toJsonTree(tags))

        // === Task Completions ===
        root.add("taskCompletions", gson.toJsonTree(taskCompletionDao.getAllCompletionsOnce()))

        // === Habits ===
        val habits = habitDao.getAllHabitsOnce()
        root.add("habits", gson.toJsonTree(habits))

        // === Habit Completions ===
        val habitNameById = habits.associate { it.id to it.name }
        val habitCompletions = habitCompletionDao.getAllCompletionsOnce()
        val completionsArr = JsonArray()
        for (c in habitCompletions) {
            val obj = gson.toJsonTree(c).asJsonObject
            obj.addProperty("_habitName", habitNameById[c.habitId])
            completionsArr.add(obj)
        }
        root.add("habitCompletions", completionsArr)

        // === Habit Logs ===
        val habitLogs = habitLogDao.getAllLogsOnce()
        val logsArr = JsonArray()
        for (log in habitLogs) {
            val obj = gson.toJsonTree(log).asJsonObject
            obj.addProperty("_habitName", habitNameById[log.habitId])
            logsArr.add(obj)
        }
        root.add("habitLogs", logsArr)

        // === Leisure Logs ===
        root.add("leisureLogs", gson.toJsonTree(leisureDao.getAllLogsOnce()))

        // === Self-Care Logs & Steps ===
        root.add("selfCareLogs", gson.toJsonTree(selfCareDao.getAllLogsOnce()))
        root.add("selfCareSteps", gson.toJsonTree(selfCareDao.getAllStepsOnce()))

        // === Courses / Assignments / Course Completions ===
        val courses = schoolworkDao.getAllCoursesOnce()
        val courseNameById = courses.associate { it.id to it.name }
        root.add("courses", gson.toJsonTree(courses))

        val assignments = schoolworkDao.getAllAssignmentsOnce()
        val assignmentsArr = JsonArray()
        for (a in assignments) {
            val obj = gson.toJsonTree(a).asJsonObject
            obj.addProperty("_courseName", courseNameById[a.courseId])
            assignmentsArr.add(obj)
        }
        root.add("assignments", assignmentsArr)

        val courseCompletions = schoolworkDao.getAllCompletionsOnce()
        val courseCompletionsArr = JsonArray()
        for (c in courseCompletions) {
            val obj = gson.toJsonTree(c).asJsonObject
            obj.addProperty("_courseName", courseNameById[c.courseId])
            courseCompletionsArr.add(obj)
        }
        root.add("courseCompletions", courseCompletionsArr)

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

        // Calendar
        val calendar = JsonObject()
        calendar.addProperty("enabled", calendarPreferences.isEnabled().first())
        calendar.addProperty("calendarId", calendarPreferences.getCalendarId().first())
        calendar.addProperty("calendarName", calendarPreferences.getCalendarName().first())
        config.add("calendar", calendar)

        // User Preferences (v1.3.0 customizability)
        val userPrefs = JsonObject()
        val snapshot = userPreferencesDataStore.allFlow.first()
        val appearance = JsonObject()
        appearance.addProperty("compactMode", snapshot.appearance.compactMode)
        appearance.addProperty("showTaskCardBorders", snapshot.appearance.showTaskCardBorders)
        appearance.addProperty("cardCornerRadius", snapshot.appearance.cardCornerRadius)
        userPrefs.add("appearance", appearance)
        val swipe = JsonObject()
        swipe.addProperty("right", snapshot.swipe.right.name)
        swipe.addProperty("left", snapshot.swipe.left.name)
        userPrefs.add("swipe", swipe)
        val defaults = JsonObject()
        defaults.addProperty("defaultPriority", snapshot.taskDefaults.defaultPriority)
        defaults.addProperty("defaultReminderOffset", snapshot.taskDefaults.defaultReminderOffset)
        if (snapshot.taskDefaults.defaultProjectId != null) {
            defaults.addProperty("defaultProjectId", snapshot.taskDefaults.defaultProjectId)
        }
        defaults.addProperty("startOfWeek", snapshot.taskDefaults.startOfWeek.name)
        if (snapshot.taskDefaults.defaultDuration != null) {
            defaults.addProperty("defaultDuration", snapshot.taskDefaults.defaultDuration)
        }
        defaults.addProperty("autoSetDueDate", snapshot.taskDefaults.autoSetDueDate.name)
        defaults.addProperty("smartDefaultsEnabled", snapshot.taskDefaults.smartDefaultsEnabled)
        userPrefs.add("taskDefaults", defaults)
        val quickAdd = JsonObject()
        quickAdd.addProperty("showConfirmation", snapshot.quickAdd.showConfirmation)
        quickAdd.addProperty("autoAssignProject", snapshot.quickAdd.autoAssignProject)
        userPrefs.add("quickAdd", quickAdd)
        // Work-Life Balance (v1.4.0 V1)
        val wlb = JsonObject()
        wlb.addProperty("workTarget", snapshot.workLifeBalance.workTarget)
        wlb.addProperty("personalTarget", snapshot.workLifeBalance.personalTarget)
        wlb.addProperty("selfCareTarget", snapshot.workLifeBalance.selfCareTarget)
        wlb.addProperty("healthTarget", snapshot.workLifeBalance.healthTarget)
        wlb.addProperty("autoClassifyEnabled", snapshot.workLifeBalance.autoClassifyEnabled)
        wlb.addProperty("showBalanceBar", snapshot.workLifeBalance.showBalanceBar)
        wlb.addProperty("overloadThresholdPct", snapshot.workLifeBalance.overloadThresholdPct)
        userPrefs.add("workLifeBalance", wlb)
        config.add("userPreferences", userPrefs)

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

    companion object {
        /**
         * Current export format version.
         *
         * Bump this only for breaking structural changes. Purely additive changes
         * (new entity fields, new entity collections) do NOT require a version bump
         * because [DataImporter] tolerates missing fields via [DataImporter.mergeWithDefaults].
         */
        const val EXPORT_VERSION = 3
    }
}
