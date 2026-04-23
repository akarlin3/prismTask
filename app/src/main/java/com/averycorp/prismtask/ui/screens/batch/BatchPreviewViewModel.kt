package com.averycorp.prismtask.ui.screens.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.remote.api.AmbiguousEntityHintResponse
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State + actions for the BatchPreviewScreen.
 *
 * The hosting NavGraph route receives the user's command text via
 * SavedStateHandle (set by the QuickAddBar caller) and triggers
 * [loadPreview] on first composition. Approve commits via the
 * repository and emits [BatchEvent.Approved] (with the freshly
 * created `batch_id`) so the upstream Snackbar can offer Undo.
 */
@HiltViewModel
class BatchPreviewViewModel
@Inject
constructor(
    private val repository: BatchOperationsRepository,
    private val undoBus: BatchUndoEventBus
) : ViewModel() {
    private val _state = MutableStateFlow<BatchPreviewState>(BatchPreviewState.Idle)
    val state: StateFlow<BatchPreviewState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BatchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BatchEvent> = _events.asSharedFlow()

    /** Per-mutation include flag, keyed by index in the loaded mutations list. */
    private val _excluded = MutableStateFlow<Set<Int>>(emptySet())
    val excluded: StateFlow<Set<Int>> = _excluded.asStateFlow()

    fun loadPreview(commandText: String) {
        if (_state.value is BatchPreviewState.Loading) return
        _state.value = BatchPreviewState.Loading(commandText)
        viewModelScope.launch {
            try {
                val response = repository.parseCommand(commandText)
                _state.value = BatchPreviewState.Loaded(
                    commandText = commandText,
                    mutations = response.mutations,
                    confidence = response.confidence,
                    ambiguousEntities = response.ambiguousEntities
                )
                _excluded.value = emptySet()
            } catch (e: Exception) {
                _state.value = BatchPreviewState.Error(
                    commandText = commandText,
                    message = e.message ?: "Failed to parse batch command"
                )
            }
        }
    }

    fun toggleExclusion(index: Int) {
        val current = _excluded.value
        _excluded.value = if (index in current) current - index else current + index
    }

    fun approve() {
        val loaded = _state.value as? BatchPreviewState.Loaded ?: return
        val toApply = loaded.mutations.filterIndexed { idx, _ -> idx !in _excluded.value }
        if (toApply.isEmpty()) {
            viewModelScope.launch {
                _events.emit(BatchEvent.Cancelled(reason = "No mutations selected"))
            }
            return
        }
        _state.value = BatchPreviewState.Committing(loaded.commandText)
        viewModelScope.launch {
            try {
                val result = repository.applyBatch(loaded.commandText, toApply)
                undoBus.notifyApplied(
                    BatchAppliedEvent(
                        batchId = result.batchId,
                        commandText = loaded.commandText,
                        appliedCount = result.appliedCount,
                        skippedCount = result.skipped.size
                    )
                )
                _events.emit(
                    BatchEvent.Approved(
                        batchId = result.batchId,
                        appliedCount = result.appliedCount,
                        skippedCount = result.skipped.size
                    )
                )
            } catch (e: Exception) {
                _state.value = BatchPreviewState.Error(
                    commandText = loaded.commandText,
                    message = e.message ?: "Failed to commit batch"
                )
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            _events.emit(BatchEvent.Cancelled(reason = null))
        }
    }
}

sealed class BatchPreviewState {
    data object Idle : BatchPreviewState()
    data class Loading(val commandText: String) : BatchPreviewState()
    data class Loaded(
        val commandText: String,
        val mutations: List<ProposedMutationResponse>,
        val confidence: Float,
        val ambiguousEntities: List<AmbiguousEntityHintResponse>
    ) : BatchPreviewState()
    data class Committing(val commandText: String) : BatchPreviewState()
    data class Error(val commandText: String, val message: String) : BatchPreviewState()
}

sealed class BatchEvent {
    data class Approved(val batchId: String, val appliedCount: Int, val skippedCount: Int) : BatchEvent()
    data class Cancelled(val reason: String?) : BatchEvent()
}
