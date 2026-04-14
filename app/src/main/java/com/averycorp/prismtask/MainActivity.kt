package com.averycorp.prismtask

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.diagnostics.DiagnosticLogger
import com.averycorp.prismtask.data.preferences.AppearancePrefs
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.ShakePreferences
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.UpdateChecker
import com.averycorp.prismtask.data.remote.VersionInfo
import com.averycorp.prismtask.data.remote.sync.BackendSyncService
import com.averycorp.prismtask.domain.usecase.ScreenshotCapture
import com.averycorp.prismtask.domain.usecase.ShakeDetector
import com.averycorp.prismtask.notifications.NotificationHelper
import com.averycorp.prismtask.ui.components.UpdateDialog
import com.averycorp.prismtask.ui.navigation.PrismTaskNavGraph
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.theme.PriorityColors
import com.averycorp.prismtask.ui.theme.PrismTaskTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var tabPreferences: TabPreferences

    @Inject
    lateinit var syncService: SyncService

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var a11yPreferences: com.averycorp.prismtask.data.preferences.A11yPreferences

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var shakeDetector: ShakeDetector

    @Inject
    lateinit var shakePreferences: ShakePreferences

    @Inject
    lateinit var screenshotCapture: ScreenshotCapture

    @Inject
    lateinit var diagnosticLogger: DiagnosticLogger

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var backendSyncService: BackendSyncService

    /**
     * Snapshot of the shake-to-report user preference, read by onResume so we
     * only register the accelerometer listener when the feature is enabled.
     * Updated from a LaunchedEffect once DataStore emits.
     */
    @Volatile
    private var shakeFeatureEnabled: Boolean = ShakePreferences.DEFAULT_ENABLED

    companion object {
        /** Intent extra key set by the QuickAdd widget to route deep-links. */
        const val EXTRA_LAUNCH_ACTION = "com.averycorp.prismtask.LAUNCH_ACTION"
        const val ACTION_QUICK_ADD = "quick_add"
        const val ACTION_OPEN_TEMPLATES = "open_templates"
        const val ACTION_VOICE_INPUT = "voice_input"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to create notification channel", e)
        }
        try {
            syncService.startAutoSync()
        } catch (e: Exception) {
            Log.e("MainActivity", "Auto-sync failed to start", e)
        }
        try {
            billingManager.initialize(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Billing init failed", e)
        }
        try {
            setCrashlyticsUserId()
        } catch (e: Exception) {
            Log.e("MainActivity", "Crashlytics user ID setup failed", e)
        }
        val launchAction = intent?.getStringExtra(EXTRA_LAUNCH_ACTION)
        // v1.4.0 V9: support Android share-intent entry into the Paste
        // Conversation screen. When another app sends text to PrismTask
        // (ACTION_SEND, text/plain) the text is forwarded to the nav
        // graph which navigates to PasteConversation with a pre-filled
        // input. Non-share launches leave this null.
        val initialSharedText: String? = when {
            intent?.action == Intent.ACTION_SEND && intent?.type == "text/plain" ->
                intent?.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        setContent {
            var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }

            LaunchedEffect(Unit) {
                val checker = UpdateChecker(this@MainActivity)
                updateInfo = checker.checkForUpdate()
            }

            updateInfo?.let { info ->
                UpdateDialog(
                    versionInfo = info,
                    onUpdate = {
                        UpdateChecker(this@MainActivity).downloadAndInstall(info)
                        updateInfo = null
                    },
                    onDismiss = { updateInfo = null }
                )
            }

            val themeMode by themePreferences
                .getThemeMode()
                .collectAsStateWithLifecycle(initialValue = "system")
            val accentColor by themePreferences
                .getAccentColor()
                .collectAsStateWithLifecycle(initialValue = "#2563EB")
            val backgroundColorOverride by themePreferences
                .getBackgroundColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val surfaceColorOverride by themePreferences
                .getSurfaceColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val errorColorOverride by themePreferences
                .getErrorColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val fontScale by themePreferences
                .getFontScale()
                .collectAsStateWithLifecycle(initialValue = 1.0f)

            val priorityNone by themePreferences
                .getPriorityColorNone()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityLow by themePreferences
                .getPriorityColorLow()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityMedium by themePreferences
                .getPriorityColorMedium()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityHigh by themePreferences
                .getPriorityColorHigh()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityUrgent by themePreferences
                .getPriorityColorUrgent()
                .collectAsStateWithLifecycle(initialValue = "")

            val hasCompletedOnboarding by onboardingPreferences
                .hasCompletedOnboarding()
                .collectAsStateWithLifecycle(initialValue = null as Boolean?)

            val reduceMotion by a11yPreferences
                .getReduceMotion()
                .collectAsStateWithLifecycle(initialValue = false)
            val highContrast by a11yPreferences
                .getHighContrast()
                .collectAsStateWithLifecycle(initialValue = false)
            val largeTouchTargets by a11yPreferences
                .getLargeTouchTargets()
                .collectAsStateWithLifecycle(initialValue = false)

            val tabOrder by tabPreferences
                .getTabOrder()
                .collectAsStateWithLifecycle(initialValue = TabPreferences.DEFAULT_ORDER)
            val hiddenTabs by tabPreferences
                .getHiddenTabs()
                .collectAsStateWithLifecycle(initialValue = emptySet())

            val appearance by userPreferencesDataStore.appearanceFlow
                .collectAsStateWithLifecycle(initialValue = AppearancePrefs())

            val priorityColors = PriorityColors(
                none = parseColorOrDefault(priorityNone, PriorityColors().none),
                low = parseColorOrDefault(priorityLow, PriorityColors().low),
                medium = parseColorOrDefault(priorityMedium, PriorityColors().medium),
                high = parseColorOrDefault(priorityHigh, PriorityColors().high),
                urgent = parseColorOrDefault(priorityUrgent, PriorityColors().urgent)
            )

            val notificationSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            val notificationSnackbarScope = rememberCoroutineScope()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    notificationSnackbarScope.launch {
                        notificationSnackbarHostState.showSnackbar(
                            message = "Notifications disabled \u2014 reminders won't work. Enable in Settings.",
                            duration = androidx.compose.material3.SnackbarDuration.Long
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Exact-alarm permission. On API 33+ we declare USE_EXACT_ALARM
            // which is auto-granted for reminder-style apps, so no prompt is
            // needed. On API 31-32 the user must toggle SCHEDULE_EXACT_ALARM
            // via system Settings, otherwise reminders silently fall back to
            // inexact alarms (which Samsung/other OEMs aggressively delay).
            var showExactAlarmDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ) {
                    val am = getSystemService(AlarmManager::class.java)
                    if (am != null && !am.canScheduleExactAlarms()) {
                        showExactAlarmDialog = true
                    }
                }
            }
            if (showExactAlarmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showExactAlarmDialog = false },
                    title = { androidx.compose.material3.Text("Enable Exact Reminders") },
                    text = {
                        androidx.compose.material3.Text(
                            "PrismTask needs exact alarm permission for reliable " +
                                "reminders. Without it, notifications may be delayed " +
                                "or skipped by the system."
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showExactAlarmDialog = false
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .apply { data = Uri.parse("package:$packageName") }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Failed to open exact alarm settings", e)
                            }
                        }) {
                            androidx.compose.material3.Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showExactAlarmDialog = false
                        }) {
                            androidx.compose.material3.Text("Not Now")
                        }
                    }
                )
            }

            // Samsung (and other OEM) battery optimization prompt. Samsung
            // devices aggressively kill background alarms even when exact
            // alarms are granted, so we ask the user once to whitelist
            // PrismTask from battery optimization. The dialog is shown at
            // most once per install regardless of the user's choice.
            var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return@LaunchedEffect
                val alreadyShown = onboardingPreferences
                    .hasShownBatteryOptimizationPrompt()
                    .first()
                if (alreadyShown) return@LaunchedEffect
                val pm = getSystemService(PowerManager::class.java)
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    showBatteryOptimizationDialog = true
                }
            }
            if (showBatteryOptimizationDialog) {
                val dismissAndRecord: () -> Unit = {
                    showBatteryOptimizationDialog = false
                    notificationSnackbarScope.launch {
                        onboardingPreferences.setBatteryOptimizationPromptShown()
                    }
                }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = dismissAndRecord,
                    title = { androidx.compose.material3.Text("Improve Reminder Reliability") },
                    text = {
                        androidx.compose.material3.Text(
                            "Samsung devices may delay notifications. Tap below to " +
                                "disable battery optimization for PrismTask so " +
                                "reminders fire on time."
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                ).apply { data = Uri.parse("package:$packageName") }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.w(
                                    "MainActivity",
                                    "Failed to open battery optimization settings",
                                    e
                                )
                            }
                            dismissAndRecord()
                        }) {
                            androidx.compose.material3.Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = dismissAndRecord) {
                            androidx.compose.material3.Text("Not Now")
                        }
                    }
                )
            }

            // Once Firebase confirms we're signed in, refresh admin status
            // from the backend so the UI (and ProFeatureGate) reflects admin
            // privileges without waiting for a manual sync to run.
            val isSignedIn by authManager.isSignedIn.collectAsStateWithLifecycle()
            LaunchedEffect(isSignedIn) {
                if (isSignedIn) {
                    try {
                        backendSyncService.checkAdminStatus()
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Admin status check failed", e)
                    }
                }
            }

            // Shake-to-report: collect shake events and show confirmation dialog.
            // Respects the user's enabled toggle and sensitivity preference.
            var showShakeDialog by remember { mutableStateOf(false) }
            var pendingScreenshotUri by remember { mutableStateOf<android.net.Uri?>(null) }

            val shakeEnabled by shakePreferences
                .getEnabled()
                .collectAsStateWithLifecycle(initialValue = ShakePreferences.DEFAULT_ENABLED)
            val shakeSensitivity by shakePreferences
                .getSensitivity()
                .collectAsStateWithLifecycle(initialValue = ShakePreferences.DEFAULT_SENSITIVITY)

            LaunchedEffect(shakeSensitivity) {
                shakeDetector.threshold = when (shakeSensitivity) {
                    ShakePreferences.SENSITIVITY_LOW -> ShakeDetector.THRESHOLD_LOW_SENSITIVITY
                    ShakePreferences.SENSITIVITY_HIGH -> ShakeDetector.THRESHOLD_HIGH_SENSITIVITY
                    else -> ShakeDetector.THRESHOLD_MEDIUM_SENSITIVITY
                }
            }

            LaunchedEffect(shakeEnabled) {
                shakeFeatureEnabled = shakeEnabled
                if (shakeEnabled) {
                    shakeDetector.register()
                } else {
                    shakeDetector.unregister()
                    // Dismiss any lingering prompt if the user just disabled the feature.
                    if (showShakeDialog) {
                        showShakeDialog = false
                        pendingScreenshotUri = null
                    }
                }
            }

            LaunchedEffect(Unit) {
                shakeDetector.shakeEvents.collect {
                    if (!shakeFeatureEnabled) return@collect
                    if (!showShakeDialog) {
                        triggerHapticFeedback()
                        val uri = screenshotCapture.capture(this@MainActivity)
                        pendingScreenshotUri = uri
                        showShakeDialog = true
                    }
                }
            }

            PrismTaskTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                backgroundColorOverride = backgroundColorOverride,
                surfaceColorOverride = surfaceColorOverride,
                errorColorOverride = errorColorOverride,
                fontScale = fontScale,
                priorityColors = priorityColors,
                reduceMotion = reduceMotion,
                highContrast = highContrast,
                largeTouchTargets = largeTouchTargets,
                compactMode = appearance.compactMode,
                cardCornerRadius = appearance.cardCornerRadius,
                showCardBorders = appearance.showTaskCardBorders
            ) {
                val navController = androidx.navigation.compose.rememberNavController()

                if (showShakeDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            showShakeDialog = false
                            pendingScreenshotUri = null
                        },
                        title = { androidx.compose.material3.Text("Report a Bug?") },
                        text = {
                            androidx.compose.material3.Text(
                                "A screenshot has been captured. Would you like to file a bug report?"
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showShakeDialog = false
                                navController.navigate(PrismTaskRoute.BugReport.createRoute("ShakeReport"))
                            }) {
                                androidx.compose.material3.Text("Report")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showShakeDialog = false
                                pendingScreenshotUri = null
                            }) {
                                androidx.compose.material3.Text("Cancel")
                            }
                        }
                    )
                }

                if (hasCompletedOnboarding == null) {
                    // DataStore hasn't emitted yet — show a minimal loading state
                    // so we don't flash the wrong screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PrismTaskNavGraph(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            tabOrder = tabOrder,
                            hiddenTabs = hiddenTabs,
                            initialLaunchAction = launchAction,
                            initialSharedText = initialSharedText,
                            hasCompletedOnboarding = hasCompletedOnboarding!!
                        )

                        // Notification permission denial snackbar
                        androidx.compose.material3.SnackbarHost(
                            hostState = notificationSnackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 80.dp)
                        )

                        // Floating feedback button for beta/debug builds
                        if (BuildConfig.DEBUG) {
                            com.averycorp.prismtask.ui.components.FeedbackButton(
                                onClick = {
                                    navController.navigate(PrismTaskRoute.BugReport.createRoute("FloatingButton"))
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 140.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (shakeFeatureEnabled) {
            shakeDetector.register()
        }
        screenshotCapture.cleanupOldScreenshots(this)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.unregister()
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: SecurityException) {
            Log.w("MainActivity", "Vibrate permission denied", e)
        }
    }

    private fun setCrashlyticsUserId() {
        val user = FirebaseAuth.getInstance().currentUser
        FirebaseCrashlytics.getInstance().setUserId(user?.uid ?: "anonymous")
    }

    private fun parseColorOrDefault(hex: String, default: Color): Color {
        if (hex.isBlank()) return default
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            default
        }
    }
}
