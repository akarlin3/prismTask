package com.averykarlin.averytask.widget

import android.content.Context
import android.content.Intent
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
import com.averykarlin.averytask.MainActivity

class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getTodayData(context)
        } catch (_: Exception) {
            TodayWidgetData(0, 0, emptyList(), 0, 0)
        }

        provideContent {
            GlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }
}

@Composable
private fun TodayWidgetContent(data: TodayWidgetData) {
    val total = data.totalTasks + data.totalHabits
    val completed = data.completedTasks + data.completedHabits

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.background)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "$completed/$total",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = GlanceTheme.colors.primary
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Today",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onBackground
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        data.taskNames.take(4).forEach { name ->
            Text(
                text = "\u2022 $name",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onBackground
                ),
                maxLines = 1
            )
        }

        if (data.totalHabits > 0) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "${data.completedHabits}/${data.totalHabits} habits done",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.secondary
                )
            )
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
