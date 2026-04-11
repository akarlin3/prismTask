package com.averycorp.prismtask.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
 * Productivity Score widget. Shows today's 0-100 score as a large number
 * with a colored backing, trend delta since yesterday, and an open-app
 * tap target. Fits a 2x2 or 3x2 cell.
 */
class ProductivityWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            WidgetDataProvider.getProductivityData(context)
        } catch (_: Exception) {
            ProductivityWidgetData(0, 0, 0, 0)
        }

        provideContent {
            GlanceTheme {
                ProductivityContent(data)
            }
        }
    }
}

@Composable
private fun ProductivityContent(data: ProductivityWidgetData) {
    val color = when {
        data.score >= 80 -> Color(0xFF2E7D32)
        data.score >= 60 -> Color(0xFFED6C02)
        else -> Color(0xFFC62828)
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
            text = trendLabel,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurface
            )
        )
        Text(
            text = "${data.completed}/${data.total} tasks",
            style = TextStyle(
                fontSize = 10.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

class ProductivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProductivityWidget()
}
