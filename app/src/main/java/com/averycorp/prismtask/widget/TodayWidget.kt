package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TodayWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(150.dp, 100.dp)
        private val MEDIUM = DpSize(250.dp, 150.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getTodayData(context)
        } catch (
            _: Exception
        ) {
            TodayWidgetData(0, 0, emptyList(), 0, 0, emptyList(), 0)
        }
        provideContent {
            val size = LocalSize.current
            GlanceTheme { TodayWidgetContent(context, data, size) }
        }
    }
}

@Composable
private fun TodayWidgetContent(context: Context, data: TodayWidgetData, size: DpSize) {
    val isSmall = size.width < 250.dp
    val isLarge = size.width >= 350.dp
    val total = data.totalTasks
    val completed = data.completedTasks
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val todayLabel = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }
    val overdueCount = data.tasks.count { it.isOverdue }
    val openTodayIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_today")
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Today",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface)
                )
                if (!isSmall) {
                    Text(text = todayLabel, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
            ScoreBadge(score = data.productivityScore)
        }
        if (isSmall) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(text = "$completed/$total tasks done", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface))
            if (data.totalHabits >
                0
            ) {
                Text(
                    text = "${data.completedHabits}/${data.totalHabits} habits",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.secondary)
                )
            }
        } else {
            Spacer(modifier = GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "$completed of $total tasks \u00b7 ${data.completedHabits}/${data.totalHabits} habits",
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            val maxTasks = if (isLarge) 5 else 3
            if (data.tasks.isEmpty()) {
                Text(text = "All Caught Up! \u2728", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                data.tasks.take(maxTasks).forEach { task ->
                    WidgetTaskRowView(context, task, showDate = isLarge)
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }
            }
            if (isLarge &&
                data.totalHabits > 0
            ) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    data.habitIcons.take(5).forEach { icon ->
                        Text(text = icon, style = TextStyle(fontSize = 13.sp))
                        Spacer(modifier = GlanceModifier.width(3.dp))
                    }
                }
            }
            if (isLarge &&
                overdueCount > 0
            ) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(
                            8.dp
                        ).background(
                            ColorProvider(Color(0x33D32F2F))
                        ).padding(horizontal = 8.dp, vertical = 3.dp)
                        .clickable(actionStartActivity(openTodayIntent))
                ) {
                    Text(
                        text = "$overdueCount overdue",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color(0xFFD32F2F)))
                    )
                }
            }
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openTodayIntent))) {
            Text(
                text = "View All \u2192",
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun WidgetTaskRowView(context: Context, task: WidgetTaskRow, showDate: Boolean = false) {
    val taskIntent = Intent(context, MainActivity::class.java).apply {
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_task")
        putExtra("task_id", task.id)
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = GlanceModifier.size(width = 3.dp, height = 18.dp).cornerRadius(2.dp).background(priorityColorFor(task.priority))) {
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
            modifier = GlanceModifier
                .size(
                    18.dp
                ).cornerRadius(
                    4.dp
                ).background(
                    if (task.isCompleted) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
                ).clickable(actionRunCallback<ToggleTaskFromWidgetAction>(parameters = taskIdParams(task.id))),
            contentAlignment = Alignment.Center
        ) {
            if (task.isCompleted) {
                Text(
                    text = "\u2713",
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.title,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = when {
                        task.isOverdue -> GlanceTheme.colors.error
                        task.isCompleted -> GlanceTheme.colors.onSurfaceVariant
                        else -> GlanceTheme.colors.onSurface
                    },
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickable(actionStartActivity(taskIntent))
            )
            if (showDate &&
                task.dueDate != null
            ) {
                Text(
                    text = smartDateLabel(task.dueDate, task.isOverdue),
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = if (task.isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

private fun smartDateLabel(dueDate: Long, isOverdue: Boolean): String {
    val now = System.currentTimeMillis()
    if (isOverdue) {
        val daysDiff = TimeUnit.MILLISECONDS.toDays(now - dueDate)
        return when {
            daysDiff <= 0 -> "Due today"
            daysDiff == 1L -> "Overdue by 1 day"
            else -> "Overdue by $daysDiff days"
        }
    }
    return "Due ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dueDate))}"
}

@Composable
private fun ScoreBadge(score: Int) {
    val backgroundColor = when {
        score >= 80 -> ColorProvider(Color(0xFF2E7D32))
        score >= 60 -> ColorProvider(Color(0xFFED6C02))
        else -> ColorProvider(Color(0xFFC62828))
    }
    Box(modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).background(backgroundColor), contentAlignment = Alignment.Center) {
        Text(text = score.toString(), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorProvider(Color.White)))
    }
}

internal fun priorityColorFor(priority: Int): ColorProvider = when (priority) {
    4 -> ColorProvider(Color(0xFFD32F2F))
    3 -> ColorProvider(Color(0xFFF57C00))
    2 -> ColorProvider(Color(0xFFFBC02D))
    1 -> ColorProvider(Color(0xFF388E3C))
    else -> ColorProvider(Color(0xFF9E9E9E))
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
