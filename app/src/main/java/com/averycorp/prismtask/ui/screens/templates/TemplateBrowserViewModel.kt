package com.averycorp.prismtask.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.data.repository.SelfCareRepository
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
 * ViewModel behind Settings → Browse Templates. Reuses the same picker
 * content as onboarding but commits selections immediately on demand (rather
 * than on onboarding completion). Pre-populates nothing so returning users
 * can additively pick more templates without fighting existing state.
 */
@HiltViewModel
class TemplateBrowserViewModel
@Inject
constructor(
    private val selfCareRepository: SelfCareRepository,
    private val leisurePreferences: LeisurePreferences
) : ViewModel() {
    private val _selections = MutableStateFlow(TemplateSelections())
    val selections: StateFlow<TemplateSelections> = _selections.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun update(selections: TemplateSelections) {
        _selections.value = selections
    }

    fun commit() {
        val snapshot = _selections.value
        viewModelScope.launch {
            applyLeisureSelection(LeisureSlotId.MUSIC, snapshot.musicIds)
            applyLeisureSelection(LeisureSlotId.FLEX, snapshot.flexIds)
            listOf("morning", "bedtime", "housework").forEach { routineType ->
                val stepIds = snapshot.effectiveStepIds(routineType)
                if (stepIds.isNotEmpty()) {
                    selfCareRepository.seedSelfCareSteps(routineType, stepIds.toList())
                }
            }
            _selections.value = TemplateSelections()
            _messages.tryEmit("Templates added")
        }
    }

    /**
     * In the Settings browser we only un-hide leisure options the user
     * picks; we do NOT re-hide ones they didn't pick, since that would
     * undo choices they made previously. This keeps the screen additive.
     */
    private suspend fun applyLeisureSelection(slot: LeisureSlotId, selectedIds: Set<String>) {
        selectedIds.forEach { id ->
            leisurePreferences.setBuiltInHidden(slot, id, hidden = false)
        }
    }
}
