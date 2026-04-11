package com.averycorp.prismtask.ui.screens.checkin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.domain.usecase.CheckInStep
import kotlinx.coroutines.launch

/**
 * Morning Check-In screen (v1.4.0 V4).
 *
 * Guided horizontal pager: mood/energy → top tasks → habits → balance →
 * calendar → confirmation. Each step is its own small composable below.
 * Users can swipe or tap "Next" to advance; the final page persists a
 * CheckInLog row via the ViewModel so the Today screen stops prompting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningCheckInScreen(
    navController: NavController,
    viewModel: MorningCheckInViewModel = hiltViewModel()
) {
    val planState by viewModel.plan.collectAsStateWithLifecycle()
    val completedSteps by viewModel.completedSteps.collectAsStateWithLifecycle()
    val isFinished by viewModel.isFinished.collectAsStateWithLifecycle()

    LaunchedEffect(isFinished) {
        if (isFinished) navController.popBackStack()
    }

    // Add an implicit final "Ready To Go" page.
    val steps = planState.steps
    val pageCount = steps.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning Check-In", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StepIndicator(
                current = pagerState.currentPage,
                total = pageCount,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page < steps.size) {
                    StepContent(
                        step = steps[page],
                        viewModel = viewModel,
                        planState = planState,
                        completedSteps = completedSteps
                    )
                } else {
                    FinalReadyPage(onDone = { viewModel.finalize() })
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) { Text("Back") }
                Button(onClick = {
                    if (pagerState.currentPage < pageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        viewModel.finalize()
                    }
                }) {
                    Text(if (pagerState.currentPage < pageCount - 1) "Next" else "Done")
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until total) {
            val color = if (i <= current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHighest
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun StepContent(
    step: CheckInStep,
    viewModel: MorningCheckInViewModel,
    planState: CheckInScreenState,
    completedSteps: Set<CheckInStep>
) {
    when (step) {
        CheckInStep.MOOD_ENERGY -> MoodEnergyStep(
            onSubmit = { mood, energy, notes -> viewModel.logMoodEnergy(mood, energy, notes) }
        )
        CheckInStep.MEDICATIONS -> SimpleMessageStep(
            emoji = "\uD83D\uDC8A",
            title = "Medications",
            body = "Confirm your morning medications in the Medication tab before continuing.",
            actionLabel = "Mark Confirmed",
            onAction = { viewModel.markStepComplete(step) }
        )
        CheckInStep.TOP_TASKS -> TopTasksStep(
            tasks = planState.topTasks,
            onDone = { viewModel.markStepComplete(step) }
        )
        CheckInStep.HABITS -> SimpleMessageStep(
            emoji = "\uD83D\uDCAA",
            title = "Habits",
            body = "You have ${planState.habits.size} habit(s) for today. Open the Habits tab to check them off.",
            actionLabel = "Got It",
            onAction = { viewModel.markStepComplete(step) }
        )
        CheckInStep.BALANCE -> SimpleMessageStep(
            emoji = "\u2696\uFE0F",
            title = "Balance Check",
            body = "A quick reminder that your week's shape matters. Open the Today screen to see your balance bar and burnout score.",
            actionLabel = "Got It",
            onAction = { viewModel.markStepComplete(step) }
        )
        CheckInStep.CALENDAR -> SimpleMessageStep(
            emoji = "\uD83D\uDCC5",
            title = "Calendar Glance",
            body = "Your calendar shows your upcoming events. Open Weekly Planner for a detailed view.",
            actionLabel = "Got It",
            onAction = { viewModel.markStepComplete(step) }
        )
    }
}

@Composable
private fun MoodEnergyStep(onSubmit: (mood: Int, energy: Int, notes: String?) -> Unit) {
    var mood by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("How are you feeling?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Mood", style = MaterialTheme.typography.bodyMedium)
        EmojiRow(
            emojis = listOf("\uD83D\uDE22", "\uD83D\uDE15", "\uD83D\uDE10", "\uD83D\uDE42", "\uD83D\uDE0A"),
            selected = mood - 1,
            onSelect = { mood = it + 1 }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Energy", style = MaterialTheme.typography.bodyMedium)
        EmojiRow(
            emojis = listOf("\uD83D\uDD0B", "\uD83D\uDD0B", "\uD83D\uDD0B", "\uD83D\uDD0B", "\u26A1"),
            selected = energy - 1,
            onSelect = { energy = it + 1 }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Anything affecting your day?") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSubmit(mood, energy, notes.takeIf { it.isNotBlank() }) }) {
            Text("Save & Continue")
        }
    }
}

@Composable
private fun EmojiRow(emojis: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        emojis.forEachIndexed { index, emoji ->
            val bg = if (index == selected) MaterialTheme.colorScheme.primaryContainer
            else Color.Transparent
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { onSelect(index) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(emoji, fontSize = 28.sp)
                }
            }
        }
    }
}

@Composable
private fun TopTasksStep(
    tasks: List<com.averycorp.prismtask.data.local.entity.TaskEntity>,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Top Tasks",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Text(
                "Clear day — focus on self-care.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tasks.forEach { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "P${task.priority}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onDone) { Text("I've Reviewed These") }
    }
}

@Composable
private fun SimpleMessageStep(
    emoji: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAction) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun FinalReadyPage(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u2728", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Ready To Go!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "You've checked in with yourself. That's already a win.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone) { Text("Start My Day") }
    }
}

