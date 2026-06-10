package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.chat.NativeChatRepository
import io.github.sanitised.st.chat.TavernNativeChatDataSource
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.TextPromptBuildResult
import io.github.sanitised.st.chat.prompt.TextPromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NativeEngineMode {
    CHAT_COMPLETION,
    TEXT_COMPLETION,
    FALLBACK,
}

enum class ActiveGenerationRoute {
    NONE,
    NATIVE,
    BRIDGE,
}

enum class GenerationStopTarget {
    NATIVE,
    BRIDGE,
}

fun stopTargetForGeneration(mode: String, route: ActiveGenerationRoute): GenerationStopTarget =
    if (mode == "group" || route == ActiveGenerationRoute.BRIDGE) {
        GenerationStopTarget.BRIDGE
    } else {
        GenerationStopTarget.NATIVE
    }

fun engineMode(settings: Map<String, Any?>, authorsNote: String = ""): NativeEngineMode =
    when (settings["main_api"] as? String) {
        "openai" -> NativeEngineMode.CHAT_COMPLETION
        "textgenerationwebui" -> if (TextPromptBuilder.supports(settings, authorsNote)) {
            NativeEngineMode.TEXT_COMPLETION
        } else {
            NativeEngineMode.FALLBACK
        }
        else -> NativeEngineMode.FALLBACK
    }

/**
 * Native generation engine: assembles the prompt on-device ([PromptBuilder] / [TextPromptBuilder]),
 * calls the backend generate endpoint directly, mirrors the reply into [ChatStore]
 * for immediate UI, and persists the canonical JSONL via [TavernCoreApi].
 *
 * MVP: 1v1 character chats for Chat Completion and first-batch Text Completion.
 * Groups, attachments, complex templates, and unsupported APIs fall back to [BridgeChatEngine].
 */
class NativeChatEngine(
    private val scope: CoroutineScope,
    private val store: ChatStore,
    private val bridge: ChatRuntimeBridgeActions,
    private val clientProvider: () -> TavernCoreApi,
    private val logger: NativeChatLogger = NativeChatLogger.Android,
) : ChatEngine {

    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var activeGenerationRoute = ActiveGenerationRoute.NONE

    override fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || store.isGenerating) return
        // The native engine only handles 1v1 Chat Completion text. Everything else
        // (group chats, attachments) stays on the WebView bridge, which writes +
        // clears the pending attachments and reuses the full ST semantics.
        if (store.mode == "group" || store.pendingAttachments.isNotEmpty()) {
            activeGenerationRoute = ActiveGenerationRoute.BRIDGE
            runBridgeWrite { sendMessage(message) }
            return
        }
        val avatar = store.avatarUrl
        val chatFile = store.chatFile
        if (avatar.isBlank() || chatFile.isBlank()) {
            store.recordCommandError("当前聊天未就绪，无法发送")
            return
        }
        launchGeneration {
            val client = clientProvider()
            val settings = client.getSettings()
            val mode = engineMode(settings, authorsNote = store.authorsNote)
            if (mode == NativeEngineMode.FALLBACK) {
                activeGenerationRoute = ActiveGenerationRoute.BRIDGE
                store.isGenerating = false
                runBridgeWrite { sendMessage(message) }
                return@launchGeneration
            }
            activeGenerationRoute = ActiveGenerationRoute.NATIVE
            val character = client.getCharacter(avatar)
            val userName = (settings["username"] as? String)?.takeIf { it.isNotBlank() } ?: "User"
            val date = sendDate()
            var optimisticUserId: Int? = null
            var optimisticAssistantId: Int? = null
            var persisted = false

            try {
                // Optimistic user + empty assistant placeholder for live streaming; once saved,
                // this native store plus JSONL is the current session source of truth.
                val userId = store.messages.size
                optimisticUserId = userId
                store.addMessage(message(userId, userName, message, isUser = true))
                val history = store.messages.filter { !it.isSystem }
                val payload = when (mode) {
                    NativeEngineMode.CHAT_COMPLETION -> buildPayload(client, character, userName, history, settings)
                    NativeEngineMode.TEXT_COMPLETION -> buildTextPayload(client, character, userName, history, settings)
                    NativeEngineMode.FALLBACK -> error("fallback mode should have returned before optimistic append")
                }
                val model = payload["model"] as? String ?: ""
                val aiId = store.messages.size
                optimisticAssistantId = aiId
                store.addMessage(message(aiId, character.name, "", isUser = false))

                val reply = when (mode) {
                    NativeEngineMode.CHAT_COMPLETION -> streamReply(client, payload, aiId)
                    NativeEngineMode.TEXT_COMPLETION -> streamTextReply(client, payload, aiId)
                    NativeEngineMode.FALLBACK -> ""
                }

                val chat = client.getChatJsonl(avatar, chatFile)
                ensureHeader(chat, userName, character.name, date)
                chat += userMessageMap(userName, message, date)
                if (reply.isNotBlank()) {
                    chat += aiMessageMap(
                        name = character.name,
                        text = reply,
                        date = date,
                        model = model,
                        api = if (mode == NativeEngineMode.TEXT_COMPLETION) "textgenerationwebui" else "openai",
                        type = payload["api_type"] as? String,
                    )
                }
                NativeChatRepository(dataSourceProvider = { TavernNativeChatDataSource(client) })
                    .save(avatar, chatFile, chat)
                persisted = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (!persisted) rollbackOptimisticMessages(store, optimisticUserId, optimisticAssistantId)
                throw e
            } catch (e: Exception) {
                if (!persisted) {
                    rollbackOptimisticMessages(store, optimisticUserId, optimisticAssistantId)
                    runCatching { bridge.reloadChat() }
                }
                throw e
            }
        }
    }

    override fun regenerate() {
        // Native regenerate would drop the existing swipe history (swipes/swipe_id),
        // which is real data loss. Until native swipe semantics are aligned, regenerate
        // stays on the WebView runtime which preserves swipes correctly.
        runBridgeWrite { regenerate() }
    }

    override fun continueGeneration() {
        // MVP: continue is not yet implemented natively; fall back to the runtime.
        runBridgeWrite { continueGeneration() }
    }

    override fun stop() {
        when (stopTargetForGeneration(store.mode, activeGenerationRoute)) {
            GenerationStopTarget.BRIDGE -> bridge.stopGeneration()
            GenerationStopTarget.NATIVE -> {
                // Ends the stream at the next token; the in-flight job then persists the partial.
                stopRequested = true
                store.isGenerating = false
            }
        }
    }

    /**
     * Assembles the generate payload: scans the character + chat lorebooks against
     * recent messages and folds the author's note in via [PromptBuilder].
     */
    private suspend fun buildPayload(
        client: TavernCoreApi,
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
    ): Map<String, Any?> {
        val context = buildPromptContext(client, character, history, settings)
        return PromptBuilder.build(
            character = character,
            userName = userName,
            history = history,
            settings = settings,
            personaDescription = context.personaDescription,
            worldInfoBefore = context.worldInfoBefore,
            worldInfoAfter = context.worldInfoAfter,
            authorsNote = store.authorsNote,
        )
    }

    private suspend fun buildTextPayload(
        client: TavernCoreApi,
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
    ): Map<String, Any?> {
        val context = buildPromptContext(client, character, history, settings)
        return when (val result = TextPromptBuilder.build(
            character = character,
            userName = userName,
            history = history,
            settings = settings,
            personaDescription = context.personaDescription,
            worldInfoBefore = context.worldInfoBefore,
            worldInfoAfter = context.worldInfoAfter,
            authorsNote = store.authorsNote,
        )) {
            is TextPromptBuildResult.Ready -> result.payload
            is TextPromptBuildResult.Unsupported -> throw IllegalStateException(result.reason)
        }
    }

    private suspend fun buildPromptContext(
        client: TavernCoreApi,
        character: CharacterDetail,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
    ): NativePromptContext {
        val entries = collectWorldInfo(client, character)
        val wi = WorldInfoEngine.scan(
            entries = entries,
            history = history.map { it.mes },
            recursive = true,
            defaultScanDepth = settings.intValue("world_info_depth", DEFAULT_WI_SCAN_DEPTH),
        )
        val personaDescription = (settings["power_user"] as? Map<*, *>)
            ?.get("persona_description") as? String ?: ""
        return NativePromptContext(
            personaDescription = personaDescription,
            worldInfoBefore = wi.before,
            worldInfoAfter = wi.after,
        )
    }

    /** Lorebooks to scan: the character's embedded world + the chat-bound world. */
    private suspend fun collectWorldInfo(client: TavernCoreApi, character: CharacterDetail): List<WorldInfoEntry> {
        val names = LinkedHashSet<String>()
        character.world.takeIf { it.isNotBlank() }?.let { names += it }
        store.worldInfoName.takeIf { it.isNotBlank() }?.let { names += it }
        if (names.isEmpty()) return emptyList()
        return names.flatMap { name ->
            runCatching { client.getWorldInfo(name).entries }.getOrElse { emptyList() }
        }
    }

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    /**
     * Streams the reply into the placeholder message [aiId], updating the store as
     * tokens arrive. Stops cleanly when [stopRequested] flips (the partial is kept).
     * Falls back to a non-streaming call if the stream yields nothing (e.g. an SSE
     * shape we don't parse), so unknown providers still work.
     */
    private suspend fun streamReply(client: TavernCoreApi, payload: Map<String, Any?>, aiId: Int): String {
        val source = payload["chat_completion_source"] as? String ?: ""
        val model = payload["model"] as? String ?: ""
        return streamGeneratedReply(
            aiId = aiId,
            source = source,
            model = model,
            stream = { client.generateChatCompletionStream(payload) },
            generate = { client.generateChatCompletion(payload) },
        )
    }

    private suspend fun streamTextReply(client: TavernCoreApi, payload: Map<String, Any?>, aiId: Int): String {
        val source = payload["api_type"] as? String ?: ""
        val model = payload["model"] as? String ?: ""
        return streamGeneratedReply(
            aiId = aiId,
            source = source,
            model = model,
            stream = { client.generateTextCompletionStream(payload) },
            generate = { client.generateTextCompletion(payload) },
        )
    }

    private suspend fun streamGeneratedReply(
        aiId: Int,
        source: String,
        model: String,
        stream: () -> Flow<String>,
        generate: suspend () -> String,
    ): String {
        logger.info(TAG, "stream source=$source model=$model")
        val acc = StringBuilder()
        fun apply() {
            val idx = store.messages.indexOfFirst { it.id == aiId }
            if (idx >= 0) store.messages[idx] = store.messages[idx].copy(mes = acc.toString())
        }
        try {
            var lastApply = 0L
            stream()
                .takeWhile { !stopRequested }
                .collect { delta ->
                    acc.append(delta)
                    // Throttle UI updates to ~60ms to avoid per-token recomposition storms.
                    val now = System.currentTimeMillis()
                    if (now - lastApply >= 60) {
                        lastApply = now
                        apply()
                    }
                }
            apply() // flush the final text
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (acc.isNotEmpty()) {
                apply()
                throw IllegalStateException("源=$source 模型=${model.ifBlank { "(空!)" }}：${e.message}", e)
            }
            logger.warn(TAG, "stream failed, falling back to non-stream: ${e.message}")
        }
        if (acc.isEmpty() && !stopRequested) {
            val reply = runGenerate(source, model, generate)
            acc.append(reply)
            apply()
        }
        return acc.toString()
    }

    /** Non-streaming generate; used as the fallback for unsupported SSE shapes. */
    private suspend fun runGenerate(source: String, model: String, generate: suspend () -> String): String {
        return try {
            generate()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("源=$source 模型=${model.ifBlank { "(空!)" }}：${e.message}", e)
        }
    }

    private fun launchGeneration(block: suspend () -> Unit) {
        job = scope.launch {
            stopRequested = false
            store.isGenerating = true
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "生成失败"
                logger.warn(TAG, "native generation failed: $msg", e)
                store.runtimeError = msg
                store.pushToast("error", "原生生成失败", msg)
            } finally {
                store.isGenerating = false
                if (activeGenerationRoute == ActiveGenerationRoute.NATIVE) {
                    activeGenerationRoute = ActiveGenerationRoute.NONE
                }
            }
        }
    }

    private fun message(id: Int, name: String, text: String, isUser: Boolean): ChatMessage =
        ChatMessage(
            id = id,
            name = name,
            mes = text,
            isUser = isUser,
            isSystem = false,
            sendDate = sendDate(),
            swipeId = 0,
            swipes = if (isUser) emptyList() else listOf(text),
            extra = JSONObject()
        )

    private fun runBridgeWrite(block: ChatRuntimeBridgeActions.() -> Unit) {
        bridge.reloadChat()
        bridge.block()
    }

    private fun userMessageMap(name: String, text: String, date: String): Map<String, Any?> =
        linkedMapOf(
            "name" to name,
            "is_user" to true,
            "is_system" to false,
            "send_date" to date,
            "mes" to text,
            "extra" to emptyMap<String, Any?>()
        )

    private fun aiMessageMap(
        name: String,
        text: String,
        date: String,
        model: String,
        api: String,
        type: String?,
    ): Map<String, Any?> {
        val extra = linkedMapOf<String, Any?>("api" to api, "model" to model)
        type?.takeIf { it.isNotBlank() }?.let { extra["type"] = it }
        return linkedMapOf(
            "name" to name,
            "is_user" to false,
            "is_system" to false,
            "send_date" to date,
            "mes" to text,
            "swipes" to listOf(text),
            "swipe_id" to 0,
            "extra" to extra
        )
    }

    private fun ensureHeader(chat: MutableList<Any?>, userName: String, charName: String, date: String) {
        if (chat.isNotEmpty()) return
        chat += linkedMapOf<String, Any?>(
            "user_name" to userName,
            "character_name" to charName,
            "create_date" to date,
            "chat_metadata" to linkedMapOf<String, Any?>("integrity" to UUID.randomUUID().toString())
        )
    }

    private fun sendDate(): String =
        SimpleDateFormat("MMMM d, yyyy h:mma", Locale.ENGLISH).format(Date()).lowercase(Locale.ENGLISH)

    private companion object {
        const val TAG = "NativeChatEngine"
        const val DEFAULT_WI_SCAN_DEPTH = 2
    }
}

private data class NativePromptContext(
    val personaDescription: String,
    val worldInfoBefore: String,
    val worldInfoAfter: String,
)

fun rollbackOptimisticMessages(
    store: ChatStore,
    userMessageId: Int?,
    assistantMessageId: Int?,
) {
    assistantMessageId?.let(store::deleteMessage)
    userMessageId?.let(store::deleteMessage)
}
