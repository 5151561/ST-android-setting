package io.github.sanitised.st.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun reasoningParsesFromExtra() {
        val message = chatMessage(extra = JSONObject().put("reasoning", "let me think"))
        assertEquals("let me think", message.reasoning)
    }

    @Test
    fun reasoningBlankOrMissingIsNull() {
        assertNull(chatMessage(extra = JSONObject().put("reasoning", "")).reasoning)
        assertNull(chatMessage(extra = JSONObject()).reasoning)
    }

    @Test
    fun toolInvocationsParseWithObjectParameters() {
        val message = chatMessage(
            extra = JSONObject().put(
                "tool_invocations",
                JSONArray().put(
                    JSONObject()
                        .put("id", "call_1")
                        .put("displayName", "搜索网页")
                        .put("name", "web_search")
                        .put("parameters", JSONObject().put("query", "weather"))
                        .put("result", "sunny 25C")
                )
            )
        )
        val tools = message.toolInvocations
        assertEquals(1, tools.size)
        assertEquals("call_1", tools[0].id)
        assertEquals("搜索网页", tools[0].displayName)
        assertEquals("web_search", tools[0].name)
        assertTrue(tools[0].parameters.contains("query"))
        assertEquals("sunny 25C", tools[0].result)
    }

    @Test
    fun toolInvocationDisplayNameFallsBackToName() {
        val message = chatMessage(
            extra = JSONObject().put(
                "tool_invocations",
                JSONArray().put(
                    JSONObject()
                        .put("name", "calculator")
                        .put("parameters", "{}")
                        .put("result", "42")
                )
            )
        )
        val tools = message.toolInvocations
        assertEquals(1, tools.size)
        assertEquals("calculator", tools[0].displayName)
        assertEquals("42", tools[0].result)
    }

    @Test
    fun toolInvocationsEmptyWhenAbsent() {
        assertTrue(chatMessage(extra = JSONObject()).toolInvocations.isEmpty())
    }

    @Test
    fun bookmarkLinkParsesFromExtra() {
        val message = chatMessage(extra = JSONObject().put("bookmark_link", "剧情转折点 - Checkpoint #1"))
        assertEquals("剧情转折点 - Checkpoint #1", message.bookmarkLink)
    }

    @Test
    fun bookmarkLinkBlankOrMissingIsNull() {
        assertNull(chatMessage(extra = JSONObject().put("bookmark_link", "")).bookmarkLink)
        assertNull(chatMessage(extra = JSONObject()).bookmarkLink)
    }

    @Test
    fun branchesParseFromExtra() {
        val message = chatMessage(
            extra = JSONObject().put(
                "branches",
                JSONArray().put("Branch #1").put("Branch #2").put("")
            )
        )
        assertEquals(listOf("Branch #1", "Branch #2"), message.branches)
    }

    @Test
    fun branchesEmptyWhenAbsent() {
        assertTrue(chatMessage(extra = JSONObject()).branches.isEmpty())
    }

    @Test
    fun parsesRuntimeToastEvent() {
        val json = """{"name":"runtime.toast","payload":{"type":"error","title":"失败","message":"未知命令"}}"""
        val event = BridgeEvent.parse(json)
        assertTrue(event is BridgeEvent.Toast)
        val toast = event as BridgeEvent.Toast
        assertEquals("error", toast.type)
        assertEquals("失败", toast.title)
        assertEquals("未知命令", toast.message)
    }

    @Test
    fun itemizedPromptParsesComponentsAndMeta() {
        val json = JSONObject()
            .put("available", true)
            .put("mesId", 12)
            .put("total", 4096)
            .put(
                "components",
                JSONArray()
                    .put(JSONObject().put("name", "角色描述").put("tokens", 800))
                    .put(JSONObject().put("name", "聊天历史").put("tokens", 1800))
                    .put(JSONObject().put("name", "").put("tokens", 50))
            )
            .put("presetName", "Default")
            .put("modelUsed", "claude-4")
            .put("apiUsed", "openai")
            .put("tokenizer", "cl100k")
        val prompt = ItemizedPrompt.fromJson(json)
        assertEquals(12, prompt.mesId)
        assertEquals(4096, prompt.total)
        assertEquals(2, prompt.components.size)
        assertEquals("角色描述", prompt.components[0].name)
        assertEquals(800, prompt.components[0].tokens)
        assertEquals("Default", prompt.presetName)
        assertEquals("cl100k", prompt.tokenizer)
    }

    @Test
    fun dataBankParsesThreeSources() {
        val json = JSONObject()
            .put(
                "global",
                JSONArray().put(
                    JSONObject().put("url", "/user/files/lore.txt").put("name", "lore.txt").put("size", 12800).put("created", 1L)
                )
            )
            .put("character", JSONArray())
            .put(
                "chat",
                JSONArray()
                    .put(JSONObject().put("url", "/user/files/a.md").put("name", "a.md").put("size", 100))
                    .put(JSONObject().put("url", "/user/files/b.md").put("name", "b.md").put("size", 200))
            )
        val bank = DataBankAttachments.fromJson(json)
        assertEquals(1, bank.global.size)
        assertEquals("lore.txt", bank.global[0].name)
        assertEquals(12800L, bank.global[0].size)
        assertTrue(bank.character.isEmpty())
        assertEquals(2, bank.chat.size)
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
