package com.averycorp.prismtask.ui.screens.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.usecase.ConversationTaskExtractor
import com.averycorp.prismtask.domain.usecase.ExtractedTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Paste Conversation screen (v1.4.0 V9).
 *
 * Holds the pasted input, the list of extracted candidates with their
 * selected/title-edit state, and drives the create-tasks batch on
 * confirmation.
 */
@HiltViewModel
class PasteConversationViewModel
    @Inject
    constructor(
        private val taskRepository: TaskRepository
    ) : ViewModel() {
        private val extractor = ConversationTaskExtractor()

        private val _input = MutableStateFlow("")
        val input: StateFlow<String> = _input.asStateFlow()

        private val _candidates = MutableStateFlow<List<EditableCandidate>>(emptyList())
        val candidates: StateFlow<List<EditableCandidate>> = _candidates.asStateFlow()

        private val _createdCount = MutableStateFlow<Int?>(null)
        val createdCount: StateFlow<Int?> = _createdCount.asStateFlow()

        fun onInputChange(text: String) {
            _input.value = text
        }

        fun onSourceLabel(source: String?) {
            _candidates.value = _candidates.value.map { it.copy(source = source) }
        }

        fun extract(source: String? = null) {
            val results = extractor.extract(_input.value, source)
            _candidates.value = results.map { task ->
                EditableCandidate(
                    title = task.title,
                    confidence = task.confidence,
                    source = task.source,
                    selected = true
                )
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

        fun createSelected() {
            viewModelScope.launch {
                val selected = _candidates.value.filter { it.selected && it.title.isNotBlank() }
                for (candidate in selected) {
                    taskRepository.addTask(title = candidate.title)
                }
                _createdCount.value = selected.size
            }
        }

        fun reset() {
            _input.value = ""
            _candidates.value = emptyList()
            _createdCount.value = null
        }
    }

data class EditableCandidate(
    val title: String,
    val confidence: Float,
    val source: String?,
    val selected: Boolean
) {
    fun toExtracted(): ExtractedTask = ExtractedTask(
        title = title,
        confidence = confidence,
        source = source
    )
}
