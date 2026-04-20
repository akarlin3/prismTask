package com.averycorp.prismtask.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.settings.sections.GoogleCalendarSection
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
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

    val calendarConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleCalendarConsentResult(result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.calendarConsentIntent.collect { intent ->
            calendarConsentLauncher.launch(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Calendar") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
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
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
