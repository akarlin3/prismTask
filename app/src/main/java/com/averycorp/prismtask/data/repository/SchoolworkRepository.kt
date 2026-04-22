package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.entity.AssignmentEntity
import com.averycorp.prismtask.data.local.entity.CourseCompletionEntity
import com.averycorp.prismtask.data.local.entity.CourseEntity
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.util.DayBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SchoolworkRepository
@Inject
constructor(
    private val dao: SchoolworkDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val syncTracker: SyncTracker
) {
    private suspend fun startOfToday(): Long =
        DayBoundary.startOfCurrentDay(taskBehaviorPreferences.getDayStartHour().first())

    private suspend fun todayLocalString(): String =
        DayBoundary.currentLocalDateString(taskBehaviorPreferences.getDayStartHour().first())

    // --- Courses ---

    fun getActiveCourses(): Flow<List<CourseEntity>> = dao.getActiveCourses()

    fun getAllCourses(): Flow<List<CourseEntity>> = dao.getAllCourses()

    suspend fun getCourseById(id: Long): CourseEntity? = dao.getCourseById(id)

    suspend fun insertCourse(course: CourseEntity): Long {
        val id = dao.insertCourse(course.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackCreate(id, "course")
        return id
    }

    suspend fun updateCourse(course: CourseEntity) {
        dao.updateCourse(course.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(course.id, "course")
    }

    suspend fun deleteCourse(id: Long) {
        syncTracker.trackDelete(id, "course")
        dao.deleteCourse(id)
    }

    // --- Assignments ---

    fun getAssignmentsForCourse(courseId: Long): Flow<List<AssignmentEntity>> =
        dao.getAssignmentsForCourse(courseId)

    fun getActiveAssignments(): Flow<List<AssignmentEntity>> = dao.getActiveAssignments()

    fun getAllAssignments(): Flow<List<AssignmentEntity>> = dao.getAllAssignments()

    suspend fun getAssignmentById(id: Long): AssignmentEntity? = dao.getAssignmentById(id)

    suspend fun insertAssignment(assignment: AssignmentEntity): Long {
        val id = dao.insertAssignment(assignment.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackCreate(id, "assignment")
        return id
    }

    suspend fun updateAssignment(assignment: AssignmentEntity) {
        dao.updateAssignment(assignment.copy(updatedAt = System.currentTimeMillis()))
        syncTracker.trackUpdate(assignment.id, "assignment")
    }

    suspend fun deleteAssignment(id: Long) {
        dao.deleteAssignment(id)
        syncTracker.trackDelete(id, "assignment")
    }

    suspend fun toggleAssignmentComplete(id: Long) {
        val assignment = dao.getAssignmentById(id) ?: return
        val now = if (!assignment.completed) System.currentTimeMillis() else null
        dao.updateAssignment(
            assignment.copy(
                completed = !assignment.completed,
                completedAt = now,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncTracker.trackUpdate(id, "assignment")
    }

    fun getActiveAssignmentCount(courseId: Long): Flow<Int> = dao.getActiveAssignmentCount(courseId)

    // --- Course Completions (daily habit) ---

    fun getTodayCompletions(): Flow<List<CourseCompletionEntity>> =
        taskBehaviorPreferences.getDayStartHour().flatMapLatest { hour ->
            dao.getCompletionsForDate(DayBoundary.startOfCurrentDay(hour))
        }

    suspend fun toggleCourseCompletion(courseId: Long) {
        val today = startOfToday()
        val existing = dao.getCompletionOnce(today, courseId)
        if (existing != null) {
            if (existing.completed) {
                syncTracker.trackDelete(existing.id, "course_completion")
                dao.deleteCompletion(today, courseId)
            } else {
                dao.updateCompletion(
                    existing.copy(
                        completed = true,
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                syncTracker.trackUpdate(existing.id, "course_completion")
            }
        } else {
            val id = dao.insertCompletion(
                CourseCompletionEntity(
                    date = today,
                    courseId = courseId,
                    completed = true,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            syncTracker.trackCreate(id, "course_completion")
        }
        syncHabitCompletion()
    }

    suspend fun resetToday() {
        val today = startOfToday()
        dao.getCompletionsForDateOnce(today).forEach { syncTracker.trackDelete(it.id, "course_completion") }
        dao.deleteCompletionsForDate(today)
        syncHabitCompletion()
    }

    // --- Habit sync ---

    suspend fun ensureHabitExists() {
        getOrCreateSchoolHabit()
    }

    private suspend fun getOrCreateSchoolHabit(): HabitEntity {
        val existing = habitDao.getHabitByName(SCHOOL_HABIT_NAME)
        if (existing != null) return existing
        val id = habitDao.insert(
            HabitEntity(
                name = SCHOOL_HABIT_NAME,
                description = "Complete daily work for all courses",
                icon = "\uD83C\uDF93",
                color = "#CFB87C",
                category = "School",
                targetFrequency = 1,
                frequencyPeriod = "daily",
                isBuiltIn = true,
                templateKey = "builtin_school"
            )
        )
        return habitDao.getHabitByIdOnce(id)
            ?: error("Habit not found after insert")
    }

    private suspend fun syncHabitCompletion() {
        val habit = getOrCreateSchoolHabit()
        val today = startOfToday()
        val todayLocal = todayLocalString()
        val completions = dao.getCompletionsForDateOnce(today)
        // Need to know how many active courses there are
        // Use a one-shot query approach
        val courseCount = dao.getActiveCourseCount()
        val completedCount = completions.count { it.completed }
        val allDone = courseCount > 0 && completedCount >= courseCount
        val alreadyCompleted = habitCompletionDao.isCompletedOnDateLocalOnce(habit.id, todayLocal)

        if (allDone && !alreadyCompleted) {
            habitCompletionDao.insert(
                HabitCompletionEntity(
                    habitId = habit.id,
                    completedDate = today,
                    completedAt = System.currentTimeMillis(),
                    completedDateLocal = todayLocal
                )
            )
        } else if (!allDone && alreadyCompleted) {
            habitCompletionDao.deleteByHabitAndDateLocal(habit.id, todayLocal)
        }
    }

    companion object {
        const val SCHOOL_HABIT_NAME = "School"
    }
}
