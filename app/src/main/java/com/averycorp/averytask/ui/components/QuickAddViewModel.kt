package com.averycorp.averytask.ui.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.converter.RecurrenceConverter
import com.averycorp.averytask.data.local.dao.UsageLogDao
import com.averycorp.averytask.data.local.entity.TaskEntity
import com.averycorp.averytask.data.local.entity.UsageLogEntity
import com.averycorp.averytask.data.repository.ProjectRepository
import com.averycorp.averytask.data.repository.TagRepository
import com.averycorp.averytask.data.repository.TaskRepository
import com.averycorp.averytask.domain.usecase.NaturalLanguageParser
import com.averycorp.averytask.domain.usecase.ParsedTask
import com.averycorp.averytask.domain.usecase.ParsedTaskResolver
import com.averycorp.averytask.domain.usecase.extractKeywords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
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
    private val usageLogDao: UsageLogDao
) : ViewModel() {

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

    fun onSubmit() {
        val text = inputText.value.trim()
        if (text.isBlank()) return

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
}
