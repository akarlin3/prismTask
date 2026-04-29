package com.averycorp.prismtask.ui.screens.multicreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.ExtractedTask
import com.averycorp.prismtask.domain.usecase.NaturalLanguageParser
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing the multi-task QuickAddBar flow (Phase B / PR-C of
 * the multi-task creation audit). Coordinates the
 * [NaturalLanguageParser.extractFromText] call, the editable
 * candidate list, and the batch-create commit on Approve.
 *
 * UX contract per the audit:
 *  - extract → preview → toggle / edit-title → create-selected → emit
 *    [createdCount] for the host screen to consume (typically a
 *    snackbar + back-pop).
 *  - Empty selection on Approve is a no-op (no tasks created, count
 *    remains null so the host doesn't pop).
 */
@HiltViewModel
class MultiCreateViewModel
@Inject
constructor(
    private val parser: NaturalLanguageParser,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val proFeatureGate: ProFeatureGate
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _candidates = MutableStateFlow<List<EditableMultiCreateCandidate>>(emptyList())
    val candidates: StateFlow<List<EditableMultiCreateCandidate>> = _candidates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _createdCount = MutableStateFlow<Int?>(null)
    val createdCount: StateFlow<Int?> = _createdCount.asStateFlow()

    fun setInput(text: String) {
        _input.value = text
    }

    /**
     * Submit [_input] to the extract-from-text endpoint and populate
     * [_candidates]. The parser handles its own regex fallback on
     * network/Pro/empty-response failures, so this method always
     * resolves with *something* unless the user typed nothing.
     */
    fun extract() {
        val text = _input.value
        if (text.isBlank()) {
            _candidates.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = parser.extractFromText(
                    input = text,
                    source = QUICK_ADD_SOURCE,
                    isProEnabled = { proFeatureGate.hasAccess(ProFeatureGate.AI_NLP) }
                )
                _candidates.value = results.map { it.toEditable() }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggle(index: Int) {
        val list = _candidates.value.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(selected = !list[index].selected)
        _candidates.value = list
    }

    fun editTitle(index: Int, newTitle: String) {
        val list = _candidates.value.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(title = newTitle)
        _candidates.value = list
    }

    /**
     * Insert each selected candidate into [TaskRepository] in source
     * order. Empty selection is a no-op — [_createdCount] is left null
     * so the screen doesn't pop. Project names are resolved
     * case-insensitively against existing projects; new names are
     * auto-created.
     */
    fun createSelected() {
        viewModelScope.launch {
            val selected = _candidates.value
                .filter { it.selected && it.title.isNotBlank() }
            if (selected.isEmpty()) {
                _createdCount.value = 0
                return@launch
            }
            for (candidate in selected) {
                val projectId = candidate.suggestedProject
                    ?.takeIf { it.isNotBlank() }
                    ?.let { findOrCreateProject(it) }
                taskRepository.addTask(
                    title = candidate.title.trim(),
                    dueDate = candidate.suggestedDueDate,
                    priority = candidate.suggestedPriority,
                    projectId = projectId
                )
            }
            _createdCount.value = selected.size
        }
    }

    fun reset() {
        _input.value = ""
        _candidates.value = emptyList()
        _createdCount.value = null
    }

    private suspend fun findOrCreateProject(name: String): Long {
        val trimmed = name.trim()
        val key = trimmed.lowercase()
        val existing = try {
            projectRepository.getAllProjects()
                .first()
                .firstOrNull { it.name.lowercase() == key }
        } catch (_: Exception) {
            null
        }
        return existing?.id ?: projectRepository.addProject(name = trimmed)
    }

    companion object {
        const val QUICK_ADD_SOURCE: String = "quick_add"
    }
}

data class EditableMultiCreateCandidate(
    val title: String,
    val confidence: Float,
    val suggestedDueDate: Long?,
    val suggestedPriority: Int,
    val suggestedProject: String?,
    val selected: Boolean
)

private fun ExtractedTask.toEditable(): EditableMultiCreateCandidate =
    EditableMultiCreateCandidate(
        title = title,
        confidence = confidence,
        suggestedDueDate = suggestedDueDate,
        suggestedPriority = suggestedPriority,
        suggestedProject = suggestedProject,
        selected = true
    )
