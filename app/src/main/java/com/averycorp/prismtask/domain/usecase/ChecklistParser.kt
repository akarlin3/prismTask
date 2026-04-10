package com.averycorp.prismtask.domain.usecase

import android.util.Log
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.preferences.ApiPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// --- Public result models ---

data class ComprehensiveImportResult(
    val course: ParsedCourse,
    val project: ParsedProject,
    val tags: List<ParsedTag>,
    val tasks: List<ChecklistParsedTask>
)

data class ParsedCourse(
    val code: String,
    val name: String,
    val deadline: Long?,
    val assignments: List<ParsedAssignment>
)

data class ParsedAssignment(
    val title: String,
    val dueDate: Long?,
    val time: String?,
    val type: String?,
    val completed: Boolean
)

data class ParsedProject(
    val name: String,
    val color: String,
    val icon: String
)

data class ParsedTag(
    val name: String,
    val color: String
)

data class ChecklistParsedTask(
    val title: String,
    val description: String?,
    val dueDate: Long?,
    val priority: Int,
    val completed: Boolean,
    val tags: List<String>,
    val subtasks: List<ChecklistParsedTask>,
    val estimatedMinutes: Int?
)

@Singleton
class ChecklistParser @Inject constructor(
    private val apiPreferences: ApiPreferences,
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun parse(content: String): ComprehensiveImportResult? {
        val claudeResult = parseWithClaude(content)
        if (claudeResult != null) return claudeResult

        // Fall back to regex → wrap in comprehensive result
        val regexCourse = parseWithRegex(content) ?: return null
        return wrapRegexResult(regexCourse)
    }

    fun parseWithRegex(content: String): ParsedCourse? {
        return parseScheduleFormat(content) ?: parseTasksFormat(content)
    }

    private fun wrapRegexResult(course: ParsedCourse): ComprehensiveImportResult {
        val tags = course.assignments.mapNotNull { it.type }.distinct().map { type ->
            ParsedTag(name = type, color = tagColorForType(type))
        }
        val tasks = course.assignments.map { a ->
            ChecklistParsedTask(
                title = a.title,
                description = a.time?.let { "Duration: $it" },
                dueDate = a.dueDate,
                priority = if (a.type == "exam") 4 else 0,
                completed = a.completed,
                tags = listOfNotNull(a.type),
                subtasks = emptyList(),
                estimatedMinutes = null
            )
        }
        return ComprehensiveImportResult(
            course = course,
            project = ParsedProject(
                name = "${course.code} — ${course.name}",
                color = "#4A90D9",
                icon = "\uD83D\uDCDA"
            ),
            tags = tags,
            tasks = tasks
        )
    }

    private fun tagColorForType(type: String): String = when (type) {
        "exam" -> "#EF4444"
        "video" -> "#3B82F6"
        "assignment" -> "#F59E0B"
        "code" -> "#10B981"
        "reading" -> "#8B5CF6"
        else -> "#6B7280"
    }

    // --- Claude API path ---

    private suspend fun parseWithClaude(content: String): ComprehensiveImportResult? {
        val apiKey = apiPreferences.getClaudeApiKey().firstOrNull()
        if (apiKey.isNullOrBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                callClaude(apiKey, content)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("ChecklistParser", "Claude API call failed", e)
                null
            }
        }
    }

    private fun callClaude(apiKey: String, content: String): ComprehensiveImportResult? {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val systemPrompt = """
You are a structured data extractor for a task management app. The user will give you the contents of a JSX/TSX file, a text list, schedule, syllabus, or other content.

Your job is to extract EVERY actionable item, preserving ALL detail from the original. Do not summarize or skip anything. Replicate every aspect of the source material.

Return ONLY a JSON object with this exact schema (no other text):

{
  "course": {
    "code": "string — course code like CSCA 5454, or a short identifier if not a course",
    "name": "string — full name or title"
  },
  "project": {
    "name": "string — project display name (e.g. 'CSCA 5454 — Data Structures')",
    "color": "string — hex color like #4A90D9",
    "icon": "string — single emoji for the project"
  },
  "tags": [
    {
      "name": "string — tag name (e.g. 'exam', 'video', 'reading', 'code', 'assignment')",
      "color": "string — hex color"
    }
  ],
  "tasks": [
    {
      "title": "string — the task description, faithfully preserved from source",
      "description": "string or null — extra details, notes, context, URLs, instructions from the source",
      "dueDate": "string or null — YYYY-MM-DD, use year $year if not specified",
      "priority": 0,
      "completed": false,
      "tags": ["string — tag names that apply to this task"],
      "estimatedMinutes": null,
      "subtasks": [
        {
          "title": "string",
          "description": "string or null",
          "dueDate": "string or null",
          "priority": 0,
          "completed": false,
          "tags": [],
          "estimatedMinutes": null,
          "subtasks": []
        }
      ]
    }
  ]
}

Rules:
- Extract EVERY item from the source. Do not summarize, merge, or skip items.
- Preserve exact titles, descriptions, notes, and any metadata from the original.
- If the source has sections/phases/weeks, group items by due date rather than flattening structure — use subtasks if an item clearly has sub-items.
- Skip only true off-days, holidays, rest days, or explicit breaks. Keep buffer/review days as tasks.
- For exams/tests/quizzes: set priority to 4 (urgent) and prefix title with "EXAM: "
- For assignments/homework: set priority to 2 (medium)
- For videos/lectures: set priority to 0 (none)
- For readings: set priority to 1 (low)
- Extract all dates and convert to YYYY-MM-DD format
- If content has duration/time estimates (e.g. "30 min", "1.5 hrs"), set estimatedMinutes to the number of minutes
- The completed field should reflect any done/checked/completed state from the source
- Assign appropriate tags to each task based on its type (video, assignment, exam, reading, code, etc.)
- Create tag entries for all unique task types you encounter — pick semantically meaningful colors
- For the project, pick an appropriate emoji icon and color based on the subject matter
- Return ONLY valid JSON, no explanation or markdown
""".trimIndent()

        val requestJson = gson.toJson(
            ClaudeReq(
                model = "claude-opus-4-6-20250528",
                maxTokens = 8192,
                system = systemPrompt,
                messages = listOf(ClaudeMsg(role = "user", content = content))
            )
        )

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            if (BuildConfig.DEBUG) Log.e("ChecklistParser", "Claude API returned ${response.code}")
            return null
        }

        val body = response.body?.string() ?: return null
        return parseClaudeResponse(body)
    }

    private fun parseClaudeResponse(body: String): ComprehensiveImportResult? {
        val root = JsonParser.parseString(body).asJsonObject
        val contentArray = root.getAsJsonArray("content") ?: return null
        val textBlock = contentArray.firstOrNull {
            it.asJsonObject.get("type")?.asString == "text"
        }?.asJsonObject ?: return null

        val text = textBlock.get("text")?.asString ?: return null
        val jsonStr = extractJson(text)
        val parsed = gson.fromJson(jsonStr, ClaudeFullResult::class.java) ?: return null

        if (parsed.tasks.isNullOrEmpty()) return null

        val course = ParsedCourse(
            code = parsed.course?.code ?: "UNKNOWN",
            name = parsed.course?.name ?: parsed.course?.code ?: "Unknown",
            deadline = null,
            assignments = parsed.tasks.map { t ->
                ParsedAssignment(
                    title = t.title,
                    dueDate = t.dueDate?.let { parseDateStringIso(it) },
                    time = t.estimatedMinutes?.let { "${it}min" },
                    type = t.tags?.firstOrNull(),
                    completed = t.completed ?: false
                )
            }
        )

        val project = ParsedProject(
            name = parsed.project?.name ?: "${course.code} — ${course.name}",
            color = parsed.project?.color ?: "#4A90D9",
            icon = parsed.project?.icon ?: "\uD83D\uDCDA"
        )

        val tags = parsed.tags?.map { t ->
            ParsedTag(name = t.name, color = t.color ?: "#6B7280")
        } ?: emptyList()

        val tasks = parsed.tasks.map { it.toPublic() }

        return ComprehensiveImportResult(
            course = course,
            project = project,
            tags = tags,
            tasks = tasks
        )
    }

    private fun ClaudeTaskItem.toPublic(): ChecklistParsedTask = ChecklistParsedTask(
        title = title,
        description = description,
        dueDate = dueDate?.let { parseDateStringIso(it) },
        priority = (priority ?: 0).coerceIn(0, 4),
        completed = completed ?: false,
        tags = tags ?: emptyList(),
        subtasks = subtasks?.map { it.toPublic() } ?: emptyList(),
        estimatedMinutes = estimatedMinutes
    )

    private fun extractJson(text: String): String {
        val fencePattern = Regex("""```(?:json)?\s*\n?(.*?)\n?```""", RegexOption.DOT_MATCHES_ALL)
        val match = fencePattern.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text.trim()
    }

    private fun parseDateStringIso(dateStr: String): Long? {
        val isoPattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
        val match = isoPattern.find(dateStr) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = (match.groupValues[2].toIntOrNull() ?: return null) - 1
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 23, 59, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // --- Claude API models ---

    private data class ClaudeReq(
        val model: String,
        @SerializedName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<ClaudeMsg>
    )

    private data class ClaudeMsg(val role: String, val content: String)

    private data class ClaudeFullResult(
        val course: ClaudeCourseInfo?,
        val project: ClaudeProjectInfo?,
        val tags: List<ClaudeTagInfo>?,
        val tasks: List<ClaudeTaskItem>?
    )

    private data class ClaudeCourseInfo(val code: String?, val name: String?)

    private data class ClaudeProjectInfo(
        val name: String?,
        val color: String?,
        val icon: String?
    )

    private data class ClaudeTagInfo(val name: String, val color: String?)

    private data class ClaudeTaskItem(
        val title: String,
        val description: String?,
        val dueDate: String?,
        val priority: Int?,
        val completed: Boolean?,
        val tags: List<String>?,
        val estimatedMinutes: Int?,
        val subtasks: List<ClaudeTaskItem>?
    )

    // --- Regex fallback parsers ---

    private fun parseScheduleFormat(content: String): ParsedCourse? {
        if (!content.contains("SCHEDULE")) return null

        val code = extractCourseCode(content) ?: return null
        val name = extractCourseName(content) ?: code

        val assignments = mutableListOf<ParsedAssignment>()

        val dayPattern = Regex("""\{\s*date:\s*"([^"]+)"[^}]*?tasks:\s*\[(.*?)\]\s*\}""", RegexOption.DOT_MATCHES_ALL)
        for (dayMatch in dayPattern.findAll(content)) {
            val dateStr = dayMatch.groupValues[1]
            val tasksBlock = dayMatch.groupValues[2]

            val taskPattern = Regex("""\{\s*id:\s*"[^"]*"\s*,\s*text:\s*"([^"]+)"\s*,\s*time:\s*"([^"]+)"\s*,\s*type:\s*"([^"]+)"(?:\s*,\s*done:\s*(true))?\s*\}""")
            for (taskMatch in taskPattern.findAll(tasksBlock)) {
                val text = taskMatch.groupValues[1]
                val time = taskMatch.groupValues[2]
                val type = taskMatch.groupValues[3]
                val done = taskMatch.groupValues[4] == "true"

                val typePrefix = when (type) {
                    "video" -> "\u25B6 "
                    "assignment" -> "\u270E "
                    "code" -> "\u27E8/\u27E9 "
                    else -> ""
                }

                assignments.add(
                    ParsedAssignment(
                        title = "$typePrefix$text",
                        dueDate = parseDateString(dateStr),
                        time = time,
                        type = type,
                        completed = done
                    )
                )
            }
        }

        if (assignments.isEmpty()) return null
        return ParsedCourse(code = code, name = name, deadline = null, assignments = assignments)
    }

    private fun parseTasksFormat(content: String): ParsedCourse? {
        if (!content.contains("const TASKS")) return null

        val code = extractCourseCode(content) ?: return null
        val name = extractCourseName(content) ?: code

        val assignments = mutableListOf<ParsedAssignment>()

        val itemPattern = Regex(
            """\{\s*id:\s*"[^"]*"\s*,\s*date:\s*"([^"]+)"\s*,\s*label:\s*"([^"]+)"(?:\s*,\s*time:\s*"([^"]*)")?(?:\s*,\s*done:\s*(true))?(?:\s*,\s*off:\s*true)?(?:\s*,\s*buffer:\s*true)?\s*\}"""
        )

        for (match in itemPattern.findAll(content)) {
            val dateStr = match.groupValues[1]
            val label = match.groupValues[2]
            val time = match.groupValues[3].ifEmpty { null }
            val done = match.groupValues[4] == "true"

            val fullMatch = match.value
            if (fullMatch.contains("off: true") || label.endsWith("— OFF") || label == "OFF") continue

            val isBuffer = fullMatch.contains("buffer: true")
            if (isBuffer && (label.startsWith("BUFFER") || label == "Buffer")) continue

            assignments.add(
                ParsedAssignment(
                    title = label,
                    dueDate = parseDateString(dateStr),
                    time = time,
                    type = guessType(label),
                    completed = done
                )
            )
        }

        if (assignments.isEmpty()) return null
        return ParsedCourse(code = code, name = name, deadline = null, assignments = assignments)
    }

    private fun extractCourseCode(content: String): String? {
        val codePattern = Regex("""[A-Z]{2,5}\s*\d{4}""")
        return codePattern.find(content)?.value
    }

    private fun extractCourseName(content: String): String? {
        val h1Pattern = Regex(""">([^<]{10,80})</h1>""")
        val match = h1Pattern.find(content)
        if (match != null) return match.groupValues[1].trim()

        val titlePattern = Regex("""title:\s*"([^"]{10,80})"""")
        return titlePattern.find(content)?.groupValues?.get(1)
    }

    private fun parseDateString(dateStr: String): Long? {
        val months = mapOf(
            "Jan" to Calendar.JANUARY, "Feb" to Calendar.FEBRUARY, "Mar" to Calendar.MARCH,
            "Apr" to Calendar.APRIL, "May" to Calendar.MAY, "Jun" to Calendar.JUNE,
            "Jul" to Calendar.JULY, "Aug" to Calendar.AUGUST, "Sep" to Calendar.SEPTEMBER,
            "Oct" to Calendar.OCTOBER, "Nov" to Calendar.NOVEMBER, "Dec" to Calendar.DECEMBER
        )
        val parts = dateStr.trim().split(" ", limit = 2)
        if (parts.size != 2) return null
        val month = months[parts[0]] ?: return null
        val day = parts[1].toIntOrNull() ?: return null

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        cal.set(currentYear, month, day, 23, 59, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun guessType(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.startsWith("watch:") || lower.contains("video") -> "video"
            lower.contains("programming") || lower.contains("final exam") || lower.contains("coding") -> "code"
            lower.contains("assignment") || lower.contains("quiz") || lower.contains("graded") || lower.contains("honor code") -> "assignment"
            else -> "assignment"
        }
    }
}
