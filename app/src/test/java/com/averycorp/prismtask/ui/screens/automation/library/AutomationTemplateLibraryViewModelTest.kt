package com.averycorp.prismtask.ui.screens.automation.library

import app.cash.turbine.test
import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.repository.AutomationRuleRepository
import com.averycorp.prismtask.data.repository.AutomationTemplateRepository
import com.averycorp.prismtask.data.seed.AutomationStarterLibrary
import com.averycorp.prismtask.data.seed.AutomationTemplateCategory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationTemplateLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleRepository: AutomationRuleRepository = mockk(relaxed = true)
    private lateinit var templateRepository: AutomationTemplateRepository
    private lateinit var viewModel: AutomationTemplateLibraryViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        templateRepository = AutomationTemplateRepository(ruleRepository)
        viewModel = AutomationTemplateLibraryViewModel(templateRepository)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun initialSections_coverAllCategories() = runTest {
        viewModel.sections.test {
            val initial = awaitItem()
            // Empty query → all 7 categories present
            assertEquals(
                AutomationTemplateCategory.values().size,
                initial.size
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun query_filtersToMatchingTemplates() = runTest {
        viewModel.setQuery("urgent")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.sections.test {
            val filtered = awaitItem()
            // At least one section appears with the urgent rules.
            assertTrue(
                filtered.any { section ->
                    section.templates.any { it.id == "builtin.notify_overdue_urgent" }
                }
            )
            // Empty categories drop out.
            assertTrue(filtered.all { it.templates.isNotEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun importTemplate_emitsImportedEvent() = runTest {
        coEvery { ruleRepository.getByTemplateKeyOnce(any()) } returns null
        coEvery {
            ruleRepository.create(
                name = any(),
                description = any(),
                trigger = any(),
                condition = any(),
                actions = any(),
                priority = any(),
                enabled = any(),
                isBuiltIn = any(),
                templateKey = any()
            )
        } returns 99L

        val tplId = AutomationStarterLibrary.ALL_TEMPLATES.first().id

        viewModel.events.test {
            viewModel.importTemplate(tplId)
            testDispatcher.scheduler.advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is AutomationTemplateLibraryViewModel.LibraryEvent.Imported)
        }
    }

    @Test fun importTemplate_existingTemplate_emitsAlreadyImportedEvent() = runTest {
        val tpl = AutomationStarterLibrary.ALL_TEMPLATES.first()
        coEvery { ruleRepository.getByTemplateKeyOnce(tpl.id) } returns
            AutomationRuleEntity(
                id = 5L,
                name = "stub",
                templateKey = tpl.id,
                triggerJson = "{}",
                actionJson = "[]",
                createdAt = 0L,
                updatedAt = 0L
            )

        viewModel.events.test {
            viewModel.importTemplate(tpl.id)
            testDispatcher.scheduler.advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is AutomationTemplateLibraryViewModel.LibraryEvent.AlreadyImported)
            assertEquals(
                tpl.name,
                (event as AutomationTemplateLibraryViewModel.LibraryEvent.AlreadyImported).templateName
            )
        }
    }

    @Test fun importTemplate_unknownId_emitsImportFailed() = runTest {
        viewModel.events.test {
            viewModel.importTemplate("starter.unknown.id")
            testDispatcher.scheduler.advanceUntilIdle()
            val event = awaitItem()
            assertEquals(
                AutomationTemplateLibraryViewModel.LibraryEvent.ImportFailed,
                event
            )
        }
    }
}
