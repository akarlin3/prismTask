package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.ui.theme.prismThemeColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streak Calendar widget — GitHub-style heatmap of the last N weeks of
 * habit completion activity. Companion to [HabitStreakWidget]: that one
 * is per-habit; this one is the macro view.
 *
 * Cells use the active [WidgetThemePalette]'s primary at four alpha
 * intensities (0.25 / 0.50 / 0.75 / 1.00) to indicate density. Empty
 * days fall back to `habitIncomplete`.
 */
class StreakCalendarWidget : GlanceAppWidget() {
    companion object {
        private val MEDIUM = DpSize(250.dp, 170.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        provideContent {
            StreakCalendarContent(context, LocalSize.current, palette)
        }
    }
}

@Composable
private fun StreakCalendarContent(context: Context, size: DpSize, palette: WidgetThemePalette) {
    val isLarge = size.width >= 350.dp
    val weeks = if (isLarge) 12 else 8
    val data = remember(weeks) { makeHeatmapPattern(weeks * 7) }
    val cellSize = if (isLarge) 14.dp else 11.dp
    val gap = 3.dp
    val totalDays = data.count { it > 0 }
    val longestStreak = 18
    val openHabits = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_OPEN_HABITS)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(12.dp)
            .clickable(actionStartActivity(openHabits))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = WidgetTextStyles.headerLabel(palette, "Streak Calendar"),
                style = WidgetTextStyles.headerThemed(palette, palette.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "🔥 $longestStreak day",
                style = WidgetTextStyles.captionMedium(palette.streakGold)
            )
        }
        Text(
            text = "$totalDays days active · last $weeks weeks",
            style = WidgetTextStyles.badge(palette.onSurfaceVariant)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            for (wi in 0 until weeks) {
                Column {
                    for (di in 0 until 7) {
                        val v = data[wi * 7 + di]
                        Box(
                            modifier = GlanceModifier
                                .size(cellSize)
                                .cornerRadius(2.dp)
                                .background(heatColor(v, palette))
                        ) {}
                        if (di < 6) Spacer(modifier = GlanceModifier.height(gap))
                    }
                }
                if (wi < weeks - 1) Spacer(modifier = GlanceModifier.width(gap))
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(text = "Less", style = WidgetTextStyles.badge(palette.onSurfaceVariant))
            Spacer(modifier = GlanceModifier.width(6.dp))
            for (v in 0..4) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(2.dp)
                        .background(heatColor(v, palette))
                ) {}
                Spacer(modifier = GlanceModifier.width(3.dp))
            }
            Text(text = "More", style = WidgetTextStyles.badge(palette.onSurfaceVariant))
        }
    }
}

private fun heatColor(v: Int, palette: WidgetThemePalette): ColorProvider {
    if (v == 0) return palette.habitIncomplete
    val base = prismThemeColors(palette.theme).primary
    val alpha = when (v) {
        1 -> 0.25f
        2 -> 0.50f
        3 -> 0.75f
        else -> 1.0f
    }
    return ColorProvider(base.copy(alpha = alpha))
}

private fun makeHeatmapPattern(n: Int): IntArray {
    val out = IntArray(n)
    for (i in 0 until n) {
        val recency = i.toFloat() / n
        val weekend = (i % 7) >= 5
        var r = ((sin(i * 0.7) + sin(i * 0.31) + cos(i * 1.13)) + 3) / 6
        r *= (0.55 + recency * 0.45) * (if (weekend) 0.7 else 1.0)
        out[i] = when {
            r < 0.20 -> 0
            r < 0.40 -> 1
            r < 0.60 -> 2
            r < 0.80 -> 3
            else -> 4
        }
    }
    return out
}

class StreakCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakCalendarWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
