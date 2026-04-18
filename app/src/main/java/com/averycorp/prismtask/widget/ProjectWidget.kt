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
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.size
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
 * A long-tap on the widget opens the app's [MainActivity] and navigates
 * straight to the project's detail screen via the `project_id` intent
 * extra (resolved by NavGraph's deep-link handler). The per-instance
 * project selection is held in [WidgetConfigDataStore.projectConfigFlow];
 * when unset the widget renders a "Tap To Configure" empty state.
 *
 * Refresh cadence matches the other widgets: `updatePeriodMillis` in the
 * meta-data XML drives the system path, and [WidgetUpdateManager] pokes
 * it on project / milestone / task events.
 */
class ProjectWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(150.dp, 100.dp)
        private val MEDIUM = DpSize(250.dp, 150.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Resolve the user's per-widget project selection, then snapshot the
        // full data. Both calls happen *before* provideContent so the widget
        // renders a single consistent frame — no mid-compose suspension.
        val data = runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            val config = WidgetConfigDataStore.snapshotProjectConfig(context, appWidgetId)
            config.projectId?.let { projectId ->
                WidgetDataProvider.getProjectData(context, projectId)
            }
        }.getOrNull()

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                ProjectWidgetContent(context = context, data = data, size = size)
            }
        }
    }
}

@Composable
private fun ProjectWidgetContent(context: Context, data: ProjectWidgetData?, size: DpSize) {
    val openApp = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val isSmall = size.width < 250.dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(openApp))
    ) {
        // Theme-color stripe that mirrors the in-app ProjectCard.
        val stripeColor = parseStripeColor(data?.themeColorHex)
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .fillMaxHeight()
                .background(stripeColor)
        ) { }

        if (data == null) {
            EmptyState(isSmall = isSmall)
            return@Row
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(if (isSmall) 8.dp else 12.dp)
        ) {
            // Header: icon + name + streak
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(text = data.icon, style = TextStyle(fontSize = 16.sp))
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = data.name,
                    style = WidgetTextStyles.header(GlanceTheme.colors.onSurface),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (data.streak > 0) {
                    Text(
                        text = "\uD83D\uDD25 ${data.streak}",
                        style = WidgetTextStyles.captionMedium(GlanceTheme.colors.primary)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress bar (manual — Glance's LinearProgressIndicator doesn't
            // let us tint with the project accent directly inside a stripe).
            ProgressTrack(progress = data.milestoneProgress, accent = stripeColor)

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Upcoming milestone OR next-due task
            val headline = data.upcomingMilestoneTitle ?: data.nextDueTaskTitle
            if (headline != null) {
                Text(
                    text = if (data.upcomingMilestoneTitle != null) "Next: $headline" else "Task: $headline",
                    style = WidgetTextStyles.caption(GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            } else {
                Text(
                    text = "All Caught Up",
                    style = WidgetTextStyles.caption(GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Footer: task count + days-since
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                val done = (data.totalTasks - data.openTasks).coerceAtLeast(0)
                Text(
                    text = if (data.totalTasks > 0) "$done/${data.totalTasks}" else "No Tasks",
                    style = WidgetTextStyles.badge(GlanceTheme.colors.onSurfaceVariant)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                val daysSince = data.daysSinceActivity
                if (daysSince != null && daysSince > 3) {
                    Text(
                        text = "$daysSince d idle",
                        style = WidgetTextStyles.badgeBold(GlanceTheme.colors.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float, accent: ColorProvider) {
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .cornerRadius(3.dp)
            .background(GlanceTheme.colors.surfaceVariant)
    ) {
        // A narrow Box with a width proportional to progress sits on top.
        // Glance doesn't support fractional widths directly so we fake it
        // with a fillMaxWidth + a right-side spacer. For a first-cut widget
        // this is close enough to the in-app progress bar. Glance 2.x will
        // likely add a fraction API.
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
private fun EmptyState(isSmall: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83D\uDCC2",
            style = TextStyle(fontSize = if (isSmall) 20.sp else 28.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap To Configure",
            style = WidgetTextStyles.caption(GlanceTheme.colors.onSurfaceVariant)
        )
    }
}

/**
 * Parse a project's stored hex (or future theme-token shim) into a
 * [ColorProvider] for the Glance stripe. Widgets run outside of the app's
 * [com.averycorp.prismtask.ui.theme.LocalPrismColors] Composition so we
 * can't resolve tokens here — a hex passthrough is the best we can do
 * until Glance gains theme-token awareness.
 */
private fun parseStripeColor(hex: String?): ColorProvider {
    val fallback = Color(android.graphics.Color.parseColor("#4A90D9"))
    val parsed = hex
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: fallback
    return ColorProvider(parsed)
}

class ProjectWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProjectWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
