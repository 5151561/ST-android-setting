package io.github.sanitised.st.chat.engine

import android.util.Log
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.chat.ChatRuntimeBridge
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Native Chat Completion engine: assembles the prompt on-device ([PromptBuilder]),
 * calls the backend generate endpoint directly, mirrors the reply into [ChatStore]
 * for immediate UI, persists the canonical JSONL via [TavernCoreApi], then asks the
 * hidden runtime to reload from disk so it stays the single source of truth.
 *
 * MVP: 1v1 character chats, non-streaming, Chat Completion sources only. Group chats
 * and advanced semantics still go through [BridgeChatEngine].
 */
class NativeChatEngine(
    private val scope: CoroutineScope,
    private val store: ChatStore,
    private val bridge: ChatRuntimeBridge,
    private val clientProvider: () -> TavernCoreApi,
) : ChatEngine {

    private var job: Job? = null

    override fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || store.isGenerating) return
        // Group chats keep the WebView fallback (no single character card to assemble).
        if (store.mode == "group") {
            bridge.sendMessage(message)
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
            val character = client.getCharacter(avatar)
            val userName = (settings["username"] as? String)?.takeIf { it.isNotBlank() } ?: "User"
            val date = sendDate()

            // Optimistic user message for instant UI; replaced by the canonical
            // snapshot after the runtime reloads from disk below.
            store.addMessage(message(store.messages.size, userName, message, isUser = true))

            val history = store.messages.filter { !it.isSystem }
            val payload = buildPayload(client, character, userName, history, settings)
            val model = payload["model"] as? String ?: ""
            val reply = runGenerate(client, payload)

            store.addMessage(message(store.messages.size, character.name, reply, isUser = false))

            val chat = client.getChatJsonl(avatar, chatFile)
            ensureHeader(chat, userName, character.name, date)
            chat += userMessageMap(userName, message, date)
            chat += aiMessageMap(character.name, reply, date, model)
            client.saveChatJsonl(avatar, chatFile, chat)
            bridge.reloadChat()
        }
    }

    override fun regenerate() {
        if (store.isGenerating) return
        if (store.mode == "group") {
            bridge.regenerate()
            return
        }
        val avatar = store.avatarUrl
        val chatFile = store.chatFile
        val last = store.messages.lastOrNull()
        if (avatar.isBlank() || chatFile.isBlank() || last == null || last.isUser) {
            store.recordCommandError("没有可重写的回复")
            return
        }
        launchGeneration {
            val client = clientProvider()
            val settings = client.getSettings()
            val character = client.getCharacter(avatar)
            val userName = (settings["username"] as? String)?.takeIf { it.isNotBlank() } ?: "User"
            val date = sendDate()

            // Drop the last assistant reply from the visible list and regenerate
            // from the remaining history (which now ends with the user turn).
            store.deleteMessage(last.id)
            val history = store.messages.filter { !it.isSystem }
            val payload = buildPayload(client, character, userName, history, settings)
            val model = payload["model"] as? String ?: ""
            val reply = runGenerate(client, payload)

            store.addMessage(message(store.messages.size, character.name, reply, isUser = false))

            val chat = client.getChatJsonl(avatar, chatFile)
            if (chat.size > 1 && (chat.last() as? Map<*, *>)?.get("is_user") == false) {
                chat.removeAt(chat.size - 1)
            }
            chat += aiMessageMap(character.name, reply, date, model)
            client.saveChatJsonl(avatar, chatFile, chat)
            bridge.reloadChat()
        }
    }

    override fun continueGeneration() {
        // MVP: continue is not yet implemented natively; fall back to the runtime.
        bridge.continueGeneration()
    }

    override fun stop() {
        job?.cancel()
        store.isGenerating = false
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
        val entries = collectWorldInfo(client, character)
        val scanText = history.takeLast(WI_SCAN_DEPTH).joinToString("\n") { it.mes }
        val wi = WorldInfoScanner.scan(entries, scanText)
        return PromptBuilder.build(
            character = character,
            userName = userName,
            history = history,
            settings = settings,
            worldInfoBefore = wi.before,
            worldInfoAfter = wi.after,
            authorsNote = store.authorsNote,
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

    /**
     * Calls the backend generate, surfacing the resolved source/model so it is
     * obvious which model is actually used (diagnostics for the native path).
     */
    private suspend fun runGenerate(client: TavernCoreApi, payload: Map<String, Any?>): String {
        val source = payload["chat_completion_source"] as? String ?: ""
        val model = payload["model"] as? String ?: ""
        val msgCount = (payload["messages"] as? List<*>)?.size ?: 0
        // Silent breadcrumb for logcat; the failure path below surfaces a toast.
        Log.i(TAG, "generate source=$source model=$model msgs=$msgCount")
        return try {
            client.generateChatCompletion(payload)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("源=$source 模型=${model.ifBlank { "(空!)" }}：${e.message}", e)
        }
    }

    private fun launchGeneration(block: suspend () -> Unit) {
        job = scope.launch {
            store.isGenerating = true
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "生成失败"
                Log.w(TAG, "native generation failed: $msg", e)
                store.runtimeError = msg
                store.pushToast("error", "原生生成失败", msg)
            } finally {
                store.isGenerating = false
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

    private fun userMessageMap(name: String, text: String, date: String): Map<String, Any?> =
        linkedMapOf(
            "name" to name,
            "is_user" to true,
            "is_system" to false,
            "send_date" to date,
            "mes" to text,
            "extra" to emptyMap<String, Any?>()
        )

    private fun aiMessageMap(name: String, text: String, date: String, model: String): Map<String, Any?> =
        linkedMapOf(
            "name" to name,
            "is_user" to false,
            "is_system" to false,
            "send_date" to date,
            "mes" to text,
            "swipes" to listOf(text),
            "swipe_id" to 0,
            "extra" to linkedMapOf<String, Any?>("api" to "openai", "model" to model)
        )

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
        const val WI_SCAN_DEPTH = 3
    }
}
