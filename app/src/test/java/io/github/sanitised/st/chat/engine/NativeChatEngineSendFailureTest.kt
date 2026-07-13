package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.PendingAttachment
import io.github.sanitised.st.chat.buildNativeCharacterChatSnapshot
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.coroutineContext

/**
 * Guards against the regression where a failure while persisting the JSONL (after
 * the user's message was optimistically added, and possibly after the AI reply
 * finished streaming) deleted both messages from the UI, silently discarding the
 * user's input and any reply they'd already seen.
 */
class NativeChatEngineSendFailureTest {

    @Test
    fun saveFailureKeepsMessagesVisibleAndOffersRetry() = runBlocking {
        val api = RecordingApi(settings = openAiSettings(), failSave = true)
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        assertEquals(listOf("hello", "native reply"), store.messages.map { it.mes })
        assertNotNull(store.saveError)
        assertNotNull(store.pendingRetry)
        assertEquals(emptyList<String>(), api.savedChatFiles())

        api.failSave = false
        store.pendingRetry?.invoke()
        joinLaunchedJobs()

        assertNull(store.saveError)
        assertEquals(listOf("hello", "native reply"), store.messages.map { it.mes })
        assertEquals(listOf("hello", "native reply"), api.savedMessages())
    }

    @Test
    fun saveFailureBeforeAnyReplyDropsOnlyTheBlankAssistantPlaceholder() = runBlocking {
        val api = RecordingApi(settings = mapOf("main_api" to "unknown-provider"), failSave = true)
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        // Unsupported provider: no optimistic messages are added at all, nothing to
        // preserve — the existing "unsupported" reporting path is untouched.
        assertTrue(store.messages.isEmpty())
        assertNull(store.saveError)
    }

    @Test
    fun retryRestoresPendingAttachmentsBeforeResending() = runBlocking {
        val api = RecordingApi(settings = openAiSettings(), failSave = true)
        val store = readyCharacterStore(api.currentChat())
        val attachment = PendingAttachment(url = "/user/files/lore.txt", name = "lore.txt", size = 42L, isMedia = false)
        store.addPendingAttachment(attachment)
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("see attached")
        joinLaunchedJobs()

        assertNotNull(store.saveError)
        assertTrue(store.pendingAttachments.isEmpty())

        api.failSave = false
        store.pendingRetry?.invoke()
        joinLaunchedJobs()

        assertNull(store.saveError)
        assertEquals(listOf("lore.txt"), api.savedUserFileNames())
    }

    @Test
    fun regenerateSaveFailureKeepsStreamedReplyAndOffersRetry() = runBlocking {
        val api = RecordingApi(settings = openAiSettings(), failSave = true, chat = chatWithAssistant())
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.regenerate()
        joinLaunchedJobs()

        // The freshly streamed reply stays visible in place of the old one instead of
        // silently reverting, and the still-unsaved state is surfaced with a retry.
        assertEquals("native reply", store.messages.last().mes)
        assertNotNull(store.saveError)
        assertNotNull(store.pendingRetry)

        api.failSave = false
        store.pendingRetry?.invoke()
        joinLaunchedJobs()

        assertNull(store.saveError)
        assertEquals("native reply", api.savedMessages().last())
    }

    private fun chatWithAssistant(): MutableList<Any?> =
        mutableListOf(
            linkedMapOf(
                "user_name" to "Alex",
                "character_name" to "Alice",
                "chat_metadata" to linkedMapOf("integrity" to "start"),
            ),
            linkedMapOf(
                "name" to "Alex",
                "is_user" to true,
                "is_system" to false,
                "send_date" to "June 1, 2026 1:00pm",
                "mes" to "hello",
                "extra" to emptyMap<String, Any?>(),
            ),
            linkedMapOf(
                "name" to "Alice",
                "is_user" to false,
                "is_system" to false,
                "send_date" to "June 1, 2026 1:01pm",
                "mes" to "old reply",
                "swipes" to listOf("old reply"),
                "swipe_id" to 0,
                "extra" to emptyMap<String, Any?>(),
            ),
        )

    private fun readyCharacterStore(chat: MutableList<Any?>): ChatStore =
        ChatStore().apply {
            applySnapshot(
                buildNativeCharacterChatSnapshot(
                    avatar = "Alice.png",
                    character = CharacterDetail(id = "Alice.png", name = "Alice", chat = "main.jsonl"),
                    chatFile = "main.jsonl",
                    rawChat = chat,
                ),
                markRuntimeReady = false,
            )
        }

    private suspend fun joinLaunchedJobs() {
        coroutineContext.job.children.toList().joinAll()
    }

    private fun openAiSettings(): Map<String, Any?> =
        mapOf(
            "main_api" to "openai",
            "username" to "Alex",
            "oai_settings" to mapOf(
                "chat_completion_source" to "openai",
                "openai_model" to "gpt-test",
                "openai_max_tokens" to 100,
                "openai_max_context" to 2000,
            ),
        )

    private class RecordingApi(
        private val settings: Map<String, Any?>,
        var failSave: Boolean,
        private val reply: String = "native reply",
        chat: MutableList<Any?> = initialChat(),
    ) {
        private var chat = chat
        private var saved: MutableList<Any?>? = null
        private val saveCalls = mutableListOf<String>()

        fun currentChat(): MutableList<Any?> = chat.deepCopyChat()

        fun savedMessages(): List<String> =
            (saved ?: error("No saved chat"))
                .drop(1)
                .map {
                    @Suppress("UNCHECKED_CAST")
                    (it as Map<String, Any?>)["mes"].toString()
                }

        fun savedChatFiles(): List<String> = saveCalls.toList()

        fun savedUserFileNames(): List<String> {
            val row = (saved ?: error("No saved chat")).drop(1).first() as Map<*, *>
            val extra = row["extra"] as? Map<*, *> ?: return emptyList()
            val files = extra["files"] as? List<*> ?: return emptyList()
            return files.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
        }

        fun proxy(): TavernCoreApi =
            Proxy.newProxyInstance(
                TavernCoreApi::class.java.classLoader,
                arrayOf(TavernCoreApi::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getSettings" -> settings
                    "getCharacter" -> CharacterDetail(
                        id = "Alice.png",
                        name = "Alice",
                        description = "A test character",
                        chat = "main.jsonl",
                    )
                    "getWorldInfo" -> WorldInfoBook(args?.getOrNull(0)?.toString().orEmpty())
                    "getChatJsonl" -> chat.deepCopyChat()
                    "saveChatJsonl" -> {
                        if (failSave) error("simulated save failure")
                        @Suppress("UNCHECKED_CAST")
                        val next = (args?.getOrNull(2) as List<Any?>).deepCopyChat()
                        val chatFile = args.getOrNull(1).toString()
                        saveCalls += chatFile
                        if (chatFile.removeSuffix(".jsonl") == "main") {
                            chat = next.deepCopyChat()
                            saved = chat.deepCopyChat()
                        }
                        Unit
                    }
                    "generateChatCompletionStream" -> flowOf(reply)
                    "generateChatCompletion" -> reply
                    "generateTextCompletionStream" -> flowOf(reply)
                    "generateTextCompletion" -> reply
                    else -> error("Unexpected TavernCoreApi call: ${method.name}")
                }
            } as TavernCoreApi

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
}

private fun List<Any?>.deepCopyChat(): MutableList<Any?> =
    map { deepCopyValue(it) }.toMutableList()

private fun deepCopyValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
            value.forEach { (key, nested) ->
                if (key != null) out[key.toString()] = deepCopyValue(nested)
            }
        }
        is List<*> -> value.map { deepCopyValue(it) }
        is Array<*> -> value.map { deepCopyValue(it) }
        else -> value
    }
