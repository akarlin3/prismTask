package com.averycorp.prismtask.ui.screens.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.billing.SubscriptionState
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.export.DataExporter
import com.averycorp.prismtask.data.export.DataImporter
import com.averycorp.prismtask.data.export.ImportMode
import com.averycorp.prismtask.data.preferences.ApiPreferences
import com.averycorp.prismtask.data.preferences.ArchivePreferences
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.preferences.BackendSyncPreferences
import com.averycorp.prismtask.data.calendar.CalendarInfo
import com.averycorp.prismtask.data.calendar.CalendarManager
import com.averycorp.prismtask.data.calendar.CalendarSyncPreferences
import com.averycorp.prismtask.data.calendar.DIRECTION_BOTH
import com.averycorp.prismtask.data.calendar.FREQUENCY_15MIN
import com.averycorp.prismtask.data.preferences.CalendarPreferences
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.TemplatePreferences
import com.averycorp.prismtask.data.preferences.TimerPreferences
import com.averycorp.prismtask.ui.navigation.ALL_BOTTOM_NAV_ITEMS
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.remote.AppUpdater
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.CalendarSyncService
import com.averycorp.prismtask.data.remote.DeviceCalendar
import com.averycorp.prismtask.data.remote.GoogleDriveService
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.ImportResponse
import com.averycorp.prismtask.data.remote.api.LoginRequest
import com.averycorp.prismtask.data.remote.api.RegisterRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.averycorp.prismtask.data.remote.sync.BackendSyncService
import com.averycorp.prismtask.data.repository.TaskRepository
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
    @ApplicationContext private val appContext: Context,
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
    private val database: PrismTaskDatabase,
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val calendarSyncService: CalendarSyncService,
    private val taskRepository: TaskRepository,
    private val googleDriveService: GoogleDriveService,
    private val backendSyncService: BackendSyncService,
    private val backendSyncPreferences: BackendSyncPreferences,
    private val templatePreferences: TemplatePreferences,
    private val authTokenPreferences: AuthTokenPreferences,
    private val prismTaskApi: PrismTaskApi,
    val appUpdater: AppUpdater,
    private val calendarManager: CalendarManager,
    private val calendarSyncPreferences: CalendarSyncPreferences,
    private val billingManager: BillingManager
) : ViewModel() {

    // --- Subscription ---
    val userTier: StateFlow<UserTier> = billingManager.userTier
    val subscriptionState: StateFlow<SubscriptionState> = billingManager.proSubscriptionState

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

    val timerLongBreakDurationSeconds: StateFlow<Int> = timerPreferences.getLongBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_LONG_BREAK_SECONDS)

    fun setTimerWorkDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setWorkDurationSeconds(minutes * 60) }
    }

    fun setTimerBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setBreakDurationSeconds(minutes * 60) }
    }

    fun setTimerLongBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setLongBreakDurationSeconds(minutes * 60) }
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

    // --- Google Calendar API Sync ---
    val isGCalConnected: StateFlow<Boolean> = calendarManager.isCalendarConnected
    val gCalAccountEmail: StateFlow<String?> = calendarManager.connectedAccountEmail

    val gCalSyncEnabled: StateFlow<Boolean> = calendarSyncPreferences.isCalendarSyncEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gCalSyncCalendarId: StateFlow<String> = calendarSyncPreferences.getSyncCalendarId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "primary")

    val gCalSyncDirection: StateFlow<String> = calendarSyncPreferences.getSyncDirection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DIRECTION_BOTH)

    val gCalShowEvents: StateFlow<Boolean> = calendarSyncPreferences.getShowCalendarEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val gCalSyncCompletedTasks: StateFlow<Boolean> = calendarSyncPreferences.getSyncCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gCalSyncFrequency: StateFlow<String> = calendarSyncPreferences.getSyncFrequency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FREQUENCY_15MIN)

    val gCalLastSyncTimestamp: StateFlow<Long> = calendarSyncPreferences.getLastSyncTimestamp()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _gCalAvailableCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val gCalAvailableCalendars: StateFlow<List<CalendarInfo>> = _gCalAvailableCalendars

    private val _isGCalSyncing = MutableStateFlow(false)
    val isGCalSyncing: StateFlow<Boolean> = _isGCalSyncing

    fun connectGoogleCalendar() {
        viewModelScope.launch {
            val result = calendarManager.connectCalendar()
            result.onSuccess {
                loadGCalCalendars()
                _messages.emit("Google Calendar connected")
            }.onFailure { e ->
                _messages.emit(e.message ?: "Failed to connect Google Calendar")
            }
        }
    }

    fun disconnectGoogleCalendar() {
        viewModelScope.launch {
            calendarManager.disconnectCalendar()
            calendarSyncPreferences.clearAll()
            _gCalAvailableCalendars.value = emptyList()
            _messages.emit("Google Calendar disconnected")
        }
    }

    fun loadGCalCalendars() {
        viewModelScope.launch {
            _gCalAvailableCalendars.value = calendarManager.getUserCalendars()
        }
    }

    fun setGCalSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            calendarSyncPreferences.setCalendarSyncEnabled(enabled)
            if (enabled) {
                _messages.emit("Google Calendar sync enabled")
            } else {
                _messages.emit("Google Calendar sync disabled")
            }
        }
    }

    fun setGCalSyncCalendarId(calendarId: String) {
        viewModelScope.launch { calendarSyncPreferences.setSyncCalendarId(calendarId) }
    }

    fun setGCalSyncDirection(direction: String) {
        viewModelScope.launch { calendarSyncPreferences.setSyncDirection(direction) }
    }

    fun setGCalShowEvents(show: Boolean) {
        viewModelScope.launch { calendarSyncPreferences.setShowCalendarEvents(show) }
    }

    fun setGCalSyncCompletedTasks(sync: Boolean) {
        viewModelScope.launch { calendarSyncPreferences.setSyncCompletedTasks(sync) }
    }

    fun setGCalSyncFrequency(frequency: String) {
        viewModelScope.launch { calendarSyncPreferences.setSyncFrequency(frequency) }
    }

    fun syncGCalNow() {
        viewModelScope.launch {
            _isGCalSyncing.value = true
            try {
                calendarSyncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
                _messages.emit("Google Calendar sync complete")
            } catch (e: Exception) {
                _messages.emit("Sync failed: ${e.message}")
            } finally {
                _isGCalSyncing.value = false
            }
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

    // --- Backend Sync (FastAPI) ---
    val backendLastSyncAt: StateFlow<Long> = backendSyncPreferences.lastSyncAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val backendConnected: StateFlow<Boolean> = authTokenPreferences.accessTokenFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isBackendSyncing = MutableStateFlow(false)
    val isBackendSyncing: StateFlow<Boolean> = _isBackendSyncing

    private val _isBackendAuthenticating = MutableStateFlow(false)
    val isBackendAuthenticating: StateFlow<Boolean> = _isBackendAuthenticating

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _isDriveExporting = MutableStateFlow(false)
    val isDriveExporting: StateFlow<Boolean> = _isDriveExporting

    private val _isDriveImporting = MutableStateFlow(false)
    val isDriveImporting: StateFlow<Boolean> = _isDriveImporting

    private val _isCloudExporting = MutableStateFlow(false)
    val isCloudExporting: StateFlow<Boolean> = _isCloudExporting

    private val _isCloudImporting = MutableStateFlow(false)
    val isCloudImporting: StateFlow<Boolean> = _isCloudImporting

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

    fun onBackendSync() {
        viewModelScope.launch {
            if (_isBackendSyncing.value) return@launch
            _isBackendSyncing.value = true
            try {
                val result = backendSyncService.fullSync()
                result.fold(
                    onSuccess = { summary ->
                        _messages.emit(
                            "Backend sync complete — pushed ${summary.pushed}, pulled ${summary.pulled}"
                        )
                    },
                    onFailure = { e ->
                        Log.e("SettingsVM", "Backend sync failed", e)
                        _messages.emit("Backend sync failed: ${e.message ?: "unknown error"}")
                    }
                )
            } finally {
                _isBackendSyncing.value = false
            }
        }
    }

    fun onBackendLogin(email: String, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (_isBackendAuthenticating.value) return@launch
            _isBackendAuthenticating.value = true
            try {
                val tokens = prismTaskApi.login(LoginRequest(email = email, password = password))
                authTokenPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
                _messages.emit("Connected to backend")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("SettingsVM", "Backend login failed", e)
                _messages.emit("Login failed: ${e.message ?: "unknown error"}")
                onComplete(false)
            } finally {
                _isBackendAuthenticating.value = false
            }
        }
    }

    fun onBackendRegister(email: String, password: String, name: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (_isBackendAuthenticating.value) return@launch
            _isBackendAuthenticating.value = true
            try {
                val tokens = prismTaskApi.register(
                    RegisterRequest(email = email, password = password, name = name)
                )
                authTokenPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
                _messages.emit("Backend account created")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("SettingsVM", "Backend register failed", e)
                _messages.emit("Registration failed: ${e.message ?: "unknown error"}")
                onComplete(false)
            } finally {
                _isBackendAuthenticating.value = false
            }
        }
    }

    fun onBackendDisconnect() {
        viewModelScope.launch {
            authTokenPreferences.clearTokens()
            backendSyncPreferences.clear()
            // Reset the templates-first-sync flag so the next account the
            // user connects to gets a fresh first-connect push. The seeded
            // flag is intentionally preserved — we don't want to re-insert
            // the built-ins just because the user disconnected.
            templatePreferences.setFirstSyncDone(false)
            _messages.emit("Disconnected from backend")
        }
    }

    fun onExportToCloud() {
        viewModelScope.launch {
            if (_isCloudExporting.value) return@launch
            _isCloudExporting.value = true
            try {
                val responseBody = withContext(Dispatchers.IO) { prismTaskApi.exportJson() }
                val bytes = withContext(Dispatchers.IO) { responseBody.bytes() }
                val timestamp = java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    java.util.Locale.US
                ).format(java.util.Date())
                val filename = "prismtask_cloud_$timestamp.json"
                val savedName = withContext(Dispatchers.IO) {
                    saveToDownloads(filename, bytes)
                }
                _messages.emit("Saved $savedName to Downloads")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Cloud export failed", e)
                _messages.emit("Cloud export failed: ${e.message ?: "unknown error"}")
            } finally {
                _isCloudExporting.value = false
            }
        }
    }

    fun onImportFromCloud(jsonBytes: ByteArray, filename: String) {
        viewModelScope.launch {
            if (_isCloudImporting.value) return@launch
            _isCloudImporting.value = true
            try {
                val mediaType = "application/json".toMediaType()
                val part = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = filename,
                    body = jsonBytes.toRequestBody(mediaType)
                )
                val result: ImportResponse = withContext(Dispatchers.IO) {
                    prismTaskApi.importJson(part, mode = "merge")
                }
                val parts = mutableListOf<String>()
                if (result.tasksImported > 0) parts.add("${result.tasksImported} tasks")
                if (result.projectsImported > 0) parts.add("${result.projectsImported} projects")
                if (result.tagsImported > 0) parts.add("${result.tagsImported} tags")
                if (result.habitsImported > 0) parts.add("${result.habitsImported} habits")
                val summary = if (parts.isEmpty()) "Nothing imported" else "Imported ${parts.joinToString(", ")}"
                _messages.emit(summary)
            } catch (e: Exception) {
                Log.e("SettingsVM", "Cloud import failed", e)
                _messages.emit("Cloud import failed: ${e.message ?: "unknown error"}")
            } finally {
                _isCloudImporting.value = false
            }
        }
    }

    private fun saveToDownloads(filename: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create Downloads entry")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Unable to open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return filename
        } else {
            // Pre-Q fallback: write to the app's external files Download dir,
            // which doesn't require WRITE_EXTERNAL_STORAGE permission.
            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("External storage unavailable")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(bytes) }
            return file.absolutePath
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
                calendarSyncPreferences.clearAll()
                calendarManager.disconnectCalendar()
                leisurePreferences.clearAll()
                habitListPreferences.clearAll()
                backendSyncPreferences.clear()
                templatePreferences.clear()
                authTokenPreferences.clearTokens()
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

    fun launchUpgrade(activity: android.app.Activity, tier: UserTier = UserTier.PRO) {
        viewModelScope.launch {
            try {
                billingManager.launchPurchaseFlow(activity, tier)
            } catch (e: Exception) {
                _messages.emit("Could not start purchase: ${e.message}")
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            try {
                billingManager.restorePurchases()
                _messages.emit("Purchases restored")
            } catch (e: Exception) {
                _messages.emit("Could not restore purchases: ${e.message}")
            }
        }
    }
}
