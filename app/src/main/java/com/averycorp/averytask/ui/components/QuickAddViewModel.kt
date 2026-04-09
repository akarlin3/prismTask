package com.averycorp.averytask.ui.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.converter.RecurrenceConverter
import com.averycorp.averytask.data.local.dao.UsageLogDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.local.entity.TaskTemplateEntity
import com.averycorp.averytask.data.local.entity.UsageLogEntity
import com.averycorp.averytask.data.repository.ProjectRepository
import com.averycorp.averytask.data.repository.TagRepository
import com.averycorp.averytask.data.repository.TaskRepository
import com.averycorp.averytask.data.repository.TaskTemplateRepository
import com.averycorp.averytask.domain.usecase.NaturalLanguageParser
import com.averycorp.averytask.domain.usecase.ParsedTask
import com.averycorp.averytask.domain.usecase.ParsedTaskResolver
import com.averycorp.averytask.domain.usecase.extractKeywords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val parser: NaturalLanguageParser,
    private val resolver: ParsedTaskResolver,
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val projectRepository: ProjectRepository,
    private val templateRepository: TaskTemplateRepository,
    private val usageLogDao: UsageLogDao
) : ViewModel() {

    /**
     * List of candidate templates shown in the disambiguation popup when a
     * "/query" shortcut matches more than one template. Null when the popup
     * is dismissed; non-empty when the user needs to pick.
     */
    private val _templateDisambiguation = MutableStateFlow<List<TaskTemplateEntity>?>(null)
    val templateDisambiguation: StateFlow<List<TaskTemplateEntity>?> =
        _templateDisambiguation.asStateFlow()

    val inputText = MutableStateFlow("")

    val parsedPreview: StateFlow<ParsedTask?> = inputText
        .debounce(200)
        .map { text ->
            if (text.isBlank()) null else parser.parse(text)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    fun onInputChanged(text: String) {
        inputText.value = text
    }

    fun onToggleExpand() {
        _isExpanded.value = !_isExpanded.value
    }

    fun onSubmit(plannedDateOverride: Long? = null) {
        val text = inputText.value.trim()
        if (text.isBlank()) return

        // Template shortcut branch — "/name" or "template:name" bypass the
        // normal NLP pipeline and instead resolve against the user's
        // template library.
        val templateQuery = parser.extractTemplateQuery(text)
        if (templateQuery != null) {
            viewModelScope.launch {
                _isSubmitting.value = true
                try {
                    resolveAndCreateFromTemplate(templateQuery, plannedDateOverride)
                } catch (e: Exception) {
                    Log.e("QuickAddVM", "Failed to resolve template shortcut", e)
                } finally {
                    _isSubmitting.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                Log.i("QuickAddVM", "Submitting quick-add task, calling remote NLP parser")
                val parsed = parser.parseRemote(text)
                val resolved = resolver.resolve(parsed)

                // Auto-create unmatched tags
                val newTagIds = resolved.unmatchedTags.map { tagName ->
                    tagRepository.addTag(name = tagName)
                }
                val allTagIds = resolved.tagIds + newTagIds

                // Auto-create unmatched project
                var projectId = resolved.projectId
                if (projectId == null && resolved.unmatchedProject != null) {
                    projectId = projectRepository.addProject(name = resolved.unmatchedProject)
                }

                // Build recurrence JSON
                val recurrenceJson = resolved.recurrenceRule?.let { RecurrenceConverter.toJson(it) }

                val now = System.currentTimeMillis()
                val task = TaskEntity(
                    title = resolved.title,
                    dueDate = resolved.dueDate,
                    dueTime = resolved.dueTime,
                    priority = resolved.priority,
                    projectId = projectId,
                    recurrenceRule = recurrenceJson,
                    plannedDate = plannedDateOverride,
                    createdAt = now,
                    updatedAt = now
                )
                val taskId = taskRepository.insertTask(task)

                // Assign tags
                if (allTagIds.isNotEmpty()) {
                    tagRepository.setTagsForTask(taskId, allTagIds)
                }

                // Log usage for suggestions
                val keywords = extractKeywords(resolved.title).joinToString(",")
                if (keywords.isNotBlank()) {
                    allTagIds.forEach { tagId ->
                        val tagName = resolved.unmatchedTags.getOrNull(
                            (tagId - (resolved.tagIds.lastOrNull() ?: 0) - 1).toInt().coerceAtLeast(0)
                        ) ?: resolved.title
                        usageLogDao.insert(UsageLogEntity(
                            eventType = "tag_assigned",
                            entityId = tagId,
                            entityName = tagName,
                            taskTitle = resolved.title,
                            titleKeywords = keywords
                        ))
                    }
                    if (projectId != null) {
                        usageLogDao.insert(UsageLogEntity(
                            eventType = "project_assigned",
                            entityId = projectId,
                            entityName = resolved.unmatchedProject ?: "",
                            taskTitle = resolved.title,
                            titleKeywords = keywords
                        ))
                    }
                }

                inputText.value = ""
            } catch (e: Exception) {
                Log.e("QuickAddVM", "Failed to create task", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /**
     * Resolve a template name query against the user's library. If exactly
     * one template matches (case-insensitive substring), creates the task
     * from it immediately. If multiple match, exposes the candidates via
     * [templateDisambiguation] so the UI can show a picker popup.
     */
    private suspend fun resolveAndCreateFromTemplate(
        query: String,
        plannedDateOverride: Long?
    ) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        val all = try {
            templateRepository.getAllTemplates().first()
        } catch (e: Exception) {
            Log.e("QuickAddVM", "Failed to fetch templates", e)
            return
        }
        val matches = fuzzyMatchTemplates(all, normalized)
        when {
            matches.isEmpty() -> {
                Log.i("QuickAddVM", "No template matches for '$normalized'")
            }
            matches.size == 1 -> {
                createFromTemplate(matches.first().id, plannedDateOverride)
                inputText.value = ""
            }
            else -> {
                // More than one candidate — let the UI disambiguate.
                _templateDisambiguation.value = matches
            }
        }
    }

    /**
     * Plan-aware template creation: delegates to the repository's
     * [TaskTemplateRepository.createTaskFromTemplate] with the current
     * plan-date override so quick-add shortcuts in the Plan-for-Today sheet
     * land tasks on today's dashboard.
     */
    private suspend fun createFromTemplate(templateId: Long, plannedDateOverride: Long?) {
        try {
            val newTaskId = templateRepository.createTaskFromTemplate(
                templateId = templateId,
                dueDateOverride = plannedDateOverride
            )
            if (plannedDateOverride != null) {
                taskRepository.planTaskForToday(newTaskId)
            }
        } catch (e: Exception) {
            Log.e("QuickAddVM", "Failed to create from template", e)
        }
    }

    /**
     * Called by the UI when the user taps a candidate in the disambiguation
     * popup. Creates the task from the chosen template, clears the input,
     * and dismisses the popup.
     */
    fun onDisambiguationSelected(templateId: Long, plannedDateOverride: Long? = null) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                createFromTemplate(templateId, plannedDateOverride)
                inputText.value = ""
            } finally {
                _templateDisambiguation.value = null
                _isSubmitting.value = false
            }
        }
    }

    fun onDismissDisambiguation() {
        _templateDisambiguation.value = null
    }

    /**
     * Simple substring-based fuzzy matcher: returns templates whose name
     * contains any of the space-separated tokens in [query] (case-insensitive).
     * Ranks exact-name matches first, then prefix matches, then substring.
     */
    private fun fuzzyMatchTemplates(
        templates: List<TaskTemplateEntity>,
        query: String
    ): List<TaskTemplateEntity> {
        val q = query.lowercase()
        return templates
            .mapNotNull { template ->
                val name = template.name.lowercase()
                val score = when {
                    name == q -> 0
                    name.startsWith(q) -> 1
                    name.contains(q) -> 2
                    else -> null
                }
                score?.let { template to it }
            }
            .sortedBy { it.second }
            .map { it.first }
    }
}
