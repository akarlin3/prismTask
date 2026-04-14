package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun ContributionGrid(
    completionsByDay: Map<LocalDate, Int>,
    targetPerDay: Int,
    habitColor: Color,
    today: LocalDate = LocalDate.now(),
    weeks: Int = 12,
    onDayClick: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val startDate = today
        .minusWeeks(weeks.toLong() - 1)
        .with(java.time.DayOfWeek.MONDAY)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        var currentDate = startDate
        repeat(weeks) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(7) {
                    val date = currentDate
                    val count = completionsByDay[date] ?: 0
                    val intensity = if (targetPerDay > 0) {
                        (count.toFloat() / targetPerDay).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val isToday = date == today
                    val cellColor = when {
                        date.isAfter(today) -> Color.Transparent
                        intensity >= 1f -> habitColor
                        intensity >= 0.5f -> habitColor.copy(alpha = 0.6f)
                        intensity > 0f -> habitColor.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cellColor)
                            .then(
                                if (isToday) {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(2.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ).then(
                                if (onDayClick != null && !date.isAfter(today)) {
                                    Modifier.clickable { onDayClick(date) }
                                } else {
                                    Modifier
                                }
                            )
                    )
                    currentDate = currentDate.plusDays(1)
                }
            }
        }
    }
}
