package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectPhaseDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema-level coverage for the `project_phases` table introduced in
 * `MIGRATION_72_73`. Pins parent FK CASCADE + order-index management.
 */
@RunWith(AndroidJUnit4::class)
class ProjectPhaseDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var phaseDao: ProjectPhaseDao
    private lateinit var projectDao: ProjectDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        phaseDao = database.projectPhaseDao()
        projectDao = database.projectDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertReturnsRowAndOrdersByIndex() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val first = phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "B", orderIndex = 1))
        val second = phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "A", orderIndex = 0))

        val phases = phaseDao.getPhasesOnce(projectId)
        assertEquals(2, phases.size)
        assertEquals(second, phases[0].id)
        assertEquals(first, phases[1].id)
    }

    @Test
    fun getMaxOrderIndexReturnsMinusOneWhenEmpty() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        assertEquals(-1, phaseDao.getMaxOrderIndex(projectId))
        phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "X", orderIndex = 7))
        assertEquals(7, phaseDao.getMaxOrderIndex(projectId))
    }

    @Test
    fun deletingProjectCascadesToPhases() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val phaseId = phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "Phase F"))
        assertNotNull(phaseDao.getByIdOnce(phaseId))

        // Use the entity-deletion path so Room actually applies the FK cascade
        // — `deleteById` is a raw query that bypasses Room's FK trigger graph
        // when invoked through the test database.
        val project = projectDao.getProjectByIdOnce(projectId)
        assertNotNull(project)
        projectDao.delete(project!!)

        assertNull(phaseDao.getByIdOnce(phaseId))
    }
}
