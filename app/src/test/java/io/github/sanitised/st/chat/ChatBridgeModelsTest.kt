package io.github.sanitised.st.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBridgeModelsTest {
    @Test
    fun mediaAttachmentsParseFromMessageExtraMediaArray() {
        val message = chatMessage(
            extra = JSONObject()
                .put(
                    "media",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("url", "/user/images/cat.png")
                                .put("type", "image/png")
                                .put("title", "cat.png")
                        )
                )
        )

        assertEquals(
            listOf(MediaAttachment(url = "/user/images/cat.png", type = "image/png", title = "cat.png")),
            message.mediaAttachments
        )
    }

    @Test
    fun fileAttachmentsParseFromMessageExtraFilesArray() {
        val message = chatMessage(
            extra = JSONObject()
                .put(
                    "files",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("url", "/user/files/notes.pdf")
                                .put("name", "notes.pdf")
                                .put("size", 2048L)
                        )
                )
        )

        assertEquals(
            listOf(FileAttachment(url = "/user/files/notes.pdf", name = "notes.pdf", size = 2048L)),
            message.fileAttachments
        )
    }

    @Test
    fun attachmentParsingUsesPathFallbacksAndSkipsBlankUrls() {
        val message = chatMessage(
            extra = JSONObject()
                .put(
                    "media",
                    JSONArray()
                        .put(JSONObject().put("path", "/user/images/scene.webp").put("name", "scene"))
                        .put(JSONObject().put("url", ""))
                )
                .put(
                    "files",
                    JSONArray()
                        .put(JSONObject().put("path", "/user/files/lore.txt").put("name", "lore.txt"))
                        .put(JSONObject().put("path", ""))
                )
        )

        assertEquals(
            listOf(MediaAttachment(url = "/user/images/scene.webp", type = "", title = "scene")),
            message.mediaAttachments
        )
        assertEquals(
            listOf(FileAttachment(url = "/user/files/lore.txt", name = "lore.txt", size = 0L)),
            message.fileAttachments
        )
    }

    private fun chatMessage(extra: JSONObject): ChatMessage = ChatMessage(
        id = 1,
        name = "Alice",
        mes = "hello",
        isUser = true,
        isSystem = false,
        sendDate = "May 30, 2026 12:00pm",
        swipeId = 0,
        swipes = emptyList(),
        extra = extra
    )
}
