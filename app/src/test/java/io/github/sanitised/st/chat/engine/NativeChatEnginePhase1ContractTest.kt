package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.PendingAttachment
import io.github.sanitised.st.chat.buildNativeCharacterChatSnapshot
import io.github.sanitised.st.chat.fileAttachments
import io.github.sanitised.st.chat.mediaAttachments
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.coroutineContext

class NativeChatEnginePhase1ContractTest {

    @Test
    fun nativeSuccessPathSavesJsonlWithoutReloadingCompatibilityRuntime() = runBlocking {
        val api = RecordingTavernCoreApi(settings = openAiSettings())
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        assertEquals("main.jsonl", api.savedChatFiles().last())
        assertTrue(api.savedChatFiles().first().startsWith("__native-backup__main__"))
        assertEquals(listOf("hello", "native reply"), api.savedMessages())
        assertEquals(listOf("hello", "native reply"), store.messages.map { it.mes })
    }

    @Test
    fun unsupportedNativeSendReportsNativeProviderGapWithoutFallback() = runBlocking {
        val api = RecordingTavernCoreApi(settings = mapOf("main_api" to "unknown-provider"))
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        assertEquals(emptyList<String>(), api.savedChatFiles())
        assertEquals("当前 provider 尚未接入原生生成", store.runtimeError)
        assertEquals(emptyList<String>(), store.messages.map { it.mes })
    }

    @Test
    fun regenerateAppendsNewSwipeAndPersistsJsonl() = runBlocking {
        val api = RecordingTavernCoreApi(settings = openAiSettings(), chat = RecordingTavernCoreApi.chatWithAssistant())
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.regenerate()
        joinLaunchedJobs()

        val savedAssistant = api.savedAssistantMessage()
        assertEquals("native reply", savedAssistant["mes"])
        assertEquals(2, savedAssistant["swipe_id"])
        assertEquals(listOf("old reply", "alternate reply", "native reply"), savedAssistant["swipes"])
        assertEquals("native reply", store.messages.last().mes)
        assertEquals(2, store.messages.last().swipeId)
    }

    @Test
    fun continueGenerationAppendsToActiveSwipeAndPersistsJsonl() = runBlocking {
        val api = RecordingTavernCoreApi(
            settings = openAiSettings(),
            chat = RecordingTavernCoreApi.chatWithAssistant(),
            reply = " continued"
        )
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.continueGeneration()
        joinLaunchedJobs()

        val savedAssistant = api.savedAssistantMessage()
        assertEquals("old reply continued", savedAssistant["mes"])
        assertEquals(0, savedAssistant["swipe_id"])
        assertEquals(listOf("old reply continued", "alternate reply"), savedAssistant["swipes"])
        assertEquals("old reply continued", store.messages.last().mes)
    }

    @Test
    fun nativeSendPersistsPendingAttachmentsIntoUserMessageExtra() = runBlocking {
        val api = RecordingTavernCoreApi(settings = openAiSettings())
        val store = readyCharacterStore(api.currentChat())
        store.addPendingAttachment(PendingAttachment(url = "/user/files/lore.txt", name = "lore.txt", size = 42L, isMedia = false))
        store.addPendingAttachment(PendingAttachment(url = "/user/images/ref.png", name = "ref.png", size = 2048L, isMedia = true))
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("see attached")
        joinLaunchedJobs()

        val userMessage = store.messages.first()
        assertEquals(emptyList<PendingAttachment>(), store.pendingAttachments.toList())
        assertEquals("lore.txt", userMessage.fileAttachments.single().name)
        assertEquals("ref.png", userMessage.mediaAttachments.single().title)
        assertEquals(listOf("see attached", "native reply"), api.savedMessages())
        assertEquals("lore.txt", api.savedUserFileNames().single())
        assertEquals("ref.png", api.savedUserMediaNames().single())
    }

    @Test
    fun stopCancelsInFlightGenerationSoNextSendCannotReviveItsSave() = runBlocking {
        val firstStreamStarted = CompletableDeferred<Unit>()
        val releaseFirstStream = CompletableDeferred<Unit>()
        val api = RecordingTavernCoreApi(
            settings = openAiSettings(),
            streamReply = { call ->
                flow {
                    if (call == 1) {
                        firstStreamStarted.complete(Unit)
                        releaseFirstStream.await()
                        emit("stale reply")
                    } else {
                        emit("fresh reply")
                    }
                }
            },
        )
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("first")
        firstStreamStarted.await()
        engine.stop()

        engine.send("second")
        releaseFirstStream.complete(Unit)
        joinLaunchedJobs()

        assertEquals(listOf("second", "fresh reply"), api.savedMessages())
        assertEquals(listOf("second", "fresh reply"), store.messages.map { it.mes })
    }

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

    private class RecordingTavernCoreApi(
        private val settings: Map<String, Any?>,
        chat: MutableList<Any?> = initialChat(),
        private val reply: String = "native reply",
        private val streamReply: ((Int) -> Flow<String>)? = null,
    ) {
        private var chat = chat
        private var saved: MutableList<Any?>? = null
        private val saveCalls = mutableListOf<String>()
        private var streamCalls = 0

        fun currentChat(): MutableList<Any?> = chat.deepCopyChat()

        fun savedMessages(): List<String> =
            (saved ?: error("No saved chat"))
                .drop(1)
                .map {
                    @Suppress("UNCHECKED_CAST")
                    (it as Map<String, Any?>)["mes"].toString()
                }

        fun savedChatFiles(): List<String> = saveCalls.toList()

        fun savedAssistantMessage(): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return (saved ?: error("No saved chat")).drop(1).last() as Map<String, Any?>
        }

        fun savedUserFileNames(): List<String> = savedUserExtraList("files", "name")

        fun savedUserMediaNames(): List<String> = savedUserExtraList("media", "name")

        private fun savedUserExtraList(listKey: String, valueKey: String): List<String> {
            val row = (saved ?: error("No saved chat")).drop(1).first() as Map<*, *>
            val extra = row["extra"] as? Map<*, *> ?: return emptyList()
            val list = extra[listKey] as? List<*> ?: return emptyList()
            return list.mapNotNull { item ->
                (item as? Map<*, *>)?.get(valueKey)?.toString()
            }
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
                    "generateChatCompletionStream" -> {
                        streamCalls += 1
                        streamReply?.invoke(streamCalls) ?: flowOf(reply)
                    }
                    "generateChatCompletion" -> reply
                    "generateTextCompletionStream" -> {
                        streamCalls += 1
                        streamReply?.invoke(streamCalls) ?: flowOf(reply)
                    }
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

            fun chatWithAssistant(): MutableList<Any?> =
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
                        "swipes" to listOf("old reply", "alternate reply"),
                        "swipe_id" to 0,
                        "swipe_info" to listOf(
                            mapOf("send_date" to "a"),
                            mapOf("send_date" to "b"),
                        ),
                        "extra" to mapOf("reasoning" to "kept"),
                    ),
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
