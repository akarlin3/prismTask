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
import androidx.compose.material.icons.filled.Settings
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
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.leisure.components.ActivitySection
import com.averycorp.prismtask.ui.screens.leisure.components.AddActivityDialog
import com.averycorp.prismtask.ui.screens.leisure.components.LeisureOption
import com.averycorp.prismtask.ui.screens.leisure.components.ProgressCard
import com.averycorp.prismtask.ui.screens.leisure.components.SectionHeader
import kotlinx.coroutines.delay

private val musicColor = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeisureScreen(
    navController: NavController,
    viewModel: LeisureViewModel = hiltViewModel()
) {
    val musicState by viewModel.musicSlot.collectAsStateWithLifecycle()
    val flexState by viewModel.flexSlot.collectAsStateWithLifecycle()
    val customStates by viewModel.customSlots.collectAsStateWithLifecycle()

    // Active slots in display order. Disabled slots are hidden entirely.
    val slots = (listOf(musicState, flexState) + customStates).filter { it.config.enabled }
    val doneCount = slots.count { it.done }
    val targetCount = slots.size
    val allDone = targetCount > 0 && doneCount == targetCount
    val progress = if (targetCount == 0) 0f else doneCount / targetCount.toFloat()

    var showAddDialog by remember { mutableStateOf<LeisureSectionKey?>(null) }
    var activityToDelete by remember { mutableStateOf<Pair<LeisureSectionKey, LeisureOption>?>(null) }

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
                    IconButton(onClick = { navController.navigate(PrismTaskRoute.LeisureSettings.route) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Customize Leisure",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            if (targetCount == 0) {
                AllSlotsDisabledCard(
                    onOpenSettings = { navController.navigate(PrismTaskRoute.LeisureSettings.route) }
                )
            } else {
                ProgressCard(
                    doneCount = doneCount,
                    target = targetCount,
                    progress = progress,
                    allDone = allDone
                )
                Spacer(Modifier.height(20.dp))

                slots.forEachIndexed { index, state ->
                    SlotBlock(
                        state = state,
                        accentColor = if (state.builtInSlot == LeisureSlotId.MUSIC) musicColor else MaterialTheme.colorScheme.primary,
                        onPick = { viewModel.pickActivity(state.key, it) },
                        onToggleDone = { viewModel.toggleDone(state.key, true) },
                        onClearPick = { viewModel.clearPick(state.key) },
                        onRequestAdd = { showAddDialog = state.key },
                        onRequestDelete = { opt -> activityToDelete = state.key to opt },
                        isCustom = { viewModel.isCustomActivity(it) }
                    )
                    if (index != slots.lastIndex) Spacer(Modifier.height(20.dp))
                }

                Spacer(Modifier.height(24.dp))

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
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    showAddDialog?.let { key ->
        val categoryLabel = slots.firstOrNull { it.key == key }?.config?.label
            ?: when (key) {
                is LeisureSectionKey.BuiltIn -> when (key.slot) {
                    LeisureSlotId.MUSIC -> musicState.config.label
                    LeisureSlotId.FLEX -> flexState.config.label
                }
                is LeisureSectionKey.Custom -> "Section"
            }
        AddActivityDialog(
            category = categoryLabel,
            onDismiss = { showAddDialog = null },
            onConfirm = { label, icon ->
                viewModel.addActivity(key, label, icon)
                showAddDialog = null
            }
        )
    }

    activityToDelete?.let { (key, option) ->
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text("Remove Activity") },
            text = { Text("Remove \"${option.label}\" from the list?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeActivity(key, option.id)
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
private fun SlotBlock(
    state: LeisureSlotState,
    accentColor: Color,
    onPick: (String) -> Unit,
    onToggleDone: () -> Unit,
    onClearPick: () -> Unit,
    onRequestAdd: () -> Unit,
    onRequestDelete: (LeisureOption) -> Unit,
    isCustom: (String) -> Boolean
) {
    val durationMs = state.config.durationMinutes.toLong() * 60_000L
    val durationLabel = "${state.config.durationMinutes} min"
    val autoComplete = state.config.autoComplete

    // Per-slot stopwatch state, rekeyed when the duration config changes so the
    // threshold effect picks up the new value.
    var running by remember(state.key) { mutableStateOf(false) }
    var base by remember(state.key) { mutableLongStateOf(0L) }
    var accumulated by remember(state.key) { mutableLongStateOf(0L) }
    var display by remember(state.key) { mutableLongStateOf(0L) }
    var autoStarted by remember(state.key) { mutableStateOf(false) }

    LaunchedEffect(state.picked, state.done) {
        if (state.picked != null && !state.done && !autoStarted) {
            accumulated = 0L
            display = 0L
            running = true
            autoStarted = true
        } else if (state.picked == null) {
            running = false
            accumulated = 0L
            display = 0L
            autoStarted = false
        }
    }

    LaunchedEffect(running, durationMs, autoComplete) {
        if (running) {
            base = System.currentTimeMillis()
            while (true) {
                display = accumulated + (System.currentTimeMillis() - base)
                if (autoComplete && display >= durationMs && !state.done) {
                    running = false
                    display = durationMs
                    accumulated = display
                    onToggleDone()
                    break
                }
                delay(50)
            }
        }
    }

    SectionHeader(
        icon = state.config.emoji,
        title = "${state.config.label} \u2014 Pick One ($durationLabel)"
    )
    Spacer(Modifier.height(8.dp))
    ActivitySection(
        options = state.options,
        picked = state.picked,
        done = state.done,
        accentColor = accentColor,
        duration = durationLabel,
        thresholdMs = durationMs,
        elapsedMs = display,
        timerRunning = running,
        columns = state.config.gridColumns,
        onPick = onPick,
        onDone = onToggleDone,
        onClear = {
            running = false
            accumulated = 0L
            display = 0L
            autoStarted = false
            onClearPick()
        },
        onPause = {
            accumulated += System.currentTimeMillis() - base
            display = accumulated
            running = false
        },
        onResume = { running = true },
        onTimerReset = {
            running = false
            accumulated = 0L
            display = 0L
        },
        onAdd = onRequestAdd,
        onLongPressOption = { option ->
            if (isCustom(option.id)) onRequestDelete(option)
        }
    )
}

@Composable
private fun AllSlotsDisabledCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "No leisure sections enabled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Turn at least one section on in customization to start tracking leisure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Open Customization")
            }
        }
    }
}
