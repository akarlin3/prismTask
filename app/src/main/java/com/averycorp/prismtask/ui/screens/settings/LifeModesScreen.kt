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
import com.averycorp.prismtask.ui.screens.settings.sections.ModesSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeModesScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selfCareEnabled by viewModel.selfCareEnabled.collectAsStateWithLifecycle()
    val medicationEnabled by viewModel.medicationEnabled.collectAsStateWithLifecycle()
    val schoolEnabled by viewModel.schoolEnabled.collectAsStateWithLifecycle()
    val leisureEnabled by viewModel.leisureEnabled.collectAsStateWithLifecycle()
    val houseworkEnabled by viewModel.houseworkEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Life Modes") },
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
            ModesSection(
                selfCareEnabled = selfCareEnabled,
                medicationEnabled = medicationEnabled,
                houseworkEnabled = houseworkEnabled,
                schoolEnabled = schoolEnabled,
                leisureEnabled = leisureEnabled,
                onSelfCareChange = viewModel::setSelfCareEnabled,
                onMedicationChange = viewModel::setMedicationEnabled,
                onHouseworkChange = viewModel::setHouseworkEnabled,
                onSchoolChange = viewModel::setSchoolEnabled,
                onLeisureChange = viewModel::setLeisureEnabled
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
