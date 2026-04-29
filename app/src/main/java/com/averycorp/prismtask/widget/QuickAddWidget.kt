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
import androidx.glance.text.TextStyle
import com.averycorp.prismtask.MainActivity
import java.util.Calendar

class QuickAddWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(200.dp, 40.dp)
        private val LARGE = DpSize(250.dp, 100.dp)
        internal val PLACEHOLDERS =
            listOf("What's on your mind?", "Add a task...", "What needs doing?", "Plan something great...", "Quick capture...")
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        val templates = try {
            WidgetDataProvider.getTopTemplates(context, limit = 3)
        } catch (_: Exception) {
            emptyList()
        }
        provideContent {
            val size = LocalSize.current
            QuickAddContent(context, templates, size, palette)
        }
    }
}

@Composable
private fun QuickAddContent(
    context: Context,
    templates: List<TemplateShortcut>,
    size: DpSize,
    palette: WidgetThemePalette
) {
    val isLarge = size.height >= 100.dp
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val placeholder = QuickAddWidget.PLACEHOLDERS[dayOfYear % QuickAddWidget.PLACEHOLDERS.size]
    val addTaskIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_QUICK_ADD)
    }
    val voiceIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_VOICE_INPUT)
    }
    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp).background(palette.background)) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(28.dp)
                .background(palette.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "◆", style = TextStyle(fontSize = 16.sp, color = palette.primary))
            Spacer(modifier = GlanceModifier.width(10.dp))
            Box(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(addTaskIntent))) {
                Text(
                    text = placeholder,
                    style = WidgetTextStyles.body(palette.onSurfaceVariant)
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .cornerRadius(18.dp)
                    .background(palette.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable(actionStartActivity(voiceIntent))
            ) {
                Text(text = "🎤", style = TextStyle(fontSize = 16.sp, color = palette.onPrimaryContainer))
            }
        }
        if (isLarge && templates.isNotEmpty()) {
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
                            .cornerRadius(8.dp)
                            .background(palette.secondaryContainer)
                            .padding(vertical = 8.dp)
                            .clickable(actionStartActivity(tplIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = tpl.icon, style = TextStyle(fontSize = 16.sp))
                            Text(
                                text = tpl.name.take(10),
                                style = WidgetTextStyles.badge(palette.onSecondaryContainer),
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
