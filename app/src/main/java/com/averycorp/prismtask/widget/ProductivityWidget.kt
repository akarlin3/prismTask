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
 * Productivity Score widget. Shows today's 0-100 score with a trend.
 *
 * Adapts to two size breakpoints:
 * - Small (2x2): just the big score number + trend arrow
 * - Large (3x2+): score + completed/total counts + trend details
 */
class ProductivityWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(120.dp, 120.dp)
        private val LARGE = DpSize(200.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getProductivityData(context)
        } catch (_: Exception) {
            ProductivityWidgetData(0, 0, 0, 0)
        }

        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                ProductivityContent(data, size)
            }
        }
    }
}

@Composable
private fun ProductivityContent(data: ProductivityWidgetData, size: DpSize) {
    val isLarge = size.width >= 200.dp
    val color = when {
        data.score >= 80 -> Color(0xFF2E7D32)
        data.score >= 60 -> Color(0xFFED6C02)
        else -> Color(0xFFC62828)
    }
    val trendArrow = when {
        data.trendPoints > 0 -> "\u2191"
        data.trendPoints < 0 -> "\u2193"
        else -> "\u2013"
    }
    val trendLabel = when {
        data.trendPoints > 0 -> "\u2191 ${data.trendPoints} pts"
        data.trendPoints < 0 -> "\u2193 ${-data.trendPoints} pts"
        else -> "\u2013 no change"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Productivity",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        if (isLarge) {
            // Large layout: score + details side-by-side
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(70.dp)
                        .cornerRadius(35.dp)
                        .background(ColorProvider(color.copy(alpha = 0.15f))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.score.toString(),
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(color)
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(12.dp))
                Column {
                    Text(
                        text = trendLabel,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = "${data.completed}/${data.total} tasks",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    if (data.total > 0) {
                        Text(
                            text = "vs yesterday",
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        } else {
            // Small layout: just score + trend arrow
            Box(
                modifier = GlanceModifier
                    .size(70.dp)
                    .cornerRadius(35.dp)
                    .background(ColorProvider(color.copy(alpha = 0.15f))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.score.toString(),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(color)
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "$trendArrow ${data.completed}/${data.total}",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                )
            )
        }
    }
}

class ProductivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProductivityWidget()
}
