package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.dao.DailyEssentialSlotCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.entity.AssignmentEntity
import com.averycorp.prismtask.data.local.entity.CourseEntity
import com.averycorp.prismtask.data.local.entity.DailyEssentialSlotCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.LeisureLogEntity
import com.averycorp.prismtask.data.preferences.DailyEssentialsPreferences
import com.averycorp.prismtask.data.preferences.LeisurePreferences
import com.averycorp.prismtask.data.preferences.LeisureSlotConfig
import com.averycorp.prismtask.data.preferences.LeisureSlotId
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.domain.model.SelfCareRoutines
import com.averycorp.prismtask.util.DayBoundary
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class LeisureKind { MUSIC, FLEX }

data class StepState(
    val stepId: String,
    val label: String,
    val completedToday: Boolean,
    val timeOfDay: String
)

data class RoutineCardState(
    val routineType: String,
    val displayName: String,
    val steps: List<StepState>
) {
    val allComplete: Boolean get() = steps.isNotEmpty() && steps.all { it.completedToday }
}

data class HabitCardState(
    val habitId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val completedToday: Boolean
)

data class AssignmentSummary(
    val id: Long,
    val title: String,
    val courseName: String,
    val courseColor: Int,
    val completed: Boolean
)

data class SchoolworkCardState(
    val habit: HabitCardState?,
    val assignmentsDueToday: List<AssignmentSummary>
) {
    val hasContent: Boolean get() = habit != null || assignmentsDueToday.isNotEmpty()
}

data class LeisureCardState(
    val kind: LeisureKind,
    val pickedForToday: String?,
    val doneForToday: Boolean,
    val label: String = when (kind) {
        LeisureKind.MUSIC -> "Music"
        LeisureKind.FLEX -> "Flex Leisure"
    },
    val enabled: Boolean = true
)

data class MedicationCardState(
    val totalDueToday: Int,
    val nextDose: MedicationDose,
    val otherDoses: List<MedicationDose>,
    /**
     * Slot-grouped view of the same dose list. One entry per distinct clock
     * time (plus an optional trailing "anytime" bucket). UI renders this as
     * one row per slot with the "{time} meds: {a, b, c}" label.
     */
    val slots: List<MedicationSlot> = emptyList()
)

/**
 * Aggregated state for the Daily Essentials section. Any field may be null
 * when the corresponding card is unconfigured / empty and should be hidden.
 * The section itself is hidden when [isEmpty] AND the user has already
 * dismissed the onboarding hint.
 */
data class DailyEssentialsUiState(
    val morning: RoutineCardState?,
    val bedtime: RoutineCardState?,
    val housework: HabitCardState?,
    val houseworkRoutine: RoutineCardState?,
    val schoolwork: SchoolworkCardState?,
    val musicLeisure: LeisureCardState,
    val flexLeisure: LeisureCardState,
    val medication: MedicationCardState?,
    val hasSeenHint: Boolean
) {
    val isEmpty: Boolean
        get() = morning == null &&
            bedtime == null &&
            housework == null &&
            houseworkRoutine == null &&
            (schoolwork == null || !schoolwork.hasContent) &&
            medication == null &&
            musicLeisure.pickedForToday == null &&
            flexLeisure.pickedForToday == null

    companion object {
        fun empty(): DailyEssentialsUiState = DailyEssentialsUiState(
            morning = null,
            bedtime = null,
            housework = null,
            houseworkRoutine = null,
            schoolwork = null,
            musicLeisure = LeisureCardState(LeisureKind.MUSIC, null, false),
            flexLeisure = LeisureCardState(LeisureKind.FLEX, null, false),
            medication = null,
            hasSeenHint = false
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DailyEssentialsUseCase
@Inject
constructor(
    private val selfCareDao: SelfCareDao,
    private val schoolworkDao: SchoolworkDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val leisureRepository: LeisureRepository,
    private val medicationStatusUseCase: MedicationStatusUseCase,
    private val slotCompletionDao: DailyEssentialSlotCompletionDao,
    private val dailyEssentialsPreferences: DailyEssentialsPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val leisurePreferences: LeisurePreferences
) {
    /**
     * Composite feed for the Daily Essentials section. All time windows use
     * [TaskBehaviorPreferences.getDayStartHour] so the section respects the
     * user's configured rollover hour.
     */
    fun observeToday(): Flow<DailyEssentialsUiState> =
        taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
            val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
            val windowStart = DayBoundary.calendarMidnightOfCurrentDay(dayStartHour)
            val windowEnd = DayBoundary.calendarMidnightOfNextDay(dayStartHour)

            combine(
                observeRoutineCard("morning", "Morning Routine", todayStart),
                observeRoutineCard("bedtime", "Bedtime Routine", todayStart),
                observeHouseworkCard(todayStart),
                observeRoutineCard("housework", "Housework", todayStart),
                observeSchoolworkCard(todayStart, windowStart, windowEnd),
                leisureRepository.getTodayLog(),
                medicationStatusUseCase.observeDueDosesToday(),
                slotCompletionDao.observeForDate(todayStart),
                dailyEssentialsPreferences.hasSeenHint,
                leisurePreferences.getSlotConfig(LeisureSlotId.MUSIC),
                leisurePreferences.getSlotConfig(LeisureSlotId.FLEX)
            ) { args -> combineDailyEssentials(args) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun combineDailyEssentials(args: Array<Any?>): DailyEssentialsUiState {
        val morning = args[0] as RoutineCardState?
        val bedtime = args[1] as RoutineCardState?
        val housework = args[2] as HabitCardState?
        val houseworkRoutine = args[3] as RoutineCardState?
        val schoolwork = args[4] as SchoolworkCardState?
        val leisureLog = args[5] as LeisureLogEntity?
        val dueDoses = args[6] as List<MedicationDose>
        val materializedSlots = args[7] as List<DailyEssentialSlotCompletionEntity>
        val seenHint = args[8] as Boolean
        val musicConfig = args[9] as LeisureSlotConfig
        val flexConfig = args[10] as LeisureSlotConfig

        val allSlots = MedicationSlotGrouper.group(dueDoses, materializedSlots)
        // The "medication card" should collapse when there are no pending
        // doses AND no materialized slot is still checked. A materialized
        // ``taken_at != null`` row for a slot whose doses were all taken
        // virtually would otherwise flicker the card away prematurely; the
        // virtual list already filters out taken interval/self-care doses,
        // so ``dueDoses.isEmpty`` is the right signal here.
        val medicationState = if (dueDoses.isEmpty()) {
            null
        } else {
            MedicationCardState(
                totalDueToday = dueDoses.size,
                nextDose = dueDoses.first(),
                otherDoses = dueDoses.drop(1),
                slots = allSlots
            )
        }

        return DailyEssentialsUiState(
            morning = morning,
            bedtime = bedtime,
            housework = housework,
            houseworkRoutine = houseworkRoutine,
            schoolwork = schoolwork,
            musicLeisure = LeisureCardState(
                kind = LeisureKind.MUSIC,
                pickedForToday = if (musicConfig.enabled) leisureLog?.musicPick else null,
                doneForToday = musicConfig.enabled && leisureLog?.musicDone == true,
                label = musicConfig.label,
                enabled = musicConfig.enabled
            ),
            flexLeisure = LeisureCardState(
                kind = LeisureKind.FLEX,
                pickedForToday = if (flexConfig.enabled) leisureLog?.flexPick else null,
                doneForToday = flexConfig.enabled && leisureLog?.flexDone == true,
                label = flexConfig.label,
                enabled = flexConfig.enabled
            ),
            medication = medicationState,
            hasSeenHint = seenHint
        )
    }

    private fun observeRoutineCard(
        routineType: String,
        displayName: String,
        todayStart: Long
    ): Flow<RoutineCardState?> = combine(
        selfCareDao.getStepsForRoutine(routineType),
        selfCareDao.getLogForDate(routineType, todayStart)
    ) { steps, log ->
        if (steps.isEmpty()) return@combine null
        val tierOrder = SelfCareRoutines.getTierOrder(routineType)
        val selectedTier = resolveSelectedTier(log?.selectedTier, tierOrder)
            ?: return@combine null
        val visibleSteps = steps.filter {
            SelfCareRoutines.tierIncludes(tierOrder, selectedTier, it.tier)
        }
        if (visibleSteps.isEmpty()) return@combine null
        val takenIds = parseStepIds(log?.completedSteps)
        RoutineCardState(
            routineType = routineType,
            displayName = displayName,
            steps = visibleSteps.map { step ->
                StepState(
                    stepId = step.stepId,
                    label = step.label,
                    completedToday = step.stepId in takenIds,
                    timeOfDay = step.timeOfDay
                )
            }
        )
    }

    private fun observeHouseworkCard(todayStart: Long): Flow<HabitCardState?> =
        dailyEssentialsPreferences.houseworkHabitId.flatMapLatest { habitId ->
            if (habitId == null) {
                flowOf(null)
            } else {
                habitDao.getHabitById(habitId).flatMapLatest { habit ->
                    if (habit == null || habit.isArchived) {
                        flowOf(null)
                    } else {
                        habitCompletionDao.isCompletedOnDate(habit.id, todayStart)
                            .map { completed -> habit.toHabitCardState(completed) }
                    }
                }
            }
        }

    private fun observeSchoolworkCard(
        todayStart: Long,
        windowStart: Long,
        windowEnd: Long
    ): Flow<SchoolworkCardState?> {
        val habitCardFlow: Flow<HabitCardState?> =
            dailyEssentialsPreferences.schoolworkHabitId.flatMapLatest { habitId ->
                if (habitId == null) {
                    flowOf(null)
                } else {
                    habitDao.getHabitById(habitId).flatMapLatest { habit ->
                        if (habit == null || habit.isArchived) {
                            flowOf(null)
                        } else {
                            habitCompletionDao.isCompletedOnDate(habit.id, todayStart)
                                .map { completed -> habit.toHabitCardState(completed) }
                        }
                    }
                }
            }

        val assignmentsFlow: Flow<List<AssignmentSummary>> = combine(
            schoolworkDao.getAssignmentsDueBetween(windowStart, windowEnd),
            schoolworkDao.getActiveCourses()
        ) { assignments, courses ->
            val courseById: Map<Long, CourseEntity> = courses.associateBy { it.id }
            assignments.map { it.toSummary(courseById) }
        }

        return combine(habitCardFlow, assignmentsFlow) { habit, assignments ->
            if (habit == null && assignments.isEmpty()) {
                null
            } else {
                SchoolworkCardState(habit = habit, assignmentsDueToday = assignments)
            }
        }
    }

    private fun HabitEntity.toHabitCardState(completed: Boolean): HabitCardState =
        HabitCardState(
            habitId = id,
            name = name,
            icon = icon,
            color = color,
            completedToday = completed
        )

    private fun AssignmentEntity.toSummary(
        courseById: Map<Long, CourseEntity>
    ): AssignmentSummary {
        val course = courseById[courseId]
        return AssignmentSummary(
            id = id,
            title = title,
            courseName = course?.name.orEmpty(),
            courseColor = course?.color ?: 0,
            completed = completed
        )
    }

    companion object {
        /**
         * Resolves the tier used to filter a routine's steps. When the user
         * hasn't logged the routine yet today, we fall back to the second-to-
         * last tier in the order (matching SelfCareViewModel's default) so new
         * days start at a reasonable mid-tier rather than the lightest or the
         * heaviest variant.
         */
        @JvmStatic
        internal fun resolveSelectedTier(logTier: String?, tierOrder: List<String>): String? {
            if (!logTier.isNullOrBlank() && logTier in tierOrder) return logTier
            if (tierOrder.isEmpty()) return null
            return tierOrder.getOrNull(tierOrder.size - 2) ?: tierOrder.last()
        }

        /**
         * Mirrors the medication log parser but only needs step IDs — handles
         * both the legacy string-array format and the richer object format
         * with `{id, note, at, timeOfDay}` entries. Exposed on the companion
         * so unit tests don't need to instantiate the use case.
         */
        @JvmStatic
        internal fun parseStepIds(json: String?): Set<String> {
            if (json.isNullOrBlank() || json == "[]") return emptySet()
            return try {
                val parsed = JsonParser.parseString(json)
                if (!parsed.isJsonArray) return emptySet()
                val array = parsed.asJsonArray as JsonArray
                array.mapNotNull { element ->
                    when {
                        element.isJsonPrimitive -> element.asString
                        element.isJsonObject -> element.asJsonObject.get("id")?.asString
                        else -> null
                    }
                }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
}
