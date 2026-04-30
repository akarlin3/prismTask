package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.averycorp.prismtask.MainActivity

/**
 * Focus widget — single hero "next thing" card. Where TodayWidget is a
 * list, this picks one task (highest urgency open) and devotes the
 * whole surface to it. Tapping the body opens the task; tapping Start
 * launches the timer with that task pre-selected.
 */
class FocusWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        val data = try {
            WidgetDataProvider.getTodayData(context)
        } catch (_: Exception) {
            null
        }
        provideContent {
            FocusContent(context, LocalSize.current, palette, data)
        }
    }
}

@Composable
private fun FocusContent(
    context: Context,
    size: DpSize,
    palette: WidgetThemePalette,
    data: TodayWidgetData?
) {
    val isSmall = size.width < 200.dp
    // Pick the top task from the live snapshot when present; otherwise
    // fall back to the design mockup so the widget always renders.
    val pick = data?.tasks?.firstOrNull { !it.isCompleted }
    val title = pick?.title ?: "Sketch onboarding flow"
    val taskId = pick?.id
    val priority = pick?.priority ?: 3
    val dueLabel = "Due 2:30 PM · 4 hr"

    val openTask = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (taskId != null) {
            putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_task")
            putExtra("task_id", taskId)
        }
    }
    val startTimer = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_timer")
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(if (isSmall) 12.dp else 14.dp)
            .clickable(actionStartActivity(openTask))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "◎", style = TextStyle(fontSize = 12.sp, color = palette.primary))
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "Focus on",
                style = WidgetTextStyles.captionMedium(palette.primary)
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))

        Text(
            text = title,
            style = TextStyle(
                fontSize = if (isSmall) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = palette.displayFontFamily,
                color = palette.onSurface
            ),
            maxLines = if (isSmall) 2 else 3
        )

        if (!isSmall) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .cornerRadius(3.dp)
                        .background(priorityColorFor(priority, palette))
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Priority",
                    style = WidgetTextStyles.caption(palette.onSurfaceVariant)
                )
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = dueLabel,
                    style = WidgetTextStyles.badge(palette.onSurfaceVariant),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .cornerRadius(14.dp)
                    .background(palette.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(actionStartActivity(startTimer))
            ) {
                Text(
                    text = if (isSmall) "▶" else "▶ Start",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.onPrimary)
                )
            }
        }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
