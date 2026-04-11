package com.averycorp.prismtask

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.TabPreferences
import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.UpdateChecker
import com.averycorp.prismtask.data.remote.VersionInfo
import com.averycorp.prismtask.notifications.NotificationHelper
import com.averycorp.prismtask.ui.components.UpdateDialog
import com.averycorp.prismtask.ui.navigation.PrismTaskNavGraph
import com.averycorp.prismtask.ui.theme.PrismTaskTheme
import com.averycorp.prismtask.ui.theme.PriorityColors
import dagger.hilt.android.AndroidEntryPoint
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
        NotificationHelper.createNotificationChannel(this)
        syncService.startAutoSync()
        billingManager.initialize(this)
        val launchAction = intent?.getStringExtra(EXTRA_LAUNCH_ACTION)
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

            val themeMode by themePreferences.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = "system")
            val accentColor by themePreferences.getAccentColor()
                .collectAsStateWithLifecycle(initialValue = "#2563EB")
            val backgroundColorOverride by themePreferences.getBackgroundColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val surfaceColorOverride by themePreferences.getSurfaceColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val errorColorOverride by themePreferences.getErrorColor()
                .collectAsStateWithLifecycle(initialValue = "")
            val fontScale by themePreferences.getFontScale()
                .collectAsStateWithLifecycle(initialValue = 1.0f)

            val priorityNone by themePreferences.getPriorityColorNone()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityLow by themePreferences.getPriorityColorLow()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityMedium by themePreferences.getPriorityColorMedium()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityHigh by themePreferences.getPriorityColorHigh()
                .collectAsStateWithLifecycle(initialValue = "")
            val priorityUrgent by themePreferences.getPriorityColorUrgent()
                .collectAsStateWithLifecycle(initialValue = "")

            val hasCompletedOnboarding by onboardingPreferences.hasCompletedOnboarding()
                .collectAsStateWithLifecycle(initialValue = true)

            val reduceMotion by a11yPreferences.getReduceMotion()
                .collectAsStateWithLifecycle(initialValue = false)
            val highContrast by a11yPreferences.getHighContrast()
                .collectAsStateWithLifecycle(initialValue = false)
            val largeTouchTargets by a11yPreferences.getLargeTouchTargets()
                .collectAsStateWithLifecycle(initialValue = false)

            val tabOrder by tabPreferences.getTabOrder()
                .collectAsStateWithLifecycle(initialValue = TabPreferences.DEFAULT_ORDER)
            val hiddenTabs by tabPreferences.getHiddenTabs()
                .collectAsStateWithLifecycle(initialValue = emptySet())

            val priorityColors = PriorityColors(
                none = parseColorOrDefault(priorityNone, PriorityColors().none),
                low = parseColorOrDefault(priorityLow, PriorityColors().low),
                medium = parseColorOrDefault(priorityMedium, PriorityColors().medium),
                high = parseColorOrDefault(priorityHigh, PriorityColors().high),
                urgent = parseColorOrDefault(priorityUrgent, PriorityColors().urgent)
            )

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }

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
                largeTouchTargets = largeTouchTargets
            ) {
                PrismTaskNavGraph(
                    modifier = Modifier.fillMaxSize(),
                    tabOrder = tabOrder,
                    hiddenTabs = hiddenTabs,
                    initialLaunchAction = launchAction,
                    hasCompletedOnboarding = hasCompletedOnboarding
                )
            }
        }
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
