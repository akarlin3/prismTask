package com.todounified.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ── API response models ──

data class AiParsedResult(
    val name: String = "",
    val emoji: String = "📦",
    val description: String = "",
    val tasks: List<AiParsedTask> = emptyList(),
    val originalStructure: String = "",
    val renderHtml: String = ""
)

data class AiParsedTask(
    val title: String = "",
    val done: Boolean = false,
    val priority: String = "medium",
    val dueDate: String = "",
    val tags: List<String> = emptyList()
)

// ── Anthropic API models ──

private data class ApiRequest(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<ApiMessage>
)

private data class ApiMessage(val role: String, val content: String)

private data class ApiResponse(val content: List<ApiContentBlock>?)
private data class ApiContentBlock(val type: String, val text: String?)

// ── Service ──

class JsxParserService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val systemPrompt = """
You are a JSX component analyzer. The user will give you the source code of a React to-do list component. Your job is to:
1. Understand what the component renders (layout, task items, structure).
2. Return ONLY a JSON object (no markdown, no backticks, no preamble) with this schema:
{
  "name": "string - the inferred list name from the component, or the filename",
  "emoji": "string - a single emoji that fits the list theme",
  "description": "string - one-sentence summary of what this component does",
  "tasks": [
    {
      "title": "string - task text",
      "done": false,
      "priority": "medium",
      "dueDate": "",
      "tags": []
    }
  ],
  "originalStructure": "string - brief description of the original UI structure",
  "renderHtml": "string - a self-contained HTML string that recreates the visual appearance of the original component. Use inline styles. Dark theme (#0d0d12 background, #e2e2e8 text). Do NOT include script tags."
}

Map priorities to: urgent, high, medium, low. Extract all task data you can find in the JSX.
    """.trimIndent()

    suspend fun parseJsx(jsxCode: String, fileName: String): AiParsedResult = withContext(Dispatchers.IO) {
        val request = ApiRequest(
            model = "claude-sonnet-4-20250514",
            max_tokens = 4000,
            system = systemPrompt,
            messages = listOf(
                ApiMessage("user", "Filename: $fileName\n\nJSX Source:\n```jsx\n$jsxCode\n```")
            )
        )

        val jsonBody = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from API")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $body")
        }

        val apiResponse = gson.fromJson(body, ApiResponse::class.java)
        val text = apiResponse.content
            ?.filter { it.type == "text" }
            ?.mapNotNull { it.text }
            ?.joinToString("") ?: throw Exception("No text in API response")

        val cleanJson = text
            .replace(Regex("^```json\\s*"), "")
            .replace(Regex("```\\s*$"), "")
            .trim()

        gson.fromJson(cleanJson, AiParsedResult::class.java)
    }
}
