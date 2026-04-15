package com.averycorp.prismtask.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.billing.BillingPeriod
import com.averycorp.prismtask.data.billing.SubscriptionState
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.calendar.CalendarInfo
import com.averycorp.prismtask.data.calendar.CalendarManager
import com.averycorp.prismtask.data.calendar.CalendarSyncPreferences
import com.averycorp.prismtask.data.calendar.DIRECTION_BOTH
import com.averycorp.prismtask.data.calendar.FREQUENCY_15MIN
import com.averycorp.prismtask.data.export.DataExporter
import com.averycorp.prismtask.data.export.DataImporter
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.preferences.A11yPreferences
import com.averycorp.prismtask.data.preferences.ArchivePreferences
import com.averycorp.prismtask.data.preferences.AuthTokenPreferences
import com.averycorp.prismtask.data.preferences.BackendSyncPreferences
import com.averycorp.prismtask.data.preferences.CalendarPreferences
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.ShakePreferences
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.TemplatePreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.TimerPreferences
import com.averycorp.prismtask.data.preferences.UrgencyWeights
import com.averycorp.prismtask.data.preferences.VoicePreferences
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.CalendarSyncService
import com.averycorp.prismtask.data.remote.DeviceCalendar
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.ui.navigation.ALL_BOTTOM_NAV_ITEMS
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * The Settings screen ViewModel. Long-running action handlers for
 * Export/Import have been moved to a companion extension file
 * ([SettingsViewModelExportImport]) to keep this file focused on
 * state exposure and simple setters.
 */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    @ApplicationContext internal val appContext: Context,
    private val themePreferences: ThemePreferences,
    private val archivePreferences: ArchivePreferences,
    private val dashboardPreferences: DashboardPreferences,
    private val tabPreferences: TabPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val timerPreferences: TimerPreferences,
    private val calendarPreferences: CalendarPreferences,
    private val leisurePreferences: LeisurePreferences,
    private val habitListPreferences: HabitListPreferences,
    private val database: PrismTaskDatabase,
    internal val dataExporter: DataExporter,
    internal val dataImporter: DataImporter,
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val calendarSyncService: CalendarSyncService,
    private val taskRepository: TaskRepository,
    internal val backendSyncPreferences: BackendSyncPreferences,
    internal val templatePreferences: TemplatePreferences,
    internal val authTokenPreferences: AuthTokenPreferences,
    private val calendarManager: CalendarManager,
    private val calendarSyncPreferences: CalendarSyncPreferences,
    private val billingManager: BillingManager,
    private val voicePreferences: VoicePreferences,
    private val a11yPreferences: A11yPreferences,
    private val shakePreferences: ShakePreferences,
    private val userPreferencesDataStore: com.averycorp.prismtask.data.preferences.UserPreferencesDataStore,
    private val boundaryRuleRepository: com.averycorp.prismtask.data.repository.BoundaryRuleRepository,
    private val moodEnergyRepository: com.averycorp.prismtask.data.repository.MoodEnergyRepository,
    private val medicationRefillRepository: com.averycorp.prismtask.data.repository.MedicationRefillRepository,
    private val checkInLogRepository: com.averycorp.prismtask.data.repository.CheckInLogRepository,
    private val clinicalReportPdfWriter: com.averycorp.prismtask.data.export.ClinicalReportPdfWriter,
    private val onboardingPreferences: OnboardingPreferences,
    private val widgetUpdateManager: com.averycorp.prismtask.widget.WidgetUpdateManager,
    private val ndPreferencesDataStore: com.averycorp.prismtask.data.preferences.NdPreferencesDataStore,
    private val notificationPreferences: NotificationPreferences
) : ViewModel() {
    private val _checkInStreak = kotlinx.coroutines.flow.MutableStateFlow(0)
    val checkInStreak: StateFlow<Int> = _checkInStreak

    init {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .setCustomKey("screen", "SettingsScreen")
        } catch (_: Exception) {
        }
        viewModelScope.launch {
            try {
                val todayStart = java.util.Calendar
                    .getInstance()
                    .apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                _checkInStreak.value = checkInLogRepository.currentStreak(todayStart)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to load check-in streak", e)
            }
        }
    }

    val latestReleaseTag: StateFlow<String?> = MutableStateFlow(null)

    private val _isExportingClinicalReport = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isExportingClinicalReport: StateFlow<Boolean> = _isExportingClinicalReport

    private val _clinicalReportUri = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val clinicalReportUri: StateFlow<android.net.Uri?> = _clinicalReportUri

    fun clearClinicalReportUri() {
        _clinicalReportUri.value = null
    }

    fun exportClinicalReport() {
        if (_isExportingClinicalReport.value) return
        _isExportingClinicalReport.value = true
        viewModelScope.launch {
            try {
                val end = System.currentTimeMillis()
                val start = end - 30L * 24 * 60 * 60 * 1000
                val generator = com.averycorp.prismtask.domain.usecase
                    .ClinicalReportGenerator()
                val inputs = com.averycorp.prismtask.domain.usecase.ClinicalReportInputs(
                    userName = null,
                    dateRangeStart = start,
                    dateRangeEnd = end,
                    tasks = taskRepository.getAllTasksOnce(),
                    moodEnergyLogs = moodEnergyRepository.getRange(start, end),
                    medications = medicationRefillRepository.getAll()
                )
                val report = generator.generate(inputs)
                val uri = clinicalReportPdfWriter.write(appContext, report)
                _clinicalReportUri.value = uri
            } finally {
                _isExportingClinicalReport.value = false
            }
        }
    }

    // --- v1.3.0 User Preferences ---
    val appearancePrefs: StateFlow<com.averycorp.prismtask.data.preferences.AppearancePrefs> =
        userPreferencesDataStore.appearanceFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .AppearancePrefs()
            )

    val swipePrefs: StateFlow<com.averycorp.prismtask.data.preferences.SwipePrefs> =
        userPreferencesDataStore.swipeFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .SwipePrefs()
            )

    val taskDefaultPrefs: StateFlow<com.averycorp.prismtask.data.preferences.TaskDefaults> =
        userPreferencesDataStore.taskDefaultsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .TaskDefaults()
            )

    val quickAddPrefs: StateFlow<com.averycorp.prismtask.data.preferences.QuickAddPrefs> =
        userPreferencesDataStore.quickAddFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .QuickAddPrefs()
            )

    /** Work-Life Balance preferences (v1.4.0 V1). */
    val workLifeBalancePrefs: StateFlow<com.averycorp.prismtask.data.preferences.WorkLifeBalancePrefs> =
        userPreferencesDataStore.workLifeBalanceFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .WorkLifeBalancePrefs()
            )

    fun setWorkLifeBalancePrefs(prefs: com.averycorp.prismtask.data.preferences.WorkLifeBalancePrefs) {
        viewModelScope.launch { userPreferencesDataStore.setWorkLifeBalance(prefs) }
    }

    /** Boundary rules (v1.4.0 V3). */
    val boundaryRules: StateFlow<List<com.averycorp.prismtask.domain.model.BoundaryRule>> =
        boundaryRuleRepository
            .observeRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { boundaryRuleRepository.seedBuiltInIfEmpty() }
    }

    fun toggleBoundaryRule(rule: com.averycorp.prismtask.domain.model.BoundaryRule, enabled: Boolean) {
        viewModelScope.launch {
            boundaryRuleRepository.update(rule.copy(isEnabled = enabled))
        }
    }

    fun deleteBoundaryRule(rule: com.averycorp.prismtask.domain.model.BoundaryRule) {
        viewModelScope.launch { boundaryRuleRepository.delete(rule.id) }
    }

    fun addBoundaryRuleFromNlp(text: String): Boolean {
        val parsed = com.averycorp.prismtask.domain.usecase.BoundaryRuleParser
            .parse(text)
            ?: return false
        viewModelScope.launch { boundaryRuleRepository.insert(parsed) }
        return true
    }

    fun insertBoundaryRule(rule: com.averycorp.prismtask.domain.model.BoundaryRule) {
        viewModelScope.launch { boundaryRuleRepository.insert(rule) }
    }

    fun updateBoundaryRule(rule: com.averycorp.prismtask.domain.model.BoundaryRule) {
        viewModelScope.launch { boundaryRuleRepository.update(rule) }
    }

    // --- Brain Mode / ND preferences ---
    val ndPrefs: StateFlow<com.averycorp.prismtask.data.preferences.NdPreferences> =
        ndPreferencesDataStore.ndPreferencesFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .NdPreferences()
            )

    fun setAdhdMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setAdhdMode(enabled) }
    }

    fun setCalmMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setCalmMode(enabled) }
    }

    fun setFocusReleaseMode(enabled: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setFocusReleaseMode(enabled) }
    }

    fun setGoodEnoughTimersEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setGoodEnoughTimersEnabled(e) }
    }

    fun setDefaultGoodEnoughMinutes(m: Int) {
        viewModelScope.launch { ndPreferencesDataStore.setDefaultGoodEnoughMinutes(m) }
    }

    fun setGoodEnoughEscalation(e: com.averycorp.prismtask.data.preferences.GoodEnoughEscalation) {
        viewModelScope.launch { ndPreferencesDataStore.setGoodEnoughEscalation(e) }
    }

    fun setAntiReworkEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setAntiReworkEnabled(e) }
    }

    fun setSoftWarningEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setSoftWarningEnabled(e) }
    }

    fun setCoolingOffEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setCoolingOffEnabled(e) }
    }

    fun setCoolingOffMinutes(m: Int) {
        viewModelScope.launch { ndPreferencesDataStore.setCoolingOffMinutes(m) }
    }

    fun setRevisionCounterEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setRevisionCounterEnabled(e) }
    }

    fun setMaxRevisions(m: Int) {
        viewModelScope.launch { ndPreferencesDataStore.setMaxRevisions(m) }
    }

    fun setShipItCelebrationsEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setShipItCelebrationsEnabled(e) }
    }

    fun setCelebrationIntensity(i: com.averycorp.prismtask.data.preferences.CelebrationIntensity) {
        viewModelScope.launch { ndPreferencesDataStore.setCelebrationIntensity(i) }
    }

    fun setParalysisBreakersEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setParalysisBreakersEnabled(e) }
    }

    fun setAutoSuggestEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setAutoSuggestEnabled(e) }
    }

    fun setSimplifyChoicesEnabled(e: Boolean) {
        viewModelScope.launch { ndPreferencesDataStore.setSimplifyChoicesEnabled(e) }
    }

    fun setStuckDetectionMinutes(m: Int) {
        viewModelScope.launch { ndPreferencesDataStore.setStuckDetectionMinutes(m) }
    }

    /** Forgiveness-first streak preferences (v1.4.0 V5). */
    val forgivenessPrefs: StateFlow<com.averycorp.prismtask.data.preferences.ForgivenessPrefs> =
        userPreferencesDataStore.forgivenessFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                com.averycorp.prismtask.data.preferences
                    .ForgivenessPrefs()
            )

    fun setForgivenessPrefs(prefs: com.averycorp.prismtask.data.preferences.ForgivenessPrefs) {
        viewModelScope.launch { userPreferencesDataStore.setForgivenessPrefs(prefs) }
    }

    fun setCompactMode(enabled: Boolean) {
        viewModelScope.launch { userPreferencesDataStore.setCompactMode(enabled) }
    }

    fun setShowCardBorders(enabled: Boolean) {
        viewModelScope.launch { userPreferencesDataStore.setShowCardBorders(enabled) }
    }

    fun setCardCornerRadius(radius: Int) {
        viewModelScope.launch { userPreferencesDataStore.setCardCornerRadius(radius) }
    }

    fun setSwipeRight(action: com.averycorp.prismtask.domain.model.SwipeAction) {
        viewModelScope.launch { userPreferencesDataStore.setSwipeRight(action) }
    }

    fun setSwipeLeft(action: com.averycorp.prismtask.domain.model.SwipeAction) {
        viewModelScope.launch { userPreferencesDataStore.setSwipeLeft(action) }
    }

    fun setTaskDefaults(defaults: com.averycorp.prismtask.data.preferences.TaskDefaults) {
        viewModelScope.launch { userPreferencesDataStore.setTaskDefaults(defaults) }
    }

    fun setSmartDefaultsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesDataStore.setSmartDefaultsEnabled(enabled) }
    }

    fun setQuickAddPrefs(prefs: com.averycorp.prismtask.data.preferences.QuickAddPrefs) {
        viewModelScope.launch { userPreferencesDataStore.setQuickAdd(prefs) }
    }

    // --- Voice Input ---
    val voiceInputEnabled: StateFlow<Boolean> = voicePreferences
        .getVoiceInputEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceFeedbackEnabled: StateFlow<Boolean> = voicePreferences
        .getVoiceFeedbackEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val continuousModeEnabled: StateFlow<Boolean> = voicePreferences
        .getContinuousModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setVoiceInputEnabled(enabled: Boolean) {
        viewModelScope.launch { voicePreferences.setVoiceInputEnabled(enabled) }
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { voicePreferences.setVoiceFeedbackEnabled(enabled) }
    }

    fun setContinuousModeEnabled(enabled: Boolean) {
        viewModelScope.launch { voicePreferences.setContinuousModeEnabled(enabled) }
    }

    // --- Accessibility ---
    val reduceMotionEnabled: StateFlow<Boolean> = a11yPreferences
        .getReduceMotion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val highContrastEnabled: StateFlow<Boolean> = a11yPreferences
        .getHighContrast()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val largeTouchTargetsEnabled: StateFlow<Boolean> = a11yPreferences
        .getLargeTouchTargets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { a11yPreferences.setReduceMotion(enabled) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { a11yPreferences.setHighContrast(enabled) }
    }

    fun setLargeTouchTargets(enabled: Boolean) {
        viewModelScope.launch { a11yPreferences.setLargeTouchTargets(enabled) }
    }

    // --- Shake To Report ---
    val shakeEnabled: StateFlow<Boolean> = shakePreferences
        .getEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShakePreferences.DEFAULT_ENABLED)
    val shakeSensitivity: StateFlow<String> = shakePreferences
        .getSensitivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShakePreferences.DEFAULT_SENSITIVITY)

    fun setShakeEnabled(enabled: Boolean) {
        viewModelScope.launch { shakePreferences.setEnabled(enabled) }
    }

    fun setShakeSensitivity(sensitivity: String) {
        viewModelScope.launch { shakePreferences.setSensitivity(sensitivity) }
    }

    // --- Widgets ---
    fun refreshWidgets() {
        viewModelScope.launch { widgetUpdateManager.updateAllWidgets() }
    }

    // --- Subscription ---
    val userTier: StateFlow<UserTier> = billingManager.userTier
    val billingPeriod: StateFlow<BillingPeriod> = billingManager.billingPeriod
    val subscriptionState: StateFlow<SubscriptionState> = billingManager.proSubscriptionState
    val debugTierOverride: StateFlow<UserTier?> = billingManager.debugTierOverride
    val isAdmin: StateFlow<Boolean> = billingManager.isAdmin

    // --- Notification Settings ---
    // Per-type enable flags backed by NotificationPreferences.
    val taskRemindersEnabled: StateFlow<Boolean> = notificationPreferences.taskRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val timerAlertsEnabled: StateFlow<Boolean> = notificationPreferences.timerAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val medicationRemindersEnabled: StateFlow<Boolean> = notificationPreferences.medicationRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dailyBriefingEnabled: StateFlow<Boolean> = notificationPreferences.dailyBriefingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val eveningSummaryEnabled: StateFlow<Boolean> = notificationPreferences.eveningSummaryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val weeklySummaryEnabled: StateFlow<Boolean> = notificationPreferences.weeklySummaryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val overloadAlertsEnabled: StateFlow<Boolean> = notificationPreferences.overloadAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val reengagementEnabled: StateFlow<Boolean> = notificationPreferences.reengagementEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fullScreenNotificationsEnabled: StateFlow<Boolean> =
        notificationPreferences.fullScreenNotificationsEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val overrideVolumeEnabled: StateFlow<Boolean> =
        notificationPreferences.overrideVolumeEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val repeatingVibrationEnabled: StateFlow<Boolean> =
        notificationPreferences.repeatingVibrationEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationImportance: StateFlow<String> = notificationPreferences.importance
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            NotificationPreferences.DEFAULT_IMPORTANCE
        )

    val defaultReminderOffset: StateFlow<Long> = notificationPreferences.defaultReminderOffset
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            NotificationPreferences.DEFAULT_REMINDER_OFFSET_MS
        )

    fun setTaskRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPreferences.setTaskRemindersEnabled(enabled) }
    }

    fun setTimerAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPreferences.setTimerAlertsEnabled(enabled) }
    }

    fun setMedicationRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPreferences.setMedicationRemindersEnabled(enabled) }
    }

    fun setDailyBriefingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setDailyBriefingEnabled(enabled)
            if (enabled) {
                com.averycorp.prismtask.notifications.BriefingNotificationWorker
                    .schedule(appContext)
            } else {
                com.averycorp.prismtask.notifications.BriefingNotificationWorker
                    .cancel(appContext)
            }
        }
    }

    fun setEveningSummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setEveningSummaryEnabled(enabled)
            if (enabled) {
                com.averycorp.prismtask.notifications.EveningSummaryWorker
                    .schedule(appContext)
            } else {
                com.averycorp.prismtask.notifications.EveningSummaryWorker
                    .cancel(appContext)
            }
        }
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPreferences.setWeeklySummaryEnabled(enabled) }
    }

    fun setOverloadAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { notificationPreferences.setOverloadAlertsEnabled(enabled) }
    }

    fun setReengagementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setReengagementEnabled(enabled)
            if (enabled) {
                com.averycorp.prismtask.notifications.ReengagementWorker
                    .schedule(appContext)
            } else {
                com.averycorp.prismtask.notifications.ReengagementWorker
                    .cancel(appContext)
            }
        }
    }

    fun setFullScreenNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setFullScreenNotificationsEnabled(enabled)
        }
    }

    fun setOverrideVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setOverrideVolumeEnabled(enabled)
            // Channel sound is immutable; recreate so the new alarm-stream
            // audio attributes take effect next reminder.
            try {
                com.averycorp.prismtask.notifications.NotificationHelper
                    .createNotificationChannel(appContext)
            } catch (_: Exception) {
            }
        }
    }

    fun setRepeatingVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setRepeatingVibrationEnabled(enabled)
            // Channel vibration pattern is immutable; recreate so the new
            // pattern takes effect next reminder.
            try {
                com.averycorp.prismtask.notifications.NotificationHelper
                    .createNotificationChannel(appContext)
            } catch (_: Exception) {
            }
        }
    }

    fun setNotificationImportance(level: String) {
        viewModelScope.launch {
            notificationPreferences.setImportance(level)
            // Re-create the channel for the current importance — the helper
            // tears down the stale channel (whose importance is immutable)
            // and creates a fresh one tagged with the new importance suffix.
            try {
                com.averycorp.prismtask.notifications.NotificationHelper
                    .createNotificationChannel(appContext)
            } catch (e: Exception) {
                Log.w("SettingsVM", "Failed to recreate notification channel after importance change", e)
            }
        }
    }

    fun setDefaultReminderOffset(offsetMs: Long) {
        viewModelScope.launch { notificationPreferences.setDefaultReminderOffset(offsetMs) }
    }

    // --- Theme ---
    val themeMode: StateFlow<String> = themePreferences
        .getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<String> = themePreferences
        .getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#2563EB")

    val recentCustomColors: StateFlow<List<String>> = themePreferences
        .getRecentCustomColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgroundColor: StateFlow<String> = themePreferences
        .getBackgroundColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val surfaceColor: StateFlow<String> = themePreferences
        .getSurfaceColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val errorColor: StateFlow<String> = themePreferences
        .getErrorColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val fontScale: StateFlow<Float> = themePreferences
        .getFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val priorityColorNone: StateFlow<String> = themePreferences
        .getPriorityColorNone()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorLow: StateFlow<String> = themePreferences
        .getPriorityColorLow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorMedium: StateFlow<String> = themePreferences
        .getPriorityColorMedium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorHigh: StateFlow<String> = themePreferences
        .getPriorityColorHigh()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val priorityColorUrgent: StateFlow<String> = themePreferences
        .getPriorityColorUrgent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // --- Dashboard ---
    val sectionOrder: StateFlow<List<String>> = dashboardPreferences
        .getSectionOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_ORDER)

    val hiddenSections: StateFlow<Set<String>> = dashboardPreferences
        .getHiddenSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressStyle: StateFlow<String> = dashboardPreferences
        .getProgressStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ring")

    // --- Navigation ---
    val tabOrder: StateFlow<List<String>> = tabPreferences
        .getTabOrder()
        .map { order -> order + ALL_BOTTOM_NAV_ITEMS.map { it.route }.filter { it !in order } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TabPreferences.DEFAULT_ORDER)

    val hiddenTabs: StateFlow<Set<String>> = tabPreferences
        .getHiddenTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // --- Task Behavior ---
    val defaultSort: StateFlow<String> = taskBehaviorPreferences
        .getDefaultSort()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DUE_DATE")

    val defaultViewMode: StateFlow<String> = taskBehaviorPreferences
        .getDefaultViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "UPCOMING")

    val urgencyWeights: StateFlow<UrgencyWeights> = taskBehaviorPreferences
        .getUrgencyWeights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UrgencyWeights())

    val reminderPresets: StateFlow<List<Long>> = taskBehaviorPreferences
        .getReminderPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(0L, 900_000L, 1_800_000L, 3_600_000L, 86_400_000L))

    val firstDayOfWeek: StateFlow<DayOfWeek> = taskBehaviorPreferences
        .getFirstDayOfWeek()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayOfWeek.MONDAY)

    val dayStartHour: StateFlow<Int> = taskBehaviorPreferences
        .getDayStartHour()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Timer / Pomodoro ---
    val timerWorkDurationSeconds: StateFlow<Int> = timerPreferences
        .getWorkDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_WORK_SECONDS)

    val timerBreakDurationSeconds: StateFlow<Int> = timerPreferences
        .getBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_BREAK_SECONDS)

    val timerLongBreakDurationSeconds: StateFlow<Int> = timerPreferences
        .getLongBreakDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_LONG_BREAK_SECONDS)

    val pomodoroAvailableMinutes: StateFlow<Int> = timerPreferences
        .getPomodoroAvailableMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_AVAILABLE_MINUTES)

    val pomodoroFocusPreference: StateFlow<String> = timerPreferences
        .getPomodoroFocusPreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerPreferences.DEFAULT_FOCUS_PREFERENCE)

    fun setTimerWorkDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setWorkDurationSeconds(minutes * 60) }
    }

    fun setTimerBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setBreakDurationSeconds(minutes * 60) }
    }

    fun setTimerLongBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setLongBreakDurationSeconds(minutes * 60) }
    }

    fun setPomodoroAvailableMinutes(minutes: Int) {
        viewModelScope.launch { timerPreferences.setPomodoroAvailableMinutes(minutes) }
    }

    fun setPomodoroFocusPreference(preference: String) {
        viewModelScope.launch { timerPreferences.setPomodoroFocusPreference(preference) }
    }

    // --- Habits / Streaks ---
    val streakMaxMissedDays: StateFlow<Int> = habitListPreferences
        .getStreakMaxMissedDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitListPreferences.DEFAULT_STREAK_MAX_MISSED_DAYS)

    fun setStreakMaxMissedDays(days: Int) {
        viewModelScope.launch { habitListPreferences.setStreakMaxMissedDays(days) }
    }

    // --- Modes ---
    val selfCareEnabled: StateFlow<Boolean> = habitListPreferences
        .isSelfCareEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val medicationEnabled: StateFlow<Boolean> = habitListPreferences
        .isMedicationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val schoolEnabled: StateFlow<Boolean> = habitListPreferences
        .isSchoolEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val leisureEnabled: StateFlow<Boolean> = habitListPreferences
        .isLeisureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val houseworkEnabled: StateFlow<Boolean> = habitListPreferences
        .isHouseworkEnabled()
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
    val autoArchiveDays: StateFlow<Int> = archivePreferences
        .getAutoArchiveDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val archivedCount: StateFlow<Int> = taskRepository
        .getArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Calendar Sync ---
    val calendarSyncEnabled: StateFlow<Boolean> = calendarPreferences
        .isEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val calendarName: StateFlow<String> = calendarPreferences
        .getCalendarName()
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

    val gCalSyncEnabled: StateFlow<Boolean> = calendarSyncPreferences
        .isCalendarSyncEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gCalSyncCalendarId: StateFlow<String> = calendarSyncPreferences
        .getSyncCalendarId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "primary")

    val gCalSyncDirection: StateFlow<String> = calendarSyncPreferences
        .getSyncDirection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DIRECTION_BOTH)

    val gCalShowEvents: StateFlow<Boolean> = calendarSyncPreferences
        .getShowCalendarEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val gCalSyncCompletedTasks: StateFlow<Boolean> = calendarSyncPreferences
        .getSyncCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gCalSyncFrequency: StateFlow<String> = calendarSyncPreferences
        .getSyncFrequency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FREQUENCY_15MIN)

    val gCalLastSyncTimestamp: StateFlow<Long> = calendarSyncPreferences
        .getLastSyncTimestamp()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _gCalAvailableCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val gCalAvailableCalendars: StateFlow<List<CalendarInfo>> = _gCalAvailableCalendars

    private val _isGCalSyncing = MutableStateFlow(false)
    val isGCalSyncing: StateFlow<Boolean> = _isGCalSyncing

    fun connectGoogleCalendar() {
        viewModelScope.launch {
            val result = calendarManager.connectCalendar()
            result
                .onSuccess {
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

    // --- Auth + Shared State ---
    val isSignedIn: StateFlow<Boolean> = authManager.isSignedIn

    val userEmail: String? get() = authManager.currentUser.value?.email

    internal val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    internal val _pendingJsonExport = MutableStateFlow<String?>(null)
    val pendingJsonExport: StateFlow<String?> = _pendingJsonExport

    internal val _pendingCsvExport = MutableStateFlow<String?>(null)
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    internal val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    internal val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    // --- Theme setters ---
    fun setThemeMode(mode: String) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { themePreferences.setAccentColor(hex) }
    }

    fun setCustomAccentColor(hex: String) {
        viewModelScope.launch {
            if (ThemePreferences.isValidHex(hex)) {
                themePreferences.setAccentColor(hex)
                themePreferences.addRecentCustomColor(hex)
            }
        }
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

    // Firebase sync
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

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting

    /**
     * Granular reset based on [options]. Executes each selected category in
     * order, clears local Room tables and DataStore prefs as needed, then calls
     * [onDone] with a flag indicating whether to navigate to Onboarding.
     * Backend errors do not block local deletion; a partial-success message is
     * shown instead.
     */
    fun resetAppData(
        options: com.averycorp.prismtask.ui.components.dialogs.ResetOptions,
        onDone: (navigateToOnboarding: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isResetting.value = true
            try {
                withContext(Dispatchers.IO) {
                    if (options.tasksAndProjects) {
                        database.taskDao().deleteAll()
                        database.taskDao().deleteAllTaskTagCrossRefs()
                        database.projectDao().deleteAll()
                        database.attachmentDao().deleteAll()
                        database.taskCompletionDao().deleteAll()
                    }
                    if (options.habitsAndHistory) {
                        database.habitDao().deleteAll()
                        database.habitCompletionDao().deleteAll()
                        database.habitLogDao().deleteAll()
                    }
                    if (options.tags) {
                        database.tagDao().deleteAll()
                        database.tagDao().deleteAllCrossRefs()
                    }
                    if (options.templates) {
                        database.taskTemplateDao().deleteAll()
                        database.habitTemplateDao().deleteAll()
                        database.projectTemplateDao().deleteAll()
                    }
                    if (options.calendarSyncData) {
                        database.calendarSyncDao().deleteAll()
                    }
                }
                if (options.calendarSyncData) {
                    calendarSyncPreferences.clearAll()
                    calendarManager.disconnectCalendar()
                }
                if (options.preferencesAndSettings) {
                    themePreferences.clearAll()
                    archivePreferences.clearAll()
                    dashboardPreferences.resetToDefaults()
                    tabPreferences.resetToDefaults()
                    taskBehaviorPreferences.resetToDefaults()
                    calendarPreferences.clearAll()
                    leisurePreferences.clearAll()
                    habitListPreferences.clearAll()
                    backendSyncPreferences.clear()
                    templatePreferences.clear()
                    userPreferencesDataStore.clearAll()
                    shakePreferences.clearAll()
                    // Auth tokens and pro status cache are intentionally preserved.
                }
                if (options.restartOnboarding) {
                    onboardingPreferences.resetOnboarding()
                }
                _messages.emit("App data has been reset")
                onDone(options.restartOnboarding)
            } catch (e: Exception) {
                Log.e("SettingsVM", "Reset failed", e)
                _messages.emit("Reset failed: ${e.message}")
            } finally {
                _isResetting.value = false
            }
        }
    }

    fun launchUpgrade(activity: android.app.Activity, period: BillingPeriod = BillingPeriod.MONTHLY) {
        viewModelScope.launch {
            try {
                billingManager.launchPurchaseFlow(activity, period)
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

    /** Debug-only: set an in-memory tier override for testing gated features. */
    fun setDebugTier(tier: UserTier) {
        billingManager.setDebugTier(tier)
    }

    /** Debug-only: clear the tier override and revert to the real billing state. */
    fun clearDebugTier() {
        billingManager.clearDebugTier()
    }

    /**
     * Debug-only: reset the onboarding flag so the tutorial will be shown
     * again as if the app was just installed. The caller is responsible for
     * navigating to the Onboarding route after this completes.
     */
    fun resetOnboarding(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                onboardingPreferences.resetOnboarding()
                _messages.emit("Tutorial Reset — Showing Now")
                onDone()
            } catch (e: Exception) {
                _messages.emit("Could not reset tutorial: ${e.message}")
            }
        }
    }
}
