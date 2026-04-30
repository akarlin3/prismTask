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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

/**
 * Eisenhower Matrix widget — at-a-glance 2×2 quadrant snapshot.
 *
 * Each quadrant cell carries the count of tasks currently routed to it
 * plus the headline task in that bucket. Colors are pulled from the
 * active [WidgetThemePalette]'s `quadrantQ*` tokens, which mirror the
 * in-app Eisenhower screen.
 *
 * Tapping anywhere on the widget opens the Eisenhower screen in
 * MainActivity (via the standard launch intent).
 */
class EisenhowerWidget : GlanceAppWidget() {
    companion object {
        private val MEDIUM = DpSize(250.dp, 150.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        provideContent {
            EisenhowerContent(context, LocalSize.current, palette)
        }
    }
}

private data class Quad(
    val key: String,
    val label: String,
    val count: Int,
    val color: ColorProvider,
    val bgColor: ColorProvider,
    val top: String
)

@Composable
private fun EisenhowerContent(context: Context, size: DpSize, palette: WidgetThemePalette) {
    val isLarge = size.width >= 350.dp
    // Sample state — wiring through a TaskRepository.eisenhowerCounts() is a
    // follow-up. Headlines mirror the design mockup so the widget renders
    // recognizably even before data is plumbed.
    val quads = listOf(
        Quad("Q1", "Do", 3, palette.quadrantQ1, palette.quadrantQ1Bg, "Pay parking ticket"),
        Quad("Q2", "Schedule", 5, palette.quadrantQ2, palette.quadrantQ2Bg, "Sketch onboarding flow"),
        Quad("Q3", "Delegate", 2, palette.quadrantQ3, palette.quadrantQ3Bg, "Reply to design crit"),
        Quad("Q4", "Drop", 4, palette.quadrantQ4, palette.quadrantQ4Bg, "News scroll session")
    )
    val total = quads.sumOf { it.count }
    val openMatrix = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_matrix")
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(if (isLarge) 12.dp else 10.dp)
            .clickable(actionStartActivity(openMatrix))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = WidgetTextStyles.headerLabel(palette, "Matrix"),
                style = WidgetTextStyles.headerThemed(palette, palette.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "$total tasks",
                style = WidgetTextStyles.badge(palette.onSurfaceVariant)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))

        // 2×2 grid: two rows, two quads each.
        QuadRow(quads[0], quads[1], palette = palette, compact = !isLarge)
        Spacer(modifier = GlanceModifier.height(5.dp))
        QuadRow(quads[2], quads[3], palette = palette, compact = !isLarge)
    }
}

@Composable
private fun QuadRow(left: Quad, right: Quad, palette: WidgetThemePalette, compact: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        QuadCell(left, palette = palette, compact = compact, modifier = GlanceModifier.defaultWeight())
        Spacer(modifier = GlanceModifier.width(5.dp))
        QuadCell(right, palette = palette, compact = compact, modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun QuadCell(q: Quad, palette: WidgetThemePalette, compact: Boolean, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .cornerRadius(8.dp)
            .background(q.bgColor)
            .padding(if (compact) 6.dp else 8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = q.key, style = WidgetTextStyles.badgeBold(q.color))
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = q.label,
                    style = WidgetTextStyles.captionMedium(palette.onSurface),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = q.count.toString(),
                    style = WidgetTextStyles.bodyBold(q.color)
                )
            }
            if (!compact) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = q.top,
                    style = WidgetTextStyles.badge(palette.onSurfaceVariant),
                    maxLines = 1
                )
            }
        }
    }
}

class EisenhowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EisenhowerWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
