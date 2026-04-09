package com.averycorp.averytask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.averytask.data.local.dao.ProjectDao
import com.averycorp.averytask.data.local.dao.TaskDao
import com.averycorp.averytask.data.local.database.AveryTaskDatabase
import com.averycorp.averytask.data.local.entity.ProjectEntity
import com.averycorp.averytask.data.local.entity.TaskEntity
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

    // --- Drag-to-reorder / custom sort ---

    @Test
    fun test_updateSortOrders_persistsNewOrder() = runTest {
        val aId = taskDao.insert(TaskEntity(title = "A"))
        val bId = taskDao.insert(TaskEntity(title = "B"))
        val cId = taskDao.insert(TaskEntity(title = "C"))

        // Reverse the order: C, B, A
        taskDao.updateSortOrders(listOf(cId to 0, bId to 1, aId to 2))

        val ordered = taskDao.getAllTasksByCustomOrder().first()
        assertEquals(listOf("C", "B", "A"), ordered.map { it.title })
    }

    @Test
    fun test_getMaxRootSortOrder_emptyAndNonEmpty() = runTest {
        // Empty: returns -1 so callers can use max + 1 as the "next" slot.
        assertEquals(-1, taskDao.getMaxRootSortOrder())

        taskDao.insert(TaskEntity(title = "first", sortOrder = 5))
        taskDao.insert(TaskEntity(title = "second", sortOrder = 17))
        taskDao.insert(TaskEntity(title = "third", sortOrder = 9))

        assertEquals(17, taskDao.getMaxRootSortOrder())
    }

    @Test
    fun test_getMaxRootSortOrder_ignoresSubtasks() = runTest {
        val parentId = taskDao.insert(TaskEntity(title = "parent", sortOrder = 3))
        // Subtask with a huge sort_order must NOT be reported because
        // subtasks use per-parent sort_order, not global root order.
        taskDao.insert(
            TaskEntity(
                title = "subtask",
                parentTaskId = parentId,
                sortOrder = 999
            )
        )

        assertEquals(3, taskDao.getMaxRootSortOrder())
    }

    @Test
    fun test_updateSortOrders_doesNotAffectOtherTasks() = runTest {
        // Simulates reordering within a group: updating the sort_order of
        // a subset must not disturb the sort_order of tasks outside the set.
        val aId = taskDao.insert(TaskEntity(title = "A", sortOrder = 0))
        val bId = taskDao.insert(TaskEntity(title = "B", sortOrder = 1))
        val cId = taskDao.insert(TaskEntity(title = "C", sortOrder = 2))
        val dId = taskDao.insert(TaskEntity(title = "D", sortOrder = 3))

        // Swap A and B only; C and D should remain untouched.
        taskDao.updateSortOrders(listOf(bId to 0, aId to 1))

        val ordered = taskDao.getAllTasksByCustomOrder().first()
        assertEquals(listOf("B", "A", "C", "D"), ordered.map { it.title })

        val cTask = taskDao.getTaskByIdOnce(cId)!!
        val dTask = taskDao.getTaskByIdOnce(dId)!!
        assertEquals(2, cTask.sortOrder)
        assertEquals(3, dTask.sortOrder)
    }

    @Test
    fun test_getAllTasksByCustomOrder_respectsSortOrder() = runTest {
        // Insert in arbitrary order; query should return them by sort_order.
        taskDao.insert(TaskEntity(title = "second", sortOrder = 10))
        taskDao.insert(TaskEntity(title = "third", sortOrder = 20))
        taskDao.insert(TaskEntity(title = "first", sortOrder = 5))

        val ordered = taskDao.getAllTasksByCustomOrder().first()
        assertEquals(listOf("first", "second", "third"), ordered.map { it.title })
    }
}
