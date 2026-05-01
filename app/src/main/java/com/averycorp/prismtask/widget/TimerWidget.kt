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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.averycorp.prismtask.MainActivity

class TimerWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        val state = try {
            TimerStateDataStore.read(context)
        } catch (_: Exception) {
            null
        }
        provideContent {
            val size = LocalSize.current
            if (state != null) {
                TimerWidgetContent(context, state, size, palette)
            } else {
                WidgetLoadingState(palette)
            }
        }
    }
}

@Composable
private fun TimerWidgetContent(
    context: Context,
    state: TimerWidgetState,
    size: DpSize,
    palette: WidgetThemePalette
) {
    val isLarge = size.width >= 200.dp
    val isActive = state.isRunning || state.isPaused
    val isWork = state.sessionType == "work"
    val accentColor = if (isWork) palette.timerWork else palette.timerBreak
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_OPEN_TIMER)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(12.dp)
            .clickable(actionStartActivity(launchIntent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isActive) {
            Text(
                text = WidgetTextStyles.headerLabel(palette, "⏱️ Timer"),
                style = WidgetTextStyles.headerThemed(palette, palette.onSurface)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "Ready to Focus",
                style = WidgetTextStyles.caption(palette.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            val timerIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_OPEN_TIMER)
            }
            Box(
                modifier = GlanceModifier
                    .cornerRadius(20.dp)
                    .background(palette.primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(timerIntent))
            ) {
                Text(
                    text = "▶ Start",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.onPrimary)
                )
            }
        } else {
            val minutes = state.remainingSeconds / 60
            val seconds = state.remainingSeconds % 60
            val timeText = "%d:%02d".format(minutes, seconds)
            val progress = if (state.totalSeconds > 0) 1f - (state.remainingSeconds.toFloat() / state.totalSeconds) else 0f

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

            Text(
                text = timeText,
                style = if (isLarge) {
                    WidgetTextStyles.timerLargeThemed(
                        palette,
                        if (state.isPaused) palette.onSurfaceVariant else palette.onSurface
                    )
                } else {
                    WidgetTextStyles.timerSmallThemed(
                        palette,
                        if (state.isPaused) palette.onSurfaceVariant else palette.onSurface
                    )
                }
            )

            if (isLarge && state.currentTaskTitle != null) {
                Text(
                    text = state.currentTaskTitle,
                    style = WidgetTextStyles.badge(palette.onSurfaceVariant),
                    maxLines = 1
                )
            }

            if (isLarge && isWork && state.currentSession < state.totalSessions) {
                val isLongBreak = state.currentSession % 4 == 0 && state.currentSession > 0
                val breakDuration = if (isLongBreak) "15 min break next" else "5 min break next"
                Text(
                    text = breakDuration,
                    style = WidgetTextStyles.badge(palette.timerBreak)
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(if (isLarge) 8.dp else 3.dp),
                color = accentColor,
                backgroundColor = palette.surfaceVariant
            )
            Spacer(modifier = GlanceModifier.height(6.dp))

            // No pause/resume/skip controls here: the live countdown lives
            // in TimerViewModel.viewModelScope, which doesn't observe this
            // DataStore, so any widget-side mutation is overwritten by the
            // next ViewModel sync. The whole widget is clickable to open
            // the Timer screen, where the in-app controls work.
            Text(
                text = if (state.isPaused) "Tap to resume" else "Tap to manage",
                style = WidgetTextStyles.caption(palette.onSurfaceVariant)
            )
        }
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
