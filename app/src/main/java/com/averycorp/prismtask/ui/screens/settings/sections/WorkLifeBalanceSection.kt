package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.WorkLifeBalancePrefs
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow
import com.averycorp.prismtask.ui.theme.LifeCategoryColor

/**
 * Settings section for the Work-Life Balance Engine (v1.4.0 V1).
 *
 * Lets the user:
 *  - Toggle auto-classification of new tasks.
 *  - Toggle the Today screen balance bar.
 *  - Adjust the four target ratios (must sum to 100%).
 *  - Adjust the overload threshold.
 *
 * Ratio sliders are intentionally independent so the user can nudge one
 * category without the UI fighting them. A live "Sum: N%" label indicates
 * whether the configuration is valid — invalid sums are rejected at the
 * DataStore boundary via [WorkLifeBalancePrefs.isValid].
 */
@Composable
fun WorkLifeBalanceSection(
    prefs: WorkLifeBalancePrefs,
    onPrefsChange: (WorkLifeBalancePrefs) -> Unit,
    onViewReport: () -> Unit = {}
) {
    SectionHeader("Work-Life Balance")

    FilledTonalButton(
        onClick = onViewReport,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text("View Weekly Report")
    }

    SettingsToggleRow(
        title = "Auto-Classify Tasks",
        subtitle = "Guess a life category from the task title",
        checked = prefs.autoClassifyEnabled,
        onCheckedChange = { onPrefsChange(prefs.copy(autoClassifyEnabled = it)) }
    )

    SettingsToggleRow(
        title = "Show Balance Bar on Today",
        subtitle = "Render the stacked category bar above your task list",
        checked = prefs.showBalanceBar,
        onCheckedChange = { onPrefsChange(prefs.copy(showBalanceBar = it)) }
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Target Ratios",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val sum = prefs.workTarget + prefs.personalTarget + prefs.selfCareTarget + prefs.healthTarget
    val sumColor = if (sum in 99..101) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    Text(
        text = "Sum: $sum% (should be 100%)",
        style = MaterialTheme.typography.labelSmall,
        color = sumColor,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    CategoryRatioSlider(
        label = LifeCategory.label(LifeCategory.WORK),
        color = LifeCategoryColor.WORK,
        value = prefs.workTarget,
        onChange = { onPrefsChange(prefs.copy(workTarget = it)) }
    )
    CategoryRatioSlider(
        label = LifeCategory.label(LifeCategory.PERSONAL),
        color = LifeCategoryColor.PERSONAL,
        value = prefs.personalTarget,
        onChange = { onPrefsChange(prefs.copy(personalTarget = it)) }
    )
    CategoryRatioSlider(
        label = LifeCategory.label(LifeCategory.SELF_CARE),
        color = LifeCategoryColor.SELF_CARE,
        value = prefs.selfCareTarget,
        onChange = { onPrefsChange(prefs.copy(selfCareTarget = it)) }
    )
    CategoryRatioSlider(
        label = LifeCategory.label(LifeCategory.HEALTH),
        color = LifeCategoryColor.HEALTH,
        value = prefs.healthTarget,
        onChange = { onPrefsChange(prefs.copy(healthTarget = it)) }
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Overload Threshold",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "Warn when work exceeds target by ${prefs.overloadThresholdPct}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Slider(
        value = prefs.overloadThresholdPct.toFloat(),
        onValueChange = { onPrefsChange(prefs.copy(overloadThresholdPct = it.toInt().coerceIn(5, 25))) },
        valueRange = 5f..25f,
        steps = 19
    )

    HorizontalDivider()
}

@Composable
private fun CategoryRatioSlider(
    label: String,
    color: Color,
    value: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(96.dp)
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 19
        )
    }
}
