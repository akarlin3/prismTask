package com.averycorp.prismtask.ui.screens.automation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.averycorp.prismtask.data.seed.AutomationTemplate
import com.averycorp.prismtask.data.seed.AutomationTemplateCategory
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationCondition
import com.averycorp.prismtask.domain.automation.AutomationTrigger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationTemplateLibraryScreen(
    navController: NavHostController,
    viewModel: AutomationTemplateLibraryViewModel = hiltViewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var detailTemplate by remember { mutableStateOf<AutomationTemplate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AutomationTemplateLibraryViewModel.LibraryEvent.Imported ->
                    snackbarHostState.showSnackbar(
                        "Added \"${event.templateName}\" — toggle to enable in Automation"
                    )
                is AutomationTemplateLibraryViewModel.LibraryEvent.AlreadyImported ->
                    snackbarHostState.showSnackbar(
                        "Already added \"${event.templateName}\" — find it in Automation"
                    )
                AutomationTemplateLibraryViewModel.LibraryEvent.ImportFailed ->
                    snackbarHostState.showSnackbar("Could not import template — please try again")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Browse Templates") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (sections.isEmpty()) {
                EmptyState(query = query)
                return@Scaffold
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                sections.forEach { section ->
                    item(key = "header-${section.category.name}") {
                        CategoryHeader(category = section.category, count = section.templates.size)
                    }
                    items(section.templates, key = { "${section.category.name}-${it.id}" }) { tpl ->
                        TemplateCard(
                            template = tpl,
                            onClick = { detailTemplate = tpl },
                            onAdd = { viewModel.importTemplate(tpl.id) }
                        )
                    }
                }
            }
        }
    }

    detailTemplate?.let { template ->
        TemplateDetailSheet(
            template = template,
            onDismiss = { detailTemplate = null },
            onImport = {
                coroutineScope.launch {
                    viewModel.importTemplate(template.id)
                    detailTemplate = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag("template-library-search"),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        placeholder = { Text("Search templates") }
    )
}

@Composable
private fun CategoryHeader(category: AutomationTemplateCategory, count: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count Templates",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TemplateCard(
    template: AutomationTemplate,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("template-card-${template.id}"),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TriggerChip(template.trigger)
                    if (template.requiresAi) {
                        Spacer(Modifier.width(8.dp))
                        AiPill()
                    }
                }
            }
            IconButton(onClick = onAdd, modifier = Modifier.testTag("add-${template.id}")) {
                Icon(Icons.Filled.Add, contentDescription = "Add to my rules")
            }
        }
    }
}

@Composable
private fun TriggerChip(trigger: AutomationTrigger) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = triggerLabel(trigger),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AiPill() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Requires AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (query.isBlank()) "No Templates Available" else "No Matches",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (query.isBlank()) {
                "The library is empty — please update the app."
            } else {
                "No template matches \"$query\". Try a shorter keyword."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDetailSheet(
    template: AutomationTemplate,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(template.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))

            DetailSection(
                title = "When This Fires",
                body = triggerLabel(template.trigger)
            )

            template.condition?.let { condition ->
                Spacer(Modifier.height(16.dp))
                DetailSection(
                    title = "Only If",
                    body = conditionLabel(condition)
                )
            }

            Spacer(Modifier.height(16.dp))
            DetailSection(
                title = "Then It Does",
                body = template.actions.joinToString("\n") { "• ${actionLabel(it)}" }
            )

            if (template.requiresAi) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Requires AI features to be enabled in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().testTag("template-detail-add")
            ) {
                Text("Add To My Rules")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Imported templates start disabled — toggle them on in Automation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun triggerLabel(trigger: AutomationTrigger): String = when (trigger) {
    is AutomationTrigger.EntityEvent -> "When ${humanizeEventKind(trigger.eventKind)}"
    is AutomationTrigger.TimeOfDay -> "Daily at %02d:%02d".format(trigger.hour, trigger.minute)
    is AutomationTrigger.DayOfWeekTime ->
        "%s at %02d:%02d".format(humanizeDays(trigger.daysOfWeek), trigger.hour, trigger.minute)
    AutomationTrigger.Manual -> "Tap Run Now"
    is AutomationTrigger.Composed -> "After rule #${trigger.parentRuleId} fires"
}

private fun humanizeEventKind(kind: String): String = when (kind) {
    "TaskCreated" -> "a task is created"
    "TaskUpdated" -> "a task is updated"
    "TaskCompleted" -> "a task is completed"
    "TaskDeleted" -> "a task is deleted"
    "HabitCompleted" -> "a habit is completed"
    "HabitStreakHit" -> "a habit streak hits"
    "MedicationLogged" -> "a medication is logged"
    else -> kind
}

private fun humanizeDays(days: Set<String>): String = when {
    days == setOf("SATURDAY", "SUNDAY") -> "Weekends"
    days == setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY") -> "Weekdays"
    days.size == 1 -> days.first().lowercase().replaceFirstChar { it.uppercase() }
    else -> days.joinToString(", ") { it.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
}

internal fun conditionLabel(condition: AutomationCondition): String = when (condition) {
    is AutomationCondition.Compare -> {
        val opLabel = when (condition.opType) {
            AutomationCondition.Op.EQ -> "is"
            AutomationCondition.Op.NE -> "is not"
            AutomationCondition.Op.GT -> ">"
            AutomationCondition.Op.GTE -> "≥"
            AutomationCondition.Op.LT -> "<"
            AutomationCondition.Op.LTE -> "≤"
            AutomationCondition.Op.CONTAINS -> "contains"
            AutomationCondition.Op.STARTS_WITH -> "starts with"
            AutomationCondition.Op.EXISTS -> "exists"
            AutomationCondition.Op.WITHIN_LAST_MS -> "within last (ms)"
        }
        val v = condition.value
        val rendered = when {
            v == null -> ""
            v is Map<*, *> && v.containsKey("@now") -> "now"
            else -> v.toString()
        }
        "${condition.field} $opLabel $rendered".trimEnd()
    }
    is AutomationCondition.And ->
        condition.children.joinToString(" AND ") { conditionLabel(it) }
    is AutomationCondition.Or ->
        condition.children.joinToString(" OR ") { conditionLabel(it) }
    is AutomationCondition.Not ->
        "NOT ${conditionLabel(condition.child)}"
}

internal fun actionLabel(action: AutomationAction): String = when (action) {
    is AutomationAction.Notify -> "Send notification: \"${action.title}\""
    is AutomationAction.MutateTask -> "Update task: ${action.updates.keys.joinToString()}"
    is AutomationAction.MutateHabit -> "Update habit: ${action.updates.keys.joinToString()}"
    is AutomationAction.MutateMedication -> "Update medication: ${action.updates.keys.joinToString()}"
    is AutomationAction.ScheduleTimer -> "Start ${action.mode.lowercase()} timer ${action.durationMinutes}m"
    is AutomationAction.ApplyBatch -> "Apply ${action.mutations.size} batch mutation(s)"
    is AutomationAction.AiComplete -> "Ask AI: \"${action.prompt.take(40).trimEnd()}…\""
    is AutomationAction.AiSummarize -> "Ask AI to summarize ${action.scope.replace('_', ' ')} (last ${action.maxItems})"
    is AutomationAction.LogMessage -> "Log: ${action.message}"
}
