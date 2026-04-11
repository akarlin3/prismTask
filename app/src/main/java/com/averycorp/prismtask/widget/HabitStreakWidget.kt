package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

/**
 * Habit streak widget. Shows a 2-column grid of habits with tappable
 * completion cells. Tapping a cell toggles that habit's completion for
 * today via [ToggleHabitFromWidgetAction].
 */
class HabitStreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getHabitData(context)
        } catch (_: Exception) {
            HabitWidgetData(emptyList(), 0)
        }

        provideContent {
            GlanceTheme {
                HabitStreakContent(data)
            }
        }
    }
}

@Composable
private fun HabitStreakContent(data: HabitWidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        // Header: "Habits" + streak indicator
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Habits",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (data.longestStreak > 0) {
                Text(
                    text = "\uD83D\uDD25 ${data.longestStreak} day",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.primary
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.habits.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to add a habit",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        } else {
            // 2-column grid via pairs of rows.
            val chunks = data.habits.chunked(2)
            chunks.take(4).forEach { pair ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    pair.forEachIndexed { index, habit ->
                        if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                        HabitCell(
                            habit = habit,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = "View All \u2192",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun HabitCell(
    habit: HabitWidgetItem,
    modifier: GlanceModifier
) {
    val tint = if (habit.isCompletedToday) {
        ColorProvider(Color(0xFFC8E6C9))
    } else {
        GlanceTheme.colors.surfaceVariant
    }
    Box(
        modifier = modifier
            .cornerRadius(10.dp)
            .background(tint)
            .padding(8.dp)
            .clickable(
                actionRunCallback<ToggleHabitFromWidgetAction>(
                    parameters = habitIdParams(habit.id)
                )
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = habit.icon,
                style = TextStyle(fontSize = 18.sp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = habit.name,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    ),
                    maxLines = 1
                )
                if (habit.streak > 0) {
                    Text(
                        text = "${habit.streak}d",
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
            Text(
                text = if (habit.isCompletedToday) "\u25CF" else "\u25CB",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = if (habit.isCompletedToday)
                        ColorProvider(Color(0xFF2E7D32))
                    else GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

class HabitStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitStreakWidget()
}
