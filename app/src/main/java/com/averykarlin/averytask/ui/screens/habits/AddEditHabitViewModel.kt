package com.averykarlin.averytask.ui.screens.habits

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val habitId: Long? = savedStateHandle.get<Long>("habitId")?.takeIf { it != -1L }
    val isEditMode: Boolean = habitId != null

    private var existingHabit: HabitEntity? = null

    var name by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var targetFrequency by mutableIntStateOf(1)
        private set
    var frequencyPeriod by mutableStateOf("daily")
        private set
    var activeDays by mutableStateOf<Set<Int>>(emptySet())
        private set
    var color by mutableStateOf("#4A90D9")
        private set
    var icon by mutableStateOf("\u2B50")
        private set
    var reminderEnabled by mutableStateOf(false)
        private set
    var reminderHour by mutableIntStateOf(9)
        private set
    var reminderMinute by mutableIntStateOf(0)
        private set
    var category by mutableStateOf("")
        private set
    var createDailyTask by mutableStateOf(false)
        private set
    var nameError by mutableStateOf(false)
        private set

    init {
        if (habitId != null) {
            viewModelScope.launch {
                habitRepository.getHabitByIdOnce(habitId)?.let { habit ->
                    existingHabit = habit
                    name = habit.name
                    description = habit.description ?: ""
                    targetFrequency = habit.targetFrequency
                    frequencyPeriod = habit.frequencyPeriod
                    activeDays = parseActiveDays(habit.activeDays)
                    color = habit.color
                    icon = habit.icon
                    category = habit.category ?: ""
                    createDailyTask = habit.createDailyTask
                    if (habit.reminderTime != null) {
                        reminderEnabled = true
                        reminderHour = (habit.reminderTime / (60 * 60 * 1000)).toInt()
                        reminderMinute = ((habit.reminderTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        if (value.isNotBlank()) nameError = false
    }

    fun onDescriptionChange(value: String) { description = value }
    fun onTargetFrequencyChange(value: Int) { targetFrequency = value.coerceIn(1, 10) }
    fun onFrequencyPeriodChange(value: String) { frequencyPeriod = value }
    fun onToggleActiveDay(day: Int) {
        activeDays = if (day in activeDays) activeDays - day else activeDays + day
    }
    fun onColorChange(value: String) { color = value }
    fun onIconChange(value: String) { icon = value }
    fun onReminderEnabledChange(value: Boolean) { reminderEnabled = value }
    fun onReminderHourChange(value: Int) { reminderHour = value }
    fun onReminderMinuteChange(value: Int) { reminderMinute = value }
    fun onCategoryChange(value: String) { category = value }
    fun onCreateDailyTaskChange(value: Boolean) { createDailyTask = value }

    suspend fun saveHabit(): Boolean {
        if (name.isBlank()) {
            nameError = true
            return false
        }

        return try {
            val reminderTime = if (reminderEnabled) {
                (reminderHour.toLong() * 60 * 60 * 1000) + (reminderMinute.toLong() * 60 * 1000)
            } else null

            val activeDaysJson = if (frequencyPeriod == "weekly" && activeDays.isNotEmpty()) {
                "[${activeDays.sorted().joinToString(",")}]"
            } else null

            val existing = existingHabit
            if (existing != null) {
                habitRepository.updateHabit(
                    existing.copy(
                        name = name.trim(),
                        description = description.trim().ifEmpty { null },
                        targetFrequency = targetFrequency,
                        frequencyPeriod = frequencyPeriod,
                        activeDays = activeDaysJson,
                        color = color,
                        icon = icon,
                        reminderTime = reminderTime,
                        category = category.trim().ifEmpty { null },
                        createDailyTask = createDailyTask
                    )
                )
            } else {
                habitRepository.addHabit(
                    HabitEntity(
                        name = name.trim(),
                        description = description.trim().ifEmpty { null },
                        targetFrequency = targetFrequency,
                        frequencyPeriod = frequencyPeriod,
                        activeDays = activeDaysJson,
                        color = color,
                        icon = icon,
                        reminderTime = reminderTime,
                        category = category.trim().ifEmpty { null },
                        createDailyTask = createDailyTask
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.e("AddEditHabitVM", "Failed to save habit", e)
            _errorMessages.emit("Something went wrong")
            false
        }
    }

    suspend fun deleteHabit() {
        try {
            habitId?.let { habitRepository.deleteHabit(it) }
        } catch (e: Exception) {
            Log.e("AddEditHabitVM", "Failed to delete habit", e)
            _errorMessages.emit("Something went wrong")
        }
    }

    private fun parseActiveDays(json: String?): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            json.trim('[', ']').split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
