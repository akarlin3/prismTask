package com.averykarlin.averytask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.database.AveryTaskDatabase
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AveryTaskDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var projectDao: ProjectDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AveryTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        projectDao = database.projectDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun test_insertAndGetTask() = runTest {
        val task = TaskEntity(title = "Test task")
        taskDao.insert(task)

        val tasks = taskDao.getAllTasks().first()
        assertEquals(1, tasks.size)
        assertEquals("Test task", tasks[0].title)
    }

    @Test
    fun test_markCompleted() = runTest {
        val id = taskDao.insert(TaskEntity(title = "Complete me"))
        val completedAt = System.currentTimeMillis()
        taskDao.markCompleted(id, completedAt)

        val task = taskDao.getTaskById(id).first()
        assertNotNull(task)
        assertTrue(task!!.isCompleted)
        assertEquals(completedAt, task.completedAt)
    }

    @Test
    fun test_markIncomplete() = runTest {
        val id = taskDao.insert(TaskEntity(title = "Toggle me"))
        taskDao.markCompleted(id, System.currentTimeMillis())
        taskDao.markIncomplete(id, System.currentTimeMillis())

        val task = taskDao.getTaskById(id).first()
        assertNotNull(task)
        assertFalse(task!!.isCompleted)
        assertNull(task.completedAt)
    }

    @Test
    fun test_deleteById() = runTest {
        val id = taskDao.insert(TaskEntity(title = "Delete me"))
        taskDao.deleteById(id)

        val tasks = taskDao.getAllTasks().first()
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun test_getTasksByProject() = runTest {
        val projectAId = projectDao.insert(ProjectEntity(name = "Project A"))
        val projectBId = projectDao.insert(ProjectEntity(name = "Project B"))

        taskDao.insert(TaskEntity(title = "Project A task", projectId = projectAId))
        taskDao.insert(TaskEntity(title = "Project B task", projectId = projectBId))
        taskDao.insert(TaskEntity(title = "Another A task", projectId = projectAId))

        val projectATasks = taskDao.getTasksByProject(projectAId).first()
        assertEquals(2, projectATasks.size)
        assertTrue(projectATasks.all { it.projectId == projectAId })
    }

    @Test
    fun test_getSubtasks() = runTest {
        val parentId = taskDao.insert(TaskEntity(title = "Parent"))
        taskDao.insert(TaskEntity(title = "Child 1", parentTaskId = parentId))
        taskDao.insert(TaskEntity(title = "Child 2", parentTaskId = parentId))
        taskDao.insert(TaskEntity(title = "Unrelated"))

        val subtasks = taskDao.getSubtasks(parentId).first()
        assertEquals(2, subtasks.size)
        assertTrue(subtasks.all { it.parentTaskId == parentId })
    }

    @Test
    fun test_getOverdueTasks() = runTest {
        val now = System.currentTimeMillis()
        val yesterday = now - 86_400_000L
        val tomorrow = now + 86_400_000L

        taskDao.insert(TaskEntity(title = "Overdue", dueDate = yesterday))
        taskDao.insert(TaskEntity(title = "Future", dueDate = tomorrow))
        taskDao.insert(TaskEntity(title = "Overdue but done", dueDate = yesterday, isCompleted = true))

        val overdue = taskDao.getOverdueTasks(now).first()
        assertEquals(1, overdue.size)
        assertEquals("Overdue", overdue[0].title)
    }

    @Test
    fun test_getTasksDueOnDate() = runTest {
        val startOfDay = 1_700_000_000_000L
        val endOfDay = startOfDay + 86_400_000L

        taskDao.insert(TaskEntity(title = "Today", dueDate = startOfDay + 3_600_000L))
        taskDao.insert(TaskEntity(title = "Tomorrow", dueDate = endOfDay + 3_600_000L))
        taskDao.insert(TaskEntity(title = "Yesterday", dueDate = startOfDay - 3_600_000L))

        val todayTasks = taskDao.getTasksDueOnDate(startOfDay, endOfDay).first()
        assertEquals(1, todayTasks.size)
        assertEquals("Today", todayTasks[0].title)
    }
}
