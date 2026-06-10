package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeChatRepositorySafetyTest {

    @Test
    fun savesBackupBeforeTargetAndRefreshesIntegrity() = runBlocking {
        val source = SafetyDataSource()
        val repository = NativeChatRepository(
            dataSourceProvider = { source },
            backupNameProvider = { _, _ -> "main.native-backup-1" },
        )
        val chat = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "hello", isUser = true))
        }

        repository.save("Alice.png", "main.jsonl", chat)

        assertEquals(listOf("main.native-backup-1", "main.jsonl"), source.saveCalls.map { it.chatFile })
        assertEquals("start", source.saved("main.native-backup-1").integrity())
        assertEquals(listOf("hello"), source.saved("main.jsonl").messages())
        assertNotEquals("start", source.saved("main.jsonl").integrity())
    }

    @Test
    fun rejectsBlindSaveWhenDiskIntegrityChangedAfterLoad() = runBlocking {
        val source = SafetyDataSource()
        val repository = NativeChatRepository(
            dataSourceProvider = { source },
            backupNameProvider = { _, _ -> "main.native-backup-1" },
        )
        val chat = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "late edit", isUser = true))
        }
        source.replaceDiskIntegrity("changed-by-webview")

        assertThrows(NativeChatIntegrityConflict::class.java) {
            runBlocking { repository.save("Alice.png", "main.jsonl", chat) }
        }
        assertTrue(source.saveCalls.isEmpty())
    }

    @Test
    fun serializesWritesForTheSameCharacterChat() = runBlocking {
        val source = SafetyDataSource(saveDelayMs = 40)
        val repository = NativeChatRepository(
            dataSourceProvider = { source },
            backupNameProvider = { _, _ -> "main.native-backup-${source.saveCalls.size + 1}" },
        )
        val first = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "first", isUser = true))
        }
        val second = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "second", isUser = true))
        }

        coroutineScope {
            listOf(
                async { runCatching { repository.save("Alice.png", "main.jsonl", first) } },
                async { runCatching { repository.save("Alice.png", "main.jsonl", second) } },
            ).awaitAll()
        }

        assertEquals(1, source.maxConcurrentSaves)
    }

    @Test
    fun listChatNamesSkipsNativeBackupFiles() = runBlocking {
        val source = SafetyDataSource()
        source.addChat("main.native-backup-20260610-100000-000")
        source.addChat("__native-backup__main__20260610-100001-000")
        val repository = NativeChatRepository(dataSourceProvider = { source })

        assertEquals(setOf("main"), repository.listChatNames("Alice.png"))
    }

    @Test
    fun savePrunesOldNativeBackupsForTheSameChat() = runBlocking {
        val source = SafetyDataSource()
        listOf(
            "main.native-backup-20260610-100000-000",
            "main.native-backup-20260610-100001-000",
            "main.native-backup-20260610-100002-000",
            "main.native-backup-20260610-100003-000",
            "main.native-backup-20260610-100004-000",
        ).forEach(source::addChat)
        val repository = NativeChatRepository(
            dataSourceProvider = { source },
            backupNameProvider = { _, _ -> "main.native-backup-20260610-100005-000" },
        )
        val chat = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "hello", isUser = true))
        }

        repository.save("Alice.png", "main.jsonl", chat)

        assertEquals(listOf("main.native-backup-20260610-100000-000.jsonl"), source.deletedChats)
    }

    @Test
    fun backupPruningUsesTimestampAcrossLegacyAndPrefixedBackupNames() = runBlocking {
        val source = SafetyDataSource()
        listOf(
            "main.native-backup-20260610-100000-000",
            "main.native-backup-20260610-100001-000",
            "main.native-backup-20260610-100002-000",
            "main.native-backup-20260610-100003-000",
            "main.native-backup-20260610-100004-000",
        ).forEach(source::addChat)
        val repository = NativeChatRepository(
            dataSourceProvider = { source },
            backupNameProvider = { _, _ -> "__native-backup__main__20260610-100005-000" },
        )
        val chat = source.getChatJsonl("Alice.png", "main.jsonl").apply {
            add(message("Alex", "hello", isUser = true))
        }

        repository.save("Alice.png", "main.jsonl", chat)

        assertEquals(listOf("main.native-backup-20260610-100000-000.jsonl"), source.deletedChats)
    }

    private class SafetyDataSource(
        private val saveDelayMs: Long = 0,
    ) : NativeChatDataSource {
        private val chats = linkedMapOf("main" to initialChat())
        val saveCalls = mutableListOf<SaveCall>()
        val deletedChats = mutableListOf<String>()
        private var activeSaves = 0
        var maxConcurrentSaves = 0
            private set

        override suspend fun getCharacter(avatar: String): CharacterDetail =
            CharacterDetail(id = avatar, name = "Alice", chat = "main.jsonl")

        override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
            saved(chatFile).deepCopyChat()

        override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
            activeSaves += 1
            maxConcurrentSaves = maxOf(maxConcurrentSaves, activeSaves)
            try {
                if (saveDelayMs > 0) delay(saveDelayMs)
                saveCalls += SaveCall(chatFile, chat.deepCopyChat())
                chats[chatFile.removeSuffix(".jsonl")] = chat.deepCopyChat()
            } finally {
                activeSaves -= 1
            }
        }

        override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
            chats.keys.map { CharacterChatSummary(id = it, fileName = "$it.jsonl") }

        override suspend fun deleteCharacterChat(avatar: String, chatFile: String) {
            deletedChats += chatFile
            chats.remove(chatFile.removeSuffix(".jsonl"))
        }

        fun saved(chatFile: String): MutableList<Any?> =
            chats[chatFile.removeSuffix(".jsonl")] ?: error("No saved chat $chatFile")

        fun addChat(chatFile: String) {
            chats[chatFile.removeSuffix(".jsonl")] = initialChat()
        }

        fun replaceDiskIntegrity(integrity: String) {
            chats["main"] = chats["main"]!!.deepCopyChat().apply {
                headerMetadata()["integrity"] = integrity
            }
        }

        companion object {
            fun initialChat(): MutableList<Any?> =
                mutableListOf(
                    linkedMapOf(
                        "user_name" to "Alex",
                        "character_name" to "Alice",
                        "chat_metadata" to linkedMapOf("integrity" to "start"),
                    )
                )
        }
    }

    private data class SaveCall(
        val chatFile: String,
        val chat: MutableList<Any?>,
    )
}

private fun message(name: String, text: String, isUser: Boolean): MutableMap<String, Any?> =
    linkedMapOf(
        "name" to name,
        "is_user" to isUser,
        "is_system" to false,
        "send_date" to "June 3",
        "mes" to text,
        "extra" to linkedMapOf<String, Any?>(),
    )

private fun List<Any?>.messages(): List<String> =
    drop(1).map {
        @Suppress("UNCHECKED_CAST")
        (it as Map<String, Any?>)["mes"].toString()
    }

private fun List<Any?>.integrity(): String =
    headerMetadata()["integrity"].toString()

private fun List<Any?>.headerMetadata(): MutableMap<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    val header = first() as MutableMap<String, Any?>
    @Suppress("UNCHECKED_CAST")
    return header["chat_metadata"] as MutableMap<String, Any?>
}

private fun List<Any?>.deepCopyChat(): MutableList<Any?> =
    map { deepCopyChatValue(it) }.toMutableList()

private fun deepCopyChatValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
            value.forEach { (key, nested) ->
                if (key != null) out[key.toString()] = deepCopyChatValue(nested)
            }
        }
        is List<*> -> value.map { deepCopyChatValue(it) }
        is Array<*> -> value.map { deepCopyChatValue(it) }
        else -> value
    }
