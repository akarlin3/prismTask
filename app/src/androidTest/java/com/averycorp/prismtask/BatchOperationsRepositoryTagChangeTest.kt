package com.averycorp.prismtask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.ProposedMutationResponse
import com.averycorp.prismtask.data.repository.BatchOperationsRepository
import com.averycorp.prismtask.domain.usecase.BatchUserContextProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers TAG_CHANGE apply + undo for [BatchOperationsRepository]. Web
 * slice #728 ported to Android in PR #697 — these tests are the missing
 * regression net flagged during the audit. The repository's `applyTagDelta`
 * helper is exercised end-to-end through `applyBatch` so the snapshot,
 * cross-ref writes, and case-insensitive auto-create are all in scope.
 */
@RunWith(AndroidJUnit4::class)
class BatchOperationsRepositoryTagChangeTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: PrismTaskDatabase
    private lateinit var repository: BatchOperationsRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // The TAG_CHANGE paths only exercise DAOs + the database, never the
        // backend client or context provider. Both deps are wired through
        // `parseCommand` only, so relaxed mocks are safe here.
        val api = mockk<PrismTaskApi>(relaxed = true)
        val contextProvider = mockk<BatchUserContextProvider>(relaxed = true)

        repository = BatchOperationsRepository(
            database = database,
            api = api,
            taskDao = database.taskDao(),
            habitDao = database.habitDao(),
            projectDao = database.projectDao(),
            tagDao = database.tagDao(),
            habitCompletionDao = database.habitCompletionDao(),
            batchUndoLogDao = database.batchUndoLogDao(),
            medicationDao = database.medicationDao(),
            medicationDoseDao = database.medicationDoseDao(),
            medicationSlotDao = database.medicationSlotDao(),
            medicationTierStateDao = database.medicationTierStateDao(),
            contextProvider = contextProvider
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun applyTagChange_addsNewTagAndCreatesCrossRef() = runTest {
        val taskId = database.taskDao().insert(TaskEntity(title = "Plan launch"))

        val result = repository.applyBatch(
            commandText = "tag launch as #personal",
            mutations = listOf(tagChangeMutation(taskId, added = listOf("personal")))
        )

        assertEquals(1, result.appliedCount)
        val resolvedTags = repository.getTagNamesForTasks(listOf(taskId))[taskId].orEmpty()
        assertEquals(listOf("personal"), resolvedTags)

        // Tag was auto-created with the entity's default color (#6B7280) — no
        // explicit color from the AI plan should land here.
        val tag = database.tagDao().getAllTagsOnce().single()
        assertEquals("personal", tag.name)
        assertEquals("#6B7280", tag.color)
    }

    @Test
    fun applyTagChange_caseInsensitiveMatch_reusesExistingTag() = runTest {
        val existingId = database.tagDao().insert(TagEntity(name = "Personal"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Buy groceries"))

        repository.applyBatch(
            commandText = "tag groceries as #personal",
            mutations = listOf(tagChangeMutation(taskId, added = listOf("personal")))
        )

        val allTags = database.tagDao().getAllTagsOnce()
        assertEquals("Existing 'Personal' tag should be reused, not duplicated", 1, allTags.size)
        assertEquals(existingId, allTags.single().id)
        assertEquals(listOf(existingId), database.tagDao().getTagIdsForTaskOnce(taskId))
    }

    @Test
    fun applyTagChange_removeOfMissingTagName_isNoop() = runTest {
        val workId = database.tagDao().insert(TagEntity(name = "work"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Status report"))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = workId))

        val result = repository.applyBatch(
            commandText = "untag #unknown from status report",
            mutations = listOf(tagChangeMutation(taskId, removed = listOf("unknown")))
        )

        assertEquals(1, result.appliedCount)
        assertEquals(listOf(workId), database.tagDao().getTagIdsForTaskOnce(taskId))
        assertTrue(
            "Auto-create on remove path is not allowed",
            database.tagDao().getAllTagsOnce().none { it.name.equals("unknown", ignoreCase = true) }
        )
    }

    @Test
    fun applyTagChange_preservesTagsNotMentioned() = runTest {
        val workId = database.tagDao().insert(TagEntity(name = "work"))
        val urgentId = database.tagDao().insert(TagEntity(name = "urgent"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Sprint review"))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = workId))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = urgentId))

        repository.applyBatch(
            commandText = "tag sprint review as #personal",
            mutations = listOf(tagChangeMutation(taskId, added = listOf("personal")))
        )

        val resulting = repository.getTagNamesForTasks(listOf(taskId))[taskId].orEmpty().toSet()
        assertEquals(setOf("personal", "urgent", "work"), resulting)
    }

    @Test
    fun applyTagChange_addAndRemoveInOneMutation_landsBothDeltas() = runTest {
        val urgentId = database.tagDao().insert(TagEntity(name = "urgent"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Refactor billing"))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = urgentId))

        repository.applyBatch(
            commandText = "replace #urgent with #later on refactor billing",
            mutations = listOf(
                tagChangeMutation(
                    taskId,
                    added = listOf("later"),
                    removed = listOf("urgent")
                )
            )
        )

        val resulting = repository.getTagNamesForTasks(listOf(taskId))[taskId].orEmpty()
        assertEquals(listOf("later"), resulting)
    }

    @Test
    fun undoBatch_restoresExactPriorTagList() = runTest {
        val workId = database.tagDao().insert(TagEntity(name = "work"))
        val urgentId = database.tagDao().insert(TagEntity(name = "urgent"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Ship release"))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = workId))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = urgentId))

        val applyResult = repository.applyBatch(
            commandText = "swap urgent for personal on ship release",
            mutations = listOf(
                tagChangeMutation(
                    taskId,
                    added = listOf("personal"),
                    removed = listOf("urgent")
                )
            )
        )
        // Sanity: post-apply state matches the delta.
        assertEquals(
            setOf("personal", "work"),
            repository.getTagNamesForTasks(listOf(taskId))[taskId].orEmpty().toSet()
        )

        val undo = repository.undoBatch(applyResult.batchId)
        assertEquals(1, undo.restored)
        assertTrue(undo.failed.isEmpty())

        val restored = repository.getTagNamesForTasks(listOf(taskId))[taskId].orEmpty().toSet()
        assertEquals(setOf("urgent", "work"), restored)
    }

    @Test
    fun undoBatch_doesNotDeleteAutoCreatedTags() = runTest {
        val taskId = database.taskDao().insert(TaskEntity(title = "Outline post"))

        val applyResult = repository.applyBatch(
            commandText = "tag outline post as #blog",
            mutations = listOf(tagChangeMutation(taskId, added = listOf("blog")))
        )
        val createdBlogTag = database.tagDao().getAllTagsOnce()
            .firstOrNull { it.name.equals("blog", ignoreCase = true) }
        assertNotNull("Auto-create should have inserted the 'blog' tag", createdBlogTag)

        repository.undoBatch(applyResult.batchId)

        // Cross-ref reverted, but the auto-created tag survives undo —
        // the user may have applied #blog to other tasks in the meantime.
        assertTrue(
            "Auto-created tag must persist across undo",
            database.tagDao().getAllTagsOnce()
                .any { it.name.equals("blog", ignoreCase = true) }
        )
        val taggedAfterUndo = database.tagDao().getTagIdsForTaskOnce(taskId)
        assertTrue("Cross-ref must be removed by undo", taggedAfterUndo.isEmpty())
    }

    @Test
    fun getTagNamesForTasks_returnsEmptyListForTaskWithNoTags() = runTest {
        val taskId = database.taskDao().insert(TaskEntity(title = "Untagged task"))
        val map = repository.getTagNamesForTasks(listOf(taskId))
        assertEquals(emptyList<String>(), map[taskId])
    }

    @Test
    fun getTagNamesForTasks_returnsSortedNamesPerTask() = runTest {
        val taskId = database.taskDao().insert(TaskEntity(title = "Multi-tag"))
        val zebraId = database.tagDao().insert(TagEntity(name = "zebra"))
        val alphaId = database.tagDao().insert(TagEntity(name = "alpha"))
        val midId = database.tagDao().insert(TagEntity(name = "middle"))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = zebraId))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = alphaId))
        database.tagDao().addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = midId))

        val map = repository.getTagNamesForTasks(listOf(taskId))
        assertEquals(listOf("alpha", "middle", "zebra"), map[taskId])
    }

    private fun tagChangeMutation(
        taskId: Long,
        added: List<String> = emptyList(),
        removed: List<String> = emptyList()
    ) = ProposedMutationResponse(
        entityType = "TASK",
        entityId = taskId.toString(),
        mutationType = "TAG_CHANGE",
        proposedNewValues = mapOf(
            "tags_added" to added,
            "tags_removed" to removed
        ),
        humanReadableDescription = "TAG_CHANGE on task $taskId"
    )
}
