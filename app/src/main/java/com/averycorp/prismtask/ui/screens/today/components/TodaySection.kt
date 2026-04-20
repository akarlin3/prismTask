package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismShapes
import com.averycorp.prismtask.ui.theme.LocalPrismFonts

/**
 * Collapsible section wrapper used throughout the Today screen — e.g.
 * "Overdue", "Up Next", "Completed". Renders a clickable header with
 * emoji + title + count badge + chevron, and animates content in/out
 * when [expanded] toggles.
 */
@Composable
internal fun CollapsibleSection(
    emoji: String,
    title: String,
    count: Int,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    countLabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(280))
    ) {
        SectionHeaderRow(
            emoji = emoji,
            title = title,
            count = count,
            countLabel = countLabel,
            accentColor = accentColor,
            expanded = expanded,
            onToggle = onToggle
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeaderRow(
    emoji: String,
    title: String,
    count: Int,
    countLabel: String?,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(220),
        label = "chevronRotation"
    )
    val colors = LocalPrismColors.current
    val fonts = LocalPrismFonts.current.body
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        // Section headers ("TODAY", "HABITS", etc.) use the active theme
        // font at 10sp / letterSpacing 1.5sp / Medium weight per the
        // design spec, rendered uppercase so the terminal-style themes
        // (Cyberpunk / Matrix) read as command labels.
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = fonts,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = accentColor
        )
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(LocalPrismShapes.current.chip)
                .background(accentColor.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = countLabel ?: "$count",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = fonts,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
            tint = colors.muted
        )
    }
}
