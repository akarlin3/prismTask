package com.averykarlin.averytask.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    taskRepository: TaskRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = themePreferences.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<String> = themePreferences.getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#2563EB")

    val autoArchiveDays: StateFlow<Int> = archivePreferences.getAutoArchiveDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val archivedCount: StateFlow<Int> = taskRepository.getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setThemeMode(mode: String) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { themePreferences.setAccentColor(hex) }
    }

    fun setAutoArchiveDays(days: Int) {
        viewModelScope.launch { archivePreferences.setAutoArchiveDays(days) }
    }
}
