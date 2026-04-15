package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.QuickAddBar

/**
 * Floating variant of [QuickAddBar] used on the Today screen — sits in
 * the Scaffold's bottomBar slot with a slightly translucent surface and
 * elevation so it visually floats above the task list.
 */
@Composable
internal fun FloatingQuickAddBar(
    autoStartVoice: Boolean = false,
    onVoiceAutoStartConsumed: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            QuickAddBar(
                autoStartVoice = autoStartVoice,
                onVoiceMessage = { }
            )
            LaunchedEffect(autoStartVoice) {
                if (autoStartVoice) onVoiceAutoStartConsumed()
            }
        }
    }
}
