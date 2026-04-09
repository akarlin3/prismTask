package com.averycorp.averytask.ui.screens.templates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.averytask.data.local.entity.TaskTemplateEntity
import com.averycorp.averytask.data.repository.TaskTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateListViewModel @Inject constructor(
    private val templateRepository: TaskTemplateRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * All templates, filtered client-side by the current category and search
     * query. Keeping the filter here (instead of querying the DAO per change)
     * lets the screen react instantly to chip taps without re-subscribing to
     * a new Flow each time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<TaskTemplateEntity>> = combine(
        templateRepository.getAllTemplates(),
        _selectedCategory,
        _searchQuery
    ) { all, category, query ->
        val normalized = query.trim()
        all.filter { template ->
            val matchesCategory = category == null || template.category == category
            val matchesQuery = normalized.isEmpty() ||
                template.name.contains(normalized, ignoreCase = true) ||
                (template.templateTitle?.contains(normalized, ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = templateRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            try {
                templateRepository.deleteTemplate(id)
            } catch (e: Exception) {
                Log.e("TemplateListVM", "Failed to delete template", e)
            }
        }
    }
}
