package io.github.sanitised.st.chat.engine

/**
 * Abstraction over the chat *generation* lifecycle so the visible chat UI
 * (`NativeChatScreen`) is decoupled from whichever engine produces replies.
 *
 * Two implementations exist during the native migration:
 *  - [BridgeChatEngine]: drives the hidden SillyTavern WebView runtime via
 *    `ChatRuntimeBridge` (the original architecture, kept as fallback).
 *  - `NativeChatEngine` (Phase B): assembles the prompt on-device and calls the
 *    backend generate endpoint directly.
 *
 * Only the core generation actions live here. Phase 1 single-chat message
 * mutations now route through `NativeChatRuntime`; still-unmigrated commands
 * such as quick replies and extension-specific UI remain on `ChatRuntimeBridge`.
 */
interface ChatEngine {
    /** Send a user message and generate a reply. Pending attachments are read from the store. */
    fun send(text: String)

    /** Stop the in-flight generation, if any. */
    fun stop()

    /** Regenerate the last AI reply. */
    fun regenerate()

    /** Continue (extend) the last AI reply. */
    fun continueGeneration()
}
