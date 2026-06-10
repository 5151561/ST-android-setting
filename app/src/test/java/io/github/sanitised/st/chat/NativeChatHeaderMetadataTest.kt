package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Phase 1 收尾：作者注 / CFG 作为 chat_metadata（header）原生读写。
 * 字段名对齐上游 ST：authors-note.js `metadata_keys.prompt` = note_prompt，
 * cfg-scale.js `metadataKeys` = cfg_guidance_scale / cfg_negative_prompt / cfg_positive_prompt。
 */
class NativeChatHeaderMetadataTest {

    @Test
    fun setAuthorsNoteWritesUpstreamNotePromptAndDropsLegacyKey() {
        val chat = initialChat()
        NativeChatJsonOps.setAuthorsNote(chat, "Keep the tone gentle.")
        val metadata = headerMetadata(chat)
        assertEquals("Keep the tone gentle.", metadata["note_prompt"])
        assertFalse(metadata.containsKey("authors_note"))
    }

    @Test
    fun setCfgWritesUpstreamCfgKeys() {
        val chat = initialChat()
        NativeChatJsonOps.setCfg(chat, scale = 1.5, negativePrompt = "no purple prose", positivePrompt = "be vivid")
        val metadata = headerMetadata(chat)
        assertEquals(1.5, metadata["cfg_guidance_scale"])
        assertEquals("no purple prose", metadata["cfg_negative_prompt"])
        assertEquals("be vivid", metadata["cfg_positive_prompt"])
    }

    @Test
    fun snapshotPrefersNotePromptOverLegacyAuthorsNote() {
        val chat = initialChat()
        headerMetadata(chat)["note_prompt"] = "upstream value"
        headerMetadata(chat)["authors_note"] = "legacy value"
        val snapshot = buildNativeCharacterChatSnapshot(
            avatar = "Alice.png",
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
            chatFile = "main.jsonl",
            rawChat = chat,
        )
        assertEquals("upstream value", snapshot.metadata.optString("authorsNote"))
    }

    @Test
    fun snapshotFallsBackToLegacyAuthorsNote() {
        val chat = initialChat()
        headerMetadata(chat)["authors_note"] = "legacy value"
        val snapshot = buildNativeCharacterChatSnapshot(
            avatar = "Alice.png",
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
            chatFile = "main.jsonl",
            rawChat = chat,
        )
        assertEquals("legacy value", snapshot.metadata.optString("authorsNote"))
    }

    @Test
    fun runtimeSetAuthorsNoteSavesJsonlAndRefreshesStore() = runBlocking {
        val source = FakeSource()
        val store = storeWith(source)
        val runtime = NativeChatRuntime(store) { source }

        runtime.setAuthorsNote("Stay in character.")

        assertEquals("Stay in character.", headerMetadata(source.savedCurrent())["note_prompt"])
        assertEquals("Stay in character.", store.authorsNote)
    }

    @Test
    fun runtimeSetCfgSavesJsonlAndRefreshesStore() = runBlocking {
        val source = FakeSource()
        val store = storeWith(source)
        val runtime = NativeChatRuntime(store) { source }

        runtime.setCfg(scale = 2.0, negativePrompt = "neg", positivePrompt = "pos")

        val metadata = headerMetadata(source.savedCurrent())
        assertEquals(2.0, metadata["cfg_guidance_scale"])
        assertEquals("neg", metadata["cfg_negative_prompt"])
        assertEquals("pos", metadata["cfg_positive_prompt"])
        assertEquals(2.0, store.cfgScale.toDouble(), 0.0)
        assertEquals("neg", store.cfgNegativePrompt)
        assertEquals("pos", store.cfgPositivePrompt)
    }

    private fun storeWith(source: FakeSource): ChatStore =
        ChatStore().apply {
            applySnapshot(
                buildNativeCharacterChatSnapshot(
                    avatar = "Alice.png",
                    character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
                    chatFile = "main.jsonl",
                    rawChat = initialChat(),
                ),
                markRuntimeReady = false,
            )
        }

    private class FakeSource : NativeChatDataSource {
        private val chats = linkedMapOf("main" to initialChat())

        override suspend fun getCharacter(avatar: String): CharacterDetail =
            CharacterDetail(id = avatar, name = "Alice", chat = "main.jsonl")

        override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
            (chats[chatFile.removeSuffix(".jsonl")] ?: mutableListOf()).deepCopy()

        override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
            chats[chatFile.removeSuffix(".jsonl")] = chat.deepCopy()
        }

        override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
            chats.keys.map { CharacterChatSummary(id = it, fileName = "$it.jsonl") }

        fun savedCurrent(): MutableList<Any?> = chats.getValue("main")
    }

    private companion object {
        fun initialChat(): MutableList<Any?> =
            mutableListOf(
                linkedMapOf<String, Any?>(
                    "user_name" to "Alex",
                    "character_name" to "Alice",
                    "chat_metadata" to linkedMapOf<String, Any?>("integrity" to "start"),
                ),
                linkedMapOf<String, Any?>(
                    "name" to "Alex",
                    "is_user" to true,
                    "is_system" to false,
                    "send_date" to "",
                    "mes" to "hello",
                    "extra" to linkedMapOf<String, Any?>(),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        fun headerMetadata(chat: List<Any?>): MutableMap<String, Any?> =
            (chat.first() as MutableMap<String, Any?>)["chat_metadata"] as MutableMap<String, Any?>

        fun List<Any?>.deepCopy(): MutableList<Any?> =
            map { deepCopyValue(it) }.toMutableList()

        fun deepCopyValue(value: Any?): Any? =
            when (value) {
                is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
                    value.forEach { (key, nested) ->
                        if (key != null) out[key.toString()] = deepCopyValue(nested)
                    }
                }
                is List<*> -> value.map { deepCopyValue(it) }
                else -> value
            }
    }
}
