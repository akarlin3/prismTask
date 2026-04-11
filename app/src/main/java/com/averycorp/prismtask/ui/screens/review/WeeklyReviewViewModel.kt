package com.averycorp.prismtask.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.usecase.WeeklyReviewAggregator
import com.averycorp.prismtask.domain.usecase.WeeklyReviewStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the AI Weekly Review screen (v1.4.0 V6).
 *
 * Aggregates the current week's stats + prior week for comparison and
 * builds a local "Wins / Misses / Suggestions" narrative from the numbers.
 * The Premium path that calls Claude Haiku is scoped for a follow-up —
 * for Free users (and as the offline fallback) the narrative is rule-based
 * so every user gets a review.
 */
@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val aggregator = WeeklyReviewAggregator()

    private val _state = MutableStateFlow(WeeklyReviewState())
    val state: StateFlow<WeeklyReviewState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val tasks = taskRepository.getAllTasksOnce()
            val thisWeek = aggregator.aggregate(tasks)
            val lastWeek = aggregator.aggregate(tasks, reference = thisWeek.weekStart - 1)
            val narrative = buildNarrative(thisWeek, lastWeek)
            _state.value = WeeklyReviewState(
                thisWeek = thisWeek,
                lastWeek = lastWeek,
                narrative = narrative
            )
        }
    }

    private fun buildNarrative(
        thisWeek: WeeklyReviewStats,
        lastWeek: WeeklyReviewStats
    ): WeeklyReviewNarrative {
        val wins = mutableListOf<String>()
        val misses = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        if (thisWeek.completed > lastWeek.completed) {
            wins.add("Completed ${thisWeek.completed - lastWeek.completed} more task${if (thisWeek.completed - lastWeek.completed == 1) "" else "s"} than last week.")
        }
        if (thisWeek.completionRate >= 0.75f) {
            wins.add("Strong completion rate of ${(thisWeek.completionRate * 100).toInt()}%.")
        }
        val selfCareThisWeek = thisWeek.byCategory[LifeCategory.SELF_CARE] ?: 0
        if (selfCareThisWeek > 0) {
            wins.add("You made time for $selfCareThisWeek self-care task${if (selfCareThisWeek == 1) "" else "s"}.")
        }

        if (thisWeek.slipped > 0) {
            misses.add("${thisWeek.slipped} task${if (thisWeek.slipped == 1) "" else "s"} slipped — they're ready to carry forward.")
        }
        val workThisWeek = thisWeek.byCategory[LifeCategory.WORK] ?: 0
        val totalCat = thisWeek.byCategory.values.sum()
        if (totalCat > 0 && workThisWeek > totalCat / 2) {
            misses.add("Work made up the majority of your completed tasks this week.")
        }

        if (selfCareThisWeek == 0 && thisWeek.completed > 0) {
            suggestions.add("Try scheduling one self-care task for the week ahead.")
        }
        if (thisWeek.slipped > thisWeek.completed) {
            suggestions.add("You have more slipped than completed tasks — consider a lighter plan next week.")
        }
        if (wins.isEmpty()) {
            wins.add("You showed up this week — that counts.")
        }

        return WeeklyReviewNarrative(wins = wins, misses = misses, suggestions = suggestions)
    }
}

data class WeeklyReviewState(
    val thisWeek: WeeklyReviewStats? = null,
    val lastWeek: WeeklyReviewStats? = null,
    val narrative: WeeklyReviewNarrative = WeeklyReviewNarrative()
)

data class WeeklyReviewNarrative(
    val wins: List<String> = emptyList(),
    val misses: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)
