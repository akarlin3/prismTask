package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.ProjectWithCount
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getProjectById(id: Long): Flow<ProjectEntity?> = projectDao.getProjectById(id)

    fun getProjectWithTaskCount(): Flow<List<ProjectWithCount>> =
        projectDao.getProjectWithTaskCount()

    suspend fun addProject(name: String, color: String = "#4A90D9", icon: String = "\uD83D\uDCC1"): Long {
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            name = name,
            color = color,
            icon = icon,
            createdAt = now,
            updatedAt = now
        )
        return projectDao.insert(project)
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.delete(project)
    }
}
