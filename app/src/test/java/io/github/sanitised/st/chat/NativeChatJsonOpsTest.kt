package io.github.sanitised.st.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatJsonOpsTest {

    @Test
    fun editsMessageTextAndActiveSwipeWithoutDroppingUnknownFields() {
        val chat = mutableListOf<Any?>(
            header(),
            message("Alex", "hello", isUser = true, unknown = "keep"),
            message("Alice", "old", swipes = listOf("first", "old"), swipeId = 1, extra = mapOf("reasoning" to "think"))
        )

        NativeChatJsonOps.editMessage(chat, messageId = 1, text = "new")

        val row = chat.message(1)
        assertEquals("new", row["mes"])
        assertEquals(listOf("first", "new"), row["swipes"])
        assertEquals("think", row.extra()["reasoning"])
        assertEquals("keep", chat.message(0)["unknown"])
    }

    @Test
    fun deletesHidesMovesReasoningAndAttachmentsInCharacterMessageIndexSpace() {
        val chat = mutableListOf<Any?>(
            header(),
            message("Alex", "one", isUser = true),
            message(
                "Alice",
                "two",
                extra = mapOf(
                    "reasoning" to "old thought",
                    "files" to listOf(mapOf("name" to "a.txt"), mapOf("name" to "b.txt")),
                    "media" to listOf(mapOf("url" to "1.png"), mapOf("url" to "2.png")),
                )
            ),
            message("Alex", "three", isUser = true)
        )

        NativeChatJsonOps.setHidden(chat, messageId = 1, hidden = true)
        NativeChatJsonOps.setReasoning(chat, messageId = 1, reasoning = "new thought")
        NativeChatJsonOps.deleteAttachment(chat, messageId = 1, kind = NativeAttachmentKind.FILE, index = 0)
        NativeChatJsonOps.deleteAttachment(chat, messageId = 1, kind = NativeAttachmentKind.MEDIA, index = 1)
        NativeChatJsonOps.setMediaDisplay(chat, messageId = 1, display = NativeMediaDisplay.GALLERY)
        NativeChatJsonOps.moveMessage(chat, messageId = 2, delta = -1)
        NativeChatJsonOps.deleteMessage(chat, messageId = 0)

        assertEquals(listOf("three", "two"), chat.messages().map { it["mes"] })
        val moved = chat.message(1)
        assertTrue(moved["is_system"] as Boolean)
        assertEquals("new thought", moved.extra()["reasoning"])
        assertEquals(listOf(mapOf("name" to "b.txt")), moved.extra()["files"])
        assertEquals(listOf(mapOf("url" to "1.png")), moved.extra()["media"])
        assertEquals("gallery", moved.extra()["media_display"])
    }

    @Test
    fun switchesCreatesAndDeletesSwipesKeepingMesAndSwipeInfoInSync() {
        val chat = mutableListOf<Any?>(
            header(),
            message("Alice", "first", swipes = listOf("first", "second"), swipeId = 0, swipeInfo = listOf(mapOf("send_date" to "a"), mapOf("send_date" to "b")))
        )

        NativeChatJsonOps.switchSwipe(chat, messageId = 0, delta = 1)
        assertEquals(1, chat.message(0)["swipe_id"])
        assertEquals("second", chat.message(0)["mes"])

        NativeChatJsonOps.createSwipe(chat, messageId = 0, text = "third")
        assertEquals(2, chat.message(0)["swipe_id"])
        assertEquals("third", chat.message(0)["mes"])
        assertEquals(listOf("first", "second", "third"), chat.message(0)["swipes"])
        assertEquals(3, (chat.message(0)["swipe_info"] as List<*>).size)

        NativeChatJsonOps.deleteSwipe(chat, messageId = 0, swipeId = 1)
        assertEquals(1, chat.message(0)["swipe_id"])
        assertEquals("third", chat.message(0)["mes"])
        assertEquals(listOf("first", "third"), chat.message(0)["swipes"])
        assertEquals(2, (chat.message(0)["swipe_info"] as List<*>).size)
    }

    @Test
    fun createsCheckpointAndBranchCopiesPrefixAndLinksCurrentMessage() {
        val chat = mutableListOf<Any?>(
            header(metadata = mapOf("integrity" to "abc", "world_info" to "Lore")),
            message("Alex", "one", isUser = true),
            message("Alice", "two"),
            message("Alex", "three", isUser = true)
        )

        val checkpoint = NativeChatJsonOps.createCheckpoint(
            chat = chat,
            currentChatName = "main",
            messageId = 1,
            name = "main - Checkpoint #1"
        )
        assertEquals("main - Checkpoint #1", checkpoint.linkedName)
        assertEquals("main - Checkpoint #1", chat.message(1).extra()["bookmark_link"])
        assertEquals(listOf("one", "two"), checkpoint.chatCopy.messages().map { it["mes"] })
        assertEquals("main", checkpoint.chatCopy.headerMetadata()["main_chat"])
        assertEquals("Lore", checkpoint.chatCopy.headerMetadata()["world_info"])

        val branch = NativeChatJsonOps.createBranch(
            chat = chat,
            currentChatName = "main",
            messageId = 0,
            name = "main - Branch #1"
        )
        assertEquals("main - Branch #1", branch.linkedName)
        assertEquals(listOf("main - Branch #1"), chat.message(0).extra()["branches"])
        assertEquals(listOf("one"), branch.chatCopy.messages().map { it["mes"] })
        assertEquals("main", branch.chatCopy.headerMetadata()["main_chat"])
    }

    private fun header(metadata: Map<String, Any?> = mapOf("integrity" to "abc")): MutableMap<String, Any?> =
        linkedMapOf(
            "user_name" to "Alex",
            "character_name" to "Alice",
            "chat_metadata" to LinkedHashMap(metadata)
        )

    private fun message(
        name: String,
        text: String,
        isUser: Boolean = false,
        swipes: List<String> = emptyList(),
        swipeId: Int = 0,
        swipeInfo: List<Map<String, Any?>> = emptyList(),
        extra: Map<String, Any?> = emptyMap(),
        unknown: String? = null,
    ): MutableMap<String, Any?> = linkedMapOf<String, Any?>(
        "name" to name,
        "is_user" to isUser,
        "is_system" to false,
        "send_date" to "June 3",
        "mes" to text,
        "swipe_id" to swipeId,
        "swipes" to swipes,
        "swipe_info" to swipeInfo,
        "extra" to LinkedHashMap(extra)
    ).also { row ->
        if (unknown != null) row["unknown"] = unknown
    }

    private fun MutableList<Any?>.message(id: Int): MutableMap<String, Any?> =
        messages()[id]

    private fun List<Any?>.messages(): List<MutableMap<String, Any?>> =
        drop(1).map { @Suppress("UNCHECKED_CAST") (it as MutableMap<String, Any?>) }

    private fun MutableMap<String, Any?>.extra(): MutableMap<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this["extra"] as MutableMap<String, Any?>
    }

    private fun List<Any?>.headerMetadata(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val header = first() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        return header["chat_metadata"] as Map<String, Any?>
    }
}
