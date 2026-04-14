package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun WeeklyProgressDots(
    completedDays: Set<DayOfWeek>,
    activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    color: Color,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier
) {
    val todayDow = today.dayOfWeek
    val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        days.forEach { day ->
            val isActive = day in activeDays
            val isCompleted = day in completedDays
            val isToday = day == todayDow

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            !isActive -> Modifier.background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            isCompleted -> Modifier.background(color)
                            else -> Modifier.background(color.copy(alpha = 0.2f))
                        }
                    ).then(
                        if (isToday) {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
