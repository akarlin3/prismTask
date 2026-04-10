package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
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

    private lateinit var database: PrismTaskDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var projectDao: ProjectDao
    private lateinit var tagDao: TagDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        projectDao = database.projectDao()
        tagDao = database.tagDao()
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

    // --- Batch edit operations (multi-select bulk editing) ---

    @Test
    fun test_batchUpdatePriority_updatesAllSpecifiedTasks() = runTest {
        // Three tasks all start with priority 0 (None) — the batch should
        // leave every one of them at priority 3 (High).
        val id1 = taskDao.insert(TaskEntity(title = "A", priority = 0))
        val id2 = taskDao.insert(TaskEntity(title = "B", priority = 0))
        val id3 = taskDao.insert(TaskEntity(title = "C", priority = 0))

        taskDao.batchUpdatePriority(listOf(id1, id2, id3), priority = 3)

        assertEquals(3, taskDao.getTaskByIdOnce(id1)!!.priority)
        assertEquals(3, taskDao.getTaskByIdOnce(id2)!!.priority)
        assertEquals(3, taskDao.getTaskByIdOnce(id3)!!.priority)
    }

    @Test
    fun test_batchUpdatePriority_doesNotAffectUnselectedTasks() = runTest {
        // Only the two selected tasks should see their priority change;
        // the third is left on its original value.
        val id1 = taskDao.insert(TaskEntity(title = "A", priority = 1))
        val id2 = taskDao.insert(TaskEntity(title = "B", priority = 1))
        val untouched = taskDao.insert(TaskEntity(title = "C", priority = 2))

        taskDao.batchUpdatePriority(listOf(id1, id2), priority = 4)

        assertEquals(4, taskDao.getTaskByIdOnce(id1)!!.priority)
        assertEquals(4, taskDao.getTaskByIdOnce(id2)!!.priority)
        // Unselected task must keep its original priority.
        assertEquals(2, taskDao.getTaskByIdOnce(untouched)!!.priority)
    }

    @Test
    fun test_batchReschedule_setsNewDueDateForAllTasks() = runTest {
        val id1 = taskDao.insert(TaskEntity(title = "A", dueDate = 1_000L))
        val id2 = taskDao.insert(TaskEntity(title = "B", dueDate = 2_000L))
        val newDate = 9_999L

        taskDao.batchReschedule(listOf(id1, id2), newDate)

        assertEquals(newDate, taskDao.getTaskByIdOnce(id1)!!.dueDate)
        assertEquals(newDate, taskDao.getTaskByIdOnce(id2)!!.dueDate)
    }

    @Test
    fun test_batchReschedule_handlesNullDateToRemoveDueDate() = runTest {
        // Regression: passing null must clear the due_date column, not
        // no-op. This is the "Remove Date" affordance in the popup.
        val id1 = taskDao.insert(TaskEntity(title = "A", dueDate = 1_000L))
        val id2 = taskDao.insert(TaskEntity(title = "B", dueDate = 2_000L))

        taskDao.batchReschedule(listOf(id1, id2), newDueDate = null)

        assertNull(taskDao.getTaskByIdOnce(id1)!!.dueDate)
        assertNull(taskDao.getTaskByIdOnce(id2)!!.dueDate)
    }

    @Test
    fun test_batchReschedule_doesNotAffectUnselectedTasks() = runTest {
        val id1 = taskDao.insert(TaskEntity(title = "A", dueDate = 1_000L))
        val untouched = taskDao.insert(TaskEntity(title = "B", dueDate = 2_000L))

        taskDao.batchReschedule(listOf(id1), newDueDate = 5_000L)

        assertEquals(5_000L, taskDao.getTaskByIdOnce(id1)!!.dueDate)
        // The unselected task must keep its original due date.
        assertEquals(2_000L, taskDao.getTaskByIdOnce(untouched)!!.dueDate)
    }

    @Test
    fun test_batchAddTag_createsCrossReferencesForAllTasks() = runTest {
        val id1 = taskDao.insert(TaskEntity(title = "A"))
        val id2 = taskDao.insert(TaskEntity(title = "B"))
        val id3 = taskDao.insert(TaskEntity(title = "C"))
        val tagId = tagDao.insert(TagEntity(name = "urgent"))

        taskDao.batchAddTag(listOf(id1, id2, id3), tagId)

        // Every task should now carry the tag.
        assertEquals(listOf(tagId), tagDao.getTagIdsForTaskOnce(id1))
        assertEquals(listOf(tagId), tagDao.getTagIdsForTaskOnce(id2))
        assertEquals(listOf(tagId), tagDao.getTagIdsForTaskOnce(id3))
    }

    @Test
    fun test_batchRemoveTag_removesCrossReferencesForAllTasks() = runTest {
        val id1 = taskDao.insert(TaskEntity(title = "A"))
        val id2 = taskDao.insert(TaskEntity(title = "B"))
        val tagId = tagDao.insert(TagEntity(name = "deprecated"))
        tagDao.addTagToTask(TaskTagCrossRef(taskId = id1, tagId = tagId))
        tagDao.addTagToTask(TaskTagCrossRef(taskId = id2, tagId = tagId))

        taskDao.batchRemoveTag(listOf(id1, id2), tagId)

        assertTrue(tagDao.getTagIdsForTaskOnce(id1).isEmpty())
        assertTrue(tagDao.getTagIdsForTaskOnce(id2).isEmpty())
    }

    @Test
    fun test_batchMoveToProject_updatesProjectIdAndAllowsNullToUnassign() = runTest {
        val projectAId = projectDao.insert(ProjectEntity(name = "A"))
        val projectBId = projectDao.insert(ProjectEntity(name = "B"))
        val id1 = taskDao.insert(TaskEntity(title = "t1", projectId = projectAId))
        val id2 = taskDao.insert(TaskEntity(title = "t2", projectId = projectAId))
        val untouched = taskDao.insert(TaskEntity(title = "t3", projectId = projectAId))

        // Move id1 + id2 into B. untouched stays in A.
        taskDao.batchMoveToProject(listOf(id1, id2), projectBId)
        assertEquals(projectBId, taskDao.getTaskByIdOnce(id1)!!.projectId)
        assertEquals(projectBId, taskDao.getTaskByIdOnce(id2)!!.projectId)
        assertEquals(projectAId, taskDao.getTaskByIdOnce(untouched)!!.projectId)

        // Now remove id1 from any project by passing null.
        taskDao.batchMoveToProject(listOf(id1), null)
        assertNull(taskDao.getTaskByIdOnce(id1)!!.projectId)
        // id2 must stay in B.
        assertEquals(projectBId, taskDao.getTaskByIdOnce(id2)!!.projectId)
    }

}
