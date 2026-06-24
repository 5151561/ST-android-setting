package io.github.sanitised.st.chat.engine

/**
 * Abstraction over the chat *generation* lifecycle so the visible chat UI
 * (`NativeChatScreen`) talks to one native generation boundary.
 *
 * `NativeChatEngine` assembles prompts on-device, calls the local SillyTavern
 * backend endpoints directly, and persists the resulting JSONL.
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
