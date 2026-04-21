package com.averycorp.prismtask.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.PrismTaskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One-off config activity shown by the launcher when the user drops a
 * [ProjectWidget] onto their home screen. Picks which project the widget
 * will track and persists it to [WidgetConfigDataStore]. Setting
 * `RESULT_OK` back with the `appWidgetId` extra is required by the
 * `AppWidgetManager` contract.
 */
@AndroidEntryPoint
class ProjectWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var database: PrismTaskDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to CANCELED: if the user backs out, the widget is removed.
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val state = MutableStateFlow<List<ProjectEntity>>(emptyList())
        lifecycleScope.launch {
            val active = database.projectDao().getAllProjectsOnce()
                .filter { it.status == "ACTIVE" }
            state.value = active
        }

        setContent {
            PrismTaskTheme {
                ProjectPicker(
                    projectsFlow = state,
                    onCancel = { finish() },
                    onConfirm = { projectId ->
                        lifecycleScope.launch {
                            WidgetConfigDataStore.setProjectConfig(
                                this@ProjectWidgetConfigActivity,
                                appWidgetId,
                                WidgetConfigDataStore.ProjectConfig(projectId = projectId)
                            )
                            // Ask Glance to immediately render the newly-bound
                            // widget with the selected project.
                            runCatching { ProjectWidget().updateAll(this@ProjectWidgetConfigActivity) }

                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(
                                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                                    appWidgetId
                                )
                            )
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPicker(
    projectsFlow: StateFlow<List<ProjectEntity>>,
    onCancel: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val prismColors = LocalPrismColors.current
    val projects by projectsFlow.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = prismColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pick A Project",
                        fontWeight = FontWeight.Bold,
                        color = prismColors.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = prismColors.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (projects.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Active Projects",
                        style = MaterialTheme.typography.titleMedium,
                        color = prismColors.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Open PrismTask And Create A Project First.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = prismColors.muted
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectOption(
                            project = project,
                            selected = selectedId == project.id,
                            onSelect = { selectedId = project.id }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { selectedId?.let(onConfirm) },
                    enabled = selectedId != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ProjectOption(
    project: ProjectEntity,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val prismColors = LocalPrismColors.current
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(project.themeColorKey ?: project.color))
    }.getOrDefault(prismColors.primary)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) prismColors.tagSurface else prismColors.surface)
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = project.icon,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = project.name,
            style = MaterialTheme.typography.bodyLarge,
            color = prismColors.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
}
