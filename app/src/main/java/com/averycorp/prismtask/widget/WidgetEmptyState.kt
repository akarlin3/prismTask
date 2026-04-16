package com.averycorp.prismtask.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Standardized empty-state display for all PrismTask widgets.
 *
 * Shows an emoji, a short message, and an optional action button so that
 * every widget looks consistent when there is no data to render.
 */
@Composable
fun WidgetEmptyState(
    emoji: String,
    message: String,
    actionLabel: String? = null,
    actionCallback: Class<out ActionCallback>? = null
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = TextStyle(fontSize = 24.sp))
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            message,
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
        if (actionLabel != null && actionCallback != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            androidx.glance.Button(
                text = actionLabel,
                onClick = actionRunCallback(actionCallback)
            )
        }
    }
}

/**
 * Minimal loading state for Glance widgets.
 *
 * Glance does not support spinners, so we show a centered text indicator
 * that is replaced once the data fetch completes.
 */
@Composable
fun WidgetLoadingState() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Loading\u2026",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}
