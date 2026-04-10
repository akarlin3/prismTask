package com.averycorp.averytask.ui.screens.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    navController: NavController,
    viewModel: TimerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        TimerContent(
            padding = padding,
            uiState = uiState,
            onToggleStartPause = viewModel::toggleStartPause,
            onReset = viewModel::reset,
            onSetMode = viewModel::setMode,
            onSkipToNext = viewModel::skipToNext,
            onResetPomodoro = viewModel::resetPomodoro,
            onTogglePomodoroEnabled = viewModel::togglePomodoroEnabled,
            onToggleAutoStartBreaks = viewModel::toggleAutoStartBreaks,
            onToggleAutoStartWork = viewModel::toggleAutoStartWork
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerContent(
    padding: PaddingValues,
    uiState: TimerUiState,
    onToggleStartPause: () -> Unit,
    onReset: () -> Unit,
    onSetMode: (TimerMode) -> Unit,
    onSkipToNext: () -> Unit,
    onResetPomodoro: () -> Unit,
    onTogglePomodoroEnabled: () -> Unit,
    onToggleAutoStartBreaks: () -> Unit,
    onToggleAutoStartWork: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val breakAccent = MaterialTheme.colorScheme.tertiary
    val activeColor = if (uiState.mode == TimerMode.WORK) accent else breakAccent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Mode switch (only when Pomodoro is off — Pomodoro auto-manages modes)
        if (!uiState.pomodoroEnabled) {
            ModeSelector(uiState = uiState, onSetMode = onSetMode)
        }

        // Session indicator dots (Pomodoro mode)
        if (uiState.pomodoroEnabled) {
            PomodoroSessionIndicator(
                completedSessions = uiState.completedSessions,
                sessionsUntilLongBreak = uiState.sessionsUntilLongBreak,
                activeColor = accent
            )
        }

        // Mode label above ring (Pomodoro mode)
        if (uiState.pomodoroEnabled) {
            val label = when {
                uiState.mode == TimerMode.WORK -> "Focus Session ${(uiState.completedSessions % uiState.sessionsUntilLongBreak) + 1}"
                uiState.isLongBreak -> "Long Break"
                else -> "Short Break"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = activeColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Timer ring display
        val modeLabel = when {
            !uiState.pomodoroEnabled && uiState.mode == TimerMode.WORK -> "Focus"
            !uiState.pomodoroEnabled -> "Break"
            uiState.mode == TimerMode.WORK -> "Focus"
            uiState.isLongBreak -> "Long Break"
            else -> "Break"
        }
        TimerRing(
            remainingSeconds = uiState.remainingSeconds,
            totalSeconds = uiState.totalSeconds,
            activeColor = activeColor,
            modeLabel = modeLabel
        )

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = if (uiState.pomodoroEnabled) onResetPomodoro else onReset,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset"
                )
            }

            Button(
                onClick = onToggleStartPause,
                modifier = Modifier
                    .height(64.dp)
                    .width(160.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isRunning) "Pause" else "Start",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isRunning) "Pause" else "Start",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (uiState.pomodoroEnabled) {
                FilledIconButton(
                    onClick = onSkipToNext,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip To Next"
                    )
                }
            }
        }

        // Completed sessions count
        if (uiState.pomodoroEnabled && uiState.completedSessions > 0) {
            Text(
                text = "${uiState.completedSessions} ${if (uiState.completedSessions == 1) "Session" else "Sessions"} Completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()

        // Pomodoro settings section
        PomodoroSettings(
            uiState = uiState,
            onTogglePomodoroEnabled = onTogglePomodoroEnabled,
            onToggleAutoStartBreaks = onToggleAutoStartBreaks,
            onToggleAutoStartWork = onToggleAutoStartWork
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    uiState: TimerUiState,
    onSetMode: (TimerMode) -> Unit
) {
    val options = listOf(TimerMode.WORK to "Work", TimerMode.BREAK to "Break")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = uiState.mode == mode,
                onClick = { onSetMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                )
            ) { Text(label) }
        }
    }
}

@Composable
private fun PomodoroSessionIndicator(
    completedSessions: Int,
    sessionsUntilLongBreak: Int,
    activeColor: Color
) {
    val currentInCycle = completedSessions % sessionsUntilLongBreak
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until sessionsUntilLongBreak) {
            val isCompleted = i < currentInCycle
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = if (isCompleted) activeColor else MaterialTheme.colorScheme.surfaceVariant
            ) {}
        }
    }
}

@Composable
private fun PomodoroSettings(
    uiState: TimerUiState,
    onTogglePomodoroEnabled: () -> Unit,
    onToggleAutoStartBreaks: () -> Unit,
    onToggleAutoStartWork: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Pomodoro",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SettingsToggleRow(
            label = "Pomodoro Mode",
            description = "Auto-cycle: work, short break, long break",
            checked = uiState.pomodoroEnabled,
            onToggle = onTogglePomodoroEnabled
        )

        if (uiState.pomodoroEnabled) {
            SettingsToggleRow(
                label = "Auto-Start Breaks",
                description = "Start break timer automatically after focus",
                checked = uiState.autoStartBreaks,
                onToggle = onToggleAutoStartBreaks
            )

            SettingsToggleRow(
                label = "Auto-Start Focus",
                description = "Start focus timer automatically after break",
                checked = uiState.autoStartWork,
                onToggle = onToggleAutoStartWork
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun TimerRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    activeColor: Color,
    modeLabel: String
) {
    val progress = if (totalSeconds > 0) {
        remainingSeconds.toFloat() / totalSeconds.toFloat()
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "timer_progress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .size(260.dp)
            .clip(RoundedCornerShape(130.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
