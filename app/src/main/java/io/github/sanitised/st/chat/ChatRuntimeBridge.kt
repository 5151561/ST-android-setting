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
    }

    fun detach() {
        webView = null
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
                store.runtimeState = RuntimeState.READY
                store.runtimeError = null
                requestSnapshot()
            }
            is BridgeEvent.RuntimeError -> {
                store.runtimeState = RuntimeState.ERROR
                store.runtimeError = event.message
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
        val wv = webView ?: return
        val escaped = message.toJson().replace("\\", "\\\\").replace("'", "\\'")
        val js = "window.STAndroidChatRuntime && window.STAndroidChatRuntime.dispatch('$escaped');"
        mainHandler.post {
            wv.evaluateJavascript(js, null)
        }
    }

    companion object {
        private const val TAG = "ChatRuntimeBridge"
    }
}
