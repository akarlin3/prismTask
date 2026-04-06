package com.averykarlin.averytask.data.repository

import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.SchoolworkDao
import com.averykarlin.averytask.data.local.entity.AssignmentEntity
import com.averykarlin.averytask.data.local.entity.CourseCompletionEntity
import com.averykarlin.averytask.data.local.entity.CourseEntity
import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolworkRepository @Inject constructor(
    private val dao: SchoolworkDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao
) {
    // --- Courses ---

    fun getActiveCourses(): Flow<List<CourseEntity>> = dao.getActiveCourses()
    fun getAllCourses(): Flow<List<CourseEntity>> = dao.getAllCourses()
    suspend fun getCourseById(id: Long): CourseEntity? = dao.getCourseById(id)
    suspend fun insertCourse(course: CourseEntity): Long = dao.insertCourse(course)
    suspend fun updateCourse(course: CourseEntity) = dao.updateCourse(course)
    suspend fun deleteCourse(id: Long) = dao.deleteCourse(id)

    // --- Assignments ---

    fun getAssignmentsForCourse(courseId: Long): Flow<List<AssignmentEntity>> =
        dao.getAssignmentsForCourse(courseId)

    fun getActiveAssignments(): Flow<List<AssignmentEntity>> = dao.getActiveAssignments()
    fun getAllAssignments(): Flow<List<AssignmentEntity>> = dao.getAllAssignments()

    suspend fun getAssignmentById(id: Long): AssignmentEntity? = dao.getAssignmentById(id)

    suspend fun insertAssignment(assignment: AssignmentEntity): Long =
        dao.insertAssignment(assignment)

    suspend fun updateAssignment(assignment: AssignmentEntity) = dao.updateAssignment(assignment)
    suspend fun deleteAssignment(id: Long) = dao.deleteAssignment(id)

    suspend fun toggleAssignmentComplete(id: Long) {
        val assignment = dao.getAssignmentById(id) ?: return
        val now = if (!assignment.completed) System.currentTimeMillis() else null
        dao.updateAssignment(assignment.copy(completed = !assignment.completed, completedAt = now))
    }

    fun getActiveAssignmentCount(courseId: Long): Flow<Int> = dao.getActiveAssignmentCount(courseId)

    // --- Course Completions (daily habit) ---

    fun getTodayCompletions(): Flow<List<CourseCompletionEntity>> =
        dao.getCompletionsForDate(todayMidnight())

    suspend fun toggleCourseCompletion(courseId: Long) {
        val today = todayMidnight()
        val existing = dao.getCompletionOnce(today, courseId)
        if (existing != null) {
            if (existing.completed) {
                dao.deleteCompletion(today, courseId)
            } else {
                dao.updateCompletion(existing.copy(
                    completed = true,
                    completedAt = System.currentTimeMillis()
                ))
            }
        } else {
            dao.insertCompletion(CourseCompletionEntity(
                date = today,
                courseId = courseId,
                completed = true,
                completedAt = System.currentTimeMillis()
            ))
        }
        syncHabitCompletion()
    }

    suspend fun resetToday() {
        dao.deleteCompletionsForDate(todayMidnight())
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
                frequencyPeriod = "daily"
            )
        )
        return habitDao.getHabitByIdOnce(id)!!
    }

    private suspend fun syncHabitCompletion() {
        val habit = getOrCreateSchoolHabit()
        val today = todayMidnight()
        val completions = dao.getCompletionsForDateOnce(today)
        // Need to know how many active courses there are
        // Use a one-shot query approach
        val courseCount = dao.getActiveCourseCount()
        val completedCount = completions.count { it.completed }
        val allDone = courseCount > 0 && completedCount >= courseCount
        val alreadyCompleted = habitCompletionDao.isCompletedOnDateOnce(habit.id, today)

        if (allDone && !alreadyCompleted) {
            habitCompletionDao.insert(
                HabitCompletionEntity(
                    habitId = habit.id,
                    completedDate = today,
                    completedAt = System.currentTimeMillis()
                )
            )
        } else if (!allDone && alreadyCompleted) {
            habitCompletionDao.deleteByHabitAndDate(habit.id, today)
        }
    }

    companion object {
        const val SCHOOL_HABIT_NAME = "School"

        fun todayMidnight(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
