package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.ui.theme.LocalPrismAttrs
import com.averycorp.prismtask.ui.theme.LocalPrismColors

/**
 * Theme-aware toggle switch.
 *
 * - Matrix: renders `[ON]` / `[OFF]` bracket text in a sharp bordered box
 * - All other themes: standard pill toggle using [PrismThemeColors] accent
 */
@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val attrs = LocalPrismAttrs.current
    val colors = LocalPrismColors.current
    val accent = accentColor ?: colors.primary

    if (attrs.terminal) {
        val textColor = if (checked) accent else colors.muted
        val borderColor = if (checked) accent else colors.border
        val bgColor = if (checked) accent.copy(alpha = 0.10f) else Color.Transparent

        Box(
            modifier = modifier
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
                .border(1.dp, borderColor, RoundedCornerShape(0.dp))
                .background(bgColor, RoundedCornerShape(0.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (checked) "[ON]" else "[OFF]",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = colors.onSurface,
                uncheckedTrackColor = colors.surfaceVariant,
                uncheckedBorderColor = colors.border
            )
        )
    }
}
