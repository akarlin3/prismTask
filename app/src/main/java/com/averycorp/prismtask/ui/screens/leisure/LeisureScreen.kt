package com.averycorp.prismtask.ui.screens.leisure

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay

data class LeisureOption(val id: String, val label: String, val icon: String)

private val musicColor = Color(0xFF8B5CF6)
private val successColor = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeisureScreen(
    navController: NavController,
    viewModel: LeisureViewModel = hiltViewModel()
) {
    val log by viewModel.todayLog.collectAsStateWithLifecycle()
    val instruments by viewModel.musicActivities.collectAsStateWithLifecycle()
    val flexOptions by viewModel.flexActivities.collectAsStateWithLifecycle()

    val musicPick = log?.musicPick
    val musicDone = log?.musicDone ?: false
    val flexPick = log?.flexPick
    val flexDone = log?.flexDone ?: false

    // Add activity dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogCategory by remember { mutableStateOf("music") } // "music" or "flex"

    // Delete confirmation state
    var activityToDelete by remember { mutableStateOf<LeisureOption?>(null) }
    var deleteCategory by remember { mutableStateOf("music") }
    val doneCount = (if (musicDone) 1 else 0) + (if (flexDone) 1 else 0)
    val allDone = doneCount == 2
    val progress = doneCount / 2f

    // Per-section stopwatch state
    var musicRunning by remember { mutableStateOf(false) }
    var musicBase by remember { mutableLongStateOf(0L) }
    var musicAccumulated by remember { mutableLongStateOf(0L) }
    var musicDisplay by remember { mutableLongStateOf(0L) }
    var musicAutoStarted by remember { mutableStateOf(false) }

    var flexRunning by remember { mutableStateOf(false) }
    var flexBase by remember { mutableLongStateOf(0L) }
    var flexAccumulated by remember { mutableLongStateOf(0L) }
    var flexDisplay by remember { mutableLongStateOf(0L) }
    var flexAutoStarted by remember { mutableStateOf(false) }

    // Auto-start music stopwatch when activity is picked
    LaunchedEffect(musicPick) {
        if (musicPick != null && !musicDone && !musicAutoStarted) {
            musicAccumulated = 0L
            musicDisplay = 0L
            musicRunning = true
            musicAutoStarted = true
        } else if (musicPick == null) {
            musicRunning = false
            musicAccumulated = 0L
            musicDisplay = 0L
            musicAutoStarted = false
        }
    }

    // Auto-start flex stopwatch when activity is picked
    LaunchedEffect(flexPick) {
        if (flexPick != null && !flexDone && !flexAutoStarted) {
            flexAccumulated = 0L
            flexDisplay = 0L
            flexRunning = true
            flexAutoStarted = true
        } else if (flexPick == null) {
            flexRunning = false
            flexAccumulated = 0L
            flexDisplay = 0L
            flexAutoStarted = false
        }
    }

    // Music stopwatch tick + auto-complete at 15 min
    LaunchedEffect(musicRunning) {
        if (musicRunning) {
            musicBase = System.currentTimeMillis()
            while (true) {
                musicDisplay = musicAccumulated + (System.currentTimeMillis() - musicBase)
                if (musicDisplay >= 15 * 60 * 1_000L && !musicDone) {
                    musicRunning = false
                    musicDisplay = 15 * 60 * 1_000L
                    musicAccumulated = musicDisplay
                    viewModel.toggleMusicDone(true)
                    break
                }
                delay(50)
            }
        }
    }

    // Flex stopwatch tick + auto-complete at 30 min
    LaunchedEffect(flexRunning) {
        if (flexRunning) {
            flexBase = System.currentTimeMillis()
            while (true) {
                flexDisplay = flexAccumulated + (System.currentTimeMillis() - flexBase)
                if (flexDisplay >= 30 * 60 * 1_000L && !flexDone) {
                    flexRunning = false
                    flexDisplay = 30 * 60 * 1_000L
                    flexAccumulated = flexDisplay
                    viewModel.toggleFlexDone(true)
                    break
                }
                delay(50)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "DAILY",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Leisure Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToday() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Progress card
            ProgressCard(doneCount = doneCount, progress = progress, allDone = allDone)

            Spacer(Modifier.height(20.dp))

            // Music Practice section
            SectionHeader(icon = "\uD83C\uDFB5", title = "Music Practice \u2014 Pick One (15 min)")
            Spacer(Modifier.height(8.dp))
            ActivitySection(
                options = instruments,
                picked = musicPick,
                done = musicDone,
                accentColor = musicColor,
                duration = "15 min",
                thresholdMs = 15 * 60 * 1_000L,
                elapsedMs = musicDisplay,
                timerRunning = musicRunning,
                columns = 3,
                onPick = { viewModel.pickMusic(it) },
                onDone = { viewModel.toggleMusicDone(true) },
                onClear = {
                    musicRunning = false
                    musicAccumulated = 0L
                    musicDisplay = 0L
                    musicAutoStarted = false
                    viewModel.clearMusicPick()
                },
                onPause = {
                    musicAccumulated += System.currentTimeMillis() - musicBase
                    musicDisplay = musicAccumulated
                    musicRunning = false
                },
                onResume = { musicRunning = true },
                onTimerReset = {
                    musicRunning = false
                    musicAccumulated = 0L
                    musicDisplay = 0L
                },
                onAdd = {
                    addDialogCategory = "music"
                    showAddDialog = true
                },
                onLongPressOption = { option ->
                    if (viewModel.isCustomActivity(option.id)) {
                        activityToDelete = option
                        deleteCategory = "music"
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            // Flexible Activity section
            SectionHeader(icon = "\uD83C\uDFB2", title = "Flexible \u2014 Pick One (30 min)")
            Spacer(Modifier.height(8.dp))
            ActivitySection(
                options = flexOptions,
                picked = flexPick,
                done = flexDone,
                accentColor = MaterialTheme.colorScheme.primary,
                duration = "30 min",
                thresholdMs = 30 * 60 * 1_000L,
                elapsedMs = flexDisplay,
                timerRunning = flexRunning,
                columns = 2,
                onPick = { viewModel.pickFlex(it) },
                onDone = { viewModel.toggleFlexDone(true) },
                onClear = {
                    flexRunning = false
                    flexAccumulated = 0L
                    flexDisplay = 0L
                    flexAutoStarted = false
                    viewModel.clearFlexPick()
                },
                onPause = {
                    flexAccumulated += System.currentTimeMillis() - flexBase
                    flexDisplay = flexAccumulated
                    flexRunning = false
                },
                onResume = { flexRunning = true },
                onTimerReset = {
                    flexRunning = false
                    flexAccumulated = 0L
                    flexDisplay = 0L
                },
                onAdd = {
                    addDialogCategory = "flex"
                    showAddDialog = true
                },
                onLongPressOption = { option ->
                    if (viewModel.isCustomActivity(option.id)) {
                        activityToDelete = option
                        deleteCategory = "flex"
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // Footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Work can wait. This can't.\nNo optimizing \u2014 just pick one and do it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Add activity dialog
    if (showAddDialog) {
        AddActivityDialog(
            category = if (addDialogCategory == "music") "Music" else "Flex",
            onDismiss = { showAddDialog = false },
            onConfirm = { label, icon ->
                if (addDialogCategory == "music") {
                    viewModel.addMusicActivity(label, icon)
                } else {
                    viewModel.addFlexActivity(label, icon)
                }
                showAddDialog = false
            }
        )
    }

    // Delete confirmation dialog
    activityToDelete?.let { option ->
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text("Remove Activity") },
            text = { Text("Remove \"${option.label}\" from the list?") },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteCategory == "music") {
                        viewModel.removeMusicActivity(option.id)
                    } else {
                        viewModel.removeFlexActivity(option.id)
                    }
                    activityToDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { activityToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddActivityDialog(
    category: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $category Activity") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Activity Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { if (it.length <= 2) icon = it },
                    label = { Text("Emoji Icon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), icon.ifBlank { "\u2B50" }) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProgressCard(doneCount: Int, progress: Float, allDone: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "progress"
    )
    val progressColor by animateColorAsState(
        targetValue = if (allDone) successColor else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "progressColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$doneCount / 2 daily minimum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            if (allDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\u2713 Leisure day complete. Nice work, Avery.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = successColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String) {
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
private fun ActivitySection(
    options: List<LeisureOption>,
    picked: String?,
    done: Boolean,
    accentColor: Color,
    duration: String,
    thresholdMs: Long,
    elapsedMs: Long,
    timerRunning: Boolean,
    columns: Int,
    onPick: (String) -> Unit,
    onDone: () -> Unit,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onTimerReset: () -> Unit,
    onAdd: () -> Unit,
    onLongPressOption: (LeisureOption) -> Unit
) {
    if (picked == null) {
        // Grid picker — options + add button
        val totalItems = options.size + 1
        val rows = (totalItems + columns - 1) / columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height((rows * 90 + (rows - 1) * 8).dp)
        ) {
            items(options, key = { it.id }) { option ->
                OptionCard(
                    option = option,
                    onClick = { onPick(option.id) },
                    onLongClick = { onLongPressOption(option) }
                )
            }
            item(key = "_add") {
                AddOptionCard(onClick = onAdd)
            }
        }
    } else {
        val selected = options.find { it.id == picked }!!
        // Selected item with checkbox
        SelectedItem(
            option = selected,
            done = done,
            accentColor = accentColor,
            duration = duration,
            onDone = onDone
        )

        if (!done) {
            Spacer(Modifier.height(8.dp))
            // Inline timer
            SectionTimer(
                elapsedMs = elapsedMs,
                thresholdMs = thresholdMs,
                running = timerRunning,
                accentColor = accentColor,
                onPause = onPause,
                onResume = onResume,
                onReset = onTimerReset
            )
            TextButton(onClick = onClear) {
                Text(
                    "\u2190 Pick something else",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OptionCard(option: LeisureOption, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(option.icon, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                option.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AddOptionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add activity",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Add",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectedItem(
    option: LeisureOption,
    done: Boolean,
    accentColor: Color,
    duration: String,
    onDone: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (done) accentColor.copy(alpha = 0.27f) else MaterialTheme.colorScheme.outline,
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (done) accentColor.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDone)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (done) accentColor else Color.Transparent)
                    .border(
                        2.dp,
                        if (done) accentColor else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${option.icon} ${option.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!done) {
                    Text(
                        "Tap when done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTimer(
    elapsedMs: Long,
    thresholdMs: Long,
    running: Boolean,
    accentColor: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val elapsedMin = ((elapsedMs % 3_600_000) / 60_000).toInt()
    val elapsedSec = ((elapsedMs % 60_000) / 1_000).toInt()
    val thresholdMin = (thresholdMs / 60_000).toInt()
    val timerProgress = (elapsedMs.toFloat() / thresholdMs).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = timerProgress,
        animationSpec = tween(200),
        label = "timerProgress"
    )

    val timeText = "${elapsedMin.toString().padStart(2, '0')}:${elapsedSec.toString().padStart(2, '0')} / ${thresholdMin}:00"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                timeText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.2f)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = elapsedMs > 0,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                if (running) {
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Pause", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Resume", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
