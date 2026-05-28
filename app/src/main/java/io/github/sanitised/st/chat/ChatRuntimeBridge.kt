package io.github.sanitised.st.chat

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject

class ChatRuntimeBridge(
    private val store: ChatStore
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
        store.markRuntimeUnavailable()
    }

    fun detach(webView: WebView? = null) {
        if (webView == null || this.webView == webView) {
            this.webView = null
            store.markRuntimeUnavailable()
        }
    }

    fun markRuntimeLoading(message: String? = null) {
        store.markRuntimeUnavailable(message)
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
                requestSnapshot()
            }
            is BridgeEvent.RuntimeError -> {
                store.markRuntimeError(event.message)
            }
            is BridgeEvent.ChatLoaded -> {
                store.applySnapshot(event.snapshot)
            }
            is BridgeEvent.ChatChanged -> {
                requestSnapshot()
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
            is BridgeEvent.CommandResult -> {}
            is BridgeEvent.CommandError -> {
                store.recordCommandError(event.message)
                Log.w(TAG, "Command error [${event.commandId}]: ${event.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        dispatch(BridgeMessage(kind = "command", name = "chat.send", payload = JSONObject().put("text", text)))
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

    fun newChat() {
        dispatch(BridgeMessage(kind = "command", name = "chat.new"))
    }

    fun reloadChat() {
        dispatch(BridgeMessage(kind = "command", name = "chat.reload"))
    }

    fun requestSnapshot() {
        dispatch(BridgeMessage(kind = "command", name = "runtime.getSnapshot"))
    }

    private fun dispatch(message: BridgeMessage) {
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
            wv.evaluateJavascript(js) { result ->
                if (result != "true") {
                    store.markRuntimeUnavailable("聊天运行时正在重新连接")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ChatRuntimeBridge"
    }
}
