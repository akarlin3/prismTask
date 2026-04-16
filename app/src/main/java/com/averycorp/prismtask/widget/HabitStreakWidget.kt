package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.averycorp.prismtask.MainActivity

class HabitStreakWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(150.dp, 100.dp)
        private val MEDIUM = DpSize(250.dp, 150.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getHabitData(context)
        } catch (_: Exception) {
            null
        }
        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                if (data != null) {
                    HabitStreakContent(context, data, size)
                } else {
                    WidgetLoadingState()
                }
            }
        }
    }
}

@Composable
private fun HabitStreakContent(context: Context, data: HabitWidgetData, size: DpSize) {
    val isSmall = size.width < 250.dp
    val isLarge = size.width >= 350.dp
    val habitsIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_habits")
    }
    val completedToday = data.habits.count { it.isCompletedToday }
    val totalHabits = data.habits.size

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(if (isLarge) 12.dp else 8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(habitsIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Habits",
                style = WidgetTextStyles.header(GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            if (data.longestStreak > 0) {
                Text(
                    text = "\uD83D\uDD25 ${data.longestStreak} day${if (data.longestStreak != 1) "s" else ""}",
                    style = WidgetTextStyles.captionMedium(
                        if (data.longestStreak > 30) WidgetColors.streakGold
                        else GlanceTheme.colors.primary
                    )
                )
            }
        }
        // Subtitle: completed today count
        if (totalHabits > 0) {
            Text(
                text = "$completedToday of $totalHabits done today",
                style = WidgetTextStyles.badge(GlanceTheme.colors.onSurfaceVariant)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (data.habits.isEmpty()) {
            WidgetEmptyState(
                emoji = "\uD83C\uDF31",
                message = "No Habits Yet"
            )
        } else if (isSmall) {
            data.habits.take(3).forEach { habit ->
                SmallHabitRow(habit)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        } else {
            // Large: show up to 16, Medium: show up to 6
            val maxHabits = if (isLarge) minOf(data.habits.size, 16) else 6
            val maxRows = if (isLarge) 8 else 3
            data.habits.take(maxHabits).chunked(2).take(maxRows).forEach { pair ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    pair.forEachIndexed { index, habit ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                        }
                        HabitCell(habit, showWeeklyDots = isLarge, modifier = GlanceModifier.defaultWeight())
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(habitsIntent))) {
            Text(
                text = "View All \u2192",
                style = WidgetTextStyles.badge(GlanceTheme.colors.primary)
            )
        }
    }
}

@Composable
private fun SmallHabitRow(habit: HabitWidgetItem) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<ToggleHabitFromWidgetAction>(parameters = habitIdParams(habit.id))
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = habit.icon, style = TextStyle(fontSize = 16.sp))
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = habit.name,
            style = WidgetTextStyles.caption(GlanceTheme.colors.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = if (habit.isCompletedToday) "\u2705" else "\u25CB",
            style = TextStyle(
                fontSize = 14.sp,
                color = if (habit.isCompletedToday) WidgetColors.habitComplete else GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun HabitCell(habit: HabitWidgetItem, showWeeklyDots: Boolean, modifier: GlanceModifier) {
    val tint = if (habit.isCompletedToday) WidgetColors.habitCompleteBg else GlanceTheme.colors.surfaceVariant
    Box(
        modifier = modifier
            .cornerRadius(8.dp)
            .background(tint)
            .padding(8.dp)
            .clickable(actionRunCallback<ToggleHabitFromWidgetAction>(parameters = habitIdParams(habit.id)))
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = habit.icon, style = TextStyle(fontSize = 18.sp))
                Spacer(modifier = GlanceModifier.width(6.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = habit.name,
                        style = WidgetTextStyles.captionMedium(GlanceTheme.colors.onSurface),
                        maxLines = 1
                    )
                    if (habit.streak > 0) {
                        Text(
                            text = "\uD83D\uDD25 ${habit.streak}",
                            style = TextStyle(fontSize = 9.sp, color = WidgetColors.streakFire)
                        )
                    }
                }
                Text(
                    text = if (habit.isCompletedToday) "\u25CF" else "\u25CB",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = if (habit.isCompletedToday) WidgetColors.habitComplete else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            if (showWeeklyDots) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                WeeklyDots(habit)
            }
        }
    }
}

@Composable
private fun WeeklyDots(habit: HabitWidgetItem) {
    val dayOfWeek = java.util.Calendar
        .getInstance()
        .get(java.util.Calendar.DAY_OF_WEEK)
    val todayIdx = when (dayOfWeek) {
        java.util.Calendar.MONDAY -> 0
        java.util.Calendar.TUESDAY -> 1
        java.util.Calendar.WEDNESDAY -> 2
        java.util.Calendar.THURSDAY -> 3
        java.util.Calendar.FRIDAY -> 4
        java.util.Calendar.SATURDAY -> 5
        java.util.Calendar.SUNDAY -> 6
        else -> 0
    }
    Row {
        for (i in 0..6) {
            val isFuture = i > todayIdx
            val isToday = i == todayIdx
            val daysFromToday = todayIdx - i
            val isCompleted = when {
                isToday -> habit.isCompletedToday
                isFuture -> false
                else -> daysFromToday < habit.streak + (if (habit.isCompletedToday) 0 else -1)
            }
            val dotColor = when {
                isFuture -> GlanceTheme.colors.surfaceVariant
                isCompleted -> WidgetColors.streakFire
                else -> WidgetColors.habitIncomplete
            }
            Box(modifier = GlanceModifier.size(5.dp).cornerRadius(3.dp).background(dotColor)) {}
            if (i < 6) {
                Spacer(modifier = GlanceModifier.width(2.dp))
            }
        }
    }
}

class HabitStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitStreakWidget()
}
