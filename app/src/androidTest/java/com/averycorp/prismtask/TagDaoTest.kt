package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagDaoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var tagDao: TagDao
    private lateinit var taskDao: TaskDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tagDao = database.tagDao()
        taskDao = database.taskDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveTag() = runTest {
        val id = tagDao.insert(TagEntity(name = "work", color = "#2563EB"))
        val tag = tagDao.getTagByIdOnce(id)
        assertEquals("work", tag?.name)
        assertEquals("#2563EB", tag?.color)
    }

    @Test
    fun assignTagToTaskViaCrossRef() = runTest {
        val tagId = tagDao.insert(TagEntity(name = "urgent", color = "#DC2626"))
        val taskId = taskDao.insert(TaskEntity(title = "Fix bug"))
        tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))

        val tagsForTask = tagDao.getTagsForTask(taskId).first()
        assertEquals(1, tagsForTask.size)
        assertEquals("urgent", tagsForTask[0].name)
    }

    @Test
    fun removeTagFromTaskClearsRelation() = runTest {
        val tagId = tagDao.insert(TagEntity(name = "home", color = "#059669"))
        val taskId = taskDao.insert(TaskEntity(title = "Laundry"))
        tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))

        tagDao.removeTagFromTask(taskId, tagId)
        val tagsForTask = tagDao.getTagsForTask(taskId).first()
        assertTrue(tagsForTask.isEmpty())
    }

    @Test
    fun deleteTagCascadesToCrossRefs() = runTest {
        val tag = TagEntity(name = "temp", color = "#EA580C")
        val tagId = tagDao.insert(tag)
        val taskId = taskDao.insert(TaskEntity(title = "With temp tag"))
        tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))

        tagDao.delete(tag.copy(id = tagId))

        val tagsForTask = tagDao.getTagsForTask(taskId).first()
        assertTrue(tagsForTask.isEmpty())
    }

    @Test
    fun getAllTagsReturnsInsertedTags() = runTest {
        tagDao.insert(TagEntity(name = "a", color = "#000000"))
        tagDao.insert(TagEntity(name = "b", color = "#111111"))
        tagDao.insert(TagEntity(name = "c", color = "#222222"))

        val all = tagDao.getAllTags().first()
        assertEquals(3, all.size)
        assertEquals(setOf("a", "b", "c"), all.map { it.name }.toSet())
    }

    @Test
    fun searchTagsFiltersByPrefix() = runTest {
        tagDao.insert(TagEntity(name = "work", color = "#000"))
        tagDao.insert(TagEntity(name = "worship", color = "#000"))
        tagDao.insert(TagEntity(name = "fun", color = "#000"))

        val results = tagDao.searchTags("wor").first()
        assertEquals(2, results.size)
        assertTrue(results.all { it.name.startsWith("wor") })
    }
}
