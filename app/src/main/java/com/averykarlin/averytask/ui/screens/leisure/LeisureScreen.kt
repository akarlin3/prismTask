package com.averykarlin.averytask.ui.screens.leisure

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay

data class LeisureOption(val id: String, val label: String, val icon: String)

private val instruments = listOf(
    LeisureOption("bass", "Bass", "\uD83C\uDFB8"),
    LeisureOption("guitar", "Guitar", "\uD83C\uDFB8"),
    LeisureOption("drums", "Drums", "\uD83E\uDD41"),
    LeisureOption("piano", "Piano", "\uD83C\uDFB9"),
    LeisureOption("singing", "Singing", "\uD83C\uDFA4"),
)

private val flexOptions = listOf(
    LeisureOption("read", "Read", "\uD83D\uDCD6"),
    LeisureOption("gaming", "Gaming", "\uD83C\uDFAE"),
    LeisureOption("cook", "Cook something new", "\uD83C\uDF73"),
    LeisureOption("watch", "Watch a show or movie", "\uD83D\uDCFA"),
    LeisureOption("boardgame", "Board game / puzzle", "\uD83E\uDDE9"),
)

private val musicColor = Color(0xFF8B5CF6)
private val successColor = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeisureScreen(
    navController: NavController,
    viewModel: LeisureViewModel = hiltViewModel()
) {
    val log by viewModel.todayLog.collectAsStateWithLifecycle()

    val musicPick = log?.musicPick
    val musicDone = log?.musicDone ?: false
    val flexPick = log?.flexPick
    val flexDone = log?.flexDone ?: false
    val startedAt = log?.startedAt

    val doneCount = (if (musicDone) 1 else 0) + (if (flexDone) 1 else 0)
    val allDone = doneCount == 2
    val progress = doneCount / 2f

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        if (startedAt != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000
                delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "DAILY",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Leisure Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    if (startedAt != null) {
                        Text(
                            "\u23F1 ${formatElapsed(elapsedSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.resetToday() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Progress card
            ProgressCard(doneCount = doneCount, progress = progress, allDone = allDone)

            Spacer(Modifier.height(20.dp))

            // Music Practice section
            SectionHeader(icon = "\uD83C\uDFB5", title = "Music Practice \u2014 Pick One (15 min)")
            Spacer(Modifier.height(8.dp))
            ActivitySection(
                options = instruments,
                picked = musicPick,
                done = musicDone,
                accentColor = musicColor,
                duration = "15 min",
                columns = 3,
                onPick = { viewModel.pickMusic(it) },
                onDone = { viewModel.toggleMusicDone(true) },
                onClear = { viewModel.clearMusicPick() }
            )

            Spacer(Modifier.height(20.dp))

            // Flexible Activity section
            SectionHeader(icon = "\uD83C\uDFB2", title = "Flexible \u2014 Pick One (30 min)")
            Spacer(Modifier.height(8.dp))
            ActivitySection(
                options = flexOptions,
                picked = flexPick,
                done = flexDone,
                accentColor = MaterialTheme.colorScheme.primary,
                duration = "30 min",
                columns = 2,
                onPick = { viewModel.pickFlex(it) },
                onDone = { viewModel.toggleFlexDone(true) },
                onClear = { viewModel.clearFlexPick() }
            )

            Spacer(Modifier.height(24.dp))

            // Footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Work can wait. This can't.\nNo optimizing \u2014 just pick one and do it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProgressCard(doneCount: Int, progress: Float, allDone: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "progress"
    )
    val progressColor by animateColorAsState(
        targetValue = if (allDone) successColor else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "progressColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$doneCount / 2 daily minimum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            if (allDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\u2713 Leisure day complete. Nice work, Avery.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = successColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivitySection(
    options: List<LeisureOption>,
    picked: String?,
    done: Boolean,
    accentColor: Color,
    duration: String,
    columns: Int,
    onPick: (String) -> Unit,
    onDone: () -> Unit,
    onClear: () -> Unit
) {
    if (picked == null) {
        // Grid picker
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(
                if (options.size <= columns) 90.dp
                else ((((options.size + columns - 1) / columns) * 90) + ((options.size + columns - 1) / columns - 1) * 8).dp
            )
        ) {
            items(options, key = { it.id }) { option ->
                OptionCard(option = option, onClick = { onPick(option.id) })
            }
        }
    } else {
        val selected = options.find { it.id == picked }!!
        // Selected item with checkbox
        SelectedItem(
            option = selected,
            done = done,
            accentColor = accentColor,
            duration = duration,
            onDone = onDone
        )
        if (!done) {
            TextButton(onClick = onClear) {
                Text(
                    "\u2190 Pick something else",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionCard(option: LeisureOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(option.icon, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                option.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectedItem(
    option: LeisureOption,
    done: Boolean,
    accentColor: Color,
    duration: String,
    onDone: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (done) accentColor.copy(alpha = 0.27f) else MaterialTheme.colorScheme.outline,
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (done) accentColor.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDone)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (done) accentColor else Color.Transparent)
                    .border(
                        2.dp,
                        if (done) accentColor else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${option.icon} ${option.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!done) {
                    Text(
                        "Tap when done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
