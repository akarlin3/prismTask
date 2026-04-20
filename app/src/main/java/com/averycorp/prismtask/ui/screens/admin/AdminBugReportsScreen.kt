package com.averycorp.prismtask.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.remote.api.AdminBugReportResponse

private val ALL_STATUSES = listOf("SUBMITTED", "ACKNOWLEDGED", "FIXED", "WONT_FIX")
private val ALL_SEVERITIES = listOf("MINOR", "MAJOR", "CRITICAL")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminBugReportsScreen(
    navController: NavController,
    viewModel: AdminBugReportsViewModel = hiltViewModel()
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val severityFilter by viewModel.severityFilter.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Bug Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            FilterBar(
                statusFilter = statusFilter,
                severityFilter = severityFilter,
                onStatusChange = viewModel::setStatusFilter,
                onSeverityChange = viewModel::setSeverityFilter
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${reports.size} report${if (reports.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && reports.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading reports…")
                        }
                    }
                    error != null && reports.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Could Not Load Reports",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = viewModel::refresh) { Text("Retry") }
                        }
                    }
                    reports.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No Reports",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "No reports match the current filters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                        ) {
                            items(reports, key = { it.reportId }) { report ->
                                ReportRow(
                                    report = report,
                                    onClick = { viewModel.select(report) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { report ->
        ReportDetailDialog(
            report = report,
            onDismiss = { viewModel.select(null) },
            onUpdateStatus = { newStatus, notes ->
                viewModel.updateStatus(report.reportId, newStatus, notes)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    statusFilter: String?,
    severityFilter: String?,
    onStatusChange: (String?) -> Unit,
    onSeverityChange: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { onStatusChange(null) },
                label = { Text("All") }
            )
            ALL_STATUSES.forEach { s ->
                FilterChip(
                    selected = statusFilter == s,
                    onClick = { onStatusChange(if (statusFilter == s) null else s) },
                    label = { Text(prettyStatus(s)) }
                )
            }
        }
        Text(
            "Severity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = severityFilter == null,
                onClick = { onSeverityChange(null) },
                label = { Text("All") }
            )
            ALL_SEVERITIES.forEach { s ->
                FilterChip(
                    selected = severityFilter == s,
                    onClick = { onSeverityChange(if (severityFilter == s) null else s) },
                    label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: AdminBugReportResponse,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = report.category.replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            SeverityPill(report.severity)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = report.description.take(200),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${report.deviceManufacturer} ${report.deviceModel} · " +
                    "v${report.appVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(report.status)
        }
        report.createdAt?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeverityPill(severity: String) {
    val (bg, fg) = when (severity) {
        "CRITICAL" -> Color(0xFFFFE0E0) to Color(0xFFB00020)
        "MAJOR" -> Color(0xFFFFE7CC) to Color(0xFF9A5200)
        else -> Color(0xFFE6F1FB) to Color(0xFF1F5B8F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = severity.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "FIXED" -> Color(0xFFE1F5EE) to Color(0xFF0F6B4F)
        "ACKNOWLEDGED" -> Color(0xFFEEEDFE) to Color(0xFF4B3FB0)
        "WONT_FIX" -> Color(0xFFF1EFE8) to Color(0xFF5C5449)
        else -> Color(0xFFFAEEDA) to Color(0xFF7A4E00)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = prettyStatus(status),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportDetailDialog(
    report: AdminBugReportResponse,
    onDismiss: () -> Unit,
    onUpdateStatus: (String, String?) -> Unit
) {
    var notes by remember(report.reportId) {
        mutableStateOf(report.adminNotes.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.category.replace('_', ' '),
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeverityPill(report.severity)
                    StatusPill(report.status)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(report.description, style = MaterialTheme.typography.bodyMedium)

                val parsedSteps = parseJsonStringList(report.steps)
                if (parsedSteps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader("Steps to Reproduce")
                    parsedSteps.forEachIndexed { idx, step ->
                        Text("${idx + 1}. $step", style = MaterialTheme.typography.bodySmall)
                    }
                }

                val screenshots = parseJsonStringList(report.screenshotUris)
                if (screenshots.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader("Screenshots")
                    screenshots.forEach { uri ->
                        Text(
                            uri,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Device")
                LabelRow("Make / Model", "${report.deviceManufacturer} ${report.deviceModel}")
                LabelRow("Android", report.androidVersion.toString())
                LabelRow("App", "${report.appVersion} (${report.appVersionCode}) ${report.buildType}")
                LabelRow("Tier", report.userTier.ifBlank { "—" })

                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Context")
                LabelRow("Screen", report.currentScreen.ifBlank { "—" })
                LabelRow("Tasks", report.taskCount.toString())
                LabelRow("Habits", report.habitCount.toString())
                LabelRow("RAM free", "${report.availableRamMb} MB")
                LabelRow("Storage free", "${report.freeStorageMb} MB")
                LabelRow("Network", report.networkType.ifBlank { "—" })
                LabelRow(
                    "Battery",
                    "${report.batteryPercent}%${if (report.isCharging) " (charging)" else ""}"
                )

                report.diagnosticLog?.takeIf { it.isNotBlank() }?.let { log ->
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader("Diagnostic Log")
                    Text(
                        log,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Admin Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Internal notes…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Update Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALL_STATUSES.forEach { s ->
                        AssistChip(
                            onClick = {
                                onUpdateStatus(s, notes.ifBlank { null })
                            },
                            label = { Text(prettyStatus(s)) },
                            enabled = s != report.status || notes != report.adminNotes.orEmpty()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

private fun prettyStatus(s: String): String = when (s) {
    "WONT_FIX" -> "Won't Fix"
    else -> s.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Backend stores ``steps`` and ``screenshot_uris`` as JSON-encoded string arrays.
 * Parse minimally without pulling in Gson here — values are short and safe.
 */
private fun parseJsonStringList(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return try {
        com.google.gson.Gson()
            .fromJson(raw, Array<String>::class.java)
            ?.toList()
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}
