package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatRuntimeTest {

    @Test
    fun editsDeletesHidesAndSwipesCurrentCharacterChatThroughApiAndRefreshesStore() = runBlocking {
        val source = FakeNativeChatDataSource()
        val store = ChatStore().apply {
            applySnapshot(snapshot(), markRuntimeReady = false)
        }
        val runtime = NativeChatRuntime(store) { source }

        runtime.editMessage(1, "edited")
        runtime.setMessageHidden(0, true)
        runtime.swipeNext(1)
        runtime.deleteMessage(0)

        assertEquals(listOf("second"), source.savedCurrent().messages().map { it["mes"] })
        assertEquals(listOf("second"), store.messages.map { it.mes })
        assertEquals(0, store.messages.single().id)
        assertEquals(1, store.messages.single().swipeId)
    }

    @Test
    fun createsCheckpointWithoutOpeningItAndCreatesBranchThenOpensBranch() = runBlocking {
        val source = FakeNativeChatDataSource()
        val store = ChatStore().apply {
            applySnapshot(snapshot(), markRuntimeReady = false)
        }
        val runtime = NativeChatRuntime(store) { source }

        val checkpointName = runtime.createCheckpoint(messageId = 1, requestedName = "")
        assertEquals("main - Checkpoint #1", checkpointName)
        assertEquals("main.jsonl", store.chatFile)
        assertEquals("main - Checkpoint #1", source.savedCurrent().message(1).extra()["bookmark_link"])
        assertEquals(listOf("one", "first"), source.savedChat(checkpointName).messages().map { it["mes"] })
        assertEquals("main", source.savedChat(checkpointName).headerMetadata()["main_chat"])

        val branchName = runtime.createBranch(messageId = 0)
        assertEquals("main - Branch #1", branchName)
        assertEquals("main - Branch #1", store.chatFile)
        assertEquals(listOf("one"), store.messages.map { it.mes })
        assertEquals(listOf("main - Branch #1"), source.savedCurrent().message(0).extra()["branches"])
        assertEquals("main", source.savedChat(branchName).headerMetadata()["main_chat"])
    }

    @Test
    fun createsNewCharacterChatAndOpensIt() = runBlocking {
        val source = FakeNativeChatDataSource(
            character = CharacterDetail(
                id = "Alice.png",
                name = "Alice",
                chat = "main.jsonl",
                firstMessage = "Welcome back.",
                alternateGreetings = listOf("Alternate hello.")
            )
        )
        val store = ChatStore().apply {
            applySnapshot(snapshot(), markRuntimeReady = false)
        }
        val runtime = NativeChatRuntime(store) { source }

        val newChat = runtime.createNewChat("Alice.png")

        assertTrue(newChat.startsWith("Alice - "))
        assertEquals(newChat, store.chatFile)
        assertEquals(listOf("Welcome back."), store.messages.map { it.mes })
        assertEquals("Alice", source.savedChat(newChat).header()["character_name"])
        assertEquals(listOf("Welcome back."), source.savedChat(newChat).messages().map { it["mes"] })
        assertEquals(listOf("Welcome back.", "Alternate hello."), source.savedChat(newChat).message(0)["swipes"])
    }

    private fun snapshot(): ChatSnapshot =
        buildNativeCharacterChatSnapshot(
            avatar = "Alice.png",
            character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
            chatFile = "main.jsonl",
            rawChat = FakeNativeChatDataSource.initialChat()
        )

    private class FakeNativeChatDataSource(
        private val character: CharacterDetail = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
    ) : NativeChatDataSource {
        private val chats = linkedMapOf("main" to initialChat())
        private val saved = linkedMapOf<String, MutableList<Any?>>()

        override suspend fun getCharacter(avatar: String): CharacterDetail =
            character.copy(id = avatar)

        override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
            (chats[chatFile.removeSuffix(".jsonl")] ?: mutableListOf()).deepCopyChat()

        override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
            val key = chatFile.removeSuffix(".jsonl")
            chats[key] = chat.deepCopyChat()
            saved[key] = chat.deepCopyChat()
        }

        override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
            chats.keys.map { name -> CharacterChatSummary(id = name, fileName = "$name.jsonl") }

        fun savedCurrent(): MutableList<Any?> = savedChat("main")

        fun savedChat(name: String): MutableList<Any?> =
            saved[name.removeSuffix(".jsonl")] ?: error("No saved chat named $name")

        companion object {
            fun initialChat(): MutableList<Any?> = mutableListOf(
                linkedMapOf(
                    "user_name" to "Alex",
                    "character_name" to "Alice",
                    "chat_metadata" to linkedMapOf("integrity" to "abc", "world_info" to "Lore")
                ),
                linkedMapOf(
                    "name" to "Alex",
                    "is_user" to true,
                    "is_system" to false,
                    "send_date" to "June 3",
                    "mes" to "one",
                    "extra" to linkedMapOf<String, Any?>()
                ),
                linkedMapOf(
                    "name" to "Alice",
                    "is_user" to false,
                    "is_system" to false,
                    "send_date" to "June 3",
                    "mes" to "first",
                    "swipe_id" to 0,
                    "swipes" to listOf("first", "second"),
                    "swipe_info" to listOf(mapOf("send_date" to "a"), mapOf("send_date" to "b")),
                    "extra" to linkedMapOf<String, Any?>()
                )
            )
        }
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

    private fun List<Any?>.header(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return first() as Map<String, Any?>
    }
}

private fun List<Any?>.deepCopyChat(): MutableList<Any?> =
    map { deepCopyValue(it) }.toMutableList()

private fun deepCopyValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
            value.forEach { (key, nested) -> if (key != null) out[key.toString()] = deepCopyValue(nested) }
        }
        is List<*> -> value.map { deepCopyValue(it) }
        is Array<*> -> value.map { deepCopyValue(it) }
        else -> value
    }
