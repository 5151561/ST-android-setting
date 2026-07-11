package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.chat.ItemizedPromptStore
import io.github.sanitised.st.chat.NativeChatRepository
import io.github.sanitised.st.chat.NativeChatJsonOps
import io.github.sanitised.st.chat.TavernNativeChatDataSource
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.buildNativeCharacterChatSnapshot
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.TextPromptBuildResult
import io.github.sanitised.st.chat.prompt.TextPromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NativeEngineMode {
    CHAT_COMPLETION,
    TEXT_COMPLETION,
    UNSUPPORTED,
}

enum class ActiveGenerationRoute {
    NONE,
    NATIVE,
}

enum class GenerationStopTarget {
    NATIVE,
}

fun stopTargetForGeneration(mode: String, route: ActiveGenerationRoute): GenerationStopTarget =
    GenerationStopTarget.NATIVE

fun engineMode(settings: Map<String, Any?>, authorsNote: String = ""): NativeEngineMode =
    NativeGenerationRouter.route(settings, authorsNote).mode

/**
 * Native generation engine: assembles the prompt on-device ([PromptBuilder] / [TextPromptBuilder]),
 * calls the backend generate endpoint directly, mirrors the reply into [ChatStore]
 * for immediate UI, and persists the canonical JSONL via [TavernCoreApi].
 *
 * App chat generation now has a single native route. Unsupported providers fail
 * explicitly here instead of falling back to the removed WebView runtime.
 */
class NativeChatEngine(
    private val scope: CoroutineScope,
    private val store: ChatStore,
    private val clientProvider: () -> TavernCoreApi,
    private val logger: NativeChatLogger = NativeChatLogger.Android,
    private val itemizedPromptStore: ItemizedPromptStore = ItemizedPromptStore.Global,
) : ChatEngine {

    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var activeGenerationRoute = ActiveGenerationRoute.NONE

    override fun send(text: String) {
        val message = text.trim()
        val pendingAttachments = store.pendingAttachments.toList()
        if ((message.isEmpty() && pendingAttachments.isEmpty()) || store.isGenerating) return
        if (store.mode == "group") {
            store.recordCommandError("群聊请使用原生群聊详情页生成")
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
            val route = NativeGenerationRouter.route(settings, authorsNote = store.authorsNote)
            val mode = route.mode
            if (mode == NativeEngineMode.UNSUPPORTED) {
                store.isGenerating = false
                store.recordCommandError("当前 provider 尚未接入原生生成")
                store.pushToast("error", "原生生成不可用", "当前 provider 尚未接入原生生成")
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
                store.addMessage(message(userId, userName, message, isUser = true, attachments = pendingAttachments))
                if (pendingAttachments.isNotEmpty()) store.clearPendingAttachments()
                val history = store.messages.filter { !it.isSystem }
                val payload = when (mode) {
                    NativeEngineMode.CHAT_COMPLETION -> buildPayload(client, character, userName, history, route.settings)
                    NativeEngineMode.TEXT_COMPLETION -> buildTextPayload(client, character, userName, history, route.settings)
                    NativeEngineMode.UNSUPPORTED -> error("unsupported mode should have returned before optimistic append")
                }
                val model = payload["model"] as? String ?: ""
                val aiId = store.messages.size
                optimisticAssistantId = aiId
                store.addMessage(message(aiId, character.name, "", isUser = false))
                itemizedPromptStore.record(aiId, payload, route)

                val reply = when (mode) {
                    NativeEngineMode.CHAT_COMPLETION -> streamReply(client, payload, aiId)
                    NativeEngineMode.TEXT_COMPLETION -> streamTextReply(client, payload, aiId)
                    NativeEngineMode.UNSUPPORTED -> ""
                }

                val chat = client.getChatJsonl(avatar, chatFile)
                ensureHeader(chat, userName, character.name, date)
                chat += userMessageMap(userName, message, date, pendingAttachments)
                if (reply.isNotBlank()) {
                    chat += aiMessageMap(
                        name = character.name,
                        text = reply,
                        date = date,
                        model = model,
                        api = route.api,
                        type = route.source,
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
                }
                throw e
            }
        }
    }

    override fun regenerate() {
        mutateLastAssistantWithGeneration(
            includeCurrentAssistantInPrompt = false,
            prefix = "",
        ) { chat, messageId, generated, _ ->
            NativeChatJsonOps.createSwipe(chat, messageId, generated)
        }
    }

    override fun continueGeneration() {
        val current = store.messages.lastOrNull { !it.isUser && !it.isSystem }?.mes.orEmpty()
        mutateLastAssistantWithGeneration(
            includeCurrentAssistantInPrompt = true,
            prefix = current,
        ) { chat, messageId, generated, original ->
            NativeChatJsonOps.editMessage(chat, messageId, original + generated)
        }
    }

    override fun stop() {
        when (stopTargetForGeneration(store.mode, activeGenerationRoute)) {
            GenerationStopTarget.NATIVE -> {
                stopRequested = true
                job?.cancel("Native generation stopped")
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

    private fun mutateLastAssistantWithGeneration(
        includeCurrentAssistantInPrompt: Boolean,
        prefix: String,
        applyGenerated: (MutableList<Any?>, messageId: Int, generated: String, original: String) -> Unit,
    ) {
        if (store.isGenerating) return
        if (store.mode == "group") {
            store.recordCommandError("群聊请使用原生群聊详情页生成")
            return
        }
        val avatar = store.avatarUrl
        val chatFile = store.chatFile
        if (avatar.isBlank() || chatFile.isBlank()) {
            store.recordCommandError("当前聊天未就绪，无法生成")
            return
        }
        val target = store.messages.lastOrNull { !it.isUser && !it.isSystem }
        if (target == null) {
            store.recordCommandError("没有可重写的 AI 消息")
            return
        }

        launchGeneration {
            val client = clientProvider()
            val settings = client.getSettings()
            val route = NativeGenerationRouter.route(settings, authorsNote = store.authorsNote)
            val mode = route.mode
            if (mode == NativeEngineMode.UNSUPPORTED) {
                store.isGenerating = false
                store.recordCommandError("当前 provider 尚未接入原生生成")
                store.pushToast("error", "原生生成不可用", "当前 provider 尚未接入原生生成")
                return@launchGeneration
            }
            activeGenerationRoute = ActiveGenerationRoute.NATIVE
            val character = client.getCharacter(avatar)
            val userName = (settings["username"] as? String)?.takeIf { it.isNotBlank() } ?: "User"
            val history = store.messages
                .filter { !it.isSystem }
                .filter { includeCurrentAssistantInPrompt || it.id != target.id }
            val payload = when (mode) {
                NativeEngineMode.CHAT_COMPLETION -> buildPayload(client, character, userName, history, route.settings)
                NativeEngineMode.TEXT_COMPLETION -> buildTextPayload(client, character, userName, history, route.settings)
                NativeEngineMode.UNSUPPORTED -> error("unsupported mode should have returned before generation")
            }
            itemizedPromptStore.record(target.id, payload, route)
            val generated = when (mode) {
                NativeEngineMode.CHAT_COMPLETION -> streamReply(client, payload, target.id, prefix)
                NativeEngineMode.TEXT_COMPLETION -> streamTextReply(client, payload, target.id, prefix)
                NativeEngineMode.UNSUPPORTED -> ""
            }
            if (generated.isBlank()) return@launchGeneration

            val chat = client.getChatJsonl(avatar, chatFile)
            applyGenerated(chat, target.id, generated, target.mes)
            NativeChatRepository(dataSourceProvider = { TavernNativeChatDataSource(client) })
                .save(avatar, chatFile, chat)
            store.applySnapshot(
                buildNativeCharacterChatSnapshot(
                    avatar = avatar,
                    character = character,
                    chatFile = chatFile,
                    rawChat = chat,
                ),
                markRuntimeReady = false,
            )
        }
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
    private suspend fun streamReply(
        client: TavernCoreApi,
        payload: Map<String, Any?>,
        aiId: Int,
        prefix: String = "",
    ): String {
        val source = payload["chat_completion_source"] as? String ?: ""
        val model = payload["model"] as? String ?: ""
        return streamGeneratedReply(
            aiId = aiId,
            source = source,
            model = model,
            prefix = prefix,
            stream = { client.generateChatCompletionStream(payload) },
            generate = { client.generateChatCompletion(payload) },
        )
    }

    private suspend fun streamTextReply(
        client: TavernCoreApi,
        payload: Map<String, Any?>,
        aiId: Int,
        prefix: String = "",
    ): String {
        val source = payload["api_type"] as? String ?: ""
        val model = payload["model"] as? String ?: ""
        return streamGeneratedReply(
            aiId = aiId,
            source = source,
            model = model,
            prefix = prefix,
            stream = { client.generateTextCompletionStream(payload) },
            generate = { client.generateTextCompletion(payload) },
        )
    }

    private suspend fun streamGeneratedReply(
        aiId: Int,
        source: String,
        model: String,
        prefix: String = "",
        stream: () -> Flow<String>,
        generate: suspend () -> String,
    ): String {
        logger.info(TAG, "stream source=$source model=$model")
        val acc = StringBuilder()
        fun apply() {
            val idx = store.messages.indexOfFirst { it.id == aiId }
            if (idx >= 0) store.messages[idx] = store.messages[idx].copy(mes = prefix + acc.toString())
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
        val launchedJob = scope.launch(start = CoroutineStart.LAZY) {
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
                if (job == coroutineContext[Job]) {
                    store.isGenerating = false
                    activeGenerationRoute = ActiveGenerationRoute.NONE
                    job = null
                }
            }
        }
        job = launchedJob
        launchedJob.start()
    }

    private fun message(
        id: Int,
        name: String,
        text: String,
        isUser: Boolean,
        attachments: List<io.github.sanitised.st.chat.PendingAttachment> = emptyList(),
    ): ChatMessage =
        ChatMessage(
            id = id,
            name = name,
            mes = text,
            isUser = isUser,
            isSystem = false,
            sendDate = sendDate(),
            swipeId = 0,
            swipes = if (isUser) emptyList() else listOf(text),
            extra = attachmentsExtraJson(attachments)
        )

    private fun userMessageMap(
        name: String,
        text: String,
        date: String,
        attachments: List<io.github.sanitised.st.chat.PendingAttachment>,
    ): Map<String, Any?> =
        linkedMapOf(
            "name" to name,
            "is_user" to true,
            "is_system" to false,
            "send_date" to date,
            "mes" to text,
            "extra" to attachmentsExtraMap(attachments)
        )

    private fun attachmentsExtraJson(
        attachments: List<io.github.sanitised.st.chat.PendingAttachment>
    ): JSONObject {
        val extra = JSONObject()
        val media = JSONArray()
        val files = JSONArray()
        attachments.forEach { attachment ->
            val item = JSONObject()
                .put("url", attachment.url)
                .put("path", attachment.url)
                .put("name", attachment.name)
                .put("title", attachment.name)
                .put("size", attachment.size)
            if (attachment.isMedia) {
                media.put(item.put("type", "image"))
            } else {
                files.put(item)
            }
        }
        if (media.length() > 0) extra.put("media", media)
        if (files.length() > 0) extra.put("files", files)
        return extra
    }

    private fun attachmentsExtraMap(
        attachments: List<io.github.sanitised.st.chat.PendingAttachment>
    ): Map<String, Any?> {
        if (attachments.isEmpty()) return emptyMap()
        val media = mutableListOf<Map<String, Any?>>()
        val files = mutableListOf<Map<String, Any?>>()
        attachments.forEach { attachment ->
            val item = linkedMapOf<String, Any?>(
                "url" to attachment.url,
                "path" to attachment.url,
                "name" to attachment.name,
                "title" to attachment.name,
                "size" to attachment.size,
            )
            if (attachment.isMedia) {
                item["type"] = "image"
                media += item
            } else {
                files += item
            }
        }
        return linkedMapOf<String, Any?>().apply {
            if (media.isNotEmpty()) put("media", media)
            if (files.isNotEmpty()) put("files", files)
        }
    }

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
