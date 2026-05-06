package com.averycorp.prismtask.ui.screens.medication.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.entity.MedicationSlotEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the [MedicationEditorDialog] Save-button gate
 * shipped in PR #1141. The post-fix gate is
 *
 *   `name.isNotBlank() && (selections.isNotEmpty() || activeSlots.isEmpty())`
 *
 * with the empty-`activeSlots` escape hatch preserving the bootstrap UX
 * (a fresh user with zero slots can still create their first medication;
 * they'd link slots later from the Settings → Medication Slots editor).
 *
 * The ViewModel-layer regression test
 * (`MedicationViewModelAddMedicationTest`) pins what the ViewModel does
 * once `onConfirm` fires; this test pins the rendered UI state — i.e.
 * does the Save button refuse the click in the first place. Without
 * this test, a future refactor of `enabled = ...` would not regress
 * the unit tests but would silently regress the dialog.
 *
 * See `docs/audits/F5_MEDICATION_HYGIENE_FOLLOWONS_AUDIT.md` § B.3.
 */
@RunWith(AndroidJUnit4::class)
class MedicationEditorDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val morningSlot = MedicationSlotEntity(
        id = 10L,
        name = "Morning",
        idealTime = "09:00",
        driftMinutes = 60
    )

    private val eveningSlot = MedicationSlotEntity(
        id = 11L,
        name = "Evening",
        idealTime = "21:00",
        driftMinutes = 60
    )

    @Test
    fun saveDisabled_whenNameBlankAndSelectionsEmpty_andActiveSlotsPresent() {
        composeRule.setContent {
            MedicationEditorDialog(
                title = "Add Medication",
                initialName = "",
                initialSelections = emptyList(),
                activeSlots = listOf(morningSlot, eveningSlot),
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _ -> },
                onCreateNewSlot = {}
            )
        }
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveDisabled_whenNamePresentButSelectionsEmpty_andActiveSlotsPresent() {
        // The PR #1141 regression-prevention case: without the
        // `selections.isNotEmpty() || activeSlots.isEmpty()` gate, the
        // dialog would let the user save a med with no slot link, which
        // matched the operator's "no slot selected" repro for the crash.
        composeRule.setContent {
            MedicationEditorDialog(
                title = "Add Medication",
                initialName = "Lamotrigine",
                initialSelections = emptyList(),
                activeSlots = listOf(morningSlot, eveningSlot),
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _ -> },
                onCreateNewSlot = {}
            )
        }
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveEnabled_whenNamePresentAndSelectionsPresent() {
        composeRule.setContent {
            MedicationEditorDialog(
                title = "Add Medication",
                initialName = "Lamotrigine",
                initialSelections = listOf(MedicationSlotSelection(slotId = morningSlot.id)),
                activeSlots = listOf(morningSlot, eveningSlot),
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _ -> },
                onCreateNewSlot = {}
            )
        }
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun saveEnabled_whenNamePresentAndActiveSlotsEmpty_bootstrapPath() {
        // Empty `activeSlots` is the only branch where empty selections
        // is still a valid Save state — the user has no slots yet, so
        // they can't pick any. Forcing slot-picked here would be a
        // chicken-and-egg trap on first launch.
        composeRule.setContent {
            MedicationEditorDialog(
                title = "Add Medication",
                initialName = "Lamotrigine",
                initialSelections = emptyList(),
                activeSlots = emptyList(),
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _ -> },
                onCreateNewSlot = {}
            )
        }
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun saveDisabled_whenNameBlankEvenWithSelections() {
        composeRule.setContent {
            MedicationEditorDialog(
                title = "Add Medication",
                initialName = "   ",
                initialSelections = listOf(MedicationSlotSelection(slotId = morningSlot.id)),
                activeSlots = listOf(morningSlot, eveningSlot),
                onDismiss = {},
                onConfirm = { _, _, _, _, _, _ -> },
                onCreateNewSlot = {}
            )
        }
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }
}
