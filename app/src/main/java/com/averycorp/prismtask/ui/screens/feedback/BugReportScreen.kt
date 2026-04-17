package com.averycorp.prismtask.ui.screens.feedback

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.model.BugCategory
import com.averycorp.prismtask.domain.model.BugSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BugReportScreen(
    navController: NavController,
    viewModel: BugReportViewModel = hiltViewModel()
) {
    val category by viewModel.category.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val severity by viewModel.severity.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val screenshotUris by viewModel.screenshotUris.collectAsStateWithLifecycle()
    val includeDiagnosticLog by viewModel.includeDiagnosticLog.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submitSuccess by viewModel.submitSuccess.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val contextExpanded by viewModel.contextExpanded.collectAsStateWithLifecycle()
    val diagnosticLogCount by viewModel.diagnosticLogCount.collectAsStateWithLifecycle()
    val isFeatureRequest by viewModel.isFeatureRequest.collectAsStateWithLifecycle()
    val importance by viewModel.importance.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addScreenshot(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(submitSuccess) {
        if (submitSuccess) {
            navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isFeatureRequest) "Request a Feature" else "Report a Bug",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (isFeatureRequest) "Tell us what you'd like to see" else "Help us improve PrismTask",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isSignedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(8.dp)
                        ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign in from Settings to submit reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: What Happened
            Text(
                "What Happened?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!isFeatureRequest) {
                // Category dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { categoryExpanded = true }) {
                        Text("Category: ${formatCategory(category)}")
                    }
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        BugCategory.entries.filter { it != BugCategory.FEATURE_REQUEST }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(formatCategory(cat)) },
                                onClick = {
                                    viewModel.setCategory(cat)
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.setDescription(it) },
                label = {
                    Text(
                        if (isFeatureRequest) {
                            "Describe the feature you'd like..."
                        } else {
                            "Describe what went wrong..."
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            if (!isFeatureRequest) {
                Spacer(modifier = Modifier.height(12.dp))

                // Severity chips
                Text(
                    "Severity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityChip("Minor (Annoying)", BugSeverity.MINOR, severity == BugSeverity.MINOR) {
                        viewModel.setSeverity(BugSeverity.MINOR)
                    }
                    SeverityChip("Major (Blocks Me)", BugSeverity.MAJOR, severity == BugSeverity.MAJOR) {
                        viewModel.setSeverity(BugSeverity.MAJOR)
                    }
                    SeverityChip("Critical (Data Loss/Crash)", BugSeverity.CRITICAL, severity == BugSeverity.CRITICAL) {
                        viewModel.setSeverity(BugSeverity.CRITICAL)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "How Important Is This?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Nice to Have", "Important", "Essential").forEach { level ->
                        FilterChip(
                            selected = importance == level,
                            onClick = { viewModel.setImportance(level) },
                            label = { Text(level) }
                        )
                    }
                }
            }

            if (!isFeatureRequest) {
                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Steps to Reproduce
                Text(
                    "Steps to Reproduce",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Optional but very helpful",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                steps.forEachIndexed { index, step ->
                    OutlinedTextField(
                        value = step,
                        onValueChange = { viewModel.updateStep(index, it) },
                        label = { Text("Step ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Screenshots
                Text(
                    "Screenshots",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (screenshotUris.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        screenshotUris.forEachIndexed { index, uri ->
                            ScreenshotThumbnail(
                                uri = uri,
                                onRemove = { viewModel.removeScreenshot(index) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (screenshotUris.size < 3) {
                    OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach Screenshot")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isFeatureRequest) {
                // Section 4: Auto-collected context
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleContextExpanded() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Auto-Collected Context",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tap to see what's being sent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (contextExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (contextExpanded) "Collapse" else "Expand"
                    )
                }

                AnimatedVisibility(visible = contextExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp)
                            ).padding(12.dp)
                    ) {
                        deviceInfo?.let { info ->
                            ContextLine("Device", "${info.manufacturer} ${info.model}")
                            ContextLine("Android", "API ${info.sdkVersion}")
                            ContextLine("App Version", "${info.appVersion} (${info.appVersionCode})")
                            ContextLine("Build", info.buildType)
                            ContextLine("Tier", info.userTier)
                            ContextLine("Tasks", "${info.taskCount}")
                            ContextLine("Habits", "${info.habitCount}")
                            ContextLine("RAM Available", "${info.availableRamMb} MB")
                            ContextLine("Storage Free", "${info.freeStorageMb} MB")
                            ContextLine("Network", info.networkType)
                            ContextLine("Battery", "${info.batteryPercent}%${if (info.isCharging) " (charging)" else ""}")
                        } ?: Text("Collecting\u2026", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Diagnostic log toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Include Diagnostic Log",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "$diagnosticLogCount events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeDiagnosticLog,
                        onCheckedChange = { viewModel.setIncludeDiagnosticLog(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.isNotBlank() && !isSubmitting && isSignedIn
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isFeatureRequest) "Send Request" else "Send Report")
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            Text(
                text = if (isFeatureRequest) {
                    "Feature requests help us prioritize what to build next."
                } else {
                    "Reports include device info to help us debug. No personal task content is included."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScreenshotThumbnail(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "Screenshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SeverityChip(label: String, value: BugSeverity, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun ContextLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatCategory(category: BugCategory): String = when (category) {
    BugCategory.CRASH -> "Crash"
    BugCategory.UI_GLITCH -> "UI Glitch"
    BugCategory.FEATURE_NOT_WORKING -> "Feature Not Working"
    BugCategory.DATA_LOSS -> "Data Loss"
    BugCategory.PERFORMANCE -> "Performance"
    BugCategory.SYNC_ISSUE -> "Sync Issue"
    BugCategory.WIDGET_ISSUE -> "Widget Issue"
    BugCategory.FEATURE_REQUEST -> "Feature Request"
    BugCategory.OTHER -> "Other"
}
