package com.averykarlin.averytask.domain.model

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
        RoutineTier("full", "Full", "~8 min", 0xFF8B5CF6),
    )

    val morningSteps = listOf(
        RoutineStep("cleanser", "Cleanser", "~1 min", "survival", phase = "Skincare"),
        RoutineStep("moisturizer", "Moisturizer", "~30 sec", "survival", phase = "Skincare"),
        RoutineStep("deodorant", "Deodorant", "~15 sec", "survival", phase = "Hygiene"),
        RoutineStep("teeth", "Brush teeth", "~2 min", "solid", phase = "Hygiene"),
        RoutineStep("toner", "Toner", "~30 sec", "solid", phase = "Skincare"),
        RoutineStep("hair", "Hair styling", "~2 min", "solid", phase = "Grooming"),
        RoutineStep("serum", "Serum / treatment", "~30 sec", "full", phase = "Skincare"),
        RoutineStep("eyecream", "Eye cream", "~30 sec", "full", phase = "Skincare"),
    )

    val morningTierOrder = listOf("survival", "solid", "full")

    val bedtimeTiers = listOf(
        RoutineTier("survival", "Survival", "~15 min", 0xFFF59E0B),
        RoutineTier("basic", "Basic", "~17 min", 0xFF10B981),
        RoutineTier("solid", "Solid", "~30 min", 0xFF3B82F6),
        RoutineTier("full", "Full", "~36+ min", 0xFF8B5CF6),
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
        RoutineStep("meditate", "Meditation", "~15 min", "survival", note = "In bed, lights out — last step", phase = "Sleep"),
    )

    val bedtimeTierOrder = listOf("survival", "basic", "solid", "full")

    fun tierIncludes(tierOrder: List<String>, activeTier: String, stepTier: String): Boolean {
        return tierOrder.indexOf(stepTier) <= tierOrder.indexOf(activeTier)
    }

    fun getSteps(routineType: String): List<RoutineStep> = when (routineType) {
        "morning" -> morningSteps
        "medication" -> medicationSteps
        else -> bedtimeSteps
    }

    fun getTiers(routineType: String): List<RoutineTier> = when (routineType) {
        "morning" -> morningTiers
        "medication" -> medicationTiers
        else -> bedtimeTiers
    }

    fun getTierOrder(routineType: String): List<String> = when (routineType) {
        "morning" -> morningTierOrder
        "medication" -> medicationTierOrder
        else -> bedtimeTierOrder
    }

    fun getPhases(routineType: String): List<RoutinePhase> {
        val steps = getSteps(routineType)
        val tierOrder = getTierOrder(routineType)
        val phaseOrder = if (routineType == "morning") {
            listOf("Skincare", "Hygiene", "Grooming")
        } else {
            listOf("Wash", "Skincare", "Hygiene", "Sleep")
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
    )

    val medicationSteps = emptyList<RoutineStep>()

    val medicationTierOrder = listOf("essential", "prescription", "complete")

    val isMedicationType: (String) -> Boolean = { it == "medication" }
}
