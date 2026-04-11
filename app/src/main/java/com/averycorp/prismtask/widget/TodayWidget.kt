package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
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
import com.averycorp.prismtask.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today home screen widget. Displays a compact summary for the current
 * day: productivity score, task progress, an interactive checklist of the
 * next tasks, and a habit summary strip.
 *
 * Tapping a checkbox toggles completion directly from the widget via
 * [ToggleTaskFromWidgetAction]. Tapping the footer opens the Today screen
 * in the app.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getTodayData(context)
        } catch (_: Exception) {
            TodayWidgetData(0, 0, emptyList(), 0, 0, emptyList(), 0)
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
    val total = data.totalTasks
    val completed = data.completedTasks
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val todayLabel = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
    ) {
        // Header row: title + date + score badge
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Text(
                    text = todayLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            ScoreBadge(score = data.productivityScore)
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "$completed of $total done",
            style = TextStyle(
                fontSize = 10.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.tasks.isEmpty()) {
            Text(
                text = "\uD83C\uDF89 Nothing on your plate",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            data.tasks.take(5).forEach { task ->
                WidgetTaskRowView(task)
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
        }

        if (data.totalHabits > 0) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${data.completedHabits}/${data.totalHabits} habits",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.secondary,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                data.habitIcons.take(5).forEach { icon ->
                    Text(text = icon, style = TextStyle(fontSize = 11.sp))
                    Spacer(modifier = GlanceModifier.width(2.dp))
                }
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
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun WidgetTaskRowView(task: WidgetTaskRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tappable checkbox — toggles completion directly
        Box(
            modifier = GlanceModifier
                .size(18.dp)
                .cornerRadius(4.dp)
                .background(
                    if (task.isCompleted) GlanceTheme.colors.primary
                    else GlanceTheme.colors.surfaceVariant
                )
                .clickable(
                    actionRunCallback<ToggleTaskFromWidgetAction>(
                        parameters = taskIdParams(task.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (task.isCompleted) {
                Text(
                    text = "\u2713",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        // Priority dot
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .cornerRadius(3.dp)
                .background(priorityColorFor(task.priority))
        ) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
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
            maxLines = 1
        )
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val backgroundColor = when {
        score >= 80 -> ColorProvider(Color(0xFF2E7D32))
        score >= 60 -> ColorProvider(Color(0xFFED6C02))
        else -> ColorProvider(Color(0xFFC62828))
    }
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score.toString(),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ColorProvider(Color.White)
            )
        )
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
