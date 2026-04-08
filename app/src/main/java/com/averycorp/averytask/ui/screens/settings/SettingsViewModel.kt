package com.averycorp.averytask.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.export.DataExporter
import com.averycorp.averytask.data.export.DataImporter
import com.averycorp.averytask.data.export.ImportMode
import com.averycorp.averytask.data.preferences.ApiPreferences
import com.averycorp.averytask.data.preferences.ArchivePreferences
import com.averycorp.averytask.data.preferences.CalendarPreferences
import com.averycorp.averytask.data.preferences.DashboardPreferences
import com.averycorp.averytask.data.preferences.TabPreferences
import com.averycorp.averytask.data.preferences.TaskBehaviorPreferences
import com.averycorp.averytask.data.preferences.TimerPreferences
import com.averycorp.averytask.ui.navigation.ALL_BOTTOM_NAV_ITEMS
import com.averycorp.averytask.data.preferences.ThemePreferences
import com.averycorp.averytask.data.preferences.UrgencyWeights
import com.averycorp.averytask.data.local.database.AveryTaskDatabase
import com.averycorp.averytask.data.preferences.HabitListPreferences
import com.averycorp.averytask.data.preferences.LeisurePreferences
import com.averycorp.averytask.data.remote.AppUpdater
import com.averycorp.averytask.data.remote.AuthManager
import com.averycorp.averytask.data.remote.CalendarSyncService
import com.averycorp.averytask.data.remote.DeviceCalendar
import com.averycorp.averytask.data.remote.GoogleDriveService
import com.averycorp.averytask.data.remote.SyncService
import com.averycorp.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    private val apiPreferences: ApiPreferences,
    private val dashboardPreferences: DashboardPreferences,
    private val tabPreferences: TabPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val timerPreferences: TimerPreferences,
    private val calendarPreferences: CalendarPreferences,
    private val leisurePreferences: LeisurePreferences,
    private val habitListPreferences: HabitListPreferences,
    private val database: AveryTaskDatabase,
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val calendarSyncService: CalendarSyncService,
    private val taskRepository: TaskRepository,
    private val googleDriveService: GoogleDriveService,
    val appUpdater: AppUpdater
) : ViewModel() {

    // --- Theme ---
    val themeMode: StateFlow<String> = themePreferences.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<String> = themePreferences.getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#2563EB")

    val backgroundColor: StateFlow<String> = themePreferences.getBackgroundColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val surfaceColor: StateFlow<String> = themePreferences.getSurfaceColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val errorColor: StateFlow<String> = themePreferences.getErrorColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val fontScale: StateFlow<Float> = themePreferences.getFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val priorityColorNone: StateFlow<String> = themePreferences.getPriorityColorNone()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorLow: StateFlow<String> = themePreferences.getPriorityColorLow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorMedium: StateFlow<String> = themePreferences.getPriorityColorMedium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorHigh: StateFlow<String> = themePreferences.getPriorityColorHigh()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorUrgent: StateFlow<String> = themePreferences.getPriorityColorUrgent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // --- Dashboard ---
    val sectionOrder: StateFlow<List<String>> = dashboardPreferences.getSectionOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_ORDER)

    val hiddenSections: StateFlow<Set<String>> = dashboardPreferences.getHiddenSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressStyle: StateFlow<String> = dashboardPreferences.getProgressStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ring")

    // --- Navigation ---
    // Append any tabs not yet in the saved order (e.g. new tabs added in an app update),
    // so users who upgraded see the new tabs in the reorder list.
    val tabOrder: StateFlow<List<String>> = tabPreferences.getTabOrder()
        .map { order -> order + ALL_BOTTOM_NAV_ITEMS.map { it.route }.filter { it !in order } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TabPreferences.DEFAULT_ORDER)

    val hiddenTabs: StateFlow<Set<String>> = tabPreferences.getHiddenTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // --- Task Behavior ---
    val defaultSort: StateFlow<String> = taskBehaviorPreferences.getDefaultSort()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DUE_DATE")

    val defaultViewMode: StateFlow<String> = taskBehaviorPreferences.getDefaultViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "UPCOMING")

    val urgencyWeights: StateFlow<UrgencyWeights> = taskBehaviorPreferences.getUrgencyWeights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UrgencyWeights())

    val reminderPresets: StateFlow<List<Long>> = taskBehaviorPreferences.getReminderPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(0L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L))

    val firstDayOfWeek: StateFlow<DayOfWeek> = taskBehaviorPreferences.getFirstDayOfWeek()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayOfWeek.MONDAY)

    val dayStartHour: StateFlow<Int> = taskBehaviorPreferences.getDayStartHour()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Timer ---
    val timerWorkDurationSeconds: StateFlow<Int> = timerPreferences.getWorkDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_WORK_SECONDS)

    val timerBreakDurationSeconds: StateFlow<Int> = timerPreferences.getBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_BREAK_SECONDS)

    fun setTimerWorkDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setWorkDurationSeconds(minutes * 60) }
    }

    fun setTimerBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setBreakDurationSeconds(minutes * 60) }
    }

    // --- Modes ---
    val selfCareEnabled: StateFlow<Boolean> = habitListPreferences.isSelfCareEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val medicationEnabled: StateFlow<Boolean> = habitListPreferences.isMedicationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val schoolEnabled: StateFlow<Boolean> = habitListPreferences.isSchoolEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val leisureEnabled: StateFlow<Boolean> = habitListPreferences.isLeisureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val houseworkEnabled: StateFlow<Boolean> = habitListPreferences.isHouseworkEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setSelfCareEnabled(enabled: Boolean) {
        viewModelScope.launch { habitListPreferences.setSelfCareEnabled(enabled) }
    }

    fun setMedicationEnabled(enabled: Boolean) {
        viewModelScope.launch { habitListPreferences.setMedicationEnabled(enabled) }
    }

    fun setSchoolEnabled(enabled: Boolean) {
        viewModelScope.launch { habitListPreferences.setSchoolEnabled(enabled) }
    }

    fun setLeisureEnabled(enabled: Boolean) {
        viewModelScope.launch { habitListPreferences.setLeisureEnabled(enabled) }
    }

    fun setHouseworkEnabled(enabled: Boolean) {
        viewModelScope.launch { habitListPreferences.setHouseworkEnabled(enabled) }
    }

    // --- Archive / Data ---
    val autoArchiveDays: StateFlow<Int> = archivePreferences.getAutoArchiveDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val claudeApiKey: StateFlow<String> = apiPreferences.getClaudeApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val archivedCount: StateFlow<Int> = taskRepository.getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Calendar Sync ---
    val calendarSyncEnabled: StateFlow<Boolean> = calendarPreferences.isEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val calendarName: StateFlow<String> = calendarPreferences.getCalendarName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _availableCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val availableCalendars: StateFlow<List<DeviceCalendar>> = _availableCalendars

    fun loadCalendars() {
        _availableCalendars.value = calendarSyncService.getAvailableCalendars()
    }

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            calendarPreferences.setEnabled(enabled)
            if (enabled) {
                // Sync all existing tasks with due dates
                try {
                    val tasks = taskRepository.getAllTasksOnce()
                    calendarSyncService.fullCalendarSync(tasks)
                    _messages.emit("Calendar sync enabled")
                } catch (e: Exception) {
                    _messages.emit("Calendar sync failed: ${e.message}")
                }
            } else {
                calendarSyncService.clearAllEvents()
                _messages.emit("Calendar sync disabled")
            }
        }
    }

    fun selectCalendar(calendar: DeviceCalendar) {
        viewModelScope.launch {
            calendarPreferences.setCalendarId(calendar.id)
            calendarPreferences.setCalendarName(calendar.name)
        }
    }

    val isSignedIn: StateFlow<Boolean> = authManager.isSignedIn

    val userEmail: String? get() = authManager.currentUser.value?.email

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _pendingJsonExport = MutableStateFlow<String?>(null)
    val pendingJsonExport: StateFlow<String?> = _pendingJsonExport

    private val _pendingCsvExport = MutableStateFlow<String?>(null)
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _isDriveExporting = MutableStateFlow(false)
    val isDriveExporting: StateFlow<Boolean> = _isDriveExporting

    private val _isDriveImporting = MutableStateFlow(false)
    val isDriveImporting: StateFlow<Boolean> = _isDriveImporting

    // --- Theme setters ---
    fun setThemeMode(mode: String) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { themePreferences.setAccentColor(hex) }
    }

    fun setBackgroundColor(hex: String) {
        viewModelScope.launch { themePreferences.setBackgroundColor(hex) }
    }

    fun setSurfaceColor(hex: String) {
        viewModelScope.launch { themePreferences.setSurfaceColor(hex) }
    }

    fun setErrorColor(hex: String) {
        viewModelScope.launch { themePreferences.setErrorColor(hex) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { themePreferences.setFontScale(scale) }
    }

    fun setPriorityColor(level: Int, hex: String) {
        viewModelScope.launch { themePreferences.setPriorityColor(level, hex) }
    }

    fun resetColorOverrides() {
        viewModelScope.launch { themePreferences.resetColorOverrides() }
    }

    // --- Dashboard setters ---
    fun setSectionOrder(order: List<String>) {
        viewModelScope.launch { dashboardPreferences.setSectionOrder(order) }
    }

    fun setHiddenSections(hidden: Set<String>) {
        viewModelScope.launch { dashboardPreferences.setHiddenSections(hidden) }
    }

    fun setProgressStyle(style: String) {
        viewModelScope.launch { dashboardPreferences.setProgressStyle(style) }
    }

    fun resetDashboardDefaults() {
        viewModelScope.launch { dashboardPreferences.resetToDefaults() }
    }

    // --- Navigation setters ---
    fun setTabOrder(order: List<String>) {
        viewModelScope.launch { tabPreferences.setTabOrder(order) }
    }

    fun setHiddenTabs(hidden: Set<String>) {
        viewModelScope.launch { tabPreferences.setHiddenTabs(hidden) }
    }

    fun resetTabDefaults() {
        viewModelScope.launch { tabPreferences.resetToDefaults() }
    }

    // --- Task Behavior setters ---
    fun setDefaultSort(sort: String) {
        viewModelScope.launch { taskBehaviorPreferences.setDefaultSort(sort) }
    }

    fun setDefaultViewMode(mode: String) {
        viewModelScope.launch { taskBehaviorPreferences.setDefaultViewMode(mode) }
    }

    fun setUrgencyWeights(weights: UrgencyWeights) {
        viewModelScope.launch { taskBehaviorPreferences.setUrgencyWeights(weights) }
    }

    fun setReminderPresets(presets: List<Long>) {
        viewModelScope.launch { taskBehaviorPreferences.setReminderPresets(presets) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { taskBehaviorPreferences.setFirstDayOfWeek(day) }
    }

    fun setDayStartHour(hour: Int) {
        viewModelScope.launch { taskBehaviorPreferences.setDayStartHour(hour) }
    }

    fun resetTaskBehaviorDefaults() {
        viewModelScope.launch { taskBehaviorPreferences.resetToDefaults() }
    }

    // --- Archive ---
    fun setAutoArchiveDays(days: Int) {
        viewModelScope.launch { archivePreferences.setAutoArchiveDays(days) }
    }

    fun setClaudeApiKey(key: String) {
        viewModelScope.launch { apiPreferences.setClaudeApiKey(key) }
    }

    fun clearClaudeApiKey() {
        viewModelScope.launch { apiPreferences.clearClaudeApiKey() }
    }

    fun onExportJson() {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                _pendingJsonExport.value = dataExporter.exportToJson()
            } catch (e: Exception) {
                Log.e("SettingsVM", "JSON export failed", e)
                _messages.emit("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun onExportCsv() {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                _pendingCsvExport.value = dataExporter.exportToCsv()
            } catch (e: Exception) {
                Log.e("SettingsVM", "CSV export failed", e)
                _messages.emit("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearPendingExports() {
        _pendingJsonExport.value = null
        _pendingCsvExport.value = null
    }

    fun onImportJson(jsonString: String) {
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
                _messages.emit("Import failed: ${e.message}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun onExportToDrive() {
        viewModelScope.launch {
            _isDriveExporting.value = true
            try {
                val jsonData = dataExporter.exportToJson()
                val result = googleDriveService.exportToDrive(jsonData)
                result.fold(
                    onSuccess = { _messages.emit(it) },
                    onFailure = { _messages.emit("Drive export failed: ${it.message}") }
                )
            } catch (e: Exception) {
                Log.e("SettingsVM", "Drive export failed", e)
                _messages.emit("Drive export failed: ${e.message}")
            } finally {
                _isDriveExporting.value = false
            }
        }
    }

    fun onImportFromDrive() {
        viewModelScope.launch {
            _isDriveImporting.value = true
            try {
                val result = googleDriveService.importFromDrive()
                result.fold(
                    onSuccess = { jsonData ->
                        val importResult = dataImporter.importFromJson(jsonData, ImportMode.MERGE)
                        val parts = mutableListOf<String>()
                        if (importResult.tasksImported > 0) parts.add("${importResult.tasksImported} tasks")
                        if (importResult.projectsImported > 0) parts.add("${importResult.projectsImported} projects")
                        if (importResult.tagsImported > 0) parts.add("${importResult.tagsImported} tags")
                        if (importResult.habitsImported > 0) parts.add("${importResult.habitsImported} habits")
                        if (importResult.habitCompletionsImported > 0) parts.add("${importResult.habitCompletionsImported} completions")
                        if (importResult.configImported) parts.add("config")
                        val summary = if (parts.isEmpty()) "Nothing new to import" else "Restored ${parts.joinToString(", ")}"
                        val dupInfo = if (importResult.duplicatesSkipped > 0) " (${importResult.duplicatesSkipped} duplicates skipped)" else ""
                        _messages.emit("$summary from Google Drive$dupInfo")
                    },
                    onFailure = { _messages.emit("Drive import failed: ${it.message}") }
                )
            } catch (e: Exception) {
                Log.e("SettingsVM", "Drive import failed", e)
                _messages.emit("Drive import failed: ${e.message}")
            } finally {
                _isDriveImporting.value = false
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
        viewModelScope.launch {
            authManager.signOut()
            _messages.emit("Signed out")
        }
    }

    // --- App Update ---
    init {
        viewModelScope.launch {
            appUpdater.fetchLatestReleaseTag()
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            appUpdater.checkForUpdate()
        }
    }

    fun downloadAndInstallUpdate() {
        viewModelScope.launch {
            appUpdater.downloadAndInstall()
        }
    }

    fun refreshLatestReleaseTag() {
        viewModelScope.launch {
            appUpdater.fetchLatestReleaseTag()
        }
    }

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting

    fun resetApp() {
        viewModelScope.launch {
            _isResetting.value = true
            try {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }
                themePreferences.clearAll()
                archivePreferences.clearAll()
                dashboardPreferences.resetToDefaults()
                tabPreferences.resetToDefaults()
                taskBehaviorPreferences.resetToDefaults()
                calendarPreferences.clearAll()
                leisurePreferences.clearAll()
                habitListPreferences.clearAll()
                authManager.signOut()
                _messages.emit("App reset complete. Restart recommended.")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Reset failed", e)
                _messages.emit("Reset failed: ${e.message}")
            } finally {
                _isResetting.value = false
            }
        }
    }
}
