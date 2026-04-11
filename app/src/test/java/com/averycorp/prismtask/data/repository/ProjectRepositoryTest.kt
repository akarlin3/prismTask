package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectWithCount
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end style tests for [ProjectRepository] using an in-memory fake
 * [ProjectDao]. SyncTracker is relaxed-mocked so we can verify the side
 * effects without caring about its internals.
 */
class ProjectRepositoryTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var syncTracker: SyncTracker
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        projectDao = FakeProjectDao()
        syncTracker = mockk(relaxed = true)
        repo = ProjectRepository(projectDao, syncTracker)
    }

    @Test
    fun addProject_storesFieldsAndTracksCreate() = runBlocking {
        val id = repo.addProject(
            name = "Launch",
            color = "#FF0000",
            icon = "\uD83D\uDE80"
        )

        val stored = projectDao.projects.single { it.id == id }
        assertEquals("Launch", stored.name)
        assertEquals("#FF0000", stored.color)
        assertEquals("\uD83D\uDE80", stored.icon)
        coVerify { syncTracker.trackCreate(id, "project") }
    }

    @Test
    fun addProject_defaultColorAndIconWhenUnspecified() = runBlocking {
        val id = repo.addProject(name = "Inbox")
        val stored = projectDao.projects.single { it.id == id }
        assertEquals("#4A90D9", stored.color)
        assertEquals("\uD83D\uDCC1", stored.icon)
    }

    @Test
    fun updateProject_bumpsUpdatedAtAndPersistsFields() = runBlocking {
        val id = projectDao.insert(ProjectEntity(name = "Old", createdAt = 1L, updatedAt = 1L))
        val existing = projectDao.projects.single { it.id == id }

        repo.updateProject(existing.copy(name = "New"))

        val after = projectDao.projects.single { it.id == id }
        assertEquals("New", after.name)
        assertTrue("updatedAt should advance after update", after.updatedAt > existing.updatedAt)
        coVerify { syncTracker.trackUpdate(id, "project") }
    }

    @Test
    fun deleteProject_removesFromStoreAndTracksDelete() = runBlocking {
        val id = projectDao.insert(ProjectEntity(name = "Throw away"))
        val existing = projectDao.projects.single { it.id == id }

        repo.deleteProject(existing)

        assertTrue(projectDao.projects.none { it.id == id })
        coVerify { syncTracker.trackDelete(id, "project") }
    }

    @Test
    fun getAllProjects_flowReflectsInsertedProjects() = runBlocking {
        projectDao.insert(ProjectEntity(name = "Alpha"))
        projectDao.insert(ProjectEntity(name = "Beta"))
        val list = repo.getAllProjects().first()
        assertEquals(2, list.size)
        assertTrue(list.any { it.name == "Alpha" })
        assertTrue(list.any { it.name == "Beta" })
    }

    @Test
    fun getProjectById_flowReturnsTheMatchingProjectOrNull() = runBlocking {
        val id = projectDao.insert(ProjectEntity(name = "Findable"))

        val hit = repo.getProjectById(id).first()
        assertNotNull(hit)
        assertEquals("Findable", hit?.name)

        val miss = repo.getProjectById(9999L).first()
        assertEquals(null, miss)
    }

    @Test
    fun searchProjects_filtersByNameSubstring() = runBlocking {
        projectDao.insert(ProjectEntity(name = "Home Renovation"))
        projectDao.insert(ProjectEntity(name = "Work Tasks"))
        projectDao.insert(ProjectEntity(name = "Homework"))

        val results = repo.searchProjects("home").first()
        assertEquals(2, results.size)
        assertTrue(results.all { it.name.contains("Home", ignoreCase = true) })
    }

    @Test
    fun multipleProjectsWithSameName_areAllPersisted() = runBlocking {
        // The repository/DAO doesn't enforce name uniqueness — duplicates should
        // coexist rather than silently dropping. This documents that contract.
        val a = repo.addProject(name = "Copy")
        val b = repo.addProject(name = "Copy")
        assertTrue(a != b)
        assertEquals(2, projectDao.projects.count { it.name == "Copy" })
    }

    // ---------------------------------------------------------------------
    // Fake DAO
    // ---------------------------------------------------------------------

    private class FakeProjectDao : ProjectDao {
        val projects = mutableListOf<ProjectEntity>()
        private var nextId = 1L

        override suspend fun insert(project: ProjectEntity): Long {
            val id = if (project.id == 0L) nextId++ else project.id.also { nextId = maxOf(nextId, it + 1) }
            projects.removeAll { it.id == id }
            projects.add(project.copy(id = id))
            return id
        }

        override suspend fun update(project: ProjectEntity) {
            val idx = projects.indexOfFirst { it.id == project.id }
            if (idx >= 0) projects[idx] = project
        }

        override suspend fun delete(project: ProjectEntity) {
            projects.removeAll { it.id == project.id }
        }

        override fun getAllProjects(): Flow<List<ProjectEntity>> = flowOf(projects.toList())

        override fun getProjectById(id: Long): Flow<ProjectEntity?> =
            flowOf(projects.firstOrNull { it.id == id })

        override suspend fun getAllProjectsOnce(): List<ProjectEntity> = projects.toList()

        override suspend fun getProjectByIdOnce(id: Long): ProjectEntity? =
            projects.firstOrNull { it.id == id }

        override fun getProjectWithTaskCount(): Flow<List<ProjectWithCount>> = flowOf(
            projects.map {
                ProjectWithCount(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    icon = it.icon,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    taskCount = 0
                )
            }
        )

        override fun searchProjects(query: String): Flow<List<ProjectEntity>> =
            flowOf(projects.filter { it.name.contains(query, ignoreCase = true) })
    }
}
