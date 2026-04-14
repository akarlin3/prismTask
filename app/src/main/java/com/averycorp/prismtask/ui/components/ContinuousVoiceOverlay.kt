package com.averycorp.prismtask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen hands-free voice overlay. When visible, the user can speak
 * successive commands; each transcript is consumed by the parent via
 * [onTranscript] which is expected to either execute a voice command or
 * create a task. Tapping anywhere outside the waveform exits the overlay.
 */
@Composable
fun ContinuousVoiceOverlay(
    visible: Boolean,
    listening: Boolean,
    transcript: String,
    rmsLevel: Float,
    onExit: () -> Unit,
    onRestart: () -> Unit
) {
    // When the mic drops back to idle mid-overlay (utterance finished), auto-
    // restart so the next phrase is captured without user intervention.
    LaunchedEffect(visible, listening) {
        if (visible && !listening) {
            kotlinx.coroutines.delay(250)
            if (visible) onRestart()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExit
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Voice Mode",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (listening) "Listening…" else "Processing…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Waveform(
                    rmsLevel = rmsLevel,
                    active = listening,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = transcript.ifBlank { "Say something…" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Try: \"What's next?\", \"Complete buy milk\",\n" +
                        "\"How many tasks today?\", \"Exit\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(onClick = onExit) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Exit Voice Mode")
                }
            }

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close voice mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun Waveform(rmsLevel: Float, active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val smoothed = remember { Animatable(0f) }
    LaunchedEffect(rmsLevel) {
        smoothed.animateTo(rmsLevel, tween(120))
    }
    Canvas(modifier = Modifier.size(180.dp)) {
        val base = size.minDimension / 2f
        if (!active) {
            drawCircle(color = color.copy(alpha = 0.2f), radius = base * 0.5f)
            return@Canvas
        }
        for (i in 0..2) {
            val p = ((phase + i / 3f) % 1f)
            val r = base * (0.4f + p * 0.6f * (0.7f + smoothed.value * 0.6f))
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.5f),
                radius = r,
                style = Stroke(width = 3f + smoothed.value * 6f)
            )
        }
        drawCircle(color = color.copy(alpha = 0.85f), radius = base * 0.28f)
    }
}
