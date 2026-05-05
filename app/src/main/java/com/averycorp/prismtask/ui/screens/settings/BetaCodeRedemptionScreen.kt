package com.averycorp.prismtask.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaCodeRedemptionScreen(
    navController: NavController,
    viewModel: BetaCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Redeem Beta Code") },
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
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Got a beta-tester unlock code? Enter it here to unlock Pro features for the duration of the code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = code,
                onValueChange = { viewModel.onCodeChanged(it) },
                label = { Text("Beta code") },
                placeholder = { Text("EARLY-BIRD-2026") },
                singleLine = true,
                enabled = state !is BetaCodeUiState.Loading && state !is BetaCodeUiState.Success,
                isError = state is BetaCodeUiState.Error,
                modifier = Modifier.fillMaxWidth()
            )

            when (val current = state) {
                is BetaCodeUiState.Idle -> {
                    Button(
                        onClick = { viewModel.redeem() },
                        enabled = code.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Redeem")
                    }
                }

                is BetaCodeUiState.Loading -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }

                is BetaCodeUiState.Error -> {
                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.redeem() },
                        enabled = code.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again")
                    }
                }

                is BetaCodeUiState.Success -> {
                    Text(
                        text = "Pro unlocked!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val sub = current.proUntil?.let { until ->
                        // Surface the raw ISO timestamp; calendar formatting
                        // is owned by date-display utilities elsewhere and
                        // this is rarely-shown copy. Trim the time portion
                        // so the date reads cleanly in TalkBack.
                        "Active until ${until.substringBefore('T')}"
                    } ?: "Pro is unlocked on this account."
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
