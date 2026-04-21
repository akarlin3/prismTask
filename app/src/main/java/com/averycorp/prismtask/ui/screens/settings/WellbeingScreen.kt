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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.settings.sections.AddBoundaryRuleSheet
import com.averycorp.prismtask.ui.screens.settings.sections.BoundariesSection
import com.averycorp.prismtask.ui.screens.settings.sections.ClinicalReportSection
import com.averycorp.prismtask.ui.screens.settings.sections.WorkLifeBalanceSection
import com.averycorp.prismtask.ui.theme.ThemedSubScreenTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val workLifeBalancePrefs by viewModel.workLifeBalancePrefs.collectAsStateWithLifecycle()
    val boundaryRules by viewModel.boundaryRules.collectAsStateWithLifecycle()
    val isExportingClinicalReport by viewModel.isExportingClinicalReport.collectAsStateWithLifecycle()

    var showBoundarySheet by remember { mutableStateOf(false) }
    var editingBoundaryRule by remember {
        mutableStateOf<com.averycorp.prismtask.domain.model.BoundaryRule?>(null)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    if (showBoundarySheet) {
        val editing = editingBoundaryRule
        AddBoundaryRuleSheet(
            existingRule = editing,
            onDismiss = {
                showBoundarySheet = false
                editingBoundaryRule = null
            },
            onSave = { rule ->
                val isUpdate = editing != null
                if (isUpdate) {
                    viewModel.updateBoundaryRule(rule)
                } else {
                    viewModel.insertBoundaryRule(rule)
                }
                showBoundarySheet = false
                editingBoundaryRule = null
                val verb = if (isUpdate) "updated" else "added"
                snackbarScope.launch {
                    snackbarHostState.showSnackbar("Rule $verb: ${rule.name}")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { ThemedSubScreenTitle("Wellbeing") },
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
            WorkLifeBalanceSection(
                prefs = workLifeBalancePrefs,
                onPrefsChange = viewModel::setWorkLifeBalancePrefs,
                onViewReport = {
                    navController.navigate(PrismTaskRoute.WeeklyBalanceReport.route)
                }
            )

            BoundariesSection(
                rules = boundaryRules,
                onToggle = { rule, enabled -> viewModel.toggleBoundaryRule(rule, enabled) },
                onDelete = viewModel::deleteBoundaryRule,
                onAdd = {
                    editingBoundaryRule = null
                    showBoundarySheet = true
                },
                onEdit = { rule ->
                    editingBoundaryRule = rule
                    showBoundarySheet = true
                }
            )

            ClinicalReportSection(
                isExporting = isExportingClinicalReport,
                onExportReport = { viewModel.exportClinicalReport() }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
