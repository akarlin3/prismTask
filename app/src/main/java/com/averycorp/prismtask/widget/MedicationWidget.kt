package com.averycorp.prismtask.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.averycorp.prismtask.MainActivity

/**
 * Medication widget — mirrors the in-app meds slot/tier model.
 *
 * Tier color mapping (matches the in-app screen):
 *   ESSENTIAL    → primary
 *   PRESCRIPTION → infoColor
 *   COMPLETE     → successColor
 *   SKIPPED      → muted
 *
 * Three sizes (declared via SizeMode.Responsive):
 * - SMALL_WIDE (4×1): compact "next dose" headline + day progress bar
 * - LARGE     (4×3): full slot list with per-slot dose checks
 * - LARGE_WIDE (5×3): same as LARGE with looser spacing
 */
class MedicationWidget : GlanceAppWidget() {
    companion object {
        private val SMALL_WIDE = DpSize(200.dp, 100.dp)
        private val LARGE = DpSize(350.dp, 250.dp)
        private val LARGE_WIDE = DpSize(450.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_WIDE, LARGE, LARGE_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = loadWidgetPalette(context)
        provideContent {
            MedicationContent(context, LocalSize.current, palette)
        }
    }
}

private enum class MedTier { ESSENTIAL, PRESCRIPTION, COMPLETE, SKIPPED }

private data class MedSlot(
    val name: String,
    val time: String,
    val tier: MedTier,
    val taken: Int,
    val total: Int,
    val active: Boolean,
    val isNext: Boolean = false
)

@Composable
private fun MedicationContent(context: Context, size: DpSize, palette: WidgetThemePalette) {
    val isSmall = size.height < 130.dp
    val openMeds = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_LAUNCH_ACTION, "open_medication")
    }

    // Sample state — wiring to MedicationRefillRepository is a follow-up.
    val slots = listOf(
        MedSlot("Morning", "8:00 AM", MedTier.COMPLETE, 3, 3, active = true),
        MedSlot("Afternoon", "1:00 PM", MedTier.PRESCRIPTION, 1, 2, active = true),
        MedSlot("Evening", "7:00 PM", MedTier.ESSENTIAL, 0, 3, active = true, isNext = true),
        MedSlot("Night", "10:30 PM", MedTier.SKIPPED, 0, 1, active = false)
    )
    val totalDoses = slots.sumOf { it.total }
    val takenDoses = slots.sumOf { it.taken }
    val nextSlot = slots.firstOrNull { it.isNext } ?: slots.first { it.active && it.taken < it.total }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(palette.widgetCornerRadius)
            .background(palette.surfaceBackground)
            .padding(if (isSmall) 11.dp else 12.dp)
            .clickable(actionStartActivity(openMeds))
    ) {
        if (isSmall) {
            CompactNextDose(palette, nextSlot, takenDoses, totalDoses)
        } else {
            FullDayView(palette, slots, takenDoses, totalDoses, nextSlot)
        }
    }
}

// Receiver bound to ColumnScope so `GlanceModifier.defaultWeight()` (which
// is declared as a ColumnScope/RowScope extension) resolves when this
// widget body is emitted directly into the parent Column in
// [MedicationContent].
@Composable
private fun androidx.glance.layout.ColumnScope.CompactNextDose(
    palette: WidgetThemePalette,
    nextSlot: MedSlot,
    takenDoses: Int,
    totalDoses: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(tierColor(nextSlot.tier, palette))
        ) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "Next Dose",
            style = WidgetTextStyles.captionMedium(palette.onSurfaceVariant),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = "$takenDoses/$totalDoses",
            style = WidgetTextStyles.badge(palette.onSurfaceVariant)
        )
    }
    Text(
        text = WidgetTextStyles.headerLabel(palette, nextSlot.name),
        style = WidgetTextStyles.headerThemed(palette, palette.onSurface)
    )
    Text(
        text = "${nextSlot.time} · ${nextSlot.total - nextSlot.taken} pending",
        style = WidgetTextStyles.caption(palette.onSurfaceVariant)
    )
    Spacer(modifier = GlanceModifier.defaultWeight())
    LinearProgressIndicator(
        progress = (takenDoses.toFloat() / totalDoses).coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = palette.successColor,
        backgroundColor = palette.surfaceVariant
    )
}

@Composable
private fun FullDayView(
    palette: WidgetThemePalette,
    slots: List<MedSlot>,
    takenDoses: Int,
    totalDoses: Int,
    nextSlot: MedSlot
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = WidgetTextStyles.headerLabel(palette, "Medication"),
            style = WidgetTextStyles.headerThemed(palette, palette.onSurface),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = "$takenDoses/$totalDoses",
            style = WidgetTextStyles.captionMedium(palette.successColor)
        )
    }
    Spacer(modifier = GlanceModifier.height(6.dp))
    LinearProgressIndicator(
        progress = (takenDoses.toFloat() / totalDoses).coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
        color = palette.successColor,
        backgroundColor = palette.surfaceVariant
    )
    Spacer(modifier = GlanceModifier.height(8.dp))
    slots.forEach { slot ->
        SlotRow(slot, palette, highlight = slot === nextSlot)
        Spacer(modifier = GlanceModifier.height(5.dp))
    }
}

@Composable
private fun SlotRow(slot: MedSlot, palette: WidgetThemePalette, highlight: Boolean) {
    val tcolor = tierColor(slot.tier, palette)
    val allDone = slot.active && slot.taken == slot.total
    val rowBg: ColorProvider = if (highlight) palette.primaryContainer else palette.surfaceVariant
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(6.dp)
            .background(rowBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(tcolor)) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = slot.name,
                style = WidgetTextStyles.captionMedium(
                    if (slot.active) palette.onSurface else palette.onSurfaceVariant
                ),
                maxLines = 1
            )
            Text(
                text = if (slot.active) slot.time else "${slot.time} · skipped",
                style = WidgetTextStyles.badge(palette.onSurfaceVariant),
                maxLines = 1
            )
        }
        Text(
            text = if (allDone) "✓ done" else "${slot.taken}/${slot.total}",
            style = WidgetTextStyles.badgeBold(
                if (allDone) palette.successColor else palette.onSurfaceVariant
            )
        )
    }
}

private fun tierColor(tier: MedTier, palette: WidgetThemePalette): ColorProvider = when (tier) {
    MedTier.ESSENTIAL -> palette.primary
    MedTier.PRESCRIPTION -> palette.infoColor
    MedTier.COMPLETE -> palette.successColor
    MedTier.SKIPPED -> palette.onSurfaceVariant
}

class MedicationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedicationWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        clearWidgetConfigOnDelete(this, context, appWidgetIds)
    }
}
