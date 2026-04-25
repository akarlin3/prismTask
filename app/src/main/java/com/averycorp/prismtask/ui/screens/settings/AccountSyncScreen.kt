package com.averycorp.prismtask.ui.screens.settings

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.settings.sections.AccountSyncSection
import com.averycorp.prismtask.ui.screens.settings.sections.DeleteAccountSection
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isDeletingAccount by viewModel.isDeletingAccount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // After a successful account-deletion request the user is signed out and
    // their local data is wiped. Pop back to the previous screen — the parent
    // navigator detects the signed-out state and routes to AuthScreen.
    LaunchedEffect(Unit) {
        viewModel.accountDeletionCompleted.collect {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Account & Sync") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            AccountSyncSection(
                isSignedIn = isSignedIn,
                userEmail = viewModel.userEmail,
                isSyncing = isSyncing,
                onSync = viewModel::onSync,
                onSignOut = viewModel::onSignOut,
                onSignIn = { navController.navigate("auth") }
            )
            Spacer(modifier = Modifier.height(32.dp))
            DeleteAccountSection(
                isSignedIn = isSignedIn,
                isDeleting = isDeletingAccount,
                onRequestDeletion = viewModel::onRequestAccountDeletion
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
