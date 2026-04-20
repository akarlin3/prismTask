package com.averycorp.prismtask.ui.screens.schoolwork

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.remote.api.SyllabusEventResponse
import com.averycorp.prismtask.data.remote.api.SyllabusRecurringItemResponse
import com.averycorp.prismtask.data.remote.api.SyllabusTaskResponse
import com.averycorp.prismtask.ui.theme.LocalPrismColors

@Composable private fun schoolAccent(): Color =
    LocalPrismColors.current.dataVisualizationPalette.getOrElse(0) { LocalPrismColors.current.primary }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusReviewScreen(
    navController: NavController,
    viewModel: SyllabusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checkedTasks by viewModel.checkedTasks.collectAsStateWithLifecycle()
    val checkedEvents by viewModel.checkedEvents.collectAsStateWithLifecycle()
    val checkedRecurring by viewModel.checkedRecurring.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Navigate back on success after showing summary
    LaunchedEffect(uiState) {
        if (uiState is SyllabusViewModel.UiState.Success) {
            val s = uiState as SyllabusViewModel.UiState.Success
            val parts = mutableListOf<String>()
            if (s.tasksCreated > 0) parts.add("${s.tasksCreated} tasks")
            if (s.eventsCreated > 0) parts.add("${s.eventsCreated} events")
            if (s.recurringCreated > 0) parts.add("${s.recurringCreated} recurring schedule")
            snackbarHostState.showSnackbar("Added ${parts.joinToString(", ")}")
            viewModel.resetToIdle()
            navController.popBackStack()
        }
    }

    val totalChecked = checkedTasks.size + checkedEvents.size + checkedRecurring.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Review Syllabus Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetToIdle()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            when (uiState) {
                is SyllabusViewModel.UiState.Review -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onConfirm() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = totalChecked > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = schoolAccent(),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Add $totalChecked Items to PrismTask",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is SyllabusViewModel.UiState.Uploading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = schoolAccent())
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Analyzing Your Syllabus...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is SyllabusViewModel.UiState.Confirming -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = schoolAccent())
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Adding Items...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is SyllabusViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDCCB", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is SyllabusViewModel.UiState.Review -> {
                val result = state.result

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    // Course name header
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "\uD83C\uDF93 ${result.courseName}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // Tasks section
                    if (result.tasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Tasks (${result.tasks.size})",
                                icon = "\uD83D\uDCDD"
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(result.tasks) { index, task ->
                            val effective = viewModel.getEffectiveTask(index, task)
                            val checked = index in checkedTasks
                            SyllabusTaskItem(
                                task = effective,
                                checked = checked,
                                onToggle = { viewModel.onTaskToggled(index, !checked) },
                                onEdit = { edited -> viewModel.onTaskEdited(index, edited) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    // Events section
                    if (result.events.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Calendar Events (${result.events.size})",
                                icon = "\uD83D\uDCC5"
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(result.events) { index, event ->
                            val effective = viewModel.getEffectiveEvent(index, event)
                            val checked = index in checkedEvents
                            SyllabusEventItem(
                                event = effective,
                                checked = checked,
                                onToggle = { viewModel.onEventToggled(index, !checked) },
                                onEdit = { edited -> viewModel.onEventEdited(index, edited) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    // Recurring section
                    if (result.recurringSchedule.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recurring Schedule (${result.recurringSchedule.size})",
                                icon = "\uD83D\uDD01"
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(result.recurringSchedule) { index, item ->
                            val effective = viewModel.getEffectiveRecurring(index, item)
                            val checked = index in checkedRecurring
                            SyllabusRecurringItem(
                                item = effective,
                                checked = checked,
                                onToggle = { viewModel.onRecurringToggled(index, !checked) },
                                onEdit = { edited -> viewModel.onRecurringEdited(index, edited) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            else -> {
                // Idle or Success — handled by navigation
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CheckBox(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) schoolAccent() else Color.Transparent)
            .border(
                2.dp,
                if (checked) schoolAccent() else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Checked",
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SyllabusTaskItem(
    task: SyllabusTaskResponse,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: (SyllabusTaskResponse) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(task.title) { mutableStateOf(task.title) }
    var editDate by remember(task.dueDate) { mutableStateOf(task.dueDate ?: "") }

    ItemCard(checked = checked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(checked = checked, onClick = onToggle)
            Spacer(Modifier.width(12.dp))

            if (editing) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onEdit(
                                task.copy(
                                    title = editTitle,
                                    dueDate = editDate.ifBlank { null }
                                )
                            )
                            editing = false
                        })
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.dueDate != null) {
                            Text(
                                "Due: ${task.dueDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "No Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TypeChip(task.type)
                    }
                }
            }

            IconButton(
                onClick = {
                    if (editing) {
                        onEdit(
                            task.copy(
                                title = editTitle,
                                dueDate = editDate.ifBlank { null }
                            )
                        )
                    }
                    editing = !editing
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editing) "Save" else "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyllabusEventItem(
    event: SyllabusEventResponse,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: (SyllabusEventResponse) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(event.title) { mutableStateOf(event.title) }
    var editDate by remember(event.date) { mutableStateOf(event.date ?: "") }

    ItemCard(checked = checked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(checked = checked, onClick = onToggle)
            Spacer(Modifier.width(12.dp))

            if (editing) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onEdit(
                                event.copy(
                                    title = editTitle,
                                    date = editDate.ifBlank { null }
                                )
                            )
                            editing = false
                        })
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val timeStr = buildString {
                            if (event.date != null) append(event.date)
                            if (event.startTime != null) {
                                if (isNotEmpty()) append(" ")
                                append(event.startTime)
                                if (event.endTime != null) append("-${event.endTime}")
                            }
                        }
                        if (timeStr.isNotEmpty()) {
                            Text(
                                timeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (event.location != null) {
                            Text(
                                "\uD83D\uDCCD ${event.location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (editing) {
                        onEdit(
                            event.copy(
                                title = editTitle,
                                date = editDate.ifBlank { null }
                            )
                        )
                    }
                    editing = !editing
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editing) "Save" else "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyllabusRecurringItem(
    item: SyllabusRecurringItemResponse,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: (SyllabusRecurringItemResponse) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(item.title) { mutableStateOf(item.title) }

    ItemCard(checked = checked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(checked = checked, onClick = onToggle)
            Spacer(Modifier.width(12.dp))

            if (editing) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onEdit(item.copy(title = editTitle))
                        editing = false
                    })
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.dayOfWeek.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = schoolAccent(),
                            fontWeight = FontWeight.Bold
                        )
                        val timeStr = buildString {
                            if (item.startTime != null) {
                                append(item.startTime)
                                if (item.endTime != null) append("-${item.endTime}")
                            }
                        }
                        if (timeStr.isNotEmpty()) {
                            Text(
                                timeStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.location != null) {
                            Text(
                                "\uD83D\uDCCD ${item.location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (editing) {
                        onEdit(item.copy(title = editTitle))
                    }
                    editing = !editing
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editing) "Save" else "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    checked: Boolean,
    content: @Composable () -> Unit
) {
    val borderColor = if (checked) {
        schoolAccent().copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val bgColor = if (checked) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        content()
    }
}

@Composable
private fun TypeChip(type: String) {
    val c = LocalPrismColors.current
    val chipColor = when (type) {
        "exam" -> c.destructiveColor
        "quiz" -> c.warningColor
        "project" -> c.dataVisualizationPalette.getOrElse(2) { c.primary }
        "assignment" -> c.primary
        "reading" -> c.successColor
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            type.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            fontWeight = FontWeight.Bold
        )
    }
}
