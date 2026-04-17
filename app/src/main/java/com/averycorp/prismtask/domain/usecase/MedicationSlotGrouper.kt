package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * Represents a single grouped row on the Daily Essentials medication list.
 *
 * A slot aggregates every [MedicationDose] that shares the same clock time
 * (or the pseudo "anytime" bucket for interval-based doses). The UI renders
 * one row per slot with the label ``{time} meds: {med1, med2, ...}`` and a
 * single checkbox that marks every dose in [doses] as taken for the day.
 *
 * [slotKey] is the stable identifier used for the ``daily_essential_slot_completions``
 * row: either a ``"HH:mm"`` time or [MedicationSlotGrouper.ANYTIME_KEY].
 */
data class MedicationSlot(
    val slotKey: String,
    val displayTime: String,
    val doses: List<MedicationDose>,
    val allTakenVirtually: Boolean,
    val materializedTakenAt: Long?
) {
    /**
     * Final "taken" state after merging the virtual derivation with the
     * materialized completion row. Materialized rows always win — a non-null
     * ``taken_at`` on the completion entity is the authoritative signal.
     */
    val isTaken: Boolean
        get() = materializedTakenAt != null || allTakenVirtually

    val medNames: List<String>
        get() = doses.map { it.displayLabel.ifBlank { it.medicationName } }

    val doseKeys: List<String>
        get() = doses.map { it.doseKey }
}

/**
 * Pure grouping helpers. Kept as an object (no injected deps) so unit tests
 * can exercise the slot-assignment rules without spinning up Room or
 * DataStore.
 *
 * Canonical slot-key convention:
 *
 *  - ``"HH:mm"`` for specific-time or time-of-day-mapped doses.
 *  - [ANYTIME_KEY] for interval-based doses with no fixed time. These sort
 *    last so the user always sees their scheduled doses first.
 */
object MedicationSlotGrouper {
    const val ANYTIME_KEY = "anytime"
    const val ANYTIME_DISPLAY = "Anytime"

    private val TIME_OF_DAY_BUCKETS = mapOf(
        "morning" to "08:00",
        "afternoon" to "13:00",
        "evening" to "18:00",
        "night" to "21:00"
    )

    /** Bucket a self-care step's ``time_of_day`` into a canonical slot key. */
    fun slotKeyForTimeOfDay(timeOfDay: String?): String {
        val normalized = timeOfDay?.trim()?.lowercase() ?: return ANYTIME_KEY
        return TIME_OF_DAY_BUCKETS[normalized] ?: ANYTIME_KEY
    }

    /** Coerce a user-entered specific time to ``HH:mm``; invalid values fall back to anytime. */
    fun normalizeTimeKey(raw: String?): String {
        if (raw.isNullOrBlank()) return ANYTIME_KEY
        val trimmed = raw.trim()
        // Accept "H:mm", "HH:mm", "HH:mm:ss".
        val parts = trimmed.split(":")
        if (parts.size < 2) return ANYTIME_KEY
        val hour = parts[0].toIntOrNull() ?: return ANYTIME_KEY
        val minute = parts[1].toIntOrNull() ?: return ANYTIME_KEY
        if (hour !in 0..23 || minute !in 0..59) return ANYTIME_KEY
        return "%02d:%02d".format(hour, minute)
    }

    /**
     * Group the virtual dose list into slots, merging in any materialized
     * ``daily_essential_slot_completions`` rows for today so a checked-off
     * slot stays checked across recompositions.
     *
     * Ordering: clock-time slots first (lexicographic "HH:mm" == chronological),
     * then the "anytime" bucket. Doses inside a slot keep input order so the
     * same medication always shows at the same position.
     */
    fun group(
        doses: List<MedicationDose>,
        materialized: List<DailyEssentialSlotCompletionEntity> = emptyList()
    ): List<MedicationSlot> {
        if (doses.isEmpty() && materialized.isEmpty()) return emptyList()
        val materializedBySlot = materialized.associateBy { it.slotKey }

        val buckets = linkedMapOf<String, MutableList<MedicationDose>>()
        for (dose in doses) {
            val key = dose.slotKey ?: ANYTIME_KEY
            buckets.getOrPut(key) { mutableListOf() }.add(dose)
        }

        val sortedKeys = buckets.keys.sortedWith(slotKeyComparator())
        return sortedKeys.map { key ->
            val bucketDoses = buckets.getValue(key)
            MedicationSlot(
                slotKey = key,
                displayTime = displayTimeFor(key),
                doses = bucketDoses,
                allTakenVirtually = bucketDoses.all { it.takenToday },
                materializedTakenAt = materializedBySlot[key]?.takenAt
            )
        }
    }

    /** Human-facing label for a slot key — the ``HH:mm`` passes through verbatim. */
    fun displayTimeFor(slotKey: String): String =
        if (slotKey == ANYTIME_KEY) ANYTIME_DISPLAY else slotKey

    /**
     * Compose the "{time} meds: {med1, med2, med3}" row label, truncating the
     * visible medication list to [visibleLimit] and appending ``+N more``
     * when the slot has more doses than that.
     */
    fun rowLabel(slot: MedicationSlot, visibleLimit: Int = 3): String {
        val names = slot.medNames
        val head = names.take(visibleLimit).joinToString(", ")
        val extra = names.size - visibleLimit
        val tail = if (extra > 0) ", +$extra more" else ""
        return "${slot.displayTime} meds: $head$tail"
    }

    private fun slotKeyComparator(): Comparator<String> = Comparator { a, b ->
        val aAnytime = a == ANYTIME_KEY
        val bAnytime = b == ANYTIME_KEY
        when {
            aAnytime && bAnytime -> 0
            aAnytime -> 1
            bAnytime -> -1
            else -> a.compareTo(b)
        }
    }

    /** Parse the JSON-serialized list of dose keys off a materialized row. */
    fun parseMedIdsJson(json: String?): List<String> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val parsed = JsonParser.parseString(json)
            if (!parsed.isJsonArray) return emptyList()
            (parsed.asJsonArray as JsonArray).mapNotNull { element ->
                if (element.isJsonPrimitive) element.asString else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Serialize a list of dose keys into the ``med_ids_json`` column format. */
    fun encodeMedIdsJson(ids: List<String>): String {
        if (ids.isEmpty()) return "[]"
        val array = JsonArray()
        ids.forEach { array.add(it) }
        return array.toString()
    }
}
