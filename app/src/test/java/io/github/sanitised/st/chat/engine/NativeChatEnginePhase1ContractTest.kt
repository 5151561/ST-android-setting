package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.buildNativeCharacterChatSnapshot
import java.lang.reflect.Proxy
import kotlinx.coroutines.Job
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
        val bridge = RecordingBridgeActions()
        val api = RecordingTavernCoreApi(settings = openAiSettings())
        val store = readyCharacterStore(api.currentChat())
        val engine = NativeChatEngine(
            scope = this,
            store = store,
            bridge = bridge,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        assertEquals(emptyList<String>(), bridge.events)
        assertEquals("main.jsonl", api.savedChatFiles().last())
        assertTrue(api.savedChatFiles().first().startsWith("main.native-backup-"))
        assertEquals(listOf("hello", "native reply"), api.savedMessages())
        assertEquals(listOf("hello", "native reply"), store.messages.map { it.mes })
    }

    @Test
    fun unsupportedNativeSendAlignsWebViewBeforeBridgeSend() = runBlocking {
        val bridge = RecordingBridgeActions()
        val api = RecordingTavernCoreApi(settings = mapOf("main_api" to "kobold"))
        val engine = NativeChatEngine(
            scope = this,
            store = readyCharacterStore(api.currentChat()),
            bridge = bridge,
            clientProvider = { api.proxy() },
            logger = NativeChatLogger.None,
        )

        engine.send("hello")
        joinLaunchedJobs()

        assertEquals(listOf("reloadChat", "sendMessage:hello"), bridge.events)
    }

    @Test
    fun bridgeGenerationWritesAlignWebViewBeforeDispatch() {
        val bridge = RecordingBridgeActions()
        val engine = NativeChatEngine(
            scope = kotlinx.coroutines.CoroutineScope(Job()),
            store = readyCharacterStore(RecordingTavernCoreApi.initialChat()),
            bridge = bridge,
            clientProvider = { error("Bridge-only actions should not need the API") },
            logger = NativeChatLogger.None,
        )

        engine.regenerate()
        engine.continueGeneration()

        assertEquals(listOf("reloadChat", "regenerate", "reloadChat", "continueGeneration"), bridge.events)
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

    private class RecordingBridgeActions : ChatRuntimeBridgeActions {
        val events = mutableListOf<String>()

        override fun sendMessage(text: String) {
            events += "sendMessage:$text"
        }

        override fun stopGeneration() {
            events += "stopGeneration"
        }

        override fun regenerate() {
            events += "regenerate"
        }

        override fun continueGeneration() {
            events += "continueGeneration"
        }

        override fun reloadChat() {
            events += "reloadChat"
        }
    }

    private class RecordingTavernCoreApi(
        private val settings: Map<String, Any?>,
    ) {
        private var chat = initialChat()
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
                    "generateChatCompletionStream" -> flowOf("native reply")
                    "generateChatCompletion" -> "native reply"
                    "generateTextCompletionStream" -> flowOf("native reply")
                    "generateTextCompletion" -> "native reply"
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
