package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.AveryTaskDatabase
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AveryTaskDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var taskDao: TaskDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AveryTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectDao = database.projectDao()
        taskDao = database.taskDao()
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
}
