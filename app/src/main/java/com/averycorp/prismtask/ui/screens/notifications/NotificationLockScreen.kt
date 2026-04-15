package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.notifications.LockScreenVisibility

@Composable
fun NotificationLockScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var visibility by remember { mutableStateOf(profile.lockScreenVisibility) }

    NotificationSubScreenScaffold("Lock screen", navController) {
        SubHeader("What appears on the lock screen")
        LockScreenVisibility.values().forEach { v ->
            RadioRow(
                label = v.label,
                secondary = when (v) {
                    LockScreenVisibility.SHOW_ALL -> "Show the task title, description, and actions"
                    LockScreenVisibility.APP_NAME_ONLY -> "Show \u201cPrismTask\u201d only \u2014 content revealed after unlock"
                    LockScreenVisibility.HIDDEN -> "Do not display on the lock screen at all"
                },
                selected = visibility == v,
                onSelect = { visibility = v }
            )
        }

        Button(
            onClick = {
                val entity = profiles.firstOrNull { it.id == profile.id } ?: return@Button
                viewModel.commitProfileEdit(entity.copy(lockScreenVisibilityKey = visibility.key))
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}
