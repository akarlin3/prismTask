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
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogScreen(
    navController: NavController,
    viewModel: MedicationLogViewModel = hiltViewModel()
) {
    val days by viewModel.days.collectAsStateWithLifecycle()

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
        if (days.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "💊",
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
                items(days, key = { it.date }) { day -> LogDayCard(day) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LogDayCard(day: MedicationLogDay) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val dateLabel = dateFormat.format(Date(isoDateToMillis(day.date)))

    // Known TOD sections render in the standard order. "anytime" goes
    // into an OTHER bucket; any slot_key that's a "HH:mm" goes into a
    // TIMED bucket so specific-time schedules show up distinctly from
    // time-of-day-bucket schedules.
    val dosesBySlot = day.dosesBySlot
    val todEntries = SelfCareRoutines.timesOfDay.mapNotNull { tod ->
        val dosesForTod = dosesBySlot[tod.id] ?: return@mapNotNull null
        tod to dosesForTod
    }
    val timedDoses = dosesBySlot.entries
        .filter { it.key !in KNOWN_SLOT_KEYS && it.key.matches(CLOCK_REGEX) }
        .sortedBy { it.key }
    val anytimeDoses = dosesBySlot["anytime"].orEmpty()

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
            // No aggregate "complete" concept in the new entity model;
            // the dose count communicates activity adequately.
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${day.loggedCount} logged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        todEntries.forEach { (tod, dosesForTod) ->
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(label = tod.label.uppercase(), icon = tod.icon, color = Color(tod.color))
            Spacer(modifier = Modifier.height(4.dp))
            dosesForTod.forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose)
            }
        }

        timedDoses.forEach { (clock, dosesForClock) ->
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(label = clock, icon = "⏰", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            dosesForClock.forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose)
            }
        }

        if (anytimeDoses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(
                label = "ANYTIME",
                icon = null,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            anytimeDoses.forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose)
            }
        }
    }
}

@Composable
private fun SlotHeader(label: String, icon: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Text(text = icon, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f
        )
    }
}

@Composable
private fun DoseRow(label: String, dose: MedicationDoseEntity) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val successColor = LocalPrismColors.current.successColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(successColor)
                .border(1.dp, successColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (dose.takenAt > 0L) {
            Text(
                text = timeFormat.format(Date(dose.takenAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (dose.note.isNotBlank()) {
        Text(
            text = dose.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, bottom = 2.dp)
        )
    }
}

private val KNOWN_SLOT_KEYS = setOf("morning", "afternoon", "evening", "night", "anytime")
private val CLOCK_REGEX = Regex("""\d{2}:\d{2}""")

private fun isoDateToMillis(iso: String): Long = try {
    LocalDate.parse(iso)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
} catch (_: Exception) {
    System.currentTimeMillis()
}
