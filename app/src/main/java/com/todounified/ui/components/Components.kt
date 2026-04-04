package com.todounified.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todounified.data.Priority
import com.todounified.data.Task
import com.todounified.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

// ── Priority config ──
data class PriorityStyle(val label: String, val color: Color, val bg: Color, val icon: String)

val priorityStyles = mapOf(
    Priority.URGENT to PriorityStyle("Urgent", Red, RedBg, "⚡"),
    Priority.HIGH to PriorityStyle("High", Orange, OrangeBg, "🔺"),
    Priority.MEDIUM to PriorityStyle("Medium", Blue, BlueBg, "◆"),
    Priority.LOW to PriorityStyle("Low", Gray, GrayBg, "▽"),
)

// ── Tag Pill ──
@Composable
fun TagPill(tag: String, small: Boolean = false) {
    val hue = tag.fold(0) { acc, c -> acc + c.code } % 360
    val color = Color.hsl(hue.toFloat(), 0.7f, 0.65f)
    val bg = Color.hsl(hue.toFloat(), 0.6f, 0.5f, 0.15f)

    Text(
        text = tag,
        fontSize = if (small) 10.sp else 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        fontFamily = MonoFont,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 1.dp else 2.dp)
    )
}

// ── Task Row ──
@Composable
fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val style = priorityStyles[task.priority] ?: priorityStyles[Priority.MEDIUM]!!
    val isOverdue = task.dueDate.isNotBlank() && !task.done && try {
        LocalDate.parse(task.dueDate) < LocalDate.now()
    } catch (_: Exception) { false }

    val borderColor by animateColorAsState(
        if (isOverdue) Red.copy(alpha = 0.35f) else Border, label = "border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (task.done) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(
                    2.dp,
                    if (task.done) style.color else Color.White.copy(alpha = 0.2f),
                    RoundedCornerShape(6.dp)
                )
                .background(if (task.done) style.color else Color.Transparent)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (task.done) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = MonoFont,
                color = if (task.done) OnSurfaceFaint else OnSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Priority badge
                Text(
                    text = "${style.icon} ${style.label}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = style.color,
                    fontFamily = MonoFont,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(style.bg)
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
                // Due date
                if (task.dueDate.isNotBlank()) {
                    val formatted = try {
                        val date = LocalDate.parse(task.dueDate)
                        date.format(DateTimeFormatter.ofPattern("MMM d"))
                    } catch (_: Exception) { task.dueDate }
                    Text(
                        text = "📅 $formatted${if (isOverdue) " — overdue" else ""}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOverdue) Red else OnSurfaceFaint,
                        fontFamily = MonoFont
                    )
                }
                // Tags
                task.tags.forEach { TagPill(it, small = true) }
            }
        }

        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = OnSurfaceFaint, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = OnSurfaceFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Priority Selector ──
@Composable
fun PrioritySelector(selected: Priority, onSelect: (Priority) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Priority.entries.forEach { p ->
            val style = priorityStyles[p]!!
            val isSelected = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        2.dp,
                        if (isSelected) style.color else Border,
                        RoundedCornerShape(6.dp)
                    )
                    .background(if (isSelected) style.bg else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = style.icon,
                    fontSize = 12.sp,
                    color = if (isSelected) style.color else OnSurfaceFaint
                )
            }
        }
    }
}

// ── Tag Selector ──
@Composable
fun TagSelector(
    allTags: List<String>,
    selectedTags: List<String>,
    onToggle: (String) -> Unit
) {
    val defaultTags = listOf("work", "personal", "research", "errand", "health", "code")
    val combined = (defaultTags + allTags).distinct()

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        combined.take(8).forEach { tag ->
            val selected = tag in selectedTags
            Text(
                text = tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) OnSurface else OnSurfaceFaint,
                fontFamily = MonoFont,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (selected) BorderBright else Border,
                        RoundedCornerShape(999.dp)
                    )
                    .background(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onToggle(tag) }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

// ── Styled Text Field ──
@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 13,
    fontWeight: FontWeight = FontWeight.Normal
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = OnSurface,
            fontSize = fontSize.sp,
            fontFamily = MonoFont,
            fontWeight = fontWeight
        ),
        cursorBrush = SolidColor(Indigo),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = OnSurfaceFaint, fontSize = fontSize.sp, fontFamily = MonoFont)
            }
            inner()
        }
    )
}
