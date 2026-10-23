package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.ui.screens.activeApiConnectionProvider
import io.github.sanitised.st.ui.screens.modelForProvider

/**
 * Assembles a Chat Completion request payload on-device, replicating the core
 * subset of SillyTavern's prompt manager order for Chat Completion.
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
     * @param personaDescription active user persona description (power_user.persona_description).
     * @param worldInfoBefore scanned lorebook text inserted before the character defs.
     * @param worldInfoAfter scanned lorebook text inserted after the character defs.
     * @param authorsNote chat author's note, injected at [authorsNoteDepth] turns from the end.
     */
    fun build(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
        personaDescription: String = "",
        worldInfoBefore: String = "",
        worldInfoAfter: String = "",
        authorsNote: String = "",
        authorsNoteDepth: Int = DEFAULT_AUTHORS_NOTE_DEPTH,
        extensionPrompts: List<ExtensionPrompt> = emptyList(),
    ): Map<String, Any?> {
        val oai = (settings["oai_settings"] as? Map<*, *>)?.stringKeyed() ?: emptyMap()
        val source = (oai["chat_completion_source"] as? String)?.takeIf { it.isNotBlank() } ?: "openai"
        val activeProvider = activeApiConnectionProvider(settings)
        val model = modelForProvider(settings, activeProvider)
            .ifBlank { activeProvider.defaultModels.firstOrNull().orEmpty() }

        val charName = character.name
        fun sub(text: String): String = text
            .replace(CHAR_MACRO, charName)
            .replace(USER_MACRO, userName)

        val fallbackSystemPrompt = (oai["sysprompt_text"] as? String)?.takeIf { it.isNotBlank() }
        val systemMessages = buildSystemMessages(
            character = character,
            sub = ::sub,
            fallbackSystemPrompt = fallbackSystemPrompt,
            worldInfoBefore = sub(worldInfoBefore),
            worldInfoAfter = sub(worldInfoAfter),
            personaDescription = sub(personaDescription),
            extensionPrompts = extensionPrompts,
        )
        val systemTokenCost = systemMessages.sumOf { estimateTokens(it["content"] as String) }

        val maxTokens = oai.intValue("openai_max_tokens", DEFAULT_MAX_TOKENS)
        val maxContext = oai.intValue("openai_max_context", DEFAULT_MAX_CONTEXT)
        val historyBudget = (maxContext - maxTokens - systemTokenCost).coerceAtLeast(0)

        val messages = mutableListOf<Map<String, Any?>>()
        messages += systemMessages
        messages += trimToBudget(history, historyBudget) { msg ->
            mapOf(
                "role" to if (msg.isUser) "user" else "assistant",
                "content" to sub(msg.mes)
            )
        }

        val resolvedAuthorsNote = authorsNote.takeIf { it.isNotBlank() }
            ?: (oai["depth_prompt_prompt"] as? String)?.takeIf { it.isNotBlank() }
            ?: (settings["author_note"] as? String).orEmpty()
        val resolvedAuthorsNoteDepth = if (authorsNote.isNotBlank()) {
            authorsNoteDepth
        } else {
            oai.intValue("depth_prompt_depth", authorsNoteDepth).coerceAtLeast(0)
        }

        // Author's note: injected as a system turn `depth` messages from the end
        // (ST default depth 4), but never before the leading system prompt.
        if (resolvedAuthorsNote.isNotBlank()) {
            val minIndex = systemMessages.size
            val insertAt = (messages.size - resolvedAuthorsNoteDepth).coerceIn(minIndex, messages.size)
            messages.add(insertAt, mapOf("role" to "system", "content" to sub(resolvedAuthorsNote)))
        }

        val rawPostProc = (oai["custom_prompt_post_processing"] as? String)?.trim().orEmpty()
        val normalizedPostProc = if (rawPostProc.equals("none", ignoreCase = true)) "" else rawPostProc

        val payload = linkedMapOf<String, Any?>(
            "messages" to messages,
            "type" to "normal",
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
            "logit_bias" to null,
            "group_names" to emptyList<String>(),
            "include_reasoning" to oai.booleanValue("show_thoughts", false),
            "enable_web_search" to oai.booleanValue("enable_web_search", false),
            "request_images" to oai.booleanValue("request_images", false),
            "request_image_resolution" to (oai["request_image_resolution"] as? String ?: "auto"),
            "request_image_aspect_ratio" to (oai["request_image_aspect_ratio"] as? String ?: "auto"),
            "custom_prompt_post_processing" to normalizedPostProc,
        )

        val reasoningEffort = (oai["reasoning_effort"] as? String)?.trim().orEmpty().ifEmpty { "auto" }
        if (source !in setOf("custom", "pollinations") || !reasoningEffort.equals("auto", ignoreCase = true)) {
            payload["reasoning_effort"] = reasoningEffort
        }

        val verbosity = (oai["verbosity"] as? String)?.trim().orEmpty()
        payload["verbosity"] = if (verbosity.equals("auto", ignoreCase = true)) "" else verbosity

        val topK = numDouble(oai["top_k"] ?: oai["top_k_openai"], 0.0).toInt()
        if (topK > 0) {
            payload["top_k"] = topK
        }

        val minP = numDouble(oai["min_p"] ?: oai["min_p_openai"], 0.0)
        if (minP > 0.0) {
            payload["min_p"] = minP
        }

        val rawRepPen = oai["repetition_penalty"] ?: oai["rep_pen"] ?: oai["rep_pen_openai"]
        if (rawRepPen != null) {
            val repPen = numDouble(rawRepPen, 1.0)
            if (repPen > 0.0 && repPen != 1.0) {
                payload["repetition_penalty"] = repPen
                payload["rep_pen"] = repPen
            }
        }

        val topA = numDouble(oai["top_a"] ?: oai["top_a_openai"], 0.0)
        if (topA > 0.0) {
            payload["top_a"] = topA
        }

        val seed = numInt(oai["seed"], -1)
        if (seed >= 0) {
            payload["seed"] = seed
        }

        if (oai.booleanValue("claude_fast_mode", false)) {
            payload["claude_fast_mode"] = true
        }
        if (oai.booleanValue("custom_claude_prompt_caching", false)) {
            payload["custom_claude_prompt_caching"] = true
        }
        if (oai.booleanValue("custom_openai_responses_websocket", false)) {
            payload["custom_openai_responses_websocket"] = true
        }

        val reverseProxy = ((oai["reverse_proxy"] as? String)?.takeIf { it.isNotBlank() }
            ?: (oai["reverse_proxy_$source"] as? String)?.takeIf { it.isNotBlank() })
        if (reverseProxy != null) {
            payload["reverse_proxy"] = reverseProxy
            payload["proxy_password"] = ((oai["proxy_password"] as? String)?.takeIf { it.isNotBlank() }
                ?: (oai["proxy_password_$source"] as? String)).orEmpty()
        }
        val forwardedStringKeys = listOf(
            "secret_id",
            "vertexai_auth_mode",
            "vertexai_region",
            "vertexai_express_project_id",
            "custom_url",
            "custom_api_format",
            "custom_include_headers",
            "custom_include_body",
            "custom_exclude_body",
            "opencode_endpoint",
            "opencode_api_format",
            "moonshot_endpoint",
            "siliconflow_endpoint",
            "minimax_endpoint",
            "zai_endpoint",
            "pollinations_endpoint",
            "workers_ai_account_id",
            "aws_bedrock_region",
            "aws_bedrock_custom_response_path",
            "aws_bedrock_custom_stream_path",
            "nanogpt_provider"
        )
        for (key in forwardedStringKeys) {
            (oai[key] as? String)?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
                payload[key] = value
            }
        }
        if (source == "vertexai") {
            payload.putIfAbsent("vertexai_auth_mode", "express")
            payload.putIfAbsent("vertexai_region", "us-central1")
        }
        if (oai.containsKey("aws_bedrock_use_custom_template")) {
            payload["aws_bedrock_use_custom_template"] =
                oai.booleanValue("aws_bedrock_use_custom_template", false)
        }
        if (oai.containsKey("nanogpt_payg_override")) {
            payload["nanogpt_payg_override"] =
                oai.booleanValue("nanogpt_payg_override", false)
        }
        if (source == "makersuite" || source == "vertexai" || oai.containsKey("use_sysprompt")) {
            payload["use_sysprompt"] = oai.booleanValue("use_sysprompt", true)
        }
        return payload
    }

    private fun buildSystemMessages(
        character: CharacterDetail,
        sub: (String) -> String,
        fallbackSystemPrompt: String? = null,
        worldInfoBefore: String,
        worldInfoAfter: String,
        personaDescription: String,
        extensionPrompts: List<ExtensionPrompt>,
    ): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        fun add(role: String = "system", content: String) {
            if (content.isNotBlank()) messages += mapOf("role" to role, "content" to content.trim())
        }

        val mainPrompt = character.systemPrompt.takeIf { it.isNotBlank() }
            ?: fallbackSystemPrompt?.takeIf { it.isNotBlank() }
            ?: defaultMainPrompt(character.name)
        add(content = sub(mainPrompt))
        extensionPrompts
            .filter { it.position == ExtensionPromptPosition.BEFORE_PROMPT }
            .forEach { add(role = it.role, content = sub(it.content)) }
        add(content = worldInfoBefore)
        add(content = personaDescription)
        add(content = sub(character.description))
        add(content = sub(character.personality))
        add(content = sub(character.scenario))
        extensionPrompts
            .filter { it.position == ExtensionPromptPosition.IN_PROMPT }
            .forEach { add(role = it.role, content = sub(it.content)) }
        add(content = worldInfoAfter)
        add(content = formatMessageExamples(character.messageExample, sub))
        return messages
    }

    private fun defaultMainPrompt(charName: String): String =
        "Write $charName's next reply in a fictional chat between $charName and {{user}}."

    private fun formatMessageExamples(messageExample: String, sub: (String) -> String): String {
        if (messageExample.isBlank()) return ""
        return sub(messageExample)
            .replace(Regex("<START>", RegexOption.IGNORE_CASE), "[Start a new Chat]")
            .trim()
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
        numInt(this[key], default)

    private fun Map<String, Any?>.doubleValue(key: String, default: Double): Double =
        numDouble(this[key], default)

    private fun numInt(value: Any?, default: Int): Int =
        when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: value.trim().toDoubleOrNull()?.toInt() ?: default
            else -> default
        }

    private fun numDouble(value: Any?, default: Double): Double =
        when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.booleanValue(key: String, default: Boolean): Boolean =
        when (val v = this[key]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }

    private val CHAR_MACRO = Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE)
    private val USER_MACRO = Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE)
}
