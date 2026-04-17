package com.averycorp.prismtask.ui.screens.templates

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.google.gson.Gson
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
class AddEditTemplateViewModel
@Inject
constructor(
    private val templateRepository: TaskTemplateRepository,
    projectRepository: ProjectRepository,
    tagRepository: TagRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val templateId: Long? =
        savedStateHandle.get<Long>("templateId")?.takeIf { it != -1L }
    val isEdit: Boolean = templateId != null

    private var existingTemplate: TaskTemplateEntity? = null

    // ---- Template-level fields ----
    var name by mutableStateOf("")
        private set
    var icon by mutableStateOf(DEFAULT_TEMPLATE_ICON)
        private set
    var category by mutableStateOf("")
        private set
    var nameError by mutableStateOf(false)
        private set

    // ---- Task blueprint fields ----
    var templateTitle by mutableStateOf("")
        private set
    var templateDescription by mutableStateOf("")
        private set
    var templatePriority by mutableIntStateOf(0)
        private set
    var templateProjectId by mutableStateOf<Long?>(null)
        private set
    var templateTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var templateDuration by mutableStateOf<Int?>(null)
        private set
    var templateRecurrence by mutableStateOf<RecurrenceRule?>(null)
        private set
    var templateSubtasks by mutableStateOf<List<String>>(emptyList())
        private set

    // Existing categories on other templates, so the form can surface them as
    // quick-pick chips even when the user hasn't typed anything yet.
    var existingCategories by mutableStateOf<List<String>>(emptyList())
        private set

    val availableProjects: StateFlow<List<ProjectEntity>> = projectRepository
        .getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<TagEntity>> = tagRepository
        .getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                // Snapshot existing categories once so the form can surface
                // them as quick-pick chips. A live Flow would over-complicate
                // the simple chip row.
                existingCategories = templateRepository
                    .getAllCategories()
                    .firstOrNull()
                    ?.filter { it.isNotBlank() }
                    ?.sorted()
                    ?: emptyList()
            } catch (_: Exception) {
                // Best-effort — empty list is acceptable fallback.
            }
        }

        if (templateId != null) {
            viewModelScope.launch {
                try {
                    templateRepository.getTemplateById(templateId)?.let { template ->
                        existingTemplate = template
                        name = template.name
                        icon = template.icon ?: DEFAULT_TEMPLATE_ICON
                        category = template.category.orEmpty()
                        templateTitle = template.templateTitle.orEmpty()
                        templateDescription = template.templateDescription.orEmpty()
                        templatePriority = template.templatePriority ?: 0
                        templateProjectId = template.templateProjectId
                        templateTagIds = TaskTemplateRepository
                            .parseTagIds(template.templateTagsJson)
                            .toSet()
                        templateDuration = template.templateDuration
                        templateRecurrence = template.templateRecurrenceJson
                            ?.let { RecurrenceConverter.fromJson(it) }
                        templateSubtasks = TaskTemplateRepository
                            .parseSubtaskTitles(template.templateSubtasksJson)
                    }
                } catch (e: Exception) {
                    Log.e("AddEditTemplateVM", "Failed to load template", e)
                    _errorMessages.emit("Failed to load template")
                }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        if (value.isNotBlank()) nameError = false
    }

    fun onIconChange(value: String) {
        icon = value
    }

    fun onCategoryChange(value: String) {
        category = value
    }

    fun onTemplateTitleChange(value: String) {
        templateTitle = value
    }

    fun onTemplateDescriptionChange(value: String) {
        templateDescription = value
    }

    fun onTemplatePriorityChange(value: Int) {
        templatePriority = value
    }

    fun onTemplateProjectIdChange(value: Long?) {
        templateProjectId = value
    }

    fun onTemplateDurationChange(value: Int?) {
        templateDuration = value
    }

    fun onTemplateRecurrenceChange(value: RecurrenceRule?) {
        templateRecurrence = value
    }

    fun onToggleTag(tagId: Long) {
        templateTagIds = if (tagId in templateTagIds) {
            templateTagIds - tagId
        } else {
            templateTagIds + tagId
        }
    }

    fun onAddSubtask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) {
            templateSubtasks = templateSubtasks + trimmed
        }
    }

    fun onRemoveSubtask(index: Int) {
        if (index in templateSubtasks.indices) {
            templateSubtasks = templateSubtasks.toMutableList().also { it.removeAt(index) }
        }
    }

    suspend fun saveTemplate(): Boolean {
        if (name.isBlank()) {
            nameError = true
            return false
        }
        return try {
            val now = System.currentTimeMillis()
            val tagsJson = if (templateTagIds.isNotEmpty()) {
                gson.toJson(templateTagIds.toList())
            } else {
                null
            }
            val subtasksJson = if (templateSubtasks.isNotEmpty()) {
                gson.toJson(templateSubtasks)
            } else {
                null
            }
            val recurrenceJson = templateRecurrence?.let { RecurrenceConverter.toJson(it) }

            val existing = existingTemplate
            if (existing != null) {
                templateRepository.updateTemplate(
                    existing.copy(
                        name = name.trim(),
                        icon = icon,
                        category = category.trim().ifEmpty { null },
                        templateTitle = templateTitle.trim().ifEmpty { null },
                        templateDescription = templateDescription.trim().ifEmpty { null },
                        templatePriority = templatePriority.takeIf { it > 0 },
                        templateProjectId = templateProjectId,
                        templateTagsJson = tagsJson,
                        templateRecurrenceJson = recurrenceJson,
                        templateDuration = templateDuration,
                        templateSubtasksJson = subtasksJson
                    )
                )
            } else {
                templateRepository.createTemplate(
                    TaskTemplateEntity(
                        name = name.trim(),
                        icon = icon,
                        category = category.trim().ifEmpty { null },
                        templateTitle = templateTitle.trim().ifEmpty { null },
                        templateDescription = templateDescription.trim().ifEmpty { null },
                        templatePriority = templatePriority.takeIf { it > 0 },
                        templateProjectId = templateProjectId,
                        templateTagsJson = tagsJson,
                        templateRecurrenceJson = recurrenceJson,
                        templateDuration = templateDuration,
                        templateSubtasksJson = subtasksJson,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.e("AddEditTemplateVM", "Failed to save template", e)
            _errorMessages.emit("Couldn't save template")
            false
        }
    }

    suspend fun deleteTemplate() {
        try {
            templateId?.let { templateRepository.deleteTemplate(it) }
        } catch (e: Exception) {
            Log.e("AddEditTemplateVM", "Failed to delete template", e)
            _errorMessages.emit("Couldn't delete template")
        }
    }

    companion object {
        const val DEFAULT_TEMPLATE_ICON = "\uD83D\uDCCB" // 📋
        private val gson = Gson()
    }
}
