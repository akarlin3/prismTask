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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.MedicationDoseEntity
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.domain.model.medication.AchievedTier
import com.averycorp.prismtask.ui.screens.medication.components.LogCustomDoseSheet
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.time.Instant
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
    var showCustomDoseSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medication Log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomDoseSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Log Custom Dose")
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

    if (showCustomDoseSheet) {
        LogCustomDoseSheet(
            onDismiss = { showCustomDoseSheet = false },
            onLog = { name, takenAt, note ->
                viewModel.logCustomDose(name = name, takenAtMillis = takenAt, note = note)
                showCustomDoseSheet = false
            }
        )
    }
}

@Composable
private fun LogDayCard(day: MedicationLogDay) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val dateLabel = dateFormat.format(Date(isoDateToMillis(day.date)))

    // Primary path: doses whose `slotKey` parses to a known slot id render
    // under the slot's actual display name (e.g. "MORNING · 09:00"),
    // ordered by `slot.idealTime`. The medication screen writes
    // `dose.slotKey = slot.id.toString()` for every tap (per the bulk-mark
    // fix), so the log used to silently drop these because the legacy
    // bucketing only recognised TOD strings ("morning", "afternoon", …).
    val dosesByResolvedSlot = day.dosesByResolvedSlot
    val tierEntriesBySlotId = day.slotEntries.associateBy { it.slot.id }

    val activeSlots = (dosesByResolvedSlot.keys.map { it.id } + tierEntriesBySlotId.keys)
        .distinct()
        .mapNotNull { day.slotsById[it] }
        .sortedBy { it.idealTime }

    // Fallback: anything whose slot key didn't resolve through `slotsById`
    // — pre-migration TOD strings ("morning"/"afternoon"/"evening"/
    // "night"/"anytime"), "HH:MM" clock strings, and orphaned numeric ids
    // whose slot has since been deleted.
    val legacyDosesBySlot = day.legacyDosesBySlot
    val legacyTodEntries = SelfCareRoutines.timesOfDay.mapNotNull { tod ->
        val dosesForTod = legacyDosesBySlot[tod.id] ?: return@mapNotNull null
        tod to dosesForTod
    }
    val legacyTimedDoses = legacyDosesBySlot.entries
        .filter { it.key !in KNOWN_SLOT_KEYS && it.key.matches(CLOCK_REGEX) }
        .sortedBy { it.key }
    val anytimeDoses = legacyDosesBySlot["anytime"].orEmpty()
    val orphanedNumericDoses = legacyDosesBySlot.entries
        .filter { entry -> entry.key.toLongOrNull() != null && entry.key !in KNOWN_SLOT_KEYS }
        .flatMap { it.value }

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
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${day.loggedCount} logged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // A slot has either real doses OR a tier-state-only entry (the
        // viewmodel suppresses the tier-entry when any dose covers the
        // slot), so the two branches are mutually exclusive.
        val groupedDate = parseIsoDateOrNull(day.date)

        activeSlots.forEach { slot ->
            val slotDoses = dosesByResolvedSlot[slot]?.sortedBy { it.takenAt }
            val tierEntry = tierEntriesBySlotId[slot.id]
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(
                label = "${slot.name.uppercase()} · ${slot.idealTime}",
                icon = "⏰",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (!slotDoses.isNullOrEmpty()) {
                slotDoses.forEach { dose ->
                    DoseRow(label = day.medicationName(dose), dose = dose, groupedDate = groupedDate)
                }
            } else if (tierEntry != null) {
                SlotTierEntryRow(entry = tierEntry, groupedDate = groupedDate)
            }
        }

        legacyTodEntries.forEach { (tod, dosesForTod) ->
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(label = tod.label.uppercase(), icon = tod.icon, color = Color(tod.color))
            Spacer(modifier = Modifier.height(4.dp))
            dosesForTod.forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose, groupedDate = groupedDate)
            }
        }

        legacyTimedDoses.forEach { (clock, dosesForClock) ->
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(label = clock, icon = "⏰", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            dosesForClock.forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose, groupedDate = groupedDate)
            }
        }

        if (anytimeDoses.isNotEmpty() || orphanedNumericDoses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SlotHeader(
                label = "ANYTIME",
                icon = null,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            (anytimeDoses + orphanedNumericDoses).forEach { dose ->
                DoseRow(label = day.medicationName(dose), dose = dose, groupedDate = groupedDate)
            }
        }

        // Tier-entries for slots that aren't in slotsById (slot deleted /
        // archived after the user logged it). The active-slots loop above
        // can't reach them because their slot isn't resolvable, but the
        // history is still worth surfacing.
        val orphanedTierEntries = day.slotEntries
            .filter { it.slot.id !in day.slotsById }
        if (orphanedTierEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            orphanedTierEntries.forEach { entry ->
                SlotHeader(
                    label = entry.slot.name.uppercase(),
                    icon = "⏰",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                SlotTierEntryRow(entry = entry, groupedDate = groupedDate)
                Spacer(modifier = Modifier.height(6.dp))
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
private fun DoseRow(label: String, dose: MedicationDoseEntity, groupedDate: LocalDate?) {
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
            // Promoted from labelSmall/onSurfaceVariant → bodySmall/primary
            // so the "when was it taken" answer reads as primary metadata
            // rather than muted secondary info.
            Text(
                text = formatTimeWithDateIfDifferent(dose.takenAt, groupedDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
    if (!dose.doseAmount.isNullOrBlank()) {
        Text(
            text = "Dose: ${dose.doseAmount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 22.dp, bottom = 2.dp)
        )
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

@Composable
private fun SlotTierEntryRow(entry: SlotTierEntry, groupedDate: LocalDate?) {
    val displayTime = entry.displayTime
    val tierLabel = when (entry.tier) {
        AchievedTier.SKIPPED -> "Skipped"
        AchievedTier.ESSENTIAL -> "Essential"
        AchievedTier.PRESCRIPTION -> "Prescription"
        AchievedTier.COMPLETE -> "Complete"
    }
    val accent = when (entry.tier) {
        AchievedTier.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        AchievedTier.ESSENTIAL -> LocalPrismColors.current.destructiveColor
        AchievedTier.PRESCRIPTION -> LocalPrismColors.current.infoColor
        AchievedTier.COMPLETE -> LocalPrismColors.current.successColor
    }
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
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Slot logged: $tierLabel",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (displayTime != null) {
            Text(
                text = formatTimeWithDateIfDifferent(displayTime, groupedDate),
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
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

private fun parseIsoDateOrNull(iso: String): LocalDate? = try {
    LocalDate.parse(iso)
} catch (_: Exception) {
    null
}

/**
 * Formats [timestamp] as `h:mm a`, prefixed with the calendar date when the
 * timestamp's wall-clock date differs from [groupedDate] (the SoD-grouped
 * day card heading). Surfaces the gap created when the user's Start-of-Day
 * pushes a late-night/early-morning dose onto the previous day's card.
 */
internal fun formatTimeWithDateIfDifferent(timestamp: Long, groupedDate: LocalDate?): String {
    val zone = ZoneId.systemDefault()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeLabel = timeFormat.format(Date(timestamp))
    val actualDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    if (groupedDate == null || actualDate == groupedDate) return timeLabel
    val datePattern = if (actualDate.year == groupedDate.year) "MMM d" else "MMM d, yyyy"
    val dateLabel = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(timestamp))
    return "$dateLabel · $timeLabel"
}
