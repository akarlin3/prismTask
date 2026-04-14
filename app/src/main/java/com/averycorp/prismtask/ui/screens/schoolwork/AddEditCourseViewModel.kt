package com.averycorp.prismtask.ui.screens.schoolwork

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.entity.CourseEntity
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditCourseViewModel
    @Inject
    constructor(
        private val repository: SchoolworkRepository,
        savedStateHandle: SavedStateHandle
    ) : ViewModel() {
        private val courseId: Long = savedStateHandle.get<Long>("courseId") ?: -1L
        val isEditing = courseId != -1L

        private val _name = MutableStateFlow("")
        val name: StateFlow<String> = _name

        private val _code = MutableStateFlow("")
        val code: StateFlow<String> = _code

        private val _icon = MutableStateFlow("\uD83D\uDCDA")
        val icon: StateFlow<String> = _icon

        private val _color = MutableStateFlow(0)
        val color: StateFlow<Int> = _color

        init {
            if (isEditing) {
                viewModelScope.launch {
                    repository.getCourseById(courseId)?.let { course ->
                        _name.value = course.name
                        _code.value = course.code
                        _icon.value = course.icon
                        _color.value = course.color
                    }
                }
            }
        }

        fun onNameChange(value: String) {
            _name.value = value
        }

        fun onCodeChange(value: String) {
            _code.value = value
        }

        fun onIconChange(value: String) {
            _icon.value = value
        }

        fun onColorChange(value: Int) {
            _color.value = value
        }

        fun save(onDone: () -> Unit) {
            if (_name.value.isBlank() || _code.value.isBlank()) return
            viewModelScope.launch {
                if (isEditing) {
                    val existing = repository.getCourseById(courseId) ?: return@launch
                    repository.updateCourse(
                        existing.copy(
                            name = _name.value.trim(),
                            code = _code.value.trim(),
                            icon = _icon.value,
                            color = _color.value
                        )
                    )
                } else {
                    repository.insertCourse(
                        CourseEntity(
                            name = _name.value.trim(),
                            code = _code.value.trim(),
                            icon = _icon.value,
                            color = _color.value
                        )
                    )
                }
                onDone()
            }
        }
    }
