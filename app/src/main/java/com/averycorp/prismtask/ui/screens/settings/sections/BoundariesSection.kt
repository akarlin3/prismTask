package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.domain.model.BoundaryRule
import com.averycorp.prismtask.domain.model.BoundaryRuleType
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.ui.components.settings.SectionHeader

/**
 * Settings section for managing boundary rules (v1.4.0 V3).
 *
 * Lists each configured rule with a toggle + delete button and offers an
 * "Add rule" CTA that routes to a manual-entry dialog (to be added in a
 * follow-up). For now, the seed rules from [com.averycorp.prismtask.domain.usecase.BoundaryEnforcer.BUILT_IN]
 * are what users see on first run.
 */
@Composable
fun BoundariesSection(
    rules: List<BoundaryRule>,
    onToggle: (BoundaryRule, Boolean) -> Unit,
    onDelete: (BoundaryRule) -> Unit,
    onAdd: () -> Unit
) {
    SectionHeader("Boundaries")
    Text(
        text = "Time windows where certain life categories are blocked or suggested",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (rules.isEmpty()) {
        Text(
            text = "No boundary rules yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        rules.forEach { rule ->
            BoundaryRuleRow(rule = rule, onToggle = onToggle, onDelete = onDelete)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onAdd) { Text("Add Rule") }
    HorizontalDivider()
}

@Composable
private fun BoundaryRuleRow(
    rule: BoundaryRule,
    onToggle: (BoundaryRule, Boolean) -> Unit,
    onDelete: (BoundaryRule) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val typeLabel = when (rule.ruleType) {
                BoundaryRuleType.BLOCK_CATEGORY -> "Block"
                BoundaryRuleType.SUGGEST_CATEGORY -> "Suggest"
                BoundaryRuleType.REMIND -> "Remind"
            }
            val dayLabel = if (rule.activeDays.size == 7) "every day"
            else if (rule.activeDays == BoundaryRule.WEEKDAYS) "weekdays"
            else if (rule.activeDays == BoundaryRule.WEEKEND) "weekends"
            else rule.activeDays.joinToString(", ") { it.name.take(3).lowercase() }
            val categoryLabel = LifeCategory.label(rule.category)
            Text(
                text = "$typeLabel $categoryLabel · ${BoundaryRule.formatTime(rule.startTime)}–${BoundaryRule.formatTime(rule.endTime)} · $dayLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = rule.isEnabled,
            onCheckedChange = { onToggle(rule, it) }
        )
        IconButton(onClick = { onDelete(rule) }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
