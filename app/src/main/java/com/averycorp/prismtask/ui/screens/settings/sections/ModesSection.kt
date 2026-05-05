package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

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

    SettingsToggleRow(
        title = "Self Care",
        subtitle = "Show the Self-Care section on Today and in Habits",
        checked = selfCareEnabled,
        onCheckedChange = onSelfCareChange
    )
    SettingsToggleRow(
        title = "Medication",
        subtitle = "Show medication reminders, refill projection, and the Meds tab",
        checked = medicationEnabled,
        onCheckedChange = onMedicationChange
    )
    SettingsToggleRow(
        title = "Housework",
        subtitle = "Show the housework routine and Daily Essentials card",
        checked = houseworkEnabled,
        onCheckedChange = onHouseworkChange
    )
    SettingsToggleRow(
        title = "Schoolwork",
        subtitle = "Show the schoolwork routine, courses, and assignments",
        checked = schoolEnabled,
        onCheckedChange = onSchoolChange
    )
    SettingsToggleRow(
        title = "Leisure",
        subtitle = "Show the leisure picks card and downtime tracking",
        checked = leisureEnabled,
        onCheckedChange = onLeisureChange
    )

    HorizontalDivider()
}
