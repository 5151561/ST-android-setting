package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.chat.ChatRuntimeBridge

/**
 * [ChatEngine] backed by the hidden SillyTavern WebView runtime. Delegates each
 * generation action to the existing [ChatRuntimeBridge] commands, preserving the
 * original (and current default) behaviour.
 */
class BridgeChatEngine(
    private val bridge: ChatRuntimeBridge
) : ChatEngine {
    override fun send(text: String) = bridge.sendMessage(text)
    override fun stop() = bridge.stopGeneration()
    override fun regenerate() = bridge.regenerate()
    override fun continueGeneration() = bridge.continueGeneration()
}
