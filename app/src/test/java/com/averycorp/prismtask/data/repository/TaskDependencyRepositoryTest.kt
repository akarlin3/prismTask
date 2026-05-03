package com.averycorp.prismtask.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TaskDependencyRepositoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var repo: TaskDependencyRepository
    private lateinit var syncTracker: SyncTracker

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncTracker = mockk(relaxed = true)
        repo = TaskDependencyRepository(database.taskDependencyDao(), syncTracker)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun addDependency_rejectsCycle() = runTest {
        val a = database.taskDao().insert(TaskEntity(title = "A"))
        val b = database.taskDao().insert(TaskEntity(title = "B"))
        repo.addDependency(a, b).getOrThrow()

        val result = repo.addDependency(b, a)
        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is TaskDependencyRepository.DependencyError.CycleRejected)
    }

    @Test
    fun addDependency_isIdempotentOnDuplicate() = runTest {
        val a = database.taskDao().insert(TaskEntity(title = "A"))
        val b = database.taskDao().insert(TaskEntity(title = "B"))
        val first = repo.addDependency(a, b).getOrThrow()
        val second = repo.addDependency(a, b).getOrThrow()
        assertEquals("idempotent — same row id returned", first, second)
    }

    @Test
    fun removeDependency_swallowsMissingEdge() = runTest {
        val a = database.taskDao().insert(TaskEntity(title = "A"))
        val b = database.taskDao().insert(TaskEntity(title = "B"))
        // No edge exists; removeDependency should be a no-op.
        repo.removeDependency(a, b)
        assertEquals(0, repo.getBlockersOf(b).size)
    }

    @Test
    fun addDependency_tracksSyncCreate() = runTest {
        val a = database.taskDao().insert(TaskEntity(title = "A"))
        val b = database.taskDao().insert(TaskEntity(title = "B"))
        coEvery { syncTracker.trackCreate(any(), any()) } returns Unit

        val edgeId = repo.addDependency(a, b).getOrThrow()

        coVerify(exactly = 1) { syncTracker.trackCreate(edgeId, "task_dependency") }
    }
}
