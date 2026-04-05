package com.averykarlin.averytask.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.export.DataExporter
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    private val dataExporter: DataExporter,
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

    // Export state — the actual file sharing is handled by the screen via Activity intents
    private val _exportedJson = MutableStateFlow<String?>(null)
    val exportedJson: StateFlow<String?> = _exportedJson

    private val _exportedCsv = MutableStateFlow<String?>(null)
    val exportedCsv: StateFlow<String?> = _exportedCsv

    fun onExportJson() {
        viewModelScope.launch {
            try { _exportedJson.value = dataExporter.exportToJson() }
            catch (e: Exception) { Log.e("SettingsVM", "JSON export failed", e) }
        }
    }

    fun onExportCsv() {
        viewModelScope.launch {
            try { _exportedCsv.value = dataExporter.exportToCsv() }
            catch (e: Exception) { Log.e("SettingsVM", "CSV export failed", e) }
        }
    }

    fun onImportJson() {
        // Import is triggered from the screen via file picker activity result
    }

    fun clearExport() { _exportedJson.value = null; _exportedCsv.value = null }
}
