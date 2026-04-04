package com.todounified.ui.screens

import android.app.DatePickerDialog
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.todounified.data.*
import com.todounified.ui.components.*
import com.todounified.ui.theme.*
import com.todounified.viewmodel.*
import java.time.LocalDate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: TodoViewModel) {
    val state by vm.uiState.collectAsState()
    var showAddTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var showNewList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var sortBy by remember { mutableStateOf("created") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importJsxFile(it) }
    }

    val activeListWithTasks = state.lists.find { it.list.id == state.activeListId }
    val activeImport = state.importedTabs.find { it.id == state.activeImportId }
    val isNative = state.activeListId != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        // ═══ Header ═══
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    "// TODO",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = MonoFont,
                    color = OnSurface,
                    letterSpacing = (-1).sp
                )
                Text(
                    "UNIFIED TASK MANAGER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = MonoFont,
                    color = OnSurfaceFaint,
                    letterSpacing = 1.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Settings
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, "Settings", tint = OnSurfaceDim, modifier = Modifier.size(20.dp))
                }
                // Import button
                OutlinedButton(
                    onClick = {
                        if (state.apiKey.isBlank()) {
                            showSettings = true
                        } else {
                            filePicker.launch(arrayOf("*/*"))
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Green,
                        containerColor = GreenBg.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("📥", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFont)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ═══ Tabs ═══
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Native list tabs
            items(state.lists, key = { it.list.id }) { listWithTasks ->
                val l = listWithTasks.list
                val active = state.activeListId == l.id
                val done = listWithTasks.tasks.count { it.done }
                val total = listWithTasks.tasks.size

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { vm.selectList(l.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(l.emoji, fontSize = 16.sp)
                        Text(
                            l.name, fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) OnSurface else OnSurfaceDim,
                            fontFamily = MonoFont
                        )
                        if (total > 0) {
                            Text(
                                "$done/$total", fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceFaint, fontFamily = MonoFont
                            )
                        }
                    }
                }
            }

            // Imported tabs
            items(state.importedTabs, key = { it.id }) { tab ->
                val active = state.activeImportId == tab.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { vm.selectImport(tab.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tab.emoji, fontSize = 16.sp)
                        Text(
                            tab.name, fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) OnSurface else OnSurfaceDim,
                            fontFamily = MonoFont, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "JSX", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            color = Green, fontFamily = MonoFont,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(GreenBg)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Add list button
            item {
                if (showNewList) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        StyledTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            placeholder = "List name…",
                            modifier = Modifier.width(100.dp)
                        )
                        IconButton(
                            onClick = {
                                vm.addList(newListName)
                                newListName = ""
                                showNewList = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Check, "Add", tint = Indigo)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showNewList = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceFaint),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text("+ List", fontSize = 12.sp, fontFamily = MonoFont)
                    }
                }
            }
        }

        HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 8.dp))

        // ═══ Native list content ═══
        if (isNative && activeListWithTasks != null) {
            val allTasks = activeListWithTasks.tasks
            val totalAll = allTasks.size
            val totalDone = allTasks.count { it.done }

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("all" to "All", "active" to "Active", "done" to "Done").forEach { (key, label) ->
                        val sel = filter == key
                        Text(
                            text = if (key == "all" && totalAll > 0) "$label ($totalAll)" else label,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (sel) OnSurface else OnSurfaceFaint,
                            fontFamily = MonoFont,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { filter = key }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Sort (simple text toggle)
                    Text(
                        text = when (sortBy) { "priority" -> "↕ Pri"; "due" -> "↕ Due"; else -> "↕ New" },
                        fontSize = 10.sp, color = OnSurfaceFaint, fontFamily = MonoFont,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Border, RoundedCornerShape(6.dp))
                            .clickable {
                                sortBy = when (sortBy) { "created" -> "priority"; "priority" -> "due"; else -> "created" }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    if (!showAddTask && editingTask == null) {
                        Button(
                            onClick = { showAddTask = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text("+ Task", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFont)
                        }
                    }
                }
            }

            // Progress bar
            if (totalAll > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$totalDone of $totalAll complete", fontSize = 10.sp, color = OnSurfaceFaint, fontFamily = MonoFont)
                    Text("${if (totalAll > 0) (totalDone * 100 / totalAll) else 0}%", fontSize = 10.sp, color = OnSurfaceFaint, fontFamily = MonoFont)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(if (totalAll > 0) totalDone.toFloat() / totalAll else 0f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(Indigo, Blue)))
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Add task form
            AnimatedVisibility(showAddTask) {
                TaskFormCard(
                    onSubmit = { title, pri, due, tags ->
                        vm.addTask(title, pri, due, tags)
                        showAddTask = false
                    },
                    onCancel = { showAddTask = false },
                    allTags = allTasks.flatMap { it.tags }.distinct()
                )
            }

            // Edit task form
            editingTask?.let { task ->
                TaskFormCard(
                    initial = task,
                    onSubmit = { title, pri, due, tags ->
                        vm.updateTask(task, title, pri, due, tags)
                        editingTask = null
                    },
                    onCancel = { editingTask = null },
                    allTags = allTasks.flatMap { it.tags }.distinct()
                )
            }

            // Task list
            val priorityOrder = mapOf(Priority.URGENT to 0, Priority.HIGH to 1, Priority.MEDIUM to 2, Priority.LOW to 3)
            val filtered = when (filter) {
                "active" -> allTasks.filter { !it.done }
                "done" -> allTasks.filter { it.done }
                else -> allTasks
            }.sortedWith(
                when (sortBy) {
                    "priority" -> compareBy { priorityOrder[it.priority] ?: 2 }
                    "due" -> compareBy<Task> { it.dueDate.isBlank() }.thenBy { it.dueDate }
                    else -> compareByDescending { it.createdAt }
                }
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when (filter) {
                                    "done" -> "No completed tasks yet."
                                    "active" -> "All tasks are done! 🎉"
                                    else -> "Empty list. Hit + Task to get started."
                                },
                                color = OnSurfaceFaint, fontSize = 13.sp, fontFamily = MonoFont
                            )
                        }
                    }
                }
                items(filtered, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { vm.toggleTask(task) },
                        onDelete = { vm.deleteTask(task) },
                        onEdit = { editingTask = task; showAddTask = false }
                    )
                }
                // Clear done button
                if (totalDone > 0 && filter != "done") {
                    item {
                        OutlinedButton(
                            onClick = { vm.clearDoneTasks() },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(
                                "Clear $totalDone completed task${if (totalDone > 1) "s" else ""}",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFont
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        // ═══ Imported tab content ═══
        if (!isNative && activeImport != null) {
            ImportedTabContent(
                tab = activeImport,
                vm = vm,
                nativeLists = state.lists.map { it.list }
            )
        }

        // ═══ Import status snackbar ═══
        if (state.importStatus != ImportStatus.Idle) {
            ImportStatusBar(state.importStatus, state.importError) {
                vm.resetImportStatus()
            }
        }
    }

    // ═══ Settings Dialog ═══
    if (showSettings) {
        SettingsDialog(
            apiKey = state.apiKey,
            onSave = { vm.setApiKey(it); showSettings = false },
            onDismiss = { showSettings = false }
        )
    }
}

// ── Task Form Card ──
@Composable
fun TaskFormCard(
    initial: Task? = null,
    onSubmit: (String, Priority, String, List<String>) -> Unit,
    onCancel: () -> Unit,
    allTags: List<String>
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var priority by remember(initial) { mutableStateOf(initial?.priority ?: Priority.MEDIUM) }
    var dueDate by remember(initial) { mutableStateOf(initial?.dueDate ?: "") }
    var tags by remember(initial) { mutableStateOf(initial?.tags ?: emptyList()) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StyledTextField(title, { title = it }, "Task title…", fontSize = 15, fontWeight = FontWeight.SemiBold)

        // Priority
        Column {
            Text("PRIORITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceFaint, fontFamily = MonoFont, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            PrioritySelector(priority) { priority = it }
        }

        // Due date
        Column {
            Text("DUE DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceFaint, fontFamily = MonoFont, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = dueDate.ifBlank { "No date set" },
                    fontSize = 12.sp, color = if (dueDate.isBlank()) OnSurfaceFaint else OnSurface,
                    fontFamily = MonoFont,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .clickable {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                dueDate = "%04d-%02d-%02d".format(y, m + 1, d)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                if (dueDate.isNotBlank()) {
                    Text(
                        "✕", color = OnSurfaceFaint, fontSize = 12.sp,
                        modifier = Modifier.clickable { dueDate = "" }
                    )
                }
            }
        }

        // Tags
        Column {
            Text("TAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnSurfaceFaint, fontFamily = MonoFont, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            TagSelector(allTags, tags) { tag ->
                tags = if (tag in tags) tags - tag else tags + tag
            }
        }

        // Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Border)
            ) {
                Text("Cancel", fontSize = 12.sp, fontFamily = MonoFont, color = OnSurfaceDim)
            }
            Button(
                onClick = { if (title.isNotBlank()) onSubmit(title, priority, dueDate, tags) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Text(
                    if (initial != null) "Save" else "Add Task",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFont
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

// ── Imported Tab Content ──
@Composable
fun ImportedTabContent(tab: ImportedTab, vm: TodoViewModel, nativeLists: List<TaskList>) {
    var view by remember { mutableStateOf("preview") }
    var mergeTarget by remember(nativeLists) { mutableStateOf(nativeLists.firstOrNull()?.id ?: "") }
    val extractedTasks = remember(tab) { vm.getExtractedTasks(tab) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Info
        item {
            Column {
                Text("Imported from ${tab.fileName}", fontSize = 11.sp, color = OnSurfaceFaint, fontFamily = MonoFont)
                if (tab.description.isNotBlank()) {
                    Text(tab.description, fontSize = 12.sp, color = OnSurfaceDim, fontFamily = MonoFont)
                }
                if (tab.originalStructure.isNotBlank()) {
                    Text("Structure: ${tab.originalStructure}", fontSize = 10.sp, color = OnSurfaceFaint, fontFamily = MonoFont)
                }
            }
        }

        // Merge controls
        if (extractedTasks.isNotEmpty() && nativeLists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenBg.copy(alpha = 0.3f))
                        .border(1.dp, Green.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Merge ${extractedTasks.size} tasks →", fontSize = 11.sp, color = OnSurfaceDim, fontFamily = MonoFont)
                    // Simple target display (first list)
                    val targetList = nativeLists.find { it.id == mergeTarget }
                    Text(
                        "${targetList?.emoji ?: ""} ${targetList?.name ?: ""}",
                        fontSize = 11.sp, color = OnSurface, fontFamily = MonoFont,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Border, RoundedCornerShape(6.dp))
                            .clickable {
                                val idx = nativeLists.indexOfFirst { it.id == mergeTarget }
                                val next = (idx + 1) % nativeLists.size
                                mergeTarget = nativeLists[next].id
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Button(
                        onClick = { vm.mergeImportedTasks(tab, mergeTarget) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text("Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFont)
                    }
                }
            }
        }

        // View toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("preview" to "Preview", "tasks" to "Tasks (${extractedTasks.size})", "source" to "Source").forEach { (k, l) ->
                        Text(
                            l, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (view == k) OnSurface else OnSurfaceFaint,
                            fontFamily = MonoFont,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (view == k) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { view = k }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { vm.deleteImportedTab(tab) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("Remove", fontSize = 11.sp, fontFamily = MonoFont)
                }
            }
        }

        // Content views
        when (view) {
            "preview" -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                setBackgroundColor(android.graphics.Color.parseColor("#0D0D12"))
                                loadDataWithBaseURL(null, """
                                    <html><body style="background:#0d0d12;color:#e2e2e8;font-family:monospace;padding:16px;margin:0;">
                                    ${tab.renderHtml}
                                    </body></html>
                                """.trimIndent(), "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp)
                    )
                }
            }

            "tasks" -> {
                if (extractedTasks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No tasks could be extracted.", color = OnSurfaceFaint, fontSize = 13.sp, fontFamily = MonoFont)
                        }
                    }
                } else {
                    items(extractedTasks.size) { i ->
                        val t = extractedTasks[i]
                        val priStyle = priorityStyles[
                            try { Priority.valueOf(t.priority.uppercase()) } catch (_: Exception) { Priority.MEDIUM }
                        ] ?: priorityStyles[Priority.MEDIUM]!!

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Border, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${priStyle.icon} ${priStyle.label}",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = priStyle.color, fontFamily = MonoFont,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(priStyle.bg)
                                    .padding(horizontal = 8.dp, vertical = 1.dp)
                            )
                            Text(
                                t.title, fontSize = 13.sp, fontFamily = MonoFont,
                                color = if (t.done) OnSurfaceFaint else OnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            t.tags.forEach { TagPill(it, small = true) }
                        }
                    }
                }
            }

            "source" -> item {
                Text(
                    tab.sourceCode,
                    fontSize = 11.sp, color = OnSurfaceDim, fontFamily = MonoFont,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Import Status Bar ──
@Composable
fun ImportStatusBar(status: ImportStatus, error: String, onDismiss: () -> Unit) {
    val (bg, text) = when (status) {
        ImportStatus.Reading -> OrangeBg to "Reading file…"
        ImportStatus.Parsing -> IndigoBg to "🤖 AI analyzing component…"
        ImportStatus.Done -> GreenBg to "✅ Import complete!"
        ImportStatus.Error -> RedBg to "❌ $error"
        else -> return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = status == ImportStatus.Done || status == ImportStatus.Error) { onDismiss() }
            .padding(12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontFamily = MonoFont, color = OnSurface)
    }
}

// ── Settings Dialog ──
@Composable
fun SettingsDialog(apiKey: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text("Settings", fontFamily = MonoFont, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Anthropic API key for JSX import analysis",
                    fontSize = 12.sp, color = OnSurfaceDim, fontFamily = MonoFont
                )
                StyledTextField(key, { key = it }, "sk-ant-…")
                Text(
                    "Your key is stored locally and only used for JSX parsing.",
                    fontSize = 10.sp, color = OnSurfaceFaint, fontFamily = MonoFont
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) { Text("Save", fontFamily = MonoFont) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = MonoFont, color = OnSurfaceDim) }
        }
    )
}
