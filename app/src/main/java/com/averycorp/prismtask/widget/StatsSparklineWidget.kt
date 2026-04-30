package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.averycorp.prismtask.MainActivity
import kotlin.math.roundToInt

/**
 * Stats Sparkline widget — week-over-week tasks completed trend.
 *
 * The mockup uses an SVG line chart but Glance only supports
 * RemoteViews-friendly primitives, so the visualization here is a
 * 7-bar column chart per day with today highlighted. Today's bar
 * carries the primary accent; the rest carry primary at 0.6 alpha.
 *
 * Header surfaces the delta vs. last week (▲ / ▼ percentage), with
 * the on-color picked from the theme's success / destructive tokens.
 */
class StatsSparklineWidget : GlanceAppWidget() {
    companion object {
        private val SMALL_WIDE = DpSize(200.dp, 100.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
        private val LARGE_WIDE = DpSize(450.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_WIDE, LARGE, LARGE_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        provideContent {
            SparklineContent(context, LocalSize.current, palette)
        }
    }
}

@Composable
private fun SparklineContent(context: Context, size: DpSize, palette: WidgetThemePalette) {
    val isWide = size.width >= 450.dp
    val isSmall = size.height < 130.dp

    // Sample state. Wiring through TaskCompletionRepository.weeklyCounts() is
    // a follow-up.
    val thisWeek = listOf(12, 8, 15, 11, 18, 14, 9)
    val lastWeek = listOf(10, 13, 11, 9, 14, 12, 16)
    val total = thisWeek.sum()
    val lastTotal = lastWeek.sum()
    val delta = total - lastTotal
    val deltaPct = ((delta.toFloat() / lastTotal) * 100).roundToInt()
    val up = delta >= 0
    val maxBar = (thisWeek.maxOrNull() ?: 1).coerceAtLeast(1)

    val openInsights = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_insights")
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(if (isSmall) 11.dp else 12.dp)
            .clickable(actionStartActivity(openInsights))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "This Week",
                style = WidgetTextStyles.captionMedium(palette.onSurfaceVariant),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "${if (up) "▲" else "▼"} ${kotlin.math.abs(deltaPct)}%",
                style = WidgetTextStyles.captionMedium(
                    if (up) palette.successColor else palette.scoreRed
                )
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = total.toString(),
                style = WidgetTextStyles.scoreLargeThemed(palette, palette.onSurface)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "tasks · vs $lastTotal",
                style = WidgetTextStyles.caption(palette.onSurfaceVariant)
            )
        }

        if (!isSmall) Spacer(modifier = GlanceModifier.height(8.dp))

        // Bar chart row — fills remaining vertical space.
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(top = 4.dp)
        ) {
            BarChart(thisWeek, maxBar, palette, isWide)
        }

        if (isWide) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "${(total / 7.0 * 10).roundToInt() / 10.0}/day avg",
                    style = WidgetTextStyles.badge(palette.onSurfaceVariant)
                )
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, max: Int, palette: WidgetThemePalette, isWide: Boolean) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val todayIdx = values.lastIndex
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Bottom
            ) {
                // Pseudo "max-stretch": each bar height is computed in 4dp
                // increments scaled to 0..40dp. Glance composables don't
                // support fractional fills so we approximate the heatmap.
                val barHeight = ((v.toFloat() / max) * 40).coerceAtLeast(2f).dp
                Box(
                    modifier = GlanceModifier
                        .width(if (isWide) 14.dp else 10.dp)
                        .height(barHeight)
                        .cornerRadius(2.dp)
                        .background(if (i == todayIdx) palette.primary else palette.primaryContainer)
                ) {}
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = days[i],
                    style = WidgetTextStyles.badge(
                        if (i == todayIdx) palette.primary else palette.onSurfaceVariant
                    )
                )
            }
        }
    }
}

class StatsSparklineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsSparklineWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
