package io.github.sanitised.st.chat

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class ChatRuntimeBridge(
    private val store: ChatStore
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val pendingCommands = mutableMapOf<String, PendingCommand>()

    private class PendingCommand(
        val name: String,
        val timeoutRunnable: Runnable
    )

    fun attach(webView: WebView) {
        this.webView = webView
        store.markRuntimeUnavailable()
    }

    fun detach(webView: WebView? = null) {
        if (webView == null || this.webView == webView) {
            this.webView = null
            store.markRuntimeUnavailable()
            clearPendingCommands()
        }
    }

    fun markRuntimeLoading(message: String? = null) {
        store.markRuntimeUnavailable(message)
        clearPendingCommands()
    }

    fun onEvent(json: String) {
        val event = BridgeEvent.parse(json)
        if (event == null) {
            Log.w(TAG, "Unknown bridge event: $json")
            return
        }
        mainHandler.post { handleEvent(event) }
    }

    private fun handleEvent(event: BridgeEvent) {
        when (event) {
            is BridgeEvent.RuntimeReady -> {
                store.markRuntimeReady()
                connect(auto = true)
                loadQuickReplies()
                loadExtensions()
            }
            is BridgeEvent.RuntimeError -> {
                store.markRuntimeError(event.message)
            }
            is BridgeEvent.ChatLoaded -> {
                store.applySnapshot(event.snapshot)
            }
            is BridgeEvent.ChatChanged -> {
                requestSnapshot()
                loadQuickReplies()
            }
            is BridgeEvent.MessageAdded -> {
                store.addMessage(event.message)
            }
            is BridgeEvent.MessageUpdated -> {
                store.updateMessage(event.message)
            }
            is BridgeEvent.MessageDeleted -> {
                store.deleteMessage(event.messageId)
            }
            is BridgeEvent.GenerationStarted -> {
                store.isGenerating = true
            }
            is BridgeEvent.GenerationEnded -> {
                store.isGenerating = false
                requestSnapshot()
            }
            is BridgeEvent.GenerationStopped -> {
                store.isGenerating = false
                requestSnapshot()
            }
            is BridgeEvent.GenerationError -> {
                store.isGenerating = false
                store.runtimeError = event.message
            }
            is BridgeEvent.StreamToken -> {
                val idx = store.messages.indexOfFirst { it.id == event.messageId }
                if (idx >= 0) {
                    store.messages[idx] = store.messages[idx].copy(mes = event.fullText)
                }
            }
            is BridgeEvent.SaveError -> {
                store.recordSaveError(event.message)
                Log.w(TAG, "Save error: ${event.message}")
            }
            is BridgeEvent.Toast -> {
                store.pushToast(event.type, event.title, event.message)
            }
            is BridgeEvent.CommandResult -> {
                val pending = completePendingCommand(event.commandId)
                when (pending?.name) {
                    "quickReply.list" -> applyQuickReplyResult(event.payload)
                    "extensions.list" -> applyExtensionsResult(event.payload)
                    "itemizedPrompt.get" -> applyItemizedPromptResult(event.payload)
                    "dataBank.list" -> store.applyDataBank(DataBankAttachments.fromJson(event.payload))
                }
            }
            is BridgeEvent.CommandError -> {
                val pending = completePendingCommand(event.commandId)
                when (pending?.name) {
                    "itemizedPrompt.get" -> store.recordItemizedPromptError(event.message)
                    "dataBank.list" -> store.clearDataBank()
                }
                store.recordCommandError(event.message)
                Log.w(TAG, "Command error [${event.commandId}]: ${event.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        val payload = JSONObject().put("text", text)
        if (store.pendingAttachments.isNotEmpty()) {
            payload.put(
                "attachments",
                JSONArray().apply {
                    store.pendingAttachments.forEach { attachment ->
                        put(
                            JSONObject()
                                .put("url", attachment.url)
                                .put("name", attachment.name)
                                .put("size", attachment.size)
                                .put("isMedia", attachment.isMedia)
                        )
                    }
                }
            )
        }
        dispatch(BridgeMessage(kind = "command", name = "chat.send", payload = payload))
        store.clearPendingAttachments()
    }

    fun stopGeneration() {
        dispatch(BridgeMessage(kind = "command", name = "generation.stop"))
    }

    fun regenerate() {
        dispatch(BridgeMessage(kind = "command", name = "generation.regenerate"))
    }

    fun continueGeneration() {
        dispatch(BridgeMessage(kind = "command", name = "generation.continue"))
    }

    fun openCharacter(avatarUrl: String, chatFile: String? = null) {
        val payload = JSONObject().put("avatarUrl", avatarUrl)
        if (chatFile != null) payload.put("chatFile", chatFile)
        dispatch(BridgeMessage(kind = "command", name = "chat.openCharacter", payload = payload))
    }

    fun openGroup(groupId: String, chatId: String? = null) {
        val payload = JSONObject().put("groupId", groupId)
        if (chatId != null) payload.put("chatId", chatId)
        dispatch(BridgeMessage(kind = "command", name = "chat.openGroup", payload = payload))
    }

    fun swipePrevious(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.swipePrevious",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun swipeNext(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.swipeNext",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun editMessage(messageId: Int, newText: String) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.edit",
                payload = JSONObject().put("id", messageId).put("text", newText)
            )
        )
    }

    fun deleteMessageFromChat(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.delete",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun hideMessage(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.hide",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun unhideMessage(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "message.unhide",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun setAuthorsNote(text: String) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "authorsNote.set",
                payload = JSONObject().put("text", text)
            )
        )
    }

    fun setCfg(scale: Float, negativePrompt: String, positivePrompt: String) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "cfg.set",
                payload = JSONObject()
                    .put("scale", scale.toDouble())
                    .put("negativePrompt", negativePrompt)
                    .put("positivePrompt", positivePrompt)
            )
        )
    }

    fun newChat() {
        dispatch(BridgeMessage(kind = "command", name = "chat.new"))
    }

    fun reloadChat() {
        dispatch(BridgeMessage(kind = "command", name = "chat.reload"))
    }

    /**
     * Drives the runtime ST frontend to connect to the configured model API so
     * online_status leaves 'no_connection'. The native settings UI only writes
     * the API config + secret to disk; it never triggers the running frontend's
     * connect, which is why generation otherwise fails with "未连接模型 API".
     *
     * @param auto best-effort proactive connect (no error surfaced on failure).
     */
    /**
     * Re-reads settings.json into the running runtime frontend (and reconnects if
     * needed). Call after the native settings UI changes the API/model so the
     * persistent runtime stops generating with stale in-memory settings.
     */
    fun reloadSettings() {
        dispatch(BridgeMessage(kind = "command", name = "runtime.reloadSettings"))
    }

    fun connect(auto: Boolean = false) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "runtime.connect",
                payload = JSONObject().put("auto", auto)
            ),
            trackTimeout = !auto
        )
    }

    fun loadQuickReplies() {
        dispatch(BridgeMessage(kind = "command", name = "quickReply.list"), silentTimeout = true)
    }

    fun loadExtensions() {
        dispatch(BridgeMessage(kind = "command", name = "extensions.list"), silentTimeout = true)
    }

    fun loadItemizedPrompt(messageId: Int) {
        store.beginItemizedPromptLoad()
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "itemizedPrompt.get",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun loadDataBank() {
        store.beginDataBankLoad()
        dispatch(BridgeMessage(kind = "command", name = "dataBank.list"))
    }

    fun executeQuickReply(setName: String, label: String) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "quickReply.execute",
                payload = JSONObject().put("setName", setName).put("label", label)
            )
        )
    }

    fun createCheckpoint(messageId: Int, name: String?) {
        val payload = JSONObject().put("id", messageId)
        if (!name.isNullOrBlank()) payload.put("name", name)
        dispatch(BridgeMessage(kind = "command", name = "chat.createCheckpoint", payload = payload))
    }

    fun createBranch(messageId: Int) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "chat.createBranch",
                payload = JSONObject().put("id", messageId)
            )
        )
    }

    fun openCheckpoint(name: String) {
        dispatch(
            BridgeMessage(
                kind = "command",
                name = "chat.openCheckpoint",
                payload = JSONObject().put("name", name)
            )
        )
    }

    fun requestSnapshot() {
        dispatch(BridgeMessage(kind = "command", name = "runtime.getSnapshot"), trackTimeout = false)
    }

    fun dismissSaveError() {
        store.clearSaveError()
    }

    fun retrySave() {
        dispatch(BridgeMessage(kind = "command", name = "runtime.save"))
    }

    private fun dispatch(
        message: BridgeMessage,
        trackTimeout: Boolean = true,
        silentTimeout: Boolean = false
    ) {
        mainHandler.post {
            val wv = webView
            if (wv == null) {
                store.recordCommandError("聊天运行时还没准备好")
                return@post
            }
            val js = """
                (function() {
                  const runtime = window.STAndroidChatRuntime;
                  if (!runtime || typeof runtime.dispatch !== 'function') return false;
                  runtime.dispatch(${JSONObject.quote(message.toJson())});
                  return true;
                })();
            """.trimIndent()
            if (trackTimeout) {
                registerTimeout(message.id, message.name, silentTimeout)
            }
            wv.evaluateJavascript(js) { result ->
                if (result != "true") {
                    if (trackTimeout) {
                        completePendingCommand(message.id)
                    }
                    store.markRuntimeUnavailable("聊天运行时正在重新连接")
                    return@evaluateJavascript
                }
            }
        }
    }

    private fun registerTimeout(commandId: String, commandName: String, silent: Boolean = false) {
        val timeoutMs = when {
            commandName.startsWith("chat.open") -> OPEN_TIMEOUT_MS
            commandName.startsWith("generation.") -> GENERATION_TIMEOUT_MS
            else -> DEFAULT_TIMEOUT_MS
        }
        val runnable = Runnable {
            pendingCommands.remove(commandId)
            val label = COMMAND_LABELS[commandName] ?: commandName
            // Background, auto-fired loads (quick replies, extensions) are
            // best-effort and retried on the next chat change — a transient
            // timeout should be logged, not surfaced as a runtime error banner.
            if (silent) {
                Log.w(TAG, "Background command timeout [$commandId] $commandName after ${timeoutMs}ms (silent)")
            } else {
                store.recordCommandError("$label 超时，运行时可能无响应")
                Log.w(TAG, "Command timeout [$commandId] $commandName after ${timeoutMs}ms")
            }
        }
        pendingCommands[commandId] = PendingCommand(commandName, runnable)
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun completePendingCommand(commandId: String): PendingCommand? {
        val pending = pendingCommands.remove(commandId) ?: return null
        mainHandler.removeCallbacks(pending.timeoutRunnable)
        return pending
    }

    private fun applyQuickReplyResult(payload: JSONObject) {
        val items = payload.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val label = obj.optString("label")
                if (label.isBlank()) return@mapNotNull null
                QuickReplyItem(
                    setName = obj.optString("setName"),
                    label = label,
                    icon = obj.optString("icon"),
                    message = obj.optString("message")
                )
            }
        } ?: emptyList()
        store.setQuickReplies(items)
    }

    private fun applyExtensionsResult(payload: JSONObject) {
        val names = payload.optJSONArray("names")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        store.setLoadedExtensions(names)
    }

    private fun applyItemizedPromptResult(payload: JSONObject) {
        if (!payload.optBoolean("available", false)) {
            store.applyItemizedPrompt(null)
            return
        }
        store.applyItemizedPrompt(ItemizedPrompt.fromJson(payload))
    }

    private fun clearPendingCommands() {
        pendingCommands.values.forEach { mainHandler.removeCallbacks(it.timeoutRunnable) }
        pendingCommands.clear()
    }

    companion object {
        private const val TAG = "ChatRuntimeBridge"
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val OPEN_TIMEOUT_MS = 30_000L
        private const val GENERATION_TIMEOUT_MS = 60_000L

        private val COMMAND_LABELS = mapOf(
            "chat.send" to "发送消息",
            "chat.openCharacter" to "打开角色",
            "chat.openGroup" to "打开群聊",
            "runtime.save" to "保存聊天",
            "runtime.connect" to "连接模型 API",
            "runtime.reloadSettings" to "重载运行时设置",
            "chat.new" to "新建聊天",
            "chat.reload" to "重载聊天",
            "generation.stop" to "停止生成",
            "generation.regenerate" to "重写",
            "generation.continue" to "继续生成",
            "message.edit" to "编辑消息",
            "message.delete" to "删除消息",
            "message.swipePrevious" to "切换 swipe",
            "message.swipeNext" to "切换 swipe",
            "message.hide" to "隐藏消息",
            "message.unhide" to "取消隐藏消息",
            "authorsNote.set" to "设置作者注",
            "cfg.set" to "设置 CFG",
            "quickReply.list" to "加载快捷回复",
            "quickReply.execute" to "执行快捷回复",
            "extensions.list" to "加载扩展列表",
            "itemizedPrompt.get" to "加载提示词分析",
            "dataBank.list" to "加载数据银行",
            "chat.createCheckpoint" to "创建存档点",
            "chat.createBranch" to "创建分支",
            "chat.openCheckpoint" to "打开存档点"
        )
    }
}
