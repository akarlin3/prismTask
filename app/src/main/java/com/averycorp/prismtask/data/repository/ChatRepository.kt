package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.remote.api.ChatActionResponse
import com.averycorp.prismtask.data.remote.api.ChatRequest
import com.averycorp.prismtask.data.remote.api.ChatResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val actions: List<ChatActionResponse> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}

@Singleton
class ChatRepository
@Inject
constructor(
    private val api: PrismTaskApi
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var conversationId: String = generateConversationId()
    private var conversationDate: LocalDate = LocalDate.now()
    private var messagesSinceContextRefresh = 0

    /** Maximum conversation pairs kept in history (spec: 10). */
    private val maxHistoryPairs = 10

    /**
     * Returns the current conversation ID, resetting if the day has changed.
     */
    fun getConversationId(): String {
        resetIfNewDay()
        return conversationId
    }

    /**
     * Returns whether the user context block should be refreshed
     * (every 5 messages per spec).
     */
    fun shouldRefreshContext(): Boolean = messagesSinceContextRefresh >= 5 || messagesSinceContextRefresh == 0

    fun markContextRefreshed() {
        messagesSinceContextRefresh = 0
    }

    /**
     * Sends a message to the AI chat backend and returns the response.
     * Manages conversation history, trimming to max pairs.
     */
    suspend fun sendMessage(
        userMessage: String,
        taskContextId: Long? = null
    ): ChatResponse {
        resetIfNewDay()

        val userMsg = ChatMessage(
            role = ChatMessage.Role.USER,
            text = userMessage
        )
        _messages.value = _messages.value + userMsg
        messagesSinceContextRefresh++

        val response = api.aiChat(
            ChatRequest(
                message = userMessage,
                conversationId = conversationId,
                taskContextId = taskContextId
            )
        )

        val assistantMsg = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            text = response.message,
            actions = response.actions
        )
        _messages.value = _messages.value + assistantMsg

        trimHistory()

        return response
    }

    fun clearConversation() {
        _messages.value = emptyList()
        conversationId = generateConversationId()
        conversationDate = LocalDate.now()
        messagesSinceContextRefresh = 0
    }

    private fun resetIfNewDay() {
        val today = LocalDate.now()
        if (today != conversationDate) {
            clearConversation()
        }
    }

    /**
     * Trims conversation history to keep at most [maxHistoryPairs] user+assistant pairs.
     * Oldest pairs are dropped silently per spec.
     */
    private fun trimHistory() {
        val current = _messages.value
        if (current.size > maxHistoryPairs * 2) {
            _messages.value = current.takeLast(maxHistoryPairs * 2)
        }
    }

    private fun generateConversationId(): String {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "chat_${date}_${UUID.randomUUID().toString().take(8)}"
    }
}
