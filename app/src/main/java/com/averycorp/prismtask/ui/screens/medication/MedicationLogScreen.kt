package com.averycorp.prismtask.ui.screens.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.averycorp.prismtask.ui.theme.LocalPrismShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.SelfCareLogEntity
import com.averycorp.prismtask.data.local.entity.SelfCareStepEntity
import com.averycorp.prismtask.data.repository.MedStepLog
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogScreen(
    navController: NavController,
    viewModel: MedicationLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication Log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\uD83D\uDC8A",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Medication History Yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Logged medications will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(logs, key = { it.id }) { log ->
                    LogDayCard(
                        log = log,
                        steps = steps,
                        medStepLogs = viewModel.parseMedStepLogs(log),
                        tiersByTime = viewModel.parseTiersByTime(log)
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LogDayCard(
    log: SelfCareLogEntity,
    steps: List<SelfCareStepEntity>,
    medStepLogs: List<MedStepLog>,
    tiersByTime: Map<String, String>
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val dateLabel = dateFormat.format(Date(log.date))
    val tiers = SelfCareRoutines.medicationTiers

    // Group steps by time-of-day for this log.
    val timeGroups = SelfCareRoutines.timesOfDay.mapNotNull { tod ->
        val stepsInTime = steps.filter { step ->
            tod.id in SelfCareRoutines.parseTimeOfDay(step.timeOfDay)
        }
        if (stepsInTime.isNotEmpty()) tod to stepsInTime else null
    }
    // Ungrouped / legacy steps without a recognized time-of-day.
    val allGroupedStepIds = timeGroups.flatMap { (_, s) -> s.map { it.id } }.toSet()
    val ungrouped = steps.filter { it.id !in allGroupedStepIds }

    val loggedCount = medStepLogs.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (log.isComplete) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(LocalPrismColors.current.successColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$loggedCount logged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        timeGroups.forEach { (tod, stepsInGroup) ->
            Spacer(modifier = Modifier.height(10.dp))
            val todColor = Color(tod.color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = tod.icon, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tod.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = todColor,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f
                )
                val pickedTier = tiersByTime[tod.id]
                if (pickedTier != null) {
                    val tierInfo = tiers.find { it.id == pickedTier }
                    val tierC = tierInfo?.let { Color(it.color) } ?: todColor
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(LocalPrismShapes.current.chip)
                            .background(tierC.copy(alpha = 0.15f))
                            .border(1.dp, tierC.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tierInfo?.label ?: pickedTier,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tierC
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            stepsInGroup.forEach { step ->
                val entry = medStepLogs.firstOrNull {
                    it.id == step.stepId && (it.timeOfDay == tod.id || it.timeOfDay.isBlank())
                }
                StepRow(label = step.label, entry = entry)
            }
        }

        if (ungrouped.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "OTHER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f
            )
            Spacer(modifier = Modifier.height(4.dp))
            ungrouped.forEach { step ->
                val entry = medStepLogs.firstOrNull { it.id == step.stepId }
                StepRow(label = step.label, entry = entry)
            }
        }
    }
}

@Composable
private fun StepRow(label: String, entry: MedStepLog?) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val logged = entry != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val successColor = LocalPrismColors.current.successColor
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    if (logged) {
                        successColor
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ).border(
                    1.dp,
                    if (logged) {
                        successColor
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (logged) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (logged) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        if (entry != null && entry.at > 0L) {
            Text(
                text = timeFormat.format(Date(entry.at)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (entry != null && entry.note.isNotBlank()) {
        Text(
            text = entry.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, bottom = 2.dp)
        )
    }
}
