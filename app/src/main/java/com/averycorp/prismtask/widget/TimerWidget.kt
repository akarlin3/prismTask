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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

class TimerWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = try {
            TimerStateDataStore.read(context)
        } catch (_: Exception) {
            null
        }
        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                if (state != null) {
                    TimerWidgetContent(context, state, size)
                } else {
                    WidgetLoadingState()
                }
            }
        }
    }
}

@Composable
private fun TimerWidgetContent(context: Context, state: TimerWidgetState, size: DpSize) {
    val isLarge = size.width >= 200.dp
    val isActive = state.isRunning || state.isPaused
    val isWork = state.sessionType == "work"
    val accentColor = if (isWork) WidgetColors.timerWork else WidgetColors.timerBreak
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity(launchIntent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isActive) {
            // Idle state: "Ready to Focus" with start button
            Text(
                text = "\u23F1\uFE0F Timer",
                style = WidgetTextStyles.header(GlanceTheme.colors.onSurface)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "Ready to Focus",
                style = WidgetTextStyles.caption(GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            val timerIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_timer")
            }
            Box(
                modifier = GlanceModifier
                    .cornerRadius(20.dp)
                    .background(GlanceTheme.colors.primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(timerIntent))
            ) {
                Text(
                    text = "\u25B6 Start",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary)
                )
            }
        } else {
            val minutes = state.remainingSeconds / 60
            val seconds = state.remainingSeconds % 60
            val timeText = "%d:%02d".format(minutes, seconds)
            val progress = if (state.totalSeconds > 0) 1f - (state.remainingSeconds.toFloat() / state.totalSeconds) else 0f

            // Session info: "Session 2 of 4" or "Break Time"
            val sessionLabel = if (isWork) {
                "Session ${state.currentSession} of ${state.totalSessions}"
            } else {
                "Break Time"
            }
            Text(
                text = sessionLabel,
                style = WidgetTextStyles.badgeBold(accentColor)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            // Main countdown
            Text(
                text = timeText,
                style = if (isLarge) {
                    WidgetTextStyles.timerLarge(
                        if (state.isPaused) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
                    )
                } else {
                    WidgetTextStyles.timerSmall(
                        if (state.isPaused) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
                    )
                }
            )

            // Task title (large only)
            if (isLarge && state.currentTaskTitle != null) {
                Text(
                    text = state.currentTaskTitle,
                    style = WidgetTextStyles.badge(GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            }

            // Next break info (large work session only)
            if (isLarge && isWork && state.currentSession < state.totalSessions) {
                val isLongBreak = state.currentSession % 4 == 0 && state.currentSession > 0
                val breakDuration = if (isLongBreak) "15 min break next" else "5 min break next"
                Text(
                    text = breakDuration,
                    style = WidgetTextStyles.badge(WidgetColors.timerBreak)
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Progress bar — increased height for large breakpoint
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(if (isLarge) 8.dp else 3.dp),
                color = accentColor,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
            Spacer(modifier = GlanceModifier.height(6.dp))

            // Control buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isPaused) {
                    WidgetButton("\u25B6", accentColor, actionRunCallback<ResumeTimerAction>())
                    if (isLarge) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        WidgetButton("\u25A0", WidgetColors.timerStop, actionRunCallback<StopTimerAction>())
                    }
                } else {
                    WidgetButton("\u23F8", accentColor, actionRunCallback<PauseTimerAction>())
                    if (isLarge && !isWork) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        WidgetButton("\u23ED", GlanceTheme.colors.secondaryContainer, actionRunCallback<SkipBreakAction>())
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetButton(text: String, backgroundColor: ColorProvider, onClick: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(16.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onColored)
        )
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
