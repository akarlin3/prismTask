package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.ExternalAnchorDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectPhaseDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.ExternalAnchorEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.ProjectPhaseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAnchorDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var dao: ExternalAnchorDao
    private lateinit var projectDao: ProjectDao
    private lateinit var phaseDao: ProjectPhaseDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.externalAnchorDao()
        projectDao = database.projectDao()
        phaseDao = database.projectPhaseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun anchorsListedNewestFirst() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val older = dao.insert(
            ExternalAnchorEntity(
                projectId = projectId,
                label = "Older",
                anchorJson = "{}",
                createdAt = 100L,
                updatedAt = 100L
            )
        )
        val newer = dao.insert(
            ExternalAnchorEntity(
                projectId = projectId,
                label = "Newer",
                anchorJson = "{}",
                createdAt = 200L,
                updatedAt = 200L
            )
        )
        val rows = dao.getAnchorsOnce(projectId)
        assertEquals(listOf(newer, older), rows.map { it.id })
    }

    @Test
    fun phaseDeleteSetsPhaseIdNullProjectDeleteCascades() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val phaseId = phaseDao.insert(
            ProjectPhaseEntity(projectId = projectId, title = "Phase F")
        )
        val anchorId = dao.insert(
            ExternalAnchorEntity(
                projectId = projectId,
                phaseId = phaseId,
                label = "Linked",
                anchorJson = "{}"
            )
        )

        // Phase delete -> SET NULL on the anchor.
        val phase = phaseDao.getByIdOnce(phaseId)!!
        phaseDao.delete(phase)
        assertNull(dao.getByIdOnce(anchorId)!!.phaseId)

        // Project delete -> CASCADE removes anchor.
        val project = projectDao.getProjectByIdOnce(projectId)!!
        projectDao.delete(project)
        assertNull(dao.getByIdOnce(anchorId))
    }

    @Test
    fun getAnchorsForPhaseFiltersToPhase() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val phaseA = phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "A"))
        val phaseB = phaseDao.insert(ProjectPhaseEntity(projectId = projectId, title = "B"))
        dao.insert(
            ExternalAnchorEntity(
                projectId = projectId, phaseId = phaseA, label = "in-A", anchorJson = "{}"
            )
        )
        dao.insert(
            ExternalAnchorEntity(
                projectId = projectId, phaseId = phaseB, label = "in-B", anchorJson = "{}"
            )
        )
        dao.insert(
            ExternalAnchorEntity(
                projectId = projectId, phaseId = null, label = "no-phase", anchorJson = "{}"
            )
        )
        assertEquals(1, dao.getAnchorsForPhaseOnce(phaseA).size)
        assertEquals(3, dao.getAnchorsOnce(projectId).size)
    }
}
