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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.settings.sections.AccessibilitySection
import com.averycorp.prismtask.ui.screens.settings.sections.ShakeSection
import com.averycorp.prismtask.ui.screens.settings.sections.VoiceInputSection
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val reduceMotionEnabled by viewModel.reduceMotionEnabled.collectAsStateWithLifecycle()
    val highContrastEnabled by viewModel.highContrastEnabled.collectAsStateWithLifecycle()
    val largeTouchTargetsEnabled by viewModel.largeTouchTargetsEnabled.collectAsStateWithLifecycle()

    val voiceInputEnabled by viewModel.voiceInputEnabled.collectAsStateWithLifecycle()
    val voiceFeedbackEnabled by viewModel.voiceFeedbackEnabled.collectAsStateWithLifecycle()
    val continuousModeEnabled by viewModel.continuousModeEnabled.collectAsStateWithLifecycle()

    val shakeEnabled by viewModel.shakeEnabled.collectAsStateWithLifecycle()
    val shakeSensitivity by viewModel.shakeSensitivity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Accessibility") },
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
            AccessibilitySection(
                reduceMotionEnabled = reduceMotionEnabled,
                highContrastEnabled = highContrastEnabled,
                largeTouchTargetsEnabled = largeTouchTargetsEnabled,
                onReduceMotionChange = viewModel::setReduceMotion,
                onHighContrastChange = viewModel::setHighContrast,
                onLargeTouchTargetsChange = viewModel::setLargeTouchTargets
            )

            VoiceInputSection(
                voiceInputEnabled = voiceInputEnabled,
                voiceFeedbackEnabled = voiceFeedbackEnabled,
                continuousModeEnabled = continuousModeEnabled,
                onVoiceInputEnabledChange = viewModel::setVoiceInputEnabled,
                onVoiceFeedbackEnabledChange = viewModel::setVoiceFeedbackEnabled,
                onContinuousModeEnabledChange = viewModel::setContinuousModeEnabled
            )

            ShakeSection(
                shakeEnabled = shakeEnabled,
                shakeSensitivity = shakeSensitivity,
                onShakeEnabledChange = viewModel::setShakeEnabled,
                onShakeSensitivityChange = viewModel::setShakeSensitivity
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
