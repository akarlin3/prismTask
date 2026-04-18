package com.averycorp.prismtask.ui.screens.leisure.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotConfig
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.ui.screens.leisure.LeisureViewModel.Companion.defaultsFor
import com.averycorp.prismtask.ui.screens.leisure.components.LeisureOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeisureSlotEditState(
    val slot: LeisureSlotId,
    val config: LeisureSlotConfig,
    val builtIns: List<BuiltInActivityState>
)

data class BuiltInActivityState(
    val option: LeisureOption,
    val hidden: Boolean
)

@HiltViewModel
class LeisureSettingsViewModel
@Inject
constructor(
    private val preferences: LeisurePreferences
) : ViewModel() {

    val musicState: StateFlow<LeisureSlotEditState> = editStateFlow(LeisureSlotId.MUSIC)
    val flexState: StateFlow<LeisureSlotEditState> = editStateFlow(LeisureSlotId.FLEX)

    private fun editStateFlow(slot: LeisureSlotId): StateFlow<LeisureSlotEditState> =
        preferences.getSlotConfig(slot).map { config ->
            LeisureSlotEditState(
                slot = slot,
                config = config,
                builtIns = defaultsFor(slot).map { opt ->
                    BuiltInActivityState(opt, hidden = opt.id in config.hiddenBuiltInIds)
                }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LeisureSlotEditState(
                slot = slot,
                config = LeisureSlotConfig.defaultFor(slot),
                builtIns = defaultsFor(slot).map { BuiltInActivityState(it, hidden = false) }
            )
        )

    fun setEnabled(slot: LeisureSlotId, enabled: Boolean) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, enabled = enabled) }
    }

    fun setLabel(slot: LeisureSlotId, label: String) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, label = label) }
    }

    fun setEmoji(slot: LeisureSlotId, emoji: String) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, emoji = emoji) }
    }

    fun setDurationMinutes(slot: LeisureSlotId, minutes: Int) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, durationMinutes = minutes) }
    }

    fun setGridColumns(slot: LeisureSlotId, columns: Int) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, gridColumns = columns) }
    }

    fun setAutoComplete(slot: LeisureSlotId, autoComplete: Boolean) {
        viewModelScope.launch { preferences.updateSlotConfig(slot, autoComplete = autoComplete) }
    }

    fun setBuiltInHidden(slot: LeisureSlotId, builtInId: String, hidden: Boolean) {
        viewModelScope.launch { preferences.setBuiltInHidden(slot, builtInId, hidden) }
    }

    fun removeCustomActivity(slot: LeisureSlotId, id: String) {
        viewModelScope.launch { preferences.removeActivity(slot, id) }
    }

    fun addCustomActivity(slot: LeisureSlotId, label: String, icon: String) {
        viewModelScope.launch { preferences.addActivity(slot, label, icon) }
    }

    fun resetSlot(slot: LeisureSlotId) {
        viewModelScope.launch { preferences.resetSlotConfig(slot) }
    }
}
