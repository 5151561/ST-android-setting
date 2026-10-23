package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.ChatPayloadChunk
import io.github.sanitised.st.api.ChatPayloadCursor
import io.github.sanitised.st.api.ChatPayloadTail
import io.github.sanitised.st.api.TavernCoreApi
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatLoaderTest {

    @Test
    fun buildsCharacterChatSnapshotFromJsonlWithoutRuntimeBridge() {
        val snapshot = buildNativeCharacterChatSnapshot(
            avatar = "Alice.png",
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "current.jsonl"),
            chatFile = "current.jsonl",
            rawChat = listOf(
                mapOf(
                    "user_name" to "Alex",
                    "character_name" to "Alice",
                    "create_date" to "June 2, 2026",
                    "chat_metadata" to mapOf(
                        "integrity" to "abc",
                        "authors_note" to "keep it tense",
                        "cfg_guidance_scale" to 1.25,
                        "cfg_negative_prompt" to "flat",
                        "cfg_positive_prompt" to "vivid",
                        "world_info" to "Library",
                        "quickReply" to mapOf(
                            "setList" to listOf(mapOf("set" to "Chat Set", "isVisible" to true))
                        )
                    )
                ),
                mapOf(
                    "name" to "Alex",
                    "is_user" to true,
                    "mes" to "hello",
                    "send_date" to "June 2",
                    "extra" to mapOf("bookmark_link" to "checkpoint")
                ),
                mapOf(
                    "name" to "Alice",
                    "is_user" to false,
                    "mes" to "hi",
                    "swipe_id" to 1,
                    "swipes" to listOf("first", "second"),
                    "extra" to mapOf("reasoning" to "thinking")
                )
            )
        )

        assertEquals("character", snapshot.mode)
        assertEquals("Alice.png", snapshot.avatarUrl)
        assertEquals("Alice", snapshot.characterName)
        assertEquals("current.jsonl", snapshot.chatFile)
        assertFalse(snapshot.isGenerating)
        assertEquals("keep it tense", snapshot.metadata.optString("authorsNote"))
        assertEquals(1.25, snapshot.metadata.optDouble("cfgScale"), 0.0)
        assertEquals("flat", snapshot.metadata.optString("cfgNegativePrompt"))
        assertEquals("vivid", snapshot.metadata.optString("cfgPositivePrompt"))
        assertEquals("Library", snapshot.metadata.optString("worldInfo"))
        assertEquals(
            "Chat Set",
            snapshot.metadata.optJSONObject("quickReply")
                ?.optJSONArray("setList")
                ?.optJSONObject(0)
                ?.optString("set")
        )

        assertEquals(listOf(0, 1), snapshot.messages.map { it.id })
        assertEquals("Alex", snapshot.messages[0].name)
        assertEquals("hello", snapshot.messages[0].mes)
        assertEquals("checkpoint", snapshot.messages[0].extra.optString("bookmark_link"))
        assertEquals(listOf("first", "second"), snapshot.messages[1].swipes)
        assertEquals("thinking", snapshot.messages[1].extra.optString("reasoning"))
    }

    @Test
    fun openCharacterFallsBackToExistingNonBackupChatWhenDefaultChatMissingOnDisk() = runTest {
        val store = ChatStore()
        val api = fakeLoaderApi(
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "missing-default.jsonl"),
            chats = listOf(
                CharacterChatSummary(id = "__native-backup__existing__20260602-120000-000", fileName = "__native-backup__existing__20260602-120000-000.jsonl"),
                CharacterChatSummary(id = "existing-chat", fileName = "existing-chat.jsonl"),
            ),
            tails = mapOf(
                "missing-default.jsonl" to ChatPayloadTail(
                    header = null,
                    messages = emptyList(),
                    cursor = null,
                    hasMoreBefore = false,
                ),
                "existing-chat.jsonl" to ChatPayloadTail(
                    header = mapOf("user_name" to "User", "character_name" to "Alice", "chat_metadata" to mapOf("integrity" to "id-1")),
                    messages = listOf(mapOf("name" to "Alice", "is_user" to false, "mes" to "Welcome back")),
                    cursor = null,
                    hasMoreBefore = false,
                ),
            ),
        )

        val loader = NativeChatLoader(store) { api }
        val opened = loader.openCharacter("Alice.png", chatFile = null)

        assertTrue(opened)
        assertEquals("existing-chat.jsonl", store.chatFile)
        assertEquals(listOf("Welcome back"), store.messages.map { it.mes })
        assertFalse(store.hasMoreHistory)
        assertNull(store.historyCursor)
    }

    @Test
    fun openCharacterReturnsFalseWhenDefaultChatMissingAndNoExistingChats() = runTest {
        val store = ChatStore()
        val api = fakeLoaderApi(
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "missing-default.jsonl"),
            chats = emptyList(),
            tails = emptyMap(),
        )

        val loader = NativeChatLoader(store) { api }
        val opened = loader.openCharacter("Alice.png", chatFile = null)

        assertFalse(opened)
        assertTrue(store.messages.isEmpty())
    }

    @Test
    fun openCharacterTailAndLoadMoreHistoryPrependsAndReindexesMessages() = runTest {
        val store = ChatStore()
        val initialCursor = ChatPayloadCursor(offset = 512L, size = 1024L, modifiedMillis = 1000L)
        val api = fakeLoaderApi(
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "long.jsonl"),
            chats = listOf(CharacterChatSummary(id = "long", fileName = "long.jsonl")),
            tails = mapOf(
                "long.jsonl" to ChatPayloadTail(
                    header = mapOf("user_name" to "User", "character_name" to "Alice", "chat_metadata" to mapOf("integrity" to "id-1")),
                    messages = listOf(
                        mapOf("name" to "User", "is_user" to true, "mes" to "m3"),
                        mapOf("name" to "Alice", "is_user" to false, "mes" to "m4"),
                    ),
                    cursor = initialCursor,
                    hasMoreBefore = true,
                )
            ),
            chunks = mapOf(
                initialCursor to ChatPayloadChunk(
                    messages = listOf(
                        mapOf("name" to "User", "is_user" to true, "mes" to "m1"),
                        mapOf("name" to "Alice", "is_user" to false, "mes" to "m2"),
                    ),
                    cursor = null,
                    hasMoreBefore = false,
                )
            ),
        )

        val loader = NativeChatLoader(store) { api }
        assertTrue(loader.openCharacter("Alice.png", "long.jsonl"))
        assertEquals(listOf(0, 1), store.messages.map { it.id })
        assertEquals(listOf("m3", "m4"), store.messages.map { it.mes })
        assertTrue(store.hasMoreHistory)
        assertEquals(initialCursor, store.historyCursor)

        assertTrue(loader.loadMoreHistory())
        assertEquals(listOf(0, 1, 2, 3), store.messages.map { it.id })
        assertEquals(listOf("m1", "m2", "m3", "m4"), store.messages.map { it.mes })
        assertFalse(store.hasMoreHistory)
        assertNull(store.historyCursor)
    }

    private fun fakeLoaderApi(
        character: CharacterDetail,
        chats: List<CharacterChatSummary>,
        tails: Map<String, ChatPayloadTail>,
        chunks: Map<ChatPayloadCursor, ChatPayloadChunk> = emptyMap(),
    ): TavernCoreApi =
        Proxy.newProxyInstance(
            TavernCoreApi::class.java.classLoader,
            arrayOf(TavernCoreApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCharacter" -> character
                "listCharacterChats" -> chats
                "getChatTailJsonl" -> {
                    val chatFile = args?.get(1) as String
                    tails[chatFile] ?: ChatPayloadTail(
                        header = null,
                        messages = emptyList(),
                        cursor = null,
                        hasMoreBefore = false,
                    )
                }
                "getChatBeforeJsonl" -> {
                    val cursor = args?.get(2) as ChatPayloadCursor
                    chunks[cursor] ?: ChatPayloadChunk(
                        messages = emptyList(),
                        cursor = null,
                        hasMoreBefore = false,
                    )
                }
                else -> error("Unexpected TavernCoreApi call: ${method.name}")
            }
        } as TavernCoreApi
}
