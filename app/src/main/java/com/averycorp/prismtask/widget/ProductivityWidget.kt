package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.text.Text
import com.averycorp.prismtask.MainActivity

/**
 * Productivity Score widget. Shows today's 0-100 score with a trend.
 *
 * Adapts to two size breakpoints:
 * - Small (2x2): just the big score number + trend arrow
 * - Large (3x2+): score + completed/total counts + trend details + yesterday's score
 */
class ProductivityWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        val data = try {
            WidgetDataProvider.getProductivityData(context)
        } catch (_: Exception) {
            null
        }

        provideContent {
            val size = LocalSize.current
            if (data != null) {
                ProductivityContent(data, size, palette)
            } else {
                WidgetLoadingState(palette)
            }
        }
    }
}

@Composable
private fun ProductivityContent(data: ProductivityWidgetData, size: DpSize, palette: WidgetThemePalette) {
    val isLarge = size.width >= 200.dp
    val scoreColor = when {
        data.score >= 80 -> palette.scoreGreen
        data.score >= 60 -> palette.scoreOrange
        else -> palette.scoreRed
    }
    val scoreBgColor = when {
        data.score >= 80 -> palette.scoreGreenBg
        data.score >= 60 -> palette.scoreOrangeBg
        else -> palette.scoreRedBg
    }
    val trendArrow = when {
        data.trendPoints > 0 -> "↑"
        data.trendPoints < 0 -> "↓"
        else -> "–"
    }
    val trendLabel = when {
        data.trendPoints > 0 -> "↑ ${data.trendPoints} pts"
        data.trendPoints < 0 -> "↓ ${-data.trendPoints} pts"
        else -> "– no change"
    }
    val yesterdayScore = (data.score - data.trendPoints).coerceIn(0, 100)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surface)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Productivity",
            style = WidgetTextStyles.captionMedium(palette.onSurfaceVariant)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        if (data.total == 0) {
            Text(
                text = "0",
                style = WidgetTextStyles.scoreLarge(palette.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Start Your Day!",
                style = WidgetTextStyles.caption(palette.onSurfaceVariant)
            )
        } else if (isLarge) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(70.dp)
                        .cornerRadius(35.dp)
                        .background(scoreBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.score.toString(),
                        style = WidgetTextStyles.scoreLarge(scoreColor)
                    )
                }
                Spacer(modifier = GlanceModifier.width(12.dp))
                Column {
                    Text(
                        text = trendLabel,
                        style = WidgetTextStyles.body(palette.onSurface)
                    )
                    Text(
                        text = "${data.completed}/${data.total} tasks",
                        style = WidgetTextStyles.caption(palette.onSurfaceVariant)
                    )
                    Text(
                        text = "Yesterday: $yesterdayScore",
                        style = WidgetTextStyles.badge(palette.onSurfaceVariant)
                    )
                }
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .size(70.dp)
                    .cornerRadius(35.dp)
                    .background(scoreBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.score.toString(),
                    style = WidgetTextStyles.scoreLarge(scoreColor)
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "$trendArrow ${data.completed}/${data.total}",
                style = WidgetTextStyles.captionMedium(palette.onSurface)
            )
        }
    }
}

class ProductivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProductivityWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
