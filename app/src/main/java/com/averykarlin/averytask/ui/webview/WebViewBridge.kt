package com.averykarlin.averytask.ui.webview

import android.webkit.JavascriptInterface
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TagRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * JavascriptInterface bridge between React WebView and native Android.
 * Methods annotated with @JavascriptInterface are callable from JavaScript.
 */
class WebViewBridge(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val habitRepository: HabitRepository,
    private val tagRepository: TagRepository,
    private val themePreferences: ThemePreferences,
    private val onNavigate: (String) -> Unit,
    private val scope: CoroutineScope
) {
    private val gson = Gson()

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun endOfToday(): Long = startOfToday() + 86_400_000L

    // === Task Operations ===

    @JavascriptInterface
    fun getTasks(): String = runBlocking(Dispatchers.IO) {
        val tasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        gson.toJson(tasks)
    }

    @JavascriptInterface
    fun getOverdueTasks(): String = runBlocking(Dispatchers.IO) {
        val tasks = taskRepository.getOverdueTasks(startOfToday()).firstOrNull() ?: emptyList()
        val rootTasks = tasks.filter { it.parentTaskId == null && it.archivedAt == null }
        gson.toJson(rootTasks)
    }

    @JavascriptInterface
    fun getTodayTasks(): String = runBlocking(Dispatchers.IO) {
        val tasks = taskRepository.getTasksDueOnDate(startOfToday(), endOfToday()).firstOrNull() ?: emptyList()
        val filtered = tasks.filter { !it.isCompleted && it.parentTaskId == null && it.archivedAt == null }
        gson.toJson(filtered)
    }

    @JavascriptInterface
    fun getPlannedTasks(): String = runBlocking(Dispatchers.IO) {
        // Tasks planned for today but not due today
        val allTasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        val start = startOfToday()
        val end = endOfToday()
        val planned = allTasks.filter { task ->
            !task.isCompleted &&
            task.parentTaskId == null &&
            task.archivedAt == null &&
            task.plannedDate != null &&
            task.plannedDate >= start &&
            task.plannedDate < end &&
            (task.dueDate == null || task.dueDate >= end || task.dueDate < start)
        }
        gson.toJson(planned)
    }

    @JavascriptInterface
    fun getCompletedToday(): String = runBlocking(Dispatchers.IO) {
        val allTasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        val start = startOfToday()
        val completed = allTasks.filter { task ->
            task.isCompleted &&
            task.parentTaskId == null &&
            task.archivedAt == null &&
            task.completedAt != null &&
            task.completedAt >= start
        }
        gson.toJson(completed)
    }

    @JavascriptInterface
    fun getTasksNotInToday(): String = runBlocking(Dispatchers.IO) {
        val allTasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        val start = startOfToday()
        val end = endOfToday()
        val notInToday = allTasks.filter { task ->
            !task.isCompleted &&
            task.parentTaskId == null &&
            task.archivedAt == null &&
            (task.dueDate == null || task.dueDate >= end) &&
            (task.plannedDate == null || task.plannedDate < start || task.plannedDate >= end)
        }
        gson.toJson(notInToday)
    }

    @JavascriptInterface
    fun getSubtasks(parentTaskId: Long): String = runBlocking(Dispatchers.IO) {
        val subtasks = taskRepository.getSubtasks(parentTaskId).firstOrNull() ?: emptyList()
        gson.toJson(subtasks)
    }

    @JavascriptInterface
    fun getTaskTags(taskId: Long): String = runBlocking(Dispatchers.IO) {
        val tags = tagRepository.getTagsForTask(taskId).firstOrNull() ?: emptyList()
        gson.toJson(tags)
    }

    @JavascriptInterface
    fun completeTask(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            taskRepository.completeTask(taskId)
        }
    }

    @JavascriptInterface
    fun uncompleteTask(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            taskRepository.uncompleteTask(taskId)
        }
    }

    @JavascriptInterface
    fun deleteTask(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            taskRepository.deleteTask(taskId)
        }
    }

    @JavascriptInterface
    fun addTask(json: String) {
        scope.launch(Dispatchers.IO) {
            val data = gson.fromJson(json, Map::class.java)
            taskRepository.addTask(
                title = data["title"] as? String ?: return@launch,
                description = data["description"] as? String,
                dueDate = (data["dueDate"] as? Number)?.toLong(),
                dueTime = (data["dueTime"] as? Number)?.toLong(),
                priority = (data["priority"] as? Number)?.toInt() ?: 0,
                projectId = (data["projectId"] as? Number)?.toLong()
            )
        }
    }

    @JavascriptInterface
    fun planForToday(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            val task = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
            taskRepository.updateTask(task.copy(plannedDate = startOfToday()))
        }
    }

    @JavascriptInterface
    fun removeFromToday(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            val task = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
            taskRepository.updateTask(task.copy(plannedDate = null))
        }
    }

    // === Project Operations ===

    @JavascriptInterface
    fun getProjects(): String = runBlocking(Dispatchers.IO) {
        val projects = projectRepository.getAllProjects().firstOrNull() ?: emptyList()
        gson.toJson(projects)
    }

    @JavascriptInterface
    fun addProject(json: String) {
        scope.launch(Dispatchers.IO) {
            val data = gson.fromJson(json, Map::class.java)
            projectRepository.addProject(
                name = data["name"] as? String ?: return@launch,
                color = data["color"] as? String ?: "#4A90D9",
                icon = data["icon"] as? String ?: "\uD83D\uDCC1"
            )
        }
    }

    @JavascriptInterface
    fun deleteProject(projectId: Long) {
        scope.launch(Dispatchers.IO) {
            val project = projectRepository.getProjectById(projectId).firstOrNull() ?: return@launch
            projectRepository.deleteProject(project)
        }
    }

    // === Habit Operations ===

    @JavascriptInterface
    fun getHabitsWithStatus(): String = runBlocking(Dispatchers.IO) {
        val habits = habitRepository.getHabitsWithFullStatus().firstOrNull() ?: emptyList()
        gson.toJson(habits)
    }

    @JavascriptInterface
    fun toggleHabitCompletion(habitId: Long, isCompleted: Boolean) {
        scope.launch(Dispatchers.IO) {
            val today = startOfToday()
            if (isCompleted) {
                habitRepository.uncompleteHabit(habitId, today)
            } else {
                habitRepository.completeHabit(habitId, today)
            }
        }
    }

    @JavascriptInterface
    fun deleteHabit(habitId: Long) {
        scope.launch(Dispatchers.IO) {
            habitRepository.deleteHabit(habitId)
        }
    }

    // === Tag Operations ===

    @JavascriptInterface
    fun getTags(): String = runBlocking(Dispatchers.IO) {
        val tags = tagRepository.getAllTags().firstOrNull() ?: emptyList()
        gson.toJson(tags)
    }

    // === Settings ===

    @JavascriptInterface
    fun getTheme(): String = runBlocking(Dispatchers.IO) {
        themePreferences.getThemeMode().firstOrNull() ?: "dark"
    }

    @JavascriptInterface
    fun setTheme(mode: String) {
        scope.launch(Dispatchers.IO) {
            themePreferences.setThemeMode(mode)
        }
    }

    @JavascriptInterface
    fun getAccentColor(): String = runBlocking(Dispatchers.IO) {
        themePreferences.getAccentColor().firstOrNull() ?: "#4A90D9"
    }

    @JavascriptInterface
    fun setAccentColor(color: String) {
        scope.launch(Dispatchers.IO) {
            themePreferences.setAccentColor(color)
        }
    }

    @JavascriptInterface
    fun getAuthState(): String {
        return gson.toJson(mapOf("signedIn" to false))
    }

    @JavascriptInterface
    fun getVersion(): String = "v0.5.0"

    @JavascriptInterface
    fun exportJson() {
        // Handled by native navigation
        onNavigate("export_json")
    }

    @JavascriptInterface
    fun exportCsv() {
        onNavigate("export_csv")
    }

    @JavascriptInterface
    fun importJson() {
        onNavigate("import_json")
    }

    @JavascriptInterface
    fun syncNow() {
        onNavigate("sync_now")
    }

    @JavascriptInterface
    fun signIn() {
        onNavigate("auth")
    }

    @JavascriptInterface
    fun signOut() {
        onNavigate("sign_out")
    }

    // === Navigation ===

    @JavascriptInterface
    fun navigate(route: String) {
        onNavigate(route)
    }
}
