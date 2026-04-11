package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.AdvancedToggle
import com.averycorp.prismtask.ui.components.settings.ColorOverrideRow
import com.averycorp.prismtask.ui.components.settings.ColorPickerDialog
import com.averycorp.prismtask.ui.components.settings.PriorityColorRow
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.screens.settings.accentColors
import com.averycorp.prismtask.ui.theme.PriorityColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSection(
    themeMode: String,
    accentColor: String,
    recentCustomColors: List<String>,
    backgroundColor: String,
    surfaceColor: String,
    errorColor: String,
    priorityColorNone: String,
    priorityColorLow: String,
    priorityColorMedium: String,
    priorityColorHigh: String,
    priorityColorUrgent: String,
    fontScale: Float,
    onThemeModeChange: (String) -> Unit,
    onAccentColorChange: (String) -> Unit,
    onCustomAccentColorChange: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onSurfaceColorChange: (String) -> Unit,
    onErrorColorChange: (String) -> Unit,
    onPriorityColorChange: (Int, String) -> Unit,
    onResetColorOverrides: () -> Unit
) {
    var showCustomAccentPicker by remember { mutableStateOf(false) }
    var showAppearanceAdvanced by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Color picker dialog (generic - used by color overrides)
    showColorPicker?.let { (title, currentHex) ->
        ColorPickerDialog(
            title = title,
            currentHex = currentHex,
            onSelect = { hex ->
                when (title) {
                    "Background" -> onBackgroundColorChange(hex)
                    "Surface" -> onSurfaceColorChange(hex)
                    "Error" -> onErrorColorChange(hex)
                    "None Priority" -> onPriorityColorChange(0, hex)
                    "Low Priority" -> onPriorityColorChange(1, hex)
                    "Medium Priority" -> onPriorityColorChange(2, hex)
                    "High Priority" -> onPriorityColorChange(3, hex)
                    "Urgent Priority" -> onPriorityColorChange(4, hex)
                }
                showColorPicker = null
            },
            onClear = {
                when (title) {
                    "Background" -> onBackgroundColorChange("")
                    "Surface" -> onSurfaceColorChange("")
                    "Error" -> onErrorColorChange("")
                    "None Priority" -> onPriorityColorChange(0, "")
                    "Low Priority" -> onPriorityColorChange(1, "")
                    "Medium Priority" -> onPriorityColorChange(2, "")
                    "High Priority" -> onPriorityColorChange(3, "")
                    "Urgent Priority" -> onPriorityColorChange(4, "")
                }
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }

    SectionHeader("Appearance")

    Text(
        text = "Theme",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
            FilterChip(
                selected = themeMode == value,
                onClick = { onThemeModeChange(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Accent Color",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        accentColors.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            val isSelected = accentColor.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable { onAccentColorChange(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        val isCustomSelected = !accentColors.any { accentColor.equals(it, ignoreCase = true) }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                            Color(0xFFFF0000)
                        )
                    )
                )
                .then(
                    if (isCustomSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                )
                .clickable { showCustomAccentPicker = true },
            contentAlignment = Alignment.Center
        ) {
            if (isCustomSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Custom color selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (recentCustomColors.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Recent Custom Colors",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recentCustomColors.forEach { hex ->
                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                val isSelected = accentColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { onCustomAccentColorChange(hex) }
                )
            }
        }
    }

    if (showCustomAccentPicker) {
        ColorPickerDialog(
            title = "Custom Accent Color",
            currentHex = if (accentColors.any { accentColor.equals(it, ignoreCase = true) }) "" else accentColor,
            onSelect = { hex ->
                onCustomAccentColorChange(hex)
                showCustomAccentPicker = false
            },
            onClear = {
                onAccentColorChange("#2563EB")
                showCustomAccentPicker = false
            },
            onDismiss = { showCustomAccentPicker = false }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Font Size: ${String.format("%.0f%%", fontScale * 100)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Slider(
        value = fontScale,
        onValueChange = { onFontScaleChange(it) },
        valueRange = 0.8f..1.4f,
        steps = 5,
        modifier = Modifier.fillMaxWidth()
    )

    AdvancedToggle(expanded = showAppearanceAdvanced, onToggle = { showAppearanceAdvanced = !showAppearanceAdvanced })
    AnimatedVisibility(visible = showAppearanceAdvanced) {
        Column {
            Text(
                text = "Color Overrides",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ColorOverrideRow("Background", backgroundColor) { showColorPicker = "Background" to backgroundColor }
            ColorOverrideRow("Surface", surfaceColor) { showColorPicker = "Surface" to surfaceColor }
            ColorOverrideRow("Error", errorColor) { showColorPicker = "Error" to errorColor }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Priority Colors",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val defaults = PriorityColors()
            PriorityColorRow("None", priorityColorNone, defaults.none) { showColorPicker = "None Priority" to priorityColorNone }
            PriorityColorRow("Low", priorityColorLow, defaults.low) { showColorPicker = "Low Priority" to priorityColorLow }
            PriorityColorRow("Medium", priorityColorMedium, defaults.medium) { showColorPicker = "Medium Priority" to priorityColorMedium }
            PriorityColorRow("High", priorityColorHigh, defaults.high) { showColorPicker = "High Priority" to priorityColorHigh }
            PriorityColorRow("Urgent", priorityColorUrgent, defaults.urgent) { showColorPicker = "Urgent Priority" to priorityColorUrgent }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onResetColorOverrides) {
                Text("Reset All Color Overrides", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
}
