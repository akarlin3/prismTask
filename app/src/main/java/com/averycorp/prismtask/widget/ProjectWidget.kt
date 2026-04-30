package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

/**
 * Home-screen widget for a single user-picked project.
 *
 * Content layout (Phase 3):
 * - Header: theme-color stripe + icon + project name (1 line, ellipsized) +
 *   streak flame.
 * - Progress bar keyed to milestone completion.
 * - Upcoming milestone title, or next-due task title when the project has
 *   no open milestones.
 * - Footer: task count + "N days since activity" badge (only when > 3 days).
 *
 * The project's per-instance theme accent (a hex color stored on the
 * project) drives the stripe + progress bar fill. Surrounding chrome
 * (surface, on-surface, error) is themed by the user's selected
 * [com.averycorp.prismtask.ui.theme.PrismTheme] via [WidgetThemePalette].
 */
class ProjectWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(150.dp, 100.dp)
        private val MEDIUM = DpSize(250.dp, 150.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        val data = runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            val config = WidgetConfigDataStore.snapshotProjectConfig(context, appWidgetId)
            config.projectId?.let { projectId ->
                WidgetDataProvider.getProjectData(context, projectId)
            }
        }.getOrNull()

        provideContent {
            val size = LocalSize.current
            ProjectWidgetContent(context = context, data = data, size = size, palette = palette)
        }
    }
}

@Composable
private fun ProjectWidgetContent(
    context: Context,
    data: ProjectWidgetData?,
    size: DpSize,
    palette: WidgetThemePalette
) {
    val openApp = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val isSmall = size.width < 250.dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .clickable(actionStartActivity(openApp))
    ) {
        val stripeColor = parseStripeColor(data?.themeColorHex, palette)
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .fillMaxHeight()
                .background(stripeColor)
        ) { }

        if (data == null) {
            EmptyState(isSmall = isSmall, palette = palette)
            return@Row
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(if (isSmall) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(text = data.icon, style = TextStyle(fontSize = 16.sp))
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = WidgetTextStyles.headerLabel(palette, data.name),
                    style = WidgetTextStyles.headerThemed(palette, palette.onSurface),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (data.streak > 0) {
                    Text(
                        text = "🔥 ${data.streak}",
                        style = WidgetTextStyles.captionMedium(palette.streakFire)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            ProgressTrack(progress = data.milestoneProgress, accent = stripeColor, palette = palette)

            Spacer(modifier = GlanceModifier.height(6.dp))

            val headline = data.upcomingMilestoneTitle ?: data.nextDueTaskTitle
            if (headline != null) {
                Text(
                    text = if (data.upcomingMilestoneTitle != null) "Next: $headline" else "Task: $headline",
                    style = WidgetTextStyles.caption(palette.onSurfaceVariant),
                    maxLines = 1
                )
            } else {
                Text(
                    text = "All Caught Up",
                    style = WidgetTextStyles.caption(palette.onSurfaceVariant),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                val done = (data.totalTasks - data.openTasks).coerceAtLeast(0)
                Text(
                    text = if (data.totalTasks > 0) "$done/${data.totalTasks}" else "No Tasks",
                    style = WidgetTextStyles.badge(palette.onSurfaceVariant)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                val daysSince = data.daysSinceActivity
                if (daysSince != null && daysSince > 3) {
                    Text(
                        text = "$daysSince d idle",
                        style = WidgetTextStyles.badgeBold(palette.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float, accent: ColorProvider, palette: WidgetThemePalette) {
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .cornerRadius(3.dp)
            .background(palette.surfaceVariant)
    ) {
        if (safe > 0f) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width((200 * safe).dp.coerceAtLeast(2.dp))
                    .cornerRadius(3.dp)
                    .background(accent)
            ) { }
        }
    }
}

@Composable
private fun EmptyState(isSmall: Boolean, palette: WidgetThemePalette) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "📂",
            style = TextStyle(fontSize = if (isSmall) 20.sp else 28.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap To Configure",
            style = WidgetTextStyles.caption(palette.onSurfaceVariant)
        )
    }
}

/**
 * Parse a project's stored hex (or future theme-token shim) into a
 * [ColorProvider] for the Glance stripe. Falls back to the user's
 * selected PrismTheme primary so the stripe still feels themed when
 * the project lacks a custom color.
 */
private fun parseStripeColor(hex: String?, palette: WidgetThemePalette): ColorProvider {
    val parsed = hex
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        }
    return if (parsed != null) ColorProvider(parsed) else palette.primary
}

class ProjectWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProjectWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
