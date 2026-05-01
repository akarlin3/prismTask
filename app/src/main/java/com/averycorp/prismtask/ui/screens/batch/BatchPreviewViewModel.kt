package com.averycorp.prismtask.ui.screens.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.NdPreferencesDataStore
import com.averycorp.prismtask.data.remote.api.AmbiguousEntityHintResponse
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val undoBus: BatchUndoEventBus,
    ndPreferencesDataStore: NdPreferencesDataStore
) : ViewModel() {
    private val _state = MutableStateFlow<BatchPreviewState>(BatchPreviewState.Idle)
    val state: StateFlow<BatchPreviewState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BatchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BatchEvent> = _events.asSharedFlow()

    /** Per-mutation include flag, keyed by index in the loaded mutations list. */
    private val _excluded = MutableStateFlow<Set<Int>>(emptySet())
    val excluded: StateFlow<Set<Int>> = _excluded.asStateFlow()

    /**
     * Calm Mode (sensory-reduction tier) suppresses the inline disambiguation
     * picker and routes the user to a Cancel-and-retype flow instead. Other
     * ND modes leave the picker visible — only Calm Mode signals "fewer
     * decision affordances at once."
     */
    val simplifiedUi: StateFlow<Boolean> = ndPreferencesDataStore.ndPreferencesFlow
        .map { it.calmModeEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadPreview(commandText: String) {
        if (_state.value is BatchPreviewState.Loading) return
        _state.value = BatchPreviewState.Loading(commandText)
        viewModelScope.launch {
            try {
                val response = repository.parseCommand(commandText)
                // Belt-and-suspenders: even if Haiku flagged a phrase as
                // ambiguous via `ambiguous_entities`, Hard Rule #3 in the
                // system prompt is non-deterministic — Haiku may still emit
                // a mutation for one of the candidates. Strip those before
                // they can be silently approved. The hint itself stays so
                // the banner + picker can offer a corrective UX.
                val ambiguousIds: Set<String> = response.ambiguousEntities
                    .flatMap { it.candidateEntityIds }
                    .toSet()
                val (strippedMutations, keptMutations) = response.mutations.partition {
                    it.entityId in ambiguousIds
                }
                val medCandidates = collectMedicationCandidates(response.ambiguousEntities)
                val tagChangeTaskIds = keptMutations
                    .asSequence()
                    .filter { it.mutationType == "TAG_CHANGE" && it.entityType == "TASK" }
                    .mapNotNull { it.entityId.toLongOrNull() }
                    .distinct()
                    .toList()
                val currentTags = repository.getTagNamesForTasks(tagChangeTaskIds)
                _state.value = BatchPreviewState.Loaded(
                    commandText = commandText,
                    mutations = keptMutations,
                    confidence = response.confidence,
                    ambiguousEntities = response.ambiguousEntities,
                    currentTags = currentTags,
                    strippedAmbiguousCount = strippedMutations.size,
                    strippedMutations = strippedMutations,
                    medicationCandidates = medCandidates
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

    /**
     * Per-hint medication candidate options for the disambiguation picker.
     * Only populated when the hint targets MEDICATION and the candidate IDs
     * resolve to live local rows — archived/missing meds are filtered out
     * so the picker never offers a stale choice.
     */
    private suspend fun collectMedicationCandidates(
        hints: List<AmbiguousEntityHintResponse>
    ): Map<Int, List<MedicationCandidate>> {
        val out = mutableMapOf<Int, List<MedicationCandidate>>()
        hints.forEachIndexed { idx, hint ->
            if (hint.candidateEntityType != "MEDICATION") return@forEachIndexed
            val ids = hint.candidateEntityIds.mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return@forEachIndexed
            val resolved = repository.getMedicationsByIds(ids)
                .map { MedicationCandidate(it.id.toString(), it.name, it.displayLabel) }
            if (resolved.isNotEmpty()) out[idx] = resolved
        }
        return out
    }

    /**
     * User picked [pickedEntityId] from the picker for [hintIndex]. Look up
     * the stripped mutation that originally targeted one of this hint's
     * candidates, swap its entity_id to the user's pick, append it to the
     * mutations list, and drop both the hint + the recovered stripped
     * mutation from state. The picker is one-way per hint — there's no
     * "change my mind" path; user can still uncheck the row from the
     * normal mutation list.
     */
    fun resolveAmbiguity(hintIndex: Int, pickedEntityId: String) {
        val loaded = _state.value as? BatchPreviewState.Loaded ?: return
        val hint = loaded.ambiguousEntities.getOrNull(hintIndex) ?: return
        if (pickedEntityId !in hint.candidateEntityIds) return
        val candidateSet = hint.candidateEntityIds.toSet()
        val recovered = loaded.strippedMutations.firstOrNull {
            it.entityId in candidateSet && it.entityType == hint.candidateEntityType
        } ?: return
        val resolved = recovered.copy(entityId = pickedEntityId)
        _state.value = loaded.copy(
            mutations = loaded.mutations + resolved,
            ambiguousEntities = loaded.ambiguousEntities.filterIndexed { i, _ -> i != hintIndex },
            strippedMutations = loaded.strippedMutations - recovered,
            strippedAmbiguousCount = (loaded.strippedAmbiguousCount - 1).coerceAtLeast(0),
            medicationCandidates = loaded.medicationCandidates
                .filterKeys { it != hintIndex }
                .mapKeys { (k, _) -> if (k > hintIndex) k - 1 else k }
        )
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
        val ambiguousEntities: List<AmbiguousEntityHintResponse>,
        /**
         * Current tag names keyed by task id, populated only for tasks
         * targeted by TAG_CHANGE mutations. The preview row renders a
         * before/after diff against this list.
         */
        val currentTags: Map<Long, List<String>> = emptyMap(),
        /**
         * Number of mutations that were dropped from [mutations] because
         * their `entity_id` appeared in `ambiguous_entities[].candidate_entity_ids`.
         * Surfaced in the AmbiguityBanner copy so the user knows that
         * Haiku-flagged-ambiguous mutations were withheld.
         */
        val strippedAmbiguousCount: Int = 0,
        /**
         * The mutations that were stripped, retained so the picker can
         * recover the original `mutation_type` / `proposed_new_values`
         * (date, slot_key, etc.) when the user picks a candidate.
         */
        val strippedMutations: List<ProposedMutationResponse> = emptyList(),
        /**
         * Resolved medication candidates per hint index, for picker UI.
         * Only present for MEDICATION-typed hints whose candidate IDs
         * still resolve to live local rows.
         */
        val medicationCandidates: Map<Int, List<MedicationCandidate>> = emptyMap()
    ) : BatchPreviewState()
    data class Committing(val commandText: String) : BatchPreviewState()
    data class Error(val commandText: String, val message: String) : BatchPreviewState()
}

data class MedicationCandidate(
    val entityId: String,
    val name: String,
    val displayLabel: String?
)

sealed class BatchEvent {
    data class Approved(val batchId: String, val appliedCount: Int, val skippedCount: Int) : BatchEvent()
    data class Cancelled(val reason: String?) : BatchEvent()
}
