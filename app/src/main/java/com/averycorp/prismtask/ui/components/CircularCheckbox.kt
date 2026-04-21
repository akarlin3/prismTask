package com.averycorp.prismtask.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.ui.theme.LocalPrismAttrs
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismFonts

/**
 * A circular checkbox that follows Android/Material Design aesthetics.
 *
 * When unchecked: shows an empty circle outline.
 * When checked: shows a filled circle with a check mark.
 *
 * Matrix theme ([PrismThemeAttrs.terminal]): renders `[x]` / `[ ]` bracket
 * notation in the mono font instead of the circle, matching the terminal
 * checkbox spec from the mockup. Tap behaviour is identical in all themes.
 */
@Composable
fun CircularCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedColor: Color = MaterialTheme.colorScheme.outline,
    checkmarkColor: Color = Color.White,
    size: Dp = 24.dp
) {
    val attrs = LocalPrismAttrs.current
    if (attrs.terminal) {
        val prismColors = LocalPrismColors.current
        val monoFont = LocalPrismFonts.current.mono
        val label = if (checked) "[x]" else "[ ]"
        val textColor = if (checked) prismColors.primary else prismColors.muted
        Box(
            modifier = modifier
                .size(size)
                .then(
                    if (onCheckedChange != null && enabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Checkbox
                        ) { onCheckedChange(!checked) }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                fontFamily = monoFont,
                fontSize = (size.value * 0.55f).sp
            )
        }
        return
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (checked) checkedColor else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "checkboxBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            checked -> checkedColor
            else -> uncheckedColor
        },
        animationSpec = tween(durationMillis = 150),
        label = "checkboxBorder"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, borderColor, CircleShape)
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Checkbox
                    ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (checked) "Checked" else "Unchecked",
                tint = if (enabled) checkmarkColor else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(size * 0.67f)
            )
        }
    }
}
