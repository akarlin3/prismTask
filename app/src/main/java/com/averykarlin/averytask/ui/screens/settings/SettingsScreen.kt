package com.averykarlin.averytask.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averykarlin.averytask.data.preferences.DashboardPreferences
import com.averykarlin.averytask.data.preferences.TabPreferences
import com.averykarlin.averytask.data.preferences.UrgencyWeights
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import com.averykarlin.averytask.ui.navigation.ALL_BOTTOM_NAV_ITEMS
import com.averykarlin.averytask.ui.theme.PriorityColors

private val accentColors = listOf(
    "#2563EB", "#7C3AED", "#DB2777", "#DC2626", "#EA580C", "#D97706",
    "#65A30D", "#059669", "#0891B2", "#6366F1", "#8B5CF6", "#EC4899"
)

private val sectionLabels = mapOf(
    "progress" to "Progress Card",
    "habits" to "Habits",
    "overdue" to "Overdue",
    "today_tasks" to "Today Tasks",
    "plan_more" to "Plan More",
    "completed" to "Completed"
)

private val sortLabels = mapOf(
    "DUE_DATE" to "Due Date",
    "PRIORITY" to "Priority",
    "URGENCY" to "Urgency",
    "CREATED" to "Date Created",
    "ALPHABETICAL" to "Alphabetical"
)

private val viewModeLabels = mapOf(
    "UPCOMING" to "Upcoming",
    "LIST" to "List",
    "WEEK" to "Week",
    "MONTH" to "Month"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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
    val autoArchiveDays by viewModel.autoArchiveDays.collectAsStateWithLifecycle()
    val claudeApiKey by viewModel.claudeApiKey.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val isResetting by viewModel.isResetting.collectAsStateWithLifecycle()
    val pendingJson by viewModel.pendingJsonExport.collectAsStateWithLifecycle()
    val pendingCsv by viewModel.pendingCsvExport.collectAsStateWithLifecycle()
    val sectionOrder by viewModel.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val progressStyle by viewModel.progressStyle.collectAsStateWithLifecycle()
    val tabOrder by viewModel.tabOrder.collectAsStateWithLifecycle()
    val hiddenTabs by viewModel.hiddenTabs.collectAsStateWithLifecycle()
    val defaultSort by viewModel.defaultSort.collectAsStateWithLifecycle()
    val defaultViewMode by viewModel.defaultViewMode.collectAsStateWithLifecycle()
    val urgencyWeights by viewModel.urgencyWeights.collectAsStateWithLifecycle()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    val dayStartHour by viewModel.dayStartHour.collectAsStateWithLifecycle()
    val calendarSyncEnabled by viewModel.calendarSyncEnabled.collectAsStateWithLifecycle()
    val calendarName by viewModel.calendarName.collectAsStateWithLifecycle()
    val availableCalendars by viewModel.availableCalendars.collectAsStateWithLifecycle()

    var showAutoArchiveDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showAppearanceAdvanced by remember { mutableStateOf(false) }
    var showDashboardAdvanced by remember { mutableStateOf(false) }
    var showTaskAdvanced by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val data = pendingJson ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data.toByteArray())
            }
            viewModel.clearPendingExports()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val data = pendingCsv ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data.toByteArray())
            }
            viewModel.clearPendingExports()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
            if (jsonString != null) {
                viewModel.onImportJson(jsonString)
            }
        }
    }

    LaunchedEffect(pendingJson) {
        if (pendingJson != null) createJsonLauncher.launch("averytask_backup.json")
    }

    LaunchedEffect(pendingCsv) {
        if (pendingCsv != null) createCsvLauncher.launch("averytask_tasks.csv")
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset App") },
            text = {
                Text("This will permanently delete all tasks, projects, tags, habits, and settings, and sign you out. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetApp()
                    }
                ) {
                    Text("Reset Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAutoArchiveDialog) {
        val options = listOf(3 to "3 days", 7 to "7 days", 14 to "14 days", 30 to "30 days", 0 to "Never")
        AlertDialog(
            onDismissRequest = { showAutoArchiveDialog = false },
            confirmButton = {
                TextButton(onClick = { showAutoArchiveDialog = false }) { Text("Close") }
            },
            title = { Text("Auto-archive completed tasks") },
            text = {
                Column {
                    options.forEach { (days, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoArchiveDays(days)
                                    showAutoArchiveDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = autoArchiveDays == days,
                                onClick = {
                                    viewModel.setAutoArchiveDays(days)
                                    showAutoArchiveDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    // Color picker dialog
    showColorPicker?.let { (title, currentHex) ->
        ColorPickerDialog(
            title = title,
            currentHex = currentHex,
            onSelect = { hex ->
                when (title) {
                    "Background" -> viewModel.setBackgroundColor(hex)
                    "Surface" -> viewModel.setSurfaceColor(hex)
                    "Error" -> viewModel.setErrorColor(hex)
                    "None Priority" -> viewModel.setPriorityColor(0, hex)
                    "Low Priority" -> viewModel.setPriorityColor(1, hex)
                    "Medium Priority" -> viewModel.setPriorityColor(2, hex)
                    "High Priority" -> viewModel.setPriorityColor(3, hex)
                    "Urgent Priority" -> viewModel.setPriorityColor(4, hex)
                }
                showColorPicker = null
            },
            onClear = {
                when (title) {
                    "Background" -> viewModel.setBackgroundColor("")
                    "Surface" -> viewModel.setSurfaceColor("")
                    "Error" -> viewModel.setErrorColor("")
                    "None Priority" -> viewModel.setPriorityColor(0, "")
                    "Low Priority" -> viewModel.setPriorityColor(1, "")
                    "Medium Priority" -> viewModel.setPriorityColor(2, "")
                    "High Priority" -> viewModel.setPriorityColor(3, "")
                    "Urgent Priority" -> viewModel.setPriorityColor(4, "")
                }
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        ) {
            AnimatedVisibility(visible = isSyncing || isImporting || isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
            // ========== APPEARANCE ==========
            SectionHeader("Appearance")

            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
                    FilterChip(
                        selected = themeMode == value,
                        onClick = { viewModel.setThemeMode(value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Accent Color",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                accentColors.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = accentColor.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.setAccentColor(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Font Scale
            Text(
                text = "Font Size: ${String.format("%.0f%%", fontScale * 100)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = fontScale,
                onValueChange = { viewModel.setFontScale(it) },
                valueRange = 0.8f..1.4f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // Advanced appearance
            AdvancedToggle(expanded = showAppearanceAdvanced, onToggle = { showAppearanceAdvanced = !showAppearanceAdvanced })
            AnimatedVisibility(visible = showAppearanceAdvanced) {
                Column {
                    Text(
                        text = "Color Overrides",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ColorOverrideRow("Background", backgroundColor) { showColorPicker = "Background" to backgroundColor }
                    ColorOverrideRow("Surface", surfaceColor) { showColorPicker = "Surface" to surfaceColor }
                    ColorOverrideRow("Error", errorColor) { showColorPicker = "Error" to errorColor }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Priority Colors",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val defaults = PriorityColors()
                    PriorityColorRow("None", priorityColorNone, defaults.none) { showColorPicker = "None Priority" to priorityColorNone }
                    PriorityColorRow("Low", priorityColorLow, defaults.low) { showColorPicker = "Low Priority" to priorityColorLow }
                    PriorityColorRow("Medium", priorityColorMedium, defaults.medium) { showColorPicker = "Medium Priority" to priorityColorMedium }
                    PriorityColorRow("High", priorityColorHigh, defaults.high) { showColorPicker = "High Priority" to priorityColorHigh }
                    PriorityColorRow("Urgent", priorityColorUrgent, defaults.urgent) { showColorPicker = "Urgent Priority" to priorityColorUrgent }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.resetColorOverrides() }) {
                        Text("Reset all color overrides", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ========== DASHBOARD ==========
            SectionHeader("Dashboard")

            Text(
                text = "Progress Style",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ring" to "Ring", "bar" to "Bar", "percentage" to "Percentage").forEach { (value, label) ->
                    FilterChip(
                        selected = progressStyle == value,
                        onClick = { viewModel.setProgressStyle(value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Visible Sections",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            DashboardPreferences.DEFAULT_ORDER.forEach { key ->
                val label = sectionLabels[key] ?: key
                val isHidden = key in hiddenSections
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newHidden = if (isHidden) hiddenSections - key else hiddenSections + key
                            viewModel.setHiddenSections(newHidden)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = !isHidden, onCheckedChange = {
                        val newHidden = if (isHidden) hiddenSections - key else hiddenSections + key
                        viewModel.setHiddenSections(newHidden)
                    })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            AdvancedToggle(expanded = showDashboardAdvanced, onToggle = { showDashboardAdvanced = !showDashboardAdvanced })
            AnimatedVisibility(visible = showDashboardAdvanced) {
                Column {
                    Text(
                        text = "Section Order",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    sectionOrder.forEachIndexed { index, key ->
                        ReorderableRow(
                            label = sectionLabels[key] ?: key,
                            canMoveUp = index > 0,
                            canMoveDown = index < sectionOrder.size - 1,
                            onMoveUp = {
                                val mutable = sectionOrder.toMutableList()
                                mutable[index] = mutable[index - 1].also { mutable[index - 1] = mutable[index] }
                                viewModel.setSectionOrder(mutable)
                            },
                            onMoveDown = {
                                val mutable = sectionOrder.toMutableList()
                                mutable[index] = mutable[index + 1].also { mutable[index + 1] = mutable[index] }
                                viewModel.setSectionOrder(mutable)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.resetDashboardDefaults() }) {
                        Text("Reset dashboard", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()

            // ========== MODES ==========
            SectionHeader("Modes")

            val selfCareEnabled by viewModel.selfCareEnabled.collectAsStateWithLifecycle()
            val medicationEnabled by viewModel.medicationEnabled.collectAsStateWithLifecycle()
            val schoolEnabled by viewModel.schoolEnabled.collectAsStateWithLifecycle()
            val leisureEnabled by viewModel.leisureEnabled.collectAsStateWithLifecycle()

            ModeToggleRow("Self Care", selfCareEnabled) { viewModel.setSelfCareEnabled(it) }
            ModeToggleRow("Medication", medicationEnabled) { viewModel.setMedicationEnabled(it) }
            ModeToggleRow("Schoolwork", schoolEnabled) { viewModel.setSchoolEnabled(it) }
            ModeToggleRow("Leisure", leisureEnabled) { viewModel.setLeisureEnabled(it) }

            HorizontalDivider()

            // ========== NAVIGATION ==========
            SectionHeader("Navigation")

            Text(
                text = "Visible Tabs",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            val visibleTabCount = ALL_BOTTOM_NAV_ITEMS.count { it.route !in hiddenTabs }
            ALL_BOTTOM_NAV_ITEMS.forEach { item ->
                val isHidden = item.route in hiddenTabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isHidden && visibleTabCount <= 2) return@clickable
                            val newHidden = if (isHidden) hiddenTabs - item.route else hiddenTabs + item.route
                            viewModel.setHiddenTabs(newHidden)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = !isHidden,
                        onCheckedChange = {
                            if (!isHidden && visibleTabCount <= 2) return@Checkbox
                            val newHidden = if (isHidden) hiddenTabs - item.route else hiddenTabs + item.route
                            viewModel.setHiddenTabs(newHidden)
                        }
                    )
                    Text(item.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tab Order",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            tabOrder.forEachIndexed { index, route ->
                val item = ALL_BOTTOM_NAV_ITEMS.find { it.route == route }
                ReorderableRow(
                    label = item?.label ?: route,
                    canMoveUp = index > 0,
                    canMoveDown = index < tabOrder.size - 1,
                    onMoveUp = {
                        val mutable = tabOrder.toMutableList()
                        mutable[index] = mutable[index - 1].also { mutable[index - 1] = mutable[index] }
                        viewModel.setTabOrder(mutable)
                    },
                    onMoveDown = {
                        val mutable = tabOrder.toMutableList()
                        mutable[index] = mutable[index + 1].also { mutable[index + 1] = mutable[index] }
                        viewModel.setTabOrder(mutable)
                    }
                )
            }
            TextButton(onClick = { viewModel.resetTabDefaults() }) {
                Text("Reset navigation", color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()

            // ========== TASK DEFAULTS ==========
            SectionHeader("Task Defaults")

            SettingsRowWithSubtitle(
                title = "Default Sort",
                subtitle = sortLabels[defaultSort] ?: defaultSort,
                onClick = {
                    // cycle through sort options
                    val keys = sortLabels.keys.toList()
                    val next = keys[(keys.indexOf(defaultSort) + 1) % keys.size]
                    viewModel.setDefaultSort(next)
                }
            )
            SettingsRowWithSubtitle(
                title = "Default View",
                subtitle = viewModeLabels[defaultViewMode] ?: defaultViewMode,
                onClick = {
                    val keys = viewModeLabels.keys.toList()
                    val next = keys[(keys.indexOf(defaultViewMode) + 1) % keys.size]
                    viewModel.setDefaultViewMode(next)
                }
            )
            SettingsRowWithSubtitle(
                title = "First Day of Week",
                subtitle = firstDayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                onClick = {
                    val days = DayOfWeek.entries
                    val next = days[(firstDayOfWeek.ordinal + 1) % days.size]
                    viewModel.setFirstDayOfWeek(next)
                }
            )
            SettingsRowWithSubtitle(
                title = "Day Start Hour",
                subtitle = if (dayStartHour == 0) "Midnight" else String.format("%d:00 %s", if (dayStartHour > 12) dayStartHour - 12 else if (dayStartHour == 0) 12 else dayStartHour, if (dayStartHour < 12) "AM" else "PM"),
                onClick = {
                    val next = (dayStartHour + 1) % 24
                    viewModel.setDayStartHour(next)
                }
            )

            AdvancedToggle(expanded = showTaskAdvanced, onToggle = { showTaskAdvanced = !showTaskAdvanced })
            AnimatedVisibility(visible = showTaskAdvanced) {
                Column {
                    Text(
                        text = "Urgency Scoring Weights",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Weights auto-normalize to sum to 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    var localDueDate by remember(urgencyWeights) { mutableFloatStateOf(urgencyWeights.dueDate) }
                    var localPriority by remember(urgencyWeights) { mutableFloatStateOf(urgencyWeights.priority) }
                    var localAge by remember(urgencyWeights) { mutableFloatStateOf(urgencyWeights.age) }
                    var localSubtasks by remember(urgencyWeights) { mutableFloatStateOf(urgencyWeights.subtasks) }

                    fun normalizeAndSave() {
                        val total = localDueDate + localPriority + localAge + localSubtasks
                        if (total > 0) {
                            val w = UrgencyWeights(
                                localDueDate / total, localPriority / total,
                                localAge / total, localSubtasks / total
                            )
                            localDueDate = w.dueDate
                            localPriority = w.priority
                            localAge = w.age
                            localSubtasks = w.subtasks
                            viewModel.setUrgencyWeights(w)
                        }
                    }

                    WeightSlider("Due Date", localDueDate) { localDueDate = it; normalizeAndSave() }
                    WeightSlider("Priority", localPriority) { localPriority = it; normalizeAndSave() }
                    WeightSlider("Task Age", localAge) { localAge = it; normalizeAndSave() }
                    WeightSlider("Subtasks", localSubtasks) { localSubtasks = it; normalizeAndSave() }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.resetTaskBehaviorDefaults() }) {
                        Text("Reset task defaults", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()

            // ========== DATA ==========
            SectionHeader("Data")

            SettingsRow(
                title = "Manage Tags",
                onClick = { navController.navigate("tag_management") }
            )
            SettingsRow(
                title = "Manage Projects",
                onClick = { navController.navigate("project_list") }
            )
            SettingsRowWithSubtitle(
                title = "Auto-archive",
                subtitle = if (autoArchiveDays == 0) "Never" else "After $autoArchiveDays days",
                onClick = { showAutoArchiveDialog = true }
            )
            SettingsRowWithSubtitle(
                title = "Archive",
                subtitle = "$archivedCount archived tasks",
                onClick = { navController.navigate("archive") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                enabled = !isResetting,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                if (isResetting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resetting...", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Reset App", color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider()

            // ========== BACKUP & EXPORT ==========
            SectionHeader("Backup & Export")

            SettingsRow(title = "Export as JSON", onClick = { viewModel.onExportJson() })
            SettingsRow(title = "Export as CSV", onClick = { viewModel.onExportCsv() })
            SettingsRow(title = "Import from JSON", onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })

            HorizontalDivider()

            // ========== ACCOUNT & SYNC ==========
            SectionHeader("Account & Sync")

            if (isSignedIn) {
                val email = viewModel.userEmail
                if (email != null) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onSync() },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now")
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.onSignOut() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sign Out")
                    }
                }
            } else {
                Text(
                    text = "Sign in to sync across devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Button(
                    onClick = { navController.navigate("auth") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Sign in with Google")
                }
            }

            HorizontalDivider()

            // ========== GOOGLE CALENDAR ==========
            SectionHeader("Google Calendar")

            var showCalendarPicker by remember { mutableStateOf(false) }
            val calendarPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions.values.all { it }
                if (granted) {
                    viewModel.loadCalendars()
                    showCalendarPicker = true
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync tasks to calendar", style = MaterialTheme.typography.bodyLarge)
                    if (calendarSyncEnabled && calendarName.isNotBlank()) {
                        Text(
                            text = calendarName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                androidx.compose.material3.Switch(
                    checked = calendarSyncEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val calId = viewModel.calendarName.value
                            if (calId.isBlank()) {
                                // Need to pick a calendar first
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            } else {
                                viewModel.setCalendarSyncEnabled(true)
                            }
                        } else {
                            viewModel.setCalendarSyncEnabled(false)
                        }
                    }
                )
            }

            if (calendarSyncEnabled) {
                OutlinedButton(
                    onClick = {
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(if (calendarName.isNotBlank()) "Change Calendar ($calendarName)" else "Select Calendar")
                }
            }

            if (showCalendarPicker && availableCalendars.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showCalendarPicker = false },
                    title = { Text("Select Calendar") },
                    text = {
                        Column {
                            availableCalendars.forEach { cal ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectCalendar(cal)
                                            viewModel.setCalendarSyncEnabled(true)
                                            showCalendarPicker = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            cal.accountName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCalendarPicker = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            HorizontalDivider()

            // ========== AI ==========
            SectionHeader("AI")

            var showApiKeyDialog by remember { mutableStateOf(false) }
            var apiKeyInput by remember { mutableStateOf("") }

            val apiKeySubtitle = if (claudeApiKey.isNotBlank()) {
                "Configured (\u2022\u2022\u2022\u2022${claudeApiKey.takeLast(4)})"
            } else {
                "Not configured"
            }

            SettingsRowWithSubtitle(
                title = "Claude API Key",
                subtitle = apiKeySubtitle,
                onClick = {
                    apiKeyInput = ""
                    showApiKeyDialog = true
                }
            )

            if (showApiKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showApiKeyDialog = false },
                    title = { Text("Claude API Key") },
                    text = {
                        Column {
                            Text(
                                "Used for AI-powered import parsing. Get a key from console.anthropic.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                placeholder = { Text("sk-ant-...") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) viewModel.setClaudeApiKey(apiKeyInput.trim())
                                showApiKeyDialog = false
                            },
                            enabled = apiKeyInput.isNotBlank()
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            if (claudeApiKey.isNotBlank()) {
                                TextButton(onClick = {
                                    viewModel.clearClaudeApiKey()
                                    showApiKeyDialog = false
                                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                            }
                            TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
                        }
                    }
                )
            }

            HorizontalDivider()

            // ========== ABOUT ==========
            SectionHeader("About")

            Text(
                text = "AveryTask v0.6.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Made by Avery Karlin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRowWithSubtitle(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AdvancedToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Advanced",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReorderableRow(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ColorOverrideRow(label: String, currentHex: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentHex.isNotBlank()) {
            val color = try { Color(android.graphics.Color.parseColor(currentHex)) } catch (_: Exception) { Color.Gray }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("--", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (currentHex.isNotBlank()) currentHex else "Default",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PriorityColorRow(label: String, currentHex: String, defaultColor: Color, onClick: () -> Unit) {
    val displayColor = if (currentHex.isNotBlank()) {
        try { Color(android.graphics.Color.parseColor(currentHex)) } catch (_: Exception) { defaultColor }
    } else defaultColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(displayColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = if (currentHex.isNotBlank()) currentHex else "Default",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeToggleRow(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun WeightSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.7f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(36.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    title: String,
    currentHex: String,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember(currentHex) { mutableStateOf(currentHex) }

    val presetColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
        "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#607D8B",
        "#9E9E9E", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Hex color") },
                    placeholder = { Text("#FF0000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (hexInput.equals(hex, ignoreCase = true))
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { hexInput = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(hexInput) },
                enabled = hexInput.isNotBlank()
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Reset to Default", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
