package com.averycorp.prismtask.ui.screens.today.dailyessentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.usecase.DailyEssentialsUiState
import com.averycorp.prismtask.domain.usecase.MedicationDose
import com.averycorp.prismtask.domain.usecase.MedicationSlot
import com.averycorp.prismtask.ui.screens.today.components.CollapsibleSection
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.HabitCard
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.LeisureCard
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.MedicationCard
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.MedicationSlotBottomSheet
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.RoutineCard
import com.averycorp.prismtask.ui.screens.today.dailyessentials.cards.SchoolworkCard
import com.averycorp.prismtask.ui.theme.LocalPrismColors

data class DailyEssentialsActions(
    val onToggleRoutineStep: (routineType: String, stepId: String) -> Unit,
    val onToggleHousework: () -> Unit,
    val onToggleSchoolworkHabit: () -> Unit,
    val onOpenAssignment: (assignmentId: Long) -> Unit,
    val onPickMusic: () -> Unit,
    val onToggleMusicDone: () -> Unit,
    val onPickFlex: () -> Unit,
    val onToggleFlexDone: () -> Unit,
    /**
     * Batch-mark every dose in a medication slot. Second arg is the new
     * "taken" state (checked ↔ unchecked). Implementations typically call
     * ``DailyEssentialSlotCompletionRepository.toggleSlot`` and fan out to
     * each dose's native completion log.
     */
    val onToggleMedicationSlot: (MedicationSlot, Boolean) -> Unit,
    /** Single-dose toggle from inside the detail bottom sheet. */
    val onToggleMedicationDose: (MedicationSlot, MedicationDose, Boolean) -> Unit,
    val onDismissHint: () -> Unit,
    val onOpenSettings: () -> Unit
)

/**
 * Collapsible section wrapper for the seven virtual Daily Essentials cards.
 * Cards render in a fixed order: Morning → Medication → Housework →
 * Schoolwork → Music → Flex → Bedtime. Empty cards are omitted entirely;
 * if the whole section is empty, a one-time onboarding banner links to
 * Settings → Daily Essentials.
 */
@Composable
fun DailyEssentialsSection(
    state: DailyEssentialsUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: DailyEssentialsActions,
    modifier: Modifier = Modifier
) {
    val prismColors = LocalPrismColors.current
    val visibleCardCount = listOfNotNull(
        state.morning,
        state.medication,
        state.housework,
        state.houseworkRoutine,
        state.schoolwork?.takeIf { it.hasContent },
        state.musicLeisure.pickedForToday?.let { state.musicLeisure },
        state.flexLeisure.pickedForToday?.let { state.flexLeisure },
        state.bedtime
    ).size

    if (state.isEmpty && state.hasSeenHint) return

    var activeSlot by remember { mutableStateOf<MedicationSlot?>(null) }
    activeSlot?.let { slot ->
        MedicationSlotBottomSheet(
            slot = slot,
            onDismiss = { activeSlot = null },
            onToggleDose = { dose, checked ->
                actions.onToggleMedicationDose(slot, dose, checked)
            }
        )
    }

    CollapsibleSection(
        emoji = "\u2728",
        title = "Daily Essentials",
        count = visibleCardCount,
        accentColor = prismColors.primary,
        expanded = expanded,
        onToggle = onToggleExpanded
    ) {
        if (state.isEmpty) {
            EmptyStateHint(
                onOpenSettings = actions.onOpenSettings,
                onDismiss = actions.onDismissHint,
                modifier = modifier
            )
            return@CollapsibleSection
        }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.morning?.let { morning ->
                RoutineCard(
                    state = morning,
                    onToggleStep = { stepId -> actions.onToggleRoutineStep("morning", stepId) }
                )
            }
            state.medication?.let { medication ->
                MedicationCard(
                    state = medication,
                    onToggleSlot = actions.onToggleMedicationSlot,
                    onOpenSlot = { slot -> activeSlot = slot }
                )
            }
            state.housework?.let { housework ->
                HabitCard(
                    state = housework,
                    contentDescriptionPrefix = "Housework",
                    onToggle = actions.onToggleHousework
                )
            }
            state.houseworkRoutine?.let { houseworkRoutine ->
                RoutineCard(
                    state = houseworkRoutine,
                    onToggleStep = { stepId -> actions.onToggleRoutineStep("housework", stepId) }
                )
            }
            state.schoolwork?.takeIf { it.hasContent }?.let { schoolwork ->
                SchoolworkCard(
                    state = schoolwork,
                    onToggleHabit = actions.onToggleSchoolworkHabit,
                    onOpenAssignment = actions.onOpenAssignment
                )
            }
            state.musicLeisure.takeIf { it.pickedForToday != null }?.let { music ->
                LeisureCard(
                    state = music,
                    onTapBody = actions.onPickMusic,
                    onToggleDone = actions.onToggleMusicDone
                )
            }
            state.flexLeisure.takeIf { it.pickedForToday != null }?.let { flex ->
                LeisureCard(
                    state = flex,
                    onTapBody = actions.onPickFlex,
                    onToggleDone = actions.onToggleFlexDone
                )
            }
            state.bedtime?.let { bedtime ->
                RoutineCard(
                    state = bedtime,
                    onToggleStep = { stepId -> actions.onToggleRoutineStep("bedtime", stepId) }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateHint(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Set Up Your Daily Essentials",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Pick the habits and routines you want to see every morning " +
                    "— medication, housework, leisure, and more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Not Now") }
                TextButton(onClick = onOpenSettings) { Text("Set Up") }
            }
        }
    }
}
