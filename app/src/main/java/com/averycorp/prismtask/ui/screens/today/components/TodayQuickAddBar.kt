package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.QuickAddBar
import com.averycorp.prismtask.ui.theme.ChipShape
import com.averycorp.prismtask.ui.theme.LocalPrismAttrs
import com.averycorp.prismtask.ui.theme.LocalPrismColors

/**
 * Floating variant of [QuickAddBar] used on the Today screen — sits in the
 * Scaffold's bottomBar slot with theme-specific styling:
 *
 * - CYBERPUNK: dashed primary-colored border, no fill background.
 * - SYNTHWAVE: horizontal gradient tint (primary→secondary at low alpha).
 * - MATRIX:    solid 1dp primary border, transparent background.
 * - VOID:      standard frosted surface (no decoration).
 */
@Composable
internal fun FloatingQuickAddBar(
    autoStartVoice: Boolean = false,
    onVoiceAutoStartConsumed: () -> Unit = {}
) {
    val colors = LocalPrismAttrs.current.let { _ -> LocalPrismColors.current }
    val attrs = LocalPrismAttrs.current

    val barShape = RoundedCornerShape(
        topStart = attrs.cardRadius.dp,
        topEnd = attrs.cardRadius.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    when {
        // Cyberpunk — dashed neon border, transparent fill
        attrs.brackets -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(colors.primary.copy(alpha = 0.6f), colors.secondary.copy(alpha = 0.6f))
                        ),
                        shape = barShape
                    )
                    .padding(vertical = 6.dp)
            ) {
                QuickAddBar(autoStartVoice = autoStartVoice, onVoiceMessage = {})
            }
        }

        // Synthwave — gradient tint behind the bar
        attrs.sunset -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.12f),
                                colors.secondary.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .background(colors.background.copy(alpha = 0.82f))
                    .padding(vertical = 6.dp)
            ) {
                QuickAddBar(autoStartVoice = autoStartVoice, onVoiceMessage = {})
            }
        }

        // Matrix — solid thin primary border, dark background
        attrs.terminal -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .border(1.dp, colors.primary.copy(alpha = 0.5f), barShape)
                    .padding(vertical = 6.dp)
            ) {
                QuickAddBar(autoStartVoice = autoStartVoice, onVoiceMessage = {})
            }
        }

        // Void / default — frosted translucent surface
        else -> {
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
                    QuickAddBar(autoStartVoice = autoStartVoice, onVoiceMessage = {})
                }
            }
        }
    }

    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) onVoiceAutoStartConsumed()
    }
}
