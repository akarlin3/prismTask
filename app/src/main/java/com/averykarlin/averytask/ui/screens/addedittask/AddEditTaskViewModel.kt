package com.averykarlin.averytask.ui.screens.addedittask

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.averykarlin.averytask.data.local.converter.RecurrenceConverter
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import com.averykarlin.averytask.domain.model.RecurrenceRule
import com.averykarlin.averytask.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val reminderScheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val taskId: Long? = savedStateHandle.get<Long>("taskId")?.takeIf { it != -1L }
    val isEditMode: Boolean = taskId != null

    private var existingTask: TaskEntity? = null

    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var dueDate by mutableStateOf<Long?>(null)
        private set
    var dueTime by mutableStateOf<Long?>(null)
        private set
    var priority by mutableIntStateOf(0)
        private set
    var projectId by mutableStateOf<Long?>(null)
        private set
    var parentTaskId by mutableStateOf<Long?>(null)
        private set
    var recurrenceRule by mutableStateOf<RecurrenceRule?>(null)
        private set
    var reminderOffset by mutableStateOf<Long?>(null)
        private set
    var titleError by mutableStateOf(false)
        private set

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (taskId != null) {
            viewModelScope.launch {
                taskRepository.getTaskById(taskId).firstOrNull()?.let { task ->
                    existingTask = task
                    title = task.title
                    description = task.description.orEmpty()
                    dueDate = task.dueDate
                    dueTime = task.dueTime
                    priority = task.priority
                    projectId = task.projectId
                    parentTaskId = task.parentTaskId
                    recurrenceRule = task.recurrenceRule?.let { RecurrenceConverter.fromJson(it) }
                    reminderOffset = task.reminderOffset
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value
        if (value.isNotBlank()) titleError = false
    }

    fun onDescriptionChange(value: String) { description = value }
    fun onDueDateChange(value: Long?) { dueDate = value }
    fun onDueTimeChange(value: Long?) { dueTime = value }
    fun onPriorityChange(value: Int) { priority = value }
    fun onProjectIdChange(value: Long?) { projectId = value }
    fun onRecurrenceRuleChange(value: RecurrenceRule?) { recurrenceRule = value }
    fun onReminderOffsetChange(value: Long?) { reminderOffset = value }

    suspend fun saveTask(): Boolean {
        if (title.isBlank()) {
            titleError = true
            return false
        }

        return try {
            val trimmedTitle = title.trim()
            val trimmedDesc = description.trim().ifEmpty { null }
            val recurrenceJson = recurrenceRule?.let { RecurrenceConverter.toJson(it) }
            val existing = existingTask
            val savedId: Long
            if (existing != null) {
                taskRepository.updateTask(
                    existing.copy(
                        title = trimmedTitle,
                        description = trimmedDesc,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        priority = priority,
                        projectId = projectId,
                        parentTaskId = parentTaskId,
                        reminderOffset = reminderOffset,
                        recurrenceRule = recurrenceJson
                    )
                )
                savedId = existing.id
            } else {
                savedId = taskRepository.addTask(
                    title = trimmedTitle,
                    description = trimmedDesc,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    priority = priority,
                    projectId = projectId,
                    parentTaskId = parentTaskId
                )
                // Update reminder offset and recurrence on the newly created task
                if (reminderOffset != null || recurrenceJson != null) {
                    taskRepository.getTaskById(savedId).firstOrNull()?.let { created ->
                        taskRepository.updateTask(
                            created.copy(
                                reminderOffset = reminderOffset ?: created.reminderOffset,
                                recurrenceRule = recurrenceJson ?: created.recurrenceRule
                            )
                        )
                    }
                }
            }

            // Schedule or cancel reminder
            if (reminderOffset != null && dueDate != null) {
                reminderScheduler.scheduleReminder(savedId, trimmedTitle, trimmedDesc, dueDate!!, reminderOffset!!)
            } else {
                reminderScheduler.cancelReminder(savedId)
            }

            true
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to save task", e)
            _errorMessages.emit("Something went wrong")
            false
        }
    }

    suspend fun deleteTask() {
        try {
            taskId?.let { taskRepository.deleteTask(it) }
        } catch (e: Exception) {
            Log.e("AddEditTaskVM", "Failed to delete task", e)
            _errorMessages.emit("Something went wrong")
        }
    }
}
