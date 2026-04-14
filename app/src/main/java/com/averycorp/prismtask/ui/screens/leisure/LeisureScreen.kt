package com.averycorp.prismtask.ui.screens.leisure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.leisure.components.ActivitySection
import com.averycorp.prismtask.ui.screens.leisure.components.AddActivityDialog
import com.averycorp.prismtask.ui.screens.leisure.components.LeisureOption
import com.averycorp.prismtask.ui.screens.leisure.components.ProgressCard
import com.averycorp.prismtask.ui.screens.leisure.components.SectionHeader
import kotlinx.coroutines.delay

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
