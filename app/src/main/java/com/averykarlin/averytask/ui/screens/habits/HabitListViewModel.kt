package com.averykarlin.averytask.ui.screens.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averykarlin.averytask.data.local.entity.HabitEntity
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.HabitWithStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository
) : ViewModel() {

    val habits: StateFlow<List<HabitWithStatus>> = habitRepository.getHabitsWithFullStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onToggleCompletion(habitId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyCompleted) {
                habitRepository.uncompleteHabit(habitId, System.currentTimeMillis())
            } else {
                habitRepository.completeHabit(habitId, System.currentTimeMillis())
            }
        }
    }

    fun onDeleteHabit(habitId: Long) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
        }
    }

    fun onReorderHabits(fromIndex: Int, toIndex: Int) {
        val currentList = habits.value.map { it.habit }.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        val reordered = currentList.mapIndexed { index, habit -> habit.copy(sortOrder = index) }
        viewModelScope.launch {
            habitRepository.updateSortOrders(reordered)
        }
    }
}
