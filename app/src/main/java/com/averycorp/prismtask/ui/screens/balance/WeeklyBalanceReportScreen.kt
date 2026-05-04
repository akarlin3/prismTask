package com.averycorp.prismtask.ui.screens.balance

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.domain.model.CognitiveLoad
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.usecase.BurnoutBand
import com.averycorp.prismtask.domain.usecase.CognitiveLoadBalanceState
import com.averycorp.prismtask.domain.usecase.WeeklyReviewStats
import com.averycorp.prismtask.ui.theme.CognitiveLoadColor
import com.averycorp.prismtask.ui.theme.LifeCategoryColor
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Weekly Balance Report (v1.4.0 V3 phase 2).
 *
 * Shows the user's completed-task distribution across life categories for a
 * single week, with a donut chart, per-category 4-week sparklines, summary
 * stats, and the current burnout score. The left/right arrows in the header
 * scroll through previous/next weeks.
 *
 * The donut chart and sparklines are drawn with Compose [Canvas] so there
 * is no external chart library dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyBalanceReportScreen(
    navController: NavController,
    viewModel: WeeklyBalanceReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Balance Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val stats = state.stats
                    if (stats != null) {
                        IconButton(onClick = {
                            val report = buildString {
                                appendLine(
                                    "PrismTask Weekly Balance — " +
                                        dateFormat.format(Date(stats.weekStart)) +
                                        " to " +
                                        dateFormat.format(Date(stats.weekEnd))
                                )
                                appendLine()
                                appendLine("Completed: ${stats.completed}")
                                appendLine("Slipped: ${stats.slipped}")
                                appendLine("Rescheduled: ${stats.rescheduled}")
                                appendLine(
                                    "Completion rate: %.0f%%".format(
                                        stats.completionRate * 100
                                    )
                                )
                                if (stats.byCategory.isNotEmpty()) {
                                    appendLine()
                                    appendLine("By life category:")
                                    stats.byCategory.entries
                                        .sortedByDescending { it.value }
                                        .forEach { (category, count) ->
                                            appendLine("• ${category.name}: $count")
                                        }
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "PrismTask weekly balance report"
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share report")
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share report")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WeekHeader(
                stats = state.stats,
                dateFormat = dateFormat,
                onPrevious = { viewModel.previousWeek() },
                onNext = { viewModel.nextWeek() }
            )

            val stats = state.stats ?: return@Column

            DonutChartSection(stats = stats)

            FourWeekTrendSection(
                trend = state.fourWeekTrend,
                counts = state.fourWeekCounts
            )

            StatsCardsSection(stats = stats)

            BurnoutSection(score = state.burnoutScore, band = state.burnoutBand)

            if (state.cognitiveLoadBalance.totalTracked > 0) {
                CognitiveLoadSection(state = state.cognitiveLoadBalance)
            }

            if (stats.carryForward.isNotEmpty()) {
                CarryForwardSection(tasks = stats.carryForward)
            }
        }
    }
}

// ─── Cognitive Load section ───────────────────────────────────────────────

@Composable
private fun CognitiveLoadSection(state: CognitiveLoadBalanceState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cognitive Load",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Dominant: ${CognitiveLoad.label(state.dominantLoad)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${state.totalTracked} task${if (state.totalTracked == 1) "" else "s"} tagged this week",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Stacked bar (current week)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                CognitiveLoad.TRACKED.forEach { load ->
                    val ratio = (state.currentRatios[load] ?: 0f).coerceIn(0f, 1f)
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(ratio)
                                .fillMaxSize()
                                .background(CognitiveLoadColor.forLoad(load))
                        )
                    }
                }
            }
            // Per-tier breakdown rows: dot + label + percent.
            CognitiveLoad.TRACKED.forEach { load ->
                val pct = ((state.currentRatios[load] ?: 0f) * 100).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CognitiveLoadColor.forLoad(load))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = CognitiveLoad.label(load),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Header ────────────────────────────────────────────────────────────────

@Composable
private fun WeekHeader(
    stats: WeeklyReviewStats?,
    dateFormat: SimpleDateFormat,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week")
            }
            Text(
                text = stats?.let {
                    "Week of ${dateFormat.format(Date(it.weekStart))} – ${dateFormat.format(Date(it.weekEnd - 1))}"
                } ?: "…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Week")
            }
        }
        val pct = stats?.let { (it.completionRate * 100f).toInt() } ?: 0
        Text(
            text = "$pct% Completed",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Donut Chart ───────────────────────────────────────────────────────────

@Composable
private fun DonutChartSection(stats: WeeklyReviewStats) {
    val totalCompleted = stats.byCategory.values.sum()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Category Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Start
            )

            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    categoryCounts = stats.byCategory,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalCompleted.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalCompleted == 1) "Task" else "Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DonutLegend(categoryCounts = stats.byCategory)
        }
    }
}

@Composable
private fun DonutChart(
    categoryCounts: Map<LifeCategory, Int>,
    modifier: Modifier = Modifier
) {
    val total = categoryCounts.values.sum()
    val strokeWidth = 28.dp
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)

        // Empty state: draw a single neutral ring so the chart still reads as
        // a donut even when the week has no completed tracked tasks.
        if (total == 0) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
            )
            return@Canvas
        }

        // Small gap between segments so adjacent categories don't smear into
        // each other. Sub-pixel when the ring is thin.
        val gapDegrees = 1.5f
        var startAngle = -90f
        LifeCategory.TRACKED.forEach { category ->
            val count = categoryCounts[category] ?: 0
            if (count > 0) {
                val sweep = (count.toFloat() / total.toFloat()) * 360f - gapDegrees
                if (sweep > 0f) {
                    drawArc(
                        color = LifeCategoryColor.forCategory(category),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                }
                startAngle += (count.toFloat() / total.toFloat()) * 360f
            }
        }
    }
}

@Composable
private fun DonutLegend(categoryCounts: Map<LifeCategory, Int>) {
    val total = categoryCounts.values.sum().coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LifeCategory.TRACKED.forEach { category ->
            val count = categoryCounts[category] ?: 0
            val pct = (count * 100) / total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LifeCategoryColor.forCategory(category))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = LifeCategory.label(category),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$count · $pct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── 4-Week Sparklines ─────────────────────────────────────────────────────

@Composable
private fun FourWeekTrendSection(
    trend: Map<LifeCategory, List<Float>>,
    counts: Map<LifeCategory, List<Int>>
) {
    val hasAnyData = trend.values.any { list -> list.any { it > 0f } }
    if (!hasAnyData) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "4-Week Trend",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            LifeCategory.TRACKED.forEach { category ->
                val values = trend[category].orEmpty()
                val countList = counts[category].orEmpty()
                SparklineRow(
                    label = LifeCategory.label(category),
                    values = values,
                    counts = countList,
                    color = LifeCategoryColor.forCategory(category)
                )
            }
        }
    }
}

@Composable
private fun SparklineRow(
    label: String,
    values: List<Float>,
    counts: List<Int>,
    color: Color
) {
    val thisWeek = counts.lastOrNull() ?: 0
    val lastWeek = counts.getOrNull(counts.size - 2) ?: 0
    val delta = when {
        lastWeek == 0 && thisWeek == 0 -> "—"
        thisWeek > lastWeek -> "▲ vs $lastWeek"
        thisWeek < lastWeek -> "▼ vs $lastWeek"
        else -> "= vs $lastWeek"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(100.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Text(
                text = "$thisWeek $delta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        )
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val dotColor = color
    val dotDimColor = color.copy(alpha = 0.35f)
    val lineDimColor = color.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        // Normalize each point to the series' own max so a mostly-zero
        // category still shows a readable polyline.
        val maxVal = max(values.max(), 0.01f)
        val padV = 6.dp.toPx()
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f

        fun yFor(v: Float): Float {
            val h = size.height - padV * 2
            return size.height - padV - (v / maxVal) * h
        }

        // If everything is 0f, draw a flat baseline so the row isn't empty.
        val allZero = values.all { it == 0f }
        if (allZero) {
            drawLine(
                color = lineDimColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx()
            )
            values.forEachIndexed { i, _ ->
                val x = i * stepX
                drawCircle(dotDimColor, radius = 3.dp.toPx(), center = Offset(x, size.height / 2f))
            }
            return@Canvas
        }

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            // Emphasize the final (current-week) dot so the user can find it
            // at a glance.
            val isLast = i == values.lastIndex
            drawCircle(
                color = if (isLast) dotColor else dotDimColor,
                radius = if (isLast) 4.dp.toPx() else 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

// ─── Stats Cards ───────────────────────────────────────────────────────────

@Composable
private fun StatsCardsSection(stats: WeeklyReviewStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard("Completed", stats.completed.toString(), Modifier.weight(1f))
            MetricCard("Slipped", stats.slipped.toString(), Modifier.weight(1f))
            MetricCard("Rescheduled", stats.rescheduled.toString(), Modifier.weight(1f))
        }

        val rate = stats.completionRate.coerceIn(0f, 1f)
        val ratePct = (rate * 100f).toInt()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Completion Rate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$ratePct%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { rate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Burnout Section ───────────────────────────────────────────────────────

@Composable
private fun BurnoutSection(score: Int, band: BurnoutBand) {
    val bandColor = when (band) {
        BurnoutBand.BALANCED -> LifeCategoryColor.PERSONAL
        BurnoutBand.MONITOR -> LocalPrismColors.current.warningColor
        BurnoutBand.CAUTION -> LocalPrismColors.current.urgentAccent
        BurnoutBand.HIGH_RISK -> LifeCategoryColor.HEALTH
    }
    val showNudge = band == BurnoutBand.CAUTION || band == BurnoutBand.HIGH_RISK

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Burnout Score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$score/100",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(bandColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = BurnoutBand.label(band),
                        style = MaterialTheme.typography.labelLarge,
                        color = bandColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (score / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = bandColor,
                trackColor = bandColor.copy(alpha = 0.15f)
            )

            if (showNudge) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(LifeCategoryColor.SELF_CARE.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (band == BurnoutBand.HIGH_RISK) {
                            "\uD83D\uDCA1 Your burnout signals are high. Try blocking time for rest and prioritize self-care this week."
                        } else {
                            "\uD83D\uDCA1 Things are heating up. Consider scheduling a short self-care break to recharge."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─── Carry Forward ─────────────────────────────────────────────────────────

@Composable
private fun CarryForwardSection(tasks: List<TaskEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Carry Forward",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            tasks.take(5).forEach { task ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                LifeCategoryColor.forCategory(
                                    LifeCategory.fromStorage(task.lifeCategory)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (tasks.size > 5) {
                Text(
                    text = "+${tasks.size - 5} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
