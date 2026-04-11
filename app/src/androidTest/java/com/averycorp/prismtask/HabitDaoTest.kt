package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var completionDao: HabitCompletionDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        habitDao = database.habitDao()
        completionDao = database.habitCompletionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryHabitById() = runTest {
        val id = habitDao.insert(
            HabitEntity(
                name = "Drink water",
                color = "#2563EB",
                icon = "\uD83D\uDCA7"
            )
        )
        val fetched = habitDao.getHabitByIdOnce(id)
        assertNotNull(fetched)
        assertEquals("Drink water", fetched?.name)
    }

    @Test
    fun getActiveHabitsExcludesArchived() = runTest {
        habitDao.insert(HabitEntity(name = "active", isArchived = false))
        habitDao.insert(HabitEntity(name = "archived", isArchived = true))

        val active = habitDao.getActiveHabits().first()
        assertEquals(1, active.size)
        assertEquals("active", active[0].name)
    }

    @Test
    fun deleteHabitRemovesIt() = runTest {
        val id = habitDao.insert(HabitEntity(name = "temp"))
        habitDao.deleteById(id)

        val fetched = habitDao.getHabitByIdOnce(id)
        assertEquals(null, fetched)
    }

    @Test
    fun insertCompletionAndQueryByHabit() = runTest {
        val habitId = habitDao.insert(HabitEntity(name = "Meditate"))
        val today = 0L // midnight epoch for the test
        completionDao.insert(
            HabitCompletionEntity(
                habitId = habitId,
                completedDate = today,
                completedAt = System.currentTimeMillis()
            )
        )
        val completions = completionDao.getCompletionsForHabit(habitId).first()
        assertEquals(1, completions.size)
        assertEquals(habitId, completions[0].habitId)
    }

    @Test
    fun deleteHabitCascadesToCompletions() = runTest {
        val habitId = habitDao.insert(HabitEntity(name = "Run"))
        completionDao.insert(
            HabitCompletionEntity(
                habitId = habitId,
                completedDate = 0L,
                completedAt = System.currentTimeMillis()
            )
        )
        habitDao.deleteById(habitId)

        val completions = completionDao.getCompletionsForHabit(habitId).first()
        assertTrue(completions.isEmpty())
    }

    @Test
    fun updateHabitPersistsChanges() = runTest {
        val id = habitDao.insert(
            HabitEntity(name = "Old", color = "#000000", icon = "X")
        )
        val original = habitDao.getHabitByIdOnce(id) ?: error("not found")
        habitDao.update(original.copy(name = "New", color = "#FFFFFF"))

        val updated = habitDao.getHabitByIdOnce(id)
        assertEquals("New", updated?.name)
        assertEquals("#FFFFFF", updated?.color)
    }

    @Test
    fun getAllHabitsReturnsAllInserted() = runTest {
        habitDao.insert(HabitEntity(name = "a"))
        habitDao.insert(HabitEntity(name = "b"))
        habitDao.insert(HabitEntity(name = "c"))

        val all = habitDao.getAllHabits().first()
        assertEquals(3, all.size)
    }
}
