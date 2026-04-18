package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.MilestoneDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.TaskCompletionDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.MilestoneEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskCompletionEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var taskDao: TaskDao
    private lateinit var milestoneDao: MilestoneDao
    private lateinit var taskCompletionDao: TaskCompletionDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectDao = database.projectDao()
        taskDao = database.taskDao()
        milestoneDao = database.milestoneDao()
        taskCompletionDao = database.taskCompletionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun test_insertAndGetProject() = runTest {
        val project = ProjectEntity(name = "Work", color = "#FF0000", icon = "\uD83D\uDCBC")
        projectDao.insert(project)

        val projects = projectDao.getAllProjects().first()
        assertEquals(1, projects.size)
        assertEquals("Work", projects[0].name)
        assertEquals("#FF0000", projects[0].color)
    }

    @Test
    fun test_deleteProject() = runTest {
        val project = ProjectEntity(name = "Temp")
        val id = projectDao.insert(project)
        projectDao.delete(project.copy(id = id))

        val projects = projectDao.getAllProjects().first()
        assertTrue(projects.isEmpty())
    }

    @Test
    fun test_getProjectWithTaskCount() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "Dev"))
        val emptyProjectId = projectDao.insert(ProjectEntity(name = "Empty"))

        taskDao.insert(TaskEntity(title = "Task 1", projectId = projectId))
        taskDao.insert(TaskEntity(title = "Task 2", projectId = projectId))
        taskDao.insert(TaskEntity(title = "Task 3", projectId = projectId))

        val results = projectDao.getProjectWithTaskCount().first()
        assertEquals(2, results.size)

        val devProject = results.find { it.name == "Dev" }!!
        assertEquals(3, devProject.taskCount)

        val emptyProject = results.find { it.name == "Empty" }!!
        assertEquals(0, emptyProject.taskCount)
    }

    // ---------------------------------------------------------------------
    // v1.4.0 Projects feature coverage
    // ---------------------------------------------------------------------

    @Test
    fun observeByStatus_filtersOutNonMatchingRows() = runTest {
        projectDao.insert(ProjectEntity(name = "A", status = "ACTIVE"))
        projectDao.insert(ProjectEntity(name = "B", status = "ACTIVE"))
        projectDao.insert(ProjectEntity(name = "C", status = "COMPLETED"))
        projectDao.insert(ProjectEntity(name = "D", status = "ARCHIVED"))

        assertEquals(2, projectDao.observeByStatus("ACTIVE").first().size)
        assertEquals(1, projectDao.observeByStatus("COMPLETED").first().size)
        assertEquals(1, projectDao.observeByStatus("ARCHIVED").first().size)
    }

    @Test
    fun getAggregateRow_countsMilestonesAndPicksFirstOpenUpcoming() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "Aggregates"))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "First", orderIndex = 0, isCompleted = true, completedAt = 1000L))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "Second", orderIndex = 1))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "Third", orderIndex = 2))
        taskDao.insert(TaskEntity(title = "t1", projectId = pid))
        taskDao.insert(TaskEntity(title = "t2", projectId = pid, isCompleted = true))

        val agg = projectDao.getAggregateRow(pid)!!
        assertEquals(3, agg.totalMilestones)
        assertEquals(1, agg.completedMilestones)
        assertEquals("Second", agg.upcomingMilestoneTitle)
        assertEquals(2, agg.totalTasks)
        assertEquals(1, agg.openTasks)
    }

    @Test
    fun getAggregateRow_excludesSubtasksFromTaskCounts() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "WithSubs"))
        val parentId = taskDao.insert(TaskEntity(title = "parent", projectId = pid))
        taskDao.insert(TaskEntity(title = "sub1", projectId = pid, parentTaskId = parentId))
        taskDao.insert(TaskEntity(title = "sub2", projectId = pid, parentTaskId = parentId, isCompleted = true))

        val agg = projectDao.getAggregateRow(pid)!!
        // Subtasks are hidden from the top-line counter — only the parent counts.
        assertEquals(1, agg.totalTasks)
        assertEquals(1, agg.openTasks)
    }

    @Test
    fun getAggregateRow_returnsNullForUnknownProject() = runTest {
        assertNull(projectDao.getAggregateRow(9999L))
    }

    @Test
    fun getTaskActivityDates_returnsDirectTaskCompletions() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "A"))
        val t1 = taskDao.insert(TaskEntity(title = "t1", projectId = pid))
        val t2 = taskDao.insert(TaskEntity(title = "t2", projectId = pid))
        taskCompletionDao.insert(TaskCompletionEntity(taskId = t1, projectId = pid, completedDate = 1000L))
        taskCompletionDao.insert(TaskCompletionEntity(taskId = t2, projectId = pid, completedDate = 2000L))

        val dates = projectDao.getTaskActivityDates(pid)
        assertEquals(setOf(1000L, 2000L), dates.toSet())
    }

    @Test
    fun getTaskActivityDates_inheritsSubtaskCompletionsFromParentProject() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "Parent project"))
        val parent = taskDao.insert(TaskEntity(title = "parent", projectId = pid))
        // Subtask has NO project_id of its own — inheritance at read time.
        val sub = taskDao.insert(TaskEntity(title = "sub", projectId = null, parentTaskId = parent))
        taskCompletionDao.insert(TaskCompletionEntity(taskId = sub, projectId = null, completedDate = 5000L))

        val dates = projectDao.getTaskActivityDates(pid)
        assertEquals(listOf(5000L), dates)
    }

    @Test
    fun getTaskActivityDates_excludesUnrelatedProjectCompletions() = runTest {
        val a = projectDao.insert(ProjectEntity(name = "A"))
        val b = projectDao.insert(ProjectEntity(name = "B"))
        val ta = taskDao.insert(TaskEntity(title = "ta", projectId = a))
        val tb = taskDao.insert(TaskEntity(title = "tb", projectId = b))
        taskCompletionDao.insert(TaskCompletionEntity(taskId = ta, projectId = a, completedDate = 1L))
        taskCompletionDao.insert(TaskCompletionEntity(taskId = tb, projectId = b, completedDate = 2L))

        assertEquals(listOf(1L), projectDao.getTaskActivityDates(a))
        assertEquals(listOf(2L), projectDao.getTaskActivityDates(b))
    }

    @Test
    fun milestones_cascadeDeleteWhenProjectDeleted() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "To delete"))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "will vanish", orderIndex = 0))
        assertEquals(1, milestoneDao.getMilestonesOnce(pid).size)

        projectDao.delete(ProjectEntity(id = pid, name = "ignored"))

        assertEquals(0, milestoneDao.getMilestonesOnce(pid).size)
    }

    @Test
    fun milestones_retainInsertOrderByOrderIndex() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "Ordered"))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "B", orderIndex = 1))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "A", orderIndex = 0))
        milestoneDao.insert(MilestoneEntity(projectId = pid, title = "C", orderIndex = 2))

        val titles = milestoneDao.getMilestonesOnce(pid).map { it.title }
        assertEquals(listOf("A", "B", "C"), titles)
    }

    @Test
    fun projectEntity_newFieldsDefaultToActiveAndNull() = runTest {
        val pid = projectDao.insert(ProjectEntity(name = "Default"))
        val loaded = projectDao.getProjectByIdOnce(pid)
        assertNotNull(loaded)
        assertEquals("ACTIVE", loaded!!.status)
        assertNull(loaded.description)
        assertNull(loaded.themeColorKey)
        assertNull(loaded.startDate)
        assertNull(loaded.endDate)
        assertNull(loaded.completedAt)
        assertNull(loaded.archivedAt)
    }
}
