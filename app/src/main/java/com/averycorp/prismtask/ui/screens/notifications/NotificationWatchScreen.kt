package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.domain.model.notifications.VibrationIntensity

@Composable
fun NotificationWatchScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val mode by viewModel.watchSyncMode.collectAsStateWithLifecycle()
    val volume by viewModel.watchVolumePercent.collectAsStateWithLifecycle()
    val intensity by viewModel.watchHapticIntensity.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Smartwatch", navController) {
        Text(
            "Controls alerts routed to Apple Watch or Wear OS. Settings below are used on both platforms, with the companion watch app enforcing them on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionSpacer()
        SubHeader("Sync strategy")
        listOf(
            NotificationPreferences.WATCH_SYNC_MIRROR to ("Mirror phone" to "Watch inherits phone settings"),
            NotificationPreferences.WATCH_SYNC_WATCH_ONLY to ("Watch only" to "Suppress phone alerts when the watch is reachable"),
            NotificationPreferences.WATCH_SYNC_DIFFERENTIATED to ("Separate watch settings" to "Edit watch sound + haptics independently"),
            NotificationPreferences.WATCH_SYNC_DISABLED to ("Disabled" to "Never route alerts to the watch")
        ).forEach { (key, pair) ->
            RadioRow(
                label = pair.first,
                secondary = pair.second,
                selected = mode == key,
                onSelect = { viewModel.setWatchSyncMode(key) }
            )
        }

        if (mode == NotificationPreferences.WATCH_SYNC_DIFFERENTIATED) {
            SectionSpacer()
            SubHeader("Watch volume")
            LabeledSlider(
                label = "Watch volume",
                value = volume.toFloat(),
                valueRange = 0f..100f,
                format = { "${it.toInt()}%" },
                onChange = { viewModel.setWatchVolumePercent(it.toInt()) }
            )

            SubHeader("Watch haptic intensity")
            VibrationIntensity.values().forEach { i ->
                RadioRow(
                    label = i.label,
                    selected = intensity == i.key,
                    onSelect = { viewModel.setWatchHapticIntensity(i.key) }
                )
            }
        }
    }
}
