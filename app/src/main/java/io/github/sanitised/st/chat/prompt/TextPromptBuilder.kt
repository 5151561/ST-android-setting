package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.resolveTextGenServer
import io.github.sanitised.st.chat.ChatMessage

sealed class TextPromptBuildResult {
    data class Ready(
        val payload: Map<String, Any?>,
        val prompt: String,
        val stopStrings: List<String>,
    ) : TextPromptBuildResult()

    data class Unsupported(val reason: String) : TextPromptBuildResult()
}

object TextPromptBuilder {
    private const val CHARS_PER_TOKEN = 4
    private const val PER_TURN_OVERHEAD_TOKENS = 4
    private const val DEFAULT_MAX_CONTEXT = 2048

    private val supportedApiTypes = setOf("ooba", "koboldcpp", "llamacpp", "ollama")
    private val allowedStoryPlaceholders = setOf(
        "system",
        "description",
        "personality",
        "scenario",
        "persona",
        "wiBefore",
        "wiAfter",
        "loreBefore",
        "loreAfter",
        "anchorBefore",
        "anchorAfter",
        "char",
        "user",
        "mesExamples",
        "mesExamplesRaw",
    )

    fun build(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
        personaDescription: String = "",
        worldInfoBefore: String = "",
        worldInfoAfter: String = "",
        authorsNote: String = "",
    ): TextPromptBuildResult {
        unsupportedReason(settings, authorsNote)?.let { return TextPromptBuildResult.Unsupported(it) }
        val textGen = settings.mapValue("textgenerationwebui_settings")
        val apiType = textGen.stringValue("type").ifBlank { "ooba" }
        val maxTokens = settings.intValue("amount_gen", 300)
        val maxContext = textGen.intValue("max_context", DEFAULT_MAX_CONTEXT)

        val powerUser = settings.mapValue("power_user")
        val context = ContextTemplateSettings.fromMap(powerUser.mapValue("context"))
        val instruct = InstructSettings.fromMap(powerUser.mapValue("instruct"))
        val template = InstructTemplate(instruct, userName, character.name)

        val prompt = buildPrompt(
            character = character,
            userName = userName,
            history = history,
            maxContext = maxContext,
            maxTokens = maxTokens,
            personaDescription = personaDescription,
            worldInfoBefore = worldInfoBefore,
            worldInfoAfter = worldInfoAfter,
            context = context,
            template = template,
        )
        val stopStrings = StopStringBuilder.build(instruct, context, userName, character.name)
        val payload = linkedMapOf<String, Any?>(
            "prompt" to prompt,
            "model" to modelForApiType(textGen, apiType),
            "api_type" to apiType,
            "api_server" to resolveTextGenServer(settings, apiType),
            "stream" to false,
            "max_new_tokens" to maxTokens,
            "max_tokens" to maxTokens,
            "truncation_length" to maxContext,
            "temperature" to textGen.doubleValue("temp", 0.7),
            "top_p" to textGen.doubleValue("top_p", 1.0),
            "top_k" to textGen.intValue("top_k", 0),
            "repetition_penalty" to textGen.doubleValue("rep_pen", 1.0),
            "rep_pen" to textGen.doubleValue("rep_pen", 1.0),
            "stopping_strings" to stopStrings,
            "stop" to stopStrings,
        )
        if (apiType == "ollama" || apiType == "llamacpp") {
            payload["num_ctx"] = maxContext
        }
        return TextPromptBuildResult.Ready(payload, prompt, stopStrings)
    }

    fun supports(settings: Map<String, Any?>, authorsNote: String = ""): Boolean =
        unsupportedReason(settings, authorsNote) == null

    private fun unsupportedReason(settings: Map<String, Any?>, authorsNote: String): String? {
        if (settings["main_api"] != "textgenerationwebui") {
            return "main_api is not textgenerationwebui"
        }
        val textGen = settings.mapValue("textgenerationwebui_settings")
        val apiType = textGen.stringValue("type").ifBlank { "ooba" }
        if (apiType !in supportedApiTypes) {
            return "unsupported api_type: $apiType"
        }
        val powerUser = settings.mapValue("power_user")
        val context = ContextTemplateSettings.fromMap(powerUser.mapValue("context"))
        val instruct = InstructSettings.fromMap(powerUser.mapValue("instruct"))
        if (!instruct.enabled) {
            return "unsupported instruct.enabled=false"
        }
        if (context.storyStringPrefix.isNotBlank() || context.storyStringSuffix.isNotBlank()) {
            return "unsupported story_string_prefix/suffix"
        }
        if (context.storyStringPosition != 0) {
            return "unsupported story_string_position: ${context.storyStringPosition}"
        }
        if (authorsNote.isNotBlank()) {
            return "unsupported authors_note"
        }
        if (hasComplexHandlebars(context.storyString)) {
            return "complex Handlebars story_string is unsupported"
        }
        unknownStoryPlaceholder(context.storyString)?.let {
            return "unsupported story_string placeholder: $it"
        }
        return null
    }

    private fun buildPrompt(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        maxContext: Int,
        maxTokens: Int,
        personaDescription: String,
        worldInfoBefore: String,
        worldInfoAfter: String,
        context: ContextTemplateSettings,
        template: InstructTemplate,
    ): String {
        val values = mapOf(
            "system" to character.systemPrompt,
            "description" to character.description,
            "personality" to character.personality,
            "scenario" to character.scenario,
            "persona" to personaDescription,
            "wiBefore" to worldInfoBefore,
            "wiAfter" to worldInfoAfter,
            "loreBefore" to worldInfoBefore,
            "loreAfter" to worldInfoAfter,
            "anchorBefore" to "",
            "anchorAfter" to "",
            "char" to character.name,
            "user" to userName,
            "mesExamples" to character.messageExample,
            "mesExamplesRaw" to character.messageExample,
        ).mapValues { (_, value) -> substituteMacros(value, userName, character.name) }

        val blocks = mutableListOf<String>()
        renderStoryString(context.storyString, values).trimEnd().takeIf { it.isNotBlank() }?.let { blocks += it }
        context.chatStart.takeIf { it.isNotBlank() }?.let {
            blocks += substituteMacros(it, userName, character.name)
        }
        val header = blocks.joinToString("\n")
        val promptTail = template.formatPrompt(name = character.name)
        val turnBudget = (maxContext - maxTokens - estimateTokens(header) - estimateTokens(promptTail))
            .coerceAtLeast(0)
        val turns = trimTurnsToBudget(history.filter { !it.isSystem }, turnBudget)
        turns.forEachIndexed { index, message ->
            blocks += template.formatChat(
                name = message.name.ifBlank { if (message.isUser) userName else character.name },
                message = message.mes,
                isUser = message.isUser,
                position = when (index) {
                    0 -> InstructTurnPosition.FIRST
                    turns.lastIndex -> InstructTurnPosition.LAST
                    else -> InstructTurnPosition.NORMAL
                }
            )
        }
        blocks += promptTail
        return blocks.joinToString("\n")
    }

    private fun trimTurnsToBudget(history: List<ChatMessage>, budgetTokens: Int): List<ChatMessage> {
        val kept = ArrayDeque<ChatMessage>()
        var used = 0
        for (message in history.asReversed()) {
            val cost = estimateTokens(message.mes) + PER_TURN_OVERHEAD_TOKENS
            if (used + cost > budgetTokens && kept.isNotEmpty()) break
            kept.addFirst(message)
            used += cost
        }
        return kept.toList()
    }

    private fun estimateTokens(text: String): Int = (text.length / CHARS_PER_TOKEN) + 1

    private fun renderStoryString(storyString: String, values: Map<String, String>): String {
        var rendered = storyString
        allowedStoryPlaceholders.forEach { key ->
            rendered = rendered.replace(
                Regex("\\{\\{\\s*${Regex.escape(key)}\\s*\\}\\}", RegexOption.IGNORE_CASE),
                values[key].orEmpty()
            )
        }
        return rendered
    }

    private fun hasComplexHandlebars(storyString: String): Boolean {
        Regex("\\{\\{([^}]+)\\}\\}").findAll(storyString).forEach { match ->
            val body = match.groupValues[1].trim()
            if (body.startsWith("#") || body.startsWith("/") || body == "else") return true
            if (body.any { it.isWhitespace() }) return true
        }
        return false
    }

    private fun unknownStoryPlaceholder(storyString: String): String? =
        Regex("\\{\\{([^}]+)\\}\\}").findAll(storyString)
            .map { it.groupValues[1].trim() }
            .firstOrNull { body ->
                allowedStoryPlaceholders.none { allowed -> allowed.equals(body, ignoreCase = true) }
            }

    private fun modelForApiType(textGen: Map<String, Any?>, apiType: String): String =
        when (apiType) {
            "ooba" -> textGen.stringValue("custom_model")
            "llamacpp" -> textGen.stringValue("llamacpp_model")
            "ollama" -> textGen.stringValue("ollama_model")
            "koboldcpp" -> textGen.stringValue("koboldcpp_model")
            else -> ""
        }

    private fun substituteMacros(text: String, userName: String, charName: String): String =
        text
            .replace(Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE), charName)
            .replace(Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE), userName)

    private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.doubleValue(key: String, default: Double): Double =
        when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
}
