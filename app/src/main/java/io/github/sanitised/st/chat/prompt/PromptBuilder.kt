package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.ui.prototype.activeApiConnectionProvider
import io.github.sanitised.st.ui.prototype.modelForProvider

/**
 * Assembles a Chat Completion request payload on-device, replicating the core
 * subset of SillyTavern's `prepareOpenAIMessages` (openai.js). MVP scope:
 * system prompt from the character card + persona, then the visible chat history
 * as user/assistant turns, trimmed to a rough context budget.
 *
 * Advanced ST behaviour (world info, author's note, message examples, extension
 * prompts, instruct templates, regex) is intentionally out of scope here and is
 * added in later phases / kept on the WebView fallback.
 *
 * Pure Kotlin (no Android deps) so it can be unit-tested directly.
 */
object PromptBuilder {

    /** Rough token estimate; ST uses a real tokenizer, this is a conservative approximation. */
    private const val CHARS_PER_TOKEN = 4
    private const val PER_MESSAGE_OVERHEAD_TOKENS = 4
    private const val DEFAULT_MAX_CONTEXT = 4096
    private const val DEFAULT_MAX_TOKENS = 300

    private const val DEFAULT_AUTHORS_NOTE_DEPTH = 4

    /**
     * @param character active character card.
     * @param userName persona / user display name (settings `username`).
     * @param history visible (non-system) messages, oldest-first, including the just-sent user message.
     * @param settings full settings map from `getSettings()` (expects nested `oai_settings`).
     * @param worldInfoBefore scanned lorebook text inserted before the character defs.
     * @param worldInfoAfter scanned lorebook text inserted after the character defs.
     * @param authorsNote chat author's note, injected at [authorsNoteDepth] turns from the end.
     */
    fun build(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
        worldInfoBefore: String = "",
        worldInfoAfter: String = "",
        authorsNote: String = "",
        authorsNoteDepth: Int = DEFAULT_AUTHORS_NOTE_DEPTH,
    ): Map<String, Any?> {
        val oai = (settings["oai_settings"] as? Map<*, *>)?.stringKeyed() ?: emptyMap()
        val source = (oai["chat_completion_source"] as? String)?.takeIf { it.isNotBlank() } ?: "openai"
        val model = modelForProvider(settings, activeApiConnectionProvider(settings))

        val charName = character.name
        fun sub(text: String): String = text
            .replace(CHAR_MACRO, charName)
            .replace(USER_MACRO, userName)

        val systemContent = buildSystemPrompt(character, ::sub, sub(worldInfoBefore), sub(worldInfoAfter))

        val maxTokens = oai.intValue("openai_max_tokens", DEFAULT_MAX_TOKENS)
        val maxContext = oai.intValue("openai_max_context", DEFAULT_MAX_CONTEXT)
        val historyBudget = (maxContext - maxTokens - estimateTokens(systemContent)).coerceAtLeast(0)

        val messages = mutableListOf<Map<String, Any?>>()
        if (systemContent.isNotBlank()) {
            messages += mapOf("role" to "system", "content" to systemContent)
        }
        messages += trimToBudget(history, historyBudget) { msg ->
            mapOf(
                "role" to if (msg.isUser) "user" else "assistant",
                "content" to sub(msg.mes)
            )
        }

        // Author's note: injected as a system turn `depth` messages from the end
        // (ST default depth 4), but never before the leading system prompt.
        if (authorsNote.isNotBlank()) {
            val minIndex = if (systemContent.isNotBlank()) 1 else 0
            val insertAt = (messages.size - authorsNoteDepth).coerceIn(minIndex, messages.size)
            messages.add(insertAt, mapOf("role" to "system", "content" to sub(authorsNote)))
        }

        val payload = linkedMapOf<String, Any?>(
            "messages" to messages,
            "model" to model,
            "chat_completion_source" to source,
            "stream" to false,
            "temperature" to oai.doubleValue("temp_openai", 1.0),
            "frequency_penalty" to oai.doubleValue("freq_pen_openai", 0.0),
            "presence_penalty" to oai.doubleValue("pres_pen_openai", 0.0),
            "top_p" to oai.doubleValue("top_p_openai", 1.0),
            "max_tokens" to maxTokens,
            "user_name" to userName,
            "char_name" to charName,
        )
        (oai["reverse_proxy"] as? String)?.takeIf { it.isNotBlank() }?.let { proxy ->
            payload["reverse_proxy"] = proxy
            payload["proxy_password"] = (oai["proxy_password"] as? String).orEmpty()
        }
        return payload
    }

    private fun buildSystemPrompt(
        character: CharacterDetail,
        sub: (String) -> String,
        worldInfoBefore: String,
        worldInfoAfter: String,
    ): String {
        val parts = mutableListOf<String>()
        if (worldInfoBefore.isNotBlank()) parts += worldInfoBefore
        if (character.systemPrompt.isNotBlank()) parts += sub(character.systemPrompt)
        if (character.description.isNotBlank()) parts += sub(character.description)
        if (character.personality.isNotBlank()) parts += "${character.name}'s personality: ${sub(character.personality)}"
        if (character.scenario.isNotBlank()) parts += "Scenario: ${sub(character.scenario)}"
        if (worldInfoAfter.isNotBlank()) parts += worldInfoAfter
        return parts.joinToString("\n\n").trim()
    }

    /** Keeps the most recent messages whose estimated tokens fit [budgetTokens]. */
    private fun trimToBudget(
        history: List<ChatMessage>,
        budgetTokens: Int,
        transform: (ChatMessage) -> Map<String, Any?>,
    ): List<Map<String, Any?>> {
        val kept = ArrayDeque<Map<String, Any?>>()
        var used = 0
        for (msg in history.asReversed()) {
            val cost = estimateTokens(msg.mes) + PER_MESSAGE_OVERHEAD_TOKENS
            if (used + cost > budgetTokens && kept.isNotEmpty()) break
            kept.addFirst(transform(msg))
            used += cost
        }
        return kept.toList()
    }

    private fun estimateTokens(text: String): Int = (text.length / CHARS_PER_TOKEN) + 1

    private fun Map<*, *>.stringKeyed(): Map<String, Any?> =
        entries.associate { (k, v) -> k.toString() to v }

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.doubleValue(key: String, default: Double): Double =
        when (val v = this[key]) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }

    private val CHAR_MACRO = Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE)
    private val USER_MACRO = Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE)
}
