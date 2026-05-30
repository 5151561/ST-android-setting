package io.github.sanitised.st.chat

import io.github.sanitised.st.ui.webview.WebViewTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun visibleMessagesIncludeSystemMessages() {
        // System messages (including user-hidden messages) are shown in the
        // native UI with a visual indicator — they should NOT be filtered out.
        val visible = visibleChatMessages(
            listOf(
                chatMessage(id = 0, isSystem = true, text = "system"),
                chatMessage(id = 1, text = "hello")
            )
        )

        assertEquals(listOf(0, 1), visible.map { it.id })
    }

    @Test
    fun dateLabelUsesFirstMessageSendDate() {
        val label = conversationDateLabel(
            listOf(
                chatMessage(id = 0, isSystem = true, sendDate = "May 25, 2026 10:00am"),
                chatMessage(id = 1, sendDate = "May 26, 2026 12:00pm")
            )
        )

        assertEquals("May 25, 2026 10:00am", label)
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

    @Test
    fun chatStoreTracksPendingAttachmentsUntilCleared() {
        val store = ChatStore()
        val attachment = PendingAttachment(
            url = "/user/files/notes.pdf",
            name = "notes.pdf",
            size = 2048L,
            isMedia = false
        )

        store.addPendingAttachment(attachment)

        assertEquals(listOf(attachment), store.pendingAttachments.toList())

        store.clearPendingAttachments()

        assertEquals(emptyList<PendingAttachment>(), store.pendingAttachments.toList())
    }

    @Test
    fun chatStoreAppliesCfgAndWorldInfoMetadataFromSnapshot() {
        val store = ChatStore()
        val snapshot = ChatSnapshot(
            mode = "character",
            avatarUrl = "Alice.png",
            characterName = "Alice",
            chatFile = "chat.jsonl",
            isGenerating = false,
            messages = emptyList(),
            metadata = JSONObject()
                .put("cfgScale", 1.7)
                .put("cfgNegativePrompt", "avoid purple prose")
                .put("cfgPositivePrompt", "stay grounded")
                .put("worldInfo", "Archive World")
        )

        store.applySnapshot(snapshot)

        assertEquals(1.7f, store.cfgScale)
        assertEquals("avoid purple prose", store.cfgNegativePrompt)
        assertEquals("stay grounded", store.cfgPositivePrompt)
        assertEquals("Archive World", store.worldInfoName)
    }

    @Test
    fun attachmentDisplayUrlResolvesLocalSillyTavernPaths() {
        assertEquals(
            "http://127.0.0.1:8020/user/files/notes.pdf",
            attachmentDisplayUrl(8020, "/user/files/notes.pdf")
        )
        assertEquals(
            "http://127.0.0.1:8020/user/images/cat.png",
            attachmentDisplayUrl(8020, "user/images/cat.png")
        )
        assertEquals(
            "https://example.com/file.png",
            attachmentDisplayUrl(8020, "https://example.com/file.png")
        )
    }

    @Test
    fun attachmentSizeLabelUsesReadableUnits() {
        assertEquals("0 B", attachmentSizeLabel(0L))
        assertEquals("512 B", attachmentSizeLabel(512L))
        assertEquals("2.0 KB", attachmentSizeLabel(2048L))
        assertEquals("1.5 MB", attachmentSizeLabel(1_572_864L))
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
