package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.ProjectWithCount
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.remote.SyncTracker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository
@Inject
constructor(
    private val projectDao: ProjectDao,
    private val syncTracker: SyncTracker
) {
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getProjectById(id: Long): Flow<ProjectEntity?> = projectDao.getProjectById(id)

    fun getProjectWithTaskCount(): Flow<List<ProjectWithCount>> =
        projectDao.getProjectWithTaskCount()

    fun searchProjects(query: String): Flow<List<ProjectEntity>> = projectDao.searchProjects(query)

    suspend fun addProject(name: String, color: String = "#4A90D9", icon: String = "\uD83D\uDCC1"): Long {
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            name = name,
            color = color,
            icon = icon,
            createdAt = now,
            updatedAt = now
        )
        val id = projectDao.insert(project)
        syncTracker.trackCreate(id, "project")
        return id
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(project.id, "project")
    }

    suspend fun deleteProject(project: ProjectEntity) {
        syncTracker.trackDelete(project.id, "project")
        projectDao.delete(project)
    }
}
