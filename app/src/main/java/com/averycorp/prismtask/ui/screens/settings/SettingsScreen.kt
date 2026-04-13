package com.averycorp.prismtask.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.settings.sections.AboutSection
import com.averycorp.prismtask.ui.screens.settings.sections.AccessibilitySection
import com.averycorp.prismtask.ui.screens.settings.sections.AccountSyncSection
import com.averycorp.prismtask.ui.screens.settings.sections.AiNotificationsSection
import com.averycorp.prismtask.ui.screens.settings.sections.AiSection
import com.averycorp.prismtask.ui.screens.settings.sections.AppearanceSection
import com.averycorp.prismtask.ui.screens.settings.sections.BackupExportSection
import com.averycorp.prismtask.ui.screens.settings.sections.DashboardSection
import com.averycorp.prismtask.ui.screens.settings.sections.DataSection
import com.averycorp.prismtask.ui.screens.settings.sections.DebugOnboardingSection
import com.averycorp.prismtask.ui.screens.settings.sections.DebugTierSection
import com.averycorp.prismtask.ui.screens.settings.sections.DeviceCalendarSection
import com.averycorp.prismtask.ui.screens.settings.sections.DisplaySection
import com.averycorp.prismtask.ui.screens.settings.sections.GoogleCalendarSection
import com.averycorp.prismtask.ui.screens.settings.sections.HabitsSection
import com.averycorp.prismtask.ui.screens.settings.sections.ModesSection
import com.averycorp.prismtask.ui.screens.settings.sections.NavigationSection
import com.averycorp.prismtask.ui.screens.settings.sections.SubscriptionSection
import com.averycorp.prismtask.ui.screens.settings.sections.SwipeActionsSection
import com.averycorp.prismtask.ui.screens.settings.sections.TaskDefaultsSection
import com.averycorp.prismtask.ui.screens.settings.sections.TimerSection
import com.averycorp.prismtask.ui.screens.settings.sections.VoiceInputSection
import com.averycorp.prismtask.ui.screens.settings.sections.BoundariesSection
import com.averycorp.prismtask.ui.screens.settings.sections.CheckInStreakSection
import com.averycorp.prismtask.ui.screens.settings.sections.ClinicalReportSection
import com.averycorp.prismtask.ui.screens.settings.sections.ForgivenessStreakSection
import com.averycorp.prismtask.ui.screens.settings.sections.HelpFeedbackSection
import com.averycorp.prismtask.ui.screens.settings.sections.WorkLifeBalanceSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Subscription
    val userTier by viewModel.userTier.collectAsStateWithLifecycle()
    val debugTierOverride by viewModel.debugTierOverride.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    // Appearance
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val backgroundColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
    val surfaceColor by viewModel.surfaceColor.collectAsStateWithLifecycle()
    val errorColor by viewModel.errorColor.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val priorityColorNone by viewModel.priorityColorNone.collectAsStateWithLifecycle()
    val priorityColorLow by viewModel.priorityColorLow.collectAsStateWithLifecycle()
    val priorityColorMedium by viewModel.priorityColorMedium.collectAsStateWithLifecycle()
    val priorityColorHigh by viewModel.priorityColorHigh.collectAsStateWithLifecycle()
    val priorityColorUrgent by viewModel.priorityColorUrgent.collectAsStateWithLifecycle()
    val recentCustomColors by viewModel.recentCustomColors.collectAsStateWithLifecycle()

    // Display + Swipe
    val appearancePrefs by viewModel.appearancePrefs.collectAsStateWithLifecycle()
    val swipePrefs by viewModel.swipePrefs.collectAsStateWithLifecycle()

    // Work-Life Balance (v1.4.0 V1)
    val workLifeBalancePrefs by viewModel.workLifeBalancePrefs.collectAsStateWithLifecycle()

    // Forgiveness-first streaks (v1.4.0 V5)
    val forgivenessPrefs by viewModel.forgivenessPrefs.collectAsStateWithLifecycle()

    // Boundary rules (v1.4.0 V3)
    val boundaryRules by viewModel.boundaryRules.collectAsStateWithLifecycle()
    var showAddBoundaryDialog by remember { mutableStateOf(false) }

    // Clinical report export (v1.4.0 V8)
    val isExportingClinicalReport by viewModel.isExportingClinicalReport.collectAsStateWithLifecycle()
    val clinicalReportUri by viewModel.clinicalReportUri.collectAsStateWithLifecycle()

    // Check-in streak (v1.4.0 V4)
    val checkInStreak by viewModel.checkInStreak.collectAsStateWithLifecycle()

    // About
    val latestReleaseTag by viewModel.latestReleaseTag.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clinicalReportUri) {
        val uri = clinicalReportUri
        if (uri != null) {
            snackbarHostState.showSnackbar("Health report saved to Downloads")
            viewModel.clearClinicalReportUri()
        }
    }

    // Data
    val autoArchiveDays by viewModel.autoArchiveDays.collectAsStateWithLifecycle()
    val claudeApiKey by viewModel.claudeApiKey.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val isResetting by viewModel.isResetting.collectAsStateWithLifecycle()

    // Sync
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val pendingJson by viewModel.pendingJsonExport.collectAsStateWithLifecycle()
    val pendingCsv by viewModel.pendingCsvExport.collectAsStateWithLifecycle()

    // Dashboard / Tabs
    val sectionOrder by viewModel.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val progressStyle by viewModel.progressStyle.collectAsStateWithLifecycle()
    val tabOrder by viewModel.tabOrder.collectAsStateWithLifecycle()
    val hiddenTabs by viewModel.hiddenTabs.collectAsStateWithLifecycle()

    // Task defaults
    val defaultSort by viewModel.defaultSort.collectAsStateWithLifecycle()
    val defaultViewMode by viewModel.defaultViewMode.collectAsStateWithLifecycle()
    val urgencyWeights by viewModel.urgencyWeights.collectAsStateWithLifecycle()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    val dayStartHour by viewModel.dayStartHour.collectAsStateWithLifecycle()

    // Voice / Accessibility
    val voiceInputEnabled by viewModel.voiceInputEnabled.collectAsStateWithLifecycle()
    val voiceFeedbackEnabled by viewModel.voiceFeedbackEnabled.collectAsStateWithLifecycle()
    val continuousModeEnabled by viewModel.continuousModeEnabled.collectAsStateWithLifecycle()
    val reduceMotionEnabled by viewModel.reduceMotionEnabled.collectAsStateWithLifecycle()
    val highContrastEnabled by viewModel.highContrastEnabled.collectAsStateWithLifecycle()
    val largeTouchTargetsEnabled by viewModel.largeTouchTargetsEnabled.collectAsStateWithLifecycle()

    // Timer / Pomodoro
    val timerWorkSeconds by viewModel.timerWorkDurationSeconds.collectAsStateWithLifecycle()
    val timerBreakSeconds by viewModel.timerBreakDurationSeconds.collectAsStateWithLifecycle()
    val timerLongBreakSeconds by viewModel.timerLongBreakDurationSeconds.collectAsStateWithLifecycle()
    val pomodoroAvailableMinutes by viewModel.pomodoroAvailableMinutes.collectAsStateWithLifecycle()
    val pomodoroFocusPreference by viewModel.pomodoroFocusPreference.collectAsStateWithLifecycle()

    // Habits
    val streakMaxMissedDays by viewModel.streakMaxMissedDays.collectAsStateWithLifecycle()

    // Calendar (device)
    val calendarSyncEnabled by viewModel.calendarSyncEnabled.collectAsStateWithLifecycle()
    val calendarName by viewModel.calendarName.collectAsStateWithLifecycle()
    val availableCalendars by viewModel.availableCalendars.collectAsStateWithLifecycle()

    // Google Calendar
    val isGCalConnected by viewModel.isGCalConnected.collectAsStateWithLifecycle()
    val gCalAccountEmail by viewModel.gCalAccountEmail.collectAsStateWithLifecycle()
    val gCalSyncEnabled by viewModel.gCalSyncEnabled.collectAsStateWithLifecycle()
    val gCalSyncCalendarId by viewModel.gCalSyncCalendarId.collectAsStateWithLifecycle()
    val gCalSyncDirection by viewModel.gCalSyncDirection.collectAsStateWithLifecycle()
    val gCalShowEvents by viewModel.gCalShowEvents.collectAsStateWithLifecycle()
    val gCalSyncCompletedTasks by viewModel.gCalSyncCompletedTasks.collectAsStateWithLifecycle()
    val gCalSyncFrequency by viewModel.gCalSyncFrequency.collectAsStateWithLifecycle()
    val gCalLastSyncTimestamp by viewModel.gCalLastSyncTimestamp.collectAsStateWithLifecycle()
    val gCalAvailableCalendars by viewModel.gCalAvailableCalendars.collectAsStateWithLifecycle()
    val isGCalSyncing by viewModel.isGCalSyncing.collectAsStateWithLifecycle()

    // Modes
    val selfCareEnabled by viewModel.selfCareEnabled.collectAsStateWithLifecycle()
    val medicationEnabled by viewModel.medicationEnabled.collectAsStateWithLifecycle()
    val schoolEnabled by viewModel.schoolEnabled.collectAsStateWithLifecycle()
    val leisureEnabled by viewModel.leisureEnabled.collectAsStateWithLifecycle()
    val houseworkEnabled by viewModel.houseworkEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Activity result launchers for export/import
    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val data = pendingJson ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data.toByteArray())
            }
            viewModel.clearPendingExports()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val data = pendingCsv ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data.toByteArray())
            }
            viewModel.clearPendingExports()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
            if (jsonString != null) {
                viewModel.onImportJson(jsonString)
            }
        }
    }

    LaunchedEffect(pendingJson) {
        if (pendingJson != null) createJsonLauncher.launch("prismtask_backup.json")
    }

    LaunchedEffect(pendingCsv) {
        if (pendingCsv != null) createCsvLauncher.launch("prismtask_tasks.csv")
    }

    if (showAddBoundaryDialog) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddBoundaryDialog = false },
            title = { Text("Add Boundary Rule") },
            text = {
                Column {
                    Text(
                        "Describe a rule in plain English, e.g. 'No work after 7pm on weekdays'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = false },
                        isError = error,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) {
                        Text(
                            "Couldn't parse that. Try 'No work after 8pm'.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (viewModel.addBoundaryRuleFromNlp(text)) {
                        showAddBoundaryDialog = false
                    } else {
                        error = true
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddBoundaryDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = isSyncing || isImporting || isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SubscriptionSection(
                    userTier = userTier,
                    onLaunchUpgrade = { activity, tier -> viewModel.launchUpgrade(activity, tier) },
                    onRestorePurchases = { viewModel.restorePurchases() }
                )

                AppearanceSection(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    recentCustomColors = recentCustomColors,
                    backgroundColor = backgroundColor,
                    surfaceColor = surfaceColor,
                    errorColor = errorColor,
                    priorityColorNone = priorityColorNone,
                    priorityColorLow = priorityColorLow,
                    priorityColorMedium = priorityColorMedium,
                    priorityColorHigh = priorityColorHigh,
                    priorityColorUrgent = priorityColorUrgent,
                    fontScale = fontScale,
                    onThemeModeChange = viewModel::setThemeMode,
                    onAccentColorChange = viewModel::setAccentColor,
                    onCustomAccentColorChange = viewModel::setCustomAccentColor,
                    onFontScaleChange = viewModel::setFontScale,
                    onBackgroundColorChange = viewModel::setBackgroundColor,
                    onSurfaceColorChange = viewModel::setSurfaceColor,
                    onErrorColorChange = viewModel::setErrorColor,
                    onPriorityColorChange = viewModel::setPriorityColor,
                    onResetColorOverrides = viewModel::resetColorOverrides
                )

                DisplaySection(
                    appearancePrefs = appearancePrefs,
                    onCompactModeChange = viewModel::setCompactMode,
                    onShowCardBordersChange = viewModel::setShowCardBorders,
                    onCardCornerRadiusChange = viewModel::setCardCornerRadius
                )

                SwipeActionsSection(
                    swipePrefs = swipePrefs,
                    onSwipeRightChange = viewModel::setSwipeRight,
                    onSwipeLeftChange = viewModel::setSwipeLeft
                )

                DashboardSection(
                    progressStyle = progressStyle,
                    sectionOrder = sectionOrder,
                    hiddenSections = hiddenSections,
                    onProgressStyleChange = viewModel::setProgressStyle,
                    onHiddenSectionsChange = viewModel::setHiddenSections,
                    onSectionOrderChange = viewModel::setSectionOrder,
                    onResetDashboardDefaults = viewModel::resetDashboardDefaults
                )

                ModesSection(
                    selfCareEnabled = selfCareEnabled,
                    medicationEnabled = medicationEnabled,
                    houseworkEnabled = houseworkEnabled,
                    schoolEnabled = schoolEnabled,
                    leisureEnabled = leisureEnabled,
                    onSelfCareChange = viewModel::setSelfCareEnabled,
                    onMedicationChange = viewModel::setMedicationEnabled,
                    onHouseworkChange = viewModel::setHouseworkEnabled,
                    onSchoolChange = viewModel::setSchoolEnabled,
                    onLeisureChange = viewModel::setLeisureEnabled
                )

                NavigationSection(
                    tabOrder = tabOrder,
                    hiddenTabs = hiddenTabs,
                    onHiddenTabsChange = viewModel::setHiddenTabs,
                    onTabOrderChange = viewModel::setTabOrder,
                    onResetTabDefaults = viewModel::resetTabDefaults
                )

                WorkLifeBalanceSection(
                    prefs = workLifeBalancePrefs,
                    onPrefsChange = viewModel::setWorkLifeBalancePrefs
                )

                ForgivenessStreakSection(
                    prefs = forgivenessPrefs,
                    onPrefsChange = viewModel::setForgivenessPrefs
                )

                BoundariesSection(
                    rules = boundaryRules,
                    onToggle = { rule, enabled -> viewModel.toggleBoundaryRule(rule, enabled) },
                    onDelete = viewModel::deleteBoundaryRule,
                    onAdd = { showAddBoundaryDialog = true }
                )

                CheckInStreakSection(streak = checkInStreak)

                ClinicalReportSection(
                    isExporting = isExportingClinicalReport,
                    onExportReport = { viewModel.exportClinicalReport() }
                )

                TaskDefaultsSection(
                    defaultSort = defaultSort,
                    defaultViewMode = defaultViewMode,
                    firstDayOfWeek = firstDayOfWeek,
                    dayStartHour = dayStartHour,
                    urgencyWeights = urgencyWeights,
                    onDefaultSortChange = viewModel::setDefaultSort,
                    onDefaultViewModeChange = viewModel::setDefaultViewMode,
                    onFirstDayOfWeekChange = viewModel::setFirstDayOfWeek,
                    onDayStartHourChange = viewModel::setDayStartHour,
                    onUrgencyWeightsChange = viewModel::setUrgencyWeights,
                    onResetTaskBehaviorDefaults = viewModel::resetTaskBehaviorDefaults
                )

                TimerSection(
                    timerWorkSeconds = timerWorkSeconds,
                    timerBreakSeconds = timerBreakSeconds,
                    timerLongBreakSeconds = timerLongBreakSeconds,
                    pomodoroAvailableMinutes = pomodoroAvailableMinutes,
                    pomodoroFocusPreference = pomodoroFocusPreference,
                    onTimerWorkMinutesChange = viewModel::setTimerWorkDurationMinutes,
                    onTimerBreakMinutesChange = viewModel::setTimerBreakDurationMinutes,
                    onTimerLongBreakMinutesChange = viewModel::setTimerLongBreakDurationMinutes,
                    onPomodoroAvailableMinutesChange = viewModel::setPomodoroAvailableMinutes,
                    onPomodoroFocusPreferenceChange = viewModel::setPomodoroFocusPreference
                )

                HabitsSection(
                    streakMaxMissedDays = streakMaxMissedDays,
                    onStreakMaxMissedDaysChange = viewModel::setStreakMaxMissedDays
                )

                DataSection(
                    autoArchiveDays = autoArchiveDays,
                    archivedCount = archivedCount,
                    isResetting = isResetting,
                    onAutoArchiveDaysChange = viewModel::setAutoArchiveDays,
                    onResetAppData = { options ->
                        viewModel.resetAppData(options) { navigateToOnboarding ->
                            if (navigateToOnboarding) {
                                navController.navigate(PrismTaskRoute.Onboarding.route) {
                                    popUpTo(PrismTaskRoute.MainTabs.route) { inclusive = true }
                                }
                            } else if (options.preferencesAndSettings) {
                                (context as? android.app.Activity)?.recreate()
                            }
                        }
                    },
                    onNavigateToTags = { navController.navigate("tag_management") },
                    onNavigateToProjects = { navController.navigate("project_list") },
                    onNavigateToTemplates = { navController.navigate("templates") },
                    onNavigateToArchive = { navController.navigate("archive") }
                )

                BackupExportSection(
                    onExportJson = viewModel::onExportJson,
                    onExportCsv = viewModel::onExportCsv,
                    onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )

                AccountSyncSection(
                    isSignedIn = isSignedIn,
                    userEmail = viewModel.userEmail,
                    isSyncing = isSyncing,
                    onSync = viewModel::onSync,
                    onSignOut = viewModel::onSignOut,
                    onSignIn = { navController.navigate("auth") }
                )

                DeviceCalendarSection(
                    calendarSyncEnabled = calendarSyncEnabled,
                    calendarName = calendarName,
                    availableCalendars = availableCalendars,
                    onLoadCalendars = viewModel::loadCalendars,
                    onSelectCalendar = viewModel::selectCalendar,
                    onSetCalendarSyncEnabled = viewModel::setCalendarSyncEnabled
                )

                GoogleCalendarSection(
                    isGCalConnected = isGCalConnected,
                    gCalAccountEmail = gCalAccountEmail,
                    gCalSyncEnabled = gCalSyncEnabled,
                    gCalSyncCalendarId = gCalSyncCalendarId,
                    gCalAvailableCalendars = gCalAvailableCalendars,
                    gCalSyncDirection = gCalSyncDirection,
                    gCalShowEvents = gCalShowEvents,
                    gCalSyncCompletedTasks = gCalSyncCompletedTasks,
                    gCalSyncFrequency = gCalSyncFrequency,
                    gCalLastSyncTimestamp = gCalLastSyncTimestamp,
                    isGCalSyncing = isGCalSyncing,
                    onConnectGoogleCalendar = viewModel::connectGoogleCalendar,
                    onDisconnectGoogleCalendar = viewModel::disconnectGoogleCalendar,
                    onSetGCalSyncEnabled = viewModel::setGCalSyncEnabled,
                    onLoadGCalCalendars = viewModel::loadGCalCalendars,
                    onSetGCalSyncCalendarId = viewModel::setGCalSyncCalendarId,
                    onSetGCalSyncDirection = viewModel::setGCalSyncDirection,
                    onSetGCalShowEvents = viewModel::setGCalShowEvents,
                    onSetGCalSyncCompletedTasks = viewModel::setGCalSyncCompletedTasks,
                    onSetGCalSyncFrequency = viewModel::setGCalSyncFrequency,
                    onSyncGCalNow = viewModel::syncGCalNow
                )

                AiSection(
                    claudeApiKey = claudeApiKey,
                    onSetClaudeApiKey = viewModel::setClaudeApiKey,
                    onClearClaudeApiKey = viewModel::clearClaudeApiKey,
                    onNavigateToEisenhower = { navController.navigate(PrismTaskRoute.EisenhowerMatrix.route) },
                    onNavigateToSmartPomodoro = { navController.navigate(PrismTaskRoute.SmartPomodoro.route) },
                    onNavigateToDailyBriefing = { navController.navigate(PrismTaskRoute.DailyBriefing.route) },
                    onNavigateToWeeklyPlanner = { navController.navigate(PrismTaskRoute.WeeklyPlanner.route) },
                    onNavigateToTimeline = { navController.navigate(PrismTaskRoute.Timeline.route) }
                )

                AiNotificationsSection(
                    eveningSummaryEnabled = viewModel.eveningSummaryEnabled,
                    reengagementEnabled = viewModel.reengagementEnabled,
                    onEveningSummaryToggle = viewModel::onEveningSummaryToggle,
                    onReengagementToggle = viewModel::onReengagementToggle
                )

                VoiceInputSection(
                    voiceInputEnabled = voiceInputEnabled,
                    voiceFeedbackEnabled = voiceFeedbackEnabled,
                    continuousModeEnabled = continuousModeEnabled,
                    onVoiceInputEnabledChange = viewModel::setVoiceInputEnabled,
                    onVoiceFeedbackEnabledChange = viewModel::setVoiceFeedbackEnabled,
                    onContinuousModeEnabledChange = viewModel::setContinuousModeEnabled
                )

                AccessibilitySection(
                    reduceMotionEnabled = reduceMotionEnabled,
                    highContrastEnabled = highContrastEnabled,
                    largeTouchTargetsEnabled = largeTouchTargetsEnabled,
                    onReduceMotionChange = viewModel::setReduceMotion,
                    onHighContrastChange = viewModel::setHighContrast,
                    onLargeTouchTargetsChange = viewModel::setLargeTouchTargets
                )

                HelpFeedbackSection(
                    onNavigateToBugReport = {
                        navController.navigate(
                            com.averycorp.prismtask.ui.navigation.PrismTaskRoute.BugReport.createRoute("Settings")
                        )
                    },
                    onNavigateToFeatureRequest = {
                        navController.navigate(
                            com.averycorp.prismtask.ui.navigation.PrismTaskRoute.FeatureRequest.route
                        )
                    },
                    onNavigateToMyReports = {
                        navController.navigate(
                            com.averycorp.prismtask.ui.navigation.PrismTaskRoute.MyReports.route
                        )
                    }
                )

                AboutSection(
                    latestReleaseTag = latestReleaseTag,
                    onRefreshWidgets = viewModel::refreshWidgets
                )

                if (isAdmin) {
                    DebugTierSection(
                        debugTierOverride = debugTierOverride,
                        onSetDebugTier = viewModel::setDebugTier,
                        onClearDebugTier = viewModel::clearDebugTier
                    )

                    DebugOnboardingSection(
                        onShowTutorial = {
                            viewModel.resetOnboarding {
                                navController.navigate(PrismTaskRoute.Onboarding.route) {
                                    popUpTo(PrismTaskRoute.MainTabs.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
