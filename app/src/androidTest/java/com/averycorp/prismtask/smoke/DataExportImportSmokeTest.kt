package com.averycorp.prismtask.smoke

import com.averycorp.prismtask.data.export.DataImporter
import com.averycorp.prismtask.data.export.ImportMode
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import javax.inject.Inject

/**
 * Smoke tests for data import/export. The real [DataImporter] is wired
 * through Hilt; we feed it JSON payloads and verify the DAOs see the
 * imported entities. Export coverage lives in the DataExporterTest
 * unit-test suite — this suite focuses on the import path where the full
 * DAO wiring matters.
 */
@HiltAndroidTest
class DataExportImportSmokeTest : SmokeTestBase() {
    @Inject
    lateinit var dataImporter: DataImporter

    @Test
    fun importEmptyJson_producesZeroedResult() = runBlocking {
        val result = dataImporter.importFromJson("{}", ImportMode.MERGE)
        assert(result.tasksImported == 0)
        assert(result.errors.isEmpty())
    }

    @Test
    fun importMalformedJson_surfacesError() = runBlocking {
        val result = dataImporter.importFromJson("not json at all", ImportMode.MERGE)
        assert(result.errors.isNotEmpty())
    }

    @Test
    fun importProjects_persistsRowsInRoom() = runBlocking {
        val json =
            """
            { "projects": [ { "name": "Imported Project", "color": "#112233" } ] }
            """.trimIndent()

        val result = dataImporter.importFromJson(json, ImportMode.MERGE)
        assert(result.projectsImported == 1)

        val allProjects = database.projectDao().getAllProjectsOnce()
        assert(allProjects.any { it.name == "Imported Project" })
    }

    @Test
    fun importTasks_persistsRowsInRoom() = runBlocking {
        val json =
            """
            { "tasks": [ { "title": "Imported Task", "priority": 2 } ] }
            """.trimIndent()

        val result = dataImporter.importFromJson(json, ImportMode.MERGE)
        assert(result.tasksImported == 1)

        val all = database.taskDao().getAllTasks().first()
        assert(all.any { it.title == "Imported Task" })
    }

    @Test
    fun importDuplicateProjectInMergeMode_isSkipped() = runBlocking {
        // "Work" is already seeded by SmokeTestBase / TestDataSeeder.
        val json =
            """
            { "projects": [ { "name": "Work" }, { "name": "Fresh" } ] }
            """.trimIndent()

        val result = dataImporter.importFromJson(json, ImportMode.MERGE)
        assert(result.projectsImported == 1)
        assert(result.duplicatesSkipped == 1)
    }

    @Test
    fun importHabits_persistsRowsInRoom() = runBlocking {
        val json =
            """
            { "habits": [ { "name": "Imported Habit", "icon": "\uD83D\uDE00" } ] }
            """.trimIndent()

        val result = dataImporter.importFromJson(json, ImportMode.MERGE)
        assert(result.habitsImported == 1)

        val all = database.habitDao().getAllHabitsOnce()
        assert(all.any { it.name == "Imported Habit" })
    }

    @Test
    fun importTaskWithUnknownProjectName_dropsFk() = runBlocking {
        val json =
            """
            { "tasks": [ { "title": "Orphan task", "_projectName": "Nonexistent" } ] }
            """.trimIndent()

        dataImporter.importFromJson(json, ImportMode.MERGE)

        val task = database
            .taskDao()
            .getAllTasks()
            .first()
            .firstOrNull { it.title == "Orphan task" }
        assert(task != null)
        assert(task?.projectId == null) {
            "An unknown _projectName should result in projectId being null"
        }
    }
}
