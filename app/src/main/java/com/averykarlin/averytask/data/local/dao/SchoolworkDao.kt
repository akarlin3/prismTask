package com.averykarlin.averytask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.averykarlin.averytask.data.local.entity.AssignmentEntity
import com.averykarlin.averytask.data.local.entity.CourseCompletionEntity
import com.averykarlin.averytask.data.local.entity.CourseEntity
import com.averykarlin.averytask.data.local.entity.StudyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolworkDao {

    // --- Courses ---

    @Query("SELECT * FROM courses WHERE active = 1 ORDER BY sort_order ASC, name ASC")
    fun getActiveCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY active DESC, sort_order ASC, name ASC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun getCourseCount(): Int

    @Query("SELECT COUNT(*) FROM courses WHERE active = 1")
    suspend fun getActiveCourseCount(): Int

    // --- Assignments ---

    @Query("SELECT * FROM assignments WHERE course_id = :courseId ORDER BY completed ASC, due_date ASC, created_at DESC")
    fun getAssignmentsForCourse(courseId: Long): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE completed = 0 ORDER BY due_date ASC, created_at DESC")
    fun getActiveAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments ORDER BY completed ASC, due_date ASC, created_at DESC")
    fun getAllAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE id = :id LIMIT 1")
    suspend fun getAssignmentById(id: Long): AssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: AssignmentEntity): Long

    @Update
    suspend fun updateAssignment(assignment: AssignmentEntity)

    @Query("DELETE FROM assignments WHERE id = :id")
    suspend fun deleteAssignment(id: Long)

    @Query("SELECT COUNT(*) FROM assignments WHERE course_id = :courseId AND completed = 0")
    fun getActiveAssignmentCount(courseId: Long): Flow<Int>

    // --- Course Completions ---

    @Query("SELECT * FROM course_completions WHERE date = :date")
    fun getCompletionsForDate(date: Long): Flow<List<CourseCompletionEntity>>

    @Query("SELECT * FROM course_completions WHERE date = :date")
    suspend fun getCompletionsForDateOnce(date: Long): List<CourseCompletionEntity>

    @Query("SELECT * FROM course_completions WHERE date = :date AND course_id = :courseId LIMIT 1")
    suspend fun getCompletionOnce(date: Long, courseId: Long): CourseCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: CourseCompletionEntity): Long

    @Update
    suspend fun updateCompletion(completion: CourseCompletionEntity)

    @Query("DELETE FROM course_completions WHERE date = :date AND course_id = :courseId")
    suspend fun deleteCompletion(date: Long, courseId: Long)

    @Query("DELETE FROM course_completions WHERE date = :date")
    suspend fun deleteCompletionsForDate(date: Long)

    // --- Study Logs (legacy, kept for migration compatibility) ---

    @Query("SELECT * FROM study_logs WHERE date = :date LIMIT 1")
    fun getLogForDate(date: Long): Flow<StudyLogEntity?>

    @Query("SELECT * FROM study_logs WHERE date = :date LIMIT 1")
    suspend fun getLogForDateOnce(date: Long): StudyLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StudyLogEntity): Long

    @Update
    suspend fun updateLog(log: StudyLogEntity)
}
