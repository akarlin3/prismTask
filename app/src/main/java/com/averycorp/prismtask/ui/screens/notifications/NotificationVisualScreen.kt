package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.notifications.BadgeMode
import com.averycorp.prismtask.domain.model.notifications.NotificationDisplayMode
import com.averycorp.prismtask.domain.model.notifications.ToastPosition
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun NotificationVisualScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val badgeMode by viewModel.badgeMode.collectAsStateWithLifecycle()
    val toastPos by viewModel.toastPosition.collectAsStateWithLifecycle()
    val highContrast by viewModel.highContrastEnabled.collectAsStateWithLifecycle()

    var displayMode by remember { mutableStateOf(profile.displayMode) }

    NotificationSubScreenScaffold("Visual Display", navController) {
        SubHeader("Display mode")
        NotificationDisplayMode.values().forEach { m ->
            RadioRow(
                label = m.label,
                secondary = when (m) {
                    NotificationDisplayMode.STANDARD_BANNER -> "System default heads-up"
                    NotificationDisplayMode.PERSISTENT_BANNER -> "Stays until tapped"
                    NotificationDisplayMode.FULL_SCREEN -> "Full-screen takeover, even on lock screen"
                    NotificationDisplayMode.MINIMAL_CORNER -> "Small corner toast (desktop/web)"
                },
                selected = displayMode == m,
                onSelect = { displayMode = m }
            )
        }

        SectionSpacer()
        SubHeader("Badge count")
        BadgeMode.values().forEach { b ->
            RadioRow(
                label = b.label,
                selected = badgeMode == b.key,
                onSelect = { viewModel.setBadgeMode(b.key) }
            )
        }

        SectionSpacer()
        SubHeader("Toast position (desktop / web)")
        ToastPosition.values().forEach { p ->
            RadioRow(
                label = p.label,
                selected = toastPos == p.key,
                onSelect = { viewModel.setToastPosition(p.key) }
            )
        }

        SectionSpacer()
        SubHeader("Accessibility")
        SettingsToggleRow(
            title = "High-contrast notification skin",
            subtitle = "Use a high-contrast palette for all rendered alerts",
            checked = highContrast,
            onCheckedChange = viewModel::setHighContrastEnabled
        )

        Button(
            onClick = {
                val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                viewModel.commitProfileEdit(entity.copy(displayModeKey = displayMode.key))
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}
