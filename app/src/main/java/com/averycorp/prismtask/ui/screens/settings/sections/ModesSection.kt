package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.averycorp.prismtask.ui.components.settings.ModeToggleRow
import com.averycorp.prismtask.ui.components.settings.SectionHeader

@Composable
fun ModesSection(
    selfCareEnabled: Boolean,
    medicationEnabled: Boolean,
    houseworkEnabled: Boolean,
    schoolEnabled: Boolean,
    leisureEnabled: Boolean,
    onSelfCareChange: (Boolean) -> Unit,
    onMedicationChange: (Boolean) -> Unit,
    onHouseworkChange: (Boolean) -> Unit,
    onSchoolChange: (Boolean) -> Unit,
    onLeisureChange: (Boolean) -> Unit
) {
    SectionHeader("Modes")

    ModeToggleRow("Self Care", selfCareEnabled, onSelfCareChange)
    ModeToggleRow("Medication", medicationEnabled, onMedicationChange)
    ModeToggleRow("Housework", houseworkEnabled, onHouseworkChange)
    ModeToggleRow("Schoolwork", schoolEnabled, onSchoolChange)
    ModeToggleRow("Leisure", leisureEnabled, onLeisureChange)

    HorizontalDivider()
}
