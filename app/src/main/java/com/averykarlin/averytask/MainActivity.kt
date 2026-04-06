package com.averykarlin.averytask

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averykarlin.averytask.data.preferences.TabPreferences
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.remote.SyncService
import com.averykarlin.averytask.data.remote.UpdateChecker
import com.averykarlin.averytask.data.remote.VersionInfo
import com.averykarlin.averytask.notifications.NotificationHelper
import com.averykarlin.averytask.ui.components.UpdateDialog
import com.averykarlin.averytask.ui.navigation.AveryTaskNavGraph
import com.averykarlin.averytask.ui.theme.AveryTaskTheme
import com.averykarlin.averytask.ui.theme.PriorityColors
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)
        syncService.startAutoSync()
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

            AveryTaskTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                backgroundColorOverride = backgroundColorOverride,
                surfaceColorOverride = surfaceColorOverride,
                errorColorOverride = errorColorOverride,
                fontScale = fontScale,
                priorityColors = priorityColors
            ) {
                AveryTaskNavGraph(
                        modifier = Modifier.fillMaxSize(),
                        tabOrder = tabOrder,
                        hiddenTabs = hiddenTabs
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
