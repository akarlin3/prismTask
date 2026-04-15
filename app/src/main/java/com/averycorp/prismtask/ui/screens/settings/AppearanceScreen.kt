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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.settings.sections.AppearanceSection
import com.averycorp.prismtask.ui.screens.settings.sections.DisplaySection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val backgroundColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
    val surfaceColor by viewModel.surfaceColor.collectAsStateWithLifecycle()
    val errorColor by viewModel.errorColor.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val priorityColorNone by viewModel.priorityColorNone.collectAsStateWithLifecycle()
    val priorityColorLow by viewModel.priorityColorLow.collectAsStateWithLifecycle()
    val priorityColorMedium by viewModel.priorityColorMedium.collectAsStateWithLifecycle()
    val priorityColorHigh by viewModel.priorityColorHigh.collectAsStateWithLifecycle()
    val priorityColorUrgent by viewModel.priorityColorUrgent.collectAsStateWithLifecycle()
    val recentCustomColors by viewModel.recentCustomColors.collectAsStateWithLifecycle()
    val appearancePrefs by viewModel.appearancePrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
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
            AppearanceSection(
                themeMode = themeMode,
                accentColor = accentColor,
                recentCustomColors = recentCustomColors,
                backgroundColor = backgroundColor,
                surfaceColor = surfaceColor,
                errorColor = errorColor,
                priorityColorNone = priorityColorNone,
                priorityColorLow = priorityColorLow,
                priorityColorMedium = priorityColorMedium,
                priorityColorHigh = priorityColorHigh,
                priorityColorUrgent = priorityColorUrgent,
                fontScale = fontScale,
                onThemeModeChange = viewModel::setThemeMode,
                onAccentColorChange = viewModel::setAccentColor,
                onCustomAccentColorChange = viewModel::setCustomAccentColor,
                onFontScaleChange = viewModel::setFontScale,
                onBackgroundColorChange = viewModel::setBackgroundColor,
                onSurfaceColorChange = viewModel::setSurfaceColor,
                onErrorColorChange = viewModel::setErrorColor,
                onPriorityColorChange = viewModel::setPriorityColor,
                onResetColorOverrides = viewModel::resetColorOverrides
            )

            DisplaySection(
                appearancePrefs = appearancePrefs,
                onCompactModeChange = viewModel::setCompactMode,
                onShowCardBordersChange = viewModel::setShowCardBorders,
                onCardCornerRadiusChange = viewModel::setCardCornerRadius
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
