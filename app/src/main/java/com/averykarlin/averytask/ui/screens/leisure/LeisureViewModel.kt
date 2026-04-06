package com.averykarlin.averytask.ui.screens.leisure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.LeisureLogEntity
import com.averykarlin.averytask.data.repository.LeisureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeisureViewModel @Inject constructor(
    private val repository: LeisureRepository
) : ViewModel() {

    val todayLog: StateFlow<LeisureLogEntity?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
}
