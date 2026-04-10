package com.averycorp.prismtask.data.remote

import android.util.Log
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.preferences.ApiPreferences
import com.averycorp.prismtask.domain.usecase.ParsedTodoItem
import com.averycorp.prismtask.domain.usecase.ParsedTodoList
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
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

@Singleton
class ClaudeParserService @Inject constructor(
    private val apiPreferences: ApiPreferences,
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun parse(content: String): ParsedTodoList? {
        val apiKey = apiPreferences.getClaudeApiKey().firstOrNull()
        if (apiKey.isNullOrBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                callClaude(apiKey, content)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("ClaudeParser", "API call failed", e)
                null
            }
        }
    }

    private fun callClaude(apiKey: String, content: String): ParsedTodoList? {
        val systemPrompt = buildSystemPrompt()

        val requestJson = gson.toJson(
            ClaudeRequest(
                model = "claude-opus-4-6-20250528",
                maxTokens = 4096,
                system = systemPrompt,
                messages = listOf(
                    ClaudeMessage(role = "user", content = content)
                )
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
            if (BuildConfig.DEBUG) Log.e("ClaudeParser", "API returned ${response.code}")
            return null
        }

        val body = response.body?.string() ?: return null
        return parseResponse(body)
    }

    private fun parseResponse(body: String): ParsedTodoList? {
        val root = JsonParser.parseString(body).asJsonObject
        val contentArray = root.getAsJsonArray("content") ?: return null
        val textBlock = contentArray.firstOrNull {
            it.asJsonObject.get("type")?.asString == "text"
        }?.asJsonObject ?: return null

        val text = textBlock.get("text")?.asString ?: return null

        // Extract JSON from the response (might be wrapped in markdown code block)
        val jsonStr = extractJson(text)
        val parsed = gson.fromJson(jsonStr, ClaudeParsedResult::class.java) ?: return null

        if (parsed.items.isNullOrEmpty()) return null

        return ParsedTodoList(
            name = parsed.name,
            items = parsed.items.map { it.toParsedTodoItem() }
        )
    }

    private fun extractJson(text: String): String {
        // Strip markdown code fences if present
        val fencePattern = Regex("""```(?:json)?\s*\n?(.*?)\n?```""", RegexOption.DOT_MATCHES_ALL)
        val match = fencePattern.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text.trim()
    }

    private fun buildSystemPrompt(): String = """
You are a structured data extractor. The user will give you the contents of a JSX/TSX file or a text list that contains a to-do list, schedule, or checklist.

Extract all actionable items and return ONLY a JSON object with this exact schema (no other text):

{
  "name": "string or null — the list/schedule title if apparent",
  "items": [
    {
      "title": "string — the task/item description",
      "description": "string or null — extra details, duration, notes",
      "dueDate": "string or null — date in YYYY-MM-DD format, use year ${Calendar.getInstance().get(Calendar.YEAR)} if not specified",
      "priority": 0,
      "completed": false,
      "subtasks": []
    }
  ]
}

Rules:
- Skip items that are days off, holidays, rest days, or breaks
- For exam/test items, set priority to 4 (urgent) and prefix title with "EXAM: "
- For other items, keep priority at 0
- Extract dates and convert to YYYY-MM-DD
- Include duration/time info in the description field
- The completed field should reflect the done/checked state from the source
- Subtasks should use the same object schema
- Return ONLY valid JSON, no explanation
""".trimIndent()

    // --- Request/response models ---

    private data class ClaudeRequest(
        val model: String,
        @SerializedName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<ClaudeMessage>
    )

    private data class ClaudeMessage(
        val role: String,
        val content: String
    )

    private data class ClaudeParsedResult(
        val name: String?,
        val items: List<ClaudeParsedItem>?
    )

    private data class ClaudeParsedItem(
        val title: String,
        val description: String?,
        val dueDate: String?,
        val priority: Int?,
        val completed: Boolean?,
        val subtasks: List<ClaudeParsedItem>?
    ) {
        fun toParsedTodoItem(): ParsedTodoItem = ParsedTodoItem(
            title = title,
            description = description,
            dueDate = dueDate?.let { parseDateString(it) },
            priority = (priority ?: 0).coerceIn(0, 4),
            completed = completed ?: false,
            subtasks = subtasks?.map { it.toParsedTodoItem() } ?: emptyList()
        )
    }
}

private fun parseDateString(dateStr: String): Long? {
    // "2026-04-15" ISO format
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
