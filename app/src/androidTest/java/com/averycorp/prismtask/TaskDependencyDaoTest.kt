package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskDependencyDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskDependencyEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDependencyDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var depDao: TaskDependencyDao
    private lateinit var taskDao: TaskDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        depDao = database.taskDependencyDao()
        taskDao = database.taskDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun uniqueIndexCollapsesDuplicateEdges() = runTest {
        val a = taskDao.insert(TaskEntity(title = "A"))
        val b = taskDao.insert(TaskEntity(title = "B"))
        val first = depDao.insert(TaskDependencyEntity(blockerTaskId = a, blockedTaskId = b))
        val second = depDao.insert(TaskDependencyEntity(blockerTaskId = a, blockedTaskId = b))
        // OnConflictStrategy.IGNORE returns -1 on conflict.
        assertEquals(-1L, second)
        assertNotNull(depDao.getByIdOnce(first))
        assertEquals(1, depDao.getAllOnce().size)
    }

    @Test
    fun blockersOfReturnsIncomingEdges() = runTest {
        val a = taskDao.insert(TaskEntity(title = "A"))
        val b = taskDao.insert(TaskEntity(title = "B"))
        val c = taskDao.insert(TaskEntity(title = "C"))
        depDao.insert(TaskDependencyEntity(blockerTaskId = a, blockedTaskId = c))
        depDao.insert(TaskDependencyEntity(blockerTaskId = b, blockedTaskId = c))

        val blockers = depDao.getBlockersOfOnce(c).map { it.blockerTaskId }.toSet()
        assertEquals(setOf(a, b), blockers)
    }

    @Test
    fun deletingTaskCascadesEdges() = runTest {
        val a = taskDao.insert(TaskEntity(title = "A"))
        val b = taskDao.insert(TaskEntity(title = "B"))
        depDao.insert(TaskDependencyEntity(blockerTaskId = a, blockedTaskId = b))
        assertEquals(1, depDao.getAllOnce().size)

        val taskA = taskDao.getTaskByIdOnce(a)!!
        taskDao.delete(taskA)

        assertEquals(0, depDao.getAllOnce().size)
    }

    @Test
    fun findEdgeIdLooksUpExistingPair() = runTest {
        val a = taskDao.insert(TaskEntity(title = "A"))
        val b = taskDao.insert(TaskEntity(title = "B"))
        assertNull(depDao.findEdgeIdOnce(a, b))
        val id = depDao.insert(TaskDependencyEntity(blockerTaskId = a, blockedTaskId = b))
        assertEquals(id, depDao.findEdgeIdOnce(a, b))
    }
}
