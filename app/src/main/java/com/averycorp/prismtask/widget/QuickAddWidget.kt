package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.averycorp.prismtask.MainActivity

class QuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickAddContent(context)
            }
        }
    }
}

@Composable
private fun QuickAddContent(context: Context) {
    // Two-button row: the main "Add Task" target plus a compact templates
    // shortcut that deep-links the user into the template list screen.
    // Both routes launch MainActivity with an intent extra that
    // MainActivity reads on cold/warm start to pick the initial route.
    val addTaskIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_QUICK_ADD)
    }
    val templatesIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, MainActivity.ACTION_OPEN_TEMPLATES)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(GlanceTheme.colors.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(addTaskIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2705",
                style = TextStyle(fontSize = 18.sp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Add Task...",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.secondary
                )
            )
        }
        Box(
            modifier = GlanceModifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clickable(actionStartActivity(templatesIntent))
        ) {
            Text(
                text = "\uD83D\uDCCB",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                )
            )
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
