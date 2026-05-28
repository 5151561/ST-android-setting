package io.github.sanitised.st.chat

import io.github.sanitised.st.ui.webview.WebViewTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun visibleMessagesExcludeSystemMessages() {
        val visible = visibleChatMessages(
            listOf(
                chatMessage(id = 0, isSystem = true, text = "system"),
                chatMessage(id = 1, text = "hello")
            )
        )

        assertEquals(listOf(1), visible.map { it.id })
    }

    @Test
    fun dateLabelUsesActualFirstVisibleSendDateOnly() {
        val label = conversationDateLabel(
            listOf(
                chatMessage(id = 0, isSystem = true, sendDate = "fake system date"),
                chatMessage(id = 1, sendDate = "May 26, 2026 12:00pm")
            )
        )

        assertEquals("May 26, 2026 12:00pm", label)
        assertNull(conversationDateLabel(listOf(chatMessage(id = 2, sendDate = ""))))
    }

    @Test
    fun scrollTargetAccountsForOptionalDateChipAndVisibleMessages() {
        assertNull(chatListScrollTargetIndex(emptyList(), null))
        assertEquals(0, chatListScrollTargetIndex(emptyList(), "May 26, 2026 12:00pm"))
        assertEquals(
            2,
            chatListScrollTargetIndex(
                visibleMessages = listOf(chatMessage(id = 1), chatMessage(id = 2)),
                dateLabel = "May 26, 2026 12:00pm"
            )
        )
    }

    @Test
    fun messageItemKeyDoesNotChangeWhenSwipeChanges() {
        assertEquals("message-7", chatMessageItemKey(chatMessage(id = 7, swipeId = 0)))
        assertEquals("message-7", chatMessageItemKey(chatMessage(id = 7, swipeId = 1)))
    }

    @Test
    fun readyTargetCommandKeyIsStableForSameTarget() {
        assertEquals("snapshot", readyTargetCommandKey(WebViewTarget.CHAT))
        assertEquals(
            "character:Aria.png:chat-a",
            readyTargetCommandKey(WebViewTarget.CharacterChat("Aria.png", "chat-a"))
        )
        assertEquals(
            "group:group-1:chat-a",
            readyTargetCommandKey(WebViewTarget.GroupChat("group-1", "chat-a"))
        )
    }

    private fun chatMessage(
        id: Int,
        text: String = "message",
        isSystem: Boolean = false,
        sendDate: String = "May 26, 2026 12:00pm",
        swipeId: Int = 0
    ): ChatMessage = ChatMessage(
        id = id,
        name = "Alice",
        mes = text,
        isUser = false,
        isSystem = isSystem,
        sendDate = sendDate,
        swipeId = swipeId,
        swipes = emptyList(),
        extra = JSONObject()
    )
}
