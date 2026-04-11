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
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

/**
 * Upcoming widget — shows tasks for today, tomorrow, and the day after in
 * a compact 3-column timeline. Overdue tasks appear at the top in red.
 */
class UpcomingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getUpcomingData(context)
        } catch (_: Exception) {
            UpcomingWidgetData(emptyList(), emptyList(), emptyList(), emptyList())
        }

        provideContent {
            GlanceTheme {
                UpcomingContent(data)
            }
        }
    }
}

@Composable
private fun UpcomingContent(data: UpcomingWidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Upcoming",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "${data.totalCount} tasks",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        if (data.overdue.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(6.dp)
                    .background(ColorProvider(Color(0x33D32F2F)))
                    .padding(6.dp)
            ) {
                Column {
                    Text(
                        text = "Overdue (${data.overdue.size})",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color(0xFFD32F2F))
                        )
                    )
                    data.overdue.take(2).forEach { row ->
                        Text(
                            text = "\u2022 ${row.title}",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(Color(0xFFD32F2F))
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            DayColumn(
                label = "Today",
                tasks = data.today,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            DayColumn(
                label = "Tomorrow",
                tasks = data.tomorrow,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            DayColumn(
                label = "+2 Days",
                tasks = data.dayAfter,
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

@Composable
private fun DayColumn(
    label: String,
    tasks: List<WidgetTaskRow>,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier
            .cornerRadius(8.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(6.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.primary
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (tasks.isEmpty()) {
            Text(
                text = "\u2014",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            tasks.take(3).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .size(4.dp)
                            .cornerRadius(2.dp)
                            .background(priorityColorFor(row.priority))
                    ) {}
                    Spacer(modifier = GlanceModifier.width(3.dp))
                    Text(
                        text = row.title,
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = GlanceTheme.colors.onSurface
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
