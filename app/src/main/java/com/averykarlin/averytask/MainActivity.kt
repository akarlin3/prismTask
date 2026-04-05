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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.notifications.NotificationHelper
import com.averykarlin.averytask.ui.navigation.AveryTaskNavGraph
import com.averykarlin.averytask.ui.theme.AveryTaskTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)
        setContent {
            val themeMode by themePreferences.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = "system")
            val accentColor by themePreferences.getAccentColor()
                .collectAsStateWithLifecycle(initialValue = "#2563EB")

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

            AveryTaskTheme(themeMode = themeMode, accentColor = accentColor) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AveryTaskNavGraph(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
