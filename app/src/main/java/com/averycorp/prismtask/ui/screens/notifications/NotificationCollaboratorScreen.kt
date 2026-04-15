package com.averycorp.prismtask.ui.screens.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun NotificationCollaboratorScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val digest by viewModel.collabDigestMode.collectAsStateWithLifecycle()
    val assigned by viewModel.collabAssignedEnabled.collectAsStateWithLifecycle()
    val mentioned by viewModel.collabMentionedEnabled.collectAsStateWithLifecycle()
    val status by viewModel.collabStatusEnabled.collectAsStateWithLifecycle()
    val comment by viewModel.collabCommentEnabled.collectAsStateWithLifecycle()
    val dueSoon by viewModel.collabDueSoonEnabled.collectAsStateWithLifecycle()

    NotificationSubScreenScaffold("Collaborator updates", navController) {
        SubHeader("Delivery")
        listOf(
            NotificationPreferences.COLLAB_DIGEST_IMMEDIATE to "Immediately",
            NotificationPreferences.COLLAB_DIGEST_HOURLY to "Hourly digest",
            NotificationPreferences.COLLAB_DIGEST_DAILY to "Daily digest",
            NotificationPreferences.COLLAB_DIGEST_MUTED to "Muted"
        ).forEach { (key, label) ->
            RadioRow(
                label = label,
                selected = digest == key,
                onSelect = { viewModel.setCollabDigestMode(key) }
            )
        }

        SectionSpacer()
        SubHeader("Event types")
        SettingsToggleRow(
            title = "Assigned to me",
            subtitle = "When a task is assigned to you",
            checked = assigned,
            onCheckedChange = viewModel::setCollabAssignedEnabled
        )
        SettingsToggleRow(
            title = "@mentions",
            subtitle = "When someone mentions you in a comment or task",
            checked = mentioned,
            onCheckedChange = viewModel::setCollabMentionedEnabled
        )
        SettingsToggleRow(
            title = "Status changes",
            subtitle = "When a shared task changes status",
            checked = status,
            onCheckedChange = viewModel::setCollabStatusEnabled
        )
        SettingsToggleRow(
            title = "Comments added",
            subtitle = "New comments on shared tasks",
            checked = comment,
            onCheckedChange = viewModel::setCollabCommentEnabled
        )
        SettingsToggleRow(
            title = "Due-soon on shared tasks",
            subtitle = "Due-date reminders on tasks you share",
            checked = dueSoon,
            onCheckedChange = viewModel::setCollabDueSoonEnabled
        )
    }
}
