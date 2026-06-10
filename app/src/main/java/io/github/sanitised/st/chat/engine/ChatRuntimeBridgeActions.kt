package io.github.sanitised.st.chat.engine

import android.util.Log
import io.github.sanitised.st.chat.ChatRuntimeBridge

interface ChatRuntimeBridgeActions {
    fun sendMessage(text: String)
    fun stopGeneration()
    fun regenerate()
    fun continueGeneration()
    fun reloadChat()
}

class DefaultChatRuntimeBridgeActions(
    private val bridge: ChatRuntimeBridge,
) : ChatRuntimeBridgeActions {
    override fun sendMessage(text: String) = bridge.sendMessage(text)
    override fun stopGeneration() = bridge.stopGeneration()
    override fun regenerate() = bridge.regenerate()
    override fun continueGeneration() = bridge.continueGeneration()
    override fun reloadChat() = bridge.reloadChat()
}

interface NativeChatLogger {
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)

    object Android : NativeChatLogger {
        override fun info(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun warn(tag: String, message: String, throwable: Throwable?) {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }

    object None : NativeChatLogger {
        override fun info(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
