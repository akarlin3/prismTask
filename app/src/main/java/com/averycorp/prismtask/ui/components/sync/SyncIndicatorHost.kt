package com.averycorp.prismtask.ui.components.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.ui.components.SyncStatusIndicator

/**
 * Drop-in host that renders the shared sync indicator in a screen's top-bar
 * actions slot. Handles tap → details sheet; long-press → debug panel in
 * [BuildConfig.DEBUG] builds.
 */
@Composable
fun SyncIndicatorHost(
    modifier: Modifier = Modifier,
    viewModel: SyncIndicatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val recentErrors by viewModel.recentErrors.collectAsStateWithLifecycle()

    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showDebug by rememberSaveable { mutableStateOf(false) }

    SyncStatusIndicator(
        syncState = uiState,
        onTap = { showDetails = true },
        onLongPress = {
            if (BuildConfig.DEBUG) showDebug = true else showDetails = true
        },
        modifier = modifier
    )

    if (showDetails) {
        SyncDetailsSheet(
            state = uiState,
            lastSyncAt = lastSyncAt,
            pendingCount = pendingCount,
            recentErrors = recentErrors,
            onDismiss = { showDetails = false },
            onForceSync = {
                viewModel.forceSync()
                showDetails = false
            },
            onDismissErrors = viewModel::dismissErrors
        )
    }

    if (showDebug && BuildConfig.DEBUG) {
        SyncDebugPanel(
            viewModel = viewModel,
            onDismiss = { showDebug = false }
        )
    }
}
