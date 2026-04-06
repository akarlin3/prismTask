package com.averykarlin.averytask.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.export.DataExporter
import com.averykarlin.averytask.data.export.DataImporter
import com.averykarlin.averytask.data.export.ImportMode
import com.averykarlin.averytask.data.export.ImportResult
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.remote.AuthManager
import com.averykarlin.averytask.data.remote.SyncService
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    private val authManager: AuthManager,
    private val syncService: SyncService,
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

    val isSignedIn: StateFlow<Boolean> = authManager.isSignedIn

    val userEmail: String? get() = authManager.currentUser.value?.email

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Export data holders — Screen reads these after requesting a file location
    private val _pendingJsonExport = MutableStateFlow<String?>(null)
    val pendingJsonExport: StateFlow<String?> = _pendingJsonExport

    private val _pendingCsvExport = MutableStateFlow<String?>(null)
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun setThemeMode(mode: String) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { themePreferences.setAccentColor(hex) }
    }

    fun setAutoArchiveDays(days: Int) {
        viewModelScope.launch { archivePreferences.setAutoArchiveDays(days) }
    }

    fun onExportJson() {
        viewModelScope.launch {
            try {
                _pendingJsonExport.value = dataExporter.exportToJson()
            } catch (e: Exception) {
                Log.e("SettingsVM", "JSON export failed", e)
                _messages.emit("Export failed: ${e.message}")
            }
        }
    }

    fun onExportCsv() {
        viewModelScope.launch {
            try {
                _pendingCsvExport.value = dataExporter.exportToCsv()
            } catch (e: Exception) {
                Log.e("SettingsVM", "CSV export failed", e)
                _messages.emit("Export failed: ${e.message}")
            }
        }
    }

    fun clearPendingExports() {
        _pendingJsonExport.value = null
        _pendingCsvExport.value = null
    }

    fun onImportJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val result = dataImporter.importFromJson(jsonString, ImportMode.MERGE)
                _messages.emit("Imported ${result.tasksImported} tasks, ${result.projectsImported} projects, ${result.tagsImported} tags" +
                    if (result.duplicatesSkipped > 0) " (${result.duplicatesSkipped} duplicates skipped)" else "")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Import failed", e)
                _messages.emit("Import failed: ${e.message}")
            }
        }
    }

    fun onSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                syncService.fullSync()
                _messages.emit("Sync complete")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Sync failed", e)
                _messages.emit("Sync failed: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun onSignOut() {
        authManager.signOut()
        viewModelScope.launch {
            _messages.emit("Signed out")
        }
    }
}
