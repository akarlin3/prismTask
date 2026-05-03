package com.averycorp.prismtask.ui.screens.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.averycorp.prismtask.domain.automation.AutomationCondition.Op

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationRuleEditScreen(
    navController: NavHostController,
    viewModel: AutomationRuleEditViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == null) "New Rule" else "Edit Rule") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { BasicSection(draft, viewModel) }
            item { TriggerSection(draft, viewModel) }
            item { ConditionSection(draft, viewModel) }
            item {
                Text("Actions", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(draft.actions, key = { idx, _ -> idx }) { idx, action ->
                ActionEditor(
                    index = idx,
                    action = action,
                    canRemove = draft.actions.size > 1,
                    onChange = { viewModel.replaceAction(idx, it) },
                    onRemove = { viewModel.removeAction(idx) }
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addAction() }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Action")
                }
            }
        }
    }
}

@Composable
private fun BasicSection(draft: RuleDraft, vm: AutomationRuleEditViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { vm.setName(it) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { vm.setDescription(it) },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = draft.enabled, onCheckedChange = { vm.setEnabled(it) })
            }
        }
    }
}

@Composable
private fun TriggerSection(draft: RuleDraft, vm: AutomationRuleEditViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("When", style = MaterialTheme.typography.titleMedium)
            DropdownPicker(
                label = "Trigger",
                value = draft.triggerKind.label,
                options = TriggerKind.values().map { it.label to it }
            ) { vm.setTriggerKind(it) }
            when (draft.triggerKind) {
                TriggerKind.ENTITY_EVENT -> DropdownPicker(
                    label = "Event",
                    value = draft.entityEventKind,
                    options = AutomationRuleEditViewModel.ENTITY_EVENT_KINDS.map { it to it }
                ) { vm.setEntityEventKind(it) }
                TriggerKind.TIME_OF_DAY -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.timeHour.toString(),
                        onValueChange = { vm.setTimeHour(it.toIntOrNull() ?: 0) },
                        label = { Text("Hour (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = draft.timeMinute.toString(),
                        onValueChange = { vm.setTimeMinute(it.toIntOrNull() ?: 0) },
                        label = { Text("Minute (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                TriggerKind.DAY_OF_WEEK_TIME -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft.timeHour.toString(),
                            onValueChange = { vm.setTimeHour(it.toIntOrNull() ?: 0) },
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = draft.timeMinute.toString(),
                            onValueChange = { vm.setTimeMinute(it.toIntOrNull() ?: 0) },
                            label = { Text("Minute (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AutomationRuleEditViewModel.DAYS_OF_WEEK.forEach { day ->
                            val selected = day in draft.daysOfWeek
                            OutlinedButton(
                                onClick = { vm.toggleDayOfWeek(day) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = day.take(2),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    if (draft.daysOfWeek.isEmpty()) {
                        Text(
                            "Pick at least one day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                TriggerKind.MANUAL -> Text(
                    "Use \"Run Now\" from the rules list to fire this rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TriggerKind.COMPOSED -> {
                    val candidates by vm.composedCandidates.collectAsStateWithLifecycle()
                    if (candidates.isEmpty()) {
                        Text(
                            "No other rules available — create a primary rule first, then this one can chain off it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        DropdownPicker(
                            label = "Parent Rule",
                            value = candidates
                                .firstOrNull { it.id.toString() == draft.composedParentRuleId }
                                ?.name
                                ?: "Pick a rule",
                            options = candidates.map { it.name to it.id.toString() }
                        ) { vm.setComposedParentRuleId(it) }
                        Text(
                            "Cycles of length 5+ abort at fire time. The picker hides the rule " +
                                "being edited and any rule that already chains off it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionSection(draft: RuleDraft, vm: AutomationRuleEditViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Only If",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = draft.condition != null,
                    onCheckedChange = { vm.setConditionEnabled(it) }
                )
            }
            val node = draft.condition
            if (node != null) {
                ConditionNode(node = node, path = emptyList(), depth = 0, vm = vm)
                Text(
                    "Tip: numeric values are coerced from text; tag matches use #tag form. Wrap clauses in AND/OR/NOT to combine them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConditionNode(
    node: ConditionDraft,
    path: List<Int>,
    depth: Int,
    vm: AutomationRuleEditViewModel
) {
    val isRoot = path.isEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nodeLabel(node),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                NodeKindMenu(current = node) { newNode ->
                    if (isRoot) {
                        vm.setConditionRoot(newNode)
                    } else {
                        vm.replaceConditionAt(path) { newNode }
                    }
                }
                if (!isRoot) {
                    IconButton(
                        onClick = {
                            if (path.isEmpty()) {
                                vm.setConditionRoot(null)
                            } else {
                                vm.replaceConditionAt(path) { null }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove clause")
                    }
                }
            }
            when (node) {
                is ConditionDraft.Leaf -> LeafEditor(node) { newLeaf ->
                    if (isRoot) {
                        vm.setConditionRoot(newLeaf)
                    } else {
                        vm.replaceConditionAt(path) { newLeaf }
                    }
                }
                is ConditionDraft.And -> {
                    node.children.forEachIndexed { i, child ->
                        ConditionNode(child, path + i, depth + 1, vm)
                    }
                    OutlinedButton(
                        onClick = {
                            if (isRoot) {
                                vm.setConditionRoot(node.copy(children = node.children + ConditionDraft.Leaf()))
                            } else {
                                vm.replaceConditionAt(path) { current ->
                                    (current as ConditionDraft.And).copy(
                                        children = current.children + ConditionDraft.Leaf()
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Clause")
                    }
                }
                is ConditionDraft.Or -> {
                    node.children.forEachIndexed { i, child ->
                        ConditionNode(child, path + i, depth + 1, vm)
                    }
                    OutlinedButton(
                        onClick = {
                            if (isRoot) {
                                vm.setConditionRoot(node.copy(children = node.children + ConditionDraft.Leaf()))
                            } else {
                                vm.replaceConditionAt(path) { current ->
                                    (current as ConditionDraft.Or).copy(
                                        children = current.children + ConditionDraft.Leaf()
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Clause")
                    }
                }
                is ConditionDraft.Not -> {
                    ConditionNode(node.child, path + 0, depth + 1, vm)
                }
            }
        }
    }
}

@Composable
private fun NodeKindMenu(
    current: ConditionDraft,
    onPick: (ConditionDraft) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(currentKindLabel(current), style = MaterialTheme.typography.labelSmall)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Leaf") }, onClick = {
                expanded = false
                onPick(currentLeaf(current))
            })
            DropdownMenuItem(text = { Text("AND group") }, onClick = {
                expanded = false
                onPick(ConditionDraft.And(listOf(currentLeaf(current))))
            })
            DropdownMenuItem(text = { Text("OR group") }, onClick = {
                expanded = false
                onPick(ConditionDraft.Or(listOf(currentLeaf(current))))
            })
            DropdownMenuItem(text = { Text("NOT (negate)") }, onClick = {
                expanded = false
                onPick(ConditionDraft.Not(currentLeaf(current)))
            })
        }
    }
}

private fun currentKindLabel(node: ConditionDraft): String = when (node) {
    is ConditionDraft.Leaf -> "Leaf"
    is ConditionDraft.And -> "AND"
    is ConditionDraft.Or -> "OR"
    is ConditionDraft.Not -> "NOT"
}

private fun currentLeaf(node: ConditionDraft): ConditionDraft.Leaf = when (node) {
    is ConditionDraft.Leaf -> node
    is ConditionDraft.And -> node.children.firstOrNull() as? ConditionDraft.Leaf
        ?: ConditionDraft.Leaf()
    is ConditionDraft.Or -> node.children.firstOrNull() as? ConditionDraft.Leaf
        ?: ConditionDraft.Leaf()
    is ConditionDraft.Not -> currentLeaf(node.child)
}

private fun nodeLabel(node: ConditionDraft): String = when (node) {
    is ConditionDraft.Leaf -> "${node.field} ${node.op.key} ${node.value.ifEmpty { "?" }}"
    is ConditionDraft.And -> "All of (${node.children.size})"
    is ConditionDraft.Or -> "Any of (${node.children.size})"
    is ConditionDraft.Not -> "Not"
}

@Composable
private fun LeafEditor(leaf: ConditionDraft.Leaf, onChange: (ConditionDraft.Leaf) -> Unit) {
    DropdownPicker(
        label = "Field",
        value = leaf.field,
        options = AutomationRuleEditViewModel.CONDITION_FIELDS.map { it to it }
    ) { onChange(leaf.copy(field = it)) }
    DropdownPicker(
        label = "Operator",
        value = leaf.op.name,
        options = Op.values().map { it.name to it }
    ) { onChange(leaf.copy(op = it)) }
    OutlinedTextField(
        value = leaf.value,
        onValueChange = { onChange(leaf.copy(value = it)) },
        label = { Text("Value") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActionEditor(
    index: Int,
    action: ActionDraft,
    canRemove: Boolean,
    onChange: (ActionDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Action ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
            DropdownPicker(
                label = "Type",
                value = labelForAction(action),
                options = listOf(
                    "Notify" to ActionDraft.Notify(),
                    "Log Message" to ActionDraft.Log(""),
                    "Set Task Priority" to ActionDraft.MutateTaskPriority(),
                    "Start Timer" to ActionDraft.Timer()
                )
            ) { onChange(it) }
            when (action) {
                is ActionDraft.Notify -> {
                    OutlinedTextField(
                        value = action.title,
                        onValueChange = { onChange(action.copy(title = it)) },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = action.body,
                        onValueChange = { onChange(action.copy(body = it)) },
                        label = { Text("Body") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ActionDraft.Log -> OutlinedTextField(
                    value = action.message,
                    onValueChange = { onChange(action.copy(message = it)) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
                is ActionDraft.MutateTaskPriority -> OutlinedTextField(
                    value = action.priority.toString(),
                    onValueChange = {
                        onChange(action.copy(priority = it.toIntOrNull()?.coerceIn(0, 4) ?: action.priority))
                    },
                    label = { Text("Priority (0-4)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                is ActionDraft.Timer -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = action.durationMinutes.toString(),
                        onValueChange = {
                            onChange(action.copy(durationMinutes = it.toIntOrNull() ?: action.durationMinutes))
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    DropdownPicker(
                        label = "Mode",
                        value = action.mode,
                        options = listOf(
                            "WORK" to "WORK",
                            "BREAK" to "BREAK",
                            "LONG_BREAK" to "LONG_BREAK"
                        ),
                        modifier = Modifier.weight(1f)
                    ) { onChange(action.copy(mode = it)) }
                }
            }
        }
    }
}

private fun labelForAction(action: ActionDraft): String = when (action) {
    is ActionDraft.Notify -> "Notify"
    is ActionDraft.Log -> "Log Message"
    is ActionDraft.MutateTaskPriority -> "Set Task Priority"
    is ActionDraft.Timer -> "Start Timer"
}

@Composable
private fun <T> DropdownPicker(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, item) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onPick(item)
                    }
                )
            }
        }
    }
}
