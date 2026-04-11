package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
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
import com.averycorp.prismtask.MainActivity

/**
 * Quick-Add home screen widget.
 *
 * Search-bar style row with a PrismTask icon on the left, a dimmed
 * "Add a task..." placeholder that launches the quick-add flow, and a
 * trailing mic button that launches the app directly into voice input
 * mode. At 4x2 and above, a second row of template shortcut tiles is
 * shown below the bar.
 */
class QuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val templates = try {
            WidgetDataProvider.getTopTemplates(context, limit = 3)
        } catch (_: Exception) {
            emptyList()
        }
        provideContent {
            GlanceTheme {
                QuickAddContent(context, templates)
            }
        }
    }
}

@Composable
private fun QuickAddContent(
    context: Context,
    templates: List<TemplateShortcut>
) {
    val addTaskIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_QUICK_ADD)
    }
    val voiceIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_VOICE_INPUT)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(10.dp)
            .background(GlanceTheme.colors.background)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u25C6",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = GlanceTheme.colors.primary
                )
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(addTaskIntent))
            ) {
                Text(
                    text = "Add a task...",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .cornerRadius(18.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable(actionStartActivity(voiceIntent))
            ) {
                Text(
                    text = "\uD83C\uDFA4",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
        }

        if (templates.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                templates.take(3).forEachIndexed { index, tpl ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                    val tplIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_OPEN_TEMPLATES)
                    }
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(12.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .padding(vertical = 8.dp)
                            .clickable(actionStartActivity(tplIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tpl.icon,
                                style = TextStyle(fontSize = 16.sp)
                            )
                            Text(
                                text = tpl.name.take(10),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = GlanceTheme.colors.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
