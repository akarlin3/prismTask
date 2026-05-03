package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectRiskDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.ProjectRiskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema-level coverage for `project_risks`. Validates the open-first
 * sort and parent FK cascade.
 */
@RunWith(AndroidJUnit4::class)
class ProjectRiskDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var riskDao: ProjectRiskDao
    private lateinit var projectDao: ProjectDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        riskDao = database.projectRiskDao()
        projectDao = database.projectDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun openRisksSortBeforeResolved() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        val resolved = riskDao.insert(
            ProjectRiskEntity(
                projectId = projectId,
                title = "Old",
                level = "HIGH",
                resolvedAt = 100L,
                createdAt = 50L,
                updatedAt = 50L
            )
        )
        val open = riskDao.insert(
            ProjectRiskEntity(
                projectId = projectId,
                title = "Active",
                level = "MEDIUM",
                resolvedAt = null,
                createdAt = 10L,
                updatedAt = 10L
            )
        )

        val risks = riskDao.getRisksOnce(projectId)
        assertEquals(2, risks.size)
        // Open risk must appear first regardless of created_at ordering.
        assertEquals(open, risks[0].id)
        assertEquals(resolved, risks[1].id)
    }

    @Test
    fun deletingProjectCascadesToRisks() = runTest {
        val projectId = projectDao.insert(ProjectEntity(name = "P"))
        riskDao.insert(ProjectRiskEntity(projectId = projectId, title = "Risk A"))
        riskDao.insert(ProjectRiskEntity(projectId = projectId, title = "Risk B"))

        val project = projectDao.getProjectByIdOnce(projectId)!!
        projectDao.delete(project)

        assertEquals(0, riskDao.getAllRisksOnce().size)
    }
}
