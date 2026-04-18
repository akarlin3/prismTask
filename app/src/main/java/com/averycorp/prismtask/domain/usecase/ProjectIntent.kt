package com.averycorp.prismtask.domain.usecase

/**
 * Natural-language intent recognized by [ProjectIntentParser].
 *
 * The parser's job is to decide whether a quick-add input is a
 * project-management action — creating a project, marking one complete,
 * or adding a milestone — or the default case (a task creation). When
 * nothing matches, callers fall through to [NaturalLanguageParser] to get
 * a [ParsedTask] and route it into the existing task-creation pipeline.
 *
 * Introduced in v1.4.0 as part of the Projects feature.
 */
sealed class ProjectIntent {
    /**
     * "start a project called X", "create project X", "new project: X",
     * "make a new project X". Regex fallback for this intent is mandatory
     * per the Phase 4 spec.
     */
    data class CreateProject(val name: String) : ProjectIntent()

    /**
     * "mark the AAPM paper done", "finish project X", "complete the X
     * project". Resolution to a concrete project ID happens downstream
     * against the user's live project list — this intent only carries the
     * name the user said.
     */
    data class CompleteProject(val projectName: String) : ProjectIntent()

    /**
     * "add milestone 'finish draft' to AAPM project". Both fields are
     * populated from the input — no follow-up picker required.
     */
    data class AddMilestone(val milestoneTitle: String, val projectName: String) : ProjectIntent()

    /**
     * Default case — the input is a normal task creation. Callers should
     * fall through to the existing [NaturalLanguageParser] pipeline.
     * `projectHint` is non-null when the input trailed with "for the X
     * project" — used by QuickAdd to pre-bind `projectId` on the new task.
     */
    data class CreateTask(val projectHint: String? = null) : ProjectIntent()
}
