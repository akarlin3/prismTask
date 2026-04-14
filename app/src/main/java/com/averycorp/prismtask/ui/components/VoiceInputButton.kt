package com.averycorp.prismtask.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Circular microphone button with a pulsing-ring animation while active.
 *
 * When [listening] is true, three concentric circles expand and fade out at
 * staggered offsets behind the mic icon to signal "recording". The pulse
 * radius is modulated by [rmsLevel] so the rings breathe with the user's
 * voice volume. When idle the button renders a plain mic icon.
 *
 * Long-press (if [onLongClick] is provided) enters continuous hands-free
 * voice mode. A regular tap toggles a single utterance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceInputButton(
    listening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tintIdle: Color = MaterialTheme.colorScheme.primary,
    tintActive: Color = MaterialTheme.colorScheme.error,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (listening) {
            PulseRings(color = tintActive, rmsLevel = rmsLevel)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (listening) {
                        tintActive.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }
                ).combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button
                ).semantics {
                    contentDescription =
                        if (listening) {
                            "Stop voice input"
                        } else {
                            "Voice input. Long press for hands free mode"
                        }
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = null,
                tint = if (listening) tintActive else tintIdle
            )
        }
    }
}

@Composable
private fun PulseRings(color: Color, rmsLevel: Float) {
    val transition = rememberInfiniteTransition(label = "voice-pulse")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 466, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 933, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Smooth RMS follower so the rings don't jitter frame-to-frame.
    val smoothedRms = remember { Animatable(0f) }
    LaunchedEffect(rmsLevel) {
        smoothedRms.animateTo(rmsLevel, tween(150, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = Modifier.size(56.dp)) {
        val base = size.minDimension / 2f
        val intensityBoost = 0.6f + smoothedRms.value * 0.6f
        listOf(phase1, phase2, phase3).forEach { p ->
            val radius = base * (0.55f + p * 0.9f * intensityBoost)
            val alpha = (1f - p) * 0.45f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                style = Stroke(
                    width = 2.5f + smoothedRms.value * 2.5f
                )
            )
        }
    }
}

/**
 * Convenience function composing the sensor button and optional continuous
 * overlay. When [showOverlay] is true, the full-screen voice overlay is
 * rendered on top of content and the exit callback triggers
 * [onStopContinuous].
 */
@Composable
fun VoiceModeHost(
    showOverlay: Boolean,
    listening: Boolean,
    transcript: String,
    rmsLevel: Float,
    onStopContinuous: () -> Unit,
    onRestartListening: () -> Unit
) {
    ContinuousVoiceOverlay(
        visible = showOverlay,
        listening = listening,
        transcript = transcript,
        rmsLevel = rmsLevel,
        onExit = onStopContinuous,
        onRestart = onRestartListening
    )
}
