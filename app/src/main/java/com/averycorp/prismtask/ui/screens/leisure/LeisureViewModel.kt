package com.averycorp.prismtask.ui.screens.leisure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.ui.screens.leisure.components.LeisureOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeisureViewModel @Inject constructor(
    private val repository: LeisureRepository,
    private val leisurePreferences: LeisurePreferences
) : ViewModel() {

    val todayLog: StateFlow<LeisureLogEntity?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val musicActivities: StateFlow<List<LeisureOption>> = leisurePreferences.getCustomMusicActivities()
        .map { custom ->
            DEFAULT_INSTRUMENTS + custom.map { LeisureOption(it.id, it.label, it.icon) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_INSTRUMENTS)

    val flexActivities: StateFlow<List<LeisureOption>> = leisurePreferences.getCustomFlexActivities()
        .map { custom ->
            DEFAULT_FLEX_OPTIONS + custom.map { LeisureOption(it.id, it.label, it.icon) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_FLEX_OPTIONS)

    fun pickMusic(activityId: String) {
        viewModelScope.launch { repository.setMusicPick(activityId) }
    }

    fun pickFlex(activityId: String) {
        viewModelScope.launch { repository.setFlexPick(activityId) }
    }

    fun toggleMusicDone(done: Boolean) {
        viewModelScope.launch { repository.toggleMusicDone(done) }
    }

    fun toggleFlexDone(done: Boolean) {
        viewModelScope.launch { repository.toggleFlexDone(done) }
    }

    fun clearMusicPick() {
        viewModelScope.launch { repository.clearMusicPick() }
    }

    fun clearFlexPick() {
        viewModelScope.launch { repository.clearFlexPick() }
    }

    fun resetToday() {
        viewModelScope.launch { repository.resetToday() }
    }

    fun addMusicActivity(label: String, icon: String) {
        viewModelScope.launch { leisurePreferences.addMusicActivity(label, icon) }
    }

    fun addFlexActivity(label: String, icon: String) {
        viewModelScope.launch { leisurePreferences.addFlexActivity(label, icon) }
    }

    fun removeMusicActivity(id: String) {
        viewModelScope.launch { leisurePreferences.removeMusicActivity(id) }
    }

    fun removeFlexActivity(id: String) {
        viewModelScope.launch { leisurePreferences.removeFlexActivity(id) }
    }

    fun isCustomActivity(id: String): Boolean = id.startsWith("custom_")

    companion object {
        val DEFAULT_INSTRUMENTS = listOf(
            LeisureOption("bass", "Bass", "\uD83C\uDFB8"),
            LeisureOption("guitar", "Guitar", "\uD83C\uDFB8"),
            LeisureOption("drums", "Drums", "\uD83E\uDD41"),
            LeisureOption("piano", "Piano", "\uD83C\uDFB9"),
            LeisureOption("singing", "Singing", "\uD83C\uDFA4"),
        )

        val DEFAULT_FLEX_OPTIONS = listOf(
            LeisureOption("read", "Read", "\uD83D\uDCD6"),
            LeisureOption("gaming", "Gaming", "\uD83C\uDFAE"),
            LeisureOption("cook", "Cook something new", "\uD83C\uDF73"),
            LeisureOption("watch", "Watch a show or movie", "\uD83D\uDCFA"),
            LeisureOption("boardgame", "Board game / puzzle", "\uD83E\uDDE9"),
        )
    }
}
