package com.averycorp.prismtask.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.HighlightedText
import com.averycorp.prismtask.ui.components.RichEmptyState
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.theme.LocalPriorityColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val taskResults by viewModel.taskResults.collectAsStateWithLifecycle()
    val tagResults by viewModel.tagResults.collectAsStateWithLifecycle()
    val projectResults by viewModel.projectResults.collectAsStateWithLifecycle()
    val descriptionPreviewLines by viewModel.descriptionPreviewLines.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search tasks, tags, and projects") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (query.isBlank()) {
                RichEmptyState(
                    icon = "\uD83D\uDD0D",
                    title = "Search",
                    description = "Search tasks, tags, and projects"
                )
            } else {
                val hasResults = taskResults.isNotEmpty() || tagResults.isNotEmpty() || projectResults.isNotEmpty()

                if (!hasResults) {
                    RichEmptyState(
                        icon = "\uD83D\uDD0D",
                        title = "No Results",
                        description = "Try different keywords or check your spelling."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (taskResults.isNotEmpty()) {
                            item {
                                SectionHeader("Tasks (${taskResults.size})")
                            }
                            items(taskResults, key = { "task_${it.id}" }) { task ->
                                SearchTaskItem(
                                    task = task,
                                    query = query,
                                    onClick = {
                                        navController.navigate(
                                            PrismTaskRoute.AddEditTask.createRoute(task.id)
                                        )
                                    },
                                    descriptionMaxLines = descriptionPreviewLines
                                )
                            }
                        }

                        if (tagResults.isNotEmpty()) {
                            item {
                                SectionHeader("Tags (${tagResults.size})")
                            }
                            items(tagResults, key = { "tag_${it.id}" }) { tag ->
                                SearchTagItem(
                                    tag = tag,
                                    query = query,
                                    onClick = {
                                        navController.navigate(PrismTaskRoute.TagManagement.route)
                                    }
                                )
                            }
                        }

                        if (projectResults.isNotEmpty()) {
                            item {
                                SectionHeader("Projects (${projectResults.size})")
                            }
                            items(projectResults, key = { "project_${it.id}" }) { project ->
                                SearchProjectItem(
                                    project = project,
                                    query = query,
                                    onClick = {
                                        navController.navigate(PrismTaskRoute.ProjectList.route)
                                    }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SearchTaskItem(
    task: TaskEntity,
    query: String,
    onClick: () -> Unit,
    descriptionMaxLines: Int = 2
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularCheckbox(
                checked = task.isCompleted,
                onCheckedChange = null,
                enabled = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = task.title,
                    query = query,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
                if (!task.description.isNullOrBlank()) {
                    HighlightedText(
                        text = task.description,
                        query = query,
                        maxLines = descriptionMaxLines,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(LocalPriorityColors.current.forLevel(task.priority))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun SearchTagItem(
    tag: TagEntity,
    query: String,
    onClick: () -> Unit
) {
    val tagColor = try {
        Color(android.graphics.Color.parseColor(tag.color))
    } catch (_: Exception) {
        Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(tagColor.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tagColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        HighlightedText(
            text = tag.name,
            query = query,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchProjectItem(
    project: ProjectEntity,
    query: String,
    onClick: () -> Unit
) {
    val projectColor = try {
        Color(android.graphics.Color.parseColor(project.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = projectColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = project.icon,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            HighlightedText(
                text = project.name,
                query = query,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
