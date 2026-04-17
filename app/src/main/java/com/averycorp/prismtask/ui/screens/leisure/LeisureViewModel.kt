package com.averycorp.prismtask.ui.screens.leisure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotConfig
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.ui.screens.leisure.components.LeisureOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for one leisure slot. [options] is the fully-merged activity list
 * (built-ins minus hidden + user-added customs), ready to render.
 */
data class LeisureSlotState(
    val slot: LeisureSlotId,
    val config: LeisureSlotConfig,
    val options: List<LeisureOption>,
    val picked: String?,
    val done: Boolean
)

@HiltViewModel
class LeisureViewModel
@Inject
constructor(
    private val repository: LeisureRepository,
    private val leisurePreferences: LeisurePreferences
) : ViewModel() {
    val todayLog: StateFlow<LeisureLogEntity?> = repository
        .getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val musicSlot: StateFlow<LeisureSlotState> = slotStateFlow(LeisureSlotId.MUSIC)
    val flexSlot: StateFlow<LeisureSlotState> = slotStateFlow(LeisureSlotId.FLEX)

    private fun slotStateFlow(slot: LeisureSlotId): StateFlow<LeisureSlotState> = combine(
        leisurePreferences.getSlotConfig(slot),
        repository.getTodayLog()
    ) { config, log ->
        val defaults = defaultsFor(slot).filter { it.id !in config.hiddenBuiltInIds }
        val customs = config.customActivities.map { LeisureOption(it.id, it.label, it.icon) }
        LeisureSlotState(
            slot = slot,
            config = config,
            options = defaults + customs,
            picked = if (slot == LeisureSlotId.MUSIC) log?.musicPick else log?.flexPick,
            done = if (slot == LeisureSlotId.MUSIC) log?.musicDone == true else log?.flexDone == true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LeisureSlotState(
            slot = slot,
            config = LeisureSlotConfig.defaultFor(slot),
            options = defaultsFor(slot),
            picked = null,
            done = false
        )
    )

    fun pickActivity(slot: LeisureSlotId, activityId: String) {
        viewModelScope.launch {
            when (slot) {
                LeisureSlotId.MUSIC -> repository.setMusicPick(activityId)
                LeisureSlotId.FLEX -> repository.setFlexPick(activityId)
            }
        }
    }

    fun toggleDone(slot: LeisureSlotId, done: Boolean) {
        viewModelScope.launch {
            when (slot) {
                LeisureSlotId.MUSIC -> repository.toggleMusicDone(done)
                LeisureSlotId.FLEX -> repository.toggleFlexDone(done)
            }
        }
    }

    fun clearPick(slot: LeisureSlotId) {
        viewModelScope.launch {
            when (slot) {
                LeisureSlotId.MUSIC -> repository.clearMusicPick()
                LeisureSlotId.FLEX -> repository.clearFlexPick()
            }
        }
    }

    fun resetToday() {
        viewModelScope.launch { repository.resetToday() }
    }

    fun addActivity(slot: LeisureSlotId, label: String, icon: String) {
        viewModelScope.launch { leisurePreferences.addActivity(slot, label, icon) }
    }

    fun removeActivity(slot: LeisureSlotId, id: String) {
        viewModelScope.launch { leisurePreferences.removeActivity(slot, id) }
    }

    fun isCustomActivity(id: String): Boolean = id.startsWith("custom_")

    companion object {
        val DEFAULT_INSTRUMENTS = listOf(
            LeisureOption("bass", "Bass", "\uD83C\uDFB8"),
            LeisureOption("guitar", "Guitar", "\uD83C\uDFB8"),
            LeisureOption("drums", "Drums", "\uD83E\uDD41"),
            LeisureOption("piano", "Piano", "\uD83C\uDFB9"),
            LeisureOption("singing", "Singing", "\uD83C\uDFA4")
        )

        val DEFAULT_FLEX_OPTIONS = listOf(
            LeisureOption("read", "Read", "\uD83D\uDCD6"),
            LeisureOption("gaming", "Gaming", "\uD83C\uDFAE"),
            LeisureOption("cook", "Cook something new", "\uD83C\uDF73"),
            LeisureOption("watch", "Watch a show or movie", "\uD83D\uDCFA"),
            LeisureOption("boardgame", "Board game / puzzle", "\uD83E\uDDE9")
        )

        fun defaultsFor(slot: LeisureSlotId): List<LeisureOption> = when (slot) {
            LeisureSlotId.MUSIC -> DEFAULT_INSTRUMENTS
            LeisureSlotId.FLEX -> DEFAULT_FLEX_OPTIONS
        }
    }
}
