package com.averykarlin.averytask.ui.screens.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.SelfCareLogEntity
import com.averykarlin.averytask.data.local.entity.SelfCareStepEntity
import com.averykarlin.averytask.data.preferences.BuiltInSortOrders
import com.averykarlin.averytask.data.preferences.HabitListPreferences
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.HabitWithStatus
import com.averykarlin.averytask.data.repository.LeisureRepository
import com.averykarlin.averytask.data.repository.SchoolworkRepository
import com.averykarlin.averytask.data.repository.SelfCareRepository
import com.averykarlin.averytask.domain.model.SelfCareRoutines
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelfCareCardData(
    val completedCount: Int,
    val totalCount: Int,
    val tierLabel: String,
    val isComplete: Boolean
)

sealed class HabitListItem {
    abstract val sortOrder: Int

    data class HabitItem(val habitWithStatus: HabitWithStatus) : HabitListItem() {
        override val sortOrder get() = habitWithStatus.habit.sortOrder
    }

    data class SelfCareItem(
        val routineType: String,
        val cardData: SelfCareCardData,
        override val sortOrder: Int
    ) : HabitListItem()

    data class BuiltInHabitItem(
        val type: String,
        val habitWithStatus: HabitWithStatus,
        override val sortOrder: Int
    ) : HabitListItem()

    val key: Any get() = when (this) {
        is HabitItem -> habitWithStatus.habit.id
        is SelfCareItem -> "selfcare_$routineType"
        is BuiltInHabitItem -> "builtin_$type"
    }
}

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val selfCareRepository: SelfCareRepository,
    private val habitListPreferences: HabitListPreferences,
    private val gson: Gson
) : ViewModel() {

    private val habits: StateFlow<List<HabitWithStatus>> = habitRepository.getHabitsWithFullStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val morningLog: StateFlow<SelfCareLogEntity?> = selfCareRepository.getTodayLog("morning")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val bedtimeLog: StateFlow<SelfCareLogEntity?> = selfCareRepository.getTodayLog("bedtime")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val medicationLog: StateFlow<SelfCareLogEntity?> = selfCareRepository.getTodayLog("medication")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val morningSteps: StateFlow<List<SelfCareStepEntity>> = selfCareRepository.getSteps("morning")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val bedtimeSteps: StateFlow<List<SelfCareStepEntity>> = selfCareRepository.getSteps("bedtime")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val medicationSteps: StateFlow<List<SelfCareStepEntity>> = selfCareRepository.getSteps("medication")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val houseworkLog: StateFlow<SelfCareLogEntity?> = selfCareRepository.getTodayLog("housework")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val houseworkSteps: StateFlow<List<SelfCareStepEntity>> = selfCareRepository.getSteps("housework")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val builtInSortOrders: StateFlow<BuiltInSortOrders> = habitListPreferences.getBuiltInSortOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            BuiltInSortOrders(
                HabitListPreferences.DEFAULT_MORNING_ORDER,
                HabitListPreferences.DEFAULT_BEDTIME_ORDER,
                HabitListPreferences.DEFAULT_MEDICATION_ORDER,
                HabitListPreferences.DEFAULT_SCHOOL_ORDER,
                HabitListPreferences.DEFAULT_LEISURE_ORDER,
                HabitListPreferences.DEFAULT_HOUSEWORK_ORDER
            ))

    private val selfCareEnabled: StateFlow<Boolean> = habitListPreferences.isSelfCareEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val medicationEnabled: StateFlow<Boolean> = habitListPreferences.isMedicationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val schoolEnabled: StateFlow<Boolean> = habitListPreferences.isSchoolEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val leisureEnabled: StateFlow<Boolean> = habitListPreferences.isLeisureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val houseworkEnabled: StateFlow<Boolean> = habitListPreferences.isHouseworkEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val items: StateFlow<List<HabitListItem>> = combine(
        habits, morningLog, bedtimeLog, medicationLog, morningSteps, bedtimeSteps, medicationSteps, builtInSortOrders,
        selfCareEnabled, medicationEnabled, schoolEnabled, leisureEnabled, houseworkLog, houseworkSteps, houseworkEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val habitList = values[0] as List<HabitWithStatus>
        val mLog = values[1] as SelfCareLogEntity?
        val bLog = values[2] as SelfCareLogEntity?
        val medLog = values[3] as SelfCareLogEntity?
        val mSteps = values[4] as List<SelfCareStepEntity>
        val bSteps = values[5] as List<SelfCareStepEntity>
        val medSteps = values[6] as List<SelfCareStepEntity>
        val sortOrders = values[7] as BuiltInSortOrders
        val selfCareOn = values[8] as Boolean
        val medicationOn = values[9] as Boolean
        val schoolOn = values[10] as Boolean
        val leisureOn = values[11] as Boolean
        val hwLog = values[12] as SelfCareLogEntity?
        val hwSteps = values[13] as List<SelfCareStepEntity>
        val houseworkOn = values[14] as Boolean

        val morningCard = computeCardData(mLog, mSteps, "morning")
        val bedtimeCard = computeCardData(bLog, bSteps, "bedtime")
        val medicationCard = computeCardData(medLog, medSteps, "medication")
        val houseworkCard = computeCardData(hwLog, hwSteps, "housework")

        val autoHabitNames = mutableSetOf<String>()
        if (selfCareOn) {
            autoHabitNames.add(SelfCareRepository.MORNING_HABIT_NAME)
            autoHabitNames.add(SelfCareRepository.BEDTIME_HABIT_NAME)
        }
        if (medicationOn) autoHabitNames.add(SelfCareRepository.MEDICATION_HABIT_NAME)
        if (houseworkOn) autoHabitNames.add(SelfCareRepository.HOUSEWORK_HABIT_NAME)
        if (schoolOn) autoHabitNames.add(SchoolworkRepository.SCHOOL_HABIT_NAME)
        if (leisureOn) autoHabitNames.add(LeisureRepository.LEISURE_HABIT_NAME)
        // Always filter out built-in habit names from the regular habit list
        val allBuiltInNames = setOf(
            SelfCareRepository.MORNING_HABIT_NAME,
            SelfCareRepository.BEDTIME_HABIT_NAME,
            SelfCareRepository.MEDICATION_HABIT_NAME,
            SelfCareRepository.HOUSEWORK_HABIT_NAME,
            SchoolworkRepository.SCHOOL_HABIT_NAME,
            LeisureRepository.LEISURE_HABIT_NAME
        )

        val schoolHabit = habitList.find { it.habit.name == SchoolworkRepository.SCHOOL_HABIT_NAME }
        val leisureHabit = habitList.find { it.habit.name == LeisureRepository.LEISURE_HABIT_NAME }

        val allItems = mutableListOf<HabitListItem>()
        if (selfCareOn) {
            allItems.add(HabitListItem.SelfCareItem("morning", morningCard, sortOrders.morning))
            allItems.add(HabitListItem.SelfCareItem("bedtime", bedtimeCard, sortOrders.bedtime))
        }
        if (medicationOn) {
            allItems.add(HabitListItem.SelfCareItem("medication", medicationCard, sortOrders.medication))
        }
        if (houseworkOn) {
            allItems.add(HabitListItem.SelfCareItem("housework", houseworkCard, sortOrders.housework))
        }
        if (schoolOn && schoolHabit != null) {
            allItems.add(HabitListItem.BuiltInHabitItem("school", schoolHabit, sortOrders.school))
        }
        if (leisureOn && leisureHabit != null) {
            allItems.add(HabitListItem.BuiltInHabitItem("leisure", leisureHabit, sortOrders.leisure))
        }
        habitList
            .filter { it.habit.name !in allBuiltInNames }
            .forEach { allItems.add(HabitListItem.HabitItem(it)) }

        allItems.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun computeCardData(log: SelfCareLogEntity?, steps: List<SelfCareStepEntity>, routineType: String): SelfCareCardData {
        val tier = log?.selectedTier ?: if (routineType == "medication") {
            "prescription"
        } else {
            SelfCareRoutines.getTierOrder(routineType).let {
                if (it.size >= 2) it[it.size - 2] else it.first()
            }
        }
        val tiers = SelfCareRoutines.getTiers(routineType)
        val tierLabel = tiers.find { it.id == tier }?.label ?: tier.replaceFirstChar { it.uppercase() }
        val visibleSteps = selfCareRepository.getVisibleStepsFromEntities(steps, tier, routineType)
        val completedStepIds = parseSteps(log?.completedSteps)
        val completedCount = visibleSteps.count { it.stepId in completedStepIds }
        return SelfCareCardData(
            completedCount = completedCount,
            totalCount = visibleSteps.size,
            tierLabel = tierLabel,
            isComplete = log?.isComplete == true
        )
    }

    private fun parseSteps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun onToggleCompletion(habitId: Long, isFullyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isFullyCompleted) {
                    habitRepository.uncompleteHabit(habitId, System.currentTimeMillis())
                } else {
                    habitRepository.completeHabit(habitId, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                android.util.Log.e("HabitListVM", "Failed to toggle habit completion", e)
            }
        }
    }

    fun onDecrementCompletion(habitId: Long) {
        viewModelScope.launch {
            try {
                habitRepository.uncompleteHabit(habitId, System.currentTimeMillis())
            } catch (e: Exception) {
                android.util.Log.e("HabitListVM", "Failed to decrement habit completion", e)
            }
        }
    }

    fun completeWithNotes(habitId: Long, notes: String?) {
        viewModelScope.launch {
            try {
                habitRepository.completeHabit(habitId, System.currentTimeMillis(), notes)
            } catch (e: Exception) {
                android.util.Log.e("HabitListVM", "Failed to complete habit with notes", e)
            }
        }
    }

    suspend fun getRecentLogs(habitId: Long): List<com.averykarlin.averytask.data.local.entity.HabitCompletionEntity> {
        return habitRepository.getCompletionsForHabitOnce(habitId).take(20)
    }

    fun onDeleteHabit(habitId: Long) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
        }
    }

    fun onReorderItems(fromIndex: Int, toIndex: Int) {
        val currentList = items.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)

        val habitEntities = mutableListOf<com.averykarlin.averytask.data.local.entity.HabitEntity>()
        var orders = BuiltInSortOrders(
            morning = HabitListPreferences.DEFAULT_MORNING_ORDER,
            bedtime = HabitListPreferences.DEFAULT_BEDTIME_ORDER,
            medication = HabitListPreferences.DEFAULT_MEDICATION_ORDER,
            school = HabitListPreferences.DEFAULT_SCHOOL_ORDER,
            leisure = HabitListPreferences.DEFAULT_LEISURE_ORDER,
            housework = HabitListPreferences.DEFAULT_HOUSEWORK_ORDER
        )

        currentList.forEachIndexed { index, listItem ->
            when (listItem) {
                is HabitListItem.HabitItem -> {
                    habitEntities.add(listItem.habitWithStatus.habit.copy(sortOrder = index))
                }
                is HabitListItem.SelfCareItem -> {
                    orders = when (listItem.routineType) {
                        "morning" -> orders.copy(morning = index)
                        "bedtime" -> orders.copy(bedtime = index)
                        "medication" -> orders.copy(medication = index)
                        "housework" -> orders.copy(housework = index)
                        else -> orders
                    }
                }
                is HabitListItem.BuiltInHabitItem -> {
                    orders = when (listItem.type) {
                        "school" -> orders.copy(school = index)
                        "leisure" -> orders.copy(leisure = index)
                        else -> orders
                    }
                }
            }
        }

        viewModelScope.launch {
            habitRepository.updateSortOrders(habitEntities)
            habitListPreferences.setBuiltInSortOrders(orders)
        }
    }
}
