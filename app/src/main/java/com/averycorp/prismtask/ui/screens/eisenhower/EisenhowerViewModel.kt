package com.averycorp.prismtask.ui.screens.eisenhower

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.api.EisenhowerRequest
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EisenhowerViewModel
    @Inject
    constructor(
        private val taskDao: TaskDao,
        private val api: PrismTaskApi,
        private val proFeatureGate: ProFeatureGate
    ) : ViewModel() {
        val userTier: StateFlow<UserTier> = proFeatureGate.userTier

        private val _allIncompleteTasks = taskDao.getIncompleteRootTasks()

        val quadrants: StateFlow<Map<String, List<TaskEntity>>> = _allIncompleteTasks
            .map { tasks ->
                val categorized = tasks.filter { it.eisenhowerQuadrant != null && it.archivedAt == null }
                mapOf(
                    "Q1" to categorized.filter { it.eisenhowerQuadrant == "Q1" },
                    "Q2" to categorized.filter { it.eisenhowerQuadrant == "Q2" },
                    "Q3" to categorized.filter { it.eisenhowerQuadrant == "Q3" },
                    "Q4" to categorized.filter { it.eisenhowerQuadrant == "Q4" }
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _lastCategorizedAt = MutableStateFlow<Long?>(null)
        val lastCategorizedAt: StateFlow<Long?> = _lastCategorizedAt

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private val _expandedQuadrant = MutableStateFlow<String?>(null)
        val expandedQuadrant: StateFlow<String?> = _expandedQuadrant

        init {
            // Check if tasks are already categorized
            viewModelScope.launch {
                _allIncompleteTasks.collect { tasks ->
                    val latest = tasks.mapNotNull { it.eisenhowerUpdatedAt }.maxOrNull()
                    _lastCategorizedAt.value = latest
                }
            }
        }

        private val _showUpgradePrompt = MutableStateFlow(false)
        val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt

        fun dismissUpgradePrompt() {
            _showUpgradePrompt.value = false
        }

        fun categorize() {
            if (!proFeatureGate.hasAccess(ProFeatureGate.AI_EISENHOWER)) {
                _showUpgradePrompt.value = true
                return
            }
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    val response = api.categorizeEisenhower(EisenhowerRequest())
                    val now = System.currentTimeMillis()
                    for (cat in response.categorizations) {
                        taskDao.updateEisenhowerQuadrant(
                            id = cat.taskId,
                            quadrant = cat.quadrant,
                            reason = cat.reason,
                            updatedAt = now
                        )
                    }
                    _lastCategorizedAt.value = now
                } catch (e: Exception) {
                    _error.value = e.message ?: "Failed to categorize tasks"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        fun moveTaskToQuadrant(taskId: Long, quadrant: String) {
            viewModelScope.launch {
                taskDao.updateEisenhowerQuadrant(
                    id = taskId,
                    quadrant = quadrant,
                    reason = "Manually moved"
                )
            }
        }

        fun completeTask(taskId: Long) {
            viewModelScope.launch {
                taskDao.markCompleted(taskId, System.currentTimeMillis())
            }
        }

        fun expandQuadrant(quadrant: String?) {
            _expandedQuadrant.value = quadrant
        }

        fun clearError() {
            _error.value = null
        }
    }
