package com.averycorp.averytask.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import com.averycorp.averytask.MainActivity

class HabitStreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getHabitData(context)
        } catch (_: Exception) {
            HabitWidgetData(emptyList())
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
            .padding(12.dp)
            .background(GlanceTheme.colors.background)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Habit Streaks",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onBackground
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.habits.isEmpty()) {
            Text(
                text = "Add a habit in PrismTask",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.secondary
                )
            )
        } else {
            data.habits.take(6).forEach { habit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(
                        text = habit.icon,
                        style = TextStyle(fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = habit.name,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onBackground
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = if (habit.isCompletedToday) "\u2705" else "\u2B1C",
                        style = TextStyle(fontSize = 14.sp)
                    )
                }
            }
        }
    }
}

class HabitStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitStreakWidget()
}
