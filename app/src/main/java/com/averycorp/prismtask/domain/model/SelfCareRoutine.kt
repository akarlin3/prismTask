package com.averycorp.prismtask.domain.model

data class RoutineStep(
    val id: String,
    val label: String,
    val duration: String,
    val tier: String,
    val note: String = "",
    val phase: String
)

data class RoutineTier(
    val id: String,
    val label: String,
    val time: String,
    val color: Long
)

data class RoutinePhase(
    val name: String,
    val steps: List<RoutineStep>
)

object SelfCareRoutines {
    val morningTiers = listOf(
        RoutineTier("survival", "Survival", "~2 min", 0xFFF59E0B),
        RoutineTier("solid", "Solid", "~5 min", 0xFF3B82F6),
        RoutineTier("full", "Full", "~8 min", 0xFF8B5CF6)
    )

    val morningSteps = listOf(
        RoutineStep("cleanser", "Cleanser", "~1 min", "survival", phase = "Skincare"),
        RoutineStep("moisturizer", "Moisturizer", "~30 sec", "survival", phase = "Skincare"),
        RoutineStep("deodorant", "Deodorant", "~15 sec", "survival", phase = "Hygiene"),
        RoutineStep("teeth", "Brush teeth", "~2 min", "solid", phase = "Hygiene"),
        RoutineStep("toner", "Toner", "~30 sec", "solid", phase = "Skincare"),
        RoutineStep("hair", "Hair styling", "~2 min", "solid", phase = "Grooming"),
        RoutineStep("serum", "Serum / treatment", "~30 sec", "full", phase = "Skincare"),
        RoutineStep("eyecream", "Eye cream", "~30 sec", "full", phase = "Skincare")
    )

    val morningTierOrder = listOf("survival", "solid", "full")

    val bedtimeTiers = listOf(
        RoutineTier("survival", "Survival", "~15 min", 0xFFF59E0B),
        RoutineTier("basic", "Basic", "~17 min", 0xFF10B981),
        RoutineTier("solid", "Solid", "~30 min", 0xFF3B82F6),
        RoutineTier("full", "Full", "~36+ min", 0xFF8B5CF6)
    )

    val bedtimeSteps = listOf(
        RoutineStep("cleanser", "Cleanser", "~1 min", "basic", phase = "Skincare"),
        RoutineStep("moisturizer", "Moisturizer", "~30 sec", "basic", phase = "Skincare"),
        RoutineStep("shower", "Shower", "~10 min", "solid", phase = "Wash"),
        RoutineStep("toner", "Toner", "~30 sec", "solid", phase = "Skincare"),
        RoutineStep("serum", "Serum / treatment", "~30 sec", "solid", phase = "Skincare"),
        RoutineStep("brush", "Brush teeth", "~2 min", "solid", phase = "Hygiene"),
        RoutineStep("eyecream", "Eye cream", "~30 sec", "full", phase = "Skincare"),
        RoutineStep("exfoliant", "Exfoliant", "~1 min", "full", note = "2-3x / week only", phase = "Skincare"),
        RoutineStep("mask", "Mask", "~5 min", "full", note = "1-2x / week only", phase = "Skincare"),
        RoutineStep("meditate", "Meditation", "~15 min", "survival", note = "In bed, lights out — last step", phase = "Sleep")
    )

    val bedtimeTierOrder = listOf("survival", "basic", "solid", "full")

    fun tierIncludes(tierOrder: List<String>, activeTier: String, stepTier: String): Boolean = tierOrder.indexOf(
        stepTier
    ) <= tierOrder.indexOf(activeTier)

    fun getSteps(routineType: String): List<RoutineStep> = when (routineType) {
        "morning" -> morningSteps
        "medication" -> medicationSteps
        "housework" -> houseworkSteps
        else -> bedtimeSteps
    }

    fun getTiers(routineType: String): List<RoutineTier> = when (routineType) {
        "morning" -> morningTiers
        "medication" -> medicationTiers
        "housework" -> houseworkTiers
        else -> bedtimeTiers
    }

    fun getTierOrder(routineType: String): List<String> = when (routineType) {
        "morning" -> morningTierOrder
        "medication" -> medicationTierOrder
        "housework" -> houseworkTierOrder
        else -> bedtimeTierOrder
    }

    fun getPhases(routineType: String): List<RoutinePhase> {
        val steps = getSteps(routineType)
        val phaseOrder = when (routineType) {
            "morning" -> listOf("Skincare", "Hygiene", "Grooming")
            "housework" -> listOf("Kitchen", "Living Areas", "Bathroom", "Laundry")
            else -> listOf("Wash", "Skincare", "Hygiene", "Sleep")
        }
        return phaseOrder.map { phaseName ->
            RoutinePhase(phaseName, steps.filter { it.phase == phaseName })
        }
    }

    fun getVisibleSteps(routineType: String, tier: String): List<RoutineStep> {
        val steps = getSteps(routineType)
        val tierOrder = getTierOrder(routineType)
        return steps.filter { tierIncludes(tierOrder, tier, it.tier) }
    }

    val medicationTiers = listOf(
        RoutineTier("essential", "Essential", "—", 0xFFEF4444),
        RoutineTier("prescription", "Prescription", "—", 0xFF3B82F6),
        RoutineTier("complete", "Complete", "—", 0xFF10B981),
        RoutineTier("skipped", "Skipped", "—", 0xFF6B7280)
    )

    val medicationSteps = emptyList<RoutineStep>()

    // Note: "skipped" is intentionally excluded from the tier order so it never
    // marks any medication as visible/logged via the cumulative tier logic.
    val medicationTierOrder = listOf("essential", "prescription", "complete")

    // --- Housework ---

    val houseworkTiers = listOf(
        RoutineTier("quick", "Quick", "~15 min", 0xFFF59E0B),
        RoutineTier("regular", "Regular", "~30 min", 0xFF3B82F6),
        RoutineTier("deep", "Deep", "~60+ min", 0xFF8B5CF6)
    )

    val houseworkSteps = listOf(
        RoutineStep("dishes", "Dishes", "~10 min", "quick", phase = "Kitchen"),
        RoutineStep("wipe_counters", "Wipe counters", "~3 min", "quick", phase = "Kitchen"),
        RoutineStep("tidy_up", "Tidy up", "~5 min", "quick", phase = "Living Areas"),
        RoutineStep("vacuum_sweep", "Vacuum / sweep", "~10 min", "regular", phase = "Living Areas"),
        RoutineStep("trash", "Take out trash", "~3 min", "regular", phase = "Kitchen"),
        RoutineStep("start_laundry", "Start laundry", "~5 min", "regular", phase = "Laundry"),
        RoutineStep("clean_sink", "Clean bathroom sink", "~5 min", "deep", phase = "Bathroom"),
        RoutineStep("clean_toilet", "Clean toilet", "~5 min", "deep", phase = "Bathroom"),
        RoutineStep("dust", "Dust surfaces", "~10 min", "deep", phase = "Living Areas"),
        RoutineStep("mop", "Mop floors", "~10 min", "deep", phase = "Living Areas"),
        RoutineStep("fold_laundry", "Fold laundry", "~10 min", "deep", phase = "Laundry")
    )

    val houseworkTierOrder = listOf("quick", "regular", "deep")

    val isMedicationType: (String) -> Boolean = { it == "medication" }

    data class TimeOfDayInfo(
        val id: String,
        val label: String,
        val icon: String,
        val color: Long
    )

    val timesOfDay = listOf(
        TimeOfDayInfo("morning", "Morning", "\uD83C\uDF05", 0xFFF59E0B),
        TimeOfDayInfo("afternoon", "Afternoon", "\u2600\uFE0F", 0xFFF97316),
        TimeOfDayInfo("evening", "Evening", "\uD83C\uDF06", 0xFF8B5CF6),
        TimeOfDayInfo("night", "Night", "\uD83C\uDF19", 0xFF3B82F6)
    )

    val timeOfDayOrder = listOf("morning", "afternoon", "evening", "night")

    fun parseTimeOfDay(timeOfDay: String): Set<String> =
        timeOfDay
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun serializeTimeOfDay(times: Set<String>): String =
        timeOfDayOrder.filter { it in times }.joinToString(",")
}
