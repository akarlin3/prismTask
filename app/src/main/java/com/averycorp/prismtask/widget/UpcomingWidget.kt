package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
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
 * Upcoming widget — shows tasks for today, tomorrow, and the day after.
 *
 * Adapts to two size breakpoints:
 * - Medium (4x3): 3-day columns (today/tomorrow/day after)
 * - Large (5x4+): 3-day columns with expanded task details per day
 */
class UpcomingWidget : GlanceAppWidget() {
    companion object {
        private val MEDIUM = DpSize(250.dp, 170.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getUpcomingData(context)
        } catch (_: Exception) {
            UpcomingWidgetData(emptyList(), emptyList(), emptyList(), emptyList())
        }

        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                UpcomingContent(data, size)
            }
        }
    }
}

@Composable
private fun UpcomingContent(data: UpcomingWidgetData, size: DpSize) {
    val isLarge = size.width >= 350.dp
    val maxTasksPerColumn = if (isLarge) 5 else 3

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

        if (data.totalCount == 0) {
            Spacer(modifier = GlanceModifier.height(16.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing Upcoming \u2014 Enjoy the Peace \uD83C\uDF3F",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        } else {
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
                    maxTasks = maxTasksPerColumn,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                DayColumn(
                    label = "Tomorrow",
                    tasks = data.tomorrow,
                    maxTasks = maxTasksPerColumn,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                DayColumn(
                    label = "+2 Days",
                    tasks = data.dayAfter,
                    maxTasks = maxTasksPerColumn,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    label: String,
    tasks: List<WidgetTaskRow>,
    maxTasks: Int,
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
            tasks.take(maxTasks).forEach { row ->
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
            if (tasks.size > maxTasks) {
                Text(
                    text = "+${tasks.size - maxTasks} more",
                    style = TextStyle(
                        fontSize = 8.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
