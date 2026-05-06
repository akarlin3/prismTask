package com.averycorp.prismtask.ui.screens.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.usecase.RefillUrgency
import com.averycorp.prismtask.ui.theme.LocalPrismColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationRefillScreen(
    navController: NavController,
    viewModel: MedicationRefillViewModel = hiltViewModel()
) {
    val meds by viewModel.medications.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface refill-flow errors (repository throws, future backend
    // exception sources) via a Snackbar. Mirrors the collector wired
    // into MedicationScreen by PR #1141 — see
    // `docs/audits/F5_MEDICATION_HYGIENE_FOLLOWONS_AUDIT.md` § F.5b.
    LaunchedEffect(Unit) {
        viewModel.errorMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication Refills", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add medication")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (meds.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No Medications Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "Tap + to add a medication and start tracking refills.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(meds, key = { it.row.id }) { item ->
                    MedicationCard(
                        item = item,
                        onDose = { viewModel.recordDailyDose(item.row) },
                        onRefill = { newSupply -> viewModel.recordRefill(item.row, newSupply) },
                        onDelete = { viewModel.disableRefillTracking(item.row.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMedicationDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, pillCount, pillsPerDose, dosesPerDay, pharmacy, phone ->
                viewModel.addMedication(name, pillCount, pillsPerDose, dosesPerDay, pharmacy, phone)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MedicationCard(
    item: MedicationWithForecast,
    onDose: () -> Unit,
    onRefill: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var showRefillDialog by remember { mutableStateOf(false) }
    val c = LocalPrismColors.current
    val urgencyColor = when (item.forecast.urgency) {
        RefillUrgency.HEALTHY -> c.successColor
        RefillUrgency.UPCOMING -> c.warningColor
        RefillUrgency.URGENT -> c.urgentAccent
        RefillUrgency.OUT_OF_STOCK -> c.destructiveColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(urgencyColor)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = item.row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) {
                    Text("Stop Tracking", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
            val pillCount = item.row.pillCount ?: 0
            val pillSuffix = if (pillCount == 1) "" else "s"
            val daySuffix = if (item.forecast.daysRemaining == 1) "" else "s"
            Text(
                text = "$pillCount pill$pillSuffix · " +
                    "${item.forecast.daysRemaining} day$daySuffix remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.row.pharmacyName != null) {
                Text(
                    text = "Pharmacy: ${item.row.pharmacyName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDose) { Text("Record Dose") }
                Button(onClick = { showRefillDialog = true }) { Text("Refilled") }
            }
        }
    }

    if (showRefillDialog) {
        var supplyText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRefillDialog = false },
            title = { Text("Refill ${item.row.name}") },
            text = {
                OutlinedTextField(
                    value = supplyText,
                    onValueChange = { supplyText = it },
                    label = { Text("New Supply (Pills)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    supplyText.toIntOrNull()?.let {
                        onRefill(it)
                        showRefillDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRefillDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        pillCount: Int,
        pillsPerDose: Int,
        dosesPerDay: Int,
        pharmacy: String?,
        phone: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pillCount by remember { mutableStateOf("30") }
    var pillsPerDose by remember { mutableStateOf("1") }
    var dosesPerDay by remember { mutableStateOf("1") }
    var pharmacy by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = pillCount,
                    onValueChange = { pillCount = it },
                    label = { Text("Pill Count") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = pillsPerDose,
                    onValueChange = { pillsPerDose = it },
                    label = { Text("Pills Per Dose") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = dosesPerDay,
                    onValueChange = { dosesPerDay = it },
                    label = { Text("Doses Per Day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = pharmacy,
                    onValueChange = { pharmacy = it },
                    label = { Text("Pharmacy (Optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Pharmacy Phone (Optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pc = pillCount.toIntOrNull() ?: return@TextButton
                val ppd = pillsPerDose.toIntOrNull() ?: 1
                val dpd = dosesPerDay.toIntOrNull() ?: 1
                if (name.isNotBlank()) {
                    onConfirm(name, pc, ppd, dpd, pharmacy, phone)
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
