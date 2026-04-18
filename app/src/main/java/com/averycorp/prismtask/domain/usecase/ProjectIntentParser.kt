package com.averycorp.prismtask.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Regex-first intent parser for the v1.4.0 Projects feature.
 *
 * Runs as a pre-pass before [NaturalLanguageParser]: if the input matches
 * a project-management intent (create/complete a project, add a milestone)
 * the caller short-circuits and dispatches to [com.averycorp.prismtask.data.repository.ProjectRepository].
 * Otherwise the caller treats the input as a normal task — with an optional
 * `projectHint` extracted when the input trailed with "for the X project".
 *
 * Keeping this regex-only for v1:
 *  - `create_project` — has a full regex fallback per the Phase 4 spec so
 *    offline quick-adds still work.
 *  - `complete_project` — regex catches common phrasings; ambiguous inputs
 *    (a project title that happens to mention "done") fall through to
 *    task creation, which is the safe default.
 *  - `add_milestone` — regex requires the literal word "milestone" to
 *    avoid hijacking ordinary task phrases. Unrecognized phrasings fall
 *    through.
 *
 * Backend LLM routing for these intents (schema-versioned response) is
 * deferred to a Phase 4 follow-up. Regex-only already covers the
 * happy-path quick-adds the spec calls out.
 */
@Singleton
class ProjectIntentParser @Inject constructor() {

    private val createProjectPatterns = listOf(
        // "start a project called X" / "start a new project called X"
        Regex("""^(?:please\s+)?start\s+(?:a\s+)?(?:new\s+)?project\s+(?:called\s+|named\s+|titled\s+)?["']?(.+?)["']?\s*$""", RegexOption.IGNORE_CASE),
        // "create (a/new) project X" / "create project: X"
        Regex("""^(?:please\s+)?create\s+(?:a\s+)?(?:new\s+)?project\s*[:\-]?\s*["']?(.+?)["']?\s*$""", RegexOption.IGNORE_CASE),
        // "new project: X" / "new project X"
        Regex("""^new\s+project\s*[:\-]?\s*["']?(.+?)["']?\s*$""", RegexOption.IGNORE_CASE),
        // "make a (new) project X"
        Regex("""^make\s+(?:a\s+)?(?:new\s+)?project\s+(?:called\s+|named\s+|titled\s+)?["']?(.+?)["']?\s*$""", RegexOption.IGNORE_CASE)
    )

    private val completeProjectPatterns = listOf(
        // "mark (the) X (project) done" / "mark (the) X paper done"
        Regex("""^mark\s+(?:the\s+)?(.+?)(?:\s+project)?\s+(?:done|complete|completed|finished)\s*$""", RegexOption.IGNORE_CASE),
        // "finish (the) X project" / "complete (the) X project"
        Regex("""^(?:finish|complete)\s+(?:the\s+)?(.+?)(?:\s+project)?\s*$""", RegexOption.IGNORE_CASE),
        // "X project is done"
        Regex("""^(.+?)\s+project\s+is\s+(?:done|complete|completed|finished)\s*$""", RegexOption.IGNORE_CASE)
    )

    private val addMilestonePattern =
        Regex(
            """^add\s+(?:a\s+)?milestone\s+["']?(.+?)["']?\s+to\s+(?:the\s+)?(.+?)(?:\s+project)?\s*$""",
            RegexOption.IGNORE_CASE
        )

    private val projectHintPattern =
        Regex(
            """^(.+?)\s+(?:for|on)\s+(?:the\s+|my\s+)?(.+?)\s+project\s*$""",
            RegexOption.IGNORE_CASE
        )

    /**
     * Classify [input] as a [ProjectIntent]. Falls back to [ProjectIntent.CreateTask]
     * when no project intent matches — callers then run the normal task
     * pipeline. The optional `projectHint` on `CreateTask` exposes a
     * detected "for the X project" trailer so the caller can pre-bind
     * `projectId` on the new task.
     */
    fun parse(input: String): ProjectIntent {
        val trimmed = input.trim().trimEnd('.', '!', '?')
        if (trimmed.isEmpty()) return ProjectIntent.CreateTask()

        // create_project — mandatory regex fallback per spec.
        for (pattern in createProjectPatterns) {
            val match = pattern.find(trimmed)
            val raw = match?.groups?.get(1)?.value?.trim()?.trimEnd('.')
            if (!raw.isNullOrBlank()) {
                // Guard against over-greedy matches like "start a project called"
                // with nothing after — shouldn't happen because the regex requires
                // at least one non-whitespace char, but defensive coding.
                return ProjectIntent.CreateProject(name = raw)
            }
        }

        // add_milestone — single-pattern match.
        val milestoneMatch = addMilestonePattern.find(trimmed)
        if (milestoneMatch != null) {
            val title = milestoneMatch.groupValues[1].trim()
            val project = milestoneMatch.groupValues[2].trim()
            if (title.isNotBlank() && project.isNotBlank()) {
                return ProjectIntent.AddMilestone(milestoneTitle = title, projectName = project)
            }
        }

        // complete_project — tried after create/milestone so longer phrasings win.
        for (pattern in completeProjectPatterns) {
            val match = pattern.find(trimmed)
            val name = match?.groupValues?.getOrNull(1)?.trim()
            if (!name.isNullOrBlank() && looksLikeProjectReference(name, trimmed)) {
                return ProjectIntent.CompleteProject(projectName = name)
            }
        }

        // Default: task creation. Try to pluck a trailing "for the X project"
        // so QuickAdd can pre-bind projectId.
        val hintMatch = projectHintPattern.find(trimmed)
        if (hintMatch != null) {
            val hint = hintMatch.groupValues[2].trim()
            if (hint.isNotBlank()) return ProjectIntent.CreateTask(projectHint = hint)
        }

        return ProjectIntent.CreateTask()
    }

    /**
     * Heuristic: a `complete_project` match is only plausible when the
     * matched name actually looks project-shaped — either the user said
     * "project" explicitly, or the matched name is short enough to plausibly
     * be a project title. Prevents "finish the laundry" from firing a
     * project-completion intent.
     */
    private fun looksLikeProjectReference(name: String, fullInput: String): Boolean {
        val lower = fullInput.lowercase()
        val mentionsProjectWord = lower.contains(" project") || lower.contains("project ")
        if (mentionsProjectWord) return true
        // Fallback: only fire on phrasing that includes "paper", "draft",
        // "abstract" — project-ish words. Otherwise it's probably a task.
        val projectyWords = listOf("paper", "draft", "abstract", "report", "thesis", "proposal")
        return projectyWords.any { it in lower }
    }
}
