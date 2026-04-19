package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Dual-wheel Start-of-Day picker. The user scrolls two independent LazyColumns —
 * one for hours, one for minutes (at 5-min granularity) — and the centered row
 * is the selected value. Snaps on release.
 *
 * Used by both the first-launch prompt (no dismiss button) and the Settings
 * screen (dismissable).
 */
@Composable
fun StartOfDayPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    dismissable: Boolean,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by rememberSaveable { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var selectedMinute by rememberSaveable {
        // Snap to nearest 5-min step for the wheel.
        mutableIntStateOf((initialMinute.coerceIn(0, 59) / 5) * 5)
    }

    AlertDialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        title = {
            Text(
                text = "When Does Your Day Start?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "This controls when habits reset and streaks roll over. " +
                        "Most people pick between 3–5 AM. Calendar dates and explicit " +
                        "due dates are unaffected.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelColumn(
                        items = (0..23).toList(),
                        initial = selectedHour,
                        format = { "%02d".format(it) },
                        onValueChange = { selectedHour = it }
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    WheelColumn(
                        items = (0..55 step 5).toList(),
                        initial = selectedMinute,
                        format = { "%02d".format(it) },
                        onValueChange = { selectedMinute = it }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatAmPm(selectedHour),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text("Set")
            }
        },
        dismissButton = if (dismissable) {
            {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        } else {
            null
        }
    )
}

private fun formatAmPm(hour: Int): String = when {
    hour == 0 -> "AM"
    hour < 12 -> "AM"
    hour == 12 -> "PM"
    else -> "PM"
}

private const val ITEM_HEIGHT_DP = 40
private const val VISIBLE_ROWS = 3 // padding row + centered row + padding row

/**
 * A snap-scrolling wheel column. Items outside the center row are dimmed.
 * Reports the new value as the user scrolls past each row.
 */
@Composable
private fun WheelColumn(
    items: List<Int>,
    initial: Int,
    format: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    val initialIndex = items.indexOf(initial).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val snapBehavior = rememberSnapFlingBehavior(listState)

    // Report the centered index back up as it changes.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx ->
                val clamped = idx.coerceIn(0, items.size - 1)
                onValueChange(items[clamped])
            }
    }

    Box(
        modifier = Modifier
            .height((ITEM_HEIGHT_DP * VISIBLE_ROWS).dp)
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top padding item so item 0 can center.
            item {
                Box(Modifier.height(ITEM_HEIGHT_DP.dp))
            }
            items.forEachIndexed { i, value ->
                item {
                    val centered = listState.firstVisibleItemIndex == i
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT_DP.dp)
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(i)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = format(value),
                            textAlign = TextAlign.Center,
                            fontSize = if (centered) 22.sp else 18.sp,
                            fontWeight = if (centered) FontWeight.Bold else FontWeight.Normal,
                            color = if (centered) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            // Bottom padding item so last item can center.
            item {
                Box(Modifier.height(ITEM_HEIGHT_DP.dp))
            }
        }

        // Selection indicator lines at the top and bottom of the center row.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT_DP.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

/** Renders "4:00 AM" for use in Settings subtitles. */
fun formatStartOfDay(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(displayHour, minute, suffix)
}
