package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
