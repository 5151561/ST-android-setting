package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.ChatPayloadCursor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(1, source.getChatJsonlCallCount)
        assertFalse(source.savedChatKeys().any { isNativeChatBackupName(it) })
    }

    @Test
    fun mutatingTailPaginatedStoreResolvesRawIndexAndPreservesTailWindow() = runBlocking {
        val source = FakeNativeChatDataSource()
        val cursor = ChatPayloadCursor(offset = 128L, size = 512L, modifiedMillis = 1000L)
        // Store only holds the last message ("first") out of the 2 messages on disk ("one", "first")
        val tailRawChat = listOf(
            FakeNativeChatDataSource.initialChat()[0],
            FakeNativeChatDataSource.initialChat()[2],
        )
        val store = ChatStore().apply {
            applySnapshot(
                buildNativeCharacterChatSnapshot(
                    avatar = "Alice.png",
                    character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
                    chatFile = "main.jsonl",
                    rawChat = tailRawChat,
                ),
                markRuntimeReady = false,
            )
            hasMoreHistory = true
            historyCursor = cursor
        }
        val runtime = NativeChatRuntime(store) { source }

        // UI messageId = 0 is the second message on disk (index 1)
        runtime.editMessage(0, "tail-edited")

        assertEquals(listOf("one", "tail-edited"), source.savedCurrent().messages().map { it["mes"] })
        assertEquals(listOf("tail-edited"), store.messages.map { it.mes })
        assertEquals(0, store.messages.single().id)
        assertTrue(store.hasMoreHistory)
        assertEquals(cursor, store.historyCursor)
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

    @Test
    fun `appendCharacterMessages normalizes missing header integrity identically for dataSource and cache`() = runBlocking {
        val source = FakeNativeChatDataSource()
        val repository = NativeChatRepository(dataSourceProvider = { source })

        // Load empty chat so cachedSession is initialized with empty messages.
        val (_, initialLoaded) = repository.load("Alice.png", "empty.jsonl")
        assertTrue(initialLoaded.isEmpty())

        val headerWithoutIntegrity = linkedMapOf<String, Any?>(
            "user_name" to "Alex",
            "character_name" to "Alice",
            "chat_metadata" to linkedMapOf<String, Any?>("world_info" to "Lore")
        )
        val firstMessage = linkedMapOf<String, Any?>(
            "name" to "Alex",
            "is_user" to true,
            "is_system" to false,
            "send_date" to "June 3",
            "mes" to "hello",
            "extra" to linkedMapOf<String, Any?>()
        )

        repository.appendCharacterMessages(
            avatar = "Alice.png",
            chatFile = "empty.jsonl",
            newMessages = listOf(firstMessage),
            headerIfMissing = headerWithoutIntegrity,
        )

        val passedHeader = source.lastAppendHeaderIfMissing ?: error("Expected headerIfMissing to be passed")
        @Suppress("UNCHECKED_CAST")
        val passedIntegrity = (passedHeader["chat_metadata"] as? Map<String, Any?>)
            ?.get("integrity")
            ?.toString()
            .orEmpty()
        assertTrue(passedIntegrity.isNotBlank())

        val diskIntegrity = source.savedChat("empty.jsonl").headerMetadata()["integrity"]?.toString()
        val (_, cachedChat) = repository.load("Alice.png", "empty.jsonl")
        val cachedIntegrity = cachedChat.headerMetadata()["integrity"]?.toString()

        assertEquals(passedIntegrity, diskIntegrity)
        assertEquals(passedIntegrity, cachedIntegrity)
    }

    @Test
    fun `appendCharacterMessages uses expectedIntegrity recorded from tail load`() = runBlocking {
        val source = FakeNativeChatDataSource()
        val repository = NativeChatRepository(dataSourceProvider = { source })

        val tailLoadedRawChat = listOf<Any?>(
            mapOf(
                "user_name" to "Alex",
                "character_name" to "Alice",
                "chat_metadata" to mapOf("integrity" to "abc", "world_info" to "Lore")
            ),
            mapOf(
                "name" to "Alice",
                "is_user" to false,
                "is_system" to false,
                "send_date" to "June 3",
                "mes" to "first"
            )
        )
        NativeChatRepository.recordKnownIntegrity("Alice.png", "main.jsonl", tailLoadedRawChat)

        val newMessage = linkedMapOf<String, Any?>(
            "name" to "Alex",
            "is_user" to true,
            "is_system" to false,
            "send_date" to "June 3",
            "mes" to "follow up",
            "extra" to linkedMapOf<String, Any?>()
        )
        repository.appendCharacterMessages(
            avatar = "Alice.png",
            chatFile = "main.jsonl",
            newMessages = listOf(newMessage),
        )

        assertEquals("abc", source.lastAppendExpectedIntegrity)
        assertEquals(0, source.getChatJsonlCallCount)
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
        var getChatJsonlCallCount: Int = 0
            private set
        var lastAppendExpectedIntegrity: String? = null
            private set
        var lastAppendHeaderIfMissing: Map<String, Any?>? = null
            private set

        override suspend fun getCharacter(avatar: String): CharacterDetail =
            character.copy(id = avatar)

        override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> {
            getChatJsonlCallCount += 1
            return (chats[chatFile.removeSuffix(".jsonl")] ?: mutableListOf()).deepCopyChat()
        }

        override suspend fun getChatIntegrity(avatar: String, chatFile: String): String? {
            val chat = chats[chatFile.removeSuffix(".jsonl")] ?: return null
            val header = chat.firstOrNull() as? Map<*, *> ?: return null
            val meta = header["chat_metadata"] as? Map<*, *> ?: return null
            return meta["integrity"]?.toString()
        }

        override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
            val key = chatFile.removeSuffix(".jsonl")
            chats[key] = chat.deepCopyChat()
            saved[key] = chat.deepCopyChat()
        }

        override suspend fun appendChatJsonl(
            avatar: String,
            chatFile: String,
            newMessages: List<Any?>,
            headerIfMissing: Map<String, Any?>?,
            expectedIntegrity: String?,
        ): Boolean {
            lastAppendExpectedIntegrity = expectedIntegrity
            lastAppendHeaderIfMissing = headerIfMissing
            val key = chatFile.removeSuffix(".jsonl")
            val current = (chats[key] ?: mutableListOf()).deepCopyChat()
            if (current.isEmpty()) {
                headerIfMissing?.let { current.add(deepCopyValue(it)) }
            }
            newMessages.forEach { current.add(deepCopyValue(it)) }
            chats[key] = current.deepCopyChat()
            saved[key] = current.deepCopyChat()
            return true
        }

        override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
            chats.keys.map { name -> CharacterChatSummary(id = name, fileName = "$name.jsonl") }

        fun savedCurrent(): MutableList<Any?> = savedChat("main")

        fun savedChatKeys(): Set<String> = saved.keys.toSet()

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
