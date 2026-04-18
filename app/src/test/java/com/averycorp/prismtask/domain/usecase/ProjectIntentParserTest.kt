package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIntentParserTest {
    private val parser = ProjectIntentParser()

    // ---------------------------------------------------------------------
    // create_project
    // ---------------------------------------------------------------------

    @Test
    fun `start a project called X — happy path`() {
        val intent = parser.parse("start a project called AAPM abstract")
        assertEquals(ProjectIntent.CreateProject("AAPM abstract"), intent)
    }

    @Test
    fun `create project X — with colon`() {
        assertEquals(
            ProjectIntent.CreateProject("Garage cleanup"),
            parser.parse("create project: Garage cleanup")
        )
    }

    @Test
    fun `new project X — no preposition`() {
        assertEquals(
            ProjectIntent.CreateProject("Onboarding"),
            parser.parse("new project Onboarding")
        )
    }

    @Test
    fun `make a new project named X`() {
        assertEquals(
            ProjectIntent.CreateProject("Q2 Roadmap"),
            parser.parse("make a new project named Q2 Roadmap")
        )
    }

    @Test
    fun `quoted project name is preserved without quotes`() {
        assertEquals(
            ProjectIntent.CreateProject("Finish the thing"),
            parser.parse("start a project called \"Finish the thing\"")
        )
    }

    @Test
    fun `case insensitive trigger`() {
        assertEquals(
            ProjectIntent.CreateProject("Reno"),
            parser.parse("START A PROJECT CALLED Reno")
        )
    }

    // ---------------------------------------------------------------------
    // complete_project
    // ---------------------------------------------------------------------

    @Test
    fun `mark the X paper done — fires complete_project`() {
        val intent = parser.parse("mark the pancData paper done")
        assertEquals(ProjectIntent.CompleteProject("pancData paper"), intent)
    }

    @Test
    fun `finish the X project — fires complete_project`() {
        assertEquals(
            ProjectIntent.CompleteProject("pancData"),
            parser.parse("finish the pancData project")
        )
    }

    @Test
    fun `X project is done`() {
        assertEquals(
            ProjectIntent.CompleteProject("pancData"),
            parser.parse("pancData project is done")
        )
    }

    @Test
    fun `ambiguous finish — ordinary task with no project-ish words falls through`() {
        // "finish the laundry" should NOT fire complete_project — laundry isn't
        // a project-ish word and the input doesn't mention "project".
        val intent = parser.parse("finish the laundry")
        assertTrue(
            "expected CreateTask, got $intent",
            intent is ProjectIntent.CreateTask
        )
    }

    // ---------------------------------------------------------------------
    // add_milestone
    // ---------------------------------------------------------------------

    @Test
    fun `add milestone X to Y project`() {
        assertEquals(
            ProjectIntent.AddMilestone(
                milestoneTitle = "finish draft",
                projectName = "AAPM"
            ),
            parser.parse("add milestone 'finish draft' to AAPM project")
        )
    }

    @Test
    fun `add milestone unquoted`() {
        assertEquals(
            ProjectIntent.AddMilestone(
                milestoneTitle = "finish draft",
                projectName = "AAPM"
            ),
            parser.parse("add milestone finish draft to AAPM project")
        )
    }

    @Test
    fun `add milestone to the X project`() {
        assertEquals(
            ProjectIntent.AddMilestone(
                milestoneTitle = "ship",
                projectName = "Onboarding"
            ),
            parser.parse("add milestone ship to the Onboarding project")
        )
    }

    @Test
    fun `add milestone — missing 'to' falls through`() {
        // Without "to X project" we can't infer the parent — fall through.
        val intent = parser.parse("add milestone finish draft")
        assertTrue(intent is ProjectIntent.CreateTask)
    }

    // ---------------------------------------------------------------------
    // create_task with projectId hint
    // ---------------------------------------------------------------------

    @Test
    fun `for the X project — extracts hint`() {
        val intent = parser.parse("draft intro for the AAPM project")
        assertEquals(ProjectIntent.CreateTask(projectHint = "AAPM"), intent)
    }

    @Test
    fun `on my X project — extracts hint`() {
        val intent = parser.parse("update references on my thesis project")
        assertEquals(ProjectIntent.CreateTask(projectHint = "thesis"), intent)
    }

    @Test
    fun `plain task — no hint, no intent`() {
        val intent = parser.parse("email Dr. Aliotta about the slides")
        assertEquals(ProjectIntent.CreateTask(projectHint = null), intent)
    }

    // ---------------------------------------------------------------------
    // Misc edge cases
    // ---------------------------------------------------------------------

    @Test
    fun `empty input yields CreateTask with null hint`() {
        assertEquals(ProjectIntent.CreateTask(projectHint = null), parser.parse(""))
    }

    @Test
    fun `whitespace-only input yields CreateTask`() {
        assertEquals(ProjectIntent.CreateTask(projectHint = null), parser.parse("   \n\t  "))
    }

    @Test
    fun `trailing punctuation is stripped before matching`() {
        assertEquals(
            ProjectIntent.CreateProject("Reno"),
            parser.parse("start a project called Reno.")
        )
    }
}
