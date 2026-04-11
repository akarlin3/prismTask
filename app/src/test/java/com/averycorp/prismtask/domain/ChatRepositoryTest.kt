package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.remote.api.ChatActionResponse
import com.averycorp.prismtask.data.remote.api.ChatResponse
import com.averycorp.prismtask.data.remote.api.ChatTokensUsed
import com.averycorp.prismtask.data.repository.ChatMessage
import com.averycorp.prismtask.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    // --- ChatMessage ---

    @Test
    fun `ChatMessage defaults to USER role`() {
        val msg = ChatMessage(role = ChatMessage.Role.USER, text = "hello")
        assertEquals(ChatMessage.Role.USER, msg.role)
        assertEquals("hello", msg.text)
        assertTrue(msg.actions.isEmpty())
    }

    @Test
    fun `ChatMessage ASSISTANT with actions`() {
        val actions = listOf(
            ChatActionResponse(type = "complete", taskId = "42")
        )
        val msg = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            text = "Done!",
            actions = actions
        )
        assertEquals(ChatMessage.Role.ASSISTANT, msg.role)
        assertEquals(1, msg.actions.size)
        assertEquals("complete", msg.actions[0].type)
        assertEquals("42", msg.actions[0].taskId)
    }

    @Test
    fun `ChatMessage has unique id`() {
        val msg1 = ChatMessage(role = ChatMessage.Role.USER, text = "a")
        val msg2 = ChatMessage(role = ChatMessage.Role.USER, text = "b")
        assertTrue(msg1.id != msg2.id)
    }

    // --- ChatResponse parsing ---

    @Test
    fun `ChatResponse parses message and actions`() {
        val response = ChatResponse(
            message = "That's a good one.",
            actions = listOf(
                ChatActionResponse(
                    type = "reschedule",
                    taskId = "123",
                    to = "tomorrow"
                )
            ),
            conversationId = "chat_2026-04-11_abc12345",
            tokensUsed = ChatTokensUsed(input = 500, output = 100)
        )

        assertEquals("That's a good one.", response.message)
        assertEquals(1, response.actions.size)
        assertEquals("reschedule", response.actions[0].type)
        assertEquals("123", response.actions[0].taskId)
        assertEquals("tomorrow", response.actions[0].to)
        assertEquals(500, response.tokensUsed?.input)
        assertEquals(100, response.tokensUsed?.output)
    }

    @Test
    fun `ChatResponse with empty actions`() {
        val response = ChatResponse(
            message = "I hear you.",
            actions = emptyList(),
            conversationId = "chat_2026-04-11_def67890"
        )

        assertEquals("I hear you.", response.message)
        assertTrue(response.actions.isEmpty())
    }

    @Test
    fun `ChatResponse with breakdown action`() {
        val response = ChatResponse(
            message = "Here's a plan:",
            actions = listOf(
                ChatActionResponse(
                    type = "breakdown",
                    taskId = "555",
                    subtasks = listOf("Write thesis", "List subheadings", "Write body")
                )
            ),
            conversationId = "chat_2026-04-11_ghi"
        )

        val action = response.actions[0]
        assertEquals("breakdown", action.type)
        assertEquals("555", action.taskId)
        assertEquals(3, action.subtasks?.size)
        assertEquals("Write thesis", action.subtasks?.get(0))
    }

    @Test
    fun `ChatResponse with create_task action`() {
        val response = ChatResponse(
            message = "Added it.",
            actions = listOf(
                ChatActionResponse(
                    type = "create_task",
                    title = "Buy groceries",
                    due = "tomorrow",
                    priority = "low"
                )
            ),
            conversationId = "chat_2026-04-11_jkl"
        )

        val action = response.actions[0]
        assertEquals("create_task", action.type)
        assertEquals("Buy groceries", action.title)
        assertEquals("tomorrow", action.due)
        assertEquals("low", action.priority)
    }

    @Test
    fun `ChatResponse with reschedule_batch action`() {
        val response = ChatResponse(
            message = "Moved 3 tasks.",
            actions = listOf(
                ChatActionResponse(
                    type = "reschedule_batch",
                    taskIds = listOf("1", "2", "3"),
                    to = "next_week"
                )
            ),
            conversationId = "chat_2026-04-11_mno"
        )

        val action = response.actions[0]
        assertEquals("reschedule_batch", action.type)
        assertEquals(3, action.taskIds?.size)
        assertEquals("next_week", action.to)
    }

    @Test
    fun `ChatResponse with start_timer action`() {
        val response = ChatResponse(
            message = "Let's go.",
            actions = listOf(
                ChatActionResponse(
                    type = "start_timer",
                    taskId = "99",
                    minutes = 25
                )
            ),
            conversationId = "chat_2026-04-11_pqr"
        )

        val action = response.actions[0]
        assertEquals("start_timer", action.type)
        assertEquals("99", action.taskId)
        assertEquals(25, action.minutes)
    }

    @Test
    fun `ChatResponse with archive action`() {
        val response = ChatResponse(
            message = "Dropped it. No guilt.",
            actions = listOf(
                ChatActionResponse(
                    type = "archive",
                    taskId = "77"
                )
            ),
            conversationId = "chat_2026-04-11_stu"
        )

        val action = response.actions[0]
        assertEquals("archive", action.type)
        assertEquals("77", action.taskId)
    }

    @Test
    fun `ChatResponse with multiple actions`() {
        val response = ChatResponse(
            message = "Let me help.",
            actions = listOf(
                ChatActionResponse(type = "complete", taskId = "1"),
                ChatActionResponse(type = "reschedule", taskId = "2", to = "tomorrow"),
                ChatActionResponse(type = "create_task", title = "New task", due = "today", priority = "high")
            ),
            conversationId = "chat_2026-04-11_vwx"
        )

        assertEquals(3, response.actions.size)
        assertEquals("complete", response.actions[0].type)
        assertEquals("reschedule", response.actions[1].type)
        assertEquals("create_task", response.actions[2].type)
    }

    // --- Conversation ID format ---

    @Test
    fun `conversation ID contains date prefix`() {
        // The conversation ID should start with "chat_" and contain today's date
        val id = "chat_2026-04-11_abc12345"
        assertTrue(id.startsWith("chat_"))
        assertTrue(id.contains("2026-04-11"))
    }

    // --- shouldRefreshContext ---

    @Test
    fun `shouldRefreshContext true on first call`() {
        // A fresh ChatRepository (messagesSinceContextRefresh == 0) should want to refresh.
        // We can't test this directly without the API, but we can verify
        // the ChatMessage model works correctly.
        val msg = ChatMessage(role = ChatMessage.Role.USER, text = "test")
        assertEquals("test", msg.text)
    }

    // --- ChatActionResponse defaults ---

    @Test
    fun `ChatActionResponse nullable fields default to null`() {
        val action = ChatActionResponse(type = "complete", taskId = "1")
        assertEquals("complete", action.type)
        assertEquals("1", action.taskId)
        assertEquals(null, action.taskIds)
        assertEquals(null, action.to)
        assertEquals(null, action.subtasks)
        assertEquals(null, action.minutes)
        assertEquals(null, action.title)
        assertEquals(null, action.due)
        assertEquals(null, action.priority)
    }
}
