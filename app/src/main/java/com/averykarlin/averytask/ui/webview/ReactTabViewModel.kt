package com.averykarlin.averytask.ui.webview

import androidx.lifecycle.ViewModel
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TagRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ReactTabViewModel @Inject constructor(
    val taskRepository: TaskRepository,
    val projectRepository: ProjectRepository,
    val habitRepository: HabitRepository,
    val tagRepository: TagRepository,
    val themePreferences: ThemePreferences
) : ViewModel()
