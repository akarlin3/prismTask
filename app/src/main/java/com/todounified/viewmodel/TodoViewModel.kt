package com.todounified.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.todounified.ai.AiParsedTask
import com.todounified.ai.JsxParserService
import com.todounified.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val lists: List<TaskListWithTasks> = emptyList(),
    val importedTabs: List<ImportedTab> = emptyList(),
    val activeListId: String? = null,
    val activeImportId: String? = null,
    val importStatus: ImportStatus = ImportStatus.Idle,
    val importError: String = "",
    val apiKey: String = ""
)

enum class ImportStatus { Idle, Reading, Parsing, Done, Error }

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val db = TodoDatabase.getDatabase(app)
    private val dao = db.todoDao()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Observe lists
        viewModelScope.launch {
            dao.getAllListsWithTasks().collect { lists ->
                _uiState.update { state ->
                    val newState = state.copy(lists = lists)
                    // Auto-select first list if none selected
                    if (newState.activeListId == null && newState.activeImportId == null && lists.isNotEmpty()) {
                        newState.copy(activeListId = lists.first().list.id)
                    } else newState
                }
            }
        }
        // Observe imported tabs
        viewModelScope.launch {
            dao.getAllImportedTabs().collect { tabs ->
                _uiState.update { it.copy(importedTabs = tabs) }
            }
        }
        // Seed default lists if empty
        viewModelScope.launch {
            if (dao.getListCount() == 0) {
                dao.insertList(TaskList(genId(), "Inbox", "📥", 0))
                dao.insertList(TaskList(genId(), "Work", "💼", 1))
                dao.insertList(TaskList(genId(), "Personal", "🏠", 2))
            }
        }
    }

    private fun genId() = UUID.randomUUID().toString()

    // ── Tab selection ──
    fun selectList(id: String) {
        _uiState.update { it.copy(activeListId = id, activeImportId = null) }
    }

    fun selectImport(id: String) {
        _uiState.update { it.copy(activeImportId = id, activeListId = null) }
    }

    // ── List CRUD ──
    fun addList(name: String) {
        val id = genId()
        val emojis = listOf("📋", "🎯", "🔬", "🎵", "🏃", "📚", "🛠", "🌟", "🧪", "🎮")
        viewModelScope.launch {
            val order = _uiState.value.lists.size
            dao.insertList(TaskList(id, name.ifBlank { "New List" }, emojis.random(), order))
            _uiState.update { it.copy(activeListId = id, activeImportId = null) }
        }
    }

    fun renameList(id: String, newName: String) {
        viewModelScope.launch {
            val list = _uiState.value.lists.find { it.list.id == id }?.list ?: return@launch
            dao.updateList(list.copy(name = newName))
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            val list = _uiState.value.lists.find { it.list.id == id }?.list ?: return@launch
            dao.deleteList(list)
            if (_uiState.value.activeListId == id) {
                val remaining = _uiState.value.lists.filter { it.list.id != id }
                _uiState.update { it.copy(activeListId = remaining.firstOrNull()?.list?.id) }
            }
        }
    }

    // ── Task CRUD ──
    fun addTask(title: String, priority: Priority, dueDate: String, tags: List<String>) {
        val listId = _uiState.value.activeListId ?: return
        viewModelScope.launch {
            dao.insertTask(Task(genId(), listId, title, false, priority, dueDate, tags))
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { dao.updateTask(task.copy(done = !task.done)) }
    }

    fun updateTask(task: Task, title: String, priority: Priority, dueDate: String, tags: List<String>) {
        viewModelScope.launch {
            dao.updateTask(task.copy(title = title, priority = priority, dueDate = dueDate, tags = tags))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.deleteTask(task) }
    }

    fun clearDoneTasks() {
        val listId = _uiState.value.activeListId ?: return
        viewModelScope.launch { dao.clearDoneTasks(listId) }
    }

    // ── Import ──
    fun setApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun importJsxFile(uri: Uri) {
        val context = getApplication<Application>()
        val apiKey = _uiState.value.apiKey

        if (apiKey.isBlank()) {
            _uiState.update { it.copy(importStatus = ImportStatus.Error, importError = "API key required. Set it in Settings.") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importStatus = ImportStatus.Reading) }

                // Read file
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsxCode = inputStream?.bufferedReader()?.readText() ?: throw Exception("Could not read file")
                inputStream.close()

                val fileName = uri.lastPathSegment ?: "unknown.jsx"

                _uiState.update { it.copy(importStatus = ImportStatus.Parsing) }

                // Call AI
                val service = JsxParserService(apiKey)
                val result = service.parseJsx(jsxCode, fileName)

                // Save to database
                val tabId = genId()
                val extractedTasks = result.tasks.map { t ->
                    AiParsedTask(t.title, t.done, t.priority, t.dueDate, t.tags)
                }

                val tab = ImportedTab(
                    id = tabId,
                    name = result.name.ifBlank { fileName.removeSuffix(".jsx").removeSuffix(".js").removeSuffix(".tsx") },
                    emoji = result.emoji.ifBlank { "📦" },
                    fileName = fileName,
                    description = result.description,
                    originalStructure = result.originalStructure,
                    renderHtml = result.renderHtml,
                    sourceCode = jsxCode,
                    extractedTasksJson = gson.toJson(extractedTasks)
                )
                dao.insertImportedTab(tab)

                _uiState.update {
                    it.copy(
                        importStatus = ImportStatus.Done,
                        activeImportId = tabId,
                        activeListId = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(importStatus = ImportStatus.Error, importError = e.message ?: "Import failed")
                }
            }
        }
    }

    fun resetImportStatus() {
        _uiState.update { it.copy(importStatus = ImportStatus.Idle, importError = "") }
    }

    fun deleteImportedTab(tab: ImportedTab) {
        viewModelScope.launch {
            dao.deleteImportedTab(tab)
            if (_uiState.value.activeImportId == tab.id) {
                val firstList = _uiState.value.lists.firstOrNull()?.list?.id
                _uiState.update { it.copy(activeImportId = null, activeListId = firstList) }
            }
        }
    }

    fun mergeImportedTasks(tab: ImportedTab, targetListId: String) {
        viewModelScope.launch {
            val type = object : TypeToken<List<AiParsedTask>>() {}.type
            val parsed: List<AiParsedTask> = gson.fromJson(tab.extractedTasksJson, type)
            val tasks = parsed.map { t ->
                Task(
                    id = genId(),
                    listId = targetListId,
                    title = t.title,
                    done = t.done,
                    priority = try { Priority.valueOf(t.priority.uppercase()) } catch (_: Exception) { Priority.MEDIUM },
                    dueDate = t.dueDate,
                    tags = t.tags
                )
            }
            dao.insertTasks(tasks)
            _uiState.update { it.copy(activeListId = targetListId, activeImportId = null) }
        }
    }

    fun getExtractedTasks(tab: ImportedTab): List<AiParsedTask> {
        return try {
            val type = object : TypeToken<List<AiParsedTask>>() {}.type
            gson.fromJson(tab.extractedTasksJson, type)
        } catch (_: Exception) { emptyList() }
    }
}
