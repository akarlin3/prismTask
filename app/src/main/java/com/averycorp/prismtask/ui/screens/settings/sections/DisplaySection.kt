package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.preferences.AppearancePrefs
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.components.settings.SettingsToggleRow

@Composable
fun DisplaySection(
    appearancePrefs: AppearancePrefs,
    onCompactModeChange: (Boolean) -> Unit,
    onShowCardBordersChange: (Boolean) -> Unit,
    onCardCornerRadiusChange: (Int) -> Unit
) {
    SectionHeader("Display")

    SettingsToggleRow(
        title = "Compact Mode",
        subtitle = "Reduce vertical padding throughout the app",
        checked = appearancePrefs.compactMode,
        onCheckedChange = onCompactModeChange
    )

    SettingsToggleRow(
        title = "Card Borders",
        subtitle = "Show outlines around task and project cards",
        checked = appearancePrefs.showTaskCardBorders,
        onCheckedChange = onShowCardBordersChange
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Card Corner Radius: ${appearancePrefs.cardCornerRadius}dp",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Slider(
        value = appearancePrefs.cardCornerRadius.toFloat(),
        onValueChange = { onCardCornerRadiusChange(it.toInt()) },
        valueRange = 0f..24f,
        steps = 23,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(appearancePrefs.cardCornerRadius.dp),
        border = if (appearancePrefs.showTaskCardBorders) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (appearancePrefs.compactMode) 8.dp else 16.dp
            )
        ) {
            Text(
                "Sample Task",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!appearancePrefs.compactMode) {
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "Preview of your card styling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
}
