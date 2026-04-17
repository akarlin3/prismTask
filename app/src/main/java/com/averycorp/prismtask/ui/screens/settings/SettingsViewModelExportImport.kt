package com.averycorp.prismtask.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.export.ImportMode
import kotlinx.coroutines.launch

/**
 * Export/Import action handlers extracted from [SettingsViewModel] as
 * extension functions. All depended-on preferences, services, and state
 * flags are exposed as `internal` on the ViewModel so these extensions
 * can use them.
 */
internal fun SettingsViewModel.onExportJson() {
    viewModelScope.launch {
        _isExporting.value = true
        try {
            _pendingJsonExport.value = dataExporter.exportToJson()
        } catch (e: Exception) {
            Log.e("SettingsVM", "JSON export failed", e)
            _messages.emit("Export failed")
        } finally {
            _isExporting.value = false
        }
    }
}

internal fun SettingsViewModel.onExportCsv() {
    viewModelScope.launch {
        _isExporting.value = true
        try {
            _pendingCsvExport.value = dataExporter.exportToCsv()
        } catch (e: Exception) {
            Log.e("SettingsVM", "CSV export failed", e)
            _messages.emit("Export failed")
        } finally {
            _isExporting.value = false
        }
    }
}

internal fun SettingsViewModel.clearPendingExports() {
    _pendingJsonExport.value = null
    _pendingCsvExport.value = null
}

internal fun SettingsViewModel.onImportJson(jsonString: String) {
    viewModelScope.launch {
        _isImporting.value = true
        try {
            val result = dataImporter.importFromJson(jsonString, ImportMode.MERGE)
            val parts = mutableListOf<String>()
            if (result.tasksImported > 0) parts.add("${result.tasksImported} tasks")
            if (result.projectsImported > 0) parts.add("${result.projectsImported} projects")
            if (result.tagsImported > 0) parts.add("${result.tagsImported} tags")
            if (result.habitsImported > 0) parts.add("${result.habitsImported} habits")
            if (result.habitCompletionsImported > 0) parts.add("${result.habitCompletionsImported} habit completions")
            if (result.leisureLogsImported > 0) parts.add("${result.leisureLogsImported} leisure logs")
            if (result.selfCareLogsImported > 0) parts.add("${result.selfCareLogsImported} self-care logs")
            if (result.selfCareStepsImported > 0) parts.add("${result.selfCareStepsImported} self-care steps")
            if (result.coursesImported > 0) parts.add("${result.coursesImported} courses")
            if (result.assignmentsImported > 0) parts.add("${result.assignmentsImported} assignments")
            if (result.courseCompletionsImported > 0) parts.add("${result.courseCompletionsImported} course completions")
            if (result.configImported) parts.add("config")
            val summary = if (parts.isEmpty()) "Nothing new to import" else "Imported ${parts.joinToString(", ")}"
            val dupInfo = if (result.duplicatesSkipped > 0) " (${result.duplicatesSkipped} duplicates skipped)" else ""
            _messages.emit("$summary$dupInfo")
        } catch (e: Exception) {
            Log.e("SettingsVM", "Import failed", e)
            _messages.emit("Import failed")
        } finally {
            _isImporting.value = false
        }
    }
}
